package tachiyomi.data.achievement.handler

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.FeatureBasedAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.achievement.handler.checkers.TimeBasedAchievementChecker
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository

class AchievementHandler(
    private val eventBus: AchievementEventBus,
    private val repository: AchievementRepository,
    private val diversityChecker: DiversityAchievementChecker,
    private val streakChecker: StreakAchievementChecker,
    private val timeBasedChecker: TimeBasedAchievementChecker,
    private val featureBasedChecker: FeatureBasedAchievementChecker,
    private val featureCollector: FeatureUsageCollector,
    private val pointsManager: PointsManager,
    private val unlockableManager: UnlockableManager,
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val novelHandler: NovelDatabaseHandler,
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
    private val userProfileManager: UserProfileManager,
    private val activityDataRepository: ActivityDataRepository,
) {

    interface AchievementUnlockCallback {
        fun onAchievementUnlocked(achievement: Achievement)
    }

    var unlockCallback: AchievementUnlockCallback? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val librarySecretIds = setOf(
        "secret_harem_king",
        "secret_isekai_truck",
        "secret_chad",
        "secret_saitama",
        "secret_jojo",
    )
    private val completionSecretIds = setOf(
        "secret_crybaby",
        "secret_shonen",
        "secret_deku",
        "secret_eren",
        "secret_lelouch",
    )

    fun start() {
        logcat(LogPriority.INFO) {
            "[ACHIEVEMENTS] AchievementHandler.start() called - subscribing to event bus (${eventBus.hashCode()})"
        }
        scope.launch {
            try {
                sanitizeCrossCategoryFirstAchievements()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "[ACHIEVEMENTS] Failed to sanitize first achievements: ${e.message}"
                }
            }

            eventBus.events.collect { event ->
                try {
                    logcat(LogPriority.VERBOSE) { "[ACHIEVEMENTS] Event received: $event" }
                    processEvent(event)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) {
                        "[ACHIEVEMENTS] Error processing achievement event: $event, ${e.message}"
                    }
                }
            }
        }
    }

    private suspend fun sanitizeCrossCategoryFirstAchievements() {
        val mangaRead = (mangaHandler.awaitOneOrNull { db -> db.historyQueries.getTotalChaptersRead() } ?: 0L) > 0L
        val animeWatched =
            (animeHandler.awaitOneOrNull { db -> db.animehistoryQueries.getTotalEpisodesWatched() } ?: 0L) > 0L
        val novelRead =
            (novelHandler.awaitOneOrNull { db -> db.novel_historyQueries.getTotalChaptersRead() } ?: 0L) > 0L

        var corrected = false
        corrected = sanitizeFirstAchievement("first_chapter", mangaRead) || corrected
        corrected = sanitizeFirstAchievement("first_episode", animeWatched) || corrected
        corrected = sanitizeFirstAchievement("first_novel_chapter", novelRead) || corrected

        if (corrected) {
            updateMetaAchievements()
        }
    }

    private suspend fun sanitizeFirstAchievement(
        achievementId: String,
        hasRelevantHistory: Boolean,
    ): Boolean {
        val progress = repository.getProgress(achievementId).first() ?: return false
        if (!progress.isUnlocked || hasRelevantHistory) return false

        repository.insertOrUpdateProgress(
            progress.copy(
                progress = 0,
                isUnlocked = false,
                unlockedAt = null,
                lastUpdated = System.currentTimeMillis(),
            ),
        )
        logcat(LogPriority.WARN) {
            "[ACHIEVEMENTS] Corrected invalid unlock for $achievementId (no relevant history found)"
        }
        return true
    }

    private suspend fun processEvent(event: AchievementEvent) {
        when (event) {
            is AchievementEvent.ChapterRead -> handleChapterRead(event)
            is AchievementEvent.NovelChapterRead -> handleNovelChapterRead(event)
            is AchievementEvent.EpisodeWatched -> handleEpisodeWatched(event)
            is AchievementEvent.LibraryAdded -> handleLibraryAdded(event)
            is AchievementEvent.LibraryRemoved -> handleLibraryRemoved(event)
            is AchievementEvent.MangaCompleted -> handleMangaCompleted(event)
            is AchievementEvent.AnimeCompleted -> handleAnimeCompleted(event)
            is AchievementEvent.NovelCompleted -> handleNovelCompleted(event)
            is AchievementEvent.SessionEnd -> handleSessionEnd(event)
            is AchievementEvent.AppStart -> handleAppStart(event)
            is AchievementEvent.FeatureUsed -> handleFeatureUsed(event)
        }
    }

    private suspend fun handleChapterRead(event: AchievementEvent.ChapterRead) {
        logcat(LogPriority.INFO) {
            "[ACHIEVEMENTS] handleChapterRead: mangaId=${event.mangaId}, chapter=${event.chapterNumber}"
        }
        streakChecker.logChapterRead()

        val achievements = getAchievementsForCategory(AchievementCategory.MANGA)
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Found ${achievements.size} MANGA achievements (incl BOTH)" }

        val relevantAchievements = achievements.filter {
            it.type == AchievementType.QUANTITY ||
                it.type == AchievementType.EVENT ||
                it.type == AchievementType.STREAK
        }.filter { it.id != "read_long_manga" }

        relevantAchievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }

        checkReadSecrets()
    }

    private suspend fun handleEpisodeWatched(event: AchievementEvent.EpisodeWatched) {
        streakChecker.logEpisodeWatched()

        val achievements = getAchievementsForCategory(AchievementCategory.ANIME)
        val relevantAchievements = achievements.filter {
            it.type == AchievementType.QUANTITY ||
                it.type == AchievementType.EVENT ||
                it.type == AchievementType.STREAK
        }

        relevantAchievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleNovelChapterRead(event: AchievementEvent.NovelChapterRead) {
        streakChecker.logChapterRead()

        val achievements = getAchievementsForCategory(AchievementCategory.NOVEL)
        val relevantAchievements = achievements.filter {
            it.type == AchievementType.QUANTITY ||
                it.type == AchievementType.EVENT ||
                it.type == AchievementType.STREAK
        }.filter { it.id != "read_long_novel" }

        relevantAchievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleLibraryAdded(event: AchievementEvent.LibraryAdded) {
        val achievements = getAchievementsForCategory(event.type)
            .filter {
                it.type == AchievementType.DIVERSITY ||
                    it.type == AchievementType.LIBRARY
            }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }

        checkLibrarySecrets()
    }

    private suspend fun handleLibraryRemoved(event: AchievementEvent.LibraryRemoved) {
        checkLibrarySecrets()
    }

    private suspend fun handleMangaCompleted(event: AchievementEvent.MangaCompleted) {
        val achievements = getAchievementsForCategory(AchievementCategory.MANGA)
            .filter { it.type == AchievementType.EVENT }

        achievements.forEach { achievement ->
            // РЎРїРµС†РёР°Р»СЊРЅР°СЏ РѕР±СЂР°Р±РѕС‚РєР° РґР»СЏ read_long_manga
            if (achievement.id == "read_long_manga") {
                if (checkLongMangaAchievement(event)) {
                    val currentProgress = repository.getProgress(achievement.id).first()
                    applyProgressUpdate(achievement, currentProgress, 1)
                }
            } else {
                checkAndUpdateProgress(achievement, event)
            }
        }
        checkCompletionSecrets(event)
    }

    private suspend fun handleAnimeCompleted(event: AchievementEvent.AnimeCompleted) {
        val achievements = getAchievementsForCategory(AchievementCategory.ANIME)
            .filter { it.type == AchievementType.EVENT }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }

        checkCompletionSecrets(event)
    }

    private suspend fun handleNovelCompleted(event: AchievementEvent.NovelCompleted) {
        val achievements = getAchievementsForCategory(AchievementCategory.NOVEL)
            .filter { it.type == AchievementType.EVENT }

        achievements.forEach { achievement ->
            if (achievement.id == "read_long_novel") {
                if (checkLongNovelAchievement(event)) {
                    val currentProgress = repository.getProgress(achievement.id).first()
                    applyProgressUpdate(achievement, currentProgress, 1)
                }
            } else {
                checkAndUpdateProgress(achievement, event)
            }
        }
    }

    private suspend fun handleSessionEnd(event: AchievementEvent.SessionEnd) {
        checkTimeAchievements()
    }

    private suspend fun handleAppStart(event: AchievementEvent.AppStart) {
        checkTimeAchievements()
        return
        // Handle time-based achievements (РќРѕС‡РЅРѕР№ С‡С‚РµС†, Р–Р°РІРѕСЂРѕРЅРѕРє, etc.)
        val achievements = repository.getAll().first()
            .filter {
                it.type == AchievementType.EVENT &&
                    (
                        it.id.contains("time") ||
                            it.id.contains("owl") ||
                            it.id.contains("lark") ||
                            it.id.contains("morning") ||
                            it.id.contains("night") ||
                            it.id.contains("early_bird")
                        )
            }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleFeatureUsed(event: AchievementEvent.FeatureUsed) {
        featureCollector.onFeatureUsed(event.feature, event.count)
        checkFeatureAchievements()
        return
        // РЎРѕС…СЂР°РЅСЏРµРј СЃС‚Р°С‚РёСЃС‚РёРєСѓ РёСЃРїРѕР»СЊР·РѕРІР°РЅРёСЏ С„СѓРЅРєС†РёР№
        featureCollector.onFeatureUsed(event.feature, event.count)

        // Handle feature usage achievements (РљРѕР»Р»РµРєС†РёРѕРЅРµСЂ, РСЃСЃР»РµРґРѕРІР°С‚РµР»СЊ, etc.)
        val achievements = repository.getAll().first()
            .filter {
                it.type == AchievementType.EVENT &&
                    (
                        it.id.contains("feature") ||
                            it.id.contains("download") ||
                            it.id.contains("search") ||
                            it.id.contains("backup") ||
                            it.id.contains("filter") ||
                            it.id.contains("collector") ||
                            it.id.contains("explorer")
                        )
            }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    /**
     * РџСѓР±Р»РёС‡РЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ РѕС‚РїСЂР°РІРєРё СЃРѕР±С‹С‚РёР№ Рѕ РёСЃРїРѕР»СЊР·РѕРІР°РЅРёРё С„СѓРЅРєС†РёР№
     * Р’С‹Р·С‹РІР°Р№С‚Рµ СЌС‚РѕС‚ РјРµС‚РѕРґ РёР· UI РїСЂРё РІС‹РїРѕР»РЅРµРЅРёРё РѕРїСЂРµРґРµР»РµРЅРЅС‹С… РґРµР№СЃС‚РІРёР№
     */
    fun trackFeatureUsed(feature: AchievementEvent.Feature) {
        scope.launch {
            try {
                eventBus.tryEmit(AchievementEvent.FeatureUsed(feature = feature))
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "[ACHIEVEMENTS] Failed to track feature usage: ${e.message}" }
            }
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
        if (achievement.isTiered) {
            // РћР±СЂР°Р±РѕС‚РєР° РјРЅРѕРіРѕСѓСЂРѕРІРЅРµРІРѕРіРѕ РґРѕСЃС‚РёР¶РµРЅРёСЏ
            applyTieredProgressUpdate(achievement, currentProgress, newProgress)
        } else {
            // РћР±СЂР°Р±РѕС‚РєР° РѕР±С‹С‡РЅРѕРіРѕ РґРѕСЃС‚РёР¶РµРЅРёСЏ
            applyStandardProgressUpdate(achievement, currentProgress, newProgress)
        }
    }

    private suspend fun applyStandardProgressUpdate(
        achievement: Achievement,
        currentProgress: AchievementProgress?,
        newProgress: Int,
    ) {
        val threshold = achievement.threshold ?: 1
        logcat(LogPriority.INFO) {
            "[ACHIEVEMENTS] Checking ${achievement.id}: current=$currentProgress, new=$newProgress, threshold=$threshold"
        }

        if (currentProgress == null) {
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Creating new progress for ${achievement.id}" }
            repository.insertOrUpdateProgress(
                AchievementProgress.createStandard(
                    achievementId = achievement.id,
                    progress = newProgress,
                    maxProgress = threshold,
                    isUnlocked = newProgress >= threshold,
                    unlockedAt = if (newProgress >= threshold) System.currentTimeMillis() else null,
                ),
            )

            if (newProgress >= threshold) {
                logcat(LogPriority.INFO) { "[ACHIEVEMENTS] UNLOCKING ${achievement.id} on first check!" }
                onAchievementUnlocked(achievement)
            }
        } else if (!currentProgress.isUnlocked) {
            val shouldUnlock = newProgress >= threshold
            logcat(LogPriority.INFO) {
                "[ACHIEVEMENTS] Updating progress for ${achievement.id}: shouldUnlock=$shouldUnlock"
            }
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

    private suspend fun applyTieredProgressUpdate(
        achievement: Achievement,
        currentProgress: AchievementProgress?,
        newProgress: Int,
    ) {
        val tiers = achievement.tiers ?: return
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Checking tiered ${achievement.id}: newProgress=$newProgress" }

        // Р’С‹С‡РёСЃР»СЏРµРј С‚РµРєСѓС‰РёР№ СѓСЂРѕРІРµРЅСЊ
        val newTierIndex = tiers.indexOfLast { newProgress >= it.threshold }
        val newCurrentTier = newTierIndex + 1 // 0-based index to 1-based tier
        val previousTier = currentProgress?.currentTier ?: 0

        // Р’С‹С‡РёСЃР»СЏРµРј РїСЂРѕРіСЂРµСЃСЃ РґРѕ СЃР»РµРґСѓСЋС‰РµРіРѕ СѓСЂРѕРІРЅСЏ
        val nextTier = tiers.getOrNull(newCurrentTier)
        val tierProgress = if (nextTier != null) {
            val previousThreshold = tiers.getOrNull(newCurrentTier - 1)?.threshold ?: 0
            newProgress - previousThreshold
        } else {
            // РЈР¶Рµ РЅР° РјР°РєСЃРёРјР°Р»СЊРЅРѕРј СѓСЂРѕРІРЅРµ
            0
        }

        val tierMaxProgress = if (nextTier != null) {
            val previousThreshold = tiers.getOrNull(newCurrentTier - 1)?.threshold ?: 0
            nextTier.threshold - previousThreshold
        } else {
            100
        }

        logcat(LogPriority.INFO) {
            "[ACHIEVEMENTS] Tiered ${achievement.id}: " +
                "tier=$newCurrentTier/${tiers.size}, " +
                "tierProgress=$tierProgress/$tierMaxProgress, " +
                "previousTier=$previousTier"
        }

        // РЎРѕР·РґР°РµРј РёР»Рё РѕР±РЅРѕРІР»СЏРµРј РїСЂРѕРіСЂРµСЃСЃ
        val progressToSave = AchievementProgress.createTiered(
            achievementId = achievement.id,
            progress = newProgress,
            currentTier = newCurrentTier,
            maxTier = tiers.size,
            tierProgress = tierProgress,
            tierMaxProgress = tierMaxProgress,
            isUnlocked = newCurrentTier > 0,
            unlockedAt = if (newCurrentTier > 0 && previousTier == 0) {
                System.currentTimeMillis()
            } else {
                currentProgress?.unlockedAt
            },
        )

        repository.insertOrUpdateProgress(progressToSave)

        // Р•СЃР»Рё СѓСЂРѕРІРµРЅСЊ РїРѕРІС‹СЃРёР»СЃСЏ, РѕС‚РїСЂР°РІР»СЏРµРј СѓРІРµРґРѕРјР»РµРЅРёРµ
        if (newCurrentTier > previousTier && newCurrentTier > 0) {
            val unlockedTier = tiers[newTierIndex]
            logcat(LogPriority.INFO) {
                "[ACHIEVEMENTS] TIER UP! ${achievement.id}: " +
                    "tier $previousTier -> $newCurrentTier (${unlockedTier.title})"
            }
            onAchievementTierUp(achievement, unlockedTier, newCurrentTier)
        }
    }

    private fun onAchievementTierUp(
        achievement: Achievement,
        tier: tachiyomi.domain.achievement.model.AchievementTier,
        tierLevel: Int,
    ) {
        logcat(LogPriority.INFO) {
            "Achievement tier up: ${achievement.title} - Tier $tierLevel: ${tier.title} (+${tier.points} points)"
        }

        scope.launch {
            try {
                activityDataRepository.recordAchievementUnlock()
                pointsManager.addPoints(tier.points)
                updateMetaAchievements()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to add tier points for achievement: ${achievement.title}, ${e.message}"
                }
            }

            try {
                unlockableManager.unlockAchievementRewards(achievement)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to unlock tier rewards for achievement: ${achievement.title}, ${e.message}"
                }
            }

            // Р’С‹РґР°РµРј XP Р·Р° РґРѕСЃС‚РёР¶РµРЅРёРµ СѓСЂРѕРІРЅСЏ
            try {
                val xpReward = tier.points * 10 // XP = points * 10
                userProfileManager.addXP(xpReward)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to add XP for tier up: ${achievement.title}, ${e.message}"
                }
            }
        }

        // РћС‚РїСЂР°РІР»СЏРµРј callback
        unlockCallback?.onAchievementUnlocked(achievement)
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
                // РЎРїРµС†РёР°Р»СЊРЅР°СЏ РѕР±СЂР°Р±РѕС‚РєР° РґР»СЏ РґРѕСЃС‚РёР¶РµРЅРёР№ Р·Р°РІРµСЂС€РµРЅРёСЏ
                when {
                    achievement.id.startsWith("complete_") && achievement.id.endsWith("_manga") -> {
                        getCompletedMangaCount()
                    }
                    achievement.id.startsWith("complete_") && achievement.id.endsWith("_anime") -> {
                        getCompletedAnimeCount()
                    }
                    achievement.id.startsWith("complete_") && achievement.id.endsWith("_novel") -> {
                        getCompletedNovelCount()
                    }
                    else -> getTotalReadForCategory(achievement.category)
                }
            }
            AchievementType.DIVERSITY -> {
                when {
                    achievement.id.contains("genre", ignoreCase = true) -> {
                        when {
                            achievement.id.contains(
                                "manga",
                                ignoreCase = true,
                            ) -> diversityChecker.getMangaGenreDiversity()
                            achievement.id.contains(
                                "anime",
                                ignoreCase = true,
                            ) -> diversityChecker.getAnimeGenreDiversity()
                            achievement.id.contains(
                                "novel",
                                ignoreCase = true,
                            ) -> diversityChecker.getNovelGenreDiversity()
                            else -> diversityChecker.getGenreDiversity()
                        }
                    }
                    achievement.id.contains("source", ignoreCase = true) -> {
                        when {
                            achievement.id.contains(
                                "manga",
                                ignoreCase = true,
                            ) -> diversityChecker.getMangaSourceDiversity()
                            achievement.id.contains(
                                "anime",
                                ignoreCase = true,
                            ) -> diversityChecker.getAnimeSourceDiversity()
                            achievement.id.contains(
                                "novel",
                                ignoreCase = true,
                            ) -> diversityChecker.getNovelSourceDiversity()
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
                val mangaCount = mangaHandler.awaitOneOrNull { db -> db.historyQueries.getTotalChaptersRead() } ?: 0L
                val animeCount =
                    animeHandler.awaitOneOrNull { db -> db.animehistoryQueries.getTotalEpisodesWatched() } ?: 0L
                minOf(mangaCount, animeCount).toInt().coerceAtLeast(0)
            }
            AchievementType.SECRET -> {
                // Secret achievements are handled by checkSecretAchievements()
                // Return current progress from DB
                currentProgress?.progress ?: 0
            }
            AchievementType.TIME_BASED -> {
                // РСЃРїРѕР»СЊР·СѓРµРј TimeBasedAchievementChecker РґР»СЏ Р°СЃРёРЅС…СЂРѕРЅРЅРѕР№ РїСЂРѕРІРµСЂРєРё
                // Р’РѕР·РІСЂР°С‰Р°РµРј С‚РµРєСѓС‰РёР№ РїСЂРѕРіСЂРµСЃСЃ, РѕР±РЅРѕРІР»РµРЅРёРµ РїСЂРѕРёР·РѕР№РґРµС‚ Р°СЃРёРЅС…СЂРѕРЅРЅРѕ
                currentProgress?.progress ?: 0
            }
            AchievementType.FEATURE_BASED -> {
                // РСЃРїРѕР»СЊР·СѓРµРј FeatureBasedAchievementChecker РґР»СЏ Р°СЃРёРЅС…СЂРѕРЅРЅРѕР№ РїСЂРѕРІРµСЂРєРё
                // Р’РѕР·РІСЂР°С‰Р°РµРј С‚РµРєСѓС‰РёР№ РїСЂРѕРіСЂРµСЃСЃ, РѕР±РЅРѕРІР»РµРЅРёРµ РїСЂРѕРёР·РѕР№РґРµС‚ Р°СЃРёРЅС…СЂРѕРЅРЅРѕ
                currentProgress?.progress ?: 0
            }
        }
    }

    private fun isEventMatch(achievementId: String, event: AchievementEvent): Boolean {
        return when (event) {
            is AchievementEvent.ChapterRead -> achievementId == "first_chapter"
            is AchievementEvent.NovelChapterRead -> achievementId == "first_novel_chapter"
            is AchievementEvent.EpisodeWatched -> achievementId == "first_episode"
            is AchievementEvent.LibraryAdded -> false
            is AchievementEvent.LibraryRemoved -> false
            is AchievementEvent.MangaCompleted ->
                achievementId == "complete_1_manga" || achievementId == "read_long_manga"
            is AchievementEvent.AnimeCompleted ->
                achievementId == "complete_1_anime"
            is AchievementEvent.NovelCompleted ->
                achievementId == "complete_1_novel" || achievementId == "read_long_novel"
            is AchievementEvent.SessionEnd -> false
            is AchievementEvent.AppStart -> false
            is AchievementEvent.FeatureUsed -> false
        }
        val id = achievementId.lowercase()
        return when (event) {
            is AchievementEvent.ChapterRead -> {
                val isMangaReadPattern = id.contains("chapter") || id.contains("read")
                isMangaReadPattern &&
                    !id.contains("novel") &&
                    !id.contains("ranobe") &&
                    !id.contains("episode") &&
                    !id.contains("anime")
            }
            is AchievementEvent.NovelChapterRead ->
                (id.contains("novel") || id.contains("ranobe")) &&
                    (id.contains("chapter") || id.contains("read") || id.contains("first"))
            is AchievementEvent.EpisodeWatched ->
                id.contains("episode") || id.contains("watch")
            is AchievementEvent.LibraryAdded ->
                id.contains("library") || id.contains("favorite") || id.contains("collect") || id.contains("added")
            is AchievementEvent.LibraryRemoved -> false
            is AchievementEvent.MangaCompleted ->
                id.contains("manga_complete") ||
                    id.contains("completed_manga") ||
                    id.contains("manga_completed") ||
                    (id.contains("complete") && id.contains("_manga")) ||
                    id == "read_long_manga"
            is AchievementEvent.AnimeCompleted ->
                id.contains("anime_complete") ||
                    id.contains("completed_anime") ||
                    id.contains("anime_completed") ||
                    (id.contains("complete") && id.contains("_anime"))
            is AchievementEvent.NovelCompleted ->
                id.contains("novel_complete") ||
                    id.contains("completed_novel") ||
                    id.contains("novel_completed") ||
                    (id.contains("complete") && id.contains("_novel")) ||
                    id == "read_long_novel"
            is AchievementEvent.SessionEnd ->
                id.contains("session") || id.contains("time")
            is AchievementEvent.AppStart -> {
                // РџСЂРѕРІРµСЂРєР° РґРѕСЃС‚РёР¶РµРЅРёР№, РѕСЃРЅРѕРІР°РЅРЅС‹С… РЅР° РІСЂРµРјРµРЅРё СЃСѓС‚РѕРє
                when (event.hourOfDay) {
                    in 2..5 -> id.contains("night") || id.contains("owl") || id.contains("late")
                    in 6..9 -> id.contains("morning") || id.contains("lark") || id.contains("early_bird")
                    in 10..14 -> id.contains("afternoon")
                    in 15..18 -> id.contains("evening")
                    in 19..23, 0, 1 -> id.contains("night")
                    else -> false
                }
            }
            is AchievementEvent.FeatureUsed -> {
                // РџСЂРѕРІРµСЂРєР° РґРѕСЃС‚РёР¶РµРЅРёР№, РѕСЃРЅРѕРІР°РЅРЅС‹С… РЅР° РёСЃРїРѕР»СЊР·РѕРІР°РЅРёРё С„СѓРЅРєС†РёР№
                when (event.feature) {
                    AchievementEvent.Feature.DOWNLOAD -> id.contains("download") || id.contains("collector")
                    AchievementEvent.Feature.SEARCH, AchievementEvent.Feature.ADVANCED_SEARCH ->
                        id.contains("search") || id.contains("explorer")
                    AchievementEvent.Feature.BACKUP -> id.contains("backup")
                    AchievementEvent.Feature.FILTER -> id.contains("filter")
                    AchievementEvent.Feature.SETTINGS -> id.contains("settings")
                    AchievementEvent.Feature.SECRET_HALL_UNLOCKED -> id.contains("secret_hall")
                    else -> false
                }
            }
        }
    }

    /**
     * РЎРїРµС†РёР°Р»СЊРЅР°СЏ РїСЂРѕРІРµСЂРєР° РґР»СЏ РґРѕСЃС‚РёР¶РµРЅРёСЏ "Р”РѕР»РіРѕСЃС‚СЂРѕР№С‰РёРє"
     * РўСЂРµР±СѓРµС‚: РјР°РЅРіР° Р·Р°РІРµСЂС€РµРЅР° Р 200+ РіР»Р°РІ РїСЂРѕС‡РёС‚Р°РЅРѕ
     */
    private suspend fun checkLongMangaAchievement(event: AchievementEvent.MangaCompleted): Boolean {
        val chaptersRead =
            mangaHandler.awaitOneOrNull { db ->
                db.chaptersQueries.getReadChapterCountByMangaId(event.mangaId)
            } ?: 0L
        return chaptersRead >= 200
    }

    private suspend fun checkLongNovelAchievement(event: AchievementEvent.NovelCompleted): Boolean {
        val chaptersRead =
            novelHandler.awaitOneOrNull { db ->
                db.novel_chaptersQueries.getReadChapterCountByNovelId(event.novelId)
            } ?: 0L
        return chaptersRead >= 200
    }

    /**
     * РџРѕР»СѓС‡РёС‚СЊ РєРѕР»РёС‡РµСЃС‚РІРѕ Р·Р°РІРµСЂС€РµРЅРЅС‹С… РјР°РЅРі РІ Р±РёР±Р»РёРѕС‚РµРєРµ
     */
    private suspend fun getCompletedMangaCount(): Int {
        return mangaHandler.awaitOneOrNull { db ->
            db.mangasQueries.getCompletedMangaCount()
        }?.toInt() ?: 0
    }

    /**
     * РџРѕР»СѓС‡РёС‚СЊ РєРѕР»РёС‡РµСЃС‚РІРѕ Р·Р°РІРµСЂС€РµРЅРЅС‹С… Р°РЅРёРјРµ РІ Р±РёР±Р»РёРѕС‚РµРєРµ
     */
    private suspend fun getCompletedAnimeCount(): Int {
        return animeHandler.awaitOneOrNull { db ->
            db.animesQueries.getCompletedAnimeCount()
        }?.toInt() ?: 0
    }

    private suspend fun getCompletedNovelCount(): Int {
        return novelHandler.awaitOneOrNull { db ->
            db.novelsQueries.getCompletedNovelCount()
        }?.toInt() ?: 0
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
                mangaHandler.awaitOneOrNull { db -> db.historyQueries.getTotalChaptersRead() }?.toInt() ?: 0
            }
            AchievementCategory.ANIME -> {
                animeHandler.awaitOneOrNull { db -> db.animehistoryQueries.getTotalEpisodesWatched() }?.toInt() ?: 0
            }
            AchievementCategory.NOVEL -> {
                novelHandler.awaitOneOrNull { db -> db.novel_historyQueries.getTotalChaptersRead() }?.toInt() ?: 0
            }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                val mangaCount = mangaHandler.awaitOneOrNull { db -> db.historyQueries.getTotalChaptersRead() } ?: 0L
                val animeCount =
                    animeHandler.awaitOneOrNull { db -> db.animehistoryQueries.getTotalEpisodesWatched() } ?: 0L
                val novelCount =
                    novelHandler.awaitOneOrNull { db -> db.novel_historyQueries.getTotalChaptersRead() } ?: 0L
                (mangaCount + animeCount + novelCount).toInt()
            }
        }
    }

    private suspend fun getLibraryCountForCategory(category: AchievementCategory): Int {
        val mangaCount = mangaHandler.awaitOneOrNull { db -> db.mangasQueries.getLibraryCount() } ?: 0L
        val animeCount = animeHandler.awaitOneOrNull { db -> db.animesQueries.getLibraryCount() } ?: 0L
        val novelCount = novelHandler.awaitOneOrNull { db -> db.novelsQueries.getLibraryCount() } ?: 0L
        return when (category) {
            AchievementCategory.MANGA -> mangaCount.toInt()
            AchievementCategory.ANIME -> animeCount.toInt()
            AchievementCategory.NOVEL -> novelCount.toInt()
            AchievementCategory.BOTH, AchievementCategory.SECRET -> (mangaCount + animeCount + novelCount).toInt()
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

    private suspend fun checkTimeAchievements() {
        val achievements = repository.getAll().first()
            .filter { it.type == AchievementType.TIME_BASED }
        checkTypedAchievements(achievements) { achievement, currentProgress ->
            timeBasedChecker.check(
                achievement,
                currentProgress ?: AchievementProgress.createStandard(achievement.id, 0, 0, false),
            )
        }
    }

    private suspend fun checkFeatureAchievements() {
        val achievements = repository.getAll().first()
            .filter { it.type == AchievementType.FEATURE_BASED }
        checkTypedAchievements(achievements) { achievement, currentProgress ->
            featureBasedChecker.check(
                achievement,
                currentProgress ?: AchievementProgress.createStandard(achievement.id, 0, 0, false),
            )
        }
    }

    private suspend fun checkLibrarySecrets() {
        val achievements = repository.getByCategory(AchievementCategory.SECRET).first()
            .filter { it.id in librarySecretIds }
        achievements.forEach { achievement ->
            val currentProgress = repository.getProgress(achievement.id).first()
            if (currentProgress?.isUnlocked == true) return@forEach

            val shouldUnlock = when (achievement.id) {
                "secret_harem_king" -> checkHaremKing()
                "secret_isekai_truck" -> checkIsekaiTruck()
                "secret_chad" -> checkChad()
                "secret_saitama" -> checkSaitama()
                "secret_jojo" -> checkJojo()
                else -> false
            }

            if (shouldUnlock) {
                applyProgressUpdate(achievement, currentProgress, achievement.threshold ?: 1)
            }
        }
    }

    private suspend fun checkCompletionSecrets(event: AchievementEvent) {
        val achievements = repository.getByCategory(AchievementCategory.SECRET).first()
            .filter { it.id in completionSecretIds }
        achievements.forEach { achievement ->
            val currentProgress = repository.getProgress(achievement.id).first()
            if (currentProgress?.isUnlocked == true) return@forEach

            val shouldUnlock = when (achievement.id) {
                "secret_crybaby" -> checkCrybaby(event)
                "secret_shonen" -> checkShonen()
                "secret_deku" -> checkDeku(event)
                "secret_eren" -> checkEren(event)
                "secret_lelouch" -> checkLelouch(event)
                else -> false
            }

            if (shouldUnlock) {
                applyProgressUpdate(achievement, currentProgress, achievement.threshold ?: 1)
            }
        }
    }

    private suspend fun checkReadSecrets() {
        val achievement = repository.getByCategory(AchievementCategory.SECRET).first()
            .firstOrNull { it.id == "secret_onepiece" } ?: return
        val currentProgress = repository.getProgress(achievement.id).first()
        if (currentProgress?.isUnlocked == true) return

        if (checkOnePiece()) {
            applyProgressUpdate(achievement, currentProgress, achievement.threshold ?: 1)
        }
    }

    private suspend fun checkPointsSecrets() {
        val achievement = repository.getByCategory(AchievementCategory.SECRET).first()
            .firstOrNull { it.id == "secret_goku" } ?: return
        val currentProgress = repository.getProgress(achievement.id).first()
        if (currentProgress?.isUnlocked == true) return

        if (checkGoku()) {
            applyProgressUpdate(achievement, currentProgress, achievement.threshold ?: 1)
        }
    }

    private suspend fun checkTypedAchievements(
        achievements: List<Achievement>,
        shouldUnlock: suspend (Achievement, AchievementProgress?) -> Boolean,
    ) {
        achievements.forEach { achievement ->
            val currentProgress = repository.getProgress(achievement.id).first()
            if (currentProgress?.isUnlocked == true) return@forEach

            if (shouldUnlock(achievement, currentProgress)) {
                val threshold = achievement.threshold ?: 1
                repository.insertOrUpdateProgress(
                    AchievementProgress.createStandard(
                        achievementId = achievement.id,
                        progress = threshold,
                        maxProgress = threshold,
                        isUnlocked = true,
                        unlockedAt = System.currentTimeMillis(),
                    ),
                )
                onAchievementUnlocked(achievement)
            }
        }
    }

    private fun onAchievementUnlocked(achievement: Achievement) {
        logcat(LogPriority.INFO) { "Achievement unlocked: ${achievement.title} (+${achievement.points} points)" }

        scope.launch {
            try {
                activityDataRepository.recordAchievementUnlock()
                pointsManager.addPoints(achievement.points)
                pointsManager.incrementUnlocked()
                updateMetaAchievements()
                checkPointsSecrets()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to add points for achievement: ${achievement.title}, ${e.message}" }
            }

            try {
                unlockableManager.unlockAchievementRewards(achievement)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to unlock rewards for achievement: ${achievement.title}, ${e.message}"
                }
            }

            // Р’С‹РґР°РµРј РЅР°РіСЂР°РґС‹ Р·Р° РґРѕСЃС‚РёР¶РµРЅРёРµ
            if (achievement.hasRewards) {
                try {
                    userProfileManager.grantRewards(achievement.getAllRewards())
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Failed to grant profile rewards: ${e.message}" }
                }
            }
        }

        unlockCallback?.onAchievementUnlocked(achievement)
    }

    // ==================== TIME & FEATURE BASED ACHIEVEMENT CHECKERS ====================

    /**
     * РџСЂРѕРІРµСЂРєР° TIME_BASED Рё FEATURE_BASED РґРѕСЃС‚РёР¶РµРЅРёР№
     * Р’С‹Р·С‹РІР°РµС‚СЃСЏ РґР»СЏ РІСЃРµС… СЃРѕР±С‹С‚РёР№
     */
    private suspend fun checkTimeAndFeatureAchievements(event: AchievementEvent) {
        // РџРѕР»СѓС‡Р°РµРј РІСЃРµ РґРѕСЃС‚РёР¶РµРЅРёСЏ РєР°С‚РµРіРѕСЂРёРё BOTH
        val achievements = repository.getByCategory(AchievementCategory.BOTH).first()

        val timeAndFeatureAchievements = achievements.filter {
            it.type == AchievementType.TIME_BASED || it.type == AchievementType.FEATURE_BASED
        }

        timeAndFeatureAchievements.forEach { achievement ->
            val currentProgress = repository.getProgress(achievement.id).first()
            if (currentProgress?.isUnlocked == true) return@forEach

            val threshold = achievement.threshold ?: 1

            when (achievement.type) {
                AchievementType.TIME_BASED -> {
                    val shouldUnlock = timeBasedChecker.check(
                        achievement,
                        currentProgress ?: AchievementProgress.createStandard(achievement.id, 0, 0, false),
                    )
                    if (shouldUnlock && (currentProgress == null || !currentProgress.isUnlocked)) {
                        repository.insertOrUpdateProgress(
                            AchievementProgress.createStandard(
                                achievementId = achievement.id,
                                progress = threshold,
                                maxProgress = threshold,
                                isUnlocked = true,
                                unlockedAt = System.currentTimeMillis(),
                            ),
                        )
                        onAchievementUnlocked(achievement)
                    }
                }
                AchievementType.FEATURE_BASED -> {
                    val shouldUnlock = featureBasedChecker.check(
                        achievement,
                        currentProgress ?: AchievementProgress.createStandard(achievement.id, 0, 0, false),
                    )
                    if (shouldUnlock && (currentProgress == null || !currentProgress.isUnlocked)) {
                        repository.insertOrUpdateProgress(
                            AchievementProgress.createStandard(
                                achievementId = achievement.id,
                                progress = threshold,
                                maxProgress = threshold,
                                isUnlocked = true,
                                unlockedAt = System.currentTimeMillis(),
                            ),
                        )
                        onAchievementUnlocked(achievement)
                    }
                }
                else -> {}
            }
        }
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
        val mangaHaremCount = mangaHandler.awaitOneOrNull { db ->
            db.mangasQueries.getLibraryGenreCount("Harem")
        } ?: 0L
        val animeHaremCount = animeHandler.awaitOneOrNull { db ->
            db.animesQueries.getLibraryGenreCount("Harem")
        } ?: 0L
        return (mangaHaremCount + animeHaremCount) >= 20
    }

    /**
     * secret_isekai_truck: Library has 20+ titles with "Isekai" genre
     */
    private suspend fun checkIsekaiTruck(): Boolean {
        val mangaIsekaiCount = mangaHandler.awaitOneOrNull { db ->
            db.mangasQueries.getLibraryGenreCount("Isekai")
        } ?: 0L
        val animeIsekaiCount = animeHandler.awaitOneOrNull { db ->
            db.animesQueries.getLibraryGenreCount("Isekai")
        } ?: 0L
        return (mangaIsekaiCount + animeIsekaiCount) >= 20
    }

    /**
     * secret_chad: 10+ completed manga AND 0 ongoing manga (only completed)
     */
    private suspend fun checkChad(): Boolean {
        val completedCount = getCompletedMangaCount()
        val ongoingCount =
            mangaHandler.awaitOneOrNull { db ->
                db.mangasQueries.getLibraryCountByStatus(SManga.ONGOING.toLong())
            }?.toInt() ?: 0
        return completedCount >= 10 && ongoingCount == 0
    }

    /**
     * secret_shonen: 10+ completed titles with "Shounen" or "Shonen" genre
     */
    private suspend fun checkShonen(): Boolean {
        val completedMangaShonen =
            mangaHandler.awaitOneOrNull { db ->
                db.mangasQueries.getCompletedLibraryCountByAnyGenre(
                    SManga.COMPLETED.toLong(),
                    "Shounen",
                    "Shonen",
                )
            } ?: 0L
        val completedAnimeShonen =
            animeHandler.awaitOneOrNull { db ->
                db.animesQueries.getCompletedLibraryCountByAnyGenre(
                    SManga.COMPLETED.toLong(),
                    "Shounen",
                    "Shonen",
                )
            } ?: 0L
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
        val mangaCount = mangaHandler.awaitOneOrNull { db ->
            db.mangasQueries.getLibraryCount()
        } ?: 0L
        val animeCount = animeHandler.awaitOneOrNull { db ->
            db.animesQueries.getLibraryCount()
        } ?: 0L

        return mangaCount == 1L && animeCount == 1L
    }

    /**
     * secret_jojo: Library contains "Jojo" or "JoJo" or "Jojo's Bizarre Adventure" in title
     */
    private suspend fun checkJojo(): Boolean {
        val hasJojoManga = mangaHandler.awaitOneOrNull { db ->
            db.mangasQueries.hasLibraryTitleLike("jojo")
        } ?: false
        val hasJojoAnime = animeHandler.awaitOneOrNull { db ->
            db.animesQueries.hasLibraryTitleLike("jojo")
        } ?: false

        return hasJojoManga || hasJojoAnime
    }

    /**
     * secret_onepiece: Total chapters read >= 1000
     */
    private suspend fun checkOnePiece(): Boolean {
        val totalChapters = mangaHandler.awaitOneOrNull { db ->
            db.historyQueries.getTotalChaptersRead()
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
