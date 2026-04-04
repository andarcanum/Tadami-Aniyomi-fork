package eu.kanade.presentation.reader.novel

enum class GoogleTranslationUiState {
    Translating,
    CachedVisible,
    CachedHidden,
    Ready,
}

fun resolveGoogleTranslationUiState(
    isTranslating: Boolean,
    hasCache: Boolean,
    isVisible: Boolean,
): GoogleTranslationUiState {
    return when {
        isTranslating -> GoogleTranslationUiState.Translating
        hasCache && isVisible -> GoogleTranslationUiState.CachedVisible
        hasCache && !isVisible -> GoogleTranslationUiState.CachedHidden
        else -> GoogleTranslationUiState.Ready
    }
}
