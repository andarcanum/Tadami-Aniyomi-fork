package eu.kanade.tachiyomi.data.track

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.test.DummyTracker
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.manga.model.MangaTrack as DomainTrack

class TrackerManagerNovelFilteringTest {

    @Test
    fun `logged in manga trackers excludes novel trackers and anime trackers`() {
        val trackers = listOf(
            mangaTracker(7L),
            mangaTracker(10L),
            mangaTracker(11L),
            DummyTracker(id = 2L, name = "AniList", isLoggedIn = true, valLogoColor = 0),
        )

        val novelTrackerIds = setOf(10L, 11L)

        val novelIds = filterLoggedInTrackersForEntry(
            isNovelEntry = true,
            trackers = trackers,
            novelTrackerIds = novelTrackerIds,
        ).map { it.id }.toSet()
        novelIds shouldContain 7L
        novelIds shouldContain 10L
        novelIds shouldContain 11L
        novelIds shouldNotContain 2L

        val mangaIds = filterLoggedInTrackersForEntry(
            isNovelEntry = false,
            trackers = trackers,
            novelTrackerIds = novelTrackerIds,
        ).map { it.id }.toSet()
        mangaIds shouldContain 7L
        mangaIds shouldNotContain 10L
        mangaIds shouldNotContain 11L
        mangaIds shouldNotContain 2L
    }

    private fun mangaTracker(id: Long): Tracker {
        return FakeMangaTracker(
            id = id,
            name = "MangaTracker $id",
        )
    }

    private class FakeMangaTracker(
        id: Long,
        name: String,
    ) : Tracker by DummyTracker(
        id = id,
        name = name,
        isLoggedIn = true,
        valLogoColor = 0,
    ),
        MangaTracker {

        override fun getStatusListManga(): List<Long> = listOf(1L, 2L, 3L, 4L, 5L)

        override fun getReadingStatus(): Long = 1L

        override fun getRereadingStatus(): Long = 1L

        override fun getCompletionStatus(): Long = 2L

        override fun getScoreList(): ImmutableList<String> = persistentListOf("0")

        override fun displayScore(track: DomainTrack): String = "-"

        override fun getStatusForManga(status: Long): StringResource? = null

        override suspend fun update(track: MangaTrack, didReadChapter: Boolean): MangaTrack = track

        override suspend fun bind(track: MangaTrack, hasReadChapters: Boolean): MangaTrack = track

        override suspend fun searchManga(query: String): List<MangaTrackSearch> = emptyList()

        override suspend fun refresh(track: MangaTrack): MangaTrack = track
    }
}
