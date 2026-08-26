package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter

/**
 * Regression tests for the hydration race: the deferred hydrate job must merge download-state
 * fields by chapter id instead of overwriting the whole list with a stale snapshot.
 */
class MangaScreenModelHydrationMergeTest {

    @Test
    fun `hydration preserves fresher chapter rows and selection`() {
        val staleChapter = chapter(id = 1L)
        val fresherChapter = staleChapter.copy(read = true) // DB emitted a newer row after hydration started
        val current = listOf(
            ChapterList.Item(fresherChapter, MangaDownload.State.NOT_DOWNLOADED, 0, selected = true),
        )
        val hydrated = listOf(
            ChapterList.Item(staleChapter, MangaDownload.State.DOWNLOADED, 100),
        )

        val merged = mergeHydrationById(current, hydrated)

        merged shouldBe listOf(
            ChapterList.Item(fresherChapter, MangaDownload.State.DOWNLOADED, 100, selected = true),
        )
    }

    @Test
    fun `items missing from hydration snapshot are kept as-is`() {
        val current = listOf(
            ChapterList.Item(chapter(id = 1L), MangaDownload.State.DOWNLOADED, 50, selected = true),
        )
        val hydrated = emptyList<ChapterList.Item>()

        val merged = mergeHydrationById(current, hydrated)

        merged shouldBe current
    }

    @Test
    fun `unchanged download state keeps same item instance`() {
        val item = ChapterList.Item(chapter(id = 1L), MangaDownload.State.NOT_DOWNLOADED, 0)
        val hydrated = listOf(ChapterList.Item(item.chapter, MangaDownload.State.NOT_DOWNLOADED, 0))

        val merged = mergeHydrationById(listOf(item), hydrated)

        merged[0] shouldBe item
    }

    @Test
    fun `mapChaptersPreservingDownloadState reuses existing items and download states without disk checks`() {
        val chapter1 = chapter(id = 1L)
        val chapter2 = chapter(id = 2L)
        val chapter3 = chapter(id = 3L) // New chapter

        val existingItem1 = ChapterList.Item(chapter1, MangaDownload.State.DOWNLOADED, 100, selected = true)
        val existingItem2 = ChapterList.Item(chapter2, MangaDownload.State.DOWNLOADING, 45, selected = false)
        val currentItems = listOf(existingItem1, existingItem2)

        var diskCheckCount = 0
        val mapped = mapChaptersPreservingDownloadState(
            currentItems = currentItems,
            newChapters = listOf(chapter1, chapter2, chapter3),
            manga = tachiyomi.domain.entries.manga.model.Manga.create().copy(id = 10L, source = 1L),
            selectedIds = setOf(1L),
            isChapterDownloaded = {
                diskCheckCount++
                it.id == 3L
            },
            getActiveDownload = { null },
        )

        // Chapter 1 kept same instance (chapter + selection unchanged)
        mapped[0] shouldBe existingItem1
        // Chapter 2 preserved its active download state without disk check
        mapped[1].downloadState shouldBe MangaDownload.State.DOWNLOADING
        mapped[1].downloadProgress shouldBe 45
        // Chapter 3 resolved download state via callback (only 1 disk check)
        mapped[2].downloadState shouldBe MangaDownload.State.DOWNLOADED
        diskCheckCount shouldBe 1
    }

    private fun chapter(id: Long): Chapter = Chapter.create().copy(
        id = id,
        sourceOrder = id,
    )
}
