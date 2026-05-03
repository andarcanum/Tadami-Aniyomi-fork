/*
 * Adapted from Animetail (Animetailapp/Animetail) — Apache-2.0 licensed.
 * Original source:
 *   app/src/main/java/eu/kanade/tachiyomi/data/track/trakt/dto/TraktSearchResult.kt
 */
package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TraktSearchResult(
    val type: String,
    val score: Double? = null,
    val show: TraktShow? = null,
    val movie: TraktMovie? = null,
)

@Serializable
data class TraktShow(
    val title: String,
    val year: Int? = null,
    val ids: TraktIds,
    val overview: String? = null,
    val images: TraktImages? = null,
)

@Serializable
data class TraktMovie(
    val title: String,
    val year: Int? = null,
    val ids: TraktIds,
    val overview: String? = null,
    val images: TraktImages? = null,
)

@Serializable
data class TraktIds(
    val trakt: Long,
    val slug: String = "",
    val imdb: String? = null,
    val tmdb: Long? = null,
)

@Serializable
data class TraktImages(
    val poster: JsonElement? = null,
)
