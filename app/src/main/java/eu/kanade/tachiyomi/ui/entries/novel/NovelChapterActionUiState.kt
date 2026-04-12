package eu.kanade.tachiyomi.ui.entries.novel

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat

@Immutable
data class NovelChapterActionUiState(
    val showGeminiRow: Boolean,
    val translateState: NovelChapterActionIconState,
    val downloadTranslatedState: NovelChapterActionIconState,
    val translatedDownloadFormat: NovelTranslatedDownloadFormat,
)

enum class NovelChapterActionIconState {
    Hidden,
    Neutral,
    Active,
    InProgress,
}

object NovelChapterActionStateResolver {
    fun resolve(
        geminiEnabled: Boolean,
        hasTranslationCache: Boolean,
        isTranslating: Boolean,
        isTranslatedDownloaded: Boolean,
        isTranslatedDownloading: Boolean,
        translatedDownloadFormat: NovelTranslatedDownloadFormat = NovelTranslatedDownloadFormat.TXT,
    ): NovelChapterActionUiState {
        val showGeminiRow = geminiEnabled
        val translateState = when {
            !showGeminiRow -> NovelChapterActionIconState.Hidden
            isTranslating -> NovelChapterActionIconState.InProgress
            hasTranslationCache -> NovelChapterActionIconState.Active
            else -> NovelChapterActionIconState.Neutral
        }
        val downloadTranslatedState = when {
            !showGeminiRow -> NovelChapterActionIconState.Hidden
            isTranslatedDownloading -> NovelChapterActionIconState.InProgress
            isTranslatedDownloaded -> NovelChapterActionIconState.Active
            else -> NovelChapterActionIconState.Neutral
        }

        return NovelChapterActionUiState(
            showGeminiRow = showGeminiRow,
            translateState = translateState,
            downloadTranslatedState = downloadTranslatedState,
            translatedDownloadFormat = translatedDownloadFormat,
        )
    }
}
