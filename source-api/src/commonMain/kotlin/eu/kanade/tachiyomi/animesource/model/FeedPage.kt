package eu.kanade.tachiyomi.animesource.model

import java.io.Serializable

data class FeedPage(
    val videos: List<ShortVideoItem>,
    val hasNextPage: Boolean,
    /**
     * Opaque continuation token for cursor-based sources. Protocol (contract v17):
     * 1. The first response with nextCursor != null locks the host into cursor mode for
     *    this feed generation; all later requests pass the last returned token verbatim.
     * 2. In cursor mode, nextCursor == null && hasNextPage == true is a protocol violation:
     *    the host logs WARN and stops paginating.
     * 3. hasNextPage == false is terminal and authoritative in both modes.
     * Page-int sources must always return null here.
     */
    val nextCursor: String? = null,
) : Serializable
