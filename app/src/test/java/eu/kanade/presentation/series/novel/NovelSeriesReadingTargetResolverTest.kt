package eu.kanade.presentation.series.novel

import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.model.NovelSeries
import kotlin.test.assertEquals

class NovelSeriesReadingTargetResolverTest {

    @Test
    fun `returns next novel when active novel is fully read`() {
        val series = LibraryNovelSeries(
            series = NovelSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(
                libraryNovel(id = 10L, title = "One", totalChapters = 100, readCount = 90),
                libraryNovel(id = 20L, title = "Two", totalChapters = 20, readCount = 0),
            ),
        )

        val chapters = listOf(
            libraryNovelChapters(
                novelId = 10L,
                chapterIds = listOf(1L, 2L, 3L),
                read = true,
            ),
            libraryNovelChapters(
                novelId = 20L,
                chapterIds = listOf(11L, 12L),
                read = false,
            ),
        )

        val target = resolveNovelSeriesReadingTarget(series, chapters)

        assertEquals(20L, target?.novel?.novel?.id)
        assertEquals(11L, target?.chapter?.id)
    }

    private fun libraryNovel(
        id: Long,
        title: String,
        totalChapters: Long,
        readCount: Long,
    ): LibraryNovel {
        return LibraryNovel(
            novel = Novel.create().copy(
                id = id,
                title = title,
                source = 1L,
                url = "https://example.com/$id",
            ),
            category = 0L,
            totalChapters = totalChapters,
            readCount = readCount,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }

    private fun libraryNovelChapters(
        novelId: Long,
        chapterIds: List<Long>,
        read: Boolean,
    ): Pair<LibraryNovel, List<NovelChapter>> {
        val novel = libraryNovel(id = novelId, title = "Novel $novelId", totalChapters = 1, readCount = 0)
        return novel to chapterIds.mapIndexed { index, chapterId ->
            NovelChapter.create().copy(
                id = chapterId,
                novelId = novelId,
                read = read,
                chapterNumber = (index + 1).toDouble(),
                sourceOrder = (index + 1).toLong(),
                name = "Chapter $chapterId",
                url = "https://example.com/novel/$novelId/$chapterId",
            )
        }
    }
}
