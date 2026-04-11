package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import com.hippo.unifile.UniFile
import eu.kanade.domain.items.novelchapter.model.toSNovelChapter
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis

class NovelDownloadManager(
    private val application: Application? = runCatching { Injekt.get<Application>() }.getOrNull(),
    private val sourceManager: NovelSourceManager? = runCatching { Injekt.get<NovelSourceManager>() }.getOrNull(),
    private val storageManager: StorageManager? = runCatching { Injekt.get<StorageManager>() }.getOrNull(),
    private val downloadCache: NovelDownloadCache? = runCatching { Injekt.get<NovelDownloadCache>() }.getOrNull(),
    private val chapterFetchTimeoutMillis: Long = DEFAULT_CHAPTER_FETCH_TIMEOUT_MILLIS,
    private val fetchChapterText: suspend (Novel, NovelChapter) -> String? = { novel, chapter ->
        sourceManager?.get(novel.source)?.getChapterText(chapter.toSNovelChapter())
    },
) {

    private val legacyRootDir: File?
        get() = runCatching { application?.filesDir }.getOrNull()?.let { File(it, ROOT_DIR_NAME) }

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
        return resolveChapterFile(novel, chapterId)?.exists() == true ||
            legacyChapterFile(novel, chapterId)?.exists() == true
    }

    fun getDownloadCount(novel: Novel): Int {
        return calculateScopedDirectoryCount(novelDirectory(novel, create = false, useStablePath = true)) +
            calculateScopedDirectoryCount(novelDirectory(novel, create = false, useStablePath = false)) +
            calculateLegacyDirectoryCount(legacyNovelDirectory(novel))
    }

    fun getDownloadCount(): Int {
        return calculateScopedDirectoryCount(rootDir) + calculateLegacyDirectoryCount(legacyRootDir)
    }

    fun hasAnyDownloadedChapter(novel: Novel): Boolean {
        return getDownloadCount(novel) > 0
    }

    fun getDownloadSize(novel: Novel): Long {
        return calculateScopedDirectorySize(novelDirectory(novel, create = false, useStablePath = true)) +
            calculateScopedDirectorySize(novelDirectory(novel, create = false, useStablePath = false)) +
            calculateLegacyDirectorySize(legacyNovelDirectory(novel))
    }

    fun getDownloadSize(): Long {
        return calculateScopedDirectorySize(rootDir) + calculateLegacyDirectorySize(legacyRootDir)
    }

    suspend fun downloadChapter(novel: Novel, chapter: NovelChapter): Boolean {
        var fetchElapsed = 0L
        val text = try {
            var fetchedText: String? = null
            fetchElapsed = measureTimeMillis {
                fetchedText = withTimeout(chapterFetchTimeoutMillis) {
                    fetchChapterText(novel, chapter)
                }
            }
            fetchedText
        } catch (_: TimeoutCancellationException) {
            logcat(LogPriority.WARN) {
                "Novel download fetch timed out: novel=${novel.id}, chapter=${chapter.id}, timeoutMs=$chapterFetchTimeoutMillis"
            }
            return false
        } ?: return false
        coroutineContext.ensureActive()
        val file = chapterFile(novel, chapter.id, create = true) ?: return false
        var writeElapsed = 0L
        writeElapsed = measureTimeMillis {
            val outputStream = file.openOutputStream()
            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(text)
            }
        }
        logcat(LogPriority.DEBUG) {
            "Novel download completed: novel=${novel.id}, chapter=${chapter.id}, fetchMs=$fetchElapsed, writeMs=$writeElapsed, chars=${text.length}"
        }
        downloadCache?.onChaptersChanged(
            novel = novel,
            chapterIds = setOf(chapter.id),
            downloaded = true,
        )
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
        findChapterFile(novel, chapterId)?.delete()
        legacyChapterFile(novel, chapterId)?.delete()
        cleanupDirectories(novel)
        downloadCache?.onChaptersChanged(
            novel = novel,
            chapterIds = setOf(chapterId),
            downloaded = false,
        )
    }

    fun deleteChapters(novel: Novel, chapterIds: Collection<Long>) {
        chapterIds.forEach { chapterId ->
            findChapterFile(novel, chapterId)?.delete()
            legacyChapterFile(novel, chapterId)?.delete()
        }
        cleanupDirectories(novel)
        downloadCache?.onChaptersChanged(
            novel = novel,
            chapterIds = chapterIds.toSet(),
            downloaded = false,
        )
    }

    fun deleteNovel(novel: Novel) {
        val scopedDir = novelDirectory(novel)
        if (scopedDir?.exists() == true) {
            scopedDir.delete()
        }

        val legacyScopedDir = novelDirectory(novel, create = false, useStablePath = false)
        if (legacyScopedDir?.exists() == true) {
            legacyScopedDir.delete()
        }

        val legacyDir = legacyNovelDirectory(novel)
        if (legacyDir?.exists() == true) {
            legacyDir.deleteRecursively()
        }

        cleanupDirectories(novel)
        downloadCache?.onNovelRemoved(novel)
    }

    fun getDownloadedChapterText(novel: Novel, chapterId: Long): String? {
        resolveChapterFile(novel, chapterId)
            ?.takeIf { it.exists() }
            ?.let { file ->
                runCatching {
                    val inputStream = file.openInputStream()
                    inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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
        useStablePath: Boolean = true,
    ): UniFile? {
        val novelDir = novelDirectory(novel, create = create, useStablePath = useStablePath) ?: return null
        val chapterName = "$chapterId.html"
        return if (create) {
            novelDir.findFile(chapterName) ?: novelDir.createFile(chapterName)
        } else {
            novelDir.findFile(chapterName)
        }
    }

    private fun resolveChapterFile(novel: Novel, chapterId: Long): UniFile? {
        chapterFile(novel, chapterId)?.let { return it }
        val legacyFile = chapterFile(novel, chapterId, useStablePath = false) ?: return null
        val stableFile = chapterFile(novel, chapterId, create = true) ?: return legacyFile
        return runCatching {
            legacyFile.openInputStream().use { input ->
                stableFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!legacyFile.delete()) {
                logcat(LogPriority.WARN) {
                    "NovelDownloadManager: migrated chapter=$chapterId but could not remove legacy file"
                }
            }
            stableFile
        }.getOrElse { legacyFile }
    }

    private fun findChapterFile(novel: Novel, chapterId: Long): UniFile? {
        return chapterFile(novel, chapterId) ?: chapterFile(novel, chapterId, useStablePath = false)
    }

    private fun legacyChapterFile(novel: Novel, chapterId: Long): File? {
        return legacyNovelDirectory(novel)?.let { File(it, "$chapterId.html") }
    }

    private fun novelDirectory(
        novel: Novel,
        create: Boolean = false,
        useStablePath: Boolean = true,
    ): UniFile? {
        val baseDir = rootDir ?: return null
        val sourceDirName = if (useStablePath) {
            getStableSourceDirName(novel)
        } else {
            getLegacySourceDirName(novel)
        }
        val novelDirName = if (useStablePath) {
            getStableNovelDirName(novel)
        } else {
            getLegacyNovelDirName(novel)
        }
        val sourceDir = if (create) {
            baseDir.createDirectory(sourceDirName)
        } else {
            baseDir.findFile(sourceDirName)
        } ?: return null
        return if (create) {
            sourceDir.createDirectory(novelDirName)
        } else {
            sourceDir.findFile(novelDirName)
        }
    }

    private fun cleanupDirectories(novel: Novel) {
        listOf(true, false).forEach { useStablePath ->
            val baseDir = rootDir ?: return@forEach
            val sourceDirName = if (useStablePath) {
                getStableSourceDirName(novel)
            } else {
                getLegacySourceDirName(novel)
            }
            val novelDirName = if (useStablePath) {
                getStableNovelDirName(novel)
            } else {
                getLegacyNovelDirName(novel)
            }
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
        val legacyNovelDir = legacyNovelDirectory(novel)
        if (legacyNovelDir?.exists() == true && legacyNovelDir.listFiles().isNullOrEmpty()) {
            legacyNovelDir.delete()
        }
        val legacySourceDir = File(legacyBaseDir, "${novel.source}")
        if (legacySourceDir.exists() && legacySourceDir.listFiles().isNullOrEmpty()) {
            legacySourceDir.delete()
        }
    }

    private fun calculateScopedDirectoryCount(directory: UniFile?): Int {
        if (directory == null || !directory.exists()) return 0
        if (!directory.isDirectory) return 1
        return directory.listFiles()?.sumOf(::calculateScopedDirectoryCount) ?: 0
    }

    private fun calculateScopedDirectorySize(directory: UniFile?): Long {
        if (directory == null || !directory.exists()) return 0L
        if (!directory.isDirectory) return directory.length().takeIf { it > 0L } ?: 0L
        return directory.listFiles()?.sumOf(::calculateScopedDirectorySize) ?: 0L
    }

    private fun calculateLegacyDirectoryCount(directory: File?): Int {
        if (directory == null || !directory.exists()) return 0
        if (directory.isFile) return 1
        return directory.listFiles()?.sumOf(::calculateLegacyDirectoryCount) ?: 0
    }

    private fun calculateLegacyDirectorySize(directory: File?): Long {
        if (directory == null || !directory.exists()) return 0L
        if (directory.isFile) return directory.length()
        return directory.listFiles()?.sumOf(::calculateLegacyDirectorySize) ?: 0L
    }

    private fun getStableSourceDirName(novel: Novel): String {
        return DiskUtil.buildValidFilename(novel.source.toString())
    }

    private fun getStableNovelDirName(novel: Novel): String {
        return DiskUtil.buildValidFilename(novel.id.toString())
    }

    private fun getLegacySourceDirName(novel: Novel): String {
        val source = sourceManager?.getOrStub(novel.source)
        val sourceName = source?.toString()?.ifBlank { null } ?: novel.source.toString()
        return DiskUtil.buildValidFilename(sourceName)
    }

    private fun getLegacyNovelDirName(novel: Novel): String {
        val title = novel.title.ifBlank { novel.id.toString() }
        return DiskUtil.buildValidFilename(title)
    }

    private fun legacyNovelDirectory(novel: Novel): File? {
        val baseDir = legacyRootDir ?: return null
        return File(baseDir, "${novel.source}/${novel.id}")
    }

    private companion object {
        const val ROOT_DIR_NAME = "novels"
        const val DEFAULT_CHAPTER_FETCH_TIMEOUT_MILLIS = 30_000L
    }
}
