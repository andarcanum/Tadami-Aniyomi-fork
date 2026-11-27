package eu.kanade.tachiyomi.data.translation.profile

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.profile.LegacyCredentialScope
import tachiyomi.domain.translation.profile.ReasoningChoice
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LegacyPreferenceAdapterTest {
    private lateinit var preferences: NovelReaderPreferences
    private lateinit var store: AndroidPreferenceStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shared = context.getSharedPreferences("legacy-translation-test", Context.MODE_PRIVATE)
        shared.edit().clear().commit()
        store = AndroidPreferenceStore(context, shared)
        preferences = NovelReaderPreferences(store, Json { ignoreUnknownKeys = true })
    }

    @Test
    fun matchingKeysAreRemovedWithoutLosingUnknownSourceFields(): Unit = runTest {
        preferences.geminiApiKey().set("global-fixture")
        store.getString(LegacyPreferenceAccess.SOURCE_OVERRIDES).set(
            """{"-42":{"deepSeekApiKey":"source-fixture","geminiModel":"gemini-2.5-pro",
                "future":{"nested":[1,true]},"fontSize":23},"9":{"future":"keep"}}""",
        )
        val proofs = listOf(
            proof(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0, "global-fixture"),
            proof(LegacyProvider.GEMINI_PRIVATE, LegacyCredentialScope.GLOBAL, 0, "global-fixture"),
            proof(LegacyProvider.DEEPSEEK, LegacyCredentialScope.SOURCE, -42, "source-fixture"),
        )
        try {
            assertEquals(LegacyRemovalResult(3, 0), preferences.compareAndRemoveLegacyCredentials(proofs))
            assertFalse(preferences.geminiApiKey().isSet())
            val raw = store.getString(LegacyPreferenceAccess.SOURCE_OVERRIDES).get()
            val source = (Json.parseToJsonElement(raw) as JsonObject)["-42"] as JsonObject
            assertFalse("deepSeekApiKey" in source)
            assertEquals(Json.parseToJsonElement("""{"nested":[1,true]}"""), source["future"])
            assertEquals("gemini-2.5-pro", (source["geminiModel"] as kotlinx.serialization.json.JsonPrimitive).content)
            assertTrue(raw.contains("keep"))
        } finally {
            proofs.forEach { it.close() }
        }
    }

    @Test
    fun aLaterCredentialEditIsPreservedExactly(): Unit = runTest {
        preferences.deepSeekApiKey().set("later-fixture")
        val proof = proof(LegacyProvider.DEEPSEEK, LegacyCredentialScope.GLOBAL, 0, "earlier-fixture")
        try {
            assertEquals(LegacyRemovalResult(0, 1), preferences.compareAndRemoveLegacyCredentials(listOf(proof)))
            assertEquals("later-fixture", preferences.deepSeekApiKey().get())
        } finally {
            proof.close()
        }
    }

    @Test
    fun snapshotUsesEffectiveSourceSettingsAndCopiesOnlyOwnedKeys(): Unit = runTest {
        preferences.geminiApiKey().set("global-fixture")
        preferences.geminiModel().set("gemini-2.5-pro")
        store.getString(LegacyPreferenceAccess.SOURCE_OVERRIDES).set(
            """{"-42":{"geminiApiKey":null,"geminiTargetLang":"English","deepSeekApiKey":"owned-fixture"}}""",
        )
        val snapshot = LegacyTranslationPreferenceAdapter(preferences).readSnapshot()
        snapshot.use {
            val source = it.scopeSettings.single { scope -> scope.scopeId == -42L }
            val config = source.providers.single { config -> config.provider == LegacyProvider.GEMINI }
            assertEquals("gemini-2.5-pro", config.model?.exactModelId)
            assertEquals("English", config.languages.target)
            assertEquals(
                listOf(LegacyProvider.DEEPSEEK),
                it.credentials.filter { key -> key.scopeId == -42L }
                    .map { key -> key.provider },
            )
            assertFalse(it.toString().contains("fixture"))
        }
    }

    @Test
    fun liveReaderSettingsDoNotCarryLegacyPlaintext(): Unit = runTest {
        preferences.geminiApiKey().set("global-fixture")
        preferences.deepSeekApiKey().set("deepseek-fixture")
        val settings = preferences.resolveSettings(-42)
        assertEquals(-42L, settings.translationSourceId)
        assertEquals("", settings.geminiApiKey)
        assertEquals("", settings.deepSeekApiKey)
        assertFalse(settings.toString().contains("fixture"))
    }

    @Test
    fun adapterClampsFloatsNormalizesToggleAllowsLocalHttpAndIsolatesBrokenProvider(): Unit = runTest {
        preferences.geminiModel().set("gemma-4-31b-it")
        preferences.geminiReasoningEffort().set(" HIGH ")
        preferences.geminiTemperature().set(4.5f)
        preferences.geminiTopP().set(1.5f)
        preferences.openRouterBaseUrl().set("   ")
        preferences.ollamaCloudBaseUrl().set("http://localhost:11434/api")
        preferences.mistralBaseUrl().set("http://remote.example/v1")
        store.getString(LegacyPreferenceAccess.SOURCE_OVERRIDES).set(
            """{"-42":{"deepSeekModel":123,"geminiApiKey":"source-gem-key"}}""",
        )

        val adapter = LegacyTranslationPreferenceAdapter(preferences, store)
        val snapshot = adapter.readSnapshot()
        snapshot.use { snap ->
            assertTrue(snap.hasConfiguredKeylessSettings)
            val global = snap.scopeSettings.single { it.scope == LegacyCredentialScope.GLOBAL }
            val gemma = global.providers.single { it.provider == LegacyProvider.GEMINI }
            assertEquals(ReasoningChoice.Toggle(true), gemma.model?.generation?.reasoning)

            val openRouter = global.providers.single { it.provider == LegacyProvider.OPENROUTER }
            assertEquals("https://openrouter.ai/api/v1", openRouter.baseUrl)

            val ollama = global.providers.single { it.provider == LegacyProvider.OLLAMA_CLOUD }
            assertTrue(ollama.allowInsecureLocalHTTP)
            assertEquals(2.0, ollama.model?.generation?.temperature)
            assertEquals(1.0, ollama.model?.generation?.topP)

            val mistral = global.providers.single { it.provider == LegacyProvider.MISTRAL }
            assertFalse(mistral.allowInsecureLocalHTTP)

            val source = snap.scopeSettings.single { it.scopeId == -42L }
            val badDeepSeek = source.providers.single { it.provider == LegacyProvider.DEEPSEEK }
            assertEquals(LegacyConfigurationIssue.INVALID_SETTING, badDeepSeek.issue)
            assertNull(source.providers.single { it.provider == LegacyProvider.GEMINI }.issue)
            assertNotNull(snap.credentials.singleOrNull { it.provider == LegacyProvider.GEMINI && it.scopeId == -42L })
        }

        assertFalse(adapter.hasCompletedKeylessMigration())
        adapter.markKeylessMigrationComplete()
        assertTrue(adapter.hasCompletedKeylessMigration())
    }

    private fun proof(provider: LegacyProvider, scope: LegacyCredentialScope, id: Long, value: String) =
        VerifiedLegacyCredential(provider, scope, id, "00000000-0000-0000-0000-000000000001", CredentialSecret(value))
}
