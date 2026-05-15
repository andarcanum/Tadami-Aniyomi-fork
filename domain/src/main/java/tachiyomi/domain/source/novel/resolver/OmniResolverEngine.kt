package tachiyomi.domain.source.novel.resolver

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.domain.source.novel.resolver.model.OmniChapter
import tachiyomi.domain.source.novel.resolver.model.OmniParsingResult

class OmniResolverEngine {
    fun parse(url: String, html: String): OmniParsingResult {
        val document = Jsoup.parse(html, url)
        val initialResult = OmniParsingResult(
            title = extractTitle(document),
            author = extractAuthor(document),
            description = extractDescription(document),
            thumbnailUrl = extractThumbnail(document),
            chapters = extractChapters(document)
        )
        
        // Deep Scan: If we have very few chapters, look for an "All Chapters" link
        if (initialResult.chapters.size in 1..60) {
            val allChaptersLink = document.select("a").find { link ->
                val text = link.text().lowercase()
                text.contains(Regex("view all|show all|all chapters|все главы|список глав|archive", RegexOption.IGNORE_CASE))
            }
            
            if (allChaptersLink != null) {
                val archiveUrl = allChaptersLink.absUrl("href")
                if (archiveUrl.isNotBlank() && archiveUrl != url) {
                    // Mark this result as needing a second pass or just return the hint
                    android.util.Log.d("OmniSource", "Found archive link: $archiveUrl")
                }
            }
        }
        
        return initialResult
    }

    private fun extractTitle(doc: Document): String {
        return doc.select("meta[property=og:title]").attr("content").takeIf { it.isNotBlank() }
            ?: doc.select("h1").first()?.text()
            ?: doc.title().substringBefore(" - ").substringBefore(" | ")
    }

    private fun extractAuthor(doc: Document): String? {
        return doc.select("meta[property=book:author]").attr("content").takeIf { it.isNotBlank() }
            ?: doc.select("div:contains(Author:), span:contains(Author:), p:contains(Author:)").firstOrNull()?.text()
                ?.substringAfter("Author:")?.trim()
    }

    private fun extractDescription(doc: Document): String? {
        return doc.select("meta[property=og:description]").attr("content").takeIf { it.isNotBlank() }
            ?: doc.select("meta[name=description]").attr("content").takeIf { it.isNotBlank() }
    }

    private fun extractThumbnail(doc: Document): String? {
        return doc.select("meta[property=og:image]").attr("content").takeIf { it.isNotBlank() }
            ?: doc.select("img").asSequence()
                .map { it.absUrl("src") }
                .filter { it.contains("cover", ignoreCase = true) || it.contains("poster", ignoreCase = true) }
                .firstOrNull()
    }
    private fun extractChapters(doc: Document): List<OmniChapter> {
        val allLinks = doc.select("a[href]")
        val baseHost = doc.baseUri().toHttpUrlOrNull()?.host

        return allLinks.asSequence()
            .filter {
                val absUrl = it.absUrl("href")
                absUrl.isNotBlank() && (
                    baseHost == null ||
                        absUrl.toHttpUrlOrNull()?.host == baseHost
                    )
            }
            .map { link ->
                var score = 0
                val text = link.text()
                val url = link.absUrl("href")

                // Keyword scoring - increase weight for explicit novel terms
                val hasKeyword = text.contains(Regex("Chapter|Глава|Vol|Ch\\.|Том|Часть", RegexOption.IGNORE_CASE))
                if (hasKeyword) score += 20
                if (text.contains(Regex("\\d+"))) score += 10

                // Pattern scoring in URL
                if (url.contains(Regex("/chapter-\\d+", RegexOption.IGNORE_CASE))) score += 10
                if (url.contains(Regex("/glava-\\d+", RegexOption.IGNORE_CASE))) score += 10

                // Structure scoring: Look for patterns in parent or grandparent
                val parent = link.parent()
                val grandParent = parent?.parent()
                
                val siblingLinks = parent?.select("a")?.size ?: 0
                val grandSiblingLinks = grandParent?.select("a")?.size ?: 0
                
                if (siblingLinks > 5 || grandSiblingLinks > 10) {
                    score += 15
                }
                
                // Header bonus: Many sites put chapters in H2/H3
                if (parent?.tagName()?.matches(Regex("h[1-6]")) == true) {
                    score += 10
                }

                link to score
            }
            .filter { it.second >= 25 } // Keep the threshold but make it easier to reach with headers/keywords
            .distinctBy { it.first.absUrl("href") }
            .mapIndexed { index, (link, _) ->
                val name = link.text().trim()
                val chapterNumber = parseChapterNumber(name) ?: (index + 1).toFloat()
                
                OmniChapter(
                    name = name,
                    url = link.absUrl("href"),
                    dateUpload = System.currentTimeMillis() - (index * 1000),
                    chapterNumber = chapterNumber
                )
            }
            .toList()
    }

    private fun parseChapterNumber(name: String): Float? {
        val regex = Regex("(?i)(?:chapter|ch|глава|гл|vol|том)\\.?\\s*(\\d+(?:\\.\\d+)?)")
        val match = regex.find(name)
        return match?.groupValues?.get(1)?.toFloatOrNull() 
            ?: Regex("(\\d+(?:\\.\\d+)?)").find(name)?.groupValues?.get(1)?.toFloatOrNull()
    }
}
