package eu.kanade.tachiyomi.ui.reels.components

import android.net.Uri
import android.os.Message
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tadami.aurora.BuildConfig
import eu.kanade.tachiyomi.animesource.model.WebLoginCookie
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Hosted web login (contract v20, AnimeFeedWebLoginSource): a fullscreen WebView running the
 * service's own sign-in page (captcha, email + one-time code, magic link, ...). Session
 * recovery has two stages: the DOM-storage dump (localStorage + sessionStorage + the page's
 * `window.kinde.getToken()` via a prompt bridge) is offered to the source automatically on
 * page-finished events and via the "Done" action; when that finds nothing liftable, the
 * dialog loads the source's OWN PKCE authorize URL (stage 2) in the same WebView and hands
 * own state-matched redirects to the source for the code exchange.
 */
@Composable
fun ReelsWebLoginDialog(
    startUrl: String,
    freshStartUrl: () -> String?,
    showHint: Boolean,
    isOwnRedirect: (String) -> Boolean,
    stage2Attempt: Int,
    // Contract v22: the source's own session instrumentation and extra dump origins.
    // null/empty = the generic cookie/localStorage dump only.
    instrumentationJs: String? = null,
    extraCookieOrigins: List<String> = emptyList(),
    // Contract v24: origin-scoped records instead of the lossy name→value merge, so the
    // source can resolve same-named cookies from different origins/ports.
    onSession: (cookies: List<WebLoginCookie>, localStorage: Map<String, String>) -> Unit,
    onDoneSession: (cookies: List<WebLoginCookie>, localStorage: Map<String, String>) -> Unit,
    onOwnRedirect: (url: String, cookies: List<WebLoginCookie>) -> Unit,
    onDismiss: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Cookie origins to dump: seeded from the start URL and grown with every page the
    // WebView actually visits (login flows redirect through auth hosts whose host-only
    // cookies are otherwise unreachable).
    val cookieOrigins = remember(startUrl, extraCookieOrigins) { seedOrigins(startUrl, extraCookieOrigins) }

    // Async-dump plumbing: one immutable pending request (its consumer + its own cookie
    // snapshot + its monotonic id). The JS answer arrives either synchronously
    // (evaluateJavascript result) or later through the window.prompt bridge. The id is
    // embedded in the JS payload, so a late answer can only satisfy the request that
    // produced it — ids are never reused and are not reset on stage 2.
    var pendingDump by remember { mutableStateOf<PendingDump?>(null) }
    var dumpSeq by remember { mutableStateOf(0L) }
    var isStage2Active by remember { mutableStateOf(false) }
    var redirectHandled by remember { mutableStateOf(false) }

    // Stage 2 (contract v20): load the source's own PKCE authorize URL in the same WebView;
    // with the auth2 cookie from the SPA flow it auto-completes and its state-matched
    // redirect is intercepted by [isOwnRedirect]/[onOwnRedirect] below for the code exchange.
    LaunchedEffect(stage2Attempt) {
        if (stage2Attempt > 0) {
            isStage2Active = true
            redirectHandled = false
            // An answer from the SPA phase must not land during the code exchange.
            pendingDump = null
            webView?.loadUrl(freshStartUrl() ?: startUrl)
        } else {
            isStage2Active = false
            redirectHandled = false
        }
    }

    fun deliverDump(requestId: Long, storage: Map<String, String>, fromUrl: String?) {
        if (isStage2Active) return
        val pending = pendingDump ?: return
        if (pending.id != requestId) return
        // The answer must come from a page whose cookies we are allowed to read. A navigation
        // clears [pendingDump] in onPageStarted, so this also rejects cross-page deliveries.
        val origin = originOf(fromUrl) ?: return
        if (origin !in cookieOrigins) return
        pendingDump = null
        pending.consumer(pending.cookies, storage)
    }

    fun requestDump(view: WebView, consumer: (List<WebLoginCookie>, Map<String, String>) -> Unit) {
        if (isStage2Active) return
        val requestId = ++dumpSeq
        pendingDump = PendingDump(
            id = requestId,
            cookies = cookieDump(cookieOrigins),
            consumer = consumer,
        )
        view.evaluateJavascript(localStorageDumpJs(requestId)) { raw ->
            val parsed = parseJsonStringMap(raw)
            if (parsed.isNotEmpty()) deliverDump(requestId, parsed, view.url)
        }
    }

    // Shared chrome client: popup window attachment + the __DUMP2__ prompt bridge.
    val chromeClient = remember {
        object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                // Popup (window.open) paths must render VISIBLY: a sibling WebView attached
                // on top of the main one. An invisible ferry looks like a black screen.
                val popupContainer = (view.parent as? FrameLayout)
                val hostScope = this
                val popup = WebView(view.context)
                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.settings.setSupportMultipleWindows(false)
                popup.webChromeClient = hostScope
                (resultMsg.obj as? WebView.WebViewTransport)?.webView = popup
                resultMsg.sendToTarget()
                popupContainer?.addView(
                    popup,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                return true
            }

            override fun onCloseWindow(window: WebView) {
                (window.parent as? FrameLayout)?.removeView(window)
                // Popup WebViews hold native threads: teardown must destroy, not just detach.
                window.destroy()
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?,
            ): Boolean {
                val dump = parseDumpPrompt(message)
                    ?: return super.onJsPrompt(view, url, message, defaultValue, result)
                deliverDump(dump.requestId, parseJsonStringMap(dump.json), view?.url ?: url)
                result?.confirm("")
                return true
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(MR.strings.reels_web_login_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp),
                    )
                    TextButton(onClick = { webView?.let { requestDump(it, onDoneSession) } }) {
                        Text(stringResource(MR.strings.reels_web_login_done))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                }
                if (showHint) {
                    Text(
                        text = stringResource(MR.strings.reels_web_login_not_yet),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val container = FrameLayout(context)
                        container.addView(
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.setSupportMultipleWindows(true)
                                // The site gates its SPA on bot/WebView user agents (a default
                                // WebView UA carries "; wv"): present a plain Chrome mobile UA.
                                settings.userAgentString = WEBVIEW_USER_AGENT
                                // chrome://inspect fallback when a future site change blanks again.
                                // Remote debugging must never ship in production builds.
                                if (BuildConfig.DEBUG) {
                                    WebView.setWebContentsDebuggingEnabled(true)
                                }
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                webChromeClient = chromeClient
                                webViewClient = object : WebViewClient() {
                                    private fun handleRedirect(view: WebView, url: String?): Boolean {
                                        if (redirectHandled || url == null) return false
                                        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
                                        if (uri.getQueryParameter("code") != null && isOwnRedirect(url)) {
                                            redirectHandled = true
                                            view.stopLoading()
                                            onOwnRedirect(url, cookieDump(cookieOrigins))
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(
                                        view: WebView,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?,
                                    ) {
                                        if (handleRedirect(view, url)) return
                                        // A navigation invalidates every in-flight dump: its answer
                                        // would describe a page we no longer own.
                                        pendingDump = null
                                        originOf(url)?.let { cookieOrigins += it }
                                        // Instrument the SPA's own fetch/XHR so the dump can lift its
                                        // live credentials (contract v22: the source supplies the
                                        // snippet; the legacy fallback serves pre-v22 APKs).
                                        if (url != null && Uri.parse(url).host == Uri.parse(startUrl).host) {
                                            val js = instrumentationJs?.takeIf { it.isNotBlank() }
                                            if (js != null) view.evaluateJavascript(js, null)
                                        }
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        val url = request.url.toString()
                                        if (handleRedirect(view, url)) return true
                                        val scheme = request.url.scheme
                                        if (scheme != null && scheme != "http" && scheme != "https") {
                                            runCatching {
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    request.url,
                                                ).apply {
                                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                view.context.startActivity(intent)
                                            }
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        if (handleRedirect(view, url)) return
                                        // Navigation trail without page content: host + first path
                                        // segment only (deep slugs can carry sensitive words).
                                        val safe = url?.let { u ->
                                            val uri = Uri.parse(u)
                                            uri.host + "/" + (uri.pathSegments.firstOrNull() ?: "")
                                        }
                                        // Debug-only navigation trail (audit): production builds must not log per-page events.
                                        if (BuildConfig.DEBUG) {
                                            Log.d(TAG, "page finished: $safe")
                                        }
                                        // Re-inject the credential instrumentation (idempotent)
                                        // and re-dump with a delay: the SPA performs its first
                                        // api calls only after boot, i.e. after this event.
                                        val js = instrumentationJs?.takeIf { it.isNotBlank() }
                                        if (js != null) view.evaluateJavascript(js, null)
                                        if (!isStage2Active && url != null && isBackOnServiceSite(url, startUrl)) {
                                            requestDump(view, onSession)
                                            view.postDelayed({
                                                if (!isStage2Active) requestDump(view, onSession)
                                            }, 4000)
                                            view.postDelayed({
                                                if (!isStage2Active) requestDump(view, onSession)
                                            }, 9000)
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError,
                                    ) {
                                        if (request.isForMainFrame) {
                                            Log.w(TAG, "main frame error: ${error.errorCode}")
                                        }
                                        super.onReceivedError(view, request, error)
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        errorResponse: WebResourceResponse,
                                    ) {
                                        if (request.isForMainFrame) {
                                            Log.w(TAG, "main frame http error: ${errorResponse.statusCode}")
                                        }
                                        super.onReceivedHttpError(view, request, errorResponse)
                                    }
                                }
                                loadUrl(startUrl)
                            }.also { webView = it },
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        container
                    },
                    onRelease = { container ->
                        // WebViews hold native threads: teardown must destroy, not just detach.
                        // Destroy every child (the main WebView plus any open popup).
                        (0 until container.childCount).forEach { i ->
                            (container.getChildAt(i) as? WebView)?.destroy()
                        }
                        webView = null
                    },
                )
            }
        }
    }
}

/**
 * Offscreen (1dp) WebView that silently passes the service's Cloudflare managed challenge
 * AND re-lifts a fresh SPA session (cookies + instrumented localStorage bearer) — zero user
 * interaction. Mounted while the model's bootstrap counter is >0; a successful import
 * verification reloads the feed/sheets and unmounts this view. The challenge usually
 * auto-solves in seconds; the dump fires twice (4s/9s) because the SPA performs its first
 * authenticated api calls only after boot.
 */
@Composable
fun CfBootstrapWebView(
    startUrl: String,
    attempt: Int,
    onSession: (cookies: List<WebLoginCookie>, localStorage: Map<String, String>) -> Unit,
    instrumentationJs: String? = null,
    extraCookieOrigins: List<String> = emptyList(),
) {
    var firesLeft by remember(attempt) { mutableStateOf(2) }
    val currentAttempt by rememberUpdatedState(attempt)
    var view by remember { mutableStateOf<WebView?>(null) }
    val dumpIds = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val cookieOrigins = remember(startUrl, extraCookieOrigins) { seedOrigins(startUrl, extraCookieOrigins) }
    LaunchedEffect(attempt) {
        firesLeft = 2
        view?.reload()
    }
    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = WEBVIEW_USER_AGENT
                CookieManager.getInstance().setAcceptCookie(true)
                webChromeClient = object : WebChromeClient() {
                    // The dump JS answers through window.prompt when the site exposes its own
                    // token SDK. This view is offscreen: without an owner the prompt would
                    // surface as a system dialog (or be dropped by the default client).
                    override fun onJsPrompt(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        defaultValue: String?,
                        result: JsPromptResult?,
                    ): Boolean {
                        val dump = parseDumpPrompt(message)
                            ?: return super.onJsPrompt(view, url, message, defaultValue, result)
                        val storage = parseJsonStringMap(dump.json)
                        if (storage.isNotEmpty()) onSession(cookieDump(cookieOrigins), storage)
                        result?.confirm("")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(page: WebView, url: String?) {
                        originOf(url)?.let { cookieOrigins += it }
                        val scheduledFor = currentAttempt
                        page.postDelayed({ dump(page, scheduledFor) }, 4000)
                        page.postDelayed({ dump(page, scheduledFor) }, 9000)
                    }

                    private fun dump(page: WebView, scheduledFor: Int) {
                        // A timer queued by an older attempt must not consume the current
                        // attempt's dump budget.
                        if (scheduledFor != currentAttempt || firesLeft <= 0) return
                        firesLeft -= 1
                        val dumpStorage: () -> Unit = {
                            page.evaluateJavascript(localStorageDumpJs(dumpIds.incrementAndGet())) { raw ->
                                onSession(cookieDump(cookieOrigins), parseJsonStringMap(raw))
                            }
                        }
                        // Contract v22: the source's instrumentation; the legacy fallback
                        // keeps pre-v22 APKs working until they opt in.
                        val js = instrumentationJs?.takeIf { it.isNotBlank() }
                        if (js != null) page.evaluateJavascript(js) { dumpStorage() } else dumpStorage()
                    }
                }
                loadUrl(startUrl)
            }.also { view = it }
        },
        onRelease = { webViewRef ->
            view = null
            webViewRef.destroy()
        },
    )
}

/** True when [url] is on the service domain (from [startUrl]) outside its /auth routes. */
private fun isBackOnServiceSite(url: String, startUrl: String): Boolean {
    val serviceHost = Uri.parse(startUrl).host ?: return false
    val uri = Uri.parse(url)
    return uri.host == serviceHost && !uri.path.orEmpty().startsWith("/auth")
}

/** Synchronous CookieManager dump of the given origins, one record per origin. */
private fun cookieDump(urls: Collection<String>): List<WebLoginCookie> = buildList {
    val cookieManager = CookieManager.getInstance()
    urls.forEach { origin ->
        cookieManager.getCookie(origin)?.split(";")?.forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                add(
                    WebLoginCookie(
                        origin = origin,
                        name = pair.substring(0, idx).trim(),
                        value = pair.substring(idx + 1).trim(),
                    ),
                )
            }
        }
    }
}

/** In-flight dump: its consumer, the cookie snapshot taken at request time, and its own id. */
private class PendingDump(
    val id: Long,
    val cookies: List<WebLoginCookie>,
    val consumer: (List<WebLoginCookie>, Map<String, String>) -> Unit,
)

/** evaluateJavascript result / prompt payload -> key-value map. */
private fun parseJsonStringMap(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank() || raw == "null") return emptyMap()
    return try {
        val inner = when (val value = org.json.JSONTokener(raw).nextValue()) {
            is String -> value
            else -> raw
        }
        val obj = org.json.JSONObject(inner)
        buildMap {
            for (key in obj.keys()) put(key, obj.optString(key, ""))
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

// The pre-v22 host-side RedGIFs instrumentation lived here. Contract v22 moved it into the
// plugin (AnimeFeedLoginInstrumentationSource.sessionInstrumentationJs) and every web-login
// plugin now implements the capability (redgifs supplies the snippet, xfree an empty one),
// so the host injects NOTHING by default — no source-specific JS on foreign origins.

private const val TAG = "ReelsWebLogin"

// Plain Chrome mobile UA: the default WebView UA ("...; wv") is gated by the site's SPA.
private const val WEBVIEW_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"
