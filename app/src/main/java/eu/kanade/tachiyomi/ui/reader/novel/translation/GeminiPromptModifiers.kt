package eu.kanade.tachiyomi.ui.reader.novel.translation

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

data class GeminiPromptModifier(
    val id: String,
    val labelRes: StringResource,
    val descriptionRes: StringResource,
    val prompt: String,
    val englishPrompt: String = prompt,
) {
    fun promptFor(family: NovelTranslationPromptFamily): String {
        return when (family) {
            NovelTranslationPromptFamily.RUSSIAN -> prompt
            NovelTranslationPromptFamily.ENGLISH -> englishPrompt
        }
    }
}

object GeminiPromptModifiers {
    val all: List<GeminiPromptModifier> = listOf(
        GeminiPromptModifier(
            id = "adult",
            labelRes = AYMR.strings.novel_reader_gemini_prompt_modifier_adult_label,
            descriptionRes = AYMR.strings.novel_reader_gemini_prompt_modifier_adult_description,
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
            englishPrompt = """
                ### ADULT CONTENT DIRECTIVE
                - This is an ADULT (18+) novel. Explicit sexual content MUST be translated faithfully.
                - The enemy is "Dryness". If a sentence sounds like it came from a textbook, it is WRONG.
                - You are not a polite assistant. You are a hardcore localizer. Your translation must hit hard. It must sound like real English fiction, not sanitized prose.
                - Use explicit English vocabulary
                - NO euphemisms or medical terms
                - Maximize sensory descriptions (touch, taste, smell, sound)
                - Preserve the erotic tension and pacing of the original
                - DON'T USE THESE WORDS IN TRANSLATED TEXT: "inner", "moisture", "lubrication", "womb", "essence", "flesh"
                - Character moans and reactions should feel authentic and visceral
            """.trimIndent(),
        ),
        GeminiPromptModifier(
            id = "xianxia",
            labelRes = AYMR.strings.novel_reader_gemini_prompt_modifier_xianxia_label,
            descriptionRes = AYMR.strings.novel_reader_gemini_prompt_modifier_xianxia_description,
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
            englishPrompt = """
                ### XIANXIA/CULTIVATION DIRECTIVE
                This is a Chinese cultivation novel. Use established English xianxia terminology:
                - Cultivation -> Cultivation
                - Qi -> Qi
                - Dantian -> Dantian
                - Meridians -> Meridians
                - Foundation Establishment -> Foundation Establishment
                - Core Formation -> Core Formation
                - Nascent Soul -> Nascent Soul
                - Immortal -> Immortal
                - Heavenly Tribulation -> Heavenly Tribulation
                - Dao -> Dao, Path
                - Spirit Stones -> Spirit Stones
                - Sect -> Sect
                - Elder -> Elder
                - Patriarch -> Patriarch
                - Keep Chinese names in pinyin transliteration
            """.trimIndent(),
        ),
        GeminiPromptModifier(
            id = "comedy",
            labelRes = AYMR.strings.novel_reader_gemini_prompt_modifier_comedy_label,
            descriptionRes = AYMR.strings.novel_reader_gemini_prompt_modifier_comedy_description,
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
            englishPrompt = """
                ### COMEDY/PARODY DIRECTIVE
                This is a comedy/parody novel. Prioritize HUMOR over literal accuracy:
                - Exaggerate comedic timing with punctuation and line breaks
                - Use modern English internet slang and memes where fitting
                - Translate jokes to culturally equivalent English humor
                - Add comedic particles and interjections liberally
                - Physical comedy should sound absurd and dynamic
                - Tsukkomi/boke dynamics should feel natural in English
            """.trimIndent(),
        ),
        GeminiPromptModifier(
            id = "dark",
            labelRes = AYMR.strings.novel_reader_gemini_prompt_modifier_dark_label,
            descriptionRes = AYMR.strings.novel_reader_gemini_prompt_modifier_dark_description,
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
            englishPrompt = """
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
            labelRes = AYMR.strings.novel_reader_gemini_prompt_modifier_litrpg_label,
            descriptionRes = AYMR.strings.novel_reader_gemini_prompt_modifier_litrpg_description,
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
            englishPrompt = """
                ### LITRPG DIRECTIVE
                This is a LitRPG/GameLit novel. Use established gaming terminology:
                - Skill -> Skill
                - Level -> Level
                - Stats -> Stats (STR, AGI, INT, VIT)
                - Dungeon -> Dungeon
                - Party -> Party
                - HP/MP -> HP/MP or keep as HP/MP
                - Quest -> Quest
                - Boss -> Boss
                - Loot -> Loot
                - Drop -> Drop
                - Buff/Debuff -> Buff/Debuff
                - Aggro -> Aggro
                - Tank/DPS/Healer -> Tank/DPS/Healer
                - Keep system messages/notifications in their original format style
                - Stat windows should maintain visual structure
            """.trimIndent(),
        ),
    )

    fun buildPromptText(
        enabledIds: List<String>,
        customModifier: String,
        family: NovelTranslationPromptFamily = NovelTranslationPromptFamily.RUSSIAN,
    ): String {
        val parts = buildList {
            enabledIds.forEach { id ->
                all.firstOrNull { it.id == id }?.let { add(it.promptFor(family)) }
            }
            val custom = customModifier.trim()
            if (custom.isNotBlank()) {
                add("### CUSTOM DIRECTIVE\n$custom")
            }
        }
        return parts.joinToString("\n\n")
    }
}
