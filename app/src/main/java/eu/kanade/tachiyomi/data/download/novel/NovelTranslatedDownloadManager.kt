package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class NovelTranslatedDownloadFormat {
    TXT,
    DOCX,
}

class NovelTranslatedDownloadManager(
    private val application: Application? = runCatching { Injekt.get<Application>() }.getOrNull(),
    private val sourceManager: NovelSourceManager? = runCatching { Injekt.get<NovelSourceManager>() }.getOrNull(),
    private val storageManager: StorageManager? = runCatching { Injekt.get<StorageManager>() }.getOrNull(),
) {

    private val downloadedIdsCache = mutableMapOf<Pair<Long, NovelTranslatedDownloadFormat>, Set<Long>>()

    @Volatile
    private var cachedTotalSize: Long? = null

    private val rootDir: UniFile?
        get() = storageManager?.getDownloadsDirectory()?.createDirectory(ROOT_DIR_NAME)

    fun hasTranslationCache(chapterId: Long): Boolean {
        return NovelReaderTranslationDiskCacheStore.get(chapterId)
            ?.translatedByIndex
            ?.isNotEmpty() == true
    }

    fun isTranslatedChapterDownloaded(
        novel: Novel,
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ): Boolean {
        return translatedFileNameVariants(chapter, format).any { fileName ->
            translatedFile(novel, fileName)?.exists() == true
        }
    }

    fun getTranslatedChapterIds(
        novel: Novel,
        chapters: List<NovelChapter>,
        format: NovelTranslatedDownloadFormat,
    ): Set<Long> {
        if (chapters.isEmpty()) return emptySet()

        val cacheKey = Pair(novel.id, format)
        synchronized(downloadedIdsCache) {
            val cached = downloadedIdsCache[cacheKey]
            if (cached != null) {
                return cached
            }
        }

        val translatedFileNames = translatedNovelDirectories(novel)
            .asSequence()
            .flatMap { directory -> directory.listFiles()?.asSequence().orEmpty() }
            .filter { file -> file.isFile }
            .mapNotNull { file -> file.name }
            .toSet()

        val result = if (translatedFileNames.isEmpty()) {
            emptySet()
        } else {
            chapters
                .asSequence()
                .filter { chapter ->
                    translatedFileNameVariants(chapter, format).any { it in translatedFileNames }
                }
                .map { chapter -> chapter.id }
                .toSet()
        }

        synchronized(downloadedIdsCache) {
            downloadedIdsCache[cacheKey] = result
        }
        return result
    }

    fun getTranslatedFile(
        novel: Novel,
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ): UniFile? {
        return translatedFileNameVariants(chapter, format)
            .firstNotNullOfOrNull { fileName -> translatedFile(novel, fileName) }
    }

    fun deleteTranslatedChapter(
        novel: Novel,
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ) {
        translatedFileNameVariants(chapter, format).forEach { fileName ->
            translatedFileInDirectory(
                baseDir = rootDir,
                sourceDirName = getReadableSourceDirName(novel),
                novelDirName = getReadableNovelDirName(novel),
                fileName = fileName,
            )?.delete()
            getLegacyReadableSourceDirName(novel)?.let { legacySourceDirName ->
                translatedFileInDirectory(
                    baseDir = rootDir,
                    sourceDirName = legacySourceDirName,
                    novelDirName = getReadableNovelDirName(novel),
                    fileName = fileName,
                )?.delete()
            }
            translatedFileInDirectory(
                baseDir = rootDir,
                sourceDirName = getStableSourceDirName(novel),
                novelDirName = getStableNovelDirName(novel),
                fileName = fileName,
            )?.delete()
        }

        synchronized(downloadedIdsCache) {
            downloadedIdsCache.remove(Pair(novel.id, format))
        }
        cachedTotalSize = null
    }

    suspend fun exportTranslatedChapter(
        novel: Novel,
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ): Result<Unit> {
        val cached = NovelReaderTranslationDiskCacheStore.get(chapter.id)
            ?: return Result.failure(IllegalStateException("Translation cache not found"))
        if (cached.translatedByIndex.isEmpty()) {
            return Result.failure(IllegalStateException("Translation cache is empty"))
        }

        val content = buildTranslatedText(cached.translatedByIndex)
        if (content.isBlank()) {
            return Result.failure(IllegalStateException("Translated text is empty"))
        }

        val file = exportFile(novel, chapter, format)
            ?: return Result.failure(IllegalStateException("Unable to create destination file"))

        val result = runCatching {
            when (format) {
                NovelTranslatedDownloadFormat.TXT -> {
                    val outputStream = file.openOutputStream()
                    outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(content)
                    }
                }
                NovelTranslatedDownloadFormat.DOCX -> {
                    val docxBytes = buildDocxBytes(content)
                    val output = file.openOutputStream()
                    output.use {
                        output.write(docxBytes)
                    }
                }
            }
        }

        if (result.isSuccess) {
            // Remove a pre-id-scheme duplicate so the same chapter is not listed/counted twice.
            runCatching {
                val legacyName = buildLegacyTranslatedFileName(chapter, format)
                if (legacyName != buildTranslatedFileName(chapter, format)) {
                    translatedFile(novel, legacyName)?.delete()
                }
            }
            synchronized(downloadedIdsCache) {
                downloadedIdsCache.remove(Pair(novel.id, format))
            }
            cachedTotalSize = null
        } else {
            // Never leave the empty placeholder created by exportFile behind: its mere existence
            // reads as "downloaded" while the chapter has no content on disk.
            runCatching { file.delete() }
            logcat(LogPriority.WARN) {
                "NovelTranslatedDownloadManager: export failed for chapter=${chapter.id}, placeholder removed"
            }
        }

        return result
    }

    fun getDownloadSize(): Long {
        cachedTotalSize?.let { return it }
        val size = calculateScopedDirectorySize(rootDir)
        cachedTotalSize = size
        return size
    }

    private fun exportFile(
        novel: Novel,
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ): UniFile? {
        val baseDir = rootDir ?: return null
        val sourceDir = baseDir.createDirectory(getReadableSourceDirName(novel)) ?: return null
        val novelDir = sourceDir.createDirectory(getReadableNovelDirName(novel)) ?: return null
        val fileName = buildTranslatedFileName(chapter, format)
        return novelDir.findFile(fileName) ?: novelDir.createFile(fileName)
    }

    private fun translatedFile(
        novel: Novel,
        fileName: String,
    ): UniFile? {
        val readable = translatedFileInDirectory(
            baseDir = rootDir,
            sourceDirName = getReadableSourceDirName(novel),
            novelDirName = getReadableNovelDirName(novel),
            fileName = fileName,
        )
        if (readable != null) return readable

        val legacyReadable = getLegacyReadableSourceDirName(novel)?.let { legacySourceDirName ->
            translatedFileInDirectory(
                baseDir = rootDir,
                sourceDirName = legacySourceDirName,
                novelDirName = getReadableNovelDirName(novel),
                fileName = fileName,
            )
        }
        if (legacyReadable != null) return legacyReadable

        val stableFile = translatedFileInDirectory(
            baseDir = rootDir,
            sourceDirName = getStableSourceDirName(novel),
            novelDirName = getStableNovelDirName(novel),
            fileName = fileName,
        ) ?: return null

        return migrateToReadable(novel, stableFile, fileName) ?: stableFile
    }

    private fun migrateToReadable(novel: Novel, stableFile: UniFile, fileName: String): UniFile? {
        val baseDir = rootDir ?: return null
        val sourceDir = baseDir.createDirectory(getReadableSourceDirName(novel)) ?: return null
        val novelDir = sourceDir.createDirectory(getReadableNovelDirName(novel)) ?: return null
        val readableFile = novelDir.findFile(fileName) ?: novelDir.createFile(fileName) ?: return null
        return runCatching {
            stableFile.openInputStream().use { input ->
                readableFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!stableFile.delete()) {
                logcat(LogPriority.WARN) {
                    "NovelTranslatedDownloadManager: migrated $fileName but could not remove stable file"
                }
            }
            readableFile
        }.getOrElse { error ->
            // A partially copied readable file would shadow the intact stable file on the next
            // lookup (the readable branch runs first), so never leave the truncated copy behind.
            runCatching { readableFile.delete() }
            logcat(LogPriority.WARN, error) {
                "NovelTranslatedDownloadManager: failed to migrate $fileName to the readable dir"
            }
            null
        }
    }

    private fun translatedNovelDirectories(novel: Novel): List<UniFile> {
        val baseDir = rootDir ?: return emptyList()
        return listOfNotNull(
            baseDir.findFile(getReadableSourceDirName(novel))
                ?.findFile(getReadableNovelDirName(novel)),
            getLegacyReadableSourceDirName(novel)
                ?.let { legacySourceDirName ->
                    baseDir.findFile(legacySourceDirName)
                        ?.findFile(getReadableNovelDirName(novel))
                },
            baseDir.findFile(getStableSourceDirName(novel))
                ?.findFile(getStableNovelDirName(novel)),
        ).distinctBy { it.filePath ?: it.name }
    }

    private fun translatedFileInDirectory(
        baseDir: UniFile?,
        sourceDirName: String,
        novelDirName: String,
        fileName: String,
    ): UniFile? {
        val sourceDir = baseDir?.findFile(sourceDirName) ?: return null
        val novelDir = sourceDir.findFile(novelDirName) ?: return null
        return novelDir.findFile(fileName)
    }

    private fun legacyTranslatedFile(
        novel: Novel,
        fileName: String,
    ): UniFile? {
        return translatedFileInDirectory(
            baseDir = rootDir,
            sourceDirName = getReadableSourceDirName(novel),
            novelDirName = getReadableNovelDirName(novel),
            fileName = fileName,
        )
    }

    private fun buildTranslatedFileName(
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ): String {
        val ext = if (format == NovelTranslatedDownloadFormat.TXT) "txt" else "docx"
        val chapterNumber = formatChapterNumber(chapter.chapterNumber)
        val chapterName = chapter.name.ifBlank { chapter.id.toString() }
        // The chapter id suffix keeps same-number/same-name chapters (scanlator branches,
        // duplicates) from colliding on one file: overwrite on export, cross-delete, and a false
        // "downloaded" state for both branches.
        return DiskUtil.buildValidFilename("$chapterNumber - $chapterName [${chapter.id}].$ext")
    }

    /** Pre-id-scheme display-name file; still recognized for lookup and removed on re-export. */
    private fun buildLegacyTranslatedFileName(
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ): String {
        val ext = if (format == NovelTranslatedDownloadFormat.TXT) "txt" else "docx"
        val chapterNumber = formatChapterNumber(chapter.chapterNumber)
        val chapterName = chapter.name.ifBlank { chapter.id.toString() }
        return DiskUtil.buildValidFilename("$chapterNumber - $chapterName.$ext")
    }

    private fun translatedFileNameVariants(
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ): List<String> {
        return listOf(
            buildTranslatedFileName(chapter, format),
            buildLegacyTranslatedFileName(chapter, format),
        ).distinct()
    }

    private fun buildTranslatedText(translatedByIndex: Map<Int, String>): String {
        return translatedByIndex.toSortedMap()
            .values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "\n\n")
    }

    private fun calculateScopedDirectorySize(directory: UniFile?): Long {
        if (directory == null || !directory.exists()) return 0L
        if (!directory.isDirectory) return directory.length().takeIf { it > 0L } ?: 0L
        return directory.listFiles()?.sumOf(::calculateScopedDirectorySize) ?: 0L
    }

    private fun buildDocxBytes(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            addZipEntry(
                zip = zip,
                name = "[Content_Types].xml",
                value = """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                """.trimIndent(),
            )
            addZipEntry(
                zip = zip,
                name = "_rels/.rels",
                value = """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                """.trimIndent(),
            )

            val body = text.split("\n\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(separator = "") { paragraph ->
                    "<w:p><w:r><w:t xml:space=\"preserve\">${escapeXml(paragraph)}</w:t></w:r></w:p>"
                }

            addZipEntry(
                zip = zip,
                name = "word/document.xml",
                value = """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        $body
                        <w:sectPr/>
                      </w:body>
                    </w:document>
                """.trimIndent(),
            )
        }
        return output.toByteArray()
    }

    private fun addZipEntry(
        zip: ZipOutputStream,
        name: String,
        value: String,
    ) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatChapterNumber(chapterNumber: Double): String {
        return if (chapterNumber % 1.0 == 0.0) {
            chapterNumber.toLong().toString()
        } else {
            chapterNumber.toString()
        }
    }

    private fun getStableSourceDirName(novel: Novel): String {
        return DiskUtil.buildValidFilename(novel.source.toString())
    }

    private fun getStableNovelDirName(novel: Novel): String {
        return DiskUtil.buildValidFilename(novel.id.toString())
    }

    private fun getReadableSourceDirName(novel: Novel): String {
        val sourceName = sourceManager?.getOrStub(novel.source)?.name?.ifBlank { null }
            ?: novel.source.toString()
        return DiskUtil.buildValidFilename(sourceName)
    }

    private fun getLegacyReadableSourceDirName(novel: Novel): String? {
        val legacyName = sourceManager?.getOrStub(novel.source)?.toString()?.ifBlank { null }
            ?: return null
        val legacyDirName = DiskUtil.buildValidFilename(legacyName)
        if (legacyDirName == getReadableSourceDirName(novel)) return null
        return legacyDirName
    }

    private fun getReadableNovelDirName(novel: Novel): String {
        val title = novel.title.ifBlank { novel.id.toString() }
        return DiskUtil.buildValidFilename(title)
    }

    private companion object {
        const val ROOT_DIR_NAME = "novels_translated"
    }
}
