package eu.kanade.presentation.more.settings.translation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.ExecutionPolicy
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileRetryMode
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException
import java.io.ObjectOutputStream

class AiTranslationUiModelsTest {
    @Test
    fun waitingNeverOffersForceOrRetry() {
        val row = job(AiTranslationJobStatus.WAITING).copy(notBeforeAt = 600_000)
        row.commands() shouldBe listOf(AiTranslationJobCommand.PAUSE, AiTranslationJobCommand.CANCEL)
        row.commands().contains(AiTranslationJobCommand.RETRY_MISSING) shouldBe false
        row.commands().contains(AiTranslationJobCommand.RETRANSLATE) shouldBe false
    }

    @Test
    fun pausedOffersResumeAndOwnCancel() {
        job(AiTranslationJobStatus.PAUSED).commands() shouldBe
            listOf(AiTranslationJobCommand.RESUME, AiTranslationJobCommand.CANCEL)
        AiTranslationAction.RequestJobCommand("run-1", AiTranslationJobCommand.CANCEL).scope shouldBe
            AiTranslationJobScope.OWNED
    }

    @Test
    fun incompleteCoverageCannotLookComplete() {
        val partial = job(AiTranslationJobStatus.COMPLETED).copy(percent = 100, verifiedCount = 1, totalCount = 3)
        partial.effectiveStatus shouldBe AiTranslationJobStatus.PARTIAL
        partial.displayPercent shouldBe 99
        val complete = partial.copy(verifiedCount = 3)
        complete.effectiveStatus shouldBe AiTranslationJobStatus.COMPLETED
        complete.displayPercent shouldBe 100
        job(AiTranslationJobStatus.RUNNING).copy(percent = 100).displayPercent shouldBe 99
    }

    @Test
    fun authorizationFailureRequiresConnectionAction() {
        val row = job(AiTranslationJobStatus.FAILED).copy(errorClass = TranslationFailureKind.AUTHORIZATION)
        row.needsAction shouldBe true
        row.commands() shouldBe emptyList()
    }

    @Test
    fun retryHintsRespectFutureMinimum() {
        aiTranslationWaitMinutes(60_001, 0) shouldBe 2L
        aiTranslationWaitMinutes(60_000, 0) shouldBe 1L
        aiTranslationWaitMinutes(1000, 1000) shouldBe null
        (aiTranslationWaitMinutes(Long.MAX_VALUE, 0)!! > 0) shouldBe true
    }

    @Test
    fun endpointAndCredentialDraftErrorsStaySafe() {
        val bad = AiConnectionDraft(ID, "Name", baseUrl = "https://user:secret@example.com/v1?key=secret")
        bad.toDomainOrNull() shouldBe null
        bad.errors().contains(AiTranslationFormError.ENDPOINT) shouldBe true
        bad.toString().contains("secret") shouldBe false
        val local = AiConnectionDraft(ID, "Local", baseUrl = "http://127.0.0.1:11434/api")
        local.toDomainOrNull() shouldBe null
        local.copy(allowInsecureLocalHTTP = true, maxConcurrency = 4).toDomainOrNull()?.maxConcurrency shouldBe 4
        AiCredentialBindingDraft("raw-key", priority = "invalid").toDomainOrNull() shouldBe null
    }

    @Test
    fun modelAndNumericDraftValidationUsesDeclaredFields() {
        AiProfileDraft(ID, "Name", CONNECTION).errors().contains(AiTranslationFormError.MODEL) shouldBe true
        val model = ModelSelection(
            "model",
            generation = GenerationSettings(maxOutputTokens = 8192),
            outputTokenField = OutputTokenField.MAX_TOKENS,
        )
        val draft = AiProfileDraft(
            ID,
            "Name",
            CONNECTION,
            model = model,
            supportedFields = setOf(AiGenerationField.OUTPUT_TOKENS),
            maxOutputTokens = "not-a-number",
        )
        draft.toDomainOrNull() shouldBe null
        draft.copy(maxOutputTokens = "4096").toDomainOrNull()?.generation?.maxOutputTokens shouldBe 4096
    }

    @Test
    fun manualModelsOmitUnconfirmedGeneration() {
        val previous = AiProfileDraft(
            ID,
            "Name",
            CONNECTION,
            model = ModelSelection("known"),
            supportedFields = setOf(AiGenerationField.TEMPERATURE),
            temperature = "1",
        )
        val changed = previous.selectModel(AiTranslationModelUi(ModelSelection("manual-exact-id"), "Manual"))
        changed.supportedFields shouldBe emptySet()
        changed.toDomainOrNull()?.generation shouldBe GenerationSettings()
        changed.model?.reasoningContract shouldBe ReasoningContract.Unknown
    }

    @Test
    fun reasoningBudgetDoesNotGuessOrClamp() {
        val model = ModelSelection("budget-model", ReasoningContract.Budget(128, 32768, canUseDynamic = true))
        val draft = AiProfileDraft(
            ID,
            "Name",
            CONNECTION,
            model = model,
            reasoning = ReasoningChoice.Budget(128),
            reasoningBudget = "0",
        )
        draft.toDomainOrNull() shouldBe null
        draft.copy(reasoningBudget = "-1").toDomainOrNull()?.generation?.reasoning shouldBe ReasoningChoice.Budget(-1)
    }

    @Test
    fun recoveryBudgetAndParallelismStayValidated() {
        AiRecoveryDraft(maxRequests = "0").toDomainOrNull() shouldBe null
        AiRecoveryDraft(maxRequests = "bad").toDomainOrNull() shouldBe null
        AiRecoveryDraft(parallelism = 9).toDomainOrNull() shouldBe null
        val policy = AiRecoveryDraft(ExecutionPolicy(ProfileRetryMode.WAIT_FOR_SERVICE), maxRequests = "100")
            .toDomainOrNull()!!
        policy.maxRequests shouldBe 100L
        policy.repairAttempts shouldBe 3
        AiRecoveryDraft(maxRequests = "").toDomainOrNull()?.maxRequests shouldBe null
    }

    @Test
    fun passwordDraftIsEphemeralAndNotSavedState() {
        val draft = AiCredentialSecretDraft()
        draft.canSubmit(null, "Alias") shouldBe false
        draft.update("fake-secret")
        draft.canSubmit(null, "Alias") shouldBe true
        draft.toString().contains("fake-secret") shouldBe false
        shouldThrow<NotSerializableException> {
            ObjectOutputStream(ByteArrayOutputStream()).use { it.writeObject(draft) }
        }
        draft.takeAndClear() shouldBe "fake-secret"
        draft.value shouldBe ""
        draft.update("another-secret")
        draft.clear()
        draft.value shouldBe ""
        draft.canSubmit(ID, "Alias") shouldBe true // Metadata-only edit keeps the existing encrypted key.
    }

    @Test
    fun invalidSecretsAndUnmaskedMetadataAreRejected() {
        val draft = AiCredentialSecretDraft()
        draft.update("bad" + 10.toChar() + "key")
        draft.canSubmit(null, "Alias") shouldBe false
        shouldThrow<IllegalArgumentException> { AiTranslationCredentialUi(ID, "Alias", "full-unmasked-key") }
    }

    @Test
    fun profileDraftPreservesConfiguredGenerationFieldsAndDiscoveredContract() {
        val profile = TranslationProfile(
            id = ID,
            name = "Custom",
            connectionId = CONNECTION,
            model = ModelSelection(
                exactModelId = "custom-model",
                reasoningContract = ReasoningContract.Levels(listOf("minimal", "low", "medium", "high")),
                generation = GenerationSettings(
                    temperature = 0.6,
                    topP = 0.9,
                    topK = 40,
                    maxOutputTokens = 4096,
                    reasoning = ReasoningChoice.Level("low"),
                ),
                outputTokenField = OutputTokenField.MAX_TOKENS,
            ),
            languages = TranslationLanguages("English", "Russian"),
        )
        val draft = AiProfileDraft.from(profile, discoveredContract = ReasoningContract.Unknown)
        draft.supportedFields shouldBe setOf(
            AiGenerationField.TEMPERATURE,
            AiGenerationField.TOP_P,
            AiGenerationField.TOP_K,
            AiGenerationField.OUTPUT_TOKENS,
        )
        draft.discoveredReasoningContract shouldBe ReasoningContract.Unknown
        draft.pendingParallelism shouldBe null
        val updated = draft.copy(
            temperature = "0.7",
            pendingParallelism = 4,
            model = draft.model?.copy(
                reasoningContract = ReasoningContract.Toggle,
                generation = draft.model.generation.copy(reasoning = ReasoningChoice.Toggle(true)),
            ),
            reasoning = ReasoningChoice.Toggle(true),
        )
        updated.pendingParallelism shouldBe 4
        val saved = updated.toDomainOrNull()!!
        saved.generation.temperature shouldBe 0.7
        saved.generation.maxOutputTokens shouldBe 4096
        saved.model.reasoningContract shouldBe ReasoningContract.Toggle
        saved.generation.reasoning shouldBe ReasoningChoice.Toggle(true)

        val reverted = updated.copy(
            model = updated.model?.copy(
                reasoningContract = ReasoningContract.Unknown,
                generation = updated.model.generation.copy(reasoning = ReasoningChoice.ApiDefault),
            ),
            reasoning = ReasoningChoice.ApiDefault,
        )
        reverted.toDomainOrNull()?.model?.reasoningContract shouldBe ReasoningContract.Unknown
        reverted.toDomainOrNull()?.generation?.reasoning shouldBe ReasoningChoice.ApiDefault
    }

    @Test
    fun connectionDraftPreservesPresetFlavorAndConcurrency() {
        val connection = ConnectionProfile(
            id = CONNECTION,
            name = "OpenRouter",
            protocol = TranslationProtocol.OPENAI_CHAT,
            baseUrl = "https://openrouter.ai/api/v1",
            auth = ProfileAuth.BEARER,
            presetFlavor = PresetFlavor.OPENROUTER,
            maxConcurrency = 6,
        )
        val draft = AiConnectionDraft.from(connection)
        draft.presetFlavor shouldBe PresetFlavor.OPENROUTER
        draft.maxConcurrency shouldBe 6
        draft.toDomainOrNull() shouldBe connection
    }

    companion object {
        const val ID = "00000000-0000-0000-0000-000000000001"
        const val CONNECTION = "00000000-0000-0000-0000-000000000002"
        fun job(status: AiTranslationJobStatus) = AiTranslationJobUi(
            "run-1", 1L, 2L, title = "Book", chapterLabel = "Chapter", profileLabel = "Profile", status = status,
            verifiedCount = 1, totalCount = 3,
        )
    }
}
