package tachiyomi.data.achievement.localization

import tachiyomi.data.achievement.model.AchievementJson

data class AchievementLocalizedText(
    val title: String,
    val description: String?,
)

fun interface AchievementTextResolver {
    fun resolve(achievement: AchievementJson): AchievementLocalizedText
}
