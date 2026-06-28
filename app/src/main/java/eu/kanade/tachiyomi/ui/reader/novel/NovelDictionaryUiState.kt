package eu.kanade.tachiyomi.ui.reader.novel

sealed interface NovelDictionaryUiState {
    data object Idle : NovelDictionaryUiState

    data class Looking(
        val selection: NovelSelectedTextSelection,
    ) : NovelDictionaryUiState

    data class Result(
        val selection: NovelSelectedTextSelection,
        val result: NovelDictionaryResult,
    ) : NovelDictionaryUiState

    data class Error(
        val selection: NovelSelectedTextSelection,
        val reason: NovelSelectedTextTranslationErrorReason,
    ) : NovelDictionaryUiState

    data class Unavailable(
        val reason: NovelSelectedTextTranslationErrorReason,
    ) : NovelDictionaryUiState
}
