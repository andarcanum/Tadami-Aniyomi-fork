package eu.kanade.domain.easteregg.aurora

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Глобальная шина «эха»: AuroraQuest.offer() сам кладёт сюда
 * каждый пройденный этап (Progress) и финальную разблокировку
 * (payload). UI-оверлей AuroraEchoOverlay подписывается ОДИН раз
 * в корне приложения — вспышка этапа и финальный экран
 * появляются поверх ЛЮБОГО экрана без ручной проводки.
 *
 * На неверные ответы сюда НИЧЕГО не попадает (правило тишины).
 */
object AuroraEchoBus {
    private val _flash = MutableStateFlow<AuroraEcho.Progress?>(null)
    val flash: StateFlow<AuroraEcho.Progress?> = _flash

    private val _unlocked = MutableStateFlow<AuroraPayload?>(null)
    val unlocked: StateFlow<AuroraPayload?> = _unlocked

    /** Одноразовые «шёпоты» — тихие атмосферные намёки (без ответов!). */
    private val _whisper = MutableStateFlow<String?>(null)
    val whisper: StateFlow<String?> = _whisper

    /**
     * Хук побочных эффектов финала: разблокировка достижения,
     * начисление очков, уведомление. Установить ОДИН раз при
     * старте приложения (см. ШАГ 13 в AI_INTEGRATION_GUIDE.md).
     * Вызывается до показа финального экрана.
     * Не логировать содержимое payload.
     */
    @Volatile
    var onUnlocked: ((AuroraPayload) -> Unit)? = null

    fun emit(echo: AuroraEcho.Progress) {
        _flash.value = echo
    }

    fun consume() {
        _flash.value = null
    }

    fun emitUnlocked(payload: AuroraPayload) {
        runCatching { onUnlocked?.invoke(payload) }
        _unlocked.value = payload
    }

    fun consumeUnlocked() {
        _unlocked.value = null
    }

    fun emitWhisper(text: String) {
        _whisper.value = text
    }

    fun consumeWhisper() {
        _whisper.value = null
    }
}
