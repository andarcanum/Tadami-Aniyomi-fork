package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GoogleTranslationServiceTest {

    private val server = MockWebServer()

    @BeforeEach
    fun setup() {
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `uses get for short text translation`() = runTest {
        server.enqueue(
            MockResponse().setBody("""[[["Короткий текст","Short text",null,null,1]],null,"en"]"""),
        )
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
        )

        val translated = service.translateSingle(
            text = "Short text",
            sourceLanguage = "auto",
            targetLanguage = "ru",
        )

        translated shouldBe "Короткий текст"
        val request = server.takeRequest()
        request.method shouldBe "GET"
        request.requestUrl?.queryParameter("q") shouldBe "Short text"
    }

    @Test
    fun `uses post for long text translation`() = runTest {
        server.enqueue(
            MockResponse().setBody("""[[["Длинный текст","${"A".repeat(600)}",null,null,1]],null,"en"]"""),
        )
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
        )

        service.translateSingle(
            text = "A".repeat(600),
            sourceLanguage = "en",
            targetLanguage = "ru",
        ) shouldBe "Длинный текст"

        val request = server.takeRequest()
        request.method shouldBe "POST"
        request.body.readUtf8() shouldBe "client=gtx&sl=en&tl=ru&dt=t&q=${"A".repeat(600)}"
    }

    @Test
    fun `translates chunked paragraphs using novela style markers`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[[["[0]\nПервый\n\n[1]\nВторой","[0]\nFirst\n\n[1]\nSecond",null,null,1]],null,"en"]""",
            ),
        )
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
            maxChunkChars = 64,
        )

        val result = service.translateBatch(
            texts = listOf("First", "Second"),
            params = GoogleTranslationParams(sourceLang = "auto", targetLang = "ru"),
        )

        result.translatedByText shouldContain ("First" to "Первый")
        result.translatedByText shouldContain ("Second" to "Второй")
    }

    @Test
    fun `falls back to single translation when a marker is missing from chunk response`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[[["[0]\nПервый","[0]\nFirst\n\n[1]\nSecond",null,null,1]],null,"en"]""",
            ),
        )
        server.enqueue(
            MockResponse().setBody("""[[["Второй","Second",null,null,1]],null,"en"]"""),
        )
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
            maxChunkChars = 64,
        )

        val result = service.translateBatch(
            texts = listOf("First", "Second"),
            params = GoogleTranslationParams(sourceLang = "auto", targetLang = "ru"),
        )

        result.translatedByText shouldContain ("First" to "Первый")
        result.translatedByText shouldContain ("Second" to "Второй")
        server.requestCount shouldBe 2
    }
}
