package eu.kanade.tachiyomi.ui.reader.loader

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun shouldBypassChapterPageListCache(sourceBaseUrl: String?): Boolean {
    val host = sourceBaseUrl
        ?.toHttpUrlOrNull()
        ?.host
        ?: return false
    return host.equals("inkstory.net", ignoreCase = true) ||
        host.equals("api.inkstory.net", ignoreCase = true) ||
        host.equals("nartag.com", ignoreCase = true)
}
