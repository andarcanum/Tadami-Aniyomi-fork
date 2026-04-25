package eu.kanade.tachiyomi.data.library.novel

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.tadami.aurora.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateFailure
import eu.kanade.tachiyomi.data.library.LibraryUpdateFailureNotificationFormatter
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.math.RoundingMode
import java.text.NumberFormat
import tachiyomi.i18n.R as I18nR

class NovelLibraryUpdateNotifier(
    private val context: Context,
) {

    private val percentFormatter = NumberFormat.getPercentInstance().apply {
        roundingMode = RoundingMode.DOWN
        maximumFractionDigits = 0
    }

    private val cancelIntent by lazy {
        NotificationReceiver.cancelLibraryUpdatePendingBroadcast(context)
    }

    val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.stringResource(AYMR.strings.aurora_updating_novel))
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

    fun showProgressNotification(
        novels: List<Novel>,
        current: Int,
        total: Int,
        updated: Int,
        failed: Int,
    ) {
        val safeTotal = total.coerceAtLeast(1)

        progressNotificationBuilder
            .setContentTitle(
                context.stringResource(
                    MR.strings.notification_updating_progress,
                    percentFormatter.format(current.toFloat() / safeTotal),
                ),
            )
            .setContentText("$current/$total | +$updated | !$failed")
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

    fun showNoUpdatesNotification(checked: Int) {
        context.notify(
            Notifications.ID_NEW_NOVEL_CHAPTERS,
            Notifications.CHANNEL_LIBRARY_PROGRESS,
        ) {
            setContentTitle(context.stringResource(AYMR.strings.aurora_updating_novel))
            setContentText(context.stringResource(MR.strings.update_check_no_new_updates))
            setSubText("$checked")
            setSmallIcon(R.drawable.ic_ani)
            setAutoCancel(true)
        }
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
                    I18nR.plurals.notification_new_chapters_summary,
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

    fun showUpdateErrorNotification(errors: List<LibraryUpdateFailure>, uri: Uri) {
        if (errors.isEmpty()) return

        val notificationText = LibraryUpdateFailureNotificationFormatter.build(
            context = context,
            failures = errors,
            hideContent = false,
        )

        context.notify(
            Notifications.ID_NOVEL_LIBRARY_ERROR,
            Notifications.CHANNEL_LIBRARY_ERROR,
        ) {
            setContentTitle(context.stringResource(MR.strings.notification_update_error, errors.size))
            setContentText(notificationText.contentText)
            notificationText.bigText?.let {
                setStyle(NotificationCompat.BigTextStyle().bigText(it))
            }
            setSmallIcon(R.drawable.ic_ani)
            setContentIntent(NotificationReceiver.openErrorLogPendingActivity(context, uri))
        }
    }

    fun cancelProgressNotification() {
        context.cancelNotification(Notifications.ID_NOVEL_LIBRARY_PROGRESS)
    }
}
