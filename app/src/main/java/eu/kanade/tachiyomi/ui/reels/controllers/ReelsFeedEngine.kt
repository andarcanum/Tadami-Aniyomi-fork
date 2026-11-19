package eu.kanade.tachiyomi.ui.reels.controllers

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.reels.ReelsQualityController
import eu.kanade.tachiyomi.ui.reels.ReelsTasteEngine

/**
 * Unified feed helper facade coordinating on-device "For You" taste reranking and long-session
 * memory window pruning across both single-source and multi-source Mix feeds.
 */
internal class ReelsFeedEngine(
    val tasteEngine: ReelsTasteEngine = ReelsTasteEngine(),
) {
    /**
     * Re-ranks an incoming single-source batch for sources without server-side personalization.
     * Preserves source order verbatim while [tasteEngine] has no signals yet.
     */
    fun rerankSingleSource(
        incoming: List<ShortVideoItem>,
        recentTail: List<ShortVideoItem>,
        allowPersonalization: Boolean,
    ): List<ShortVideoItem> {
        if (!allowPersonalization || incoming.size <= 1 || !tasteEngine.hasSignals()) return incoming
        return tasteEngine.rerankItems(incoming, recentTail = recentTail)
    }

    /** Re-ranks a Mix batch of `(sourceId, item)` slots while preserving per-slot provenance. */
    fun <T> rerankMixSlots(
        slots: List<T>,
        itemOf: (T) -> ShortVideoItem,
        recentTail: List<ShortVideoItem>,
    ): List<T> {
        if (slots.size <= 1 || !tasteEngine.hasSignals()) return slots
        return tasteEngine.rerank(slots, itemOf = itemOf, recentTail = recentTail)
    }

    /**
     * Prunes [ReelsQualityController] maps outside a `±[radius]` window around [activeIndex] so
     * hour-long sessions (500–1000+ reels) stay memory-bounded.
     */
    fun pruneQualityWindows(
        items: List<ShortVideoItem>,
        activeIndex: Int,
        singleController: ReelsQualityController,
        mixControllers: Map<Long, ReelsQualityController>,
        radius: Int = ReelsQualityController.QUALITY_WINDOW_RADIUS,
    ) {
        if (items.size <= radius * 2 + 4) return
        val from = (activeIndex - radius).coerceAtLeast(0)
        val to = (activeIndex + radius).coerceAtMost(items.lastIndex)
        if (from > to) return
        val keepIds = HashSet<String>((to - from + 1) * 2)
        for (i in from..to) {
            keepIds.add(items[i].id)
        }
        singleController.pruneOutsideWindow(keepIds)
        mixControllers.values.forEach { it.pruneOutsideWindow(keepIds) }
    }
}
