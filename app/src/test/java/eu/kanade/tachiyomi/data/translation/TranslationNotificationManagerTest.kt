package eu.kanade.tachiyomi.data.translation

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class TranslationNotificationManagerTest {
    @Test
    fun `can be created with application context`() {
        val app = mockk<Application>(relaxed = true)
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        every { app.getSystemService(NotificationManager::class.java) } returns notificationManager
        every { app.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager

        TranslationNotificationManager(
            context = app,
            queueManager = mockk(relaxed = true),
        )
    }
}
