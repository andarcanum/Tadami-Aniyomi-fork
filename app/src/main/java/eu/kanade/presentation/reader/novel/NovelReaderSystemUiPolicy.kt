package eu.kanade.presentation.reader.novel

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

    fun clear() {
        internalChapterReplacePending = false
    }
}

internal object NovelReaderChapterHandoffPolicy {
    @Volatile
    private var internalChapterHandoffPending = false

    fun markInternalChapterHandoff() {
        internalChapterHandoffPending = true
    }

    fun consumeInternalChapterHandoff(): Boolean {
        val pending = internalChapterHandoffPending
        internalChapterHandoffPending = false
        return pending
    }

    fun clear() {
        internalChapterHandoffPending = false
    }
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

internal fun shouldRestoreSystemBarsOnDispose(
    isInternalChapterReplace: Boolean,
): Boolean {
    return !isInternalChapterReplace
}

internal fun shouldRestoreSavedPageReaderProgress(
    isInternalChapterHandoff: Boolean,
): Boolean {
    return !isInternalChapterHandoff
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
