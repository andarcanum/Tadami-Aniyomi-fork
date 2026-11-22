package eu.kanade.tachiyomi.network.interceptor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
    fun `clearAll releases blacklisted hosts for a retry`() {
        CoverRequestPolicy.recordFailure("example.org")
        CoverRequestPolicy.recordFailure("example.org")
        assertTrue(CoverRequestPolicy.isBlacklisted("example.org"))

        CoverRequestPolicy.clearAll()
        assertFalse(CoverRequestPolicy.isBlacklisted("example.org"))
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

    @Test
    fun `cover request policy converts IDN fallback url to punycode`() {
        val request = CoverRequestPolicy.markCoverRequest(
            Request.Builder().url("https://example.org/poster.jpg"),
            fallbackUrl = "https://ранобэ.рф/images/books/4260/vertical-73.jpeg",
        ).build()

        val fallback = CoverRequestPolicy.coverFallbackUrl(request)
        assertFalse(fallback!!.contains("ранобэ"))
        assertTrue(fallback.startsWith("https://xn--"))
        assertTrue(fallback.contains("xn--"))
    }

    @Test
    fun `404 is not treated as a host-level cover blacklist failure`() {
        assertFalse(CoverRequestPolicy.isRecoverableResponseCode(404))
        assertTrue(CoverRequestPolicy.isRecoverableResponseCode(403))
        assertTrue(CoverRequestPolicy.isRecoverableResponseCode(429))
        assertTrue(CoverRequestPolicy.isRecoverableResponseCode(503))
    }

    @Test
    fun `blacklisted host expires automatically after cooldown ttl`() {
        var nowMs = 1_000L
        CoverRequestPolicy.clockMs = { nowMs }

        CoverRequestPolicy.recordFailure("cdn.example.org")
        CoverRequestPolicy.recordFailure("cdn.example.org")
        assertTrue(CoverRequestPolicy.isBlacklisted("cdn.example.org"))

        nowMs += CoverRequestPolicy.BLACKLIST_COOLDOWN_MS - 1L
        assertTrue(CoverRequestPolicy.isBlacklisted("cdn.example.org"))

        nowMs += 1L
        assertFalse(CoverRequestPolicy.isBlacklisted("cdn.example.org"))

        // After expiry, a single new failure must not immediately re-blacklist.
        CoverRequestPolicy.recordFailure("cdn.example.org")
        assertFalse(CoverRequestPolicy.isBlacklisted("cdn.example.org"))
        CoverRequestPolicy.recordFailure("cdn.example.org")
        assertTrue(CoverRequestPolicy.isBlacklisted("cdn.example.org"))
    }

    @Test
    fun `successful non-cover request clears blacklisted host in CoverRecoveryInterceptor`() {
        CoverRequestPolicy.recordFailure("cdn.example.org")
        CoverRequestPolicy.recordFailure("cdn.example.org")
        assertTrue(CoverRequestPolicy.isBlacklisted("cdn.example.org"))

        val client = OkHttpClient.Builder()
            .addInterceptor(CoverRecoveryInterceptor())
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        client.newCall(Request.Builder().url("https://cdn.example.org/catalog").build()).execute().use { resp ->
            assertEquals(200, resp.code)
        }

        assertFalse(CoverRequestPolicy.isBlacklisted("cdn.example.org"))
    }
}
