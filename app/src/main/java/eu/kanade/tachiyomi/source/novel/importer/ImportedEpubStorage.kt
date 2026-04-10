package eu.kanade.tachiyomi.source.novel.importer

import java.io.File

internal class ImportedEpubStorage(
    private val baseDirectory: File,
) {
    fun novelDirectory(novelId: Long): File {
        return File(baseDirectory, novelId.toString()).apply { mkdirs() }
    }

    fun writeChapter(novelId: Long, chapterId: Long, html: String) {
        val chapterDir = File(novelDirectory(novelId), chapterId.toString()).apply { mkdirs() }
        val indexFile = File(chapterDir, "index.html")
        indexFile.writeText(html)
    }

    fun writeAsset(novelId: Long, asset: ImportedEpubAsset, bytes: ByteArray) {
        val assetDir = File(novelDirectory(novelId), "assets").apply { mkdirs() }
        val assetFile = File(assetDir, asset.targetFileName)
        assetFile.writeBytes(bytes)
    }
}