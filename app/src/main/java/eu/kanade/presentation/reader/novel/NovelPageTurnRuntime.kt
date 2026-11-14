@file:OptIn(eu.wewox.pagecurl.ExperimentalPageCurlApi::class)

package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import eu.wewox.pagecurl.page.PageCurlState

internal data class NovelPageTurnLayoutKey(
    val chapterId: Long,
    val layoutGeneration: Long,
    val constraints: Constraints,
    val contentPages: List<NovelPageContentPage>,
    val spreadColumns: Int,
    val virtualPageCount: Int,
    val hasPreviousChapter: Boolean,
    val hasNextChapter: Boolean,
    val horizontalPadding: DpOffset,
)

/** Keeps obsolete NonCancellable pagecurl completions away from the active reader. */
internal class NovelPageTurnRuntime(
    primaryState: PageCurlState,
    secondaryState: PageCurlState? = null,
) {
    private val initialGeneration = Generation(primaryState, secondaryState)
    private var activeGeneration = initialGeneration

    private val savedPages: List<Int>
        get() = listOf(activeGeneration.primaryState.current, activeGeneration.secondaryState?.current ?: -1)
    private var activeLayoutKey: NovelPageTurnLayoutKey? = null
    private var lastRequestedPage = -1

    fun generation(
        layoutKey: NovelPageTurnLayoutKey,
        initialPrimaryPage: Int,
        initialSecondaryPage: Int? = null,
        requestedPage: Int = -1,
    ): Generation {
        val initialized = activeLayoutKey != null
        val newRestore = requestedPage >= 0 && requestedPage != lastRequestedPage
        if (initialized && (
                activeLayoutKey != layoutKey || newRestore || !activeGeneration.isActive
                )
        ) {
            activeGeneration.dispose()
            activeGeneration = Generation(
                primaryState = PageCurlState(initialCurrent = initialPrimaryPage.coerceAtLeast(0)),
                secondaryState = initialSecondaryPage?.let {
                    PageCurlState(initialCurrent = it.coerceAtLeast(0))
                },
            )
        }
        activeLayoutKey = layoutKey
        lastRequestedPage = requestedPage
        return activeGeneration
    }

    companion object {
        val Saver = listSaver<NovelPageTurnRuntime, Int>(
            save = { it.savedPages },
            restore = { pages ->
                NovelPageTurnRuntime(
                    primaryState = PageCurlState(initialCurrent = pages[0]),
                    secondaryState = pages.getOrNull(1)?.takeIf { it >= 0 }?.let {
                        PageCurlState(initialCurrent = it)
                    },
                )
            },
        )
    }

    inner class Generation internal constructor(
        val primaryState: PageCurlState,
        val secondaryState: PageCurlState?,
    ) {
        private var disposed = false

        val isActive: Boolean
            get() = !disposed && activeGeneration === this

        fun publish(block: () -> Unit) {
            if (isActive) block()
        }

        fun dispose() {
            disposed = true
        }
    }
}
