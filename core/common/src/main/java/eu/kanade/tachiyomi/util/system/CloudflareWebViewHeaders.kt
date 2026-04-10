package eu.kanade.tachiyomi.util.system

import java.util.Locale

private val cloudflareBlockedHeaders = setOf(
    "sec-ch-ua",
    "sec-ch-ua-full-version-list",
)

fun sanitizeCloudflareRequestHeaders(
    requestHeaders: Map<String, String>,
    contextPackageName: String,
    spoofedPackageName: String,
): Map<String, String> {
    return requestHeaders.filterNot { (name, value) ->
        when (name.lowercase(Locale.ROOT)) {
            "x-requested-with" -> value == contextPackageName || value == spoofedPackageName
            in cloudflareBlockedHeaders -> true
            else -> false
        }
    }
}
