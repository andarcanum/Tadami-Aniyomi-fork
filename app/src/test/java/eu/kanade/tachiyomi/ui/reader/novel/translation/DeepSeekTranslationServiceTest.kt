package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailureKind

class DeepSeekTranslationServiceTest {

    private val server = MockWebServer()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `parses xml translation from chat completions response`() = runTest {
        val logs = mutableListOf<String>()
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"<s i='0'>Privet</s><s i='1'>Mir</s>"}}]}""",
            ),
        )
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { mode, _ ->
                when (mode) {
                    GeminiPromptMode.CLASSIC -> "classic_system"
                    GeminiPromptMode.ADULT_18 -> "adult_system"
                }
            },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello", "World"),
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-chat",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.ADULT_18,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
                reasoningEffort = "max",
            ),
            onLog = logs::add,
        )

        translated shouldBe listOf("Privet", "Mir")
        logs.joinToString("\n").shouldContain("DeepSeek request:")
        logs.joinToString("\n").shouldContain("reasoningEffort=max")
        logs.joinToString("\n").shouldContain("thinking=enabled")
        logs.joinToString("\n").shouldNotContain("Hello")
        logs.joinToString("\n").shouldNotContain("test-key")
        val request = server.takeRequest()
        request.path shouldBe "/chat/completions"
        val body = request.body.readUtf8()
        body.shouldContain("\"stream\":false")
        body.shouldContain("adult_system")
        body.shouldContain("\"thinking\":{\"type\":\"enabled\"}")
        body.shouldContain("\"reasoning_effort\":\"high\"")
    }

    @Test
    fun `returns null when model is blank`() = runTest {
        val logs = mutableListOf<String>()
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { _, _ -> "system" },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
                reasoningEffort = "none",
            ),
            onLog = logs::add,
        )

        translated.shouldBeNull()
        logs.joinToString("\n").shouldContain("DeepSeek translateBatch skipped: model is blank")
        server.requestCount shouldBe 0
    }

    @Test
    fun `disables thinking when reasoning effort is none`() = runTest {
        val logs = mutableListOf<String>()
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"<s i='0'>Privet</s>"}}]}""",
            ),
        )
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { _, _ -> "system" },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-v4-pro",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
                reasoningEffort = "none",
            ),
            onLog = logs::add,
        )

        translated shouldBe listOf("Privet")
        logs.joinToString("\n").shouldContain("reasoningEffort=none")
        logs.joinToString("\n").shouldContain("thinking=disabled")
        val body = server.takeRequest().body.readUtf8()
        body.shouldContain("\"thinking\":{\"type\":\"disabled\"}")
    }

    @Test
    fun `uses larger max tokens for large thinking requests`() = runTest {
        val logs = mutableListOf<String>()
        val segments = List(40) { "x".repeat(100) }
        val translatedContent = segments.indices.joinToString("") { index ->
            "<s i='$index'>Privet</s>"
        }
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"$translatedContent"}}]}""",
            ),
        )
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { _, _ -> "system" },
        )

        val translated = service.translateBatch(
            segments = segments,
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-v4-flash",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
                reasoningEffort = "max",
            ),
            onLog = logs::add,
        )

        translated shouldBe List(40) { "Privet" }
        logs.joinToString("\n").shouldContain("maxTokens=32768")
        val body = server.takeRequest().body.readUtf8()
        body.shouldContain("\"max_tokens\":32768")
    }

    @Test
    fun `preserves token limit as typed failure instead of an empty result`() = runTest {
        val logs = mutableListOf<String>()
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"","reasoning_content":"Thinking..."}}]}""",
            ),
        )
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { _, _ -> "system" },
        )

        val error = runCatching {
            service.translateBatch(
                segments = listOf("Hello"),
                params = DeepSeekTranslationParams(
                    baseUrl = server.url("/").toString().trimEnd('/'),
                    apiKey = "test-key",
                    model = "deepseek-v4-flash",
                    sourceLang = "English",
                    targetLang = "Russian",
                    promptMode = GeminiPromptMode.CLASSIC,
                    promptModifiers = "",
                    temperature = 0.7f,
                    topP = 0.95f,
                    reasoningEffort = "max",
                ),
                onLog = logs::add,
            )
        }.exceptionOrNull()

        (error is TranslationAttemptException) shouldBe true
        (error as TranslationAttemptException).failure.kind shouldBe TranslationFailureKind.TRUNCATED
        server.requestCount shouldBe 1
    }

    @Test
    fun `strips trailing chat completions suffix without duplicating path while preserving bare root`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"choices":[{"message":{"content":"<s i='0'>Privet</s>"}}]}"""),
        )
        server.enqueue(
            MockResponse().setBody("""{"choices":[{"message":{"content":"<s i='0'>Mir</s>"}}]}"""),
        )
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { _, _ -> "system" },
        )
        val baseParams = DeepSeekTranslationParams(
            baseUrl = server.url("/chat/completions").toString().trimEnd('/'),
            apiKey = "test-key",
            model = "deepseek-chat",
            sourceLang = "English",
            targetLang = "Russian",
            promptMode = GeminiPromptMode.CLASSIC,
            promptModifiers = "",
            temperature = 0.7f,
            topP = 0.95f,
            reasoningEffort = "none",
        )

        service.translateBatch(segments = listOf("Hello"), params = baseParams, onLog = {}) shouldBe listOf("Privet")
        service.translateBatch(
            segments = listOf("World"),
            params = baseParams.copy(baseUrl = server.url("/").toString().trimEnd('/')),
            onLog = {},
        ) shouldBe listOf("Mir")

        server.takeRequest().path shouldBe "/chat/completions"
        server.takeRequest().path shouldBe "/chat/completions"
    }
}
