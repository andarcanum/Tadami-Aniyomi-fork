package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.toAsciiUrl
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

object CoverRequestPolicy {

    const val HEADER_COVER_REQUEST = "X-Aurora-Cover-Request"
    const val HEADER_COVER_FALLBACK_URL = "X-Aurora-Cover-Fallback-Url"
    const val HEADER_COVER_ATTEMPT = "X-Aurora-Cover-Attempt"

    private const val FAILURE_THRESHOLD = 2

    /** Cooldown after which a blacklisted cover host is eligible for a fresh retry. */
    internal const val BLACKLIST_COOLDOWN_MS = 60_000L

    @Volatile
    internal var clockMs: () -> Long = { System.nanoTime() / 1_000_000L }

    private data class HostState(
        var failures: Int = 0,
        var blacklistedAtMs: Long = 0L,
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
            builder.header(HEADER_COVER_FALLBACK_URL, fallbackUrl.toAsciiUrl())
        }
        return builder
    }

    /**
     * Host-wide failure codes that justify temporarily pausing cover fetches for a host.
     * HTTP 404 is deliberately excluded: a missing cover URL is per-resource and must
     * never blacklist healthy covers hosted on the same CDN.
     */
    fun isRecoverableResponseCode(code: Int): Boolean {
        return code in setOf(403, 429, 500, 502, 503)
    }

    fun recordFailure(host: String) {
        if (host.isEmpty()) return
        val now = clockMs()
        val state = hostStates.getOrPut(host) { HostState() }
        synchronized(state) {
            if (state.failures >= FAILURE_THRESHOLD && now - state.blacklistedAtMs >= BLACKLIST_COOLDOWN_MS) {
                state.failures = 0
                state.blacklistedAtMs = 0L
            }
            state.failures += 1
            if (state.failures >= FAILURE_THRESHOLD && state.blacklistedAtMs == 0L) {
                state.blacklistedAtMs = now
            }
        }
    }

    fun clear(host: String) {
        hostStates.remove(host)
    }

    /**
     * Drops every recorded failure (e.g. when connectivity is restored): hosts
     * blacklisted by a broken network (bad VPN exit, captive portal) get one
     * honest retry instead of staying pinned to the placeholder until the
     * process restarts.
     */
    fun clearAll() {
        hostStates.clear()
    }

    fun isBlacklisted(host: String): Boolean {
        if (host.isEmpty()) return false
        val state = hostStates[host] ?: return false
        val now = clockMs()
        synchronized(state) {
            if (state.failures < FAILURE_THRESHOLD) return false
            if (now - state.blacklistedAtMs >= BLACKLIST_COOLDOWN_MS) {
                state.failures = 0
                state.blacklistedAtMs = 0L
                hostStates.remove(host, state)
                return false
            }
            return true
        }
    }

    fun resetForTests() {
        clockMs = { System.nanoTime() / 1_000_000L }
        clearAll()
    }
}
