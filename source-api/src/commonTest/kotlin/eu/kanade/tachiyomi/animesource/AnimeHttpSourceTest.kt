package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class AnimeHttpSourceTest {

    @Test
    fun `resolveVideo uses legacy getVideoUrl path for unresolved videos`() = runTest {
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

            override suspend fun getVideoUrl(video: Video): String {
                video.url shouldBe "https://example.com/watch"
                return "https://cdn.example.com/stream.m3u8"
            }
        }

        val unresolved = Video(
            url = "https://example.com/watch",
            quality = "1080p",
            videoUrl = "null",
        )

        val resolved = source.resolveVideo(unresolved)

        resolved.shouldNotBeNull()
        resolved.videoUrl shouldBe "https://cdn.example.com/stream.m3u8"
        resolved.videoTitle shouldBe "1080p"
    }

    @Test
    fun `resolveVideo leaves already resolved videos untouched`() = runTest {
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

            override suspend fun getVideoUrl(video: Video): String = error("Should not resolve")
        }

        val ready = Video(
            url = "https://example.com/watch",
            quality = "720p",
            videoUrl = "https://cdn.example.com/already.m3u8",
        )

        source.resolveVideo(ready) shouldBe ready
    }
}
