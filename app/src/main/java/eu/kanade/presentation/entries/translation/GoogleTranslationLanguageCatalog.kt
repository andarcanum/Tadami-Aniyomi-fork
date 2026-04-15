package eu.kanade.presentation.entries.translation

import eu.kanade.tachiyomi.extension.novel.normalizeNovelLang
import java.util.Locale

data class GoogleTranslationLanguageSuggestion(
    val canonicalName: String,
    val code: String,
)

data class GoogleTranslationLanguageFamilyOption(
    val code: String,
    val label: String,
)

private data class GoogleTranslationLanguageEntry(
    val canonicalName: String,
    val code: String,
    val aliases: List<String>,
)

private val googleTranslationLanguageEntries = listOf(
    GoogleTranslationLanguageEntry("Auto", "auto", listOf("auto", "авто", "automatic", "автоматически")),
    GoogleTranslationLanguageEntry("English", "en", listOf("english", "eng", "англ", "английский", "en")),
    GoogleTranslationLanguageEntry("Russian", "ru", listOf("russian", "рус", "русский", "ru")),
    GoogleTranslationLanguageEntry("Japanese", "ja", listOf("japanese", "jpn", "япон", "японский", "ja")),
    GoogleTranslationLanguageEntry("Chinese", "zh-CN", listOf("chinese", "china", "китай", "китайский", "zh")),
    GoogleTranslationLanguageEntry("Korean", "ko", listOf("korean", "корей", "корейский", "ko")),
    GoogleTranslationLanguageEntry("German", "de", listOf("german", "deutsch", "нем", "немецкий", "de")),
    GoogleTranslationLanguageEntry("French", "fr", listOf("french", "франц", "французский", "fr")),
    GoogleTranslationLanguageEntry("Spanish", "es", listOf("spanish", "espanol", "испан", "испанский", "es")),
    GoogleTranslationLanguageEntry("Italian", "it", listOf("italian", "итал", "итальянский", "it")),
    GoogleTranslationLanguageEntry("Portuguese", "pt", listOf("portuguese", "португ", "португальский", "pt")),
    GoogleTranslationLanguageEntry("Ukrainian", "uk", listOf("ukrainian", "украин", "украинский", "uk")),
    GoogleTranslationLanguageEntry("Polish", "pl", listOf("polish", "поль", "польский", "pl")),
    GoogleTranslationLanguageEntry("Turkish", "tr", listOf("turkish", "турец", "турецкий", "tr")),
    GoogleTranslationLanguageEntry("Arabic", "ar", listOf("arabic", "араб", "арабский", "ar")),
    GoogleTranslationLanguageEntry("Hindi", "hi", listOf("hindi", "хинди", "hi")),
    GoogleTranslationLanguageEntry("Thai", "th", listOf("thai", "тай", "тайский", "th")),
    GoogleTranslationLanguageEntry("Vietnamese", "vi", listOf("vietnamese", "вьет", "вьетнамский", "vi")),
    GoogleTranslationLanguageEntry("Indonesian", "id", listOf("indonesian", "индонез", "индонезийский", "id")),
)

private val googleTranslationLanguageFamilies by lazy {
    googleTranslationLanguageEntries
        .asSequence()
        .filterNot { it.code == "auto" }
        .map {
            GoogleTranslationLanguageFamilyOption(
                code = it.code.languageFamilyCode(),
                label = it.canonicalName,
            )
        }
        .distinctBy { it.code }
        .toList()
}

private val supportedGoogleTranslationLanguageFamilies by lazy {
    googleTranslationLanguageFamilies.mapTo(linkedSetOf()) { it.code }
}

fun googleTranslationLanguageSuggestions(input: String): List<GoogleTranslationLanguageSuggestion> {
    val normalizedInput = input.trim().lowercase(Locale.ROOT)
    if (normalizedInput.isBlank()) return emptyList()

    return googleTranslationLanguageEntries
        .asSequence()
        .filter { entry ->
            entry.canonicalName.lowercase(Locale.ROOT).contains(normalizedInput) ||
                entry.code.lowercase(Locale.ROOT).startsWith(normalizedInput) ||
                entry.aliases.any { alias -> alias.lowercase(Locale.ROOT).contains(normalizedInput) }
        }
        .map { entry ->
            GoogleTranslationLanguageSuggestion(
                canonicalName = entry.canonicalName,
                code = entry.code,
            )
        }
        .take(8)
        .toList()
}

fun googleTranslationSourceLanguageFamilyOptions(): List<GoogleTranslationLanguageFamilyOption> {
    return googleTranslationLanguageFamilies
}

fun resolveGoogleTranslationSourceLanguageFamily(rawLanguage: String?): String? {
    val normalized = normalizeNovelLang(rawLanguage).trim().lowercase(Locale.ROOT)
    if (normalized.isBlank()) return null
    if (normalized == "all" || normalized == "multi" || normalized == "auto") return null

    googleTranslationLanguageEntries.firstOrNull { entry ->
        val entryCode = entry.code.lowercase(Locale.ROOT)
        val entryName = entry.canonicalName.lowercase(Locale.ROOT)
        normalized == entryCode ||
            normalized == entryName ||
            entry.aliases.any { alias -> normalized == alias.lowercase(Locale.ROOT) }
    }?.let {
        return it.code.languageFamilyCode()
    }

    val family = normalized.languageFamilyCode()
    if (family.isBlank() || family == "und") return null

    return family.takeIf { it in supportedGoogleTranslationLanguageFamilies || it.startsWith("zh") }
        ?.let { if (it.startsWith("zh")) "zh" else it }
}

fun resolveGoogleTranslationLanguageFamily(languageTag: String?): String? {
    val normalized = normalizeNovelLang(languageTag).trim().lowercase(Locale.ROOT)
    if (normalized.isBlank()) return null
    if (normalized == "all" || normalized == "multi" || normalized == "auto") return null

    val family = normalized.languageFamilyCode()
    if (family.isBlank() || family == "und") return null

    return family
}

fun shouldTranslateAuroraEntry(
    enabled: Boolean,
    sourceLanguage: String?,
    targetLanguage: String,
    allowedSourceFamilies: Set<String>,
): Boolean {
    if (!enabled || allowedSourceFamilies.isEmpty()) return false

    val sourceFamily = resolveGoogleTranslationSourceLanguageFamily(sourceLanguage) ?: return false
    if (sourceFamily !in allowedSourceFamilies) return false

    val targetFamily = resolveGoogleTranslationLanguageFamily(targetLanguage) ?: return false
    return sourceFamily != targetFamily
}

private fun String.languageFamilyCode(): String {
    return substringBefore('-')
        .substringBefore('_')
        .trim()
        .lowercase(Locale.ROOT)
}
