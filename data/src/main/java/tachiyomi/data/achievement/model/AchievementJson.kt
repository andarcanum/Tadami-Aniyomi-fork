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
)
