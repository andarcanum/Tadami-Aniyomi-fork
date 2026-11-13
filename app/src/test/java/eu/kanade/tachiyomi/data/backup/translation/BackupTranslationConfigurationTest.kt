package eu.kanade.tachiyomi.data.backup.translation

import eu.kanade.tachiyomi.data.backup.create.BackupPayloadEmitter
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupTranslationConfiguration
import eu.kanade.tachiyomi.data.backup.models.BackupTranslationNovelAssignment
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.profile.ProfileAssignment
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import java.io.ByteArrayOutputStream

class BackupTranslationConfigurationTest {
    @Test
    fun `nested public catalog survives backup protobuf and JSON round trips`() {
        val payload = BackupTranslationConfiguration.fromCatalog(translationCatalog())
        val backup = Backup(isLegacy = false, backupTranslationConfiguration = payload)
        val proto = ProtoBuf.decodeFromByteArray(
            Backup.serializer(),
            ProtoBuf.encodeToByteArray(Backup.serializer(), backup),
        )
        val json = Json.decodeFromString(Backup.serializer(), Json.encodeToString(Backup.serializer(), backup))
        proto.backupTranslationConfiguration!!.decode().toCatalog() shouldBe translationCatalog()
        json.backupTranslationConfiguration!!.decode().toCatalog() shouldBe translationCatalog()
    }

    @Test
    fun `native streamed consumer retains configuration and matches protobuf bytes`() {
        val backup =
            Backup(
                isLegacy = false,
                backupTranslationConfiguration = BackupTranslationConfiguration.fromCatalog(translationCatalog()),
            )
        val bytes = ByteArrayOutputStream().also { BackupPayloadEmitter.emitNative(backup, ProtoBuf, it) }.toByteArray()
        assertArrayEquals(ProtoBuf.encodeToByteArray(Backup.serializer(), backup), bytes)
        ProtoBuf.decodeFromByteArray(
            Backup.serializer(),
            bytes,
        ).backupTranslationConfiguration!!.decode().toCatalog() shouldBe
            translationCatalog()
    }

    @Test
    fun `old backups default to no configuration and old consumer ignores the new field`() {
        val oldBytes = ProtoBuf.encodeToByteArray(Backup.serializer(), Backup(isLegacy = false))
        ProtoBuf.decodeFromByteArray(Backup.serializer(), oldBytes).backupTranslationConfiguration shouldBe null
        LegacyBackup().toBackup().backupTranslationConfiguration shouldBe null
        val current =
            Backup(backupTranslationConfiguration = BackupTranslationConfiguration.fromCatalog(translationCatalog()))
        val legacy = ProtoBuf.decodeFromByteArray(
            LegacyBackup.serializer(),
            ProtoBuf.encodeToByteArray(Backup.serializer(), current),
        )
        legacy.toBackup().backupTranslationConfiguration shouldBe null
    }

    @Test
    fun `novel assignments encode only stable source URL references not old numeric IDs`() {
        val oldId = 987654321L
        val reference = BackupTranslationNovelAssignment(SOURCE, NOVEL_URL, PROFILE)
        val payload = BackupTranslationConfiguration.fromCatalog(translationCatalog(oldId), listOf(reference))
        payload.decode().novelAssignments shouldBe listOf(reference)
        payload.decode().assignments.none { it.scope == ProfileAssignmentScope.NOVEL } shouldBe true
        payload.configurationJson.contains(oldId.toString()) shouldBe false
    }

    @Test
    fun `configuration has no raw key vault health or reservation fields`() {
        val backup =
            Backup(backupTranslationConfiguration = BackupTranslationConfiguration.fromCatalog(translationCatalog()))
        val json = Json.encodeToString(Backup.serializer(), backup)
        val proto = ProtoBuf.encodeToByteArray(Backup.serializer(), backup).toString(Charsets.UTF_8)
        listOf(
            VAULT_VALUE,
            "ciphertext",
            "keystore",
            "health",
            "reservations",
            "reservedRequestIds",
            "rawKey",
            "apiKey",
        ).forEach { forbidden ->
            json.contains(forbidden) shouldBe false
            proto.contains(forbidden) shouldBe false
        }
    }

    @Test
    fun `unsupported version and unknown secret fields fail without payload or cause echo`() {
        val payload = BackupTranslationConfiguration.fromCatalog(translationCatalog())
        val unknown =
            JsonObject(
                Json.parseToJsonElement(
                    payload.configurationJson,
                ).jsonObject + ("rawKey" to JsonPrimitive(VAULT_VALUE)),
            )
        listOf(
            payload.copy(version = 2),
            payload.copy(configurationJson = unknown.toString()),
            payload.copy(configurationJson = VAULT_VALUE),
        ).forEach { invalid ->
            val failure = assertThrows(IllegalArgumentException::class.java) { invalid.decode() }
            failure.message!!.contains(VAULT_VALUE) shouldBe false
            failure.cause shouldBe null
            invalid.toString().contains(VAULT_VALUE) shouldBe false
        }
    }

    @Test
    fun `numeric novel assignments duplicate IDs and dangling catalog links are rejected`() {
        val payload = BackupTranslationConfiguration.fromCatalog(translationCatalog())
        val numeric = JsonObject(
            Json.parseToJsonElement(payload.configurationJson).jsonObject + (
                "assignments" to
                    Json.encodeToJsonElement(listOf(ProfileAssignment(ProfileAssignmentScope.NOVEL, 7, PROFILE)))
                ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            payload.copy(configurationJson = numeric.toString()).decode()
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupTranslationConfiguration.fromCatalog(
                translationCatalog().copy(
                    credentials = listOf(translationCatalog().credentials[0], translationCatalog().credentials[0]),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupTranslationConfiguration.fromCatalog(translationCatalog().copy(connections = emptyList()))
        }
    }

    @Test
    fun `oversized public catalog export is rejected with bounded per-entry preflight`() {
        val catalog = translationCatalog()
        val prompt = catalog.profiles.single().prompt.copy(customText = "é".repeat(20_000))
        val profiles = catalog.profiles + List(430) { index ->
            catalog.profiles.single().copy(
                id = "10000000-0000-0000-0000-" + index.toString().padStart(12, '0'),
                prompt = prompt,
            )
        }
        val failure = assertThrows(IllegalArgumentException::class.java) {
            BackupTranslationConfiguration.fromCatalog(catalog.copy(profiles = profiles))
        }
        failure.cause shouldBe null
        (failure.message!!.length < 100) shouldBe true
    }

    @Test
    fun `UTF8 configuration limit rejects oversized input without echo`() {
        val payload =
            BackupTranslationConfiguration(
                configurationJson = "é".repeat(BackupTranslationConfiguration.MAX_JSON_BYTES / 2 + 1),
            )
        val failure = assertThrows(IllegalArgumentException::class.java) { payload.decode() }
        failure.cause shouldBe null
        (failure.message!!.length < 100) shouldBe true
    }
}
