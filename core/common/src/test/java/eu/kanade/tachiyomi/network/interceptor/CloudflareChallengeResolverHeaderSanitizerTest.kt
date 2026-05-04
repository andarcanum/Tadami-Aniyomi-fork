package eu.kanade.tachiyomi.network.interceptor

import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldNotContainKey
import org.junit.jupiter.api.Test

class CloudflareChallengeResolverHeaderSanitizerTest {

    @Test
    fun `sanitizeCloudflareReplayHeaders removes fingerprinting and unsafe headers`() {
        val headers = sanitizeCloudflareReplayHeaders(
            requestHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0",
                "Cookie" to "cf_chl_rc_ni=2",
                "Host" to "ravenscans.org",
                "Connection" to "Keep-Alive",
                "X-Requested-With" to "com.tadami.aurora.localdev",
                "sec-ch-ua" to "\"Android WebView\";v=\"145\"",
                "sec-ch-ua-full-version-list" to "\"Android WebView\";v=\"145.0.0.0\"",
            ),
            contextPackageName = "com.tadami.aurora.localdev",
            spoofedPackageName = "com.android.chrome",
        )

        headers shouldContain ("User-Agent" to "Mozilla/5.0")
        headers shouldContain ("Cookie" to "cf_chl_rc_ni=2")
        headers.shouldNotContainKey("Host")
        headers.shouldNotContainKey("Connection")
        headers.shouldNotContainKey("X-Requested-With")
        headers.shouldNotContainKey("sec-ch-ua")
        headers.shouldNotContainKey("sec-ch-ua-full-version-list")
    }
}
