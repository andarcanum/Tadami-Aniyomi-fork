package eu.kanade.tachiyomi.ui.reels

/**
 * Contract v17 sticky-cursor pagination shared by every host surface (feed, niches, album
 * screen, album search, album catalog). The first non-null token locks the whole generation
 * into cursor mode; a null token while the source still claims more pages is a protocol
 * violation that stops pagination instead of replaying the head page forever.
 */
internal data class ReelsPagination(
    val nextPage: Int = 1,
    val nextCursor: String? = null,
    val cursorMode: Boolean = false,
    val canLoadMore: Boolean = true,
) {
    /** Advances after one successful page. [cursorLost] marks a sticky-protocol violation. */
    fun advance(hasNextPage: Boolean, nextCursor: String?): ReelsPaginationStep {
        val locked = cursorMode || nextCursor != null
        val lost = locked && nextCursor == null && hasNextPage
        return ReelsPaginationStep(
            pagination = copy(
                nextPage = nextPage + 1,
                nextCursor = nextCursor,
                cursorMode = locked,
                canLoadMore = hasNextPage && !lost,
            ),
            cursorLost = lost,
        )
    }
}

internal data class ReelsPaginationStep(
    val pagination: ReelsPagination,
    val cursorLost: Boolean,
)
