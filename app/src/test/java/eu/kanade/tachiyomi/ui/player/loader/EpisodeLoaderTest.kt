package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class EpisodeLoaderTest {

    @Test
    fun `loadHosterVideos keeps preloaded legacy videos unresolved until selection`() = runTest {
        val source = object : AnimeHttpSource() {
            override val name = "Test"
            override val baseUrl = "https://example.com"
            override val lang = "en"
            override val supportsLatest = false

            override fun popularAnimeRequest(page: Int): Request = error("Not used")

            override fun popularAnimeParse(response: Response): AnimesPage = error("Not used")

            override fun latestUpdatesRequest(page: Int): Request = error("Not used")

            override fun latestUpdatesParse(response: Response): AnimesPage = error("Not used")

            override fun searchAnimeRequest(
                page: Int,
                query: String,
                filters: AnimeFilterList,
            ): Request = error("Not used")

            override fun searchAnimeParse(response: Response): AnimesPage = error("Not used")

            override fun animeDetailsParse(response: Response): SAnime = error("Not used")

            override fun episodeListParse(response: Response): List<SEpisode> = error("Not used")

            override fun episodeVideoParse(response: Response): SEpisode = error("Not used")

            override fun seasonListParse(response: Response): List<SAnime> = error("Not used")

            override fun hosterListParse(response: Response): List<Hoster> = error("Not used")

            override fun videoListParse(response: Response, hoster: Hoster): List<Video> = error("Not used")

            override fun videoListParse(response: Response): List<Video> = error("Not used")

            override fun videoUrlParse(response: Response): String = error("Not used")

            override suspend fun getVideoUrl(video: Video): String = error("Should not eagerly resolve")
        }

        val unresolved = Video(
            url = "https://example.com/watch",
            quality = "1080p",
            videoUrl = "null",
        )
        val hoster = Hoster(
            hosterName = "AniStar",
            videoList = listOf(unresolved),
        )

        val state = EpisodeLoader.loadHosterVideos(source, hoster)
            .shouldBeInstanceOf<HosterState.Ready>()

        state.videoList.single().videoUrl shouldBe "null"
        state.videoList.single().url shouldBe "https://example.com/watch"
    }
}
