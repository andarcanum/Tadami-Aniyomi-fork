/*
 * Adapted from Animetail (Animetailapp/Animetail) — Apache-2.0 licensed.
 * Original source: app/src/main/java/eu/kanade/tachiyomi/data/track/tmdb/Tmdb.kt
 *
 * TMDB v3 anime tracker. Limited compared to traditional trackers — TMDB has no
 * episode-progress endpoint, so we only sync rating + watchlist (status).
 * Media metadata enrichment is intentionally omitted in this fork.
 */
package eu.kanade.tachiyomi.data.track.tmdb

import android.graphics.Color
import com.tadami.aurora.R
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import logcat.LogPriority
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.util.Locale
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

class Tmdb(id: Long) : BaseTracker(id, "TMDB"), AnimeTracker {

    companion object {
        const val WATCHING = 11L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 15L
        const val REWATCHING = 16L

        // App-level v3 API key for TMDB; bundled like our other tracker app credentials.
        const val API_KEY = "2636d1247a3d193fdb5334f9d5780429"

        const val REDIRECT_URI = "aniyomi://tmdb-auth"

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
            .toImmutableList()
    }

    private val sessionId: String
        get() = trackPreferences.trackToken(this).get()

    private val api: TmdbApi
        get() = TmdbApi(client, API_KEY, sessionId)

    override fun getLogo() = R.drawable.ic_tracker_tmdb

    override fun getLogoColor() = Color.rgb(13, 37, 63)

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun displayScore(track: DomainAnimeTrack): String = track.score.toInt().toString()

    override fun getStatusListAnime(): List<Long> = listOf(WATCHING, PLAN_TO_WATCH, COMPLETED)

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        WATCHING -> AYMR.strings.watching
        PLAN_TO_WATCH -> AYMR.strings.plan_to_watch
        COMPLETED -> MR.strings.completed
        else -> null
    }

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = REWATCHING

    override fun getCompletionStatus(): Long = COMPLETED

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> {
        val results = api.searchMulti(query)
        return results
            .filter { it.mediaType == "tv" || it.mediaType == "movie" }
            .map { r ->
                val isTv = r.mediaType == "tv"
                AnimeTrackSearch.create(this@Tmdb.id).apply {
                    remote_id = r.id
                    title = r.title
                    tracking_url = if (isTv) {
                        "https://www.themoviedb.org/tv/${r.id}"
                    } else {
                        "https://www.themoviedb.org/movie/${r.id}"
                    }
                    summary = r.overview
                    cover_url = r.posterPath?.let { TmdbApi.IMAGE_BASE + it } ?: ""
                    publishing_type = if (isTv) "TV" else "Movie"
                }
            }
            .distinctBy { it.remote_id }
    }

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        if (track.status != COMPLETED && didWatchEpisode) {
            track.status = if (
                track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0
            ) {
                COMPLETED
            } else {
                PLAN_TO_WATCH
            }
        }

        if (sessionId.isNotBlank()) {
            try {
                val mediaType = if (track.tracking_url.contains("/tv/")) "tv" else "movie"
                updateRating(track, mediaType)
                val shouldWatchlist = track.status == PLAN_TO_WATCH
                api.addToWatchlist(mediaType, track.remote_id, shouldWatchlist)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "TMDB update: failed to sync watchlist. Error: ${e.message}" }
            }
        }
        return track
    }

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        val remoteTrack = findLibAnime(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                track.status = if (hasSeenEpisodes) WATCHING else track.status
            }
            if (remoteTrack.status != PLAN_TO_WATCH && sessionId.isNotBlank()) {
                add(track)
            }
            update(track)
        } else {
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        findLibAnime(track)?.let { remoteTrack ->
            track.score = remoteTrack.score
            track.total_episodes = remoteTrack.total_episodes
        }
        return track
    }

    override suspend fun login(username: String, password: String) = login(username)

    suspend fun login(code: String) {
        try {
            val oauth = api.createSession(code)
            val sessionId = oauth.optString("session_id")
            trackPreferences.trackToken(this).set(sessionId)
            try {
                val account = api.getAccount()
                val username = account.optString("username")
                    .ifEmpty { account.optString("name", "tmdb") }
                saveCredentials(username, sessionId)
            } catch (_: Exception) {
                saveCredentials("tmdb", sessionId)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "TMDB login: failed with error ${e.message}" }
            logout()
        }
    }

    /**
     * Build the user-facing TMDB authorization URL. The user grants our app a request token
     * which TMDB then redirects back to us via [REDIRECT_URI] for [TrackLoginActivity] to pick
     * up and exchange for a session id via [login].
     */
    suspend fun getAuthUrl(): String {
        val tokenJson = api.getRequestToken()
        val requestToken = tokenJson.optString("request_token")
        return "https://www.themoviedb.org/authenticate/$requestToken?redirect_to=$REDIRECT_URI"
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
    }

    private suspend fun add(track: AnimeTrack): AnimeTrack {
        if (sessionId.isNotBlank()) {
            try {
                val mediaType = if (track.tracking_url.contains("/tv/")) "tv" else "movie"
                api.addToWatchlist(mediaType, track.remote_id, true)
            } catch (_: Exception) {
                // Best-effort; ignore network failures so the local track still binds.
            }
        }
        return track
    }

    private suspend fun findLibAnime(track: AnimeTrack): AnimeTrack? {
        if (track.remote_id == 0L || sessionId.isBlank()) return null
        return try {
            val lang = Locale.getDefault().toLanguageTag()
            val accountJson = try {
                api.getAccountStates(track.remote_id, "tv", lang)
            } catch (_: Exception) {
                api.getAccountStates(track.remote_id, "movie", lang)
            }
            val detail = try {
                api.getMovie(track.remote_id, null)
            } catch (_: Exception) {
                api.getTv(track.remote_id, null)
            }
            val isMovie = detail.additional.optString("media_type", "") == "movie" ||
                detail.additional.has("runtime")
            val defaultEpisodes: Long = if (isMovie) 1 else 100
            val totalEpisodes = detail.additional.optLong("number_of_episodes", defaultEpisodes)

            AnimeTrack.create(this@Tmdb.id).apply {
                remote_id = track.remote_id
                title = detail.title
                score = if (accountJson.has("rated") && accountJson.get("rated") is JSONObject) {
                    accountJson.getJSONObject("rated").optDouble("value", 0.0)
                } else {
                    0.0
                }
                status = if (accountJson.optBoolean("watchlist", false)) PLAN_TO_WATCH else WATCHING
                total_episodes = totalEpisodes
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun updateRating(track: AnimeTrack, mediaType: String) {
        if (track.score > 0) {
            if (mediaType == "movie") {
                api.addMovieRating(track.remote_id, track.score)
            } else {
                api.addTvRating(track.remote_id, track.score)
            }
        } else {
            if (mediaType == "movie") {
                api.deleteMovieRating(track.remote_id)
            } else {
                api.deleteTvRating(track.remote_id)
            }
        }
    }
}
