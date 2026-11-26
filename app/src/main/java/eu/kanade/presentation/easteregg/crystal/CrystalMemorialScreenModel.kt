package eu.kanade.presentation.easteregg.crystal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.easteregg.crystal.CrystalMemorialManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.easteregg.crystal.CrystalCompletionResult
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState

internal class CrystalMemorialScreenModel(private val manager: CrystalMemorialManager, val replay: Boolean) :
    StateScreenModel<CrystalMemorialScreenModel.State>(initialState(manager.latestState, replay)) {
    data class State(
        val loaded: Boolean = false,
        val allowed: Boolean = false,
        val loadFailed: Boolean = false,
        val granted: Boolean = false,
        val saving: Boolean = false,
        val grantFailed: Boolean = false,
        val firstGrant: Boolean = false,
        val generation: Long = 0L,
        val stale: Boolean = false,
        val resumeCeremony: Boolean = false,
    )
    private var acknowledging = false
    init {
        reload()
    }

    fun reload() {
        mutableState.update {
            if (it.loaded && !it.loadFailed) it else it.copy(loaded = false, loadFailed = false)
        }
        screenModelScope.launch {
            try {
                val saved = manager.repository.getState()
                mutableState.value = stateFrom(saved, replay)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(loaded = true, loadFailed = true) }
            }
        }
    }

    fun onVisible() {
        if (replay || !state.value.allowed) return
        screenModelScope.launch {
            try {
                manager.repository.acknowledgeAutomaticPresentation()
            } catch (
                e: CancellationException,
            ) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "Crystal presentation marker could not be saved: " + e.javaClass.simpleName }
            }
        }
    }

    fun complete() {
        val current = state.value
        if (!current.loaded || !current.allowed || current.saving || current.granted || current.stale) return
        mutableState.update { it.copy(saving = true, grantFailed = false) }
        screenModelScope.launch {
            try {
                when (manager.completeSession(current.generation, replay)) {
                    is CrystalCompletionResult.Granted -> mutableState.update {
                        it.copy(granted = true, saving = false, firstGrant = true)
                    }
                    is CrystalCompletionResult.AlreadyGranted -> mutableState.update {
                        it.copy(granted = true, saving = false, firstGrant = false)
                    }
                    CrystalCompletionResult.NotEligible, CrystalCompletionResult.StaleSession -> mutableState.update {
                        it.copy(stale = true, saving = false)
                    }
                    CrystalCompletionResult.RetryableFailure -> mutableState.update {
                        it.copy(grantFailed = true, saving = false)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(grantFailed = true, saving = false) }
            }
        }
    }

    fun acknowledgeCeremony(onDone: () -> Unit) {
        if (acknowledging) return
        acknowledging = true
        screenModelScope.launch {
            try {
                if (!replay) {
                    try {
                        manager.repository.acknowledgeCeremony()
                    } catch (
                        e: CancellationException,
                    ) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN) {
                            "Crystal ceremony marker could not be saved: " +
                                e.javaClass.simpleName
                        }
                    }
                }
                mutableState.update { it.copy(firstGrant = false, resumeCeremony = false) }
                onDone()
            } finally {
                acknowledging = false
            }
        }
    }

    companion object {
        internal fun stateFrom(saved: CrystalMemorialState, replay: Boolean): State = State(
            loaded = true,
            allowed = if (replay) saved.isGranted else saved.isEligible || saved.isGranted,
            granted = saved.isGranted,
            generation = saved.generation,
            firstGrant = !replay && saved.hasPendingCeremony,
            resumeCeremony = !replay && saved.hasPendingCeremony,
        )

        internal fun initialState(saved: CrystalMemorialState, replay: Boolean): State {
            val candidate = stateFrom(saved, replay)
            return if (candidate.allowed) candidate else State()
        }
    }
}
