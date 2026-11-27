package eu.kanade.tachiyomi.data.translation.profile

import eu.kanade.presentation.more.settings.translation.AiGenerationField
import eu.kanade.presentation.more.settings.translation.AiTranslationModelUi
import eu.kanade.presentation.more.settings.translation.ProfileModelOperations
import eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiModelEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiModelMetadataStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiThinkingContract
import eu.kanade.tachiyomi.ui.reader.novel.translation.ProfileCompatibleApi
import eu.kanade.tachiyomi.ui.reader.novel.translation.ProfileRequestBuilder
import eu.kanade.tachiyomi.ui.reader.novel.translation.SharedTranslationRequests
import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationHttpTransport
import eu.kanade.tachiyomi.ui.reader.novel.translation.asArrayOrNull
import eu.kanade.tachiyomi.ui.reader.novel.translation.asObjectOrNull
import eu.kanade.tachiyomi.ui.reader.novel.translation.asStringOrNull
import eu.kanade.tachiyomi.ui.reader.novel.translation.isPublicGoogleModelsOrigin
import eu.kanade.tachiyomi.ui.reader.novel.translation.isSupportedGeminiTextModelId
import eu.kanade.tachiyomi.ui.reader.novel.translation.parseTranslationPayload
import eu.kanade.tachiyomi.ui.reader.novel.translation.profileApiRootUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveGeminiModelCapabilities
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveTranslationReasoningOptions
import eu.kanade.tachiyomi.ui.reader.novel.translation.validateTranslationCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialRouteDecision
import tachiyomi.domain.translation.profile.CredentialRoutingPolicy
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileEndpointPolicy
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.RoutingFailure
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import tachiyomi.domain.translation.profile.TranslationProtocol

/** Explicit, bounded catalogue/probe operations share the runtime's fixed-origin one-shot transport. */
internal class ProfileModelOperationsImpl(
    client: OkHttpClient,
    private val json: Json,
    private val credentials: CredentialStore,
    private val repository: TranslationProfileRepository? = null,
) : ProfileModelOperations {
    private val transport = TranslationHttpTransport(client, json)
    private val api = ProfileCompatibleApi(client, json, transport)

    override suspend fun models(connection: ConnectionProfile): List<AiTranslationModelUi> = withContext(
        Dispatchers.IO,
    ) {
        ProfileEndpointPolicy.origin(connection.baseUrl, connection.allowInsecureLocalHTTP)
        val apiRoot = profileApiRootUrl(connection)
        val root = apiRoot.newBuilder()
            .addPathSegment(if (connection.protocol == TranslationProtocol.OLLAMA_NATIVE) "tags" else "models").build()
        val all = mutableListOf<JsonObject>()
        val seen = mutableSetOf<String>()
        var cursor: String? = null
        var after: String? = null
        var selected: String? = null
        for (page in 0 until 100) {
            val url = root.newBuilder().apply {
                if (connection.protocol == TranslationProtocol.GEMINI_GENERATE) {
                    addQueryParameter("pageSize", "1000")
                    cursor?.let { addQueryParameter("pageToken", it) }
                } else {
                    cursor?.let { addQueryParameter("page_token", it) }
                    after?.let { addQueryParameter("after", it) }
                }
            }.build()
            var carrier: CredentialSecret? = null
            val raw = try {
                transport.execute(
                    GET(url),
                    "Model catalogue",
                    "",
                    CredentialRoutingPolicy.serviceBucket(connection, "catalogue"),
                ) { admitted ->
                    selected = selectProbeCredential(connection, "catalogue")
                    carrier = selected?.let { requiredCredential(it) }
                    admitted.newBuilder().headers(headers(connection, carrier?.reveal().orEmpty())).build()
                }
            } catch (error: TranslationAttemptException) {
                recordFailure(connection, "catalogue", selected, error.failure)
                throw error
            } finally {
                carrier?.close()
            }
            val payload = parseTranslationPayload(raw, json, "Model catalogue")
            val array = when (connection.protocol) {
                TranslationProtocol.OPENAI_CHAT -> payload["data"]
                else -> payload["models"]
            } as? JsonArray ?: invalidCatalogue()
            all += array.map { it as? JsonObject ?: invalidCatalogue() }
            if (all.size > 2_000) invalidCatalogue()
            val next = payload["nextPageToken"].asStringOrNull() ?: payload["next_page_token"].asStringOrNull()
            val hasMore = (payload["has_more"] as? JsonPrimitive)?.booleanOrNull == true
            cursor = next?.takeIf(String::isNotBlank)
            after = if (cursor == null && hasMore) {
                payload["last_id"].asStringOrNull()
                    ?: array.lastOrNull().asObjectOrNull()?.get("id").asStringOrNull() ?: invalidCatalogue()
            } else {
                null
            }
            val token = cursor ?: after
            if (token == null) {
                val rows = catalogueRows(connection, all)
                repository?.resetServiceHealth(connection, "catalogue", selected, System.currentTimeMillis())
                return@withContext rows
            }
            if (token.length > 2_048 || token.any(Char::isISOControl) || !seen.add(token)) invalidCatalogue()
        }
        invalidCatalogue()
    }

    override suspend fun probe(connection: ConnectionProfile, model: ModelSelection?): Boolean {
        if (model == null) {
            models(connection)
            return true
        }
        val prepared = ProfileRequestBuilder.build(
            connection,
            model,
            "Return the source block unchanged using <s i='0'>OK</s>.",
            "<s i='0'>OK</s>",
            listOf("OK"),
        )
        return dispatchProbe(connection, model.exactModelId, prepared, 1).singleOrNull()?.isNotBlank() == true
    }

    internal suspend fun probeTranslation(
        snapshot: TranslationQueueProfileSnapshot,
        segments: List<String>,
    ): List<String?> {
        if (segments.isEmpty()) return emptyList()
        return dispatchProbe(
            snapshot.connectionProfile ?: invalidCatalogue(),
            snapshot.wireModelId(),
            snapshot.profileRequest(segments),
            segments.size,
        )
    }

    internal suspend fun translateConfigured(
        snapshot: TranslationQueueProfileSnapshot,
        segments: List<String>,
    ): List<String?> {
        if (segments.isEmpty()) return emptyList()
        return dispatchProbe(
            snapshot.connectionProfile ?: invalidCatalogue(),
            snapshot.wireModelId(),
            snapshot.profileRequest(segments),
            segments.size,
            explicitProbe = false,
        )
    }

    private suspend fun dispatchProbe(
        connection: ConnectionProfile,
        model: String,
        prepared: eu.kanade.tachiyomi.ui.reader.novel.translation.PreparedProfileRequest,
        count: Int,
        explicitProbe: Boolean = true,
    ): List<String?> = withContext(Dispatchers.IO) {
        val key = CredentialRoutingPolicy.serviceBucket(connection, model)
        SharedTranslationRequests.gate.configure(key, connection.maxConcurrency)
        var selected: String? = null
        try {
            val answer = api.dispatch(connection, prepared, count, resolveCredential = {
                selected?.let { requiredCredential(it) }
            }, quotaKeyOverride = key, beforeAttempt = {
                selected =
                    selectProbeCredential(connection, model, explicitProbe)
            })
            if (answer.size != count || answer.any { it.isNullOrBlank() }) invalidCatalogue()
            repository?.resetServiceHealth(connection, model, selected, System.currentTimeMillis())
            answer
        } catch (error: TranslationAttemptException) {
            recordFailure(connection, model, selected, error.failure)
            throw error
        }
    }

    private suspend fun selectProbeCredential(
        connection: ConnectionProfile,
        model: String,
        explicitProbe: Boolean = true,
    ): String? {
        val stored = repository?.readRoutingState() ?: tachiyomi.domain.translation.profile.CredentialRoutingState()
        // Explicit probe may test a repaired key/action flag, but never bypasses outstanding shared due times.
        val evidence = connection.credentialBindings.associate { binding ->
            binding.credentialId to
                CredentialHealth(notBeforeAt = stored.health[binding.credentialId]?.notBeforeAt ?: 0)
        }
        val state = if (explicitProbe) {
            stored.copy(
                health = stored.health + evidence,
                connectionsNeedingAction = stored.connectionsNeedingAction - connection.id,
            )
        } else {
            stored
        }
        return when (val route = CredentialRoutingPolicy.route(connection, model, state, System.currentTimeMillis())) {
            is CredentialRouteDecision.Selected -> route.credentialId
            is CredentialRouteDecision.Waiting -> throw TranslationAttemptException(
                TranslationFailure(
                    TranslationFailureKind.WAITING,
                    "Shared quota group is cooling down",
                    retryAfterAt = route.notBeforeAt,
                    attempted = false,
                ),
            )
            CredentialRouteDecision.NeedsAction -> throw TranslationAttemptException(
                TranslationFailure(
                    TranslationFailureKind.AUTHORIZATION,
                    "Connection needs a credential",
                    attempted = false,
                ),
            )
        }
    }

    private suspend fun requiredCredential(id: String): CredentialSecret {
        val value = try {
            credentials.read(id)
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (_: Exception) {
            CredentialRead.Unavailable
        }
        if (value !is CredentialRead.Available) {
            throw TranslationAttemptException(
                TranslationFailure(
                    TranslationFailureKind.AUTHORIZATION,
                    "Connection credential is unavailable",
                    attempted = false,
                ),
            )
        }
        try {
            validateTranslationCredential(value.secret.reveal())
        } catch (
            error: Exception,
        ) {
            value.secret.close()
            throw error
        }
        return value.secret
    }

    private suspend fun recordFailure(
        connection: ConnectionProfile,
        model: String,
        id: String?,
        failure: TranslationFailure,
    ) {
        val kind = when (failure.kind) {
            TranslationFailureKind.AUTHORIZATION -> RoutingFailure.CREDENTIAL_REJECTED
            TranslationFailureKind.RATE_LIMIT -> RoutingFailure.GROUP_LIMITED
            TranslationFailureKind.TRANSIENT -> RoutingFailure.SERVICE_UNAVAILABLE
            else -> return
        }
        if (connection.auth != ProfileAuth.NONE && id == null) return
        repository?.recordRoutingFailure(
            connection,
            model,
            id,
            kind,
            maxOf(failure.retryAfterAt ?: 0, System.currentTimeMillis() + 2_000),
            System.currentTimeMillis(),
        )
    }

    private fun catalogueRows(connection: ConnectionProfile, objects: List<JsonObject>): List<AiTranslationModelUi> {
        if (connection.protocol == TranslationProtocol.GEMINI_GENERATE) {
            val descriptors = objects.map { item ->
                val id = item["name"].asStringOrNull()?.removePrefix("models/") ?: invalidCatalogue()
                val methods = item["supportedGenerationMethods"].asArrayOrNull()?.map {
                    it.asStringOrNull() ?: invalidCatalogue()
                }.orEmpty()
                GeminiModelEntry(
                    id, item["displayName"].asStringOrNull() ?: id,
                    integer(item, "inputTokenLimit"), integer(item, "outputTokenLimit"),
                    decimal(item, "temperature"), decimal(item, "maxTemperature"), decimal(item, "topP"),
                    integer(
                        item,
                        "topK",
                        positive = false,
                    ),
                    (item["thinking"] as? JsonPrimitive)?.booleanOrNull, methods,
                )
            }.filter {
                (it.supportedGenerationMethods.isEmpty() || "generateContent" in it.supportedGenerationMethods) &&
                    isSupportedGeminiTextModelId(it.id)
            }
            val publicGoogle = profileApiRootUrl(connection).isPublicGoogleModelsOrigin()
            if (publicGoogle) GeminiModelMetadataStore.recordCompletePublicCatalogue(descriptors)
            return descriptors.map { entry ->
                val caps = resolveGeminiModelCapabilities(entry.id, entry)
                val contract = when (val value = caps?.thinking) {
                    GeminiThinkingContract.Unsupported -> ReasoningContract.None
                    GeminiThinkingContract.Toggle -> ReasoningContract.Toggle
                    is GeminiThinkingContract.Levels -> ReasoningContract.Levels(value.options)
                    is GeminiThinkingContract.TokenBudget -> ReasoningContract.Budget(
                        value.minimum,
                        value.maximum,
                        value.canDisable,
                        value.canUseDynamic,
                    )
                    else -> ReasoningContract.Unknown
                }
                val fields = buildSet {
                    if (entry.temperature != null) add(AiGenerationField.TEMPERATURE)
                    if (entry.topP != null) add(AiGenerationField.TOP_P)
                    if (entry.topK != null) add(AiGenerationField.TOP_K)
                    if (entry.outputTokenLimit != null) add(AiGenerationField.OUTPUT_TOKENS)
                }
                AiTranslationModelUi(
                    ModelSelection(
                        entry.id,
                        contract,
                        GenerationSettings(maxOutputTokens = entry.outputTokenLimit?.coerceAtMost(16_384)),
                        OutputTokenField.MAX_OUTPUT_TOKENS,
                    ),
                    entry.displayName,
                    fields,
                )
            }.distinctBy { it.selection.exactModelId }.sortedBy { it.selection.exactModelId }
        }
        return objects.map { item ->
            val id = (if (connection.protocol == TranslationProtocol.OLLAMA_NATIVE) item["name"] else item["id"])
                .asStringOrNull() ?: invalidCatalogue()
            val parameters = item["supported_parameters"].asArrayOrNull().orEmpty().mapNotNull { it.asStringOrNull() }
            val native = connection.presetFlavor != PresetFlavor.NONE
            val output = when {
                connection.protocol == TranslationProtocol.OLLAMA_NATIVE || native -> OutputTokenField.MAX_TOKENS
                "max_completion_tokens" in parameters -> OutputTokenField.MAX_COMPLETION_TOKENS
                "max_tokens" in parameters -> OutputTokenField.MAX_TOKENS
                else -> OutputTokenField.OMIT
            }
            val provider = when (connection.presetFlavor) {
                PresetFlavor.DEEPSEEK -> NovelTranslationProvider.DEEPSEEK
                PresetFlavor.MISTRAL -> NovelTranslationProvider.MISTRAL
                PresetFlavor.OPENROUTER -> NovelTranslationProvider.OPENROUTER
                PresetFlavor.OLLAMA -> NovelTranslationProvider.OLLAMA_CLOUD
                else -> null
            }
            val options = provider?.let { resolveTranslationReasoningOptions(it, id) }.orEmpty()
            val contract = if (options.isEmpty()) ReasoningContract.Unknown else ReasoningContract.Levels(options)
            val defaultOpenAiSampling = connection.protocol == TranslationProtocol.OPENAI_CHAT && parameters.isEmpty()
            val fields = buildSet {
                if (native || connection.protocol == TranslationProtocol.OLLAMA_NATIVE || defaultOpenAiSampling ||
                    "temperature" in parameters
                ) {
                    add(AiGenerationField.TEMPERATURE)
                }
                if (native || connection.protocol == TranslationProtocol.OLLAMA_NATIVE || defaultOpenAiSampling ||
                    "top_p" in parameters
                ) {
                    add(AiGenerationField.TOP_P)
                }
                if (connection.protocol == TranslationProtocol.OLLAMA_NATIVE) add(AiGenerationField.TOP_K)
                if (output != OutputTokenField.OMIT) add(AiGenerationField.OUTPUT_TOKENS)
            }
            AiTranslationModelUi(ModelSelection(id, contract, outputTokenField = output), id, fields)
        }.distinctBy { it.selection.exactModelId }.sortedBy { it.selection.exactModelId }
    }

    private fun integer(item: JsonObject, field: String, positive: Boolean = true): Int? =
        item[field]?.takeUnless { it is JsonNull }?.let {
            val number = (it as? JsonPrimitive)?.intOrNull ?: invalidCatalogue()
            if (number < if (positive) 1 else 0) invalidCatalogue()
            number
        }
    private fun decimal(item: JsonObject, field: String): Float? =
        item[field]?.takeUnless { it is JsonNull }?.let {
            val number = (it as? JsonPrimitive)?.floatOrNull ?: invalidCatalogue()
            if (!number.isFinite() || number < 0) invalidCatalogue()
            number
        }
    private fun headers(connection: ConnectionProfile, key: String): Headers = Headers.Builder().apply {
        when (connection.auth) {
            ProfileAuth.NONE -> Unit
            ProfileAuth.BEARER -> add("Authorization", "Bearer $key")
            ProfileAuth.X_GOOG_API_KEY -> add("x-goog-api-key", key)
        }
    }.build()
}

private fun invalidCatalogue(): Nothing = throw TranslationAttemptException(
    TranslationFailure(
        TranslationFailureKind.INVALID_RESPONSE,
        "Model catalogue or probe response is invalid or unsupported",
    ),
)
