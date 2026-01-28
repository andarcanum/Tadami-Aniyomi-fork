package tachiyomi.data.achievement.handler

import tachiyomi.data.achievement.model.AchievementEvent
import java.util.Calendar

class SessionManager(
    private val eventBus: AchievementEventBus,
    private val featureCollector: FeatureUsageCollector,
) {
    private var startTime: Long = 0L
    private var startTimestamp: Long = 0L

    fun onSessionStart() {
        startTime = System.currentTimeMillis()
        startTimestamp = startTime

        // Отправляем событие AppStart с информацией о времени суток
        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        eventBus.tryEmit(AchievementEvent.AppStart(hourOfDay = hourOfDay))

        // Сохраняем информацию о сессии в FeatureUsageCollector
        featureCollector.onSessionStart(startTimestamp, hourOfDay)
    }

    fun onSessionEnd() {
        if (startTime == 0L) return

        val durationMs = System.currentTimeMillis() - startTime

        // Отправляем событие SessionEnd
        eventBus.tryEmit(AchievementEvent.SessionEnd(durationMs))

        // Сохраняем длительность сессии в FeatureUsageCollector
        featureCollector.onSessionEnd(durationMs)

        startTime = 0L
    }
}
