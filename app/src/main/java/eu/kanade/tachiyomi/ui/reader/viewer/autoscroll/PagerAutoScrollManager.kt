package eu.kanade.tachiyomi.ui.reader.viewer.autoscroll

import android.os.SystemClock
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat

/**
 * Auto-scroll manager for [PagerViewer] that advances pages at regular intervals based on
 * the configured speed using a coroutine loop.
 *
 * Unlike [android.os.CountDownTimer] (which triggers `onTick` immediately at `t = 0`), this
 * implementation always waits for the full page delay before advancing, waits for the current
 * page to finish loading before turning it, and notifies [onReachedEnd] when there are no more
 * pages or chapters ahead.
 *
 * @property viewer The [PagerViewer] instance to control.
 */
class PagerAutoScrollManager(
    private val viewer: PagerViewer,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
) : AutoScrollManager {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(AutoScrollState())
    override val state: StateFlow<AutoScrollState> = _state.asStateFlow()

    override val isRunning: Boolean
        get() = state.value.isActive &&
            !state.value.isPaused &&
            state.value.cooldownUntilMs <= clockMs()

    /**
     * Invoked when the manager stops itself after reaching the end of readable content,
     * allowing the viewer to sync UI state.
     */
    var onReachedEnd: (() -> Unit)? = null

    private var scrollJob: Job? = null
    private var cooldownUntilMs: Long = 0L

    /**
     * Calculates the page delay in milliseconds based on the speed setting.
     * Higher speed values result in shorter delays.
     *
     * @param speed The speed value (1-100).
     * @return The delay in milliseconds before advancing to the next page.
     */
    internal fun calculatePageDelay(speed: Int): Long {
        // Map speed (1-100) to delay (10000ms - 2000ms)
        // Speed 1 = 10000ms (10s, very slow), Speed 50 = 6000ms (6s, medium)
        // Speed 100 = 2000ms (2s, fast)
        val clampedSpeed = speed.coerceIn(1, 100)
        return (10000 - (clampedSpeed - 1) * 80).toLong()
    }

    override fun setCooldown(delayMs: Long) {
        if (!_state.value.isActive || _state.value.isPaused) {
            return
        }

        val safeDelay = delayMs.coerceAtLeast(0L)
        cooldownUntilMs = clockMs() + safeDelay
        _state.update { it.copy(cooldownUntilMs = cooldownUntilMs) }
        scheduleLoop(initialCooldownMs = safeDelay)
    }

    override fun start(speed: Int?) {
        if (speed != null) {
            _state.update { it.copy(speed = speed.coerceIn(1, 100)) }
        }

        stopTimer()
        cooldownUntilMs = 0L
        _state.update { it.copy(isActive = true, isPaused = false, cooldownUntilMs = 0L) }
        scheduleLoop(initialCooldownMs = 0L)
        logcat { "PagerAutoScrollManager started with speed ${_state.value.speed}" }
    }

    override fun stop() {
        stopTimer()
        cooldownUntilMs = 0L
        _state.update { it.copy(isActive = false, isPaused = false, cooldownUntilMs = 0L) }
        logcat { "PagerAutoScrollManager stopped" }
    }

    override fun pause() {
        if (!_state.value.isActive || _state.value.isPaused) return

        stopTimer()
        _state.update { it.copy(isPaused = true) }
        logcat { "PagerAutoScrollManager paused" }
    }

    override fun resume() {
        if (!_state.value.isActive || !_state.value.isPaused) return

        _state.update { it.copy(isPaused = false) }
        val remainingCooldown = (cooldownUntilMs - clockMs()).coerceAtLeast(0L)
        if (remainingCooldown == 0L && cooldownUntilMs != 0L) {
            cooldownUntilMs = 0L
            _state.update { it.copy(cooldownUntilMs = 0L) }
        }
        scheduleLoop(initialCooldownMs = remainingCooldown)
        logcat { "PagerAutoScrollManager resumed" }
    }

    override fun togglePause() {
        when {
            !_state.value.isActive -> start()
            _state.value.isPaused -> resume()
            else -> pause()
        }
    }

    override fun setSpeed(speed: Int) {
        val clampedSpeed = speed.coerceIn(1, 100)
        if (_state.value.speed == clampedSpeed) return
        _state.update { it.copy(speed = clampedSpeed) }

        // If actively running (not in cooldown), restart the delay with the new speed
        // without immediately skipping the current page.
        if (isRunning) {
            scheduleLoop(initialCooldownMs = 0L)
        }
        logcat { "PagerAutoScrollManager speed set to $clampedSpeed" }
    }

    override fun destroy() {
        stop()
        scope.cancel()
    }

    private fun scheduleLoop(initialCooldownMs: Long) {
        stopTimer()
        scrollJob = scope.launch {
            if (initialCooldownMs > 0L) {
                delay(initialCooldownMs)
                if (!isActive || !_state.value.isActive || _state.value.isPaused) return@launch
                cooldownUntilMs = 0L
                _state.update { it.copy(cooldownUntilMs = 0L) }
            }

            while (isActive && _state.value.isActive && !_state.value.isPaused) {
                if (viewer.isAtAutoScrollEnd()) {
                    val callback = onReachedEnd
                    stop()
                    callback?.invoke()
                    break
                }

                delay(calculatePageDelay(_state.value.speed))
                if (!isActive || !_state.value.isActive || _state.value.isPaused) break

                // Do not flip past a page that is still downloading/decoding.
                var waitedMs = 0L
                while (
                    isActive &&
                    _state.value.isActive &&
                    !_state.value.isPaused &&
                    !viewer.isCurrentPageReady() &&
                    waitedMs < MAX_READY_WAIT_MS
                ) {
                    delay(READY_POLL_INTERVAL_MS)
                    waitedMs += READY_POLL_INTERVAL_MS
                }
                if (!isActive || !_state.value.isActive || _state.value.isPaused) break

                if (viewer.isAtAutoScrollEnd()) {
                    val callback = onReachedEnd
                    stop()
                    callback?.invoke()
                    break
                }

                viewer.moveToNext()

                if (viewer.isAtAutoScrollEnd()) {
                    val callback = onReachedEnd
                    stop()
                    callback?.invoke()
                    break
                }
            }
        }
    }

    private fun stopTimer() {
        scrollJob?.cancel()
        scrollJob = null
    }

    companion object {
        private const val READY_POLL_INTERVAL_MS = 250L
        private const val MAX_READY_WAIT_MS = 15_000L
    }
}
