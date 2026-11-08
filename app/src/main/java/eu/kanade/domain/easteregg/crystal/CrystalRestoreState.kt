package eu.kanade.domain.easteregg.crystal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CrystalRestoreSnapshot(val activeRestores: Int = 0, val completedRestores: Long = 0) {
    val inProgress: Boolean get() = activeRestores > 0
}

/** Covers direct restore as well as WorkManager, with no dependency on emitted reading events. */
internal class CrystalRestoreMonitor {
    private val current = MutableStateFlow(CrystalRestoreSnapshot())
    val state: StateFlow<CrystalRestoreSnapshot> = current.asStateFlow()

    @Synchronized
    fun begin() {
        current.value = current.value.copy(activeRestores = current.value.activeRestores + 1)
    }

    @Synchronized
    fun end() {
        val before = current.value
        check(before.activeRestores > 0) { "Unbalanced memorial restore boundary" }
        current.value = CrystalRestoreSnapshot(before.activeRestores - 1, before.completedRestores + 1)
    }
}

internal object CrystalRestoreState {
    val monitor = CrystalRestoreMonitor()
}
