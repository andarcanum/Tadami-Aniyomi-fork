package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.reels.anime.model.ReelsActiveCheckpoint
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsSessionGuardTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun candidate(id: String): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = "tg:1",
            sourceId = 101L,
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

    private fun videoItem(id: String): ShortVideoItem {
        return ShortVideoItem(
            id = id,
            title = "Title $id",
            author = "Author",
            videoUrl = "https://example.com/$id.mp4",
            videoUrlHd = null,
            posterUrl = "https://example.com/$id.jpg",
            posterUrlVertical = null,
            durationSec = 60f,
            hasAudio = true,
            webUrl = "https://example.com/$id",
        )
    }

    @Test
    fun unattached_continuity_discards_checkpoints_without_crash() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val continuity = ReelsSessionContinuity(
            repository = repo,
            scope = this,
            ioDispatcher = testDispatcher,
        )
        // No attachHandle called (e.g. Incognito or not replay capable)

        val itemA = videoItem("clip-incognito")
        continuity.onVisitStarted(itemA)
        continuity.onPlaybackSample("clip-incognito", positionMs = 5000L)
        continuity.flushActiveVisit()
        testDispatcher.scheduler.advanceUntilIdle()

        // Zero writes to session repository
        assertEquals(0, repo.checkpoints.size)
        assertNull(repo.activeCheckpoint)
    }

    @Test
    fun invalidate_nullifies_attached_handle_and_drops_active_checkpoint() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val continuity = ReelsSessionContinuity(
            repository = repo,
            scope = this,
            ioDispatcher = testDispatcher,
        )
        val handle = ReelsSessionHandle(
            key = ReelsSessionKey("tg:1", 101L, "ctx"),
            sessionId = "s1",
            ownerNonce = "nonce1",
            historyGeneration = 1L,
        )
        continuity.attachHandle(handle)
        continuity.onVisitStarted(videoItem("clip-1"))
        continuity.flushActiveVisit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("clip-1", repo.activeCheckpoint?.item?.itemId)

        // Account context changed or logout
        continuity.invalidate(dropCheckpoint = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(repo.activeCheckpoint)
        assertNull(continuity.attachedHandle())
    }

    @Test
    fun clearScope_removes_queued_and_checkpoint() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val handle = repo.open(ReelsSessionKey("tg:1", 101L, "ctx"), "nonce1", 1L, reset = true)
        repo.appendPage(handle, listOf(candidate("c1"), candidate("c2")))
        repo.checkpoint(handle, ReelsActiveCheckpoint(candidate("c1"), 5000L, false, 1L))

        assertEquals(2, repo.queued.size)
        assertEquals("c1", repo.activeCheckpoint?.item?.itemId)

        repo.clearScope("tg:1")

        assertEquals(0, repo.queued.size)
        assertNull(repo.activeCheckpoint)
    }
}
