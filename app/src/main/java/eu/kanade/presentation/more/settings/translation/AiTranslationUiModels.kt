package eu.kanade.presentation.more.settings.translation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tachiyomi.domain.translation.credentials.CredentialIds
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialRoutingMode
import tachiyomi.domain.translation.profile.ExecutionPolicy
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileRetryMode
import tachiyomi.domain.translation.profile.PromptSettings
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol

// These presentation values are not Serializable/Parcelable/SavedState containers.
enum class AiTranslationTab { JOBS, PROFILES, CONNECTIONS }
enum class AiTranslationLoadState { LOADING, READY, ERROR }
enum class AiTranslationJobStatus { QUEUED, RUNNING, WAITING, PAUSED, COMPLETED, PARTIAL, FAILED, CANCELLED }
enum class AiTranslationFragmentStatus { VERIFIED, PENDING, WAITING, FAILED }
enum class AiTranslationProbeStatus { UNKNOWN, RUNNING, VERIFIED, FAILED }
enum class AiTranslationMigrationStatus { UNKNOWN, RUNNING, VERIFIED, FAILED }
enum class AiTranslationJobCommand { DETAILS, OPEN, PAUSE, RESUME, CANCEL, RETRY_MISSING, RETRANSLATE }
enum class AiTranslationJobScope { OWNED, ALL_OWNED }
enum class AiGenerationField { TEMPERATURE, TOP_P, TOP_K, OUTPUT_TOKENS }
enum class AiTranslationFormError { NAME, ENDPOINT, CREDENTIAL, CONNECTION, MODEL, LANGUAGE, GENERATION, BUDGET }

data class AiTranslationFragmentUi(val index: Int, val status: AiTranslationFragmentStatus)

data class AiTranslationJobUi(
    val id: String,
    val novelId: Long,
    val chapterId: Long? = null,
    val batchToken: String? = null,
    val title: String,
    val chapterLabel: String,
    val profileLabel: String,
    val status: AiTranslationJobStatus,
    val percent: Int = 0,
    val verifiedCount: Int = 0,
    val totalCount: Int = 0,
    val notBeforeAt: Long? = null,
    val errorClass: TranslationFailureKind? = null,
    val fragments: List<AiTranslationFragmentUi> = emptyList(),
    val execution: ExecutionPolicy? = null,
    val snapshotProfile: TranslationProfile? = null,
    val snapshotConnection: ConnectionProfile? = null,
) {
    val effectiveStatus: AiTranslationJobStatus
        get() = if (status == AiTranslationJobStatus.COMPLETED &&
            (totalCount <= 0 || verifiedCount != totalCount)
        ) {
            AiTranslationJobStatus.PARTIAL
        } else {
            status
        }
    val displayPercent: Int
        get() = if (effectiveStatus == AiTranslationJobStatus.COMPLETED) 100 else percent.coerceIn(0, 99)
    val needsAction: Boolean
        get() = effectiveStatus in setOf(AiTranslationJobStatus.FAILED, AiTranslationJobStatus.PAUSED) &&
            errorClass in setOf(
                TranslationFailureKind.AUTHORIZATION,
                TranslationFailureKind.CONFIGURATION,
                TranslationFailureKind.QUOTA,
            )
    fun commands(): List<AiTranslationJobCommand> = when (effectiveStatus) {
        AiTranslationJobStatus.QUEUED, AiTranslationJobStatus.RUNNING, AiTranslationJobStatus.WAITING ->
            listOf(AiTranslationJobCommand.PAUSE, AiTranslationJobCommand.CANCEL)
        AiTranslationJobStatus.PAUSED -> listOf(AiTranslationJobCommand.RESUME, AiTranslationJobCommand.CANCEL)
        AiTranslationJobStatus.COMPLETED -> listOf(AiTranslationJobCommand.OPEN, AiTranslationJobCommand.RETRANSLATE)
        AiTranslationJobStatus.PARTIAL, AiTranslationJobStatus.CANCELLED ->
            listOf(AiTranslationJobCommand.RETRY_MISSING)
        AiTranslationJobStatus.FAILED -> if (needsAction) emptyList() else listOf(AiTranslationJobCommand.RETRY_MISSING)
    }
    override fun toString(): String = "AiTranslationJobUi(id=$id, status=$effectiveStatus, progress=$displayPercent)"
}

data class AiTranslationCredentialUi(
    val id: String,
    val alias: String,
    val maskedSuffix: String = "",
    val healthStatus: CredentialHealthStatus? = null,
    val notBeforeAt: Long? = null,
) {
    init {
        require(CredentialIds.isValid(id))
        require(maskedSuffix.length <= 8 && maskedSuffix.none(Char::isISOControl))
    }
    override fun toString(): String = "AiTranslationCredentialUi(id=$id)"
}

data class AiTranslationModelUi(
    val selection: ModelSelection,
    val label: String,
    val supportedGenerationFields: Set<AiGenerationField> = emptySet(),
)

data class AiTranslationUiState(
    val tab: AiTranslationTab = AiTranslationTab.JOBS,
    val jobs: List<AiTranslationJobUi> = emptyList(),
    val profiles: List<TranslationProfile> = emptyList(),
    val connections: List<ConnectionProfile> = emptyList(),
    val credentials: List<AiTranslationCredentialUi> = emptyList(),
    val models: List<AiTranslationModelUi> = emptyList(),
    val defaultProfileId: String? = null,
    val readerProfileId: String? = null,
    val loadState: AiTranslationLoadState = AiTranslationLoadState.READY,
    val errorClass: TranslationFailureKind? = null,
    val catalogLoading: Boolean = false,
    val catalogConnectionId: String? = null,
    val editorSaving: Boolean = false,
    val probeStates: Map<String, AiTranslationProbeStatus> = emptyMap(),
    val migrationStatus: AiTranslationMigrationStatus = AiTranslationMigrationStatus.UNKNOWN,
    val vaultAvailable: Boolean = true,
    val editor: AiTranslationEditor? = null,
    val nowMillis: Long = 0,
    val aiTranslationEnabled: Boolean = true,
)

data class AiTranslationQuickUiState(
    val profile: TranslationProfile?,
    val profiles: List<TranslationProfile> = emptyList(),
    val sourceLabel: String = "",
    val chapterLabel: String = "",
    val job: AiTranslationJobUi? = null,
    val canStart: Boolean = false,
    val loading: Boolean = false,
    val nowMillis: Long = 0,
    val connection: ConnectionProfile? = null,
    val connections: List<ConnectionProfile> = emptyList(),
    val credentials: List<AiTranslationCredentialUi> = emptyList(),
    val models: List<AiTranslationModelUi> = emptyList(),
    val catalogConnectionId: String? = null,
    val catalogLoading: Boolean = false,
    val probeStates: Map<String, AiTranslationProbeStatus> = emptyMap(),
    val activeJobsCount: Int = 0,
    val jobMatchesCurrentProfile: Boolean = true,
)

sealed interface AiTranslationAction {
    data class SelectTab(val tab: AiTranslationTab) : AiTranslationAction
    data class EditConnection(val id: String?, val returnTo: AiProfileDraft? = null) : AiTranslationAction
    data class EditProfile(val id: String?) : AiTranslationAction
    data class EditRecovery(val profileId: String?) : AiTranslationAction
    data class EditorChanged(val editor: AiTranslationEditor) : AiTranslationAction
    data object DismissEditor : AiTranslationAction
    data object Reload : AiTranslationAction
    data class SaveConnection(val connection: ConnectionProfile) : AiTranslationAction
    data class SaveProfile(val profile: TranslationProfile, val parallelism: Int? = null) : AiTranslationAction
    data class DeleteConnection(val id: String) : AiTranslationAction
    data class DeleteProfile(val id: String) : AiTranslationAction
    data class SelectDefaultProfile(val id: String) : AiTranslationAction
    data class SelectCurrentProfile(val id: String) : AiTranslationAction
    data class SetAiTranslationEnabled(val enabled: Boolean) : AiTranslationAction
    data class SetProfileReasoning(
        val profileId: String,
        val contract: ReasoningContract,
        val reasoning: ReasoningChoice,
    ) : AiTranslationAction
    data class UpdateProfileLanguages(
        val profileId: String,
        val source: String,
        val target: String,
    ) : AiTranslationAction
    data class SetProfilePrompt(
        val profileId: String,
        val prompt: PromptSettings,
    ) : AiTranslationAction
    data class SetProfileGeneration(
        val profileId: String,
        val generation: GenerationSettings,
    ) : AiTranslationAction
    data class SetProfileRetryMode(
        val profileId: String,
        val retryMode: ProfileRetryMode,
    ) : AiTranslationAction
    data class SelectProfileModel(
        val profileId: String,
        val selection: ModelSelection,
    ) : AiTranslationAction
    data class OpenProfileModelPicker(val profileId: String) : AiTranslationAction
    data class SetConnectionCredentialEnabled(
        val connectionId: String,
        val credentialId: String,
        val enabled: Boolean,
    ) : AiTranslationAction
    data class TranslateCurrentChapter(
        val profileId: String,
        val forceRetranslate: Boolean = false,
    ) : AiTranslationAction
    data class RefreshModels(val connectionId: String) : AiTranslationAction
    data class TestConnection(val id: String) : AiTranslationAction
    data class RequestJobCommand(
        val jobId: String?,
        val command: AiTranslationJobCommand,
        val scope: AiTranslationJobScope = AiTranslationJobScope.OWNED,
    ) : AiTranslationAction
    data class ConfirmJobCommand(val request: RequestJobCommand) : AiTranslationAction
    data class SaveRecovery(
        val profileId: String?,
        val policy: ExecutionPolicy,
        val connectionId: String?,
        val parallelism: Int,
    ) : AiTranslationAction
    data object OpenAllJobs : AiTranslationAction
    data object OpenFullSettings : AiTranslationAction
    data object ReviewMigration : AiTranslationAction
    data object StartMigration : AiTranslationAction
    data class DeleteCredential(val id: String) : AiTranslationAction
}

sealed interface AiTranslationEditor {
    data class Connection(
        val draft: AiConnectionDraft,
        val returnTo: AiProfileDraft? = null,
    ) : AiTranslationEditor
    data class Profile(val draft: AiProfileDraft) : AiTranslationEditor
    data class Models(
        val profile: AiProfileDraft,
        val query: String = "",
        val applyImmediately: Boolean = false,
    ) : AiTranslationEditor
    data class Credential(
        val id: String?,
        val alias: String,
        val connectionId: String? = null,
        val returnTo: AiConnectionDraft? = null,
        val returnToProfile: AiProfileDraft? = null,
    ) : AiTranslationEditor
    data class Recovery(
        val profileId: String?,
        val connectionId: String?,
        val draft: AiRecoveryDraft,
        val returnTo: AiProfileDraft? = null,
    ) : AiTranslationEditor
    data class JobDetails(val job: AiTranslationJobUi) : AiTranslationEditor
    data class Confirmation(val request: AiTranslationAction.RequestJobCommand) : AiTranslationEditor
    data object Migration : AiTranslationEditor
    data class DeleteConfirmation(val kind: AiTranslationDeleteKind, val id: String) : AiTranslationEditor
}

enum class AiTranslationDeleteKind { CONNECTION, PROFILE, CREDENTIAL }

data class AiCredentialBindingDraft(
    val credentialId: String,
    val enabled: Boolean = true,
    val priority: String = "0",
    val quotaGroupLabel: String = "shared",
) {
    fun toDomainOrNull(): CredentialBinding? = runCatching {
        CredentialBinding(credentialId, priority.toInt(), enabled, quotaGroupLabel)
    }.getOrNull()
}

data class AiConnectionDraft(
    val id: String,
    val name: String = "",
    val protocol: TranslationProtocol = TranslationProtocol.GEMINI_GENERATE,
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    val auth: ProfileAuth = ProfileAuth.X_GOOG_API_KEY,
    val presetFlavor: PresetFlavor = PresetFlavor.NONE,
    val bindings: List<AiCredentialBindingDraft> = emptyList(),
    val routingMode: CredentialRoutingMode = CredentialRoutingMode.PRIORITY,
    val allowInsecureLocalHTTP: Boolean = false,
    val maxConcurrency: Int = 2,
) {
    fun toDomainOrNull(): ConnectionProfile? = runCatching {
        ConnectionProfile(
            id, name, protocol, baseUrl, auth, presetFlavor,
            bindings.map { it.toDomainOrNull() ?: error("Invalid credential metadata") },
            routingMode, allowInsecureLocalHTTP, maxConcurrency,
        )
    }.getOrNull()
    fun errors(): Set<AiTranslationFormError> = buildSet {
        if (name.isBlank() || name.length > 120) add(AiTranslationFormError.NAME)
        if (bindings.any { it.toDomainOrNull() == null }) add(AiTranslationFormError.CREDENTIAL)
        if (toDomainOrNull() == null && isEmpty()) add(AiTranslationFormError.ENDPOINT)
    }
    override fun toString(): String = "AiConnectionDraft(id=$id, protocol=$protocol, bindings=${bindings.size})"
    companion object {
        fun from(profile: ConnectionProfile) = AiConnectionDraft(
            profile.id, profile.name, profile.protocol, profile.baseUrl, profile.auth, profile.presetFlavor,
            profile.credentialBindings.map {
                AiCredentialBindingDraft(it.credentialId, it.enabled, it.priority.toString(), it.quotaGroupLabel)
            },
            profile.routingMode, profile.allowInsecureLocalHTTP, profile.maxConcurrency,
        )
    }
}

data class AiProfileDraft(
    val id: String,
    val name: String = "",
    val connectionId: String = "",
    val model: ModelSelection? = null,
    val sourceLanguage: String = "English",
    val targetLanguage: String = "Russian",
    val modeId: String = "CLASSIC",
    val styleId: String = "PROFESSIONAL",
    val modifiersText: String = "",
    val customText: String = "",
    val temperature: String = "",
    val topP: String = "",
    val topK: String = "",
    val maxOutputTokens: String = "",
    val reasoning: ReasoningChoice = ReasoningChoice.ApiDefault,
    val supportedFields: Set<AiGenerationField> = emptySet(),
    val execution: ExecutionPolicy = ExecutionPolicy(),
    val reasoningBudget: String = (reasoning as? ReasoningChoice.Budget)?.tokens?.toString().orEmpty(),
    val pendingParallelism: Int? = null,
    val discoveredReasoningContract: ReasoningContract? = model?.reasoningContract,
) {
    fun toDomainOrNull(): TranslationProfile? = runCatching {
        val selected = model ?: error("Missing model")
        val previous = selected.generation
        val generation = GenerationSettings(
            if (AiGenerationField.TEMPERATURE in supportedFields) {
                temperature.optionalDouble()
            } else {
                previous.temperature
            },
            if (AiGenerationField.TOP_P in supportedFields) topP.optionalDouble() else previous.topP,
            if (AiGenerationField.TOP_K in supportedFields) topK.optionalInt() else previous.topK,
            if (AiGenerationField.OUTPUT_TOKENS in supportedFields) {
                maxOutputTokens.optionalInt()
            } else {
                previous.maxOutputTokens
            },
            if (reasoning is ReasoningChoice.Budget) {
                ReasoningChoice.Budget(reasoningBudget.toInt())
            } else {
                reasoning
            },
        )
        TranslationProfile(
            id,
            name,
            connectionId,
            selected.copy(generation = generation),
            TranslationLanguages(sourceLanguage, targetLanguage),
            PromptSettings(
                modeId,
                styleId,
                modifiersText.split(',').map(String::trim).filter(String::isNotEmpty),
                customText,
            ),
            execution,
        )
    }.getOrNull()
    fun errors(): Set<AiTranslationFormError> = buildSet {
        if (name.isBlank()) add(AiTranslationFormError.NAME)
        if (!CredentialIds.isValid(connectionId)) add(AiTranslationFormError.CONNECTION)
        if (model == null) add(AiTranslationFormError.MODEL)
        if (sourceLanguage.isBlank() || targetLanguage.isBlank()) add(AiTranslationFormError.LANGUAGE)
        if (toDomainOrNull() == null && isEmpty()) add(AiTranslationFormError.GENERATION)
    }
    fun selectModel(option: AiTranslationModelUi): AiProfileDraft = copy(
        model = option.selection,
        supportedFields = option.supportedGenerationFields,
        temperature = option.selection.generation.temperature?.toString().orEmpty(),
        topP = option.selection.generation.topP?.toString().orEmpty(),
        topK = option.selection.generation.topK?.toString().orEmpty(),
        maxOutputTokens = option.selection.generation.maxOutputTokens?.toString().orEmpty(),
        reasoning = option.selection.generation.reasoning,
        reasoningBudget =
        (option.selection.generation.reasoning as? ReasoningChoice.Budget)
            ?.tokens?.toString().orEmpty(),
        discoveredReasoningContract = option.selection.reasoningContract,
    )
    override fun toString(): String = "AiProfileDraft(id=$id, connection=$connectionId)"
    companion object {
        fun from(
            profile: TranslationProfile,
            fields: Set<AiGenerationField> = emptySet(),
            discoveredContract: ReasoningContract? = null,
        ): AiProfileDraft {
            val preservedFields = buildSet {
                addAll(fields)
                if (profile.generation.temperature != null) add(AiGenerationField.TEMPERATURE)
                if (profile.generation.topP != null) add(AiGenerationField.TOP_P)
                if (profile.generation.topK != null) add(AiGenerationField.TOP_K)
                if (profile.generation.maxOutputTokens != null) add(AiGenerationField.OUTPUT_TOKENS)
            }
            return AiProfileDraft(
                profile.id, profile.name, profile.connectionId, profile.model, profile.languages.source,
                profile.languages.target, profile.prompt.modeId, profile.prompt.styleId,
                profile.prompt.modifierIds.joinToString(", "), profile.prompt.customText,
                profile.generation.temperature?.toString().orEmpty(), profile.generation.topP?.toString().orEmpty(),
                profile.generation.topK?.toString().orEmpty(), profile.generation.maxOutputTokens?.toString().orEmpty(),
                profile.generation.reasoning, preservedFields, profile.execution,
                discoveredReasoningContract = discoveredContract ?: profile.model.reasoningContract,
            )
        }
    }
}

data class AiRecoveryDraft(
    val policy: ExecutionPolicy = ExecutionPolicy(),
    val maxRequests: String = policy.maxRequests?.toString().orEmpty(),
    val maxReservedTokens: String = policy.maxReservedTokens?.toString().orEmpty(),
    val parallelism: Int = 2,
) {
    fun toDomainOrNull(): ExecutionPolicy? = runCatching {
        require(parallelism in 1..8)
        policy.copy(maxRequests = maxRequests.optionalLong(), maxReservedTokens = maxReservedTokens.optionalLong())
    }.getOrNull()
}

/** Owned only by the password editor, never by UiState, SavedState, generic event history or persistence. */
internal class AiCredentialSecretDraft {
    var value: String by mutableStateOf("")
        private set
    fun update(value: String) {
        this.value = value
    }
    fun canSubmit(id: String?, alias: String): Boolean =
        (id == null || CredentialIds.isValid(id)) && alias.isNotBlank() && alias.length <= 120 &&
            alias.none(Char::isISOControl) &&
            (
                (value.isBlank() && id != null) ||
                    (value.isNotBlank() && value.length <= 16384 && value.all { it.code in 32..126 })
                )
    fun takeAndClear(): String? = value.takeIf(String::isNotBlank).also { clear() }
    fun clear() {
        value = ""
    }
    override fun toString(): String = "AiCredentialSecretDraft([redacted])"
}

fun aiTranslationWaitMinutes(notBeforeAt: Long?, nowMillis: Long): Long? {
    if (notBeforeAt == null || notBeforeAt <= nowMillis) return null
    val remaining = notBeforeAt - nowMillis
    return remaining / 60_000 + if (remaining % 60_000 == 0L) 0 else 1
}
private fun String.optionalDouble(): Double? = if (isBlank()) null else toDouble()
private fun String.optionalInt(): Int? = if (isBlank()) null else toInt()
private fun String.optionalLong(): Long? = if (isBlank()) null else toLong()
