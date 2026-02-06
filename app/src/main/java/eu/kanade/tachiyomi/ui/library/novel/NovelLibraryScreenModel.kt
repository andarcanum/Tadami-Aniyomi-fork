package eu.kanade.tachiyomi.ui.library.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelLibraryScreenModel(
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
) : StateScreenModel<NovelLibraryScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            getLibraryNovel.subscribe()
                .collectLatest { novels ->
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            rawItems = novels,
                            items = filterItems(novels, current.searchQuery),
                        )
                    }
                }
        }
    }

    fun search(query: String?) {
        mutableState.update { current ->
            val trimmed = query?.trim().orEmpty().ifBlank { null }
            current.copy(
                searchQuery = trimmed,
                items = filterItems(current.rawItems, trimmed),
            )
        }
    }

    private fun filterItems(
        items: List<LibraryNovel>,
        query: String?,
    ): List<LibraryNovel> {
        if (query.isNullOrBlank()) return items
        return items.filter { it.novel.title.contains(query, ignoreCase = true) }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val rawItems: List<LibraryNovel> = emptyList(),
        val items: List<LibraryNovel> = emptyList(),
        val searchQuery: String? = null,
    ) {
        val isLibraryEmpty: Boolean
            get() = rawItems.isEmpty()
    }
}
