package eu.kanade.presentation.webview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CloudflareWebViewHeadersTest {

    @Test
    fun `sanitizes cloudflare sensitive headers and keeps unrelated values`() {
        val sanitized = sanitizeCloudflareRequestHeaders(
            requestHeaders = linkedMapOf(
                "User-Agent" to "test-agent",
                "X-Requested-With" to "com.tadami.aurora",
                "sec-ch-ua" to "\"Chromium\";v=\"125\"",
                "sec-ch-ua-full-version-list" to "\"Chromium\";v=\"125.0.0.0\"",
                "Accept" to "text/html",
            ),
            contextPackageName = "com.tadami.aurora",
            spoofedPackageName = "com.tadami.aurora.spoof",
        )

        assertEquals(
            linkedMapOf(
                "User-Agent" to "test-agent",
                "Accept" to "text/html",
            ),
            sanitized,
        )
    }

    @Test
    fun `preserves x-requested-with when it belongs to another package`() {
        val sanitized = sanitizeCloudflareRequestHeaders(
            requestHeaders = linkedMapOf(
                "X-Requested-With" to "com.example.other",
                "Accept" to "text/html",
            ),
            contextPackageName = "com.tadami.aurora",
            spoofedPackageName = "com.tadami.aurora.spoof",
        )

        assertEquals(
            linkedMapOf(
                "X-Requested-With" to "com.example.other",
                "Accept" to "text/html",
            ),
            sanitized,
        )
    }
}
