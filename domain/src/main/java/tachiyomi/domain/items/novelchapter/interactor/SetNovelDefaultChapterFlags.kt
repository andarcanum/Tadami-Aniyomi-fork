package tachiyomi.domain.items.novelchapter.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.service.LibraryPreferences

class SetNovelDefaultChapterFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setNovelChapterFlags: SetNovelChapterFlags,
    private val getFavorites: GetNovelFavorites,
) {

    suspend fun await(novel: Novel) {
        withNonCancellableContext {
            with(libraryPreferences) {
                setNovelChapterFlags.awaitSetAllFlags(
                    novelId = novel.id,
                    unreadFilter = filterNovelChapterByRead().get(),
                    downloadedFilter = filterNovelChapterByDownloaded().get(),
                    bookmarkedFilter = filterNovelChapterByBookmarked().get(),
                    sortingMode = sortNovelChapterBySourceOrNumber().get(),
                    sortingDirection = sortNovelChapterByAscendingOrDescending().get(),
                    displayMode = displayNovelChapterByNameOrNumber().get(),
                )
            }
        }
    }

    suspend fun awaitAll() {
        withNonCancellableContext {
            getFavorites.await().forEach { await(it) }
        }
    }
}
