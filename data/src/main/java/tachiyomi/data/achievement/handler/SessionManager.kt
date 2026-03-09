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

        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        featureCollector.onSessionStart(startTimestamp, hourOfDay)
        eventBus.tryEmit(AchievementEvent.AppStart(hourOfDay = hourOfDay))
    }

    fun onSessionEnd() {
        if (startTime == 0L) return

        val durationMs = System.currentTimeMillis() - startTime
        featureCollector.onSessionEnd(durationMs)
        eventBus.tryEmit(AchievementEvent.SessionEnd(durationMs))

        startTime = 0L
    }
}
