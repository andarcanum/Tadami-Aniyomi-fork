package eu.kanade.tachiyomi.data.translation.profile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.profile.LegacyCredentialScope

/** Raw values never enter a saved UI state, diagnostics, profile JSON or a queue snapshot. */
internal class LegacyPreferenceValues(internal val values: Map<String, *>) {
    override fun toString(): String = "LegacyPreferenceValues([REDACTED])"
}

internal data class LegacyCredentialField(val globalKey: String, val sourceField: String)

internal object LegacyPreferenceAccess {
    const val SOURCE_OVERRIDES = "novel_reader_source_overrides"

    // Gemini and the private preset intentionally share the same physical legacy preference.
    fun field(provider: LegacyProvider): LegacyCredentialField = when (provider) {
        LegacyProvider.GEMINI, LegacyProvider.GEMINI_PRIVATE ->
            LegacyCredentialField("novel_reader_gemini_api_key", "geminiApiKey")
        LegacyProvider.OPENROUTER -> LegacyCredentialField("novel_reader_openrouter_api_key", "openRouterApiKey")
        LegacyProvider.DEEPSEEK -> LegacyCredentialField("novel_reader_deepseek_api_key", "deepSeekApiKey")
        LegacyProvider.MISTRAL -> LegacyCredentialField("novel_reader_mistral_api_key", "mistralApiKey")
        LegacyProvider.NVIDIA -> LegacyCredentialField("novel_reader_nvidia_api_key", "nvidiaApiKey")
        LegacyProvider.OLLAMA_CLOUD ->
            LegacyCredentialField("novel_reader_ollama_cloud_api_key", "ollamaCloudApiKey")
    }

    fun sourceObjects(values: Map<String, *>): JsonObject {
        val raw = values[SOURCE_OVERRIDES] ?: return JsonObject(emptyMap())
        if (raw !is String || raw.length > 4 * 1024 * 1024) invalid()
        if (raw.isEmpty()) return JsonObject(emptyMap())
        return try {
            Json.parseToJsonElement(raw) as? JsonObject ?: invalid()
        } catch (_: Exception) {
            invalid()
        }
    }

    fun sourceCredential(values: Map<String, *>, provider: LegacyProvider, sourceId: Long): String? {
        val sources = sourceObjects(values)
        val source = sources[sourceId.toString()] as? JsonObject ?: return null
        val value = source[field(provider).sourceField] ?: return null
        if (value == kotlinx.serialization.json.JsonNull) return null
        return (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()
    }

    fun globalCredential(values: Map<String, *>, provider: LegacyProvider): String =
        values[field(provider).globalKey]?.let { it as? String ?: invalid() }.orEmpty()

    fun credential(values: Map<String, *>, provider: LegacyProvider, sourceId: Long?): CredentialSecret? {
        val raw = sourceId?.let { sourceCredential(values, provider, it) } ?: globalCredential(values, provider)
        return raw.takeIf { it.isNotBlank() }?.let(::CredentialSecret)
    }

    /** Caller serializes this operation with all legacy credential and source-override writers. */
    fun remove(store: PreferenceStore, proofs: List<VerifiedLegacyCredential>): LegacyRemovalResult {
        val values = store.getAll()
        val sources = sourceObjects(values).toMutableMap()
        var removed = 0
        var changed = 0
        var sourcesChanged = false
        val physical = proofs.groupBy {
            Triple(
                it.scope,
                it.scopeId,
                if (it.scope == LegacyCredentialScope.GLOBAL) {
                    field(it.provider).globalKey
                } else {
                    field(it.provider).sourceField
                },
            )
        }
        physical.forEach { (location, matching) ->
            val (scope, scopeId, key) = location
            if (scope == LegacyCredentialScope.GLOBAL) {
                val current = values[key] as? String
                if (current != null && matching.all { it.matches(current) }) {
                    store.getString(key).delete()
                    removed += matching.size
                } else {
                    changed += matching.size
                }
            } else {
                val source = sources[scopeId.toString()] as? JsonObject
                val current = (source?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (source != null && current != null && matching.all { it.matches(current) }) {
                    sources[scopeId.toString()] = JsonObject(source.toMutableMap().apply { remove(key) })
                    sourcesChanged = true
                    removed += matching.size
                } else {
                    changed += matching.size
                }
            }
        }
        if (sourcesChanged) store.getString(SOURCE_OVERRIDES).set(JsonObject(sources).toString())
        return LegacyRemovalResult(removed, changed)
    }

    private fun invalid(): Nothing = throw IllegalStateException("Invalid legacy translation preferences")
}
