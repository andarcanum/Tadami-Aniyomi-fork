package eu.kanade.domain.source.interactor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ForegroundIncognitoState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /**
     * Owner token for app-level writes (MainActivity) that aggregate the global incognito flag
     * with the per-media foreground flags.
     */
    val AppOwner: Any = Any()

    // A-M7: ownership-tagged writes. Reader/player transitions resume the NEW screen before the
    // old ViewModel's onCleared runs; the old unconditional set(false) silently dropped
    // FLAG_SECURE and IME incognito in the new reader while source-level incognito was on. Only
    // the owner that last raised the state may clear it; raising transfers ownership.
    private var owner: Any? = null

    @Synchronized
    fun set(owner: Any?, active: Boolean) {
        if (active) {
            this.owner = owner
            _active.value = true
        } else if (this.owner === owner) {
            this.owner = null
            _active.value = false
        }
    }
}
