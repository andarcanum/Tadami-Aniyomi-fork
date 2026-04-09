package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsSelectionResolverTest {

    @Test
    fun `voice selection syncs locale from selected voice`() {
        val resolved = resolveNovelTtsVoiceSelection(
            availableVoices = listOf(
                NovelTtsVoiceDescriptor(
                    id = "voice.ru",
                    name = "Anna",
                    localeTag = "ru-RU",
                ),
                NovelTtsVoiceDescriptor(
                    id = "voice.en",
                    name = "Emma",
                    localeTag = "en-US",
                ),
            ),
            availableLocales = listOf("ru-RU", "en-US"),
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = true,
                supportsLocaleEnumeration = true,
            ),
            preferredVoiceId = "voice.en",
            preferredLocaleTag = "ru-RU",
        )

        resolved.selectedVoiceId shouldBe "voice.en"
        resolved.selectedLocaleTag shouldBe "en-US"
        resolved.showLocaleFallback shouldBe false
    }

    @Test
    fun `missing selected voice falls back to first voice for the engine`() {
        val resolved = resolveNovelTtsVoiceSelection(
            availableVoices = listOf(
                NovelTtsVoiceDescriptor(
                    id = "voice.jp",
                    name = "Hana",
                    localeTag = "ja-JP",
                ),
                NovelTtsVoiceDescriptor(
                    id = "voice.en",
                    name = "Emma",
                    localeTag = "en-US",
                ),
            ),
            availableLocales = listOf("ja-JP", "en-US"),
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = true,
                supportsLocaleEnumeration = true,
            ),
            preferredVoiceId = "voice.missing",
            preferredLocaleTag = "",
        )

        resolved.selectedVoiceId shouldBe "voice.jp"
        resolved.selectedLocaleTag shouldBe "ja-JP"
    }

    @Test
    fun `locale fallback keeps locale selection when voice enumeration is unavailable`() {
        val resolved = resolveNovelTtsVoiceSelection(
            availableVoices = emptyList(),
            availableLocales = listOf("ru-RU", "en-US"),
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = false,
                supportsLocaleEnumeration = true,
            ),
            preferredVoiceId = "",
            preferredLocaleTag = "ru-RU",
        )

        resolved.selectedVoiceId shouldBe ""
        resolved.selectedLocaleTag shouldBe "ru-RU"
        resolved.showLocaleFallback shouldBe true
    }
}
