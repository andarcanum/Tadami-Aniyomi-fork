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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onSession: (cookies: Map<String, String>, localStorage: Map<String, String>) -> Unit,
    onDoneSession: (cookies: Map<String, String>, localStorage: Map<String, String>) -> Unit,
    onOwnRedirect: (url: String, cookies: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Async-dump plumbing: a dump stores the target consumer + the cookie map; the JS answer
    // arrives either synchronously (evaluateJavascript result) or later through the
    // window.prompt bridge (the window.kinde.getToken path / the timeout). The generation
    // counter makes exactly one of the two deliveries win.
    var dumpMember by remember { mutableStateOf<((Map<String, String>, Map<String, String>) -> Unit)?>(null) }
    var dumpCookiesHolder by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var dumpGen by remember { mutableStateOf(0) }

    fun deliverDump(gen: Int, storage: Map<String, String>) {
        if (dumpGen != gen || gen == 0) return
        val consumer = dumpMember ?: return
        dumpMember = null
        dumpGen = 0
        consumer(dumpCookiesHolder, storage)
    }

    fun requestDump(view: WebView, consumer: (Map<String, String>, Map<String, String>) -> Unit) {
        val cookies = cookieDump()
        dumpCookiesHolder = cookies
        dumpMember = consumer
        val gen = dumpGen + 1
        dumpGen = gen
        view.evaluateJavascript(LOCAL_STORAGE_DUMP_JS) { raw ->
            val parsed = parseJsonStringMap(raw)
            if (parsed.isNotEmpty()) deliverDump(gen, parsed)
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
                popup.settings.databaseEnabled = true
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
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?,
            ): Boolean {
                if (message?.startsWith(DEEP_DUMP_MARKER) == true) {
                    deliverDump(dumpGen, parseJsonStringMap(message.removePrefix(DEEP_DUMP_MARKER)))
                    result?.confirm("")
                    return true
                }
                return super.onJsPrompt(view, url, message, defaultValue, result)
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
                                settings.databaseEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.setSupportMultipleWindows(true)
                                // The site gates its SPA on bot/WebView user agents (a default
                                // WebView UA carries "; wv"): present a plain Chrome mobile UA.
                                settings.userAgentString = WEBVIEW_USER_AGENT
                                // chrome://inspect fallback when a future site change blanks again.
                                WebView.setWebContentsDebuggingEnabled(true)
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                webChromeClient = chromeClient
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?,
                                    ) {
                                        // Instrument the SPA's own fetch/XHR so the dump can lift
                                        // its live api bearer + handshake session id.
                                        if (url != null && Uri.parse(url).host == Uri.parse(startUrl).host) {
                                            view.evaluateJavascript(INSTRUMENT_JS, null)
                                        }
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        val url = request.url.toString()
                                        // Only OUR in-flight PKCE redirects are intercepted and
                                        // handed to the source for the code exchange; the SPA's
                                        // own callback redirects must load normally.
                                        if (Uri.parse(url).getQueryParameter("code") != null && isOwnRedirect(url)) {
                                            onOwnRedirect(url, cookieDump())
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        // Navigation trail without page content: host + first path
                                        // segment only (deep slugs can carry sensitive words).
                                        val safe = url?.let { u ->
                                            val uri = Uri.parse(u)
                                            uri.host + "/" + (uri.pathSegments.firstOrNull() ?: "")
                                        }
                                        Log.d(TAG, "page finished: $safe")
                                        // Re-inject the credential instrumentation (idempotent)
                                        // and re-dump with a delay: the SPA performs its first
                                        // api calls (with its live bearer) only after boot,
                                        // i.e. after this page-finished event.
                                        view.evaluateJavascript(INSTRUMENT_JS, null)
                                        if (url != null && isBackOnServiceSite(url, startUrl)) {
                                            requestDump(view, onSession)
                                            view.postDelayed({ requestDump(view, onSession) }, 4000)
                                            view.postDelayed({ requestDump(view, onSession) }, 9000)
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
                )

                // Stage 2 (contract v20): the Done press found no liftable session (the SPA
                // keeps its tokens in memory) — load the source's OWN PKCE authorize URL in
                // the same WebView; the auth2 cookie from the SPA login makes it auto-complete
                // and its redirect is intercepted via [isOwnRedirect]+[onOwnRedirect].
                LaunchedEffect(stage2Attempt) {
                    if (stage2Attempt > 0) {
                        webView?.loadUrl(freshStartUrl() ?: startUrl)
                    }
                }
            }
        }
    }
}

/** True when [url] is on the service domain (from [startUrl]) outside its /auth routes. */
private fun isBackOnServiceSite(url: String, startUrl: String): Boolean {
    val serviceHost = Uri.parse(startUrl).host ?: return false
    val uri = Uri.parse(url)
    return uri.host == serviceHost && !uri.path.orEmpty().startsWith("/auth")
}

/** Synchronous CookieManager dump of the service domains (session cookies included). */
private fun cookieDump(): Map<String, String> = buildMap {
    val cookieManager = CookieManager.getInstance()
    listOf("https://www.redgifs.com", "https://auth2.redgifs.com", "https://api.redgifs.com").forEach { domain ->
        cookieManager.getCookie(domain)?.split(";")?.forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) put(pair.substring(0, idx).trim(), pair.substring(idx + 1).trim())
        }
    }
}

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

// localStorage + sessionStorage merged into one dump (sessionStorage keys get an "ss."
// prefix); when the page exposes the site's own kinde SDK client, its ACCESS token (the one
// the site's api client itself uses) is appended as a bare JWT under a reserved key — the
// answer is delivered through the window.prompt bridge (with a 4s timeout fallback).
private const val DEEP_DUMP_MARKER = "__DUMP2__:"

// Records the SPA's own api credentials into window globals for the dump: the live
// Authorization bearer of api.redgifs.com calls and the /v2/auth/login handshake body
// (which carries the session_id the api binds the account session to).
private const val INSTRUMENT_JS =
    "(function(){" +
        "  if(window.__rgInstr)return;window.__rgInstr=1;" +
        "  function hdrAuth(o){" +
        "    try{" +
        "      if(!o||!o.headers)return null;" +
        "      if(typeof Headers!=='undefined'&&o.headers instanceof Headers)return o.headers.get('authorization');" +
        "      if(Array.isArray(o.headers)){for(var i=0;i<o.headers.length;i++){" +
        "if(String(o.headers[i][0]).toLowerCase()==='authorization')return o.headers[i][1];}" +
        "return null;}" +
        "      if(typeof o.headers==='object'){var ks=Object.keys(o.headers);" +
        "for(var j=0;j<ks.length;j++){if(ks[j].toLowerCase()==='authorization')return o.headers[ks[j]];}}" +
        "    }catch(e){}" +
        "    return null;" +
        "  }" +
        "  function note(url,auth,body){" +
        "    try{" +
        "      if(auth&&String(url).indexOf('api.redgifs.com')>=0)window.__rgBearer=String(auth);" +
        "      if(body&&String(url).indexOf('/v2/auth/login')>=0)window.__rgHandshake=String(body);" +
        "    }catch(e){}" +
        "  }" +
        "  var of=window.fetch;" +
        "  window.fetch=function(u,o){" +
        "    try{var url=(typeof u==='string')?u:(u&&u.url);note(url,hdrAuth(o),o&&o.body);}catch(e){}" +
        "    return of.apply(this,arguments);" +
        "  };" +
        "  var oo=XMLHttpRequest.prototype.open,os=XMLHttpRequest.prototype.send," +
        "oh=XMLHttpRequest.prototype.setRequestHeader;" +
        "  XMLHttpRequest.prototype.open=function(m,u){this.__rgUrl=u;this.__rgAuth=null;" +
        "return oo.apply(this,arguments);};" +
        "  XMLHttpRequest.prototype.setRequestHeader=function(k,v){" +
        "try{if(String(k).toLowerCase()==='authorization')this.__rgAuth=v;}catch(e){}" +
        "return oh.apply(this,arguments);};" +
        "  XMLHttpRequest.prototype.send=function(b){" +
        "try{note(this.__rgUrl,this.__rgAuth,b);}catch(e){}return os.apply(this,arguments);};" +
        "})()"

private const val LOCAL_STORAGE_DUMP_JS =
    "(function(){" +
        "  var base={};" +
        "  try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);" +
        "base[k]=localStorage.getItem(k);}}catch(e){}" +
        "  try{for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);" +
        "base['ss.'+k]=sessionStorage.getItem(k);}}catch(e){}" +
        "  var finished=false;" +
        "  try{if(window.__rgHandshake){base['__handshake__']=window.__rgHandshake;}}catch(e){}" +
        "  try{if(window.__rgBearer){base['__rg_bearer__']=window.__rgBearer;}}catch(e){}" +
        "  function finish(obj){if(finished)return;finished=true;" +
        "window.prompt('" + DEEP_DUMP_MARKER + "'+JSON.stringify(obj));}" +
        "  if(window.kinde&&typeof window.kinde.getToken==='function'){" +
        "    setTimeout(function(){finish(base);},4000);" +
        "    try{window.kinde.getToken().then(function(t){" +
        "if(t){base['__kinde_token__']=String(t);}finish(base);},function(){finish(base);});}" +
        "    catch(e){finish(base);}" +
        "    return;" +
        "  }" +
        "  return JSON.stringify(base);" +
        "})()"

private const val TAG = "ReelsWebLogin"

// Plain Chrome mobile UA: the default WebView UA ("...; wv") is gated by the site's SPA.
private const val WEBVIEW_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"
