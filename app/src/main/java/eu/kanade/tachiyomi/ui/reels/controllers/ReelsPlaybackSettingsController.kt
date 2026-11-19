package eu.kanade.tachiyomi.ui.reels.controllers

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.ui.reels.ReelsFeedScreenModel
import eu.kanade.tachiyomi.ui.reels.ReelsSessionSoundState
import eu.kanade.tachiyomi.ui.reels.ReelsTransitionCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns playback preferences, play/pause intent, settings hold, hardware volume-up unmute,
 * race-free `isActuallyPlaying` tracking per pager page, and the sleep timer.
 *
 * Extracted from [ReelsFeedScreenModel] to isolate playback state transitions and prevent the
 * preloaded-card swipe race (where `onPageChanged` used to overwrite `isActuallyPlaying = true`
 * back to `false`).
 */
internal class ReelsPlaybackSettingsController(
    private val scope: CoroutineScope,
    private val sourcePreferences: SourcePreferences,
    private val sessionSound: ReelsSessionSoundState,
    private val mutableState: MutableStateFlow<ReelsFeedScreenModel.State>,
) {
    private var settingsHoldActive = false
    private var sleepTimerJob: Job? = null

    /**
     * Tracks the pager index whose player most recently confirmed `Player.isPlaying == true`.
     * When a preloaded card activates and fires `onIsPlayingChanged(true)` BEFORE `onPageChanged`
     * runs in the same frame, `onPageChanged` checks this index instead of blindly clearing
     * `isActuallyPlaying = false`.
     */
    @Volatile
    private var reportedRunningPage: Int? = null

    fun toggleAutoAdvance() {
        val next = !mutableState.value.isAutoAdvance
        sourcePreferences.autoAdvanceReels().set(next)
        mutableState.update { it.copy(isAutoAdvance = next) }
    }

    fun toggleCropMode() {
        val next = !mutableState.value.isCropMode
        sourcePreferences.reelsCropMode().set(next)
        mutableState.update { it.copy(isCropMode = next) }
    }

    fun togglePreload() {
        val next = !mutableState.value.preloadEnabled
        sourcePreferences.reelsPreloadEnabled().set(next)
        mutableState.update { it.copy(preloadEnabled = next) }
    }

    fun togglePreloadWifiOnly() {
        val next = !mutableState.value.preloadWifiOnly
        sourcePreferences.reelsPreloadWifiOnly().set(next)
        mutableState.update { it.copy(preloadWifiOnly = next) }
    }

    fun togglePip() {
        val next = !mutableState.value.isPipEnabled
        sourcePreferences.reelsPipEnabled().set(next)
        mutableState.update { it.copy(isPipEnabled = next) }
    }

    fun toggleDataSaver() {
        val next = !mutableState.value.dataSaverMetered
        sourcePreferences.reelsDataSaverMetered().set(next)
        mutableState.update { it.copy(dataSaverMetered = next) }
    }

    fun toggleQuality() {
        val next = !mutableState.value.isHdQuality
        sourcePreferences.reelsHdQuality().set(next)
        mutableState.update { it.copy(isHdQuality = next) }
    }

    fun toggleMute() {
        val next = !mutableState.value.isMuted
        sessionSound.decided = true
        sourcePreferences.reelsMuted().set(next)
        mutableState.update { it.copy(isMuted = next, showUnmuteHint = false) }
    }

    /** Hardware Volume-Up unmute: unmutes cleanly when muted, no-op if already audible. */
    fun unmuteIfMuted() {
        if (!mutableState.value.isMuted) return
        sessionSound.decided = true
        sourcePreferences.reelsMuted().set(false)
        mutableState.update { it.copy(isMuted = false, showUnmuteHint = false) }
    }

    fun dismissUnmuteHint() {
        mutableState.update { if (it.showUnmuteHint) it.copy(showUnmuteHint = false) else it }
    }

    fun togglePlayPause() {
        val next = !mutableState.value.isPlaying
        mutableState.update { it.copy(isPlaying = next, userPaused = !next) }
    }

    fun setSettingsHold(hold: Boolean) {
        if (hold == settingsHoldActive) return
        settingsHoldActive = hold
        mutableState.update { current ->
            if (hold) {
                if (current.isPlaying) current.copy(isPlaying = false) else current
            } else {
                if (!current.userPaused) current.copy(isPlaying = true) else current
            }
        }
    }

    fun setPlaybackRunning(running: Boolean, pageIndex: Int? = null) {
        if (pageIndex == null) {
            reportedRunningPage = null
            mutableState.update { current ->
                if (current.isActuallyPlaying == running) current else current.copy(isActuallyPlaying = running)
            }
            return
        }
        if (running) {
            reportedRunningPage = pageIndex
            mutableState.update { current ->
                if (current.isActuallyPlaying) current else current.copy(isActuallyPlaying = true)
            }
        } else {
            if (reportedRunningPage == pageIndex) {
                reportedRunningPage = null
            }
            mutableState.update { current ->
                // Ignore a late pause/stop callback from an outgoing page that is no longer active.
                if (pageIndex != current.activeIndex || !current.isActuallyPlaying) {
                    current
                } else {
                    current.copy(isActuallyPlaying = false)
                }
            }
        }
    }

    /**
     * Computes the next `(isPlaying, userPaused, isActuallyPlaying)` triple when settling on
     * [targetIndex]:
     * - Manual user swipes (`USER_FORWARD` / `USER_BACKWARD`) auto-resume playback (TikTok/Shorts UX).
     * - Programmatic transitions preserve a deliberate sticky pause (e.g. sleep timer / restore).
     * - If the target page's preloaded player already reported `isPlaying == true`, preserve
     *   `isActuallyPlaying = true` so `FLAG_KEEP_SCREEN_ON`, PiP and chrome auto-hide keep working.
     */
    fun resolvePageTransitionState(
        current: ReelsFeedScreenModel.State,
        targetIndex: Int,
        cause: ReelsTransitionCause,
    ): PagePlaybackTransition {
        val isManualSwipe = cause == ReelsTransitionCause.USER_FORWARD ||
            cause == ReelsTransitionCause.USER_BACKWARD
        val nextUserPaused = if (isManualSwipe) false else current.userPaused
        val nextIsPlaying = !nextUserPaused && !settingsHoldActive
        val nextActuallyPlaying = (reportedRunningPage == targetIndex) && nextIsPlaying
        return PagePlaybackTransition(
            isPlaying = nextIsPlaying,
            userPaused = nextUserPaused,
            isActuallyPlaying = nextActuallyPlaying,
        )
    }

    fun resetPlaybackTracking() {
        reportedRunningPage = null
    }

    fun setSleepTimer(option: ReelsFeedScreenModel.SleepTimerOption) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        when (option) {
            ReelsFeedScreenModel.SleepTimerOption.OFF -> mutableState.update {
                it.copy(sleepTimerRemainingSec = 0, sleepAtVideoEnd = false)
            }
            ReelsFeedScreenModel.SleepTimerOption.END_OF_VIDEO -> mutableState.update {
                it.copy(sleepTimerRemainingSec = 0, sleepAtVideoEnd = true)
            }
            else -> {
                val minutes = when (option) {
                    ReelsFeedScreenModel.SleepTimerOption.M15 -> 15
                    ReelsFeedScreenModel.SleepTimerOption.M30 -> 30
                    else -> 60
                }
                mutableState.update {
                    it.copy(sleepTimerRemainingSec = minutes * 60, sleepAtVideoEnd = false)
                }
                sleepTimerJob = scope.launch {
                    while (true) {
                        delay(1000)
                        var expired = false
                        mutableState.update { current ->
                            val remaining = current.sleepTimerRemainingSec - 1
                            if (remaining <= 0) {
                                expired = true
                                current.copy(sleepTimerRemainingSec = 0)
                            } else {
                                current.copy(sleepTimerRemainingSec = remaining)
                            }
                        }
                        if (expired) {
                            triggerSleep()
                            break
                        }
                    }
                }
            }
        }
    }

    fun triggerSleep() {
        sleepTimerJob = null
        mutableState.update {
            it.copy(isPlaying = false, userPaused = true, sleepAtVideoEnd = false, sleepTimerRemainingSec = 0)
        }
    }

    fun consumeSleepAtVideoEnd(): Boolean {
        if (!mutableState.value.sleepAtVideoEnd) return false
        triggerSleep()
        return true
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
    }

    data class PagePlaybackTransition(
        val isPlaying: Boolean,
        val userPaused: Boolean,
        val isActuallyPlaying: Boolean,
    )
}
