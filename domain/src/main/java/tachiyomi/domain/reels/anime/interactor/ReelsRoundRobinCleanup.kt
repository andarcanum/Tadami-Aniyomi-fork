package tachiyomi.domain.reels.anime.interactor

import tachiyomi.domain.reels.anime.model.ReelsRoundRobinAnchor
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinFrame
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinTurn

/**
 * Shared pure cleanup of the persisted round-robin projection.
 *
 * A rewound source must be re-decided, not only re-validated: an earlier pump may have written
 * an explicit future skip (EMPTY/FILTER_BUDGET/HIDDEN/RETRY) for its slot, and such a skip
 * carries no item id, so [ReelsRoundRobinScheduler.repair] cannot release it. Dropping those
 * turns here keeps the durable and the in-memory adapters in lockstep. Played slots (at or
 * before the anchor) are never touched.
 */
object ReelsRoundRobinCleanup {

    fun clearFutureSkips(
        frames: List<ReelsRoundRobinFrame>,
        anchor: ReelsRoundRobinAnchor?,
        sources: Set<String>,
    ): List<ReelsRoundRobinFrame> {
        if (sources.isEmpty()) return frames
        return frames.map { frame ->
            val limit = playedLimit(frame, anchor)
            var turns: LinkedHashMap<String, ReelsRoundRobinTurn>? = null
            frame.members.forEachIndexed { index, member ->
                if (index <= limit || member !in sources) return@forEachIndexed
                val turn = frame.turns[member] ?: return@forEachIndexed
                if (turn.skip == null) return@forEachIndexed
                val updated = turns ?: LinkedHashMap<String, ReelsRoundRobinTurn>(frame.turns).also {
                    turns = it
                }
                updated.remove(member)
            }
            val updated = turns ?: return@map frame
            val ordered = LinkedHashMap<String, ReelsRoundRobinTurn>()
            frame.members.forEach { member -> updated[member]?.let { ordered[member] = it } }
            frame.copy(turns = ordered)
        }
    }

    /**
     * Drops the future turns that hold one of [itemIds]. Unlike [ReelsRoundRobinScheduler.repair]
     * this only touches the listed cards: an eligibility recheck must not release the later
     * reservations of the same source that are still eligible. Played slots are never touched.
     */
    fun removeFutureTurns(
        frames: List<ReelsRoundRobinFrame>,
        anchor: ReelsRoundRobinAnchor?,
        itemIds: Set<String>,
    ): List<ReelsRoundRobinFrame> {
        if (itemIds.isEmpty()) return frames
        return frames.map { frame ->
            val limit = playedLimit(frame, anchor)
            var turns: LinkedHashMap<String, ReelsRoundRobinTurn>? = null
            frame.members.forEachIndexed { index, member ->
                if (index <= limit) return@forEachIndexed
                val turn = frame.turns[member] ?: return@forEachIndexed
                val itemId = turn.itemId ?: return@forEachIndexed
                if (itemId !in itemIds) return@forEachIndexed
                val updated = turns ?: LinkedHashMap<String, ReelsRoundRobinTurn>(frame.turns).also {
                    turns = it
                }
                updated.remove(member)
            }
            val updated = turns ?: return@map frame
            val ordered = LinkedHashMap<String, ReelsRoundRobinTurn>()
            frame.members.forEach { member -> updated[member]?.let { ordered[member] = it } }
            frame.copy(turns = ordered)
        }
    }

    /** Item ids at or before the monotonic frontier. They are the played prefix, not the tail. */
    fun playedItemIds(
        frames: List<ReelsRoundRobinFrame>,
        anchor: ReelsRoundRobinAnchor?,
    ): Set<String> {
        val played = HashSet<String>()
        frames.forEach { frame ->
            val limit = playedLimit(frame, anchor)
            frame.members.forEachIndexed { index, member ->
                if (index <= limit) frame.turns[member]?.itemId?.let(played::add)
            }
        }
        return played
    }

    private fun playedLimit(frame: ReelsRoundRobinFrame, anchor: ReelsRoundRobinAnchor?): Int {
        if (anchor == null) return -1
        if (frame.number < anchor.round) return frame.members.lastIndex
        if (frame.number > anchor.round) return -1
        val byKey = frame.members.indexOf(anchor.sourceKey)
        if (byKey >= 0) return byKey
        return frame.members.indexOfFirst { frame.turns[it]?.itemId == anchor.itemId }
    }
}
