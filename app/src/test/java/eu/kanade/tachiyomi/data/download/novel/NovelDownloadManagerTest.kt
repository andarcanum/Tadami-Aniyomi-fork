package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import com.hippo.unifile.UniFile
import io.kotest.matchers.collections.shouldBeEmpty
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

    // When true, fakeUniFile output streams fail so migration/export write paths can be exercised.
    private var failOutputStreams = false

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
    fun `readable download dir uses stable source name, not volatile toString()`() {
        runBlocking {
            // Regression: novel sources do not override toString(), so using
            // source.toString() produced folder names like
            // "NovelConfigurableJsSource@19c96ed" whose identityHashCode changes per
            // app restart -> reindex failed and duplicate per-extension folders were
            // created. The readable dir must use the stable source.name instead.
            val source = MutableNovelSource(id = 10L, label = "Novel Ninja")
            val manager = createManager(source = source, chapterText = "x")
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel Title")
            val chapter = NovelChapter.create().copy(id = 2L, novelId = 1L, url = "/chapter-2")

            manager.downloadChapter(novel = novel, chapter = chapter) shouldBe true

            val downloadsDir = tempDir.resolve("downloads").toFile()
            // Folder is named after the human-readable source name...
            readableChapterFile(downloadsDir, "Novel Ninja", readableNovelDirName(novel), chapter.id)
                .exists() shouldBe true
            // ...and never after the volatile default toString().
            val sourceDirs = File(downloadsDir, "novels").listFiles()?.map { it.name }.orEmpty()
            sourceDirs.any { it.contains("@") } shouldBe false
            sourceDirs.contains(source.toString()) shouldBe false
        }
    }

    @Test
    fun `downloaded chapter is written under readable source and novel directories`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val expectedText = "<html><body>downloaded</body></html>"
            val manager = createManager(
                source = source,
                chapterText = expectedText,
            )
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel Title")
            val chapter = NovelChapter.create().copy(id = 2L, novelId = 1L, url = "/chapter-2")

            val downloaded = manager.downloadChapter(
                novel = novel,
                chapter = chapter,
            )

            downloaded shouldBe true
            readableChapterFile(
                tempDir.resolve("downloads").toFile(),
                source.name,
                readableNovelDirName(novel),
                chapter.id,
            ).exists() shouldBe true
            manager.getDownloadedChapterText(novel, chapter.id) shouldBe expectedText
        }
    }

    @Test
    fun `downloaded chapter is published atomically without leftover temp files`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val manager = createManager(source = source, chapterText = "updated text")
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel Title")
            val chapter = NovelChapter.create().copy(id = 2L, novelId = 1L, url = "/chapter-2")
            val chapterDir = tempDir.resolve("downloads").toFile()
                .resolve("novels/${source.name}/${readableNovelDirName(novel)}")
            chapterDir.mkdirs()
            // A truncated file from a previous interrupted download must be fully replaced.
            File(chapterDir, "${chapter.id}.html").writeText("stale partial")

            manager.downloadChapter(novel = novel, chapter = chapter) shouldBe true

            File(chapterDir, "${chapter.id}.html").readText() shouldBe "updated text"
            chapterDir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty().shouldBeEmpty()
            manager.getDownloadedChapterText(novel, chapter.id) shouldBe "updated text"
        }
    }

    @Test
    fun `failed fetch leaves previously downloaded content untouched`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val manager = createManager(source = source, chapterText = null)
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel Title")
            val chapter = NovelChapter.create().copy(id = 2L, novelId = 1L, url = "/chapter-2")
            val chapterDir = tempDir.resolve("downloads").toFile()
                .resolve("novels/${source.name}/${readableNovelDirName(novel)}")
            chapterDir.mkdirs()
            File(chapterDir, "${chapter.id}.html").writeText("good content")

            manager.downloadChapter(novel = novel, chapter = chapter) shouldBe false

            File(chapterDir, "${chapter.id}.html").readText() shouldBe "good content"
            chapterDir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty().shouldBeEmpty()
        }
    }

    @Test
    fun `stable numeric chapter files are migrated to readable directories`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val manager = createManager(
                source = source,
                chapterText = null,
            )
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel Title")
            val chapterId = 2L
            val stableFile = stableChapterFile(tempDir.resolve("downloads").toFile(), novel, chapterId)
                .apply {
                    parentFile?.mkdirs()
                    writeText("stable")
                }
            val readableFile =
                readableChapterFile(
                    tempDir.resolve("downloads").toFile(),
                    source.name,
                    readableNovelDirName(novel),
                    chapterId,
                )

            manager.isChapterDownloaded(novel, chapterId) shouldBe true

            readableFile.exists() shouldBe true
            readableFile.readText() shouldBe "stable"
            stableFile.exists() shouldBe false
        }
    }

    @Test
    fun `failed readable migration removes the truncated copy and keeps serving the stable file`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val manager = createManager(source = source, chapterText = null)
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel Title")
            val chapterId = 2L
            stableChapterFile(tempDir.resolve("downloads").toFile(), novel, chapterId)
                .apply {
                    parentFile?.mkdirs()
                    writeText("stable")
                }
            val readableFile = readableChapterFile(
                tempDir.resolve("downloads").toFile(),
                source.name,
                readableNovelDirName(novel),
                chapterId,
            )

            failOutputStreams = true
            manager.getDownloadedChapterText(novel, chapterId) shouldBe "stable"
            failOutputStreams = false

            // The failed copy must not leave a truncated READABLE file behind: it would shadow
            // the intact STABLE file on every following lookup.
            readableFile.exists() shouldBe false
            manager.getDownloadedChapterText(novel, chapterId) shouldBe "stable"
            readableFile.readText() shouldBe "stable"
        }
    }

    @Test
    fun `deleteNovel does not wipe a same-title novel of the same source`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val manager = createManager(source = source, chapterText = "text")
            val novelA = Novel.create().copy(id = 1L, source = 10L, title = "Same Title")
            val novelB = Novel.create().copy(id = 2L, source = 10L, title = "Same Title")
            val chapterA = NovelChapter.create().copy(id = 11L, novelId = 1L, url = "/a")
            val chapterB = NovelChapter.create().copy(id = 12L, novelId = 2L, url = "/b")

            manager.downloadChapter(novelA, chapterA) shouldBe true
            manager.downloadChapter(novelB, chapterB) shouldBe true

            manager.deleteNovel(novelA)

            // Pre-fix both novels shared "novels/Source A/Same Title" and deleting novel A
            // removed novel B's chapters with it.
            manager.isChapterDownloaded(novelB, chapterB.id) shouldBe true
            manager.getDownloadedChapterText(novelB, chapterB.id) shouldBe "text"
            manager.isChapterDownloaded(novelA, chapterA.id) shouldBe false
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

            val stableChapterFile = stableChapterFile(tempDir.resolve("downloads").toFile(), novel, 2L)
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

    @Test
    fun `failed scan is not repeated within ttl and is retried after it expires`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapterId = 2L

            var now = 1_000_000L
            val manager = createManager(source = source, chapterText = null) { now }

            // First miss walks the whole downloads tree and records a negative result.
            manager.isChapterDownloaded(novel, chapterId) shouldBe false
            val scansAfterFirst = manager.debugScanCount()

            // Second miss within the TTL must be served from the negative cache: no rescan.
            manager.isChapterDownloaded(novel, chapterId) shouldBe false
            manager.debugScanCount() shouldBe scansAfterFirst

            // After the TTL expires the scan runs again.
            now += 61_000L
            manager.isChapterDownloaded(novel, chapterId) shouldBe false
            manager.debugScanCount() shouldBe scansAfterFirst + 1
        }
    }

    @Test
    fun `download clears the negative scan cache so the file is found immediately`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(id = 2L, novelId = 1L, url = "/chapter-2")

            var now = 2_000_000L
            val manager = createManager(source = source, chapterText = "downloaded text") { now }

            // Miss first: the scan finds nothing and the negative cache is populated.
            manager.isChapterDownloaded(novel, chapter.id) shouldBe false
            val scansAfterMiss = manager.debugScanCount()
            (scansAfterMiss > 0) shouldBe true

            // Downloading the chapter must invalidate the negative cache...
            manager.downloadChapter(novel, chapter) shouldBe true

            // ...so the next lookup finds the file via the positive dir cache without a rescan.
            manager.isChapterDownloaded(novel, chapter.id) shouldBe true
            manager.debugScanCount() shouldBe scansAfterMiss
        }
    }

    private fun createManager(
        source: MutableNovelSource,
        chapterText: String?,
        applicationFilesDir: File? = null,
        clock: () -> Long = System::currentTimeMillis,
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
            clock = clock,
        )
    }

    private fun stableChapterFile(baseDir: File, novel: Novel, chapterId: Long): File {
        return File(baseDir, "novels/${novel.source}/${novel.id}/$chapterId.html")
    }

    private fun readableChapterFile(
        baseDir: File,
        sourceName: String,
        novelTitle: String,
        chapterId: Long,
    ): File {
        return File(baseDir, "novels/$sourceName/$novelTitle/$chapterId.html")
    }

    /** READABLE novel directories carry the novel id so same-title novels never share a folder. */
    private fun readableNovelDirName(novel: Novel): String = "${novel.title} [${novel.id}]"

    private fun legacyChapterFile(baseDir: File, novel: Novel, chapterId: Long): File {
        return File(baseDir, "novels/${novel.source}/${novel.id}/$chapterId.html")
    }

    private fun fakeUniFile(file: File): UniFile {
        val normalized = file.absoluteFile
        return mockk(relaxed = true) {
            every { getName() } returns normalized.name
            every { getFilePath() } returns normalized.absolutePath
            every { getParentFile() } answers {
                normalized.parentFile?.let { fakeUniFile(it) }
            }
            every { renameTo(any()) } answers {
                normalized.renameTo(File(normalized.parentFile, firstArg<String>()))
            }
            every { isDirectory() } answers { normalized.isDirectory }
            every { isFile() } answers { normalized.isFile }
            every { exists() } answers { normalized.exists() }
            every { length() } answers { normalized.length() }
            every { canRead() } answers { normalized.canRead() }
            every { canWrite() } answers { normalized.canWrite() }
            // Production TreeUriFile deletes directories recursively; mirror that (plain
            // File.delete() fails on non-empty dirs and broke deleteNovel coverage).
            every { delete() } answers {
                if (normalized.isDirectory) normalized.deleteRecursively() else normalized.delete()
            }
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
            every { openOutputStream() } answers {
                if (failOutputStreams) throw java.io.IOException("disk full")
                FileOutputStream(normalized)
            }
            every { openOutputStream(any()) } answers {
                if (failOutputStreams) throw java.io.IOException("disk full")
                FileOutputStream(normalized, firstArg())
            }
        }
    }

    private class MutableNovelSource(
        override val id: Long,
        var label: String,
    ) : eu.kanade.tachiyomi.novelsource.NovelSource {
        // The readable, stable source name (e.g. "Novel Ninja").
        override val name: String get() = label
        override val lang: String = "en"

        override suspend fun getNovelDetails(novel: eu.kanade.tachiyomi.novelsource.model.SNovel) = novel
        override suspend fun getChapterList(
            novel: eu.kanade.tachiyomi.novelsource.model.SNovel,
        ) = emptyList<eu.kanade.tachiyomi.novelsource.model.SNovelChapter>()
        override suspend fun getChapterText(chapter: eu.kanade.tachiyomi.novelsource.model.SNovelChapter) = ""

        // Real novel sources (NovelConfigurableJsSource, NovelHttpSource, ...) do NOT
        // override toString(), so it yields the volatile default "ClassName@identityHashCode".
        // Mirror that here so the download-dir naming is forced to rely on name, not toString().
        override fun toString(): String =
            "NovelConfigurableJsSource@" + Integer.toHexString(System.identityHashCode(this))
    }
}
