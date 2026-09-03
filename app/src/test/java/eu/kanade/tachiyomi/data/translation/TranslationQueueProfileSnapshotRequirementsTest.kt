package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.toTranslationCacheRequirements
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class TranslationQueueProfileSnapshotRequirementsTest {

    @Test
    fun `snapshot requirements equal the settings requirements for the same configuration`() {
        // The batch skip-check must answer exactly the same cache question the reader asks on
        // restore; this parity is what keeps "already translated" and "shows original text" from
        // contradicting each other after a provider/model/prompt switch.
        val settings = mockk<NovelReaderSettings>(relaxed = true)
        every { settings.geminiEnabled } returns true
        every { settings.geminiDisableCache } returns false
        every { settings.translationProvider } returns NovelTranslationProvider.DEEPSEEK
        every { settings.geminiModel } returns "gemini-model"
        every { settings.deepSeekModel } returns " deepseek-chat "
        every { settings.openRouterModel } returns ""
        every { settings.mistralModel } returns ""
        every { settings.nvidiaModel } returns ""
        every { settings.ollamaCloudModel } returns ""
        every { settings.geminiSourceLang } returns "English"
        every { settings.geminiTargetLang } returns "Russian"
        every { settings.geminiPromptMode } returns GeminiPromptMode.CLASSIC
        every { settings.geminiStylePreset } returns NovelTranslationStylePreset.PROFESSIONAL
        every { settings.geminiEnabledPromptModifiers } returns listOf("b", "a")
        every { settings.geminiCustomPromptModifier } returns " custom "
        every { settings.geminiPromptModifiers } returns " raw "

        val snapshot = settings.toTranslationQueueProfileSnapshot(replaceRulesFingerprint = "rules-fp")

        snapshot.toTranslationCacheRequirements() shouldBe
            settings.toTranslationCacheRequirements(replaceRulesFingerprint = "rules-fp")
    }
}
