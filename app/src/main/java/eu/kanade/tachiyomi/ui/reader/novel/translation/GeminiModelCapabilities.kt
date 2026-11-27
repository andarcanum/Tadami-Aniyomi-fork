package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Wire contracts verified 2026-11-11; discovery's thinking boolean cannot supply these ranges. */
internal sealed interface GeminiThinkingContract {
    data object Unknown : GeminiThinkingContract
    data object Unsupported : GeminiThinkingContract

    data class Levels(val options: List<String>, val defaultLevel: String) : GeminiThinkingContract

    /** Gemma 4 uses thinkingLevel minimal (off) or high (on), not Gemini's four-level scale. */
    data object Toggle : GeminiThinkingContract

    data class TokenBudget(
        val minimum: Int,
        val maximum: Int,
        val canDisable: Boolean,
        val canUseDynamic: Boolean = true,
    ) : GeminiThinkingContract {
        fun accepts(value: Int): Boolean =
            value in minimum..maximum || (value == 0 && canDisable) || (value == -1 && canUseDynamic)
    }
}

internal data class GeminiModelCapabilities(
    val thinking: GeminiThinkingContract = GeminiThinkingContract.Unknown,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val temperature: Float? = null,
    val maxTemperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
) {
    val reasoningOptions: List<String>
        get() = when (val contract = thinking) {
            is GeminiThinkingContract.Levels -> contract.options
            GeminiThinkingContract.Toggle -> listOf("minimal", "high")
            else -> emptyList()
        }

    /** Invalid or unknown optional reasoning is omitted, never guessed or silently clamped. */
    fun thinkingConfig(reasoningEffort: String = "", budgetTokens: Int? = null): JsonObject? {
        val level = reasoningEffort.trim().lowercase()
        return when (val contract = thinking) {
            is GeminiThinkingContract.Levels -> level.takeIf { it in contract.options }?.let {
                buildJsonObject { put("thinkingLevel", it) }
            }
            GeminiThinkingContract.Toggle -> level.takeIf { it == "minimal" || it == "high" }?.let {
                buildJsonObject { put("thinkingLevel", it) }
            }
            is GeminiThinkingContract.TokenBudget -> budgetTokens?.takeIf(contract::accepts)?.let {
                buildJsonObject { put("thinkingBudget", it) }
            }
            GeminiThinkingContract.Unknown,
            GeminiThinkingContract.Unsupported,
            -> null
        }
    }

    /** maxOutputTokens includes thought tokens; the chunk planner must also reserve their space. */
    fun maxOutputTokens(requestedTokens: Int = GEMINI_TRANSLATION_MAX_OUTPUT_TOKENS): Int {
        require(requestedTokens > 0) { "Output token budget must be positive" }
        return outputTokenLimit?.takeIf { it > 0 }?.let { minOf(requestedTokens, it) } ?: requestedTokens
    }
}

/**
 * Public Google model properties only, not a credential's availability list. A complete successful
 * fetch may refresh descriptors; omitted models/fields do not erase last-known-good limits.
 * No credentials, URLs, profiles or custom-origin descriptors are stored here.
 */
internal object GeminiModelMetadataStore {
    private val registry = GeminiModelDescriptorRegistry(
        readPersisted = { Injekt.get<NovelReaderPreferences>().geminiModelDescriptors().get() },
        writePersisted = { Injekt.get<NovelReaderPreferences>().geminiModelDescriptors().set(it) },
    )

    fun lookup(model: String): GeminiModelEntry? = registry.lookup(model)

    fun recordCompletePublicCatalogue(entries: List<GeminiModelEntry>) = registry.recordCompletePublicCatalogue(entries)
}

/** Plain descriptor JSON only; injection keeps tests independent of process-global preferences. */
internal class GeminiModelDescriptorRegistry(
    private val readPersisted: () -> String? = { null },
    private val writePersisted: (String) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var descriptors: Map<String, GeminiModelEntry> = emptyMap()
    private var restored = false

    @Synchronized
    fun lookup(model: String): GeminiModelEntry? {
        restoreIfAvailable()
        return descriptors[normalizeGeminiCapabilityModelId(model)]
    }

    @Synchronized
    fun recordCompletePublicCatalogue(entries: List<GeminiModelEntry>) {
        restoreIfAvailable()
        val updated = descriptors.toMutableMap()
        entries.forEach { entry ->
            val id = normalizeGeminiCapabilityModelId(entry.id)
            val previous = updated.remove(id)
            updated[id] = mergeDescriptor(entry.copy(id = id), previous)
        }
        descriptors = bounded(updated)
        val encoded = runCatching { json.encodeToString(descriptors.values.toList()) }.getOrNull() ?: return
        if (encoded.length <= MAX_DESCRIPTOR_JSON_CHARS) runCatching { writePersisted(encoded) }
    }

    private fun restoreIfAvailable() {
        if (restored) return
        // Missing DI is not a permanent empty snapshot: retry when the app finishes installing it.
        val raw = runCatching { readPersisted() }.getOrNull() ?: return
        restored = true
        if (raw.isBlank() || raw.length > MAX_DESCRIPTOR_JSON_CHARS) return
        val stored = runCatching { json.decodeFromString<List<GeminiModelEntry>>(raw) }.getOrNull() ?: return
        if (stored.size > MAX_DESCRIPTORS || stored.any { !validDescriptor(it) }) return
        val ids = stored.map { normalizeGeminiCapabilityModelId(it.id) }
        if (ids.distinct().size != ids.size) return
        val merged = stored.associateBy { normalizeGeminiCapabilityModelId(it.id) }.toMutableMap()
        // More recent in-process facts win, but absent fields may recover old known limits.
        descriptors.forEach { (id, entry) -> merged[id] = mergeDescriptor(entry, merged.remove(id)) }
        descriptors = bounded(merged)
    }

    private fun bounded(entries: Map<String, GeminiModelEntry>): Map<String, GeminiModelEntry> =
        entries.entries.toList().takeLast(MAX_DESCRIPTORS).associate { it.key to it.value }

    private fun validDescriptor(entry: GeminiModelEntry): Boolean =
        isSupportedGeminiTextModelId(entry.id) && normalizeGeminiCapabilityModelId(entry.id).length <= 200 &&
            entry.inputTokenLimit?.let { it > 0 } != false && entry.outputTokenLimit?.let { it > 0 } != false &&
            listOf(entry.temperature, entry.maxTemperature, entry.topP).all { it == null || it.isFinite() } &&
            entry.maxTemperature?.let { it >= 0f } != false

    private fun mergeDescriptor(entry: GeminiModelEntry, previous: GeminiModelEntry?): GeminiModelEntry = entry.copy(
        inputTokenLimit = entry.inputTokenLimit ?: previous?.inputTokenLimit,
        outputTokenLimit = entry.outputTokenLimit ?: previous?.outputTokenLimit,
        temperature = entry.temperature ?: previous?.temperature,
        maxTemperature = entry.maxTemperature ?: previous?.maxTemperature,
        topP = entry.topP ?: previous?.topP,
        // In a complete API descriptor, absent topK explicitly means the model does not accept it.
        topK = if ("generateContent" in entry.supportedGenerationMethods) entry.topK else entry.topK ?: previous?.topK,
        thinking = entry.thinking ?: previous?.thinking,
        supportedGenerationMethods = entry.supportedGenerationMethods.toList(),
    )

    private companion object {
        const val MAX_DESCRIPTORS = 1000
        const val MAX_DESCRIPTOR_JSON_CHARS = 2 * 1024 * 1024
    }
}

/**
 * Resolver for the public Gemini generateContent protocol. Metadata can narrow support/limits, but
 * cannot invent a wire contract. Exact IDs are intentional; future/specialized variants stay unknown.
 * This does not apply the reader's legacy preference aliases (notably gemini-2.5-flash).
 *
 * Levels/budgets: https://aistudio.google.com/docs/thinking?generate-content=true
 * Gemma toggle: https://gemma.ai/deployment/gemini-api/
 */
internal fun resolveGeminiModelCapabilities(
    model: String,
    metadata: GeminiModelEntry? = null,
): GeminiModelCapabilities {
    val id = normalizeGeminiCapabilityModelId(model)
    val descriptor = if (metadata == null) {
        GeminiModelMetadataStore.lookup(id)
    } else {
        metadata.takeIf { normalizeGeminiCapabilityModelId(it.id) == id }
    }
    val contract = when {
        !isSupportedGeminiTextModelId(id) -> GeminiThinkingContract.Unsupported
        descriptor?.thinking == false -> GeminiThinkingContract.Unsupported
        else -> resolveBundledGeminiThinkingContract(id)
    }
    return GeminiModelCapabilities(
        thinking = contract,
        inputTokenLimit = descriptor?.inputTokenLimit?.takeIf { it > 0 },
        outputTokenLimit = descriptor?.outputTokenLimit?.takeIf { it > 0 },
        temperature = descriptor?.temperature,
        maxTemperature = descriptor?.maxTemperature,
        topP = descriptor?.topP,
        topK = descriptor?.topK,
    )
}

internal fun normalizeGeminiCapabilityModelId(model: String): String = model.trim().lowercase().removePrefix("models/")

internal fun isSupportedGeminiTextModelId(model: String): Boolean {
    val id = normalizeGeminiCapabilityModelId(model)
    return (id.startsWith("gemini-") || id.startsWith("gemma-4-")) &&
        GEMINI_SPECIALIZED_MODEL_ID_PARTS.none { it in id }
}

private val GEMINI_SPECIALIZED_MODEL_ID_PARTS = listOf("-tts", "-image", "native-audio", "computer-use", "embed")

private fun resolveBundledGeminiThinkingContract(id: String): GeminiThinkingContract = when (id) {
    "gemini-3-flash",
    "gemini-3-flash-preview",
    -> GeminiThinkingContract.Levels(listOf("minimal", "low", "medium", "high"), defaultLevel = "high")
    "gemini-3.1-flash-lite",
    "gemini-3.1-flash-lite-preview",
    "gemini-3.5-flash-lite",
    "gemini-3.5-flash-lite-preview",
    -> GeminiThinkingContract.Levels(listOf("minimal", "low", "medium", "high"), defaultLevel = "minimal")
    "gemini-3-pro",
    "gemini-3-pro-preview",
    "gemini-3.1-pro",
    "gemini-3.1-pro-preview",
    -> GeminiThinkingContract.Levels(listOf("low", "high"), defaultLevel = "high")
    "gemma-4-31b-it",
    "gemma-4-26b-a4b-it",
    -> GeminiThinkingContract.Toggle
    "gemini-2.5-pro" -> GeminiThinkingContract.TokenBudget(minimum = 128, maximum = 32768, canDisable = false)
    "gemini-2.5-flash" -> GeminiThinkingContract.TokenBudget(minimum = 0, maximum = 24576, canDisable = true)
    "gemini-2.5-flash-lite" -> GeminiThinkingContract.TokenBudget(minimum = 512, maximum = 24576, canDisable = true)
    else -> GeminiThinkingContract.Unknown
}
