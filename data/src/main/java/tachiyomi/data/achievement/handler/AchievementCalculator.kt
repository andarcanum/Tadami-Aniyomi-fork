package tachiyomi.data.achievement.handler

import kotlinx.coroutines.flow.first
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository

class AchievementCalculator(
    private val repository: AchievementRepository,
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val novelHandler: NovelDatabaseHandler,
    private val diversityChecker: DiversityAchievementChecker,
    private val streakChecker: StreakAchievementChecker,
    private val achievementsDatabase: AchievementsDatabase,
) {
    companion object {
        private const val BATCH_SIZE = 50
    }

    suspend fun calculateInitialProgress(): CalculationResult {
        val startTime = System.currentTimeMillis()
        var achievementsProcessed = 0
        var achievementsUnlocked = 0

        try {
            logcat(LogPriority.INFO) { "Starting initial achievement calculation..." }

            val allAchievements = repository.getAll().first()
            val achievementsById = allAchievements.associateBy { it.id }

            val (mangaChapters, animeEpisodes, novelChapters) = getTotalConsumed()
            logcat(LogPriority.INFO) {
                "Total consumed: manga chapters=$mangaChapters, anime episodes=$animeEpisodes, novel chapters=$novelChapters"
            }

            val genreCount = diversityChecker.getGenreDiversity()
            val sourceCount = diversityChecker.getSourceDiversity()
            val mangaGenreCount = diversityChecker.getMangaGenreDiversity()
            val animeGenreCount = diversityChecker.getAnimeGenreDiversity()
            val novelGenreCount = diversityChecker.getNovelGenreDiversity()
            val mangaSourceCount = diversityChecker.getMangaSourceDiversity()
            val animeSourceCount = diversityChecker.getAnimeSourceDiversity()
            val novelSourceCount = diversityChecker.getNovelSourceDiversity()
            logcat(LogPriority.INFO) {
                "Unique genres: $genreCount (M: $mangaGenreCount, A: $animeGenreCount, N: $novelGenreCount), " +
                    "sources: $sourceCount (M: $mangaSourceCount, A: $animeSourceCount, N: $novelSourceCount)"
            }

            val streak = streakChecker.getCurrentStreak()
            logcat(LogPriority.INFO) { "Current streak: $streak days" }

            val nonMetaAchievements = allAchievements.filter { it.type != AchievementType.META }
            val needsLibraryCounts = nonMetaAchievements.any { it.type == AchievementType.LIBRARY }
            val needsCompletedCounts = nonMetaAchievements.any {
                when (it.type) {
                    AchievementType.EVENT ->
                        it.id == "complete_1_manga" ||
                            it.id == "complete_1_anime" ||
                            it.id == "complete_1_novel"
                    AchievementType.QUANTITY -> it.id.startsWith("complete_")
                    else -> false
                }
            }
            val needsLongMangaCompletion = nonMetaAchievements.any { it.id == "read_long_manga" }
            val needsLongNovelCompletion = nonMetaAchievements.any { it.id == "read_long_novel" }

            val libraryCounts = if (needsLibraryCounts) getLibraryCounts() else Triple(0L, 0L, 0L)
            val completedCounts = if (needsCompletedCounts) getCompletedCounts() else Triple(0L, 0L, 0L)
            val hasLongMangaCompletion = if (needsLongMangaCompletion) {
                hasCompletedMangaWithMinReadChapters(200)
            } else {
                false
            }
            val hasLongNovelCompletion = if (needsLongNovelCompletion) {
                hasCompletedNovelWithMinReadChapters(200)
            } else {
                false
            }

            val progressUpdates = buildList {
                nonMetaAchievements.forEach { achievement ->
                    val progress = when (achievement.type) {
                        AchievementType.QUANTITY -> calculateQuantityProgress(
                            achievement,
                            mangaChapters,
                            animeEpisodes,
                            novelChapters,
                        )
                        AchievementType.EVENT -> calculateEventProgress(
                            achievement = achievement,
                            mangaChapters = mangaChapters,
                            animeEpisodes = animeEpisodes,
                            novelChapters = novelChapters,
                            completedCounts = completedCounts,
                            hasLongMangaCompletion = hasLongMangaCompletion,
                            hasLongNovelCompletion = hasLongNovelCompletion,
                        )
                        AchievementType.DIVERSITY -> calculateDiversityProgress(
                            achievement,
                            genreCount,
                            sourceCount,
                            mangaGenreCount,
                            animeGenreCount,
                            novelGenreCount,
                            mangaSourceCount,
                            animeSourceCount,
                            novelSourceCount,
                        )
                        AchievementType.STREAK -> streak
                        AchievementType.LIBRARY -> calculateLibraryProgress(achievement, libraryCounts)
                        AchievementType.BALANCED -> calculateBalancedProgress(mangaChapters, animeEpisodes)
                        AchievementType.META -> 0
                        AchievementType.SECRET -> calculateSecretProgress(achievement)
                        AchievementType.TIME_BASED -> 0
                        AchievementType.FEATURE_BASED -> 0
                    }

                    add(buildProgress(achievement, progress))
                }
            }

            val unlockedCountExcludingMeta = progressUpdates.count { it.isUnlocked }
            val metaAchievements = allAchievements.filter { it.type == AchievementType.META }
            val metaProgressUpdates = metaAchievements.map { achievement ->
                buildProgress(achievement, unlockedCountExcludingMeta)
            }

            val allProgressUpdates = progressUpdates + metaProgressUpdates

            allProgressUpdates.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { progress ->
                    repository.insertOrUpdateProgress(progress)
                    achievementsProcessed++
                    if (progress.isUnlocked) achievementsUnlocked++
                }
            }

            val totalPoints = allProgressUpdates
                .filter { it.isUnlocked }
                .sumOf { achievementsById[it.achievementId]?.points ?: 0 }

            achievementsDatabase.userProfileQueries.updateXP(
                user_id = "default",
                total_xp = totalPoints.toLong(),
                current_xp = (totalPoints % 100).toLong(),
                level = 1, // Will be calculated by PointsManager
                xp_to_next_level = 100,
                last_updated = System.currentTimeMillis(),
            )
            achievementsDatabase.userProfileQueries.updateAchievementCounts(
                user_id = "default",
                unlocked = achievementsUnlocked.toLong(),
                total = allProgressUpdates.size.toLong(),
                last_updated = System.currentTimeMillis(),
            )

            populateActivityLog()

            val duration = System.currentTimeMillis() - startTime
            logcat(LogPriority.INFO) {
                "Achievement calculation completed in ${duration}ms. Processed: $achievementsProcessed, Unlocked: $achievementsUnlocked"
            }

            return CalculationResult(
                success = true,
                achievementsProcessed = achievementsProcessed,
                achievementsUnlocked = achievementsUnlocked,
                duration = duration,
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Achievement calculation failed: ${e.message}" }
            return CalculationResult(
                success = false,
                error = e.message ?: "Unknown error",
            )
        }
    }

    private suspend fun getTotalConsumed(): Triple<Long, Long, Long> {
        val mangaCount = mangaHandler.awaitOneOrNull {
            historyQueries.getTotalChaptersRead()
        } ?: 0L

        val animeCount = animeHandler.awaitOneOrNull {
            animehistoryQueries.getTotalEpisodesWatched()
        } ?: 0L

        val novelCount = novelHandler.awaitOneOrNull {
            novel_historyQueries.getTotalChaptersRead()
        } ?: 0L

        return Triple(mangaCount, animeCount, novelCount)
    }

    private suspend fun getLibraryCounts(): Triple<Long, Long, Long> {
        val mangaCount = mangaHandler.awaitOneOrNull { mangasQueries.getLibraryCount() } ?: 0L
        val animeCount = animeHandler.awaitOneOrNull { animesQueries.getLibraryCount() } ?: 0L
        val novelCount = novelHandler.awaitOneOrNull { novelsQueries.getLibraryCount() } ?: 0L
        return Triple(mangaCount, animeCount, novelCount)
    }

    private suspend fun getCompletedCounts(): Triple<Long, Long, Long> {
        val mangaCount = mangaHandler.awaitOneOrNull { mangasQueries.getCompletedMangaCount() } ?: 0L
        val animeCount = animeHandler.awaitOneOrNull { animesQueries.getCompletedAnimeCount() } ?: 0L
        val novelCount = novelHandler.awaitOneOrNull { novelsQueries.getCompletedNovelCount() } ?: 0L
        return Triple(mangaCount, animeCount, novelCount)
    }

    private suspend fun hasCompletedMangaWithMinReadChapters(chapterCount: Long): Boolean {
        return (
            mangaHandler.awaitOneOrNull {
                mangasQueries.hasCompletedLibraryMangaWithMinReadChapters(chapterCount)
            }
            ) ?: false
    }

    private suspend fun hasCompletedNovelWithMinReadChapters(chapterCount: Long): Boolean {
        return (
            novelHandler.awaitOneOrNull {
                novelsQueries.hasCompletedLibraryNovelWithMinReadChapters(chapterCount)
            }
            ) ?: false
    }

    private fun calculateQuantityProgress(
        achievement: Achievement,
        mangaChapters: Long,
        animeEpisodes: Long,
        novelChapters: Long,
    ): Int {
        return when (achievement.category) {
            AchievementCategory.MANGA -> mangaChapters.toInt()
            AchievementCategory.ANIME -> animeEpisodes.toInt()
            AchievementCategory.NOVEL -> novelChapters.toInt()
            AchievementCategory.BOTH -> (mangaChapters + animeEpisodes + novelChapters).toInt()
            else -> 0
        }.coerceAtLeast(0)
    }

    private fun calculateEventProgress(
        achievement: Achievement,
        mangaChapters: Long,
        animeEpisodes: Long,
        novelChapters: Long,
        completedCounts: Triple<Long, Long, Long>,
        hasLongMangaCompletion: Boolean,
        hasLongNovelCompletion: Boolean,
    ): Int {
        val (completedManga, completedAnime, completedNovel) = completedCounts
        return when (achievement.id) {
            "first_chapter" -> if (mangaChapters > 0) 1 else 0
            "first_episode" -> if (animeEpisodes > 0) 1 else 0
            "first_novel_chapter" -> if (novelChapters > 0) 1 else 0
            "complete_1_manga" -> if (completedManga > 0) 1 else 0
            "complete_1_anime" -> if (completedAnime > 0) 1 else 0
            "complete_1_novel" -> if (completedNovel > 0) 1 else 0
            "read_long_manga" -> if (hasLongMangaCompletion) 1 else 0
            "read_long_novel" -> if (hasLongNovelCompletion) 1 else 0
            else -> 0
        }
    }

    private suspend fun calculateSecretProgress(achievement: Achievement): Int {
        val threshold = achievement.threshold ?: 1
        val unlocked = when (achievement.id) {
            "secret_harem_king" -> {
                val mangaCount = mangaHandler.awaitOneOrNull {
                    mangasQueries.getLibraryGenreCount("Harem")
                } ?: 0L
                val animeCount = animeHandler.awaitOneOrNull {
                    animesQueries.getLibraryGenreCount("Harem")
                } ?: 0L
                mangaCount + animeCount >= threshold
            }
            "secret_isekai_truck" -> {
                val mangaCount = mangaHandler.awaitOneOrNull {
                    mangasQueries.getLibraryGenreCount("Isekai")
                } ?: 0L
                val animeCount = animeHandler.awaitOneOrNull {
                    animesQueries.getLibraryGenreCount("Isekai")
                } ?: 0L
                mangaCount + animeCount >= threshold
            }
            "secret_chad" -> {
                val completedCount = mangaHandler.awaitOneOrNull {
                    mangasQueries.getCompletedMangaCount()
                } ?: 0L
                val ongoingCount = mangaHandler.awaitOneOrNull {
                    mangasQueries.getLibraryCountByStatus(1L)
                } ?: 0L
                completedCount >= 10 && ongoingCount == 0L
            }
            "secret_shonen" -> {
                val mangaCount = mangaHandler.awaitOneOrNull {
                    mangasQueries.getCompletedLibraryCountByAnyGenre(2L, "Shounen", "Shonen")
                } ?: 0L
                val animeCount = animeHandler.awaitOneOrNull {
                    animesQueries.getCompletedLibraryCountByAnyGenre(2L, "Shounen", "Shonen")
                } ?: 0L
                mangaCount + animeCount >= threshold
            }
            "secret_saitama" -> {
                val mangaCount = mangaHandler.awaitOneOrNull {
                    mangasQueries.getLibraryCount()
                } ?: 0L
                val animeCount = animeHandler.awaitOneOrNull {
                    animesQueries.getLibraryCount()
                } ?: 0L
                mangaCount == 1L && animeCount == 1L
            }
            "secret_jojo" -> {
                val hasManga = mangaHandler.awaitOneOrNull {
                    mangasQueries.hasLibraryTitleLike("jojo")
                } ?: false
                val hasAnime = animeHandler.awaitOneOrNull {
                    animesQueries.hasLibraryTitleLike("jojo")
                } ?: false
                hasManga || hasAnime
            }
            else -> false
        }
        return if (unlocked) threshold else 0
    }

    private fun calculateDiversityProgress(
        achievement: Achievement,
        genreCount: Int,
        sourceCount: Int,
        mangaGenreCount: Int,
        animeGenreCount: Int,
        novelGenreCount: Int,
        mangaSourceCount: Int,
        animeSourceCount: Int,
        novelSourceCount: Int,
    ): Int {
        return when {
            achievement.id.contains("genre", ignoreCase = true) -> {
                when {
                    achievement.id.contains("manga", ignoreCase = true) -> mangaGenreCount
                    achievement.id.contains("anime", ignoreCase = true) -> animeGenreCount
                    achievement.id.contains("novel", ignoreCase = true) -> novelGenreCount
                    else -> genreCount
                }
            }
            achievement.id.contains("source", ignoreCase = true) -> {
                when {
                    achievement.id.contains("manga", ignoreCase = true) -> mangaSourceCount
                    achievement.id.contains("anime", ignoreCase = true) -> animeSourceCount
                    achievement.id.contains("novel", ignoreCase = true) -> novelSourceCount
                    else -> sourceCount
                }
            }
            else -> 0
        }
    }

    private fun calculateLibraryProgress(
        achievement: Achievement,
        libraryCounts: Triple<Long, Long, Long>,
    ): Int {
        val (mangaCount, animeCount, novelCount) = libraryCounts
        return when (achievement.category) {
            AchievementCategory.MANGA -> mangaCount.toInt()
            AchievementCategory.ANIME -> animeCount.toInt()
            AchievementCategory.NOVEL -> novelCount.toInt()
            AchievementCategory.BOTH, AchievementCategory.SECRET -> (mangaCount + animeCount + novelCount).toInt()
        }
    }

    private fun calculateBalancedProgress(
        mangaChapters: Long,
        animeEpisodes: Long,
    ): Int {
        return minOf(mangaChapters, animeEpisodes).toInt().coerceAtLeast(0)
    }

    private fun buildProgress(achievement: Achievement, progress: Int): AchievementProgress {
        val threshold = achievement.threshold ?: 1
        val isUnlocked = progress >= threshold
        return AchievementProgress(
            achievementId = achievement.id,
            progress = progress,
            maxProgress = threshold,
            isUnlocked = isUnlocked,
            unlockedAt = if (isUnlocked) System.currentTimeMillis() else null,
            lastUpdated = System.currentTimeMillis(),
        )
    }

    private suspend fun populateActivityLog() {
        logcat(LogPriority.INFO) { "Activity log population not yet implemented - streaks will build from first use" }
    }

    data class CalculationResult(
        val success: Boolean,
        val achievementsProcessed: Int = 0,
        val achievementsUnlocked: Int = 0,
        val duration: Long = 0,
        val error: String? = null,
    )
}
