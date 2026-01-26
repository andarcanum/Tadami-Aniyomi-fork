package tachiyomi.data.achievement.model

import tachiyomi.domain.achievement.model.AchievementCategory

sealed class AchievementEvent {
    abstract val timestamp: Long

    data class ChapterRead(
        val mangaId: Long,
        val chapterNumber: Int,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class EpisodeWatched(
        val animeId: Long,
        val episodeNumber: Int,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class LibraryAdded(
        val entryId: Long,
        val type: AchievementCategory,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class LibraryRemoved(
        val entryId: Long,
        val type: AchievementCategory,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class MangaCompleted(
        val mangaId: Long,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class AnimeCompleted(
        val animeId: Long,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()
}
