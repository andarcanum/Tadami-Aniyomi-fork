package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ReelsAlbumFeedSeedTest {

    @AfterEach
    fun tearDown() {
        ReelsAlbumFeedSeedStore.clear()
    }

    @Test
    fun `pending seed is consumed only for matching albumId`() {
        val item1 = ShortVideoItem(
            id = "clip-1",
            title = "Clip 1",
            videoUrl = "https://cdn.example/1.mp4",
            posterUrl = "https://cdn.example/1.jpg",
        )
        val item2 = ShortVideoItem(
            id = "clip-2",
            title = "Clip 2",
            videoUrl = "https://cdn.example/2.mp4",
            posterUrl = "https://cdn.example/2.jpg",
        )
        val seed = ReelsAlbumFeedSeed(
            albumId = "album-42",
            items = persistentListOf(item1, item2),
            targetVideoId = "clip-2",
            nextPage = 3,
            nextCursor = "cursor-abc",
            cursorMode = true,
            canLoadMore = true,
        )

        ReelsAlbumFeedSeedStore.pending = seed

        // Non-matching albumId must not consume the seed
        val nonMatch = ReelsAlbumFeedSeedStore.consume("album-999")
        nonMatch shouldBe null
        ReelsAlbumFeedSeedStore.pending shouldBe seed

        // Matching albumId consumes the seed exactly once
        val consumed = ReelsAlbumFeedSeedStore.consume("album-42")
        consumed shouldBe seed
        consumed?.items?.size shouldBe 2
        consumed?.targetVideoId shouldBe "clip-2"
        consumed?.nextPage shouldBe 3
        consumed?.nextCursor shouldBe "cursor-abc"

        // Second call gets null
        val secondConsume = ReelsAlbumFeedSeedStore.consume("album-42")
        secondConsume shouldBe null
        ReelsAlbumFeedSeedStore.pending shouldBe null
    }

    @Test
    fun `clear resets pending seed`() {
        val seed = ReelsAlbumFeedSeed(
            albumId = "album-1",
            items = persistentListOf(),
        )
        ReelsAlbumFeedSeedStore.pending = seed
        ReelsAlbumFeedSeedStore.clear()
        ReelsAlbumFeedSeedStore.pending shouldBe null
    }

    @Test
    fun `pending seed with sourceId is not consumed by another source with same albumId`() {
        val seed = ReelsAlbumFeedSeed(
            albumId = "trending",
            items = persistentListOf(),
            sourceId = 100L,
        )
        ReelsAlbumFeedSeedStore.pending = seed

        // Different source with matching album slug must not steal the seed
        ReelsAlbumFeedSeedStore.consume(albumId = "trending", sourceId = 200L) shouldBe null
        ReelsAlbumFeedSeedStore.pending shouldBe seed

        // Matching sourceId consumes the seed
        ReelsAlbumFeedSeedStore.consume(albumId = "trending", sourceId = 100L) shouldBe seed
        ReelsAlbumFeedSeedStore.pending shouldBe null
    }

    @Test
    fun `consume without sourceId retains backward compatibility for 1-arg callers`() {
        val seed = ReelsAlbumFeedSeed(
            albumId = "trending",
            items = persistentListOf(),
            sourceId = 100L,
        )
        ReelsAlbumFeedSeedStore.pending = seed

        ReelsAlbumFeedSeedStore.consume(albumId = "trending") shouldBe seed
        ReelsAlbumFeedSeedStore.pending shouldBe null
    }
}
