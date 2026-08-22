package eu.kanade.tachiyomi.ui.library.novel.quotes

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tachiyomi.domain.book.novel.interactor.DeleteNovelHighlight
import tachiyomi.domain.book.novel.interactor.UpdateNovelHighlight
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import tachiyomi.domain.book.novel.repository.NovelHighlightRepository
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Immutable
data class NovelQuotesLibraryState(
    val quotes: List<NovelHighlightWithChapter> = emptyList(),
    val query: String = "",
    val bookFilter: String? = null,
    val sortMode: NovelQuotesSortMode = NovelQuotesSortMode.DATE,
)

class NovelQuotesLibraryScreenModel(
    private val updateNovelHighlight: UpdateNovelHighlight = Injekt.get(),
    private val deleteNovelHighlight: DeleteNovelHighlight = Injekt.get(),
) : ScreenModel {

    private val repository: NovelHighlightRepository = Injekt.get()
    private val preferences: LibraryPreferences = Injekt.get()

    private val query = MutableStateFlow("")
    private val bookFilter = MutableStateFlow<String?>(null)

    val state: StateFlow<NovelQuotesLibraryState> = combine(
        repository.subscribeAll(),
        preferences.novelQuotesSortMode().changes(),
        query,
        bookFilter,
    ) { quotes, sortRaw, q, filter ->
        NovelQuotesLibraryState(
            quotes = NovelQuotesListOps.visible(quotes),
            query = q,
            bookFilter = filter,
            sortMode = NovelQuotesSortMode.from(sortRaw),
        )
    }.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NovelQuotesLibraryState(),
    )

    fun search(query: String) {
        this.query.value = query
    }

    fun selectBook(title: String?) {
        bookFilter.value = title
    }

    fun toggleSort() {
        val next = if (state.value.sortMode == NovelQuotesSortMode.DATE) {
            NovelQuotesSortMode.TITLE
        } else {
            NovelQuotesSortMode.DATE
        }
        preferences.novelQuotesSortMode().set(next.storageKey)
    }

    fun updateQuote(highlightId: Long, note: String, colorArgb: Long) {
        screenModelScope.launch {
            updateNovelHighlight.await(
                highlightId = highlightId,
                note = note,
                colorArgb = colorArgb,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun deleteQuote(highlightId: Long) {
        screenModelScope.launch {
            deleteNovelHighlight.await(highlightId)
        }
    }
}
