package eu.kanade.presentation.util

import android.os.Build
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WindowBlurPolicyTest {

    @Test
    fun `blur disabled on android 11 and older even if cross window flag is true`() {
        val supported = resolveSupportsBlurBehind(
            sdkInt = Build.VERSION_CODES.R, // API 30
            isEInk = false,
            isCrossWindowBlurEnabled = true,
        )
        assertFalse(supported)
    }

    @Test
    fun `blur disabled on e-ink devices regardless of android version`() {
        val supported = resolveSupportsBlurBehind(
            sdkInt = Build.VERSION_CODES.S, // API 31
            isEInk = true,
            isCrossWindowBlurEnabled = true,
        )
        assertFalse(supported)
    }

    @Test
    fun `blur disabled on android 12+ if OEM or user disabled cross window blur`() {
        val supported = resolveSupportsBlurBehind(
            sdkInt = Build.VERSION_CODES.S, // API 31 (e.g. Redmi 10C with MIUI blur disabled)
            isEInk = false,
            isCrossWindowBlurEnabled = false,
        )
        assertFalse(supported)
    }

    @Test
    fun `blur enabled on android 12+ when cross window blur is active and not e-ink`() {
        val supported = resolveSupportsBlurBehind(
            sdkInt = Build.VERSION_CODES.S, // API 31
            isEInk = false,
            isCrossWindowBlurEnabled = true,
        )
        assertTrue(supported)

        val supportedT = resolveSupportsBlurBehind(
            sdkInt = Build.VERSION_CODES.TIRAMISU, // API 33
            isEInk = false,
            isCrossWindowBlurEnabled = true,
        )
        assertTrue(supportedT)
    }
}
