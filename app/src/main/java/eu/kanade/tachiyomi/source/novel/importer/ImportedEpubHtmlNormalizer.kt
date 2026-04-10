package eu.kanade.tachiyomi.source.novel.importer

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal class ImportedEpubHtmlNormalizer {

    fun normalize(rawHtml: String, chapterAssetMap: Map<String, String>): String {
        val doc = Jsoup.parse(rawHtml)

        // Rewrite img src attributes
        doc.select("img").forEach { img ->
            val src = img.attr("src")
            chapterAssetMap[src]?.let { newSrc ->
                img.attr("src", newSrc)
            }
        }

        // Rewrite link href attributes for stylesheets
        doc.select("link[rel=stylesheet]").forEach { link ->
            val href = link.attr("href")
            chapterAssetMap[href]?.let { newHref ->
                link.attr("href", newHref)
            }
        }

        return doc.outerHtml()
    }
}