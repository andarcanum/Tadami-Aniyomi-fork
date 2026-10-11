package eu.kanade.tachiyomi.ui.history.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.entries.novel.LocalNovelVisibility
import eu.kanade.presentation.history.novel.NovelHistoryUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
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
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.source.local.io.novel.LocalNovelSourceFileSystem
import tachiyomi.source.local.io.novel.hasSupportedLocalNovelContent
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelHistoryScreenModel(
    private val historyRepository: NovelHistoryRepository = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val localNovelSourceFileSystem: LocalNovelSourceFileSystem = Injekt.get(),
) : ScreenModel {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val _query: MutableStateFlow<String?> = MutableStateFlow(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    private val searchQueryState = MutableStateFlow<String?>(null)
    private val dialogState = MutableStateFlow<Dialog?>(null)

    val state: StateFlow<State> = combine(
        _query.flatMapLatest { query ->
            historyRepository.getNovelHistory(query ?: "")
                .catch { error ->
                    logcat(LogPriority.ERROR, error)
                    _events.send(Event.InternalError)
                }
                .distinctUntilChanged()
                .map { items -> items.filterVisibleLocalNovels().toHistoryUiModels() }
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
    }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State())

    fun search(query: String?) {
        screenModelScope.launchIO {
            _query.emit(query)
        }
    }

    private suspend fun List<NovelHistoryWithRelations>.filterVisibleLocalNovels(): List<NovelHistoryWithRelations> {
        if (isEmpty()) return this
        val localItems = filter { LocalNovelVisibility.isLocalSource(it.coverData.sourceId) }
        if (localItems.isEmpty()) return this

        val urlByNovelId = HashMap<Long, String?>(localItems.size)
        for (item in localItems) {
            if (item.novelId in urlByNovelId) continue
            if (LocalNovelVisibility.shouldHideLocalHistoryByCover(item.coverData.sourceId, item.coverData.url)) {
                urlByNovelId[item.novelId] = null
                continue
            }
            urlByNovelId[item.novelId] = getNovel.await(item.novelId)?.url
        }

        return filter { item ->
            if (!LocalNovelVisibility.isLocalSource(item.coverData.sourceId)) return@filter true
            LocalNovelVisibility.shouldShowLocalNovelEntry(
                sourceId = item.coverData.sourceId,
                url = urlByNovelId[item.novelId],
                coverUrl = item.coverData.url,
                hasSupportedContent = localNovelSourceFileSystem::hasSupportedLocalNovelContent,
            )
        }
    }

    private fun List<NovelHistoryWithRelations>.toHistoryUiModels(): List<NovelHistoryUiModel> {
        return map { NovelHistoryUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.readAt?.time?.toLocalDate()
                val afterDate = after?.item?.readAt?.time?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> NovelHistoryUiModel.Header(afterDate)
                    else -> null
                }
            }
    }

    fun setDialog(dialog: Dialog?) {
        dialogState.update { dialog }
    }

    fun updateSearchQuery(query: String?) {
        searchQueryState.update { query }
    }

    fun removeFromHistory(history: NovelHistoryWithRelations) {
        screenModelScope.launchIO {
            historyRepository.resetNovelHistory(history.id)
        }
    }

    fun removeAllFromHistory(novelId: Long) {
        screenModelScope.launchIO {
            historyRepository.getHistoryByNovelId(novelId)
                .forEach { historyRepository.resetNovelHistory(it.id) }
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val result = historyRepository.deleteAllNovelHistory()
            if (result) {
                _events.send(Event.HistoryCleared)
            }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: List<NovelHistoryUiModel>? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val history: NovelHistoryWithRelations) : Dialog
    }

    sealed interface Event {
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}
