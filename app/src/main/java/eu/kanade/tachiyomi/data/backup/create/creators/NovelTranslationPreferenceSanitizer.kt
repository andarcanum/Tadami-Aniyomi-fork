package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Sanitizes exported copies only. Backups explicitly including private preferences bypass this helper. */
internal object NovelTranslationPreferenceSanitizer {

    fun sanitize(preference: BackupPreference): BackupPreference? {
        if (preference.key in credentialPreferenceKeys) return null
        if (preference.key != SOURCE_OVERRIDES_KEY) return preference

        val raw = (preference.value as? StringPreferenceValue)?.value ?: return null
        val sanitized = runCatching {
            val overrides = Json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
            if (overrides.values.any { it !is JsonObject }) return@runCatching null

            // Reserialize even unchanged trees: duplicate JSON keys may hide credentials in the raw input.
            withoutCredentialFields(overrides).toString()
        }.getOrNull() ?: return null
        return preference.copy(value = StringPreferenceValue(sanitized))
    }

    private fun withoutCredentialFields(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { it !in credentialOverrideFields }
                .mapValues { (_, value) -> withoutCredentialFields(value) },
        )
        is JsonArray -> JsonArray(element.map(::withoutCredentialFields))
        else -> element
    }

    private const val SOURCE_OVERRIDES_KEY = "novel_reader_source_overrides"

    private val credentialPreferenceKeys = setOf(
        "novel_reader_gemini_api_key",
        "novel_reader_openrouter_api_key",
        "novel_reader_deepseek_api_key",
        "novel_reader_mistral_api_key",
        "novel_reader_nvidia_api_key",
        "novel_reader_ollama_cloud_api_key",
    )

    private val credentialOverrideFields = setOf(
        "geminiApiKey",
        "openRouterApiKey",
        "deepSeekApiKey",
        "mistralApiKey",
        "nvidiaApiKey",
        "ollamaCloudApiKey",
    )
}
