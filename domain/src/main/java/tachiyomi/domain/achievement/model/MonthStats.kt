package tachiyomi.domain.achievement.model

data class MonthStats(
    val chaptersRead: Int,
    val episodesWatched: Int,
    val timeInAppMinutes: Int,
    val achievementsUnlocked: Int,
)
