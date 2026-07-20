package eu.kanade.domain.entries.metadata

import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.source.model.SManga

/**
 * Metadata fields that can be imported from a remote tracker into Edit Info.
 * Null means "leave current field unchanged".
 */
data class TrackerMetadataDraft(
    val title: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val status: Long? = null,
    val trackerName: String,
) {
    /** True when at least one field can replace form values. */
    val hasAnyField: Boolean
        get() = title != null ||
            author != null ||
            artist != null ||
            description != null ||
            !genres.isNullOrEmpty() ||
            status != null
}

data class TrackerMetadataSource(
    val trackerId: Long,
    val trackerName: String,
    val remoteTitle: String,
)

sealed class TrackerMetadataFetchError {
    data object NoLinkedTracks : TrackerMetadataFetchError()
    data object TrackerNotLoggedIn : TrackerMetadataFetchError()
    data object NoSearchResults : TrackerMetadataFetchError()
    data object NoRemoteMatch : TrackerMetadataFetchError()
    data object EmptyMetadata : TrackerMetadataFetchError()
    data class Unexpected(val message: String?) : TrackerMetadataFetchError()
}

sealed class TrackerMetadataFetchOutcome {
    data class Success(val draft: TrackerMetadataDraft) : TrackerMetadataFetchOutcome()
    data class ChooseTracker(val sources: List<TrackerMetadataSource>) : TrackerMetadataFetchOutcome()
    data class Error(val error: TrackerMetadataFetchError) : TrackerMetadataFetchOutcome()
}

internal fun MangaTrackSearch.toMetadataDraft(trackerName: String): TrackerMetadataDraft {
    return TrackerMetadataDraft(
        title = title.takeIf { it.isNotBlank() },
        author = authors.joinToString(", ").takeIf { it.isNotBlank() },
        artist = artists.joinToString(", ").takeIf { it.isNotBlank() },
        description = summary.takeIf { it.isNotBlank() },
        genres = genres.map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() },
        status = mapPublishingStatus(publishing_status),
        trackerName = trackerName,
    )
}

internal fun AnimeTrackSearch.toMetadataDraft(trackerName: String): TrackerMetadataDraft {
    return TrackerMetadataDraft(
        title = title.takeIf { it.isNotBlank() },
        author = authors.joinToString(", ").takeIf { it.isNotBlank() },
        artist = artists.joinToString(", ").takeIf { it.isNotBlank() },
        description = summary.takeIf { it.isNotBlank() },
        genres = genres.map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() },
        status = mapPublishingStatus(publishing_status),
        trackerName = trackerName,
    )
}

/**
 * Heuristic map from free-form tracker publishing status strings to [SManga] status constants.
 * Returns null when unknown so the form keeps its current value.
 */
internal fun mapPublishingStatus(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim().lowercase()
    return when {
        s.contains("hiatus") ||
            s.contains("on hold") ||
            s.contains("on_hold") ||
            s.contains("paused") -> SManga.ON_HIATUS.toLong()
        s.contains("cancel") || s.contains("discontinu") || s.contains("dropped") ->
            SManga.CANCELLED.toLong()
        s.contains("not_yet") ||
            s.contains("not yet") ||
            s.contains("upcoming") ||
            s.contains("tba") ||
            s.contains("announced") -> SManga.UNKNOWN.toLong()
        s.contains("publishing finished") || s.contains("publishing_finished") ->
            SManga.PUBLISHING_FINISHED.toLong()
        s.contains("licensed") -> SManga.LICENSED.toLong()
        s.contains("complete") ||
            s.contains("finished") ||
            s.contains("ended") ||
            s == "done" -> SManga.COMPLETED.toLong()
        s.contains("releas") ||
            s.contains("ongoing") ||
            s.contains("publishing") ||
            s.contains("airing") ||
            s.contains("current") ||
            s.contains("serializing") ->
            SManga.ONGOING.toLong()
        else -> null
    }
}
