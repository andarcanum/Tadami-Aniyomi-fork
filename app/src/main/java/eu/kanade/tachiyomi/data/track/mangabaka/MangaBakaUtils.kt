package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import java.time.LocalDate
import java.time.ZoneId

fun MangaTrack.toApiStatus() = when (status) {
    MangaBaka.CONSIDERING -> "considering"
    MangaBaka.COMPLETED -> "completed"
    MangaBaka.DROPPED -> "dropped"
    MangaBaka.PAUSED -> "paused"
    MangaBaka.PLAN_TO_READ -> "plan_to_read"
    MangaBaka.READING -> "reading"
    MangaBaka.REREADING -> "rereading"
    else -> throw NotImplementedError("Unknown status: $status")
}

fun parseIsoDateAsLocalStartOfDay(isoDate: String?, timeZone: ZoneId = ZoneId.systemDefault()): Long? {
    // The v1 API returns full ISO 8601 strings truncated at midnight UTC regardless of when the
    // change was actually made, so the date part must be interpreted as a local start-of-day to
    // avoid the dates drifting in negative-offset timezones.
    return isoDate
        ?.substringBefore("T")
        ?.let { LocalDate.parse(it).atStartOfDay(timeZone).toInstant().toEpochMilli() }
}
