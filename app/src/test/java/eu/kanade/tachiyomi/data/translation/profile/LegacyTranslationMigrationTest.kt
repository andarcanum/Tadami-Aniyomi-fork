package eu.kanade.tachiyomi.data.translation.profile

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.CredentialRoutingState
import tachiyomi.domain.translation.profile.ExecutionPolicy
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.LegacyCredentialReference
import tachiyomi.domain.translation.profile.LegacyCredentialScope
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAssignment
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.ProfileRequestReservation
import tachiyomi.domain.translation.profile.PromptSettings
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.RequestBudgetLedger
import tachiyomi.domain.translation.profile.RoutingFailure
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import tachiyomi.domain.translation.profile.TranslationProtocol

class LegacyTranslationMigrationTest {
    @Test
    fun `global and source configurations migrate before only verified raw keys are removed`() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.settings.keys[Location(LegacyProvider.DEEPSEEK, LegacyCredentialScope.SOURCE, -42)] = "source-value"
        val result = fixture.migration.migrate(100)
        result.status shouldBe LegacyMigrationStatus.COMPLETE
        result.removed shouldBe 7
        fixture.settings.keys shouldBe emptyMap()
        fixture.settings.unknownSourceFields shouldBe mapOf("fontSize" to "21", "future" to "retained")
        fixture.profiles.catalog.connections.size shouldBe 12
        fixture.profiles.catalog.profiles.size shouldBe 12
        fixture.profiles.catalog.legacyReferences.size shouldBe 7
        fixture.profiles.catalog.profiles.filter { it.model.exactModelId == "gemini-2.5-pro" }.size shouldBe 2
        fixture.profiles.catalog.profiles.all { it.languages.target == "Russian" } shouldBe true
        fixture.profiles.catalog.profiles.all { it.prompt.customText == "fixture directive" } shouldBe true
        fixture.vault.removals shouldBe 0
    }

    @Test
    fun `readback and imported reference verification occur before adapter removal`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.COMPLETE
        val events = fixture.events
        (events.indexOf("put") < events.indexOf("import")) shouldBe true
        (events.indexOf("import") < events.indexOf("linked-read")) shouldBe true
        (events.indexOf("linked-read") < events.indexOf("remove-legacy")) shouldBe true
        (fixture.vault.reads > 1) shouldBe true
    }

    @Test
    fun `vault write failure preserves all legacy keys and avoids configuration import`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.vault.failWrite = true
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.NEEDS_ACTION
        fixture.settings.keys.values.toList() shouldBe listOf("fixture-GEMINI")
        fixture.profiles.imports shouldBe 0
        fixture.settings.removeCalls shouldBe 0
    }

    @Test
    fun `readback mismatch preserves original values and never links or deletes`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.vault.mismatchAfterWrite = true
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.NEEDS_ACTION
        fixture.settings.keys.values.toList() shouldBe listOf("fixture-GEMINI")
        fixture.profiles.imports shouldBe 0
        fixture.settings.removeCalls shouldBe 0
    }

    @Test
    fun `missing key after copy leaves legacy plaintext in place`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.vault.unavailableAfterWrite = true
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.NEEDS_ACTION
        fixture.settings.removeCalls shouldBe 0
        fixture.settings.keys.size shouldBe 1
    }

    @Test
    fun `interrupted import safely reuses the stable copied key on restart`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.profiles.failImport = true
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.FAILED
        fixture.vault.puts shouldBe 1
        fixture.settings.keys.size shouldBe 1
        fixture.profiles.failImport = false
        fixture.migration.migrate(101).status shouldBe LegacyMigrationStatus.COMPLETE
        fixture.vault.puts shouldBe 1
        fixture.settings.keys shouldBe emptyMap()
    }

    @Test
    fun `failed imported reference lookup prevents any plaintext removal`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.profiles.hideImportedReferences = true
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.FAILED
        fixture.settings.removeCalls shouldBe 0
        fixture.settings.keys.size shouldBe 1
    }

    @Test
    fun `changed legacy value is preserved without overwriting the copied credential`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.settings.beforeRemoval = {
            fixture.settings.keys[Location(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0)] = "changed-value"
        }
        val result = fixture.migration.migrate(100)
        result.status shouldBe LegacyMigrationStatus.NEEDS_REVIEW
        result.changed shouldBe 1
        fixture.settings.keys.values.toList() shouldBe listOf("changed-value")
        fixture.vault.values.values.toList() shouldBe listOf("fixture-GEMINI")
        fixture.settings.beforeRemoval = null
        fixture.migration.migrate(101).status shouldBe LegacyMigrationStatus.NEEDS_REVIEW
        fixture.vault.values.values.toList() shouldBe listOf("fixture-GEMINI")
    }

    @Test
    fun `valid different vault value is not overwritten even before a link exists`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.migration.migrate(100)
        val id = fixture.profiles.catalog.legacyReferences.single().credentialId
        fixture.settings.keys[Location(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0)] = "new-value"
        fixture.vault.values[id] = "old-value"
        fixture.migration.migrate(101).status shouldBe LegacyMigrationStatus.NEEDS_REVIEW
        fixture.vault.values[id] shouldBe "old-value"
        fixture.settings.keys.values.toList() shouldBe listOf("new-value")
    }

    @Test
    fun `linked unavailable vault value is never silently replaced from legacy text`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.migration.migrate(100)
        fixture.settings.keys[Location(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0)] = "new-value"
        fixture.vault.unavailable = true
        val before = fixture.vault.puts
        fixture.migration.migrate(101).status shouldBe LegacyMigrationStatus.NEEDS_ACTION
        fixture.vault.puts shouldBe before
        fixture.settings.keys.size shouldBe 1
    }

    @Test
    fun `active missing model is a whole snapshot failure before any write`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.settings.scopes = fixture.settings.scopes.map { scope ->
            scope.copy(
                providers = scope.providers.map {
                    if (it.provider == scope.selectedProvider) it.copy(model = null) else it
                },
            )
        }
        val result = fixture.migration.migrate(100)
        result.status shouldBe LegacyMigrationStatus.NEEDS_ACTION
        fixture.vault.puts shouldBe 0
        fixture.profiles.imports shouldBe 0
        fixture.settings.keys.size shouldBe 1
    }

    @Test
    fun `inactive empty model preserves credential reference without inventing a profile`() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.settings.scopes = fixture.settings.scopes.map { scope ->
            scope.copy(
                providers = scope.providers.map {
                    if (it.provider == LegacyProvider.OPENROUTER) it.copy(model = null) else it
                },
            )
        }
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.COMPLETE
        fixture.profiles.catalog.profiles.size shouldBe 10
        fixture.profiles.catalog.legacyReferences.any { it.provider == "OPENROUTER" } shouldBe true
        fixture.settings.keys shouldBe emptyMap()
    }

    @Test
    fun `unsafe remote HTTP aborts before any key copy import or removal`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.settings.scopes = fixture.settings.scopes.map { scope ->
            scope.copy(
                providers = scope.providers.map {
                    if (it.provider == LegacyProvider.GEMINI) it.copy(baseUrl = "http://remote.example/v1") else it
                },
            )
        }
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.NEEDS_ACTION
        fixture.vault.puts shouldBe 0
        fixture.profiles.imports shouldBe 0
        fixture.settings.keys.size shouldBe 1
    }

    @Test
    fun `repeat migration preserves user edited modern configuration and assignment`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.migration.migrate(100)
        val edited = fixture.profiles.catalog.profiles.first().copy(name = "Edited by user")
        fixture.profiles.catalog = fixture.profiles.catalog.copy(
            profiles = fixture.profiles.catalog.profiles.map { if (it.id == edited.id) edited else it },
            assignments = listOf(ProfileAssignment(ProfileAssignmentScope.GLOBAL, 0, edited.id)),
        )
        val before = fixture.profiles.catalog
        fixture.migration.migrate(101).status shouldBe LegacyMigrationStatus.COMPLETE
        fixture.profiles.catalog shouldBe before
        fixture.vault.puts shouldBe 1
    }

    @Test
    fun `snapshot report and proof diagnostics never contain raw secrets or causes`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        val snapshot = fixture.settings.readSnapshot()
        snapshot.toString().contains("fixture-GEMINI") shouldBe false
        snapshot.credentials.single().toString().contains("fixture-GEMINI") shouldBe false
        snapshot.close()
        fixture.profiles.failImport = true
        val result = fixture.migration.migrate(100)
        result.toString().contains("fixture-GEMINI") shouldBe false
        result.toString().contains("sensitive-error") shouldBe false
    }

    @Test
    fun `stable mapping IDs depend on provider scope and source ID not secret bytes`() = runBlocking<Unit> {
        val first = Fixture(oneKey = true)
        val second = Fixture(oneKey = true)
        second.settings.keys[Location(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0)] = "different-key"
        first.migration.migrate(100)
        second.migration.migrate(100)
        first.profiles.catalog.legacyReferences.single().credentialId shouldBe
            second.profiles.catalog.legacyReferences.single().credentialId
    }

    @Test
    fun `blank legacy fields are not removed by credential migration`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.settings.keys[Location(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0)] = "  "
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.COMPLETE
        fixture.settings.keys.values.toList() shouldBe listOf("  ")
        fixture.vault.puts shouldBe 0
    }

    @Test
    fun `private native flavor is retained without any password property`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.settings.scopes = fixture.settings.scopes.map { scope ->
            if (scope.scope == LegacyCredentialScope.GLOBAL) {
                scope.copy(
                    selectedProvider = LegacyProvider.GEMINI_PRIVATE,
                    providers = scope.providers + configuration(LegacyProvider.GEMINI_PRIVATE),
                )
            } else {
                scope
            }
        }
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.COMPLETE
        fixture.profiles.catalog.connections.any { it.presetFlavor == PresetFlavor.GEMINI_PRIVATE } shouldBe true
        fixture.profiles.catalog.profiles.any { it.model.exactModelId == "gemini-2.5-pro" } shouldBe true
    }

    @Test
    fun `source generation languages prompts and concurrency are captured without upgrades`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.settings.scopes = fixture.settings.scopes.map { scope ->
            if (scope.scope == LegacyCredentialScope.SOURCE) {
                scope.copy(
                    providers = scope.providers.map { config ->
                        if (config.provider == LegacyProvider.GEMINI) {
                            config.copy(
                                model = ModelSelection(
                                    "gemini-2.5-pro",
                                    ReasoningContract.Budget(0, 8192, canDisable = true),
                                    GenerationSettings(
                                        temperature = 0.25,
                                        maxOutputTokens = 8192,
                                        reasoning = ReasoningChoice.Budget(0),
                                    ),
                                    OutputTokenField.MAX_OUTPUT_TOKENS,
                                ),
                                languages = TranslationLanguages("English", "Japanese"),
                                prompt = PromptSettings("ADULT_18", "PROFESSIONAL", listOf("keep"), "source directive"),
                                maxConcurrency = 1,
                            )
                        } else {
                            config
                        }
                    },
                )
            } else {
                scope
            }
        }
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.COMPLETE
        val captured = fixture.profiles.catalog.profiles.single { it.languages.target == "Japanese" }
        captured.model.exactModelId shouldBe "gemini-2.5-pro"
        captured.model.generation.reasoning shouldBe ReasoningChoice.Budget(0)
        captured.model.generation.maxOutputTokens shouldBe 8192
        captured.prompt.customText shouldBe "source directive"
        fixture.profiles.catalog.connections.single { it.id == captured.connectionId }.maxConcurrency shouldBe 1
    }

    @Test
    fun `unlinked copied value conflict stays needs review without overwriting the vault slot`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.profiles.failImport = true
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.FAILED
        val id = fixture.vault.values.keys.single()
        fixture.settings.keys[Location(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0)] = "changed-value"
        fixture.profiles.failImport = false
        fixture.migration.migrate(101).status shouldBe LegacyMigrationStatus.NEEDS_REVIEW
        fixture.vault.values[id] shouldBe "fixture-GEMINI"
        fixture.profiles.catalog.legacyReferences shouldBe emptyList()
        fixture.settings.keys.values.toList() shouldBe listOf("changed-value")
    }

    @Test
    fun `atomic removal adapter failure leaves raw fields while linked copy can be reused`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.settings.failRemoval = true
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.FAILED
        fixture.settings.keys.size shouldBe 1
        fixture.profiles.catalog.legacyReferences.size shouldBe 1
        fixture.settings.failRemoval = false
        fixture.migration.migrate(101).status shouldBe LegacyMigrationStatus.COMPLETE
        fixture.vault.puts shouldBe 1
        fixture.settings.keys shouldBe emptyMap()
    }

    private data class Location(val provider: LegacyProvider, val scope: LegacyCredentialScope, val id: Long)

    @Test
    fun `migration cannot shadow an acknowledged newer vault edit`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        val captured = CompletableDeferred<Unit>()
        val releaseMigration = CompletableDeferred<Unit>()
        fixture.profiles.afterCatalogRead = {
            captured.complete(Unit)
            releaseMigration.await()
        }
        val prefs = mockk<NovelReaderPreferences>()
        every { prefs.compareAndRemoveLegacyCredentials(any()) } returns LegacyRemovalResult(0, 0)
        val edits = LegacyTranslationCredentials(prefs, fixture.profiles, fixture.vault)
        val migration = async { fixture.migration.migrate(100) }
        withTimeout(5_000) { captured.await() }
        val edit = async { edits.saveLegacy(NovelTranslationProvider.GEMINI, null, "newer-value", 200) }
        try {
            // Previously the edit completed in this stale-import window; now it waits for migration.
            withTimeoutOrNull(1_500) { edit.await() }
        } finally {
            releaseMigration.complete(Unit)
        }
        migration.await().status shouldBe LegacyMigrationStatus.COMPLETE
        edit.await()
        val ref =
            requireNotNull(fixture.profiles.getLegacyCredentialReference("GEMINI", LegacyCredentialScope.GLOBAL, 0))
        edits.readRequired(ref.credentialId).use { it.reveal() shouldBe "newer-value" }
        fixture.profiles.getLegacyCredentialReference("GEMINI_PRIVATE", LegacyCredentialScope.GLOBAL, 0)
            ?.credentialId shouldBe ref.credentialId
        fixture.settings.keys shouldBe emptyMap()
    }

    @Test
    fun `ordinary migration never refills an acknowledged missing vault from plaintext`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.COMPLETE
        fixture.vault.values.clear()
        fixture.settings.keys[Location(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0)] = "restored-value"
        fixture.migration.migrate(200).status shouldBe LegacyMigrationStatus.NEEDS_ACTION
        fixture.vault.puts shouldBe 1
        fixture.settings.keys.values.toList() shouldBe listOf("restored-value")
    }

    @Test
    fun `explicit restore verifies a missing referenced record before removing restored plaintext`() =
        runBlocking<Unit> {
            val fixture = Fixture(oneKey = true)
            fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.COMPLETE
            val before = fixture.profiles.catalog
            val id = before.legacyReferences.single().credentialId
            fixture.vault.values.clear()
            fixture.settings.keys[Location(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0)] = "restored-value"
            val result = fixture.migration.migrateRestoredPreferences(200)
            result.status shouldBe LegacyMigrationStatus.COMPLETE
            result.removed shouldBe 1
            fixture.settings.keys shouldBe emptyMap()
            fixture.vault.values[id] shouldBe "restored-value"
            fixture.profiles.catalog shouldBe before
        }

    @Test
    fun `explicit restore never overwrites a healthy conflicting vault record`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.COMPLETE
        val id = fixture.profiles.catalog.legacyReferences.single().credentialId
        fixture.settings.keys[Location(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0)] =
            "different-restored-value"
        fixture.migration.migrateRestoredPreferences(200).status shouldBe LegacyMigrationStatus.NEEDS_REVIEW
        fixture.vault.values[id] shouldBe "fixture-GEMINI"
        fixture.vault.puts shouldBe 1
        fixture.settings.keys.values.toList() shouldBe listOf("different-restored-value")
    }

    @Test
    fun `explicit restore never replaces an unavailable keystore record`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.COMPLETE
        fixture.vault.unavailable = true
        fixture.settings.keys[Location(LegacyProvider.GEMINI, LegacyCredentialScope.GLOBAL, 0)] = "restored-value"
        fixture.migration.migrateRestoredPreferences(200).status shouldBe LegacyMigrationStatus.NEEDS_ACTION
        fixture.vault.puts shouldBe 1
        fixture.settings.keys.values.toList() shouldBe listOf("restored-value")
    }

    @Test
    fun `keyless local configuration migrates once without resurrecting after deletion unless restored`() =
        runBlocking<Unit> {
            val fixture = Fixture(oneKey = true)
            fixture.settings.keys.clear()
            fixture.settings.scopes = listOf(
                LegacyScopeSettings(
                    LegacyCredentialScope.GLOBAL,
                    0,
                    LegacyProvider.OLLAMA_CLOUD,
                    providers().map {
                        if (it.provider == LegacyProvider.OLLAMA_CLOUD) {
                            it.copy(
                                baseUrl = "http://localhost:11434/api",
                                allowInsecureLocalHTTP = true,
                            )
                        } else {
                            it
                        }
                    },
                    enabled = true,
                ),
            )
            fixture.settings.hasConfiguredKeyless = true

            val first = fixture.migration.migrate(100)
            first.status shouldBe LegacyMigrationStatus.COMPLETE
            fixture.vault.puts shouldBe 0
            fixture.profiles.catalog.connections.any { it.allowInsecureLocalHTTP } shouldBe true
            fixture.profiles.catalog.assignments.size shouldBe 1
            fixture.settings.keylessCompleted shouldBe true

            // Deleting all profiles later must not resurrect deleted keyless config on normal migrate()
            fixture.profiles.catalog = ProfileCatalog()
            val afterDelete = fixture.migration.migrate(101)
            afterDelete.status shouldBe LegacyMigrationStatus.COMPLETE
            fixture.profiles.catalog shouldBe ProfileCatalog()

            // Explicit restore may re-import keyless config into an empty catalog
            val restored = fixture.migration.migrateRestoredPreferences(102)
            restored.status shouldBe LegacyMigrationStatus.COMPLETE
            fixture.profiles.catalog.connections.any { it.allowInsecureLocalHTTP } shouldBe true
        }

    @Test
    fun `broken inactive keyless provider does not abort migrating valid keyed provider`() = runBlocking<Unit> {
        val fixture = Fixture(oneKey = true)
        fixture.settings.scopes = fixture.settings.scopes.map { scope ->
            scope.copy(
                providers = scope.providers.map {
                    if (it.provider == LegacyProvider.OPENROUTER) {
                        it.copy(baseUrl = "http://remote.example/v1", issue = LegacyConfigurationIssue.INVALID_SETTING)
                    } else {
                        it
                    }
                },
            )
        }

        val result = fixture.migration.migrate(100)
        result.status shouldBe LegacyMigrationStatus.COMPLETE
        fixture.vault.puts shouldBe 1
        fixture.settings.keys shouldBe emptyMap()
        fixture.profiles.catalog.connections.none { it.presetFlavor == PresetFlavor.OPENROUTER } shouldBe true
        fixture.profiles.catalog.connections.any { it.presetFlavor == PresetFlavor.NONE } shouldBe true
    }

    @Test
    fun `saveLegacy appends binding onto existing connection with another credential and stores masked suffix`() =
        runBlocking<Unit> {
            val fixture = Fixture(oneKey = true)
            fixture.migration.migrate(100).status shouldBe LegacyMigrationStatus.COMPLETE
            val existingBinding = fixture.profiles.catalog.connections.first {
                it.presetFlavor == PresetFlavor.NONE
            }.credentialBindings.single()
            // Force a distinct existing binding so saveLegacy appends rather than replacing.
            val connection = fixture.profiles.catalog.connections.first { it.presetFlavor == PresetFlavor.NONE }
            val otherId = "00000000-0000-0000-0000-000000000077"
            fixture.profiles.catalog = fixture.profiles.catalog.copy(
                connections = fixture.profiles.catalog.connections.map {
                    if (it.id == connection.id) {
                        it.copy(credentialBindings = listOf(CredentialBinding(otherId)))
                    } else {
                        it
                    }
                },
            )
            var savedConnection: ConnectionProfile? = null
            val spyProfiles = object : TranslationProfileRepository by fixture.profiles {
                override suspend fun saveConnection(connection: ConnectionProfile) {
                    savedConnection = connection
                }
            }
            val prefs = mockk<NovelReaderPreferences>()
            every { prefs.compareAndRemoveLegacyCredentials(any()) } returns LegacyRemovalResult(0, 0)
            val creds = LegacyTranslationCredentials(prefs, spyProfiles, fixture.vault)

            creds.saveLegacy(NovelTranslationProvider.GEMINI, null, "sk-test-secret-9876", 200)

            savedConnection?.credentialBindings?.map { it.credentialId } shouldBe listOf(
                otherId,
                existingBinding.credentialId,
            )
            fixture.profiles.getCredentialMetadata(existingBinding.credentialId)?.maskedSuffix shouldBe "…9876"
        }

    private class Fixture(oneKey: Boolean = false) {
        val events = mutableListOf<String>()
        val settings = Settings(oneKey, events)
        val vault = Vault(events)
        val profiles = Profiles(events)
        val migration = LegacyTranslationMigration(settings, vault, profiles)
    }

    private class Settings(oneKey: Boolean, private val events: MutableList<String>) : LegacyTranslationSettings {
        val keys = linkedMapOf<Location, String>()
        var scopes = listOf(
            LegacyScopeSettings(LegacyCredentialScope.GLOBAL, 0, LegacyProvider.GEMINI, providers(), true),
            LegacyScopeSettings(LegacyCredentialScope.SOURCE, -42, LegacyProvider.DEEPSEEK, providers(), true),
        )
        val unknownSourceFields = mapOf("fontSize" to "21", "future" to "retained")
        var removeCalls = 0
        var failRemoval = false
        var beforeRemoval: (() -> Unit)? = null
        var hasConfiguredKeyless = false
        var keylessCompleted = false
        init {
            providers().filter { !oneKey || it.provider == LegacyProvider.GEMINI }.forEach {
                keys[Location(it.provider, LegacyCredentialScope.GLOBAL, 0)] = "fixture-${it.provider}"
            }
        }
        override suspend fun hasCompletedKeylessMigration() = keylessCompleted
        override suspend fun markKeylessMigrationComplete() {
            keylessCompleted = true
        }
        override suspend fun readSnapshot() = LegacyTranslationSnapshot(
            scopes,
            keys.map { (location, value) ->
                LegacyCredentialValue(location.provider, location.scope, location.id, CredentialSecret(value))
            },
            hasConfiguredKeylessSettings = hasConfiguredKeyless,
        )
        override suspend fun compareAndRemoveVerified(values: List<VerifiedLegacyCredential>): LegacyRemovalResult {
            removeCalls++
            if (failRemoval) error("sensitive-error")
            events += "remove-legacy"
            beforeRemoval?.invoke()
            var removed = 0
            var changed = 0
            values.forEach { proof ->
                val key = Location(proof.provider, proof.scope, proof.scopeId)
                val current = keys[key]
                if (current != null && proof.matches(current)) {
                    keys.remove(key)
                    removed++
                } else {
                    changed++
                }
                proof.toString().contains("fixture-") shouldBe false
            }
            return LegacyRemovalResult(removed, changed)
        }
    }

    private class Vault(private val events: MutableList<String>) : CredentialStore {
        val values = mutableMapOf<String, String>()
        var puts = 0
        var reads = 0
        var removals = 0
        var failWrite = false
        var mismatchAfterWrite = false
        var unavailableAfterWrite = false
        var unavailable = false
        override suspend fun put(id: String, secret: String) {
            if (failWrite) error("sensitive-error")
            puts++
            events += "put"
            values[id] = secret
        }
        override suspend fun read(id: String): CredentialRead {
            reads++
            if (unavailable || (unavailableAfterWrite && puts > 0)) return CredentialRead.Unavailable
            val value = values[id] ?: return CredentialRead.Missing
            return CredentialRead.Available(CredentialSecret(if (mismatchAfterWrite) "mismatch" else value))
        }
        override suspend fun remove(id: String) {
            removals++
            values.remove(id)
        }
    }

    private class Profiles(private val events: MutableList<String>) : TranslationProfileRepository {
        var catalog = ProfileCatalog()
        var imports = 0
        var failImport = false
        var hideImportedReferences = false
        private var routing = CredentialRoutingState()
        override fun observeCatalog(): Flow<ProfileCatalog> = MutableStateFlow(catalog)
        var afterCatalogRead: (suspend () -> Unit)? = null
        override suspend fun getCatalog(): ProfileCatalog {
            val captured = catalog
            afterCatalogRead?.invoke()
            return captured
        }
        override suspend fun getConnection(id: String) = catalog.connections.singleOrNull { it.id == id }
        override suspend fun getProfile(id: String) = catalog.profiles.singleOrNull { it.id == id }
        override suspend fun getCredentialMetadata(id: String) = catalog.credentials.singleOrNull { it.id == id }
        override suspend fun getLegacyCredentialReference(
            provider: String,
            scope: LegacyCredentialScope,
            scopeId: Long,
        ): LegacyCredentialReference? {
            if (imports > 0) {
                events += "linked-read"
                if (hideImportedReferences) return null
            }
            return catalog.legacyReferences.singleOrNull {
                it.provider == provider && it.scope == scope && it.scopeId == scopeId
            }
        }
        override suspend fun importConfiguration(bundle: ProfileCatalog) {
            if (failImport) error("sensitive-error")
            events += "import"
            fun <T, K> merged(old: List<T>, next: List<T>, key: (T) -> K): List<T> =
                old.associateBy(key).toMutableMap().apply { next.forEach { put(key(it), it) } }.values.toList()
            val next = ProfileCatalog(
                merged(catalog.connections, bundle.connections) { it.id },
                merged(catalog.profiles, bundle.profiles) { it.id },
                merged(catalog.credentials, bundle.credentials) { it.id },
                merged(catalog.assignments, bundle.assignments) { it.scope to it.scopeId },
                merged(catalog.legacyReferences, bundle.legacyReferences) {
                    Triple(it.provider, it.scope, it.scopeId)
                },
            )
            check(next.profiles.all { p -> next.connections.any { it.id == p.connectionId } })
            check(next.legacyReferences.all { r -> next.credentials.any { it.id == r.credentialId } })
            catalog = next
            imports++
        }
        override suspend fun setCredentialHealth(credentialId: String, health: CredentialHealth) {
            check(catalog.credentials.any { it.id == credentialId })
            routing = routing.copy(health = routing.health + (credentialId to health))
        }
        override suspend fun saveConnection(connection: ConnectionProfile) = error("Use atomic import")
        override suspend fun saveProfile(profile: TranslationProfile) = error("Use atomic import")
        override suspend fun saveCredentialMetadata(metadata: CredentialMetadata) {
            catalog = catalog.copy(credentials = catalog.credentials.filterNot { it.id == metadata.id } + metadata)
        }
        override suspend fun saveAssignment(assignment: ProfileAssignment) = error("Use atomic import")
        override suspend fun saveLegacyCredentialReference(reference: LegacyCredentialReference) {
            catalog = catalog.copy(
                legacyReferences = catalog.legacyReferences.filterNot {
                    it.provider == reference.provider && it.scope == reference.scope && it.scopeId == reference.scopeId
                } + reference,
            )
        }
        override suspend fun deleteCredentialMetadataIfUnused(id: String): Boolean = error("No migration deletion")
        override suspend fun resetServiceHealth(
            connectionSnapshot: ConnectionProfile,
            modelId: String,
            credentialId: String?,
            now: Long,
        ) = error("No migration probe")
        override suspend fun deleteProfile(id: String): Boolean = error("No migration deletion")
        override suspend fun deleteConnection(id: String): Boolean = error("No migration deletion")
        override suspend fun removeAssignment(scope: ProfileAssignmentScope, scopeId: Long): Boolean =
            error("No deletion")
        override suspend fun pruneOrphanNovelAssignments(): Int = 0
        override fun observeRoutingState(): Flow<CredentialRoutingState> = MutableStateFlow(routing)
        override suspend fun resolveProfile(
            explicitProfileId: String?,
            novelId: Long?,
            sourceId: Long?,
        ): TranslationProfile? = error("Not a migration operation")
        override suspend fun readRoutingState() = routing
        override suspend fun readLedger(runId: Long, generation: Long) = RequestBudgetLedger("fixture")
        override suspend fun reserveRequest(
            runId: Long,
            generation: Long,
            connectionSnapshot: ConnectionProfile,
            modelId: String,
            policy: ExecutionPolicy,
            requestId: String,
            fragmentIds: Set<String>,
            tokenEstimate: Long?,
            now: Long,
        ): ProfileRequestReservation = error("No HTTP admission")
        override suspend fun recordRoutingFailure(
            connectionSnapshot: ConnectionProfile,
            modelId: String,
            credentialId: String?,
            failure: RoutingFailure,
            notBeforeAt: Long,
            now: Long,
        ) = error("No routing operation")
        override suspend fun markCompletedFragments(
            runId: Long,
            generation: Long,
            fragmentIds: Set<String>,
        ): Boolean = error("No chunk operation")
    }

    private companion object {
        fun providers() = listOf(
            LegacyProvider.GEMINI,
            LegacyProvider.OPENROUTER,
            LegacyProvider.DEEPSEEK,
            LegacyProvider.MISTRAL,
            LegacyProvider.NVIDIA,
            LegacyProvider.OLLAMA_CLOUD,
        ).map(::configuration)
        fun configuration(provider: LegacyProvider): LegacyProviderConfiguration {
            val protocol = when (provider) {
                LegacyProvider.GEMINI, LegacyProvider.GEMINI_PRIVATE -> TranslationProtocol.GEMINI_GENERATE
                LegacyProvider.OLLAMA_CLOUD -> TranslationProtocol.OLLAMA_NATIVE
                else -> TranslationProtocol.OPENAI_CHAT
            }
            val auth = when (provider) {
                LegacyProvider.GEMINI -> ProfileAuth.X_GOOG_API_KEY
                LegacyProvider.GEMINI_PRIVATE -> ProfileAuth.NONE
                else -> ProfileAuth.BEARER
            }
            val flavor = when (provider) {
                LegacyProvider.GEMINI -> PresetFlavor.NONE
                LegacyProvider.GEMINI_PRIVATE -> PresetFlavor.GEMINI_PRIVATE
                LegacyProvider.OPENROUTER -> PresetFlavor.OPENROUTER
                LegacyProvider.DEEPSEEK -> PresetFlavor.DEEPSEEK
                LegacyProvider.MISTRAL -> PresetFlavor.MISTRAL
                LegacyProvider.NVIDIA -> PresetFlavor.NVIDIA
                LegacyProvider.OLLAMA_CLOUD -> PresetFlavor.OLLAMA
            }
            return LegacyProviderConfiguration(
                provider,
                "https://example.test/api",
                protocol,
                auth,
                flavor,
                ModelSelection(
                    if (protocol == TranslationProtocol.GEMINI_GENERATE) "gemini-2.5-pro" else "fixture-model",
                ),
                TranslationLanguages("English", "Russian"),
                PromptSettings(modifierIds = listOf("a", "b"), customText = "fixture directive"),
            )
        }
    }
}
