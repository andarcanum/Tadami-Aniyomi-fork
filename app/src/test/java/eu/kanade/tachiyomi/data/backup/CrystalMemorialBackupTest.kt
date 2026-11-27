package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupPayloadEmitter
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCrystalMemorial
import eu.kanade.tachiyomi.data.backup.models.BackupUserProfile
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRestoreException
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalSerializationApi::class)
class CrystalMemorialBackupTest {
    private val receipt =
        BackupCrystalMemorial(eligibilityReason = "RELEASE", eligibleAt = 1, grantedAt = 2, grantedXp = 999)

    @Test fun nativeStreamAndSerializerAgreeAndRetainReceipt() {
        val backup =
            Backup(
                isLegacy = false,
                backupCrystalMemorial = receipt,
                backupUserProfile = BackupUserProfile(totalXP = 1999),
            )
        val stream = ByteArrayOutputStream()
        BackupPayloadEmitter.emitNative(backup, ProtoBuf, stream)
        assertArrayEquals(ProtoBuf.encodeToByteArray(Backup.serializer(), backup), stream.toByteArray())
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), stream.toByteArray())
        assertEquals(receipt, decoded.backupCrystalMemorial)
        assertEquals(1999, decoded.backupUserProfile?.totalXP)
    }

    @Test fun legacyConversionRetainsOptionalReceiptAndOlderBackupStillLoads() {
        assertEquals(receipt, LegacyBackup(backupCrystalMemorial = receipt).toBackup().backupCrystalMemorial)
        val older = ProtoBuf.encodeToByteArray(Backup.serializer(), Backup())
        assertNull(ProtoBuf.decodeFromByteArray(Backup.serializer(), older).backupCrystalMemorial)
    }

    @Test fun sisterExportDoesNotLeakUnsupportedReceiptState() {
        val stream = ByteArrayOutputStream()
        BackupPayloadEmitter.emitSister(Backup(backupCrystalMemorial = receipt), ProtoBuf, stream)
        assertNull(ProtoBuf.decodeFromByteArray(Backup.serializer(), stream.toByteArray()).backupCrystalMemorial)
    }

    @Test fun ungrantedDebugOverrideIsNotExported() {
        assertNull(
            BackupCrystalMemorial.fromState(
                CrystalMemorialState(eligibilityReason = CrystalEligibilityReason.DEBUG, eligibleAt = 1),
            ),
        )
    }

    @Test fun invalidCurrencyAndCampaignCannotBecomeAReceipt() {
        assertThrows(CrystalMemorialRestoreException::class.java) { receipt.copy(grantedXp = 1000).toState() }
        assertThrows(CrystalMemorialRestoreException::class.java) { receipt.copy(campaignKey = "other").toState() }
    }
}
