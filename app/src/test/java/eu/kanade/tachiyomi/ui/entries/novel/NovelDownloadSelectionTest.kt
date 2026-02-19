package eu.kanade.tachiyomi.ui.entries.novel

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelDownloadSelectionTest {

    @Test
    fun `selectChaptersForDownload picks next unread chapters`() {
        val novel = Novel.create()
        val chapters = listOf(
            chapter(id = 1, unread = false, order = 1),
            chapter(id = 2, unread = true, order = 2),
            chapter(id = 3, unread = true, order = 3),
            chapter(id = 4, unread = true, order = 4),
        )

        NovelScreenModel.selectChaptersForDownload(
            action = NovelDownloadAction.NEXT,
            novel = novel,
            chapters = chapters,
            downloadedChapterIds = emptySet(),
            amount = 2,
        ).map { it.id }.shouldContainExactly(2, 3)
    }

    @Test
    fun `selectChaptersForDownload skips downloaded chapters`() {
        val novel = Novel.create()
        val chapters = listOf(
            chapter(id = 10, unread = true, order = 1),
            chapter(id = 11, unread = true, order = 2),
            chapter(id = 12, unread = true, order = 3),
        )

        NovelScreenModel.selectChaptersForDownload(
            action = NovelDownloadAction.NEXT,
            novel = novel,
            chapters = chapters,
            downloadedChapterIds = setOf(10L, 11L),
            amount = 2,
        ).map { it.id }.shouldContainExactly(12)
    }

    @Test
    fun `selectChaptersForDownload returns all unread for UNREAD action`() {
        val novel = Novel.create()
        val chapters = listOf(
            chapter(id = 20, unread = false, order = 1),
            chapter(id = 21, unread = true, order = 2),
            chapter(id = 22, unread = true, order = 3),
            chapter(id = 23, unread = false, order = 4),
        )

        NovelScreenModel.selectChaptersForDownload(
            action = NovelDownloadAction.UNREAD,
            novel = novel,
            chapters = chapters,
            downloadedChapterIds = emptySet(),
            amount = 0,
        ).map { it.id }.shouldContainExactly(21, 22)
    }

    @Test
    fun `selectChaptersForDownload returns all not-downloaded chapters for ALL action`() {
        val novel = Novel.create()
        val chapters = listOf(
            chapter(id = 30, unread = false, order = 1),
            chapter(id = 31, unread = true, order = 2),
            chapter(id = 32, unread = false, order = 3),
        )

        NovelScreenModel.selectChaptersForDownload(
            action = NovelDownloadAction.ALL,
            novel = novel,
            chapters = chapters,
            downloadedChapterIds = setOf(31L),
            amount = 0,
        ).map { it.id }.shouldContainExactly(30, 32)
    }

    private fun chapter(
        id: Long,
        unread: Boolean,
        order: Long,
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = 1L,
            read = !unread,
            sourceOrder = order,
            name = "Chapter $id",
            chapterNumber = id.toDouble(),
        )
    }
}
