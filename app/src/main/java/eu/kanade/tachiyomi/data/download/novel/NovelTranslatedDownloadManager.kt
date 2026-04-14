package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.util.storage.DiskUtil
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
        val fileName = buildTranslatedFileName(chapter, format)
        return translatedFile(novel, fileName)?.exists() == true
    }

    fun getTranslatedFile(
        novel: Novel,
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ): UniFile? {
        val fileName = buildTranslatedFileName(chapter, format)
        return translatedFile(novel, fileName)
    }

    fun deleteTranslatedChapter(
        novel: Novel,
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ) {
        val fileName = buildTranslatedFileName(chapter, format)
        translatedFileInDirectory(
            baseDir = rootDir,
            sourceDirName = getStableSourceDirName(novel),
            novelDirName = getStableNovelDirName(novel),
            fileName = fileName,
        )?.delete()
        legacyTranslatedFile(novel, fileName)?.delete()
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

        return runCatching {
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
    }

    private fun exportFile(
        novel: Novel,
        chapter: NovelChapter,
        format: NovelTranslatedDownloadFormat,
    ): UniFile? {
        val baseDir = rootDir ?: return null
        val sourceDir = baseDir.createDirectory(getStableSourceDirName(novel)) ?: return null
        val novelDir = sourceDir.createDirectory(getStableNovelDirName(novel)) ?: return null
        val fileName = buildTranslatedFileName(chapter, format)
        return novelDir.findFile(fileName) ?: novelDir.createFile(fileName)
    }

    private fun translatedFile(
        novel: Novel,
        fileName: String,
    ): UniFile? {
        val stable = translatedFileInDirectory(
            baseDir = rootDir,
            sourceDirName = getStableSourceDirName(novel),
            novelDirName = getStableNovelDirName(novel),
            fileName = fileName,
        )
        if (stable != null) return stable

        return legacyTranslatedFile(novel, fileName)
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
            sourceDirName = getLegacySourceDirName(novel),
            novelDirName = getLegacyNovelDirName(novel),
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
        return DiskUtil.buildValidFilename("$chapterNumber - $chapterName.$ext")
    }

    private fun buildTranslatedText(translatedByIndex: Map<Int, String>): String {
        return translatedByIndex.toSortedMap()
            .values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "\n\n")
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

    private fun getLegacySourceDirName(novel: Novel): String {
        val sourceName = sourceManager?.getOrStub(novel.source)?.toString()?.ifBlank { null }
            ?: novel.source.toString()
        return DiskUtil.buildValidFilename(sourceName)
    }

    private fun getLegacyNovelDirName(novel: Novel): String {
        val title = novel.title.ifBlank { novel.id.toString() }
        return DiskUtil.buildValidFilename(title)
    }

    private companion object {
        const val ROOT_DIR_NAME = "novels_translated"
    }
}
