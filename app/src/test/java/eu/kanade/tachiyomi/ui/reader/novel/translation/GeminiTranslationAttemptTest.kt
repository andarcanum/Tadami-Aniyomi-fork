package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailureKind

class GeminiTranslationAttemptTest {
    private lateinit var server: MockWebServer
    private lateinit var service: GeminiTranslationService

    @BeforeEach fun setup() {
        server = MockWebServer().apply { start() }
        val prompts = mockk<GeminiPromptResolver>()
        every { prompts.resolveSystemPrompt(any(), any()) } returns "Translate faithfully"
        service = GeminiTranslationService(OkHttpClient(), Json, prompts, server.url("/").toString().trimEnd('/'))
    }

    @AfterEach fun close() {
        server.shutdown()
    }
    private fun params(model: String = "gemini-3.5-flash-lite", level: String = "medium") = GeminiTranslationParams(
        "fake-key", model, "English", "Russian", level, 8192, 0.7f, 0.95f, 40, GeminiPromptMode.CLASSIC, "",
    )
    private fun complete(text: String = "<s i='0'>Привет</s>") = MockResponse().setBody(
        """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"$text"}]}}]}""",
    )

    @Test
    fun `Gemini joins all answer parts skips thoughts and authenticates by header`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"private"},{"text":"<s i='0'>При"},{"text":"вет</s>"}]}}]}""",
            ),
        )
        service.translateBatch(listOf("Hello <s i='99'>"), params()) shouldBe listOf("Привет")
        val request = server.takeRequest()
        request.getHeader("x-goog-api-key") shouldBe "fake-key"
        request.requestUrl!!.query shouldBe null
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        val config = body["generationConfig"] as JsonObject
        (config["thinkingConfig"] as JsonObject)["thinkingLevel"].asStringOrNull() shouldBe "medium"
        body.toString().contains("&lt;s") shouldBe true
        body.toString().contains("fake-key") shouldBe false
    }

    @Test
    fun `nonempty MAX TOKENS is not accepted as a successful translation`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"<s i='0'>partial</s>"}]}}]}""",
            ),
        )
        val error = runCatching {
            service.translateBatch(listOf("Hello"), params())
        }.exceptionOrNull() as TranslationAttemptException
        error.failure.kind shouldBe TranslationFailureKind.TRUNCATED
        server.requestCount shouldBe 1
    }

    @Test
    fun `Gemma has a thinking toggle and omits Gemini specific safety settings`() = runTest {
        server.enqueue(complete())
        service.translateBatch(listOf("Hello"), params("gemma-4-31b-it", "high")) shouldBe listOf("Привет")
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        val thinking = (body["generationConfig"] as JsonObject)["thinkingConfig"] as JsonObject
        thinking["thinkingLevel"].asStringOrNull() shouldBe "high"
        thinking.containsKey("thinkingBudget") shouldBe false
        body.containsKey("safetySettings") shouldBe false
    }

    @Test
    fun `2 point 5 keeps selected model and uses budget instead of level`() = runTest {
        server.enqueue(complete())
        service.translateBatch(listOf("Hello"), params("gemini-2.5-flash")) shouldBe listOf("Привет")
        val request = server.takeRequest()
        request.path!!.contains("gemini-2.5-flash:generateContent") shouldBe true
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        val thinking = (body["generationConfig"] as JsonObject)["thinkingConfig"] as JsonObject
        thinking.containsKey("thinkingLevel") shouldBe false
        thinking["thinkingBudget"].toString() shouldBe "8192"
    }

    @Test
    fun `frozen wire configuration is used instead of later descriptor defaults`() = runTest {
        server.enqueue(complete())
        val frozen = Json.parseToJsonElement(
            """{"maxOutputTokens":2048,"thinkingConfig":{"thinkingLevel":"low"}}""",
        ) as JsonObject
        service.translateBatch(listOf("Hello"), params().copy(frozenGenerationConfig = frozen)) shouldBe
            listOf("Привет")
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        body["generationConfig"] shouldBe frozen
    }

    @Test
    fun `public prompt uses real newlines instead of escaped backslash-n literals`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"<s i='0'>Один</s><s i='1'>Два</s>"}]}}]}""",
            ),
        )
        service.translateBatch(
            listOf("One", "Two"),
            params().copy(promptModifiers = "Keep names consistent"),
        ) shouldBe listOf("Один", "Два")
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        val text = body["contents"].asArrayOrNull()!!.first().asObjectOrNull()!!["parts"]
            .asArrayOrNull()!!.first().asObjectOrNull()!!["text"].asStringOrNull()!!
        text.contains("Translate faithfully\n\nKeep names consistent\n\n") shouldBe true
        text.contains("<s i='0'>One</s>\n<s i='1'>Two</s>") shouldBe true
        text.contains("\\n") shouldBe false
    }

    @Test
    fun `private python-like plaintext mode parses multi-segment answers instead of nulling them`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Первый абзац\n\nВторой абзац"}]}}]}""",
            ),
        )
        mockkObject(GeminiPrivateBridge)
        try {
            every { GeminiPrivateBridge.isInstalled() } returns true
            val translated = service.translateBatch(
                listOf("First paragraph", "Second paragraph"),
                params().copy(
                    provider = NovelTranslationProvider.GEMINI_PRIVATE,
                    privateUnlocked = true,
                    privatePythonLikeMode = true,
                ),
            )
            translated shouldBe listOf("Первый абзац", "Второй абзац")
        } finally {
            unmockkObject(GeminiPrivateBridge)
        }
    }
}
