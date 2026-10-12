package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudflareChallengeResolverTest {

    @Test
    fun `interactive probe only treats human widgets as interactive`() {
        assertTrue(INTERACTIVE_WIDGET_PROBE.contains("cf-turnstile"))
        assertTrue(INTERACTIVE_WIDGET_PROBE.contains("challenges.cloudflare.com"))

        assertFalse(INTERACTIVE_WIDGET_PROBE.contains("challenge-stage"))
        assertFalse(INTERACTIVE_WIDGET_PROBE.contains("cf-please-wait"))
    }

    @Test
    fun `challenge markers probe covers interstitial error and widget markers`() {
        assertTrue(CHALLENGE_MARKERS_PROBE.contains("just a moment"))
        assertTrue(CHALLENGE_MARKERS_PROBE.contains("challenge-platform"))
        assertTrue(CHALLENGE_MARKERS_PROBE.contains("challenge-error-title"))
        assertTrue(CHALLENGE_MARKERS_PROBE.contains("cf-turnstile"))
    }

    @Test
    fun `clearance equal to the old cookie is not new (P4)`() {
        val original = "https://example.org/page".toHttpUrl()
        val old = Cookie.Builder().domain("example.org").name("cf_clearance").value("abc").build()
        assertFalse(hasNewCloudflareClearance(original, old) { listOf(old) })
    }

    @Test
    fun `fresh clearance on the original host is new (P4)`() {
        val original = "https://example.org/page".toHttpUrl()
        val old = Cookie.Builder().domain("example.org").name("cf_clearance").value("abc").build()
        val fresh = Cookie.Builder().domain("example.org").name("cf_clearance").value("xyz").build()
        assertTrue(hasNewCloudflareClearance(original, old) { listOf(fresh) })
    }

    @Test
    fun `clearance only on a foreign host is ignored (P4)`() {
        val original = "https://example.org/page".toHttpUrl()
        val foreign = Cookie.Builder().domain("other.com").name("cf_clearance").value("xyz").build()
        assertFalse(
            hasNewCloudflareClearance(original, null) { url ->
                if (url.host == "other.com") listOf(foreign) else emptyList()
            },
        )
    }

    @Test
    fun `first clearance with no old cookie is new (P4)`() {
        val original = "https://example.org/page".toHttpUrl()
        val fresh = Cookie.Builder().domain("example.org").name("cf_clearance").value("xyz").build()
        assertTrue(hasNewCloudflareClearance(original, null) { listOf(fresh) })
    }
}
