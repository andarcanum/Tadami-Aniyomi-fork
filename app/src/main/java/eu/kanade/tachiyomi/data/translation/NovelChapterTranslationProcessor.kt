package eu.kanade.tachiyomi.data.translation

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.effectiveTranslationBatchSize
import eu.kanade.tachiyomi.ui.reader.novel.translation.hasConfiguredTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.shouldUseSinglePrivateChapterRequestMode
import eu.kanade.tachiyomi.ui.reader.novel.translation.supportsAdultPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.translation.toDeepSeekTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toGeminiTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toMistralTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toNvidiaTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOllamaCloudTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOpenRouterTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationCacheNamespace
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationConcurrencyLimit
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationRequestConfigLog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

class NovelChapterTranslationProcessor(
    private val application: Application = Injekt.get(),
    private val geminiTranslationService: GeminiTranslationService = run {
        val networkHelper = Injekt.get<NetworkHelper>()
        val json = Injekt.get<Json>()
        GeminiTranslationService(
            client = networkHelper.client.newBuilder()
                .callTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
            json = json,
            promptResolver = GeminiPromptResolver(application),
        )
    },
    private val openRouterTranslationService: OpenRouterTranslationService = run {
        val networkHelper = Injekt.get<NetworkHelper>()
        val json = Injekt.get<Json>()
        OpenRouterTranslationService(
            client = networkHelper.client.newBuilder()
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
            json = json,
        )
    },
    private val deepSeekTranslationService: DeepSeekTranslationService = run {
        val networkHelper = Injekt.get<NetworkHelper>()
        val json = Injekt.get<Json>()
        DeepSeekTranslationService(
            client = networkHelper.client.newBuilder()
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
            json = json,
            resolveSystemPrompt = { mode, family ->
                DeepSeekPromptResolver(application).resolveSystemPrompt(mode, family)
            },
        )
    },
    private val mistralTranslationService: MistralTranslationService = run {
        val networkHelper = Injekt.get<NetworkHelper>()
        val json = Injekt.get<Json>()
        MistralTranslationService(
            client = networkHelper.client.newBuilder()
                .callTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
            json = json,
            resolveSystemPrompt = { mode, family ->
                MistralPromptResolver(application).resolveSystemPrompt(mode, family)
            },
        )
    },
    private val nvidiaTranslationService: NvidiaTranslationService = run {
        val networkHelper = Injekt.get<NetworkHelper>()
        val json = Injekt.get<Json>()
        NvidiaTranslationService(
            client = networkHelper.client.newBuilder()
                .callTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
            json = json,
        )
    },
    private val ollamaCloudTranslationService: OllamaCloudTranslationService = run {
        val networkHelper = Injekt.get<NetworkHelper>()
        val json = Injekt.get<Json>()
        OllamaCloudTranslationService(
            client = networkHelper.client.newBuilder()
                .callTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
            json = json,
        )
    },
) {

    suspend fun translateSegments(
        segments: List<String>,
        settings: NovelReaderSettings,
        onLog: ((String) -> Unit)? = null,
        onProgress: ((Int) -> Unit)? = null,
    ): Map<Int, String> {
        if (segments.isEmpty()) return emptyMap()
        if (!settings.geminiEnabled) {
            throw IllegalStateException("Translation is disabled")
        }
        if (!settings.hasConfiguredTranslationProvider()) {
            throw IllegalStateException("Translation provider is not configured")
        }

        onLog?.invoke(settings.translationRequestConfigLog())

        // Every prompt-shaping setting is part of the cache identity: the old (text, targetLang)
        // key kept serving (and re-stamping into the disk cache) translations produced by a
        // different provider/model/prompt/style after the user switched settings.
        val cacheNamespace = settings.translationCacheNamespace()
        if (settings.geminiPromptMode == GeminiPromptMode.ADULT_18 &&
            !settings.translationProvider.supportsAdultPromptMode()
        ) {
            // Honest fallback: the provider has no 18+ prompt asset and translates with CLASSIC.
            onLog?.invoke(
                "Prompt mode ADULT_18 is not supported by ${settings.translationProvider.name}; " +
                    "the CLASSIC system prompt is used instead.",
            )
        }
        val translated = mutableMapOf<Int, String>()
        val indexedBlocks = segments.mapIndexed { index, text -> index to text }
        val nonCachedSegments = mutableListOf<Pair<Int, String>>()

        indexedBlocks.forEach { (index, text) ->
            val cachedTranslation = segmentTranslationCache[SegmentCacheKey(cacheNamespace, text)]
            if (!cachedTranslation.isNullOrBlank()) {
                translated[index] = cachedTranslation
            } else {
                nonCachedSegments.add(index to text)
            }
        }

        if (nonCachedSegments.isEmpty()) {
            onLog?.invoke("All segments successfully loaded from in-memory cache!")
            onProgress?.invoke(100)
            return translated.toMap()
        }

        val updateMutex = Mutex()

        suspend fun runChunkedTranslation(
            chunks: List<List<Pair<Int, String>>>,
            chunkSize: Int,
            concurrencyLimit: Int,
            strictOnNull: Boolean,
        ) {
            if (chunks.isEmpty()) return
            val semaphore = Semaphore(concurrencyLimit)
            var completedChunks = 0
            onLog?.invoke(
                "Split chapter into ${chunks.size} chunks (batch=$chunkSize, concurrency=$concurrencyLimit)",
            )

            coroutineScope {
                chunks.mapIndexed { chunkIndex, chunk ->
                    async {
                        semaphore.withPermit {
                            onLog?.invoke("Requesting chunk ${chunkIndex + 1}/${chunks.size}")
                            val primaryResult = requestTranslationBatch(
                                segments = chunk.map { it.second },
                                settings = settings,
                                onLog = onLog,
                            )
                            val result = recoverIncompleteChunk(
                                provider = settings.translationProvider,
                                chunk = chunk,
                                result = primaryResult,
                                settings = settings,
                                onLog = onLog,
                            )
                            if (result == null && strictOnNull) {
                                throw IllegalStateException(
                                    "${settings.translationProvider} returned an empty response for chunk ${chunkIndex + 1}",
                                )
                            }
                            if (result == null) {
                                onLog?.invoke(
                                    "[${settings.translationProvider}] Chunk ${chunkIndex + 1}/${chunks.size} returned empty response (skipped)",
                                )
                            }

                            updateMutex.withLock {
                                if (result != null) {
                                    result.forEachIndexed { localIndex, text ->
                                        val pair = chunk.getOrNull(localIndex) ?: return@forEachIndexed
                                        val originalIndex = pair.first
                                        val originalText = pair.second
                                        if (!text.isNullOrBlank()) {
                                            translated[originalIndex] = text
                                            segmentTranslationCache[SegmentCacheKey(cacheNamespace, originalText)] =
                                                text
                                        }
                                    }
                                }
                                completedChunks += 1
                                val progress = ((completedChunks.toFloat() / chunks.size.toFloat()) * 100f)
                                    .toInt()
                                    .coerceIn(0, 100)
                                onProgress?.invoke(progress)
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        try {
            if (settings.shouldUseSinglePrivateChapterRequestMode()) {
                onLog?.invoke("Private Gemini mode: requesting the whole chapter at once")
                val singleResult = requestTranslationBatch(
                    segments = nonCachedSegments.map { it.second },
                    settings = settings,
                    onLog = onLog,
                )
                val singleSuccess = singleResult?.any { !it.isNullOrBlank() } == true
                if (singleSuccess) {
                    singleResult.orEmpty().forEachIndexed { index, text ->
                        if (!text.isNullOrBlank()) {
                            val pair = nonCachedSegments.getOrNull(index) ?: return@forEachIndexed
                            val originalIndex = pair.first
                            val originalText = pair.second
                            translated[originalIndex] = text
                            segmentTranslationCache[SegmentCacheKey(cacheNamespace, originalText)] = text
                        }
                    }
                    onProgress?.invoke(100)
                } else {
                    onLog?.invoke(
                        "Single chapter request failed, falling back to chunked mode " +
                            "(batch=$PRIVATE_FALLBACK_CHUNK_SIZE, concurrency=$PRIVATE_FALLBACK_CONCURRENCY)",
                    )
                    val fallbackChunks = nonCachedSegments.chunked(PRIVATE_FALLBACK_CHUNK_SIZE)
                    runChunkedTranslation(
                        chunks = fallbackChunks,
                        chunkSize = PRIVATE_FALLBACK_CHUNK_SIZE,
                        concurrencyLimit = PRIVATE_FALLBACK_CONCURRENCY,
                        strictOnNull = !settings.geminiRelaxedMode,
                    )
                }
            } else {
                val chunkSize = settings.effectiveTranslationBatchSize()
                val chunks = nonCachedSegments.chunked(chunkSize)
                runChunkedTranslation(
                    chunks = chunks,
                    chunkSize = chunkSize,
                    concurrencyLimit = settings.translationConcurrencyLimit(),
                    strictOnNull = !settings.geminiRelaxedMode,
                )
            }
        } catch (error: Exception) {
            logcat(LogPriority.WARN, error) { "Background chapter translation failed" }
            throw error
        }

        if (translated.isEmpty()) {
            val providerName = settings.translationProvider.name
            throw IllegalStateException(
                "$providerName returned no translated blocks. Check model, API key, and quota in logs.",
            )
        }

        return translated.toMap()
    }

    private suspend fun requestTranslationBatch(
        segments: List<String>,
        settings: NovelReaderSettings,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        return when (settings.translationProvider) {
            NovelTranslationProvider.GEMINI -> {
                geminiTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toGeminiTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.GEMINI_PRIVATE -> {
                geminiTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toGeminiTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.OPENROUTER -> {
                openRouterTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toOpenRouterTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.DEEPSEEK -> {
                deepSeekTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toDeepSeekTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.MISTRAL -> {
                mistralTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toMistralTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.NVIDIA -> {
                nvidiaTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toNvidiaTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.OLLAMA_CLOUD -> {
                ollamaCloudTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toOllamaCloudTranslationParams(),
                    onLog = onLog,
                )
            }
        }
    }

    private suspend fun recoverIncompleteChunk(
        provider: NovelTranslationProvider,
        chunk: List<Pair<Int, String>>,
        result: List<String?>?,
        settings: NovelReaderSettings,
        onLog: ((String) -> Unit)?,
    ): List<String?>? {
        if (!provider.supportsGranularFallback()) return result
        if (chunk.isEmpty()) return result

        val recovered = MutableList<String?>(chunk.size) { index ->
            result?.getOrNull(index)
        }
        val missingIndexes = chunk.indices.filter { index ->
            recovered.getOrNull(index).isNullOrBlank()
        }
        if (missingIndexes.isEmpty()) return recovered

        if (result == null || result.all { it.isNullOrBlank() }) {
            onLog?.invoke("[$provider] Chunk response had no translated blocks, retrying segments individually")
        } else {
            onLog?.invoke(
                "[$provider] Chunk response had missing ${missingIndexes.size}/${chunk.size} translated blocks, " +
                    "retrying missing segments individually",
            )
        }

        missingIndexes.forEach { localIndex ->
            val text = chunk.getOrNull(localIndex)?.second ?: return@forEach
            val single = requestTranslationBatch(
                segments = listOf(text),
                settings = settings,
                onLog = onLog,
            )
            val translated = single?.firstOrNull()?.takeIf { !it.isNullOrBlank() }
            if (translated != null) {
                recovered[localIndex] = translated
            }
        }
        return if (recovered.any { !it.isNullOrBlank() }) recovered else result
    }

    companion object {
        private val segmentTranslationCache = ConcurrentHashMap<SegmentCacheKey, String>()

        fun clearCache() {
            segmentTranslationCache.clear()
        }
    }

    /** In-memory segment cache key: the settings namespace plus the source text. */
    private data class SegmentCacheKey(
        val namespace: String,
        val text: String,
    )
}

private fun NovelTranslationProvider.supportsGranularFallback(): Boolean {
    return this == NovelTranslationProvider.MISTRAL ||
        this == NovelTranslationProvider.NVIDIA ||
        this == NovelTranslationProvider.OLLAMA_CLOUD
}

private const val PRIVATE_FALLBACK_CHUNK_SIZE = 40
private const val PRIVATE_FALLBACK_CONCURRENCY = 1
