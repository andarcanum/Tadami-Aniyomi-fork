package eu.kanade.tachiyomi.ui.player

import android.os.Build

internal data class MpvDemuxerCacheBudget(
    val maxForwardBytes: Long,
    val maxBackBytes: Long,
)

internal object MpvCachePolicy {
    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * 1024L * 1024L

    fun resolve(
        sdkInt: Int,
        totalMemoryBytes: Long,
        isLowRamDevice: Boolean,
    ): MpvDemuxerCacheBudget {
        if (sdkInt < Build.VERSION_CODES.O_MR1 || isLowRamDevice) {
            return MpvDemuxerCacheBudget(
                maxForwardBytes = 24L * MIB,
                maxBackBytes = 16L * MIB,
            )
        }

        return when {
            totalMemoryBytes in 1L until (4L * GIB) -> MpvDemuxerCacheBudget(
                maxForwardBytes = 32L * MIB,
                maxBackBytes = 16L * MIB,
            )
            totalMemoryBytes <= 0L || totalMemoryBytes < (6L * GIB) -> MpvDemuxerCacheBudget(
                maxForwardBytes = 48L * MIB,
                maxBackBytes = 32L * MIB,
            )
            else -> MpvDemuxerCacheBudget(
                maxForwardBytes = 64L * MIB,
                maxBackBytes = 48L * MIB,
            )
        }
    }
}
