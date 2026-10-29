package eu.kanade.domain.easteregg.aurora

import java.util.Locale

/**
 * Тексты пасхалки намеренно захардкожены в Kotlin вне :i18n/:i18n-aniyomi —
 * стелс-дизайн: осознанное отклонение от правила AGENTS.md «String Resources»,
 * чтобы таблица переводов не была видна в ресурсах приложения.
 * Task 13 исполнен: секретные тексты живут только в зашифрованном ваулте,
 * их EN-варианты — En-поля payload (выбор локали — [localized]).
 */
object AuroraLocalization {

    /** Q5 (решение пользователя): семантика заморожена — `language != "ru"`, все нерусские локали получают EN. */
    fun isEnglish(): Boolean = Locale.getDefault().language != "ru"

    /**
     * Task 9 (B3): чистый фолбэк заголовка карточки «Сердце Авроры».
     * Заработанный payload-заголовок побеждает; иначе если заголовок ачивки
     * из achievements.json blank или «???» (плейсхолдер) — локализованное
     * имя, чтобы ОТКРЫТАЯ ачивка никогда не осталась «???».
     * Перевод payload-заголовка вызывающий сохраняет через translate().
     */
    fun auroraDisplayTitle(payloadTitle: String?, achievementTitle: String?, isEnglish: Boolean): String {
        if (!payloadTitle.isNullOrBlank()) return payloadTitle
        if (achievementTitle.isNullOrBlank() || achievementTitle == "???") {
            return if (isEnglish) "Heart of Aurora" else "Сердце Авроры"
        }
        return achievementTitle
    }

    /**
     * Task 13 (Q5): выбор локали для полей vault-payload — EN-юзеру En-вариант,
     * при отсутствии En (приватный сценарий) фолбэк на RU. Чистый overload — для тестов.
     */
    fun localized(ru: String?, en: String?): String? {
        val selected = localized(ru, en, isEnglish())
        return if (Locale.getDefault().language == "es") {
            translate(selected, "es")
        } else {
            selected
        }
    }

    internal fun localized(ru: String?, en: String?, isEnglish: Boolean): String? =
        if (isEnglish) (en ?: ru) else ru

    /**
     * RU→EN таблица UI-микрокопи пасхалки (Task 13: vault-derived ключи — загадки,
     * финальные тексты, «Продолжить путь» — удалены, их заменяют En-поля payload).
     */
    internal val UI_KEYS: Map<String, String> = mapOf(
        // Echo titles
        "Эхо I — Бессонница" to "Echo I — Insomnia",
        "Эхо II — Ориентир" to "Echo II — Landmark",
        "Эхо Авроры" to "Aurora Echo",
        // C1a: echoTitle первой вспышки из AuroraHeartManager — EN-юзеры не должны видеть русский
        "Зов Авроры" to "Call of Aurora",

        // Codex titles
        "КОДЕКС АВРОРЫ" to "AURORA CODEX",
        "Зов" to "The Call",
        "Эхо" to "Echo",
        "Письмо" to "The Letter",
        "Пережить манифестацию снова" to "Relive the Manifestation",

        // UI microcopy + whispers
        "Сохранить в сердце" to "Save to Heart",
        "если слова бессильны — удержи загадку" to "if words are powerless — hold the riddle",
        "Прочитать эхо заново" to "Replay the echo",
        "Аврора заметила тебя в этот час..." to "Aurora noticed you at this hour...",
        "Некоторые границы тоньше других." to "Some boundaries are thinner than others.",
        "Скрыто северным сиянием" to "Hidden by the aurora",
    )

    private val SPANISH_UI_KEYS: Map<String, String> = mapOf(
        "Heart of Aurora" to "Corazón de Aurora",
        "Сердце Авроры" to "Corazón de Aurora",
        "Echo I — Insomnia" to "Eco I — Insomnio",
        "Echo II — Landmark" to "Eco II — Punto de referencia",
        "Aurora Echo" to "Eco de Aurora",
        "Call of Aurora" to "Llamado de Aurora",
        "AURORA CODEX" to "CÓDICE DE AURORA",
        "The Call" to "El llamado",
        "Echo" to "Eco",
        "The Letter" to "La carta",
        "Relive the Manifestation" to "Revive la manifestación",
        "Save to Heart" to "Guardar en el corazón",
        "if words are powerless — hold the riddle" to "si las palabras no bastan, mantén presionado el acertijo",
        "Replay the echo" to "Volver a reproducir el eco",
        "Aurora noticed you at this hour..." to "Aurora se fijó en ti a esta hora...",
        "Some boundaries are thinner than others." to "Algunas fronteras son más delgadas que otras.",
        "Hidden by the aurora" to "Oculto por la aurora boreal",
        "Северное сияние помнит тех, кто не спит." to "La aurora boreal recuerda a quienes no duermen.",
        "Aurora Keeper" to "Guardián de Aurora",
        "Хранитель Авроры" to "Guardián de Aurora",
        "CONSTELLATION" to "CONSTELACIÓN",
        "the thread is thin — slower" to "el hilo es frágil: más despacio",
        "touch the stars, one by one" to "toca las estrellas, una por una",
        "join the stars with one thread" to "une las estrellas con un solo hilo",
        "Anew" to "Empezar de nuevo",
        "Seal" to "Sellar",
        "weave with a thread" to "tejer con un hilo",
        "touch one by one" to "tocar una por una",
        "AURORA ECHO" to "ECO DE AURORA",
    )

    fun translate(text: String?): String? = translate(text, Locale.getDefault().language)

    /**
     * Чистый overload без Locale — для тестов. Семантика 1-в-1 со старым `when(text.trim())`:
     * null → null; ru-guard возвращает вход ДО trim; промах — исходный (не trim) текст.
     */
    internal fun translate(text: String?, languageCode: String): String? {
        if (text == null) return null
        if (languageCode == "ru") return text
        if (languageCode == "es") {
            val key = text.trim()
            val english = UI_KEYS[key] ?: key
            return SPANISH_UI_KEYS[english] ?: SPANISH_UI_KEYS[key] ?: text
        }
        return UI_KEYS[text.trim()] ?: text
    }

    fun translateThemeUnlock(themeName: String?): String? =
        translateThemeUnlock(themeName, Locale.getDefault().language)

    /**
     * C1c: шаблон вместо exact-ключа «Открыта тема: Aurora Prime».
     * null/blank → null — рендерить ли строку, решает вызывающий.
     */
    internal fun translateThemeUnlock(themeName: String?, languageCode: String): String? {
        if (themeName.isNullOrBlank()) return null
        return when (languageCode) {
            "ru" -> "Открыта тема: $themeName"
            "es" -> "Tema desbloqueado: $themeName"
            else -> "Theme unlocked: $themeName"
        }
    }
}
