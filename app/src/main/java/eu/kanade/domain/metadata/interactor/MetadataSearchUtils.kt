package eu.kanade.domain.metadata.interactor

fun normalizeMetadataSearchQuery(title: String): String {
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
        Regex("""Оригинал:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
        Regex("""Original Title:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
        Regex("""Оригинальное название:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
        Regex("""Original:\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE),
        Regex("""Оригинал:\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE),
    )

    for (pattern in patterns) {
        val match = pattern.find(description)
        if (match != null) {
            val cleaned = match.groupValues[1].trim().trimEnd('.', ',', '"', '\'')
            if (cleaned.any { it.isLetterOrDigit() }) {
                return cleaned
            }
        }
    }

    description.lines().forEach { line ->
        if (line.contains("Original", ignoreCase = true) ||
            line.contains("Оригинал", ignoreCase = true) ||
            line.contains("Romaji", ignoreCase = true) ||
            line.contains("Японское", ignoreCase = true)
        ) {
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val afterColon = line.substring(colonIndex + 1).trim().trimEnd('.', ',', '"', '\'')
                if (afterColon.any { it.isLetterOrDigit() }) {
                    return afterColon
                }
            }
        }
        extractNonCyrillicTitleCandidate(line)?.let { return it }
    }

    return null
}

internal fun extractNonCyrillicTitleCandidate(line: String): String? {
    val cleaned = line
        .filter { char -> char.code < 0x400 || char.code > 0x4FF }
        .replace(Regex("\\s+"), " ")
        .trim { ch -> ch.isWhitespace() || ch in ".,;:!?\"'()[]{}—–-/|" }
    if (cleaned.length !in 4..199) return null

    val letterCount = cleaned.count { it.isLetter() }
    if (letterCount < 3) return null

    // Reject punctuation/number remnants stripped out of Cyrillic prose (e.g. ",   .   ,  .")
    val nonWhitespaceCount = cleaned.count { !it.isWhitespace() }
    if (nonWhitespaceCount == 0 || letterCount.toDouble() / nonWhitespaceCount < 0.5) {
        return null
    }

    // Reject mostly-Cyrillic narrative lines that happen to contain a stray Latin token
    val cyrillicCount = line.count { char -> char.code in 0x400..0x4FF }
    if (cyrillicCount > letterCount * 2) return null

    return cleaned
}
