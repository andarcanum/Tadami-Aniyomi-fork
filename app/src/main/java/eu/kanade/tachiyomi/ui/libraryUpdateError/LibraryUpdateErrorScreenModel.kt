package eu.kanade.tachiyomi.ui.libraryUpdateError

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorRecord
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.novel.interactor.GetNovel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryUpdateErrorScreenModel(
    private val getManga: GetManga = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
) : StateScreenModel<LibraryUpdateErrorScreenState>(
    LibraryUpdateErrorScreenState(selectedMedia = LibraryUpdateErrorStore.getLastSelectedTab()),
) {

    private val selectedErrorIds: HashSet<Long> = HashSet()
    private val retryingErrors = mutableMapOf<LibraryUpdateErrorKey, Long>()

    init {
        screenModelScope.launchIO {
            LibraryUpdateErrorStore.errors.collectLatest { errors ->
                val currentErrors = filterCurrentLibraryErrors(errors)

                reconcileRetryingLibraryUpdateErrors(
                    errors = currentErrors,
                    retryingErrors = retryingErrors,
                )

                mutableState.update { state ->
                    state.copy(
                        isLoading = false,
                        items = currentErrors.map { record ->
                            LibraryUpdateErrorItem(
                                record = record,
                                selected = record.id in selectedErrorIds,
                                retrying = record.key in retryingErrors,
                            )
                        },
                    )
                }
            }
        }
    }

    private suspend fun filterCurrentLibraryErrors(
        errors: List<LibraryUpdateErrorRecord>,
    ): List<LibraryUpdateErrorRecord> {
        val staleErrorIds = mutableListOf<Long>()
        val currentErrors = errors.mapNotNull { record ->
            when (record.media) {
                LibraryUpdateErrorMedia.Manga -> {
                    val manga = getManga.await(record.entryId)
                    if (manga?.favorite == true) {
                        record.copy(
                            title = manga.title,
                            sourceId = manga.source,
                            thumbnailUrl = manga.thumbnailUrl,
                        )
                    } else {
                        staleErrorIds += record.id
                        null
                    }
                }
                LibraryUpdateErrorMedia.Anime -> {
                    val anime = getAnime.await(record.entryId)
                    if (anime?.favorite == true) {
                        record.copy(
                            title = anime.title,
                            sourceId = anime.source,
                            thumbnailUrl = anime.thumbnailUrl,
                        )
                    } else {
                        staleErrorIds += record.id
                        null
                    }
                }
                LibraryUpdateErrorMedia.Novel -> {
                    val novel = getNovel.await(record.entryId)
                    if (novel?.favorite == true) {
                        record.copy(
                            title = novel.title,
                            sourceId = novel.source,
                            thumbnailUrl = novel.thumbnailUrl,
                        )
                    } else {
                        staleErrorIds += record.id
                        null
                    }
                }
            }
        }

        if (staleErrorIds.isNotEmpty()) {
            LibraryUpdateErrorStore.delete(staleErrorIds)
            selectedErrorIds.removeAll(staleErrorIds.toSet())
        }

        return currentErrors
    }

    fun setSelectedTab(media: LibraryUpdateErrorMedia) {
        selectedErrorIds.clear()
        LibraryUpdateErrorStore.setLastSelectedTab(media)
        mutableState.update { state ->
            state.copy(
                selectedMedia = media,
                items = state.items.map { it.copy(selected = false) },
            )
        }
    }

    fun toggleSelection(item: LibraryUpdateErrorItem, selected: Boolean) {
        selectedErrorIds.addOrRemove(item.record.id, selected)
        mutableState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.record.id == item.record.id) it.copy(selected = selected) else it
                },
            )
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val visibleIds = state.visibleItems.map { it.record.id }.toSet()
            if (selected) {
                selectedErrorIds.addAll(visibleIds)
            } else {
                selectedErrorIds.removeAll(visibleIds)
            }
            state.copy(
                items = state.items.map {
                    if (it.record.id in visibleIds) it.copy(selected = selected) else it
                },
            )
        }
    }

    fun invertSelection() {
        mutableState.update { state ->
            val visibleIds = state.visibleItems.map { it.record.id }.toSet()
            state.copy(
                items = state.items.map {
                    if (it.record.id in visibleIds) {
                        selectedErrorIds.addOrRemove(it.record.id, !it.selected)
                        it.copy(selected = !it.selected)
                    } else {
                        it
                    }
                },
            )
        }
    }

    fun retryVisibleErrors() {
        val visibleItems = state.value.visibleItems
        if (visibleItems.isEmpty()) return

        val context = Injekt.get<Application>()
        val entryIds = visibleItems
            .map { it.record.entryId }
            .distinct()
            .toLongArray()

        val started = when (state.value.selectedMedia) {
            LibraryUpdateErrorMedia.Manga -> MangaLibraryUpdateJob.startNow(context, entryIds)
            LibraryUpdateErrorMedia.Anime -> AnimeLibraryUpdateJob.startNow(context, entryIds)
            LibraryUpdateErrorMedia.Novel -> NovelLibraryUpdateJob.startNow(context, entryIds)
        }
        if (!started) return

        visibleItems.forEach { item ->
            retryingErrors[item.record.key] = item.record.id
        }
        mutableState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.record.key in retryingErrors) {
                        item.copy(retrying = true)
                    } else {
                        item
                    }
                },
            )
        }
    }

    fun deleteSelected() {
        val selected = selectedErrorIds.toList()
        val selectedSet = selected.toSet()
        state.value.items
            .filter { it.record.id in selectedSet }
            .forEach { retryingErrors.remove(it.record.key) }
        LibraryUpdateErrorStore.delete(selected)
        selectedErrorIds.removeAll(selectedSet)
    }

    fun delete(errorId: Long) {
        state.value.items
            .firstOrNull { it.record.id == errorId }
            ?.let { retryingErrors.remove(it.record.key) }
        LibraryUpdateErrorStore.delete(errorId)
        selectedErrorIds.remove(errorId)
    }

    fun clearVisible() {
        val selectedMedia = state.value.selectedMedia
        val ids = state.value.items
            .filter { it.record.media == selectedMedia }
            .map { it.record.id }
        LibraryUpdateErrorStore.delete(ids)
        selectedErrorIds.removeAll(ids.toSet())
        retryingErrors.keys.removeAll { it.media == selectedMedia }
    }
}

internal data class LibraryUpdateErrorKey(
    val media: LibraryUpdateErrorMedia,
    val entryId: Long,
)

internal val LibraryUpdateErrorRecord.key: LibraryUpdateErrorKey
    get() = LibraryUpdateErrorKey(media = media, entryId = entryId)

internal fun reconcileRetryingLibraryUpdateErrors(
    errors: List<LibraryUpdateErrorRecord>,
    retryingErrors: MutableMap<LibraryUpdateErrorKey, Long>,
) {
    val currentKeys = errors.mapTo(mutableSetOf()) { it.key }
    retryingErrors.keys.removeAll { it !in currentKeys }
    errors.forEach { record ->
        val initialErrorId = retryingErrors[record.key]
        if (initialErrorId != null && initialErrorId != record.id) {
            retryingErrors.remove(record.key)
        }
    }
}

@Immutable
data class LibraryUpdateErrorScreenState(
    val isLoading: Boolean = true,
    val selectedMedia: LibraryUpdateErrorMedia = LibraryUpdateErrorMedia.Novel,
    val items: List<LibraryUpdateErrorItem> = emptyList(),
) {
    val visibleItems = items.filter { it.record.media == selectedMedia }
    val selected = visibleItems.filter { it.selected }
    val selectionMode = selected.isNotEmpty()
    val isRetryingVisible = visibleItems.any { it.retrying }

    fun count(media: LibraryUpdateErrorMedia): Int = items.count { it.record.media == media }

    fun groupedVisibleItems(): List<LibraryUpdateErrorUiModel> {
        return visibleItems
            .sortedWith(compareBy<LibraryUpdateErrorItem> { it.record.message }.thenBy { it.record.title })
            .groupBy { it.record.message }
            .flatMap { (message, errors) ->
                listOf(LibraryUpdateErrorUiModel.Header(message, errors.size)) +
                    errors.map { LibraryUpdateErrorUiModel.Item(it) }
            }
    }
}

@Immutable
data class LibraryUpdateErrorItem(
    val record: LibraryUpdateErrorRecord,
    val selected: Boolean,
    val retrying: Boolean = false,
)

sealed class LibraryUpdateErrorUiModel {
    data class Header(val errorMessage: String, val count: Int) : LibraryUpdateErrorUiModel()
    data class Item(val item: LibraryUpdateErrorItem) : LibraryUpdateErrorUiModel()
}
