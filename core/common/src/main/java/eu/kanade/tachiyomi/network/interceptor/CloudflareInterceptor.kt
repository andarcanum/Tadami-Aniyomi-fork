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
    // P3: injectable clock for the interactive-host negative cache (JVM-testable).
    private val clock: () -> Long = android.os.SystemClock::elapsedRealtime,
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
        // Covers must never pay for a 30s hidden WebView solve: a challenged cover grid would
        // serialize dozens of solves on the global gate and ANR the app. Fail fast instead -
        // Coil shows a placeholder and the cover-host blacklist skips repeats.
        if (CoverRequestPolicy.isCoverRequest(response.request)) {
            return false
        }
        if (response.code !in ERROR_CODES ||
            !CloudflareChallengeDetector.isCloudflareServer(response.header("Server"))
        ) {
            return false
        }
        // The cf-mitigated header is authoritative for managed/interactive challenges.
        if (CloudflareChallengeDetector.isManagedChallenge(response.header("cf-mitigated"))) {
            return true
        }
        // Limit body inspection to a small prefix; challenge markup is at the top of the
        // document and full body parsing wastes memory on large pages. The detector covers
        // the full family of challenge markers (interstitial, interactive, error), not just
        // the two error markers the interceptor used to look for.
        val bodyPeek = response.peekBody(CHALLENGE_PEEK_BYTES).string()
        return CloudflareChallengeDetector.hasChallengeMarkers(bodyPeek)
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        val host = request.url.host
        try {
            response.close()
            // Fingerprint-mismatched hosts (OkHttp challenged, WebView clean) are served via the
            // WebView bridge by the plugin runtime: skip the doomed 30s hidden solve entirely
            // and fail fast so the caller retries through the bridge immediately.
            if (CloudflareBridgeRegistry.isBridged(host)) {
                throw CloudflareFingerprintMismatchException()
            }
            // One lock object per host is kept for the process lifetime so concurrent
            // requests to the same host coalesce onto a single WebView solve and then
            // reuse the resulting cf_clearance. Removing it eagerly (as before) reintroduced
            // a race: a late arrival would create a *fresh* lock and solve in parallel,
            // spinning up duplicate WebViews and racing on the cookie. Hosts are few, so the
            // retained lock objects are negligible.
            val hostLock = challengeLockByHost.getOrPut(host) { Any() }
            synchronized(hostLock) {
                // P3: negative cache - an interactive challenge needs a human; retrying the
                // WebView solve every cycle only serializes latency on the global gate.
                if (CloudflareInteractiveChallengeTracker.freshEntry(host, clock) != null) {
                    throw IOException(
                        context.stringResource(MR.strings.information_cloudflare_interactive_challenge),
                    )
                }
                val oldCookie = cookieManager.get(request.url)
                    .firstOrNull { it.name == "cf_clearance" }

                // Only pay for an immediate network retry when there is a clearance to try.
                // With no cookie this was just an extra blocked request before WebView.
                // For coalesced followers this is the fast path: the leader already solved
                // the challenge, so the fresh clearance usually clears them without WebView.
                if (oldCookie != null) {
                    val immediateRetry = chain.proceed(request)
                    if (!shouldIntercept(immediateRetry)) {
                        CloudflareInteractiveChallengeTracker.clear(host)
                        return immediateRetry
                    }
                    immediateRetry.close()
                    cookieManager.remove(request.url, COOKIE_NAMES, 0)
                }

                // Serialize challenge solves globally (across hosts *and* interceptor
                // instances): each solve spins up a heavyweight WebView (~tens of MB,
                // up to 30 s). Per-host locks alone still allowed parallel WebViews for
                // different hosts during a library refresh -- observed as OutOfMemoryError
                // crashes on 512 MB-heap devices (see crash log #bug 0.60 / POCO X6 Pro).
                synchronized(globalChallengeGate) {
                    webViewChallengeResolver.resolve(request, oldCookie)
                }

                val firstAttempt = chain.proceed(request)
                if (!shouldIntercept(firstAttempt)) {
                    CloudflareInteractiveChallengeTracker.clear(host)
                    return firstAttempt
                }
                // The cookie set on CookieManager may not have propagated to OkHttp's
                // CookieJar yet for the in-flight connection; close and retry once.
                firstAttempt.close()
                CloudflareInteractiveChallengeTracker.clear(host)
                return chain.proceed(request)
            }
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareInteractiveChallengeException) {
            CloudflareInteractiveChallengeTracker.record(request.url.toString(), clock())
            CloudflareManualSolveRegistry.request(host)
            throw IOException(
                context.stringResource(MR.strings.information_cloudflare_interactive_challenge),
                e,
            )
        } catch (e: CloudflareFingerprintMismatchException) {
            // Hidden WebView loaded clean (no widget, no markers) yet OkHttp stays challenged:
            // a visible manual solve would show the same captcha-free logged-in page, so never
            // register a manual-solve prompt - mark the host for silent WebView-bridge routing.
            CloudflareBridgeRegistry.mark(host)
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: CloudflareBypassException) {
            CloudflareManualSolveRegistry.request(host)
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}

// P2: CF serves managed/interactive challenges with 401/429 as well as the classic 403/503
// (429 rate-limit-plus-challenge combos, 401 behind managed rules). Non-challenge 401/429
// bodies carry no challenge markers, so the marker gate below still rejects them.
internal val ERROR_CODES = listOf(401, 403, 429, 503)
private val COOKIE_NAMES = listOf("cf_clearance")

// Global (process-wide) gate for Cloudflare challenge solves: one WebView solve at a time,
// regardless of host or which interceptor instance received the request.
private val globalChallengeGate = Any()

// Just enough to capture the challenge headers/markers; the page body is larger
// but the challenge identifiers always appear near the top.
private const val CHALLENGE_PEEK_BYTES = 8L * 1024L
