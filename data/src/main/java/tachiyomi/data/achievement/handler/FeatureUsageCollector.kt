package tachiyomi.data.achievement.handler

import tachiyomi.data.achievement.model.AchievementEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Коллектор статистики использования функций и сессий
 * Хранит счетчики использования функций и информацию о сессиях
 */
class FeatureUsageCollector(
    private val eventBus: AchievementEventBus,
) {

    // Счетчики использования функций
    private val featureCounts = ConcurrentHashMap<AchievementEvent.Feature, Int>()

    // История сессий: timestamp -> час дня
    private val sessionStartTimes = ConcurrentHashMap<Long, Int>()

    // Длительности сессий в миллисекундах
    private val sessionDurations = mutableListOf<Long>()

    init {
        // Инициализация счетчиков
        AchievementEvent.Feature.entries.forEach { feature ->
            featureCounts[feature] = 0
        }
    }

    /**
     * Обработать событие FeatureUsed
     */
    fun onFeatureUsed(feature: AchievementEvent.Feature, count: Int = 1) {
        val current = featureCounts.getOrDefault(feature, 0)
        featureCounts[feature] = current + count
    }

    /**
     * Обработать начало сессии
     */
    fun onSessionStart(timestamp: Long, hourOfDay: Int) {
        sessionStartTimes[timestamp] = hourOfDay
    }

    /**
     * Обработать конец сессии
     */
    fun onSessionEnd(durationMs: Long) {
        sessionDurations.add(durationMs)
        // Храним только последние 100 сессий
        if (sessionDurations.size > 100) {
            sessionDurations.removeAt(0)
        }
    }

    /**
     * Получить счетчик использования функции
     */
    fun getFeatureCount(feature: AchievementEvent.Feature): Int {
        return featureCounts.getOrDefault(feature, 0)
    }

    /**
     * Проверить, были ли сессии в указанном временном диапазоне
     * @param startHour начало диапазона (0-23)
     * @param endHour конец диапазона (0-23)
     * @return true если была хотя бы одна сессия в этом диапазоне
     */
    fun hasSessionInTimeRange(startHour: Int, endHour: Int): Boolean {
        return sessionStartTimes.values.any { hour ->
            hour in startHour..endHour
        }
    }

    /**
     * Проверить, была ли сессия длиннее указанной длительности
     * @param minDurationMs минимальная длительность в миллисекундах
     * @return true если была сессия длиннее minDurationMs
     */
    fun hasLongSession(minDurationMs: Long): Boolean {
        return sessionDurations.any { it >= minDurationMs }
    }

    /**
     * Получить максимальную длительность сессии
     * @return максимальная длительность в миллисекундах
     */
    fun getMaxSessionDuration(): Long {
        return sessionDurations.maxOrNull() ?: 0L
    }

    /**
     * Получить среднюю длительность сессии
     * @return средняя длительность в миллисекундах
     */
    fun getAverageSessionDuration(): Long {
        if (sessionDurations.isEmpty()) return 0L
        return sessionDurations.sum() / sessionDurations.size
    }

    /**
     * Получить количество сессий
     */
    fun getSessionCount(): Int {
        return sessionStartTimes.size
    }

    /**
     * Сбросить всю статистику (для тестов)
     */
    fun reset() {
        AchievementEvent.Feature.entries.forEach { feature ->
            featureCounts[feature] = 0
        }
        sessionStartTimes.clear()
        sessionDurations.clear()
    }
}
