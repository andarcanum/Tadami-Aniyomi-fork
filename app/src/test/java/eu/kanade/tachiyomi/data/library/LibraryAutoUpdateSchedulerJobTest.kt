package eu.kanade.tachiyomi.data.library

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI

class LibraryAutoUpdateSchedulerJobTest {

    @Test
    fun `force wifi and charging enables both requirements`() {
        val policy = resolveLibraryAutoUpdateConstraintPolicy(
            restrictions = emptySet(),
            forceWifiAndCharging = true,
        )

        assertTrue(policy.requireWifi)
        assertTrue(policy.requireCharging)
        assertFalse(policy.requireNotMetered)
    }

    @Test
    fun `restrictions are respected when force mode is disabled`() {
        val policy = resolveLibraryAutoUpdateConstraintPolicy(
            restrictions = setOf(DEVICE_ONLY_ON_WIFI, DEVICE_NETWORK_NOT_METERED),
            forceWifiAndCharging = false,
        )

        assertTrue(policy.requireWifi)
        assertTrue(policy.requireNotMetered)
        assertFalse(policy.requireCharging)
    }

    @Test
    fun `charging restriction remains enabled from explicit restriction`() {
        val policy = resolveLibraryAutoUpdateConstraintPolicy(
            restrictions = setOf(DEVICE_CHARGING),
            forceWifiAndCharging = false,
        )

        assertFalse(policy.requireWifi)
        assertFalse(policy.requireNotMetered)
        assertTrue(policy.requireCharging)
    }

    @Test
    fun `legacy not metered restriction allows work on wifi`() {
        val shouldRetry = shouldRetryLegacyAutoUpdateRun(
            restrictions = setOf(DEVICE_NETWORK_NOT_METERED),
            isConnectedToWifi = true,
            isCharging = true,
        )

        assertFalse(shouldRetry)
    }

    @Test
    fun `legacy not metered restriction retries when wifi is unavailable`() {
        val shouldRetry = shouldRetryLegacyAutoUpdateRun(
            restrictions = setOf(DEVICE_NETWORK_NOT_METERED),
            isConnectedToWifi = false,
            isCharging = true,
        )

        assertTrue(shouldRetry)
    }
}
