/*
 * Adapted from Animetail (Animetailapp/Animetail) — Apache-2.0 licensed.
 * Original source:
 *   app/src/main/java/eu/kanade/tachiyomi/data/track/trakt/TraktApi.kt
 */
package eu.kanade.tachiyomi.data.track.trakt

import androidx.core.net.toUri
import com.tadami.aurora.BuildConfig
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktLibraryItem
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktOAuth
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktSearchResult
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncMovie
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TraktApi(private val client: OkHttpClient, private val interceptor: TraktInterceptor) {

    private val baseUrl = "https://api.trakt.tv"
    private val json = Json { ignoreUnknownKeys = true }
    private val userAgent = "Tadami v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"

    private val publicClient by lazy {
        client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
    }

    private val authClient by lazy {
        client.newBuilder()
            .addInterceptor(interceptor)
            .build()
    }

    companion object {
        fun authUrl(): android.net.Uri =
            "https://trakt.tv/oauth/authorize".toUri().buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", Trakt.CLIENT_ID)
                .appendQueryParameter("redirect_uri", Trakt.REDIRECT_URI)
                .build()
    }

    private fun Request.Builder.applyTraktHeaders(includeContentType: Boolean = true): Request.Builder {
        if (includeContentType) {
            header("Content-Type", "application/json")
        }
        return header("Accept", "application/json")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", userAgent)
    }

    fun search(query: String): List<TraktSearchResult> {
        val request = Request.Builder()
            .url(
                "$baseUrl/search/movie,show?extended=full,images&limit=20&query=" +
                    java.net.URLEncoder.encode(query, "utf-8"),
            )
            .applyTraktHeaders(includeContentType = false)
            .get()
            .build()
        // Public search — avoid cookies / auth so that Cloudflare doesn't reject the request.
        val response = publicClient.newCall(request).execute()
        val body = response.body.string()
        return try {
            json.decodeFromString(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Mark a single episode of a show as watched via the /sync/history endpoint. If [season] is null,
     * the season is resolved by fetching the show's season layout and matching the absolute episode
     * number against per-season episode counts.
     */
    fun updateShowEpisodeProgress(traktId: Long, season: Int? = null, episode: Int): Boolean {
        var seasonNumber: Int
        var episodeNumberInSeason: Int
        if (season != null) {
            seasonNumber = season
            episodeNumberInSeason = episode
        } else {
            try {
                val seasonsReq = Request.Builder()
                    .url("$baseUrl/shows/$traktId/seasons?extended=episodes")
                    .applyTraktHeaders(includeContentType = false)
                    .get()
                    .build()
                val seasonsResp = authClient.newCall(seasonsReq).execute()
                val seasonsBody = seasonsResp.body.string()

                val root = try {
                    json.parseToJsonElement(seasonsBody).jsonArray
                } catch (_: Exception) {
                    JsonArray(emptyList())
                }

                var matchByNumber: Int? = null
                root.forEach { seasonEl ->
                    try {
                        val seasonObj = seasonEl.jsonObject
                        val seasonNum = seasonObj["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                        if (seasonNum == 0) return@forEach // skip specials
                        val episodesArr = seasonObj["episodes"]?.jsonArray
                        if (episodesArr != null) {
                            val hasMatch = episodesArr.any { epEl ->
                                try {
                                    epEl.jsonObject["number"]?.jsonPrimitive?.intOrNull == episode
                                } catch (_: Exception) {
                                    false
                                }
                            }
                            if (hasMatch) {
                                matchByNumber = seasonNum
                            }
                        }
                    } catch (_: Exception) {
                        // ignore and continue
                    }
                }
                if (matchByNumber != null) {
                    seasonNumber = matchByNumber
                    episodeNumberInSeason = episode
                } else {
                    var cumulative = 0
                    var foundSeason: Int? = null
                    var epInSeason = episode
                    root.forEach { seasonEl ->
                        try {
                            val seasonObj = seasonEl.jsonObject
                            val seasonNum = seasonObj["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                            val episodesArr = seasonObj["episodes"]?.jsonArray
                            val count = seasonObj["episode_count"]?.jsonPrimitive?.intOrNull
                                ?: episodesArr?.size
                                ?: 0
                            if (seasonNum == 0 || count <= 0) return@forEach
                            val start = cumulative + 1
                            val end = cumulative + count
                            if (episode in start..end) {
                                foundSeason = seasonNum
                                epInSeason = episode - cumulative
                                return@forEach
                            }
                            cumulative += count
                        } catch (_: Exception) {
                            // ignore and continue
                        }
                    }
                    seasonNumber = foundSeason ?: 1
                    episodeNumberInSeason = epInSeason
                }
            } catch (_: Exception) {
                seasonNumber = 1
                episodeNumberInSeason = episode
            }
        }

        val payload = """
            {
                "shows": [
                    {
                        "ids": { "trakt": $traktId },
                        "seasons": [
                            {
                                "number": $seasonNumber,
                                "episodes": [
                                    { "number": $episodeNumberInSeason }
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("$baseUrl/sync/history")
            .applyTraktHeaders()
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    fun updateMovieWatched(syncMovie: TraktSyncMovie): Boolean {
        val syncRequest = TraktSyncRequest(movies = listOf(syncMovie))
        val requestBody = json.encodeToString(TraktSyncRequest.serializer(), syncRequest)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/sync/history")
            .applyTraktHeaders()
            .post(requestBody)
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    /**
     * Send rating(s) (1–10) to Trakt. Empty inputs are no-ops.
     */
    fun sendRatings(
        movieRatings: List<Pair<Long, Int>> = emptyList(),
        showRatings: List<Pair<Long, Int>> = emptyList(),
    ): Boolean {
        if (movieRatings.isEmpty() && showRatings.isEmpty()) return true
        val moviesJson = movieRatings.joinToString(separator = ",") { (id, rating) ->
            """{ "ids": { "trakt": $id }, "rating": $rating }"""
        }
        val showsJson = showRatings.joinToString(separator = ",") { (id, rating) ->
            """{ "ids": { "trakt": $id }, "rating": $rating }"""
        }
        val parts = mutableListOf<String>()
        if (movieRatings.isNotEmpty()) parts.add("\"movies\": [ $moviesJson ]")
        if (showRatings.isNotEmpty()) parts.add("\"shows\": [ $showsJson ]")
        val payload = "{ ${parts.joinToString(", ")} }"
        val request = Request.Builder()
            .url("$baseUrl/sync/ratings")
            .applyTraktHeaders()
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    fun loginOAuth(code: String, clientId: String, clientSecret: String, redirectUri: String): TraktOAuth? {
        val bodyJson = """
            {
                "code":"$code",
                "client_id":"$clientId",
                "client_secret":"$clientSecret",
                "redirect_uri":"$redirectUri",
                "grant_type":"authorization_code"
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("$baseUrl/oauth/token")
            .applyTraktHeaders()
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val body = response.body.string()
        return try {
            json.decodeFromString<TraktOAuth>(body)
        } catch (_: Exception) {
            null
        }
    }

    fun refreshOAuth(refreshToken: String, clientId: String, clientSecret: String): TraktOAuth? {
        val bodyJson = """
            {
                "refresh_token":"$refreshToken",
                "client_id":"$clientId",
                "client_secret":"$clientSecret",
                "grant_type":"refresh_token"
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("$baseUrl/oauth/token")
            .applyTraktHeaders()
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val body = response.body.string()
        return try {
            json.decodeFromString<TraktOAuth>(body)
        } catch (_: Exception) {
            null
        }
    }

    fun getCurrentUser(): String? {
        val request = Request.Builder()
            .url("$baseUrl/users/me")
            .applyTraktHeaders(includeContentType = false)
            .get()
            .build()
        val response = authClient.newCall(request).execute()
        val body = response.body.string()
        return try {
            val parsed = json.parseToJsonElement(body).jsonObject
            parsed["username"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    fun getUserShows(): List<TraktLibraryItem> {
        val request = Request.Builder()
            .url("$baseUrl/sync/watched/shows")
            .applyTraktHeaders(includeContentType = false)
            .get()
            .build()
        val response = authClient.newCall(request).execute()
        val body = response.body.string()
        return try {
            val root = json.parseToJsonElement(body).jsonArray
            root.mapNotNull { elem ->
                try {
                    val show = elem.jsonObject["show"]?.jsonObject ?: return@mapNotNull null
                    val ids = show["ids"]?.jsonObject
                    val traktId = ids?.get("trakt")?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val title = show["title"]?.jsonPrimitive?.contentOrNull
                    TraktLibraryItem(traktId = traktId, title = title, progress = 0)
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getUserMovies(): List<TraktLibraryItem> {
        val request = Request.Builder()
            .url("$baseUrl/sync/watched/movies")
            .applyTraktHeaders(includeContentType = false)
            .get()
            .build()
        val response = authClient.newCall(request).execute()
        val body = response.body.string()
        return try {
            val root = json.parseToJsonElement(body).jsonArray
            root.mapNotNull { elem ->
                try {
                    val movie = elem.jsonObject["movie"]?.jsonObject ?: return@mapNotNull null
                    val ids = movie["ids"]?.jsonObject
                    val traktId = ids?.get("trakt")?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val title = movie["title"]?.jsonPrimitive?.contentOrNull
                    TraktLibraryItem(traktId = traktId, title = title, progress = 0)
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun removeShowHistory(traktId: Long): Boolean {
        val payload = """
            {
                "shows": [
                    { "ids": { "trakt": $traktId } }
                ]
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("$baseUrl/sync/history/remove")
            .applyTraktHeaders()
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    fun removeMovieHistory(traktId: Long): Boolean {
        val payload = """
            {
                "movies": [
                    { "ids": { "trakt": $traktId } }
                ]
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("$baseUrl/sync/history/remove")
            .applyTraktHeaders()
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    fun getShowEpisodeCount(traktId: Long): Long {
        val request = Request.Builder()
            .url("$baseUrl/shows/$traktId/seasons?extended=episodes")
            .applyTraktHeaders(includeContentType = false)
            .get()
            .build()
        val response = publicClient.newCall(request).execute()
        val body = response.body.string()
        return try {
            val root = json.parseToJsonElement(body).jsonArray
            var total = 0L
            root.forEach { seasonEl ->
                try {
                    val seasonObj = seasonEl.jsonObject
                    val seasonNum = seasonObj["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                    val episodes = seasonObj["episodes"]?.jsonArray
                    val count = seasonObj["episode_count"]?.jsonPrimitive?.intOrNull ?: episodes?.size ?: 0
                    if (seasonNum == 0) return@forEach
                    if (count > 0) total += count.toLong()
                } catch (_: Exception) {
                    // skip malformed season entries
                }
            }
            total
        } catch (_: Exception) {
            0L
        }
    }
}
