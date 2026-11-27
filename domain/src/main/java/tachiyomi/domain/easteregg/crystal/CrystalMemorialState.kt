package tachiyomi.domain.easteregg.crystal

import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.UserProfile

object CrystalMemorialIds {
    const val GRANT_KEY = "crystal_memorial_v1"
    const val XP = 999
    const val RELIC = "special_crystal_relic"
    const val FRAME = "avatar_frame_crystal_ears"
    const val PAWS = "special_background_crystal_warm_paws"
    const val TITLE = "title_crystal_light_keeper"
    const val BADGE = "home_badge_crystal_memory_paw"
    const val SILK = "profile_crystal_moon_silk"
    val REWARDS: Set<String> = setOf(RELIC, FRAME, PAWS, TITLE, BADGE, SILK)
}

enum class CrystalEligibilityReason { RELEASE, TITLE, DEBUG, RESTORED }

/** Coherent receipt, profile and its achievement projection for native backup adapters. */
data class CrystalMemorialSnapshot(
    val state: CrystalMemorialState? = null,
    val profile: UserProfile? = null,
    val progress: AchievementProgress? = null,
)

class CrystalMemorialRestoreException(message: String) : IllegalArgumentException(message)

data class CrystalMemorialState(
    val eligibilityReason: CrystalEligibilityReason? = null,
    val eligibleAt: Long? = null,
    val automaticPresentationConsumedAt: Long? = null,
    val grantedAt: Long? = null,
    val grantedXp: Int = 0,
    val ceremonyAcknowledgedAt: Long? = null,
    val generation: Long = 0L,
) {
    val isEligible: Boolean get() = eligibilityReason != null && eligibleAt != null
    val isGranted: Boolean get() = grantedAt != null && grantedXp == CrystalMemorialIds.XP
    val hasPendingCeremony: Boolean get() = isGranted && ceremonyAcknowledgedAt == null
}

sealed interface CrystalCompletionResult {
    data class Granted(val state: CrystalMemorialState) : CrystalCompletionResult
    data class AlreadyGranted(val state: CrystalMemorialState) : CrystalCompletionResult
    data object StaleSession : CrystalCompletionResult
    data object NotEligible : CrystalCompletionResult
    data object RetryableFailure : CrystalCompletionResult
}
