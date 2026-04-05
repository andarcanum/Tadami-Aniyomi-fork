package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverRequestPolicyTest {

    @AfterEach
    fun tearDown() {
        CoverRequestPolicy.resetForTests()
    }

    @Test
    fun `cover request policy blacklists hosts after repeated failures`() {
        CoverRequestPolicy.recordFailure("example.org")
        assertFalse(CoverRequestPolicy.isBlacklisted("example.org"))

        CoverRequestPolicy.recordFailure("example.org")
        assertTrue(CoverRequestPolicy.isBlacklisted("example.org"))
    }

    @Test
    fun `cover request policy preserves fallback headers and attempt markers`() {
        val request = CoverRequestPolicy.markCoverRequest(
            Request.Builder().url("https://example.org/poster.jpg"),
            fallbackUrl = "https://fallback.example.org/poster.jpg",
            attempt = 1,
        ).build()

        assertTrue(CoverRequestPolicy.isCoverRequest(request))
        assertEquals("https://fallback.example.org/poster.jpg", CoverRequestPolicy.coverFallbackUrl(request))
        assertEquals(1, CoverRequestPolicy.coverAttempt(request))
    }
}
