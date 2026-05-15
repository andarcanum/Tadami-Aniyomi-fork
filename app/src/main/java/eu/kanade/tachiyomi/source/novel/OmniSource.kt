package eu.kanade.tachiyomi.source.novel

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import rx.Observable
import tachiyomi.domain.source.novel.resolver.ContentPurifier
import tachiyomi.domain.source.novel.resolver.OmniResolverEngine
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

import tachiyomi.domain.source.novel.resolver.repository.OmniRuleRepository
import tachiyomi.domain.source.novel.resolver.model.PaginationType

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class OmniSource(
    private val network: NetworkHelper = Injekt.get(),
    private val engine: OmniResolverEngine = OmniResolverEngine(),
    private val purifier: ContentPurifier = ContentPurifier(),
    private val ruleRepo: OmniRuleRepository = Injekt.get()
) : NovelCatalogueSource {

    private suspend fun fetchHtml(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = network.client.newCall(request).awaitSuccess()
        return response.body.string()
    }

    override val id: Long = OMNI_SOURCE_ID
    override val name: String = "OmniResolver"
    override val lang: String = "all"
    override val supportsLatest: Boolean = false

    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        if (page > 1) return NovelsPage(emptyList(), false)
        
        val url = query.trim()
        if (url.toHttpUrlOrNull() == null) return NovelsPage(emptyList(), false)
        
        android.util.Log.d("OmniSource", "Starting search for URL: $url")
        
        return try {
            val request = Request.Builder().url(url).build()
            val response = network.client.newCall(request).awaitSuccess()
            val html = response.body.string()
            
            android.util.Log.d("OmniSource", "HTML fetched, length: ${html.length}")
            
            val result = engine.parse(url, html)
            android.util.Log.d("OmniSource", "Parsing result: ${result.title}")
            
            val sNovel = SNovel.create().apply {
                this.url = url
                this.title = result.title.takeIf { it.isNotBlank() } ?: "Unknown Title"
                this.author = result.author
                this.thumbnail_url = result.thumbnailUrl
                this.initialized = true
            }
            
            NovelsPage(listOf(sNovel), false)
        } catch (e: Exception) {
            android.util.Log.e("OmniSource", "Error parsing URL", e)
            NovelsPage(emptyList(), false)
        }
    }

    override suspend fun getPopularNovels(page: Int): NovelsPage = NovelsPage(emptyList(), false)
    override suspend fun getLatestUpdates(page: Int): NovelsPage = NovelsPage(emptyList(), false)
    override fun getFilterList(): NovelFilterList = NovelFilterList()

    override fun fetchPopularNovels(page: Int): Observable<NovelsPage> = throw UnsupportedOperationException()
    override fun fetchSearchNovels(page: Int, query: String, filters: NovelFilterList): Observable<NovelsPage> = throw UnsupportedOperationException()
    override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> = throw UnsupportedOperationException()

    private fun getDomain(url: String): String = url.toHttpUrlOrNull()?.host ?: ""

    private fun selectChapterElements(
        doc: Document,
        chapterListSelector: String,
        chapterUrlSel: String?,
    ): List<Element> {
        if (chapterListSelector.isBlank()) return emptyList()

        if (chapterUrlSel.isNullOrBlank()) {
            return doc.select(chapterListSelector).toList()
        }

        val directElements = doc.select("$chapterListSelector $chapterUrlSel")
        if (directElements.isNotEmpty()) {
            return directElements.toList()
        }

        return doc.select(chapterListSelector)
            .flatMap { element ->
                if (element.tagName().equals(chapterUrlSel, ignoreCase = true)) {
                    listOf(element)
                } else {
                    element.select(chapterUrlSel).toList()
                }
            }
            .distinctBy { it.absUrl("href") }
    }

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        val html = fetchHtml(novel.url)
        
        val domain = getDomain(novel.url)
        val rule = ruleRepo.getRuleByDomain(domain)
        
        if (rule != null) {
            val doc = org.jsoup.Jsoup.parse(html, novel.url)
            val titleSel = rule.titleSelector
            val authorSel = rule.authorSelector
            val descSel = rule.descriptionSelector
            val coverSel = rule.coverSelector
            
            novel.title = titleSel?.let { doc.select(it).first()?.text() } ?: novel.title
            novel.author = authorSel?.let { doc.select(it).first()?.text() } ?: novel.author
            novel.description = descSel?.let { doc.select(it).first()?.text() } ?: novel.description
            novel.thumbnail_url = coverSel?.let { doc.select(it).first()?.absUrl("src") } ?: novel.thumbnail_url
            return novel
        }
        
        val result = engine.parse(novel.url, html)
        return novel.apply {
            title = result.title
            author = result.author
            description = result.description
            thumbnail_url = result.thumbnailUrl
        }
    }

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        val request = Request.Builder().url(novel.url).build()
        val response = network.client.newCall(request).awaitSuccess()
        val html = response.body.string()
        
        val domain = getDomain(novel.url)
        val rule = ruleRepo.getRuleByDomain(domain)
        
        if (rule != null) {
            val doc = org.jsoup.Jsoup.parse(html, novel.url)
            val chapters = mutableListOf<SNovelChapter>()
            
            val chapterUrlSel = rule.chapterUrlSelector
            val chapterNameSel = rule.chapterNameSelector
            
            // 1. Parse the main page FIRST
            val elements = selectChapterElements(doc, rule.chapterListSelector, chapterUrlSel)
            
            elements.forEach { linkElement ->
                val nameElement = if (chapterNameSel.isNullOrBlank()) linkElement else {
                    // If name selector is same as url selector, it's the same element
                    if (chapterNameSel == chapterUrlSel) linkElement else linkElement.select(chapterNameSel).first()
                }
                
                if (linkElement != null) {
                    val url = linkElement.absUrl("href")
                    if (url.isNotBlank() && chapters.none { it.url == url }) {
                        chapters.add(SNovelChapter.create().apply {
                            this.url = url
                            this.name = nameElement?.text()?.trim() ?: "Chapter"
                        })
                    }
                }
            }
            
            val paginationSel = rule.paginationSelector
            // 2. Handle AJAX_SELECT pagination (e.g. Jaomix)
            if (rule.paginationType == PaginationType.AJAX_SELECT && paginationSel != null) {
                val selectElement = doc.select(paginationSel).first()
                if (selectElement != null) {
                    // Skip the first option, assume it's the current page
                    val options = selectElement.select("option").map { it.attr("value") }.drop(1)
                    if (options.isNotEmpty()) {
                        android.util.Log.d("OmniSource", "AJAX_SELECT detected. Fetching ${options.size} extra pages.")
                        
                        val baseUrl = novel.url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: ""
                        val ajaxUrl = "$baseUrl/wp-admin/admin-ajax.php"
                        
                        val postIdMatch = Regex("(?i)data-id=\"(\\d+)\"|post_id['\":\\s]+(\\d+)").find(html)
                        val postId = postIdMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() } 
                            ?: postIdMatch?.groupValues?.get(2)
                        
                        options.forEach { pageValue ->
                            try {
                                val actionPayload = if (postId != null) {
                                    "action=loadpagenavchapstt&page=$pageValue&post_id=$postId"
                                } else {
                                    "action=loadpagenavchapstt&page=$pageValue"
                                }
                                
                                val mediaType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaTypeOrNull()
                                val payload = actionPayload.toRequestBody(mediaType)

                                
                                val ajaxRequest = Request.Builder()
                                    .url(ajaxUrl)
                                    .post(payload)
                                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                                    .header("Referer", novel.url)
                                    .header("X-Requested-With", "XMLHttpRequest")
                                    .build()
                                    
                                val ajaxResponse = network.client.newCall(ajaxRequest).awaitSuccess()
                                val chunkHtml = ajaxResponse.body.string()
                                
                                val chunkDoc = org.jsoup.Jsoup.parse(chunkHtml, novel.url)
                                
                                // Relaxed selector for AJAX chunks (they often lack the outer container)
                                var elements = selectChapterElements(chunkDoc, rule.chapterListSelector, chapterUrlSel)
                                if (elements.isEmpty()) {
                                    // Fallback: get all links in the chunk
                                    elements = chunkDoc.select("a[href]").toList()
                                }
                                
                                elements.forEach { element ->
                                    val linkElement = when {
                                        chapterUrlSel.isNullOrBlank() -> element
                                        element.tagName().equals(chapterUrlSel, ignoreCase = true) -> element
                                        else -> element.select(chapterUrlSel).first()
                                    }
                                    val nameElement = when {
                                        chapterNameSel.isNullOrBlank() -> linkElement
                                        chapterNameSel == chapterUrlSel -> linkElement
                                        element.tagName().equals(chapterNameSel, ignoreCase = true) -> element
                                        else -> element.select(chapterNameSel).first()
                                    }
                                    
                                    if (linkElement != null) {
                                        val url = linkElement.absUrl("href")
                                        if (chapters.none { it.url == url }) {
                                            chapters.add(SNovelChapter.create().apply {
                                                this.url = url
                                                this.name = nameElement?.text()?.trim() ?: "Chapter"
                                            })
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("OmniSource", "Failed to fetch AJAX chunk for page $pageValue", e)
                            }
                        }
                    }
                }
            }
            
            // 3. Handle NEXT_LINK pagination (e.g. NovelFull)
            if (rule.paginationType == PaginationType.NEXT_LINK && paginationSel != null) {
                var currentDoc = doc
                var pageCount = 1
                val maxPages = 500 // Increased limit for massive novels
                val visitedUrls = mutableSetOf(novel.url)
                
                while (pageCount < maxPages) {
                    val nextLinkElement = currentDoc.select(paginationSel).first() ?: break
                    var nextUrl = nextLinkElement.absUrl("href")
                    
                    if (nextUrl.isBlank() && nextLinkElement.tagName() != "a") {
                        nextUrl = nextLinkElement.select("a").last()?.absUrl("href") ?: ""
                    }
                    
                    if (nextUrl.isBlank() || !visitedUrls.add(nextUrl)) break
                    
                    android.util.Log.d("OmniSource", "NEXT_LINK: Fetching page ${pageCount + 1}: $nextUrl")
                    
                    try {
                        // Crucial: Use fetchHtml instead of raw network call to handle WAF/SPA on subpages
                        val nextHtml = fetchHtml(nextUrl)
                        currentDoc = org.jsoup.Jsoup.parse(nextHtml, nextUrl)
                        
                        selectChapterElements(currentDoc, rule.chapterListSelector, chapterUrlSel).forEach { element ->
                            val linkElement = when {
                                chapterUrlSel.isNullOrBlank() -> element
                                element.tagName().equals(chapterUrlSel, ignoreCase = true) -> element
                                else -> element.select(chapterUrlSel).first()
                            }
                            val nameElement = when {
                                chapterNameSel.isNullOrBlank() -> linkElement
                                chapterNameSel == chapterUrlSel -> linkElement
                                element.tagName().equals(chapterNameSel, ignoreCase = true) -> element
                                else -> element.select(chapterNameSel).first()
                            }
                            
                            if (linkElement != null) {
                                val url = linkElement.absUrl("href")
                                if (url.isNotBlank() && chapters.none { it.url == url }) {
                                    chapters.add(SNovelChapter.create().apply {
                                        this.url = url
                                        this.name = nameElement?.text()?.trim() ?: "Chapter"
                                    })
                                }
                            }
                        }
                        pageCount++
                        // Small delay to be bot-friendly
                        kotlinx.coroutines.delay(500)
                    } catch (e: Exception) {
                        android.util.Log.e("OmniSource", "Failed to fetch NEXT_LINK page: $nextUrl", e)
                        break
                    }
                }
            }
            
            // 4. Sort by assigning decreasing timestamps/chapter numbers
            chapters.forEachIndexed { index, chapter ->
                chapter.date_upload = System.currentTimeMillis() - (index * 1000)
                chapter.chapter_number = (index + 1).toFloat()
                val match = Regex("(?i)(?:chapter|ch|глава|гл|vol|том)\\.?\\s*(\\d+(?:\\.\\d+)?)").find(chapter.name)
                    ?: Regex("(\\d+(?:\\.\\d+)?)").find(chapter.name)
                match?.groupValues?.get(1)?.toFloatOrNull()?.let {
                    chapter.chapter_number = it
                }
            }
            
            return chapters
        }
        
        val doc = org.jsoup.Jsoup.parse(html, novel.url)
        val result = engine.parse(novel.url, html)
        
        // Deep Scan: Check for archive/all chapters link
        val allChaptersLink = doc.select("a").find { link ->
            val text = link.text().lowercase()
            text.contains(Regex("view all|show all|all chapters|все главы|список глав|archive", RegexOption.IGNORE_CASE))
        }
        
        val finalChapters = if (allChaptersLink != null && result.chapters.size < 100) {
            val archiveUrl = allChaptersLink.absUrl("href")
            if (archiveUrl.isNotBlank() && archiveUrl != novel.url) {
                android.util.Log.d("OmniSource", "Deep Scanning archive: $archiveUrl")
                val archiveRequest = Request.Builder().url(archiveUrl).build()
                val archiveResponse = network.client.newCall(archiveRequest).awaitSuccess()
                val archiveHtml = archiveResponse.body.string()
                val archiveResult = engine.parse(archiveUrl, archiveHtml)
                
                // Combine and deduplicate
                (result.chapters + archiveResult.chapters).distinctBy { it.url }
            } else result.chapters
        } else result.chapters

        return finalChapters.map { chapter ->
            SNovelChapter.create().apply {
                url = chapter.url
                name = chapter.name
                date_upload = chapter.dateUpload
                chapter_number = chapter.chapterNumber
            }
        }
    }

    override suspend fun getChapterText(chapter: SNovelChapter): String {
        val html = fetchHtml(chapter.url)
        
        val domain = getDomain(chapter.url)
        val rule = ruleRepo.getRuleByDomain(domain)
        
        if (rule != null) {
            val doc = org.jsoup.Jsoup.parse(html, chapter.url)
            val mainContent = doc.select(rule.contentSelector).firstOrNull() ?: return purifier.clean(html)
            
            // Apply remove selectors
            rule.removeSelectors?.split(",")?.forEach { selector ->
                if (selector.isNotBlank()) {
                    mainContent.select(selector.trim()).remove()
                }
            }
            return mainContent.html()
        }
        
        return purifier.clean(html)
    }

    companion object {
        const val OMNI_SOURCE_ID = -42L
    }
}
