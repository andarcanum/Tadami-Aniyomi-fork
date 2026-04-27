package tachiyomi.domain.items.chapter.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.library.service.LibraryPreferences

class SetMangaDefaultChapterFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setMangaChapterFlags: SetMangaChapterFlags,
    private val getFavorites: GetMangaFavorites,
) {

    suspend fun await(manga: Manga) {
        withNonCancellableContext {
            with(libraryPreferences) {
                setMangaChapterFlags.awaitSetAllFlags(
                    mangaId = manga.id,
                    unreadFilter = filterChapterByRead().get(),
                    downloadedFilter = filterChapterByDownloaded().get(),
                    bookmarkedFilter = filterChapterByBookmarked().get(),
                    sortingMode = sortChapterBySourceOrNumber().get(),
                    sortingDirection = sortChapterByAscendingOrDescending().get(),
                    displayMode = displayChapterByNameOrNumber().get(),
                )
            }
        }
    }

    suspend fun awaitAll() {
        withNonCancellableContext {
            val chapterFlags = with(libraryPreferences) {
                0L
                    .setFlag(filterChapterByRead().get(), Manga.CHAPTER_UNREAD_MASK)
                    .setFlag(filterChapterByDownloaded().get(), Manga.CHAPTER_DOWNLOADED_MASK)
                    .setFlag(filterChapterByBookmarked().get(), Manga.CHAPTER_BOOKMARKED_MASK)
                    .setFlag(sortChapterBySourceOrNumber().get(), Manga.CHAPTER_SORTING_MASK)
                    .setFlag(sortChapterByAscendingOrDescending().get(), Manga.CHAPTER_SORT_DIR_MASK)
                    .setFlag(displayChapterByNameOrNumber().get(), Manga.CHAPTER_DISPLAY_MASK)
            }
            setMangaChapterFlags.awaitSetAllFlags(
                getFavorites.await().map { manga ->
                    MangaUpdate(
                        id = manga.id,
                        chapterFlags = chapterFlags,
                    )
                },
            )
        }
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
