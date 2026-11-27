package eu.kanade.tachiyomi.ui.history.anime

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.presentation.history.anime.AnimeHistoryUiModel
import eu.kanade.tachiyomi.ui.library.leadingDebounce
import eu.kanade.tachiyomi.util.lang.buildDateGroupedUiModels
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeHistoryScreenModel(
    private val addTracks: AddAnimeTracks = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val getDuplicateLibraryAnime: GetDuplicateLibraryAnime = Injekt.get(),
    private val getHistory: GetAnimeHistory = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val removeHistory: RemoveAnimeHistory = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) : ScreenModel {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val _query: MutableStateFlow<String?> = MutableStateFlow(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    private val searchQueryState = MutableStateFlow<String?>(null)
    private val dialogState = MutableStateFlow<Dialog?>(null)

    val state: StateFlow<State> = combine(
        _query
            .leadingDebounce(SEARCH_DEBOUNCE_MILLIS)
            .flatMapLatest { query ->
                getHistory.subscribe(query ?: "")
                    .distinctUntilChanged()
                    .catch { error ->
                        logcat(LogPriority.ERROR, error)
                        _events.send(Event.InternalError)
                    }
                    .map { it.toAnimeHistoryUiModels() }
                    .flowOn(Dispatchers.IO)
            },
        dialogState,
        searchQueryState,
    ) { list, dialog, searchQuery ->
        State(
            searchQuery = searchQuery,
            list = list,
            dialog = dialog,
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State())

    fun search(query: String?) {
        screenModelScope.launchIO {
            _query.emit(query)
        }
    }

    private fun List<AnimeHistoryWithRelations>.toAnimeHistoryUiModels(): List<AnimeHistoryUiModel> {
        return buildDateGroupedUiModels(
            items = this,
            timestampMillis = { it.seenAt?.time },
            header = AnimeHistoryUiModel::Header,
            item = AnimeHistoryUiModel::Item,
        )
    }

    suspend fun getNextEpisode(): Episode? {
        return withIOContext { getNextEpisodes.await(onlyUnseen = false).firstOrNull() }
    }

    fun getNextEpisodeForAnime(animeId: Long, episodeId: Long) {
        screenModelScope.launchIO {
            sendNextEpisodeEvent(getNextEpisodes.await(animeId, episodeId, onlyUnseen = false))
        }
    }

    private suspend fun sendNextEpisodeEvent(episodes: List<Episode>) {
        val episode = episodes.firstOrNull()
        _events.send(Event.OpenEpisode(episode))
    }

    fun removeFromHistory(history: AnimeHistoryWithRelations) {
        screenModelScope.launchIO {
            removeHistory.await(history)
        }
    }

    fun removeAllFromHistory(animeId: Long) {
        screenModelScope.launchIO {
            removeHistory.await(animeId)
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val result = removeHistory.awaitAll()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    fun setDialog(dialog: Dialog?) {
        dialogState.update { dialog }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    private fun moveAnimeToCategory(animeId: Long, categories: Category?) {
        val categoryIds = listOfNotNull(categories).map { it.id }
        moveAnimeToCategory(animeId, categoryIds)
    }

    private fun moveAnimeToCategory(animeId: Long, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setAnimeCategories.await(animeId, categoryIds)
        }
    }

    fun moveAnimeToCategoriesAndAddToLibrary(anime: Anime, categories: List<Long>) {
        moveAnimeToCategory(anime.id, categories)
        if (anime.favorite) return

        screenModelScope.launchIO {
            updateAnime.awaitUpdateFavorite(anime.id, true)
        }
    }

    private suspend fun getAnimeCategoryIds(anime: Anime): List<Long> {
        return getCategories.await(anime.id)
            .map { it.id }
    }

    fun addFavorite(animeId: Long) {
        screenModelScope.launchIO {
            val anime = getAnime.await(animeId) ?: return@launchIO

            val duplicate = getDuplicateLibraryAnime.await(anime).getOrNull(0)
            if (duplicate != null) {
                dialogState.update { Dialog.DuplicateAnime(anime, duplicate) }
                return@launchIO
            }

            addFavorite(anime)
        }
    }

    fun addFavorite(anime: Anime) {
        screenModelScope.launchIO {
            // Move to default category if applicable
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultAnimeCategory().get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    val result = updateAnime.awaitUpdateFavorite(anime.id, true)
                    if (!result) return@launchIO
                    moveAnimeToCategory(anime.id, defaultCategory)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0L || categories.isEmpty() -> {
                    val result = updateAnime.awaitUpdateFavorite(anime.id, true)
                    if (!result) return@launchIO
                    moveAnimeToCategory(anime.id, null)
                }

                // Choose a category
                else -> showChangeCategoryDialog(anime)
            }

            // Sync with tracking services if applicable
            addTracks.bindEnhancedTrackers(anime, sourceManager.getOrStub(anime.source))
        }
    }

    fun showMigrateDialog(currentAnime: Anime, duplicate: Anime) {
        dialogState.update { Dialog.Migrate(newAnime = currentAnime, oldAnime = duplicate) }
    }

    fun showChangeCategoryDialog(anime: Anime) {
        screenModelScope.launch {
            val categories = getCategories()
            val selection = getAnimeCategoryIds(anime)
            dialogState.update {
                Dialog.ChangeCategory(
                    anime = anime,
                    initialSelection = categories.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                )
            }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: List<AnimeHistoryUiModel>? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val history: AnimeHistoryWithRelations) : Dialog
        data class DuplicateAnime(val anime: Anime, val duplicate: Anime) : Dialog
        data class ChangeCategory(
            val anime: Anime,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class Migrate(val newAnime: Anime, val oldAnime: Anime) : Dialog
    }

    sealed interface Event {
        data class OpenEpisode(val episode: Episode?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}

private const val SEARCH_DEBOUNCE_MILLIS = 200L
