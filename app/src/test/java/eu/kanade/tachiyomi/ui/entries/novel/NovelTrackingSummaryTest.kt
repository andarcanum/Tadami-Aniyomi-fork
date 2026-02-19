package eu.kanade.tachiyomi.ui.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.novel.model.NovelTrack

class NovelTrackingSummaryTest {

    @Test
    fun `returns zero count and false when no logged in trackers`() {
        val summary = resolveNovelTrackingSummary(
            tracks = listOf(track(id = 1L, trackerId = 2L)),
            loggedInMangaTrackerIds = emptySet(),
        )

        summary.trackingCount shouldBe 0
        summary.hasLoggedInTrackers shouldBe false
    }

    @Test
    fun `counts only tracks belonging to logged in manga trackers`() {
        val summary = resolveNovelTrackingSummary(
            tracks = listOf(
                track(id = 1L, trackerId = 2L),
                track(id = 2L, trackerId = 3L),
                track(id = 3L, trackerId = 5L),
            ),
            loggedInMangaTrackerIds = setOf(2L, 3L, 7L),
        )

        summary.trackingCount shouldBe 2
        summary.hasLoggedInTrackers shouldBe true
    }

    @Test
    fun `returns has logged in trackers true when tracker set is not empty`() {
        val summary = resolveNovelTrackingSummary(
            tracks = emptyList(),
            loggedInMangaTrackerIds = setOf(9L),
        )

        summary.trackingCount shouldBe 0
        summary.hasLoggedInTrackers shouldBe true
    }

    private fun track(id: Long, trackerId: Long): NovelTrack {
        return NovelTrack(
            id = id,
            novelId = 100L,
            trackerId = trackerId,
            remoteId = 200L,
            libraryId = null,
            title = "Track",
            lastChapterRead = 0.0,
            totalChapters = 0L,
            status = 0L,
            score = 0.0,
            remoteUrl = "",
            startDate = 0L,
            finishDate = 0L,
            private = false,
        )
    }
}
