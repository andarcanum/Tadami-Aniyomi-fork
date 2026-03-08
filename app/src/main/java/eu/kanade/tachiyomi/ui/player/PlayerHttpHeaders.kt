package eu.kanade.tachiyomi.ui.player

import okhttp3.Headers

internal data class PlaybackHttpOptions(
    val userAgent: String?,
    val referrer: String?,
    val headers: Map<String, String>,
)

internal fun mergeAndSanitizePlaybackHeaders(
    sourceHeaders: Headers,
    videoHeaders: Headers?,
): Map<String, String> {
    val mergedHeaders = linkedMapOf<String, String>()

    sourceHeaders.names().forEach { name ->
        sourceHeaders[name]?.let { value -> mergedHeaders[name.lowercase()] = value }
    }
    videoHeaders?.names()?.forEach { name ->
        videoHeaders[name]?.let { value -> mergedHeaders[name.lowercase()] = value }
    }

    return mergedHeaders
        .filterKeys { key -> !key.startsWith("X-AnimeGO-", ignoreCase = true) }
}

internal fun sanitizePlaybackHeaders(headers: Headers): Map<String, String> {
    return headers.toMultimap()
        .mapValues { (_, values) -> values.firstOrNull().orEmpty() }
        .filterKeys { key -> !key.startsWith("X-AnimeGO-", ignoreCase = true) }
}

internal fun toPlaybackHttpOptions(
    sourceHeaders: Headers,
    videoHeaders: Headers?,
): PlaybackHttpOptions {
    val mergedHeaders = mergeAndSanitizePlaybackHeaders(
        sourceHeaders = sourceHeaders,
        videoHeaders = videoHeaders,
    )

    return PlaybackHttpOptions(
        userAgent = mergedHeaders["user-agent"],
        referrer = mergedHeaders["referer"],
        headers = mergedHeaders.filterKeys { key ->
            key != "user-agent" && key != "referer"
        },
    )
}
