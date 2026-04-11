package eu.kanade.tachiyomi.source.novel.importer

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubAsset
import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubBook
import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubChapter
import mihon.core.archive.ArchiveReader
import mihon.core.archive.EpubReader
import org.jsoup.Jsoup
import java.nio.file.Paths

internal class ImportedEpubParser(
    private val context: Context,
) {

    fun parse(epubUri: Uri, fallbackFileName: String): ImportedEpubBook {
        val parcelFd = context.contentResolver.openFileDescriptor(epubUri, "r")!!
        return parcelFd.use {
            val archiveReader = ArchiveReader(it)
            val reader = EpubReader(archiveReader)

            reader.use { epubReader ->
                val packageRef = epubReader.getPackageHref()
                val packageDoc = epubReader.getPackageDocument(packageRef)
                val packageBasePath = packageRef.substringBeforeLast('/', "")
                val manifestItems = packageDoc.select("manifest > item")

                val title = packageDoc.getElementsByTag("dc:title").first()?.text()
                    ?: fallbackFileName.substringBeforeLast(".")
                val author = packageDoc.getElementsByTag("dc:creator").first()?.text()
                val description = packageDoc.getElementsByTag("dc:description").first()?.text()

                val chapters = getChaptersFromPackage(
                    reader = epubReader,
                    packageDoc = packageDoc,
                    packageBasePath = packageBasePath,
                    manifestItems = manifestItems,
                )

                val assets = getAssetsFromManifest(
                    packageBasePath = packageBasePath,
                    manifestItems = manifestItems,
                )

                ImportedEpubBook(
                    title = title,
                    author = author,
                    description = description,
                    coverFileName = findImportedEpubCoverPath(
                        packageBasePath = packageBasePath,
                        packageDoc = packageDoc,
                        manifestItems = manifestItems,
                    ),
                    chapters = chapters,
                    assets = assets,
                )
            }
        }
    }

    private fun getChaptersFromPackage(
        reader: EpubReader,
        packageDoc: org.jsoup.nodes.Document,
        packageBasePath: String,
        manifestItems: org.jsoup.select.Elements,
    ): List<ImportedEpubChapter> {
        val manifestById = manifestItems.associateBy { it.attr("id") }
        val chapterSourcePaths = manifestItems
            .filter { it.attr("media-type") == "application/xhtml+xml" }
            .map { resolveImportedEpubArchivePath(packageBasePath, it.attr("href")) }
            .toSet()
        val navChapters = getChaptersFromNav(
            reader = reader,
            packageBasePath = packageBasePath,
            manifestById = manifestById,
            chapterSourcePaths = chapterSourcePaths,
        )
        val spineChapters = getChaptersFromSpine(reader, packageBasePath, manifestById, packageDoc)
        return mergeImportedEpubChapters(navChapters, spineChapters)
    }

    private fun getChaptersFromNav(
        reader: EpubReader,
        packageBasePath: String,
        manifestById: Map<String, org.jsoup.nodes.Element>,
        chapterSourcePaths: Set<String>,
    ): List<ImportedEpubChapter> {
        val navItem = manifestById.values.firstOrNull { item ->
            item.attr("properties")
                .split(' ')
                .any { it == "nav" }
        } ?: return emptyList()

        val navHref = navItem.attr("href").takeIf { it.isNotBlank() } ?: return emptyList()
        val navPath = resolveImportedEpubArchivePath(packageBasePath, navHref)
        val navDoc = reader.getInputStream(navPath)?.use { stream ->
            Jsoup.parse(stream, null, navPath)
        } ?: return emptyList()

        return navDoc.select("nav a[href]").mapIndexedNotNull { index, anchor ->
            val chapterHref = anchor.attr("href").stripImportedEpubPathSuffix()
            if (chapterHref.isBlank()) {
                return@mapIndexedNotNull null
            }

            val sourcePath = resolveImportedEpubArchivePath(packageBasePath, chapterHref)
            if (sourcePath.isBlank() || sourcePath !in chapterSourcePaths) {
                return@mapIndexedNotNull null
            }

            val title = anchor.text().trim().ifBlank {
                extractTitleFromChapter(reader, sourcePath)
            } ?: "Chapter ${index + 1}"

            ImportedEpubChapter(
                title = title,
                sourcePath = sourcePath,
            )
        }
    }

    private fun getChaptersFromSpine(
        reader: EpubReader,
        packageBasePath: String,
        manifestById: Map<String, org.jsoup.nodes.Element>,
        packageDoc: org.jsoup.nodes.Document,
    ): List<ImportedEpubChapter> {
        val spine = packageDoc.select("spine > itemref")
            .mapNotNull { manifestById[it.attr("idref")] }
            .filter { it.attr("media-type") == "application/xhtml+xml" }

        return spine.mapIndexed { index, item ->
            val sourcePath = resolveImportedEpubArchivePath(packageBasePath, item.attr("href"))
            val title = extractTitleFromChapter(reader, sourcePath) ?: "Chapter ${index + 1}"

            ImportedEpubChapter(
                title = title,
                sourcePath = sourcePath,
            )
        }
    }

    private fun extractTitleFromChapter(reader: EpubReader, sourcePath: String): String? {
        if (sourcePath.isBlank()) return null
        return reader.getInputStream(sourcePath)?.use { stream ->
            val doc = Jsoup.parse(stream, null, sourcePath)
            doc.select("h1, h2, h3, title").first()?.text()
        }
    }

    private fun getAssetsFromManifest(
        packageBasePath: String,
        manifestItems: org.jsoup.select.Elements,
    ): List<ImportedEpubAsset> {
        return manifestItems
            .filter { item ->
                val mediaType = item.attr("media-type")
                mediaType.startsWith("image/") || mediaType == "text/css"
            }
            .mapNotNull { item ->
                val href = item.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val sourcePath = resolveImportedEpubArchivePath(packageBasePath, href)
                if (sourcePath.isBlank()) return@mapNotNull null

                ImportedEpubAsset(
                    sourcePath = sourcePath,
                    targetPath = sourcePath,
                )
            }
    }
}

internal fun findImportedEpubCoverPath(
    packageBasePath: String,
    packageDoc: org.jsoup.nodes.Document,
    manifestItems: org.jsoup.select.Elements,
): String? {
    val manifestById = manifestItems.associateBy { it.attr("id") }

    manifestItems.firstOrNull { item ->
        item.attr("media-type").startsWith("image/") &&
            item.attr("properties")
                .split(' ')
                .any { it == "cover-image" }
    }?.attr("href")?.takeIf { it.isNotBlank() }?.let { href ->
        return resolveImportedEpubArchivePath(packageBasePath, href)
    }

    packageDoc.select("metadata > meta[name=cover]").firstOrNull()
        ?.attr("content")
        ?.takeIf { it.isNotBlank() }
        ?.let { coverId ->
            manifestById[coverId]
                ?.attr("href")
                ?.takeIf { it.isNotBlank() }
                ?.let { href -> return resolveImportedEpubArchivePath(packageBasePath, href) }
        }

    manifestItems.firstOrNull { item ->
        item.attr("media-type").startsWith("image/") &&
            item.attr("href")
                .substringAfterLast('/')
                .substringBeforeLast('.')
                .equals("cover", ignoreCase = true)
    }?.attr("href")?.takeIf { it.isNotBlank() }?.let { href ->
        return resolveImportedEpubArchivePath(packageBasePath, href)
    }

    return null
}

internal fun mergeImportedEpubChapters(
    navChapters: List<ImportedEpubChapter>,
    spineChapters: List<ImportedEpubChapter>,
): List<ImportedEpubChapter> {
    if (navChapters.isEmpty()) {
        return spineChapters
    }

    val merged = linkedMapOf<String, ImportedEpubChapter>()
    navChapters.forEach { merged.putIfAbsent(it.sourcePath, it) }
    spineChapters.forEach { merged.putIfAbsent(it.sourcePath, it) }
    return merged.values.toList()
}

internal fun resolveImportedEpubArchivePath(
    basePath: String,
    relativePath: String,
): String {
    val strippedPath = relativePath.stripImportedEpubPathSuffix().replace('\\', '/')
    if (strippedPath.isBlank()) return strippedPath
    if (strippedPath.startsWith("http://") ||
        strippedPath.startsWith("https://") ||
        strippedPath.startsWith("file:")
    ) {
        return strippedPath
    }
    if (strippedPath.startsWith('/')) {
        return strippedPath.trimStart('/')
    }

    val normalizedBasePath = basePath.replace('\\', '/').trimEnd('/')
    if (normalizedBasePath.isBlank()) {
        return strippedPath.trimStart('/')
    }

    return Paths.get(normalizedBasePath)
        .resolve(strippedPath)
        .normalize()
        .toString()
        .replace('\\', '/')
        .trimStart('/')
}

internal fun String.stripImportedEpubPathSuffix(): String {
    val queryIndex = indexOf('?').takeIf { it >= 0 } ?: length
    val fragmentIndex = indexOf('#').takeIf { it >= 0 } ?: length
    val endIndex = minOf(queryIndex, fragmentIndex)
    return substring(0, endIndex)
}
