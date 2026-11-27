package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NovelTranslationPreferenceSanitizerTest {

    @Test
    fun `non private sanitizer excludes all six raw ai credential preferences`() {
        for (key in credentialKeys) {
            NovelTranslationPreferenceSanitizer.sanitize(
                BackupPreference(key, StringPreferenceValue("fixture-only")),
            ) shouldBe null
        }
    }

    @Test
    fun `source override credentials are removed while known and unknown settings survive`() {
        assertSanitizedOverrides(
            input = """
                {
                  "7": {
                    "geminiApiKey": "fixture-only",
                    "openRouterApiKey": "fixture-only",
                    "deepSeekApiKey": "fixture-only",
                    "mistralApiKey": "fixture-only",
                    "nvidiaApiKey": "fixture-only",
                    "ollamaCloudApiKey": "fixture-only",
                    "geminiModel": "fixture-model",
                    "geminiEnabled": true,
                    "geminiReasoningEffort": "minimal",
                    "translationProvider": "GEMINI",
                    "openRouterBaseUrl": "https://example.org/api",
                    "fontSize": 21,
                    "futureOption": {"items": [1, null, "keep"], "enabled": false},
                    "geminiApiKeyHint": "keep"
                  },
                  "19": {"fontSize": 18, "futureText": "keep", "futureNull": null}
                }
            """.trimIndent(),
            expected = """
                {
                  "7": {
                    "geminiModel": "fixture-model",
                    "geminiEnabled": true,
                    "geminiReasoningEffort": "minimal",
                    "translationProvider": "GEMINI",
                    "openRouterBaseUrl": "https://example.org/api",
                    "fontSize": 21,
                    "futureOption": {"items": [1, null, "keep"], "enabled": false},
                    "geminiApiKeyHint": "keep"
                  },
                  "19": {"fontSize": 18, "futureText": "keep", "futureNull": null}
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `known credential fields are also removed inside nested future objects and arrays`() {
        assertSanitizedOverrides(
            input = """
                {"7":{"future":{"items":[{"openRouterApiKey":"fixture-only","name":"keep"},2,null],
                "deepSeekApiKey":"fixture-only"},"fontSize":18}}
            """.trimIndent(),
            expected = """
                {"7":{"future":{"items":[{"name":"keep"},2,null]},"fontSize":18}}
            """.trimIndent(),
        )
    }

    @Test
    fun `override credential fields are removed regardless of their value type`() {
        assertSanitizedOverrides(
            input = """
                {"7":{"geminiApiKey":null,"openRouterApiKey":["fixture-only"],
                "deepSeekApiKey":{"value":"fixture-only"},"mistralApiKey":42,
                "nvidiaApiKey":true,"ollamaCloudApiKey":"","fontSize":18}}
            """.trimIndent(),
            expected = """{"7":{"fontSize":18}}""",
        )
    }

    @Test
    fun `malformed source overrides are dropped rather than exported`() {
        val malformed = listOf(
            "",
            "not JSON",
            """{"7":{"geminiApiKey":"fixture-only"}""",
            """{"7":{}} {"8":{"geminiApiKey":"fixture-only"}}""",
            """{"7":{"geminiApiKey":"fixture-only",}}""",
        )

        for (raw in malformed) {
            sanitizeOverrides(raw) shouldBe null
        }
    }

    @Test
    fun `wrong source override shapes are dropped rather than exported`() {
        val wrongShapes = listOf(
            "null",
            "42",
            "\"fixture-only\"",
            """[{"geminiApiKey":"fixture-only"}]""",
            """{"7":"geminiApiKey fixture-only"}""",
            """{"7":null}""",
            """{"7":[]}""",
            """{"7":{"fontSize":18},"8":"geminiApiKey fixture-only"}""",
        )

        for (raw in wrongShapes) {
            sanitizeOverrides(raw) shouldBe null
        }
    }

    @Test
    fun `non string source override preferences are dropped`() {
        val values = listOf(
            IntPreferenceValue(7),
            BooleanPreferenceValue(false),
            StringSetPreferenceValue(setOf("fixture-only")),
        )

        for (value in values) {
            NovelTranslationPreferenceSanitizer.sanitize(BackupPreference(sourceOverridesKey, value)) shouldBe null
        }
    }

    @Test
    fun `duplicate source entries are reserialized so discarded credentials cannot leak`() {
        assertSanitizedOverrides(
            input = """{"7":{"geminiApiKey":"fixture-only"},"7":{"fontSize":18}}""",
            expected = """{"7":{"fontSize":18}}""",
        )
    }

    @Test
    fun `escaped credential field names are redacted`() {
        assertSanitizedOverrides(
            input = """{"7":{"gemini\u0041piKey":"fixture-only","fontSize":18}}""",
            expected = """{"7":{"fontSize":18}}""",
        )
    }

    @Test
    fun `empty overrides and empty source objects remain valid`() {
        assertSanitizedOverrides(input = "{}", expected = "{}")
        assertSanitizedOverrides(input = """{"7":{}}""", expected = """{"7":{}}""")
    }

    @Test
    fun `unrelated preferences pass through without reinterpretation`() {
        val preferences = listOf(
            BackupPreference("novel_reader_gemini_model", StringPreferenceValue("fixture-model")),
            BackupPreference("novel_reader_nvidia_api_key_hint", StringPreferenceValue("keep")),
            BackupPreference("novel_reader_future_option", StringPreferenceValue("not JSON")),
            BackupPreference("novel_reader_gemini_enabled", BooleanPreferenceValue(true)),
        )

        for (preference in preferences) {
            NovelTranslationPreferenceSanitizer.sanitize(preference) shouldBe preference
        }
    }

    @Test
    fun `non private app backup excludes credentials and private keys while preserving settings`() {
        val preferences = credentialKeys.associateWith { "fixture-only" } + mapOf(
            sourceOverridesKey to """{"7":{"geminiApiKey":"fixture-only","fontSize":18,"future":[1,null]}}""",
            "novel_reader_gemini_enabled" to true,
            "novel_reader_gemini_model" to "fixture-model",
            Preference.privateKey("fixture") to "private-fixture",
            Preference.appStateKey("fixture") to "state-fixture",
        )

        val backup = creator(preferences).createApp(includePrivatePreferences = false).associate { it.key to it.value }

        backup.keys shouldBe setOf(sourceOverridesKey, "novel_reader_gemini_enabled", "novel_reader_gemini_model")
        backup["novel_reader_gemini_enabled"] shouldBe BooleanPreferenceValue(true)
        backup["novel_reader_gemini_model"] shouldBe StringPreferenceValue("fixture-model")
        val overrides = (backup[sourceOverridesKey] as StringPreferenceValue).value
        Json.parseToJsonElement(overrides) shouldBe Json.parseToJsonElement(
            """{"7":{"fontSize":18,"future":[1,null]}}""",
        )
    }

    @Test
    fun `non private app backup drops only the malformed override preference`() {
        val preferences = mapOf(
            sourceOverridesKey to """{"7":{"geminiApiKey":"fixture-only"}""",
            "novel_reader_font_size" to 18,
            "novel_reader_future_option" to "keep",
        )

        creator(preferences).createApp(includePrivatePreferences = false) shouldBe listOf(
            BackupPreference("novel_reader_font_size", IntPreferenceValue(18)),
            BackupPreference("novel_reader_future_option", StringPreferenceValue("keep")),
        )
    }

    @Test
    fun `private app backup intentionally retains raw credentials and unsanitized overrides`() {
        val rawOverrides = listOf(
            """{ "7": { "geminiApiKey": "fixture-only", "fontSize": 18 } }""",
            """{"7":{"geminiApiKey":"fixture-only"}""",
        )

        for (raw in rawOverrides) {
            val preferences = credentialKeys.associateWith { "fixture-only" } + mapOf(
                sourceOverridesKey to raw,
                Preference.privateKey("fixture") to "private-fixture",
                Preference.appStateKey("fixture") to "state-fixture",
            )
            val expected = credentialKeys.associateWith { StringPreferenceValue("fixture-only") } + mapOf(
                sourceOverridesKey to StringPreferenceValue(raw),
                Preference.privateKey("fixture") to StringPreferenceValue("private-fixture"),
            )

            creator(preferences).createApp(includePrivatePreferences = true)
                .associate { it.key to it.value } shouldBe expected
        }
    }

    private fun assertSanitizedOverrides(input: String, expected: String) {
        val sanitized = sanitizeOverrides(input).shouldNotBeNull()
        Json.parseToJsonElement(sanitized) shouldBe Json.parseToJsonElement(expected)
    }

    private fun sanitizeOverrides(raw: String): String? {
        val sanitized = NovelTranslationPreferenceSanitizer.sanitize(
            BackupPreference(sourceOverridesKey, StringPreferenceValue(raw)),
        )
        return (sanitized?.value as? StringPreferenceValue)?.value
    }

    private fun creator(preferences: Map<String, *>): PreferenceBackupCreator {
        val store = mockk<PreferenceStore>()
        every { store.getAll() } returns preferences
        return PreferenceBackupCreator(
            animeSourceManager = mockk(),
            mangaSourceManager = mockk(),
            preferenceStore = store,
        )
    }

    private val credentialKeys = listOf(
        "novel_reader_gemini_api_key",
        "novel_reader_openrouter_api_key",
        "novel_reader_deepseek_api_key",
        "novel_reader_mistral_api_key",
        "novel_reader_nvidia_api_key",
        "novel_reader_ollama_cloud_api_key",
    )

    private val sourceOverridesKey = "novel_reader_source_overrides"
}
