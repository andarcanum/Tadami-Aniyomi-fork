package eu.kanade.tachiyomi.data.track

import android.content.Context
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.jellyfin.Jellyfin
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.novellist.NovelList
import eu.kanade.tachiyomi.data.track.novelupdates.NovelUpdates
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.simkl.Simkl
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class TrackerManager(context: Context) {

    companion object {
        const val ANILIST = 2L
        const val KITSU = 3L
        const val KAVITA = 8L
        const val SIMKL = 101L
        const val JELLYFIN = 102L
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
    val simkl = Simkl(SIMKL)
    val jellyfin = Jellyfin(JELLYFIN)
    val novelTrackers: List<Tracker> = listOf(novelUpdates, novelList)

    val trackers = listOf(
        myAnimeList, aniList, kitsu, shikimori, bangumi,
        komga, mangaUpdates, kavita, suwayomi, novelUpdates, novelList, simkl, jellyfin,
    )

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInNovelTrackers() = filterLoggedInTrackersForEntry(
        isNovelEntry = true,
        trackers = trackers,
        novelTrackerIds = novelTrackerIds,
    )

    fun loggedInMangaTrackers() = filterLoggedInTrackersForEntry(
        isNovelEntry = false,
        trackers = trackers,
        novelTrackerIds = novelTrackerIds,
    )

fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    fun loggedInNovelTrackersFlow() = loggedInTrackersFlow().map { trackers ->
        filterLoggedInTrackersForEntry(
            isNovelEntry = true,
            trackers = trackers,
            novelTrackerIds = novelTrackerIds,
        )
    }

    fun get(id: Long) = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>) = trackers.filter { it.id in ids }

    private val novelTrackerIds = setOf(novelUpdates.id, novelList.id)
}

internal fun filterLoggedInTrackersForEntry(
    isNovelEntry: Boolean,
    trackers: List<Tracker>,
    novelTrackerIds: Set<Long>,
): List<Tracker> {
    return trackers
        .filter { it is MangaTracker && it.isLoggedIn && (isNovelEntry || it.id !in novelTrackerIds) }
}
