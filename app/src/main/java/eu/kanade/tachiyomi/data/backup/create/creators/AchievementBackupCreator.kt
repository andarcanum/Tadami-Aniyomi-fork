package eu.kanade.tachiyomi.data.backup.create.creators

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import eu.kanade.tachiyomi.data.backup.models.BackupAchievement
import eu.kanade.tachiyomi.data.backup.models.BackupDayActivity
import eu.kanade.tachiyomi.data.backup.models.BackupStats
import eu.kanade.tachiyomi.data.backup.models.BackupUserProfile
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.stats.StatsCalculations
import eu.kanade.tachiyomi.ui.stats.WatchProgress
import kotlinx.coroutines.flow.first
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.history.manga.interactor.GetTotalReadDuration
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.source.local.entries.anime.isLocal
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.animesource.model.SAnime as AnimeStatus

class AchievementBackupCreator(
    private val achievementRepository: tachiyomi.domain.achievement.repository.AchievementRepository = Injekt.get(),
    private val activityDataRepository: tachiyomi.domain.achievement.repository.ActivityDataRepository = Injekt.get(),
    private val achievementsDatabase: tachiyomi.data.achievement.database.AchievementsDatabase = Injekt.get(),
    private val userProfileManager: UserProfileManager = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getTotalReadDuration: GetTotalReadDuration = Injekt.get(),
    private val getMangaTracks: GetMangaTracks = Injekt.get(),
    private val getAnimeTracks: GetAnimeTracks = Injekt.get(),
    private val mangaDownloadManager: MangaDownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val preferences: LibraryPreferences = Injekt.get(),
) {

    /**
     * Backup all achievement data including:
     * - Achievements with progress
     * - User profile
     * - Activity log (last 365 days)
     * - Full statistics
     */
    suspend operator fun invoke(options: eu.kanade.tachiyomi.data.backup.create.BackupOptions): AchievementBackupData {
        val achievements = if (options.achievements) backupAchievements() else emptyList()
        val userProfile = if (options.achievements) backupUserProfile() else null
        val activityLog = if (options.achievements) backupActivityLog() else emptyList()
        val stats = if (options.stats) backupStats() else null

        return AchievementBackupData(
            achievements = achievements,
            userProfile = userProfile,
            activityLog = activityLog,
            stats = stats,
        )
    }

    /**
     * Backup all achievements with their progress
     */
    private suspend fun backupAchievements(): List<BackupAchievement> {
        return try {
            val achievements = achievementRepository.getAll().first()
            val progressList = achievementRepository.getAllProgress().first()

            val progressMap = progressList.associateBy { it.achievementId }

            achievements.map { achievement ->
                val progress = progressMap[achievement.id]
                BackupAchievement.fromAchievement(achievement, progress)
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error backing up achievements" }
            emptyList()
        }
    }

    /**
     * Backup user profile
     */
    private suspend fun backupUserProfile(): BackupUserProfile? {
        return try {
            val profile = userProfileManager.getCurrentProfile()
            BackupUserProfile.fromUserProfile(profile)
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error backing up user profile" }
            null
        }
    }

    /**
     * Backup activity log for the last 365 days with full metrics
     * Uses direct database access to get detailed data (chapters, episodes, app opens, etc.)
     * instead of simplified DayActivity model
     */
    private suspend fun backupActivityLog(): List<BackupDayActivity> {
        return try {
            val today = java.time.LocalDate.now()
            val startDate = today.minusDays(364) // 365 days including today
            val startDateStr = startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            val endDateStr = today.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

            // Get raw activity log records from database with all metrics
            val records = achievementsDatabase.activityLogQueries
                .getActivityForDateRange(startDateStr, endDateStr)
                .executeAsList()

            logcat { "[BACKUP] Backing up ${records.size} activity log records" }

            records.map { record ->
                BackupDayActivity.fromDatabaseRecord(
                    date = java.time.LocalDate.parse(record.date),
                    level = record.level.toInt(),
                    type = tachiyomi.domain.achievement.model.ActivityType.entries.getOrElse(record.type.toInt()) {
                        tachiyomi.domain.achievement.model.ActivityType.APP_OPEN
                    },
                    chaptersRead = record.chapters_read.toInt(),
                    episodesWatched = record.episodes_watched.toInt(),
                    appOpens = record.app_opens.toInt(),
                    achievementsUnlocked = record.achievements_unlocked.toInt(),
                    durationMs = record.duration_ms,
                )
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error backing up activity log" }
            emptyList()
        }
    }

    /**
     * Backup full statistics from database
     */
    private suspend fun backupStats(): BackupStats? {
        return try {
            val mangaStats = backupMangaStats()
            val animeStats = backupAnimeStats()

            BackupStats(
                // Manga stats
                mangaLibraryCount = mangaStats.libraryCount,
                mangaCompletedCount = mangaStats.completedCount,
                mangaTotalReadDuration = mangaStats.totalReadDuration,
                mangaStartedCount = mangaStats.startedCount,
                mangaLocalCount = mangaStats.localCount,
                chaptersTotalCount = mangaStats.totalChapters,
                chaptersReadCount = mangaStats.readChapters,
                chaptersDownloadedCount = mangaStats.downloadedChapters,
                mangaGlobalUpdateCount = mangaStats.globalUpdateCount,

                // Anime stats
                animeLibraryCount = animeStats.libraryCount,
                animeCompletedCount = animeStats.completedCount,
                animeTotalSeenDuration = animeStats.totalSeenDuration,
                animeStartedCount = animeStats.startedCount,
                animeLocalCount = animeStats.localCount,
                episodesTotalCount = animeStats.totalEpisodes,
                episodesWatchedCount = animeStats.watchedEpisodes,
                episodesDownloadedCount = animeStats.downloadedEpisodes,
                animeGlobalUpdateCount = animeStats.globalUpdateCount,

                // Tracker stats (combined from manga and anime)
                trackedTitleCount = mangaStats.trackedCount + animeStats.trackedCount,
                meanScore = calculateCombinedMeanScore(mangaStats.meanScore, animeStats.meanScore),
                trackerCount = trackerManager.loggedInTrackers().size,
            )
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error backing up stats" }
            null
        }
    }

    private data class MangaStatsData(
        val libraryCount: Int = 0,
        val completedCount: Int = 0,
        val totalReadDuration: Long = 0L,
        val startedCount: Int = 0,
        val localCount: Int = 0,
        val totalChapters: Int = 0,
        val readChapters: Int = 0,
        val downloadedChapters: Int = 0,
        val globalUpdateCount: Int = 0,
        val trackedCount: Int = 0,
        val meanScore: Double = 0.0,
    )

    private data class AnimeStatsData(
        val libraryCount: Int = 0,
        val completedCount: Int = 0,
        val totalSeenDuration: Long = 0L,
        val startedCount: Int = 0,
        val localCount: Int = 0,
        val totalEpisodes: Int = 0,
        val watchedEpisodes: Int = 0,
        val downloadedEpisodes: Int = 0,
        val globalUpdateCount: Int = 0,
        val trackedCount: Int = 0,
        val meanScore: Double = 0.0,
    )

    private suspend fun backupMangaStats(): MangaStatsData {
        return try {
            val libraryManga = getLibraryManga.await()
            val distinctManga = libraryManga.fastDistinctBy { it.id }

            // Get manga tracks
            val loggedInTrackers = trackerManager.loggedInTrackers().filter { it is MangaTracker }
            val loggedInTrackerIds = loggedInTrackers.map { it.id }.toHashSet()

            val mangaTrackMap = distinctManga.associate { manga ->
                val tracks = getMangaTracks.await(manga.id)
                    .fastFilter { it.trackerId in loggedInTrackerIds }
                manga.id to tracks
            }

            val meanScore = StatsCalculations.meanTitleScore(
                mangaTrackMap.values.map { tracks ->
                    tracks.mapNotNull { track ->
                        track.takeIf { it.score > 0.0 }?.let {
                            val service = trackerManager.get(track.trackerId) as? MangaTracker
                            service?.get10PointScore(track) ?: track.score
                        }
                    }
                },
            ).takeUnless { it.isNaN() } ?: 0.0

            MangaStatsData(
                libraryCount = distinctManga.size,
                completedCount = distinctManga.count {
                    StatsCalculations.isCompletedByUserConsumption(
                        sourceStatus = it.manga.status.toInt(),
                        customStatus = it.manga.customStatus?.toInt(),
                        completedStatus = SManga.COMPLETED,
                        terminalFallbackStatuses = setOf(
                            SManga.PUBLISHING_FINISHED,
                            SManga.CANCELLED,
                            SManga.ON_HIATUS,
                        ),
                        consumedCount = it.readCount,
                        totalCount = it.totalChapters,
                    )
                },
                totalReadDuration = getTotalReadDuration.await(),
                startedCount = distinctManga.count { it.hasStarted },
                localCount = distinctManga.count { it.manga.isLocal() },
                totalChapters = distinctManga.sumOf { it.totalChapters }.toInt(),
                readChapters = distinctManga.sumOf { it.readCount }.toInt(),
                downloadedChapters = mangaDownloadManager.getDownloadCount(),
                globalUpdateCount = getMangaGlobalUpdateItemCount(libraryManga),
                trackedCount = mangaTrackMap.count { it.value.isNotEmpty() },
                meanScore = meanScore,
            )
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error backing up manga stats" }
            MangaStatsData()
        }
    }

    private suspend fun backupAnimeStats(): AnimeStatsData {
        return try {
            val libraryAnime = getLibraryAnime.await()
            val distinctAnime = libraryAnime.fastDistinctBy { it.id }

            // Get anime tracks
            val loggedInTrackers = trackerManager.loggedInTrackers().filter { it is AnimeTracker }
            val loggedInTrackerIds = loggedInTrackers.map { it.id }.toHashSet()

            val animeTrackMap = distinctAnime.associate { anime ->
                val tracks = getAnimeTracks.await(anime.id)
                    .fastFilter { it.trackerId in loggedInTrackerIds }
                anime.id to tracks
            }

            val meanScore = StatsCalculations.meanTitleScore(
                animeTrackMap.values.map { tracks ->
                    tracks.mapNotNull { track ->
                        track.takeIf { it.score > 0.0 }?.let {
                            val service = trackerManager.get(track.trackerId) as? AnimeTracker
                            service?.get10PointScore(track) ?: track.score
                        }
                    }
                },
            ).takeUnless { it.isNaN() } ?: 0.0

            val totalWatchTime = StatsCalculations.watchDurationMillis(
                distinctAnime.flatMap { libraryAnime ->
                    getEpisodesByAnimeId.await(libraryAnime.anime.id).map { episode ->
                        WatchProgress(
                            seen = episode.seen,
                            lastSeenMillis = episode.lastSecondSeen,
                            totalMillis = episode.totalSeconds,
                        )
                    }
                },
            )

            AnimeStatsData(
                libraryCount = distinctAnime.size,
                completedCount = distinctAnime.count {
                    StatsCalculations.isCompletedByUserConsumption(
                        sourceStatus = it.anime.status.toInt(),
                        customStatus = it.anime.customStatus?.toInt(),
                        completedStatus = AnimeStatus.COMPLETED,
                        terminalFallbackStatuses = setOf(
                            AnimeStatus.PUBLISHING_FINISHED,
                            AnimeStatus.CANCELLED,
                            AnimeStatus.ON_HIATUS,
                        ),
                        consumedCount = it.seenCount,
                        totalCount = it.totalCount,
                    )
                },
                totalSeenDuration = totalWatchTime,
                startedCount = distinctAnime.count { it.hasStarted },
                localCount = distinctAnime.count { it.anime.isLocal() },
                totalEpisodes = distinctAnime.sumOf { it.totalCount }.toInt(),
                watchedEpisodes = distinctAnime.sumOf { it.seenCount }.toInt(),
                downloadedEpisodes = animeDownloadManager.getDownloadCount(),
                globalUpdateCount = getAnimeGlobalUpdateItemCount(libraryAnime),
                trackedCount = animeTrackMap.count { it.value.isNotEmpty() },
                meanScore = meanScore,
            )
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error backing up anime stats" }
            AnimeStatsData()
        }
    }

    private fun getMangaGlobalUpdateItemCount(libraryManga: List<LibraryManga>): Int {
        val includedCategories = preferences.mangaUpdateCategories().get().map { it.toLong() }
        val includedManga = if (includedCategories.isNotEmpty()) {
            libraryManga.filter { it.category in includedCategories }
        } else {
            libraryManga
        }

        val excludedCategories = preferences.mangaUpdateCategoriesExclude().get().map { it.toLong() }
        val excludedMangaIds = if (excludedCategories.isNotEmpty()) {
            libraryManga.mapNotNull { manga ->
                manga.id.takeIf { manga.category in excludedCategories }
            }
        } else {
            emptyList()
        }

        val updateRestrictions = preferences.autoUpdateItemRestrictions().get()
        return includedManga
            .filterNot { it.manga.id in excludedMangaIds }
            .distinctBy { it.manga.id }
            .count {
                !(
                    (ENTRY_NON_COMPLETED in updateRestrictions && it.manga.status.toInt() == SManga.COMPLETED) ||
                        (ENTRY_HAS_UNVIEWED in updateRestrictions && it.unreadCount != 0L) ||
                        (ENTRY_NON_VIEWED in updateRestrictions && it.totalChapters > 0 && !it.hasStarted)
                    )
            }
    }

    private fun getAnimeGlobalUpdateItemCount(libraryAnime: List<LibraryAnime>): Int {
        val includedCategories = preferences.animeUpdateCategories().get().map { it.toLong() }
        val includedAnime = if (includedCategories.isNotEmpty()) {
            libraryAnime.filter { it.category in includedCategories }
        } else {
            libraryAnime
        }

        val excludedCategories = preferences.animeUpdateCategoriesExclude().get().map { it.toLong() }
        val excludedAnimeIds = if (excludedCategories.isNotEmpty()) {
            libraryAnime.mapNotNull { anime ->
                anime.id.takeIf { anime.category in excludedCategories }
            }
        } else {
            emptyList()
        }

        val updateRestrictions = preferences.autoUpdateItemRestrictions().get()
        return includedAnime
            .filterNot { it.anime.id in excludedAnimeIds }
            .distinctBy { it.anime.id }
            .count {
                !(
                    (ENTRY_NON_COMPLETED in updateRestrictions && it.anime.status.toInt() == AnimeStatus.COMPLETED) ||
                        (ENTRY_HAS_UNVIEWED in updateRestrictions && it.unseenCount != 0L) ||
                        (ENTRY_NON_VIEWED in updateRestrictions && it.totalCount > 0 && !it.hasStarted)
                    )
            }
    }

    private fun calculateCombinedMeanScore(mangaMean: Double, animeMean: Double): Double {
        return when {
            mangaMean > 0 && animeMean > 0 -> (mangaMean + animeMean) / 2
            mangaMean > 0 -> mangaMean
            animeMean > 0 -> animeMean
            else -> 0.0
        }
    }
}

/**
 * Container for all achievement backup data
 */
data class AchievementBackupData(
    val achievements: List<BackupAchievement>,
    val userProfile: BackupUserProfile?,
    val activityLog: List<BackupDayActivity>,
    val stats: BackupStats?,
)
