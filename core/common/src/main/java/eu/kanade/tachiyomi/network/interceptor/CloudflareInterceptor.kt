package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
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
    )

    override fun shouldIntercept(response: Response): Boolean {
        if (response.code !in ERROR_CODES || response.header("Server") !in SERVER_CHECK) {
            return false
        }
        if (response.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true) {
            return true
        }
        val document = Jsoup.parse(
            response.peekBody(Long.MAX_VALUE).string(),
            response.request.url.toString(),
        )
        return document.getElementById("challenge-error-title") != null ||
            document.getElementById("challenge-error-text") != null
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        try {
            response.close()
            val hostLock = challengeLockByHost.getOrPut(request.url.host) { Any() }
            synchronized(hostLock) {
                cookieManager.remove(request.url, COOKIE_NAMES, 0)
                val oldCookie = cookieManager.get(request.url)
                    .firstOrNull { it.name == "cf_clearance" }

                webViewChallengeResolver.resolve(request, oldCookie)
                return chain.proceed(request)
            }
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}

internal val ERROR_CODES = listOf(403, 503)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")
