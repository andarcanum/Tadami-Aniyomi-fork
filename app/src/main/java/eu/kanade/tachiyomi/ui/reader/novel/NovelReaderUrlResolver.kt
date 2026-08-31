package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.source.local.entries.novel.isLocal

/** How long the source's own web-url answer may take before the string fallback serves it. */
private const val SOURCE_URL_TIMEOUT_MS = 1_500L

/**
 * Resolves the real web URL of a chapter, preferring the source's own answer.
 *
 * The local novel source stores plain file names as novel/chapter urls. Running them through the
 * host-guessing heuristic fabricates a site from the extension ("book.txt" → "https://book.txt/"),
 * and every WebView navigation to it dies with net::ERR_NAME_NOT_RESOLVED. A local book has no
 * web page, so the answer must be null, and the callers hide their "open in WebView" affordances.
 */
internal suspend fun resolveNovelChapterWebUrlForSource(
    source: NovelSource,
    chapterUrl: String,
    novelUrl: String,
    pluginSite: String?,
): String? {
    if (source.isLocal()) return null

    // A busy plugin runtime (e.g. an ongoing chapter-list refresh holds the plugin mutex) must not
    // delay showing already-downloaded/cached chapter text; give the source a short window and
    // otherwise fall back to string-based resolution.
    val sourceResolved = withTimeoutOrNull(SOURCE_URL_TIMEOUT_MS) {
        (source as? NovelWebUrlSource)
            ?.getChapterWebUrl(chapterPath = chapterUrl, novelPath = novelUrl)
    }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    if (sourceResolved != null) {
        sourceResolved.toHttpUrlOrNull()?.let { return it.toString() }
        resolveNovelChapterWebUrl(
            chapterUrl = sourceResolved,
            pluginSite = pluginSite,
            novelUrl = novelUrl,
        )?.let { return it }
    }
    return resolveNovelChapterWebUrl(
        chapterUrl = chapterUrl,
        pluginSite = pluginSite,
        novelUrl = novelUrl,
    )
}

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
