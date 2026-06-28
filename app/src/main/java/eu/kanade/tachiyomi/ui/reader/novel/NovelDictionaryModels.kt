package eu.kanade.tachiyomi.ui.reader.novel

/**
 * A single dictionary entry for a looked-up term.
 * [definitionsHtml] is sanitized HTML (safe subset) ready to be rendered.
 */
data class NovelDictionaryEntry(
    val headword: String,
    val pronunciation: String? = null,
    val partOfSpeech: String? = null,
    val definitionsHtml: String,
    val sourceLanguage: String? = null,
)

data class NovelDictionaryResult(
    val entries: List<NovelDictionaryEntry>,
    val providerFingerprint: String = "",
    val attribution: String? = null,
)

data class NovelDictionaryCacheKey(
    val backendFingerprint: String,
    val sourceLanguage: String,
    val normalizedTerm: String,
)

fun buildNovelDictionaryCacheKey(
    backendFingerprint: String,
    sourceLanguage: String,
    term: String,
): NovelDictionaryCacheKey = NovelDictionaryCacheKey(
    backendFingerprint = backendFingerprint.trim(),
    sourceLanguage = sourceLanguage.trim().lowercase(),
    normalizedTerm = normalizeNovelSelectedText(term).lowercase(),
)

/**
 * Best-effort language detection by Unicode block. Used only when the source
 * provides no language hint. Order matters: kana implies Japanese even though
 * Japanese also uses Han ideographs.
 */
fun detectNovelTextLanguage(text: String, sourceLanguage: String? = null): String? {
    var hasHan = false
    for (ch in text) {
        when (Character.UnicodeBlock.of(ch)) {
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            -> return "ja"
            Character.UnicodeBlock.HANGUL_SYLLABLES,
            Character.UnicodeBlock.HANGUL_JAMO,
            Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO,
            -> return "ko"
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            -> hasHan = true
            Character.UnicodeBlock.CYRILLIC -> return "ru"
            else -> {}
        }
    }
    if (hasHan) {
        val resolvedSource = sourceLanguage?.trim()?.lowercase()
        if (resolvedSource == "ja" || resolvedSource == "zh") {
            return resolvedSource
        }
        return "zh"
    }
    return text.firstOrNull { it.isLetter() }?.let { "en" }
}
