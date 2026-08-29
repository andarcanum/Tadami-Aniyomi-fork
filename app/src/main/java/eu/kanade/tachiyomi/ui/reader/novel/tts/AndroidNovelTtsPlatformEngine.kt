@file:Suppress("ktlint:standard:filename")

package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
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

    override fun capabilities(): NovelTtsEngineCapabilities = NovelTtsEngineCapabilities.NONE

    override suspend fun setVoice(voiceId: String?) = Unit

    override suspend fun setLocale(localeTag: String?) = Unit

    override suspend fun setSpeechRate(rate: Float) = Unit

    override suspend fun setPitch(pitch: Float) = Unit

    override suspend fun speak(utteranceId: String, text: String, flushQueue: Boolean) = Unit

    override fun stop() = Unit

    override fun shutdown() = Unit
}

internal open class AndroidNovelTtsPlatformEngine(
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
                    if (tts === instance) tts = null
                    if (continuation.isActive) continuation.resume(null)
                    return@OnInitListener
                }
                if (status == TextToSpeech.SUCCESS && instance != null) {
                    if (continuation.isActive) continuation.resume(instance)
                } else {
                    runCatching { instance?.shutdown() }
                    // Mirror the cancellation path: without this cleanup the dead
                    // instance stays in `tts`, so a later retry of the same
                    // (default) engine hits the fast-path guard
                    // `tts != null && initializedEnginePackage == engine` and is
                    // silently skipped — novel TTS stays bricked until restart.
                    if (tts === instance) tts = null
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            localInstance = createTtsInstance(listener, normalizedEnginePackage)
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

                override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                    progressListener?.onUtteranceRangeStart(utteranceId, start, end)
                }
            },
        )
    }

    /**
     * Creation seam for [TextToSpeech]. Overridden by tests to count instance
     * creations and to simulate engine bind failures.
     */
    protected open fun createTtsInstance(
        listener: TextToSpeech.OnInitListener,
        enginePackage: String?,
    ): TextToSpeech {
        return if (enginePackage.isNullOrBlank()) {
            TextToSpeech(context, listener)
        } else {
            TextToSpeech(context, listener, enginePackage)
        }
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
                ?.sortedBy { it.toLanguageTag() }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    override fun capabilities(): NovelTtsEngineCapabilities {
        val voices = runCatching { tts?.voices.orEmpty() }.getOrDefault(emptySet())
        return NovelTtsEngineCapabilities(
            // Modern engines report per-word ranges through onRangeStart (API 26+).
            // Estimated highlighting stays active as a fallback until the first
            // range event actually arrives for an utterance.
            supportsExactWordOffsets = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
            supportsReliablePauseResume = false,
            supportsVoiceEnumeration = voices.isNotEmpty(),
            supportsLocaleEnumeration = voices.isNotEmpty(),
        )
    }

    override suspend fun setVoice(voiceId: String?) {
        val engine = tts ?: return
        val normalizedVoiceId = voiceId?.takeIf { it.isNotBlank() } ?: return
        val applied = runCatching {
            val targetVoice = engine.voices?.firstOrNull { it.name == normalizedVoiceId }
            if (targetVoice != null) {
                engine.voice = targetVoice
                true
            } else {
                // Locale-fallback descriptors use the locale tag as the voice id.
                applyLanguage(engine, Locale.forLanguageTag(normalizedVoiceId))
            }
        }.getOrDefault(false)
        if (!applied) {
            logcat(LogPriority.WARN) { "Novel TTS: unable to apply voice '$normalizedVoiceId'" }
        }
    }

    override suspend fun setLocale(localeTag: String?) {
        val engine = tts ?: return
        val locale = localeTag?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: return
        val applied = runCatching { applyLanguage(engine, locale) }.getOrDefault(false)
        if (!applied) {
            logcat(LogPriority.WARN) { "Novel TTS: language '$localeTag' is not available on this engine" }
        }
    }

    private fun applyLanguage(engine: TextToSpeech, locale: Locale): Boolean {
        if (engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) return true
        // Retry with the bare language when the exact region variant is missing.
        if (locale.country.isNotBlank()) {
            // Locale(String) is deprecated; bare-language tag is the modern equivalent.
            return engine.setLanguage(Locale.forLanguageTag(locale.language)) >=
                TextToSpeech.LANG_AVAILABLE
        }
        return false
    }

    override suspend fun setSpeechRate(rate: Float) {
        runCatching { tts?.setSpeechRate(rate) }
    }

    override suspend fun setPitch(pitch: Float) {
        runCatching { tts?.setPitch(pitch) }
    }

    override suspend fun speak(utteranceId: String, text: String, flushQueue: Boolean) {
        val engine = tts ?: run {
            progressListener?.onUtteranceError(utteranceId)
            return
        }
        val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = runCatching { engine.speak(text, queueMode, Bundle.EMPTY, utteranceId) }
            .getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.SUCCESS) {
            // Propagate enqueue failures instead of silently dropping them; the
            // session controller surfaces the error and stops the word ticker.
            progressListener?.onUtteranceError(utteranceId)
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
