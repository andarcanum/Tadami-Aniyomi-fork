package tachiyomi.domain.translation.service

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import java.util.concurrent.ConcurrentHashMap

/** All consumers share admission and cooldown by endpoint/quota group, never by secret key. */
class TranslationRequestGate(private val clock: () -> Long = System::currentTimeMillis) {
    private data class Admission(val active: Int = 0, val limit: Int = 1, val due: Long = 0)
    private class Group {
        val admission = MutableStateFlow(Admission())
    }
    private val groups = ConcurrentHashMap<String, Group>()

    fun configure(key: String, limit: Int) {
        val group = groups.getOrPut(key) { Group() }
        synchronized(group) { group.admission.value = group.admission.value.copy(limit = limit.coerceIn(1, 32)) }
    }

    fun defer(key: String, due: Long) {
        val group = groups.getOrPut(key) { Group() }
        synchronized(group) {
            group.admission.value =
                group.admission.value.copy(due = maxOf(due, group.admission.value.due))
        }
    }

    suspend fun <T> execute(key: String, block: suspend () -> T): T {
        val group = groups.getOrPut(key) { Group() }
        while (true) {
            currentCoroutineContext().ensureActive()
            val acquired = synchronized(group) {
                val state = group.admission.value
                if (state.due > clock()) {
                    throw TranslationAttemptException(
                        TranslationFailure(
                            TranslationFailureKind.WAITING,
                            "Provider is cooling down after a temporary failure",
                            retryAfterAt = state.due,
                            attempted = false,
                        ),
                    )
                }
                if (state.active < state.limit) {
                    group.admission.value = state.copy(active = state.active + 1)
                    true
                } else {
                    false
                }
            }
            if (acquired) break
            group.admission.first { it.active < it.limit || it.due > clock() }
        }
        try {
            currentCoroutineContext().ensureActive()
            return block()
        } finally {
            synchronized(group) {
                val state = group.admission.value
                group.admission.value = state.copy(active = state.active - 1)
            }
        }
    }
}
