package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlaybackProgressPersistencePolicyTest {

    @Test
    fun `persists immediately on first tick then throttles until interval elapses`() {
        val policy = PlaybackProgressPersistencePolicy(
            minPersistIntervalMs = 10_000L,
            seekJumpThresholdMs = 15_000L,
        )

        policy.shouldPersist(episodeId = 10L, positionMs = 1_000L, nowElapsedMs = 100_000L) shouldBe true
        policy.recordPersisted(episodeId = 10L, positionMs = 1_000L, nowElapsedMs = 100_000L)

        policy.shouldPersist(episodeId = 10L, positionMs = 2_000L, nowElapsedMs = 101_000L) shouldBe false
        policy.shouldPersist(episodeId = 10L, positionMs = 9_000L, nowElapsedMs = 109_000L) shouldBe false
        policy.shouldPersist(episodeId = 10L, positionMs = 11_000L, nowElapsedMs = 110_000L) shouldBe true
    }

    @Test
    fun `persists immediately on significant seek jump or episode switch`() {
        val policy = PlaybackProgressPersistencePolicy(
            minPersistIntervalMs = 10_000L,
            seekJumpThresholdMs = 15_000L,
        )

        policy.recordPersisted(episodeId = 10L, positionMs = 30_000L, nowElapsedMs = 100_000L)

        // Large forward seek triggers immediate persistence
        policy.shouldPersist(episodeId = 10L, positionMs = 60_000L, nowElapsedMs = 102_000L) shouldBe true
        // Switching episode triggers immediate persistence
        policy.shouldPersist(episodeId = 11L, positionMs = 1_000L, nowElapsedMs = 102_000L) shouldBe true
    }

    @Test
    fun `resolveChapterSkipTarget advances to next chapter or end of episode for trailing ending`() {
        val chapters = listOf(
            IndexedSegment(name = "Opening", start = 10f),
            IndexedSegment(name = "Part A", start = 100f),
            IndexedSegment(name = "Ending", start = 1320f),
        )

        PlaybackProgressPersistencePolicy.resolveChapterSkipTarget(
            chapters = chapters,
            chapterIndex = 0,
            currentPosition = 25f,
            duration = 1420f,
        ) shouldBe 100f

        PlaybackProgressPersistencePolicy.resolveChapterSkipTarget(
            chapters = chapters,
            chapterIndex = 2,
            currentPosition = 1330f,
            duration = 1420f,
        ) shouldBe 1419f
    }
}
