package eu.kanade.tachiyomi.ui.reader.novel.translation

data class GoogleTranslationParams(
    val sourceLang: String,
    val targetLang: String,
)

data class GoogleTranslationBatchResponse(
    val translatedByIndex: Map<Int, String>,
    val detectedSourceLanguage: String? = null,
    /**
     * True when any request in the batch hit HTTP 429 (including per-segment fallbacks). The
     * controller surfaces it so a partial result is not silently treated as complete and the
     * user gets an explicit Resume action.
     */
    val rateLimited: Boolean = false,
)
