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
import java.util.concurrent.ConcurrentHashMap
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

    @Volatile
    private var cachedTotalCount: Int? = null

    @Volatile
    private var cachedTotalSize: Long? = null

    // Last known on-disk novel directory per novel id, remembered on write or found by scan.
    // Protects reads against READABLE directory-name drift (stub source name when the source
    // is not registered at read time, renamed novel title, etc.). See issue #143.
    private val resolvedNovelDirCache = ConcurrentHashMap<Long, UniFile>()

    fun invalidateCache() {
        cachedTotalCount = null
        cachedTotalSize = null
    }

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

    fun getDownloadedChapterIds(novel: Novel): Set<Long> {
        val chapterIds = mutableSetOf<Long>()
        scopedNovelDirectories(novel).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name?.endsWith(".html") == true) {
                    file.name?.substringBeforeLast(".html")?.toLongOrNull()?.let {
                        chapterIds.add(it)
                    }
                }
            }
        }
        legacyNovelDirectory(novel)?.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".html")) {
                file.name.substringBeforeLast(".html").toLongOrNull()?.let {
                    chapterIds.add(it)
                }
            }
        }
        return chapterIds
    }

    fun isChapterDownloaded(novel: Novel, chapterId: Long): Boolean {
        return resolveChapterFile(novel, chapterId)?.exists() == true ||
            legacyChapterFile(novel, chapterId)?.exists() == true
    }

    fun getDownloadCount(novel: Novel): Int {
        return scopedNovelDirectories(novel).sumOf(::calculateScopedDirectoryCount) +
            calculateLegacyDirectoryCount(legacyNovelDirectory(novel))
    }

    fun getDownloadCount(): Int {
        cachedTotalCount?.let { return it }
        val count = calculateScopedDirectoryCount(rootDir) + calculateLegacyDirectoryCount(legacyRootDir)
        cachedTotalCount = count
        return count
    }

    fun hasAnyDownloadedChapter(novel: Novel): Boolean {
        return getDownloadCount(novel) > 0
    }

    fun getDownloadSize(novel: Novel): Long {
        return scopedNovelDirectories(novel).sumOf(::calculateScopedDirectorySize) +
            calculateLegacyDirectorySize(legacyNovelDirectory(novel))
    }

    fun getDownloadSize(): Long {
        cachedTotalSize?.let { return it }
        val size = calculateScopedDirectorySize(rootDir) + calculateLegacyDirectorySize(legacyRootDir)
        cachedTotalSize = size
        return size
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
        file.parentFile?.let { resolvedNovelDirCache[novel.id] = it }
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
        invalidateCache()
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
        invalidateCache()
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
        invalidateCache()
        cleanupDirectories(novel)
        downloadCache?.onChaptersChanged(
            novel = novel,
            chapterIds = chapterIds.toSet(),
            downloaded = false,
        )
    }

    fun deleteNovel(novel: Novel) {
        NovelDownloadPath.entries.forEach { path ->
            val scopedDir = novelDirectory(novel, path = path)
            if (scopedDir?.exists() == true) {
                scopedDir.delete()
            }
        }

        val legacyDir = legacyNovelDirectory(novel)
        if (legacyDir?.exists() == true) {
            legacyDir.deleteRecursively()
        }

        resolvedNovelDirCache.remove(novel.id)
        invalidateCache()
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
        path: NovelDownloadPath = NovelDownloadPath.READABLE,
    ): UniFile? {
        val novelDir = novelDirectory(novel, create = create, path = path) ?: return null
        val chapterName = "$chapterId.html"
        return if (create) {
            novelDir.findFile(chapterName) ?: novelDir.createFile(chapterName)
        } else {
            novelDir.findFile(chapterName)
        }
    }

    private fun resolveChapterFile(novel: Novel, chapterId: Long): UniFile? {
        chapterFile(novel, chapterId)?.let { return it }
        val stableFile = chapterFile(novel, chapterId, path = NovelDownloadPath.STABLE_ID)
            ?: return findChapterFileByScan(novel, chapterId)
        val readableFile = chapterFile(novel, chapterId, create = true) ?: return stableFile
        return runCatching {
            stableFile.openInputStream().use { input ->
                readableFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!stableFile.delete()) {
                logcat(LogPriority.WARN) {
                    "NovelDownloadManager: migrated chapter=$chapterId but could not remove legacy file"
                }
            }
            readableFile
        }.getOrElse { stableFile }
    }

    private fun findChapterFile(novel: Novel, chapterId: Long): UniFile? {
        return chapterFile(novel, chapterId)
            ?: chapterFile(novel, chapterId, path = NovelDownloadPath.STABLE_ID)
            ?: findChapterFileByScan(novel, chapterId)
    }

    /**
     * Name-independent lookup: walks the downloads root
     * ("novels/<source dir>/<novel dir>/<chapterId>.html") looking for the chapter file.
     * Chapter ids are globally unique database ids, so the first match is guaranteed to
     * belong to this chapter even if the parent directory names have drifted (e.g. the
     * source was not registered at read time and getOrStub() produced a stub name, or the
     * novel title changed after the download). Only runs when the regular READABLE and
     * STABLE_ID lookups miss; the found directory is memoized per novel to avoid rescans.
     */
    private fun findChapterFileByScan(novel: Novel, chapterId: Long): UniFile? {
        val chapterName = "$chapterId.html"

        resolvedNovelDirCache[novel.id]?.let { cachedDir ->
            if (cachedDir.exists()) {
                cachedDir.findFile(chapterName)?.takeIf { it.isFile }?.let { return it }
            } else {
                resolvedNovelDirCache.remove(novel.id)
            }
        }

        val baseDir = rootDir ?: return null
        baseDir.listFiles()?.forEach { sourceDir ->
            if (!sourceDir.isDirectory) return@forEach
            sourceDir.listFiles()?.forEach { novelDir ->
                if (!novelDir.isDirectory) return@forEach
                val file = novelDir.findFile(chapterName)
                if (file != null && file.isFile) {
                    resolvedNovelDirCache[novel.id] = novelDir
                    return file
                }
            }
        }
        return null
    }

    private fun legacyChapterFile(novel: Novel, chapterId: Long): File? {
        return legacyNovelDirectory(novel)?.let { File(it, "$chapterId.html") }
    }

    private fun novelDirectory(
        novel: Novel,
        create: Boolean = false,
        path: NovelDownloadPath = NovelDownloadPath.READABLE,
    ): UniFile? {
        val baseDir = rootDir ?: return null
        val sourceDirName = getSourceDirName(novel, path)
        val novelDirName = getNovelDirName(novel, path)
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
        NovelDownloadPath.entries.forEach { path ->
            val baseDir = rootDir ?: return@forEach
            val sourceDirName = getSourceDirName(novel, path)
            val novelDirName = getNovelDirName(novel, path)
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

    private fun scopedNovelDirectories(novel: Novel): List<UniFile> {
        val namedDirectories = NovelDownloadPath.entries
            .mapNotNull { path -> novelDirectory(novel, create = false, path = path) }
        val cachedDirectory = resolvedNovelDirCache[novel.id]?.takeIf { it.exists() }
        return (namedDirectories + listOfNotNull(cachedDirectory))
            .distinctBy { directory -> directory.filePath ?: directory.name }
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

    private fun getSourceDirName(novel: Novel, path: NovelDownloadPath): String {
        return when (path) {
            NovelDownloadPath.READABLE -> getReadableSourceDirName(novel)
            NovelDownloadPath.STABLE_ID -> DiskUtil.buildValidFilename(novel.source.toString())
        }
    }

    private fun getNovelDirName(novel: Novel, path: NovelDownloadPath): String {
        return when (path) {
            NovelDownloadPath.READABLE -> getReadableNovelDirName(novel)
            NovelDownloadPath.STABLE_ID -> DiskUtil.buildValidFilename(novel.id.toString())
        }
    }

    private fun getReadableSourceDirName(novel: Novel): String {
        val source = sourceManager?.getOrStub(novel.source)
        // Use the stable, human-readable source name (e.g. "Novel Ninja").
        // NOTE: novel sources do NOT override toString(), so source.toString()
        // returns "ClassName@identityHashCode" (e.g. NovelConfigurableJsSource@19c96ed).
        // identityHashCode changes per instance/app restart, producing a *different*
        // folder name for the same extension every run -> reindex can't match existing
        // downloads and new duplicate folders get created. source.name is stable.
        val sourceName = source?.name?.ifBlank { null } ?: novel.source.toString()
        return DiskUtil.buildValidFilename(sourceName)
    }

    private fun getReadableNovelDirName(novel: Novel): String {
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

    private enum class NovelDownloadPath {
        READABLE,
        STABLE_ID,
    }
}
