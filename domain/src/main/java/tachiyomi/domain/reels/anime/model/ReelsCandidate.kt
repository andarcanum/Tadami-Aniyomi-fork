package tachiyomi.domain.reels.anime.model

import java.util.Date

/**
 * One durable candidate the host actually received from a source.
 *
 * The item key ([accountScopeId], [sourceId], [itemId]) is the identity: signed media URLs,
 * proxy ports and cache paths are transport details that rotate and must never be used as
 * identity. [videoUrl] is therefore a cache hint, re-resolved before playback when the
 * source exposes a resolver.
 */
data class ReelsCandidate(
    val accountScopeId: String,
    val sourceId: Long,
    val itemId: String,
    val title: String?,
    val author: String?,
    val videoUrl: String,
    val videoUrlHd: String?,
    val posterUrl: String?,
    val posterUrlVertical: String?,
    val webUrl: String?,
    val durationSec: Double?,
    val hasAudio: Boolean,
    val createdAtEpochSec: Long?,
    val discoveredAt: Date,
    val lastValidatedAt: Date?,
    val availability: String,
    val retryAfter: Date?,
    val resumePositionMs: Long?,
    val metadataSchemaVersion: Int,
) {
    companion object {
        /** Confirmed playable/known item. */
        const val AVAILABILITY_AVAILABLE = "available"

        /** Temporarily inaccessible (retryable); must never be treated as permanently gone. */
        const val AVAILABILITY_DEFERRED = "deferred"

        /** Confirmed removed by the source; kept separately from consumption. */
        const val AVAILABILITY_UNAVAILABLE = "unavailable"
    }
}
