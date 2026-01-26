package tachiyomi.data.achievement.handler

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import tachiyomi.data.achievement.model.AchievementEvent

class AchievementEventBus {
    private val _events = MutableSharedFlow<AchievementEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<AchievementEvent> = _events.asSharedFlow()

    suspend fun emit(event: AchievementEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: AchievementEvent): Boolean {
        return _events.tryEmit(event)
    }

    val subscriptionCount: Flow<Int>
        get() = _events.subscriptionCount
}
