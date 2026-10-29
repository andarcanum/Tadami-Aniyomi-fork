package eu.kanade.tachiyomi.network.interceptor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HttpErrorPageDetectorTest {

    @Test
    fun `detects next js error page with __next_error__ id and 500 title`() {
        val nextJsErrorHtml = """
            <!DOCTYPE html>
            <html id="__next_error__">
            <head>
                <title>500: This page couldn’t load</title>
            </head>
            <body>
                <div><h2>500</h2><p>This page couldn’t load</p></div>
            </body>
            </html>
        """.trimIndent()

        HttpErrorPageDetector.isErrorPage(nextJsErrorHtml) shouldBe true
    }

    @Test
    fun `detects server error and not found pages by title`() {
        val internalErrorHtml =
            "<html><head><title>500 Internal Server Error</title></head><body><h1>500</h1></body></html>"
        val notFoundHtml =
            "<html><head><title>404 Not Found</title></head><body><h1>Not Found</h1></body></html>"
        val badGatewayHtml =
            "<html><head><title>502 Bad Gateway</title></head><body>nginx</body></html>"

        HttpErrorPageDetector.isErrorPage(internalErrorHtml) shouldBe true
        HttpErrorPageDetector.isErrorPage(notFoundHtml) shouldBe true
        HttpErrorPageDetector.isErrorPage(badGatewayHtml) shouldBe true
    }

    @Test
    fun `does not flag legitimate novel chapter content`() {
        val cleanChapterHtml = """
            <html>
            <head>
                <title>I Rely on Poverty to Sweep Through Survival Games - Chapter 63</title>
            </head>
            <body>
                <h1>Chapter 63: One Dollar</h1>
                <p>The system announced the reward of 500 points to all players.</p>
                <p>Even though there was a server error mentioned in the story lore, the protagonist continued forward.</p>
            </body>
            </html>
        """.trimIndent()

        HttpErrorPageDetector.isErrorPage(cleanChapterHtml) shouldBe false
        HttpErrorPageDetector.isErrorPage("") shouldBe false
    }
}
