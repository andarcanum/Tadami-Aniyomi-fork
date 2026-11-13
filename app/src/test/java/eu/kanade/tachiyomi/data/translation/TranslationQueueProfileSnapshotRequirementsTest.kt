package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.toTranslationCacheRequirements
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class TranslationQueueProfileSnapshotRequirementsTest {
    @Test
    fun `frozen runtime settings produce the same complete artifact requirements`() {
        val settings = NovelReaderPreferences(InMemoryPreferenceStore(), Json).resolveSettings(1).copy(
            geminiEnabled = true, translationProvider = NovelTranslationProvider.DEEPSEEK,
            geminiModel = "gemini-model", deepSeekModel = " deepseek-chat ",
            geminiSourceLang = "English", geminiTargetLang = "Russian", geminiPromptMode = GeminiPromptMode.CLASSIC,
            geminiStylePreset = NovelTranslationStylePreset.PROFESSIONAL,
            geminiEnabledPromptModifiers = listOf(
                "b",
                "a",
            ),
            geminiCustomPromptModifier = " custom ", geminiPromptModifiers = " raw ",
        )
        val snapshot = settings.toTranslationQueueProfileSnapshot("rules-fp")
        snapshot.toReaderSettings(settings).toTranslationCacheRequirements("rules-fp") shouldBe
            settings.toTranslationCacheRequirements("rules-fp")
    }
}
