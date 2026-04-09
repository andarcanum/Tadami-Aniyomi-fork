package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode

data class NovelReaderTtsUiState(
    val enabled: Boolean = false,
    val playbackState: NovelTtsPlaybackState = NovelTtsPlaybackState.IDLE,
    val activeSession: NovelTtsSession? = null,
    val activeHighlightMode: NovelTtsHighlightMode = NovelTtsHighlightMode.OFF,
    val activeWordRange: NovelTtsWordRange? = null,
    val activeUtteranceText: String? = null,
    val activeSourceBlockIndex: Int? = null,
    val availableEngines: List<NovelTtsEngineDescriptor> = emptyList(),
    val availableVoices: List<NovelTtsVoiceDescriptor> = emptyList(),
    val availableLocales: List<String> = emptyList(),
    val recentLanguageTags: List<String> = emptyList(),
    val isLoadingVoices: Boolean = false,
    val selectedEnginePackage: String = "",
    val selectedVoiceId: String = "",
    val selectedLocaleTag: String = "",
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val capabilities: NovelTtsEngineCapabilities = NovelTtsEngineCapabilities(
        supportsExactWordOffsets = false,
        supportsReliablePauseResume = false,
        supportsVoiceEnumeration = false,
        supportsLocaleEnumeration = false,
    ),
    val errorMessage: String? = null,
) {
    val isPlaying: Boolean
        get() = playbackState == NovelTtsPlaybackState.PLAYING

    val canResume: Boolean
        get() = playbackState == NovelTtsPlaybackState.PAUSED && activeSession != null
}
