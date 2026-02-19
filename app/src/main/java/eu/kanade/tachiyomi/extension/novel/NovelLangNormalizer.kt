package eu.kanade.tachiyomi.extension.novel

import tachiyomi.domain.extension.novel.model.NovelPlugin
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val bidiMarks = Regex("[\\u200E\\u200F\\u202A-\\u202E]")
private val tagPattern = Regex("^[a-zA-Z]{2,3}([_-][a-zA-Z0-9]{2,8})*$")
private val localeLookup by lazy {
    val map = ConcurrentHashMap<String, String>()
    for (locale in Locale.getAvailableLocales()) {
        val language = locale.language
        if (language.isNullOrBlank()) continue

        val nativeName = normalizeToken(locale.getDisplayLanguage(locale))
        if (nativeName.isNotEmpty()) map.putIfAbsent(nativeName, language)

        val englishName = normalizeToken(locale.getDisplayLanguage(Locale.ENGLISH))
        if (englishName.isNotEmpty()) map.putIfAbsent(englishName, language)
    }
    map
}

fun normalizeNovelLang(raw: String?): String {
    if (raw == null) return ""
    val cleaned = raw.replace(bidiMarks, "").trim()
    if (cleaned.isEmpty()) return ""

    val lower = cleaned.lowercase(Locale.ROOT)
    if (lower == "multi" || lower == "all") return "all"

    val candidates = cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (candidate in candidates) {
        if (tagPattern.matches(candidate)) {
            return candidate.replace('_', '-').lowercase(Locale.ROOT)
        }

        val match = findLocaleLanguage(candidate)
        if (match != null) return match
    }

    return cleaned
}

private fun findLocaleLanguage(value: String): String? {
    val normalizedValue = normalizeToken(value)
    if (normalizedValue.isEmpty()) return null

    return localeLookup[normalizedValue]
}

private fun normalizeToken(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return value
        .replace(bidiMarks, "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")
}

fun NovelPlugin.Available.withNormalizedLang(): NovelPlugin.Available {
    val normalized = normalizeNovelLang(lang)
    return if (normalized == lang) this else copy(lang = normalized)
}

fun NovelPlugin.Installed.withNormalizedLang(): NovelPlugin.Installed {
    val normalized = normalizeNovelLang(lang)
    return if (normalized == lang) this else copy(lang = normalized)
}
