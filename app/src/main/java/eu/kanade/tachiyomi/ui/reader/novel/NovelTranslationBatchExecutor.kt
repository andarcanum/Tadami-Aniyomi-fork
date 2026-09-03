package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.domain.items.novelchapter.model.toSNovelChapter
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.replace.applyReplaceRulesToHtml
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NOVEL_TRANSLATION_EXTRACTOR_VERSION
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.effectiveTranslationBatchSize
import eu.kanade.tachiyomi.ui.reader.novel.translation.formatAiTranslationThrowableForLog
import eu.kanade.tachiyomi.ui.reader.novel.translation.hasConfiguredTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.toDeepSeekTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toGeminiTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toMistralTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toNvidiaTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOllamaCloudTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOpenRouterTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toTranslationCacheRequirements
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationCacheModelId
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationConcurrencyLimit
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationPromptModifiersFingerprint
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationRequestConfigLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Dispatches translation batches and next-chapter prefetch to the service of the configured
 * provider.
 *
 * Owns the provider translation services and the next-chapter gemini prefetch state (trigger flag,
 * job, reusable-cache checks) so [NovelReaderScreenModel] stays focused on reader orchestration.
 */
internal interface NovelTranslationBatchHost {
    fun batchReaderSettings(): NovelReaderSettings?

    fun batchCurrentNovel(): Novel?

    fun batchCurrentChapter(): NovelChapter?

    fun batchSourceManager(): NovelSourceManager

    fun batchFindNextChapter(chapter: NovelChapter): NovelChapter?

    fun batchCoroutineScope(): CoroutineScope

    fun batchCacheReadChapters(): Boolean

    fun batchAddAiTranslationLog(message: String)
}

internal class NovelTranslationBatchExecutor(
    private val host: NovelTranslationBatchHost,
    private val geminiTranslationService: GeminiTranslationService,
    private val openRouterTranslationService: OpenRouterTranslationService,
    private val deepSeekTranslationService: DeepSeekTranslationService,
    private val mistralTranslationService: MistralTranslationService,
    private val nvidiaTranslationService: NvidiaTranslationService,
    private val ollamaCloudTranslationService: OllamaCloudTranslationService,
    /** Applies user text-replacement rules to sanitized chapter markup. */
    private val replaceTextHtml: (String) -> String = { it },
) {
    private var hasTriggeredNextChapterGeminiPrefetch: Boolean = false
    private var nextChapterGeminiPrefetchJob: Job? = null

    /** Resets the one-shot prefetch trigger when a chapter is restored. */
    fun resetNextChapterGeminiPrefetchTriggered() {
        hasTriggeredNextChapterGeminiPrefetch = false
    }

    /** Cancels the running next-chapter prefetch job (e.g. on dispose). */
    fun cancelNextChapterGeminiPrefetchJob() {
        nextChapterGeminiPrefetchJob?.cancel()
    }

    /** Clears all chapter-scoped prefetch state: job, trigger flag. */
    fun clearChapterScopedPrefetchState() {
        nextChapterGeminiPrefetchJob?.cancel()
        nextChapterGeminiPrefetchJob = null
        hasTriggeredNextChapterGeminiPrefetch = false
    }

    fun maybePrefetchNextChapterGeminiTranslationOnProgress(
        currentIndex: Int,
        totalItems: Int,
    ) {
        if (hasTriggeredNextChapterGeminiPrefetch) return
        if (!hasReachedGeminiNextChapterTranslationPrefetchThreshold(currentIndex, totalItems)) return
        val settings = host.batchReaderSettings() ?: return
        if (!settings.geminiEnabled || !settings.geminiPrefetchNextChapterTranslation) return
        if (settings.geminiDisableCache) return
        if (!settings.hasConfiguredTranslationProvider()) return
        val novel = host.batchCurrentNovel() ?: return
        val chapter = host.batchCurrentChapter() ?: return
        val source = host.batchSourceManager().get(novel.source) ?: return
        val nextChapter = host.batchFindNextChapter(chapter) ?: return
        if (hasReusableTranslationCache(nextChapter.id, settings)) return
        hasTriggeredNextChapterGeminiPrefetch = true
        scheduleNextChapterGeminiTranslationPrefetch(
            nextChapter = nextChapter,
            source = source,
            settings = settings,
        )
    }

    private fun scheduleNextChapterGeminiTranslationPrefetch(
        nextChapter: NovelChapter,
        source: NovelSource,
        settings: NovelReaderSettings,
    ) {
        if (hasReusableTranslationCache(nextChapter.id, settings)) return
        nextChapterGeminiPrefetchJob?.cancel()
        nextChapterGeminiPrefetchJob = host.batchCoroutineScope().launch(Dispatchers.IO) {
            runCatching {
                if (hasReusableTranslationCache(nextChapter.id, settings)) return@runCatching
                val cacheReadChapters = host.batchCacheReadChapters()
                val nextHtml = NovelReaderChapterPrefetchCache.get(nextChapter.id)
                    ?: source.getChapterText(nextChapter.toSNovelChapter()).also { fetchedHtml ->
                        NovelReaderChapterPrefetchCache.put(nextChapter.id, fetchedHtml)
                        if (cacheReadChapters) {
                            NovelReaderChapterDiskCacheStore.put(nextChapter.id, fetchedHtml)
                        }
                    }
                if (nextHtml.isBlank()) return@runCatching
                val normalizedNextHtml = prependChapterHeadingIfMissing(
                    rawHtml = nextHtml.normalizeStructuredChapterPayload(),
                    chapterName = nextChapter.name,
                )
                val sanitizedNextHtml = replaceTextHtml(
                    sanitizeChapterHtmlForReader(normalizedNextHtml).ifBlank { normalizedNextHtml },
                )
                val nextTextBlocks = extractTextBlocks(sanitizedNextHtml)
                if (nextTextBlocks.isEmpty()) return@runCatching
                val chunkSize = settings.effectiveTranslationBatchSize()
                val chunks = nextTextBlocks.chunked(chunkSize)
                val semaphore = Semaphore(settings.translationConcurrencyLimit())
                val translated = ConcurrentHashMap<Int, String>()
                host.batchAddAiTranslationLog("🌐 ${settings.translationRequestConfigLog()} (prefetch)")
                coroutineScope {
                    chunks.mapIndexed { chunkIndex, chunk ->
                        async {
                            semaphore.withPermit {
                                val result = requestTranslationBatch(
                                    segments = chunk,
                                    settings = settings,
                                ) { message ->
                                    host.batchAddAiTranslationLog("?? Next chapter: $message")
                                }
                                if (result == null && !settings.geminiRelaxedMode) {
                                    throw IllegalStateException(
                                        "${settings.translationProvider} returned empty response for prefetched chunk ${chunkIndex + 1}",
                                    )
                                }
                                result.orEmpty().forEachIndexed { localIndex, text ->
                                    if (!text.isNullOrBlank()) {
                                        val globalIndex = chunkIndex * chunkSize + localIndex
                                        translated[globalIndex] = text
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }
                if (translated.isEmpty()) return@runCatching
                NovelReaderTranslationDiskCacheStore.put(
                    GeminiTranslationCacheEntry(
                        chapterId = nextChapter.id,
                        translatedByIndex = translated.toMap(),
                        provider = settings.translationProvider,
                        model = settings.translationCacheModelId(),
                        sourceLang = settings.geminiSourceLang,
                        targetLang = settings.geminiTargetLang,
                        promptMode = settings.geminiPromptMode,
                        stylePreset = settings.geminiStylePreset,
                        extractorVersion = NOVEL_TRANSLATION_EXTRACTOR_VERSION,
                        promptModifiersFingerprint = settings.translationPromptModifiersFingerprint(),
                        sourceSegmentCount = nextTextBlocks.size,
                    ),
                )
                host.batchAddAiTranslationLog(
                    "?? Cached ${settings.translationProvider} translation for next chapter ${nextChapter.id}",
                )
            }.onFailure { error ->
                logcat(LogPriority.WARN, error) { "Failed to prefetch AI translation for next chapter" }
                host.batchAddAiTranslationLog(
                    "?? Next chapter prefetch failed: ${formatAiTranslationThrowableForLog(error)}",
                )
            }
        }
    }

    private fun hasReusableTranslationCache(
        chapterId: Long,
        settings: NovelReaderSettings,
    ): Boolean {
        return NovelReaderTranslationDiskCacheStore.has(
            chapterId = chapterId,
            requirements = settings.toTranslationCacheRequirements(),
        )
    }

    suspend fun requestTranslationBatch(
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
}
