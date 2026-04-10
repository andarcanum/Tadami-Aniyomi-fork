package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import com.hippo.unifile.UniFile
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

class NovelDownloadManagerTest {

    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `downloadChapter returns false when chapter fetch times out`() {
        runBlocking {
            val manager = NovelDownloadManager(
                application = null,
                sourceManager = null,
                storageManager = null,
                downloadCache = null,
                chapterFetchTimeoutMillis = 50L,
                fetchChapterText = { _, _ ->
                    delay(250L)
                    "chapter text"
                },
            )

            val result = manager.downloadChapter(
                novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel"),
                chapter = NovelChapter.create().copy(id = 2L, novelId = 1L, url = "/chapter-2"),
            )

            result shouldBe false
        }
    }

    @Test
    fun `downloaded chapter survives source representation changes`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val expectedText = "<html><body>downloaded</body></html>"
            val manager = createManager(
                source = source,
                chapterText = expectedText,
            )
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(id = 2L, novelId = 1L, url = "/chapter-2")

            val downloaded = manager.downloadChapter(
                novel = novel,
                chapter = chapter,
            )

            downloaded shouldBe true

            source.label = "Source B"
            val restartedManager = createManager(
                source = source,
                chapterText = null,
            )

            restartedManager.isChapterDownloaded(novel, chapter.id) shouldBe true
            restartedManager.getDownloadedChapterText(novel, chapter.id) shouldBe expectedText
        }
    }

    @Test
    fun `legacy files are included in counts and sizes`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val manager = createManager(
                source = source,
                chapterText = null,
                applicationFilesDir = tempDir.resolve("app").toFile().apply { mkdirs() },
            )
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")

            val stableChapterFile = chapterFile(tempDir.resolve("downloads").toFile(), novel, 2L)
                .apply {
                    parentFile?.mkdirs()
                    writeText("stable")
                }
            val legacyChapterFile = legacyChapterFile(
                tempDir.resolve("app").toFile(),
                novel,
                3L,
            ).apply {
                parentFile?.mkdirs()
                writeText("legacy")
            }

            manager.getDownloadCount(novel) shouldBe 2
            manager.getDownloadSize(novel) shouldBe stableChapterFile.length() + legacyChapterFile.length()
            manager.getDownloadCount() shouldBe 2
            manager.getDownloadSize() shouldBe stableChapterFile.length() + legacyChapterFile.length()
        }
    }

    @Test
    fun `deleteChapter removes legacy filesDir chapter`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val appFilesDir = tempDir.resolve("app").toFile().apply { mkdirs() }
            val manager = createManager(
                source = source,
                chapterText = null,
                applicationFilesDir = appFilesDir,
            )
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapterId = 2L
            val legacyFile = legacyChapterFile(appFilesDir, novel, chapterId).apply {
                parentFile?.mkdirs()
                writeText("legacy")
            }

            manager.deleteChapter(novel, chapterId)

            legacyFile.exists() shouldBe false
        }
    }

    private fun createManager(
        source: MutableNovelSource,
        chapterText: String?,
        applicationFilesDir: File? = null,
    ): NovelDownloadManager {
        val storageManager = mockk<StorageManager>()
        every { storageManager.getDownloadsDirectory() } returns fakeUniFile(
            tempDir.resolve("downloads").toFile().apply { mkdirs() },
        )

        val application = applicationFilesDir?.let { filesDir ->
            val mockedApplication = mockk<Application>()
            every { mockedApplication.filesDir } returns filesDir
            mockedApplication
        }

        val sourceManager = mockk<NovelSourceManager>()
        every { sourceManager.getOrStub(any()) } returns source

        return NovelDownloadManager(
            application = application,
            sourceManager = sourceManager,
            storageManager = storageManager,
            downloadCache = null,
            fetchChapterText = { _, _ -> chapterText },
        )
    }

    private fun chapterFile(baseDir: File, novel: Novel, chapterId: Long): File {
        return File(baseDir, "novels/${novel.source}/${novel.id}/$chapterId.html")
    }

    private fun legacyChapterFile(baseDir: File, novel: Novel, chapterId: Long): File {
        return File(baseDir, "novels/${novel.source}/${novel.id}/$chapterId.html")
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
