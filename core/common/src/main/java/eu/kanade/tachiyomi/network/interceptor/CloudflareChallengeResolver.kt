package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.i18n.MR
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

interface CloudflareChallengeResolver {
    fun resolve(originalRequest: Request, oldCookie: Cookie?)
}

internal class WebViewCloudflareChallengeResolver(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    private val mainExecutor: Executor,
    private val createWebView: (Request) -> WebView,
    private val parseHeaders: (Headers) -> Map<String, String>,
    private val isWebViewOutdated: (WebView) -> Boolean,
    private val nonCloudflareClientProvider: () -> OkHttpClient? = { null },
) : CloudflareChallengeResolver {

    @SuppressLint("SetJavaScriptEnabled")
    override fun resolve(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)

        var webview: WebView? = null
        var challengeFound = false
        var cloudflareBypassed = false
        var hasInteractiveWidget = false
        var isWebViewOutdatedNow = false

        val origRequestUrl = originalRequest.url.toString()
        val targetHost = originalRequest.url.host
        val headers = parseHeaders(originalRequest.headers)

        mainExecutor.execute {
            val createdWebView = createWebView(originalRequest)
            webview = createdWebView

            createdWebView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val client = nonCloudflareClientProvider() ?: return null
                    return interceptMainFrameForCspStripping(client, request, targetHost)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    fun isCloudFlareBypassed(): Boolean {
                        return cookieManager.get(originalRequest.url)
                            .firstOrNull { it.name == "cf_clearance" }
                            .let { it != null && it != oldCookie }
                    }

                    if (isCloudFlareBypassed()) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == origRequestUrl && !challengeFound) {
                        latch.countDown()
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        if (error.errorCode in ERROR_CODES) {
                            challengeFound = true
                        } else {
                            latch.countDown()
                        }
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame) {
                        if (errorResponse.statusCode in ERROR_CODES) {
                            challengeFound = true
                        } else {
                            latch.countDown()
                        }
                    }
                }
            }

            createdWebView.loadUrl(origRequestUrl, headers)
        }

        latch.awaitFor30Seconds()

        if (!cloudflareBypassed) {
            hasInteractiveWidget = detectInteractiveWidgetSync(webview)
        }

        mainExecutor.execute {
            if (!cloudflareBypassed) {
                isWebViewOutdatedNow = webview?.let(isWebViewOutdated) == true
            }

            webview?.run {
                stopLoading()
                destroy()
            }
        }

        if (!cloudflareBypassed) {
            if (isWebViewOutdatedNow) {
                context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            } else if (hasInteractiveWidget) {
                context.toast(MR.strings.information_cloudflare_interactive_challenge, Toast.LENGTH_LONG)
                throw CloudflareInteractiveChallengeException()
            }

            throw CloudflareBypassException()
        }
    }

    private fun detectInteractiveWidgetSync(webview: WebView?): Boolean {
        if (webview == null) return false
        val checkLatch = CountDownLatch(1)
        var detected = false
        mainExecutor.execute {
            try {
                webview.evaluateJavascript(INTERACTIVE_WIDGET_PROBE) { result ->
                    detected = result == "true"
                    checkLatch.countDown()
                }
            } catch (_: Throwable) {
                checkLatch.countDown()
            }
        }
        checkLatch.await(2, TimeUnit.SECONDS)
        return detected
    }
}

/**
 * Strip `Content-Security-Policy` (and `*-Report-Only`) from main-frame responses on the
 * Cloudflare-protected host so that the challenge JS isn't blocked by Trusted Types or
 * nonce-only inline-script policies set by the page.
 *
 * Returns null to let WebView handle the request normally for any non-matching case.
 */
private fun interceptMainFrameForCspStripping(
    client: OkHttpClient,
    request: WebResourceRequest,
    targetHost: String,
): WebResourceResponse? {
    if (!request.isForMainFrame) return null
    if (!request.method.equals("GET", ignoreCase = true)) return null
    val requestHost = request.url.host ?: return null
    if (!requestHost.equals(targetHost, ignoreCase = true)) return null

    return try {
        val okHttpRequest = Request.Builder()
            .url(request.url.toString())
            .apply {
                request.requestHeaders?.forEach { (name, value) ->
                    if (value != null) addHeader(name, value)
                }
            }
            .get()
            .build()

        client.newCall(okHttpRequest).execute().use { response ->
            val mediaType = response.body.contentType()
            val mimeType = mediaType?.let { "${it.type}/${it.subtype}" } ?: "text/html"
            val charset = mediaType?.charset()?.name() ?: "UTF-8"
            val bodyBytes = response.body.bytes()
            val sanitizedHeaders = response.headers.toMultimap()
                .filterKeys { name -> name.lowercase(Locale.ROOT) !in CSP_HEADER_NAMES }
                .mapValues { it.value.joinToString(", ") }
            val reason = response.message.ifBlank { "OK" }
            WebResourceResponse(
                mimeType,
                charset,
                response.code,
                reason,
                sanitizedHeaders,
                ByteArrayInputStream(bodyBytes),
            )
        }
    } catch (_: IOException) {
        null
    } catch (_: Throwable) {
        null
    }
}

private val CSP_HEADER_NAMES = setOf(
    "content-security-policy",
    "content-security-policy-report-only",
)

private val INTERACTIVE_WIDGET_PROBE = """
    (function() {
        try {
            var hasTurnstile = document.querySelector('.cf-turnstile, [data-sitekey], iframe[src*="challenges.cloudflare.com"]') != null;
            var hasManaged = document.getElementById('challenge-stage') != null ||
                document.getElementById('cf-please-wait') != null;
            return hasTurnstile || hasManaged;
        } catch (_) {
            return false;
        }
    })();
""".trimIndent()

private fun CountDownLatch.awaitFor30Seconds() {
    await(30, TimeUnit.SECONDS)
}

internal open class CloudflareBypassException : Exception()
internal class CloudflareInteractiveChallengeException : CloudflareBypassException()
