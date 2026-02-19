package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import com.hippo.unifile.UniFile
import eu.kanade.domain.items.novelchapter.model.toSNovelChapter
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class NovelDownloadManager(
    private val application: Application? = runCatching { Injekt.get<Application>() }.getOrNull(),
    private val sourceManager: NovelSourceManager? = runCatching { Injekt.get<NovelSourceManager>() }.getOrNull(),
    private val storageManager: StorageManager? = runCatching { Injekt.get<StorageManager>() }.getOrNull(),
) {

    private val legacyRootDir: File?
        get() = application?.filesDir?.let { File(it, ROOT_DIR_NAME) }

    private val rootDir: UniFile?
        get() = storageManager?.getDownloadsDirectory()?.createDirectory(ROOT_DIR_NAME)

    fun getDownloadedChapterIds(
        novel: Novel,
        chapters: List<NovelChapter>,
    ): Set<Long> {
        return chapters.asSequence()
            .map { it.id }
            .filter { isChapterDownloaded(novel, it) }
            .toSet()
    }

    fun isChapterDownloaded(novel: Novel, chapterId: Long): Boolean {
        return chapterFile(novel, chapterId)?.exists() == true ||
            legacyChapterFile(novel, chapterId)?.exists() == true
    }

    fun getDownloadCount(novel: Novel): Int {
        val scopedCount = novelDirectory(novel)?.listFiles()?.size ?: 0
        if (scopedCount > 0) return scopedCount

        val legacyNovelDir = legacyNovelDirectory(novel)
        return if (legacyNovelDir?.exists() == true) {
            legacyNovelDir.listFiles()?.size ?: 0
        } else {
            0
        }
    }

    fun getDownloadCount(): Int {
        val scopedCount = rootDir
            ?.listFiles()
            ?.sumOf { sourceDir -> sourceDir.listFiles()?.size ?: 0 }
            ?: 0
        if (scopedCount > 0) return scopedCount

        val legacyBaseDir = legacyRootDir
        return if (legacyBaseDir?.exists() == true) {
            legacyBaseDir.listFiles()
                ?.sumOf { sourceDir ->
                    sourceDir.listFiles()
                        ?.sumOf { novelDir -> novelDir.listFiles()?.size ?: 0 }
                        ?: 0
                } ?: 0
        } else {
            0
        }
    }

    fun hasAnyDownloadedChapter(novel: Novel): Boolean {
        return getDownloadCount(novel) > 0
    }

    fun getDownloadSize(novel: Novel): Long {
        val scopedSize = novelDirectory(novel)?.let(::calculateScopedDirectorySize) ?: 0L
        if (scopedSize > 0L) return scopedSize

        val legacyNovelDir = legacyNovelDirectory(novel)
        return if (legacyNovelDir?.exists() == true) {
            calculateLegacyDirectorySize(legacyNovelDir)
        } else {
            0L
        }
    }

    fun getDownloadSize(): Long {
        val scopedSize = rootDir?.let(::calculateScopedDirectorySize) ?: 0L
        if (scopedSize > 0L) return scopedSize

        val legacyBaseDir = legacyRootDir
        return if (legacyBaseDir?.exists() == true) {
            calculateLegacyDirectorySize(legacyBaseDir)
        } else {
            0L
        }
    }

    suspend fun downloadChapter(novel: Novel, chapter: NovelChapter): Boolean {
        val source = sourceManager?.get(novel.source) ?: return false
        val text = source.getChapterText(chapter.toSNovelChapter())
        val file = chapterFile(novel, chapter.id, create = true) ?: return false
        file.openOutputStream()?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(text)
        } ?: return false
        return true
    }

    suspend fun downloadChapters(novel: Novel, chapters: List<NovelChapter>): Set<Long> {
        val downloaded = mutableSetOf<Long>()
        chapters.forEach { chapter ->
            runCatching { downloadChapter(novel, chapter) }
                .onSuccess { success ->
                    if (success) downloaded += chapter.id
                }
        }
        return downloaded
    }

    fun deleteChapter(novel: Novel, chapterId: Long) {
        chapterFile(novel, chapterId)?.delete()
        legacyChapterFile(novel, chapterId)?.delete()
        cleanupDirectories(novel)
    }

    fun deleteChapters(novel: Novel, chapterIds: Collection<Long>) {
        chapterIds.forEach { chapterId ->
            chapterFile(novel, chapterId)?.delete()
            legacyChapterFile(novel, chapterId)?.delete()
        }
        cleanupDirectories(novel)
    }

    fun deleteNovel(novel: Novel) {
        val scopedDir = novelDirectory(novel)
        if (scopedDir?.exists() == true) {
            scopedDir.delete()
        }

        val legacyDir = legacyNovelDirectory(novel)
        if (legacyDir?.exists() == true) {
            legacyDir.deleteRecursively()
        }

        cleanupDirectories(novel)
    }

    fun getDownloadedChapterText(novel: Novel, chapterId: Long): String? {
        chapterFile(novel, chapterId)
            ?.takeIf { it.exists() }
            ?.let { file ->
                runCatching {
                    file.openInputStream()?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
            }
            ?.let { return it }

        val legacyFile = legacyChapterFile(novel, chapterId) ?: return null
        if (!legacyFile.exists()) return null
        return runCatching { legacyFile.readText(Charsets.UTF_8) }.getOrNull()
    }

    private fun chapterFile(
        novel: Novel,
        chapterId: Long,
        create: Boolean = false,
    ): UniFile? {
        val novelDir = novelDirectory(novel, create = create) ?: return null
        val chapterName = "$chapterId.html"
        return if (create) {
            novelDir.findFile(chapterName) ?: novelDir.createFile(chapterName)
        } else {
            novelDir.findFile(chapterName)
        }
    }

    private fun novelDirectory(novel: Novel, create: Boolean = false): UniFile? {
        val baseDir = rootDir ?: return null
        val sourceDir = if (create) {
            baseDir.createDirectory(getSourceDirName(novel))
        } else {
            baseDir.findFile(getSourceDirName(novel))
        } ?: return null
        return if (create) {
            sourceDir.createDirectory(getNovelDirName(novel))
        } else {
            sourceDir.findFile(getNovelDirName(novel))
        }
    }

    private fun legacyChapterFile(novel: Novel, chapterId: Long): File? {
        return legacyNovelDirectory(novel)?.let { File(it, "$chapterId.html") }
    }

    private fun legacyNovelDirectory(novel: Novel): File? {
        val baseDir = legacyRootDir ?: return null
        return File(baseDir, "${novel.source}/${novel.id}")
    }

    private fun cleanupDirectories(novel: Novel) {
        val baseDir = rootDir
        if (baseDir != null) {
            val sourceDirName = getSourceDirName(novel)
            val novelDirName = getNovelDirName(novel)
            val sourceDir = baseDir.findFile(sourceDirName)
            val novelDir = sourceDir?.findFile(novelDirName)
            if (novelDir != null && novelDir.isDirectory && novelDir.listFiles()?.isEmpty() == true) {
                novelDir.delete()
            }
            if (sourceDir != null && sourceDir.isDirectory && sourceDir.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
            }
        }

        val legacyBaseDir = legacyRootDir ?: return
        val legacyNovelDir = File(legacyBaseDir, "${novel.source}/${novel.id}")
        if (legacyNovelDir.exists() && legacyNovelDir.listFiles().isNullOrEmpty()) {
            legacyNovelDir.delete()
        }
        val legacySourceDir = File(legacyBaseDir, "${novel.source}")
        if (legacySourceDir.exists() && legacySourceDir.listFiles().isNullOrEmpty()) {
            legacySourceDir.delete()
        }
    }

    private fun calculateScopedDirectorySize(directory: UniFile): Long {
        if (!directory.exists()) return 0L
        if (!directory.isDirectory) return directory.length().takeIf { it > 0L } ?: 0L
        return directory.listFiles()?.sumOf(::calculateScopedDirectorySize) ?: 0L
    }

    private fun calculateLegacyDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        if (directory.isFile) return directory.length()
        return directory.listFiles()?.sumOf(::calculateLegacyDirectorySize) ?: 0L
    }

    private fun getSourceDirName(novel: Novel): String {
        val sourceName = sourceManager?.get(novel.source)?.toString()?.ifBlank { null } ?: novel.source.toString()
        return DiskUtil.buildValidFilename(sourceName)
    }

    private fun getNovelDirName(novel: Novel): String {
        val title = novel.title.ifBlank { novel.id.toString() }
        return DiskUtil.buildValidFilename(title)
    }

    private companion object {
        const val ROOT_DIR_NAME = "novels"
    }
}
