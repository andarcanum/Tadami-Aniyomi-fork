package eu.kanade.tachiyomi.network.interceptor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudflareChallengeDetectorTest {

    @Test
    fun `cloudflare server check is case insensitive and rejects others`() {
        assertTrue(CloudflareChallengeDetector.isCloudflareServer("cloudflare"))
        assertTrue(CloudflareChallengeDetector.isCloudflareServer("Cloudflare"))
        assertTrue(CloudflareChallengeDetector.isCloudflareServer("cloudflare-nginx"))
        assertFalse(CloudflareChallengeDetector.isCloudflareServer("nginx"))
        assertFalse(CloudflareChallengeDetector.isCloudflareServer(null))
    }

    @Test
    fun `managed challenge is detected from cf-mitigated header`() {
        assertTrue(CloudflareChallengeDetector.isManagedChallenge("challenge"))
        assertTrue(CloudflareChallengeDetector.isManagedChallenge("Challenge"))
        assertTrue(CloudflareChallengeDetector.isManagedChallenge(" challenge "))
        assertFalse(CloudflareChallengeDetector.isManagedChallenge(null))
        assertFalse(CloudflareChallengeDetector.isManagedChallenge(""))
    }

    @Test
    fun `classic just a moment interstitial is now detected`() {
        // Раньше этот кейс без cf-mitigated НЕ ловился.
        val body = """
            <!DOCTYPE html><html><head><title>Just a moment...</title></head>
            <body><div class="cf-browser-verification"></div>
            <script>window._cf_chl_opt={cvId:'3'};</script>
            <script src="/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page"></script>
            </body></html>
        """.trimIndent()
        assertTrue(CloudflareChallengeDetector.hasChallengeMarkers(body))
        assertFalse(CloudflareChallengeDetector.hasInteractiveMarkers(body))
        assertEquals(
            CloudflareChallengeType.INTERSTITIAL,
            CloudflareChallengeDetector.classify(503, "cloudflare", null, body),
        )
    }

    @Test
    fun `turnstile widget is detected as interactive`() {
        val body = """
            <div class="cf-turnstile" data-sitekey="0x4AAA"></div>
            <iframe src="https://challenges.cloudflare.com/cdn-cgi/challenge-platform"></iframe>
        """.trimIndent()
        assertTrue(CloudflareChallengeDetector.hasInteractiveMarkers(body))
        assertEquals(
            CloudflareChallengeType.INTERACTIVE,
            CloudflareChallengeDetector.classify(403, "cloudflare", "challenge", body),
        )
    }

    @Test
    fun `error page markers are still detected`() {
        val body = "<div id=\"challenge-error-title\">Error</div>"
        assertTrue(CloudflareChallengeDetector.hasChallengeMarkers(body))
        assertEquals(
            CloudflareChallengeType.ERROR,
            CloudflareChallengeDetector.classify(403, "cloudflare", null, body),
        )
    }

    @Test
    fun `managed challenge classified when no body markers present`() {
        assertEquals(
            CloudflareChallengeType.MANAGED,
            CloudflareChallengeDetector.classify(403, "cloudflare", "challenge", ""),
        )
    }

    @Test
    fun `normal cloudflare page is not a challenge`() {
        val body = "<html><body>Regular content served via Cloudflare CDN</body></html>"
        assertFalse(CloudflareChallengeDetector.hasChallengeMarkers(body))
        assertEquals(
            CloudflareChallengeType.NONE,
            CloudflareChallengeDetector.classify(403, "cloudflare", null, body),
        )
    }

    @Test
    fun `non error code or non cloudflare server is never a challenge`() {
        val body = "window._cf_chl_opt"
        assertEquals(
            CloudflareChallengeType.NONE,
            CloudflareChallengeDetector.classify(200, "cloudflare", "challenge", body),
        )
        assertEquals(
            CloudflareChallengeType.NONE,
            CloudflareChallengeDetector.classify(403, "nginx", "challenge", body),
        )
    }
}
