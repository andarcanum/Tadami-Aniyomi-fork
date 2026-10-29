package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedReplayItemResult
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnimeFeedContractV27Test {

    private class FakeReplaySource(
        private val accountId: String? = "tg_12345",
        private val continuityKey: String = "feed_global_unseen",
    ) : AnimeFeedReplaySource {
        override fun accountScopeId(): String? = accountId

        override val replayContextChanges: Flow<FeedReplayContext?> =
            flowOf(accountId?.let { FeedReplayContext(it, continuityKey) })

        override suspend fun prepareReplay(): FeedReplayContext? =
            accountId?.let { FeedReplayContext(it, continuityKey) }

        override suspend fun resolveReplayItem(
            itemId: String,
            expectedContext: FeedReplayContext,
            filters: AnimeFilterList,
        ): FeedReplayItemResult {
            if (expectedContext.accountId != accountId) return FeedReplayItemResult.ContextChanged
            return FeedReplayItemResult.Available(
                ShortVideoItem(
                    id = itemId,
                    title = "Title $itemId",
                    videoUrl = "https://cdn.example.invalid/$itemId/sd.mp4",
                    videoUrlHd = "https://cdn.example.invalid/$itemId/hd.mp4",
                    posterUrl = "https://cdn.example.invalid/$itemId/poster.jpg",
                ),
            )
        }
    }

    private class BareOldSource : AnimeFeedAccountIdentitySource {
        override fun accountScopeId(): String? = "legacy_user"
    }

    @Test
    fun replayCapabilityRequiresAccountIdentityAndIsInstanceofDetected() {
        val replay: Any = FakeReplaySource()
        assertTrue(replay is AnimeFeedReplaySource)
        assertTrue(replay is AnimeFeedAccountIdentitySource)
        assertFalse(replay is AnimeFeedLoginSource)
        assertFalse(replay is AnimeFeedWebLoginSource)

        val bare: Any = BareOldSource()
        assertFalse(bare is AnimeFeedReplaySource)
        assertTrue(bare is AnimeFeedAccountIdentitySource)
    }

    @Test
    fun replayItemResultVariantsAreExhaustiveAndRetainPayload() {
        val item = ShortVideoItem(
            id = "test_item_1",
            videoUrl = "https://cdn.example.invalid/1.mp4",
            posterUrl = "https://cdn.example.invalid/1.jpg",
        )
        val available: FeedReplayItemResult = FeedReplayItemResult.Available(item)
        val filteredOut: FeedReplayItemResult = FeedReplayItemResult.FilteredOut
        val unavailable: FeedReplayItemResult = FeedReplayItemResult.Unavailable
        val deferred: FeedReplayItemResult = FeedReplayItemResult.Deferred(retryAtEpochMillis = 1700000000000L)
        val contextChanged: FeedReplayItemResult = FeedReplayItemResult.ContextChanged

        assertIs<FeedReplayItemResult.Available>(available)
        assertEquals("test_item_1", available.item.id)
        assertEquals("https://cdn.example.invalid/1.mp4", available.item.videoUrl)
        assertEquals("https://cdn.example.invalid/1.jpg", available.item.posterUrl)

        assertIs<FeedReplayItemResult.FilteredOut>(filteredOut)
        assertIs<FeedReplayItemResult.Unavailable>(unavailable)
        assertIs<FeedReplayItemResult.Deferred>(deferred)
        assertEquals(1700000000000L, deferred.retryAtEpochMillis)
        assertIs<FeedReplayItemResult.ContextChanged>(contextChanged)
    }

    @Test
    fun feedReplayContextHoldsAccountAndContinuityKey() {
        val ctx = FeedReplayContext(accountId = "acc_99", continuityKey = "key_main")
        assertEquals("acc_99", ctx.accountId)
        assertEquals("key_main", ctx.continuityKey)
    }

    @Test
    fun v26ConstructorsAndDescriptorsArePreserved() {
        // Ensure ShortVideoItem constructor with default arguments matches v26 contract
        val item = ShortVideoItem(
            id = "vid_1",
            videoUrl = "https://example.com/v.mp4",
            posterUrl = "https://example.com/p.jpg",
        )
        assertEquals("vid_1", item.id)
        assertEquals(null, item.title)
        assertEquals(null, item.author)
        assertEquals("https://example.com/v.mp4", item.videoUrl)
        assertEquals(null, item.videoUrlHd)
        assertEquals("https://example.com/p.jpg", item.posterUrl)
        assertEquals(null, item.posterUrlVertical)
        assertEquals(null, item.durationSec)
        assertFalse(item.hasAudio)
        assertEquals(emptyList(), item.tags)
        assertEquals(null, item.webUrl)
        assertEquals(null, item.viewsCount)
        assertEquals(null, item.likesCount)
        assertFalse(item.isAuthorVerified)
        assertEquals(null, item.createdAtEpochSec)

        // Ensure FeedPage constructor matches v26 contract
        val page = FeedPage(videos = listOf(item), hasNextPage = true, nextCursor = "cur_1")
        assertEquals(1, page.videos.size)
        assertEquals("cur_1", page.nextCursor)
        assertTrue(page.hasNextPage)
    }
}
