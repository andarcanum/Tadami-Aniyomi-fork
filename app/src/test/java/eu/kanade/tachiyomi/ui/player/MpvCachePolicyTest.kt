package eu.kanade.tachiyomi.ui.player

import android.os.Build
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MpvCachePolicyTest {

    private val mib = 1024L * 1024L
    private val gib = 1024L * 1024L * 1024L

    @Test
    fun `low ram device receives conservative cache budget`() {
        MpvCachePolicy.resolve(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            totalMemoryBytes = 8L * gib,
            isLowRamDevice = true,
        ) shouldBe MpvDemuxerCacheBudget(
            maxForwardBytes = 24L * mib,
            maxBackBytes = 16L * mib,
        )
    }

    @Test
    fun `older android versions receive conservative cache budget`() {
        MpvCachePolicy.resolve(
            sdkInt = Build.VERSION_CODES.O,
            totalMemoryBytes = 6L * gib,
            isLowRamDevice = false,
        ) shouldBe MpvDemuxerCacheBudget(
            maxForwardBytes = 24L * mib,
            maxBackBytes = 16L * mib,
        )
    }

    @Test
    fun `sub 4gb device receives reduced cache budget`() {
        MpvCachePolicy.resolve(
            sdkInt = Build.VERSION_CODES.R,
            totalMemoryBytes = 3L * gib,
            isLowRamDevice = false,
        ) shouldBe MpvDemuxerCacheBudget(
            maxForwardBytes = 32L * mib,
            maxBackBytes = 16L * mib,
        )
    }

    @Test
    fun `mid range 4gb device receives balanced cache budget`() {
        MpvCachePolicy.resolve(
            sdkInt = Build.VERSION_CODES.S,
            totalMemoryBytes = 4L * gib,
            isLowRamDevice = false,
        ) shouldBe MpvDemuxerCacheBudget(
            maxForwardBytes = 48L * mib,
            maxBackBytes = 32L * mib,
        )
    }

    @Test
    fun `high memory device receives full cache budget`() {
        MpvCachePolicy.resolve(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            totalMemoryBytes = 8L * gib,
            isLowRamDevice = false,
        ) shouldBe MpvDemuxerCacheBudget(
            maxForwardBytes = 64L * mib,
            maxBackBytes = 48L * mib,
        )
    }
}
