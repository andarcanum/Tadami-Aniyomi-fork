package eu.kanade.tachiyomi.ui.updates.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.presentation.updates.novel.NovelUpdatesUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.model.AchievementEvent
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
) : StateScreenModel<NovelUpdatesScreenModel.State>(State()) {

    val lastUpdated = libraryPreferences.lastUpdatedTimestamp().get()
    private val selectedChapterIds = hashSetOf<Long>()

    init {
        screenModelScope.launchIO {
            val limit = ZonedDateTime.now().minusMonths(3).toInstant()
            getUpdates.subscribe(limit)
                .distinctUntilChanged()
                .catch { logcat(LogPriority.ERROR, it) }
                .collectLatest { updates ->
                    mutableState.value = State(
                        isLoading = false,
                        items = updates
                            .map { update ->
                                NovelUpdatesItem(
                                    update = update,
                                    selected = selectedChapterIds.contains(update.chapterId),
                                )
                            }
                            .toPersistentList(),
                    )
                }
        }
    }

    fun toggleSelection(item: NovelUpdatesItem, selected: Boolean) {
        mutableState.value = mutableState.value.copy(
            items = mutableState.value.items.map {
                if (it.update.chapterId == item.update.chapterId) {
                    selectedChapterIds.addOrRemove(item.update.chapterId, selected)
                    it.copy(selected = selected)
                } else {
                    it
                }
            }.toPersistentList(),
        )
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.value = mutableState.value.copy(
            items = mutableState.value.items.map {
                selectedChapterIds.addOrRemove(it.update.chapterId, selected)
                it.copy(selected = selected)
            }.toPersistentList(),
        )
    }

    fun invertSelection() {
        mutableState.value = mutableState.value.copy(
            items = mutableState.value.items.map {
                selectedChapterIds.addOrRemove(it.update.chapterId, !it.selected)
                it.copy(selected = !it.selected)
            }.toPersistentList(),
        )
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
