package eu.kanade.tachiyomi.ui.browse.novel.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.novel.interactor.GetEnabledNovelSources
import eu.kanade.domain.source.novel.interactor.ToggleNovelSource
import eu.kanade.domain.source.novel.interactor.ToggleNovelSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.novel.NovelSourceUiModel
import eu.kanade.tachiyomi.util.system.LAST_USED_KEY
import eu.kanade.tachiyomi.util.system.PINNED_KEY
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.novel.model.Pin
import tachiyomi.domain.source.novel.model.Source
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class NovelSourcesScreenModel(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getEnabledSources: GetEnabledNovelSources = Injekt.get(),
    private val toggleSource: ToggleNovelSource = Injekt.get(),
    private val togglePin: ToggleNovelSourcePin = Injekt.get(),
    // BRN-15: nullable + runCatching so DI-less unit tests can construct the SM (the gate is
    // then skipped - test flows emit real sources immediately).
    private val sourceManager: NovelSourceManager? = runCatching { Injekt.get<NovelSourceManager>() }.getOrNull(),
) : StateScreenModel<NovelSourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    // BRN-14: written on IO (collectLatest), read on main - @Volatile (see the manga SM).
    @Volatile
    private var rawSources: List<Source> = emptyList()

    init {
        screenModelScope.launchIO {
            // BRN-15: wait for manager initialization - the early EMPTY emission flipped
            // isLoading=false and cold start flashed "no sources" (manga etalon).
            sourceManager?.isInitialized?.first { it }
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
        sourcePreferences.verticalPinnedLayout().changes()
            .onEach { vertical ->
                updateState { state -> state.copy(verticalPinnedLayout = vertical) }
            }
            .launchIn(screenModelScope)
    }

    private fun updateState(transform: (State) -> State = { it }) {
        // BRN-14: compute INSIDE mutableState.update from one rawSources snapshot (the
        // cross-thread read/compute/write was last-writer-wins on stale inputs - manga etalon).
        val snapshot = rawSources
        mutableState.update { previous ->
            val current = transform(previous)
            val query = current.searchQuery
            val isQueryBlank = query.isBlank()
            val collapsed = current.collapsedLanguages
            val verticalLayout = current.verticalPinnedLayout

            val (pinned, others) = when {
                isQueryBlank && !verticalLayout -> snapshot.partition { Pin.Actual in it.pin }
                else -> Pair(emptyList(), snapshot)
            }

            val filtered = if (isQueryBlank) {
                others
            } else {
                others.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.lang.contains(query, ignoreCase = true)
                }
            }

            val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
                when {
                    d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                    d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                    d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                    d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byLang = filtered.groupByTo(map) {
                when {
                    verticalLayout && isQueryBlank && Pin.Actual in it.pin -> PINNED_KEY
                    it.isUsedLast -> LAST_USED_KEY
                    else -> it.lang
                }
            }

            val uiItems = buildList(filtered.size + byLang.size) {
                for ((lang, langSources) in byLang) {
                    val isCollapsed = lang in collapsed && isQueryBlank && lang != PINNED_KEY
                    add(NovelSourceUiModel.Header(lang, isCollapsed = isCollapsed))
                    if (!isCollapsed) {
                        for (source in langSources) {
                            add(NovelSourceUiModel.Item(source))
                        }
                    }
                }
            }

            current.copy(
                isLoading = false,
                items = uiItems.toImmutableList(),
                pinnedItems = pinned.toImmutableList(),
            )
        }
    }

    fun search(query: String) {
        updateState { it.copy(searchQuery = query) }
    }

    fun toggleLanguage(language: String) {
        updateState { state ->
            val newCollapsed = if (language in state.collapsedLanguages) {
                state.collapsedLanguages - language
            } else {
                state.collapsedLanguages + language
            }
            state.copy(collapsedLanguages = newCollapsed.toImmutableSet())
        }
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
        val verticalPinnedLayout: Boolean = false,
    ) {
        val isEmpty = items.isEmpty() && pinnedItems.isEmpty()
    }
}
