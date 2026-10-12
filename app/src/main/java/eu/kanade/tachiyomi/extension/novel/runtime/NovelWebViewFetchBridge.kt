package eu.kanade.tachiyomi.extension.novel.runtime

import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeDetector
import eu.kanade.tachiyomi.network.interceptor.HttpErrorPageDetector
import eu.kanade.tachiyomi.network.interceptor.attachWebViewToWindow
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Executes plugin fetches through a WebView's own network stack for hosts whose Cloudflare
 * challenge the OkHttp path cannot clear.
 *
 * Cloudflare trusts the WebView fingerprint and challenges OkHttp's, and a trusted WebView
 * navigation is often let through *without* a challenge - which means no cf_clearance cookie is
 * ever issued for OkHttp to replay. Running the very same request as a same-origin `fetch()`
 * inside a WebView on the target origin reuses the browser fingerprint and the shared cookie
 * jar, so protected endpoints (search, ranking, chapter POSTs) answer normally.
 *
 * The bridge keeps one hidden WebView per process, navigates it to the target origin once and
 * serializes requests through it. Response bodies travel back through a JavascriptInterface in
 * chunks to stay under the binder transaction limit.
 */
internal object NovelWebViewFetchBridge {

    private const val TAG = "NovelWebViewBridge"
    private const val PREPARE_TIMEOUT_MS = 5_000L
    private const val NAVIGATE_TIMEOUT_MS = 25_000L
    private const val FETCH_TIMEOUT_MS = 45_000L
    private const val CHUNK_SIZE = 256 * 1024
    private const val MAX_CACHED_RESPONSES = 128
    private const val MAX_CACHED_BODY_CHARS = 512 * 1024
    private const val MIN_HOST_INTERVAL_MS = 600L

    // Lazy: unit tests touch the pure helpers without an Android looper present.
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val bridgeLock = Any()
    private val cacheLock = Any()
    private val pendingCalls = ConcurrentHashMap<String, BridgeCall>()
    private val lastRequestTimeByHost = ConcurrentHashMap<String, Long>()
    private val json = Json { ignoreUnknownKeys = true }

    private var webView: WebView? = null

    @Volatile
    private var loadedOrigin: String? = null

    @Volatile
    private var navigationLatch: CountDownLatch? = null

    @Volatile
    private var activeCheckComplete: (() -> Unit)? = null

    @Volatile
    private var lastMainFrameStatusCode: Int = 200

    /**
     * Last successfully fetched page URL per bridge, used as context / Referer for non-GET calls.
     *
     * A browser issues plugin POSTs from the page the plugin just read (e.g. the series page), so
     * the request carries that page as Referer and its DOM as context.
     */
    @Volatile
    private var lastPageUrl: String? = null

    @Volatile
    private var loadedPageUrl: String? = null

    @Volatile
    private var activePageFinished: Boolean = false

    @Volatile
    private var pageFinishedAtMs: Long = 0L

    /**
     * Short-lived memory cache for bridge GET responses. NovelUpdates rate-limits bursts hard
     * (catalogue paging + details + chapter + prefetch within seconds) while the bridge
     * bypasses OkHttp's HTTP cache entirely, so every re-entry/duplicate tap paid a full
     * network hit. Served entries are 200-299 only, guarded by per-endpoint TTLs; POSTs
     * (admin-ajax chapter lists depend on login state) are never cached. Access is synchronized
     * on [cacheLock] so cache hits can return with zero delay without contending for [bridgeLock].
     */
    private val responseCache = object : LinkedHashMap<String, CachedBridgeResponse>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedBridgeResponse>?): Boolean {
            return size > MAX_CACHED_RESPONSES
        }
    }

    private class CachedBridgeResponse(
        val atMs: Long,
        val ttlMs: Long,
        val status: Int,
        val url: String,
        val headers: Map<String, String>,
        val body: String?,
    ) {
        fun isFresh(nowMs: Long): Boolean = nowMs - atMs < ttlMs
    }

    private class BridgeCall {
        val payload = StringBuilder()
        val finished = CountDownLatch(1)

        @Volatile
        var error: String? = null
    }

    @Serializable
    private data class BridgePayload(
        val status: Int,
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
    )

    fun fetchBlocking(
        url: String,
        options: NovelJsRuntimeFactory.JsFetchRequest,
    ): NovelJsRuntimeFactory.JsFetchResponse? {
        val targetOrigin = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: return null
        val host = url.toHttpUrlOrNull()?.host
        val isGet = options.method.equals("GET", ignoreCase = true)

        if (isGet) {
            cachedBridgeResponse(url)?.let { return it }
        }

        synchronized(bridgeLock) {
            logBridgeCookiePresence(url)

            if (isGet && isRedirectUrl(url)) {
                try {
                    navigateAndExtractPage(url)?.let { payload ->
                        if (payload.status in 200..299) {
                            lastPageUrl = payload.url
                            val ttl = bridgeCacheTtlMs(url)
                            cacheBridgeResponse(url, payload, ttl)
                            cacheBridgeResponse(payload.url, payload, ttl)
                        }
                        return toFetchResponse(payload)
                    }
                } finally {
                    markHostDone(host)
                }
                return null
            }

            paceHost(host)
            try {
                if (!prepareOnMain(targetOrigin)) return null
                if (loadedOrigin != targetOrigin) return null

                runBridgeFetchWithRateLimitRetry(url, options)?.let { payload ->
                    if (isGet && payload.status in 200..299) {
                        lastPageUrl = url
                        cacheBridgeResponse(url, payload, bridgeCacheTtlMs(url))
                    }
                    return toFetchResponse(payload)
                }

                // Same-origin fetch above may fail if a non-/extnu/ endpoint redirects cross-origin.
                // Navigate directly and extract DOM.
                if (!isGet) return null
                val finalUrl = navigateToUrl(url) ?: return null
                val originRoot = "$targetOrigin/"
                if (finalUrl == originRoot && url != originRoot) return null

                extractDomFromWebView(finalUrl)?.let { payload ->
                    if (payload.status in 200..299) {
                        lastPageUrl = payload.url
                        val ttl = bridgeCacheTtlMs(url)
                        cacheBridgeResponse(url, payload, ttl)
                        if (payload.url != url) {
                            cacheBridgeResponse(payload.url, payload, ttl)
                        }
                    }
                    return toFetchResponse(payload)
                }
                return null
            } finally {
                markHostDone(host)
            }
        }
    }

    /**
     * Determines whether [url] is a known redirect endpoint (such as NovelUpdates' /extnu/ chapter
     * links that bounce to external translator sites). These must be navigated directly rather than
     * attempted with same-origin CORS fetch().
     */
    internal fun isRedirectUrl(url: String): Boolean {
        val path = url.toHttpUrlOrNull()?.encodedPath.orEmpty()
        return path.startsWith("/extnu/")
    }

    internal fun minHostIntervalMs(host: String?): Long {
        if (host != null && host.contains("novelupdates.com", ignoreCase = true)) {
            return 1_500L
        }
        return MIN_HOST_INTERVAL_MS
    }

    /**
     * Enforces a minimum interval between consecutive requests to the same host to prevent
     * Cloudflare and origin burst rate-limiting.
     */
    private fun paceHost(host: String?) {
        if (host == null) return
        val now = System.currentTimeMillis()
        val lastTime = lastRequestTimeByHost[host] ?: 0L
        val elapsed = now - lastTime
        val minInterval = minHostIntervalMs(host)
        if (elapsed < minInterval) {
            val sleepMs = minInterval - elapsed
            try {
                Thread.sleep(sleepMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun markHostDone(host: String?) {
        if (host != null) {
            lastRequestTimeByHost[host] = System.currentTimeMillis()
        }
    }

    /**
     * Cookie-jar presence probe (names only, never values): tells login death apart from
     * server rejection. If the wordpress session names vanish from the shared jar, the client
     * lost them; if they are present yet admin-ajax answers "0", the server killed the session.
     */
    private fun logBridgeCookiePresence(url: String) {
        runCatching {
            val raw = android.webkit.CookieManager.getInstance().getCookie(url).orEmpty()
            val names = raw.split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }
            val hasLogin = names.any { it.startsWith("wordpress_logged_in_") }
            val hasCf = names.any { it == "cf_clearance" }
            logcat(priority = LogPriority.DEBUG, tag = TAG) {
                "cookies host=${url.toHttpUrlOrNull()?.host} login=$hasLogin cf=$hasCf names=$names"
            }
        }
    }

    private fun cacheKey(url: String): String = "GET $url"

    private fun cachedBridgeResponse(url: String): NovelJsRuntimeFactory.JsFetchResponse? {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            val cached = responseCache[cacheKey(url)]
            if (cached != null && cached.isFresh(now)) {
                logcat(priority = LogPriority.DEBUG, tag = TAG) {
                    "cache hit status=${cached.status} url=$url"
                }
                return NovelJsRuntimeFactory.JsFetchResponse(
                    status = cached.status,
                    url = cached.url,
                    headers = cached.headers,
                    body = cached.body,
                    bodyBase64 = null,
                )
            }
            if (cached != null) {
                responseCache.remove(cacheKey(url))
            }
            return null
        }
    }

    private fun cacheBridgeResponse(url: String, payload: BridgePayload, ttlMs: Long) {
        val bodyBytes = payload.body?.length ?: 0
        if (bodyBytes > MAX_CACHED_BODY_CHARS) return
        synchronized(cacheLock) {
            responseCache[cacheKey(url)] = CachedBridgeResponse(
                atMs = System.currentTimeMillis(),
                ttlMs = ttlMs,
                status = payload.status,
                url = payload.url,
                headers = payload.headers,
                body = payload.body,
            )
        }
    }

    /**
     * Fresh catalogue pages go stale fast (new releases/rankings), chapter/translator content
     * is static once published.
     */
    internal fun bridgeCacheTtlMs(url: String): Long {
        val path = url.toHttpUrlOrNull()?.encodedPath.orEmpty()
        return when {
            path.startsWith("/extnu/") -> 30 * 60 * 1_000L
            path.startsWith("/series/") -> 10 * 60 * 1_000L
            path.startsWith("/series-ranking/") || path.startsWith("/series-finder/") -> 10 * 60 * 1_000L
            else -> 5 * 60 * 1_000L
        }
    }

    /**
     * One fetch plus retries on HTTP 429: NovelUpdates rate-limits bursts (catalogue
     * paging + details + chapter in seconds) and answers with a ~6k challenge-flavoured page
     * the plugin mistakes for a captcha. Honors Retry-After when present, otherwise backs off
     * 2.5s then 6s; the last result is returned as-is (no endless loops inside a blocking bridge).
     */
    private fun runBridgeFetchWithRateLimitRetry(
        url: String,
        options: NovelJsRuntimeFactory.JsFetchRequest,
    ): BridgePayload? {
        var attempt = runBridgeFetch(url, options) ?: return null
        if (attempt.status != 429) return attempt
        for (backoff in longArrayOf(2_500L, 6_000L)) {
            val waitMs = retryAfterMs(attempt.headers).coerceIn(1_000L, 8_000L).coerceAtLeast(backoff)
            logcat(priority = LogPriority.WARN, tag = TAG) {
                "rate-limited (429), retrying after ${waitMs}ms url=$url"
            }
            try {
                Thread.sleep(waitMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return attempt
            }
            attempt = runBridgeFetch(url, options) ?: return attempt
            if (attempt.status != 429) return attempt
        }
        return attempt
    }

    internal fun retryAfterMs(headers: Map<String, String>): Long {
        val raw = headers.entries.firstOrNull { it.key.equals("retry-after", ignoreCase = true) }?.value
            ?.trim().orEmpty()
        raw.toLongOrNull()?.let { return it * 1_000L }
        return 2_500L
    }

    private fun toFetchResponse(payload: BridgePayload): NovelJsRuntimeFactory.JsFetchResponse {
        return NovelJsRuntimeFactory.JsFetchResponse(
            status = payload.status,
            url = payload.url,
            headers = payload.headers,
            body = payload.body,
            bodyBase64 = null,
        )
    }

    /** Runs one same-origin fetch inside the prepared bridge document. Null on timeout/error. */
    private fun runBridgeFetch(
        url: String,
        options: NovelJsRuntimeFactory.JsFetchRequest,
    ): BridgePayload? {
        val token = UUID.randomUUID().toString()
        val call = BridgeCall()
        pendingCalls[token] = call
        try {
            val script = FETCH_SCRIPT.replace("__CFG__", buildBridgeConfigJson(token, url, options))
            handler.post { webView?.evaluateJavascript(script, null) }
            if (!call.finished.await(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                logcat(priority = LogPriority.WARN, tag = TAG) { "fetch timed out url=$url" }
                return null
            }
            call.error?.let { error ->
                logcat(priority = LogPriority.WARN, tag = TAG) { "fetch failed url=$url error=$error" }
                return null
            }
            val payload = runCatching {
                json.decodeFromString(BridgePayload.serializer(), call.payload.toString())
            }.getOrNull()
            if (payload == null) {
                logcat(priority = LogPriority.WARN, tag = TAG) { "fetch returned unparsable payload url=$url" }
                return null
            }
            logcat(priority = LogPriority.DEBUG, tag = TAG) {
                "fetch via webview status=${payload.status} bytes=${payload.body?.length ?: 0} url=$url"
            }
            return payload
        } finally {
            pendingCalls.remove(token)
        }
    }

    /**
     * Navigates the WebView directly to [url] (following HTTP 302 redirects to the translator site
     * and passing any Cloudflare browser checks), then extracts document.documentElement.outerHTML
     * directly from the rendered DOM.
     *
     * This avoids doomed same-origin CORS fetch() attempts against redirect URLs (which always fail
     * with TypeError in Chromium) and avoids duplicate network hits.
     */
    private fun navigateAndExtractPage(url: String): BridgePayload? {
        lastMainFrameStatusCode = 200
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val host = httpUrl.host
        paceHost(host)

        val finalUrl = navigateToUrl(url, expectRedirect = true) ?: return null
        if (isRedirectUrl(finalUrl)) {
            logcat(priority = LogPriority.WARN, tag = TAG) {
                "redirect navigation did not leave redirect URL: $finalUrl from: $url"
            }
            return null
        }
        val originRoot = "${httpUrl.scheme}://${httpUrl.host}/"
        if (finalUrl == originRoot && url != originRoot) {
            logcat(priority = LogPriority.WARN, tag = TAG) {
                "redirect navigation landed back on origin root: $finalUrl from: $url"
            }
            return null
        }

        return extractDomFromWebView(finalUrl)
    }

    /**
     * Extracts outer HTML from the currently rendered WebView DOM using [EXTRACT_PAGE_SCRIPT].
     */
    private fun extractDomFromWebView(landedUrl: String): BridgePayload? {
        val token = UUID.randomUUID().toString()
        val call = BridgeCall()
        pendingCalls[token] = call
        try {
            val script = EXTRACT_PAGE_SCRIPT.replace("__TOKEN__", token)
            handler.post { webView?.evaluateJavascript(script, null) }
            if (!call.finished.await(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                logcat(priority = LogPriority.WARN, tag = TAG) { "dom extraction timed out url=$landedUrl" }
                return null
            }
            call.error?.let { error ->
                logcat(priority = LogPriority.WARN, tag = TAG) { "dom extraction failed url=$landedUrl error=$error" }
                return null
            }
            val payload = runCatching {
                json.decodeFromString(BridgePayload.serializer(), call.payload.toString())
            }.getOrNull()
            if (payload == null) {
                logcat(priority = LogPriority.WARN, tag = TAG) {
                    "dom extraction returned unparsable payload url=$landedUrl"
                }
                return null
            }
            val body = payload.body
            if (body == null || body.length < 200 || CloudflareChallengeDetector.isBlockingChallengePage(body)) {
                logcat(priority = LogPriority.WARN, tag = TAG) {
                    "extracted dom contains challenge markers or too short (${body?.length} chars), discarding url=$landedUrl"
                }
                return null
            }
            val statusCode = when {
                lastMainFrameStatusCode in 400..599 -> lastMainFrameStatusCode
                HttpErrorPageDetector.isErrorPage(body) -> 500
                else -> payload.status
            }
            val finalPayload = if (statusCode != payload.status) {
                payload.copy(status = statusCode)
            } else {
                payload
            }
            logcat(priority = LogPriority.DEBUG, tag = TAG) {
                "extracted dom via webview status=${finalPayload.status} bytes=${finalPayload.body?.length ?: 0} url=${finalPayload.url}"
            }
            return finalPayload
        } finally {
            pendingCalls.remove(token)
        }
    }

    /**
     * Loads [url] in the bridge WebView and returns the landed page URL after redirects
     * (extnu -> translator) and any automatic Cloudflare check complete. Null on timeout.
     */
    private fun navigateToUrl(url: String, expectRedirect: Boolean = false): String? {
        val latch = CountDownLatch(1)
        navigationLatch = latch
        activePageFinished = false
        val startTime = System.currentTimeMillis()
        var checkRunnable: Runnable? = null

        handler.post {
            runCatching {
                val view = ensureWebView()
                attachWebViewToWindow(view)
                loadedPageUrl = null
                activePageFinished = false

                fun checkComplete() {
                    if (latch.count == 0L) return
                    val currentUrl = view.url.orEmpty()
                    if (currentUrl.isBlank() || currentUrl == "about:blank") return

                    if (expectRedirect && isRedirectUrl(currentUrl)) {
                        // Still sitting on redirect endpoint (/extnu/); wait for redirect.
                        return
                    }

                    // Main frame must finish loading before extracting DOM, or allow fallback after 8s
                    val elapsed = System.currentTimeMillis() - startTime
                    if (!activePageFinished && elapsed < 8_000L) {
                        return
                    }

                    // For redirected translator chapter pages, give client-side SPAs (React/Vue) a small
                    // grace window after page load so chapter text paragraphs have time to mount in DOM,
                    // unless paragraphs (pCount >= 3) are already present.
                    val timeSinceFinished = if (activePageFinished) {
                        System.currentTimeMillis() - pageFinishedAtMs
                    } else {
                        0L
                    }
                    val requiresSpaGrace = expectRedirect && timeSinceFinished < 1_500L
                    val probeJs = if (requiresSpaGrace) {
                        """
                        (function() {
                            try {
                                var res = (${CloudflareChallengeDetector.pageReadyProbeJs});
                                if (res !== 'ready') return res;
                                var pCount = document.querySelectorAll('p').length;
                                var b = document.body;
                                var textLen = (b ? (b.innerText || b.textContent || '') : '').trim().length;
                                if (pCount < 3 && textLen < 300) return 'waiting';
                                return 'ready';
                            } catch (_) {
                                return 'waiting';
                            }
                        })();
                        """.trimIndent()
                    } else {
                        CloudflareChallengeDetector.pageReadyProbeJs
                    }

                    view.evaluateJavascript(probeJs) { result ->
                        if (latch.count == 0L) return@evaluateJavascript
                        val status = result?.trim('"', ' ', '\n', '\r')
                        if (status == "ready") {
                            val finalUrl = view.url.orEmpty()
                            if (finalUrl.isNotBlank() && finalUrl != "about:blank") {
                                if (!expectRedirect || !isRedirectUrl(finalUrl)) {
                                    loadedPageUrl = finalUrl
                                    loadedOrigin = finalUrl.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
                                    runCatching {
                                        val cookie = CookieManager.getInstance().getCookie(finalUrl)
                                        if (cookie?.contains("cf_clearance") == true) {
                                            CookieManager.getInstance().flush()
                                        }
                                    }
                                    latch.countDown()
                                }
                            }
                        }
                    }
                }

                activeCheckComplete = ::checkComplete

                val poller = object : Runnable {
                    override fun run() {
                        if (latch.count == 0L) return
                        checkComplete()
                        if (latch.count > 0L) {
                            handler.postDelayed(this, 350L)
                        }
                    }
                }
                checkRunnable = poller
                handler.postDelayed(poller, 350L)

                view.loadUrl(url)
            }.onFailure { e ->
                logcat(priority = LogPriority.WARN, tag = TAG) { "navigateToUrl failed: ${e.message}" }
                latch.countDown()
            }
        }

        val completed = latch.await(NAVIGATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        handler.post {
            checkRunnable?.let { handler.removeCallbacks(it) }
            activeCheckComplete = null
        }

        val landed = loadedPageUrl
        logcat(priority = LogPriority.DEBUG, tag = TAG) {
            "navigation completed=$completed landed=$landed from=$url expectRedirect=$expectRedirect"
        }
        return landed
    }

    /** Creates the hidden WebView when needed and parks it on [targetOrigin]. Main-thread safe. */
    private fun prepareOnMain(targetOrigin: String): Boolean {
        if (loadedOrigin == targetOrigin) return true
        val landed = navigateToUrl("$targetOrigin/", expectRedirect = false)
        return loadedOrigin == targetOrigin || landed?.startsWith("$targetOrigin") == true
    }

    private fun ensureWebView(): WebView {
        webView?.let { view ->
            attachWebViewToWindow(view)
            return view
        }
        val context = Injekt.get<Application>()
        val view = object : WebView(context) {
            // The bridge overlay must never swallow touches meant for the activity below it.
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean = false
        }
        view.setDefaultSettings()
        // Allow network images - Cloudflare Turnstile / managed challenges require asset and canvas loading to succeed.
        view.settings.userAgentString = Injekt.get<NetworkHelper>().pluginUserAgentProvider()
        view.addJavascriptInterface(BridgeInterface, "TadamiBridge")
        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                activePageFinished = false
                pageFinishedAtMs = 0L
                lastMainFrameStatusCode = 200
            }

            override fun onPageFinished(view: WebView, url: String) {
                activePageFinished = true
                pageFinishedAtMs = System.currentTimeMillis()
                loadedPageUrl = url
                loadedOrigin = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
                runCatching {
                    val cookie = CookieManager.getInstance().getCookie(url)
                    if (cookie?.contains("cf_clearance") == true) {
                        CookieManager.getInstance().flush()
                    }
                }
                activeCheckComplete?.invoke()
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame) {
                    lastMainFrameStatusCode = errorResponse.statusCode
                    logcat(priority = LogPriority.WARN, tag = TAG) {
                        "HTTP error on main frame status=${errorResponse.statusCode} url=${request.url}"
                    }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    activePageFinished = true
                    lastMainFrameStatusCode = 502
                    navigationLatch?.countDown()
                }
            }
        }
        attachWebViewToWindow(view)
        webView = view
        return view
    }

    private object BridgeInterface {
        @JavascriptInterface
        fun chunk(token: String, data: String) {
            pendingCalls[token]?.payload?.append(data)
        }

        @JavascriptInterface
        fun done(token: String) {
            pendingCalls[token]?.finished?.countDown()
        }

        @JavascriptInterface
        fun error(token: String, message: String) {
            pendingCalls[token]?.let { call ->
                call.error = message
                call.finished.countDown()
            }
        }
    }

    internal fun buildBridgeConfigJson(
        token: String,
        url: String,
        options: NovelJsRuntimeFactory.JsFetchRequest,
    ): String = buildJsonObject {
        put("token", token)
        put("url", url)
        put("method", options.method.uppercase(Locale.US))
        put("chunk", CHUNK_SIZE)
        val referrer = options.headers.entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value
            ?: options.referrer
            ?: lastPageUrl?.takeIf { page ->
                val origin = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
                origin != null && page.startsWith("$origin/")
            }
        if (!referrer.isNullOrBlank()) {
            put("referrer", referrer)
        }
        putJsonObject("headers") {
            filterBridgeHeaders(options.headers).forEach { (name, value) -> put(name, value) }
        }
        put(
            "bodyKind",
            when (options.bodyType) {
                NovelJsRuntimeFactory.BodyType.Text -> "text"
                NovelJsRuntimeFactory.BodyType.Form -> "form"
                NovelJsRuntimeFactory.BodyType.None -> "none"
            },
        )
        options.body?.let { put("body", it) }
        options.formEntries?.let { entries ->
            putJsonArray("form") {
                entries.forEach { entry ->
                    add(
                        buildJsonArray {
                            add(JsonPrimitive(entry.key))
                            add(JsonPrimitive(entry.value))
                        },
                    )
                }
            }
        }
    }.toString()

    /** Browser fetch() forbids setting transport headers; sending them would throw a TypeError. */
    internal fun filterBridgeHeaders(headers: Map<String, String>): Map<String, String> {
        return headers.filterKeys { name ->
            val lower = name.lowercase(Locale.US)
            lower !in FORBIDDEN_BRIDGE_HEADERS && !lower.startsWith("proxy-") && !lower.startsWith("sec-")
        }
    }

    private val FORBIDDEN_BRIDGE_HEADERS = setOf(
        "accept-charset",
        "accept-encoding",
        "access-control-request-headers",
        "access-control-request-method",
        "connection",
        "content-length",
        "cookie",
        "cookie2",
        "date",
        "dnt",
        "expect",
        "host",
        "keep-alive",
        "origin",
        "proxy-",
        "referer",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
        "user-agent",
        "via",
    )

    private val FETCH_SCRIPT = """
        (function(cfg){
          var B = window.TadamiBridge;
          if (!B) { return; }
          var opts = { method: cfg.method, headers: cfg.headers, credentials: 'include', redirect: 'follow' };
          if (cfg.referrer) { opts.referrer = cfg.referrer; }
          if (cfg.bodyKind === 'text' && cfg.body != null) { opts.body = cfg.body; }
          if (cfg.bodyKind === 'form' && cfg.form) {
            // Encode by hand and pin the Content-Type: a browser-native URLSearchParams would do
            // the same, but an explicit body leaves no room for engine-specific surprises and
            // WordPress only fills its POST map for urlencoded bodies.
            opts.body = cfg.form.map(function(pair){ return encodeURIComponent(pair[0]) + "=" + encodeURIComponent(pair[1]); }).join("&");
            opts.headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
          }
          fetch(cfg.url, opts).then(function(response){
            return response.text().then(function(text){
              var headers = {};
              response.headers.forEach(function(value, key){ headers[key] = value; });
              var payload = JSON.stringify({ status: response.status, url: response.url, headers: headers, body: text });
              for (var i = 0; i < payload.length; i += cfg.chunk) {
                B.chunk(cfg.token, payload.substr(i, cfg.chunk));
              }
              B.done(cfg.token);
            });
          }).catch(function(error){ B.error(cfg.token, String(error)); });
        })(__CFG__);
    """.trimIndent()

    private val EXTRACT_PAGE_SCRIPT = """
        (function(token){
          var B = window.TadamiBridge;
          if (!B) { return; }
          try {
            var html = document.documentElement ? document.documentElement.outerHTML : '';
            var payload = JSON.stringify({
              status: 200,
              url: window.location.href,
              headers: {},
              body: html
            });
            var chunk = 262144;
            for (var i = 0; i < payload.length; i += chunk) {
              B.chunk(token, payload.substr(i, chunk));
            }
            B.done(token);
          } catch(err) {
            B.error(token, String(err));
          }
        })('__TOKEN__');
    """.trimIndent()
}
