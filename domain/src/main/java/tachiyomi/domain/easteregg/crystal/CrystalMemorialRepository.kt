package tachiyomi.domain.easteregg.crystal

import kotlinx.coroutines.flow.Flow

interface CrystalMemorialRepository {
    fun observeState(): Flow<CrystalMemorialState>
    suspend fun getState(): CrystalMemorialState
    suspend fun makeEligible(reason: CrystalEligibilityReason): CrystalMemorialState
    suspend fun acknowledgeAutomaticPresentation()
    suspend fun completeMemorial(expectedGeneration: Long): CrystalCompletionResult
    suspend fun acknowledgeCeremony()

    /** Caller must enforce debug-build access. Consumes only this gift's receipt, once. */
    suspend fun debugReset(): CrystalMemorialState
    suspend fun snapshotForBackup(): CrystalMemorialSnapshot
    suspend fun restoreSnapshot(snapshot: CrystalMemorialSnapshot)
    suspend fun repairProjections()
}
