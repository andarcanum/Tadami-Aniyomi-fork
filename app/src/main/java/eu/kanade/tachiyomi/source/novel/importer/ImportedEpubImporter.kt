package eu.kanade.tachiyomi.source.novel.importer

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubBook
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import uy.kohesive.injekt.injectLazy

internal class ImportedEpubImporter(
    private val context: Context,
    private val parser: ImportedEpubParser,
    private val storage: ImportedEpubStorage,
) {

    private val novelRepository: NovelRepository by injectLazy()
    private val chapterRepository: NovelChapterRepository by injectLazy()
    private val networkToLocal: NetworkToLocalNovel by injectLazy()

    suspend fun import(uri: Uri, fileName: String): Long {
        // Parse the EPUB
        val book = parser.parse(uri, fileName)

        // Create novel entry
        val novel = Novel.create().apply {
            title = book.title
            author = book.author
            description = book.description
            source = IMPORTED_EPUB_NOVEL_SOURCE_ID
        }

        val novelId = novelRepository.insert(novel).insertedId ?: error("Failed to insert novel")

        // Create chapter entries and persist HTML
        book.chapters.forEachIndexed { index, chapter ->
            val chapterEntry = tachiyomi.domain.items.chapter.model.Chapter.create().apply {
                this.novelId = novelId
                this.sourceOrder = index.toLong()
                this.name = chapter.title
                this.chapterNumber = (index + 1).toFloat()
            }

            val chapterId = chapterRepository.insert(chapterEntry).insertedId ?: error("Failed to insert chapter")

            // Read and store chapter HTML
            val html = readChapterHtml(uri, chapter.sourcePath)
            storage.writeChapter(novelId, chapterId, html)
        }

        // Store assets
        book.assets.forEach { asset ->
            val bytes = readAssetBytes(uri, asset.sourcePath)
            storage.writeAsset(novelId, asset, bytes)
        }

        return novelId
    }

    private fun readChapterHtml(epubUri: Uri, sourcePath: String): String {
        // TODO: Implement reading chapter HTML from EPUB
        return "<html><body>Chapter content for $sourcePath</body></html>"
    }

    private fun readAssetBytes(epubUri: Uri, sourcePath: String): ByteArray {
        // TODO: Implement reading asset bytes from EPUB
        return "asset data".toByteArray()
    }

    companion object {
        const val IMPORTED_EPUB_NOVEL_SOURCE_ID = 999L // TODO: Define proper constant
    }
}