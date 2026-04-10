package eu.kanade.tachiyomi.data.download.novel

import com.hippo.unifile.UniFile
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path

class NovelTranslatedDownloadManagerTest {

    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `legacy lookup stays scoped to the current novel`() {
        val source = MutableNovelSource(id = 10L, label = "Source A")
        val manager = createManager(source)
        val currentNovel = Novel.create().copy(id = 1L, source = 10L, title = "Novel A")
        val otherNovel = Novel.create().copy(id = 2L, source = 10L, title = "Novel B")
        val chapter = NovelChapter.create().copy(
            id = 3L,
            novelId = currentNovel.id,
            chapterNumber = 1.0,
            name = "Prologue",
        )

        translatedLegacyFile(tempDir.resolve("downloads").toFile(), otherNovel, chapter)
            .apply {
                parentFile?.mkdirs()
                writeText("other novel")
            }

        manager.isTranslatedChapterDownloaded(
            novel = currentNovel,
            chapter = chapter,
            format = NovelTranslatedDownloadFormat.TXT,
        ) shouldBe false
    }

    @Test
    fun `deleteTranslatedChapter removes stable and legacy files`() {
        val source = MutableNovelSource(id = 10L, label = "Source A")
        val manager = createManager(source)
        val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel A")
        val chapter = NovelChapter.create().copy(
            id = 3L,
            novelId = novel.id,
            chapterNumber = 1.0,
            name = "Prologue",
        )

        val stableFile = translatedStableFile(tempDir.resolve("downloads").toFile(), novel, chapter)
            .apply {
                parentFile?.mkdirs()
                writeText("stable")
            }
        val legacyFile = translatedLegacyFile(tempDir.resolve("downloads").toFile(), novel, chapter)
            .apply {
                parentFile?.mkdirs()
                writeText("legacy")
            }

        manager.deleteTranslatedChapter(
            novel = novel,
            chapter = chapter,
            format = NovelTranslatedDownloadFormat.TXT,
        )

        stableFile.exists() shouldBe false
        legacyFile.exists() shouldBe false
    }

    private fun createManager(source: MutableNovelSource): NovelTranslatedDownloadManager {
        val storageManager = mockk<StorageManager>()
        every { storageManager.getDownloadsDirectory() } returns fakeUniFile(
            tempDir.resolve("downloads").toFile().apply { mkdirs() },
        )

        val sourceManager = mockk<NovelSourceManager>()
        every { sourceManager.getOrStub(any()) } returns source

        return NovelTranslatedDownloadManager(
            application = null,
            sourceManager = sourceManager,
            storageManager = storageManager,
        )
    }

    private fun translatedStableFile(baseDir: File, novel: Novel, chapter: NovelChapter): File {
        return File(
            baseDir,
            "novels_translated/${novel.source}/${novel.id}/${translatedFileName(chapter)}",
        )
    }

    private fun translatedLegacyFile(baseDir: File, novel: Novel, chapter: NovelChapter): File {
        val sourceDir = "Source A"
        val novelDir = novel.title
        return File(
            baseDir,
            "novels_translated/$sourceDir/$novelDir/${translatedFileName(chapter)}",
        )
    }

    private fun translatedFileName(chapter: NovelChapter): String {
        return "1 - Prologue.txt"
    }

    private fun fakeUniFile(file: File): UniFile {
        val normalized = file.absoluteFile
        return mockk(relaxed = true) {
            every { getName() } returns normalized.name
            every { getFilePath() } returns normalized.absolutePath
            every { isDirectory() } answers { normalized.isDirectory }
            every { isFile() } answers { normalized.isFile }
            every { exists() } answers { normalized.exists() }
            every { length() } answers { normalized.length() }
            every { canRead() } answers { normalized.canRead() }
            every { canWrite() } answers { normalized.canWrite() }
            every { delete() } answers { normalized.delete() }
            every { listFiles() } answers {
                normalized.listFiles()
                    ?.map { fakeUniFile(it) }
                    ?.toTypedArray()
                    ?: emptyArray()
            }
            every { findFile(any()) } answers {
                val child = File(normalized, firstArg<String>())
                if (child.exists()) fakeUniFile(child) else null
            }
            every { createDirectory(any()) } answers {
                val child = File(normalized, firstArg<String>())
                child.mkdirs()
                fakeUniFile(child)
            }
            every { createFile(any()) } answers {
                val child = File(normalized, firstArg<String>())
                child.parentFile?.mkdirs()
                if (!child.exists()) {
                    child.createNewFile()
                }
                fakeUniFile(child)
            }
            every { openInputStream() } answers { FileInputStream(normalized) }
            every { openOutputStream() } answers { FileOutputStream(normalized) }
            every { openOutputStream(any()) } answers {
                FileOutputStream(normalized, firstArg())
            }
        }
    }

    private class MutableNovelSource(
        override val id: Long,
        var label: String,
    ) : eu.kanade.tachiyomi.novelsource.NovelSource {
        override val name: String = "Novel"
        override val lang: String = "en"

        override suspend fun getNovelDetails(novel: eu.kanade.tachiyomi.novelsource.model.SNovel) = novel
        override suspend fun getChapterList(
            novel: eu.kanade.tachiyomi.novelsource.model.SNovel,
        ) = emptyList<eu.kanade.tachiyomi.novelsource.model.SNovelChapter>()
        override suspend fun getChapterText(chapter: eu.kanade.tachiyomi.novelsource.model.SNovelChapter) = ""

        override fun toString(): String = label
    }
}
