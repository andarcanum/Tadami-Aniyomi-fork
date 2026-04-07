package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
