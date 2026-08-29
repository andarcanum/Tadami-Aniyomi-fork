package eu.kanade.tachiyomi.ui.reader.novel

import tachiyomi.domain.items.novelchapter.model.NovelChapter

/**
 * Windowed chapter list management for novels with thousands of chapters.
 *
 * Instead of loading all chapters into memory, keeps only a sliding window
 * of ±[DEFAULT_WINDOW_RADIUS] around the current chapter. Navigation within
 * the window is instant; navigating beyond it triggers a reload.
 */
object NovelReaderChapterWindow {

    const val DEFAULT_WINDOW_RADIUS = 50

    data class NavigationResult(
        val newWindow: List<NovelChapter>,
        val newCurrentChapter: NovelChapter,
        val reloadRequired: Boolean,
    )

    fun resolveWindow(
        chapters: List<NovelChapter>,
        currentChapterId: Long,
        windowRadius: Int,
    ): List<NovelChapter> {
        val centerIndex = chapters.indexOfFirst { it.id == currentChapterId }
        if (centerIndex < 0) return emptyList()

        val start = (centerIndex - windowRadius).coerceAtLeast(0)
        val end = (centerIndex + windowRadius + 1).coerceAtMost(chapters.size)

        return chapters.subList(start, end)
    }

    fun navigate(
        currentChapterId: Long,
        allChapters: List<NovelChapter>,
        direction: Int, // -1 for previous, +1 for next
        windowRadius: Int,
    ): NavigationResult {
        if (allChapters.isEmpty()) {
            return NavigationResult(
                newWindow = emptyList(),
                newCurrentChapter = NovelChapter.create().copy(id = currentChapterId),
                reloadRequired = false,
            )
        }
        val currentIndex = allChapters.indexOfFirst { it.id == currentChapterId }
        if (currentIndex < 0) return fallback(allChapters, currentChapterId, direction, windowRadius)
        val targetIndex = (currentIndex + direction).coerceIn(0, allChapters.lastIndex)
        val targetChapter = allChapters[targetIndex]

        val newWindow = resolveWindow(
            chapters = allChapters,
            currentChapterId = targetChapter.id,
            windowRadius = windowRadius,
        )
        return NavigationResult(
            newWindow = newWindow,
            newCurrentChapter = targetChapter,
            reloadRequired = true,
        )
    }

    private fun fallback(
        allChapters: List<NovelChapter>,
        currentChapterId: Long,
        direction: Int,
        windowRadius: Int,
    ): NavigationResult {
        val targetIndex = if (direction < 0) 0 else allChapters.lastIndex
        val target = allChapters.getOrNull(targetIndex) ?: NovelChapter.create().copy(id = currentChapterId)
        val window = resolveWindow(allChapters, target.id, windowRadius)
        return NavigationResult(window, target, reloadRequired = true)
    }
}

internal fun novelReaderNovelCompleted(
    fullChapterList: List<NovelChapter>,
    visibleWindow: List<NovelChapter>,
): Boolean {
    return fullChapterList
        .ifEmpty { visibleWindow }
        .all { it.read }
}
