package eu.kanade.presentation.more.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateIntervalCustomizationTest {

    @Test
    fun initialUsesCurrentWhenPlainHours() {
        assertEquals(6, initialCustomUpdateInterval(6))
        assertEquals(1, initialCustomUpdateInterval(1))
        assertEquals(24, initialCustomUpdateInterval(24))
    }

    @Test
    fun initialFallsBackToDefaultForSentinelsAndLongPresets() {
        assertEquals(12, initialCustomUpdateInterval(-2)) // use general
        assertEquals(12, initialCustomUpdateInterval(-1)) // on app start
        assertEquals(12, initialCustomUpdateInterval(0)) // never
        assertEquals(12, initialCustomUpdateInterval(48))
        assertEquals(12, initialCustomUpdateInterval(168))
    }

    @Test
    fun subtitleFormatsPositiveHours() {
        assertEquals("Every 7 hours", customUpdateIntervalSubtitle(7, "Every %1\$d hours"))
        assertEquals("Every 1 hours", customUpdateIntervalSubtitle(1, "Every %1\$d hours"))
        assertEquals("Каждые 9 ч", customUpdateIntervalSubtitle(9, "Каждые %1\$d ч"))
    }

    @Test
    fun subtitleIsNullForNonPositive() {
        assertNull(customUpdateIntervalSubtitle(0, "Every %1\$d hours"))
        assertNull(customUpdateIntervalSubtitle(-3, "Every %1\$d hours"))
    }

    @Test
    fun batteryWarningShowsStrictlyBelowSix() {
        assertEquals(true, showBatteryWarning(1))
        assertEquals(true, showBatteryWarning(5))
        assertEquals(false, showBatteryWarning(6))
        assertEquals(false, showBatteryWarning(24))
    }
}
