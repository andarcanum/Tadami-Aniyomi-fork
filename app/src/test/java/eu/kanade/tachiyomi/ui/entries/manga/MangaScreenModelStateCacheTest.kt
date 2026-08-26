package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel.Dialog
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel.State
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.model.StubMangaSource

class MangaScreenModelStateCacheTest {

    @BeforeEach
    fun setUp() {
        MangaScreenModel.clearStateCacheForTest()
    }

    @Test
    fun `cacheState and restoreStateFromCache returns success state without dialogs or selection`() {
        val manga = Manga.create().copy(id = 100L, title = "Test Manga", url = "/manga/100", source = 1L)
        val chapter = Chapter.create().copy(id = 200L, mangaId = 100L, name = "Ch 1", url = "/ch/1")
        val chapterItem = ChapterList.Item(
            chapter = chapter,
            downloadState = MangaDownload.State.NOT_DOWNLOADED,
            downloadProgress = 0,
            selected = true,
        )
        val source = StubMangaSource(1L, "Source", "en")

        val state = State.Success(
            manga = manga,
            source = source,
            isFromSource = false,
            chapters = listOf(chapterItem),
            availableScanlators = emptySet(),
            scanlatorChapterCounts = emptyMap(),
            excludedScanlators = emptySet(),
            dialog = Dialog.DeleteChapters(listOf(chapter)),
        )

        MangaScreenModel.cacheStateForTest(state)

        val restored = MangaScreenModel.restoreStateFromCacheForTest(100L)
        restored.shouldNotBeNull()
        restored.manga.id shouldBe 100L
        restored.chapters.size shouldBe 1
        restored.isAnySelected shouldBe false
        restored.chapters.first().selected shouldBe false
        restored.dialog.shouldBeNull()
    }

    @Test
    fun `restoreStateFromCache returns null for uncached manga id`() {
        MangaScreenModel.restoreStateFromCacheForTest(999L).shouldBeNull()
    }
}
