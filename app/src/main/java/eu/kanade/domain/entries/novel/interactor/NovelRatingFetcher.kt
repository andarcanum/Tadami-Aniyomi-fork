package eu.kanade.domain.entries.novel.interactor

import android.util.Log
import eu.kanade.domain.entries.novel.model.NovelRatingHtmlParser
import eu.kanade.domain.entries.novel.model.NovelRatingJsonParser
import eu.kanade.domain.entries.rating.EntryRatingCache
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.injectLazy
import java.util.Locale
import java.util.TimeZone

class NovelRatingFetcher {
    private val network: NetworkHelper by injectLazy()
    private val ratingCache: EntryRatingCache by injectLazy()

    suspend fun await(
        source: NovelSource,
        novel: Novel,
        forceRefresh: Boolean = false,
    ): Float? {
        val webUrl = resolveWebUrl(source, novel) ?: run {
            debugLog("await: skip source=${source.name} novelUrl=${novel.url} reason=no-web-url")
            return null
        }

        val requestUrl = webUrl.toHttpUrlOrNull() ?: run {
            debugLog("await: skip source=${source.name} novelUrl=${novel.url} reason=invalid-url url=$webUrl")
            return null
        }

        val rating = ratingCache.resolve(
            contentType = CONTENT_TYPE,
            sourceName = source.name,
            url = requestUrl.toString(),
            forceRefresh = forceRefresh,
        ) {
            when (requestUrl.host.lowercase(Locale.ROOT)) {
                "ranobelib.me" -> fetchRanobeLibRating(requestUrl)
                else -> fetchHtmlRating(requestUrl)
            }
        }
        debugLog(
            "await: source=${source.name} novelUrl=$requestUrl rating=${rating.previewFloat()}",
        )
        return rating
    }

    private suspend fun fetchHtmlRating(requestUrl: okhttp3.HttpUrl): Float? {
        val html = network.client.newCall(GET(requestUrl.toString())).awaitSuccess().use { response ->
            response.body.string()
        }
        val rating = NovelRatingHtmlParser.parse(html)
        debugLog("fetchHtmlRating: url=$requestUrl rating=${rating.previewFloat()} htmlPreview=${html.previewForLog()}")
        return rating
    }

    private suspend fun fetchRanobeLibRating(requestUrl: okhttp3.HttpUrl): Float? {
        val slug = requestUrl.ranobeLibSlug() ?: run {
            debugLog("fetchRanobeLibRating: skip reason=no-slug url=$requestUrl")
            return null
        }
        val apiUrl = "https://api.cdnlibs.org/api/manga/$slug/stats?bookmarks=true&rating=true"
        val apiRequest = Request.Builder()
            .url(apiUrl)
            .header("referer", "https://ranobelib.me/")
            .header("site-id", "3")
            .header("client-time-zone", TimeZone.getDefault().id)
            .build()
        val json = network.client.newCall(apiRequest).awaitSuccess().use { response ->
            response.body.string()
        }
        val rating = NovelRatingJsonParser.parseRanobeLibStats(json)
            ?: NovelRatingHtmlParser.parse(json)
        debugLog(
            "fetchRanobeLibRating: url=$requestUrl apiUrl=$apiUrl rating=${rating.previewFloat()} jsonPreview=${json.previewForLog()}",
        )
        return rating
    }

    private suspend fun resolveWebUrl(source: NovelSource, novel: Novel): String? {
        val siteUrl = (source as? NovelSiteSource)?.siteUrl?.takeIf { it.isNotBlank() }
        val webUrlSource = source as? NovelWebUrlSource
        if (webUrlSource != null) {
            webUrlSource.getNovelWebUrl(novel.url)
                ?.takeIf { it.isNotBlank() }
                ?.let { candidate -> resolveCandidateUrl(candidate, siteUrl) }
                ?.let { return it }
        }

        return resolveCandidateUrl(novel.url, siteUrl)
    }

    private fun resolveCandidateUrl(candidate: String, siteUrl: String?): String? {
        candidate.toHttpUrlOrNull()?.let { return it.toString() }
        val baseUrl = siteUrl?.toHttpUrlOrNull() ?: return null
        return baseUrl.resolve(candidate)?.toString()
    }

    private fun okhttp3.HttpUrl.ranobeLibSlug(): String? {
        val bookIndex = pathSegments.indexOf("book")
        if (bookIndex >= 0 && bookIndex + 1 < pathSegments.size) {
            return pathSegments[bookIndex + 1].takeIf { it.isNotBlank() }
        }
        return pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun String.previewForLog(limit: Int = 120): String {
        return replace(Regex("\\s+"), " ").take(limit)
    }

    private fun Float?.previewFloat(): String {
        return this?.let { String.format(Locale.ROOT, "%.3f", it) }.orEmpty()
    }

    private fun debugLog(message: String) {
        runCatching { Log.d("NovelRatingFetcher", message) }
    }

    private companion object {
        private const val CONTENT_TYPE = "novel"
    }
}
