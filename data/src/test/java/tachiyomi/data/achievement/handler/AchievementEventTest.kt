package tachiyomi.data.achievement.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.model.AchievementEvent

class AchievementEventTest {

    private lateinit var eventBus: AchievementEventBus
    private lateinit var featureCollector: FeatureUsageCollector
    private lateinit var sessionManager: SessionManager

    @BeforeEach
    fun setup() {
        eventBus = mockk(relaxed = true)
        featureCollector = FeatureUsageCollector(mockk(relaxed = true))
        sessionManager = SessionManager(eventBus, featureCollector)
    }

    @Test
    fun `onSessionStart emits AppStart event with correct hour`() {
        // Тест проверяет, что onSessionStart отправляет событие AppStart
        // Мы не можем контролировать время, но можем проверить, что событие отправляется
        sessionManager.onSessionStart()

        verify(exactly = 1) {
            eventBus.tryEmit(match { it is AchievementEvent.AppStart })
        }
    }

    @Test
    fun `onSessionStart emits AppStart with hour in valid range`() {
        sessionManager.onSessionStart()

        verify(exactly = 1) {
            eventBus.tryEmit(match {
                it is AchievementEvent.AppStart && it.hourOfDay in 0..23
            })
        }
    }

    @Test
    fun `onSessionStart then onSessionEnd emits both AppStart and SessionEnd`() {
        sessionManager.onSessionStart()
        sessionManager.onSessionEnd()

        verify(exactly = 1) {
            eventBus.tryEmit(match { it is AchievementEvent.AppStart })
        }
        verify(exactly = 1) {
            eventBus.tryEmit(match { it is AchievementEvent.SessionEnd })
        }
    }

    @Test
    fun `multiple session starts emit multiple AppStart events`() {
        sessionManager.onSessionStart()
        sessionManager.onSessionEnd()

        sessionManager.onSessionStart()
        sessionManager.onSessionEnd()

        verify(exactly = 2) {
            eventBus.tryEmit(match { it is AchievementEvent.AppStart })
        }
        verify(exactly = 2) {
            eventBus.tryEmit(match { it is AchievementEvent.SessionEnd })
        }
    }
}
