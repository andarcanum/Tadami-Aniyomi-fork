package eu.kanade.tachiyomi.ui.reader.novel.tts

import java.util.Locale

interface NovelTtsPlatformFactory {
    fun create(): NovelTtsPlatformEngine
}

interface NovelTtsPlatformEngine {
    suspend fun initialize(enginePackageName: String?)

    fun setProgressListener(listener: NovelTtsPlaybackProgressListener?)

    fun availableVoices(): List<NovelTtsVoiceDescriptor>

    fun availableLocales(): List<Locale>

    fun capabilities(): NovelTtsEngineCapabilities

    suspend fun setVoice(voiceId: String?)

    suspend fun setLocale(localeTag: String?)

    suspend fun setSpeechRate(rate: Float)

    suspend fun setPitch(pitch: Float)

    suspend fun speak(utteranceId: String, text: String, flushQueue: Boolean)

    fun stop()

    fun shutdown()
}

interface NovelTtsPlaybackProgressListener {
    fun onUtteranceStart(utteranceId: String) = Unit

    fun onUtteranceDone(utteranceId: String) = Unit

    fun onUtteranceError(utteranceId: String) = Unit
}

class NovelTtsEngine(
    platformFactory: NovelTtsPlatformFactory,
) {
    private val platformEngine = platformFactory.create()
    private var initializedEnginePackage: String? = null
    private var voices: List<NovelTtsVoiceDescriptor> = emptyList()
    private var locales: List<String> = emptyList()
    private var engineCapabilities = NovelTtsEngineCapabilities(
        supportsExactWordOffsets = false,
        supportsReliablePauseResume = false,
        supportsVoiceEnumeration = false,
        supportsLocaleEnumeration = false,
    )

    suspend fun initialize(enginePackageName: String?) {
        val normalizedEnginePackage = enginePackageName?.takeIf { it.isNotBlank() }
        if (initializedEnginePackage != null && initializedEnginePackage != normalizedEnginePackage) {
            platformEngine.shutdown()
        }
        platformEngine.initialize(enginePackageName)
        initializedEnginePackage = normalizedEnginePackage
        voices = platformEngine.availableVoices().ifEmpty {
            platformEngine.availableLocales()
                .map { it.toLanguageTag() }
                .distinct()
                .map { localeTag ->
                    NovelTtsVoiceDescriptor(
                        id = localeTag,
                        name = localeTag,
                        localeTag = localeTag,
                    )
                }
        }
        locales = platformEngine.availableLocales()
            .map { it.toLanguageTag() }
            .ifEmpty { voices.map { it.localeTag } }
            .distinct()
        engineCapabilities = platformEngine.capabilities()
    }

    fun availableVoices(): List<NovelTtsVoiceDescriptor> = voices

    fun availableLocales(): List<String> = locales

    fun capabilities(): NovelTtsEngineCapabilities = engineCapabilities

    fun setProgressListener(listener: NovelTtsPlaybackProgressListener?) {
        platformEngine.setProgressListener(listener)
    }

    suspend fun setVoice(voiceId: String?) {
        platformEngine.setVoice(voiceId)
    }

    suspend fun setLocale(localeTag: String?) {
        platformEngine.setLocale(localeTag)
    }

    suspend fun setSpeechRate(rate: Float) {
        platformEngine.setSpeechRate(rate)
    }

    suspend fun setPitch(pitch: Float) {
        platformEngine.setPitch(pitch)
    }

    suspend fun speak(utteranceId: String, text: String, flushQueue: Boolean) {
        platformEngine.speak(utteranceId, text, flushQueue)
    }

    fun stop() {
        platformEngine.stop()
    }

    fun shutdown() {
        platformEngine.shutdown()
        initializedEnginePackage = null
        voices = emptyList()
        locales = emptyList()
    }
}
