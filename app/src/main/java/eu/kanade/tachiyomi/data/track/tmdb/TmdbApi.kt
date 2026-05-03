/*
 * Adapted from Animetail (Animetailapp/Animetail) — Apache-2.0 licensed.
 * Original source: app/src/main/java/eu/kanade/tachiyomi/data/track/tmdb/TmdbApi.kt
 */
package eu.kanade.tachiyomi.data.track.tmdb

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tachiyomi.core.common.util.lang.withIOContext

class TmdbApi(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val sessionId: String = "",
) {

    companion object {
        const val HOST = "api.themoviedb.org"
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
        val jsonMime = "application/json".toMediaType()
    }

    private fun buildUrl(path: String, params: Map<String, String> = emptyMap()): String {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host(HOST)
            .addPathSegments(path)
            .addQueryParameter("api_key", apiKey)

        if (sessionId.isNotBlank()) {
            urlBuilder.addQueryParameter("session_id", sessionId)
        }
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        return urlBuilder.build().toString()
    }

    private suspend fun executeUrl(url: String): JSONObject {
        val request = Request.Builder().url(url).build()
        val response = withIOContext { client.newCall(request).execute() }
        response.use { resp ->
            if (!resp.isSuccessful) throw Exception("TMDB request failed: ${resp.code}")
            val body = resp.body.string()
            return JSONObject(body)
        }
    }

    suspend fun getRequestToken(language: String? = null): JSONObject {
        val params = mutableMapOf("redirect_to" to Tmdb.REDIRECT_URI)
        if (!language.isNullOrBlank()) params["language"] = language
        val url = buildUrl("3/authentication/token/new", params)
        return executeUrl(url)
    }

    suspend fun createSession(requestToken: String): JSONObject {
        val url = buildUrl("3/authentication/session/new")
        val payload = buildJsonObject {
            put("request_token", requestToken)
        }.toString().toRequestBody(jsonMime)
        val request = Request.Builder().url(url).post(payload).build()
        val response = withIOContext { client.newCall(request).execute() }
        response.use { resp ->
            if (!resp.isSuccessful) throw Exception("TMDB session creation failed: ${resp.code}")
            val body = resp.body.string()
            return JSONObject(body)
        }
    }

    suspend fun searchMulti(query: String, language: String? = null): List<TmdbSearchResult> {
        val params = mutableMapOf("query" to query)
        if (!language.isNullOrBlank()) params["language"] = language
        val url = buildUrl("3/search/multi", params)
        val json = executeUrl(url)
        val results = json.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<TmdbSearchResult>()
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            when (item.optString("media_type", "")) {
                "movie" -> list.add(
                    TmdbSearchResult(
                        id = item.optLong("id"),
                        title = item.optString("title"),
                        overview = item.optString("overview"),
                        posterPath = item.optString("poster_path").takeIf { it.isNotBlank() },
                        mediaType = "movie",
                    ),
                )
                "tv" -> list.add(
                    TmdbSearchResult(
                        id = item.optLong("id"),
                        title = item.optString("name"),
                        overview = item.optString("overview"),
                        posterPath = item.optString("poster_path").takeIf { it.isNotBlank() },
                        mediaType = "tv",
                    ),
                )
                else -> {
                    // ignore other media types (person, etc.) — we only track tv/movie.
                }
            }
        }
        return list
    }

    suspend fun getMovie(id: Long, language: String? = null): TmdbMovieDetail {
        val params = mutableMapOf<String, String>()
        if (!language.isNullOrBlank()) params["language"] = language
        val url = buildUrl("3/movie/$id", params)
        val json = executeUrl(url)
        return TmdbMovieDetail(
            id = json.optLong("id"),
            title = json.optString("title"),
            overview = json.optString("overview"),
            posterPath = json.optString("poster_path").takeIf { it.isNotBlank() },
            additional = json,
        )
    }

    suspend fun getTv(id: Long, language: String? = null): TmdbMovieDetail {
        val params = mutableMapOf<String, String>()
        if (!language.isNullOrBlank()) params["language"] = language
        val url = buildUrl("3/tv/$id", params)
        val json = executeUrl(url)
        return TmdbMovieDetail(
            id = json.optLong("id"),
            title = json.optString("name"),
            overview = json.optString("overview"),
            posterPath = json.optString("poster_path").takeIf { it.isNotBlank() },
            additional = json,
        )
    }

    suspend fun getAccountStates(id: Long, mediaType: String = "movie", language: String? = null): JSONObject {
        val path = if (mediaType == "tv") "3/tv/$id/account_states" else "3/movie/$id/account_states"
        val params = mutableMapOf<String, String>()
        if (!language.isNullOrBlank()) params["language"] = language
        return executeUrl(buildUrl(path, params))
    }

    suspend fun getAccount(): JSONObject = executeUrl(buildUrl("3/account"))

    suspend fun addToWatchlist(mediaType: String, mediaId: Long, watchlist: Boolean): JSONObject {
        val account = getAccount()
        val accountId = account.optLong("id")
        val url = buildUrl("3/account/$accountId/watchlist")
        val payload = buildJsonObject {
            put("media_type", mediaType)
            put("media_id", mediaId)
            put("watchlist", watchlist)
        }.toString().toRequestBody(jsonMime)
        val request = Request.Builder().url(url).post(payload).build()
        val response = withIOContext { client.newCall(request).execute() }
        response.use { resp ->
            if (!resp.isSuccessful) throw Exception("TMDB watchlist update failed: ${resp.code}")
            val body = resp.body.string()
            return JSONObject(body)
        }
    }

    suspend fun addMovieRating(movieId: Long, rating: Double): JSONObject {
        val url = buildUrl("3/movie/$movieId/rating")
        val payload = buildJsonObject { put("value", rating) }
            .toString()
            .toRequestBody(jsonMime)
        val request = Request.Builder().url(url).post(payload).build()
        val response = withIOContext { client.newCall(request).execute() }
        response.use { resp ->
            if (!resp.isSuccessful) throw Exception("TMDB add movie rating failed: ${resp.code}")
            val body = resp.body.string()
            return JSONObject(body)
        }
    }

    suspend fun addTvRating(tvId: Long, rating: Double): JSONObject {
        val url = buildUrl("3/tv/$tvId/rating")
        val payload = buildJsonObject { put("value", rating) }
            .toString()
            .toRequestBody(jsonMime)
        val request = Request.Builder().url(url).post(payload).build()
        val response = withIOContext { client.newCall(request).execute() }
        response.use { resp ->
            if (!resp.isSuccessful) throw Exception("TMDB add TV rating failed: ${resp.code}")
            val body = resp.body.string()
            return JSONObject(body)
        }
    }

    suspend fun deleteMovieRating(movieId: Long): JSONObject {
        val url = buildUrl("3/movie/$movieId/rating")
        val request = Request.Builder().url(url).delete().build()
        val response = withIOContext { client.newCall(request).execute() }
        response.use { resp ->
            if (!resp.isSuccessful) throw Exception("TMDB delete movie rating failed: ${resp.code}")
            val body = resp.body.string()
            return JSONObject(body)
        }
    }

    suspend fun deleteTvRating(tvId: Long): JSONObject {
        val url = buildUrl("3/tv/$tvId/rating")
        val request = Request.Builder().url(url).delete().build()
        val response = withIOContext { client.newCall(request).execute() }
        response.use { resp ->
            if (!resp.isSuccessful) throw Exception("TMDB delete TV rating failed: ${resp.code}")
            val body = resp.body.string()
            return JSONObject(body)
        }
    }
}

data class TmdbSearchResult(
    val id: Long,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val mediaType: String,
)

data class TmdbMovieDetail(
    val id: Long,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val additional: JSONObject,
)
