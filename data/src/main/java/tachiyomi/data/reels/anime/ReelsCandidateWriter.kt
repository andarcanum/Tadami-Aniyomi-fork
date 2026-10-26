package tachiyomi.data.reels.anime

import dataanime.Reels_candidate
import dataanime.Reels_candidateQueries
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsInboxCapacityException
import tachiyomi.mi.data.AnimeDatabase

/**
 * Shared transactional writer for reels candidates.
 * Enforces all-or-nothing capacity, deduplication, consumed remnant pruning and scope validation.
 */
object ReelsCandidateWriter {

    const val MAX_CANDIDATES_PER_SOURCE = 2_000L

    fun writeCandidates(
        db: AnimeDatabase,
        scope: String,
        sourceId: Long,
        candidates: List<ReelsCandidate>,
    ): Int {
        if (candidates.isEmpty()) return 0

        val queries = db.reels_candidateQueries

        // Safely prune authoritative consumed rows to free capacity before checking cap
        queries.pruneConsumedRemnants(scope, sourceId)

        // Deduplicate input items by itemId
        val distinct = candidates.distinctBy { it.itemId }
        for (c in distinct) {
            require(c.accountScopeId == scope && c.sourceId == sourceId) {
                "Candidate ${c.itemId} scope/source mismatch: expected ($scope, $sourceId) but got (${c.accountScopeId}, ${c.sourceId})"
            }
        }

        val known = queries.existingIds(scope, sourceId, distinct.map { it.itemId })
            .executeAsList()
            .toHashSet()

        val currentCount = queries.countForSource(scope, sourceId).executeAsOne()
        val available = (MAX_CANDIDATES_PER_SOURCE - currentCount).coerceAtLeast(0L)
        val newCount = distinct.count { it.itemId !in known }.toLong()

        if (newCount > available) {
            throw ReelsInboxCapacityException(required = newCount, available = available)
        }

        for (candidate in distinct) {
            upsert(queries, candidate)
        }

        return distinct.size
    }

    fun upsert(queries: Reels_candidateQueries, candidate: ReelsCandidate) {
        queries.upsert(
            account_scope_id = candidate.accountScopeId,
            source_id = candidate.sourceId,
            item_id = candidate.itemId,
            title = candidate.title,
            author = candidate.author,
            video_url = candidate.videoUrl,
            video_url_hd = candidate.videoUrlHd,
            poster_url = candidate.posterUrl,
            poster_url_vertical = candidate.posterUrlVertical,
            web_url = candidate.webUrl,
            duration_sec = candidate.durationSec,
            has_audio = candidate.hasAudio,
            created_at_epoch_sec = candidate.createdAtEpochSec,
            discovered_at = candidate.discoveredAt,
            last_validated_at = candidate.lastValidatedAt ?: candidate.discoveredAt,
            availability = candidate.availability,
            retry_after = candidate.retryAfter,
            resume_position_ms = candidate.resumePositionMs,
            metadata_schema_version = candidate.metadataSchemaVersion.toLong(),
            tags_json = candidate.tagsJson,
            views_count = candidate.viewsCount,
            likes_count = candidate.likesCount,
            is_author_verified = candidate.isAuthorVerified,
            captured_context_key = candidate.capturedContextKey,
        )
    }

    fun mapCandidate(row: Reels_candidate): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = row.account_scope_id,
            sourceId = row.source_id,
            itemId = row.item_id,
            title = row.title,
            author = row.author,
            videoUrl = row.video_url,
            videoUrlHd = row.video_url_hd,
            posterUrl = row.poster_url,
            posterUrlVertical = row.poster_url_vertical,
            webUrl = row.web_url,
            durationSec = row.duration_sec,
            hasAudio = row.has_audio,
            createdAtEpochSec = row.created_at_epoch_sec,
            discoveredAt = row.discovered_at,
            lastValidatedAt = row.last_validated_at,
            availability = row.availability,
            retryAfter = row.retry_after,
            resumePositionMs = row.resume_position_ms,
            metadataSchemaVersion = row.metadata_schema_version.toInt(),
            tagsJson = row.tags_json,
            viewsCount = row.views_count,
            likesCount = row.likes_count,
            isAuthorVerified = row.is_author_verified,
            capturedContextKey = row.captured_context_key,
        )
    }
}
