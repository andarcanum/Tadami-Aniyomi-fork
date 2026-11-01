package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import java.util.Date

/**
 * The two pure [ShortVideoItem] <-> [ReelsCandidate] conversions used by the round-robin feed.
 *
 * These are the exact conversions the legacy ScreenModel already performs. The round-robin
 * coordinator reuses them so both feed routes store identical metadata; transport hints and
 * the captured context key are added by the coordinator, not here.
 */
object ReelsCandidateMapper {

    fun ShortVideoItem.toCandidate(scope: String, sourceId: Long, now: Date): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = scope,
            sourceId = sourceId,
            itemId = id,
            title = title,
            author = author,
            videoUrl = videoUrl,
            videoUrlHd = videoUrlHd,
            posterUrl = posterUrl,
            posterUrlVertical = posterUrlVertical,
            webUrl = webUrl,
            durationSec = durationSec?.toDouble(),
            hasAudio = hasAudio,
            createdAtEpochSec = createdAtEpochSec,
            discoveredAt = now,
            lastValidatedAt = now,
            availability = ReelsCandidate.AVAILABILITY_AVAILABLE,
            retryAfter = null,
            resumePositionMs = null,
            metadataSchemaVersion = ReelsForYou.METADATA_SCHEMA_VERSION,
            tagsJson = Json.encodeToString(tags),
            viewsCount = viewsCount,
            likesCount = likesCount,
            isAuthorVerified = isAuthorVerified,
        )
    }

    fun ReelsCandidate.toShortVideoItem(): ShortVideoItem {
        return ShortVideoItem(
            id = itemId,
            title = title,
            author = author,
            videoUrl = videoUrl,
            videoUrlHd = videoUrlHd,
            posterUrl = posterUrl.orEmpty(),
            posterUrlVertical = posterUrlVertical,
            durationSec = durationSec?.toFloat(),
            hasAudio = hasAudio,
            tags = runCatching { Json.decodeFromString<List<String>>(tagsJson) }.getOrDefault(emptyList()),
            viewsCount = viewsCount,
            likesCount = likesCount,
            isAuthorVerified = isAuthorVerified,
            webUrl = webUrl,
            createdAtEpochSec = createdAtEpochSec,
        )
    }
}
