package eu.kanade.tachiyomi.source.novel

import eu.kanade.tachiyomi.novelsource.model.SNovel
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import eu.kanade.tachiyomi.source.novel.importer.ImportedEpubStorage
import java.nio.file.Path

class ImportedEpubNovelSourceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `imported epub source exposes id and name`() {
        val source = ImportedEpubNovelSource(
            novelRepository = mockk(relaxed = true),
            chapterRepository = mockk(relaxed = true),
            storage = ImportedEpubStorage(tempDir.toFile()),
        )

        source.id shouldBe IMPORTED_EPUB_NOVEL_SOURCE_ID
        source.name shouldBe IMPORTED_EPUB_NOVEL_SOURCE_NAME
    }

    @Test
    fun `imported epub source reads chapters and chapter html from storage`() = runTest {
        val novelRepository = mockk<NovelRepository>(relaxed = true)
        val chapterRepository = mockk<NovelChapterRepository>()
        val storage = ImportedEpubStorage(tempDir.toFile())
        val source = ImportedEpubNovelSource(
            novelRepository = novelRepository,
            chapterRepository = chapterRepository,
            storage = storage,
        )

        val localNovelId = 7L
        val localChapter = NovelChapter.create().copy(
            id = 11L,
            novelId = localNovelId,
            sourceOrder = 0L,
            url = "11",
            name = "Chapter 1",
            chapterNumber = 1.0,
            dateUpload = 1234L,
        )

        coEvery { chapterRepository.getChapterByNovelId(localNovelId) } returns listOf(localChapter)
        coEvery { chapterRepository.getChapterById(11L) } returns localChapter

        storage.writeChapter(localNovelId, localChapter.id, "<html><body>Stored chapter</body></html>")

        val chapters = source.getChapterList(
            SNovel.create().apply {
                url = localNovelId.toString()
                title = "Imported"
                author = null
                description = null
                genre = null
                status = 0
                thumbnail_url = null
                update_strategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE
                initialized = true
            },
        )

        chapters.size shouldBe 1
        chapters.single().url shouldBe localChapter.id.toString()
        chapters.single().name shouldBe localChapter.name

        val chapterHtml = source.getChapterText(chapters.single())
        chapterHtml shouldBe "<html><body>Stored chapter</body></html>"
    }
}
