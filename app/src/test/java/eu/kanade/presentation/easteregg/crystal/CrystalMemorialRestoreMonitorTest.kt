package eu.kanade.presentation.easteregg.crystal

import eu.kanade.domain.easteregg.crystal.CrystalRestoreMonitor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrystalMemorialRestoreMonitorTest {
    @Test fun overlappingRestoresOnlyBecomeSafeAfterBothFinish() {
        val monitor = CrystalRestoreMonitor()
        monitor.begin()
        monitor.begin()
        monitor.end()
        assertTrue(monitor.state.value.inProgress)
        assertEquals(1L, monitor.state.value.completedRestores)
        monitor.end()
        assertFalse(monitor.state.value.inProgress)
        assertEquals(2L, monitor.state.value.completedRestores)
    }

    @Test fun failureAndCancellationFinallyStillAllowBackfill() {
        val monitor = CrystalRestoreMonitor()
        monitor.begin()
        try {
            throw IllegalStateException("restore failed")
        } catch (_: IllegalStateException) {
            assertTrue(monitor.state.value.inProgress)
        } finally {
            monitor.end()
        }
        assertFalse(monitor.state.value.inProgress)
        assertEquals(1L, monitor.state.value.completedRestores)
        assertThrows(IllegalStateException::class.java) { monitor.end() }
    }
}
