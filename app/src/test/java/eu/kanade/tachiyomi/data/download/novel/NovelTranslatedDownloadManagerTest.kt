package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path

class NovelTranslatedDownloadManagerTest {

    @field:TempDir
    lateinit var tempDir: Path

    // When true, fakeUniFile output streams fail so export/migration write paths can be exercised.
    private var failOutputStreams = false

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
    fun `exported translated chapter is written under readable source and novel directories`() {
        val source = MutableNovelSource(id = 10L, label = "Source A")
        val manager = createManager(source)
        val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel A")
        val chapter = NovelChapter.create().copy(
            id = 3L,
            novelId = novel.id,
            chapterNumber = 1.0,
            name = "Prologue",
        )

        val readableFile = translatedReadableFile(
            tempDir.resolve("downloads").toFile(),
            source.label,
            novel.title,
            chapter,
        )

        // No stable file should be created
        manager.isTranslatedChapterDownloaded(
            novel = novel,
            chapter = chapter,
            format = NovelTranslatedDownloadFormat.TXT,
        ) shouldBe false

        // Place file in readable dir manually and verify detection works
        readableFile.parentFile?.mkdirs()
        readableFile.writeText("translated content")

        manager.isTranslatedChapterDownloaded(
            novel = novel,
            chapter = chapter,
            format = NovelTranslatedDownloadFormat.TXT,
        ) shouldBe true
        translatedStableFile(tempDir.resolve("downloads").toFile(), novel, chapter).exists() shouldBe false
    }

    @Test
    fun `stable translated files are migrated to readable directories on lookup`() {
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
                writeText("stable content")
            }
        val readableFile = translatedReadableFile(
            tempDir.resolve("downloads").toFile(),
            source.name,
            novel.title,
            chapter,
        )

        manager.isTranslatedChapterDownloaded(
            novel = novel,
            chapter = chapter,
            format = NovelTranslatedDownloadFormat.TXT,
        ) shouldBe true

        readableFile.exists() shouldBe true
        readableFile.readText() shouldBe "stable content"
        stableFile.exists() shouldBe false
    }

    @Test
    fun `deleteTranslatedChapter removes readable and stable files`() {
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
        val readableFile = translatedReadableFile(
            tempDir.resolve("downloads").toFile(),
            source.label,
            novel.title,
            chapter,
        ).apply {
            parentFile?.mkdirs()
            writeText("readable")
        }

        manager.deleteTranslatedChapter(
            novel = novel,
            chapter = chapter,
            format = NovelTranslatedDownloadFormat.TXT,
        )

        stableFile.exists() shouldBe false
        readableFile.exists() shouldBe false
    }

    @Test
    fun `chapter exported under source name directory is detected and deleted`() {
        val source = JsStyleNovelSource(id = 10L)
        val manager = createManager(source)
        val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel A")
        val chapter = NovelChapter.create().copy(
            id = 3L,
            novelId = novel.id,
            chapterNumber = 1.0,
            name = "Prologue",
        )

        val namedFile = translatedReadableFile(
            tempDir.resolve("downloads").toFile(),
            source.name,
            novel.title,
            chapter,
        ).apply {
            parentFile?.mkdirs()
            writeText("translated content")
        }

        manager.isTranslatedChapterDownloaded(
            novel = novel,
            chapter = chapter,
            format = NovelTranslatedDownloadFormat.TXT,
        ) shouldBe true

        manager.deleteTranslatedChapter(
            novel = novel,
            chapter = chapter,
            format = NovelTranslatedDownloadFormat.TXT,
        )

        namedFile.exists() shouldBe false
    }

    @Test
    fun `getDownloadSize includes readable translated exports and updates after delete`() {
        val source = MutableNovelSource(id = 10L, label = "Source A")
        val manager = createManager(source)
        val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel A")
        val chapter = NovelChapter.create().copy(
            id = 3L,
            novelId = novel.id,
            chapterNumber = 1.0,
            name = "Prologue",
        )

        val readableFile = translatedReadableFile(
            tempDir.resolve("downloads").toFile(),
            source.label,
            novel.title,
            chapter,
        ).apply {
            parentFile?.mkdirs()
            writeText("readable export")
        }

        manager.getDownloadSize() shouldBe readableFile.length()

        manager.deleteTranslatedChapter(
            novel = novel,
            chapter = chapter,
            format = NovelTranslatedDownloadFormat.TXT,
        )

        manager.getDownloadSize() shouldBe 0L
    }

    private fun createManager(source: eu.kanade.tachiyomi.novelsource.NovelSource): NovelTranslatedDownloadManager {
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

    private fun ensureStoreDependencies() {
        runCatching { Injekt.get<Json>() }.getOrElse {
            Injekt.addSingleton(
                fullType<Json>(),
                Json {
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                },
            )
        }
        runCatching { Injekt.get<Application>() }.getOrElse {
            val app = mockk<Application>(relaxed = true)
            every { app.cacheDir } returns tempDir.resolve("cache").toFile().apply { mkdirs() }
            every { app.codeCacheDir } returns tempDir.resolve("code-cache").toFile().apply { mkdirs() }
            Injekt.addSingleton(fullType<Application>(), app)
        }
    }

    private fun cacheEntry(chapterId: Long, text: String): GeminiTranslationCacheEntry {
        return GeminiTranslationCacheEntry(
            chapterId = chapterId,
            translatedByIndex = mapOf(0 to text),
            model = "model",
            sourceLang = "English",
            targetLang = "Russian",
            promptMode = GeminiPromptMode.ADULT_18,
        )
    }

    @Test
    fun `same-number same-name chapters do not collide on one translated file`() {
        runBlocking {
            ensureStoreDependencies()
            NovelReaderTranslationDiskCacheStore.clear()
            try {
                val source = JsStyleNovelSource(id = 10L)
                val manager = createManager(source)
                val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel A")
                val chapterA = NovelChapter.create().copy(
                    id = 3L,
                    novelId = 1L,
                    chapterNumber = 1.0,
                    name = "Prologue",
                )
                // Scanlator-branch duplicate: identical display number and name, different id.
                val chapterB = NovelChapter.create().copy(
                    id = 4L,
                    novelId = 1L,
                    chapterNumber = 1.0,
                    name = "Prologue",
                )
                NovelReaderTranslationDiskCacheStore.put(cacheEntry(3L, "alpha"))
                NovelReaderTranslationDiskCacheStore.put(cacheEntry(4L, "beta"))

                manager.exportTranslatedChapter(novel, chapterA, NovelTranslatedDownloadFormat.TXT)
                    .isSuccess shouldBe true
                manager.exportTranslatedChapter(novel, chapterB, NovelTranslatedDownloadFormat.TXT)
                    .isSuccess shouldBe true

                // Pre-fix both chapters mapped to "1 - Prologue.txt": B overwrote A and both
                // looked downloaded from one file.
                val fileA = manager.getTranslatedFile(novel, chapterA, NovelTranslatedDownloadFormat.TXT)
                val fileB = manager.getTranslatedFile(novel, chapterB, NovelTranslatedDownloadFormat.TXT)
                fileA?.getFilePath() shouldNotBe fileB?.getFilePath()
                fileA?.openInputStream()?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } shouldBe "alpha"
                fileB?.openInputStream()?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } shouldBe "beta"
            } finally {
                NovelReaderTranslationDiskCacheStore.clear()
            }
        }
    }

    @Test
    fun `failed translated export leaves no placeholder file`() {
        runBlocking {
            ensureStoreDependencies()
            NovelReaderTranslationDiskCacheStore.clear()
            try {
                val source = JsStyleNovelSource(id = 10L)
                val manager = createManager(source)
                val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel A")
                val chapter = NovelChapter.create().copy(
                    id = 5L,
                    novelId = 1L,
                    chapterNumber = 2.0,
                    name = "Chapter 2",
                )
                NovelReaderTranslationDiskCacheStore.put(cacheEntry(5L, "text"))

                failOutputStreams = true
                manager.exportTranslatedChapter(novel, chapter, NovelTranslatedDownloadFormat.TXT)
                    .isFailure shouldBe true
                failOutputStreams = false

                // Pre-fix the created-but-never-written file survived and read as "downloaded".
                manager.isTranslatedChapterDownloaded(
                    novel = novel,
                    chapter = chapter,
                    format = NovelTranslatedDownloadFormat.TXT,
                ) shouldBe false
            } finally {
                NovelReaderTranslationDiskCacheStore.clear()
            }
        }
    }

    @Test
    fun `failed stable-to-readable migration removes the truncated copy`() {
        runBlocking {
            val source = MutableNovelSource(id = 10L, label = "Source A")
            val manager = createManager(source)
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel A")
            val chapter = NovelChapter.create().copy(
                id = 3L,
                novelId = 1L,
                chapterNumber = 1.0,
                name = "Prologue",
            )
            val stableFile = translatedStableFile(tempDir.resolve("downloads").toFile(), novel, chapter)
                .apply {
                    parentFile?.mkdirs()
                    writeText("stable content")
                }
            val readableFile = translatedReadableFile(
                tempDir.resolve("downloads").toFile(),
                source.name,
                novel.title,
                chapter,
            )

            failOutputStreams = true
            manager.isTranslatedChapterDownloaded(
                novel = novel,
                chapter = chapter,
                format = NovelTranslatedDownloadFormat.TXT,
            ) shouldBe true
            failOutputStreams = false

            // The failed migration must not leave a truncated readable file shadowing the intact
            // stable one on the next lookup.
            readableFile.exists() shouldBe false
            stableFile.exists() shouldBe true
            manager.getTranslatedFile(novel, chapter, NovelTranslatedDownloadFormat.TXT)
                ?.openInputStream()?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } shouldBe "stable content"
        }
    }

    private fun translatedStableFile(baseDir: File, novel: Novel, chapter: NovelChapter): File {
        return File(
            baseDir,
            "novels_translated/${novel.source}/${novel.id}/${translatedFileName(chapter)}",
        )
    }

    private fun translatedReadableFile(
        baseDir: File,
        sourceName: String,
        novelTitle: String,
        chapter: NovelChapter,
    ): File {
        return File(
            baseDir,
            "novels_translated/$sourceName/$novelTitle/${translatedFileName(chapter)}",
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
            // Production TreeUriFile deletes directories recursively; mirror that.
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

    private class JsStyleNovelSource(
        override val id: Long,
    ) : eu.kanade.tachiyomi.novelsource.NovelSource {
        override val name: String = "My Plugin Source"
        override val lang: String = "en"

        override suspend fun getNovelDetails(novel: eu.kanade.tachiyomi.novelsource.model.SNovel) = novel
        override suspend fun getChapterList(
            novel: eu.kanade.tachiyomi.novelsource.model.SNovel,
        ) = emptyList<eu.kanade.tachiyomi.novelsource.model.SNovelChapter>()
        override suspend fun getChapterText(chapter: eu.kanade.tachiyomi.novelsource.model.SNovelChapter) = ""
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
