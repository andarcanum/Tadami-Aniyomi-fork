package eu.kanade.tachiyomi.ui.reader.novel

sealed interface NovelSelectedTextTranslationUiState {
    data object Idle : NovelSelectedTextTranslationUiState

    data class SelectionAvailable(
        val selection: NovelSelectedTextSelection,
    ) : NovelSelectedTextTranslationUiState

    data class Translating(
        val selection: NovelSelectedTextSelection,
    ) : NovelSelectedTextTranslationUiState

    data class Result(
        val selection: NovelSelectedTextSelection,
        val translationResult: NovelSelectedTextTranslationResult,
    ) : NovelSelectedTextTranslationUiState

    data class Error(
        val selection: NovelSelectedTextSelection,
        val reason: NovelSelectedTextTranslationErrorReason,
    ) : NovelSelectedTextTranslationUiState

    data class Unavailable(
        val reason: NovelSelectedTextTranslationErrorReason,
    ) : NovelSelectedTextTranslationUiState
}
