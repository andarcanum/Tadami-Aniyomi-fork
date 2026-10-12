package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor(
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val userAgent = originalRequest.header("User-Agent")?.takeIf { it.isNotBlank() }
            ?: defaultUserAgentProvider()
        val builder = originalRequest.newBuilder()
        var modified = false

        if (originalRequest.header("User-Agent").isNullOrBlank()) {
            builder.removeHeader("User-Agent").addHeader("User-Agent", userAgent)
            modified = true
        }

        val hints = resolveClientHints(userAgent)
        if (hints != null) {
            if (originalRequest.header("sec-ch-ua").isNullOrBlank()) {
                builder.addHeader("sec-ch-ua", hints.secChUa)
                modified = true
            }
            if (originalRequest.header("sec-ch-ua-mobile").isNullOrBlank()) {
                builder.addHeader("sec-ch-ua-mobile", hints.secChUaMobile)
                modified = true
            }
            if (originalRequest.header("sec-ch-ua-platform").isNullOrBlank()) {
                builder.addHeader("sec-ch-ua-platform", hints.secChUaPlatform)
                modified = true
            }
        }

        return if (modified) {
            chain.proceed(builder.build())
        } else {
            chain.proceed(originalRequest)
        }
    }

    data class ClientHints(
        val secChUa: String,
        val secChUaMobile: String,
        val secChUaPlatform: String,
    )

    companion object {
        private val CHROME_VERSION_REGEX = Regex("""\bChrome/(\d+)""")
        private val WEBVIEW_MARKER_REGEX = Regex("""\bwv\b""", RegexOption.IGNORE_CASE)

        fun extractChromeMajorVersion(userAgent: String): String? {
            return CHROME_VERSION_REGEX.find(userAgent)?.groupValues?.getOrNull(1)
        }

        fun isAndroidWebView(userAgent: String): Boolean {
            return userAgent.contains("Version/4.0", ignoreCase = true) ||
                WEBVIEW_MARKER_REGEX.containsMatchIn(userAgent)
        }

        fun resolveClientHints(userAgent: String): ClientHints? {
            if (!userAgent.contains("Chrome", ignoreCase = true)) return null

            val chromeVersion = extractChromeMajorVersion(userAgent) ?: "134"
            val isWebView = isAndroidWebView(userAgent)

            val secChUa = if (isWebView) {
                "\"Android WebView\";v=\"$chromeVersion\", \"Chromium\";v=\"$chromeVersion\", \"Not:A-Brand\";v=\"24\""
            } else {
                "\"Chromium\";v=\"$chromeVersion\", \"Not:A-Brand\";v=\"24\", \"Google Chrome\";v=\"$chromeVersion\""
            }

            val isMobile = if (userAgent.contains("Mobile", ignoreCase = true)) "?1" else "?0"

            val platform = when {
                userAgent.contains("Android", ignoreCase = true) -> "\"Android\""
                userAgent.contains("Windows", ignoreCase = true) -> "\"Windows\""
                userAgent.contains("Macintosh", ignoreCase = true) -> "\"macOS\""
                userAgent.contains("Linux", ignoreCase = true) -> "\"Linux\""
                else -> "\"Android\""
            }

            return ClientHints(
                secChUa = secChUa,
                secChUaMobile = isMobile,
                secChUaPlatform = platform,
            )
        }
    }
}
