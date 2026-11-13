package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationProtocol

class ProfileRequestBuilderTest {
    private val connectionId = "00000000-0000-0000-0000-000000000001"

    @Test
    fun `OpenAI profile uses exact model body and configured output field`() {
        val connection = ConnectionProfile(
            connectionId,
            "Test",
            TranslationProtocol.OPENAI_CHAT,
            "https://example.com/v1",
            ProfileAuth.BEARER,
        )
        val model = ModelSelection(
            "org/model:id",
            ReasoningContract.Levels(listOf("low", "high")),
            GenerationSettings(maxOutputTokens = 4096, reasoning = ReasoningChoice.Level("high")),
            OutputTokenField.MAX_COMPLETION_TOKENS,
        )
        val request = ProfileRequestBuilder.build(connection, model, "System", "Source")
        request.url shouldBe "https://example.com/v1/chat/completions"
        request.body["model"].asStringOrNull() shouldBe "org/model:id"
        request.body["max_completion_tokens"].toString() shouldBe "4096"
        request.body.containsKey("max_tokens") shouldBe false
        request.body["reasoning_effort"].asStringOrNull() shouldBe "high"
        request.toString().contains("Source") shouldBe false
    }

    @Test
    fun `Gemini root is not duplicated and unknown options are omitted`() {
        val connection = ConnectionProfile(
            connectionId,
            "Test",
            TranslationProtocol.GEMINI_GENERATE,
            "https://example.com/v1beta",
            ProfileAuth.X_GOOG_API_KEY,
        )
        val request = ProfileRequestBuilder.build(connection, ModelSelection("gemini-custom"), "System", "Source")
        request.url shouldBe "https://example.com/v1beta/models/gemini-custom:generateContent"
        (request.body["generationConfig"] as JsonObject).keys shouldBe emptySet()
        request.body.containsKey("model") shouldBe false
    }

    @Test
    fun `Gemini budget and levels never coexist in the request`() {
        val connection = ConnectionProfile(
            connectionId,
            "Test",
            TranslationProtocol.GEMINI_GENERATE,
            "https://example.com/v1beta",
            ProfileAuth.X_GOOG_API_KEY,
        )
        val model = ModelSelection(
            "gemini-2.5-flash",
            ReasoningContract.Budget(0, 24576, true, true),
            GenerationSettings(maxOutputTokens = 8192, reasoning = ReasoningChoice.Budget(4096)),
            OutputTokenField.MAX_OUTPUT_TOKENS,
        )
        val request = ProfileRequestBuilder.build(connection, model, "System", "Source")
        val config = request.body["generationConfig"] as JsonObject
        val thinking = config["thinkingConfig"] as JsonObject
        thinking.keys shouldBe setOf("thinkingBudget")
        thinking["thinkingBudget"].toString() shouldBe "4096"
        config["maxOutputTokens"].toString() shouldBe "8192"
    }

    @Test
    fun `Gemini URL normalizes bare origin and strips operation suffixes without duplicating version`() {
        val model = ModelSelection("gemini-2.5-flash")

        val bareOrigin = ConnectionProfile(
            connectionId,
            "Gemini Bare",
            TranslationProtocol.GEMINI_GENERATE,
            "https://proxy.example.com",
            ProfileAuth.X_GOOG_API_KEY,
        )
        ProfileRequestBuilder.build(bareOrigin, model, "System", "Source").url shouldBe
            "https://proxy.example.com/v1beta/models/gemini-2.5-flash:generateContent"

        val operationSuffix = ConnectionProfile(
            connectionId,
            "Gemini Operation",
            TranslationProtocol.GEMINI_GENERATE,
            "https://proxy.example.com/v1/models/old-model:streamGenerateContent",
            ProfileAuth.X_GOOG_API_KEY,
        )
        ProfileRequestBuilder.build(operationSuffix, model, "System", "Source").url shouldBe
            "https://proxy.example.com/v1/models/gemini-2.5-flash:generateContent"
    }

    @Test
    fun `OpenAI DeepSeek and Ollama URLs normalize bare origins while preserving explicit subpaths`() {
        val openAiBare = ConnectionProfile(
            connectionId,
            "OpenAI Bare",
            TranslationProtocol.OPENAI_CHAT,
            "https://proxy.example.com",
            ProfileAuth.BEARER,
        )
        ProfileRequestBuilder.build(openAiBare, ModelSelection("gpt-4o-mini"), "System", "Source").url shouldBe
            "https://proxy.example.com/v1/chat/completions"

        val openAiSubpath = ConnectionProfile(
            connectionId,
            "Gemini OpenAI Compat",
            TranslationProtocol.OPENAI_CHAT,
            "https://generativelanguage.googleapis.com/v1beta/openai",
            ProfileAuth.BEARER,
        )
        ProfileRequestBuilder.build(openAiSubpath, ModelSelection("gemini-2.5-flash"), "System", "Source").url shouldBe
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"

        val deepSeekBare = ConnectionProfile(
            connectionId,
            "DeepSeek",
            TranslationProtocol.OPENAI_CHAT,
            "https://api.deepseek.com",
            ProfileAuth.BEARER,
            presetFlavor = PresetFlavor.DEEPSEEK,
        )
        ProfileRequestBuilder.build(deepSeekBare, ModelSelection("deepseek-chat"), "System", "Source").url shouldBe
            "https://api.deepseek.com/chat/completions"

        val ollamaBare = ConnectionProfile(
            connectionId,
            "Ollama",
            TranslationProtocol.OLLAMA_NATIVE,
            "https://ollama.example.com",
            ProfileAuth.NONE,
            presetFlavor = PresetFlavor.OLLAMA,
        )
        ProfileRequestBuilder.build(ollamaBare, ModelSelection("qwen3:8b"), "System", "Source").url shouldBe
            "https://ollama.example.com/api/chat"
    }

    @Test
    fun `Gemma 4 uses systemInstruction without safetySettings while Gemma 3 inlines system prompt`() {
        val connection = ConnectionProfile(
            connectionId,
            "Gemini",
            TranslationProtocol.GEMINI_GENERATE,
            "https://generativelanguage.googleapis.com/v1beta",
            ProfileAuth.X_GOOG_API_KEY,
        )

        val gemma4Request = ProfileRequestBuilder.build(
            connection = connection,
            model = ModelSelection(
                "gemma-4-31b-it",
                ReasoningContract.Toggle,
                GenerationSettings(reasoning = ReasoningChoice.Toggle(true)),
            ),
            systemPrompt = "System",
            userPrompt = "Source",
        )
        val gemma4System = gemma4Request.body["systemInstruction"]!!.jsonObject
        gemma4System["role"]!!.jsonPrimitive.content shouldBe "system"
        gemma4System["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content shouldBe "System"
        val gemma4Text = gemma4Request.body["contents"]!!
            .jsonArray[0].jsonObject["parts"]!!
            .jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        gemma4Text shouldBe "Source"
        gemma4Request.body.containsKey("safetySettings") shouldBe false
        val gemma4Thinking = gemma4Request.body["generationConfig"]!!
            .jsonObject["thinkingConfig"]!!
            .jsonObject["thinkingLevel"]!!.jsonPrimitive.content
        gemma4Thinking shouldBe "high"

        val gemma3Request = ProfileRequestBuilder.build(
            connection = connection,
            model = ModelSelection("gemma-3-27b-it"),
            systemPrompt = "System",
            userPrompt = "Source",
        )
        gemma3Request.body.containsKey("systemInstruction") shouldBe false
        val gemma3Text = gemma3Request.body["contents"]!!
            .jsonArray[0].jsonObject["parts"]!!
            .jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        gemma3Text shouldBe "System\n\nSource"
        gemma3Request.body.containsKey("safetySettings") shouldBe false
    }

    @Test
    fun `Gemini 2_5 Pro includes safetySettings and blank systemPrompt omits systemInstruction`() {
        val connection = ConnectionProfile(
            connectionId,
            "Gemini",
            TranslationProtocol.GEMINI_GENERATE,
            "https://generativelanguage.googleapis.com/v1beta",
            ProfileAuth.X_GOOG_API_KEY,
        )

        val proRequest = ProfileRequestBuilder.build(
            connection = connection,
            model = ModelSelection(
                "gemini-2.5-pro",
                ReasoningContract.Budget(128, 32768, canDisable = false, canUseDynamic = true),
                GenerationSettings(reasoning = ReasoningChoice.Budget(2048)),
            ),
            systemPrompt = "System",
            userPrompt = "Source",
        )
        proRequest.body["systemInstruction"]!!.jsonObject["role"]!!.jsonPrimitive.content shouldBe "system"
        proRequest.body["safetySettings"]!!.jsonArray.size shouldBe 5

        val blankSystemGemini = ProfileRequestBuilder.build(
            connection = connection,
            model = ModelSelection("gemini-2.5-flash"),
            systemPrompt = "   ",
            userPrompt = "Source",
        )
        blankSystemGemini.body.containsKey("systemInstruction") shouldBe false

        val blankSystemGemma4 = ProfileRequestBuilder.build(
            connection = connection,
            model = ModelSelection("gemma-4-31b-it"),
            systemPrompt = "   ",
            userPrompt = "Source",
        )
        blankSystemGemma4.body.containsKey("systemInstruction") shouldBe false
    }
}
