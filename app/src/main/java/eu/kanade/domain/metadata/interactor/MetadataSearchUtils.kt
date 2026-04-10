package eu.kanade.domain.metadata.interactor

fun normalizeMetadataSearchQuery(title: String): String {
    var normalized = title.trim()

    val suffixesToRemove = listOf(
        "\\s+РЎРµР·РѕРЅ\\s*\\d*",
        "\\s+СЃРµР·РѕРЅ\\s*\\d*",
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

fun isPlaceholderPosterUrl(url: String?): Boolean {
    val value = url?.trim().orEmpty()
    if (value.isEmpty()) return true

    return value.contains("missing_", ignoreCase = true) ||
        value.contains("placeholder", ignoreCase = true) ||
        value.contains("no_image", ignoreCase = true)
}

fun chooseBestPosterUrl(primary: String?, fallback: String?): String? {
    return when {
        !isPlaceholderPosterUrl(primary) -> primary
        !isPlaceholderPosterUrl(fallback) -> fallback
        else -> null
    }
}

fun buildMediumPosterFallback(primary: String?): String? {
    val value = primary?.trim().orEmpty()
    if (value.isEmpty()) return null

    val fallback = value.replace("/large/", "/medium/")
    return fallback.takeIf { it != value && !isPlaceholderPosterUrl(it) }
}

fun parseOriginalTitle(description: String?): String? {
    if (description.isNullOrBlank()) return null

    val patterns = listOf(
        Regex("""Original:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
        Regex("""РћСЂРёРіРёРЅР°Р»:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
        Regex("""Original Title:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
        Regex("""РћСЂРёРіРёРЅР°Р»СЊРЅРѕРµ РЅР°Р·РІР°РЅРёРµ:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
        Regex("""Original:\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE),
        Regex("""РћСЂРёРіРёРЅР°Р»:\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE),
    )

    for (pattern in patterns) {
        val match = pattern.find(description)
        if (match != null) {
            val title = match.groupValues[1].trim()
            val cleaned = title.trimEnd('.', ',', '"', '\'')
            if (cleaned.isNotEmpty()) {
                return cleaned
            }
        }
    }

    description.lines().forEach { line ->
        if (line.contains("Original", ignoreCase = true) ||
            line.contains("РћСЂРёРіРёРЅР°Р»", ignoreCase = true) ||
            line.contains("Romaji", ignoreCase = true) ||
            line.contains("РЇРїРѕРЅСЃРєРѕРµ", ignoreCase = true)
        ) {
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val afterColon = line.substring(colonIndex + 1).trim()
                if (afterColon.isNotEmpty()) {
                    return afterColon.trimEnd('.', ',', '"', '\'')
                }
            }
        }
        val nonCyrillic = line.filter { char -> char.code < 0x400 || char.code > 0x4FF }.trim()
        if (nonCyrillic.length > 5 && nonCyrillic.length < 200) {
            return nonCyrillic.trimEnd('.', ',', '"', '\'')
        }
    }

    return null
}
