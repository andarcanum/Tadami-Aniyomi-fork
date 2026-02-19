package tachiyomi.domain.entries.novel.interactor

import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository

class SetNovelChapterFlags(
    private val novelRepository: NovelRepository,
) {

    suspend fun awaitSetDownloadedFilter(novel: Novel, flag: Long): Boolean {
        return novelRepository.updateNovel(
            NovelUpdate(
                id = novel.id,
                chapterFlags = novel.chapterFlags.setFlag(flag, Novel.CHAPTER_DOWNLOADED_MASK),
            ),
        )
    }

    suspend fun awaitSetUnreadFilter(novel: Novel, flag: Long): Boolean {
        return novelRepository.updateNovel(
            NovelUpdate(
                id = novel.id,
                chapterFlags = novel.chapterFlags.setFlag(flag, Novel.CHAPTER_UNREAD_MASK),
            ),
        )
    }

    suspend fun awaitSetBookmarkFilter(novel: Novel, flag: Long): Boolean {
        return novelRepository.updateNovel(
            NovelUpdate(
                id = novel.id,
                chapterFlags = novel.chapterFlags.setFlag(flag, Novel.CHAPTER_BOOKMARKED_MASK),
            ),
        )
    }

    suspend fun awaitSetDisplayMode(novel: Novel, flag: Long): Boolean {
        return novelRepository.updateNovel(
            NovelUpdate(
                id = novel.id,
                chapterFlags = novel.chapterFlags.setFlag(flag, Novel.CHAPTER_DISPLAY_MASK),
            ),
        )
    }

    suspend fun awaitSetSortingModeOrFlipOrder(novel: Novel, flag: Long): Boolean {
        val newFlags = novel.chapterFlags.let {
            if (novel.sorting == flag) {
                val orderFlag = if (novel.sortDescending()) {
                    Novel.CHAPTER_SORT_ASC
                } else {
                    Novel.CHAPTER_SORT_DESC
                }
                it.setFlag(orderFlag, Novel.CHAPTER_SORT_DIR_MASK)
            } else {
                it
                    .setFlag(flag, Novel.CHAPTER_SORTING_MASK)
                    .setFlag(Novel.CHAPTER_SORT_ASC, Novel.CHAPTER_SORT_DIR_MASK)
            }
        }
        return novelRepository.updateNovel(
            NovelUpdate(
                id = novel.id,
                chapterFlags = newFlags,
            ),
        )
    }

    suspend fun awaitSetAllFlags(
        novelId: Long,
        unreadFilter: Long,
        downloadedFilter: Long,
        bookmarkedFilter: Long,
        sortingMode: Long,
        sortingDirection: Long,
        displayMode: Long,
    ): Boolean {
        return novelRepository.updateNovel(
            NovelUpdate(
                id = novelId,
                chapterFlags = 0L.setFlag(unreadFilter, Novel.CHAPTER_UNREAD_MASK)
                    .setFlag(downloadedFilter, Novel.CHAPTER_DOWNLOADED_MASK)
                    .setFlag(bookmarkedFilter, Novel.CHAPTER_BOOKMARKED_MASK)
                    .setFlag(sortingMode, Novel.CHAPTER_SORTING_MASK)
                    .setFlag(sortingDirection, Novel.CHAPTER_SORT_DIR_MASK)
                    .setFlag(displayMode, Novel.CHAPTER_DISPLAY_MASK),
            ),
        )
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
