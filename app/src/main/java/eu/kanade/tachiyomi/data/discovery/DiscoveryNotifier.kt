package eu.kanade.tachiyomi.data.discovery

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.tadami.aurora.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

class DiscoveryNotifier(private val context: Context) {

    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    private fun cancelIntent(mediaType: DiscoveryMediaType?) =
        NotificationReceiver.cancelDiscoveryUpdatePendingBroadcast(context, mediaType)

    private fun titleFor(mediaType: DiscoveryMediaType?): String {
        val baseTitle = context.stringResource(AYMR.strings.aurora_for_you)
        val mediaTitle = when (mediaType) {
            DiscoveryMediaType.ANIME -> context.stringResource(AYMR.strings.aurora_updating_anime)
            DiscoveryMediaType.MANGA -> context.stringResource(AYMR.strings.aurora_updating_manga)
            DiscoveryMediaType.NOVEL -> context.stringResource(AYMR.strings.aurora_updating_novel)
            null -> null
        }
        return if (mediaTitle != null) {
            "$baseTitle: $mediaTitle"
        } else {
            baseTitle
        }
    }

    fun buildProgressNotification(
        mediaType: DiscoveryMediaType? = null,
        progressText: String? = null,
        current: Int = 0,
        total: Int = 0,
    ): Notification {
        return context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(titleFor(mediaType))
            if (!progressText.isNullOrBlank()) {
                setContentText(progressText)
            }
            if (total > 0) {
                setProgress(total, current, false)
            } else {
                setProgress(0, 0, true)
            }
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                cancelIntent(mediaType),
            )
        }.build()
    }

    fun getForegroundInfo(mediaType: DiscoveryMediaType): ForegroundInfo {
        val notification = buildProgressNotification(mediaType)
        return ForegroundInfo(
            Notifications.discoveryProgressId(mediaType),
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    fun showProgress(
        mediaType: DiscoveryMediaType,
        progressText: String? = null,
        current: Int = 0,
        total: Int = 0,
    ) {
        val notification = buildProgressNotification(mediaType, progressText, current, total)
        context.notify(Notifications.discoveryProgressId(mediaType), notification)
    }

    fun cancel(mediaType: DiscoveryMediaType) {
        NotificationManagerCompat.from(context).cancel(Notifications.discoveryProgressId(mediaType))
    }

    fun cancel() {
        DiscoveryMediaType.entries.forEach(::cancel)
    }
}
