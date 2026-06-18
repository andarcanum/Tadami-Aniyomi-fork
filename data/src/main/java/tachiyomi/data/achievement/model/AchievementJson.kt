package tachiyomi.data.achievement.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AchievementDefinitions(
    val version: Int,
    val achievements: List<AchievementJson>,
)

@Serializable
data class AchievementJson(
    val id: String,
    val type: String,
    val category: String,
    val threshold: Int? = null,
    val points: Int = 0,
    val title: String,
    val description: String? = null,
    @SerialName("badge_icon")
    val badgeIcon: String? = null,
    @SerialName("is_hidden")
    val isHidden: Boolean = false,
    @SerialName("is_secret")
    val isSecret: Boolean = false,
    @SerialName("unlockable_id")
    val unlockableId: String? = null,
    val rewards: List<tachiyomi.domain.achievement.model.Reward>? = null,
    // ---- Refactor additions (all optional / backwards-compatible) ----
    /** common | uncommon | rare | epic | legendary | mythic */
    val rarity: String? = null,
    /** Free-form tags used for filtering and grouping in the UI. */
    val tags: List<String> = emptyList(),
    /** Progressive-disclosure hint shown for locked secret achievements. */
    val hint: String? = null,
    /** Optional seasonal/event identifier (e.g. "halloween_2026"). */
    val season: String? = null,
    /** Groups a linear chain of achievements (e.g. "manga_chapters"). */
    @SerialName("tier_group")
    val tierGroup: String? = null,
    /** 1-based position of this achievement inside its [tierGroup]. */
    @SerialName("tier_level")
    val tierLevel: Int? = null,
    /** Optional cosmetic reward-set identifier (e.g. "sakura_set"). */
    @SerialName("reward_set")
    val rewardSet: String? = null,
)
