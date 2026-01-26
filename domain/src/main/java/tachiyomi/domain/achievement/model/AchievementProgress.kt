package tachiyomi.domain.achievement.model

import androidx.compose.runtime.Immutable

@Immutable
data class AchievementProgress(
    val achievementId: String,
    val progress: Int = 0,
    val maxProgress: Int = 100,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
)
