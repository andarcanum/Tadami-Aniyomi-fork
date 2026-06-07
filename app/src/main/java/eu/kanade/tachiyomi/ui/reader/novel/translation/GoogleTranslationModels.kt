package eu.kanade.tachiyomi.ui.reader.novel.translation

data class GoogleTranslationParams(
    val sourceLang: String,
    val targetLang: String,
)

data class GoogleTranslationBatchResponse(
    val translatedByIndex: Map<Int, String>,
    val detectedSourceLanguage: String? = null,
)
