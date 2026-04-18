@file:Suppress("ktlint:standard:filename")

package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class AndroidNovelTtsPlatformFactory(
    private val context: Context,
) : NovelTtsPlatformFactory {
    override fun create(): NovelTtsPlatformEngine {
        val applicationContext = runCatching { context.applicationContext }.getOrNull()
            ?: return NoOpNovelTtsPlatformEngine
        return AndroidNovelTtsPlatformEngine(applicationContext)
    }
}

private object NoOpNovelTtsPlatformEngine : NovelTtsPlatformEngine {
    override suspend fun initialize(enginePackageName: String?) = Unit

    override fun setProgressListener(listener: NovelTtsPlaybackProgressListener?) = Unit

    override fun availableVoices(): List<NovelTtsVoiceDescriptor> = emptyList()

    override fun availableLocales(): List<Locale> = emptyList()

    override fun capabilities(): NovelTtsEngineCapabilities = NovelTtsEngineCapabilities(
        supportsExactWordOffsets = false,
        supportsReliablePauseResume = false,
        supportsVoiceEnumeration = false,
        supportsLocaleEnumeration = false,
    )

    override suspend fun setVoice(voiceId: String?) = Unit

    override suspend fun setLocale(localeTag: String?) = Unit

    override suspend fun setSpeechRate(rate: Float) = Unit

    override suspend fun setPitch(pitch: Float) = Unit

    override suspend fun speak(utteranceId: String, text: String, flushQueue: Boolean) = Unit

    override fun stop() = Unit

    override fun shutdown() = Unit
}

private class AndroidNovelTtsPlatformEngine(
    private val context: Context,
) : NovelTtsPlatformEngine {
    private var tts: TextToSpeech? = null
    private var progressListener: NovelTtsPlaybackProgressListener? = null
    private var initializedEnginePackage: String? = null

    override suspend fun initialize(enginePackageName: String?) {
        val normalizedEnginePackage = enginePackageName?.takeIf { it.isNotBlank() }
        if (tts != null && initializedEnginePackage == normalizedEnginePackage) return
        if (tts != null) {
            shutdown()
        }
        val initialized = suspendCancellableCoroutine<TextToSpeech?> { continuation ->
            val instance = if (normalizedEnginePackage.isNullOrBlank()) {
                TextToSpeech(context) { status ->
                    continuation.resume(if (status == TextToSpeech.SUCCESS) tts else null)
                }
            } else {
                TextToSpeech(context, { status ->
                    continuation.resume(if (status == TextToSpeech.SUCCESS) tts else null)
                }, normalizedEnginePackage)
            }
            tts = instance
        }
        initializedEnginePackage = normalizedEnginePackage
        initialized?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    progressListener?.onUtteranceStart(utteranceId)
                }

                override fun onDone(utteranceId: String) {
                    progressListener?.onUtteranceDone(utteranceId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    progressListener?.onUtteranceError(utteranceId)
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    progressListener?.onUtteranceError(utteranceId)
                }
            },
        )
    }

    override fun setProgressListener(listener: NovelTtsPlaybackProgressListener?) {
        progressListener = listener
    }

    override fun availableVoices(): List<NovelTtsVoiceDescriptor> {
        return tts?.voices
            ?.map { voice ->
                NovelTtsVoiceDescriptor(
                    id = voice.name,
                    name = voice.name,
                    localeTag = voice.locale?.toLanguageTag().orEmpty(),
                    requiresNetwork = voice.isNetworkConnectionRequired,
                    isInstalled = voice.features?.contains(NOT_INSTALLED_FEATURE) != true,
                )
            }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    override fun availableLocales(): List<Locale> {
        return tts?.voices
            ?.mapNotNull { it.locale }
            ?.distinct()
            .orEmpty()
    }

    override fun capabilities(): NovelTtsEngineCapabilities {
        val voices = tts?.voices.orEmpty()
        return NovelTtsEngineCapabilities(
            supportsExactWordOffsets = false,
            supportsReliablePauseResume = false,
            supportsVoiceEnumeration = voices.isNotEmpty(),
            supportsLocaleEnumeration = voices.isNotEmpty(),
        )
    }

    override suspend fun setVoice(voiceId: String?) {
        val targetVoice = tts?.voices?.firstOrNull { it.name == voiceId } ?: return
        tts?.voice = targetVoice
    }

    override suspend fun setLocale(localeTag: String?) {
        val locale = localeTag?.let(Locale::forLanguageTag) ?: return
        tts?.language = locale
    }

    override suspend fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    override suspend fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    override suspend fun speak(utteranceId: String, text: String, flushQueue: Boolean) {
        val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(
            text,
            queueMode,
            Bundle.EMPTY,
            utteranceId,
        )
    }

    override fun stop() {
        runCatching {
            tts?.stop()
        }
    }

    override fun shutdown() {
        runCatching {
            tts?.stop()
        }
        runCatching {
            tts?.shutdown()
        }.also {
            tts = null
            initializedEnginePackage = null
        }
    }

    private companion object {
        const val NOT_INSTALLED_FEATURE = "notInstalled"
    }
}
