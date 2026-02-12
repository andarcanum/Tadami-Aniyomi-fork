package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelUpdatesChapterParserTest {

    @Test
    fun `extracts post id from inline script when hidden input is missing`() {
        val html = """
            <html>
              <head>
                <script>var mypostid = "12345";</script>
              </head>
              <body></body>
            </html>
        """.trimIndent()

        extractNovelUpdatesPostId(html) shouldBe "12345"
    }

    @Test
    fun `parses chapter links from ajax rows with external href`() {
        val html = """
            <ul>
              <li class="sp_li_chp">
                <a href="/group/demo">Group</a>
                <a href="//ext.example/chapter-1">c1</a>
              </li>
              <li class="sp_li_chp">
                <a href="/group/demo">Group</a>
                <a href="//ext.example/chapter-2">c2</a>
              </li>
            </ul>
        """.trimIndent()

        val chapters = parseNovelUpdatesChaptersHtml(
            chaptersHtml = html,
            siteUrl = "https://www.novelupdates.com/",
        )

        chapters.mapNotNull { it.path } shouldContainExactly listOf(
            "https://ext.example/chapter-1",
            "https://ext.example/chapter-2",
        )
    }

    @Test
    fun `parses chapter links from table rows`() {
        val html = """
            <table id="myTable">
              <tbody>
                <tr>
                  <td><a href="/group/demo">Group</a></td>
                  <td><a href="/extnu/111/">Chapter 1</a></td>
                </tr>
                <tr>
                  <td><a href="/group/demo">Group</a></td>
                  <td><a href="/extnu/222/">Chapter 2</a></td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val chapters = parseNovelUpdatesChaptersHtml(
            chaptersHtml = html,
            siteUrl = "https://www.novelupdates.com/",
        )

        chapters.mapNotNull { it.path } shouldContainExactly listOf(
            "/extnu/111/",
            "/extnu/222/",
        )
    }

    @Test
    fun `normalizes novelupdates local chapter urls to relative path`() {
        normalizeNovelUpdatesChapterPath(
            href = "https://www.novelupdates.com/extnu/123/",
            siteUrl = "https://www.novelupdates.com/",
        ) shouldBe "/extnu/123/"
    }

    @Test
    fun `returns null for blank chapter href`() {
        normalizeNovelUpdatesChapterPath(
            href = "  ",
            siteUrl = "https://www.novelupdates.com/",
        ) shouldBe null
    }
}
