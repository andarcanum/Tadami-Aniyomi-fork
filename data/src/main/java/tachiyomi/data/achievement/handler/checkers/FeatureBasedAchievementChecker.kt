package tachiyomi.data.achievement.handler.checkers

import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.handler.FeatureUsageCollector
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType

class FeatureBasedAchievementChecker(
    private val eventBus: AchievementEventBus,
    private val featureCollector: FeatureUsageCollector,
) {

    suspend fun check(
        achievement: Achievement,
        currentProgress: AchievementProgress,
    ): Boolean {
        if (achievement.type != AchievementType.FEATURE_BASED) return false

        val threshold = achievement.threshold ?: 1

        return when (achievement.id) {
            "download_starter", "chapter_collector", "trophy_hunter" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.DOWNLOAD) >= threshold
            }
            "search_user", "advanced_explorer" -> {
                val searchCount = featureCollector.getFeatureCount(AchievementEvent.Feature.SEARCH)
                val advancedSearchCount = featureCollector.getFeatureCount(AchievementEvent.Feature.ADVANCED_SEARCH)
                val totalSearches = if (achievement.id == "advanced_explorer") {
                    advancedSearchCount
                } else {
                    searchCount + advancedSearchCount
                }
                totalSearches >= threshold
            }
            "filter_master" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.FILTER) >= threshold
            }
            "backup_master" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.BACKUP) >= threshold
            }
            "settings_explorer" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.SETTINGS) >= threshold
            }
            "stats_viewer" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.STATS) >= threshold
            }
            "theme_changer" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.THEME_CHANGE) >= threshold
            }
            "persistent_clicker" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.LOGO_CLICK) >= threshold
            }
            "secret_hall_unlocked" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.SECRET_HALL_UNLOCKED) >= threshold
            }
            else -> {
                logcat(LogPriority.WARN) { "[ACHIEVEMENTS] Unknown feature_based achievement: ${achievement.id}" }
                false
            }
        }
    }

    suspend fun getProgress(
        achievement: Achievement,
        currentProgress: AchievementProgress,
    ): Float? {
        val threshold = achievement.threshold ?: return null
        if (threshold <= 0) return null

        val currentCount = when (achievement.id) {
            "download_starter", "chapter_collector", "trophy_hunter" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.DOWNLOAD)
            }
            "search_user" -> {
                val search = featureCollector.getFeatureCount(AchievementEvent.Feature.SEARCH)
                val advanced = featureCollector.getFeatureCount(AchievementEvent.Feature.ADVANCED_SEARCH)
                search + advanced
            }
            "advanced_explorer" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.ADVANCED_SEARCH)
            }
            "filter_master" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.FILTER)
            }
            "backup_master" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.BACKUP)
            }
            "settings_explorer" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.SETTINGS)
            }
            "stats_viewer" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.STATS)
            }
            "theme_changer" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.THEME_CHANGE)
            }
            "persistent_clicker" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.LOGO_CLICK)
            }
            "secret_hall_unlocked" -> {
                featureCollector.getFeatureCount(AchievementEvent.Feature.SECRET_HALL_UNLOCKED)
            }
            else -> return null
        }

        return (currentCount.toFloat() / threshold.toFloat()).coerceIn(0f, 1f)
    }
}
