package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

class MangaScreenModelTest {

    @Test
    fun `mergeHydrationById preserves item identity and updates download state`() {
        val chapter1 = Chapter.create().copy(id = 101L, mangaId = 1L, name = "Chapter 1")
        val chapter2 = Chapter.create().copy(id = 102L, mangaId = 1L, name = "Chapter 2")

        val currentItems = listOf(
            ChapterList.Item(
                chapter = chapter1,
                downloadState = MangaDownload.State.NOT_DOWNLOADED,
                downloadProgress = 0,
                selected = true,
            ),
            ChapterList.Item(
                chapter = chapter2,
                downloadState = MangaDownload.State.NOT_DOWNLOADED,
                downloadProgress = 0,
                selected = false,
            ),
        )

        val hydratedItems = listOf(
            ChapterList.Item(
                chapter = chapter1,
                downloadState = MangaDownload.State.DOWNLOADED,
                downloadProgress = 100,
                selected = false,
            ),
            ChapterList.Item(
                chapter = chapter2,
                downloadState = MangaDownload.State.DOWNLOADING,
                downloadProgress = 45,
                selected = false,
            ),
        )

        val merged = mergeHydrationById(currentItems, hydratedItems)

        merged.size shouldBe 2
        merged[0].id shouldBe 101L
        merged[0].downloadState shouldBe MangaDownload.State.DOWNLOADED
        merged[0].downloadProgress shouldBe 100
        merged[0].selected shouldBe true // Selected state from current must be preserved!

        merged[1].id shouldBe 102L
        merged[1].downloadState shouldBe MangaDownload.State.DOWNLOADING
        merged[1].downloadProgress shouldBe 45
        merged[1].selected shouldBe false
    }

    @Test
    fun `mergeHydrationById keeps existing item unmodified if download state matches`() {
        val chapter1 = Chapter.create().copy(id = 101L, mangaId = 1L, name = "Chapter 1")
        val item = ChapterList.Item(
            chapter = chapter1,
            downloadState = MangaDownload.State.DOWNLOADED,
            downloadProgress = 100,
            selected = false,
        )

        val current = listOf(item)
        val hydrated = listOf(item.copy())

        val merged = mergeHydrationById(current, hydrated)
        merged.size shouldBe 1
        (merged[0] === item) shouldBe true
    }

    @Test
    fun `shouldApplyDefaultChapterFlags returns true only when unfavorited and SHOW_ALL`() {
        val unfavoritedShowAll = Manga.create().copy(id = 1L, favorite = false, chapterFlags = Manga.SHOW_ALL)
        val favoritedShowAll = Manga.create().copy(id = 2L, favorite = true, chapterFlags = Manga.SHOW_ALL)
        val unfavoritedFiltered = Manga.create().copy(
            id = 3L,
            favorite = false,
            chapterFlags = Manga.CHAPTER_SHOW_UNREAD,
        )

        shouldApplyDefaultChapterFlags(unfavoritedShowAll) shouldBe true
        shouldApplyDefaultChapterFlags(favoritedShowAll) shouldBe false
        shouldApplyDefaultChapterFlags(unfavoritedFiltered) shouldBe false
    }
}
