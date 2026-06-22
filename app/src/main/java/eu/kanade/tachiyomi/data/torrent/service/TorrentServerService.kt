package eu.kanade.tachiyomi.data.torrent.service

import android.app.Application
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import aniyomi.core.common.torrent.ProxyMode
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import com.tadami.aurora.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.secozzi.torrserver.TorrServer
import kotlin.time.Duration.Companion.seconds

class TorrentServerService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val networkPreferences: NetworkPreferences by lazy { Injekt.get() }
    private val torrentPreferences: TorrentPreferences by lazy { Injekt.get() }
    private val torrentServerUtils: TorrentServerUtils by lazy { Injekt.get() }
    private val api: TorrentServerApi by lazy { Injekt.get() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                notification(this)
                startServer()
                return START_STICKY
            }
            ACTION_STOP -> {
                stopServer()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { TorrServer.stopServer() }
            .onFailure { logcat(LogPriority.DEBUG, it) { "Failed to stop TorrServer on service destroy" } }
        runCatching { api.setPort(0) }
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun startServer() {
        serviceScope.launch {
            if (api.echo().isNotEmpty()) return@launch

            if (networkPreferences.verboseLogging().get()) {
                TorrServer.registerLogCallback()
            }

            val proxyMode = torrentPreferences.torrServerProxyMode().get()
            val port = TorrServer.startServer(
                port = torrentPreferences.torrServerPort().get(),
                path = filesDir.absolutePath,
                proxyMode = proxyMode.value,
                proxyUrl = if (proxyMode == ProxyMode.None) "" else torrentPreferences.torrServerProxyUrl().get(),
            )
            if (port != -1) {
                api.setPort(port)
                wait(10)
                torrentServerUtils.setTrackersList()
            }
        }
    }

    private fun stopServer() {
        serviceScope.launch {
            runCatching { TorrServer.stopServer() }
                .onFailure { logcat(LogPriority.DEBUG, it) { "Failed to stop TorrServer" } }
            api.setPort(0)
            applicationContext.cancelNotification(Notifications.ID_TORRENT_SERVER)
            stopSelf()
        }
    }

    private fun notification(context: Context) {
        val startAgainIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, TorrentServerService::class.java).apply { action = ACTION_START },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exitPendingIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, TorrentServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = context.notificationBuilder(Notifications.CHANNEL_TORRENT_SERVER) {
            setSmallIcon(R.drawable.ic_ani)
            setContentTitle(stringResource(MR.strings.app_name))
            setContentText(stringResource(AYMR.strings.torrentserver_is_running))
            setAutoCancel(false)
            setOngoing(true)
            setDeleteIntent(startAgainIntent)
            setUsesChronometer(true)
            addAction(
                R.drawable.ic_close_24dp,
                stringResource(AYMR.strings.action_stop),
                exitPendingIntent,
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifications.ID_TORRENT_SERVER,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(Notifications.ID_TORRENT_SERVER, builder.build())
        }
    }

    companion object {
        const val ACTION_START = "start_torrent_server"
        const val ACTION_STOP = "stop_torrent_server"

        suspend fun startAndWait(timeoutSeconds: Int = 10): Boolean {
            start()
            return wait(timeoutSeconds)
        }

        fun start() {
            try {
                val context = Injekt.get<Application>()
                val intent = Intent(context, TorrentServerService::class.java).apply { action = ACTION_START }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Failed to start torrent service" }
            }
        }

        fun stop() {
            try {
                val context = Injekt.get<Application>()
                val intent = Intent(context, TorrentServerService::class.java).apply { action = ACTION_STOP }
                context.startService(intent)
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Failed to stop torrent service" }
            }
        }

        suspend fun wait(timeoutSeconds: Int = 10): Boolean {
            val api = Injekt.get<TorrentServerApi>()
            repeat(timeoutSeconds.coerceAtLeast(0) + 1) {
                if (api.echo().isNotEmpty()) return true
                delay(1.seconds)
            }
            return false
        }
    }
}
