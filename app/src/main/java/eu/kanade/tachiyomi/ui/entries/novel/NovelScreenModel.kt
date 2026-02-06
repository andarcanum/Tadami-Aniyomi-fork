package eu.kanade.tachiyomi.ui.entries.novel

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.items.novelchapter.service.getNovelChapterSort
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

class NovelScreenModel(
    private val lifecycle: Lifecycle,
    private val novelId: Long,
    private val getNovelWithChapters: GetNovelWithChapters = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val syncNovelChaptersWithSource: SyncNovelChaptersWithSource = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val setNovelChapterFlags: SetNovelChapterFlags = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<NovelScreenModel.State>(State.Loading) {

    private val successState: State.Success?
        get() = state.value as? State.Success

    val novel: Novel?
        get() = successState?.novel

    val source: NovelSource?
        get() = successState?.source

    val isAnyChapterSelected: Boolean
        get() = successState?.selectedChapterIds?.isNotEmpty() ?: false

    fun getNextUnreadChapter(): NovelChapter? {
        val state = successState ?: return null
        val chapters = state.processedChapters
        return if (state.novel.sortDescending()) {
            chapters.findLast { !it.read }
        } else {
            chapters.find { !it.read }
        }
    }

    init {
        screenModelScope.launchIO {
            getNovelWithChapters.subscribe(novelId)
                .distinctUntilChanged()
                .collectLatest { (novel, chapters) ->
                    updateSuccessState {
                        it.copy(
                            novel = novel,
                            chapters = chapters,
                            selectedChapterIds = it.selectedChapterIds.intersect(chapters.mapTo(mutableSetOf()) { c -> c.id }),
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            val novel = getNovelWithChapters.awaitNovel(novelId)
            val chapters = getNovelWithChapters.awaitChapters(novelId)
            mutableState.update {
                State.Success(
                    novel = novel,
                    source = sourceManager.getOrStub(novel.source),
                    chapters = chapters,
                    isRefreshingData = false,
                    dialog = null,
                    selectedChapterIds = emptySet(),
                )
            }
        }
    }

    private inline fun updateSuccessState(
        func: (State.Success) -> State.Success,
    ) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
            }
        }
    }

    fun toggleFavorite() {
        val novel = successState?.novel ?: return
        screenModelScope.launchIO {
            val dateAdded = if (!novel.favorite) {
                Instant.now().toEpochMilli()
            } else {
                0L
            }
            updateNovel.await(
                NovelUpdate(
                    id = novel.id,
                    favorite = !novel.favorite,
                    dateAdded = dateAdded,
                ),
            )
        }
    }

    fun refreshChapters(manualFetch: Boolean = true) {
        val state = successState ?: return
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            try {
                val sourceChapters = state.source.getChapterList(state.novel.toSNovel())
                syncNovelChaptersWithSource.await(
                    rawSourceChapters = sourceChapters,
                    novel = state.novel,
                    source = state.source,
                    manualFetch = manualFetch,
                )
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(message = e.message ?: "Failed to refresh")
            } finally {
                updateSuccessState { it.copy(isRefreshingData = false) }
            }
        }
    }

    fun toggleChapterRead(chapterId: Long) {
        val chapter = successState?.chapters?.firstOrNull { it.id == chapterId } ?: return
        val newRead = !chapter.read
        screenModelScope.launchIO {
            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = chapterId,
                    read = newRead,
                    lastPageRead = if (newRead) 0L else chapter.lastPageRead,
                ),
            )
        }
    }

    fun toggleChapterBookmark(chapterId: Long) {
        val chapter = successState?.chapters?.firstOrNull { it.id == chapterId } ?: return
        screenModelScope.launchIO {
            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = chapterId,
                    bookmark = !chapter.bookmark,
                ),
            )
        }
    }

    fun toggleAllChaptersRead() {
        val chapters = successState?.chapters ?: return
        if (chapters.isEmpty()) return
        val markRead = chapters.any { !it.read }
        screenModelScope.launchIO {
            novelChapterRepository.updateAllChapters(
                chapters.map {
                    NovelChapterUpdate(
                        id = it.id,
                        read = markRead,
                        lastPageRead = if (markRead) 0L else it.lastPageRead,
                    )
                },
            )
        }
    }

    fun toggleSelection(chapterId: Long) {
        val state = successState ?: return
        val selected = state.selectedChapterIds.contains(chapterId)
        updateSuccessState {
            if (selected) {
                it.copy(selectedChapterIds = it.selectedChapterIds - chapterId)
            } else {
                it.copy(selectedChapterIds = it.selectedChapterIds + chapterId)
            }
        }
    }

    fun toggleAllSelection(selectAll: Boolean) {
        val state = successState ?: return
        updateSuccessState {
            it.copy(
                selectedChapterIds = if (selectAll) {
                    state.processedChapters.mapTo(mutableSetOf()) { c -> c.id }
                } else {
                    emptySet()
                },
            )
        }
    }

    fun invertSelection() {
        val state = successState ?: return
        val allIds = state.processedChapters.mapTo(mutableSetOf()) { it.id }
        val inverted = allIds.apply { removeAll(state.selectedChapterIds) }
        updateSuccessState { it.copy(selectedChapterIds = inverted) }
    }

    fun bookmarkChapters(bookmarked: Boolean) {
        val state = successState ?: return
        val selected = state.selectedChapterIds
        if (selected.isEmpty()) return
        screenModelScope.launchIO {
            novelChapterRepository.updateAllChapters(
                state.chapters
                    .asSequence()
                    .filter { it.id in selected }
                    .filter { it.bookmark != bookmarked }
                    .map { NovelChapterUpdate(id = it.id, bookmark = bookmarked) }
                    .toList(),
            )
            toggleAllSelection(false)
        }
    }

    fun markChaptersRead(markRead: Boolean) {
        val state = successState ?: return
        val selected = state.selectedChapterIds
        if (selected.isEmpty()) return
        screenModelScope.launchIO {
            novelChapterRepository.updateAllChapters(
                state.chapters
                    .asSequence()
                    .filter { it.id in selected }
                    .filter { it.read != markRead || (!markRead && it.lastPageRead > 0L) }
                    .map {
                        NovelChapterUpdate(
                            id = it.id,
                            read = markRead,
                            lastPageRead = if (markRead) 0L else it.lastPageRead,
                        )
                    }
                    .toList(),
            )
            toggleAllSelection(false)
        }
    }

    fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun setUnreadFilter(state: TriState) {
        val novel = successState?.novel ?: return
        val flag = when (state) {
            TriState.DISABLED -> Novel.SHOW_ALL
            TriState.ENABLED_IS -> Novel.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Novel.CHAPTER_SHOW_READ
        }
        screenModelScope.launchIO {
            setNovelChapterFlags.awaitSetUnreadFilter(novel, flag)
        }
    }

    fun setBookmarkedFilter(state: TriState) {
        val novel = successState?.novel ?: return
        val flag = when (state) {
            TriState.DISABLED -> Novel.SHOW_ALL
            TriState.ENABLED_IS -> Novel.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Novel.CHAPTER_SHOW_NOT_BOOKMARKED
        }
        screenModelScope.launchIO {
            setNovelChapterFlags.awaitSetBookmarkFilter(novel, flag)
        }
    }

    fun setDisplayMode(mode: Long) {
        val novel = successState?.novel ?: return
        screenModelScope.launchIO {
            setNovelChapterFlags.awaitSetDisplayMode(novel, mode)
        }
    }

    fun setSorting(sort: Long) {
        val novel = successState?.novel ?: return
        screenModelScope.launchIO {
            setNovelChapterFlags.awaitSetSortingModeOrFlipOrder(novel, sort)
        }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val novel: Novel,
            val source: NovelSource,
            val chapters: List<NovelChapter>,
            val isRefreshingData: Boolean,
            val dialog: Dialog?,
            val selectedChapterIds: Set<Long> = emptySet(),
        ) : State {
            val processedChapters: List<NovelChapter>
                get() {
                    val chapterSort = Comparator(getNovelChapterSort(novel))
                    return chapters
                        .asSequence()
                        .filter { chapter ->
                            applyFilter(novel.unreadFilter) { !chapter.read } &&
                                applyFilter(novel.bookmarkedFilter) { chapter.bookmark }
                        }
                        .sortedWith(chapterSort)
                        .toList()
                }
        }
    }
}
