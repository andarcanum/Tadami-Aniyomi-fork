package eu.kanade.tachiyomi.ui.browse.manga.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.manga.interactor.ToggleExcludeFromMangaDataSaver
import eu.kanade.domain.source.manga.interactor.ToggleMangaSource
import eu.kanade.domain.source.manga.interactor.ToggleMangaSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.presentation.browse.manga.MangaSourceUiModel
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
import tachiyomi.domain.source.manga.model.Pin
import tachiyomi.domain.source.manga.model.Source
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class MangaSourcesScreenModel(
    private val preferences: BasePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getEnabledSources: GetEnabledMangaSources = Injekt.get(),
    private val toggleSource: ToggleMangaSource = Injekt.get(),
    private val toggleSourcePin: ToggleMangaSourcePin = Injekt.get(),
    private val sourceManager: MangaSourceManager? = runCatching { Injekt.get<MangaSourceManager>() }.getOrNull(),
    // SY -->
    private val toggleExcludeFromMangaDataSaver: ToggleExcludeFromMangaDataSaver = Injekt.get(),
    // SY <--
) : StateScreenModel<MangaSourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    // Keep track of raw sources to re-filter when query/collapsed state changes.
    // BRA-9/BRM-10: written on IO (collectLatest), read on main (search/toggle/prefs) -
    // @Volatile for cross-thread visibility.
    @Volatile
    private var rawSources: List<Source> = emptyList()

    init {
        screenModelScope.launchIO {
            // BRN-15: wait for the source manager to initialize before treating the first
            // emission as authoritative - the early EMPTY emission flipped isLoading=false and
            // a cold start flashed "no sources" instead of Loading.
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
        // SY -->
        sourcePreferences.dataSaver().changes()
            .onEach {
                mutableState.update {
                    it.copy(
                        dataSaverEnabled = sourcePreferences.dataSaver().get() != DataSaver.NONE,
                    )
                }
            }
            .launchIn(screenModelScope)
        // SY <--
        sourcePreferences.verticalPinnedLayout().changes()
            .onEach {
                mutableState.update { state -> state.copy(verticalPinnedLayout = it) }
                updateState()
            }
            .launchIn(screenModelScope)
    }

    private fun updateState() {
        // BRA-9/BRM-10: compute INSIDE mutableState.update from one rawSources snapshot. The
        // old read-state/compute/write-state sequence raced across threads (IO collectLatest vs
        // main search/toggle/prefs triggers): last-writer-wins on stale inputs could freeze the
        // rendered list until the next event.
        val sources = rawSources
        mutableState.update { current ->
            val query = current.searchQuery
            val collapsed = current.collapsedLanguages
            val verticalLayout = current.verticalPinnedLayout

            // 1. Separate Pinned (only if no search query AND not vertical layout)
            val (pinned, others) = when {
                query.isBlank() && !verticalLayout -> sources.partition { Pin.Actual in it.pin }
                else -> Pair(emptyList(), sources)
            }

            // 2. Filter by query
            val filtered = others.filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                    it.lang.contains(query, ignoreCase = true)
            }

            // 3. Group by Lang
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
                    verticalLayout && query.isBlank() && Pin.Actual in it.pin -> PINNED_KEY
                    it.isUsedLast -> LAST_USED_KEY
                    else -> it.lang
                }
            }

            // 4. Flatten to UI Models, respecting collapsed state
            val uiItems = byLang.flatMap { (lang, langSources) ->
                if (lang in collapsed && query.isBlank() && lang != PINNED_KEY) {
                    listOf(MangaSourceUiModel.Header(lang, isCollapsed = true))
                } else {
                    listOf(MangaSourceUiModel.Header(lang, isCollapsed = false)) +
                        langSources.map { MangaSourceUiModel.Item(it) }
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
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    // SY -->
    fun toggleExcludeFromMangaDataSaver(source: Source) {
        toggleExcludeFromMangaDataSaver.await(source)
    }
    // SY <--

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
        val items: ImmutableList<MangaSourceUiModel> = persistentListOf(),
        val pinnedItems: ImmutableList<Source> = persistentListOf(),
        val searchQuery: String = "",
        val collapsedLanguages: ImmutableSet<String> = persistentSetOf(),
        // SY -->
        val dataSaverEnabled: Boolean = false,
        // SY <--
        val verticalPinnedLayout: Boolean = false,
    ) {
        val isEmpty = items.isEmpty() && pinnedItems.isEmpty()
    }
}
