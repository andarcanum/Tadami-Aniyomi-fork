package eu.kanade.tachiyomi.extension.novel.runtime

import java.net.URI

fun resolveUrl(input: String, base: String?): String {
    val inputValue = input.trim()
    val baseValue = base?.trim().orEmpty()
    return runCatching {
        val inputUri = URI(inputValue)
        if (inputUri.isAbsolute) return inputUri.toString()
        if (baseValue.isBlank()) return inputValue
        val baseUri = if (baseValue.isNotBlank()) {
            ensureResolvableBaseUri(URI(baseValue))
        } else {
            URI("")
        }
        baseUri.resolve(inputUri).toString()
    }.getOrElse { inputValue }
}

private fun ensureResolvableBaseUri(baseUri: URI): URI {
    if (!baseUri.isAbsolute || !baseUri.path.isNullOrEmpty()) {
        return baseUri
    }
    return URI(
        baseUri.scheme,
        baseUri.authority,
        "/",
        baseUri.query,
        baseUri.fragment,
    )
}

fun getPathname(url: String): String {
    val value = url.trim()
    return runCatching { URI(value).path ?: "" }.getOrDefault("")
}
