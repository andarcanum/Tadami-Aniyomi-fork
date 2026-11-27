package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.translation.TranslationBatchEnqueueResult
import eu.kanade.tachiyomi.data.translation.TranslationBatchRequest
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.replace.ReplaceRule
import eu.kanade.tachiyomi.ui.reader.novel.replace.replaceRulesFingerprint
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationCacheRequirements
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.repository.TranslationRunRepository
import java.io.IOException
import java.util.UUID

@Isolated
@OptIn(ExperimentalCoroutinesApi::class)
class NovelTranslationBatchExecutorTest {
    @BeforeEach
    fun mockLegacyCacheBoundary() {
        mockkObject(NovelReaderTranslationDiskCacheStore, NovelReaderChapterPrefetchCache)
        every {
            NovelReaderTranslationDiskCacheStore.has(any(), any<NovelReaderTranslationCacheRequirements>())
        } returns false
        justRun { NovelReaderTranslationDiskCacheStore.put(any()) }
        every { NovelReaderChapterPrefetchCache.get(any()) } returns null
        justRun { NovelReaderChapterPrefetchCache.put(any(), any()) }
    }

    @AfterEach
    fun releaseLegacyCacheBoundary() {
        unmockkObject(NovelReaderTranslationDiskCacheStore, NovelReaderChapterPrefetchCache)
    }

    @Test
    fun `next chapter prefetch does not fetch source call providers or publish caches directly`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()

                fixture.queue.requests.size shouldBe 1
                coVerify(exactly = 0) { fixture.source.getChapterText(any()) }
                fixture.services.assertNoProviderCalls()
                verify(exactly = 0) { NovelReaderTranslationDiskCacheStore.put(any()) }
                verify(exactly = 0) { NovelReaderChapterPrefetchCache.get(any()) }
                verify(exactly = 0) { NovelReaderChapterPrefetchCache.put(any(), any()) }
                fixture.host.sourceManagerReads shouldBe 0
                fixture.host.rawCachePolicyReads shouldBe 0
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `unverified legacy cache does not suppress durable source verification`() {
        runTest {
            val fixture = Fixture(this)
            try {
                every {
                    NovelReaderTranslationDiskCacheStore.has(any(), any<NovelReaderTranslationCacheRequirements>())
                } returns true
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()

                fixture.queue.requests.size shouldBe 1
                verify(exactly = 0) {
                    NovelReaderTranslationDiskCacheStore.has(any(), any<NovelReaderTranslationCacheRequirements>())
                }
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `prefetch owns one named priority zero member with frozen settings and replace rules`() {
        runTest {
            val fixture = Fixture(this)
            try {
                val originalRule = ReplaceRule(id = 1L, pattern = "old", replacement = "new", isRegex = false)
                val mutableRules = mutableListOf(originalRule)
                val mutableModifiers = mutableListOf("original-modifier")
                fixture.host.rules = mutableRules
                fixture.host.settings = fixture.host.settings.copy(
                    deepSeekModel = "original-model",
                    geminiEnabledPromptModifiers = mutableModifiers,
                )
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                mutableRules.clear()
                mutableRules += originalRule.copy(pattern = "changed")
                mutableModifiers += "changed-modifier"
                fixture.host.settings = fixture.host.settings.copy(deepSeekModel = "changed-model")
                runCurrent()
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(80, 100)
                runCurrent()

                val request = fixture.queue.requests.single()
                request.novelId shouldBe 901L
                request.chapterIds shouldBe listOf(904L)
                request.priority shouldBe 0
                request.forceRetranslate shouldBe false
                request.batchToken.startsWith("prefetch-") shouldBe true
                UUID.fromString(
                    request.batchToken.removePrefix("prefetch-"),
                ).toString() shouldBe request.batchToken.removePrefix("prefetch-")
                request.profileSnapshot.snapshotVersion shouldBe 1
                request.profileSnapshot.deepSeekModel shouldBe "original-model"
                request.profileSnapshot.replaceRulesSnapshot shouldBe listOf(originalRule)
                request.profileSnapshot.replaceRulesFingerprint shouldBe "1:true:false:false:true:0:old->new"
                request.profileSnapshot.geminiEnabledPromptModifiers shouldBe listOf("original-modifier")
                fixture.queue.runs.getValue(904L).implicitOwner shouldBe false
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `cancel before admission commit waits then detaches the committed named owner`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.queue.deferAdmission = true
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()
                fixture.executor.cancelNextChapterGeminiPrefetchJob()
                fixture.readerJob.cancel()
                runCurrent()

                fixture.queue.cancelledTokens.shouldBeEmpty()
                fixture.queue.events shouldBe listOf("enqueue-start")
                fixture.queue.admissionGate.complete(Unit)
                runCurrent()

                fixture.queue.events shouldBe listOf("enqueue-start", "commit", "named-cancel")
                fixture.queue.namedOwners.shouldBeEmpty()
                fixture.queue.cancelledTokens shouldBe listOf(fixture.queue.requests.single().batchToken)
                fixture.queue.runs.getValue(904L).status shouldBe TranslationRunStatus.CANCELLED
                fixture.queue.promotions.shouldBeEmpty()
                coVerify(exactly = 0) { fixture.queue.manager.cancelChapter(any()) }
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `cancel before enqueue body starts cannot leave cleanup waiting for an impossible commit`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                fixture.executor.cancelNextChapterGeminiPrefetchJob()
                fixture.readerJob.cancel()
                runCurrent()

                fixture.queue.requests.shouldBeEmpty()
                fixture.queue.namedOwners.shouldBeEmpty()
                fixture.queue.cancelledTokens.size shouldBe 1
                fixture.queue.cleanupJobs.single().isCompleted shouldBe true
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `process cleanup cancellation cannot interrupt the deferred committed owner release`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.queue.deferCancellation = true
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()
                fixture.executor.cancelNextChapterGeminiPrefetchJob()
                runCurrent()
                val cleanup = fixture.queue.cleanupJobs.single()
                cleanup.cancel()
                runCurrent()
                fixture.queue.namedOwners.size shouldBe 1
                fixture.queue.cancellationGate.complete(Unit)
                runCurrent()

                fixture.queue.namedOwners.shouldBeEmpty()
                fixture.queue.cancelledTokens.size shouldBe 1
                cleanup.isCompleted shouldBe true
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `incoming chapter before commit joins exact frozen reader intent before named detach`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.queue.deferAdmission = true
                fixture.queue.deferPromotion = true
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()
                val request = fixture.queue.requests.single()
                fixture.executor.onChapterBecomingActive(904L)
                fixture.executor.clearChapterScopedPrefetchState()
                fixture.host.fieldsCleared = true
                fixture.readerJob.cancel()
                runCurrent()
                fixture.queue.cancelledTokens.shouldBeEmpty()
                fixture.queue.admissionGate.complete(Unit)
                runCurrent()

                fixture.queue.events shouldBe listOf("enqueue-start", "commit", "promotion-start")
                fixture.queue.namedOwners.keys shouldBe setOf(request.batchToken)
                fixture.queue.cancelledTokens.shouldBeEmpty()
                fixture.queue.promotionGate.complete(Unit)
                runCurrent()

                fixture.queue.promotions shouldBe listOf(
                    Promotion(request.profileSnapshot, 904L, 901L, request.batchToken),
                )
                fixture.queue.events shouldBe listOf(
                    "enqueue-start",
                    "commit",
                    "promotion-start",
                    "implicit-join",
                    "named-cancel",
                )
                fixture.queue.namedOwners.shouldBeEmpty()
                fixture.queue.runs.getValue(904L).implicitOwner shouldBe true
                fixture.queue.runs.getValue(904L).priority shouldBe 2
                fixture.queue.runs.getValue(904L).status shouldBe TranslationRunStatus.PENDING
                fixture.queue.requests.size shouldBe 1
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `terminal prefetched generations are not re-enqueued during activation`() {
        runTest {
            listOf(
                TranslationRunStatus.COMPLETE,
                TranslationRunStatus.FAILED,
                TranslationRunStatus.CANCELLED,
            ).forEach { terminal ->
                val fixture = Fixture(this)
                try {
                    fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                    runCurrent()
                    val original = fixture.queue.runs.getValue(904L)
                    fixture.queue.runs[904L] = original.copy(status = terminal)
                    fixture.executor.onChapterBecomingActive(904L)
                    fixture.executor.clearChapterScopedPrefetchState()
                    runCurrent()

                    fixture.queue.requests.size shouldBe 1
                    fixture.queue.runs.getValue(904L).generation shouldBe original.generation
                    fixture.queue.runs.getValue(904L).status shouldBe terminal
                    fixture.queue.runs.getValue(904L).implicitOwner shouldBe false
                    fixture.queue.events.count { it == "implicit-join" } shouldBe 0
                    fixture.queue.namedOwners.shouldBeEmpty()
                } finally {
                    fixture.close()
                    runCurrent()
                }
            }
        }
    }

    @Test
    fun `source model or rule changes cannot steal a prefetched run into the reader`() {
        runTest {
            listOf("source", "model", "rules").forEach { change ->
                val fixture = Fixture(this)
                try {
                    fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                    runCurrent()
                    val row = fixture.queue.runs.getValue(904L)
                    fixture.queue.namedOwners["other-batch"] = row.id to row.generation
                    when (change) {
                        "source" -> fixture.host.novel = fixture.host.novel.copy(source = 999L)
                        "model" -> fixture.host.settings = fixture.host.settings.copy(deepSeekModel = "different-model")
                        "rules" -> fixture.host.rules = listOf(
                            ReplaceRule(id = 2L, pattern = "changed", replacement = "new"),
                        )
                    }
                    fixture.executor.onChapterBecomingActive(904L)
                    fixture.executor.clearChapterScopedPrefetchState()
                    runCurrent()

                    fixture.queue.requests.size shouldBe 1
                    fixture.queue.promotions.shouldBeEmpty()
                    fixture.queue.runs.getValue(904L).implicitOwner shouldBe false
                    fixture.queue.runs.getValue(904L).status shouldBe TranslationRunStatus.PENDING
                    fixture.queue.namedOwners.keys shouldBe setOf("other-batch")
                } finally {
                    fixture.close()
                    runCurrent()
                }
            }
        }
    }

    @Test
    fun `unrelated chapter and dispose cancel only the named owner leaving shared reader owners`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()
                val request = fixture.queue.requests.single()
                val run = fixture.queue.runs.getValue(904L)
                fixture.queue.runs[904L] = run.copy(implicitOwner = true)
                fixture.queue.namedOwners["other-batch"] = run.id to run.generation
                fixture.executor.onChapterBecomingActive(999L)
                fixture.executor.cancelNextChapterGeminiPrefetchJob()
                fixture.executor.clearChapterScopedPrefetchState()
                fixture.readerJob.cancel()
                runCurrent()

                fixture.queue.cancelledTokens shouldBe listOf(request.batchToken)
                fixture.queue.namedOwners.keys shouldBe setOf("other-batch")
                fixture.queue.runs.getValue(904L).implicitOwner shouldBe true
                fixture.queue.runs.getValue(904L).status shouldBe TranslationRunStatus.PENDING
                fixture.queue.promotions.shouldBeEmpty()
                coVerify(exactly = 0) { fixture.queue.manager.cancelChapter(any()) }
                coVerify(exactly = 0) { fixture.queue.manager.cancelAll() }
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `display settings changes do not invalidate an otherwise exact frozen profile`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()
                fixture.host.settings = fixture.host.settings.copy(fontSize = 42)
                fixture.executor.onChapterBecomingActive(904L)
                fixture.executor.clearChapterScopedPrefetchState()
                runCurrent()

                fixture.queue.runs.getValue(904L).implicitOwner shouldBe true
                fixture.queue.requests.size shouldBe 1
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `promotion failure retains the only wanted owner until a later acknowledged handoff`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()
                fixture.queue.promotionFailure = IOException("private diagnostic fixture")
                fixture.executor.onChapterBecomingActive(904L)
                fixture.executor.cancelNextChapterGeminiPrefetchJob()
                runCurrent()

                fixture.queue.namedOwners.size shouldBe 1
                fixture.queue.cancelledTokens.shouldBeEmpty()
                fixture.queue.runs.getValue(904L).status shouldBe TranslationRunStatus.PENDING
                fixture.queue.promotionFailure = null
                fixture.executor.cancelNextChapterGeminiPrefetchJob()
                runCurrent()

                fixture.queue.namedOwners.shouldBeEmpty()
                fixture.queue.runs.getValue(904L).implicitOwner shouldBe true
                fixture.queue.cancelledTokens.size shouldBe 1
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `admission exception after commit is still cleaned without logging its private payload`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.queue.failAfterCommit = true
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()

                fixture.queue.namedOwners.shouldBeEmpty()
                fixture.queue.cancelledTokens.size shouldBe 1
                fixture.queue.runs.getValue(904L).status shouldBe TranslationRunStatus.CANCELLED
                fixture.host.logs.any { "private diagnostic fixture" in it } shouldBe false
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `late wanted cleanup cannot cancel the next speculative chapter owner`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.queue.deferAdmission = true
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()
                val incoming = fixture.queue.requests.single()
                fixture.executor.onChapterBecomingActive(904L)
                fixture.executor.clearChapterScopedPrefetchState()
                fixture.host.chapter = fixture.host.next
                fixture.host.next = fixture.host.next.copy(id = 905L, name = "Following chapter")
                fixture.queue.deferAdmission = false
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()
                val following = fixture.queue.requests.last()
                fixture.queue.admissionGate.complete(Unit)
                runCurrent()

                fixture.queue.requests.size shouldBe 2
                fixture.queue.cancelledTokens shouldBe listOf(incoming.batchToken)
                fixture.queue.namedOwners.keys shouldBe setOf(following.batchToken)
                fixture.queue.runs.getValue(904L).implicitOwner shouldBe true
                fixture.queue.runs.getValue(905L).implicitOwner shouldBe false
                fixture.queue.runs.getValue(905L).status shouldBe TranslationRunStatus.PENDING
                fixture.queue.promotions.single().chapterId shouldBe 904L
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `replaced generation is not joined or restarted by a stale prefetch token`() {
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.executor.maybePrefetchNextChapterGeminiTranslationOnProgress(30, 100)
                runCurrent()
                val original = fixture.queue.runs.getValue(904L)
                fixture.queue.runs[904L] = original.copy(generation = 2L, batchToken = "other-batch")
                fixture.queue.namedOwners["other-batch"] = original.id to 2L
                fixture.executor.onChapterBecomingActive(904L)
                fixture.executor.clearChapterScopedPrefetchState()
                runCurrent()

                fixture.queue.requests.size shouldBe 1
                fixture.queue.runs.getValue(904L).generation shouldBe 2L
                fixture.queue.runs.getValue(904L).implicitOwner shouldBe false
                fixture.queue.runs.getValue(904L).status shouldBe TranslationRunStatus.PENDING
                fixture.queue.namedOwners.keys shouldBe setOf("other-batch")
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    @Test
    fun `connection checks keep the direct dispatch for all configured providers`() {
        runTest {
            val fixture = Fixture(this)
            try {
                val expectations = listOf(
                    NovelTranslationProvider.GEMINI to "gemini",
                    NovelTranslationProvider.GEMINI_PRIVATE to "gemini",
                    NovelTranslationProvider.OPENROUTER to "openrouter",
                    NovelTranslationProvider.DEEPSEEK to "deepseek",
                    NovelTranslationProvider.MISTRAL to "mistral",
                    NovelTranslationProvider.NVIDIA to "nvidia",
                    NovelTranslationProvider.OLLAMA_CLOUD to "ollama",
                )
                expectations.forEach { (provider, expected) ->
                    fixture.executor.requestTranslationBatch(
                        listOf("Connection test"),
                        fixture.host.settings.copy(translationProvider = provider),
                    ) shouldBe listOf(expected)
                }
                fixture.queue.requests.shouldBeEmpty()
            } finally {
                fixture.close()
                runCurrent()
            }
        }
    }

    private class Fixture(scope: TestScope) {
        val readerJob = SupervisorJob(scope.backgroundScope.coroutineContext[Job])
        val queue = QueueBoundary(scope.backgroundScope)
        val services = Services()
        val source = mockk<NovelSource>().also {
            coEvery { it.getChapterText(any()) } returns "<p>Original chapter text.</p>"
        }
        val host = Host(CoroutineScope(scope.backgroundScope.coroutineContext + readerJob), source)
        val executor = NovelTranslationBatchExecutor(
            host = host,
            geminiTranslationService = services.gemini,
            openRouterTranslationService = services.openRouter,
            deepSeekTranslationService = services.deepSeek,
            mistralTranslationService = services.mistral,
            nvidiaTranslationService = services.nvidia,
            ollamaCloudTranslationService = services.ollama,
            translationQueueManager = queue.manager,
            resolveCredential = { _, _ -> null },
        )
        fun close() {
            queue.admissionGate.complete(Unit)
            queue.promotionGate.complete(Unit)
            queue.cancellationGate.complete(Unit)
            executor.cancelNextChapterGeminiPrefetchJob()
            readerJob.cancel()
        }
    }

    private data class Promotion(
        val snapshot: TranslationQueueProfileSnapshot,
        val chapterId: Long,
        val novelId: Long,
        val token: String,
    )

    /** Models the queue boundary's commit/owner acknowledgement, not SQLite implementation details. */
    private class QueueBoundary(scope: CoroutineScope) {
        val manager = mockk<TranslationQueueManager>()
        val repository = mockk<TranslationRunRepository>()
        val requests = mutableListOf<TranslationBatchRequest>()
        val promotions = mutableListOf<Promotion>()
        val cancelledTokens = mutableListOf<String>()
        val events = mutableListOf<String>()
        val runs = mutableMapOf<Long, TranslationRun>()
        val namedOwners = mutableMapOf<String, Pair<Long, Long>>()
        val cleanupJobs = mutableListOf<Job>()
        val admissionGate = CompletableDeferred<Unit>()
        val promotionGate = CompletableDeferred<Unit>()
        val cancellationGate = CompletableDeferred<Unit>()
        var deferAdmission = false
        var deferPromotion = false
        var deferCancellation = false
        var failAfterCommit = false
        var promotionFailure: Exception? = null

        init {
            every { manager.repository } returns repository
            coEvery { repository.getByChapterId(any()) } answers { runs[firstArg<Long>()] }
            every { manager.launchCleanup(any()) } answers {
                val block = firstArg<suspend () -> Unit>()
                cleanupJobs += scope.launch { block() }
                Unit
            }
            coEvery { manager.enqueueTranslationBatch(any()) } coAnswers {
                val request = firstArg<TranslationBatchRequest>()
                requests += request
                events += "enqueue-start"
                if (deferAdmission) admissionGate.await()
                val chapterId = request.chapterIds.single()
                val row = TranslationRun(
                    id = chapterId,
                    chapterId = chapterId,
                    novelId = request.novelId,
                    batchToken = request.batchToken,
                    batchOrder = 0,
                    profileSnapshotJson = Json.encodeToString(request.profileSnapshot),
                    status = TranslationRunStatus.PENDING,
                    progress = 0,
                    errorMessage = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    retryCount = 0,
                    generation = 1L,
                    priority = request.priority,
                    implicitOwner = false,
                )
                runs[chapterId] = row
                namedOwners[request.batchToken] = row.id to row.generation
                events += "commit"
                if (failAfterCommit) throw IOException("private diagnostic fixture")
                TranslationBatchEnqueueResult(request.batchToken, 1, 1, 0)
            }
            coEvery { manager.promotePrefetch(any(), any(), any(), any()) } coAnswers {
                val call = Promotion(firstArg(), arg(1), arg(2), arg(3))
                promotions += call
                events += "promotion-start"
                if (deferPromotion) promotionGate.await()
                promotionFailure?.let { throw it }
                val row = repository.getByChapterId(call.chapterId)
                if (row == null || row.status.isTerminal || row.novelId != call.novelId ||
                    row.profileSnapshotJson != Json.encodeToString(call.snapshot) ||
                    namedOwners[call.token] != (row.id to row.generation)
                ) {
                    false
                } else {
                    runs[call.chapterId] = row.copy(implicitOwner = true, priority = 2)
                    events += "implicit-join"
                    true
                }
            }
            coEvery { manager.cancelBatch(any()) } coAnswers {
                val token = firstArg<String>()
                cancelledTokens += token
                if (deferCancellation) cancellationGate.await()
                val member = namedOwners.remove(token)
                if (member != null) {
                    val row = runs[member.first]
                    if (row != null &&
                        row.generation == member.second &&
                        !row.status.isTerminal &&
                        !row.implicitOwner &&
                        namedOwners.values.none { it == member }
                    ) {
                        runs[row.chapterId] = row.copy(status = TranslationRunStatus.CANCELLED)
                    }
                }
                events += "named-cancel"
                member != null
            }
        }
    }

    private class Host(private val scope: CoroutineScope, source: NovelSource) : NovelTranslationBatchHost {
        var settings = settings()
        var novel = Novel.create().copy(id = 901L, source = 902L, title = "Test book")
        var chapter = NovelChapter.create().copy(id = 903L, novelId = 901L, name = "Current chapter")
        var next = NovelChapter.create().copy(id = 904L, novelId = 901L, name = "Next chapter")
        var rules: List<ReplaceRule> = emptyList()
        var fieldsCleared = false
        var sourceManagerReads = 0
        var rawCachePolicyReads = 0
        val logs = mutableListOf<String>()
        private val sourceManager = mockk<NovelSourceManager>().also {
            every { it.get(any()) } returns source
        }
        override fun batchReaderSettings(): NovelReaderSettings? = settings.takeUnless { fieldsCleared }
        override fun batchCurrentNovel(): Novel? = novel.takeUnless { fieldsCleared }
        override fun batchCurrentChapter(): NovelChapter? = chapter.takeUnless { fieldsCleared }
        override fun batchSourceManager(): NovelSourceManager {
            sourceManagerReads++
            return sourceManager
        }
        override fun batchFindNextChapter(chapter: NovelChapter): NovelChapter = next
        override fun batchCoroutineScope(): CoroutineScope = scope
        override fun batchCacheReadChapters(): Boolean {
            rawCachePolicyReads++
            return false
        }
        override fun batchReplaceRulesFingerprint(): String = replaceRulesFingerprint(rules)
        override fun batchReplaceRules(): List<ReplaceRule> = rules
        override fun batchAddAiTranslationLog(message: String) {
            logs += message
        }
    }

    private class Services {
        val gemini = mockk<GeminiTranslationService>()
        val openRouter = mockk<OpenRouterTranslationService>()
        val deepSeek = mockk<DeepSeekTranslationService>()
        val mistral = mockk<MistralTranslationService>()
        val nvidia = mockk<NvidiaTranslationService>()
        val ollama = mockk<OllamaCloudTranslationService>()
        init {
            coEvery { gemini.translateBatch(any(), any(), any()) } returns listOf("gemini")
            coEvery { openRouter.translateBatch(any(), any(), any()) } returns listOf("openrouter")
            coEvery { deepSeek.translateBatch(any(), any(), any()) } returns listOf("deepseek")
            coEvery { mistral.translateBatch(any(), any(), any()) } returns listOf("mistral")
            coEvery { nvidia.translateBatch(any(), any(), any()) } returns listOf("nvidia")
            coEvery { ollama.translateBatch(any(), any(), any()) } returns listOf("ollama")
        }
        fun assertNoProviderCalls() {
            coVerify(exactly = 0) { gemini.translateBatch(any(), any(), any()) }
            coVerify(exactly = 0) { openRouter.translateBatch(any(), any(), any()) }
            coVerify(exactly = 0) { deepSeek.translateBatch(any(), any(), any()) }
            coVerify(exactly = 0) { mistral.translateBatch(any(), any(), any()) }
            coVerify(exactly = 0) { nvidia.translateBatch(any(), any(), any()) }
            coVerify(exactly = 0) { ollama.translateBatch(any(), any(), any()) }
        }
    }

    companion object {
        private fun settings(): NovelReaderSettings = NovelReaderPreferences(
            InMemoryPreferenceStore(),
            Json { ignoreUnknownKeys = true },
        ).resolveSettings(902L).copy(
            geminiEnabled = true,
            geminiPrefetchNextChapterTranslation = true,
            geminiDisableCache = false,
            geminiApiKey = "test-key",
            geminiModel = "gemini-3.5-flash-lite",
            translationProvider = NovelTranslationProvider.DEEPSEEK,
            deepSeekApiKey = "test-key",
            openRouterApiKey = "test-key",
            openRouterModel = "test-model",
            mistralApiKey = "test-key",
            nvidiaApiKey = "test-key",
            nvidiaModel = "test-model",
            ollamaCloudApiKey = "test-key",
        )
    }
}
