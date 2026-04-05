package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import okhttp3.Interceptor
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
        // Check if Cloudflare anti-bot is on
        return response.code in ERROR_CODES && response.header("Server") in SERVER_CHECK
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        try {
            response.close()
            val hostLock = challengeLockByHost.getOrPut(request.url.host) { Any() }
            synchronized(hostLock) {
                val oldCookie = cookieManager.get(request.url)
                    .firstOrNull { it.name == "cf_clearance" }

                if (oldCookie != null) {
                    val retryWithExistingCookie = chain.proceed(request)
                    if (!shouldIntercept(retryWithExistingCookie)) {
                        return retryWithExistingCookie
                    }
                    retryWithExistingCookie.close()
                }

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

internal fun cloudflareChallengeUrlFor(request: Request): String {
    val url = request.url
    if (!request.shouldUseDomainRootForChallenge()) {
        return url.toString()
    }

    return url.newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .build()
        .toString()
}

private fun Request.shouldUseDomainRootForChallenge(): Boolean {
    val accept = header("Accept")
        ?.substringBefore(',')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    if (accept.startsWith("image/")) {
        return true
    }

    val path = url.encodedPath.lowercase()
    return STATIC_RESOURCE_PATH_REGEX.containsMatchIn(path)
}

internal val ERROR_CODES = listOf(403, 503)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val STATIC_RESOURCE_PATH_REGEX = Regex(
    pattern = """\.(?:avif|bmp|css|gif|ico|jpe?g|js|json|m3u8|mp4|otf|png|svg|ts|ttf|webm|webp|woff2?)$""",
    option = RegexOption.IGNORE_CASE,
)
