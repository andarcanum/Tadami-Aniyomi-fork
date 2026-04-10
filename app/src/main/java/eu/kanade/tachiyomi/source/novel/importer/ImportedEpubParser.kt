package eu.kanade.tachiyomi.source.novel.importer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubAsset
import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubBook
import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubChapter
import mihon.core.archive.ArchiveReader
import mihon.core.archive.EpubReader
import org.jsoup.Jsoup

internal class ImportedEpubParser(
    private val context: Context,
) {

    fun parse(epubUri: Uri, fallbackFileName: String): ImportedEpubBook {
        // Open EPUB reader
        val parcelFd = context.contentResolver.openFileDescriptor(epubUri, "r")!!
        return parcelFd.use {
            val archiveReader = ArchiveReader(it)
            val reader = EpubReader(archiveReader)

            reader.use { epubReader ->
                val packageRef = epubReader.getPackageHref()
                val packageDoc = epubReader.getPackageDocument(packageRef)

                // Extract metadata
                val title = packageDoc.select("dc|title").first()?.text()
                    ?: fallbackFileName.substringBeforeLast(".")
                val author = packageDoc.select("dc|creator").first()?.text()
                val description = packageDoc.select("dc|description").first()?.text()

                // Get chapters from spine
                val chapters = getChaptersFromSpine(epubReader, packageDoc, packageRef)

                // Get assets (images, stylesheets, etc.)
                val assets = getAssetsFromManifest(packageDoc, packageRef)

                ImportedEpubBook(
                    title = title,
                    author = author,
                    description = description,
                    coverFileName = null, // TODO: find cover
                    chapters = chapters,
                    assets = assets,
                )
            }
        }
    }

    private fun getChaptersFromSpine(
        reader: EpubReader,
        packageDoc: org.jsoup.nodes.Document,
        packageRef: String,
    ): List<ImportedEpubChapter> {
        val manifest = packageDoc.select("manifest > item")
            .associateBy { it.attr("id") }

        val spine = packageDoc.select("spine > itemref")
            .mapNotNull { manifest[it.attr("idref")] }
            .filter { it.attr("media-type") == "application/xhtml+xml" }

        return spine.mapIndexed { index, item ->
            val href = item.attr("href")
            val title = extractTitleFromChapter(reader, href) ?: "Chapter ${index + 1}"

            ImportedEpubChapter(
                title = title,
                sourcePath = href,
            )
        }
    }

    private fun extractTitleFromChapter(reader: EpubReader, href: String): String? {
        return reader.getInputStream(href)?.use { stream ->
            val doc = Jsoup.parse(stream, null, "")
            doc.select("h1, h2, h3, title").first()?.text()
        }
    }

    private fun getAssetsFromManifest(
        packageDoc: org.jsoup.nodes.Document,
        packageRef: String,
    ): List<ImportedEpubAsset> {
        val basePath = packageRef.substringBeforeLast('/', "")

        return packageDoc.select("manifest > item")
            .filter { item ->
                val mediaType = item.attr("media-type")
                mediaType.startsWith("image/") || mediaType == "text/css"
            }
            .map { item ->
                val href = item.attr("href")
                val targetFileName = href.substringAfterLast('/')

                ImportedEpubAsset(
                    sourcePath = if (href.startsWith('/')) href else "$basePath/$href",
                    targetFileName = targetFileName,
                )
            }
    }
}