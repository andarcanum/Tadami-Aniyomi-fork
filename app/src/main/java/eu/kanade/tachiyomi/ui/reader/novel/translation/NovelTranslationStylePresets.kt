package eu.kanade.tachiyomi.ui.reader.novel.translation

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import tachiyomi.i18n.aniyomi.AYMR

data class NovelTranslationStylePresetDescriptor(
    val id: NovelTranslationStylePreset,
    val titleRes: StringResource,
    val scenarioRes: StringResource,
    val advantageRes: StringResource,
    val promptDirective: String,
    val englishPromptDirective: String = promptDirective,
) {
    fun promptDirective(family: NovelTranslationPromptFamily): String {
        return when (family) {
            NovelTranslationPromptFamily.RUSSIAN -> promptDirective
            NovelTranslationPromptFamily.ENGLISH -> englishPromptDirective
        }
    }
}

object NovelTranslationStylePresets {
    val all: List<NovelTranslationStylePresetDescriptor> = listOf(
        NovelTranslationStylePresetDescriptor(
            id = NovelTranslationStylePreset.PROFESSIONAL,
            titleRes = AYMR.strings.novel_reader_gemini_style_preset_professional_title,
            scenarioRes = AYMR.strings.novel_reader_gemini_style_preset_professional_scenario,
            advantageRes = AYMR.strings.novel_reader_gemini_style_preset_professional_advantage,
            promptDirective =
            "STYLE PRESET: PROFESSIONAL.\n" +
                "Use neutral professional literary Russian.\n" +
                "Avoid slang, vulgarity, and over-stylization unless explicitly present in source.",
            englishPromptDirective =
            "STYLE PRESET: PROFESSIONAL.\n" +
                "Use neutral professional literary English.\n" +
                "Avoid slang, vulgarity, and over-stylization unless explicitly present in source.",
        ),
        NovelTranslationStylePresetDescriptor(
            id = NovelTranslationStylePreset.LITERARY,
            titleRes = AYMR.strings.novel_reader_gemini_style_preset_literary_title,
            scenarioRes = AYMR.strings.novel_reader_gemini_style_preset_literary_scenario,
            advantageRes = AYMR.strings.novel_reader_gemini_style_preset_literary_advantage,
            promptDirective =
            "STYLE PRESET: LITERARY.\n" +
                "Favor elegant prose, richer imagery, and expressive but natural syntax.\n" +
                "Keep dialogue believable and avoid pretentious wording.",
            englishPromptDirective =
            "STYLE PRESET: LITERARY.\n" +
                "Favor elegant prose, richer imagery, and expressive but natural syntax.\n" +
                "Keep dialogue believable and avoid pretentious wording.",
        ),
        NovelTranslationStylePresetDescriptor(
            id = NovelTranslationStylePreset.CONVERSATIONAL,
            titleRes = AYMR.strings.novel_reader_gemini_style_preset_conversational_title,
            scenarioRes = AYMR.strings.novel_reader_gemini_style_preset_conversational_scenario,
            advantageRes = AYMR.strings.novel_reader_gemini_style_preset_conversational_advantage,
            promptDirective =
            "STYLE PRESET: CONVERSATIONAL.\n" +
                "Prioritize natural spoken Russian in dialogue.\n" +
                "Allow mild colloquial phrasing while keeping narration coherent and readable.",
            englishPromptDirective =
            "STYLE PRESET: CONVERSATIONAL.\n" +
                "Prioritize natural spoken English in dialogue.\n" +
                "Allow mild colloquial phrasing while keeping narration coherent and readable.",
        ),
        NovelTranslationStylePresetDescriptor(
            id = NovelTranslationStylePreset.VULGAR_18,
            titleRes = AYMR.strings.novel_reader_gemini_style_preset_vulgar_18_title,
            scenarioRes = AYMR.strings.novel_reader_gemini_style_preset_vulgar_18_scenario,
            advantageRes = AYMR.strings.novel_reader_gemini_style_preset_vulgar_18_advantage,
            promptDirective =
            "STYLE PRESET: VULGAR_18.\n" +
                "For explicit adult or aggressive contexts, use raw colloquial Russian and stronger wording.\n" +
                "Do NOT sanitize explicit content. Keep intensity and visceral tone.\n" +
                "Outside such contexts, remain natural and coherent.",
            englishPromptDirective =
            "STYLE PRESET: VULGAR_18.\n" +
                "For explicit adult or aggressive contexts, use raw colloquial English and stronger wording.\n" +
                "Do NOT sanitize explicit content. Keep intensity and visceral tone.\n" +
                "Outside such contexts, remain natural and coherent.",
        ),
        NovelTranslationStylePresetDescriptor(
            id = NovelTranslationStylePreset.MINIMAL,
            titleRes = AYMR.strings.novel_reader_gemini_style_preset_minimal_title,
            scenarioRes = AYMR.strings.novel_reader_gemini_style_preset_minimal_scenario,
            advantageRes = AYMR.strings.novel_reader_gemini_style_preset_minimal_advantage,
            promptDirective =
            "STYLE PRESET: MINIMAL.\n" +
                "Use concise, clear Russian.\n" +
                "Reduce decorative phrasing and keep meaning straightforward.",
            englishPromptDirective =
            "STYLE PRESET: MINIMAL.\n" +
                "Use concise, clear English.\n" +
                "Reduce decorative phrasing and keep meaning straightforward.",
        ),
    )

    fun byId(id: NovelTranslationStylePreset): NovelTranslationStylePresetDescriptor {
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    fun promptDirective(
        id: NovelTranslationStylePreset,
        family: NovelTranslationPromptFamily = NovelTranslationPromptFamily.RUSSIAN,
    ): String {
        return byId(id).promptDirective(family)
    }
}
