package eu.kanade.domain.anime.interactor

internal fun normalizeMetadataSearchQuery(title: String): String {
    var normalized = title.trim()

    val suffixesToRemove = listOf(
        "\\s+Сезон\\s*\\d*",
        "\\s+сезон\\s*\\d*",
        "\\s+Season\\s*\\d*",
        "\\s+season\\s*\\d*",
        "\\s+TV\\b",
        "\\s+tv\\b",
        "\\s+Special\\b",
        "\\s+special\\b",
        "\\s+OVA\\b",
        "\\s+ova\\b",
        "\\s+ONA\\b",
        "\\s+ona\\b",
        "\\s+Movie\\b",
        "\\s+movie\\b",
    )

    suffixesToRemove.forEach { suffix ->
        normalized = normalized.replace(Regex(suffix, RegexOption.IGNORE_CASE), "")
    }

    return normalized.trim().replace(Regex("\\s+"), " ")
}

internal fun isPlaceholderPosterUrl(url: String?): Boolean {
    val value = url?.trim().orEmpty()
    if (value.isEmpty()) return true

    return value.contains("missing_", ignoreCase = true) ||
        value.contains("placeholder", ignoreCase = true) ||
        value.contains("no_image", ignoreCase = true)
}

internal fun chooseBestPosterUrl(primary: String?, fallback: String?): String? {
    return when {
        !isPlaceholderPosterUrl(primary) -> primary
        !isPlaceholderPosterUrl(fallback) -> fallback
        else -> null
    }
}

internal fun buildMediumPosterFallback(primary: String?): String? {
    val value = primary?.trim().orEmpty()
    if (value.isEmpty()) return null

    val fallback = value.replace("/large/", "/medium/")
    return fallback.takeIf { it != value && !isPlaceholderPosterUrl(it) }
}
