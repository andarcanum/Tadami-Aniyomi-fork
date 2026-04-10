package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

object CoverRequestPolicy {

    const val HEADER_COVER_REQUEST = "X-Aurora-Cover-Request"
    const val HEADER_COVER_FALLBACK_URL = "X-Aurora-Cover-Fallback-Url"
    const val HEADER_COVER_ATTEMPT = "X-Aurora-Cover-Attempt"

    private const val FAILURE_THRESHOLD = 2

    private data class HostState(
        var failures: Int = 0,
    )

    private val hostStates = ConcurrentHashMap<String, HostState>()

    fun isCoverRequest(request: Request): Boolean {
        return request.header(HEADER_COVER_REQUEST) == "1"
    }

    fun coverFallbackUrl(request: Request): String? {
        return request.header(HEADER_COVER_FALLBACK_URL)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun coverAttempt(request: Request): Int {
        return request.header(HEADER_COVER_ATTEMPT)
            ?.trim()
            ?.toIntOrNull()
            ?: 0
    }

    fun markCoverRequest(
        builder: Request.Builder,
        fallbackUrl: String? = null,
        attempt: Int = 0,
    ): Request.Builder {
        builder.header(HEADER_COVER_REQUEST, "1")
        builder.header(HEADER_COVER_ATTEMPT, attempt.toString())
        if (!fallbackUrl.isNullOrBlank()) {
            builder.header(HEADER_COVER_FALLBACK_URL, fallbackUrl.trim())
        }
        return builder
    }

    fun isRecoverableResponseCode(code: Int): Boolean {
        return code in setOf(403, 404, 429, 500, 502, 503)
    }

    fun recordFailure(host: String) {
        val state = hostStates.getOrPut(host) { HostState() }
        synchronized(state) {
            state.failures += 1
        }
    }

    fun clear(host: String) {
        hostStates.remove(host)
    }

    fun isBlacklisted(host: String): Boolean {
        val state = hostStates[host] ?: return false
        synchronized(state) {
            return state.failures >= FAILURE_THRESHOLD
        }
    }

    fun resetForTests() {
        hostStates.clear()
    }
}
