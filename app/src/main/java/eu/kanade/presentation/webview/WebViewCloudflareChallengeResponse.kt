package eu.kanade.presentation.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.util.system.sanitizeCloudflareRequestHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Locale

private val cloudflareCspHeaderNames = setOf(
    "content-security-policy",
    "content-security-policy-report-only",
)

internal fun interceptCloudflareChallengeMainFrame(
    client: OkHttpClient,
    request: WebResourceRequest?,
    targetHost: String?,
    contextPackageName: String,
    spoofedPackageName: String,
): WebResourceResponse? {
    request ?: return null
    val expectedHost = targetHost ?: return null
    if (!request.isForMainFrame) return null
    if (!request.method.equals("GET", ignoreCase = true)) return null

    val requestHost = request.url.host ?: return null
    if (!requestHost.equals(expectedHost, ignoreCase = true)) return null

    return try {
        val safeHeaders = sanitizeCloudflareReplayHeaders(
            requestHeaders = request.requestHeaders.orEmpty(),
            contextPackageName = contextPackageName,
            spoofedPackageName = spoofedPackageName,
        )
        val okHttpRequest = Request.Builder()
            .url(request.url.toString())
            .apply {
                safeHeaders.forEach { (name, value) ->
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
                .filterKeys { name -> name.lowercase(Locale.ROOT) !in cloudflareCspHeaderNames }
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

internal fun sanitizeCloudflareReplayHeaders(
    requestHeaders: Map<String, String>,
    contextPackageName: String,
    spoofedPackageName: String,
): Map<String, String> {
    val safeHeaders = requestHeaders.filterNot { (name, _) ->
        name.lowercase(Locale.ENGLISH) in cloudflareUnsafeReplayHeaderNames
    }
    return sanitizeCloudflareRequestHeaders(
        requestHeaders = safeHeaders,
        contextPackageName = contextPackageName,
        spoofedPackageName = spoofedPackageName,
    )
}

private val cloudflareUnsafeReplayHeaderNames = setOf(
    "connection",
    "content-length",
    "host",
    "keep-alive",
    "set-cookie",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
)
