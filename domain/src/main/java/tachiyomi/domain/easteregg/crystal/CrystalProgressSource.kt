package tachiyomi.domain.easteregg.crystal

import kotlinx.coroutines.flow.Flow

/** Current persisted read/seen progress; invalidations request a fresh snapshot, not event deltas. */
interface CrystalProgressSource {
    fun changes(): Flow<Unit>

    suspend fun snapshot(): CrystalProgressResult
}
