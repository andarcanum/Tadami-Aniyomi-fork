package eu.kanade.tachiyomi.network.interceptor

import java.util.concurrent.ConcurrentHashMap

/**
 * Hosts whose OkHttp fingerprint Cloudflare distrusts while the WebView fingerprint passes.
 *
 * Symptom (NovelUpdates): every OkHttp fetch is challenged, the hidden solve WebView loads a
 * clean logged-in page (no widget, no challenge markers) and therefore never earns a
 * cf_clearance, the 30s solve is wasted on every request, and the manual-solve prompt points
 * the user at a WebView page with no captcha to pass. The only working path is the
 * [eu.kanade.tachiyomi.extension.novel.runtime.NovelWebViewFetchBridge] same-origin fetch,
 * which reuses the trusted WebView stack plus the shared CookieManager (login persists).
 *
 * Unlike [CloudflareManualSolveRegistry] (human can help, show the captcha button), a bridged
 * host needs no manual action: plugin fetches route via the bridge silently. Dynamic entries live
 * for a bounded cooldown period (15 minutes), after which fast OkHttp traffic resumes automatically.
 */
object CloudflareBridgeRegistry {

    private const val TTL_MS = 15 * 60 * 1000L

    /**
     * Hosts where OkHttp is systematically challenged but the WebView passes clean.
     * Dynamic hosts are registered via [mark]. With Chromium client hints in place,
     * NovelUpdates serves catalogue and search queries fast via OkHttp without
     * needing static pre-bridging.
     */
    private val alwaysBridgedHosts = emptySet<String>()

    private val bridged = ConcurrentHashMap<String, Long>()

    fun mark(host: String) {
        bridged[host.lowercase()] = System.currentTimeMillis()
    }

    fun unmark(host: String) {
        bridged.remove(host.lowercase())
    }

    fun isBridged(host: String): Boolean {
        val normalized = host.lowercase()
        if (normalized in alwaysBridgedHosts) return true
        val markedAt = bridged[normalized] ?: return false
        if (System.currentTimeMillis() - markedAt > TTL_MS) {
            bridged.remove(normalized, markedAt)
            return false
        }
        return true
    }

    fun clearAll() {
        bridged.clear()
    }
}
