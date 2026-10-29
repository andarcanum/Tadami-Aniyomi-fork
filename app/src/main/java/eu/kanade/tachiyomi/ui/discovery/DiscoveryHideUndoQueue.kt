package eu.kanade.tachiyomi.ui.discovery

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Registers Hide/Undo operations synchronously, then executes them in submission order.
 * A mutex acquired inside independently launched IO coroutines would not preserve call order.
 * Jobs inherit cancellation and exception handling from the owning screen-model scope.
 */
internal class DiscoveryHideUndoQueue(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private var tail: Job? = null

    fun enqueue(operation: suspend () -> Unit): Job {
        val job = synchronized(lock) {
            val predecessor = tail
            scope.launch(context = dispatcher, start = CoroutineStart.LAZY) {
                predecessor?.join()
                operation()
            }.also { tail = it }
        }
        job.invokeOnCompletion {
            synchronized(lock) {
                if (tail === job) tail = null
            }
        }
        // Start outside the registration lock, including with an unconfined dispatcher.
        job.start()
        return job
    }
}
