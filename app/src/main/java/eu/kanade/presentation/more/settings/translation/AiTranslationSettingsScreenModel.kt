package eu.kanade.presentation.more.settings.translation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.translation.TranslationBatchRequest
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.data.translation.profile.TranslationConfigurationMutationGate
import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiThinkingContract
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveGeminiModelCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.translation.credentials.CredentialIds
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.model.TranslationActionPauseReason
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.CredentialRoutingState
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.PresetFlavor
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

/** Concrete adapter is supplied by the process services; no HTTP or secret is part of UiState. */
interface ProfileModelOperations {
    suspend fun models(connection: ConnectionProfile): List<AiTranslationModelUi>
    suspend fun probe(connection: ConnectionProfile, model: ModelSelection?): Boolean
}

enum class AiTranslationSettingsMessage { VALIDATION, STORAGE, UNAVAILABLE, REFERENCED }
sealed interface AiTranslationSettingsEffect {
    data class Message(val kind: AiTranslationSettingsMessage) : AiTranslationSettingsEffect
    data class OpenChapter(val chapterId: Long) : AiTranslationSettingsEffect
}

private class UiOperationFailure(val kind: AiTranslationSettingsMessage) : IllegalStateException()

class AiTranslationSettingsScreenModel(
    private val repository: TranslationProfileRepository = TranslationProfileServices.repository,
    private val credentials: CredentialStore = TranslationProfileServices.credentials,
    private val queue: TranslationQueueManager = Injekt.get(),
    private val operations: ProfileModelOperations = TranslationProfileServices.models,
    private val novels: NovelRepository = Injekt.get(),
    private val chapters: NovelChapterRepository = Injekt.get(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    scope: CoroutineScope? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val novelId: Long? = null,
    private val chapterId: Long? = null,
    private val sourceId: Long? = null,
    private val initialMigrationStatus: AiTranslationMigrationStatus = AiTranslationMigrationStatus.UNKNOWN,
    private val onCurrentProfileSelected: ((String) -> Unit)? = null,
    private val onStartChapterTranslation: ((profileId: String, forceRetranslate: Boolean) -> Unit)? = null,
    private val removeCredentialMetadata: (suspend (String) -> Boolean)? = null,
    private val ownedRunAction: (suspend (TranslationRun, AiTranslationJobCommand) -> Boolean)? = null,
    private val startMigration: (suspend () -> AiTranslationMigrationStatus)? = null,
    initialAiTranslationEnabled: Boolean = true,
    private val observeAiTranslationEnabled: Flow<Boolean>? = null,
    private val setAiTranslationEnabled: (suspend (Boolean) -> Unit)? = null,
    private val privateUnlocked: Boolean = false,
    initialCatalog: ProfileCatalog? = null,
) : StateScreenModel<AiTranslationUiState>(
    buildInitialUiState(
        initialCatalog = initialCatalog,
        initialMigrationStatus = initialMigrationStatus,
        initialAiTranslationEnabled = initialAiTranslationEnabled,
        novelId = novelId,
        sourceId = sourceId,
    ),
) {
    private val parent = scope ?: screenModelScope
    private val ownerJob = SupervisorJob(parent.coroutineContext[Job])
    private val workScope = CoroutineScope(parent.coroutineContext + ownerJob)
    private val mutableEffects = MutableSharedFlow<AiTranslationSettingsEffect>(extraBufferCapacity = 16)
    val effects = mutableEffects.asSharedFlow()
    private var catalog = initialCatalog?.copySnapshot() ?: ProfileCatalog()
    private var runs = emptyList<TranslationRun>()
    private var catalogJob: Job? = null
    private var runsJob: Job? = null
    private var progressJob: Job? = null
    private var enabledJob: Job? = null
    private var modelsJob: Job? = null
    private var probeJob: Job? = null
    private var modelEpoch = 0L
    private var probeEpoch = 0L
    private var editorEpoch = 0L
    private var disposed = false

    /** Reader projection: the same NOVEL → SOURCE → GLOBAL precedence the dispatcher resolves. */
    val quickState: StateFlow<AiTranslationQuickUiState> = state
        .map { ui -> ui.toQuickUiState(chapterId, privateUnlocked) }
        .stateIn(workScope, SharingStarted.Eagerly, state.value.toQuickUiState(chapterId, privateUnlocked))

    init {
        observe()
        workScope.launch {
            while (isActive) {
                mutableState.update { it.copy(nowMillis = clock()) }
                delay(30_000)
            }
        }
    }

    override fun onDispose() {
        disposed = true
        modelEpoch++
        probeEpoch++
        editorEpoch++
        ownerJob.cancel()
    }

    fun onAction(action: AiTranslationAction) {
        if (disposed) return
        when (action) {
            is AiTranslationAction.SelectTab -> mutableState.update { it.copy(tab = action.tab) }
            is AiTranslationAction.EditorChanged -> changeEditor(action.editor)
            AiTranslationAction.DismissEditor -> changeEditor(null)
            AiTranslationAction.Reload -> observe()
            is AiTranslationAction.EditConnection -> openConnection(action.id, action.returnTo)
            is AiTranslationAction.EditProfile -> openProfile(action.id)
            is AiTranslationAction.EditRecovery -> openRecovery(action.profileId)
            is AiTranslationAction.SaveConnection -> {
                val returnTo = (state.value.editor as? AiTranslationEditor.Connection)?.returnTo
                val openFirstProfile = returnTo == null &&
                    state.value.editor is AiTranslationEditor.Connection &&
                    catalog.profiles.isEmpty() &&
                    catalog.connections.none { it.id == action.connection.id }
                mutate(
                    closeEditor = returnTo == null && !openFirstProfile,
                    afterSuccess = {
                        val nextDraft = returnTo?.copy(
                            connectionId = action.connection.id,
                            model = null,
                            discoveredReasoningContract = null,
                            reasoning = ReasoningChoice.ApiDefault,
                            reasoningBudget = "",
                            supportedFields = emptySet(),
                        ) ?: if (openFirstProfile) {
                            AiProfileDraft(
                                id = UUID.randomUUID().toString(),
                                connectionId = action.connection.id,
                            )
                        } else {
                            null
                        }
                        if (nextDraft != null) {
                            changeEditor(AiTranslationEditor.Profile(nextDraft))
                        }
                    },
                ) {
                    repository.saveConnection(action.connection)
                    invalidateModels()
                }
            }
            is AiTranslationAction.SaveProfile -> {
                val pendingParallelism = action.parallelism
                    ?: (state.value.editor as? AiTranslationEditor.Profile)?.draft?.pendingParallelism
                mutate {
                    if (pendingParallelism != null) {
                        repository.getConnection(action.profile.connectionId)?.let { conn ->
                            repository.saveConnection(conn.copy(maxConcurrency = pendingParallelism))
                        }
                    }
                    repository.saveProfile(action.profile)
                }
            }
            is AiTranslationAction.DeleteProfile -> mutate {
                if (!repository.deleteProfile(action.id)) fail(AiTranslationSettingsMessage.REFERENCED)
            }
            is AiTranslationAction.DeleteConnection -> mutate {
                if (!repository.deleteConnection(action.id)) fail(AiTranslationSettingsMessage.REFERENCED)
                invalidateModels()
            }
            is AiTranslationAction.SelectDefaultProfile -> mutate(closeEditor = false) {
                requireProfile(action.id)
                repository.saveAssignment(ProfileAssignment(ProfileAssignmentScope.GLOBAL, 0, action.id))
            }
            is AiTranslationAction.SelectCurrentProfile -> selectForReader(action.id)
            is AiTranslationAction.SetAiTranslationEnabled -> {
                mutableState.update { it.copy(aiTranslationEnabled = action.enabled) }
                setAiTranslationEnabled?.let { setter ->
                    workScope.launch {
                        try {
                            withContext(io) { setter(action.enabled) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            message(AiTranslationSettingsMessage.STORAGE)
                        }
                    }
                }
            }
            is AiTranslationAction.SetProfileReasoning -> mutate(closeEditor = false) {
                val profile = requireProfile(action.profileId)
                val updatedModel = runCatching {
                    ModelSelection(
                        profile.model.exactModelId,
                        action.contract,
                        profile.model.generation.copy(reasoning = action.reasoning),
                        profile.model.outputTokenField,
                    )
                }.getOrElse { fail(AiTranslationSettingsMessage.VALIDATION) }
                repository.saveProfile(profile.copy(model = updatedModel))
            }
            is AiTranslationAction.UpdateProfileLanguages -> mutate(closeEditor = false) {
                val profile = requireProfile(action.profileId)
                val updated = runCatching {
                    profile.copy(languages = TranslationLanguages(action.source, action.target))
                }.getOrElse { fail(AiTranslationSettingsMessage.VALIDATION) }
                repository.saveProfile(updated)
            }
            is AiTranslationAction.SetProfilePrompt -> mutate(closeEditor = false) {
                val profile = requireProfile(action.profileId)
                val updated = runCatching {
                    profile.copy(prompt = action.prompt)
                }.getOrElse { fail(AiTranslationSettingsMessage.VALIDATION) }
                repository.saveProfile(updated)
            }
            is AiTranslationAction.SetProfileGeneration -> mutate(closeEditor = false) {
                val profile = requireProfile(action.profileId)
                val connection = catalog.connections.firstOrNull { it.id == profile.connectionId }
                val updated = runCatching {
                    val safeTokens = if (profile.model.outputTokenField == OutputTokenField.OMIT) {
                        null
                    } else {
                        action.generation.maxOutputTokens?.also { limit ->
                            if (connection?.protocol == TranslationProtocol.GEMINI_GENERATE) {
                                require(limit in 1..65_536)
                            }
                        }
                    }
                    val safeTopK = if (connection?.protocol == TranslationProtocol.OPENAI_CHAT) {
                        null
                    } else {
                        action.generation.topK
                    }
                    val safeReasoning = action.generation.reasoning.takeIf { choice ->
                        isReasoningCompatible(choice, profile.model.reasoningContract)
                    } ?: profile.model.generation.reasoning
                    val generation = action.generation.copy(
                        topK = safeTopK,
                        maxOutputTokens = safeTokens,
                        reasoning = safeReasoning,
                    )
                    profile.copy(model = profile.model.copy(generation = generation))
                }.getOrElse { fail(AiTranslationSettingsMessage.VALIDATION) }
                repository.saveProfile(updated)
            }
            is AiTranslationAction.SetProfileRetryMode -> mutate(closeEditor = false) {
                val profile = requireProfile(action.profileId)
                repository.saveProfile(profile.copy(execution = profile.execution.copy(retryMode = action.retryMode)))
            }
            is AiTranslationAction.OpenProfileModelPicker -> {
                val entity = catalog.profiles.firstOrNull { it.id == action.profileId }
                if (entity == null) {
                    unavailable()
                } else {
                    changeEditor(
                        AiTranslationEditor.Models(
                            profile = buildProfileDraft(entity),
                            applyImmediately = true,
                        ),
                    )
                }
            }
            is AiTranslationAction.SelectProfileModel -> mutate(
                closeEditor = state.value.editor is AiTranslationEditor.Models,
            ) {
                val profile = requireProfile(action.profileId)
                val connection = catalog.connections.firstOrNull { it.id == profile.connectionId }
                val merged = runCatching {
                    mergeModelSelection(profile.model.generation, action.selection, connection?.protocol)
                }.getOrElse { fail(AiTranslationSettingsMessage.VALIDATION) }
                repository.saveProfile(profile.copy(model = merged))
            }
            is AiTranslationAction.SetConnectionCredentialEnabled -> mutate(closeEditor = false) {
                val connection = repository.getConnection(action.connectionId)
                    ?: fail(AiTranslationSettingsMessage.VALIDATION)
                val updatedBindings = connection.credentialBindings.map { binding ->
                    if (binding.credentialId == action.credentialId) {
                        binding.copy(enabled = action.enabled)
                    } else {
                        binding
                    }
                }
                if (updatedBindings == connection.credentialBindings) {
                    fail(AiTranslationSettingsMessage.VALIDATION)
                }
                repository.saveConnection(connection.copy(credentialBindings = updatedBindings))
            }
            is AiTranslationAction.TranslateCurrentChapter -> translateCurrentChapter(
                action.profileId,
                action.forceRetranslate,
            )
            is AiTranslationAction.RefreshModels -> loadModels(action.connectionId)
            is AiTranslationAction.TestConnection -> probe(action.id)
            is AiTranslationAction.SaveRecovery -> saveRecovery(action)
            is AiTranslationAction.RequestJobCommand -> {
                if (action.command in setOf(AiTranslationJobCommand.CANCEL, AiTranslationJobCommand.RETRANSLATE)) {
                    changeEditor(AiTranslationEditor.Confirmation(action))
                } else {
                    jobCommand(action)
                }
            }
            is AiTranslationAction.ConfirmJobCommand -> jobCommand(action.request)
            AiTranslationAction.OpenAllJobs -> mutableState.update {
                it.copy(tab = AiTranslationTab.JOBS, editor = null)
            }
            AiTranslationAction.OpenFullSettings -> mutableState.update {
                it.copy(tab = AiTranslationTab.PROFILES, editor = null)
            }
            AiTranslationAction.ReviewMigration -> changeEditor(AiTranslationEditor.Migration)
            AiTranslationAction.StartMigration -> runMigration()
            is AiTranslationAction.DeleteCredential -> deleteCredential(action.id)
        }
    }

    private fun observe() {
        catalogJob?.cancel()
        runsJob?.cancel()
        progressJob?.cancel()
        enabledJob?.cancel()
        catalogJob = workScope.launch {
            try {
                combine(repository.observeCatalog(), repository.observeRoutingState()) { received, routing ->
                    received to routing
                }.collectLatest { (received, routing) ->
                    val scopeId = state.value.catalogConnectionId
                    if (scopeId != null && catalog.connections.firstOrNull { it.id == scopeId } !=
                        received.connections.firstOrNull { it.id == scopeId }
                    ) {
                        invalidateModels()
                    }
                    catalog = received.copySnapshot()
                    mutableState.update { current ->
                        catalog.applyTo(current, routing, novelId, sourceId)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(loadState = AiTranslationLoadState.ERROR) }
                message(AiTranslationSettingsMessage.STORAGE)
            }
        }
        runsJob = workScope.launch {
            try {
                queue.repository.observeRuns().collectLatest { received ->
                    runs = received.toList()
                    val rows = withContext(io) {
                        runs.filter {
                            it.implicitOwner ||
                                !it.batchToken.startsWith("prefetch-")
                        }
                            .map { run -> row(run) }
                    }
                    mutableState.update { it.copy(jobs = rows, nowMillis = clock()) }
                }
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (_: Exception) {
                message(AiTranslationSettingsMessage.STORAGE)
            }
        }
        progressJob = workScope.launch {
            try {
                queue.observeProgress().collect {
                    mutableState.update { current -> current.copy(nowMillis = clock()) }
                }
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (_: Exception) {
                message(AiTranslationSettingsMessage.STORAGE)
            }
        }
        if (observeAiTranslationEnabled != null) {
            enabledJob = workScope.launch {
                try {
                    observeAiTranslationEnabled.collectLatest { enabled ->
                        mutableState.update { it.copy(aiTranslationEnabled = enabled) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun row(run: TranslationRun): AiTranslationJobUi {
        val title = runCatching { novels.getNovelById(run.novelId).title }.getOrNull().orEmpty()
        val chapter = runCatching { chapters.getChapterById(run.chapterId)?.name }.getOrNull().orEmpty()
        val snapshot = snapshotProfile(run.profileSnapshotJson)
        val snapshotConn = snapshotConnection(run.profileSnapshotJson)
        val complete = run.status == TranslationRunStatus.COMPLETE &&
            !run.sourceFingerprint.isNullOrBlank() && run.sourceSegmentCount > 0
        val fragments = if (complete) {
            emptyList()
        } else {
            runCatching { queue.repository.getFragments(run) }.getOrNull().orEmpty()
        }
        val verifiedCount = if (complete) run.sourceSegmentCount else fragments.count { it.isComplete }
        val totalCount = if (complete) run.sourceSegmentCount else fragments.size
        return AiTranslationJobUi(
            runKey(run), run.novelId, run.chapterId, run.batchToken.takeIf(String::isNotBlank),
            title, chapter, snapshot?.name ?: snapshotLabel(run.profileSnapshotJson), status(run.status), run.progress,
            verifiedCount, totalCount,
            run.nextAttemptAt.takeIf { it > 0 },
            execution = snapshot?.execution,
            snapshotProfile = snapshot,
            snapshotConnection = snapshotConn,
            errorClass = when (run.actionPauseReason) {
                TranslationActionPauseReason.NEEDS_ACTION -> TranslationFailureKind.AUTHORIZATION
                TranslationActionPauseReason.BUDGET_LIMIT -> TranslationFailureKind.QUOTA
                TranslationActionPauseReason.CONFIGURATION -> TranslationFailureKind.CONFIGURATION
                null -> null
            },
        )
    }

    private fun snapshotProfile(raw: String?): TranslationProfile? {
        if (raw == null) return null
        return runCatching {
            val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
            val profile = root["translationProfile"] ?: root["profile"] ?: return@runCatching null
            json.decodeFromString<TranslationProfile>(profile.toString())
        }.getOrNull()
    }

    private fun snapshotConnection(raw: String?): ConnectionProfile? {
        if (raw == null) return null
        return runCatching {
            val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
            val connection = root["connectionProfile"] ?: root["connection"] ?: return@runCatching null
            json.decodeFromString<ConnectionProfile>(connection.toString())
        }.getOrNull()
    }

    private fun snapshotLabel(raw: String?): String {
        if (raw == null) return "—"
        return runCatching {
            snapshotProfile(raw)?.let { return@runCatching it.name }
            val old = json.decodeFromString<TranslationQueueProfileSnapshot>(raw)
            if (old.snapshotVersion < 1) return@runCatching "—"
            old.translationProvider + " · " + when (old.translationProvider) {
                "DEEPSEEK" -> old.deepSeekModel
                "OPENROUTER" -> old.openRouterModel
                "MISTRAL" -> old.mistralModel
                "NVIDIA" -> old.nvidiaModel
                "OLLAMA_CLOUD" -> old.ollamaCloudModel
                else -> old.geminiModel
            }.take(200)
        }.getOrDefault("—")
    }

    private fun AiTranslationEditor?.connectionScopeId(): String? = when (this) {
        is AiTranslationEditor.Connection -> draft.id
        is AiTranslationEditor.Credential -> returnTo?.id ?: returnToProfile?.connectionId
        is AiTranslationEditor.Profile -> draft.connectionId
        is AiTranslationEditor.Models -> profile.connectionId
        is AiTranslationEditor.Recovery -> returnTo?.connectionId
        else -> null
    }

    private fun changeEditor(editor: AiTranslationEditor?) {
        if (state.value.editorSaving) return
        val before = state.value.editor
        editorEpoch++
        val beforeConnection = (before as? AiTranslationEditor.Connection)?.draft
        val nextConnection = (editor as? AiTranslationEditor.Connection)?.draft
        val scopeId = state.value.catalogConnectionId
        val nextScopeId = editor.connectionScopeId()
        val nonConnectionModal = editor is AiTranslationEditor.JobDetails ||
            editor is AiTranslationEditor.Confirmation ||
            editor is AiTranslationEditor.DeleteConfirmation ||
            editor is AiTranslationEditor.Migration
        val scopeInvalidated = scopeId != null &&
            !nonConnectionModal &&
            (editor == null || (nextScopeId != null && nextScopeId != scopeId))
        if ((beforeConnection != null && nextConnection != null && beforeConnection != nextConnection) ||
            scopeInvalidated
        ) {
            invalidateModels()
        }
        mutableState.update { it.copy(editor = editor) }
        if (editor is AiTranslationEditor.JobDetails) loadDetail(editor.job.id)
    }

    private fun openConnection(id: String?, returnTo: AiProfileDraft? = null) {
        val entity = id?.let { value -> catalog.connections.firstOrNull { it.id == value } }
        if (id != null && entity == null) {
            unavailable()
            return
        }
        changeEditor(
            AiTranslationEditor.Connection(
                draft = entity?.let(AiConnectionDraft::from)
                    ?: AiConnectionDraft(UUID.randomUUID().toString()),
                returnTo = returnTo,
            ),
        )
    }

    private fun openProfile(id: String?) {
        val entity = id?.let { value -> catalog.profiles.firstOrNull { it.id == value } }
        if (id != null && entity == null) {
            unavailable()
            return
        }
        changeEditor(
            AiTranslationEditor.Profile(
                entity?.let(::buildProfileDraft)
                    ?: AiProfileDraft(UUID.randomUUID().toString()),
            ),
        )
    }

    private fun buildProfileDraft(entity: TranslationProfile): AiProfileDraft {
        val cached = state.value.models.firstOrNull {
            state.value.catalogConnectionId == entity.connectionId &&
                it.selection.exactModelId == entity.exactModelId
        }
        val connection = catalog.connections.firstOrNull { it.id == entity.connectionId }
        val caps = if (
            cached == null &&
            connection?.protocol == TranslationProtocol.GEMINI_GENERATE
        ) {
            resolveGeminiModelCapabilities(entity.exactModelId)
        } else {
            null
        }
        val discoveredContract = when {
            cached != null -> cached.selection.reasoningContract
            caps != null -> when (val value = caps.thinking) {
                GeminiThinkingContract.Unsupported -> ReasoningContract.None
                GeminiThinkingContract.Toggle -> ReasoningContract.Toggle
                is GeminiThinkingContract.Levels -> ReasoningContract.Levels(value.options)
                is GeminiThinkingContract.TokenBudget -> ReasoningContract.Budget(
                    value.minimum,
                    value.maximum,
                    value.canDisable,
                    value.canUseDynamic,
                )
                GeminiThinkingContract.Unknown -> ReasoningContract.Unknown
            }
            else -> entity.model.reasoningContract
        }
        val hasOutputField = entity.model.outputTokenField != OutputTokenField.OMIT
        val fields = cached?.supportedGenerationFields ?: when (connection?.protocol) {
            TranslationProtocol.GEMINI_GENERATE -> buildSet {
                add(AiGenerationField.TEMPERATURE)
                add(AiGenerationField.TOP_P)
                if (caps?.topK != null) add(AiGenerationField.TOP_K)
                if (hasOutputField) add(AiGenerationField.OUTPUT_TOKENS)
            }
            TranslationProtocol.OLLAMA_NATIVE -> buildSet {
                add(AiGenerationField.TEMPERATURE)
                add(AiGenerationField.TOP_P)
                add(AiGenerationField.TOP_K)
                if (hasOutputField) add(AiGenerationField.OUTPUT_TOKENS)
            }
            TranslationProtocol.OPENAI_CHAT -> buildSet {
                add(AiGenerationField.TEMPERATURE)
                add(AiGenerationField.TOP_P)
                if (hasOutputField) add(AiGenerationField.OUTPUT_TOKENS)
            }
            null -> emptySet()
        }
        return AiProfileDraft.from(entity, fields, discoveredContract)
    }

    private fun mergeModelSelection(
        previous: GenerationSettings,
        next: ModelSelection,
        protocol: TranslationProtocol? = null,
    ): ModelSelection {
        val maxTokens = if (next.outputTokenField == OutputTokenField.OMIT) {
            null
        } else {
            val candidate = previous.maxOutputTokens ?: next.generation.maxOutputTokens
            if (protocol == TranslationProtocol.GEMINI_GENERATE) candidate?.coerceAtMost(65_536) else candidate
        }
        val safeTopK = if (protocol == TranslationProtocol.OPENAI_CHAT) null else previous.topK
        val preservedReasoning = previous.reasoning
            .takeIf { choice ->
                choice != ReasoningChoice.ApiDefault && isReasoningCompatible(choice, next.reasoningContract)
            }
            ?: next.generation.reasoning.takeIf { choice ->
                isReasoningCompatible(choice, next.reasoningContract)
            }
            ?: ReasoningChoice.ApiDefault
        return next.copy(
            generation = previous.copy(
                topK = safeTopK,
                maxOutputTokens = maxTokens,
                reasoning = preservedReasoning,
            ),
        )
    }

    private fun isReasoningCompatible(choice: ReasoningChoice, contract: ReasoningContract): Boolean =
        choice == ReasoningChoice.ApiDefault || when (contract) {
            ReasoningContract.Unknown, ReasoningContract.None -> false
            ReasoningContract.Toggle -> choice is ReasoningChoice.Toggle
            is ReasoningContract.Levels -> choice is ReasoningChoice.Level && choice.value in contract.options
            is ReasoningContract.Budget -> choice is ReasoningChoice.Budget && contract.accepts(choice.tokens)
        }

    private fun openRecovery(id: String?) {
        val targetId = id
            ?: state.value.defaultProfileId
            ?: state.value.readerProfileId
            ?: catalog.profiles.firstOrNull()?.id
        val entity = targetId?.let { value -> catalog.profiles.firstOrNull { it.id == value } }
        if (entity == null) {
            unavailable()
            return
        }
        val connection = catalog.connections.firstOrNull { it.id == entity.connectionId }
        changeEditor(
            AiTranslationEditor.Recovery(
                entity.id,
                entity.connectionId,
                AiRecoveryDraft(entity.execution, parallelism = connection?.maxConcurrency ?: 2),
            ),
        )
    }

    private fun mutate(
        closeEditor: Boolean = true,
        afterSuccess: (() -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        val revision = editorEpoch
        workScope.launch {
            try {
                withContext(io) { TranslationConfigurationMutationGate.mutex.withLock { block() } }
                if (revision == editorEpoch) {
                    if (closeEditor) mutableState.update { it.copy(editor = null) }
                    afterSuccess?.invoke()
                }
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (
                error: UiOperationFailure,
            ) {
                message(error.kind)
            } catch (_: Exception) {
                message(AiTranslationSettingsMessage.STORAGE)
            }
        }
    }

    private suspend fun requireProfile(id: String): TranslationProfile = repository.getProfile(id)
        ?: fail(AiTranslationSettingsMessage.VALIDATION)

    private fun selectForReader(id: String) {
        val context = novelId ?: run {
            unavailable()
            return
        }
        mutate(closeEditor = false) {
            requireProfile(id)
            repository.saveAssignment(ProfileAssignment(ProfileAssignmentScope.NOVEL, context, id))
            // The reader refreshes from the catalog mirror; the callback is only an extra hint.
            onCurrentProfileSelected?.let { callback -> withContext(workScope.coroutineContext) { callback(id) } }
        }
    }

    private fun translateCurrentChapter(profileId: String, forceRetranslate: Boolean = false) {
        val novel = novelId
        val chapter = chapterId
        if (novel == null || chapter == null) {
            unavailable()
            return
        }
        mutate(closeEditor = false) {
            val profile = requireProfile(profileId)
            repository.saveAssignment(ProfileAssignment(ProfileAssignmentScope.NOVEL, novel, profileId))
            val startInReader = onStartChapterTranslation
            if (startInReader != null) {
                withContext(workScope.coroutineContext) { startInReader(profileId, forceRetranslate) }
            } else if (forceRetranslate) {
                val connection = repository.getConnection(profile.connectionId)
                    ?: fail(AiTranslationSettingsMessage.VALIDATION)
                queue.enqueueProfileChapters(
                    chapterIds = listOf(chapter),
                    novelId = novel,
                    profile = profile,
                    connection = connection,
                    rules = emptyList(),
                    forceRetranslate = true,
                )
            } else {
                queue.addToQueue(listOf(chapter), novel)
            }
        }
    }

    private fun runMigration() {
        val start = startMigration ?: run {
            unavailable()
            return
        }
        workScope.launch {
            mutableState.update { it.copy(migrationStatus = AiTranslationMigrationStatus.RUNNING) }
            val status = try {
                withContext(io) { start() }
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (_: Exception) {
                AiTranslationMigrationStatus.FAILED
            }
            mutableState.update { it.copy(migrationStatus = status) }
        }
    }

    private fun saveRecovery(action: AiTranslationAction.SaveRecovery) {
        val parentDraft = (state.value.editor as? AiTranslationEditor.Recovery)?.returnTo
        if (parentDraft != null) {
            changeEditor(
                AiTranslationEditor.Profile(
                    parentDraft.copy(
                        execution = action.policy,
                        pendingParallelism = action.parallelism,
                    ),
                ),
            )
            return
        }
        mutate(closeEditor = true) {
            val connection = action.connectionId?.let { repository.getConnection(it) }
            if (connection != null) repository.saveConnection(connection.copy(maxConcurrency = action.parallelism))
            val profile =
                action.profileId?.let { requireProfile(it) } ?: fail(AiTranslationSettingsMessage.VALIDATION)
            repository.saveProfile(profile.copy(execution = action.policy))
        }
    }

    private fun invalidateModels() {
        modelEpoch++
        modelsJob?.cancel()
        mutableState.update { it.copy(models = emptyList(), catalogConnectionId = null, catalogLoading = false) }
    }

    private suspend fun currentConnection(id: String): ConnectionProfile {
        val draft = (state.value.editor as? AiTranslationEditor.Connection)?.draft?.takeIf { it.id == id }
        if (draft != null) return draft.toDomainOrNull() ?: fail(AiTranslationSettingsMessage.VALIDATION)
        return repository.getConnection(id) ?: fail(AiTranslationSettingsMessage.VALIDATION)
    }

    private fun loadModels(id: String) {
        val epoch = ++modelEpoch
        modelsJob?.cancel()
        mutableState.update { it.copy(catalogLoading = true, catalogConnectionId = id, models = emptyList()) }
        modelsJob = workScope.launch {
            try {
                val result = withContext(io) {
                    operations.models(currentConnection(id))
                }.distinctBy { it.selection.exactModelId }
                if (epoch == modelEpoch && !disposed) {
                    mutableState.update {
                        it.copy(models = result.toList(), catalogConnectionId = id, catalogLoading = false)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: UiOperationFailure) {
                if (epoch ==
                    modelEpoch
                ) {
                    mutableState.update { it.copy(catalogLoading = false) }
                    message(error.kind)
                }
            } catch (_: Exception) {
                if (epoch == modelEpoch) {
                    mutableState.update { it.copy(catalogLoading = false) }
                    message(AiTranslationSettingsMessage.STORAGE)
                }
            }
        }
    }

    private fun probe(id: String) {
        val epoch = ++probeEpoch
        probeJob?.cancel()
        mutableState.update { it.copy(probeStates = it.probeStates + (id to AiTranslationProbeStatus.RUNNING)) }
        probeJob = workScope.launch {
            try {
                val result = withContext(io) {
                    val model = catalog.profiles.firstOrNull { it.connectionId == id }?.model
                    operations.probe(currentConnection(id), model)
                }
                if (epoch == probeEpoch && !disposed) {
                    mutableState.update {
                        it.copy(
                            probeStates = it.probeStates +
                                (
                                    id to
                                        if (result) {
                                            AiTranslationProbeStatus.VERIFIED
                                        } else {
                                            AiTranslationProbeStatus.FAILED
                                        }
                                    ),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (epoch == probeEpoch && !disposed) {
                    mutableState.update {
                        it.copy(
                            probeStates = it.probeStates +
                                (id to AiTranslationProbeStatus.FAILED),
                        )
                    }
                    message(AiTranslationSettingsMessage.STORAGE)
                }
            }
        }
    }

    /** A transient argument only. It is never added to state, effects, a ledger or SavedState. */
    fun saveCredential(id: String?, alias: String, supplied: String?) {
        val editor = state.value.editor as? AiTranslationEditor.Credential
        if (disposed || state.value.editorSaving || editor == null || editor.id != id || editor.alias != alias ||
            alias.isBlank() || alias.length > 120 || alias.any(Char::isISOControl) ||
            (
                supplied != null && (
                    supplied.isBlank() || supplied.length > 16384 || supplied.any {
                        it.code !in 32..126
                    }
                    )
                ) ||
            (id == null && supplied == null) || (id != null && !CredentialIds.isValid(id))
        ) {
            unavailable(AiTranslationSettingsMessage.VALIDATION)
            return
        }
        val keyId = id ?: UUID.randomUUID().toString()
        val revision = editorEpoch
        var transient = supplied
        mutableState.update { it.copy(editorSaving = true) }
        workScope.launch {
            var metadataCommitted = false
            try {
                withContext(io) {
                    TranslationConfigurationMutationGate.mutex.withLock {
                        val value = transient
                        val existing = if (id != null) {
                            repository.getCredentialMetadata(id)
                                ?: fail(AiTranslationSettingsMessage.VALIDATION)
                        } else {
                            null
                        }
                        if (value != null) credentials.put(keyId, value)
                        val suffix = when (val read = credentials.read(keyId)) {
                            is CredentialRead.Available -> read.secret.use { secret ->
                                val verified = secret.reveal()
                                if (value != null && value != verified) fail(AiTranslationSettingsMessage.STORAGE)
                                verified.takeLast(4)
                            }
                            else -> fail(AiTranslationSettingsMessage.STORAGE)
                        }
                        currentCoroutineContext().ensureActive()
                        val previousHealth = repository.readRoutingState().health[keyId]
                        withContext(NonCancellable) {
                            repository.saveCredentialMetadata(
                                CredentialMetadata(
                                    keyId,
                                    alias,
                                    if (value == null) existing?.maskedSuffix ?: suffix else suffix,
                                    clock(),
                                ),
                            )
                            metadataCommitted = true
                            if (value != null) {
                                repository.setCredentialHealth(
                                    keyId,
                                    CredentialHealth(
                                        notBeforeAt = previousHealth?.notBeforeAt ?: 0,
                                    ),
                                )
                            }
                        }
                    }
                }
                if (!disposed && revision == editorEpoch) {
                    mutableState.update {
                        val parent = editor.returnTo
                        val restored = parent?.copy(
                            bindings = if (parent.bindings.any { binding ->
                                    binding.credentialId == keyId
                                }
                            ) {
                                parent.bindings
                            } else {
                                parent.bindings +
                                    AiCredentialBindingDraft(keyId)
                            },
                        )
                        it.copy(
                            editor = restored?.let { next ->
                                AiTranslationEditor.Connection(next, editor.returnToProfile)
                            },
                            vaultAvailable = true,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (id != null && transient != null) {
                    runCatching {
                        withContext(NonCancellable + io) {
                            repository.setCredentialHealth(id, CredentialHealth(CredentialHealthStatus.NEEDS_ACTION))
                        }
                    }
                }
                message(AiTranslationSettingsMessage.STORAGE)
            } finally {
                transient = null // JVM immutable String copies cannot be reliably zeroized.
                if (id == null && !metadataCommitted) {
                    runCatching {
                        withContext(NonCancellable + io) {
                            if (repository.getCredentialMetadata(keyId) == null) credentials.remove(keyId)
                        }
                    }
                }
                if (revision == editorEpoch) mutableState.update { it.copy(editorSaving = false) }
            }
        }
    }

    private fun deleteCredential(id: String) = mutate {
        val guard = removeCredentialMetadata ?: fail(AiTranslationSettingsMessage.UNAVAILABLE)
        if (!guard(id)) fail(AiTranslationSettingsMessage.REFERENCED)
        credentials.remove(id)
    }

    private fun loadDetail(key: String) {
        val run = runs.firstOrNull { runKey(it) == key } ?: return
        val revision = editorEpoch
        workScope.launch {
            try {
                val fragments = withContext(io) { queue.repository.getFragments(run) }
                val details = fragments.mapIndexed { index, fragment ->
                    AiTranslationFragmentUi(
                        index + 1,
                        if (fragment.isComplete) {
                            AiTranslationFragmentStatus.VERIFIED
                        } else if (fragment.nextAttemptAt > clock()) {
                            AiTranslationFragmentStatus.WAITING
                        } else if (fragment.failure !=
                            null
                        ) {
                            AiTranslationFragmentStatus.FAILED
                        } else {
                            AiTranslationFragmentStatus.PENDING
                        },
                    )
                }
                if (revision == editorEpoch) {
                    mutableState.update { current ->
                        val editor = current.editor as? AiTranslationEditor.JobDetails
                        if (editor?.job?.id != key) {
                            current
                        } else {
                            current.copy(
                                editor = editor.copy(
                                    job = editor.job.copy(
                                        fragments = details,
                                        verifiedCount = fragments.count {
                                            it.isComplete
                                        },
                                        totalCount = fragments.size,
                                    ),
                                ),
                            )
                        }
                    }
                }
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (_: Exception) {
                message(AiTranslationSettingsMessage.STORAGE)
            }
        }
    }

    private fun jobCommand(request: AiTranslationAction.RequestJobCommand) = mutate {
        val isBatchScope = request.scope == AiTranslationJobScope.ALL_OWNED
        val selected = if (isBatchScope) {
            runs.filter { run ->
                (run.implicitOwner || !run.batchToken.startsWith("prefetch-")) &&
                    isEligibleForBatchCommand(run, request.command)
            }
        } else {
            listOf(
                runs.firstOrNull { runKey(it) == request.jobId } ?: fail(AiTranslationSettingsMessage.VALIDATION),
            )
        }
        val batchResults = mutableMapOf<String, Boolean>()
        var anySucceeded = false
        selected.forEach { run ->
            val succeeded = when (request.command) {
                AiTranslationJobCommand.OPEN -> {
                    mutableEffects.emit(AiTranslationSettingsEffect.OpenChapter(run.chapterId))
                    true
                }
                AiTranslationJobCommand.DETAILS -> {
                    withContext(workScope.coroutineContext) {
                        state.value.jobs.firstOrNull { it.id == runKey(run) }?.let {
                            changeEditor(AiTranslationEditor.JobDetails(it))
                        }
                    }
                    true
                }
                AiTranslationJobCommand.RETRY_MISSING, AiTranslationJobCommand.RETRANSLATE ->
                    ownedRunAction?.invoke(run, request.command) ?: queue.retryOwnedRun(
                        run.chapterId,
                        run.generation,
                        request.command == AiTranslationJobCommand.RETRANSLATE,
                    )
                AiTranslationJobCommand.RESUME -> when {
                    run.actionPaused -> queue.resumeActionPaused(run.chapterId, run.generation)
                    run.implicitOwner -> ownedRunAction?.invoke(run, request.command)
                        ?: queue.resumeImplicitOwner(run.chapterId, run.generation)
                    run.batchToken.isBlank() -> false
                    else -> batchResults.getOrPut(run.batchToken) { queue.resumeBatch(run.batchToken) }
                }
                else -> if (run.implicitOwner) {
                    ownedRunAction?.invoke(run, request.command) ?: when (request.command) {
                        AiTranslationJobCommand.PAUSE -> queue.pauseImplicitOwner(run.chapterId, run.generation)
                        AiTranslationJobCommand.CANCEL -> queue.detachImplicitOwner(run.chapterId, run.generation)
                        else -> false
                    }
                } else {
                    if (run.batchToken.isBlank()) {
                        false
                    } else {
                        batchResults.getOrPut(run.batchToken) {
                            when (request.command) {
                                AiTranslationJobCommand.PAUSE -> queue.pauseBatch(run.batchToken)
                                AiTranslationJobCommand.RESUME -> queue.resumeBatch(run.batchToken)
                                AiTranslationJobCommand.CANCEL -> queue.cancelBatch(run.batchToken)
                                else -> false
                            }
                        }
                    }
                }
            }
            if (succeeded) {
                anySucceeded = true
            } else if (!isBatchScope) {
                fail(AiTranslationSettingsMessage.UNAVAILABLE)
            }
        }
        if (isBatchScope && selected.isNotEmpty() && !anySucceeded) {
            fail(AiTranslationSettingsMessage.UNAVAILABLE)
        }
    }

    private fun isEligibleForBatchCommand(
        run: TranslationRun,
        command: AiTranslationJobCommand,
    ): Boolean = when (command) {
        AiTranslationJobCommand.PAUSE ->
            run.status == TranslationRunStatus.PENDING ||
                run.status == TranslationRunStatus.RUNNING ||
                run.status == TranslationRunStatus.WAITING
        AiTranslationJobCommand.RESUME ->
            run.actionPaused ||
                run.status == TranslationRunStatus.PAUSED ||
                run.status == TranslationRunStatus.WAITING
        AiTranslationJobCommand.CANCEL ->
            run.actionPaused ||
                run.status == TranslationRunStatus.PENDING ||
                run.status == TranslationRunStatus.RUNNING ||
                run.status == TranslationRunStatus.WAITING ||
                run.status == TranslationRunStatus.PAUSED
        AiTranslationJobCommand.RETRY_MISSING, AiTranslationJobCommand.RETRANSLATE ->
            run.status == TranslationRunStatus.FAILED ||
                run.status == TranslationRunStatus.CANCELLED ||
                run.status == TranslationRunStatus.COMPLETE
        else -> true
    }

    private fun unavailable(kind: AiTranslationSettingsMessage = AiTranslationSettingsMessage.UNAVAILABLE) {
        workScope.launch { message(kind) }
    }
    private suspend fun message(kind: AiTranslationSettingsMessage) {
        if (!disposed) mutableEffects.emit(AiTranslationSettingsEffect.Message(kind))
    }
    private fun fail(kind: AiTranslationSettingsMessage): Nothing = throw UiOperationFailure(kind)
    private fun runKey(run: TranslationRun): String = "run:" + run.id + ":" + run.generation
    private fun status(status: TranslationRunStatus): AiTranslationJobStatus = when (status) {
        TranslationRunStatus.PENDING -> AiTranslationJobStatus.QUEUED
        TranslationRunStatus.RUNNING -> AiTranslationJobStatus.RUNNING
        TranslationRunStatus.COMPLETE -> AiTranslationJobStatus.COMPLETED
        TranslationRunStatus.FAILED -> AiTranslationJobStatus.FAILED
        TranslationRunStatus.CANCELLED -> AiTranslationJobStatus.CANCELLED
        TranslationRunStatus.WAITING -> AiTranslationJobStatus.WAITING
        TranslationRunStatus.PAUSED -> AiTranslationJobStatus.PAUSED
    }
}

private fun ProfileCatalog.copySnapshot(): ProfileCatalog = copy(
    connections = connections.toList(),
    profiles = profiles.toList(),
    credentials = credentials.toList(),
    assignments = assignments.toList(),
)

private fun buildInitialUiState(
    initialCatalog: ProfileCatalog?,
    initialMigrationStatus: AiTranslationMigrationStatus,
    initialAiTranslationEnabled: Boolean,
    novelId: Long?,
    sourceId: Long?,
): AiTranslationUiState {
    val base = AiTranslationUiState(
        loadState = AiTranslationLoadState.LOADING,
        migrationStatus = initialMigrationStatus,
        aiTranslationEnabled = initialAiTranslationEnabled,
    )
    return initialCatalog?.copySnapshot()?.applyTo(base, CredentialRoutingState(), novelId, sourceId) ?: base
}

private fun ProfileCatalog.applyTo(
    current: AiTranslationUiState,
    routing: CredentialRoutingState,
    novelId: Long?,
    sourceId: Long?,
): AiTranslationUiState = current.copy(
    connections = connections,
    profiles = profiles,
    credentials = credentials.map { meta ->
        val health = routing.health[meta.id]
        AiTranslationCredentialUi(
            id = meta.id,
            alias = meta.alias,
            maskedSuffix = meta.maskedSuffix,
            healthStatus = health?.status,
            notBeforeAt = health?.notBeforeAt?.takeIf { it > 0L },
        )
    },
    readerProfileId = assignments.firstOrNull {
        it.scope == ProfileAssignmentScope.NOVEL && it.scopeId == novelId
    }?.profileId ?: assignments.firstOrNull {
        it.scope == ProfileAssignmentScope.SOURCE && it.scopeId == sourceId
    }?.profileId,
    defaultProfileId = assignments.firstOrNull {
        it.scope == ProfileAssignmentScope.GLOBAL
    }?.profileId,
    loadState = AiTranslationLoadState.READY,
)

private fun AiTranslationUiState.toQuickUiState(
    chapterId: Long?,
    privateUnlocked: Boolean = false,
): AiTranslationQuickUiState {
    val profileId = readerProfileId ?: defaultProfileId
    val profile = profiles.firstOrNull { it.id == profileId }
    val connection = profile?.let { selected ->
        connections.firstOrNull { it.id == selected.connectionId }
    }
    val activeStatuses = setOf(
        AiTranslationJobStatus.QUEUED,
        AiTranslationJobStatus.RUNNING,
        AiTranslationJobStatus.WAITING,
        AiTranslationJobStatus.PAUSED,
    )
    val chapterJobs = jobs.filter { it.chapterId == chapterId }
    val job = chapterJobs.lastOrNull {
        it.effectiveStatus in activeStatuses
    } ?: chapterJobs.lastOrNull { it.effectiveStatus != AiTranslationJobStatus.CANCELLED }
        ?: chapterJobs.lastOrNull()
    val jobMatchesCurrentProfile = when {
        job == null -> true
        job.snapshotProfile == null -> true
        profile == null || connection == null -> false
        else -> {
            val snap = job.snapshotProfile
            val conn = job.snapshotConnection
            snap.id == profile.id &&
                snap.connectionId == profile.connectionId &&
                snap.model == profile.model &&
                snap.languages == profile.languages &&
                snap.prompt == profile.prompt &&
                (
                    conn == null ||
                        (
                            conn.id == connection.id &&
                                conn.protocol == connection.protocol &&
                                conn.baseUrl == connection.baseUrl
                            )
                    )
        }
    }
    val privateBlocked = connection != null &&
        connection.presetFlavor == PresetFlavor.GEMINI_PRIVATE &&
        GeminiPrivateBridge.isInstalled() &&
        !GeminiPrivateBridge.isUnlocked() &&
        !privateUnlocked
    return AiTranslationQuickUiState(
        profile = profile,
        profiles = profiles,
        job = job,
        canStart = connection != null &&
            !privateBlocked &&
            (connection.auth == ProfileAuth.NONE || connection.credentialBindings.any { it.enabled }),
        loading = loadState == AiTranslationLoadState.LOADING,
        nowMillis = nowMillis,
        connection = connection,
        connections = connections,
        credentials = credentials,
        models = models,
        catalogConnectionId = catalogConnectionId,
        catalogLoading = catalogLoading,
        probeStates = probeStates,
        activeJobsCount = jobs.count { it.effectiveStatus in activeStatuses },
        jobMatchesCurrentProfile = jobMatchesCurrentProfile,
    )
}
