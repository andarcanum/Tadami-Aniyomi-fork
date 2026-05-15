package tachiyomi.domain.source.novel.resolver

import org.jsoup.Jsoup

class ContentPurifier {
    fun clean(html: String): String {
        val doc = Jsoup.parse(html)
        
        // Find main container: highest <p> density
        // We look for the element that contains the most paragraphs
        val mainContent = doc.select("div, article, section").maxByOrNull { 
            it.select("p").size 
        } ?: doc.body()
        
        // Clone to avoid modifying the original document if needed
        val cleaned = mainContent.clone()
        
        // Remove noise: scripts, styles, etc.
        cleaned.select("script, style, iframe, footer, nav, header, aside").remove()
        
        // Remove common social and sharing blocks
        cleaned.select(".social-share, .share-links, .entry-meta, .post-meta, .comments-area").remove()
        
        // Remove navigation links often found at the bottom
        cleaned.select("a").filter { link ->
            val text = link.text()
            text.contains(Regex("Next|Previous|Back|Forward|Глава|Назад|Вперед|Следующая|Предыдущая", RegexOption.IGNORE_CASE)) 
        }.forEach { it.remove() }
        
        // Remove specific Jaomix/WordPress noise
        cleaned.select(".code-block, .adsbygoogle, .wp-block-buttons").remove()
        
        return cleaned.html()
    }
}
