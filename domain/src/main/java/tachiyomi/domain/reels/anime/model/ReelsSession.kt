package tachiyomi.domain.reels.anime.model

import java.io.Serializable

data class ReelsSessionKey(
    val accountScopeId: String,
    val sourceId: Long,
    val contextKey: String,
) : Serializable

data class ReelsSessionHandle(
    val key: ReelsSessionKey,
    val sessionId: String,
    val ownerNonce: String,
    val historyGeneration: Long,
) : Serializable

data class ReelsActiveCheckpoint(
    val item: ReelsCandidate,
    val positionMs: Long,
    val completed: Boolean,
    val sequence: Long,
) : Serializable

data class ReelsQueuedCandidate(
    val ordinal: Long,
    val candidate: ReelsCandidate,
) : Serializable

data class ReelsSessionWindow(
    val entries: List<ReelsQueuedCandidate>,
    val nextOrdinal: Long,
    val hasMore: Boolean,
    val deferredCount: Int,
) : Serializable

class ReelsInboxCapacityException(
    val required: Long,
    val available: Long,
) : IllegalStateException("Reels inbox capacity exceeded: required $required, available $available")

class ReelsSessionStaleException : IllegalStateException("Reels session handle is stale or owner fence failed")
