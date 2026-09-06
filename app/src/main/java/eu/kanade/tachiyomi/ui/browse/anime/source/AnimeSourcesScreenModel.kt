package eu.kanade.tachiyomi.ui.browse.anime.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.anime.AnimeSourceUiModel
import eu.kanade.tachiyomi.util.system.LAST_USED_KEY
import eu.kanade.tachiyomi.util.system.PINNED_KEY
import eu.kanade.tachiyomi.util.system.REELS_KEY
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
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class AnimeSourcesScreenModel(
    // РЕШ-B5: the BasePreferences parameter was never read (dead DI dependency).
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val getEnabledAnimeSources: GetEnabledAnimeSources = Injekt.get(),
    private val toggleSource: ToggleAnimeSource = Injekt.get(),
    private val toggleSourcePin: ToggleAnimeSourcePin = Injekt.get(),
    private val sourceManager: AnimeSourceManager? = runCatching { Injekt.get<AnimeSourceManager>() }.getOrNull(),
) : StateScreenModel<AnimeSourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    // Keep track of raw sources to re-filter when query/collapsed state changes.
    // BRA-9: written on IO (collectLatest), read on main - @Volatile (see the manga SM).
    @Volatile
    private var rawSources: List<AnimeSource> = emptyList()

    init {
        screenModelScope.launchIO {
            // BRN-15: wait for manager initialization - the early EMPTY emission flipped
            // isLoading=false and cold start flashed "no sources" (manga etalon).
            sourceManager?.isInitialized?.first { it }
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
        sourcePreferences.verticalPinnedLayout().changes()
            .onEach {
                mutableState.update { state -> state.copy(verticalPinnedLayout = it) }
                updateState()
            }
            .launchIn(screenModelScope)
        uiPreferences.showReelsVideoFeed().changes()
            .onEach {
                updateState()
            }
            .launchIn(screenModelScope)
    }

    private fun updateState() {
        // BRA-9/BRM-10: compute INSIDE mutableState.update from one rawSources snapshot (the
        // cross-thread read/compute/write was last-writer-wins on stale inputs - manga etalon).
        val sources = rawSources
        mutableState.update { current ->
            val query = current.searchQuery
            val collapsed = current.collapsedLanguages
            val verticalLayout = current.verticalPinnedLayout
            val showReels = uiPreferences.showReelsVideoFeed().get()

            // 1. Separate Pinned (only if no search query AND not vertical layout)
            val (pinned, others) = when {
                query.isBlank() && !verticalLayout -> {
                    val pinnedList = sources.filter { !it.isFeedSource && Pin.Actual in it.pin }
                    val othersList = sources.filter { it.isFeedSource || Pin.Actual !in it.pin }
                    Pair(pinnedList, othersList)
                }
                else -> Pair(emptyList(), sources)
            }

            // 2. Filter by query and reels preference
            val filtered = others.filter { source ->
                if (source.isFeedSource && !showReels) return@filter false
                query.isBlank() || source.name.contains(query, ignoreCase = true) ||
                    source.lang.contains(query, ignoreCase = true)
            }

            // 3. Group by Lang
            val map = TreeMap<String, MutableList<AnimeSource>> { d1, d2 ->
                when {
                    d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                    d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                    d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                    d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                    d1 == REELS_KEY && d2 != REELS_KEY -> -1
                    d2 == REELS_KEY && d1 != REELS_KEY -> 1
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byLang = filtered.groupByTo(map) {
                when {
                    it.isFeedSource -> REELS_KEY
                    verticalLayout && query.isBlank() && Pin.Actual in it.pin -> PINNED_KEY
                    it.isUsedLast -> LAST_USED_KEY
                    else -> it.lang
                }
            }

            // 4. Flatten to UI Models, respecting collapsed state
            val uiItems = byLang.flatMap { (lang, langSources) ->
                if (lang in collapsed && query.isBlank() && lang != PINNED_KEY) {
                    listOf(AnimeSourceUiModel.Header(lang, isCollapsed = true))
                } else {
                    listOf(AnimeSourceUiModel.Header(lang, isCollapsed = false)) +
                        langSources.map { AnimeSourceUiModel.Item(it) }
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

    fun toggleSource(source: AnimeSource) {
        toggleSource.await(source)
    }

    fun togglePin(source: AnimeSource) {
        // Feed (Reels) sources are always grouped under the fixed REELS key; pinning one is
        // a no-op (and would drop it from the pinned grid in vertical layout), so ignore it.
        if (source.isFeedSource) return
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
        val verticalPinnedLayout: Boolean = false,
    ) {
        val isEmpty = items.isEmpty() && pinnedItems.isEmpty()
    }
}
