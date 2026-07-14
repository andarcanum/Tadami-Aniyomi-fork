package eu.kanade.domain.easteregg.aurora

import java.util.Locale

object AuroraLocalization {
    val isEnglish: Boolean
        get() = Locale.getDefault().language != "ru"

    fun translate(text: String?): String? {
        if (text == null) return null
        if (!isEnglish) return text
        return when (text.trim()) {
            "Аврора шепчет тем, кто не спит: назови время, когда граница между небом и сном " +
                "тоньше всего. Ответ отдай поиску.",
            ->
                "Aurora whispers to those who do not sleep: name the hour when the boundary " +
                    "between sky and sleep is thinnest. Give your answer to search."

            "Верно… Но дальше слова бессильны. Вспомни ту, что веками ведёт заблудших домой, " +
                "сама не сдвигаясь с места. Удержи эту загадку — и начерти её путь: с неба " +
                "до земли, по прямой, сквозь самое сердце.",
            ->
                "True… But further, words are powerless. Remember the one that for " +
                    "centuries guides the lost home, without moving from her place. Hold this " +
                    "riddle — and trace her path: from sky to earth, in a straight line, " +
                    "right through the heart."

            "Последний шаг: узнай имя танцующего света на языке народа, который увидел его " +
                "первым. Но не кричи его в пустоту поиска — построй для него дом. Место, где " +
                "ты собираешь самое дорогое, назови его именем.",
            ->
                "The final step: find the name of the dancing light in the language of " +
                    "the people who saw it first. But do not shout it into the void of search " +
                    "— build a home for it. Name the place where you gather your most " +
                    "precious things after it."

            // Echo titles
            "Эхо I — Бессонница" -> "Echo I — Insomnia"
            "Эхо II — Ориентир" -> "Echo II — Landmark"
            "Эхо Авроры" -> "Aurora Echo"

            // Codex titles
            "КОДЕКС АВРОРЫ" -> "AURORA CODEX"
            "Зов" -> "The Call"
            "Эхо" -> "Echo"
            "Письмо" -> "The Letter"
            "Продолжить путь" -> "Continue the Path"
            "Пережить манифестацию снова" -> "Relive the Manifestation"

            // Final screen payload
            "Сердце Авроры" -> "Heart of Aurora"
            "Ты услышал шёпот в час волка, начертил путь неподвижной звезды и дал " +
                "танцующему свету его древнее имя. Это достижение нельзя найти " +
                "в коде — только пройти.",
            ->
                "You heard the whisper in the hour of the wolf, traced the path of the " +
                    "immovable star, and gave the dancing light its ancient name. This " +
                    "achievement cannot be found in code — only lived."

            "Хранитель Авроры" -> "Aurora Keeper"
            "Если ты читаешь это — ты один из немногих. Этой награды нет ни в коде, ни в APK: " +
                "она существовала только в зашифрованном виде, пока ты не разгадал шёпот, " +
                "не начертил верный путь и не дал свету имя. Носи титул с " +
                "гордостью. — Разработчик",
            ->
                "If you are reading this — you are one of the few. This reward does " +
                    "not exist in code, nor in the APK: it existed only in encrypted form " +
                    "until you unraveled the whisper, traced the path, and gave the light " +
                    "its name. Wear this title with pride. — The Developer"

            "Aurora Prime" -> "Aurora Prime"
            "Открыта тема: Aurora Prime" -> "Theme unlocked: Aurora Prime"
            "Сохранить в сердце" -> "Save to Heart"
            "если слова бессильны — удержи загадку" -> "if words are powerless — hold the riddle"
            "Прочитать эхо заново" -> "Replay the echo"
            "Аврора заметила тебя в этот час..." -> "Aurora noticed you at this hour..."
            "Некоторые границы тоньше других." -> "Some boundaries are thinner than others."
            "Скрыто северным сиянием" -> "Hidden by the aurora"
            else -> text
        }
    }
}
