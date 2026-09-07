package eu.kanade.tachiyomi.ui.updates.novel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.presentation.updates.novel.NovelUpdatesUiModel
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.updates.novel.interactor.GetNovelUpdates
import tachiyomi.domain.updates.novel.model.NovelUpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime

class NovelUpdatesScreenModel(
    private val getUpdates: GetNovelUpdates = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val chapterRepository: NovelChapterRepository = Injekt.get(),
    private val eventBus: AchievementEventBus? = runCatching { Injekt.get<AchievementEventBus>() }.getOrNull(),
) : ScreenModel {

    val lastUpdated by libraryPreferences.lastUpdatedTimestamp().asState(screenModelScope)
    private val limit = ZonedDateTime.now().minusMonths(3).toInstant()
    private val selectedChapterIds = MutableStateFlow<Set<Long>>(emptySet())

    // Mini-fix: parity with manga/anime updates SMs - the toolbar/swipe refresh used to be
    // silent when the update job was already running.
    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events: Flow<Event> = _events.receiveAsFlow()

    val state: StateFlow<State> = combine(
        getUpdates.subscribe(limit)
            .distinctUntilChanged()
            .catch { logcat(LogPriority.ERROR, it) },
        selectedChapterIds,
    ) { updates, selectedChapterIds ->
        State(
            isLoading = false,
            items = updates
                .filter { !it.read }
                .map { update ->
                    NovelUpdatesItem(
                        update = update,
                        selected = selectedChapterIds.contains(update.chapterId),
                    )
                }
                .toPersistentList(),
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State())

    fun toggleSelection(item: NovelUpdatesItem, selected: Boolean) {
        selectedChapterIds.update { ids ->
            if (selected) ids + item.update.chapterId else ids - item.update.chapterId
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        selectedChapterIds.update { ids ->
            val current = state.value.items.map { it.update.chapterId }
            if (selected) ids + current else ids - current
        }
    }

    fun invertSelection() {
        selectedChapterIds.update { ids ->
            val current = state.value.items.map { it.update.chapterId }
            (ids - current) + (current - ids)
        }
    }

    fun markUpdatesRead(updates: List<NovelUpdatesItem>, read: Boolean) {
        val updatesBecomingRead = if (read) updates.filter { !it.update.read } else emptyList()
        screenModelScope.launchIO {
            chapterRepository.updateAllChapters(
                updates.map {
                    NovelChapterUpdate(
                        id = it.update.chapterId,
                        read = read,
                        lastPageRead = if (read) 0L else it.update.lastPageRead,
                    )
                },
            )
            updatesBecomingRead.forEach {
                eventBus?.tryEmit(
                    AchievementEvent.NovelChapterRead(
                        novelId = it.update.novelId,
                        chapterNumber = 0,
                    ),
                )
            }
            toggleAllSelection(false)
        }
    }

    fun bookmarkUpdates(updates: List<NovelUpdatesItem>, bookmark: Boolean) {
        screenModelScope.launchIO {
            chapterRepository.updateAllChapters(
                updates.map {
                    NovelChapterUpdate(
                        id = it.update.chapterId,
                        bookmark = bookmark,
                    )
                },
            )
            toggleAllSelection(false)
        }
    }

    // I15: startNow is suspend (blocking WM guard) - keep the public API fire-and-forget and
    // hop to IO inside instead of pushing suspend onto every toolbar caller.
    fun updateLibrary() {
        screenModelScope.launchIO {
            val started = NovelLibraryUpdateJob.startNow(Injekt.get<Application>())
            _events.send(Event.LibraryUpdateTriggered(started))
        }
    }

    sealed interface Event {
        data class LibraryUpdateTriggered(val started: Boolean) : Event
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: PersistentList<NovelUpdatesItem> = persistentListOf(),
    ) {
        val selected = items.filter { it.selected }
        val selectionMode = selected.isNotEmpty()

        fun getUiModel(): List<NovelUpdatesUiModel> {
            return items
                .map { NovelUpdatesUiModel.Item(it) }
                .insertSeparators { before, after ->
                    val beforeDate = before?.item?.update?.dateFetch?.toLocalDate()
                    val afterDate = after?.item?.update?.dateFetch?.toLocalDate()
                    when {
                        beforeDate != afterDate && afterDate != null -> NovelUpdatesUiModel.Header(afterDate)
                        else -> null
                    }
                }
        }
    }
}

@Immutable
data class NovelUpdatesItem(
    val update: NovelUpdatesWithRelations,
    val selected: Boolean = false,
)
