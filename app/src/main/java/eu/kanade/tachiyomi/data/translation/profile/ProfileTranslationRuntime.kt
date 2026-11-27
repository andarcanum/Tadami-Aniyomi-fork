package eu.kanade.tachiyomi.data.translation.profile

import eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.ui.reader.novel.translation.PreparedProfileRequest
import eu.kanade.tachiyomi.ui.reader.novel.translation.ProfileCompatibleApi
import eu.kanade.tachiyomi.ui.reader.novel.translation.ProfileRequestBuilder
import eu.kanade.tachiyomi.ui.reader.novel.translation.SharedTranslationRequests
import eu.kanade.tachiyomi.ui.reader.novel.translation.asObjectOrNull
import eu.kanade.tachiyomi.ui.reader.novel.translation.buildNovelTranslationUserPrompt
import eu.kanade.tachiyomi.ui.reader.novel.translation.escapeTranslationSource
import eu.kanade.tachiyomi.ui.reader.novel.translation.profileConfigurationFailure
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveNovelTranslationPromptFamily
import eu.kanade.tachiyomi.ui.reader.novel.translation.validateTranslationCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialRouteDecision
import tachiyomi.domain.translation.profile.CredentialRoutingPolicy
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileRequestReservation
import tachiyomi.domain.translation.profile.ProfileRetryDecision
import tachiyomi.domain.translation.profile.ProfileRetryPolicy
import tachiyomi.domain.translation.profile.RoutingFailure
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import tachiyomi.domain.translation.service.TranslationChunkExecutor
import tachiyomi.domain.translation.service.TranslationPassResult
import tachiyomi.domain.translation.service.TranslationRequestGate
import java.util.UUID
import kotlin.random.Random

/** One authority for frozen profile dispatch, live credentials and durable pre-HTTP reservations. */
class ProfileTranslationRuntime internal constructor(
    private val repository: TranslationProfileRepository,
    private val credentials: CredentialStore,
    private val api: ProfileCompatibleApi,
    private val gate: TranslationRequestGate = SharedTranslationRequests.gate,
    private val clock: () -> Long = System::currentTimeMillis,
    private val requestId: () -> String = { UUID.randomUUID().toString() },
    private val jitter: () -> Double = { Random.nextDouble() },
) {
    internal suspend fun translateFragments(
        run: TranslationRun,
        snapshot: TranslationQueueProfileSnapshot,
        fragments: List<TranslationFragment>,
        beforeRequest: suspend () -> Boolean,
        checkpoint: suspend (List<TranslationFragment>) -> Unit,
    ): TranslationPassResult = withContext(Dispatchers.IO) {
        val connection = snapshot.connectionProfile ?: profileConfigurationFailure()
        val profile = snapshot.translationProfile ?: profileConfigurationFailure()
        if (snapshot.snapshotVersion != 2 || profile.connectionId != connection.id ||
            snapshot.systemPromptSnapshot == null
        ) {
            profileConfigurationFailure()
        }
        verifyCredentialAvailability(connection)
        val modelId = snapshot.wireModelId()
        val admissionKey = CredentialRoutingPolicy.serviceBucket(connection, modelId)
        gate.configure(admissionKey, connection.maxConcurrency)
        val executor = TranslationChunkExecutor(clock = clock, nextAttemptAt = { failure, attempts, started, now ->
            (
                ProfileRetryPolicy.next(
                    profile.execution,
                    failure,
                    attempts,
                    started,
                    now,
                    jitter().coerceIn(0.0, 1.0),
                ) as? ProfileRetryDecision.RetryAt
                )
                ?.notBeforeAt
        })
        executor.execute(
            fragments,
            snapshot.geminiBatchSize.coerceIn(1, 80),
            snapshot.profileMaxRequestChars(),
            connection.maxConcurrency,
            beforeRequest = beforeRequest,
            request = { error("Profile execution requires exact fragment identities") },
            requestFragments = { batch ->
                dispatchBatch(run, snapshot, connection, profile, modelId, admissionKey, batch, beforeRequest)
            },
            checkpoint = { changes ->
                // Checkpoint acknowledgement precedes ledger completion;
                // a crash here cannot pay completed chunks again.
                checkpoint(changes)
                val verified = changes.filter { it.isComplete }.map { it.id.toString() }.toSet()
                if (verified.isNotEmpty() && !repository.markCompletedFragments(run.id, run.generation, verified)) {
                    admissionFailure(
                        TranslationFailureKind.STORAGE,
                        "Could not acknowledge the verified fragment budget ledger",
                    )
                }
            },
        )
    }

    private suspend fun dispatchBatch(
        run: TranslationRun,
        snapshot: TranslationQueueProfileSnapshot,
        connection: ConnectionProfile,
        profile: TranslationProfile,
        modelId: String,
        admissionKey: String,
        batch: List<TranslationFragment>,
        beforeRequest: suspend () -> Boolean,
    ): List<String?> {
        val prepared = snapshot.profileRequest(batch.map { it.sourceText })
        val maxFailovers = connection.credentialBindings.count { it.enabled }.coerceAtLeast(1)
        val triedCredentials = mutableSetOf<String>()
        var failovers = 0
        while (true) {
            val id = requestId()
            var selected: ProfileRequestReservation.Reserved? = null
            try {
                return api.dispatch(
                    connection,
                    prepared,
                    batch.size,
                    resolveCredential = {
                        val reserved =
                            selected ?: admissionFailure(
                                TranslationFailureKind.STORAGE,
                                "Request was not reserved",
                            )
                        reserved.credentialId?.let { requiredCredential(it) }
                    },
                    quotaKeyOverride = admissionKey,
                    beforeAttempt = {
                        if (!beforeRequest()) waiting(clock() + 1_000, "Translation owners are paused")
                        val wanted = batch.map { it.id.toString() }.toSet()
                        when (
                            val reserved = repository.reserveRequest(
                                run.id,
                                run.generation,
                                connection,
                                modelId,
                                profile.execution,
                                id,
                                wanted,
                                prepared.tokenUpperEstimate(),
                                clock(),
                            )
                        ) {
                            is ProfileRequestReservation.Reserved -> {
                                if (reserved.pendingFragmentIds != wanted) {
                                    admissionFailure(
                                        TranslationFailureKind.STORAGE,
                                        "Verified fragment ledger does not match checkpoints",
                                    )
                                }
                                selected = reserved
                            }
                            is ProfileRequestReservation.Waiting -> waiting(
                                reserved.notBeforeAt,
                                "Shared quota group is cooling down",
                            )
                            ProfileRequestReservation.NeedsAction ->
                                admissionFailure(
                                    TranslationFailureKind.AUTHORIZATION,
                                    "Connection credentials need attention",
                                )
                            is ProfileRequestReservation.BudgetStop ->
                                admissionFailure(
                                    TranslationFailureKind.QUOTA,
                                    "Configured request or token reservation budget is exhausted",
                                )
                            is ProfileRequestReservation.AlreadyReserved ->
                                admissionFailure(
                                    TranslationFailureKind.STORAGE,
                                    "This request was already reserved; it cannot be dispatched twice",
                                )
                            is ProfileRequestReservation.NoWork ->
                                admissionFailure(
                                    TranslationFailureKind.STORAGE,
                                    "Completed fragment ledger needs checkpoint recovery",
                                )
                            ProfileRequestReservation.Stale -> throw CancellationException(
                                "Translation generation is no longer current",
                            )
                        }
                    },
                )
            } catch (error: TranslationAttemptException) {
                val reserved = selected
                if (reserved != null) {
                    reserved.credentialId?.let(triedCredentials::add)
                    val routeFailure = when (error.failure.kind) {
                        TranslationFailureKind.AUTHORIZATION -> RoutingFailure.CREDENTIAL_REJECTED
                        TranslationFailureKind.RATE_LIMIT -> RoutingFailure.GROUP_LIMITED
                        TranslationFailureKind.TRANSIENT -> RoutingFailure.SERVICE_UNAVAILABLE
                        else -> null
                    }
                    if (routeFailure != null) {
                        val now = clock()
                        val attempts = batch.maxOf { it.attempts } + if (error.failure.attempted) 1 else 0
                        val started = batch.mapNotNull { it.firstFailureAt }.minOrNull() ?: now
                        val recoveryDue = (
                            ProfileRetryPolicy.next(
                                profile.execution,
                                error.failure,
                                attempts,
                                started,
                                now,
                                jitter().coerceIn(0.0, 1.0),
                            ) as? ProfileRetryDecision.RetryAt
                            )?.notBeforeAt ?: now + 2_000
                        // A waiting fragment must also cool its whole service/quota bucket, otherwise
                        // another still-pending fragment can dispatch a paid probe after only two seconds.
                        repository.recordRoutingFailure(
                            connection,
                            modelId,
                            reserved.credentialId,
                            routeFailure,
                            maxOf(error.failure.retryAfterAt ?: 0, recoveryDue),
                            now,
                        )
                    }
                    if (routeFailure == RoutingFailure.CREDENTIAL_REJECTED ||
                        routeFailure == RoutingFailure.GROUP_LIMITED
                    ) {
                        val next = CredentialRoutingPolicy.route(
                            connection,
                            modelId,
                            repository.readRoutingState(),
                            clock(),
                        )
                        if (next is CredentialRouteDecision.Selected &&
                            next.credentialId != null &&
                            next.credentialId !in triedCredentials &&
                            failovers < maxFailovers
                        ) {
                            failovers++
                            continue
                        }
                        if (next is CredentialRouteDecision.Waiting) {
                            if (routeFailure == RoutingFailure.CREDENTIAL_REJECTED) {
                                throw TranslationAttemptException(
                                    error.failure.copy(
                                        kind = TranslationFailureKind.WAITING,
                                        message = "Rejected credential disabled; " +
                                            "another configured reference may be used",
                                        retryAfterAt = next.notBeforeAt,
                                        attempted = false,
                                    ),
                                )
                            }
                            throw TranslationAttemptException(
                                error.failure.copy(
                                    retryAfterAt = maxOf(error.failure.retryAfterAt ?: 0L, next.notBeforeAt),
                                ),
                            )
                        }
                    }
                }
                throw error
            }
        }
    }

    private suspend fun verifyCredentialAvailability(connection: ConnectionProfile) {
        if (connection.auth == ProfileAuth.NONE) return
        val state = repository.readRoutingState()
        connection.credentialBindings.filter { it.enabled }.forEach { binding ->
            val stored = try {
                credentials.read(binding.credentialId)
            } catch (
                error: CancellationException,
            ) {
                throw error
            } catch (_: Exception) {
                CredentialRead.Unavailable
            }
            val available = if (stored is CredentialRead.Available) {
                stored.secret.use {
                    runCatching {
                        validateTranslationCredential(it.reveal())
                        it.reveal().isNotBlank()
                    }.getOrDefault(false)
                }
            } else {
                false
            }
            val previous = state.health[binding.credentialId]
            if (!available) {
                repository.setCredentialHealth(
                    binding.credentialId,
                    CredentialHealth(
                        CredentialHealthStatus.NEEDS_ACTION,
                        previous?.notBeforeAt ?: 0,
                    ),
                )
            } else if (previous == null) {
                repository.setCredentialHealth(binding.credentialId, CredentialHealth())
            }
            // A stored key is not evidence that a rejected key/account was fixed. Explicit probe/edit resets it.
        }
    }

    private suspend fun requiredCredential(id: String): CredentialSecret {
        val read = try {
            credentials.read(id)
        } catch (
            error: CancellationException,
        ) {
            throw error
        } catch (_: Exception) {
            CredentialRead.Unavailable
        }
        if (read !is CredentialRead.Available) {
            admissionFailure(
                TranslationFailureKind.AUTHORIZATION,
                "Selected credential is missing or unavailable",
            )
        }
        return read.secret
    }
}

internal fun TranslationQueueProfileSnapshot.wireModelId(): String = if (geminiRequestShape != null) {
    geminiModel
} else {
    translationProfile?.exactModelId ?: profileConfigurationFailure()
}

internal fun TranslationQueueProfileSnapshot.profileMaxRequestChars(): Int {
    val output = translationProfile?.generation?.maxOutputTokens ?: 8_192
    return minOf(8_000, output / 3).coerceAtLeast(32)
}

internal fun TranslationQueueProfileSnapshot.profileRequest(segments: List<String>): PreparedProfileRequest {
    if (geminiRequestShape != null) return nativeGeminiRequest(segments)
    val profile = translationProfile ?: profileConfigurationFailure()
    val connection = connectionProfile ?: profileConfigurationFailure()
    val tagged = segments.mapIndexed { index, text -> "<s i='$index'>${escapeTranslationSource(text)}</s>" }
        .joinToString("\n")
    val user = buildNovelTranslationUserPrompt(
        profile.languages.source,
        profile.languages.target,
        tagged,
        resolveNovelTranslationPromptFamily(profile.languages.target),
    )
    return ProfileRequestBuilder.build(
        connection,
        profile.model,
        systemPromptSnapshot ?: profileConfigurationFailure(),
        user,
        segments,
    )
}

private fun PreparedProfileRequest.tokenUpperEstimate(): Long? {
    val limits = listOf(
        body["max_tokens"],
        body["max_completion_tokens"],
        body["generationConfig"].asObjectOrNull()?.get("maxOutputTokens"),
        body["options"].asObjectOrNull()?.get("num_predict"),
    )
    val output = limits.firstNotNullOfOrNull { (it as? JsonPrimitive)?.intOrNull } ?: return null
    if (output <= 0) return null
    return body.toString().toByteArray(Charsets.UTF_8).size.toLong() + output
}

private fun admissionFailure(kind: TranslationFailureKind, message: String): Nothing =
    throw TranslationAttemptException(TranslationFailure(kind, message, attempted = false))
private fun waiting(due: Long, message: String): Nothing = throw TranslationAttemptException(
    TranslationFailure(
        TranslationFailureKind.WAITING,
        message,
        retryAfterAt = due,
        attempted = false,
    ),
)
