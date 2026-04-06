package eu.kanade.domain.entries.manga.interactor

import android.util.Log
import eu.kanade.domain.entries.manga.model.SourceMangaHtmlRatingParser
import eu.kanade.domain.entries.manga.model.SourceMangaRatingSourceMatcher
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.network.awaitSuccess
import tachiyomi.domain.entries.manga.model.Manga
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

class SourceMangaRatingFetcher {

    suspend fun await(source: MangaSource, manga: Manga): Float? {
        val httpSource = source as? HttpSource ?: return null
        val sourceClassName = source.javaClass.superclass?.simpleName
        val family = SourceMangaRatingSourceMatcher.resolveFamily(
            sourceName = source.name,
            sourceBaseUrl = httpSource.baseUrl,
            sourceClassName = sourceClassName,
        )
        if (family == null) {
            debugLog("await: skip source=${source.name}")
            return null
        }

        val request = httpSource.mangaDetailsRequest(manga.toSManga())
        val requestToUse = if (family == eu.kanade.domain.entries.manga.model.SourceMangaRatingFamily.INK_STORY) {
            request.newBuilder()
                .url(normalizeInkStoryUrl(request.url.toString()).toHttpUrlOrNull() ?: request.url)
                .build()
        } else {
            request
        }
        val html = httpSource.client.newCall(requestToUse).awaitSuccess().use { response ->
            response.body.string()
        }
        val rating = SourceMangaHtmlRatingParser.parse(
            sourceName = source.name,
            sourceBaseUrl = httpSource.baseUrl,
            sourceClassName = sourceClassName,
            html = html,
        )
        debugLog(
            "await: source=${source.name} family=$family mangaUrl=${requestToUse.url} rating=${rating.previewFloat()} htmlPreview=${html.previewForLog()}",
        )
        return rating
    }

    private fun normalizeInkStoryUrl(url: String): String {
        val parsedUrl = url.toHttpUrlOrNull() ?: return url
        val host = parsedUrl.host.lowercase(Locale.ROOT)
        if (host != "inkstory.net" && host != "api.inkstory.net") return url

        val pathSegments = parsedUrl.pathSegments.filter { it.isNotBlank() }
        if (pathSegments.size < 3) return url
        if (pathSegments[0] != "v2" || pathSegments[1] != "books") return url

        val slug = pathSegments[2].takeIf(String::isNotBlank) ?: return url
        return "https://inkstory.net/content/$slug"
    }

    private fun String.previewForLog(limit: Int = 120): String {
        return replace(Regex("\\s+"), " ").take(limit)
    }

    private fun Float?.previewFloat(): String {
        return this?.let { String.format("%.3f", it) }.orEmpty()
    }

    private fun debugLog(message: String) {
        runCatching { Log.d("SourceMangaHtmlFetcher", message) }
    }
}
