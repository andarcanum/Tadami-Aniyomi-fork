package eu.kanade.domain.track

import eu.kanade.domain.track.anime.MapAnimeTrackStatusToLibrary
import eu.kanade.domain.track.manga.MapMangaTrackStatusToLibrary
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryTrackStatus

/**
 * E-M1: the index-based else-branch assumes a canonical status list order; Anilist (both media)
 * and Kitsu-anime use different orders, so their ON_HOLD/DROPPED/PLAN statuses were mapped to the
 * wrong library buckets - breaking BY_TRACK_STATUS grouping and the update-job track filter.
 * Red before the fix: Anilist manga ON_HOLD mapped to PLAN_TO_READ.
 */
class MapTrackStatusToLibraryTest {

    private val trackerManager = mockk<TrackerManager>()

    private fun givenMangaTracker(service: Any) {
        val tracker = mockk<BaseTracker>()
        every { tracker.mangaService } returns service as eu.kanade.tachiyomi.data.track.MangaTracker
        every { trackerManager.get(1L) } returns tracker
    }

    private fun givenAnimeTracker(service: Any) {
        val tracker = mockk<BaseTracker>()
        every { tracker.animeService } returns service as eu.kanade.tachiyomi.data.track.AnimeTracker
        every { trackerManager.get(1L) } returns tracker
    }

    private fun anilistManga(): Anilist {
        val anilist = mockk<Anilist>()
        every { anilist.getStatusListManga() } returns listOf(1L, 5L, 2L, 6L, 3L, 4L)
        every { anilist.getReadingStatus() } returns 1L
        every { anilist.getRereadingStatus() } returns 6L
        every { anilist.getCompletionStatus() } returns 2L
        return anilist
    }

    private fun anilistAnime(): Anilist {
        val anilist = mockk<Anilist>()
        every { anilist.getStatusListAnime() } returns listOf(11L, 15L, 2L, 16L, 3L, 4L)
        every { anilist.getWatchingStatus() } returns 11L
        every { anilist.getRewatchingStatus() } returns 16L
        every { anilist.getCompletionStatus() } returns 2L
        return anilist
    }

    private fun kitsuAnime(): Kitsu {
        val kitsu = mockk<Kitsu>()
        every { kitsu.getStatusListAnime() } returns listOf(11L, 15L, 2L, 3L, 4L)
        every { kitsu.getWatchingStatus() } returns 11L
        every { kitsu.getRewatchingStatus() } returns Long.MIN_VALUE
        every { kitsu.getCompletionStatus() } returns 2L
        return kitsu
    }

    @Test
    fun `anilist manga statuses map by constants, not list index`() {
        givenMangaTracker(anilistManga())
        val mapper = MapMangaTrackStatusToLibrary(trackerManager)

        mapper.map(1L, Anilist.ON_HOLD) shouldBe LibraryTrackStatus.ON_HOLD
        mapper.map(1L, Anilist.DROPPED) shouldBe LibraryTrackStatus.DROPPED
        mapper.map(1L, Anilist.PLAN_TO_READ) shouldBe LibraryTrackStatus.PLAN_TO_READ
        mapper.map(1L, Anilist.READING) shouldBe LibraryTrackStatus.READING
        mapper.map(1L, Anilist.REREADING) shouldBe LibraryTrackStatus.REPEATING
        mapper.map(1L, Anilist.COMPLETED) shouldBe LibraryTrackStatus.COMPLETED
    }

    @Test
    fun `anilist and kitsu anime statuses map by constants`() {
        val mapper = MapAnimeTrackStatusToLibrary(trackerManager)

        givenAnimeTracker(anilistAnime())
        mapper.map(1L, Anilist.ON_HOLD) shouldBe LibraryTrackStatus.ON_HOLD
        mapper.map(1L, Anilist.DROPPED) shouldBe LibraryTrackStatus.DROPPED
        mapper.map(1L, Anilist.PLAN_TO_WATCH) shouldBe LibraryTrackStatus.PLAN_TO_READ

        givenAnimeTracker(kitsuAnime())
        mapper.map(1L, Kitsu.ON_HOLD) shouldBe LibraryTrackStatus.ON_HOLD
        mapper.map(1L, Kitsu.DROPPED) shouldBe LibraryTrackStatus.DROPPED
        mapper.map(1L, Kitsu.PLAN_TO_WATCH) shouldBe LibraryTrackStatus.PLAN_TO_READ
    }

    @Test
    fun `canonical-order services keep the index formula`() {
        // Kitsu manga list [READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ] is canonical for
        // the formula (ON_HOLD idx2, DROPPED idx3, PLAN idx4).
        val kitsu = mockk<Kitsu>()
        every { kitsu.getStatusListManga() } returns listOf(1L, 2L, 3L, 4L, 5L)
        every { kitsu.getReadingStatus() } returns 1L
        every { kitsu.getRereadingStatus() } returns Long.MIN_VALUE
        every { kitsu.getCompletionStatus() } returns 2L
        givenMangaTracker(kitsu)

        val mapper = MapMangaTrackStatusToLibrary(trackerManager)
        mapper.map(1L, 3L) shouldBe LibraryTrackStatus.ON_HOLD
        mapper.map(1L, 4L) shouldBe LibraryTrackStatus.DROPPED
        mapper.map(1L, 5L) shouldBe LibraryTrackStatus.PLAN_TO_READ
    }
}
