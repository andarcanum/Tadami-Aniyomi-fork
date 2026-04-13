package eu.kanade.tachiyomi.ui.reader.novel.translation

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.tachiyomi.extension.novel.normalizeNovelLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MlKitTranslationService(
) {
    private val modelManager: MlKitModelManager by lazy { MlKitModelManager() }

    private val languageIdentifier: LanguageIdentifier by lazy {
        LanguageIdentification.getClient()
    }

    suspend fun translateBatch(
        texts: List<String>,
        params: GoogleTranslationParams,
        onLog: ((String) -> Unit)? = null,
        onProgress: ((TranslationPhase, Int) -> Unit)? = null,
    ): GoogleTranslationBatchResponse = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) {
            return@withContext GoogleTranslationBatchResponse(emptyMap())
        }

        val normalizedSource = normalizeSourceLanguage(params.sourceLang)
        val normalizedTarget = normalizeTargetLanguage(params.targetLang)
        if (normalizedTarget.isBlank()) {
            return@withContext GoogleTranslationBatchResponse(emptyMap())
        }

        val resolvedSourceLang = if (normalizedSource == "auto") {
            detectSourceLanguage(texts.first())
        } else {
            normalizedSource
        }

        if (resolvedSourceLang.isBlank() || resolvedSourceLang == "und") {
            return@withContext GoogleTranslationBatchResponse(
                translatedByText = texts.associateWith { it },
                detectedSourceLanguage = resolvedSourceLang,
            )
        }

        val requiredModelLanguageCodes = modelManager.requiredModelLanguageCodes(
            sourceLanguage = resolvedSourceLang,
            targetLanguage = normalizedTarget,
        )
        val missingModelLanguageCodes = modelManager.missingDownloadedLanguageCodes(
            requiredLanguageCodes = requiredModelLanguageCodes,
        )
        if (missingModelLanguageCodes.isNotEmpty()) {
            throw MlKitMissingModelsException(missingModelLanguageCodes)
        }

        val translator = createTranslator(resolvedSourceLang, normalizedTarget)

        try {
            onProgress?.invoke(TranslationPhase.PREPARING_MODEL, 0)
            onProgress?.invoke(TranslationPhase.TRANSLATING, 0)

            val translations = translateTexts(texts, translator) { index, total ->
                val percent = ((index + 1) * 100) / total
                onProgress?.invoke(TranslationPhase.TRANSLATING, percent)
            }
            GoogleTranslationBatchResponse(
                translatedByText = translations,
                detectedSourceLanguage = resolvedSourceLang,
            )
        } finally {
            translator.close()
        }
    }

    private suspend fun detectSourceLanguage(text: String): String {
        return try {
            languageIdentifier.identifyLanguage(text).await() ?: "und"
        } catch (e: Exception) {
            "und"
        }
    }

    private fun createTranslator(sourceLang: String, targetLang: String): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        return Translation.getClient(options)
    }

    private suspend fun translateTexts(
        texts: List<String>,
        translator: Translator,
        onProgress: (Int, Int) -> Unit,
    ): Map<String, String> {
        val translations = mutableMapOf<String, String>()
        texts.forEachIndexed { index, text ->
            if (text.isNotBlank()) {
                val translated = translator.translate(text).await()
                translations[text] = translated
            } else {
                translations[text] = text
            }
            onProgress(index, texts.size)
        }
        return translations
    }

    private fun normalizeSourceLanguage(sourceLanguage: String): String {
        return normalizeNovelLang(sourceLanguage).takeIf { it.isNotBlank() } ?: "auto"
    }

    private fun normalizeTargetLanguage(targetLanguage: String): String {
        return normalizeNovelLang(targetLanguage)
    }
}
