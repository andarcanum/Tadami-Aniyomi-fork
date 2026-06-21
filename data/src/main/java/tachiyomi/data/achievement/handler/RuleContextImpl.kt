package tachiyomi.data.achievement.handler

import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.achievement.rules.GenreAliases
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository

class RuleContextImpl(
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val novelHandler: NovelDatabaseHandler,
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
    private val novelRepository: NovelRepository,
    private val diversityChecker: DiversityAchievementChecker,
    private val streakChecker: StreakAchievementChecker,
    private val featureCollector: FeatureUsageCollector,
    private val allProgress: Map<String, AchievementProgress>,
    private val allAchievementsMap: Map<String, Achievement>,
) : RuleContext {

    override suspend fun getChaptersRead(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> {
                mangaHandler.awaitOneOrNull { db -> db.chaptersQueries.getTotalReadChapterCount() }?.toInt() ?: 0
            }
            AchievementCategory.ANIME -> {
                animeHandler.awaitOneOrNull { db -> db.episodesQueries.getTotalSeenEpisodeCount() }?.toInt() ?: 0
            }
            AchievementCategory.NOVEL -> {
                novelHandler.awaitOneOrNull { db -> db.novel_chaptersQueries.getTotalReadChapterCount() }?.toInt() ?: 0
            }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                val manga = mangaHandler.awaitOneOrNull { db -> db.chaptersQueries.getTotalReadChapterCount() } ?: 0L
                val anime = animeHandler.awaitOneOrNull { db -> db.episodesQueries.getTotalSeenEpisodeCount() } ?: 0L
                val novel =
                    novelHandler.awaitOneOrNull { db -> db.novel_chaptersQueries.getTotalReadChapterCount() } ?: 0L
                (manga + anime + novel).toInt()
            }
        }
    }

    override suspend fun getLibraryCount(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> {
                mangaHandler.awaitOneOrNull { db -> db.mangasQueries.getLibraryCount() }?.toInt() ?: 0
            }
            AchievementCategory.ANIME -> {
                animeHandler.awaitOneOrNull { db -> db.animesQueries.getLibraryCount() }?.toInt() ?: 0
            }
            AchievementCategory.NOVEL -> {
                novelHandler.awaitOneOrNull { db -> db.novelsQueries.getLibraryCount() }?.toInt() ?: 0
            }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                val manga = mangaHandler.awaitOneOrNull { db -> db.mangasQueries.getLibraryCount() } ?: 0L
                val anime = animeHandler.awaitOneOrNull { db -> db.animesQueries.getLibraryCount() } ?: 0L
                val novel = novelHandler.awaitOneOrNull { db -> db.novelsQueries.getLibraryCount() } ?: 0L
                (manga + anime + novel).toInt()
            }
        }
    }

    override suspend fun getCompletedCount(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> {
                mangaHandler.awaitOneOrNull { db -> db.mangasQueries.getCompletedMangaCount() }?.toInt() ?: 0
            }
            AchievementCategory.ANIME -> {
                animeHandler.awaitOneOrNull { db -> db.animesQueries.getCompletedAnimeCount() }?.toInt() ?: 0
            }
            AchievementCategory.NOVEL -> {
                novelHandler.awaitOneOrNull { db -> db.novelsQueries.getCompletedNovelCount() }?.toInt() ?: 0
            }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                val manga = mangaHandler.awaitOneOrNull { db -> db.mangasQueries.getCompletedMangaCount() } ?: 0L
                val anime = animeHandler.awaitOneOrNull { db -> db.animesQueries.getCompletedAnimeCount() } ?: 0L
                val novel = novelHandler.awaitOneOrNull { db -> db.novelsQueries.getCompletedNovelCount() } ?: 0L
                (manga + anime + novel).toInt()
            }
        }
    }

    override suspend fun getOngoingCount(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> {
                mangaHandler.awaitOneOrNull { db -> db.mangasQueries.getLibraryCountByStatus(1L) }?.toInt() ?: 0
            }
            AchievementCategory.ANIME -> {
                animeHandler.awaitOneOrNull { db -> db.animesQueries.getLibraryCountByStatus(1L) }?.toInt() ?: 0
            }
            AchievementCategory.NOVEL -> {
                novelHandler.awaitOneOrNull { db -> db.novelsQueries.getLibraryCountByStatus(1L) }?.toInt() ?: 0
            }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                val manga = mangaHandler.awaitOneOrNull { db -> db.mangasQueries.getLibraryCountByStatus(1L) } ?: 0L
                val anime = animeHandler.awaitOneOrNull { db -> db.animesQueries.getLibraryCountByStatus(1L) } ?: 0L
                val novel = novelHandler.awaitOneOrNull { db -> db.novelsQueries.getLibraryCountByStatus(1L) } ?: 0L
                (manga + anime + novel).toInt()
            }
        }
    }

    override suspend fun getGenreDiversity(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> diversityChecker.getMangaGenreDiversity()
            AchievementCategory.ANIME -> diversityChecker.getAnimeGenreDiversity()
            AchievementCategory.NOVEL -> diversityChecker.getNovelGenreDiversity()
            AchievementCategory.BOTH, AchievementCategory.SECRET -> diversityChecker.getGenreDiversity()
        }
    }

    override suspend fun getSourceDiversity(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> diversityChecker.getMangaSourceDiversity()
            AchievementCategory.ANIME -> diversityChecker.getAnimeSourceDiversity()
            AchievementCategory.NOVEL -> diversityChecker.getNovelSourceDiversity()
            AchievementCategory.BOTH, AchievementCategory.SECRET -> diversityChecker.getSourceDiversity()
        }
    }

    override suspend fun hasCompletedWithMinChapters(category: AchievementCategory, minChapters: Int): Boolean {
        return when (category) {
            AchievementCategory.MANGA -> {
                mangaHandler.awaitOneOrNull { db ->
                    db.mangasQueries.hasCompletedLibraryMangaWithMinReadChapters(minChapters.toLong())
                } ?: false
            }
            AchievementCategory.NOVEL -> {
                novelHandler.awaitOneOrNull { db ->
                    db.novelsQueries.hasCompletedLibraryNovelWithMinReadChapters(minChapters.toLong())
                } ?: false
            }
            else -> false
        }
    }

    override suspend fun hasLibraryGenre(genre: String): Int {
        return GenreAliases.allGenreSearchTerms(genre).sumOf { term ->
            val manga = mangaHandler.awaitOneOrNull { db -> db.mangasQueries.getLibraryGenreCount(term) } ?: 0L
            val anime = animeHandler.awaitOneOrNull { db -> db.animesQueries.getLibraryGenreCount(term) } ?: 0L
            val novel = novelHandler.awaitOneOrNull { db -> db.novelsQueries.getLibraryGenreCount(term) } ?: 0L
            (manga + anime + novel).toInt()
        }
    }

    override suspend fun hasLibraryTitleLike(pattern: String): Boolean {
        return GenreAliases.allTitleSearchTerms(pattern).any { term ->
            val manga = mangaHandler.awaitOneOrNull { db -> db.mangasQueries.hasLibraryTitleLike(term) } ?: false
            val anime = animeHandler.awaitOneOrNull { db -> db.animesQueries.hasLibraryTitleLike(term) } ?: false
            val novel = novelHandler.awaitOneOrNull { db -> db.novelsQueries.hasLibraryTitleLike(term) } ?: false
            manga || anime || novel
        }
    }

    override suspend fun getCurrentStreak(): Int {
        return streakChecker.getCurrentStreak()
    }

    override suspend fun hasSessionInTimeRange(startHour: Int, endHour: Int): Boolean {
        return featureCollector.hasSessionInTimeRange(startHour, endHour)
    }

    override suspend fun getFeatureCount(feature: AchievementEvent.Feature): Int {
        return featureCollector.getFeatureCount(feature)
    }

    override suspend fun getMaxSessionDuration(): Long {
        return featureCollector.getMaxSessionDuration()
    }

    override suspend fun getUnlockedAchievementsCountExcluding(metaIds: Set<String>): Int {
        return allProgress.values
            .count { it.isUnlocked && it.achievementId !in metaIds }
    }

    override suspend fun getCurrentPoints(): Int {
        return allProgress.values
            .filter { it.isUnlocked }
            .sumOf { progress ->
                val achievement = allAchievementsMap[progress.achievementId] ?: return@sumOf 0
                if (achievement.isTiered) {
                    achievement.tiers?.take(progress.currentTier)?.sumOf { it.points } ?: 0
                } else {
                    achievement.points
                }
            }
    }
}
