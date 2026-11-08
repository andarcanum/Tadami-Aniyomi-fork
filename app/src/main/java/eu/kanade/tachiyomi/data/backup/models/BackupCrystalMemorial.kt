package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRestoreException
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState

/** Optional native-backup field. Debug control inputs and live session generations are not exported. */
@Serializable
data class BackupCrystalMemorial(
    @ProtoNumber(1) val campaignKey: String = CrystalMemorialIds.GRANT_KEY,
    @ProtoNumber(2) val eligibilityReason: String? = null,
    @ProtoNumber(3) val eligibleAt: Long? = null,
    @ProtoNumber(4) val automaticPresentationConsumedAt: Long? = null,
    @ProtoNumber(5) val grantedAt: Long? = null,
    @ProtoNumber(6) val grantedXp: Int = 0,
    @ProtoNumber(7) val ceremonyAcknowledgedAt: Long? = null,
    @ProtoNumber(8) val formatVersion: Int = 1,
) {
    fun toState(): CrystalMemorialState {
        if (campaignKey != CrystalMemorialIds.GRANT_KEY ||
            formatVersion != 1
        ) {
            throw CrystalMemorialRestoreException("Unsupported memorial backup record")
        }
        val reason = eligibilityReason?.let { value ->
            CrystalEligibilityReason.entries.firstOrNull { it.name == value }
                ?: throw CrystalMemorialRestoreException("Unknown memorial eligibility reason")
        }
        if ((grantedAt == null && grantedXp != 0) || (grantedAt != null && grantedXp != CrystalMemorialIds.XP) ||
            (ceremonyAcknowledgedAt != null && grantedAt == null)
        ) {
            throw CrystalMemorialRestoreException("Inconsistent memorial receipt")
        }
        return CrystalMemorialState(
            reason,
            eligibleAt,
            automaticPresentationConsumedAt,
            grantedAt,
            grantedXp,
            ceremonyAcknowledgedAt,
        )
    }

    companion object {
        fun fromState(state: CrystalMemorialState?): BackupCrystalMemorial? {
            if (state == null ||
                (state.eligibilityReason == CrystalEligibilityReason.DEBUG && !state.isGranted)
            ) {
                return null
            }
            return BackupCrystalMemorial(
                eligibilityReason = state.eligibilityReason?.name,
                eligibleAt = state.eligibleAt,
                automaticPresentationConsumedAt = state.automaticPresentationConsumedAt,
                grantedAt = state.grantedAt,
                grantedXp = state.grantedXp,
                ceremonyAcknowledgedAt = state.ceremonyAcknowledgedAt,
            )
        }
    }
}
