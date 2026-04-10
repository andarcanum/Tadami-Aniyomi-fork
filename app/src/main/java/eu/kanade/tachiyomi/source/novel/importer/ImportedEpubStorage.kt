package eu.kanade.tachiyomi.source.novel.importer

import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubAsset
import java.io.File

internal class ImportedEpubStorage(
    private val baseDirectory: File,
) {
    fun novelDirectory(novelId: Long): File {
        return File(baseDirectory, novelId.toString()).apply { mkdirs() }
    }

    fun chapterHtmlFile(novelId: Long, chapterId: Long): File {
        val chapterDir = File(novelDirectory(novelId), chapterId.toString()).apply { mkdirs() }
        return File(chapterDir, "index.html")
    }

    fun writeChapter(novelId: Long, chapterId: Long, html: String) {
        chapterHtmlFile(novelId, chapterId).writeText(html)
    }

    fun readChapterHtml(novelId: Long, chapterId: Long): String {
        return chapterHtmlFile(novelId, chapterId).readText()
    }

    fun assetFile(novelId: Long, asset: ImportedEpubAsset): File {
        return File(File(novelDirectory(novelId), "assets"), asset.targetPath)
    }

    fun writeAsset(novelId: Long, asset: ImportedEpubAsset, bytes: ByteArray): File {
        val assetFile = assetFile(novelId, asset)
        assetFile.parentFile?.mkdirs()
        assetFile.writeBytes(bytes)
        return assetFile
    }
}
