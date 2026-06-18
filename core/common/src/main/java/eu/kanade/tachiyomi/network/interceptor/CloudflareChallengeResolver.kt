package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.i18n.MR
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
        val headers = parseHeaders(originalRequest.headers)

        mainExecutor.execute {
            val createdWebView = createWebView(originalRequest)
            webview = createdWebView

            createdWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (hasNewCloudflareClearance(originalRequest, url, oldCookie)) {
                        cloudflareBypassed = true
                        CookieManager.getInstance().flush()
                        latch.countDown()
                        return
                    }

                    if (challengeFound) {
                        detectInteractiveWidget(view) { detected ->
                            if (detected && !cloudflareBypassed) {
                                hasInteractiveWidget = true
                                latch.countDown()
                            }
                        }
                    } else if (url == origRequestUrl || url.toHttpUrlOrNull()?.host != originalRequest.url.host) {
                        latch.countDown()
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        latch.countDown()
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

    private fun hasNewCloudflareClearance(originalRequest: Request, currentUrl: String, oldCookie: Cookie?): Boolean {
        return listOfNotNull(originalRequest.url, currentUrl.toHttpUrlOrNull())
            .distinctBy { it.host }
            .any { url ->
                val cookie = cookieManager.get(url).firstOrNull { it.name == "cf_clearance" }
                cookie != null && (url.host != originalRequest.url.host || cookie != oldCookie)
            }
    }

    private fun detectInteractiveWidget(webview: WebView, onResult: (Boolean) -> Unit) {
        try {
            webview.evaluateJavascript(INTERACTIVE_WIDGET_PROBE) { result ->
                onResult(result == "true")
            }
        } catch (_: Throwable) {
            onResult(false)
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
