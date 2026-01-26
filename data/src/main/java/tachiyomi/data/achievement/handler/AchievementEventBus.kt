package tachiyomi.data.achievement.handler

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import tachiyomi.data.achievement.model.AchievementEvent

/**
 * Event bus для системы достижений.
 *
 * Использует SharedFlow для передачи событий между компонентами системы.
 * Поддерживает множественную подписку и асинхронную обработку.
 *
 * Пример использования:
 * ```kotlin
 * // Отправка события
 * eventBus.emit(AchievementEvent.ChapterRead(chapterId, mangaId))
 *
 * // Подписка на события
 * eventBus.events.collect { event ->
 *     when (event) {
 *         is AchievementEvent.ChapterRead -> handleChapterRead(event)
 *         // ...
 *     }
 * }
 * ```
 *
 * @see AchievementEvent
 */
class AchievementEventBus {
    /**
     * Внутренний поток событий.
     *
     * Параметры:
     * - replay = 0: Не хранить старые события для новых подписчиков
     * - extraBufferCapacity = 100: Буфер на 100 событий при перегрузке
     * - DROP_OLDEST: При переполнении удалять старейшие события
     */
    private val _events = MutableSharedFlow<AchievementEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Публичный поток событий для чтения.
     * Подписчики получают события в реальном времени.
     */
    val events: SharedFlow<AchievementEvent> = _events.asSharedFlow()

    /**
     * Отправляет событие в шину.
     *
     * Приостанавливает выполнение если буфер полон, пока не освободится место.
     *
     * @param event Событие для отправки
     */
    suspend fun emit(event: AchievementEvent) {
        _events.emit(event)
    }

    /**
     * Пытается отправить событие без приостановки.
     *
     * @param event Событие для отправки
     * @return true если событие успешно отправлено, false если буфер полон
     */
    fun tryEmit(event: AchievementEvent): Boolean {
        return _events.tryEmit(event)
    }

    /**
     * Количество активных подписчиков.
     * Используется для мониторинга состояния системы.
     */
    val subscriptionCount: Flow<Int>
        get() = _events.subscriptionCount
}
