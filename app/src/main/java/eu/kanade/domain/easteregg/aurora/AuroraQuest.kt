package eu.kanade.domain.easteregg.aurora

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Результат попытки: прогресс по цепочке или финальная разблокировка. */
sealed interface AuroraEcho {
    data class Progress(
        val stageIndex: Int,
        val totalStages: Int,
        val echoTitle: String?,
        val nextRiddle: String,
    ) : AuroraEcho

    data class Unlocked(val payload: AuroraPayload) : AuroraEcho
}

/**
 * Состояние квеста «Сердце Авроры».
 *
 * Точки входа:
 *  - [revealHint] — вызывается триггером (например, серия, досмотренная
 *    в 03:00–04:00) — открывает первую загадку;
 *  - [offer] — скармливается каждый ОТПРАВЛЕННЫЙ запрос глобального поиска
 *    (именно submit, не каждый символ — PBKDF2 намеренно медленный).
 *
 * Имена SharedPreferences нарочно неприметные — не выдают пасхалку
 * при беглом осмотре данных приложения.
 */
class AuroraQuest(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    val isHintRevealed: Boolean get() = prefs.getBoolean(K_HINT, false)
    val isUnlocked: Boolean get() = prefs.getBoolean(K_DONE, false)
    private val stageIndex: Int get() = prefs.getInt(K_STAGE, 0)

    /** Публичные аксессоры для AuroraHeartManager (UI ходит через менеджер). */
    val currentStageIndex: Int get() = stageIndex
    val totalStagesCount: Int get() = AuroraVaultData.STAGES.size

    /** Вызывается триггером-событием. Повторные вызовы безвредны. */
    fun revealHint() {
        if (!isHintRevealed) prefs.edit().putBoolean(K_HINT, true).apply()
    }

    /** Текущая загадка для UI. null — пока подсказка не открыта или квест пройден. */
    fun currentRiddle(): String? = when {
        !isHintRevealed || isUnlocked -> null
        stageIndex == 0 -> AuroraVaultData.FIRST_RIDDLE
        else -> prefs.getString(K_RIDDLE, null)
    }

    /** Финальная награда, если квест пройден на этом устройстве. */
    fun unlockedPayload(): AuroraPayload? =
        prefs.getString(K_PAYLOAD, null)?.let {
            runCatching { json.decodeFromString<AuroraPayload>(it) }.getOrNull()
        }

    /**
     * Проверяет поисковый запрос против текущей ступени.
     * Возвращает null почти всегда — вызов безопасен и незаметен.
     */
    fun offer(query: String): AuroraEcho? {
        if (isUnlocked) return null
        if (query.length !in 3..64) return null
        val idx = stageIndex
        val stage = AuroraVaultData.STAGES.getOrNull(idx) ?: return null
        val plain = AuroraVault.tryOpen(query, stage)?.decodeToString() ?: return null
        val payload = runCatching { json.decodeFromString<AuroraPayload>(plain) }.getOrNull() ?: return null

        return if (payload.kind == "final") {
            prefs.edit()
                .putBoolean(K_DONE, true)
                .putString(K_PAYLOAD, plain)
                .remove(K_RIDDLE)
                .apply()
            AuroraEchoBus.emitUnlocked(payload)
            AuroraEcho.Unlocked(payload)
        } else {
            prefs.edit()
                .putInt(K_STAGE, idx + 1)
                .putBoolean(K_HINT, true)
                .putString(K_RIDDLE, payload.riddle)
                .apply()
            appendEcho(payload.echoTitle, payload.riddle)
            val echo = AuroraEcho.Progress(
                stageIndex = idx + 1,
                totalStages = AuroraVaultData.STAGES.size,
                echoTitle = payload.echoTitle,
                nextRiddle = payload.riddle.orEmpty(),
            )
            AuroraEchoBus.emit(echo)
            echo
        }
    }

    /** Кодекс: журнал пройденных эх (читает AuroraHeartManager.codex()). */
    private fun appendEcho(title: String?, riddle: String?) {
        val list = runCatching {
            json.decodeFromString<List<AuroraCodexEntry>>(prefs.getString(K_ECHOES, null) ?: "[]")
        }.getOrDefault(emptyList())
        prefs.edit()
            .putString(K_ECHOES, json.encodeToString(list + AuroraCodexEntry(title = title, riddle = riddle)))
            .apply()
    }

    private companion object {
        const val STORE = "render_pipeline_cache"
        const val K_ECHOES = "rp_trace"
        const val K_HINT = "rp_warm"
        const val K_STAGE = "rp_pass"
        const val K_RIDDLE = "rp_shader"
        const val K_DONE = "rp_baked"
        const val K_PAYLOAD = "rp_blob"
    }
}
