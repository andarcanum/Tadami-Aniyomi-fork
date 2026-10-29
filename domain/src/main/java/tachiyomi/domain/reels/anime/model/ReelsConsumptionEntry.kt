package tachiyomi.domain.reels.anime.model

import java.util.Date

/**
 * Durable For You interaction summary for one (account scope, source, item).
 *
 * [watchedQualifiedAt] and [skippedForwardAt] are independent facts: a deliberate skip after
 * a qualified watch must not erase the watch, and a rewatch must not rewrite the original
 * timestamp. [isConsumed] is what excludes the item from the automatic For You feed.
 */
data class ReelsConsumptionEntry(
    val accountScopeId: String,
    val sourceId: Long,
    val itemId: String,
    val watchedQualifiedAt: Date?,
    val skippedForwardAt: Date?,
    val lastActionAt: Date,
    val lastSurface: String?,
    val policyVersion: Int,
    val historyGeneration: Long,
) {
    val isConsumed: Boolean
        get() = watchedQualifiedAt != null || skippedForwardAt != null
}
