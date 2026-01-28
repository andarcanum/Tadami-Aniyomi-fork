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

    data class SessionEnd(
        val durationMs: Long,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class AppStart(
        val hourOfDay: Int, // 0-23, для достижений типа "Ночной чтец" (2-5) или "Жаворонок" (6-9)
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class FeatureUsed(
        val feature: Feature,
        val count: Int = 1,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    enum class Feature {
        SEARCH,           // Использование поиска
        ADVANCED_SEARCH,  // Расширенный поиск
        FILTER,           // Использование фильтров
        DOWNLOAD,         // Скачивание глав/серий
        BACKUP,           // Создание бэкапа
        RESTORE,          // Восстановление из бэкапа
        SETTINGS,         // Изменение настроек
        STATS,            // Просмотр статистики
        THEME_CHANGE,     // Смена темы
        LOGO_CLICK,       // Нажатие на логотип (для секретных достижений)
    }
}
