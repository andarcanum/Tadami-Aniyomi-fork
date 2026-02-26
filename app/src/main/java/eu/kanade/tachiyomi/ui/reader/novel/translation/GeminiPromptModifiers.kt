package eu.kanade.tachiyomi.ui.reader.novel.translation

data class GeminiPromptModifier(
    val id: String,
    val label: String,
    val description: String,
    val prompt: String,
)

object GeminiPromptModifiers {
    val all: List<GeminiPromptModifier> = listOf(
        GeminiPromptModifier(
            id = "adult",
            label = "18+ Adult",
            description = "Explicit content handling",
            prompt = """
                ### ADULT CONTENT DIRECTIVE
                - This is an ADULT (18+) novel. Explicit sexual content MUST be translated faithfully.
                - The Enemy is 'Dryness'.** If a sentence sounds like it came from a textbook, it is WRONG.
                - You are not a polite assistant. You are a Hardcore Localizer. Your translation must hit hard. It must sound like real Russian talk, not a sanitized book.
                - Use extreme explicit Russian vocabulary
                - NO euphemisms or medical terms
                - Maximize sensory descriptions (touch, taste, smell, sound)
                - Preserve the erotic tension and pacing of the original
                - **DON'T USE THIS WORD IN TRANSLATED TEXT:** 'нутро', 'влага', 'смазка', 'лоно', 'естество', 'плоть'
                - Character moans and reactions should feel authentic and visceral
            """.trimIndent(),
        ),
        GeminiPromptModifier(
            id = "xianxia",
            label = "Xianxia",
            description = "Chinese cultivation novels",
            prompt = """
                ### XIANXIA/CULTIVATION DIRECTIVE
                This is a Chinese cultivation novel. Use established Russian xianxia terminology:
                - Cultivation -> Культивация, Совершенствование
                - Qi -> Ци
                - Dantian -> Даньтянь
                - Meridians -> Меридианы
                - Foundation Establishment -> Становление Основы
                - Core Formation -> Формирование Ядра
                - Nascent Soul -> Юань Ин / Зарождение Души
                - Immortal -> Бессмертный
                - Heavenly Tribulation -> Небесная Кара / Небесное Испытание
                - Dao -> Дао, Путь
                - Spirit Stones -> Духовные Камни
                - Sect -> Секта
                - Elder -> Старейшина
                - Patriarch -> Патриарх
                - Keep Chinese names in pinyin transliteration
            """.trimIndent(),
        ),
        GeminiPromptModifier(
            id = "comedy",
            label = "Comedy",
            description = "Comedic/parody novels",
            prompt = """
                ### COMEDY/PARODY DIRECTIVE
                This is a comedy/parody novel. Prioritize HUMOR over literal accuracy:
                - Exaggerate comedic timing with punctuation and line breaks
                - Use modern Russian internet slang and memes where fitting
                - Translate jokes to culturally equivalent Russian humor
                - Add comedic particles (ну, блин, типа, короче) liberally
                - Physical comedy should sound absurd and dynamic
                - Tsukkomi/boke dynamics should feel natural in Russian
            """.trimIndent(),
        ),
        GeminiPromptModifier(
            id = "dark",
            label = "Dark Fantasy",
            description = "Grimdark/dark themes",
            prompt = """
                ### DARK FANTASY DIRECTIVE
                This is a dark/grimdark fantasy. Embrace the bleakness:
                - Use harsh, gritty vocabulary
                - Violence should feel visceral and impactful
                - Moral ambiguity should be preserved in dialogue
                - Avoid softening character cruelty or world darkness
                - Despair and hopelessness should resonate in word choice
                - Gore descriptions should be detailed when present
            """.trimIndent(),
        ),
        GeminiPromptModifier(
            id = "litrpg",
            label = "LitRPG",
            description = "GameLit/LitRPG novels",
            prompt = """
                ### LITRPG DIRECTIVE
                This is a LitRPG/GameLit novel. Use established gaming terminology:
                - Skill -> Навык
                - Level -> Уровень
                - Stats -> Характеристики (СИЛ, ЛОВ, ИНТ, ВЫН)
                - Dungeon -> Подземелье
                - Party -> Группа/Пати
                - HP/MP -> ОЗ/МП or just HP/MP
                - Quest -> Квест
                - Boss -> Босс
                - Loot -> Лут
                - Drop -> Дроп
                - Buff/Debuff -> Бафф/Дебафф
                - Aggro -> Аггро
                - Tank/DPS/Healer -> Танк/ДД/Хил
                - Keep system messages/notifications in their original format style
                - Stat windows should maintain visual structure
            """.trimIndent(),
        ),
    )

    fun buildPromptText(
        enabledIds: List<String>,
        customModifier: String,
    ): String {
        val parts = buildList {
            enabledIds.forEach { id ->
                all.firstOrNull { it.id == id }?.let { add(it.prompt) }
            }
            val custom = customModifier.trim()
            if (custom.isNotBlank()) {
                add("### CUSTOM DIRECTIVE\n$custom")
            }
        }
        return parts.joinToString("\n\n")
    }
}
