@file:Suppress("ktlint:standard:filename")

package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
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
    private val generation = AtomicInteger(0)

    override suspend fun initialize(enginePackageName: String?) {
        val normalizedEnginePackage = enginePackageName?.takeIf { it.isNotBlank() }
        if (tts != null && initializedEnginePackage == normalizedEnginePackage) return
        if (tts != null) {
            shutdown()
        }
        val requestGeneration = generation.incrementAndGet()
        val initialized = suspendCancellableCoroutine<TextToSpeech?> { continuation ->
            var localInstance: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                val instance = localInstance
                if (requestGeneration != generation.get()) {
                    runCatching { instance?.shutdown() }
                    if (continuation.isActive) continuation.resume(null)
                    return@OnInitListener
                }
                if (status == TextToSpeech.SUCCESS && instance != null) {
                    if (continuation.isActive) continuation.resume(instance)
                } else {
                    runCatching { instance?.shutdown() }
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            localInstance = if (normalizedEnginePackage.isNullOrBlank()) {
                TextToSpeech(context, listener)
            } else {
                TextToSpeech(context, listener, normalizedEnginePackage)
            }
            tts = localInstance
            continuation.invokeOnCancellation {
                if (requestGeneration == generation.get()) {
                    runCatching { localInstance.shutdown() }
                    if (tts === localInstance) tts = null
                }
            }
        }
        if (initialized == null || requestGeneration != generation.get()) return
        initializedEnginePackage = normalizedEnginePackage
        initialized.setOnUtteranceProgressListener(
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
        return runCatching {
            tts?.voices
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
        }.getOrDefault(emptyList())
    }

    override fun availableLocales(): List<Locale> {
        return runCatching {
            tts?.voices
                ?.mapNotNull { it.locale }
                ?.distinct()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    override fun capabilities(): NovelTtsEngineCapabilities {
        val voices = runCatching { tts?.voices.orEmpty() }.getOrDefault(emptySet())
        return NovelTtsEngineCapabilities(
            supportsExactWordOffsets = false,
            supportsReliablePauseResume = false,
            supportsVoiceEnumeration = voices.isNotEmpty(),
            supportsLocaleEnumeration = voices.isNotEmpty(),
        )
    }

    override suspend fun setVoice(voiceId: String?) {
        runCatching {
            val targetVoice = tts?.voices?.firstOrNull { it.name == voiceId } ?: return
            tts?.voice = targetVoice
        }
    }

    override suspend fun setLocale(localeTag: String?) {
        runCatching {
            val locale = localeTag?.let(Locale::forLanguageTag) ?: return
            tts?.language = locale
        }
    }

    override suspend fun setSpeechRate(rate: Float) {
        runCatching { tts?.setSpeechRate(rate) }
    }

    override suspend fun setPitch(pitch: Float) {
        runCatching { tts?.setPitch(pitch) }
    }

    override suspend fun speak(utteranceId: String, text: String, flushQueue: Boolean) {
        runCatching {
            val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(
                text,
                queueMode,
                Bundle.EMPTY,
                utteranceId,
            )
        }
    }

    override fun stop() {
        runCatching {
            tts?.stop()
        }
    }

    override fun shutdown() {
        generation.incrementAndGet()
        val instance = tts
        runCatching {
            instance?.stop()
        }
        runCatching {
            instance?.shutdown()
        }.also {
            if (tts === instance) tts = null
            initializedEnginePackage = null
        }
    }

    private companion object {
        const val NOT_INSTALLED_FEATURE = "notInstalled"
    }
}
