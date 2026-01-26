package tachiyomi.domain.achievement.model

import androidx.compose.runtime.Immutable

@Immutable
data class Achievement(
    val id: String,
    val type: AchievementType,
    val category: AchievementCategory,
    val threshold: Int? = null,
    val points: Int = 0,
    val title: String,
    val description: String? = null,
    val badgeIcon: String? = null,
    val isHidden: Boolean = false,
    val isSecret: Boolean = false,
    val unlockableId: String? = null,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
)
