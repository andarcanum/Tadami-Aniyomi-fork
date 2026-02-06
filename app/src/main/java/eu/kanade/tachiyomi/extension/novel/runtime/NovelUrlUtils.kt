package eu.kanade.tachiyomi.extension.novel.runtime

import java.net.URI

fun resolveUrl(input: String, base: String?): String {
    val inputValue = input.trim()
    val baseValue = base?.trim().orEmpty()
    return runCatching {
        val inputUri = URI(inputValue)
        if (inputUri.isAbsolute) return inputUri.toString()
        val baseUri = if (baseValue.isNotBlank()) URI(baseValue) else URI("")
        baseUri.resolve(inputUri).toString()
    }.getOrElse { inputValue }
}

fun getPathname(url: String): String {
    val value = url.trim()
    return runCatching { URI(value).path ?: "" }.getOrDefault("")
}
