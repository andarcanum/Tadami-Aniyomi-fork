package eu.kanade.domain.easteregg.crystal

import com.tadami.aurora.BuildConfig
import eu.kanade.domain.ui.UserProfilePreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.easteregg.crystal.CrystalCompletionResult
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRepository
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState

class CrystalMemorialManager(
    val repository: CrystalMemorialRepository,
    val appearance: CrystalAppearancePreferences,
    private val profile: UserProfilePreferences,
    private val debugAllowed: Boolean = BuildConfig.DEBUG && BuildConfig.BUILD_TYPE == "debug",
) {
    val state = repository.observeState()
    private val appearanceMutation = Mutex()

    suspend fun resetAndPrepareDebug(): CrystalMemorialState = appearanceMutation.withLock {
        check(debugAllowed && BuildConfig.DEBUG && BuildConfig.BUILD_TYPE == "debug") { "Memorial reset is debug-only" }
        repository.debugReset()
        if (profile.avatarFrameStyle().get() in
            CrystalAppearancePreferences.FRAME_KEYS
        ) {
            profile.avatarFrameStyle().set("none")
        }
        if (profile.profileTitle().get() == CrystalMemorialIds.TITLE) profile.profileTitle().set("none")
        if (profile.homeBadgeStyle().get() == CrystalMemorialIds.BADGE) profile.homeBadgeStyle().set("none")
        appearance.pawsEnabled().set(false)
        appearance.silkEnabled().set(false)
        repository.makeEligible(CrystalEligibilityReason.DEBUG)
    }

    /** Replay reads ownership but can never call the mutating grant operation. */
    suspend fun completeSession(expectedGeneration: Long, replay: Boolean): CrystalCompletionResult {
        if (replay) {
            val current = repository.getState()
            return if (current.isGranted) {
                CrystalCompletionResult.AlreadyGranted(
                    current,
                )
            } else {
                CrystalCompletionResult.NotEligible
            }
        }
        return repository.completeMemorial(expectedGeneration)
    }

    suspend fun setEquipped(
        cosmetic: CrystalCosmetic,
        enabled: Boolean,
        variant: String? = null,
    ): Boolean = appearanceMutation.withLock {
        if (!repository.getState().isGranted) return@withLock false
        when (cosmetic) {
            CrystalCosmetic.FRAME -> {
                val key = variant ?: "crystal_fold"
                if (enabled && key !in CrystalAppearancePreferences.FRAME_KEYS) return@withLock false
                if (enabled) {
                    profile.avatarFrameStyle().set(key)
                } else if (profile.avatarFrameStyle().get() in
                    CrystalAppearancePreferences.FRAME_KEYS
                ) {
                    profile.avatarFrameStyle().set("none")
                }
            }
            CrystalCosmetic.PAWS -> appearance.pawsEnabled().set(enabled)
            CrystalCosmetic.TITLE -> if (enabled) {
                profile.profileTitle().set(CrystalMemorialIds.TITLE)
            } else if (profile.profileTitle().get() == CrystalMemorialIds.TITLE) {
                profile.profileTitle().set("none")
            }
            CrystalCosmetic.BADGE -> if (enabled) {
                profile.homeBadgeStyle().set(CrystalMemorialIds.BADGE)
            } else if (profile.homeBadgeStyle().get() ==
                CrystalMemorialIds.BADGE
            ) {
                profile.homeBadgeStyle().set("none")
            }
            CrystalCosmetic.SILK -> {
                val key = variant ?: appearance.silkVariant().get()
                if (enabled && key !in CrystalAppearancePreferences.PALETTE_KEYS) return@withLock false
                if (enabled) appearance.silkVariant().set(key)
                appearance.silkEnabled().set(enabled)
            }
        }
        true
    }
}
