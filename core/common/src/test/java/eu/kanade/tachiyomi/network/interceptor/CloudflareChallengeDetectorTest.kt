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

    @Test
    fun `managed challenge on 429 and 401 is classified (P2)`() {
        assertEquals(
            CloudflareChallengeType.MANAGED,
            CloudflareChallengeDetector.classify(429, "cloudflare", "challenge", ""),
        )
        assertEquals(
            CloudflareChallengeType.MANAGED,
            CloudflareChallengeDetector.classify(401, "cloudflare", "challenge", ""),
        )
    }

    @Test
    fun `429 rate limit without challenge markers is not a challenge (P2)`() {
        val body = "<html><body>error code: 1015</body></html>"
        assertEquals(
            CloudflareChallengeType.NONE,
            CloudflareChallengeDetector.classify(429, "cloudflare", null, body),
        )
    }

    @Test
    fun `429 with interstitial markers is a challenge (P2)`() {
        val body = "<script>window._cf_chl_opt={cvId:'3'};</script>"
        assertEquals(
            CloudflareChallengeType.INTERSTITIAL,
            CloudflareChallengeDetector.classify(429, "cloudflare", null, body),
        )
    }

    @Test
    fun `isBlockingChallengePage detects challenge titles and active containers`() {
        val justAMoment = "<html><head><title>Just a moment...</title></head><body>Checking...</body></html>"
        val botVerification = "<html><head><title>Bot Verification</title></head><body>Checking...</body></html>"
        val activeContainer = "<html><body><div id=\"challenge-stage\"></div></body></html>"
        val errorTitle = "<html><body><span id=\"challenge-error-title\">Failed</span></body></html>"

        assertTrue(CloudflareChallengeDetector.isBlockingChallengePage(justAMoment))
        assertTrue(CloudflareChallengeDetector.isBlockingChallengePage(botVerification))
        assertTrue(CloudflareChallengeDetector.isBlockingChallengePage(activeContainer))
        assertTrue(CloudflareChallengeDetector.isBlockingChallengePage(errorTitle))
    }

    @Test
    fun `isBlockingChallengePage does not falsely flag substantive chapter pages with turnstile comments`() {
        val paragraphs = (1..50).joinToString("\n") { "<p>This is paragraph $it of a long chapter in the novel.</p>" }
        val chapterWithTurnstileCommentForm = """
            <!DOCTYPE html>
            <html>
            <head><title>Chapter 105 - The Dungeon Boss</title></head>
            <body>
            <h1>Chapter 105</h1>
            $paragraphs
            <form id="commentform">
                <h3>Leave a comment</h3>
                <div class="cf-turnstile" data-sitekey="0x4AAAAAA"></div>
            </form>
            </body>
            </html>
        """.trimIndent()

        // Substantive content (paragraphs) with comment widget must NOT be flagged as blocking challenge
        assertTrue(chapterWithTurnstileCommentForm.length > 1000)
        assertFalse(CloudflareChallengeDetector.isBlockingChallengePage(chapterWithTurnstileCommentForm))
    }

    @Test
    fun `isBlockingChallengePage flags short pages dominated by turnstile challenge`() {
        val shortTurnstilePage = """
            <!DOCTYPE html>
            <html>
            <head><title>Verify</title></head>
            <body>
            <div class="cf-turnstile" data-sitekey="0x4AAAAAA"></div>
            </body>
            </html>
        """.trimIndent()

        assertTrue(CloudflareChallengeDetector.isBlockingChallengePage(shortTurnstilePage))
    }
}
