package tachiyomi.data.achievement.localization

import tachiyomi.data.achievement.model.AchievementJson

data class AchievementLocalizedText(
    val title: String,
    val description: String?,
    val hint: String? = null,
    val hintVague: String? = null,
    val hintDirect: String? = null,
    val hintObvious: String? = null,
)

fun interface AchievementTextResolver {
    fun resolve(achievement: AchievementJson): AchievementLocalizedText
}
