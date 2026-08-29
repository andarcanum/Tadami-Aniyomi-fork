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

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                return FeedPage(videos = listOf(sampleItem), hasNextPage = true)
            }

            override suspend fun getSearchFeed(
                page: Int,
                cursor: String?,
                query: String,
                filters: AnimeFilterList,
            ): FeedPage {
                return FeedPage(videos = listOf(sampleItem), hasNextPage = false)
            }
        }

        feedSource.isFeedSource shouldBe true
        feedSource.supportsTags shouldBe true

        val feed = feedSource.getFeed(1, null, AnimeFilterList())
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

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
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

    @Test
    fun `FeedPage defaults nextCursor to null for page-int sources`() {
        val page = FeedPage(videos = emptyList(), hasNextPage = false)
        page.nextCursor shouldBe null
    }

    @Test
    fun `getSearchFeed default delegates to getFeed with cursor passed through`() = runTest {
        var seen: Pair<Int, String?>? = null
        val source = object : AnimeFeedSource {
            override val id: Long = 1003L
            override val name: String = "Default Search Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                seen = page to cursor
                return FeedPage(emptyList(), hasNextPage = false, nextCursor = "c-next")
            }
        }

        val result = source.getSearchFeed(3, "c-token", "tag", AnimeFilterList())
        seen shouldBe (3 to "c-token")
        result.nextCursor shouldBe "c-next"
    }

    @Test
    fun `creator capability is detected by instanceof and leaves plain feed sources behind`() = runTest {
        // The vals are typed as the root AnimeSource on purpose: the `is` checks below must
        // stay dynamic, mirroring the host's runtime `rawSource is AnimeCreatorFeedSource`
        // detection (a statically provable type would be a compile-time tautology).
        val plainFeed: AnimeSource = object : AnimeFeedSource {
            override val id: Long = 1004L
            override val name: String = "Plain Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(emptyList(), false)
        }
        var seen: Triple<String, Int, String?>? = null
        val creatorFeed: AnimeSource = object : AnimeFeedSource, AnimeCreatorFeedSource {
            override val id: Long = 1005L
            override val name: String = "Creator Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(emptyList(), false)

            override suspend fun getCreatorFeed(creator: String, page: Int, cursor: String?): FeedPage {
                seen = Triple(creator, page, cursor)
                return FeedPage(emptyList(), hasNextPage = false, nextCursor = "u2")
            }
        }

        (plainFeed is AnimeCreatorFeedSource) shouldBe false
        (creatorFeed is AnimeCreatorFeedSource) shouldBe true

        val capability = creatorFeed as AnimeCreatorFeedSource
        val result = capability.getCreatorFeed("babyemmy", 2, "u1")
        seen shouldBe Triple("babyemmy", 2, "u1")
        result.nextCursor shouldBe "u2"
    }
}
