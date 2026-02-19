package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun resolveNovelChapterWebUrl(
    chapterUrl: String,
    pluginSite: String?,
    novelUrl: String,
): String? {
    val rawChapterUrl = chapterUrl.trim()
    if (rawChapterUrl.isBlank()) return null

    rawChapterUrl.toHttpUrlOrNull()?.let { return it.toString() }

    val normalizedNovelBase = normalizeUrlBase(novelUrl)
    val normalizedSiteBase = normalizeUrlBase(pluginSite)
    val normalizedNovelDir = normalizedNovelBase?.let(::ensureTrailingSlash)
    val chapterIsRootRelative = rawChapterUrl.startsWith("/")

    val candidates = LinkedHashSet<String>().apply {
        if (chapterIsRootRelative) {
            normalizedSiteBase?.let(::add)
            normalizedNovelBase?.let(::add)
        } else {
            normalizedNovelBase?.let(::add)
            normalizedNovelDir?.let(::add)
            normalizedSiteBase?.let(::add)
        }
    }

    for (base in candidates) {
        resolveUrl(rawChapterUrl, base)
            .trim()
            .toHttpUrlOrNull()
            ?.let { return it.toString() }
    }

    return null
}

private fun normalizeUrlBase(base: String?): String? {
    val value = base?.trim().orEmpty()
    if (value.isBlank()) return null

    val hasScheme =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)

    val withScheme = if (hasScheme) {
        value
    } else {
        if (value.startsWith("/")) return null
        val hostCandidate = value.substringBefore('/')
        val looksLikeHost = hostCandidate.contains('.') || hostCandidate.equals("localhost", ignoreCase = true)
        if (!looksLikeHost) return null
        "https://$value"
    }

    return withScheme.toHttpUrlOrNull()?.toString()
}

private fun ensureTrailingSlash(url: String): String {
    val httpUrl = url.toHttpUrlOrNull() ?: return url
    return if (httpUrl.encodedPath.endsWith("/")) {
        httpUrl.toString()
    } else {
        httpUrl.newBuilder()
            .encodedPath(httpUrl.encodedPath + "/")
            .build()
            .toString()
    }
}
