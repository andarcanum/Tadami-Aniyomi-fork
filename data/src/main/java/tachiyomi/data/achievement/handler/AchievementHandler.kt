package tachiyomi.data.achievement.handler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import eu.kanade.tachiyomi.source.model.SManga

class AchievementHandler(
    private val eventBus: AchievementEventBus,
    private val repository: AchievementRepository,
    private val diversityChecker: DiversityAchievementChecker,
    private val streakChecker: StreakAchievementChecker,
    private val pointsManager: PointsManager,
    private val unlockableManager: UnlockableManager,
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
) {

    interface AchievementUnlockCallback {
        fun onAchievementUnlocked(achievement: Achievement)
    }

    var unlockCallback: AchievementUnlockCallback? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] AchievementHandler.start() called - subscribing to event bus (${eventBus.hashCode()})" }
        scope.launch {
            eventBus.events
                .catch { e ->
                    logcat(LogPriority.ERROR) { "[ACHIEVEMENTS] Error in achievement event stream: ${e.message}" }
                }
                .collect { event ->
                    try {
                        logcat(LogPriority.VERBOSE) { "[ACHIEVEMENTS] Event received: $event" }
                        processEvent(event)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) { "[ACHIEVEMENTS] Error processing achievement event: $event, ${e.message}" }
                    }
                }
        }
    }

    private suspend fun processEvent(event: AchievementEvent) {
        when (event) {
            is AchievementEvent.ChapterRead -> handleChapterRead(event)
            is AchievementEvent.EpisodeWatched -> handleEpisodeWatched(event)
            is AchievementEvent.LibraryAdded -> handleLibraryAdded(event)
            is AchievementEvent.LibraryRemoved -> handleLibraryRemoved(event)
            is AchievementEvent.MangaCompleted -> handleMangaCompleted(event)
            is AchievementEvent.AnimeCompleted -> handleAnimeCompleted(event)
        }

        // Check secret achievements for all events
        checkSecretAchievements(event)
    }

    private suspend fun handleChapterRead(event: AchievementEvent.ChapterRead) {
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] handleChapterRead: mangaId=${event.mangaId}, chapter=${event.chapterNumber}" }
        streakChecker.logChapterRead()

        val achievements = getAchievementsForCategory(AchievementCategory.MANGA)
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Found ${achievements.size} MANGA achievements (incl BOTH)" }

        val relevantAchievements = achievements.filter {
            it.type == AchievementType.QUANTITY ||
                it.type == AchievementType.EVENT ||
                it.type == AchievementType.STREAK ||
                it.type == AchievementType.DIVERSITY ||
                it.type == AchievementType.LIBRARY ||
                it.type == AchievementType.META
        }

        relevantAchievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleEpisodeWatched(event: AchievementEvent.EpisodeWatched) {
        streakChecker.logEpisodeWatched()

        val achievements = getAchievementsForCategory(AchievementCategory.ANIME)
        val relevantAchievements = achievements.filter {
            it.type == AchievementType.QUANTITY ||
                it.type == AchievementType.EVENT ||
                it.type == AchievementType.STREAK ||
                it.type == AchievementType.DIVERSITY ||
                it.type == AchievementType.LIBRARY ||
                it.type == AchievementType.META
        }

        relevantAchievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleLibraryAdded(event: AchievementEvent.LibraryAdded) {
        val achievements = getAchievementsForCategory(event.type)
            .filter {
                it.type == AchievementType.EVENT ||
                    it.type == AchievementType.QUANTITY ||
                    it.type == AchievementType.DIVERSITY ||
                    it.type == AchievementType.LIBRARY ||
                    it.type == AchievementType.META
            }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleLibraryRemoved(event: AchievementEvent.LibraryRemoved) {
        // no-op for now
    }

    private suspend fun handleMangaCompleted(event: AchievementEvent.MangaCompleted) {
        val achievements = getAchievementsForCategory(AchievementCategory.MANGA)
            .filter { it.type == AchievementType.EVENT }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleAnimeCompleted(event: AchievementEvent.AnimeCompleted) {
        val achievements = getAchievementsForCategory(AchievementCategory.ANIME)
            .filter { it.type == AchievementType.EVENT }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun checkAndUpdateProgress(
        achievement: Achievement,
        event: AchievementEvent,
    ) {
        val currentProgress = repository.getProgress(achievement.id).first()
        val newProgress = calculateProgress(achievement, event, currentProgress)
        applyProgressUpdate(achievement, currentProgress, newProgress)
    }

    private suspend fun applyProgressUpdate(
        achievement: Achievement,
        currentProgress: AchievementProgress?,
        newProgress: Int,
    ) {
        val threshold = achievement.threshold ?: 1
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Checking ${achievement.id}: current=$currentProgress, new=$newProgress, threshold=$threshold" }

        if (currentProgress == null) {
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Creating new progress for ${achievement.id}" }
            repository.insertOrUpdateProgress(
                AchievementProgress(
                    achievementId = achievement.id,
                    progress = newProgress,
                    maxProgress = threshold,
                    isUnlocked = newProgress >= threshold,
                    unlockedAt = if (newProgress >= threshold) System.currentTimeMillis() else null,
                    lastUpdated = System.currentTimeMillis(),
                ),
            )

            if (newProgress >= threshold) {
                logcat(LogPriority.INFO) { "[ACHIEVEMENTS] UNLOCKING ${achievement.id} on first check!" }
                onAchievementUnlocked(achievement)
            }
        } else if (!currentProgress.isUnlocked) {
            val shouldUnlock = newProgress >= threshold
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Updating progress for ${achievement.id}: shouldUnlock=$shouldUnlock" }
            repository.insertOrUpdateProgress(
                currentProgress.copy(
                    progress = newProgress,
                    isUnlocked = shouldUnlock,
                    unlockedAt = if (shouldUnlock) System.currentTimeMillis() else currentProgress.unlockedAt,
                    lastUpdated = System.currentTimeMillis(),
                ),
            )

            if (shouldUnlock) {
                logcat(LogPriority.INFO) { "[ACHIEVEMENTS] UNLOCKING ${achievement.id}!" }
                onAchievementUnlocked(achievement)
            }
        } else {
            logcat(LogPriority.VERBOSE) { "[ACHIEVEMENTS] ${achievement.id} already unlocked, skipping" }
        }
    }

    private suspend fun calculateProgress(
        achievement: Achievement,
        event: AchievementEvent,
        currentProgress: AchievementProgress?,
    ): Int {
        return when (achievement.type) {
            AchievementType.EVENT -> {
                if (currentProgress != null && currentProgress.progress > 0) {
                    currentProgress.progress
                } else if (isEventMatch(achievement.id, event)) {
                    1
                } else {
                    0
                }
            }
            AchievementType.QUANTITY -> {
                getTotalReadForCategory(achievement.category)
            }
            AchievementType.DIVERSITY -> {
                when {
                    achievement.id.contains("genre", ignoreCase = true) -> {
                        when {
                            achievement.id.contains("manga", ignoreCase = true) -> diversityChecker.getMangaGenreDiversity()
                            achievement.id.contains("anime", ignoreCase = true) -> diversityChecker.getAnimeGenreDiversity()
                            else -> diversityChecker.getGenreDiversity()
                        }
                    }
                    achievement.id.contains("source", ignoreCase = true) -> {
                        when {
                            achievement.id.contains("manga", ignoreCase = true) -> diversityChecker.getMangaSourceDiversity()
                            achievement.id.contains("anime", ignoreCase = true) -> diversityChecker.getAnimeSourceDiversity()
                            else -> diversityChecker.getSourceDiversity()
                        }
                    }
                    else -> currentProgress?.progress ?: 0
                }
            }
            AchievementType.STREAK -> {
                streakChecker.getCurrentStreak()
            }
            AchievementType.LIBRARY -> {
                getLibraryCountForCategory(achievement.category)
            }
            AchievementType.META -> {
                getUnlockedCountExcludingMeta()
            }
            AchievementType.BALANCED -> {
                val mangaCount = mangaHandler.awaitOneOrNull { historyQueries.getTotalChaptersRead() } ?: 0L
                val animeCount = animeHandler.awaitOneOrNull { animehistoryQueries.getTotalEpisodesWatched() } ?: 0L
                minOf(mangaCount, animeCount).toInt().coerceAtLeast(0)
            }
            AchievementType.SECRET -> {
                // Secret achievements are handled by checkSecretAchievements()
                // Return current progress from DB
                currentProgress?.progress ?: 0
            }
        }
    }

    private fun isEventMatch(achievementId: String, event: AchievementEvent): Boolean {
        val id = achievementId.lowercase()
        return when (event) {
            is AchievementEvent.ChapterRead ->
                id.contains("chapter") || id.contains("read")
            is AchievementEvent.EpisodeWatched ->
                id.contains("episode") || id.contains("watch")
            is AchievementEvent.LibraryAdded ->
                id.contains("library") || id.contains("favorite") || id.contains("collect") || id.contains("added")
            is AchievementEvent.LibraryRemoved -> false
            is AchievementEvent.MangaCompleted ->
                id.contains("manga_complete") || id.contains("completed_manga") || id.contains("manga_completed")
            is AchievementEvent.AnimeCompleted ->
                id.contains("anime_complete") || id.contains("completed_anime") || id.contains("anime_completed")
        }
    }

    private suspend fun getAchievementsForCategory(category: AchievementCategory): List<Achievement> {
        val primary = repository.getByCategory(category).first()
        if (category == AchievementCategory.BOTH) return primary
        val both = repository.getByCategory(AchievementCategory.BOTH).first()
        return (primary + both).distinctBy { it.id }
    }

    private suspend fun getTotalReadForCategory(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> {
                mangaHandler.awaitOneOrNull { historyQueries.getTotalChaptersRead() }?.toInt() ?: 0
            }
            AchievementCategory.ANIME -> {
                animeHandler.awaitOneOrNull { animehistoryQueries.getTotalEpisodesWatched() }?.toInt() ?: 0
            }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                val mangaCount = mangaHandler.awaitOneOrNull { historyQueries.getTotalChaptersRead() } ?: 0L
                val animeCount = animeHandler.awaitOneOrNull { animehistoryQueries.getTotalEpisodesWatched() } ?: 0L
                (mangaCount + animeCount).toInt()
            }
        }
    }

    private suspend fun getLibraryCountForCategory(category: AchievementCategory): Int {
        val mangaCount = mangaHandler.awaitOneOrNull { mangasQueries.getLibraryCount() } ?: 0L
        val animeCount = animeHandler.awaitOneOrNull { animesQueries.getLibraryCount() } ?: 0L
        return when (category) {
            AchievementCategory.MANGA -> mangaCount.toInt()
            AchievementCategory.ANIME -> animeCount.toInt()
            AchievementCategory.BOTH, AchievementCategory.SECRET -> (mangaCount + animeCount).toInt()
        }
    }

    private suspend fun getUnlockedCountExcludingMeta(): Int {
        val metaIds = repository.getAll().first()
            .filter { it.type == AchievementType.META }
            .map { it.id }
            .toSet()
        return repository.getAllProgress().first()
            .count { it.isUnlocked && it.achievementId !in metaIds }
    }

    private suspend fun updateMetaAchievements() {
        val metaAchievements = repository.getAll().first()
            .filter { it.type == AchievementType.META }
        if (metaAchievements.isEmpty()) return

        val unlockedCount = getUnlockedCountExcludingMeta()
        metaAchievements.forEach { achievement ->
            val currentProgress = repository.getProgress(achievement.id).first()
            applyProgressUpdate(achievement, currentProgress, unlockedCount)
        }
    }

    private fun onAchievementUnlocked(achievement: Achievement) {
        logcat(LogPriority.INFO) { "Achievement unlocked: ${achievement.title} (+${achievement.points} points)" }

        scope.launch {
            try {
                pointsManager.addPoints(achievement.points)
                pointsManager.incrementUnlocked()
                updateMetaAchievements()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to add points for achievement: ${achievement.title}, ${e.message}" }
            }

            try {
                unlockableManager.unlockAchievementRewards(achievement)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to unlock rewards for achievement: ${achievement.title}, ${e.message}" }
            }
        }

        unlockCallback?.onAchievementUnlocked(achievement)
    }

    // ==================== SECRET ACHIEVEMENT CHECKERS ====================

    /**
     * Main entry point for checking secret achievements.
     * Called for every achievement event.
     */
    private suspend fun checkSecretAchievements(event: AchievementEvent) {
        val secretAchievements = repository.getByCategory(AchievementCategory.SECRET).first()

        secretAchievements.forEach { achievement ->
            val currentProgress = repository.getProgress(achievement.id).first()
            if (currentProgress?.isUnlocked == true) return@forEach

            val shouldUnlock = when (achievement.id) {
                "secret_crybaby" -> checkCrybaby(event)
                "secret_harem_king" -> checkHaremKing()
                "secret_isekai_truck" -> checkIsekaiTruck()
                "secret_chad" -> checkChad()
                "secret_shonen" -> checkShonen()
                "secret_deku" -> checkDeku(event)
                "secret_eren" -> checkEren(event)
                "secret_lelouch" -> checkLelouch(event)
                "secret_saitama" -> checkSaitama()
                "secret_jojo" -> checkJojo()
                "secret_onepiece" -> checkOnePiece()
                "secret_goku" -> checkGoku()
                else -> false
            }

            if (shouldUnlock) {
                applyProgressUpdate(achievement, currentProgress, achievement.threshold ?: 1)
            }
        }
    }

    /**
     * secret_crybaby: Trigger when completing manga/anime with "Tragedy" or "Drama" genre
     */
    private suspend fun checkCrybaby(event: AchievementEvent): Boolean {
        return when (event) {
            is AchievementEvent.MangaCompleted -> {
                val manga = mangaRepository.getMangaById(event.mangaId)
                manga.hasGenre("Tragedy") || manga.hasGenre("Drama")
            }
            is AchievementEvent.AnimeCompleted -> {
                val anime = animeRepository.getAnimeById(event.animeId)
                anime.hasGenre("Tragedy") || anime.hasGenre("Drama")
            }
            else -> false
        }
    }

    /**
     * secret_harem_king: Library has 20+ titles with "Harem" genre
     */
    private suspend fun checkHaremKing(): Boolean {
        val mangaLibrary = mangaRepository.getMangaFavorites()
        val animeLibrary = animeRepository.getAnimeFavorites()

        val mangaHaremCount = mangaLibrary.count { it.hasGenre("Harem") }
        val animeHaremCount = animeLibrary.count { it.hasGenre("Harem") }

        return (mangaHaremCount + animeHaremCount) >= 20
    }

    /**
     * secret_isekai_truck: Library has 20+ titles with "Isekai" genre
     */
    private suspend fun checkIsekaiTruck(): Boolean {
        val mangaLibrary = mangaRepository.getMangaFavorites()
        val animeLibrary = animeRepository.getAnimeFavorites()

        val mangaIsekaiCount = mangaLibrary.count { it.hasGenre("Isekai") }
        val animeIsekaiCount = animeLibrary.count { it.hasGenre("Isekai") }

        return (mangaIsekaiCount + animeIsekaiCount) >= 20
    }

    /**
     * secret_chad: 10+ completed manga AND 0 ongoing manga (only completed)
     */
    private suspend fun checkChad(): Boolean {
        val mangaLibrary = mangaRepository.getMangaFavorites()

        val completedCount = mangaLibrary.count { it.status == SManga.COMPLETED.toLong() }
        val ongoingCount = mangaLibrary.count { it.status == SManga.ONGOING.toLong() }

        return completedCount >= 10 && ongoingCount == 0
    }

    /**
     * secret_shonen: 10+ completed titles with "Shounen" or "Shonen" genre
     */
    private suspend fun checkShonen(): Boolean {
        val mangaLibrary = mangaRepository.getMangaFavorites()
        val animeLibrary = animeRepository.getAnimeFavorites()

        val completedMangaShonen = mangaLibrary.count {
            it.status == SManga.COMPLETED.toLong() &&
                (it.hasGenre("Shounen") || it.hasGenre("Shonen"))
        }
        val completedAnimeShonen = animeLibrary.count {
            it.status == SManga.COMPLETED.toLong() &&
                (it.hasGenre("Shounen") || it.hasGenre("Shonen"))
        }

        return (completedMangaShonen + completedAnimeShonen) >= 10
    }

    /**
     * secret_deku: Complete title with "Super Power" genre
     */
    private suspend fun checkDeku(event: AchievementEvent): Boolean {
        return when (event) {
            is AchievementEvent.MangaCompleted -> {
                val manga = mangaRepository.getMangaById(event.mangaId)
                manga.hasGenre("Super Power")
            }
            is AchievementEvent.AnimeCompleted -> {
                val anime = animeRepository.getAnimeById(event.animeId)
                anime.hasGenre("Super Power")
            }
            else -> false
        }
    }

    /**
     * secret_eren: Complete title with "Military" genre
     */
    private suspend fun checkEren(event: AchievementEvent): Boolean {
        return when (event) {
            is AchievementEvent.MangaCompleted -> {
                val manga = mangaRepository.getMangaById(event.mangaId)
                manga.hasGenre("Military")
            }
            is AchievementEvent.AnimeCompleted -> {
                val anime = animeRepository.getAnimeById(event.animeId)
                anime.hasGenre("Military")
            }
            else -> false
        }
    }

    /**
     * secret_lelouch: Complete title with "Psychological" genre
     */
    private suspend fun checkLelouch(event: AchievementEvent): Boolean {
        return when (event) {
            is AchievementEvent.MangaCompleted -> {
                val manga = mangaRepository.getMangaById(event.mangaId)
                manga.hasGenre("Psychological")
            }
            is AchievementEvent.AnimeCompleted -> {
                val anime = animeRepository.getAnimeById(event.animeId)
                anime.hasGenre("Psychological")
            }
            else -> false
        }
    }

    /**
     * secret_saitama: Library has exactly 1 anime AND 1 manga (total 2 items)
     */
    private suspend fun checkSaitama(): Boolean {
        val mangaCount = mangaRepository.getMangaFavorites().size
        val animeCount = animeRepository.getAnimeFavorites().size

        return mangaCount == 1 && animeCount == 1
    }

    /**
     * secret_jojo: Library contains "Jojo" or "JoJo" or "Jojo's Bizarre Adventure" in title
     */
    private suspend fun checkJojo(): Boolean {
        val mangaLibrary = mangaRepository.getMangaFavorites()
        val animeLibrary = animeRepository.getAnimeFavorites()

        val jojoTitles = listOf("Jojo", "JoJo", "Jojo's Bizarre Adventure", "JoJo's Bizarre Adventure")

        val hasJojoManga = mangaLibrary.any { manga ->
            jojoTitles.any { title -> manga.title.contains(title, ignoreCase = true) }
        }
        val hasJojoAnime = animeLibrary.any { anime ->
            jojoTitles.any { title -> anime.title.contains(title, ignoreCase = true) }
        }

        return hasJojoManga || hasJojoAnime
    }

    /**
     * secret_onepiece: Total chapters read >= 1000
     */
    private suspend fun checkOnePiece(): Boolean {
        val totalChapters = mangaHandler.awaitOneOrNull {
            historyQueries.getTotalChaptersRead()
        } ?: 0L
        return totalChapters >= 1000
    }

    /**
     * secret_goku: Total achievement points >= 9000
     */
    private suspend fun checkGoku(): Boolean {
        val userPoints = pointsManager.getCurrentPoints()
        return userPoints.totalPoints >= 9000
    }

    // ==================== HELPER EXTENSIONS ====================

    private fun Manga.hasGenre(genreName: String): Boolean {
        return genre?.any { it.equals(genreName, ignoreCase = true) } == true
    }

    private fun Anime.hasGenre(genreName: String): Boolean {
        return genre?.any { it.equals(genreName, ignoreCase = true) } == true
    }
}
