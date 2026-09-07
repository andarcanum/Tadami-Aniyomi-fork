package eu.kanade.tachiyomi.ui.library

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * F3: debounce that emits the FIRST value of every collection immediately, debouncing only
 * subsequent values. The plain `debounce` delayed the library's initial render by the full
 * timeout on every screen model creation (the pipeline combine waits for all of its sources,
 * so the LoadingScreen stayed up ~250 ms longer than needed on every entry into the library).
 *
 * Implemented via channelFlow: the trailing emission happens in a cancellable child coroutine,
 * which a plain flow{} builder cannot do without violating the flow-context invariant.
 */
internal fun <T> Flow<T>.leadingDebounce(timeoutMillis: Long): Flow<T> = channelFlow {
    var pending: Job? = null
    var first = true
    collect { value ->
        if (first) {
            first = false
            send(value)
        } else {
            pending?.cancel()
            pending = launch {
                delay(timeoutMillis)
                send(value)
            }
        }
    }
}
