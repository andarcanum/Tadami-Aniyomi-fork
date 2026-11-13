package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

class GeminiModelCapabilitiesTest {
    private fun config(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject

    @Test
    fun `documented flash models send only a supported thinking level`() {
        val levels = listOf(
            "minimal" to """{"thinkingLevel":"minimal"}""",
            "low" to """{"thinkingLevel":"low"}""",
            "medium" to """{"thinkingLevel":"medium"}""",
            "high" to """{"thinkingLevel":"high"}""",
        )
        listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3-flash-preview").forEach { model ->
            levels.forEach { (level, expected) ->
                resolveGeminiModelCapabilities(model).thinkingConfig(level, budgetTokens = 1024) shouldBe
                    config(expected)
            }
        }
    }

    @Test
    fun `pro sends low or high and omits unsupported levels rather than guessing low`() {
        listOf("gemini-3-pro-preview", "gemini-3.1-pro-preview").forEach { model ->
            val capabilities = resolveGeminiModelCapabilities(model)
            capabilities.thinkingConfig("low") shouldBe config("""{"thinkingLevel":"low"}""")
            capabilities.thinkingConfig("high") shouldBe config("""{"thinkingLevel":"high"}""")
            capabilities.thinkingConfig("minimal").shouldBeNull()
            capabilities.thinkingConfig("medium").shouldBeNull()
            capabilities.thinkingConfig("invalid").shouldBeNull()
        }
    }

    @Test
    fun `Gemma 4 thinking is a minimal high toggle and does not accept low or medium`() {
        listOf("gemma-4-31b-it", "gemma-4-26b-a4b-it").forEach { model ->
            val capabilities = resolveGeminiModelCapabilities(model)
            capabilities.thinkingConfig("minimal", 512) shouldBe config("""{"thinkingLevel":"minimal"}""")
            capabilities.thinkingConfig(" HIGH ", 512) shouldBe config("""{"thinkingLevel":"high"}""")
            capabilities.thinkingConfig("low").shouldBeNull()
            capabilities.thinkingConfig("medium").shouldBeNull()
            capabilities.thinkingConfig("none").shouldBeNull()
        }
    }

    @Test
    fun `Gemini 2_5 uses only a validated per-model budget without a thinking level`() {
        val cases = listOf(
            Triple("gemini-2.5-pro", -1, -1),
            Triple("gemini-2.5-pro", 0, null),
            Triple("gemini-2.5-pro", 127, null),
            Triple("gemini-2.5-pro", 128, 128),
            Triple("gemini-2.5-pro", 32768, 32768),
            Triple("gemini-2.5-pro", 32769, null),
            Triple("gemini-2.5-flash", -1, -1),
            Triple("gemini-2.5-flash", 0, 0),
            Triple("gemini-2.5-flash", 1, 1),
            Triple("gemini-2.5-flash", 24576, 24576),
            Triple("gemini-2.5-flash", 24577, null),
            Triple("gemini-2.5-flash-lite", -1, -1),
            Triple("gemini-2.5-flash-lite", 0, 0),
            Triple("gemini-2.5-flash-lite", 1, null),
            Triple("gemini-2.5-flash-lite", 511, null),
            Triple("gemini-2.5-flash-lite", 512, 512),
            Triple("gemini-2.5-flash-lite", 24576, 24576),
            Triple("gemini-2.5-flash-lite", 24577, null),
        )
        cases.forEach { (model, budget, expected) ->
            val result = resolveGeminiModelCapabilities(model).thinkingConfig("high", budget)
            if (expected == null) {
                result.shouldBeNull()
            } else {
                result shouldBe config("""{"thinkingBudget":$expected}""")
            }
        }
        listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite").forEach { model ->
            resolveGeminiModelCapabilities(model).thinkingConfig("high", -2).shouldBeNull()
            resolveGeminiModelCapabilities(model).thinkingConfig("high", null).shouldBeNull()
        }
    }

    @Test
    fun `unknown models omit thinking even when discovery says thinking is supported`() {
        listOf("gemini-future-text", "gemma-4-unverified-it", "gemini-2.5-pro-unverified-preview").forEach { model ->
            val metadata = GeminiModelEntry(model, model, outputTokenLimit = 8192, thinking = true)
            val capabilities = resolveGeminiModelCapabilities(model, metadata)
            capabilities.thinkingConfig("high", 1024).shouldBeNull()
            capabilities.maxOutputTokens() shouldBe 8192
        }
    }

    @Test
    fun `explicit unsupported thinking metadata narrows a known contract`() {
        val metadata = GeminiModelEntry("gemini-3.5-flash-lite", "Flash Lite", thinking = false)
        resolveGeminiModelCapabilities(metadata.id, metadata).thinkingConfig("high", 1024).shouldBeNull()
    }

    @Test
    fun `default and invalid efforts omit optional wire fields instead of applying legacy fallback`() {
        val capabilities = resolveGeminiModelCapabilities("gemini-3.5-flash-lite")
        capabilities.thinkingConfig().shouldBeNull()
        capabilities.thinkingConfig("unsupported").shouldBeNull()
        capabilities.thinkingConfig(" Medium ") shouldBe config("""{"thinkingLevel":"medium"}""")
    }

    @Test
    fun `discovered output limit clamps the request while preserving a smaller requested budget`() {
        val metadata = GeminiModelEntry(
            "gemini-small-output-fixture",
            "Small output",
            inputTokenLimit = 131072,
            outputTokenLimit = 8192,
            temperature = 1f,
            maxTemperature = 2f,
            topP = 0.95f,
            topK = 64,
        )
        val capabilities = resolveGeminiModelCapabilities(metadata.id, metadata)
        capabilities.maxOutputTokens() shouldBe 8192
        capabilities.maxOutputTokens(4096) shouldBe 4096
        capabilities.inputTokenLimit shouldBe 131072
        capabilities.temperature shouldBe 1f
        capabilities.maxTemperature shouldBe 2f
        capabilities.topP shouldBe 0.95f
        capabilities.topK shouldBe 64
    }

    @Test
    fun `missing limits keep the compatible default but non-positive requests are rejected`() {
        val capabilities = resolveGeminiModelCapabilities("gemini-no-limits-fixture")
        capabilities.maxOutputTokens() shouldBe 16384
        capabilities.maxOutputTokens(2048) shouldBe 2048
        shouldThrow<IllegalArgumentException> { capabilities.maxOutputTokens(0) }
        shouldThrow<IllegalArgumentException> { capabilities.maxOutputTokens(-1) }
    }

    @Test
    fun `metadata for another model cannot leak its limits or thinking support across the ID fence`() {
        val foreign = GeminiModelEntry("gemma-4-31b-it", "Foreign model", outputTokenLimit = 1024, thinking = false)
        val capabilities = resolveGeminiModelCapabilities("gemini-unrelated-metadata-fixture", foreign)
        capabilities.maxOutputTokens() shouldBe 16384
        capabilities.thinkingConfig("high", 1024).shouldBeNull()
    }

    @Test
    fun `serialized public descriptors restore token limits sampling and thinking after restart`() {
        var persisted = ""
        val entry = GeminiModelEntry(
            "gemma-4-31b-it", "Gemma 4", inputTokenLimit = 131072, outputTokenLimit = 8192,
            temperature = 1f, maxTemperature = 2f, topP = 0.95f, topK = 64, thinking = true,
            supportedGenerationMethods = listOf("generateContent", "countTokens"),
        )
        GeminiModelDescriptorRegistry({ persisted }, { persisted = it }).recordCompletePublicCatalogue(listOf(entry))
        val restarted = GeminiModelDescriptorRegistry({ persisted })
        restarted.lookup(entry.id) shouldBe entry
        resolveGeminiModelCapabilities(entry.id, restarted.lookup(entry.id)).maxOutputTokens() shouldBe 8192
        Json.decodeFromString<List<GeminiModelEntry>>(persisted) shouldBe listOf(entry)
    }

    @Test
    fun `corrupt stored descriptors do not wipe last known good in-process limits`() {
        listOf(
            "not-json",
            "{}",
            """[{"id":"gemini-corrupt","displayName":"Bad","outputTokenLimit":0}]""",
        ).forEach { corrupt ->
            var available = false
            val registry = GeminiModelDescriptorRegistry(readPersisted = { if (available) corrupt else null })
            val good = GeminiModelEntry("gemini-good-memory-fixture", "Good", outputTokenLimit = 8192)
            registry.recordCompletePublicCatalogue(listOf(good))
            available = true
            registry.lookup(good.id) shouldBe good
            registry.lookup("gemini-corrupt").shouldBeNull()
        }
    }

    @Test
    fun `DI absence can retry restore without replacing newer memory facts`() {
        val old =
            GeminiModelEntry("gemini-late-restore-fixture", "Old", inputTokenLimit = 65536, outputTokenLimit = 8192)
        var available = false
        val registry =
            GeminiModelDescriptorRegistry(readPersisted = { if (available) Json.encodeToString(listOf(old)) else null })
        registry.recordCompletePublicCatalogue(
            listOf(old.copy(displayName = "New", inputTokenLimit = null, outputTokenLimit = 4096)),
        )
        available = true
        registry.lookup(old.id)?.outputTokenLimit shouldBe 4096
        registry.lookup(old.id)?.inputTokenLimit shouldBe 65536
        registry.lookup(old.id)?.displayName shouldBe "New"
    }

    @Test
    fun `descriptor persistence is bounded and contains no credential profile or availability fields`() {
        var persisted = ""
        val registry = GeminiModelDescriptorRegistry(writePersisted = { persisted = it })
        registry.recordCompletePublicCatalogue(
            List(1001) {
                GeminiModelEntry("gemini-capacity-$it", "Model", outputTokenLimit = 8192)
            },
        )
        val stored = Json.decodeFromString<List<GeminiModelEntry>>(persisted)
        stored.size shouldBe 1000
        registry.lookup("gemini-capacity-0").shouldBeNull()
        registry.lookup("gemini-capacity-1000")?.outputTokenLimit shouldBe 8192
        listOf("apiKey", "api_key", "baseUrl", "batchToken", "available").forEach {
            persisted.contains(it) shouldBe
                false
        }
    }

    @Test
    fun `failed optional persistence keeps the completed descriptors in memory`() {
        val registry = GeminiModelDescriptorRegistry(writePersisted = { throw java.io.IOException("private payload") })
        val entry = GeminiModelEntry("gemini-write-failure-fixture", "Model", outputTokenLimit = 8192)
        registry.recordCompletePublicCatalogue(listOf(entry))
        registry.lookup(entry.id) shouldBe entry
    }

    @Test
    fun `wire resolver accepts canonical resource IDs but does not apply reader legacy aliases`() {
        resolveGeminiModelCapabilities(" MODELS/GEMINI-3.5-FLASH-LITE ")
            .thinkingConfig("medium") shouldBe config("""{"thinkingLevel":"medium"}""")
        resolveGeminiModelCapabilities("gemini-2.5-flash")
            .thinkingConfig("high", 1024) shouldBe config("""{"thinkingBudget":1024}""")
    }

    @Test
    fun `complete descriptor removing top K must not resurrect prior sampling support`() {
        val registry = GeminiModelDescriptorRegistry()
        val model = "gemini-3.5-flash-lite"
        registry.recordCompletePublicCatalogue(
            listOf(GeminiModelEntry(model, model, topK = 40, supportedGenerationMethods = listOf("generateContent"))),
        )
        registry.lookup(model)?.topK shouldBe 40
        registry.recordCompletePublicCatalogue(
            listOf(GeminiModelEntry(model, model, supportedGenerationMethods = listOf("generateContent"))),
        )
        registry.lookup(model)?.topK shouldBe null
        resolveGeminiModelCapabilities(model, registry.lookup(model)).topK shouldBe null
    }
}
