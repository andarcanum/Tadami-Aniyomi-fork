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
 * Чекер для достижений, основанных на времени
 * Проверяет: ночной чтец, утренний чтец, марафонец
 */
class TimeBasedAchievementChecker(
    private val eventBus: AchievementEventBus,
    private val featureCollector: FeatureUsageCollector,
) {

    /**
     * Проверяет таймбейсд достижения
     * @return true если достижение выполнено
     */
    suspend fun check(
        achievement: Achievement,
        currentProgress: AchievementProgress,
    ): Boolean {
        if (achievement.type != AchievementType.TIME_BASED) return false

        return when (achievement.id) {
            "night_owl" -> checkNightOwl()
            "early_bird" -> checkEarlyBird()
            "marathon_reader" -> checkMarathonReader()
            else -> {
                logcat(LogPriority.WARN) { "[ACHIEVEMENTS] Unknown time_based achievement: ${achievement.id}" }
                false
            }
        }
    }

    /**
     * Проверка "Сова": чтение между 02:00 и 05:00
     */
    private suspend fun checkNightOwl(): Boolean {
        // Проверяем, были ли сессии в ночное время (02:00-05:00)
        return featureCollector.hasSessionInTimeRange(startHour = 2, endHour = 5)
    }

    /**
     * Проверка "Жаворонок": чтение утром (06:00-09:00)
     */
    private suspend fun checkEarlyBird(): Boolean {
        return featureCollector.hasSessionInTimeRange(startHour = 6, endHour = 9)
    }

    /**
     * Проверка "Марафонец": непрерывная сессия > 2 часов
     */
    private suspend fun checkMarathonReader(): Boolean {
        return featureCollector.hasLongSession(minDurationMs = 2 * 60 * 60 * 1000) // 2 часа
    }

    /**
     * Вычисляет прогресс для таймбейсд достижений
     * @return 0-1 прогресс или null если не применимо
     */
    suspend fun getProgress(
        achievement: Achievement,
        currentProgress: AchievementProgress,
    ): Float? {
        // Для таймбейсд достижений прогресс бинарный (0 или 1)
        return when (achievement.id) {
            "night_owl" -> if (checkNightOwl()) 1f else 0f
            "early_bird" -> if (checkEarlyBird()) 1f else 0f
            "marathon_reader" -> {
                val maxDuration = featureCollector.getMaxSessionDuration()
                val targetDuration = 2 * 60 * 60 * 1000L // 2 часа
                (maxDuration.toFloat() / targetDuration.toFloat()).coerceIn(0f, 1f)
            }
            else -> null
        }
    }
}
