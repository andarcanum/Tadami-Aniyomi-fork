package eu.kanade.presentation.more.settings.translation

import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.model.TranslationActionPauseReason
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.CredentialRoutingState
import tachiyomi.domain.translation.profile.ExecutionPolicy
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.ProfileAssignment
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.ProfileRetryMode
import tachiyomi.domain.translation.profile.PromptSettings
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import tachiyomi.domain.translation.profile.TranslationProtocol
import tachiyomi.domain.translation.repository.TranslationRunRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AiTranslationSettingsScreenModelTest {
    @Test
    fun `novel selection updates quick state even when all public lists stay equal`() = runTest {
        val fixture = Fixture(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        try {
            runCurrent()
            fixture.model.quickState.value.profile?.id shouldBe fixture.first.id
            fixture.model.onAction(AiTranslationAction.SelectCurrentProfile(fixture.second.id))
            runCurrent()
            fixture.model.quickState.value.profile?.id shouldBe fixture.second.id
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `v2 retry delegates frozen intent and current generation to the scheduler facade`() = runTest {
        val fixture = Fixture(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        try {
            runCurrent()
            coEvery { fixture.queue.retryOwnedRun(21, 7, false) } returns true
            fixture.model.onAction(
                AiTranslationAction.RequestJobCommand("run:1:7", AiTranslationJobCommand.RETRY_MISSING),
            )
            runCurrent()
            coVerify(exactly = 1) { fixture.queue.retryOwnedRun(21, 7, false) }
            coVerify(exactly = 0) { fixture.queue.enqueueTranslationBatch(any()) }
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `retranslate waits for confirmation and then explicitly forces the stored generation`() = runTest {
        val fixture = Fixture(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        try {
            runCurrent()
            val command = AiTranslationAction.RequestJobCommand("run:1:7", AiTranslationJobCommand.RETRANSLATE)
            fixture.model.onAction(command)
            runCurrent()
            (fixture.model.state.value.editor is AiTranslationEditor.Confirmation) shouldBe true
            coVerify(exactly = 0) { fixture.queue.retryOwnedRun(any(), any(), any()) }
            coEvery { fixture.queue.retryOwnedRun(21, 7, true) } returns true
            fixture.model.onAction(AiTranslationAction.ConfirmJobCommand(command))
            runCurrent()
            coVerify(exactly = 1) { fixture.queue.retryOwnedRun(21, 7, true) }
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `quick pause works without the optional root callback and affects only its implicit owner`() = runTest {
        val fixture = Fixture(backgroundScope, UnconfinedTestDispatcher(testScheduler), TranslationRunStatus.RUNNING)
        try {
            runCurrent()
            coEvery { fixture.queue.pauseImplicitOwner(21, 7) } returns true
            fixture.model.onAction(AiTranslationAction.RequestJobCommand("run:1:7", AiTranslationJobCommand.PAUSE))
            runCurrent()
            coVerify(exactly = 1) { fixture.queue.pauseImplicitOwner(21, 7) }
            coVerify(exactly = 0) { fixture.queue.pauseBatch(any()) }
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `action pause resumes one generation rather than a whole named batch`() = runTest {
        val fixture = Fixture(
            backgroundScope,
            UnconfinedTestDispatcher(testScheduler),
            TranslationRunStatus.PAUSED,
            implicit = false,
            pauseReason = TranslationActionPauseReason.BUDGET_LIMIT,
        )
        try {
            runCurrent()
            fixture.model.state.value.jobs.single().needsAction shouldBe true
            coEvery { fixture.queue.resumeActionPaused(21, 7) } returns true
            fixture.model.onAction(AiTranslationAction.RequestJobCommand("run:1:7", AiTranslationJobCommand.RESUME))
            runCurrent()
            coVerify(exactly = 1) { fixture.queue.resumeActionPaused(21, 7) }
            coVerify(exactly = 0) { fixture.queue.resumeBatch(any()) }
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `quickState starts loading and selects newest active non-cancelled run for chapter`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val completedOld = TranslationRun(
            1, 21, 11, "", 0, "{\"snapshotVersion\":2}", TranslationRunStatus.COMPLETE, 100, null, 0, 0, 0,
            generation = 1, implicitOwner = true,
        )
        val runningMiddle = completedOld.copy(
            id = 2,
            generation = 2,
            status = TranslationRunStatus.RUNNING,
            progress = 40,
        )
        val cancelledNewest = completedOld.copy(
            id = 3,
            generation = 3,
            status = TranslationRunStatus.CANCELLED,
            progress = 10,
        )
        val fixture = Fixture(
            backgroundScope,
            dispatcher,
            initialRuns = listOf(completedOld, runningMiddle, cancelledNewest),
        )
        try {
            fixture.model.quickState.value.loading shouldBe true
            runCurrent()
            fixture.model.quickState.value.loading shouldBe false
            fixture.model.quickState.value.job?.id shouldBe "run:2:2"
            fixture.runsFlow.value = listOf(completedOld, cancelledNewest)
            runCurrent()
            fixture.model.quickState.value.job?.id shouldBe "run:1:1"
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `set ai translation enabled and profile reasoning update state and persist`() = runTest {
        var persistedEnabled: Boolean? = null
        val enabledFlow = MutableStateFlow(true)
        val fixture = Fixture(
            backgroundScope,
            UnconfinedTestDispatcher(testScheduler),
            enabledFlow = enabledFlow,
            setEnabled = {
                persistedEnabled = it
                enabledFlow.value = it
            },
        )
        try {
            runCurrent()
            fixture.model.state.value.aiTranslationEnabled shouldBe true
            fixture.model.onAction(AiTranslationAction.SetAiTranslationEnabled(false))
            runCurrent()
            fixture.model.state.value.aiTranslationEnabled shouldBe false
            persistedEnabled shouldBe false

            val overrideContract = ReasoningContract.Levels(listOf("minimal", "low", "medium", "high"))
            fixture.model.onAction(
                AiTranslationAction.SetProfileReasoning(
                    fixture.first.id,
                    overrideContract,
                    ReasoningChoice.Level("low"),
                ),
            )
            runCurrent()
            val updated = fixture.model.quickState.value.profile!!
            updated.model.reasoningContract shouldBe overrideContract
            updated.generation.reasoning shouldBe ReasoningChoice.Level("low")
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `test connection and refresh models use independent epochs without cancelling each other`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture(backgroundScope, dispatcher)
        try {
            runCurrent()
            val expectedModel = AiTranslationModelUi(
                ModelSelection("gemma-4-31b-it", ReasoningContract.Toggle),
                "Gemma 4 31B",
            )
            coEvery { fixture.operations.models(any()) } coAnswers {
                delay(100)
                listOf(expectedModel)
            }
            coEvery { fixture.operations.probe(any(), any()) } coAnswers {
                delay(10)
                true
            }
            fixture.model.onAction(AiTranslationAction.RefreshModels(fixture.connection.id))
            fixture.model.onAction(AiTranslationAction.TestConnection(fixture.connection.id))
            runCurrent()
            advanceTimeBy(101)
            runCurrent()
            fixture.model.state.value.models shouldBe listOf(expectedModel)
            fixture.model.state.value.probeStates[fixture.connection.id] shouldBe AiTranslationProbeStatus.VERIFIED
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `profile to models navigation preserves loaded catalog and edits preserve discovered contract`() = runTest {
        val fixture = Fixture(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        try {
            runCurrent()
            val loaded = listOf(AiTranslationModelUi(ModelSelection("manual-model"), "Manual"))
            coEvery { fixture.operations.models(any()) } returns loaded
            fixture.model.onAction(AiTranslationAction.EditProfile(fixture.first.id))
            runCurrent()
            val opened = (fixture.model.state.value.editor as AiTranslationEditor.Profile).draft
            opened.discoveredReasoningContract shouldBe ReasoningContract.Unknown
            fixture.model.onAction(AiTranslationAction.EditorChanged(AiTranslationEditor.Models(opened)))
            fixture.model.onAction(AiTranslationAction.RefreshModels(fixture.connection.id))
            runCurrent()
            fixture.model.state.value.models shouldBe loaded
            fixture.model.onAction(AiTranslationAction.EditorChanged(AiTranslationEditor.Profile(opened)))
            runCurrent()
            fixture.model.state.value.models shouldBe loaded
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `saveRecovery with returnTo stages changes without mutating repo until SaveProfile`() = runTest {
        val fixture = Fixture(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        try {
            runCurrent()
            fixture.model.onAction(AiTranslationAction.EditProfile(fixture.first.id))
            runCurrent()
            val parent = (fixture.model.state.value.editor as AiTranslationEditor.Profile).draft
            val stagedPolicy = ExecutionPolicy(ProfileRetryMode.WAIT_FOR_SERVICE, maxRequests = 50)
            fixture.model.onAction(
                AiTranslationAction.EditorChanged(
                    AiTranslationEditor.Recovery(
                        fixture.first.id,
                        fixture.connection.id,
                        AiRecoveryDraft(stagedPolicy, maxRequests = "50", parallelism = 4),
                        returnTo = parent,
                    ),
                ),
            )
            fixture.model.onAction(
                AiTranslationAction.SaveRecovery(
                    fixture.first.id,
                    stagedPolicy,
                    fixture.connection.id,
                    parallelism = 4,
                ),
            )
            runCurrent()
            coVerify(exactly = 0) { fixture.repo.saveConnection(any()) }
            coVerify(exactly = 0) { fixture.repo.saveProfile(any()) }
            val returned = (fixture.model.state.value.editor as AiTranslationEditor.Profile).draft
            returned.execution shouldBe stagedPolicy
            returned.pendingParallelism shouldBe 4
            fixture.model.onAction(
                AiTranslationAction.SaveProfile(returned.toDomainOrNull()!!, returned.pendingParallelism),
            )
            runCurrent()
            coVerify(exactly = 1) { fixture.repo.saveConnection(match { it.maxConcurrency == 4 }) }
            coVerify(exactly = 1) { fixture.repo.saveProfile(match { it.execution == stagedPolicy }) }
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `routing health and snapshot project into ui state and all owned skips complete`() = runTest {
        val credId = "00000000-0000-0000-0000-000000000074"
        val snapshotPolicy = ExecutionPolicy(ProfileRetryMode.WAIT_FOR_SERVICE, maxRequests = 40)
        val encodedProfile = Json.encodeToString(
            TranslationProfile.serializer(),
            TranslationProfile(
                "00000000-0000-0000-0000-000000000072",
                "SnapshotProfile",
                "00000000-0000-0000-0000-000000000071",
                ModelSelection("manual-model"),
                TranslationLanguages("English", "Russian"),
                execution = snapshotPolicy,
            ),
        )
        val snapshotJson = """{"translationProfile":$encodedProfile}"""
        val completedRun = TranslationRun(
            1, 21, 11, "batch-done", 0, snapshotJson, TranslationRunStatus.COMPLETE, 100, null, 0, 0, 0,
            generation = 1, implicitOwner = false,
        )
        val runningRun = completedRun.copy(
            id = 2,
            chapterId = 22,
            batchToken = "batch-active",
            status = TranslationRunStatus.RUNNING,
            progress = 50,
            generation = 2,
        )
        val fixture = Fixture(
            backgroundScope,
            UnconfinedTestDispatcher(testScheduler),
            initialRuns = listOf(completedRun, runningRun),
            initialCredentials = listOf(CredentialMetadata(credId, "Primary", "abcd", 10L)),
            routing = CredentialRoutingState(
                health = mapOf(credId to CredentialHealth(CredentialHealthStatus.NEEDS_ACTION, notBeforeAt = 900_000L)),
            ),
        )
        fixture.fragmentsMap["1:1"] = listOf(TranslationFragment(1, 0, 0, 1, "src", translatedText = null))
        fixture.fragmentsMap["2:2"] = listOf(
            TranslationFragment(1, 0, 0, 2, "src1", translatedText = "ok"),
            TranslationFragment(2, 1, 0, 2, "src2", translatedText = null),
        )
        try {
            runCurrent()
            val credUi = fixture.model.state.value.credentials.single()
            credUi.healthStatus shouldBe CredentialHealthStatus.NEEDS_ACTION
            credUi.notBeforeAt shouldBe 900_000L

            val jobs = fixture.model.state.value.jobs
            val firstJob = jobs.first { it.id == "run:1:1" }
            firstJob.execution shouldBe snapshotPolicy
            firstJob.effectiveStatus shouldBe AiTranslationJobStatus.PARTIAL

            val secondJob = jobs.first { it.id == "run:2:2" }
            secondJob.verifiedCount shouldBe 1
            secondJob.totalCount shouldBe 2

            coEvery { fixture.queue.pauseBatch("batch-active") } returns true
            fixture.model.onAction(
                AiTranslationAction.RequestJobCommand(
                    null,
                    AiTranslationJobCommand.PAUSE,
                    AiTranslationJobScope.ALL_OWNED,
                ),
            )
            runCurrent()
            coVerify(exactly = 1) { fixture.queue.pauseBatch("batch-active") }
            coVerify(exactly = 0) { fixture.queue.pauseBatch("batch-done") }
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `inline reader actions update languages prompt retry and generation safely`() = runTest {
        val fixture = Fixture(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        try {
            runCurrent()
            fixture.model.onAction(AiTranslationAction.UpdateProfileLanguages(fixture.first.id, "Japanese", "English"))
            fixture.model.onAction(
                AiTranslationAction.SetProfilePrompt(
                    fixture.first.id,
                    PromptSettings(modeId = "ADULT", styleId = "LITERARY", modifierIds = listOf("FANTASY")),
                ),
            )
            fixture.model.onAction(
                AiTranslationAction.SetProfileRetryMode(fixture.first.id, ProfileRetryMode.WAIT_FOR_SERVICE),
            )
            fixture.model.onAction(
                AiTranslationAction.SetProfileGeneration(
                    fixture.first.id,
                    GenerationSettings(temperature = 0.65, topP = 0.9, topK = 32, maxOutputTokens = 2048),
                ),
            )
            runCurrent()

            val updated = fixture.model.quickState.value.profile!!
            updated.languages shouldBe TranslationLanguages("Japanese", "English")
            updated.prompt.styleId shouldBe "LITERARY"
            updated.prompt.modifierIds shouldBe listOf("FANTASY")
            updated.execution.retryMode shouldBe ProfileRetryMode.WAIT_FOR_SERVICE
            // fixture.first uses OutputTokenField.OMIT, so maxOutputTokens is coerced to null safely
            updated.model.generation.temperature shouldBe 0.65
            updated.model.generation.topP shouldBe 0.9
            updated.model.generation.maxOutputTokens shouldBe null
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `openProfileModelPicker and selectProfileModel preserve compatible generation and close modal`() = runTest {
        val fixture = Fixture(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        try {
            runCurrent()
            fixture.model.onAction(
                AiTranslationAction.SetProfileGeneration(
                    fixture.first.id,
                    GenerationSettings(temperature = 0.5, topP = 0.85),
                ),
            )
            fixture.model.onAction(AiTranslationAction.OpenProfileModelPicker(fixture.first.id))
            runCurrent()
            val editor = fixture.model.state.value.editor as AiTranslationEditor.Models
            editor.applyImmediately shouldBe true

            val nextModel = ModelSelection(
                exactModelId = "gemini-2.5-flash",
                reasoningContract = ReasoningContract.Levels(listOf("low", "high")),
                generation = GenerationSettings(reasoning = ReasoningChoice.Level("low")),
                outputTokenField = OutputTokenField.MAX_OUTPUT_TOKENS,
            )
            fixture.model.onAction(AiTranslationAction.SelectProfileModel(fixture.first.id, nextModel))
            runCurrent()

            fixture.model.state.value.editor shouldBe null
            val updated = fixture.model.quickState.value.profile!!
            updated.exactModelId shouldBe "gemini-2.5-flash"
            updated.model.generation.temperature shouldBe 0.5
            updated.model.generation.topP shouldBe 0.85
            updated.model.generation.reasoning shouldBe ReasoningChoice.Level("low")
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `setConnectionCredentialEnabled updates key pool binding and quickState canStart`() = runTest {
        val credId = "00000000-0000-0000-0000-000000000075"
        val fixture = Fixture(
            backgroundScope,
            UnconfinedTestDispatcher(testScheduler),
            initialCredentials = listOf(CredentialMetadata(credId, "Key1", "9999", 10L)),
        )
        try {
            runCurrent()
            val securedConn = fixture.connection.copy(
                auth = ProfileAuth.X_GOOG_API_KEY,
                credentialBindings = listOf(CredentialBinding(credId, priority = 0, enabled = true)),
            )
            fixture.model.onAction(AiTranslationAction.SaveConnection(securedConn))
            runCurrent()
            fixture.model.quickState.value.canStart shouldBe true

            fixture.model.onAction(AiTranslationAction.SetConnectionCredentialEnabled(securedConn.id, credId, false))
            runCurrent()
            fixture.model.quickState.value.canStart shouldBe false
            fixture.model.quickState.value.connection?.credentialBindings?.single()?.enabled shouldBe false
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `editConnection with returnTo profile roundtrips through credential save back to profile draft`() = runTest {
        val fixture = Fixture(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        try {
            coEvery { fixture.credentialStore.read(any()) } answers {
                CredentialRead.Available(CredentialSecret("sk-roundtrip-9876"))
            }
            runCurrent()

            val profileDraft = AiProfileDraft(
                id = "00000000-0000-0000-0000-000000000099",
                name = "gemini",
            )
            fixture.model.onAction(AiTranslationAction.EditConnection(null, returnTo = profileDraft))
            runCurrent()

            val connEditor = fixture.model.state.value.editor as AiTranslationEditor.Connection
            connEditor.returnTo shouldBe profileDraft
            val connDraft = connEditor.draft.copy(name = "Gemini")

            fixture.model.onAction(
                AiTranslationAction.EditorChanged(
                    AiTranslationEditor.Credential(
                        id = null,
                        alias = "Main key",
                        connectionId = connDraft.id,
                        returnTo = connDraft,
                        returnToProfile = profileDraft,
                    ),
                ),
            )
            runCurrent()
            fixture.model.saveCredential(null, "Main key", "sk-roundtrip-9876")
            runCurrent()

            val restoredConnEditor = fixture.model.state.value.editor as AiTranslationEditor.Connection
            restoredConnEditor.returnTo shouldBe profileDraft
            restoredConnEditor.draft.bindings.size shouldBe 1

            val savedConn = restoredConnEditor.draft.toDomainOrNull()!!
            fixture.model.onAction(AiTranslationAction.SaveConnection(savedConn))
            runCurrent()

            val restoredProfileEditor = fixture.model.state.value.editor as AiTranslationEditor.Profile
            restoredProfileEditor.draft.name shouldBe "gemini"
            restoredProfileEditor.draft.connectionId shouldBe savedConn.id
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `initialCatalog seeds ready quickState synchronously before async collection`() = runTest {
        val seededConnection = ConnectionProfile(
            "00000000-0000-0000-0000-000000000071",
            "Connection",
            TranslationProtocol.OPENAI_CHAT,
            "https://example.test/v1",
            ProfileAuth.NONE,
        )
        val seededProfile = TranslationProfile(
            "00000000-0000-0000-0000-000000000072",
            "Seeded",
            seededConnection.id,
            ModelSelection("manual-model"),
            TranslationLanguages("English", "Russian"),
        )
        val fixture = Fixture(
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            initialCatalogSeed = ProfileCatalog(
                connections = listOf(seededConnection),
                profiles = listOf(seededProfile),
                assignments = listOf(ProfileAssignment(ProfileAssignmentScope.GLOBAL, 0, seededProfile.id)),
            ),
        )
        try {
            fixture.model.state.value.loadState shouldBe AiTranslationLoadState.READY
            fixture.model.quickState.value.loading shouldBe false
            fixture.model.quickState.value.profile?.id shouldBe seededProfile.id
            fixture.model.quickState.value.canStart shouldBe true
        } finally {
            fixture.model.onDispose()
        }
    }

    @Test
    fun `saving first connection with no profiles opens profile editor prelinked to new connection`() = runTest {
        val fixture = Fixture(
            backgroundScope,
            UnconfinedTestDispatcher(testScheduler),
            initialConnections = emptyList(),
            initialProfiles = emptyList(),
        )
        try {
            runCurrent()
            fixture.model.onAction(AiTranslationAction.EditConnection(null))
            runCurrent()

            val newConn = (fixture.model.state.value.editor as AiTranslationEditor.Connection).draft
                .copy(
                    name = "Gemini",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                    auth = ProfileAuth.NONE,
                )
                .toDomainOrNull()!!
            fixture.model.onAction(AiTranslationAction.SaveConnection(newConn))
            runCurrent()

            val nextEditor = fixture.model.state.value.editor as AiTranslationEditor.Profile
            nextEditor.draft.connectionId shouldBe newConn.id

            // Editing that same connection afterwards closes normally instead of opening profile setup again.
            fixture.model.onAction(AiTranslationAction.EditConnection(newConn.id))
            runCurrent()
            fixture.model.onAction(AiTranslationAction.SaveConnection(newConn.copy(name = "Gemini Updated")))
            runCurrent()
            fixture.model.state.value.editor shouldBe null
        } finally {
            fixture.model.onDispose()
        }
    }

    private class Fixture(
        scope: CoroutineScope,
        io: kotlinx.coroutines.CoroutineDispatcher,
        status: TranslationRunStatus = TranslationRunStatus.FAILED,
        implicit: Boolean = true,
        pauseReason: TranslationActionPauseReason? = null,
        initialRuns: List<TranslationRun>? = null,
        initialCredentials: List<CredentialMetadata> = emptyList(),
        initialConnections: List<ConnectionProfile>? = null,
        initialProfiles: List<TranslationProfile>? = null,
        initialCatalogSeed: ProfileCatalog? = null,
        routing: CredentialRoutingState = CredentialRoutingState(),
        enabledFlow: MutableStateFlow<Boolean>? = null,
        setEnabled: (suspend (Boolean) -> Unit)? = null,
    ) {
        val connection =
            ConnectionProfile(
                "00000000-0000-0000-0000-000000000071",
                "Connection",
                TranslationProtocol.OPENAI_CHAT,
                "https://example.test/v1",
                ProfileAuth.NONE,
            )
        val first = TranslationProfile(
            "00000000-0000-0000-0000-000000000072",
            "First",
            connection.id,
            ModelSelection("manual-model"),
            TranslationLanguages("English", "Russian"),
        )
        val second = first.copy(id = "00000000-0000-0000-0000-000000000073", name = "Second")
        private val catalog =
            MutableStateFlow(
                ProfileCatalog(
                    connections = initialConnections ?: listOf(connection),
                    profiles = initialProfiles ?: listOf(first, second),
                    credentials = initialCredentials,
                    assignments = listOf(ProfileAssignment(ProfileAssignmentScope.GLOBAL, 0, first.id)),
                ),
            )
        private val routingState = MutableStateFlow(routing)
        private val run = TranslationRun(
            1, 21, 11, if (implicit) "" else "batch", 0, "{\"snapshotVersion\":2}", status,
            50, null, 0, 0, 0, generation = 7, implicitOwner = implicit, actionPaused = pauseReason != null,
            actionPauseReason = pauseReason,
        )
        val runsFlow = MutableStateFlow(initialRuns ?: listOf(run))
        val fragmentsMap = mutableMapOf<String, List<TranslationFragment>>()
        private val runs = mockk<TranslationRunRepository> {
            every { observeRuns(any()) } returns runsFlow
            coEvery { getFragments(any()) } coAnswers {
                val r = firstArg<TranslationRun>()
                fragmentsMap["${r.id}:${r.generation}"].orEmpty()
            }
        }
        val operations = mockk<ProfileModelOperations>()
        val credentialStore = mockk<CredentialStore>(relaxUnitFun = true)
        val queue = mockk<TranslationQueueManager>(relaxUnitFun = true) {
            every { repository } returns runs
            every { observeProgress() } returns emptyFlow()
        }
        val repo = mockk<TranslationProfileRepository>(relaxUnitFun = true) {
            every { observeCatalog() } returns catalog
            every { observeRoutingState() } returns routingState
            coEvery { readRoutingState() } answers { routingState.value }
            coEvery { saveCredentialMetadata(any()) } coAnswers {
                val meta = firstArg<CredentialMetadata>()
                catalog.value = catalog.value.copy(
                    credentials = catalog.value.credentials.filterNot { it.id == meta.id } + meta,
                )
            }
            coEvery { getProfile(any()) } coAnswers
                { catalog.value.profiles.firstOrNull { it.id == firstArg<String>() } }
            coEvery { getConnection(any()) } coAnswers
                { catalog.value.connections.firstOrNull { it.id == firstArg<String>() } }
            coEvery { saveProfile(any()) } coAnswers {
                val profile = firstArg<TranslationProfile>()
                catalog.value = catalog.value.copy(
                    profiles = catalog.value.profiles.filterNot { it.id == profile.id } + profile,
                )
            }
            coEvery { saveConnection(any()) } coAnswers {
                val updated = firstArg<ConnectionProfile>()
                catalog.value = catalog.value.copy(
                    connections = catalog.value.connections.filterNot { it.id == updated.id } + updated,
                )
            }
            coEvery { saveAssignment(any()) } coAnswers {
                val assignment = firstArg<ProfileAssignment>()
                catalog.value = catalog.value.copy(
                    assignments = catalog.value.assignments.filterNot {
                        it.scope == assignment.scope && it.scopeId == assignment.scopeId
                    } + assignment,
                )
            }
        }
        val model =
            AiTranslationSettingsScreenModel(
                repo, credentialStore, queue, operations,
                novels = mockk {
                    coEvery { getNovelById(any()) } returns Novel.create().copy(id = 11, title = "Novel")
                },
                chapters = mockk { coEvery { getChapterById(any()) } returns null },
                json = Json { ignoreUnknownKeys = true },
                scope = scope, io = io, clock = { 100 }, novelId = 11, chapterId = 21, sourceId = 31,
                observeAiTranslationEnabled = enabledFlow,
                setAiTranslationEnabled = setEnabled,
                initialCatalog = initialCatalogSeed,
            )
    }
}
