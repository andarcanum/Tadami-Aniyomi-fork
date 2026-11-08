package tachiyomi.data.easteregg.crystal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.AchievementXp
import tachiyomi.data.achievement.Crystal_memorial
import tachiyomi.data.achievement.UserProfileStorage
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.data.achievement.toDomainProfile
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.UserProfile
import tachiyomi.domain.easteregg.crystal.CrystalCompletionResult
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRepository
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRestoreException
import tachiyomi.domain.easteregg.crystal.CrystalMemorialSnapshot
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState

class CrystalMemorialRepositoryImpl(
    private val database: AchievementsDatabase,
    private val synchronizeCosmetics: suspend (Boolean) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : CrystalMemorialRepository {
    private val key = CrystalMemorialIds.GRANT_KEY
    override fun observeState(): Flow<CrystalMemorialState> = database.crystalMemorialQueries.getState(key)
        .asFlow().mapToOneOrNull(Dispatchers.IO).map { it.toState() }
    override suspend fun getState(): CrystalMemorialState = withContext(Dispatchers.IO) { readState() }
    override suspend fun makeEligible(
        reason: CrystalEligibilityReason,
    ): CrystalMemorialState = database.withProfileMutation {
        database.transaction {
            database.crystalMemorialQueries.ensureState(key)
            database.crystalMemorialQueries.makeEligible(reason.name, now(), key)
            readState()
        }
    }
    override suspend fun acknowledgeAutomaticPresentation() = database.withProfileMutation {
        database.crystalMemorialQueries.acknowledgePresentation(now(), key)
        Unit
    }
    override suspend fun acknowledgeCeremony() = database.withProfileMutation {
        database.crystalMemorialQueries.acknowledgeCeremony(now(), key)
        Unit
    }
    override suspend fun completeMemorial(expectedGeneration: Long): CrystalCompletionResult = try {
        database.withProfileMutation {
            val result = database.transaction {
                val before = readState()
                when {
                    before.generation != expectedGeneration -> CrystalCompletionResult.StaleSession
                    before.isGranted -> {
                        repairProgress(before)
                        CrystalCompletionResult.AlreadyGranted(before)
                    }
                    !before.isEligible -> CrystalCompletionResult.NotEligible
                    else -> {
                        requireNotNull(database.achievementsQueries.getById(key).executeAsOneOrNull()) {
                            "Crystal catalog is not ready"
                        }
                        val timestamp = now()
                        database.crystalMemorialQueries.claimGrant(timestamp, key, expectedGeneration)
                        val granted = readState()
                        check(granted.isGranted)
                        repairProgress(granted)
                        AchievementXp.add(database, CrystalMemorialIds.XP.toLong(), timestamp)
                        database.userProfileQueries.incrementAchievementUnlocked(
                            user_id = "default",
                            last_updated = timestamp,
                        )
                        CrystalCompletionResult.Granted(granted)
                    }
                }
            }
            if (result is CrystalCompletionResult.Granted ||
                result is CrystalCompletionResult.AlreadyGranted
            ) {
                synchronizeSafely(true)
            }
            result
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logcat(LogPriority.ERROR) { "Crystal receipt transaction failed: " + e.javaClass.simpleName }
        CrystalCompletionResult.RetryableFailure
    }

    override suspend fun debugReset(): CrystalMemorialState = database.withProfileMutation {
        val reset = database.transaction {
            database.crystalMemorialQueries.ensureState(key)
            val before = readState()
            if (before.isGranted) {
                AchievementXp.add(database, -before.grantedXp.toLong(), now())
                database.userProfileQueries.decrementAchievementUnlocked(user_id = "default", last_updated = now())
            }
            database.achievementProgressQueries.deleteById(key)
            database.crystalMemorialQueries.reset(key)
            readState()
        }
        synchronizeSafely(false)
        reset
    }

    override suspend fun snapshotForBackup(): CrystalMemorialSnapshot = database.withProfileMutation {
        database.transaction {
            val row = database.crystalMemorialQueries.getState(key).executeAsOneOrNull()
            val state = row?.toState()
            CrystalMemorialSnapshot(
                state,
                database.userProfileQueries.getDefaultProfile().executeAsOneOrNull()?.toDomainProfile(),
                state?.let {
                    AchievementProgress(
                        achievementId = key,
                        progress = if (it.isGranted) 1 else 0,
                        maxProgress = 1,
                        isUnlocked = it.isGranted,
                        unlockedAt = it.grantedAt,
                        lastUpdated = now(),
                    )
                },
            )
        }
    }

    override suspend fun restoreSnapshot(snapshot: CrystalMemorialSnapshot) = database.withProfileMutation {
        val restored = database.transaction {
            val local = readState()
            val localProfile = database.userProfileQueries.getDefaultProfile().executeAsOneOrNull()?.toDomainProfile()
            val incoming = snapshot.state
            val sourceProfile = snapshot.profile
            val sourceProgress = snapshot.progress
            if (sourceProgress != null &&
                sourceProgress.achievementId != key
            ) {
                invalidBackup("Wrong memorial achievement projection")
            }
            if (incoming != null && sourceProgress != null &&
                incoming.isGranted != sourceProgress.isUnlocked
            ) {
                invalidBackup("Receipt and achievement projection disagree")
            }
            if (incoming != null &&
                (
                    (incoming.grantedAt == null && incoming.grantedXp != 0) ||
                        (incoming.grantedAt != null && incoming.grantedXp != CrystalMemorialIds.XP)
                    )
            ) {
                invalidBackup("Inconsistent memorial receipt")
            }
            val legacyGrant = incoming == null && snapshot.progress?.isUnlocked == true
            val sourceGranted = incoming?.isGranted == true || legacyGrant
            val localContribution = if (local.isGranted) CrystalMemorialIds.XP else 0
            val sourceContribution = if (sourceGranted) CrystalMemorialIds.XP else 0
            if (sourceGranted && sourceProfile == null) invalidBackup("Granted memorial backup is missing its profile")
            if (local.isGranted && localProfile == null) invalidBackup("Local memorial receipt is missing its profile")
            if ((localProfile?.totalXP ?: 0) < localContribution ||
                (sourceProfile?.totalXP ?: 0) < sourceContribution
            ) {
                invalidBackup("Memorial XP contribution is not represented in the profile")
            }
            val granted = local.isGranted || sourceGranted
            val unionContribution = if (granted) CrystalMemorialIds.XP else 0
            val base = maxOf(
                (localProfile?.totalXP ?: 0) - localContribution,
                (sourceProfile?.totalXP ?: 0) - sourceContribution,
            ).toLong()
            val total = base + unionContribution
            if (total !in
                0L..Int.MAX_VALUE.toLong()
            ) {
                invalidBackup("Restored XP is outside the supported profile range")
            }
            val timestamp = now()
            val legacyGrantAt = if (legacyGrant) snapshot.progress?.unlockedAt ?: timestamp else null
            val grantedAt = listOfNotNull(local.grantedAt, incoming?.grantedAt, legacyGrantAt).minOrNull()
            val mergedState = CrystalMemorialState(
                eligibilityReason =
                local.eligibilityReason ?: incoming?.eligibilityReason
                    ?: if (granted) CrystalEligibilityReason.RESTORED else null,
                eligibleAt = listOfNotNull(local.eligibleAt, incoming?.eligibleAt, grantedAt).minOrNull(),
                automaticPresentationConsumedAt = listOfNotNull(
                    local.automaticPresentationConsumedAt,
                    incoming?.automaticPresentationConsumedAt,
                ).minOrNull(),
                grantedAt = grantedAt,
                grantedXp = unionContribution,
                ceremonyAcknowledgedAt = listOfNotNull(
                    local.ceremonyAcknowledgedAt,
                    incoming?.ceremonyAcknowledgedAt,
                ).minOrNull(),
                generation = local.generation + 1,
            )
            val localCount = (localProfile?.achievementsUnlocked ?: 0) - if (local.isGranted) 1 else 0
            val sourceCount = (sourceProfile?.achievementsUnlocked ?: 0) - if (sourceGranted) 1 else 0
            val count = maxOf(localCount, sourceCount, 0) + if (granted) 1 else 0
            val original = localProfile ?: sourceProfile ?: UserProfile.createDefault()
            val xp = AchievementXp.fields(total)
            // Preserve the established legacy profile merge when neither side contains this gift.
            val level = if (granted) xp.level else maxOf(localProfile?.level ?: 1, sourceProfile?.level ?: 1)
            val currentXp = if (granted) {
                xp.current.toInt()
            } else if ((sourceProfile?.level ?: 0) >
                (localProfile?.level ?: 0)
            ) {
                sourceProfile?.currentXP ?: 0
            } else {
                maxOf(localProfile?.currentXP ?: 0, sourceProfile?.currentXP ?: 0)
            }
            val nextXp = if (granted) {
                xp.next.toInt()
            } else if ((sourceProfile?.level ?: 0) >
                (localProfile?.level ?: 0)
            ) {
                sourceProfile?.xpToNextLevel ?: xp.next.toInt()
            } else {
                localProfile?.xpToNextLevel ?: sourceProfile?.xpToNextLevel ?: xp.next.toInt()
            }
            val mergedProfile = original.copy(
                userId = "default", username = sourceProfile?.username ?: localProfile?.username,
                totalXP = total.toInt(), level = level, currentXP = currentXp, xpToNextLevel = nextXp,
                titles = ((localProfile?.titles ?: emptyList()) + (sourceProfile?.titles ?: emptyList())).distinct(),
                badges = ((localProfile?.badges ?: emptyList()) + (sourceProfile?.badges ?: emptyList())).distinct(),
                unlockedThemes = (
                    (localProfile?.unlockedThemes ?: emptyList()) +
                        (sourceProfile?.unlockedThemes ?: emptyList())
                    ).distinct(),
                achievementsUnlocked = count,
                totalAchievements = maxOf(
                    localProfile?.totalAchievements ?: 0,
                    sourceProfile?.totalAchievements ?: 0,
                    count,
                ),
                joinDate = listOfNotNull(localProfile?.joinDate, sourceProfile?.joinDate).minOrNull() ?: timestamp,
            )
            UserProfileStorage.write(database, mergedProfile, timestamp)
            database.crystalMemorialQueries.restoreState(
                campaign_key = key,
                eligibility_reason = mergedState.eligibilityReason?.name,
                eligible_at = mergedState.eligibleAt,
                automatic_presentation_consumed_at = mergedState.automaticPresentationConsumedAt,
                granted_at = mergedState.grantedAt,
                granted_xp = mergedState.grantedXp.toLong(),
                ceremony_acknowledged_at = mergedState.ceremonyAcknowledgedAt,
                generation = mergedState.generation,
            )
            repairProgress(mergedState)
            mergedState
        }
        synchronizeSafely(restored.isGranted)
    }

    private fun invalidBackup(message: String): Nothing = throw CrystalMemorialRestoreException(message)

    override suspend fun repairProjections() = database.withProfileMutation {
        val state = database.transaction { readState().also { repairProgress(it) } }
        synchronizeSafely(state.isGranted)
    }

    private fun repairProgress(state: CrystalMemorialState) {
        if (state.isGranted) {
            requireNotNull(database.achievementsQueries.getById(key).executeAsOneOrNull()) {
                "Crystal catalog is not ready"
            }
            database.achievementProgressQueries.upsert(
                achievement_id = key,
                progress = 1L,
                max_progress = 1L,
                is_unlocked = 1L,
                unlocked_at = state.grantedAt,
                last_updated = now(),
            )
        } else {
            database.achievementProgressQueries.deleteById(key)
        }
    }

    private suspend fun synchronizeSafely(granted: Boolean) {
        try {
            synchronizeCosmetics(granted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The receipt is already committed. Retrying this derived cache must never replay XP.
            logcat(LogPriority.WARN) { "Crystal cosmetic projection needs repair: " + e.javaClass.simpleName }
        }
    }
    private fun readState() = database.crystalMemorialQueries.getState(key).executeAsOneOrNull().toState()
}

internal fun Crystal_memorial?.toState(): CrystalMemorialState = if (this ==
    null
) {
    CrystalMemorialState()
} else {
    CrystalMemorialState(
        eligibilityReason = CrystalEligibilityReason.entries.firstOrNull { it.name == eligibility_reason },
        eligibleAt = eligible_at,
        automaticPresentationConsumedAt = automatic_presentation_consumed_at,
        grantedAt = granted_at,
        grantedXp = granted_xp.toInt(),
        ceremonyAcknowledgedAt = ceremony_acknowledged_at,
        generation = generation,
    )
}
