package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelReaderTtsUiState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsEngineCapabilities
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsEngineDescriptor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsVoiceDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelReaderTtsControlsTest {

    @Test
    fun `controls are hidden when tts is disabled`() {
        val snapshot = resolveNovelReaderTtsControlSnapshot(
            NovelReaderTtsUiState(enabled = false),
        )

        assertFalse(snapshot.showControls)
    }

    @Test
    fun `summary line is hidden when tts is disabled`() {
        val uiState = NovelReaderTtsUiState(
            enabled = false,
            availableEngines = listOf(
                NovelTtsEngineDescriptor(
                    packageName = "engine.default",
                    label = "Default Engine",
                    isSystemDefault = true,
                ),
            ),
            availableVoices = listOf(
                NovelTtsVoiceDescriptor(
                    id = "voice.ru",
                    name = "Anna",
                    localeTag = "ru-RU",
                ),
            ),
            selectedEnginePackage = "engine.default",
            selectedVoiceId = "voice.ru",
            selectedLocaleTag = "ru-RU",
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = true,
                supportsLocaleEnumeration = true,
            ),
        )

        val snapshot = resolveNovelReaderTtsOptionsSnapshot(uiState)

        assertNull(resolveNovelReaderTtsSummaryLine(uiState, snapshot))
    }

    @Test
    fun `playback state switches primary action to pause`() {
        val snapshot = resolveNovelReaderTtsControlSnapshot(
            NovelReaderTtsUiState(
                enabled = true,
                playbackState = NovelTtsPlaybackState.PLAYING,
            ),
        )

        assertTrue(snapshot.showControls)
        assertTrue(snapshot.primaryActionIsPause)
        assertTrue(snapshot.hasVoiceSettingsAccess)
    }

    @Test
    fun `highlight flag follows highlight mode`() {
        val snapshot = resolveNovelReaderTtsControlSnapshot(
            NovelReaderTtsUiState(
                enabled = true,
                activeHighlightMode = NovelTtsHighlightMode.ESTIMATED,
            ),
        )

        assertTrue(snapshot.highlightEnabled)
    }

    @Test
    fun `options snapshot exposes selected engine and selected voice`() {
        val snapshot = resolveNovelReaderTtsOptionsSnapshot(
            NovelReaderTtsUiState(
                enabled = true,
                availableEngines = listOf(
                    NovelTtsEngineDescriptor(
                        packageName = "engine.default",
                        label = "Default Engine",
                        isSystemDefault = true,
                    ),
                ),
                availableVoices = listOf(
                    NovelTtsVoiceDescriptor(
                        id = "voice.ru",
                        name = "Anna",
                        localeTag = "ru-RU",
                    ),
                ),
                selectedEnginePackage = "engine.default",
                selectedVoiceId = "voice.ru",
                selectedLocaleTag = "ru-RU",
                capabilities = NovelTtsEngineCapabilities(
                    supportsExactWordOffsets = false,
                    supportsReliablePauseResume = true,
                    supportsVoiceEnumeration = true,
                    supportsLocaleEnumeration = true,
                ),
            ),
        )

        assertNotNull(snapshot.selectedEngine)
        assertEquals("engine.default", snapshot.selectedEngine?.packageName)
        assertEquals("voice.ru", snapshot.selectedVoice?.id)
        assertFalse(snapshot.showLocaleFallback)
    }

    @Test
    fun `options snapshot enables locale fallback when voice enumeration is unavailable`() {
        val snapshot = resolveNovelReaderTtsOptionsSnapshot(
            NovelReaderTtsUiState(
                enabled = true,
                availableLocales = listOf("en-US", "ru-RU"),
                selectedLocaleTag = "ru-RU",
                capabilities = NovelTtsEngineCapabilities(
                    supportsExactWordOffsets = false,
                    supportsReliablePauseResume = true,
                    supportsVoiceEnumeration = false,
                    supportsLocaleEnumeration = true,
                ),
            ),
        )

        assertTrue(snapshot.showLocaleFallback)
        assertEquals("ru-RU", snapshot.selectedLocaleTag)
    }

    @Test
    fun `language picker groups voices by locale and focuses selected language`() {
        val snapshot = resolveNovelReaderTtsLanguagePickerSnapshot(
            uiState = NovelReaderTtsUiState(
                enabled = true,
                availableVoices = listOf(
                    NovelTtsVoiceDescriptor(
                        id = "voice.en",
                        name = "Amy",
                        localeTag = "en-US",
                    ),
                    NovelTtsVoiceDescriptor(
                        id = "voice.ru.1",
                        name = "ru-ru-x-ruc-local",
                        localeTag = "ru-RU",
                    ),
                    NovelTtsVoiceDescriptor(
                        id = "voice.ru.2",
                        name = "ru-ru-x-rud-local",
                        localeTag = "ru-RU",
                    ),
                ),
                selectedVoiceId = "voice.ru.1",
                selectedLocaleTag = "ru-RU",
                capabilities = NovelTtsEngineCapabilities(
                    supportsExactWordOffsets = false,
                    supportsReliablePauseResume = true,
                    supportsVoiceEnumeration = true,
                    supportsLocaleEnumeration = true,
                ),
            ),
        )

        assertEquals(listOf("en-US", "ru-RU"), snapshot.languages.map { it.localeTag })
        assertTrue(snapshot.recentLanguages.isEmpty())
        assertEquals("ru-RU", snapshot.activeLanguageTag)
        assertEquals(listOf("voice.ru.1", "voice.ru.2"), snapshot.voices.map { it.voiceId })
    }

    @Test
    fun `language picker hides raw voice ids behind friendly labels`() {
        val snapshot = resolveNovelReaderTtsLanguagePickerSnapshot(
            uiState = NovelReaderTtsUiState(
                enabled = true,
                availableVoices = listOf(
                    NovelTtsVoiceDescriptor(
                        id = "ru-ru-x-ruc-local",
                        name = "ru-ru-x-ruc-local",
                        localeTag = "ru-RU",
                    ),
                    NovelTtsVoiceDescriptor(
                        id = "anna",
                        name = "Anna",
                        localeTag = "ru-RU",
                    ),
                ),
                selectedVoiceId = "anna",
                selectedLocaleTag = "ru-RU",
                recentLanguageTags = listOf("ru-RU"),
                capabilities = NovelTtsEngineCapabilities(
                    supportsExactWordOffsets = false,
                    supportsReliablePauseResume = true,
                    supportsVoiceEnumeration = true,
                    supportsLocaleEnumeration = true,
                ),
            ),
            browsingLanguageTag = "ru-RU",
        )

        assertEquals("Voice 1", snapshot.voices.first().title)
        assertEquals("Anna", snapshot.voices.last().title)
        assertEquals("Offline", snapshot.voices.first().subtitle)
        assertNull(snapshot.voices.last().subtitle)
        assertEquals(listOf("ru-RU"), snapshot.recentLanguages.map { it.localeTag })
    }

    @Test
    fun `language picker keeps browsing language when selected voice changes`() {
        val snapshot = resolveNovelReaderTtsLanguagePickerSnapshot(
            uiState = NovelReaderTtsUiState(
                enabled = true,
                availableVoices = listOf(
                    NovelTtsVoiceDescriptor(
                        id = "voice.en",
                        name = "Amy",
                        localeTag = "en-US",
                    ),
                    NovelTtsVoiceDescriptor(
                        id = "voice.ru",
                        name = "Anna",
                        localeTag = "ru-RU",
                    ),
                ),
                selectedVoiceId = "voice.en",
                selectedLocaleTag = "en-US",
                capabilities = NovelTtsEngineCapabilities(
                    supportsExactWordOffsets = false,
                    supportsReliablePauseResume = true,
                    supportsVoiceEnumeration = true,
                    supportsLocaleEnumeration = true,
                ),
            ),
            browsingLanguageTag = "ru-RU",
        )

        assertEquals("ru-RU", snapshot.activeLanguageTag)
        assertEquals(listOf("voice.ru"), snapshot.voices.map { it.voiceId })
    }

    @Test
    fun `language search filters by human label and locale tag`() {
        val filteredByLabel = filterNovelReaderTtsLanguages(
            languages = listOf(
                NovelReaderTtsLanguageOptionSnapshot(
                    localeTag = "en-US",
                    label = "English (United States)",
                    voiceCount = 1,
                ),
                NovelReaderTtsLanguageOptionSnapshot(localeTag = "ru-RU", label = "Russian (Russia)", voiceCount = 2),
                NovelReaderTtsLanguageOptionSnapshot(localeTag = "zh-CN", label = "Chinese (China)", voiceCount = 1),
            ),
            query = "rus",
        )
        val filteredByTag = filterNovelReaderTtsLanguages(
            languages = listOf(
                NovelReaderTtsLanguageOptionSnapshot(
                    localeTag = "en-US",
                    label = "English (United States)",
                    voiceCount = 1,
                ),
                NovelReaderTtsLanguageOptionSnapshot(localeTag = "ru-RU", label = "Russian (Russia)", voiceCount = 2),
                NovelReaderTtsLanguageOptionSnapshot(localeTag = "zh-CN", label = "Chinese (China)", voiceCount = 1),
            ),
            query = "zh-cn",
        )

        assertEquals(listOf("ru-RU"), filteredByLabel.map { it.localeTag })
        assertEquals(listOf("zh-CN"), filteredByTag.map { it.localeTag })
    }

    @Test
    fun `language picker surfaces recent languages in saved order`() {
        val snapshot = resolveNovelReaderTtsLanguagePickerSnapshot(
            uiState = NovelReaderTtsUiState(
                enabled = true,
                availableVoices = listOf(
                    NovelTtsVoiceDescriptor(id = "voice.jp", name = "Hana", localeTag = "ja-JP"),
                    NovelTtsVoiceDescriptor(id = "voice.en", name = "Amy", localeTag = "en-US"),
                    NovelTtsVoiceDescriptor(id = "voice.ru", name = "Anna", localeTag = "ru-RU"),
                ),
                recentLanguageTags = listOf("ru-RU", "ja-JP", "missing"),
                selectedVoiceId = "voice.ru",
                selectedLocaleTag = "ru-RU",
                capabilities = NovelTtsEngineCapabilities(
                    supportsExactWordOffsets = false,
                    supportsReliablePauseResume = true,
                    supportsVoiceEnumeration = true,
                    supportsLocaleEnumeration = true,
                ),
            ),
        )

        assertEquals(listOf("ru-RU", "ja-JP"), snapshot.recentLanguages.map { it.localeTag })
    }
}
