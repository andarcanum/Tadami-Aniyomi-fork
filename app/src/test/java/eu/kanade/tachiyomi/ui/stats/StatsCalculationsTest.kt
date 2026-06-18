package eu.kanade.tachiyomi.ui.stats

import io.kotest.matchers.doubles.shouldBeNaN
import io.kotest.matchers.floats.shouldBeExactly
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StatsCalculationsTest {

    private companion object {
        const val ONGOING = 1
        const val COMPLETED = 2
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6
        val TERMINAL_STATUSES = setOf(PUBLISHING_FINISHED, CANCELLED, ON_HIATUS)
    }

    @Test
    fun `completed statistic depends only on completed status`() {
        StatsCalculations.isCompletedStatus(status = 2, completedStatus = 2) shouldBe true
        StatsCalculations.isCompletedStatus(status = 1, completedStatus = 2) shouldBe false
    }

    @Test
    fun `manual completed status counts title as completed even when earlier content is not marked read`() {
        StatsCalculations.isCompletedByUserConsumption(
            sourceStatus = ONGOING,
            customStatus = COMPLETED,
            completedStatus = COMPLETED,
            terminalFallbackStatuses = TERMINAL_STATUSES,
            consumedCount = 50L,
            totalCount = 100L,
        ) shouldBe true
    }

    @Test
    fun `source completed title requires all known content to be consumed`() {
        StatsCalculations.isCompletedByUserConsumption(
            sourceStatus = COMPLETED,
            customStatus = null,
            completedStatus = COMPLETED,
            terminalFallbackStatuses = TERMINAL_STATUSES,
            consumedCount = 99L,
            totalCount = 100L,
        ) shouldBe false

        StatsCalculations.isCompletedByUserConsumption(
            sourceStatus = COMPLETED,
            customStatus = null,
            completedStatus = COMPLETED,
            terminalFallbackStatuses = TERMINAL_STATUSES,
            consumedCount = 100L,
            totalCount = 100L,
        ) shouldBe true
    }

    @Test
    fun `terminal fallback status counts completed when all known content is consumed`() {
        StatsCalculations.isCompletedByUserConsumption(
            sourceStatus = ON_HIATUS,
            customStatus = null,
            completedStatus = COMPLETED,
            terminalFallbackStatuses = TERMINAL_STATUSES,
            consumedCount = 100L,
            totalCount = 100L,
        ) shouldBe true
    }

    @Test
    fun `ongoing caught up title is not counted as completed without manual override`() {
        StatsCalculations.isCompletedByUserConsumption(
            sourceStatus = ONGOING,
            customStatus = null,
            completedStatus = COMPLETED,
            terminalFallbackStatuses = TERMINAL_STATUSES,
            consumedCount = 100L,
            totalCount = 100L,
        ) shouldBe false
    }

    @Test
    fun `progress fraction is safe and capped for inconsistent counters`() {
        StatsCalculations.progressFraction(done = 7, total = 10) shouldBeExactly 0.7f
        StatsCalculations.progressFraction(done = 12, total = 10) shouldBeExactly 1.0f
        StatsCalculations.progressFraction(done = 3, total = 0) shouldBeExactly 0.0f
        StatsCalculations.progressFraction(done = -3, total = 10) shouldBeExactly 0.0f
    }

    @Test
    fun `mean title score averages each title before global average`() {
        StatsCalculations.meanTitleScore(
            listOf(
                listOf(10.0, 8.0),
                listOf(6.0),
                listOf(0.0, Double.NaN),
            ),
        ) shouldBe 7.5
    }

    @Test
    fun `mean title score is NaN when there are no usable scores`() {
        StatsCalculations.meanTitleScore(
            listOf(
                listOf(0.0),
                listOf(Double.NaN),
                emptyList(),
            ),
        ).shouldBeNaN()
    }

    @Test
    fun `watch duration uses full duration for seen episodes and bounded progress for partial episodes`() {
        StatsCalculations.watchDurationMillis(
            listOf(
                WatchProgress(seen = true, lastSeenMillis = 500L, totalMillis = 1_000L),
                WatchProgress(seen = false, lastSeenMillis = 400L, totalMillis = 1_000L),
                WatchProgress(seen = false, lastSeenMillis = 2_000L, totalMillis = 1_000L),
                WatchProgress(seen = false, lastSeenMillis = -100L, totalMillis = 1_000L),
            ),
        ) shouldBeExactly 2_400L
    }
}
