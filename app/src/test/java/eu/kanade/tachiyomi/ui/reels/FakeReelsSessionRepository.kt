package eu.kanade.tachiyomi.ui.reels

import tachiyomi.domain.reels.anime.model.ReelsActiveCheckpoint
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsQueuedCandidate
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionWindow
import tachiyomi.domain.reels.anime.repository.ReelsSessionRepository
import java.util.Date
import java.util.UUID

class FakeReelsSessionRepository : ReelsSessionRepository {

    var activeCheckpoint: ReelsActiveCheckpoint? = null
    val queued = mutableListOf<ReelsQueuedCandidate>()
    val checkpoints = mutableListOf<ReelsActiveCheckpoint>()
    var currentHandle: ReelsSessionHandle? = null
    var appendCount = 0

    override suspend fun open(
        key: ReelsSessionKey,
        ownerNonce: String,
        historyGeneration: Long,
        reset: Boolean,
    ): ReelsSessionHandle {
        val sessionId = if (reset || currentHandle == null) UUID.randomUUID().toString() else currentHandle!!.sessionId
        if (reset) {
            queued.clear()
            activeCheckpoint = null
        }
        val handle = ReelsSessionHandle(key, sessionId, ownerNonce, historyGeneration)
        currentHandle = handle
        return handle
    }

    override suspend fun active(handle: ReelsSessionHandle): ReelsActiveCheckpoint? {
        return activeCheckpoint
    }

    override suspend fun window(
        handle: ReelsSessionHandle,
        afterOrdinal: Long,
        limit: Int,
    ): ReelsSessionWindow {
        val eligible = queued.filter { it.ordinal > afterOrdinal }
        val paged = eligible.take(limit)
        val nextOrd = paged.lastOrNull()?.ordinal ?: afterOrdinal
        val hasMore = eligible.size > limit
        return ReelsSessionWindow(
            entries = paged,
            nextOrdinal = nextOrd,
            hasMore = hasMore,
            deferredCount = 0,
        )
    }

    override suspend fun appendPage(
        handle: ReelsSessionHandle,
        candidates: List<ReelsCandidate>,
    ): Int {
        var maxOrd = queued.maxOfOrNull { it.ordinal } ?: 0L
        var added = 0
        for (c in candidates) {
            if (queued.any { it.candidate.itemId == c.itemId }) continue
            maxOrd++
            queued.add(ReelsQueuedCandidate(maxOrd, c))
            added++
        }
        appendCount += added
        return added
    }

    override suspend fun checkpoint(
        handle: ReelsSessionHandle,
        active: ReelsActiveCheckpoint,
    ): Boolean {
        checkpoints.add(active)
        activeCheckpoint = active
        return true
    }

    override suspend fun setAvailability(
        handle: ReelsSessionHandle,
        itemId: String,
        availability: String,
        retryAfter: Date?,
    ) {
    }

    override suspend fun invalidate(handle: ReelsSessionHandle, dropCheckpoint: Boolean) {
        if (dropCheckpoint) {
            activeCheckpoint = null
        }
        currentHandle = null
    }

    override suspend fun clearScope(scope: String) {
        queued.clear()
        activeCheckpoint = null
        currentHandle = null
    }
}
