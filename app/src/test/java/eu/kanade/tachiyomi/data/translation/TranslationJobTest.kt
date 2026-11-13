package eu.kanade.tachiyomi.data.translation

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.backup.translation.KEY_A
import eu.kanade.tachiyomi.data.backup.translation.RecordingTranslationVault
import eu.kanade.tachiyomi.data.backup.translation.TranslationBackupFixture
import eu.kanade.tachiyomi.data.translation.profile.ProfileTranslationRuntime
import eu.kanade.tachiyomi.data.translation.profile.withTranslationProfile
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.ProfileCompatibleApi
import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationHttpTransport
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterRepository
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterSnapshot
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.data.translation.TranslationRunRepositoryImpl
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.translation.model.TranslationActionPauseReason
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.ExecutionPolicy
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.ProfileRetryMode
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol
import tachiyomi.domain.translation.service.TranslationFragmentPlanner
import tachiyomi.domain.translation.service.TranslationRequestGate

/** Real SQL run/profile repositories, real profile processor/HTTP, fake external chapter source and vault. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TranslationJobTest {
    private val publications = mutableListOf<GeminiTranslationCacheEntry>()

    @Before
    fun isolateArtifacts() {
        mockkObject(NovelReaderTranslationDiskCacheStore)
        every { NovelReaderTranslationDiskCacheStore.get(any()) } returns null
        every { NovelReaderTranslationDiskCacheStore.put(any()) } answers {
            publications += firstArg<GeminiTranslationCacheEntry>()
        }
    }

    @After
    fun releaseArtifacts() {
        unmockkObject(NovelReaderTranslationDiskCacheStore)
    }

    @Test
    fun `WorkManager retains its two argument constructor`() {
        TranslationJob::class.java.getConstructor(Context::class.java, WorkerParameters::class.java)
            .parameterCount shouldBe 2
    }

    @Test
    fun `valid v2 worker publishes with frozen model root prompt and the current vault key`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.enqueue()
            fixture.db.profiles.saveConnection(
                fixture.connection.copy(baseUrl = fixture.server.url("/future/v9").toString()),
            )
            fixture.db.profiles.saveProfile(
                fixture.profile.copy(model = fixture.profile.model.copy(exactModelId = "future-model")),
            )
            fixture.vault.values[KEY_A] = "updated-test-key"
            fixture.answer("first-ru")
            fixture.answer("second-ru")
            fixture.worker().doWork()
            fixture.current().status shouldBe TranslationRunStatus.COMPLETE
            fixture.current().progress shouldBe 100
            fixture.server.requestCount shouldBe 2
            val request = fixture.server.takeRequest()
            request.path shouldBe "/v1/chat/completions"
            request.getHeader("Authorization") shouldBe "Bearer updated-test-key"
            val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
            body["model"] shouldBe JsonPrimitive("captured-model")
            val messages = body["messages"] as JsonArray
            (messages[0] as JsonObject)["content"] shouldBe JsonPrimitive("Frozen system prompt")
            publications.single().translatedByIndex shouldBe mapOf(0 to "first-ru", 1 to "second-ru")
            publications.single().model shouldBe "captured-model"
        }
    }

    @Test
    fun `malformed v2 never dispatches or substitutes live settings`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val valid = Json.parseToJsonElement(
                fixture.json.encodeToString(TranslationQueueProfileSnapshot.serializer(), fixture.snapshot),
            ) as JsonObject
            fixture.enqueueRaw(JsonObject(valid + ("geminiApiKey" to JsonPrimitive("untrusted-field"))).toString())
            fixture.worker().doWork()
            fixture.current().status shouldBe TranslationRunStatus.PAUSED
            fixture.current().actionPauseReason shouldBe TranslationActionPauseReason.CONFIGURATION
            fixture.server.requestCount shouldBe 0
            publications shouldBe emptyList()
        }
    }

    @Test
    fun `snapshot identity mismatch is parked before any HTTP`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.enqueueRaw(
                fixture.json.encodeToString(TranslationQueueProfileSnapshot.serializer(), fixture.snapshot),
                "wrong-fingerprint",
            )
            fixture.worker().doWork()
            fixture.current().actionPauseReason shouldBe TranslationActionPauseReason.CONFIGURATION
            fixture.server.requestCount shouldBe 0
        }
    }

    @Test
    fun `missing credential parks until an explicit same generation resume`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.vault.values.clear()
            fixture.enqueue()
            fixture.worker().doWork()
            val parked = fixture.current()
            parked.actionPauseReason shouldBe TranslationActionPauseReason.NEEDS_ACTION
            fixture.worker().doWork()
            fixture.server.requestCount shouldBe 0
            fixture.db.profiles.readLedger(parked.id, parked.generation).reservedRequestIds.size shouldBe 0
            fixture.vault.values[KEY_A] = "restored-test-key"
            fixture.db.profiles.setCredentialHealth(KEY_A, CredentialHealth())
            fixture.queue.resumeActionPaused(parked.chapterId, parked.generation) shouldBe true
            fixture.answer("first-ru")
            fixture.answer("second-ru")
            fixture.worker().doWork()
            fixture.current().generation shouldBe parked.generation
            fixture.current().status shouldBe TranslationRunStatus.COMPLETE
            fixture.server.takeRequest().getHeader("Authorization") shouldBe "Bearer restored-test-key"
        }
    }

    @Test
    fun `budget park survives restart and resume without refund then explicit retry reuses verified fragments`() =
        runBlocking<Unit> {
            Fixture(maxRequests = 1).use { fixture ->
                fixture.enqueue()
                fixture.answer("first-ru")
                fixture.worker().doWork()
                val parked = fixture.current()
                parked.actionPauseReason shouldBe TranslationActionPauseReason.BUDGET_LIMIT
                // Coverage progress is source-character weighted: 12 verified chars out of 25.
                parked.progress shouldBe 48
                fixture.runs.getFragments(parked).count { it.isComplete } shouldBe 1
                val ledger = fixture.db.profiles.readLedger(parked.id, parked.generation)
                ledger.reservedRequestIds.size shouldBe 1
                publications shouldBe emptyList()
                fixture.worker().doWork()
                fixture.queue.resumeActionPaused(parked.chapterId, parked.generation) shouldBe true
                fixture.worker().doWork()
                fixture.current().generation shouldBe parked.generation
                fixture.current().actionPauseReason shouldBe TranslationActionPauseReason.BUDGET_LIMIT
                fixture.db.profiles.readLedger(parked.id, parked.generation) shouldBe ledger
                fixture.server.requestCount shouldBe 1
                fixture.queue.detachImplicitOwner(parked.chapterId, parked.generation) shouldBe true
                fixture.queue.retryOwnedRun(parked.chapterId, parked.generation) shouldBe true
                fixture.answer("second-ru")
                fixture.worker().doWork()
                fixture.current().generation shouldBe parked.generation + 1
                fixture.current().status shouldBe TranslationRunStatus.COMPLETE
                fixture.server.requestCount shouldBe 2
                publications.single().translatedByIndex shouldBe mapOf(0 to "first-ru", 1 to "second-ru")
                fixture.db.profiles.readLedger(parked.id, parked.generation) shouldBe ledger
            }
        }

    @Test
    fun `wait for service releases the worker with a durable sparse wake`() = runBlocking<Unit> {
        Fixture(retryMode = ProfileRetryMode.WAIT_FOR_SERVICE).use { fixture ->
            fixture.enqueue()
            fixture.server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val started = System.currentTimeMillis()
            fixture.worker().doWork()
            val waiting = fixture.current()
            waiting.status shouldBe TranslationRunStatus.WAITING
            (waiting.nextAttemptAt >= started + 5 * 60_000) shouldBe true
            fixture.wakeDue shouldBe waiting.nextAttemptAt
            fixture.worker().doWork()
            fixture.server.requestCount shouldBe 1
            publications shouldBe emptyList()
        }
    }

    @Test
    fun `empty text chapter fails instead of parking as configuration`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.enqueue()
            fixture.worker(emptySource = true).doWork()
            val failed = fixture.current()
            failed.status shouldBe TranslationRunStatus.FAILED
            failed.actionPauseReason shouldBe null
            (failed.errorMessage?.contains("Chapter has no translatable text") == true) shouldBe true
            fixture.server.requestCount shouldBe 0
            publications shouldBe emptyList()
        }
    }

    @Test
    fun `cache hit publishes without rewriting disk cache`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.enqueue()
            fixture.answer("first-ru")
            fixture.answer("second-ru")
            fixture.worker().doWork()
            val cached = publications.single()
            publications.clear()

            every { NovelReaderTranslationDiskCacheStore.get(cached.chapterId) } returns cached
            fixture.requeue()
            fixture.worker().doWork()

            val completed = fixture.current()
            completed.status shouldBe TranslationRunStatus.COMPLETE
            fixture.server.requestCount shouldBe 2
            publications shouldBe emptyList()
        }
    }

    private class Fixture(
        maxRequests: Long? = null,
        retryMode: ProfileRetryMode = ProfileRetryMode.OFF,
    ) : AutoCloseable {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
        val db = TranslationBackupFixture()
        val server = MockWebServer().apply { start() }
        val vault = RecordingTranslationVault().also { it.values[KEY_A] = "initial-test-key" }
        private val owner = SupervisorJob()
        private val scope = CoroutineScope(owner + Dispatchers.IO)
        val runs = TranslationRunRepositoryImpl(db.handler, json)
        val queue =
            TranslationQueueManager(db.handler, json, runs, scope, {
            }, profileRepository = db.profiles, profileApplication = {
                ApplicationProvider.getApplicationContext()
            })
        val connection = ConnectionProfile(
            "00000000-0000-0000-0000-000000000031",
            "Worker connection",
            TranslationProtocol.OPENAI_CHAT,
            server.url("/v1").toString(),
            ProfileAuth.BEARER,
            credentialBindings = listOf(CredentialBinding(KEY_A)),
            allowInsecureLocalHTTP = true,
            maxConcurrency = 1,
        )
        val profile = TranslationProfile(
            "00000000-0000-0000-0000-000000000032",
            "Worker profile",
            connection.id,
            ModelSelection(
                "captured-model",
                generation = GenerationSettings(maxOutputTokens = 1024),
                outputTokenField = OutputTokenField.MAX_TOKENS,
            ),
            TranslationLanguages("English", "Russian"),
            execution = ExecutionPolicy(retryMode = retryMode, maxRequests = maxRequests),
        )
        private val preferences = NovelReaderPreferences(InMemoryPreferenceStore(), json)
        private val base = preferences.resolveSettings(-42).withTranslationProfile(profile, connection)
            .copy(geminiEnabled = true, geminiBatchSize = 1, systemPromptSnapshot = "Frozen system prompt")
        val snapshot = base.toTranslationQueueProfileSnapshot(replaceRulesSnapshot = emptyList())
        private val chapter = NovelChapter.create().copy(id = 201, novelId = 101, name = "Chapter", url = "/chapter")
        var wakeDue: Long? = null

        suspend fun enqueue() {
            db.insertNovel(101)
            db.profiles.importConfiguration(
                ProfileCatalog(
                    connections = listOf(connection),
                    profiles = listOf(profile),
                    credentials = listOf(CredentialMetadata(KEY_A, "Worker alias", "key", 0)),
                ),
            )
            queue.enqueueChapters(listOf(chapter.id), 101, base, emptyList()) shouldBe 1
        }

        suspend fun requeue() {
            queue.enqueueChapters(listOf(chapter.id), 101, base, emptyList()) shouldBe 1
        }

        suspend fun enqueueRaw(
            raw: String,
            fingerprint: String = TranslationFragmentPlanner.sourceFingerprint(
                listOf(raw),
            ),
        ) {
            enqueue()
            runs.cancelChapter(chapter.id, System.currentTimeMillis())
            runs.enqueue(listOf(chapter.id), 101, raw, fingerprint, "", false, 2, System.currentTimeMillis())
        }

        suspend fun current() = requireNotNull(runs.getByChapterId(chapter.id))

        fun answer(text: String) {
            val message = JsonObject(mapOf("content" to JsonPrimitive("<s i='0'>$text</s>")))
            val response =
                JsonObject(
                    mapOf(
                        "choices" to JsonArray(
                            listOf(JsonObject(mapOf("finish_reason" to JsonPrimitive("stop"), "message" to message))),
                        ),
                    ),
                )
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(response.toString()))
        }

        suspend fun worker(emptySource: Boolean = false): TranslationJob {
            // Recreate repository/runner objects over the same persisted SQL state on every call.
            val freshRuns = TranslationRunRepositoryImpl(db.handler, json)
            val freshQueue =
                TranslationQueueManager(db.handler, json, freshRuns, scope, {
                }, profileRepository = db.profiles, profileApplication = {
                    ApplicationProvider.getApplicationContext()
                })
            val gate = TranslationRequestGate()
            val transport = TranslationHttpTransport(OkHttpClient(), json, gate)
            val runtime =
                ProfileTranslationRuntime(
                    db.profiles,
                    vault,
                    ProfileCompatibleApi(OkHttpClient(), json, transport),
                    gate,
                )
            val processor =
                NovelChapterTranslationProcessor(
                    application = ApplicationProvider.getApplicationContext<Application>(),
                    geminiTranslationService = mockk(),
                    openRouterTranslationService = mockk(),
                    deepSeekTranslationService = mockk(),
                    mistralTranslationService = mockk(),
                    nvidiaTranslationService = mockk(),
                    ollamaCloudTranslationService = mockk(),
                    profileRuntime = runtime,
                )
            val chapterRepository = mockk<NovelChapterRepository> {
                coEvery { getChapterById(chapter.id) } returns
                    chapter
            }
            val novel = db.novels.getNovelById(101)
            val source = NovelTtsChapterSnapshot(
                novel = novel, chapter = chapter, chapterOrderList = listOf(chapter),
                rawHtml = if (emptySource) "" else "<p>First source</p><p>Second source</p>",
                customCss = null,
                customJs = null,
                pluginSite = null,
                chapterWebUrl = null,
                contentBlocks = if (emptySource) {
                    emptyList()
                } else {
                    listOf(
                        NovelReaderScreenModel.ContentBlock.Text("First source"),
                        NovelReaderScreenModel.ContentBlock.Text("Second source"),
                    )
                },
                richContentBlocks = emptyList(),
                richContentUnsupportedFeaturesDetected = false, lastSavedIndex = 0, lastSavedScrollOffsetPx = 0,
                lastSavedWebProgressPercent = 0, lastSavedPageReaderProgress = null, previousChapterId = null,
                previousChapterName = null, nextChapterId = null, nextChapterName = null,
            )
            val chapters = mockk<NovelTtsChapterRepository> {
                coEvery { loadChapterSnapshot(chapter.id, any()) } returns
                    source
            }
            return TranslationJob(
                ApplicationProvider.getApplicationContext(), mockk(relaxed = true), freshQueue,
                mockk(
                    relaxUnitFun = true,
                ),
                preferences, json, chapters, processor, chapterRepository, GetNovel(db.novels),
                enterForeground = {}, wakeScheduler = { _, due -> wakeDue = due },
            )
        }

        override fun close() = runBlocking {
            owner.cancelAndJoin()
            db.close()
            server.shutdown()
        }
    }
}
