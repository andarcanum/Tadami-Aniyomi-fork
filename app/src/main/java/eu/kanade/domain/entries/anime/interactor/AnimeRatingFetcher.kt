package eu.kanade.domain.entries.anime.interactor

import android.util.Log
import eu.kanade.domain.entries.anime.model.AnimeRatingHtmlParser
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.entries.rating.EntryRatingCache
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.awaitSuccess
import tachiyomi.domain.entries.anime.model.Anime
import uy.kohesive.injekt.injectLazy
import java.util.Locale

class AnimeRatingFetcher {
    private val ratingCache: EntryRatingCache by injectLazy()

    suspend fun await(
        source: AnimeSource,
        anime: Anime,
        forceRefresh: Boolean = false,
    ): Float? {
        val httpSource = source as? AnimeHttpSource ?: return null

        return runCatching {
            val request = httpSource.animeDetailsRequest(anime.toSAnime())

            val rating = ratingCache.resolve(
                contentType = CONTENT_TYPE,
                sourceName = source.name,
                url = request.url.toString(),
                forceRefresh = forceRefresh,
            ) {
                val html = httpSource.client.newCall(request).awaitSuccess().use { response ->
                    response.body.string()
                }
                val parsedRating = AnimeRatingHtmlParser.parse(html)
                debugLog(
                    "fetchHtml: source=${source.name} animeUrl=${request.url} rating=${parsedRating.previewFloat()} htmlPreview=${html.previewForLog()}",
                )
                parsedRating
            }

            debugLog(
                "await: source=${source.name} animeUrl=${request.url} rating=${rating.previewFloat()} forceRefresh=$forceRefresh",
            )
            rating
        }.getOrElse { error ->
            debugLog(
                "await: failed source=${source.name} error=${error.message}",
            )
            null
        }
    }

    private fun String.previewForLog(limit: Int = 120): String {
        return replace(Regex("\\s+"), " ").take(limit)
    }

    private fun Float?.previewFloat(): String {
        return this?.let { String.format(Locale.ROOT, "%.3f", it) }.orEmpty()
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private companion object {
        private const val TAG = "AnimeRatingFetcher"
        private const val CONTENT_TYPE = "anime"
    }
}
