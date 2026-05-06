package eu.kanade.tachiyomi.data.track.tmdb

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TmdbSearchTest {

    @Test
    fun `TmdbSearchResult preserves identity fields`() {
        val tv = TmdbSearchResult(
            id = 12345,
            title = "Demon Slayer",
            overview = "Tanjiro becomes a slayer.",
            posterPath = "/abc.jpg",
            mediaType = "tv",
        )
        tv.id shouldBe 12345L
        tv.mediaType shouldBe "tv"
        // The TMDB image base + poster path is what we surface as the cover URL in search results.
        (TmdbApi.IMAGE_BASE + tv.posterPath) shouldBe "https://image.tmdb.org/t/p/w500/abc.jpg"
    }

    @Test
    fun `Tmdb tracker advertises only TV-friendly statuses`() {
        val tracker = Tmdb(200L)
        tracker.getStatusListAnime() shouldBe listOf(Tmdb.WATCHING, Tmdb.PLAN_TO_WATCH, Tmdb.COMPLETED)
        tracker.getWatchingStatus() shouldBe Tmdb.WATCHING
        tracker.getCompletionStatus() shouldBe Tmdb.COMPLETED
        tracker.getRewatchingStatus() shouldBe Tmdb.REWATCHING
    }

    @Test
    fun `Tmdb status mapping returns null for unknown statuses`() {
        val tracker = Tmdb(200L)
        tracker.getStatusForAnime(Tmdb.WATCHING)?.resourceId shouldBe
            tachiyomi.i18n.aniyomi.AYMR.strings.watching.resourceId
        tracker.getStatusForAnime(Tmdb.PLAN_TO_WATCH)?.resourceId shouldBe
            tachiyomi.i18n.aniyomi.AYMR.strings.plan_to_watch.resourceId
        tracker.getStatusForAnime(Tmdb.COMPLETED)?.resourceId shouldBe
            tachiyomi.i18n.MR.strings.completed.resourceId
        tracker.getStatusForAnime(99L) shouldBe null
    }

    @Test
    fun `Tmdb score list is 0 to 10 inclusive`() {
        val tracker = Tmdb(200L)
        val scores = tracker.getScoreList()
        scores.size shouldBe 11
        scores.first() shouldBe "0"
        scores.last() shouldBe "10"
    }
}
