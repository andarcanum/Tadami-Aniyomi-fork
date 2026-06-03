package eu.kanade.tachiyomi.util

import android.util.Log

private const val TITLE_COVER_FLOW_TAG = "TitleCoverFlow"

internal fun debugTitleCoverFlow(scope: String, message: String) {
    runCatching { Log.d(TITLE_COVER_FLOW_TAG, "[$scope] $message") }
}

internal fun previewTitleCoverValue(value: Any?): String {
    return when (value) {
        null -> "null"
        is String -> previewTitleCoverUrl(value)
        else -> value.toString().replace('\n', ' ').take(140)
    }
}

internal fun previewTitleCoverUrl(url: String?): String {
    if (url.isNullOrBlank()) return "null"
    return url
        .replace('\n', ' ')
        .take(140)
}
