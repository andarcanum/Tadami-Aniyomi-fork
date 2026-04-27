package eu.kanade.tachiyomi.ui.entries.manga.track

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.test.DummyTracker
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaTrackInfoDialogTrackerSelectionTest {

    @Test
    fun `novel entry returns the novel tracker list`() {
        val novelTrackers = listOf(dummyTracker(10L), dummyTracker(11L))
        val mangaTrackers = listOf(dummyTracker(7L))

        selectTrackersForEntry(
            isNovelEntry = true,
            novelTrackers = novelTrackers,
            mangaTrackers = mangaTrackers,
        ) shouldBe novelTrackers
    }

    @Test
    fun `manga entry returns the manga tracker list`() {
        val novelTrackers = listOf(dummyTracker(10L), dummyTracker(11L))
        val mangaTrackers = listOf(dummyTracker(7L))

        selectTrackersForEntry(
            isNovelEntry = false,
            novelTrackers = novelTrackers,
            mangaTrackers = mangaTrackers,
        ) shouldBe mangaTrackers
    }

    private fun dummyTracker(id: Long): Tracker {
        return DummyTracker(
            id = id,
            name = "Tracker $id",
            isLoggedIn = true,
            valLogoColor = 0,
        )
    }
}
