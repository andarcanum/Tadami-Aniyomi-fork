package eu.kanade.tachiyomi.data.track.trakt

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TraktTrackerTest {

    @Test
    fun `Trakt advertises five anime statuses`() {
        val tracker = Trakt(201L)
        tracker.getStatusListAnime() shouldBe listOf(
            Trakt.WATCHING,
            Trakt.COMPLETED,
            Trakt.ON_HOLD,
            Trakt.DROPPED,
            Trakt.PLAN_TO_WATCH,
        )
        tracker.getWatchingStatus() shouldBe Trakt.WATCHING
        tracker.getCompletionStatus() shouldBe Trakt.COMPLETED
        // Trakt has no first-class rewatching state.
        tracker.getRewatchingStatus() shouldBe 0
    }

    @Test
    fun `Trakt status resource mapping covers all advertised statuses`() {
        val tracker = Trakt(201L)
        tracker.getStatusForAnime(Trakt.WATCHING)?.resourceId shouldBe
            tachiyomi.i18n.aniyomi.AYMR.strings.watching.resourceId
        tracker.getStatusForAnime(Trakt.PLAN_TO_WATCH)?.resourceId shouldBe
            tachiyomi.i18n.aniyomi.AYMR.strings.plan_to_watch.resourceId
        tracker.getStatusForAnime(Trakt.COMPLETED)?.resourceId shouldBe
            tachiyomi.i18n.MR.strings.completed.resourceId
        tracker.getStatusForAnime(Trakt.ON_HOLD)?.resourceId shouldBe
            tachiyomi.i18n.MR.strings.on_hold.resourceId
        tracker.getStatusForAnime(Trakt.DROPPED)?.resourceId shouldBe
            tachiyomi.i18n.MR.strings.dropped.resourceId
        tracker.getStatusForAnime(99L) shouldBe null
    }

    @Test
    fun `Trakt score list is 0 to 10 inclusive`() {
        val tracker = Trakt(201L)
        val scores = tracker.getScoreList()
        scores.size shouldBe 11
        scores.first() shouldBe "0"
        scores.last() shouldBe "10"
    }

    @Test
    fun `Trakt redirect uri matches our manifest oauth host`() {
        Trakt.REDIRECT_URI shouldBe "aniyomi://trakt-auth"
    }
}
