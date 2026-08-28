package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class AnimeFeedSourceTest {

    @Test
    fun `AnimeFeedSource isFeedSource defaults to true and returns FeedPage`() = runTest {
        val sampleItem = ShortVideoItem(
            id = "test-123",
            title = "Test Clip",
            author = "Creator",
            videoUrl = "https://example.com/video-sd.mp4",
            videoUrlHd = "https://example.com/video.mp4",
            posterUrl = "https://example.com/poster.jpg",
            durationSec = 15.5f,
            hasAudio = true,
            tags = listOf("tag1", "tag2"),
        )

        val feedSource = object : AnimeFeedSource {
            override val id: Long = 1001L
            override val name: String = "Test Reels"
            override val lang: String = "en"
            override val supportsTags: Boolean = true

            override suspend fun getFeed(page: Int, filters: AnimeFilterList): FeedPage {
                return FeedPage(videos = listOf(sampleItem), hasNextPage = true)
            }

            override suspend fun getSearchFeed(page: Int, query: String, filters: AnimeFilterList): FeedPage {
                return FeedPage(videos = listOf(sampleItem), hasNextPage = false)
            }
        }

        feedSource.isFeedSource shouldBe true
        feedSource.supportsTags shouldBe true

        val feed = feedSource.getFeed(1, AnimeFilterList())
        feed.videos.size shouldBe 1
        feed.videos.first().id shouldBe "test-123"
        feed.videos.first().hasAudio shouldBe true
        feed.hasNextPage shouldBe true
    }

    @Test
    fun `AnimeFeedSource interface defaults are usable without overrides`() = runTest {
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 1002L
            override val name: String = "Defaults Reels"
            override val lang: String = "en"

            override suspend fun getFeed(page: Int, filters: AnimeFilterList): FeedPage =
                FeedPage(emptyList(), false)

            override suspend fun getSearchFeed(page: Int, query: String, filters: AnimeFilterList): FeedPage =
                FeedPage(emptyList(), false)
        }

        feedSource.isFeedSource shouldBe true
        feedSource.supportsTags shouldBe true
        feedSource.getFilterList().list.size shouldBe 0
    }

    @Test
    fun `FeedPage with hasNextPage false carries the termination contract`() {
        val page = FeedPage(emptyList(), false)

        page.videos.size shouldBe 0
        page.hasNextPage shouldBe false
    }
}
