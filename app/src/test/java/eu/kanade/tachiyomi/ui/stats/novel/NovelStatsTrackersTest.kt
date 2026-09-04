package eu.kanade.tachiyomi.ui.stats.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.novel.model.NovelTrack

class NovelStatsTrackersTest {

    private fun track(id: Long, score: Double): NovelTrack = NovelTrack(
        id = id,
        novelId = 1L,
        trackerId = 10L,
        remoteId = id,
        libraryId = null,
        title = "T$id",
        lastChapterRead = 0.0,
        totalChapters = 0L,
        status = 0L,
        score = score,
        remoteUrl = "",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )

    @Test
    fun `trackers stat counts tracked titles and averages the scored ones`() {
        // The card used to be hardcoded to 0/NaN/0 even with logged-in trackers and tracks.
        val stat = computeNovelTrackersStat(
            trackMap = mapOf(
                1L to listOf(track(1L, 8.0)),
                2L to listOf(track(2L, 0.0)),
                3L to emptyList(),
            ),
            tenPointScoreOf = { it.score * 2.0 },
            loggedInTrackerCount = 2,
        )

        stat.trackedTitleCount shouldBe 2
        stat.meanScore shouldBe 16.0
        stat.trackerCount shouldBe 2
    }

    @Test
    fun `trackers stat without any tracks stays empty`() {
        val stat = computeNovelTrackersStat(
            trackMap = emptyMap(),
            tenPointScoreOf = { it.score },
            loggedInTrackerCount = 0,
        )

        stat.trackedTitleCount shouldBe 0
        stat.meanScore.isNaN() shouldBe true
        stat.trackerCount shouldBe 0
    }
}
