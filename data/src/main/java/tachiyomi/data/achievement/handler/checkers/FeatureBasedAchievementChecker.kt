package tachiyomi.data.achievement.handler.checkers

import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.handler.FeatureUsageCollector
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType

/**
 * Чекер для достижений, основанных на использовании функций
 * Проверяет: поиск, фильтры, скачивания, бэкап, настройки
 */
class FeatureBasedAchievementChecker(
    private val eventBus: AchievementEventBus,
    private val featureCollector: FeatureUsageCollector,
) {

    /**
     * Проверяет фиче-бейсд достижения
     * @return true если достижение выполнено
     */
    suspend fun check(
        achievement: Achievement,
        currentProgress: AchievementProgress,
    ): Boolean {
        if (achievement.type != AchievementType.FEATURE_BASED) return false

        val threshold = achievement.threshold ?: 1

        return when (achievement.id) {
            "download_starter", "chapter_collector", "trophy_hunter" -> {
                // Скачивание глав
                val downloadCount = featureCollector.getFeatureCount(AchievementEvent.Feature.DOWNLOAD)
                downloadCount >= threshold
            }
            "search_user", "advanced_explorer" -> {
                // Использование поиска
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
                // Использование фильтров
                val filterCount = featureCollector.getFeatureCount(AchievementEvent.Feature.FILTER)
                filterCount >= threshold
            }
            "backup_master" -> {
                // Создание бэкапа
                val backupCount = featureCollector.getFeatureCount(AchievementEvent.Feature.BACKUP)
                backupCount >= threshold
            }
            "settings_explorer" -> {
                // Заход в настройки
                val settingsCount = featureCollector.getFeatureCount(AchievementEvent.Feature.SETTINGS)
                settingsCount >= threshold
            }
            "stats_viewer" -> {
                // Просмотр статистики
                val statsCount = featureCollector.getFeatureCount(AchievementEvent.Feature.STATS)
                statsCount >= threshold
            }
            "theme_changer" -> {
                // Смена темы
                val themeCount = featureCollector.getFeatureCount(AchievementEvent.Feature.THEME_CHANGE)
                themeCount >= threshold
            }
            "persistent_clicker" -> {
                // Секретное: нажать на логотип 10 раз
                val logoClicks = featureCollector.getFeatureCount(AchievementEvent.Feature.LOGO_CLICK)
                logoClicks >= threshold
            }
            else -> {
                logcat(LogPriority.WARN) { "[ACHIEVEMENTS] Unknown feature_based achievement: ${achievement.id}" }
                false
            }
        }
    }

    /**
     * Вычисляет прогресс для фиче-бейсд достижений
     * @return 0-1 прогресс или null если не применимо
     */
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
            else -> return null
        }

        return (currentCount.toFloat() / threshold.toFloat()).coerceIn(0f, 1f)
    }
}
