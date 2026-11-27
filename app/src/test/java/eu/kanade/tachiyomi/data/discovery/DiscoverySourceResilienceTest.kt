package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DiscoverySourceResilienceTest {

    @Test
    fun `parse and format source failure history round-trips correctly`() {
        val records = mapOf(
            101L to SourceFailureRecord(sourceId = 101L, lastFailedAt = 1000L, failCount = 1),
            102L to SourceFailureRecord(sourceId = 102L, lastFailedAt = 2000L, failCount = 3),
        )

        val csv = formatSourceFailureHistory(records)
        val parsed = parseSourceFailureHistory(csv)

        parsed.size shouldBe 2
        parsed[101L]?.failCount shouldBe 1
        parsed[101L]?.lastFailedAt shouldBe 1000L
        parsed[102L]?.failCount shouldBe 3
        parsed[102L]?.lastFailedAt shouldBe 2000L
    }

    @Test
    fun `calculateSourceReliabilityFactor gives full 1_0 to healthy sources`() {
        val now = 1_000_000L
        val history = emptyMap<Long, SourceFailureRecord>()

        calculateSourceReliabilityFactor(sourceId = 101L, history = history, nowMs = now) shouldBe 1.0
    }

    @Test
    fun `calculateSourceReliabilityFactor heavily discounts recently failed sources`() {
        val now = 1_000_000L
        val history = mapOf(
            101L to SourceFailureRecord(sourceId = 101L, lastFailedAt = now, failCount = 1),
            102L to SourceFailureRecord(sourceId = 102L, lastFailedAt = now, failCount = 3),
        )

        val factor1 = calculateSourceReliabilityFactor(sourceId = 101L, history = history, nowMs = now)
        val factor3 = calculateSourceReliabilityFactor(sourceId = 102L, history = history, nowMs = now)

        factor1 shouldBeLessThan 0.5
        factor3 shouldBeLessThan 0.1
        factor1 shouldBeGreaterThan factor3
    }

    @Test
    fun `calculateSourceReliabilityFactor recovers smoothly over time`() {
        val failedAt = 1_000_000L
        val history = mapOf(
            101L to SourceFailureRecord(sourceId = 101L, lastFailedAt = failedAt, failCount = 1),
        )

        val immediate = calculateSourceReliabilityFactor(101L, history, nowMs = failedAt)
        val halfway = calculateSourceReliabilityFactor(101L, history, nowMs = failedAt + SOURCE_COOLOFF_MS / 2)
        val expired = calculateSourceReliabilityFactor(101L, history, nowMs = failedAt + SOURCE_COOLOFF_MS + 1000L)

        immediate shouldBeLessThan halfway
        halfway shouldBeLessThan expired
        expired shouldBe 1.0
    }

    @Test
    fun `updateSourceFailures records failures and rewards successes`() {
        val now = 1_000_000L
        val initial = mapOf(
            101L to SourceFailureRecord(sourceId = 101L, lastFailedAt = now - 5000L, failCount = 2),
        )

        // 102 fails for the first time, 101 succeeds
        val updated = updateSourceFailures(
            currentHistory = initial,
            failedIds = setOf(102L),
            successfulIds = setOf(101L),
            nowMs = now,
        )

        updated[102L]?.failCount shouldBe 1
        updated[102L]?.lastFailedAt shouldBe now
        updated[101L]?.failCount shouldBe 1 // decremented from 2 to 1
    }
}
