package eu.kanade.tachiyomi.data.translation

import android.app.Application
import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
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
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveGeminiModelCapabilities
import eu.kanade.tachiyomi.ui.reader.novel.translation.supportsAdultPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.translation.toDeepSeekTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toGeminiTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toMistralTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toNvidiaTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOllamaCloudTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOpenRouterTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationConcurrencyLimit
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationRequestConfigLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.service.TranslationChunkExecutor
import tachiyomi.domain.translation.service.TranslationFragmentPlanner
import tachiyomi.domain.translation.service.TranslationPassResult
import tachiyomi.domain.translation.service.TranslationPassState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
    private val resolveCredential: suspend (NovelTranslationProvider, Long?) -> CredentialSecret? =
        { provider, sourceId -> TranslationProfileServices.legacyCredentials.resolveLegacyOrNull(provider, sourceId) },
    private val profileRuntime: eu.kanade.tachiyomi.data.translation.profile.ProfileTranslationRuntime? = null,
) {

    /** Compatibility entry point. An incomplete map must never look like a successful chapter. */
    suspend fun translateSegments(
        segments: List<String>,
        settings: NovelReaderSettings,
        onLog: ((String) -> Unit)? = null,
        onProgress: ((Int) -> Unit)? = null,
    ): Map<Int, String> {
        if (segments.isEmpty()) return emptyMap()
        val initial = TranslationFragmentPlanner.plan(segments, maxFragmentChars(settings))
        val current = initial.associateBy { it.id }.toMutableMap()
        val result = translateFragments(initial, settings, onLog = onLog, checkpoint = { changes ->
            changes.forEach { current[it.id] = it }
            onProgress?.invoke(TranslationFragmentPlanner.progress(current.values.toList()))
        })
        val translated = TranslationFragmentPlanner.assemble(result.fragments)
        if (result.state != TranslationPassState.COMPLETE || translated.keys != segments.indices.toSet()) {
            throw IncompleteChapterTranslationException(translated, result)
        }
        return translated
    }

    suspend fun translateFragments(
        fragments: List<TranslationFragment>,
        settings: NovelReaderSettings,
        onLog: ((String) -> Unit)? = null,
        beforeRequest: suspend () -> Boolean = { true },
        checkpoint: suspend (List<TranslationFragment>) -> Unit = {},
        executor: TranslationChunkExecutor = TranslationChunkExecutor(),
        profileSnapshot: TranslationQueueProfileSnapshot? = null,
        run: TranslationRun? = null,
    ): TranslationPassResult {
        if (profileSnapshot?.snapshotVersion == 2) {
            val owner = run ?: throw TranslationAttemptException(
                TranslationFailure(
                    TranslationFailureKind.CONFIGURATION,
                    "Profile translation needs an owned durable run",
                    attempted = false,
                ),
            )
            return (profileRuntime ?: TranslationProfileServices.runtime).translateFragments(
                owner,
                profileSnapshot,
                fragments,
                beforeRequest,
                checkpoint,
            )
        }
        if (!settings.geminiEnabled || !settings.hasConfiguredTranslationProvider()) {
            throw TranslationAttemptException(
                TranslationFailure(
                    TranslationFailureKind.CONFIGURATION,
                    "Translation provider is not configured or is disabled",
                ),
            )
        }
        onLog?.invoke(settings.translationRequestConfigLog())
        if (settings.geminiPromptMode == GeminiPromptMode.ADULT_18 &&
            !settings.translationProvider.supportsAdultPromptMode()
        ) {
            onLog?.invoke("The selected provider uses the CLASSIC prompt; ADULT_18 is unavailable")
        }
        return executor.execute(
            fragments = fragments,
            maxBatchSize = settings.effectiveTranslationBatchSize(),
            maxRequestChars = maxRequestChars(settings),
            concurrency = settings.translationConcurrencyLimit(),
            beforeRequest = beforeRequest,
            request = { texts -> requestTranslationBatch(texts, settings, onLog) },
            checkpoint = checkpoint,
        )
    }

    fun maxFragmentChars(settings: NovelReaderSettings): Int = minOf(2_000, maxRequestChars(settings)).coerceAtLeast(32)

    private fun maxRequestChars(settings: NovelReaderSettings): Int {
        if (settings.translationProfile != null) {
            val frozen = (
                settings.geminiFrozenGenerationConfig?.get("maxOutputTokens") as?
                    kotlinx.serialization.json.JsonPrimitive
                )?.content?.toIntOrNull()
            val output = frozen ?: settings.translationProfile.generation.maxOutputTokens ?: 8_192
            return minOf(8_000, output / 3).coerceAtLeast(32)
        }
        val outputTokens = when (settings.translationProvider) {
            NovelTranslationProvider.GEMINI -> (
                settings.geminiFrozenGenerationConfig?.get(
                    "maxOutputTokens",
                ) as? kotlinx.serialization.json.JsonPrimitive
                )?.let {
                it.content.toIntOrNull()
            } ?: resolveGeminiModelCapabilities(settings.geminiModel).maxOutputTokens()
            NovelTranslationProvider.GEMINI_PRIVATE -> 16_384
            NovelTranslationProvider.OPENROUTER -> 16_384
            NovelTranslationProvider.DEEPSEEK -> 8_192
            else -> 8_192
        }
        // Conservative language expansion/reasoning headroom; exact tokenizer support is not assumed.
        val inputBudget = if (settings.translationProvider == NovelTranslationProvider.GEMINI) {
            (
                settings.geminiFrozenInputTokenLimit
                    ?: resolveGeminiModelCapabilities(settings.geminiModel).inputTokenLimit
                )
                ?.let { ((it - 4_096).coerceAtLeast(160) / 5) }
        } else {
            null
        }
        return minOf(8_000, outputTokens / 3, inputBudget ?: Int.MAX_VALUE).coerceAtLeast(32)
    }

    private suspend fun requestTranslationBatch(
        segments: List<String>,
        settings: NovelReaderSettings,
        onLog: ((String) -> Unit)?,
    ): List<String?>? {
        val credential = resolveCredential(settings.translationProvider, settings.translationSourceId)
        try {
            return when (settings.translationProvider) {
                NovelTranslationProvider.GEMINI,
                NovelTranslationProvider.GEMINI_PRIVATE,
                -> geminiTranslationService.translateBatch(
                    segments,
                    settings.toGeminiTranslationParams(
                        apiKey =
                        credential?.reveal() ?: settings.geminiApiKey,
                    ),
                    onLog,
                )
                NovelTranslationProvider.OPENROUTER -> openRouterTranslationService.translateBatch(
                    segments,
                    settings.toOpenRouterTranslationParams(apiKey = credential?.reveal() ?: settings.openRouterApiKey),
                    onLog,
                )
                NovelTranslationProvider.DEEPSEEK -> deepSeekTranslationService.translateBatch(
                    segments,
                    settings.toDeepSeekTranslationParams(apiKey = credential?.reveal() ?: settings.deepSeekApiKey),
                    onLog,
                )
                NovelTranslationProvider.MISTRAL -> mistralTranslationService.translateBatch(
                    segments,
                    settings.toMistralTranslationParams(apiKey = credential?.reveal() ?: settings.mistralApiKey),
                    onLog,
                )
                NovelTranslationProvider.NVIDIA -> nvidiaTranslationService.translateBatch(
                    segments,
                    settings.toNvidiaTranslationParams(apiKey = credential?.reveal() ?: settings.nvidiaApiKey),
                    onLog,
                )
                NovelTranslationProvider.OLLAMA_CLOUD -> ollamaCloudTranslationService.translateBatch(
                    segments,
                    settings.toOllamaCloudTranslationParams(
                        apiKey = credential?.reveal() ?: settings.ollamaCloudApiKey,
                    ),
                    onLog,
                )
            }
        } finally {
            credential?.close()
        }
    }

    companion object {
        /** Kept for old callers. Cross-chapter process-global text reuse was intentionally removed. */
        fun clearCache() = Unit
    }
}

class IncompleteChapterTranslationException(
    val translatedByIndex: Map<Int, String>,
    val result: TranslationPassResult,
) : IllegalStateException(result.failure?.message ?: "Chapter translation is incomplete")
