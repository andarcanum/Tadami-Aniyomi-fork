package tachiyomi.domain.source.novel.resolver

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OmniResolverMetadataTest {

    private val engine = OmniResolverEngine()

    @Test
    fun `extracts title from og tag`() {
        val html = """
            <html>
            <head>
                <meta property="og:title" content="The Great Novel">
            </head>
            </html>
        """.trimIndent()

        val result = engine.parse("https://test.com", html)
        result.title shouldBe "The Great Novel"
    }

    @Test
    fun `extracts title from h1 when og tag is missing`() {
        val html = """
            <html>
            <body>
                <h1>Fallback Title</h1>
            </body>
            </html>
        """.trimIndent()

        val result = engine.parse("https://test.com", html)
        result.title shouldBe "Fallback Title"
    }

    @Test
    fun `extracts author from custom text label`() {
        val html = """
            <html>
            <body>
                <div>Author: John Doe</div>
            </body>
            </html>
        """.trimIndent()

        val result = engine.parse("https://test.com", html)
        result.author shouldBe "John Doe"
    }

    @Test
    fun `extracts thumbnail from img with cover keyword`() {
        val html = """
            <html>
            <body>
                <img src="https://test.com/my-cover.jpg" alt="Book Cover">
            </body>
            </html>
        """.trimIndent()

        val result = engine.parse("https://test.com", html)
        result.thumbnailUrl shouldBe "https://test.com/my-cover.jpg"
    }
}
