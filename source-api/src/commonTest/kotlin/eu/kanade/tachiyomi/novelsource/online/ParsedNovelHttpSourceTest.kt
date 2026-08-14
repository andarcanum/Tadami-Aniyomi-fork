package eu.kanade.tachiyomi.novelsource.online

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import io.kotest.matchers.shouldBe
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Test

class ParsedNovelHttpSourceTest {

    private val testSource = object : ParsedNovelHttpSource() {
        override val name: String = "Test Novel Source"
        override val baseUrl: String = "https://novel.example.com"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun popularNovelSelector(): String = "div.novel-item"
        override fun popularNovelFromElement(element: Element): SNovel = SNovel.create().apply {
            title = element.select("h2.title").text()
            url = element.select("a").attr("href")
        }
        override fun popularNovelNextPageSelector(): String = "a.next-page"

        override fun searchNovelSelector(): String = "div.search-item"
        override fun searchNovelFromElement(element: Element): SNovel = SNovel.create().apply {
            title = element.select("span.name").text()
            url = element.select("a").attr("href")
        }
        override fun searchNovelNextPageSelector(): String = "a.search-next"

        override fun latestUpdatesSelector(): String = "div.latest-item"
        override fun latestUpdatesFromElement(element: Element): SNovel = SNovel.create().apply {
            title = element.select("h3.title").text()
            url = element.select("a").attr("href")
        }
        override fun latestUpdatesNextPageSelector(): String = "a.latest-next"

        override fun novelDetailsParse(document: Document): SNovel = SNovel.create().apply {
            title = document.select("h1.novel-title").text()
            author = document.select("span.author").text()
            description = document.select("div.synopsis").text()
            status = SNovel.ONGOING
        }

        override fun chapterListSelector(): String = "ul.chapters li"
        override fun chapterFromElement(element: Element): SNovelChapter = SNovelChapter.create().apply {
            name = element.select("a").text()
            url = element.select("a").attr("href")
            chapter_number = element.attr("data-num").toFloatOrNull() ?: 1f
        }

        override fun chapterTextParse(document: Document): String {
            return document.select("div.chapter-content").html()
        }

        override fun getFilterList(): NovelFilterList = NovelFilterList()
    }

    private fun createResponse(html: String, url: String = "https://novel.example.com/test"): Response {
        val request = Request.Builder().url(url).build()
        val body = html.toResponseBody("text/html; charset=utf-8".toMediaType())
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    @Test
    fun `popularNovelsParse parses novels and pagination correctly`() {
        val html = """
            <html>
                <body>
                    <div class="novel-item"><a href="/novel/1"><h2 class="title">Novel 1</h2></a></div>
                    <div class="novel-item"><a href="/novel/2"><h2 class="title">Novel 2</h2></a></div>
                    <a class="next-page" href="/page/2">Next</a>
                </body>
            </html>
        """.trimIndent()

        val result = testSource.popularNovelsParse(createResponse(html))

        result.novels.size shouldBe 2
        result.novels[0].title shouldBe "Novel 1"
        result.novels[0].url shouldBe "/novel/1"
        result.novels[1].title shouldBe "Novel 2"
        result.hasNextPage shouldBe true
    }

    @Test
    fun `searchNovelsParse parses novels list`() {
        val html = """
            <html>
                <body>
                    <div class="search-item"><a href="/novel/search-1"><span class="name">Found Novel</span></a></div>
                </body>
            </html>
        """.trimIndent()

        val result = testSource.searchNovelsParse(createResponse(html))

        result.novels.size shouldBe 1
        result.novels[0].title shouldBe "Found Novel"
        result.hasNextPage shouldBe false
    }

    @Test
    fun `novelDetailsParse extracts novel metadata`() {
        val html = """
            <html>
                <body>
                    <h1 class="novel-title">Lord of the Mysteries</h1>
                    <span class="author">Cuttlefish That Loves Diving</span>
                    <div class="synopsis">With the rising tide of steam power...</div>
                </body>
            </html>
        """.trimIndent()

        val details = testSource.novelDetailsParse(createResponse(html))

        details.title shouldBe "Lord of the Mysteries"
        details.author shouldBe "Cuttlefish That Loves Diving"
        details.description shouldBe "With the rising tide of steam power..."
        details.status shouldBe SNovel.ONGOING
    }

    @Test
    fun `chapterListParse extracts all chapters`() {
        val html = """
            <html>
                <body>
                    <ul class="chapters">
                        <li data-num="1"><a href="/novel/1/c1">Chapter 1: Crimson</a></li>
                        <li data-num="2"><a href="/novel/1/c2">Chapter 2: Situation</a></li>
                    </ul>
                </body>
            </html>
        """.trimIndent()

        val chapters = testSource.chapterListParse(createResponse(html))

        chapters.size shouldBe 2
        chapters[0].name shouldBe "Chapter 1: Crimson"
        chapters[0].url shouldBe "/novel/1/c1"
        chapters[0].chapter_number shouldBe 1f
        chapters[1].name shouldBe "Chapter 2: Situation"
        chapters[1].chapter_number shouldBe 2f
    }

    @Test
    fun `chapterTextParse extracts chapter content`() {
        val html = """
            <html>
                <body>
                    <div class="chapter-content">
                        <p>Pain!</p>
                        <p>How painful!</p>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val text = testSource.chapterTextParse(createResponse(html))

        text.contains("<p>Pain!</p>") shouldBe true
        text.contains("<p>How painful!</p>") shouldBe true
    }
}
