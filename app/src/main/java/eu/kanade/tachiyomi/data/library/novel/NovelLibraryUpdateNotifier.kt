package eu.kanade.tachiyomi.data.library.novel

import android.content.Context
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR

class NovelLibraryUpdateNotifier(
    private val context: Context,
) {

    private val cancelIntent by lazy {
        NotificationReceiver.cancelLibraryUpdatePendingBroadcast(context)
    }

    val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.app_name))
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                cancelIntent,
            )
        }
    }

    fun showProgressNotification(novels: List<Novel>, current: Int, total: Int) {
        progressNotificationBuilder
            .setContentTitle(
                context.stringResource(
                    MR.strings.notification_updating_progress,
                    "${(current * 100) / total}%",
                ),
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    novels.joinToString("\n") { it.title.chop(40) },
                ),
            )

        context.notify(
            Notifications.ID_NOVEL_LIBRARY_PROGRESS,
            progressNotificationBuilder
                .setProgress(total, current, false)
                .build(),
        )
    }

    fun showUpdateSummaryNotification(updated: List<Pair<Novel, Int>>) {
        if (updated.isEmpty()) return

        context.notify(
            Notifications.ID_NEW_NOVEL_CHAPTERS,
            Notifications.CHANNEL_NEW_CHAPTERS_EPISODES,
        ) {
            setContentTitle(context.stringResource(MR.strings.notification_new_chapters))
            setContentText(
                context.resources.getQuantityString(
                    R.plurals.notification_new_chapters_summary,
                    updated.size,
                    updated.size,
                ),
            )
            setSmallIcon(R.drawable.ic_ani)
            setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    updated.joinToString("\n") { (novel, count) ->
                        "${novel.title.chop(45)} (+$count)"
                    },
                ),
            )
            setGroup(Notifications.GROUP_NEW_NOVEL_CHAPTERS)
            setGroupSummary(true)
            setAutoCancel(true)
        }
    }

    fun showUpdateErrorNotification(failed: Int) {
        if (failed == 0) return

        context.notify(
            Notifications.ID_NOVEL_LIBRARY_ERROR,
            Notifications.CHANNEL_LIBRARY_ERROR,
        ) {
            setContentTitle(context.stringResource(MR.strings.notification_update_error, failed))
            setContentText(context.stringResource(MR.strings.action_show_errors))
            setSmallIcon(R.drawable.ic_ani)
            setAutoCancel(true)
        }
    }

    fun cancelProgressNotification() {
        context.cancelNotification(Notifications.ID_NOVEL_LIBRARY_PROGRESS)
    }
}
