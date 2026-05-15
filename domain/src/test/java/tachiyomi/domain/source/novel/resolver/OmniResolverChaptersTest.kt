package tachiyomi.domain.source.novel.resolver

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OmniResolverChaptersTest {

    private val engine = OmniResolverEngine()

    @Test
    fun `extracts chapters from a list container`() {
        val html = """
            <html>
            <body>
                <div class="chapter-list">
                    <a href="https://site.com/novel/1/chapter-1">Chapter 1</a>
                    <a href="https://site.com/novel/1/chapter-2">Chapter 2</a>
                    <a href="https://site.com/novel/1/chapter-3">Chapter 3</a>
                    <a href="https://site.com/novel/1/chapter-4">Chapter 4</a>
                    <a href="https://site.com/novel/1/chapter-5">Chapter 5</a>
                    <a href="https://site.com/novel/1/chapter-6">Chapter 6</a>
                </div>
                <div class="footer">
                    <a href="https://site.com/privacy">Privacy Policy</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = engine.parse("https://site.com/novel/1", html)
        result.chapters shouldHaveSize 6
        result.chapters[0].name shouldBe "Chapter 1"
        result.chapters[0].url shouldBe "https://site.com/novel/1/chapter-1"
    }

    @Test
    fun `ignores footer links without chapter keywords`() {
        val html = """
            <html>
            <body>
                <div class="chapters">
                    <a href="https://site.com/ch-1">Ch. 1 - Start</a>
                    <a href="https://site.com/ch-2">Ch. 2 - Middle</a>
                </div>
                <footer>
                    <a href="https://site.com/about">About Us</a>
                    <a href="https://site.com/contact">Contact</a>
                </footer>
            </body>
            </html>
        """.trimIndent()

        val result = engine.parse("https://site.com/", html)
        // Note: in this case sibling count is low (2), so it relies on keywords/patterns
        result.chapters shouldHaveSize 2
    }

    @Test
    fun `extracts bare number chapters on the same host even when the path changes`() {
        val html = """
            <html>
            <body>
                <div class="chapter-list">
                    <a href="https://site.com/read/1">1</a>
                    <a href="https://site.com/read/2">2</a>
                    <a href="https://site.com/read/3">3</a>
                    <a href="https://site.com/read/4">4</a>
                    <a href="https://site.com/read/5">5</a>
                    <a href="https://site.com/read/6">6</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = engine.parse("https://site.com/novel/1", html)
        result.chapters shouldHaveSize 6
        result.chapters[0].url shouldBe "https://site.com/read/1"
    }
}
