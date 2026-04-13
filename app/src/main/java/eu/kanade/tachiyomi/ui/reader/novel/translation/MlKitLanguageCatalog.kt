package eu.kanade.tachiyomi.ui.reader.novel.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

data class MlKitLanguageOption(
    val languageCode: String,
    val displayName: String,
    val isBuiltIn: Boolean,
)

object MlKitLanguageCatalog {

    fun supportedLanguageCodes(): List<String> {
        return TranslateLanguage.getAllLanguages()
    }

    fun supportedLanguageOptions(
        languageCodes: List<String> = supportedLanguageCodes(),
        locale: Locale = Locale.getDefault(),
    ): List<MlKitLanguageOption> {
        return languageCodes
            .mapNotNull { languageCode ->
                canonicalizeLanguageCode(languageCode)?.let { canonicalCode ->
                    MlKitLanguageOption(
                        languageCode = canonicalCode,
                        displayName = displayName(canonicalCode, locale),
                        isBuiltIn = isBuiltInLanguageCode(canonicalCode),
                    )
                }
            }
            .distinctBy { it.languageCode }
            .sortedWith(
                compareBy<MlKitLanguageOption> { it.displayName.lowercase(locale) }
                    .thenBy { it.languageCode },
            )
    }

    fun canonicalizeLanguageCode(languageCode: String): String? {
        val normalized = languageCode.trim()
        if (normalized.isBlank()) return null
        return TranslateLanguage.fromLanguageTag(normalized)
    }

    fun isBuiltInLanguageCode(languageCode: String): Boolean {
        return canonicalizeLanguageCode(languageCode) == TranslateLanguage.ENGLISH
    }

    fun requiredModelLanguageCodes(
        sourceLanguage: String,
        targetLanguage: String,
    ): Set<String> {
        return buildSet {
            canonicalizeLanguageCode(sourceLanguage)?.let { source ->
                if (!isBuiltInLanguageCode(source)) add(source)
            }
            canonicalizeLanguageCode(targetLanguage)?.let { target ->
                if (!isBuiltInLanguageCode(target)) add(target)
            }
        }
    }

    private fun displayName(languageCode: String, locale: Locale): String {
        val name = Locale.forLanguageTag(languageCode).getDisplayName(locale).trim()
        return name.takeIf { it.isNotBlank() } ?: languageCode
    }
}
