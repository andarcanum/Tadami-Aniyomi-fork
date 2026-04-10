package eu.kanade.tachiyomi.source.novel.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import eu.kanade.tachiyomi.source.novel.IMPORTED_EPUB_NOVEL_SOURCE_ID
import mihon.core.archive.ArchiveReader
import mihon.core.archive.EpubReader
import org.jsoup.Jsoup
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.util.UUID

internal class ImportedEpubImporter(
    private val context: Context,
    private val parser: ImportedEpubParser,
    private val storage: ImportedEpubStorage,
) {

    private val novelRepository: NovelRepository by injectLazy()
    private val chapterRepository: NovelChapterRepository by injectLazy()
    private val htmlNormalizer = ImportedEpubHtmlNormalizer()

    suspend fun import(uri: Uri): Long {
        val book = parser.parse(uri, getDisplayName(uri))
        val parcelFd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Failed to open EPUB file")

        return parcelFd.use { fd ->
            val archiveReader = ArchiveReader(fd)
            val reader = EpubReader(archiveReader)

            reader.use { epubReader ->
                val novel = Novel.create().copy(
                    title = book.title,
                    author = book.author,
                    description = book.description,
                    source = IMPORTED_EPUB_NOVEL_SOURCE_ID,
                    favorite = true,
                    initialized = true,
                    url = UUID.randomUUID().toString(),
                )

                val novelId = novelRepository.insertNovel(novel)
                    ?: error("Failed to insert imported EPUB novel")

                novelRepository.updateNovel(
                    NovelUpdate(
                        id = novelId,
                        url = novelId.toString(),
                        favorite = true,
                        initialized = true,
                    ),
                )

                val chapterEntries = book.chapters.mapIndexed { index, chapter ->
                    NovelChapter.create().copy(
                        novelId = novelId,
                        sourceOrder = index.toLong(),
                        url = chapter.sourcePath,
                        name = chapter.title,
                        chapterNumber = (index + 1).toDouble(),
                    )
                }

                val insertedChapters = chapterRepository.addAllChapters(chapterEntries)

                val assetPathMap = book.assets.associate { asset ->
                    val bytes = readAssetBytes(epubReader, asset.sourcePath)
                    val storedAssetFile = storage.writeAsset(novelId, asset, bytes)
                    asset.sourcePath to Uri.fromFile(storedAssetFile).toString()
                }

                val chaptersToStore = insertedChapters.mapNotNull { chapter ->
                    val rawHtml = readChapterHtml(epubReader, chapter.url)
                    if (!shouldImportEpubChapter(chapter.url, rawHtml, insertedChapters.size)) {
                        return@mapNotNull null
                    }

                    val normalizedHtml = htmlNormalizer.normalize(
                        rawHtml = rawHtml,
                        chapterSourcePath = chapter.url,
                        chapterAssetMap = assetPathMap,
                    )
                    chapter to normalizedHtml
                }

                chaptersToStore.forEach { (chapter, normalizedHtml) ->
                    storage.writeChapter(novelId, chapter.id, normalizedHtml)
                }

                val skippedChapterIds = insertedChapters
                    .filterNot { chapter -> chaptersToStore.any { it.first.id == chapter.id } }
                    .map { it.id }
                if (skippedChapterIds.isNotEmpty()) {
                    chapterRepository.removeChaptersWithIds(skippedChapterIds)
                }

                chapterRepository.updateAllChapters(
                    chaptersToStore.mapIndexed { index, (chapter, _) ->
                        NovelChapterUpdate(
                            id = chapter.id,
                            url = chapter.id.toString(),
                            sourceOrder = index.toLong(),
                            chapterNumber = (index + 1).toDouble(),
                        )
                    },
                )

                val coverUrl = book.coverFileName?.let { assetPathMap[it] }
                if (coverUrl != null) {
                    novelRepository.updateNovel(
                        NovelUpdate(
                            id = novelId,
                            thumbnailUrl = coverUrl,
                            coverLastModified = Instant.now().toEpochMilli(),
                        ),
                    )
                }

                novelId
            }
        }
    }

    private fun getDisplayName(uri: Uri): String {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )

        cursor?.use {
            val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && it.moveToFirst()) {
                val displayName = it.getString(columnIndex)
                if (!displayName.isNullOrBlank()) {
                    return displayName
                }
            }
        }

        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported.epub"
    }

    private fun readChapterHtml(epubReader: EpubReader, sourcePath: String): String {
        return epubReader.getInputStream(sourcePath)?.use { stream ->
            Jsoup.parse(stream, null, sourcePath).outerHtml()
        } ?: error("Missing EPUB chapter: $sourcePath")
    }

    private fun readAssetBytes(epubReader: EpubReader, sourcePath: String): ByteArray {
        return epubReader.getInputStream(sourcePath)?.use { stream ->
            stream.readBytes()
        } ?: error("Missing EPUB asset: $sourcePath")
    }
}

internal fun shouldImportEpubChapter(
    sourcePath: String,
    rawHtml: String,
    totalChapterCount: Int,
): Boolean {
    if (totalChapterCount <= 1) return true

    val normalizedFileName = sourcePath
        .substringAfterLast('/')
        .substringBeforeLast('.')
        .lowercase()

    val alwaysSkipFileNames = setOf("cover", "title", "titlepage", "nav", "toc", "contents", "copyright", "colophon")
    if (normalizedFileName in alwaysSkipFileNames) {
        return false
    }

    val document = Jsoup.parse(rawHtml)
    val textContent = document.body().text().trim()
    val hasOnlyCoverArt = textContent.isBlank() && document.select("img, svg, image").isNotEmpty()
    return !hasOnlyCoverArt
}
