package eu.kanade.tachiyomi.ui.reader.viewer.panel

/**
 * Pure state machine governing frame-by-frame guided panel navigation on a single manga page.
 */
class GuidedPanelController {

    sealed interface StepResult {
        data class Focus(
            val panel: PanelRect,
            val index: Int,
            val total: Int,
        ) : StepResult

        data object NextPage : StepResult
        data object PrevPage : StepResult
    }

    /**
     * Current focused panel index, or `-1` when showing the full-page overview.
     */
    var currentIndex: Int = -1
        private set

    val isFocused: Boolean
        get() = currentIndex >= 0

    fun reset() {
        currentIndex = -1
    }

    /**
     * Enters a newly selected page in guided mode: focuses Panel 0 when advancing forward, or the
     * last panel when stepping back from the following page. Returns null if the page has fewer
     * than 2 panels (full-page fallback).
     */
    fun enterPage(panels: List<PanelRect>, fromEnd: Boolean): StepResult.Focus? {
        if (panels.size < 2) {
            currentIndex = -1
            return null
        }
        val targetIndex = if (fromEnd) panels.lastIndex else 0
        currentIndex = targetIndex
        return StepResult.Focus(
            panel = panels[targetIndex],
            index = targetIndex,
            total = panels.size,
        )
    }

    /**
     * Advances to the next panel in reading order, or signals [StepResult.NextPage] once the final
     * panel on the page has been read.
     */
    fun stepForward(panels: List<PanelRect>): StepResult {
        if (panels.size < 2) {
            currentIndex = -1
            return StepResult.NextPage
        }
        val next = currentIndex + 1
        if (next >= panels.size) {
            return StepResult.NextPage
        }
        currentIndex = next
        return StepResult.Focus(
            panel = panels[next],
            index = next,
            total = panels.size,
        )
    }

    /**
     * Moves to the previous panel in reading order, or signals [StepResult.PrevPage] when already
     * at the first panel or full-page overview.
     */
    fun stepBackward(panels: List<PanelRect>): StepResult {
        if (panels.size < 2 || currentIndex <= 0) {
            currentIndex = -1
            return StepResult.PrevPage
        }
        val prev = currentIndex - 1
        currentIndex = prev
        return StepResult.Focus(
            panel = panels[prev],
            index = prev,
            total = panels.size,
        )
    }
}
