package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media.session.MediaButtonReceiver.handleIntent
import com.tadami.aurora.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class NovelTtsTransportAction {
    PREVIOUS,
    PLAY,
    PAUSE,
    NEXT,
    STOP,
}

data class NovelTtsForegroundNotificationState(
    val title: String,
    val text: String,
    val isPlaying: Boolean,
)

internal data class NovelTtsNotificationLayoutSnapshot(
    val usesSystemMediaStyle: Boolean,
    val usesCustomRemoteViews: Boolean,
    val compactActionIndices: List<Int>,
    val compactActions: List<NovelTtsTransportAction>,
    val expandedActions: List<NovelTtsTransportAction>,
)

internal fun resolveNovelTtsNotificationLayoutSnapshot(
    actions: List<NovelTtsTransportAction>,
): NovelTtsNotificationLayoutSnapshot {
    val compactActions = actions.take(3)
    return NovelTtsNotificationLayoutSnapshot(
        usesSystemMediaStyle = true,
        usesCustomRemoteViews = false,
        compactActionIndices = compactActions.indices.toList(),
        compactActions = compactActions,
        expandedActions = actions,
    )
}

class NovelTtsPlaybackServiceRuntime(
    private val controller: NovelTtsPlaybackController,
    private val audioFocusManager: NovelTtsAudioFocusController,
) {
    fun playbackState() = controller.state

    fun notificationState(): NovelTtsForegroundNotificationState {
        val currentState = controller.state.value
        val session = currentState.session
        return NovelTtsForegroundNotificationState(
            title = session?.model?.chapterTitle
                ?.takeIf { it.isNotBlank() }
                ?: session?.let { "Chapter ${it.chapterId}" }
                ?: "Novel TTS",
            text = session?.utterance?.text.orEmpty(),
            isPlaying = currentState.playbackState == NovelTtsPlaybackState.PLAYING,
        )
    }

    fun transportActions(): List<NovelTtsTransportAction> {
        val playbackState = controller.state.value.playbackState
        val playPauseAction = when (playbackState) {
            NovelTtsPlaybackState.PLAYING -> NovelTtsTransportAction.PAUSE
            else -> NovelTtsTransportAction.PLAY
        }
        return listOf(
            NovelTtsTransportAction.PREVIOUS,
            playPauseAction,
            NovelTtsTransportAction.NEXT,
            NovelTtsTransportAction.STOP,
        )
    }

    suspend fun requestPlaybackStart(): Boolean {
        val granted = audioFocusManager.requestPlaybackFocus()
        if (granted) {
            controller.resume()
        }
        return granted
    }

    suspend fun handleTransportAction(action: NovelTtsTransportAction) {
        when (action) {
            NovelTtsTransportAction.PREVIOUS -> controller.skipPrevious()
            NovelTtsTransportAction.PLAY -> requestPlaybackStart()
            NovelTtsTransportAction.PAUSE -> controller.pause()
            NovelTtsTransportAction.NEXT -> controller.skipNext()
            NovelTtsTransportAction.STOP -> {
                audioFocusManager.abandonPlaybackFocus()
                controller.stop()
            }
        }
    }
}

class NovelTtsPlaybackService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 20260408
        private const val ACTION_PREVIOUS = "eu.kanade.tachiyomi.ui.reader.novel.tts.action.PREVIOUS"
        private const val ACTION_PLAY = "eu.kanade.tachiyomi.ui.reader.novel.tts.action.PLAY"
        private const val ACTION_PAUSE = "eu.kanade.tachiyomi.ui.reader.novel.tts.action.PAUSE"
        private const val ACTION_NEXT = "eu.kanade.tachiyomi.ui.reader.novel.tts.action.NEXT"
        private const val ACTION_STOP = "eu.kanade.tachiyomi.ui.reader.novel.tts.action.STOP"
    }

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var runtime: NovelTtsPlaybackServiceRuntime? = null
    private var stateCollectionJob: Job? = null
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "NovelTtsPlaybackService").apply {
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        dispatchTransportAction(NovelTtsTransportAction.PLAY)
                    }

                    override fun onPause() {
                        dispatchTransportAction(NovelTtsTransportAction.PAUSE)
                    }

                    override fun onSkipToPrevious() {
                        dispatchTransportAction(NovelTtsTransportAction.PREVIOUS)
                    }

                    override fun onSkipToNext() {
                        dispatchTransportAction(NovelTtsTransportAction.NEXT)
                    }

                    override fun onStop() {
                        dispatchTransportAction(NovelTtsTransportAction.STOP)
                    }
                },
            )
            isActive = true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleIntent(mediaSession, intent)
        resolveTransportAction(intent?.action)?.let(::dispatchTransportAction)
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    fun bindRuntime(runtime: NovelTtsPlaybackServiceRuntime) {
        this.runtime = runtime
        stateCollectionJob?.cancel()
        stateCollectionJob = serviceScope.launch {
            runtime.playbackState().collectLatest {
                updateMediaSessionState()
                NotificationManagerCompat.from(this@NovelTtsPlaybackService)
                    .notify(NOTIFICATION_ID, buildNotification())
                if (
                    it.playbackState == NovelTtsPlaybackState.IDLE ||
                    it.playbackState == NovelTtsPlaybackState.COMPLETED
                ) {
                    stopForegroundPlayback()
                }
            }
        }
        updateMediaSessionState()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        stateCollectionJob?.cancel()
        mediaSession.release()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun dispatchTransportAction(action: NovelTtsTransportAction) {
        serviceScope.launch {
            runtime?.handleTransportAction(action)
            updateMediaSessionState()
            NotificationManagerCompat.from(this@NovelTtsPlaybackService)
                .notify(NOTIFICATION_ID, buildNotification())
            if (action == NovelTtsTransportAction.STOP) {
                stopForegroundPlayback()
            }
        }
    }

    private fun resolveTransportAction(action: String?): NovelTtsTransportAction? {
        return when (action) {
            ACTION_PREVIOUS -> NovelTtsTransportAction.PREVIOUS
            ACTION_PLAY -> NovelTtsTransportAction.PLAY
            ACTION_PAUSE -> NovelTtsTransportAction.PAUSE
            ACTION_NEXT -> NovelTtsTransportAction.NEXT
            ACTION_STOP -> NovelTtsTransportAction.STOP
            else -> null
        }
    }

    private fun updateMediaSessionState() {
        val notificationState = runtime?.notificationState()
            ?: NovelTtsForegroundNotificationState(title = "Novel TTS", text = "", isPlaying = false)
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_STOP,
            )
            .setState(
                if (notificationState.isPlaying) {
                    PlaybackStateCompat.STATE_PLAYING
                } else {
                    PlaybackStateCompat.STATE_PAUSED
                },
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                1f,
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun buildNotification() = notificationBuilder(Notifications.CHANNEL_COMMON) {
        val notificationState = runtime?.notificationState()
            ?: NovelTtsForegroundNotificationState(
                title = "Novel TTS",
                text = "",
                isPlaying = false,
            )
        val transportActions = runtime?.transportActions().orEmpty()
        val layoutSnapshot = resolveNovelTtsNotificationLayoutSnapshot(transportActions)
        layoutSnapshot.expandedActions.forEach { action ->
            addAction(buildNotificationAction(action))
        }
        setSmallIcon(R.drawable.ic_play_arrow_24dp)
        setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
        setContentTitle(notificationState.title)
        setContentText(notificationState.text)
        setOngoing(notificationState.isPlaying)
        setOnlyAlertOnce(true)
        setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowCancelButton(true)
                .setCancelButtonIntent(createServicePendingIntent(ACTION_STOP))
                .setShowActionsInCompactView(*layoutSnapshot.compactActionIndices.toIntArray()),
        )
        setDeleteIntent(createServicePendingIntent(ACTION_STOP))
    }.build()

    private fun buildNotificationAction(action: NovelTtsTransportAction): NotificationCompat.Action {
        return NotificationCompat.Action(
            resolveNotificationActionIcon(action),
            resolveNotificationActionTitle(action),
            createServicePendingIntent(resolveNotificationActionIntent(action)),
        )
    }

    private fun resolveNotificationActionIcon(action: NovelTtsTransportAction): Int {
        return when (action) {
            NovelTtsTransportAction.PREVIOUS -> R.drawable.ic_skip_previous_24dp
            NovelTtsTransportAction.PLAY -> R.drawable.ic_play_arrow_24dp
            NovelTtsTransportAction.PAUSE -> R.drawable.ic_pause_24dp
            NovelTtsTransportAction.NEXT -> R.drawable.ic_skip_next_24dp
            NovelTtsTransportAction.STOP -> R.drawable.ic_close_24dp
        }
    }

    private fun resolveNotificationActionTitle(action: NovelTtsTransportAction): String {
        return when (action) {
            NovelTtsTransportAction.PREVIOUS -> "Previous"
            NovelTtsTransportAction.PLAY -> "Play"
            NovelTtsTransportAction.PAUSE -> "Pause"
            NovelTtsTransportAction.NEXT -> "Next"
            NovelTtsTransportAction.STOP -> "Stop"
        }
    }

    private fun resolveNotificationActionIntent(action: NovelTtsTransportAction): String {
        return when (action) {
            NovelTtsTransportAction.PREVIOUS -> ACTION_PREVIOUS
            NovelTtsTransportAction.PLAY -> ACTION_PLAY
            NovelTtsTransportAction.PAUSE -> ACTION_PAUSE
            NovelTtsTransportAction.NEXT -> ACTION_NEXT
            NovelTtsTransportAction.STOP -> ACTION_STOP
        }
    }

    private fun createServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, NovelTtsPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopForegroundPlayback() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    inner class LocalBinder : Binder() {
        fun service(): NovelTtsPlaybackService = this@NovelTtsPlaybackService
    }
}
