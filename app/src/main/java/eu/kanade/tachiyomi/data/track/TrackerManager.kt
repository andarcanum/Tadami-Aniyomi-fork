package eu.kanade.tachiyomi.data.track

import android.content.Context
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.jellyfin.Jellyfin
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.novellist.NovelList
import eu.kanade.tachiyomi.data.track.novelupdates.NovelUpdates
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.simkl.Simkl
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import eu.kanade.tachiyomi.data.track.tmdb.Tmdb
import eu.kanade.tachiyomi.data.track.trakt.Trakt
import kotlinx.coroutines.flow.combine

class TrackerManager(context: Context) {

    companion object {
        const val ANILIST = 2L
        const val KITSU = 3L
        const val KAVITA = 8L
        const val MANGABAKA = 12L
        const val SIMKL = 101L
        const val JELLYFIN = 102L
        const val TMDB = 200L
        const val TRAKT = 201L
    }

    val myAnimeList = MyAnimeList(1L)
    val aniList = Anilist(ANILIST)
    val kitsu = Kitsu(KITSU)
    val shikimori = Shikimori(4L)
    val bangumi = Bangumi(5L)
    val komga = Komga(6L)
    val mangaUpdates = MangaUpdates(7L)
    val kavita = Kavita(KAVITA)
    val suwayomi = Suwayomi(9L)
    val novelUpdates = NovelUpdates(10L)
    val novelList = NovelList(11L)
    val mangaBaka = MangaBaka(MANGABAKA)
    val simkl = Simkl(SIMKL)
    val jellyfin = Jellyfin(JELLYFIN)
    val tmdb = Tmdb(TMDB)
    val trakt = Trakt(TRAKT)
    val novelTrackers: List<Tracker> = listOf(
        myAnimeList,
        aniList,
        kitsu,
        shikimori,
        mangaUpdates,
        novelUpdates,
        novelList,
        mangaBaka,
    )

    val trackers = listOf(
        myAnimeList, aniList, kitsu, shikimori, bangumi,
        komga, mangaUpdates, kavita, suwayomi, novelUpdates, novelList, mangaBaka, simkl, jellyfin,
        trakt, tmdb,
    )

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInNovelTrackers() = novelTrackers.filter { it.isLoggedIn }

    fun loggedInMangaTrackers() = filterLoggedInTrackersForEntry(
        isNovelEntry = false,
        trackers = trackers,
        novelCapableTrackerIds = novelCapableTrackerIds,
        novelOnlyTrackerIds = novelOnlyTrackerIds,
    )

    fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    fun loggedInNovelTrackersFlow() = combine(novelTrackers.map { it.isLoggedInFlow }) { loggedIn ->
        loggedIn.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) novelTrackers[index] else null
        }
    }

    fun get(id: Long) = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>) = trackers.filter { it.id in ids }

    private val novelOnlyTrackerIds = setOf(novelUpdates.id, novelList.id)
    private val novelCapableTrackerIds = novelTrackers.map { it.id }.toSet()

    fun isNovelOnlyTracker(trackerId: Long) = trackerId in novelOnlyTrackerIds
}

internal fun filterLoggedInTrackersForEntry(
    isNovelEntry: Boolean,
    trackers: List<Tracker>,
    novelCapableTrackerIds: Set<Long>,
    novelOnlyTrackerIds: Set<Long>,
): List<Tracker> {
    return trackers.filter { tracker ->
        tracker is MangaTracker &&
            tracker.isLoggedIn &&
            if (isNovelEntry) {
                tracker.id in novelCapableTrackerIds
            } else {
                tracker.id !in novelOnlyTrackerIds
            }
    }
}
