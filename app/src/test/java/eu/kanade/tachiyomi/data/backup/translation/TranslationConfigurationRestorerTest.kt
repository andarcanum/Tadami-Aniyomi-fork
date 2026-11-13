package eu.kanade.tachiyomi.data.backup.translation

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.BackupPayloadEmitter
import eu.kanade.tachiyomi.data.backup.create.creators.TranslationConfigurationBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupTranslationConfiguration
import eu.kanade.tachiyomi.data.backup.models.BackupTranslationNovelAssignment
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.TranslationConfigurationRestorer
import eu.kanade.tachiyomi.data.translation.profile.LegacyMigrationReport
import eu.kanade.tachiyomi.data.translation.profile.LegacyMigrationStatus
import eu.kanade.tachiyomi.data.translation.profile.TranslationConfigurationMutationGate
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.LegacyCredentialReference
import tachiyomi.domain.translation.profile.LegacyCredentialScope
import tachiyomi.domain.translation.profile.ProfileAssignment
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import java.io.ByteArrayOutputStream

class TranslationConfigurationRestorerTest {
    @Test
    fun `streamed consumer remaps novel identity merges unrelated catalog and preserves local keys and health`() =
        runBlocking<Unit> {
            TranslationBackupFixture().use { source ->
                source.insertNovel(7)
                source.profiles.importConfiguration(translationCatalog(7))
                val payload = TranslationConfigurationBackupCreator(source.profiles, source.handler)(BackupOptions())!!
                val wire = ByteArrayOutputStream().also {
                    BackupPayloadEmitter.emitNative(
                        Backup(isLegacy = false, backupTranslationConfiguration = payload),
                        ProtoBuf,
                        it,
                    )
                }.toByteArray()
                val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), wire).backupTranslationConfiguration
                TranslationBackupFixture().use { target ->
                    target.insertNovel(7, source = 31, url = "/different-novel")
                    target.insertNovel(70)
                    val localConnection = translationCatalog().connections.single().copy(
                        id = LOCAL_CONNECTION,
                        credentialBindings = listOf(CredentialBinding(LOCAL_KEY)),
                    )
                    val localProfile = translationCatalog().profiles.single().copy(
                        id = LOCAL_PROFILE,
                        connectionId = LOCAL_CONNECTION,
                    )
                    val local = ProfileCatalog(
                        connections = listOf(localConnection),
                        profiles = listOf(localProfile),
                        credentials = listOf(
                            translationCatalog().credentials[0],
                            CredentialMetadata(LOCAL_KEY, "Local alias", "9012", 1),
                        ),
                        assignments = listOf(ProfileAssignment(ProfileAssignmentScope.SOURCE, 777, LOCAL_PROFILE)),
                        legacyReferences = listOf(
                            LegacyCredentialReference("GEMINI", LegacyCredentialScope.SOURCE, 777, LOCAL_KEY),
                        ),
                    )
                    target.profiles.importConfiguration(local)
                    val healthy = CredentialHealth(CredentialHealthStatus.READY, 4000)
                    target.profiles.setCredentialHealth(KEY_A, healthy)
                    target.profiles.setCredentialHealth(LOCAL_KEY, healthy)
                    val vault = RecordingTranslationVault().apply {
                        values[KEY_A] = VAULT_VALUE
                        values[LOCAL_KEY] =
                            VAULT_VALUE
                    }
                    val result = TranslationConfigurationRestorer(
                        target.profiles,
                        target.novels,
                        vault::read,
                        ::completeMigration,
                    )
                        .restore(decoded, RestoreOptions())!!
                    result.configurationFailed shouldBe false
                    result.unresolvedNovelAssignments shouldBe 0
                    result.missingCredentials shouldBe 1
                    val catalog = target.profiles.getCatalog()
                    catalog.assignments.single { it.scope == ProfileAssignmentScope.NOVEL }.scopeId shouldBe 70L
                    catalog.assignments.none { it.scope == ProfileAssignmentScope.NOVEL && it.scopeId == 7L } shouldBe
                        true
                    catalog.connections.contains(localConnection) shouldBe true
                    catalog.profiles.contains(localProfile) shouldBe true
                    catalog.assignments.contains(local.assignments.single()) shouldBe true
                    catalog.legacyReferences.contains(local.legacyReferences.single()) shouldBe true
                    target.profiles.readRoutingState().health[KEY_A] shouldBe healthy
                    target.profiles.readRoutingState().health[LOCAL_KEY] shouldBe healthy
                    target.profiles.readRoutingState().health[KEY_B]?.status shouldBe
                        CredentialHealthStatus.NEEDS_ACTION
                    vault.values.keys shouldBe setOf(KEY_A, LOCAL_KEY)
                    vault.reads shouldBe 2
                    vault.puts shouldBe 0
                    vault.removals shouldBe 0
                    vault.released.forEach { assertThrows(IllegalStateException::class.java) { it.reveal() } }
                }
            }
        }

    @Test
    fun `missing stable novel is skipped without numeric fallback while global source scope survives library off`() =
        runBlocking<Unit> {
            TranslationBackupFixture().use { fixture ->
                fixture.insertNovel(7, source = 31, url = "/different-novel")
                val payload = BackupTranslationConfiguration.fromCatalog(
                    translationCatalog(7),
                    listOf(BackupTranslationNovelAssignment(SOURCE, NOVEL_URL, PROFILE)),
                )
                val vault = RecordingTranslationVault()
                val result = TranslationConfigurationRestorer(
                    fixture.profiles,
                    fixture.novels,
                    vault::read,
                    ::completeMigration,
                )
                    .restore(
                        payload,
                        RestoreOptions(libraryEntries = false, restoreNovel = false, sourceSettings = false),
                    )!!
                result.unresolvedNovelAssignments shouldBe 1
                fixture.profiles.getCatalog().assignments shouldBe translationCatalog().assignments
                fixture.profiles.getCatalog().legacyReferences shouldBe translationCatalog().legacyReferences
                result.missingCredentials shouldBe 2
            }
        }

    @Test
    fun `app settings off performs no import vault read or restored plaintext migration`() = runBlocking<Unit> {
        val profiles = mockk<TranslationProfileRepository>()
        val novels = mockk<NovelRepository>()
        var reads = 0
        var migrations = 0
        val restorer = TranslationConfigurationRestorer(
            profiles,
            novels,
            readCredential = {
                reads++
                CredentialRead.Missing
            },
            migrateRestored = {
                migrations++
                completeMigration()
            },
        )
        restorer.restore(BackupTranslationConfiguration(2, VAULT_VALUE), RestoreOptions(appSettings = false)) shouldBe
            null
        TranslationConfigurationRestorer().restore(null, RestoreOptions(appSettings = false)) shouldBe null
        reads shouldBe 0
        migrations shouldBe 0
        coVerify(exactly = 0) { profiles.importConfiguration(any()) }
        coVerify(exactly = 0) { novels.getNovelByUrlAndSourceId(any(), any()) }
    }

    @Test
    fun `old backup without public envelope still invokes approved restored migration`() = runBlocking<Unit> {
        var migrations = 0
        val result = TranslationConfigurationRestorer(
            mockk(),
            mockk(),
            readCredential = { error("No public references to read") },
            migrateRestored = {
                migrations++
                LegacyMigrationReport(LegacyMigrationStatus.NEEDS_REVIEW)
            },
        ).restore(null, RestoreOptions())!!
        migrations shouldBe 1
        result.legacyMigration!!.status shouldBe LegacyMigrationStatus.NEEDS_REVIEW
        result.configurationFailed shouldBe false
    }

    @Test
    fun `migration runs after committed import without reentrant gate and availability is checked afterwards`() =
        runBlocking<Unit> {
            TranslationBackupFixture().use { fixture ->
                val vault = RecordingTranslationVault()
                val restorer = TranslationConfigurationRestorer(fixture.profiles, fixture.novels, vault::read) {
                    val acquired = TranslationConfigurationMutationGate.mutex.tryLock()
                    acquired shouldBe true
                    if (acquired) TranslationConfigurationMutationGate.mutex.unlock()
                    fixture.profiles.getConnection(CONNECTION)!!.id shouldBe CONNECTION
                    vault.values[KEY_A] = VAULT_VALUE
                    vault.values[KEY_B] = VAULT_VALUE
                    fixture.profiles.setCredentialHealth(KEY_B, CredentialHealth(CredentialHealthStatus.READY))
                    completeMigration()
                }
                val result = restorer.restore(
                    BackupTranslationConfiguration.fromCatalog(translationCatalog()),
                    RestoreOptions(),
                )!!
                result.configurationFailed shouldBe false
                result.missingCredentials shouldBe 0
                fixture.profiles.readRoutingState().health[KEY_B]?.status shouldBe CredentialHealthStatus.READY
                vault.puts shouldBe 0
                vault.removals shouldBe 0
            }
        }

    @Test
    fun `tombstoned credential aborts entire catalog import without erasing unrelated valid local key`() =
        runBlocking<Unit> {
            TranslationBackupFixture().use { fixture ->
                fixture.profiles.saveCredentialMetadata(translationCatalog().credentials[1])
                fixture.profiles.deleteCredentialMetadataIfUnused(KEY_B) shouldBe true
                fixture.profiles.saveCredentialMetadata(CredentialMetadata(LOCAL_KEY, "Local", "9012", 1))
                val before = fixture.profiles.getCatalog()
                val vault = RecordingTranslationVault().apply { values[LOCAL_KEY] = VAULT_VALUE }
                val result = TranslationConfigurationRestorer(
                    fixture.profiles,
                    fixture.novels,
                    vault::read,
                    ::completeMigration,
                )
                    .restore(BackupTranslationConfiguration.fromCatalog(translationCatalog()), RestoreOptions())!!
                result.configurationFailed shouldBe true
                fixture.profiles.getCatalog() shouldBe before
                fixture.profiles.getCredentialMetadata(KEY_B) shouldBe null
                vault.values[LOCAL_KEY] shouldBe VAULT_VALUE
                vault.reads shouldBe 0
                vault.puts shouldBe 0
                vault.removals shouldBe 0
            }
        }

    @Test
    fun `corrupt and unavailable vault states are explicit needs action without replacement writes`() =
        runBlocking<Unit> {
            TranslationBackupFixture().use { fixture ->
                val vault = RecordingTranslationVault().apply {
                    states[KEY_A] = CredentialRead.Unavailable
                    states[KEY_B] = CredentialRead.Corrupt
                }
                val result = TranslationConfigurationRestorer(
                    fixture.profiles,
                    fixture.novels,
                    vault::read,
                    ::completeMigration,
                )
                    .restore(BackupTranslationConfiguration.fromCatalog(translationCatalog()), RestoreOptions())!!
                result.unavailableCredentials shouldBe 1
                result.corruptCredentials shouldBe 1
                result.missingCredentials shouldBe 0
                fixture.profiles.readRoutingState().health.values.all {
                    it.status == CredentialHealthStatus.NEEDS_ACTION
                } shouldBe
                    true
                vault.puts shouldBe 0
                vault.removals shouldBe 0
            }
        }

    @Test
    fun `restored missing corrupt and unavailable slots retain prior future due while marking needs action`() =
        runBlocking<Unit> {
            val futureDue = 1_900_000_000_000L
            for (state in listOf(CredentialRead.Missing, CredentialRead.Corrupt, CredentialRead.Unavailable)) {
                TranslationBackupFixture().use { fixture ->
                    fixture.profiles.saveCredentialMetadata(translationCatalog().credentials[0])
                    fixture.profiles.setCredentialHealth(
                        KEY_A,
                        CredentialHealth(CredentialHealthStatus.READY, futureDue),
                    )
                    val vault = RecordingTranslationVault().apply { states[KEY_A] = state }
                    val result = TranslationConfigurationRestorer(
                        fixture.profiles,
                        fixture.novels,
                        vault::read,
                        ::completeMigration,
                    )
                        .restore(BackupTranslationConfiguration.fromCatalog(translationCatalog()), RestoreOptions())!!
                    result.configurationFailed shouldBe false
                    fixture.profiles.readRoutingState().health[KEY_A] shouldBe
                        CredentialHealth(CredentialHealthStatus.NEEDS_ACTION, futureDue)
                    fixture.profiles.readRoutingState().health[KEY_B]?.notBeforeAt shouldBe 0L
                    vault.puts shouldBe 0
                    vault.removals shouldBe 0
                }
            }
        }

    @Test
    fun `migration failures retain safe typed report without private exception echo`() = runBlocking<Unit> {
        val privateMessage = "private fixture failure"
        val result = TranslationConfigurationRestorer(
            mockk(),
            mockk(),
            readCredential = { error("No public references to read") },
            migrateRestored = { error(privateMessage) },
        ).restore(null, RestoreOptions())!!
        result.legacyMigration!!.status shouldBe LegacyMigrationStatus.FAILED
        result.toString().contains(privateMessage) shouldBe false
    }

    @Test
    fun `restore cancellation propagates instead of turning into an import or migration error`() = runBlocking<Unit> {
        val restorer = TranslationConfigurationRestorer(
            mockk(),
            mockk(),
            readCredential = {
                error("No public references to read")
            },
            migrateRestored = { throw CancellationException("cancel") },
        )
        var cancelled = false
        try {
            restorer.restore(null, RestoreOptions())
        } catch (_: CancellationException) {
            cancelled = true
        }
        cancelled shouldBe true
    }

    private companion object {
        const val LOCAL_CONNECTION = "00000000-0000-0000-0000-000000000091"
        const val LOCAL_PROFILE = "00000000-0000-0000-0000-000000000092"
        suspend fun completeMigration() = LegacyMigrationReport(LegacyMigrationStatus.COMPLETE)
    }
}
