package eu.kanade.domain.easteregg.aurora

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Наблюдаемое состояние квеста для UI (без секретов). */
data class AuroraHeartState(
    val hintRevealed: Boolean,
    val stageIndex: Int,
    val totalStages: Int,
    val unlocked: Boolean,
)

/**
 * ЕДИНСТВЕННАЯ продакшн-точка доступа к квесту (Pillar 6 плана):
 *  - синглтон (зарегистрируй в Injekt/DI или бери через get());
 *  - StateFlow состояния — UI больше НЕ читает SharedPreferences напрямую;
 *  - offer() всегда на IO + mutex + rate-limit (PBKDF2 намеренно дорог);
 *  - версионирование ваулта: при замене AuroraVaultData прогресс
 *    мягко сбрасывается (без крашей и призрачных состояний);
 *  - ночной счётчик + одноразовый «шёпот» (Pillar 2, деликатная
 *    обнаружимость без спойлеров);
 *  - Кодекс — журнал пройденных эх для AuroraCodexScreen.
 */
class AuroraHeartManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val quest = AuroraQuest(appContext)
    private val prefs = appContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val cryptoMutex = Mutex()

    @Volatile
    private var lastAttemptAt = 0L

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AuroraHeartState> = _state

    init {
        migrateIfNeeded()
    }

    fun revealHint() {
        val wasRevealed = quest.isHintRevealed
        quest.revealHint()
        if (!wasRevealed && quest.isHintRevealed) {
            refresh()
            // Дать видимую обратную связь: вспышка + шёпот при первом открытии хинта (от чтения ранобэ и т.п.)
            val current = quest.currentRiddle()
            if (current != null) {
                AuroraEchoBus.emit(
                    AuroraEcho.Progress(
                        stageIndex = 0,
                        totalStages = quest.totalStagesCount,
                        echoTitle = "Зов Авроры",
                        nextRiddle = current,
                    ),
                )
            }
            AuroraEchoBus.emitWhisper("Аврора заметила тебя в этот час...")
        } else {
            refresh()
        }
    }

    fun currentRiddle(): String? = quest.currentRiddle()

    fun unlockedPayload(): AuroraPayload? = quest.unlockedPayload()

    fun firstRiddle(): String = AuroraVaultData.FIRST_RIDDLE

    /** Журнал пройденных эх (для Кодекса). Пуст, пока нет прогресса. */
    fun codex(): List<AuroraCodexEntry> = runCatching {
        json.decodeFromString<List<AuroraCodexEntry>>(prefs.getString(K_ECHOES, null) ?: "[]")
    }.getOrDefault(emptyList())

    /**
     * Единая точка проверки фраз из ВСЕХ каналов.
     * Всегда IO + mutex; попытки чаще чем раз в 250 мс отбрасываются
     * (анти-флуд дорогого PBKDF2). Вызывать из любой корутины.
     */
    suspend fun offer(phrase: String): AuroraEcho? {
        if (phrase.length !in 3..64) return null
        val now = SystemClock.elapsedRealtime()
        if (now - lastAttemptAt < MIN_ATTEMPT_INTERVAL_MS) return null
        lastAttemptAt = now
        val echo = cryptoMutex.withLock {
            withContext(Dispatchers.IO) { quest.offer(phrase) }
        }
        if (echo != null) refresh()
        return echo
    }

    /**
     * Отметка «ночного действия» (прочитана глава / досмотрена серия
     * в окне AuroraNight). После WHISPER_THRESHOLD таких ночей — один раз
     * за всю жизнь устройства — в шину уходит тихий шёпот без ответов.
     */
    fun registerNightAction() {
        if (quest.isUnlocked || quest.isHintRevealed) return
        val n = prefs.getInt(K_NIGHT, 0) + 1
        prefs.edit().putInt(K_NIGHT, n).apply()
        if (n >= WHISPER_THRESHOLD && !prefs.getBoolean(K_WHISPER, false)) {
            prefs.edit().putBoolean(K_WHISPER, true).apply()
            AuroraEchoBus.emitWhisper("Некоторые границы тоньше других.")
        }
    }

    fun refresh() {
        _state.value = readState()
    }

    // Centralized presentation state per strategist recommendation for AC1/AC2
    sealed class RiddlePresentation {
        object None : RiddlePresentation()
        data class Show(val riddle: String, val stageIndex: Int, val totalStages: Int) : RiddlePresentation()
    }

    private val _presentation = MutableStateFlow<RiddlePresentation>(RiddlePresentation.None)
    val presentation: StateFlow<RiddlePresentation> = _presentation

    fun onFlashFinished(echo: AuroraEcho.Progress) {
        // After flash for a solved stage (echo reports the advanced index), show the *current pending* riddle
        // using live quest state so dots and label match the next layer. Only auto-show if more stages.
        val cur = quest.currentStageIndex
        if (cur < echo.totalStages) {
            val r = currentRiddle() ?: return
            _presentation.value = RiddlePresentation.Show(r, cur, echo.totalStages)
            markCurrentStageRiddleAutoShown()
        }
    }

    fun dismissRiddle() {
        _presentation.value = RiddlePresentation.None
    }

    fun requestAutoShowForAchievements() {
        val r = currentRiddle() ?: return
        _presentation.value = RiddlePresentation.Show(r, quest.currentStageIndex, quest.totalStagesCount)
        markCurrentStageRiddleAutoShown() // one-time for manual browse path (continuation marks in onFlash)
    }

    /**
     * Полный сброс прогресса пасхалки «Сердце Авроры».
     * Доступно только в debug-сборках (кнопка на экране «Еще»).
     * Сбрасывает:
     * - состояние квеста (стадии, загадки)
     * - кодекс эхо
     * - счётчик ночных действий
     * - шёпоты
     */
    fun debugReset() {
        prefs.edit()
            .remove("rp_warm")
            .remove("rp_pass")
            .remove("rp_shader")
            .remove("rp_baked")
            .remove("rp_blob")
            .remove(K_ECHOES)
            .remove(K_NIGHT)
            .remove(K_WHISPER)
            .remove(K_VER)
            .remove(K_SHOWN_STAGES)
            .apply()

        // Сбросим внутреннее состояние квеста, чтобы currentRiddle() сразу вернул null
        lastAttemptAt = 0L
        refresh()
    }

    /** Возвращает true, если для текущей стадии ещё не было авто-открытия загадки (один раз на этап). */
    fun shouldAutoShowRiddleForCurrentStage(): Boolean {
        val stage = quest.currentStageIndex
        val shown = prefs.getString(K_SHOWN_STAGES, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet() ?: emptySet()
        return stage !in shown
    }

    /** Пометить текущую стадию как "авто-открыто один раз". */
    fun markCurrentStageRiddleAutoShown() {
        val stage = quest.currentStageIndex
        val shown = prefs.getString(K_SHOWN_STAGES, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toMutableSet() ?: mutableSetOf()
        shown += stage
        prefs.edit().putString(K_SHOWN_STAGES, shown.joinToString(",")).apply()
    }

    private fun readState() = AuroraHeartState(
        hintRevealed = quest.isHintRevealed,
        stageIndex = quest.currentStageIndex,
        totalStages = quest.totalStagesCount,
        unlocked = quest.isUnlocked,
    )

    /**
     * Версионирование ваулта: VERSION генерируется форжем из контрольных
     * хешей ступеней. Если хранилище заменили — старый прогресс невалиден
     * (другие загадки/ответы) и мягко сбрасывается. Больше не нужно
     * чистить данные приложения вручную при перегенерации сценария.
     */
    private fun migrateIfNeeded() {
        val stored = prefs.getInt(K_VER, 0)
        val payload = quest.unlockedPayload()
        val isStale = quest.isUnlocked && payload != null && payload.themeMaterial == null
        if (stored == AuroraVaultData.VERSION && !isStale) return
        prefs.edit()
            .remove("rp_warm")
            .remove("rp_pass")
            .remove("rp_shader")
            .remove("rp_baked")
            .remove("rp_blob")
            .remove(K_ECHOES)
            .remove(K_NIGHT)
            .remove(K_WHISPER)
            .remove(K_SHOWN_STAGES)
            .putInt(K_VER, AuroraVaultData.VERSION)
            .apply()
        refresh()
    }

    companion object {
        @Volatile
        private var instance: AuroraHeartManager? = null

        /** Синглтон. В проекте лучше зарегистрировать в DI (см. ШАГ 15). */
        fun get(context: Context): AuroraHeartManager =
            instance ?: synchronized(this) {
                instance ?: AuroraHeartManager(context).also { instance = it }
            }

        private const val STORE = "render_pipeline_cache"
        private const val K_VER = "rp_rev"
        private const val K_NIGHT = "rp_frames"
        private const val K_WHISPER = "rp_hdr"
        private const val K_ECHOES = "rp_trace"
        private const val K_SHOWN_STAGES = "rp_shown" // comma separated stages that have auto-opened their riddle once
        private const val MIN_ATTEMPT_INTERVAL_MS = 250L
        private const val WHISPER_THRESHOLD = 3
    }
}
