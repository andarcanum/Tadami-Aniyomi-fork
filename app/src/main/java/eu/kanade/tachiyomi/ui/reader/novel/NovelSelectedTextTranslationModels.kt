package eu.kanade.tachiyomi.ui.reader.novel

enum class NovelSelectedTextRenderer {
    NATIVE_SCROLL,
    PAGE_READER,
    WEBVIEW,
}

data class NovelSelectedTextAnchor(
    val leftPx: Int,
    val topPx: Int,
    val rightPx: Int,
    val bottomPx: Int,
) {
    val widthPx: Int get() = (rightPx - leftPx).coerceAtLeast(0)
    val heightPx: Int get() = (bottomPx - topPx).coerceAtLeast(0)
}

data class NovelSelectedTextSelection(
    val sessionId: Long,
    val renderer: NovelSelectedTextRenderer,
    val text: String,
    val anchor: NovelSelectedTextAnchor,
) {
    val normalizedText: String = normalizeNovelSelectedText(text)

    val isBlank: Boolean
        get() = normalizedText.isBlank()
}

data class NovelSelectedTextTranslationResult(
    val translation: String,
    val detectedSourceLanguage: String? = null,
    val providerFingerprint: String = "",
)

data class NovelSelectedTextTranslationCacheKey(
    val backendFingerprint: String,
    val targetLanguage: String,
    val normalizedText: String,
)

fun normalizeNovelSelectedText(text: String): String {
    return text.trim().replace(Regex("\\s+"), " ")
}

fun buildNovelSelectedTextTranslationCacheKey(
    backendFingerprint: String,
    targetLanguage: String,
    text: String,
): NovelSelectedTextTranslationCacheKey {
    return NovelSelectedTextTranslationCacheKey(
        backendFingerprint = backendFingerprint.trim(),
        targetLanguage = targetLanguage.trim(),
        normalizedText = normalizeNovelSelectedText(text),
    )
}

fun isNovelSelectedTextTranslationResponseStale(
    activeSelection: NovelSelectedTextSelection?,
    responseSessionId: Long,
): Boolean {
    return activeSelection?.sessionId != responseSessionId
}

sealed interface NovelSelectedTextTranslationErrorReason {
    data object EmptySelection : NovelSelectedTextTranslationErrorReason
    data object TooLongSelection : NovelSelectedTextTranslationErrorReason
    data object ParserFailure : NovelSelectedTextTranslationErrorReason
    data object WebViewUnavailable : NovelSelectedTextTranslationErrorReason
    data class BackendUnavailable(val message: String? = null) : NovelSelectedTextTranslationErrorReason
    data class NetworkFailure(val message: String? = null) : NovelSelectedTextTranslationErrorReason
    data class Cooldown(val remainingSeconds: Int) : NovelSelectedTextTranslationErrorReason
}
