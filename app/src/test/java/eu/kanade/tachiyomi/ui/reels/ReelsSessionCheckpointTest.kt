package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsSessionCheckpointTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

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
    fun coalesced_samples_write_at_most_once_per_2_seconds() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val continuity = ReelsSessionContinuity(
            repository = repo,
            scope = this,
            ioDispatcher = testDispatcher,
            nowMs = { testDispatcher.scheduler.currentTime },
        )
        val handle = ReelsSessionHandle(
            key = ReelsSessionKey("tg:1", 101L, "ctx"),
            sessionId = "s1",
            ownerNonce = "nonce1",
            historyGeneration = 1L,
        )
        continuity.attachHandle(handle)

        val itemA = videoItem("clip-A")
        continuity.onVisitStarted(itemA)

        // 4 Hz samples for 10 seconds = 40 samples
        // Every 250 ms, advance virtual time and sample
        for (i in 1..40) {
            advanceTimeBy(250L)
            continuity.onPlaybackSample("clip-A", positionMs = i * 250L)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // 10 seconds with 2-second coalescing = at most 5-6 periodic writes
        assertTrue("Checkpoints written: ${repo.checkpoints.size}", repo.checkpoints.size in 1..6)
        assertEquals("clip-A", repo.activeCheckpoint?.item?.itemId)
    }

    @Test
    fun completed_is_latched_and_retained_across_later_samples() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val continuity = ReelsSessionContinuity(
            repository = repo,
            scope = this,
            ioDispatcher = testDispatcher,
            nowMs = { testDispatcher.scheduler.currentTime },
        )
        val handle = ReelsSessionHandle(
            key = ReelsSessionKey("tg:1", 101L, "ctx"),
            sessionId = "s1",
            ownerNonce = "nonce1",
            historyGeneration = 1L,
        )
        continuity.attachHandle(handle)

        val itemA = videoItem("clip-A")
        continuity.onVisitStarted(itemA)
        continuity.onVideoCompleted("clip-A")

        // Later samples must retain completed=true
        advanceTimeBy(2500L)
        continuity.onPlaybackSample("clip-A", positionMs = 60000L)
        continuity.flushActiveVisit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repo.activeCheckpoint?.completed == true)
    }

    @Test
    fun an_ineligible_visit_never_persists_samples_or_boundaries() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        var now = 0L
        val continuity = ReelsSessionContinuity(this, testDispatcher, repo, nowMs = { now })
        continuity.attachHandle(ReelsSessionHandle(ReelsSessionKey("acc:101:12", 101L, "ctx"), "session", "owner", 0L))
        continuity.visitStarted(videoItem("private"), persistEligible = false)
        now = 5_000L
        continuity.sample("private", 5_000L, foreground = true)
        continuity.positionBoundary("private", 5_000L)
        continuity.videoCompleted("private")
        continuity.flushActiveVisit()
        testDispatcher.scheduler.runCurrent()
        assertTrue(repo.checkpoints.isEmpty())
    }

    @Test
    fun foreground_loss_flushes_the_latest_sampled_position() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        var now = 0L
        val continuity = ReelsSessionContinuity(this, testDispatcher, repo, nowMs = { now })
        continuity.attachHandle(ReelsSessionHandle(ReelsSessionKey("acc:101:12", 101L, "ctx"), "session", "owner", 0L))
        continuity.visitStarted(videoItem("current"), persistEligible = true)
        now = 500L
        // Below the 2s coalescing window: the periodic path stays silent.
        continuity.sample("current", 7_000L, foreground = true)
        continuity.flushActiveVisit()
        testDispatcher.scheduler.runCurrent()

        assertEquals(1, repo.checkpoints.size)
        assertEquals(7_000L, repo.checkpoints.last().positionMs)
    }

    @Test
    fun periodic_checkpoints_have_increasing_write_sequences() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        var now = 0L
        val continuity = ReelsSessionContinuity(this, testDispatcher, repo, nowMs = { now })
        continuity.attachHandle(ReelsSessionHandle(ReelsSessionKey("acc:101:12", 101L, "ctx"), "session", "owner", 0L))
        continuity.visitStarted(videoItem("current"), persistEligible = true)
        for (i in 1..3) {
            now += 2_000L
            continuity.sample("current", now, foreground = true)
            testDispatcher.scheduler.runCurrent()
        }
        assertEquals(3, repo.checkpoints.size)
        assertTrue(repo.checkpoints.zipWithNext().all { (earlier, later) -> later.sequence > earlier.sequence })
        assertEquals(6_000L, repo.activeCheckpoint?.positionMs)
    }

    @Test
    fun stale_visit_end_cannot_overwrite_newer_active_item() = testScope.runTest {
        val repo = FakeReelsSessionRepository()
        val continuity = ReelsSessionContinuity(
            repository = repo,
            scope = this,
            ioDispatcher = testDispatcher,
            nowMs = { testDispatcher.scheduler.currentTime },
        )
        val handle = ReelsSessionHandle(
            key = ReelsSessionKey("tg:1", 101L, "ctx"),
            sessionId = "s1",
            ownerNonce = "nonce1",
            historyGeneration = 1L,
        )
        continuity.attachHandle(handle)

        val itemA = videoItem("clip-A")
        val itemB = videoItem("clip-B")

        continuity.onVisitStarted(itemA)
        advanceTimeBy(1000L)
        continuity.onVisitStarted(itemB)

        // Now a stale visit end arrives for clip-A
        continuity.onVisitEnded("clip-A", positionMs = 15000L)
        continuity.flushActiveVisit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("clip-B", repo.activeCheckpoint?.item?.itemId)
    }
}
