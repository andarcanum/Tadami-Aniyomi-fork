package eu.kanade.presentation.more.settings.screen

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SettingsNovelReaderScreenTest {

    @Test
    fun `novel reader display top settings keep prompt mode under Gemini and block opposite translator`() {
        val geminiEnabledSettings = novelReaderDisplayTopSettingSpecs(
            geminiEnabled = true,
            googleTranslateEnabled = false,
            geminiEnabledTitle = "AI translator",
            geminiPromptModeTitle = "Prompt mode",
            googleTranslateEnabledTitle = "Google translator",
            googleTranslateEnabledSubtitle = "Google subtitle",
        )
        val googleEnabledSettings = novelReaderDisplayTopSettingSpecs(
            geminiEnabled = false,
            googleTranslateEnabled = true,
            geminiEnabledTitle = "AI translator",
            geminiPromptModeTitle = "Prompt mode",
            googleTranslateEnabledTitle = "Google translator",
            googleTranslateEnabledSubtitle = "Google subtitle",
        )

        geminiEnabledSettings.map { it.key } shouldContainExactly listOf(
            NovelReaderDisplaySettingKey.GeminiEnabled,
            NovelReaderDisplaySettingKey.GeminiPromptMode,
            NovelReaderDisplaySettingKey.GoogleTranslateEnabled,
        )
        geminiEnabledSettings.single { it.key == NovelReaderDisplaySettingKey.GeminiPromptMode }.enabled shouldBe true
        geminiEnabledSettings.single { it.key == NovelReaderDisplaySettingKey.GeminiPromptMode }.visible shouldBe true
        geminiEnabledSettings.single { it.key == NovelReaderDisplaySettingKey.GoogleTranslateEnabled }.enabled shouldBe
            false
        geminiEnabledSettings.single { it.key == NovelReaderDisplaySettingKey.GoogleTranslateEnabled }.subtitle shouldBe
            "Google subtitle"

        googleEnabledSettings.single { it.key == NovelReaderDisplaySettingKey.GeminiEnabled }.enabled shouldBe false
        googleEnabledSettings.single { it.key == NovelReaderDisplaySettingKey.GeminiPromptMode }.enabled shouldBe false
        googleEnabledSettings.single { it.key == NovelReaderDisplaySettingKey.GeminiPromptMode }.visible shouldBe false
        googleEnabledSettings.single { it.key == NovelReaderDisplaySettingKey.GoogleTranslateEnabled }.enabled shouldBe
            true
    }
}
