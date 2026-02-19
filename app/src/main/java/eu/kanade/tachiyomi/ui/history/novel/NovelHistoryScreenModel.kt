package eu.kanade.tachiyomi.ui.history.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.presentation.history.novel.NovelHistoryUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelHistoryScreenModel(
    private val historyRepository: NovelHistoryRepository = Injekt.get(),
) : StateScreenModel<NovelHistoryScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            historyRepository.getNovelHistory("")
                .catch { error ->
                    logcat(LogPriority.ERROR, error)
                    _events.send(Event.InternalError)
                }
                .map { it.toHistoryUiModels() }
                .flowOn(Dispatchers.IO)
                .collectLatest { newList ->
                    mutableState.update { it.copy(list = newList) }
                }
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
        mutableState.update { it.copy(dialog = dialog) }
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
