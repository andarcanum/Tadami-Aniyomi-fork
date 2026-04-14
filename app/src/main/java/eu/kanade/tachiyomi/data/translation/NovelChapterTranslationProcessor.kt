package eu.kanade.tachiyomi.data.translation

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.AirforceTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.AirforceTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptModifiers
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationPromptFamily
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationStylePresets
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveNovelTranslationPromptFamily
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationCacheModelId
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
import java.util.Locale

class NovelChapterTranslationProcessor(
    private val application: Application = Injekt.get(),
    private val geminiTranslationService: GeminiTranslationService = run {
        val networkHelper = Injekt.get<NetworkHelper>()
        val json = Injekt.get<Json>()
        GeminiTranslationService(
            client = networkHelper.client,
            json = json,
            promptResolver = GeminiPromptResolver(application),
        )
    },
    private val airforceTranslationService: AirforceTranslationService = run {
        val networkHelper = Injekt.get<NetworkHelper>()
        val json = Injekt.get<Json>()
        AirforceTranslationService(
            client = networkHelper.client.newBuilder()
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
            json = json,
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

        val translated = mutableMapOf<Int, String>()
        val indexedBlocks = segments.mapIndexed { index, text -> index to text }
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
                            val result = requestTranslationBatch(
                                segments = chunk.map { it.second },
                                settings = settings,
                                onLog = onLog,
                            )
                            if (result == null && strictOnNull) {
                                throw IllegalStateException(
                                    "${settings.translationProvider} returned an empty response for chunk ${chunkIndex + 1}",
                                )
                            }

                            updateMutex.withLock {
                                if (result != null) {
                                    result.forEachIndexed { localIndex, text ->
                                        val originalIndex = chunk.getOrNull(localIndex)?.first ?: return@forEachIndexed
                                        if (!text.isNullOrBlank()) {
                                            translated[originalIndex] = text
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
                    segments = indexedBlocks.map { it.second },
                    settings = settings,
                    onLog = onLog,
                )
                val singleSuccess = singleResult?.any { !it.isNullOrBlank() } == true
                if (singleSuccess) {
                    singleResult.orEmpty().forEachIndexed { index, text ->
                        if (!text.isNullOrBlank()) {
                            translated[index] = text
                        }
                    }
                    onProgress?.invoke(100)
                } else {
                    onLog?.invoke(
                        "Single chapter request failed, falling back to chunked mode " +
                            "(batch=$PRIVATE_FALLBACK_CHUNK_SIZE, concurrency=$PRIVATE_FALLBACK_CONCURRENCY)",
                    )
                    val fallbackChunks = indexedBlocks.chunked(PRIVATE_FALLBACK_CHUNK_SIZE)
                    runChunkedTranslation(
                        chunks = fallbackChunks,
                        chunkSize = PRIVATE_FALLBACK_CHUNK_SIZE,
                        concurrencyLimit = PRIVATE_FALLBACK_CONCURRENCY,
                        strictOnNull = !settings.geminiRelaxedMode,
                    )
                }
            } else {
                val chunkSize = settings.geminiBatchSize.coerceIn(1, 80)
                val chunks = indexedBlocks.chunked(chunkSize)
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
            throw IllegalStateException("Translation service returned no translated blocks")
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
            NovelTranslationProvider.AIRFORCE -> {
                airforceTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toAirforceTranslationParams(),
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
        }
    }
}

private fun NovelReaderSettings.translationPromptFamily(): NovelTranslationPromptFamily {
    return when (translationProvider) {
        NovelTranslationProvider.GEMINI_PRIVATE,
        NovelTranslationProvider.AIRFORCE,
        -> NovelTranslationPromptFamily.RUSSIAN
        NovelTranslationProvider.GEMINI,
        NovelTranslationProvider.OPENROUTER,
        NovelTranslationProvider.DEEPSEEK,
        -> resolveNovelTranslationPromptFamily(geminiTargetLang)
    }
}

private fun NovelReaderSettings.resolveTranslationPromptModifiers(
    family: NovelTranslationPromptFamily = NovelTranslationPromptFamily.RUSSIAN,
): String {
    val modifierText = GeminiPromptModifiers.buildPromptText(
        enabledIds = geminiEnabledPromptModifiers,
        customModifier = geminiCustomPromptModifier,
        family = family,
    )
    val styleDirective = NovelTranslationStylePresets.promptDirective(
        geminiStylePreset,
        family = family,
    ).trim()
    return listOf(
        styleDirective,
        modifierText,
        geminiPromptModifiers.trim(),
    ).filter { it.isNotBlank() }
        .joinToString("\n\n")
}

private fun NovelReaderSettings.toGeminiTranslationParams(): GeminiTranslationParams {
    return GeminiTranslationParams(
        apiKey = geminiApiKey,
        model = geminiModel.normalizeGeminiModelId(),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        reasoningEffort = geminiReasoningEffort,
        budgetTokens = geminiBudgetTokens,
        temperature = geminiTemperature,
        topP = geminiTopP,
        topK = geminiTopK,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        provider = translationProvider,
        privateUnlocked = geminiPrivateUnlocked,
        privatePythonLikeMode = geminiPrivatePythonLikeMode,
    )
}

private fun NovelReaderSettings.toAirforceTranslationParams(): AirforceTranslationParams {
    return AirforceTranslationParams(
        baseUrl = airforceBaseUrl,
        apiKey = airforceApiKey,
        model = airforceModel,
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(),
        temperature = geminiTemperature,
        topP = geminiTopP,
    )
}

private fun NovelReaderSettings.toOpenRouterTranslationParams(): OpenRouterTranslationParams {
    return OpenRouterTranslationParams(
        baseUrl = openRouterBaseUrl,
        apiKey = openRouterApiKey,
        model = openRouterModel,
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature,
        topP = geminiTopP,
    )
}

private fun NovelReaderSettings.toDeepSeekTranslationParams(): DeepSeekTranslationParams {
    return DeepSeekTranslationParams(
        baseUrl = deepSeekBaseUrl,
        apiKey = deepSeekApiKey,
        model = deepSeekModel,
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature.coerceIn(DEEPSEEK_TEMPERATURE_MIN, DEEPSEEK_TEMPERATURE_MAX),
        topP = geminiTopP.coerceIn(DEEPSEEK_TOP_P_MIN, DEEPSEEK_TOP_P_MAX),
        presencePenalty = DEEPSEEK_DEFAULT_PRESENCE_PENALTY,
        frequencyPenalty = DEEPSEEK_DEFAULT_FREQUENCY_PENALTY,
    )
}

private fun NovelReaderSettings.hasConfiguredTranslationProvider(): Boolean {
    if (!geminiEnabled) return false
    return when (translationProvider) {
        NovelTranslationProvider.GEMINI -> geminiApiKey.isNotBlank()
        NovelTranslationProvider.GEMINI_PRIVATE -> {
            geminiApiKey.isNotBlank() && isPrivateBridgeUnlocked()
        }
        NovelTranslationProvider.AIRFORCE -> {
            airforceBaseUrl.isNotBlank() && airforceApiKey.isNotBlank() && airforceModel.isNotBlank()
        }
        NovelTranslationProvider.OPENROUTER -> {
            openRouterBaseUrl.isNotBlank() &&
                openRouterApiKey.isNotBlank() &&
                openRouterModel.trim().endsWith(":free", ignoreCase = true)
        }
        NovelTranslationProvider.DEEPSEEK -> {
            deepSeekBaseUrl.isNotBlank() && deepSeekApiKey.isNotBlank() && deepSeekModel.isNotBlank()
        }
    }
}

private fun NovelReaderSettings.translationConcurrencyLimit(): Int {
    return when (translationProvider) {
        NovelTranslationProvider.GEMINI -> geminiConcurrency.coerceIn(1, 8)
        NovelTranslationProvider.GEMINI_PRIVATE -> {
            if (shouldUseSinglePrivateChapterRequestMode()) 1 else geminiConcurrency.coerceIn(1, 8)
        }
        NovelTranslationProvider.AIRFORCE -> 1
        NovelTranslationProvider.OPENROUTER -> 1
        NovelTranslationProvider.DEEPSEEK -> geminiConcurrency.coerceIn(1, MAX_DEEPSEEK_CONCURRENCY)
    }
}

private fun NovelReaderSettings.shouldUseSinglePrivateChapterRequestMode(): Boolean {
    return translationProvider == NovelTranslationProvider.GEMINI_PRIVATE &&
        GeminiPrivateBridge.isInstalled() &&
        (GeminiPrivateBridge.forceSingleChapterRequest() || geminiPrivatePythonLikeMode)
}

private fun NovelReaderSettings.requiresPrivateBridgeUnlock(): Boolean {
    return translationProvider == NovelTranslationProvider.GEMINI_PRIVATE &&
        GeminiPrivateBridge.isInstalled()
}

private fun NovelReaderSettings.isPrivateBridgeUnlocked(): Boolean {
    if (!requiresPrivateBridgeUnlock()) return true
    return geminiPrivateUnlocked || GeminiPrivateBridge.isUnlocked()
}

private fun NovelReaderSettings.translationRequestConfigLog(): String {
    val common = buildString {
        append("provider=").append(translationProvider.name)
        append(", model=").append(translationCacheModelId())
        append(", lang=").append(geminiSourceLang).append("->").append(geminiTargetLang)
        append(", prompt=").append(geminiPromptMode.name)
        append(", style=").append(geminiStylePreset.name)
        if (shouldUseSinglePrivateChapterRequestMode()) {
            append(", batch=chapter")
            append(", concurrency=1")
        } else {
            append(", batch=").append(geminiBatchSize.coerceIn(1, 80))
            append(", concurrency=").append(translationConcurrencyLimit())
        }
        append(", relaxed=").append(geminiRelaxedMode)
        append(", cache=").append(!geminiDisableCache)
    }
    val sampling = when (translationProvider) {
        NovelTranslationProvider.GEMINI -> {
            "temp=${geminiTemperature.toLogFloat()}, topP=${geminiTopP.toLogFloat()}, topK=$geminiTopK, " +
                "reasoning=$geminiReasoningEffort, budgetTokens=$geminiBudgetTokens"
        }
        NovelTranslationProvider.GEMINI_PRIVATE -> {
            "temp=${geminiTemperature.toLogFloat()}, topP=${geminiTopP.toLogFloat()}, topK=$geminiTopK, " +
                "reasoning=$geminiReasoningEffort, budgetTokens=$geminiBudgetTokens, " +
                "singleRequest=${shouldUseSinglePrivateChapterRequestMode()}, " +
                "pythonLike=$geminiPrivatePythonLikeMode, " +
                "bridgeInstalled=${GeminiPrivateBridge.isInstalled()}, bridgeUnlocked=${isPrivateBridgeUnlocked()}"
        }
        NovelTranslationProvider.AIRFORCE -> {
            "baseUrl=${airforceBaseUrl.trim()}, temp=${geminiTemperature.toLogFloat()}, topP=${geminiTopP.toLogFloat()}"
        }
        NovelTranslationProvider.OPENROUTER -> {
            val isFreeModel = openRouterModel.trim().endsWith(":free", ignoreCase = true)
            "baseUrl=${openRouterBaseUrl.trim()}, temp=${geminiTemperature.toLogFloat()}, " +
                "topP=${geminiTopP.toLogFloat()}, freeModel=$isFreeModel"
        }
        NovelTranslationProvider.DEEPSEEK -> {
            val presencePenalty = DEEPSEEK_DEFAULT_PRESENCE_PENALTY.toLogFloat()
            val frequencyPenalty = DEEPSEEK_DEFAULT_FREQUENCY_PENALTY.toLogFloat()
            "baseUrl=${deepSeekBaseUrl.trim()}, temp=${geminiTemperature.toLogFloat()}, " +
                "topP=${geminiTopP.toLogFloat()}, " +
                "presencePenalty=$presencePenalty, frequencyPenalty=$frequencyPenalty, " +
                "stream=false"
        }
    }
    return "$common, $sampling"
}

private fun Float.toLogFloat(): String = String.format(Locale.US, "%.3f", this)

private fun String.normalizeGeminiModelId(): String {
    return when (trim()) {
        "gemini-3-flash" -> "gemini-3-flash-preview"
        "gemini-2.5-flash" -> "gemini-3.1-flash-lite-preview"
        else -> this
    }
}

private const val MAX_DEEPSEEK_CONCURRENCY = 32
private const val PRIVATE_FALLBACK_CHUNK_SIZE = 40
private const val PRIVATE_FALLBACK_CONCURRENCY = 1
private const val DEEPSEEK_TEMPERATURE_MIN = 1.3f
private const val DEEPSEEK_TEMPERATURE_MAX = 1.5f
private const val DEEPSEEK_TOP_P_MIN = 0.9f
private const val DEEPSEEK_TOP_P_MAX = 0.95f
private const val DEEPSEEK_DEFAULT_PRESENCE_PENALTY = 0.15f
private const val DEEPSEEK_DEFAULT_FREQUENCY_PENALTY = 0.15f
