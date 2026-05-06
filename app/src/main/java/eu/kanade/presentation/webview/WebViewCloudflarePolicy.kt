package eu.kanade.presentation.webview

import java.util.Locale

internal fun shouldReloadMainFrameNavigation(
    requestUrl: String,
    currentUrl: String?,
    isForMainFrame: Boolean,
    method: String?,
): Boolean {
    if (!isForMainFrame) return false
    if (!method.equals("GET", ignoreCase = true)) return false
    if (!requestUrl.startsWith("http://") && !requestUrl.startsWith("https://")) return false
    return requestUrl != currentUrl
}

internal fun isCloudflareChallengePage(
    url: String?,
    title: String? = null,
): Boolean {
    val normalizedUrl = url?.lowercase(Locale.ROOT).orEmpty()
    if ("challenges.cloudflare.com" in normalizedUrl) return true
    if ("/cdn-cgi/challenge-platform/" in normalizedUrl) return true

    val normalizedTitle = title?.lowercase(Locale.ROOT).orEmpty()
    return "just a moment" in normalizedTitle || "attention required" in normalizedTitle
}
