package eu.kanade.tachiyomi.ui.storage.novel

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.ui.storage.CommonStorageScreenModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.GetVisibleNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.library.novel.LibraryNovel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelStorageScreenModel(
    private val downloadChanges: MutableSharedFlow<Unit> = MutableSharedFlow<Unit>(replay = 1).also {
        it.tryEmit(Unit)
    },
    private val downloadInitializing: MutableStateFlow<Boolean> = MutableStateFlow(false),
    private val getLibraries: GetLibraryNovel = Injekt.get(),
    getCategories: GetNovelCategories = Injekt.get(),
    getVisibleCategories: GetVisibleNovelCategories = Injekt.get(),
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
) : CommonStorageScreenModel<LibraryNovel>(
    downloadCacheChanges = downloadChanges,
    downloadCacheIsInitializing = downloadInitializing,
    libraries = getLibraries.subscribe(),
    categories = { hideHiddenCategories ->
        if (hideHiddenCategories) {
            getVisibleCategories.subscribe().map { categories ->
                categories.map { Category(it.id, it.name, it.order, it.flags, it.hidden) }
            }
        } else {
            getCategories.subscribe().map { categories ->
                categories.map { Category(it.id, it.name, it.order, it.flags, it.hidden) }
            }
        }
    },
    getDownloadSize = { downloadManager.getDownloadSize(novel) },
    getDownloadCount = { downloadManager.getDownloadCount(novel) },
    getId = { id },
    getCategoryId = { category },
    getTitle = { novel.title },
    getThumbnail = { novel.thumbnailUrl },
) {
    override fun deleteEntry(id: Long) {
        screenModelScope.launchNonCancellable {
            val novel = getLibraries.await().find {
                it.id == id
            }?.novel ?: return@launchNonCancellable
            downloadManager.deleteNovel(novel)
            downloadChanges.tryEmit(Unit)
        }
    }
}
