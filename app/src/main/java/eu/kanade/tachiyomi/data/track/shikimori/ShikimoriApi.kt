package eu.kanade.tachiyomi.data.track.shikimori

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMAddEntryResponse
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMEntry
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMOAuth
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUser
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUserListEntry
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import logcat.logcat
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

class ShikimoriApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: ShikimoriInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: MangaTrack, userId: String): MangaTrack {
        return withIOContext {
            with(json) {
                val payload = buildJsonObject {
                    putJsonObject("user_rate") {
                        put("user_id", userId)
                        put("target_id", track.remote_id)
                        put("target_type", "Manga")
                        put("chapters", track.last_chapter_read.toInt())
                        put("score", track.score.toInt())
                        put("status", track.toShikimoriStatus())
                    }
                }
                authClient.newCall(
                    POST(
                        "$API_URL/v2/user_rates",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                ).awaitSuccess()
                    .parseAs<SMAddEntryResponse>()
                    .let {
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun updateLibManga(track: MangaTrack, userId: String): MangaTrack = addLibManga(
        track,
        userId,
    )

    suspend fun deleteLibManga(track: DomainMangaTrack) {
        withIOContext {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/${track.libraryId}"))
                .awaitSuccess()
        }
    }

    suspend fun addLibAnime(track: AnimeTrack, userId: String): AnimeTrack {
        return withIOContext {
            with(json) {
                val payload = buildJsonObject {
                    putJsonObject("user_rate") {
                        put("user_id", userId)
                        put("target_id", track.remote_id)
                        put("target_type", "Anime")
                        put("episodes", track.last_episode_seen.toInt())
                        put("score", track.score.toInt())
                        put("status", track.toShikimoriStatus())
                    }
                }
                authClient.newCall(
                    POST(
                        "$API_URL/v2/user_rates",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                ).awaitSuccess()
                    .parseAs<SMAddEntryResponse>()
                    .let {
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun updateLibAnime(track: AnimeTrack, userId: String): AnimeTrack = addLibAnime(
        track,
        userId,
    )

    suspend fun deleteLibAnime(track: DomainAnimeTrack) {
        withIOContext {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/${track.libraryId}"))
                .awaitSuccess()
        }
    }

    suspend fun search(search: String): List<MangaTrackSearch> {
        return withIOContext {
            val url = "$API_URL/mangas".toUri().buildUpon()
                .appendQueryParameter("order", "popularity")
                .appendQueryParameter("search", search)
                .appendQueryParameter("limit", "20")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<List<SMEntry>>()
                    .map { it.toMangaTrack(trackId) }
            }
        }
    }

    suspend fun searchAnime(search: String): List<AnimeTrackSearch> {
        return withIOContext {
            val url = "$API_URL/animes".toUri().buildUpon()
                .appendQueryParameter("order", "popularity")
                .appendQueryParameter("search", search)
                .appendQueryParameter("limit", "20")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<List<SMEntry>>()
                    .map { it.toAnimeTrack(trackId) }
            }
        }
    }

    suspend fun getAnimeById(id: Long): SMEntry {
        return withIOContext {
            val url = "$API_URL/animes".toUri().buildUpon()
                .appendPath(id.toString())
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    suspend fun findLibManga(track: MangaTrack, userId: String): MangaTrack? {
        return withIOContext {
            val urlMangas = "$API_URL/mangas".toUri().buildUpon()
                .appendPath(track.remote_id.toString())
                .build()
            val manga = with(json) {
                authClient.newCall(GET(urlMangas.toString()))
                    .awaitSuccess()
                    .parseAs<SMEntry>()
            }

            val url = "$API_URL/v2/user_rates".toUri().buildUpon()
                .appendQueryParameter("user_id", userId)
                .appendQueryParameter("target_id", track.remote_id.toString())
                .appendQueryParameter("target_type", "Manga")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<List<SMUserListEntry>>()
                    .let { entries ->
                        if (entries.size > 1) {
                            throw Exception("Too many manga in response")
                        }
                        entries
                            .map { it.toMangaTrack(trackId, manga) }
                            .firstOrNull()
                    }
            }
        }
    }

    suspend fun findLibAnime(track: AnimeTrack, user_id: String): AnimeTrack? {
        return withIOContext {
            val urlAnimes = "$API_URL/animes".toUri().buildUpon()
                .appendPath(track.remote_id.toString())
                .build()
            val anime = with(json) {
                authClient.newCall(GET(urlAnimes.toString()))
                    .awaitSuccess()
                    .parseAs<SMEntry>()
            }

            val url = "$API_URL/v2/user_rates".toUri().buildUpon()
                .appendQueryParameter("user_id", user_id)
                .appendQueryParameter("target_id", track.remote_id.toString())
                .appendQueryParameter("target_type", "Anime")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<List<SMUserListEntry>>()
                    .let { entries ->
                        if (entries.size > 1) {
                            throw Exception("Too many manga in response")
                        }
                        entries
                            .map { it.toAnimeTrack(trackId, anime) }
                            .firstOrNull()
                    }
            }
        }
    }

    suspend fun getCurrentUser(): Int {
        return with(json) {
            authClient.newCall(GET("$API_URL/users/whoami"))
                .awaitSuccess()
                .parseAs<SMUser>()
                .id
        }
    }

    /**
     * Parse poster URL from Shikimori anime page HTML.
     * Used as fallback when API returns missing_preview.jpg placeholder.
     *
     * @param animeId Shikimori anime ID
     * @return Poster URL or null if not found
     */
    suspend fun parsePosterFromHtml(animeId: Long): String? {
        return withIOContext {
            try {
                logcat(LogPriority.DEBUG) { "Parsing poster from HTML for anime $animeId" }

                // Fetch HTML page
                val url = "$BASE_URL/animes/$animeId"
                val response = client.newCall(GET(url)).awaitSuccess()
                val html = response.body.string()

                // Parse with JSoup
                val doc = Jsoup.parse(html)

                // Try to find poster URL in data-href (best quality)
                val dataHrefPoster = doc.selectFirst("div.b-db_entry-poster[data-href]")
                    ?.attr("data-href")
                if (!dataHrefPoster.isNullOrBlank() && !dataHrefPoster.contains("missing_")) {
                    logcat(LogPriority.DEBUG) { "Found poster in data-href: $dataHrefPoster" }
                    return@withIOContext dataHrefPoster
                }

                // Fallback: try to find poster URL in meta tag
                val metaPoster = doc.selectFirst("meta[itemprop=image]")?.attr("content")
                if (!metaPoster.isNullOrBlank() && !metaPoster.contains("missing_")) {
                    logcat(LogPriority.DEBUG) { "Found poster in meta tag: $metaPoster" }
                    return@withIOContext metaPoster
                }

                // Fallback: try to find in picture element
                val picturePoster = doc.selectFirst("picture.poster img")?.attr("src")
                if (!picturePoster.isNullOrBlank() && !picturePoster.contains("missing_")) {
                    val fullUrl = if (picturePoster.startsWith("http")) {
                        picturePoster
                    } else {
                        "$BASE_URL$picturePoster"
                    }
                    logcat(LogPriority.DEBUG) { "Found poster in picture element: $fullUrl" }
                    return@withIOContext fullUrl
                }

                logcat(LogPriority.WARN) { "No poster found in HTML for anime $animeId" }
                null
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to parse poster from HTML: ${e.message}" }
                null
            }
        }
    }

    suspend fun accessToken(code: String): SMOAuth {
        return withIOContext {
            with(json) {
                client.newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun accessTokenRequest(code: String) = POST(
        OAUTH_URL,
        body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("code", code)
            .add("redirect_uri", REDIRECT_URL)
            .build(),
    )

    companion object {
        const val BASE_URL = "https://shikimori.one"
        private const val API_URL = "$BASE_URL/api"
        private const val OAUTH_URL = "$BASE_URL/oauth/token"
        private const val LOGIN_URL = "$BASE_URL/oauth/authorize"

        private const val REDIRECT_URL = "aniyomi://shikimori-auth"

        private const val CLIENT_ID = "aOAYRqOLwxpA8skpcQIXetNy4cw2rn2fRzScawlcQ5U"
        private const val CLIENT_SECRET = "jqjmORn6bh2046ulkm4lHEwJ3OA1RmO3FD2sR9f6Clw"

        fun authUrl(): Uri = LOGIN_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URL)
            .appendQueryParameter("response_type", "code")
            .build()

        fun refreshTokenRequest(token: String) = POST(
            OAUTH_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", token)
                .build(),
        )
    }
}
