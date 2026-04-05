package eu.kanade.tachiyomi.ui.reader.novel.translation

data class GoogleTranslationParams(
    val sourceLang: String,
    val targetLang: String,
)

data class GoogleTranslationBatchResponse(
    val translatedByText: Map<String, String>,
    val detectedSourceLanguage: String? = null,
)
