package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.translation.TranslationBatchRequest
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
import eu.kanade.tachiyomi.data.translation.profile.freezeForProbe
import eu.kanade.tachiyomi.data.translation.toTranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.ui.reader.novel.replace.ReplaceRule
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.hasConfiguredTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.toDeepSeekTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toGeminiTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toMistralTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toNvidiaTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOllamaCloudTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOpenRouterTranslationParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.translation.credentials.CredentialSecret
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Reader access shared by direct connection checks and durable speculative next-chapter work. */
internal interface NovelTranslationBatchHost {
    fun batchReaderSettings(): NovelReaderSettings?
    fun batchCurrentNovel(): Novel?
    fun batchCurrentChapter(): NovelChapter?
    fun batchSourceManager(): NovelSourceManager
    fun batchFindNextChapter(chapter: NovelChapter): NovelChapter?
    fun batchCoroutineScope(): CoroutineScope
    fun batchCacheReadChapters(): Boolean
    fun batchReplaceRulesFingerprint(): String
    fun batchReplaceRules(): List<ReplaceRule> = emptyList()
    fun batchAddAiTranslationLog(message: String)
}

/** Connection checks use provider services; prefetch owns only a named, low-priority queue member. */
internal class NovelTranslationBatchExecutor(
    private val host: NovelTranslationBatchHost,
    private val geminiTranslationService: GeminiTranslationService,
    private val openRouterTranslationService: OpenRouterTranslationService,
    private val deepSeekTranslationService: DeepSeekTranslationService,
    private val mistralTranslationService: MistralTranslationService,
    private val nvidiaTranslationService: NvidiaTranslationService,
    private val ollamaCloudTranslationService: OllamaCloudTranslationService,
    private val translationQueueManager: TranslationQueueManager = Injekt.get(),
    private val resolveCredential: suspend (NovelTranslationProvider, Long?) -> CredentialSecret? =
        { provider, sourceId -> TranslationProfileServices.legacyCredentials.resolveLegacyOrNull(provider, sourceId) },
) {
    private var hasTriggeredNextChapterGeminiPrefetch = false
    private var nextChapterPrefetch: NextChapterPrefetch? = null

    private class NextChapterPrefetch(val request: TranslationBatchRequest, val sourceId: Long) {
        val admissionFinished = CompletableDeferred<Unit>()
        val cleanupScheduled = AtomicBoolean(false)
        var enqueueJob: Job? = null

        @Volatile var cleanupRequested = false

        // Protected by this record's monitor; accepting activation cannot race a started detach.
        var wanted = false
        var detaching = false
    }

    fun resetNextChapterGeminiPrefetchTriggered() {
        hasTriggeredNextChapterGeminiPrefetch = false
    }

    /** Cancels only this speculative owner, never a chapter or another batch/reader owner. */
    fun cancelNextChapterGeminiPrefetchJob() {
        val prefetch = nextChapterPrefetch ?: return
        prefetch.cleanupRequested = true
        prefetch.enqueueJob?.cancel()
        schedulePrefetchCleanup(prefetch)
    }

    fun clearChapterScopedPrefetchState() {
        cancelNextChapterGeminiPrefetchJob()
        nextChapterPrefetch = null
        hasTriggeredNextChapterGeminiPrefetch = false
    }

    /** Called before old reader fields are cleared; freezes acceptance of the incoming intent. */
    fun onChapterBecomingActive(chapterId: Long) {
        val prefetch = nextChapterPrefetch ?: return
        val novel = host.batchCurrentNovel()
        val settings = host.batchReaderSettings()
        val compatible = chapterId == prefetch.request.chapterIds.single() &&
            novel != null && novel.id == prefetch.request.novelId && novel.source == prefetch.sourceId &&
            settings != null &&
            runCatching { freezeProfile(settings) == prefetch.request.profileSnapshot }.getOrDefault(false)
        if (compatible) {
            val accepted = synchronized(prefetch) {
                if (prefetch.detaching) {
                    false
                } else {
                    prefetch.wanted = true
                    true
                }
            }
            if (accepted) {
                prefetch.cleanupRequested = true
                schedulePrefetchCleanup(prefetch)
            }
        } else {
            // An already accepted reader intent must finish joining before its named owner leaves.
            cancelNextChapterGeminiPrefetchJob()
        }
    }

    fun maybePrefetchNextChapterGeminiTranslationOnProgress(currentIndex: Int, totalItems: Int) {
        if (hasTriggeredNextChapterGeminiPrefetch) return
        if (!hasReachedGeminiNextChapterTranslationPrefetchThreshold(currentIndex, totalItems)) return
        val settings = host.batchReaderSettings() ?: return
        if (!settings.geminiEnabled || !settings.geminiPrefetchNextChapterTranslation ||
            settings.geminiDisableCache
        ) {
            return
        }
        val novel = host.batchCurrentNovel() ?: return
        if (!settings.hasConfiguredTranslationProvider(novel.id)) return
        val chapter = host.batchCurrentChapter() ?: return
        if (chapter.novelId != novel.id) return
        val next = host.batchFindNextChapter(chapter) ?: return
        if (next.novelId != novel.id || next.id == chapter.id) return
        hasTriggeredNextChapterGeminiPrefetch = true
        val snapshot = try {
            freezeProfile(settings)
        } catch (_: Exception) {
            host.batchAddAiTranslationLog("Next-chapter translation settings could not be frozen")
            return
        }
        cancelNextChapterGeminiPrefetchJob()
        val prefetch = NextChapterPrefetch(
            TranslationBatchRequest(
                novelId = novel.id,
                batchToken = "prefetch-" + UUID.randomUUID(),
                chapterIds = listOf(next.id),
                profileSnapshot = snapshot,
                forceRetranslate = false,
                priority = 0,
            ),
            sourceId = novel.source,
        )
        nextChapterPrefetch = prefetch
        // Install completion handling before dispatch, including cancellation before body entry.
        val job = host.batchCoroutineScope().launch(start = CoroutineStart.LAZY) {
            try {
                // DB admission may commit despite reader cancellation. Observe settlement first;
                // an early cancelBatch on a missing row cannot revoke a later committed member.
                withContext(NonCancellable) {
                    translationQueueManager.enqueueTranslationBatch(prefetch.request)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                prefetch.cleanupRequested = true
                if (currentCoroutineContext().isActive) {
                    host.batchAddAiTranslationLog("Next-chapter translation could not be queued")
                }
            } finally {
                val cancelled = !currentCoroutineContext().isActive
                withContext(NonCancellable) {
                    if (cancelled) prefetch.cleanupRequested = true
                    prefetch.admissionFinished.complete(Unit)
                    if (prefetch.cleanupRequested) schedulePrefetchCleanup(prefetch)
                }
            }
        }
        prefetch.enqueueJob = job
        job.invokeOnCompletion { cause ->
            prefetch.admissionFinished.complete(Unit)
            if (cause != null) prefetch.cleanupRequested = true
            if (prefetch.cleanupRequested) schedulePrefetchCleanup(prefetch)
        }
        job.start()
    }

    private fun freezeProfile(settings: NovelReaderSettings): TranslationQueueProfileSnapshot {
        val rules = host.batchReplaceRules().toList()
        return settings.toTranslationQueueProfileSnapshot(host.batchReplaceRulesFingerprint(), rules).copy(
            geminiEnabledPromptModifiers = settings.geminiEnabledPromptModifiers.toList(),
        )
    }

    private fun schedulePrefetchCleanup(prefetch: NextChapterPrefetch) {
        if (!prefetch.cleanupScheduled.compareAndSet(false, true)) return
        translationQueueManager.launchCleanup {
            withContext(NonCancellable) {
                prefetch.admissionFinished.await()
                val promote = synchronized(prefetch) {
                    if (!prefetch.wanted) prefetch.detaching = true
                    prefetch.wanted
                }
                if (promote) {
                    try {
                        // This manager seam transactionally guards the existing generation,
                        // membership, profile and nonterminal state; it never starts a rerun.
                        translationQueueManager.promotePrefetch(
                            prefetch.request.profileSnapshot,
                            prefetch.request.chapterIds.single(),
                            prefetch.request.novelId,
                            prefetch.request.batchToken,
                        )
                    } catch (_: Exception) {
                        // Do not delete the only wanted owner when an implicit join was not
                        // acknowledged. A later explicit cleanup call can retry the handoff.
                        prefetch.cleanupScheduled.set(false)
                        logcat(LogPriority.WARN) { "Unable to transfer AI prefetch ownership" }
                        return@withContext
                    }
                    synchronized(prefetch) { prefetch.detaching = true }
                }
                try {
                    translationQueueManager.cancelBatch(prefetch.request.batchToken)
                } catch (_: Exception) {
                    prefetch.cleanupScheduled.set(false)
                    logcat(LogPriority.WARN) { "Unable to release AI prefetch membership" }
                }
            }
        }
    }

    suspend fun requestTranslationBatch(
        segments: List<String>,
        settings: NovelReaderSettings,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        if (settings.connectionProfile != null && settings.translationProfile != null) {
            val snapshot = settings.toTranslationQueueProfileSnapshot().freezeForProbe()
            return TranslationProfileServices.probeTranslation(snapshot, segments)
        }
        val credential = resolveCredential(settings.translationProvider, settings.translationSourceId)
        try {
            return when (settings.translationProvider) {
                NovelTranslationProvider.GEMINI -> {
                    geminiTranslationService.translateBatch(
                        segments = segments,
                        params = settings.toGeminiTranslationParams(
                            apiKey =
                            credential?.reveal() ?: settings.geminiApiKey,
                        ),
                        onLog = onLog,
                    )
                }
                NovelTranslationProvider.GEMINI_PRIVATE -> {
                    geminiTranslationService.translateBatch(
                        segments = segments,
                        params = settings.toGeminiTranslationParams(
                            apiKey =
                            credential?.reveal() ?: settings.geminiApiKey,
                        ),
                        onLog = onLog,
                    )
                }
                NovelTranslationProvider.OPENROUTER -> {
                    openRouterTranslationService.translateBatch(
                        segments = segments,
                        params = settings.toOpenRouterTranslationParams(
                            apiKey =
                            credential?.reveal() ?: settings.openRouterApiKey,
                        ),
                        onLog = onLog,
                    )
                }
                NovelTranslationProvider.DEEPSEEK -> {
                    deepSeekTranslationService.translateBatch(
                        segments = segments,
                        params = settings.toDeepSeekTranslationParams(
                            apiKey =
                            credential?.reveal() ?: settings.deepSeekApiKey,
                        ),
                        onLog = onLog,
                    )
                }
                NovelTranslationProvider.MISTRAL -> {
                    mistralTranslationService.translateBatch(
                        segments = segments,
                        params = settings.toMistralTranslationParams(
                            apiKey = credential?.reveal() ?: settings.mistralApiKey,
                        ),
                        onLog = onLog,
                    )
                }
                NovelTranslationProvider.NVIDIA -> {
                    nvidiaTranslationService.translateBatch(
                        segments = segments,
                        params = settings.toNvidiaTranslationParams(
                            apiKey =
                            credential?.reveal() ?: settings.nvidiaApiKey,
                        ),
                        onLog = onLog,
                    )
                }
                NovelTranslationProvider.OLLAMA_CLOUD -> {
                    ollamaCloudTranslationService.translateBatch(
                        segments = segments,
                        params = settings.toOllamaCloudTranslationParams(
                            apiKey =
                            credential?.reveal() ?: settings.ollamaCloudApiKey,
                        ),
                        onLog = onLog,
                    )
                }
            }
        } finally {
            credential?.close()
        }
    }
}
