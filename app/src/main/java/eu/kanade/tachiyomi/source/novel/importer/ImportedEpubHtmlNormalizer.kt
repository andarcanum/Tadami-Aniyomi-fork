package eu.kanade.tachiyomi.source.novel.importer

import org.jsoup.Jsoup
import java.nio.file.Paths

internal class ImportedEpubHtmlNormalizer {

    fun normalize(
        rawHtml: String,
        chapterSourcePath: String,
        chapterAssetMap: Map<String, String>,
    ): String {
        val doc = Jsoup.parse(rawHtml)

        doc.select("img").forEach { img ->
            val src = img.attr("src")
            resolveAssetPath(chapterSourcePath, src, chapterAssetMap)?.let { newSrc ->
                img.attr("src", newSrc)
            }
        }

        doc.select("link[rel=stylesheet]").forEach { link ->
            val href = link.attr("href")
            resolveAssetPath(chapterSourcePath, href, chapterAssetMap)?.let { newHref ->
                link.attr("href", newHref)
            }
        }

        doc.select("image, use").forEach { node ->
            val rawReference = node.attr("href").ifBlank { node.attr("xlink:href") }
            resolveAssetPath(chapterSourcePath, rawReference, chapterAssetMap)?.let { newReference ->
                if (node.hasAttr("href")) {
                    node.attr("href", newReference)
                }
                if (node.hasAttr("xlink:href")) {
                    node.attr("xlink:href", newReference)
                }
            }
        }

        return doc.outerHtml()
    }

    private fun resolveAssetPath(
        chapterSourcePath: String,
        referencePath: String,
        chapterAssetMap: Map<String, String>,
    ): String? {
        val strippedReference = referencePath.stripPathSuffix()
        val resolvedPaths = buildList {
            add(strippedReference)
            add(resolveRelativePath(chapterSourcePath, strippedReference))
        }.distinct()

        return resolvedPaths.firstNotNullOfOrNull { chapterAssetMap[it] }?.let { storedPath ->
            storedPath + referencePath.substring(strippedReference.length)
        }
    }

    private fun resolveRelativePath(basePath: String, relativePath: String): String {
        val normalizedBasePath = basePath.replace('\\', '/')
        val normalizedRelativePath = relativePath.replace('\\', '/')
        val baseDirectory = normalizedBasePath.substringBeforeLast('/', "")
        val resolved = if (baseDirectory.isBlank()) {
            Paths.get(normalizedRelativePath)
        } else {
            Paths.get(baseDirectory).resolve(normalizedRelativePath)
        }
        return resolved.normalize().toString().replace('\\', '/').trimStart('/')
    }

    private fun String.stripPathSuffix(): String {
        val queryIndex = indexOf('?').takeIf { it >= 0 } ?: length
        val fragmentIndex = indexOf('#').takeIf { it >= 0 } ?: length
        val endIndex = minOf(queryIndex, fragmentIndex)
        return substring(0, endIndex)
    }
}
