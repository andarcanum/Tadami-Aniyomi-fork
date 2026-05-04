package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
    nonCloudflareClientProvider: () -> OkHttpClient? = { null },
    private val challengeResolver: CloudflareChallengeResolver? = null,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val challengeLockByHost = ConcurrentHashMap<String, Any>()
    private val webViewChallengeResolver = challengeResolver ?: WebViewCloudflareChallengeResolver(
        context = context,
        cookieManager = cookieManager,
        mainExecutor = ContextCompat.getMainExecutor(context),
        createWebView = this::createWebView,
        parseHeaders = this::parseHeaders,
        isWebViewOutdated = { it.isOutdated() },
        nonCloudflareClientProvider = nonCloudflareClientProvider,
    )

    override fun shouldIntercept(response: Response): Boolean {
        if (response.code !in ERROR_CODES || response.header("Server") !in SERVER_CHECK) {
            return false
        }
        if (response.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true) {
            return true
        }
        // Limit body inspection to a small prefix; challenge markup is at the top of the
        // document and full body parsing wastes memory on large pages.
        val bodyPeek = response.peekBody(CHALLENGE_PEEK_BYTES).string()
        if (!CHALLENGE_HTML_MARKERS.any { it in bodyPeek }) {
            return false
        }
        return true
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        val host = request.url.host
        try {
            response.close()
            val hostLock = challengeLockByHost.getOrPut(host) { Any() }
            try {
                synchronized(hostLock) {
                    // Try the request as-is first — the user may have already solved
                    // Cloudflare manually via WebView and cookies are waiting in CookieManager.
                    val immediateRetry = chain.proceed(request)
                    if (!shouldIntercept(immediateRetry)) {
                        return immediateRetry
                    }
                    immediateRetry.close()

                    // Still blocked — clear stale clearance and run the bypass.
                    cookieManager.remove(request.url, COOKIE_NAMES, 0)
                    val oldCookie = cookieManager.get(request.url)
                        .firstOrNull { it.name == "cf_clearance" }

                    webViewChallengeResolver.resolve(request, oldCookie)

                    val firstAttempt = chain.proceed(request)
                    if (!shouldIntercept(firstAttempt)) {
                        return firstAttempt
                    }
                    // The cookie set on CookieManager may not have propagated to OkHttp's
                    // CookieJar yet for the in-flight connection; close and retry once.
                    firstAttempt.close()
                    return chain.proceed(request)
                }
            } finally {
                challengeLockByHost.remove(host, hostLock)
            }
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareInteractiveChallengeException) {
            throw IOException(
                context.stringResource(MR.strings.information_cloudflare_interactive_challenge),
                e,
            )
        } catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}

internal val ERROR_CODES = listOf(403, 503)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

// Just enough to capture the challenge headers/error markers; the page body is larger
// but the challenge identifiers always appear near the top.
private const val CHALLENGE_PEEK_BYTES = 8L * 1024L
private val CHALLENGE_HTML_MARKERS = arrayOf(
    "challenge-error-title",
    "challenge-error-text",
    "cf-mitigated",
    "cf-error-details",
)
