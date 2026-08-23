package eu.kanade.tachiyomi.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

// Keep OkHttp call adapters and JSON response decoding centralized in one networking helper file.
val jsonMime = "application/json; charset=utf-8".toMediaType()

/**
 * Returns this URL with any internationalized domain name (IDN) host converted
 * to its ASCII punycode form (e.g. "https://ранобэ.рф/" -> "https://xn--.../"),
 * so it can be used in header values, which OkHttp restricts to ASCII.
 * Non-URL or already-ASCII values are returned unchanged.
 */
fun String.toAsciiUrl(): String {
    val trimmed = trim()
    return trimmed.toHttpUrlOrNull()?.toString() ?: trimmed
}

@PublishedApi
internal val defaultJsonParser = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

fun Call.asObservable(): Observable<Response> {
    return Observable.unsafeCreate { subscriber ->
        // Since Call is a one-shot type, clone it for each new subscriber.
        val call = clone()

        // Wrap the call in a helper which handles both unsubscription and backpressure.
        val requestArbiter = object : AtomicBoolean(), Producer, Subscription {
            override fun request(n: Long) {
                if (n == 0L || !compareAndSet(false, true)) return

                try {
                    val response = call.execute()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onNext(response)
                        subscriber.onCompleted()
                    }
                } catch (e: Exception) {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(e)
                    }
                }
            }

            override fun unsubscribe() {
                call.cancel()
            }

            override fun isUnsubscribed(): Boolean {
                return call.isCanceled()
            }
        }

        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

fun Call.asObservableSuccess(): Observable<Response> {
    return asObservable().doOnNext { response ->
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
    }
}

// Based on https://github.com/gildor/kotlin-coroutines-okhttp
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun Call.await(callStack: Array<StackTraceElement>): Response {
    return suspendCancellableCoroutine { continuation ->
        val callback =
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, res, _ ->
                        res.body.close()
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    // Don't bother with resuming the continuation if it is already cancelled.
                    if (continuation.isCancelled) return
                    val exception = IOException(e.message, e).apply { stackTrace = callStack }
                    continuation.resumeWithException(exception)
                }
            }

        enqueue(callback)

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                // Ignore cancel exception
            }
        }
    }
}

suspend fun Call.await(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    return await(callStack)
}

/**
 * @since extensions-lib 1.5
 */
suspend fun Call.awaitSuccess(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    val response = await(callStack)
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code).apply { stackTrace = callStack }
    }
    return response
}

fun OkHttpClient.newCachelessCallWithProgress(request: Request, listener: ProgressListener): Call {
    val progressClient = newBuilder()
        .cache(null)
        .callTimeout(30, java.util.concurrent.TimeUnit.HOURS)
        .addNetworkInterceptor { chain ->
            val originalResponse = chain.proceed(chain.request())
            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body, listener))
                .build()
        }
        .build()

    return progressClient.newCall(request)
}

/**
 * Cover-sized timeouts for image fetchers. Covers are small and latency-bound;
 * the shared client's 2-minute call timeout would let one stalled cover pin a
 * Coil fetcher slot (and a UI placeholder) far too long.
 *
 * Derives from [this] via [OkHttpClient.newBuilder], so connection pool,
 * interceptors (Cloudflare bypass, cover recovery), DoH routing and cookies
 * of the source-specific client are preserved. Each timeout becomes
 * min(current, budget): unset/infinite values (0) adopt the budget, and a
 * source client with deliberately tighter limits keeps them.
 */
fun OkHttpClient.withCoverTimeouts(
    callTimeoutMs: Long = COVER_CALL_TIMEOUT_MS,
    connectTimeoutMs: Long = COVER_CONNECT_TIMEOUT_MS,
    readTimeoutMs: Long = COVER_READ_TIMEOUT_MS,
): OkHttpClient {
    fun pickBudget(currentMs: Int, budgetMs: Long): Long =
        if (currentMs in 1..budgetMs) currentMs.toLong() else budgetMs

    return newBuilder()
        .callTimeout(pickBudget(callTimeoutMillis, callTimeoutMs), java.util.concurrent.TimeUnit.MILLISECONDS)
        .connectTimeout(pickBudget(connectTimeoutMillis, connectTimeoutMs), java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(pickBudget(readTimeoutMillis, readTimeoutMs), java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
}

private const val COVER_CALL_TIMEOUT_MS = 25_000L
private const val COVER_CONNECT_TIMEOUT_MS = 10_000L
private const val COVER_READ_TIMEOUT_MS = 20_000L

inline fun <reified T> Response.parseAs(json: Json = defaultJsonParser): T {
    return decodeFromJsonResponse(
        deserializer = serializer(),
        response = this,
        json = json,
    )
}

fun <T> decodeFromJsonResponse(
    deserializer: DeserializationStrategy<T>,
    response: Response,
    json: Json = defaultJsonParser,
): T {
    return response.body.source().use {
        json.decodeFromBufferedSource(deserializer, it)
    }
}

/**
 * Exception that handles HTTP codes considered not successful by OkHttp.
 * Use it to have a standardized error message in the app across the extensions.
 *
 * @since extensions-lib 1.5
 * @param code [Int] the HTTP status code
 */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
