package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection

internal object NovelReaderSelectionGestureArbiter {

    fun shouldInterceptTap(
        activeSelection: NovelSelectedTextSelection?,
    ): Boolean {
        return activeSelection != null
    }

    fun shouldPromoteSelectionCandidate(
        elapsedMillis: Long,
        movedDistancePx: Float,
        touchSlopPx: Float,
        longPressTimeoutMillis: Long,
    ): Boolean {
        if (elapsedMillis < longPressTimeoutMillis) return false
        return movedDistancePx <= touchSlopPx
    }

    fun shouldHandlePlainTap(
        elapsedMillis: Long,
        movedDistancePx: Float,
        touchSlopPx: Float,
        longPressTimeoutMillis: Long,
    ): Boolean {
        if (elapsedMillis >= longPressTimeoutMillis) return false
        return movedDistancePx <= touchSlopPx
    }

    fun shouldSuppressRendererSurface(
        activeSelection: NovelSelectedTextSelection?,
        renderer: NovelSelectedTextRenderer,
    ): Boolean {
        return activeSelection?.renderer == renderer
    }

    fun shouldRestoreGestureOwnership(
        activeSelection: NovelSelectedTextSelection?,
    ): Boolean {
        return activeSelection == null
    }

    fun shouldCancelReaderMotionOnSelectionStart(
        pageTurnIntentActive: Boolean,
        autoScrollIntentActive: Boolean,
    ): Boolean {
        return pageTurnIntentActive || autoScrollIntentActive
    }
}
