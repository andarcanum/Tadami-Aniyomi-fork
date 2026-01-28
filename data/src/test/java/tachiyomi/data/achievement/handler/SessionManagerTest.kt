package tachiyomi.data.achievement.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.model.AchievementEvent

class SessionManagerTest {

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
    fun `onSessionEnd without onSessionStart does nothing`() {
        sessionManager.onSessionEnd()

        verify(exactly = 0) { eventBus.tryEmit(any()) }
    }

    @Test
    fun `onSessionStart then onSessionEnd emits SessionEnd event`() {
        sessionManager.onSessionStart()

        // Small delay to ensure duration > 0 if needed,
        // but System.currentTimeMillis() might return same value in fast tests.
        // However, SessionManager uses System.currentTimeMillis() directly.

        sessionManager.onSessionEnd()

        verify(exactly = 1) {
            eventBus.tryEmit(match { it is AchievementEvent.SessionEnd && it.durationMs >= 0 })
        }
    }

    @Test
    fun `onSessionEnd resets startTime`() {
        sessionManager.onSessionStart()
        sessionManager.onSessionEnd()

        // Second call should do nothing because startTime was reset to 0
        sessionManager.onSessionEnd()

        // Теперь мы отправляем 2 события: AppStart и SessionEnd
        verify(exactly = 2) { eventBus.tryEmit(any()) }
        verify(exactly = 1) { eventBus.tryEmit(match { it is AchievementEvent.SessionEnd }) }
        verify(exactly = 1) { eventBus.tryEmit(match { it is AchievementEvent.AppStart }) }
    }

    @Test
    fun `multiple sessions emit multiple events`() {
        sessionManager.onSessionStart()
        sessionManager.onSessionEnd()

        sessionManager.onSessionStart()
        sessionManager.onSessionEnd()

        verify(exactly = 2) {
            eventBus.tryEmit(match { it is AchievementEvent.SessionEnd })
        }
    }
}
