package eu.kanade.tachiyomi.network.interceptor

import java.util.Locale

/**
 * Pure (no Android runtime dependencies) detector for HTTP error pages (e.g. Next.js 500,
 * Cloudflare 5xx/4xx error screens, and web server error pages) that external sites or
 * translators return instead of expected content.
 */
object HttpErrorPageDetector {

    private val errorTitleMarkers = listOf(
        "500:",
        "500 -",
        "500 internal",
        "404:",
        "404 -",
        "404 not found",
        "403 forbidden",
        "502 bad gateway",
        "503 service",
        "504 gateway",
        "internal server error",
        "this page couldn't load",
        "this page couldn’t load",
        "web server is returning an unknown error",
    )

    private val bodyIdMarkers = listOf(
        "__next_error__",
        "error-code",
    )

    fun isErrorPage(html: String): Boolean {
        if (html.isBlank()) return false
        val lower = html.lowercase(Locale.ROOT)

        // Check for Next.js error markers (e.g. <html id="__next_error__">)
        if (bodyIdMarkers.any { it in lower }) {
            return true
        }

        // Fast title check: only inspect <title>...</title> to avoid false positives in chapter text
        val titleStart = lower.indexOf("<title>")
        if (titleStart != -1) {
            val titleEnd = lower.indexOf("</title>", startIndex = titleStart)
            if (titleEnd != -1) {
                val titleContent = lower.substring(titleStart + 7, titleEnd).trim()
                if (errorTitleMarkers.any { it in titleContent }) {
                    return true
                }
            }
        }

        // Check for specific error phrases when body is short (< 8KB; error pages are rarely novels)
        if (lower.length < 8192) {
            if ("this page couldn't load" in lower || "this page couldn’t load" in lower) {
                return true
            }
            if ("500 internal server error" in lower || "502 bad gateway" in lower ||
                "503 service unavailable" in lower
            ) {
                return true
            }
        }

        return false
    }
}
