package eu.kanade.tachiyomi.ui.browse.anime.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.anime.AnimeSourceUiModel
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
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class AnimeSourcesScreenModel(
    private val preferences: BasePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getEnabledAnimeSources: GetEnabledAnimeSources = Injekt.get(),
    private val toggleSource: ToggleAnimeSource = Injekt.get(),
    private val toggleSourcePin: ToggleAnimeSourcePin = Injekt.get(),
) : StateScreenModel<AnimeSourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    // Keep track of raw sources to re-filter when query/collapsed state changes
    private var rawSources: List<AnimeSource> = emptyList()

    init {
        screenModelScope.launchIO {
            getEnabledAnimeSources.subscribe()
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

        // 1. Separate Pinned (only if no search query)
        val (pinned, others) = if (query.isBlank()) {
            rawSources.partition { Pin.Actual in it.pin }
        } else {
            // When searching, show everything in the list, nothing in pinned carousel
            Pair(emptyList(), rawSources)
        }

        // 2. Filter by query
        val filtered = others.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) || it.lang.contains(query, ignoreCase = true)
        }

        // 3. Group by Lang
        val map = TreeMap<String, MutableList<AnimeSource>> { d1, d2 ->
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

        // 4. Flatten to UI Models, respecting collapsed state
        val uiItems = byLang.flatMap { (lang, sources) ->
            if (lang in collapsed && query.isBlank()) {
                listOf(AnimeSourceUiModel.Header(lang, isCollapsed = true))
            } else {
                listOf(AnimeSourceUiModel.Header(lang, isCollapsed = false)) +
                    sources.map { AnimeSourceUiModel.Item(it) }
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

    fun toggleSource(source: AnimeSource) {
        toggleSource.await(source)
    }

    fun togglePin(source: AnimeSource) {
        toggleSourcePin.await(source)
    }

    fun showSourceDialog(source: AnimeSource) {
        mutableState.update { it.copy(dialog = Dialog(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    data class Dialog(val source: AnimeSource)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: ImmutableList<AnimeSourceUiModel> = persistentListOf(),
        val pinnedItems: ImmutableList<AnimeSource> = persistentListOf(),
        val searchQuery: String = "",
        val collapsedLanguages: ImmutableSet<String> = persistentSetOf(),
    ) {
        val isEmpty = items.isEmpty() && pinnedItems.isEmpty()
    }
}
