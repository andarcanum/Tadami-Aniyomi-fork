package eu.kanade.tachiyomi.extension.novel.runtime

import android.webkit.CookieManager
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal fun applyWebViewCookies(
    headers: MutableMap<String, String>,
    url: String,
    getCookie: (String) -> String?,
) {
    val webViewCookies = getCookie(url).orEmpty()
    if (webViewCookies.isBlank()) return

    val cookieHeaderName = headers.keys.firstOrNull { it.equals("cookie", ignoreCase = true) } ?: "Cookie"
    val mergedCookies = mergeCookieHeaderValues(
        existingHeader = headers[cookieHeaderName].orEmpty(),
        webViewCookies = webViewCookies,
    )
    if (mergedCookies.isNotBlank()) {
        headers[cookieHeaderName] = mergedCookies
    }

    if (headers.keys.none { it.equals("x-xsrf-token", ignoreCase = true) }) {
        val xsrfToken = webViewCookies
            .split(';')
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("XSRF-TOKEN=") }
            ?.substringAfter('=')
            ?.let { token ->
                runCatching {
                    URLDecoder.decode(token, StandardCharsets.UTF_8.name())
                }.getOrDefault(token)
            }

        if (!xsrfToken.isNullOrBlank()) {
            headers["X-XSRF-TOKEN"] = xsrfToken
        }
    }
}

internal fun syncWebViewResponseCookies(
    url: String,
    setCookieHeaders: List<String>,
    setCookie: (String, String) -> Unit,
) {
    setCookieHeaders.forEach { setCookie(url, it) }
}

internal fun applyAndroidWebViewCookies(
    headers: MutableMap<String, String>,
    url: String,
) {
    runCatching {
        applyWebViewCookies(
            headers = headers,
            url = url,
            getCookie = { CookieManager.getInstance().getCookie(it) },
        )
    }
}

internal fun syncAndroidWebViewResponseCookies(
    url: String,
    setCookieHeaders: List<String>,
) {
    runCatching {
        val cookieManager = CookieManager.getInstance()
        syncWebViewResponseCookies(
            url = url,
            setCookieHeaders = setCookieHeaders,
            setCookie = { targetUrl, cookie -> cookieManager.setCookie(targetUrl, cookie) },
        )
        cookieManager.flush()
    }
}

private fun mergeCookieHeaderValues(
    existingHeader: String,
    webViewCookies: String,
): String {
    val merged = linkedMapOf<String, String>()

    parseCookieHeader(existingHeader).forEach { (name, value) ->
        merged[name] = value
    }
    parseCookieHeader(webViewCookies).forEach { (name, value) ->
        merged[name] = value
    }

    return merged.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}

private fun parseCookieHeader(headerValue: String): List<Pair<String, String>> {
    return headerValue.split(';')
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && '=' in it }
        .map { cookie ->
            cookie.substringBefore('=') to cookie.substringAfter('=')
        }
        .toList()
}
