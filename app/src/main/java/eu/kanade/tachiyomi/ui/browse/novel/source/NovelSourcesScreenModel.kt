package eu.kanade.tachiyomi.ui.browse.novel.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.novel.interactor.GetEnabledNovelSources
import eu.kanade.domain.source.novel.interactor.ToggleNovelSource
import eu.kanade.domain.source.novel.interactor.ToggleNovelSourcePin
import eu.kanade.presentation.browse.novel.NovelSourceUiModel
import eu.kanade.tachiyomi.util.system.LAST_USED_KEY
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.novel.model.Pin
import tachiyomi.domain.source.novel.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class NovelSourcesScreenModel(
    private val getEnabledSources: GetEnabledNovelSources = Injekt.get(),
    private val toggleSource: ToggleNovelSource = Injekt.get(),
    private val togglePin: ToggleNovelSourcePin = Injekt.get(),
) : StateScreenModel<NovelSourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    private var rawSources: List<Source> = emptyList()

    init {
        screenModelScope.launchIO {
            getEnabledSources.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.FailedFetchingSources)
                }
                .collectLatest { sources ->
                    rawSources = sources
                    updateState()
                }
        }
    }

    private fun updateState() {
        val query = state.value.searchQuery
        val collapsed = state.value.collapsedLanguages

        val (pinned, others) = if (query.isBlank()) {
            rawSources.partition { Pin.Actual in it.pin }
        } else {
            Pair(emptyList(), rawSources)
        }

        val filtered = others.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) || it.lang.contains(query, ignoreCase = true)
        }

        val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
            when {
                d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                d1 == "" && d2 != "" -> 1
                d2 == "" && d1 != "" -> -1
                else -> d1.compareTo(d2)
            }
        }
        val byLang = filtered.groupByTo(map) {
            when {
                it.isUsedLast -> LAST_USED_KEY
                else -> it.lang
            }
        }

        val uiItems = byLang.flatMap { (lang, sources) ->
            if (lang in collapsed && query.isBlank()) {
                listOf(NovelSourceUiModel.Header(lang, isCollapsed = true))
            } else {
                listOf(NovelSourceUiModel.Header(lang, isCollapsed = false)) +
                    sources.map { NovelSourceUiModel.Item(it) }
            }
        }

        mutableState.update {
            it.copy(
                isLoading = false,
                items = uiItems.toImmutableList(),
                pinnedItems = pinned.toImmutableList(),
            )
        }
    }

    fun search(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
        updateState()
    }

    fun toggleLanguage(language: String) {
        mutableState.update { state ->
            val newCollapsed = if (language in state.collapsedLanguages) {
                state.collapsedLanguages - language
            } else {
                state.collapsedLanguages + language
            }
            state.copy(collapsedLanguages = newCollapsed.toImmutableSet())
        }
        updateState()
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source.id)
    }

    fun togglePin(source: Source) {
        togglePin.await(source)
    }

    fun showSourceDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    data class Dialog(val source: Source)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: ImmutableList<NovelSourceUiModel> = persistentListOf(),
        val pinnedItems: ImmutableList<Source> = persistentListOf(),
        val searchQuery: String = "",
        val collapsedLanguages: ImmutableSet<String> = persistentSetOf(),
    ) {
        val isEmpty = items.isEmpty() && pinnedItems.isEmpty()
    }
}
