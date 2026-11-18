package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExternalApiThrottleTest {

    @Test
    fun `nextWaitMs is positive inside interval and zero after`() {
        ExternalApiThrottle.nextWaitMs(lastCallAt = 1000L, nowMs = 1500L, minIntervalMs = 700L) shouldBe 200L
        ExternalApiThrottle.nextWaitMs(lastCallAt = 1000L, nowMs = 1700L, minIntervalMs = 700L) shouldBe 0L
        ExternalApiThrottle.nextWaitMs(lastCallAt = 0L, nowMs = 1700L, minIntervalMs = 700L) shouldBe 0L
    }

    @Test
    fun `api intervals respect documented rate limits`() {
        // Shikimori: 5 rps / 90 rpm -> >=667ms; берём 700ms
        (ExternalApiThrottle.Api.SHIKIMORI.minIntervalMs >= 667L) shouldBe true
        // Jikan: 60 rpm -> >=1000ms; берём 1100ms
        (ExternalApiThrottle.Api.JIKAN.minIntervalMs >= 1000L) shouldBe true
        // MangaUpdates: лимиты не опубликованы — консервативно
        (ExternalApiThrottle.Api.MANGAUPDATES.minIntervalMs >= 250L) shouldBe true
    }

    @Test
    fun `acquire spaces consecutive calls by single minIntervalMs rather than doubling`(): Unit =
        kotlinx.coroutines.runBlocking {
            val start = System.currentTimeMillis()
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.MANGAUPDATES)
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.MANGAUPDATES)
            val elapsed = System.currentTimeMillis() - start
            (elapsed < ExternalApiThrottle.Api.MANGAUPDATES.minIntervalMs * 2 - 50L) shouldBe true
        }
}
