package eu.kanade.presentation.reader.novel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal object NovelReaderSystemUiSession {
    @Volatile
    private var internalChapterReplacePending = false

    fun markInternalChapterReplace() {
        internalChapterReplacePending = true
    }

    fun consumeInternalChapterReplace(): Boolean {
        val pending = internalChapterReplacePending
        internalChapterReplacePending = false
        return pending
    }

    fun isInternalChapterReplacePending(): Boolean {
        return internalChapterReplacePending
    }

    fun clear() {
        internalChapterReplacePending = false
    }
}

internal object NovelReaderBackdropSession {
    var backgroundColor by mutableStateOf<Color?>(null)
        private set

    fun update(backgroundColor: Color?) {
        this.backgroundColor = backgroundColor
    }
}

internal object NovelReaderChapterHandoffPolicy {
    @Volatile
    private var pendingPageReaderHandoffTarget: NovelReaderPageReaderHandoffTarget? = null

    fun markInternalChapterHandoff(target: NovelReaderPageReaderHandoffTarget) {
        pendingPageReaderHandoffTarget = target
    }

    fun consumeInternalChapterHandoff(): NovelReaderPageReaderHandoffTarget {
        val pending = pendingPageReaderHandoffTarget ?: NovelReaderPageReaderHandoffTarget.SAVED
        pendingPageReaderHandoffTarget = null
        return pending
    }

    fun clear() {
        pendingPageReaderHandoffTarget = null
    }
}

internal object NovelReaderAutoScrollHandoffPolicy {
    @Volatile
    private var pendingHandoff: NovelAutoScrollHandoffState? = null

    fun prepareHandoff(
        fromChapterId: Long,
        targetChapterId: Long,
        speed: Int,
        requestedAtMs: Long = System.currentTimeMillis(),
    ) {
        pendingHandoff = NovelAutoScrollHandoffState(
            fromChapterId = fromChapterId,
            targetChapterId = targetChapterId,
            speed = speed.coerceIn(1, 100),
            requestedAtMs = requestedAtMs,
        )
    }

    fun consumeIfMatches(
        currentChapterId: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): NovelAutoScrollHandoffState? {
        val pending = pendingHandoff ?: return null
        if (pending.targetChapterId != currentChapterId || isAutoScrollHandoffExpired(pending.requestedAtMs, nowMs)) {
            pendingHandoff = null
            return null
        }
        pendingHandoff = null
        return pending
    }

    fun cancel() {
        pendingHandoff = null
    }
}

internal object NovelReaderTtsChapterHandoffPolicy {
    /**
     * A handoff mark describes one in-flight chapter switch, which completes in seconds even on
     * slow sources. If the switch never completes (reader closed mid-handoff, failed load), a mark
     * without a lifetime would wait for a later session that happens to open the same chapter and
     * spontaneously start TTS there.
     */
    private const val PENDING_RESTORE_TTL_MS = 300_000L

    @Volatile
    private var pendingRestoreChapterId: Long? = null

    @Volatile
    private var pendingRestoreRequestedAtMs: Long = 0L

    fun markPendingRestore(chapterId: Long, requestedAtMs: Long = System.currentTimeMillis()) {
        pendingRestoreChapterId = chapterId
        pendingRestoreRequestedAtMs = requestedAtMs
    }

    fun hasPendingRestore(chapterId: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        val pendingChapterId = pendingRestoreChapterId ?: return false
        if (pendingChapterId != chapterId) return false
        if (nowMs - pendingRestoreRequestedAtMs > PENDING_RESTORE_TTL_MS) {
            clear()
            return false
        }
        return true
    }

    fun consumePendingRestore(chapterId: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!hasPendingRestore(chapterId, nowMs)) return false
        clear()
        return true
    }

    fun clear() {
        pendingRestoreChapterId = null
        pendingRestoreRequestedAtMs = 0L
    }
}

internal enum class NovelReaderPageReaderHandoffTarget {
    SAVED,
    START,
    END,
}

internal data class ReaderSystemBarsState(
    val isLightStatusBars: Boolean,
    val isLightNavigationBars: Boolean,
    val systemBarsBehavior: Int,
)

internal fun resolveReaderExitSystemBarsState(
    captured: ReaderSystemBarsState?,
    current: ReaderSystemBarsState,
): ReaderSystemBarsState {
    return captured ?: current
}

internal fun resolveActiveReaderSystemBarsState(
    showReaderUi: Boolean,
    fullScreenMode: Boolean,
    base: ReaderSystemBarsState,
): ReaderSystemBarsState {
    if (showReaderUi) {
        return base.copy(
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }
    if (!fullScreenMode) {
        return base.copy(
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }
    return base.copy(
        isLightStatusBars = false,
        isLightNavigationBars = false,
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
    )
}

internal fun shouldHideSystemBars(
    fullScreenMode: Boolean,
    showReaderUi: Boolean,
): Boolean {
    return fullScreenMode && !showReaderUi
}

internal fun resolveReaderSystemUiFlag(
    activeValue: Boolean?,
    loadingValue: Boolean?,
    initialValue: Boolean?,
): Boolean {
    return activeValue ?: loadingValue ?: initialValue ?: false
}

internal fun shouldRestoreSystemBarsOnDispose(
    isInternalChapterReplace: Boolean,
): Boolean {
    return !isInternalChapterReplace
}

internal fun shouldRestoreSavedPageReaderProgress(
    chapterHandoffTarget: NovelReaderPageReaderHandoffTarget,
): Boolean {
    return chapterHandoffTarget == NovelReaderPageReaderHandoffTarget.SAVED
}

/**
 * The screen stays on while the reader's keep-screen-on setting is on, or while the dedicated TTS
 * setting is on and speech is actually playing (paused playback must not hold the screen).
 */
internal fun resolveReaderKeepScreenOn(
    keepScreenOn: Boolean,
    ttsKeepScreenOnDuringPlayback: Boolean,
    ttsPlaybackState: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState,
): Boolean {
    return keepScreenOn ||
        (
            ttsKeepScreenOnDuringPlayback &&
                ttsPlaybackState == eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState.PLAYING
            )
}

internal fun WindowInsetsControllerCompat.captureReaderSystemBarsState(): ReaderSystemBarsState {
    return ReaderSystemBarsState(
        isLightStatusBars = isAppearanceLightStatusBars,
        isLightNavigationBars = isAppearanceLightNavigationBars,
        systemBarsBehavior = systemBarsBehavior,
    )
}

internal fun WindowInsetsControllerCompat.restoreReaderSystemBarsState(
    state: ReaderSystemBarsState,
) {
    isAppearanceLightStatusBars = state.isLightStatusBars
    isAppearanceLightNavigationBars = state.isLightNavigationBars
    systemBarsBehavior = state.systemBarsBehavior
}

@Composable
internal fun SystemUIController(
    fullScreenMode: Boolean,
    keepScreenOn: Boolean,
    showReaderUi: Boolean,
) {
    val view = LocalView.current

    val capturedSystemBarsState = remember(view) { mutableStateOf<ReaderSystemBarsState?>(null) }
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val insetsController = if (window != null) {
            WindowCompat.getInsetsController(window, view)
        } else {
            null
        }
        if (capturedSystemBarsState.value == null && insetsController != null) {
            capturedSystemBarsState.value = insetsController.captureReaderSystemBarsState()
        }
        onDispose {
            val activity = view.context.findActivity() ?: return@onDispose
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, view)
            val internalChapterReplace = NovelReaderSystemUiSession.consumeInternalChapterReplace()
            val restoredState = resolveReaderExitSystemBarsState(
                captured = capturedSystemBarsState.value,
                current = insetsController.captureReaderSystemBarsState(),
            )
            insetsController.restoreReaderSystemBarsState(restoredState)
            if (shouldRestoreSystemBarsOnDispose(isInternalChapterReplace = internalChapterReplace)) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            if (!internalChapterReplace) {
                NovelReaderSystemUiSession.clear()
            }
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)
        if (capturedSystemBarsState.value == null) {
            capturedSystemBarsState.value = insetsController.captureReaderSystemBarsState()
        }
        val baseSystemBarsState = capturedSystemBarsState.value ?: insetsController.captureReaderSystemBarsState()
        val activeSystemBarsState = resolveActiveReaderSystemBarsState(
            showReaderUi = showReaderUi,
            fullScreenMode = fullScreenMode,
            base = baseSystemBarsState,
        )

        // Keep Screen On
        if (keepScreenOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Fullscreen Mode
        if (shouldHideSystemBars(fullScreenMode = fullScreenMode, showReaderUi = showReaderUi)) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        // Re-apply desired icon appearance after show/hide, as showing bars can
        // transiently restore prior icon mode on first reveal.
        insetsController.restoreReaderSystemBarsState(activeSystemBarsState)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
