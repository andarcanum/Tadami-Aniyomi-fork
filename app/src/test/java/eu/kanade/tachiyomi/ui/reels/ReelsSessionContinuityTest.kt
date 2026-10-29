package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeFeedReplaySource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedReplayItemResult
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.reels.anime.model.ReelsActiveCheckpoint
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsQueuedCandidate
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsSessionContinuityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun candidate(id: String, scope: String = "tg:1", sourceId: Long = 101L): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = scope,
            sourceId = sourceId,
            itemId = id,
            discoveredAt = Date(1000L),
            resumePositionMs = 0L,
            videoUrl = "",
            videoUrlHd = null,
            posterUrl = "",
            posterUrlVertical = null,
            title = "Title $id",
            author = "Author",
            durationSec = 60.0,
            hasAudio = true,
            tagsJson = "[]",
            webUrl = "https://example.com/$id",
            createdAtEpochSec = 1700000000,
            availability = "available",
            retryAfter = null,
            viewsCount = 100L,
            likesCount = 10L,
            isAuthorVerified = false,
            capturedContextKey = null,
        )
    }

    private class FakeReplaySource(
        val resolvedUrls: MutableMap<String, String> = mutableMapOf(),
        val deferredIds: MutableSet<String> = mutableSetOf(),
        val unavailableIds: MutableSet<String> = mutableSetOf(),
    ) : AnimeFeedSource, AnimeFeedReplaySource {
        override val id: Long = 101L
        override val name: String = "Telegram"
        override val lang: String = "all"
        override val isFeedSource: Boolean = true
        override val supportsTags: Boolean = true

        override val replayContextChanges: Flow<FeedReplayContext?> = emptyFlow()
        override fun accountScopeId(): String? = "12345"
        override suspend fun prepareReplay(): FeedReplayContext =
            FeedReplayContext("12345", "tg_v27")

        var resolveCount = 0

        override suspend fun resolveReplayItem(
            itemId: String,
            expectedContext: FeedReplayContext,
            filters: AnimeFilterList,
        ): FeedReplayItemResult {
            resolveCount++
            if (itemId in deferredIds) return FeedReplayItemResult.Deferred(retryAtEpochMillis = null)
            if (itemId in unavailableIds) return FeedReplayItemResult.Unavailable
            val url = resolvedUrls.getOrPut(itemId) { "http://127.0.0.1:8080/stream/$itemId.mp4" }
            return FeedReplayItemResult.Available(
                ShortVideoItem(
                    id = itemId,
                    title = "Title $itemId",
                    author = "Author",
                    videoUrl = url,
                    videoUrlHd = null,
                    posterUrl = "http://127.0.0.1:8080/thumb/$itemId.jpg",
                    posterUrlVertical = null,
                    durationSec = 60f,
                    hasAudio = true,
                    webUrl = "https://example.com/$itemId",
                ),
            )
        }

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)
    }

    @Test
    fun restore_rehydrates_active_checkpoint_at_head_with_saved_seek_position() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val key = ReelsSessionKey("12345", 101L, "ctx_key")
        val activeItem = candidate("clip-1")
        repo.activeCheckpoint = ReelsActiveCheckpoint(
            item = activeItem,
            positionMs = 37200L,
            completed = false,
            sequence = 1L,
        )
        repo.queued.add(ReelsQueuedCandidate(1L, activeItem))
        repo.queued.add(ReelsQueuedCandidate(2L, candidate("clip-2")))
        repo.queued.add(ReelsQueuedCandidate(3L, candidate("clip-3")))

        val source = FakeReplaySource()
        val continuity = ReelsSessionContinuity(
            scope = testScope,
            ioDispatcher = testDispatcher,
            repository = repo,
            isCurrentKey = { true },
        )

        val restored = continuity.restore(
            source = source,
            key = key,
            context = FeedReplayContext("12345", "tg_v27"),
            filters = AnimeFilterList(),
            historyGeneration = 1L,
        )

        assertNotNull(restored)
        assertEquals("clip-1", restored!!.seekItemId)
        assertEquals(37200L, restored.seekPositionMs)
        assertEquals("clip-1", restored.initialItems.first().id)
        assertEquals(3, restored.initialItems.size)
    }

    @Test
    fun restore_keeps_deferred_active_checkpoint_at_head_with_seek() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val key = ReelsSessionKey("12345", 101L, "ctx_key")
        repo.activeCheckpoint = ReelsActiveCheckpoint(
            item = candidate("clip-1"),
            positionMs = 37200L,
            completed = false,
            sequence = 1L,
        )
        val source = FakeReplaySource(deferredIds = mutableSetOf("clip-1"))
        val continuity = ReelsSessionContinuity(
            scope = testScope,
            ioDispatcher = testDispatcher,
            repository = repo,
            isCurrentKey = { true },
        )

        val restored = continuity.restore(
            source = source,
            key = key,
            context = FeedReplayContext("12345", "tg_v27"),
            filters = AnimeFilterList(),
            historyGeneration = 1L,
        )

        assertNotNull(restored)
        assertEquals("clip-1", restored!!.initialItems.first().id)
        assertEquals("clip-1", restored.seekItemId)
        assertEquals(37200L, restored.seekPositionMs)
        assertTrue(restored.initialItems.first().videoUrl.isBlank())
    }

    @Test
    fun restore_caps_eager_resolution_attempts() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val key = ReelsSessionKey("12345", 101L, "ctx_key")
        repeat(10) { index ->
            repo.queued.add(ReelsQueuedCandidate(index + 1L, candidate("clip-$index")))
        }
        val source = FakeReplaySource(
            unavailableIds = (0 until 10).map { "clip-$it" }.toMutableSet(),
        )
        val continuity = ReelsSessionContinuity(
            scope = testScope,
            ioDispatcher = testDispatcher,
            repository = repo,
            isCurrentKey = { true },
        )

        val restored = continuity.restore(
            source = source,
            key = key,
            context = FeedReplayContext("12345", "tg_v27"),
            filters = AnimeFilterList(),
            historyGeneration = 1L,
        )

        assertNotNull(restored)
        // Six eager network attempts; the unresolved tail keeps stored metadata for lazy play.
        assertEquals(6, source.resolveCount)
        assertEquals(
            listOf("clip-6", "clip-7", "clip-8", "clip-9"),
            restored!!.initialItems.map { it.id },
        )
    }

    @Test
    fun sample_coalesces_periodic_writes_to_2000ms() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        var fakeNow = 1000L
        val continuity = ReelsSessionContinuity(
            scope = testScope,
            ioDispatcher = testDispatcher,
            repository = repo,
            nowMs = { fakeNow },
            isCurrentKey = { true },
        )

        val key = ReelsSessionKey("12345", 101L, "ctx")
        repo.open(key, "owner", 1L, reset = false)
        val source = FakeReplaySource()
        continuity.restore(source, key, FeedReplayContext("12345", "tg_v27"), AnimeFilterList(), 1L)

        val item = ShortVideoItem(
            id = "c-1",
            title = "Clip 1",
            author = "Author",
            videoUrl = "url",
            videoUrlHd = null,
            posterUrl = "",
            posterUrlVertical = null,
        )
        continuity.visitStarted(item, persistEligible = true)

        // 40 samples at 250ms = 10 seconds total
        for (i in 1..40) {
            fakeNow += 250L
            continuity.sample(itemId = "c-1", positionMs = i * 250L, foreground = true)
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // 10 seconds / 2s interval = ~5-6 flushes, never 40
        assertTrue("Checkpoints flushes should be <= 6, but was ${repo.checkpoints.size}", repo.checkpoints.size <= 6)
    }

    @Test
    fun positionBoundary_immediately_flushes_checkpoint() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val continuity = ReelsSessionContinuity(
            scope = testScope,
            ioDispatcher = testDispatcher,
            repository = repo,
            isCurrentKey = { true },
        )

        val key = ReelsSessionKey("12345", 101L, "ctx")
        repo.open(key, "owner", 1L, reset = false)
        val source = FakeReplaySource()
        continuity.restore(source, key, FeedReplayContext("12345", "tg_v27"), AnimeFilterList(), 1L)

        val item = ShortVideoItem(
            id = "c-1",
            title = "Clip 1",
            author = "Author",
            videoUrl = "url",
            videoUrlHd = null,
            posterUrl = "",
            posterUrlVertical = null,
        )
        continuity.visitStarted(item, persistEligible = true)
        continuity.positionBoundary("c-1", 15000L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.checkpoints.size)
        assertEquals(15000L, repo.checkpoints.last().positionMs)
    }

    @Test
    fun videoCompleted_latches_completed_true_and_flushes() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val continuity = ReelsSessionContinuity(
            scope = testScope,
            ioDispatcher = testDispatcher,
            repository = repo,
            isCurrentKey = { true },
        )

        val key = ReelsSessionKey("12345", 101L, "ctx")
        repo.open(key, "owner", 1L, reset = false)
        val source = FakeReplaySource()
        continuity.restore(source, key, FeedReplayContext("12345", "tg_v27"), AnimeFilterList(), 1L)

        val item = ShortVideoItem(
            id = "c-1",
            title = "Clip 1",
            author = "Author",
            videoUrl = "url",
            videoUrlHd = null,
            posterUrl = "",
            posterUrlVertical = null,
        )
        continuity.visitStarted(item, persistEligible = true)
        continuity.videoCompleted("c-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repo.checkpoints.last().completed)
    }

    @Test
    fun appendPage_preserves_order_after_all_old_items() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val key = ReelsSessionKey("12345", 101L, "ctx")
        repo.open(key, "owner", 1L, reset = false)

        // Queue 80 old items
        val old80 = (1..80).map { candidate("old-$it") }
        repo.appendPage(repo.currentHandle!!, old80)
        assertEquals(80, repo.queued.size)
        assertEquals(80L, repo.queued.last().ordinal)

        // Append 10 fresh items
        val fresh10 = (1..10).map { candidate("fresh-$it") }
        repo.appendPage(repo.currentHandle!!, fresh10)
        assertEquals(90, repo.queued.size)
        assertEquals(81L, repo.queued[80].ordinal)
        assertEquals("fresh-1", repo.queued[80].candidate.itemId)
        assertEquals(90L, repo.queued[89].ordinal)
        assertEquals("fresh-10", repo.queued[89].candidate.itemId)
    }
}
