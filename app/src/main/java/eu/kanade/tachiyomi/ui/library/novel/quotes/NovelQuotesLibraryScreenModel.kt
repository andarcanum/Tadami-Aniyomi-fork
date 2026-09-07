package eu.kanade.tachiyomi.ui.library.novel.quotes

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.ui.library.leadingDebounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchNonCancellable
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
        // Quotes-F5: the raw query used to hit this combine synchronously per keystroke and
        // the whole-list visible() pass ran on the main thread; debounce (with a leading
        // emission so the first render is not delayed) and move the work off-main via flowOn.
        query.leadingDebounce(SEARCH_DEBOUNCE_MILLIS),
        bookFilter,
    ) { quotes, sortRaw, q, filter ->
        NovelQuotesLibraryState(
            quotes = NovelQuotesListOps.visible(quotes),
            query = q,
            bookFilter = filter,
            sortMode = NovelQuotesSortMode.from(sortRaw),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
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
        // D5: compute the next mode from a synchronous pref read - state.value is an async
        // mirror, so a fast double tap read the stale mode and lost one toggle.
        preferences.novelQuotesSortMode().getAndSet { raw ->
            val next = if (NovelQuotesSortMode.from(raw) == NovelQuotesSortMode.DATE) {
                NovelQuotesSortMode.TITLE
            } else {
                NovelQuotesSortMode.DATE
            }
            next.storageKey
        }
    }

    fun updateQuote(highlightId: Long, note: String, colorArgb: Long) {
        // Quotes-F11: non-cancellable - back-navigation right after confirming used to drop
        // the DB write silently.
        screenModelScope.launchNonCancellable {
            updateNovelHighlight.await(
                highlightId = highlightId,
                note = note,
                colorArgb = colorArgb,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun deleteQuote(highlightId: Long) {
        screenModelScope.launchNonCancellable {
            deleteNovelHighlight.await(highlightId)
        }
    }
}
