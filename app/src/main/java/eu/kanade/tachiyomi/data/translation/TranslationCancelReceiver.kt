package eu.kanade.tachiyomi.data.translation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chapterId = intent.getLongExtra("chapterId", -1)
        if (chapterId == -1L) return

        val queueManager = Injekt.get<TranslationQueueManager>()
        queueManager.removeFromQueue(chapterId)
        context.cancelNotification(Notifications.ID_TRANSLATION_PROGRESS + chapterId.toInt())
    }
}
