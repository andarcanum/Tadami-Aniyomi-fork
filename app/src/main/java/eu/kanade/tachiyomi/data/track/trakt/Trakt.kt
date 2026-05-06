/*
 * Adapted from Animetail (Animetailapp/Animetail) — Apache-2.0 licensed.
 * Original source: app/src/main/java/eu/kanade/tachiyomi/data/track/trakt/Trakt.kt
 *
 * Trakt anime tracker. Treats Trakt shows and movies as anime entries:
 *   - shows  -> series with season/episode progress
 *   - movies -> single-episode entries
 *
 * Metadata enrichment (poster/description from Trakt) is intentionally not
 * wired in to this fork; Trakt is exposed as a regular tracker for status,
 * score and progress.
 */
package eu.kanade.tachiyomi.data.track.trakt

import android.graphics.Color
import com.tadami.aurora.R
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableAnimeTracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktIds
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktMovie
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktOAuth
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktShow
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncMovie
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.injectLazy
import kotlin.math.roundToInt
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

class Trakt(id: Long) : BaseTracker(id, "Trakt"), AnimeTracker, DeletableAnimeTracker {

    companion object {
        const val WATCHING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 5L

        // App-level credentials registered for Tadami at https://trakt.tv/oauth/applications.
        const val CLIENT_ID = "4a511500abc05fcb848d043e27a464b711c5c78eb26cf891001914778591ed39"
        const val CLIENT_SECRET = "3bfe7635729ef450c19cf80c1283a4a26c7d96d26d31b7eae78ed1d6dd315416"
        const val REDIRECT_URI = "aniyomi://trakt-auth"

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
            .toImmutableList()
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { TraktInterceptor(this) }

    private val api by lazy { TraktApi(client, interceptor) }

    @Volatile
    private var oauth: TraktOAuth? = null

    override fun getLogo() = R.drawable.ic_tracker_trakt

    override fun getLogoColor() = Color.rgb(237, 28, 36)

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun displayScore(track: DomainAnimeTrack): String = track.score.toInt().toString()

    override fun getStatusListAnime(): List<Long> =
        listOf(WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH)

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        WATCHING -> AYMR.strings.watching
        PLAN_TO_WATCH -> AYMR.strings.plan_to_watch
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        else -> null
    }

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = 0

    override fun getCompletionStatus(): Long = COMPLETED

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> =
        api.search(query).mapNotNull { result ->
            when (result.type) {
                "show" -> result.show?.toTrackSearch()
                "movie" -> result.movie?.toTrackSearch()
                else -> null
            }
        }

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        if (track.remote_id == 0L) return track
        ensureTotalEpisodes(track)
        applyLocalStatus(track, didWatchEpisode)
        return if (isMovieTrack(track)) {
            updateMovieTrack(track)
        } else {
            updateShowTrack(track)
        }
    }

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        try {
            val remoteId = track.remote_id
            if (remoteId == 0L) return update(track, didWatchEpisode = hasSeenEpisodes)
            ensureTotalEpisodes(track)
            val items = if (track.total_episodes == 1L) {
                api.getUserMovies()
            } else {
                api.getUserShows()
            }
            val found = items.firstOrNull { it.traktId == remoteId }
            if (found != null) {
                track.library_id = remoteId
                track.last_episode_seen = found.progress.toDouble()
                return track
            }
        } catch (_: Exception) {
            // Fall back to update() below.
        }
        return update(track, didWatchEpisode = hasSeenEpisodes)
    }

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        try {
            val remoteId = track.remote_id
            if (remoteId == 0L) return track
            ensureTotalEpisodes(track)
            val items = if (track.total_episodes == 1L) {
                api.getUserMovies()
            } else {
                api.getUserShows()
            }
            val found = items.firstOrNull { it.traktId == remoteId }
            if (found != null) {
                track.last_episode_seen = found.progress.toDouble()
            }
        } catch (_: Exception) {
            // ignore network errors, return track as-is
        }
        return track
    }

    override suspend fun delete(track: DomainAnimeTrack) {
        val rid = track.remoteId
        if (rid == 0L) return
        // Best-effort: try both endpoints; whichever doesn't apply is a server-side no-op.
        runCatching { api.removeShowHistory(rid) }
        runCatching { api.removeMovieHistory(rid) }
    }

    // OAuth login helpers; TrackLoginActivity calls login(code) with the Trakt authorization code.
    override suspend fun login(username: String, password: String) = login(password)

    fun login(code: String) {
        try {
            val token = runCatching {
                api.loginOAuth(code, CLIENT_ID, CLIENT_SECRET, REDIRECT_URI)
            }.getOrNull() ?: throw Exception("Failed to get token from Trakt")
            oauth = token
            interceptor.setAuth(token.access_token)
            saveToken(token)

            val username = runCatching { api.getCurrentUser().orEmpty() }.getOrDefault("")
            saveCredentials(username, token.access_token)
        } catch (e: Throwable) {
            logout()
            throw e
        }
    }

    /**
     * Synchronous refresh used by the interceptor before executing a request.
     * Returns true if the access token was refreshed and persisted successfully.
     */
    fun refreshAuthBlocking(): Boolean {
        return try {
            val saved = restoreToken() ?: return false
            val refreshed = api.refreshOAuth(saved.refresh_token, CLIENT_ID, CLIENT_SECRET) ?: return false
            oauth = refreshed
            interceptor.setAuth(refreshed.access_token)
            saveToken(refreshed)
            runCatching {
                val username = api.getCurrentUser().orEmpty()
                saveCredentials(username, refreshed.access_token)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun saveToken(oauth: TraktOAuth?) {
        val raw = oauth?.let { json.encodeToString(it) }.orEmpty()
        trackPreferences.trackToken(this).set(raw)
    }

    fun restoreToken(): TraktOAuth? {
        val raw = trackPreferences.trackToken(this).get()
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<TraktOAuth>(raw) }.getOrNull()
    }

    override fun logout() {
        oauth = null
        saveToken(null)
        interceptor.setAuth(null)
        super.logout()
    }

    private fun ensureTotalEpisodes(track: AnimeTrack) {
        if (track.total_episodes > 0L) return
        if (isMovieTrack(track)) {
            // Movies already mark progress with a single watch event; mirror the convention here.
            track.total_episodes = 1L
            return
        }
        runCatching {
            val total = api.getShowEpisodeCount(track.remote_id)
            if (total > 0) track.total_episodes = total
        }
    }

    private fun isMovieTrack(track: AnimeTrack): Boolean {
        if (track.total_episodes == 1L) return true
        val url = track.tracking_url
        return url.contains("/movies/", ignoreCase = true)
    }

    private fun applyLocalStatus(track: AnimeTrack, didWatchEpisode: Boolean) {
        if (!didWatchEpisode || track.status == COMPLETED) return
        track.status = if (track.total_episodes > 0 && track.last_episode_seen.toLong() == track.total_episodes) {
            COMPLETED
        } else {
            WATCHING
        }
    }

    private fun updateMovieTrack(track: AnimeTrack): AnimeTrack {
        val ids = TraktIds(trakt = track.remote_id)
        runCatching {
            if (track.last_episode_seen.toLong() >= 1L) {
                val alreadyWatched = runCatching {
                    api.getUserMovies().any { it.traktId == ids.trakt }
                }.getOrDefault(false)
                if (!alreadyWatched) {
                    api.updateMovieWatched(TraktSyncMovie(ids = ids, watched = true))
                }
            }
            syncRating(ids.trakt, track.score, isMovie = true)
        }
        return track
    }

    private fun updateShowTrack(track: AnimeTrack): AnimeTrack {
        val traktId = track.remote_id
        val urlSeason = extractSeasonFromUrl(track.tracking_url)
        val (seasonParam, episodeParam) = if (urlSeason != null) {
            urlSeason to track.last_episode_seen.roundToInt().coerceAtLeast(0)
        } else {
            resolveSeasonEpisode(track.last_episode_seen)
        }
        runCatching {
            api.updateShowEpisodeProgress(traktId, seasonParam, episodeParam)
        }
        syncRating(traktId, track.score, isMovie = false)
        return track
    }

    private fun extractSeasonFromUrl(url: String): Int? = try {
        android.net.Uri.parse(url).getQueryParameter("season")?.toIntOrNull()?.takeIf { it > 0 }
    } catch (_: Exception) {
        null
    }

    private fun resolveSeasonEpisode(lastSeen: Double): Pair<Int?, Int> = resolveTraktSeasonEpisode(lastSeen)

    private fun syncRating(traktId: Long, score: Double, isMovie: Boolean) {
        if (score <= 0.0) return
        val rating = score.toInt().coerceIn(1, 10)
        runCatching {
            if (isMovie) {
                api.sendRatings(movieRatings = listOf(traktId to rating))
            } else {
                api.sendRatings(showRatings = listOf(traktId to rating))
            }
        }
    }

    private fun TraktShow.toTrackSearch(): AnimeTrackSearch =
        createTrackSearch(ids.trakt, title, overview, images?.poster, ids.slug, isMovie = false)

    private fun TraktMovie.toTrackSearch(): AnimeTrackSearch =
        createTrackSearch(ids.trakt, title, overview, images?.poster, ids.slug, isMovie = true)

    private fun createTrackSearch(
        remoteId: Long,
        title: String,
        overview: String?,
        posterEl: JsonElement?,
        slug: String,
        isMovie: Boolean,
    ): AnimeTrackSearch {
        val path = if (isMovie) "movies" else "shows"
        val slugOrId = slug.takeIf { it.isNotBlank() } ?: remoteId.toString()
        return AnimeTrackSearch.create(this@Trakt.id).apply {
            this.remote_id = remoteId
            this.title = title
            summary = overview ?: ""
            cover_url = extractPosterUrl(posterEl)
            total_episodes = if (isMovie) 1L else 0L
            tracking_url = "https://trakt.tv/$path/$slugOrId"
        }
    }

    private fun extractPosterUrl(posterEl: JsonElement?): String = extractTraktPosterUrl(posterEl)
}
