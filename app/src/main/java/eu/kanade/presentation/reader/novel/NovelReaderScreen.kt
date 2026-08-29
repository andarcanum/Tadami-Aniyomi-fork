package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekRequest
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSpine
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookWindowState
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter

@Composable
fun NovelReaderScreen(
    rawState: NovelReaderScreenModel.State.Success,
    showReaderUi: Boolean,
    bookEngineSpine: NovelBookSpine = NovelBookSpine.EMPTY,
    bookInitialLocation: NovelBookLocation = NovelBookLocation.START,
    bookSeekRequest: BookSeekRequest? = null,
    bookWindow: NovelBookWindowState = NovelBookWindowState.EMPTY,
    chapterHighlights: Flow<List<NovelHighlight>> = emptyFlow(),
    highlightItems: Flow<List<NovelHighlightWithChapter>> = emptyFlow(),
    defaultHighlightColor: Long = 0L,
    actions: NovelReaderScreenActions,
) {
    NovelReaderContentHost(
        rawState = rawState,
        showReaderUi = showReaderUi,
        bookEngineSpine = bookEngineSpine,
        bookInitialLocation = bookInitialLocation,
        bookSeekRequest = bookSeekRequest,
        bookWindow = bookWindow,
        chapterHighlights = chapterHighlights,
        highlightItems = highlightItems,
        defaultHighlightColor = defaultHighlightColor,
        actions = actions,
    )
}
