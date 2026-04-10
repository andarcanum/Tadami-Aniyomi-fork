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
import okhttp3.Request
import tachiyomi.i18n.MR
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

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
) : CloudflareChallengeResolver {

    @SuppressLint("SetJavaScriptEnabled")
    override fun resolve(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)

        var webview: WebView? = null
        var cloudflareBypassed = false
        var isWebViewOutdatedNow = false

        val challengeUrl = cloudflareChallengeUrlFor(originalRequest)
        val headers = parseHeaders(originalRequest.headers)

        mainExecutor.execute {
            val createdWebView = createWebView(originalRequest)
            webview = createdWebView

            createdWebView.webViewClient = object : WebViewClient() {
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
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        if (error.errorCode in ERROR_CODES) {
                            Unit
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
                            Unit
                        } else {
                            latch.countDown()
                        }
                    }
                }
            }

            createdWebView.loadUrl(challengeUrl, headers)
        }

        latch.awaitFor30Seconds()

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
            }

            throw CloudflareBypassException()
        }
    }
}

private fun CountDownLatch.awaitFor30Seconds() {
    await(30, java.util.concurrent.TimeUnit.SECONDS)
}

internal class CloudflareBypassException : Exception()
