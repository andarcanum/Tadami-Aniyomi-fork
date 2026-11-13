package tachiyomi.domain.translation.profile

import kotlinx.serialization.Serializable
import tachiyomi.domain.translation.credentials.CredentialIds
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.service.TranslationRetryPolicy
import java.net.URI

/** Parses literals only. DNS/connect-address revalidation belongs to the transport adapter. */
object ProfileEndpointPolicy {
    fun isLocalEndpoint(apiRoot: String): Boolean {
        val uri = runCatching { URI(apiRoot) }.getOrNull() ?: return false
        if (!uri.isAbsolute || uri.isOpaque || uri.host == null ||
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            !uri.scheme.equals("http", ignoreCase = true)
        ) {
            return false
        }
        val port = if (uri.port == -1) 80 else uri.port
        if (port !in 1..65535) return false
        val host = uri.host.lowercase().removeSurrounding("[", "]").removeSuffix(".")
        return localLiteral(host)
    }

    fun origin(apiRoot: String, allowInsecureLocalHTTP: Boolean = false): String {
        requireProfileText(apiRoot, 2048)
        val uri = runCatching { URI(apiRoot) }.getOrNull()
        require(
            uri != null && uri.isAbsolute && !uri.isOpaque && uri.host != null &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null,
        ) { "Invalid API root endpoint" }
        val scheme = uri.scheme.lowercase()
        require(scheme == "https" || scheme == "http") { "Unsupported endpoint scheme" }
        val host = uri.host.lowercase().removeSurrounding("[", "]").removeSuffix(".")
        val port = if (uri.port == -1) if (scheme == "https") 443 else 80 else uri.port
        require(port in 1..65535) { "Invalid endpoint port" }
        if (scheme ==
            "http"
        ) {
            require(allowInsecureLocalHTTP && localLiteral(host)) {
                "Insecure endpoints require explicit local permission"
            }
        }
        val authority = if (host.contains(':')) "[$host]" else host
        return "$scheme://$authority:$port"
    }

    private fun localLiteral(host: String): Boolean {
        if (host == "localhost") return true
        if (!host.contains(':')) return privateIpv4(host)
        val words = ipv6Words(host) ?: return false
        if (words.take(7).all { it == 0 } && words.last() == 1) return true
        if (words.first() in 0xfc00..0xfdff) return true
        return words.take(5).all { it == 0 } && words[5] == 0xffff &&
            privateIpv4(listOf(words[6] shr 8, words[6] and 255, words[7] shr 8, words[7] and 255).joinToString("."))
    }

    private fun ipv6Words(host: String): List<Int>? {
        if ('%' in host) return null // Zone/interface resolution belongs to an explicitly configured adapter.
        val normalized = if ('.' in host) {
            val parts = host.substringAfterLast(':').split('.')
            if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return null
            val numbers = parts.map { it.toInt() }
            host.substringBeforeLast(':') + ":" + ((numbers[0] shl 8) or numbers[1]).toString(16) + ":" +
                ((numbers[2] shl 8) or numbers[3]).toString(16)
        } else {
            host
        }
        val halves = normalized.split("::")
        if (halves.size > 2) return null
        val left = halves[0].split(':').filter(String::isNotEmpty).map { it.toIntOrNull(16) ?: return null }
        val right = if (halves.size ==
            2
        ) {
            halves[1].split(':').filter(String::isNotEmpty).map { it.toIntOrNull(16) ?: return null }
        } else {
            emptyList()
        }
        val padding = 8 - left.size - right.size
        if (padding < 0 || (halves.size == 1 && padding != 0) || (halves.size == 2 && padding == 0)) return null
        return left + List(padding) { 0 } + right
    }

    private fun privateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4 ||
            parts.any { it.isEmpty() || it.any { char -> char !in '0'..'9' } || (it.length > 1 && it.startsWith('0')) }
        ) {
            return false
        }
        val numbers = parts.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false
        return numbers[0] == 127 || numbers[0] == 10 || (numbers[0] == 172 && numbers[1] in 16..31) ||
            (numbers[0] == 192 && numbers[1] == 168)
    }
}

@Serializable
enum class CredentialHealthStatus { READY, NEEDS_ACTION }

/** READY means the ref may be attempted, not that a model/account probe has succeeded. */
@Serializable
data class CredentialHealth(
    val status: CredentialHealthStatus = CredentialHealthStatus.READY,
    val notBeforeAt: Long = 0,
) {
    init {
        require(notBeforeAt >= 0) { "Invalid credential due time" }
    }
}

@Serializable
data class CredentialRoutingState(
    val health: Map<String, CredentialHealth> = emptyMap(),
    val groupNotBeforeAt: Map<String, Long> = emptyMap(),
    val serviceNotBeforeAt: Map<String, Long> = emptyMap(),
    val lastSelectedByConnection: Map<String, String> = emptyMap(),
    val connectionsNeedingAction: Set<String> = emptySet(),
) {
    init {
        health.keys.forEach(CredentialIds::requireValid)
        require(
            groupNotBeforeAt.values.all {
                it >= 0
            } && serviceNotBeforeAt.values.all { it >= 0 },
        ) { "Invalid routing due time" }
        lastSelectedByConnection.keys.forEach(::requireProfileUuid)
        lastSelectedByConnection.values.forEach(CredentialIds::requireValid)
        connectionsNeedingAction.forEach(::requireProfileUuid)
    }
    override fun toString(): String = "CredentialRoutingState(refs=${health.size}, groups=${groupNotBeforeAt.size})"
}

sealed interface CredentialRouteDecision {
    /** A null credential is an explicitly unauthenticated connection, never a plaintext fallback. */
    data class Selected(
        val credentialId: String?,
        val quotaGroup: String,
        val nextState: CredentialRoutingState,
    ) : CredentialRouteDecision
    data class Waiting(val notBeforeAt: Long) : CredentialRouteDecision
    data object NeedsAction : CredentialRouteDecision
}

enum class RoutingFailure { CREDENTIAL_REJECTED, GROUP_LIMITED, SERVICE_UNAVAILABLE }

object CredentialRoutingPolicy {
    fun serviceBucket(connection: ConnectionProfile, exactModelId: String): String {
        requireProfileToken(exactModelId, 200)
        return ProfileEndpointPolicy.origin(connection.baseUrl, connection.allowInsecureLocalHTTP) + "|model:" +
            exactModelId.length +
            ":" +
            exactModelId
    }
    fun quotaGroup(connection: ConnectionProfile, exactModelId: String, binding: CredentialBinding? = null): String {
        val label = binding?.quotaGroupLabel ?: "shared"
        return serviceBucket(connection, exactModelId) + "|quota:" + label.length + ":" + label
    }
    fun route(
        connection: ConnectionProfile,
        exactModelId: String,
        state: CredentialRoutingState,
        now: Long,
    ): CredentialRouteDecision {
        require(now >= 0) { "Invalid routing time" }
        val service = serviceBucket(connection, exactModelId)
        if (connection.id in state.connectionsNeedingAction) return CredentialRouteDecision.NeedsAction
        val commonDue = state.serviceNotBeforeAt[service] ?: 0L
        if (connection.auth == ProfileAuth.NONE) {
            val group = quotaGroup(connection, exactModelId)
            val due = maxOf(commonDue, state.groupNotBeforeAt[group] ?: 0L)
            return if (due >
                now
            ) {
                CredentialRouteDecision.Waiting(due)
            } else {
                CredentialRouteDecision.Selected(null, group, state)
            }
        }
        val ready = connection.credentialBindings.filter {
            it.enabled &&
                state.health[it.credentialId]?.status == CredentialHealthStatus.READY
        }
        if (ready.isEmpty()) return CredentialRouteDecision.NeedsAction
        val dueByRef = ready.associateWith {
            maxOf(
                commonDue,
                state.groupNotBeforeAt[quotaGroup(connection, exactModelId, it)] ?: 0L,
                state.health.getValue(it.credentialId).notBeforeAt,
            )
        }
        val eligible = ready.filter { dueByRef.getValue(it) <= now }
        if (eligible.isEmpty()) return CredentialRouteDecision.Waiting(dueByRef.values.min())
        val highest = eligible.maxOf { it.priority }
        val tier = eligible.filter { it.priority == highest }
        val previous = state.lastSelectedByConnection[connection.id]
        val selected = if (connection.routingMode == CredentialRoutingMode.ROUND_ROBIN) {
            tier[(tier.indexOfFirst { it.credentialId == previous } + 1) % tier.size]
        } else {
            tier.first()
        }
        return CredentialRouteDecision.Selected(
            selected.credentialId,
            quotaGroup(connection, exactModelId, selected),
            state.copy(
                lastSelectedByConnection =
                state.lastSelectedByConnection + (connection.id to selected.credentialId),
            ),
        )
    }

    fun onFailure(
        connection: ConnectionProfile,
        exactModelId: String,
        credentialId: String?,
        failure: RoutingFailure,
        notBeforeAt: Long,
        state: CredentialRoutingState,
        now: Long,
    ): CredentialRoutingState {
        require(now >= 0 && notBeforeAt >= 0) { "Invalid routing time" }
        val binding = credentialId?.let { id ->
            connection.credentialBindings.singleOrNull { it.credentialId == id }
                ?: throw IllegalArgumentException("Credential reference is outside this connection")
        }
        require(
            (connection.auth == ProfileAuth.NONE && credentialId == null) ||
                (connection.auth != ProfileAuth.NONE && binding != null),
        ) { "Invalid credential selection" }
        val service = serviceBucket(connection, exactModelId)
        val due = maxOf(safeProfileAdd(now, 1000), notBeforeAt)
        return when (failure) {
            RoutingFailure.CREDENTIAL_REJECTED -> if (credentialId == null) {
                state.copy(connectionsNeedingAction = state.connectionsNeedingAction + connection.id)
            } else {
                state.copy(
                    health =
                    state.health + (credentialId to CredentialHealth(CredentialHealthStatus.NEEDS_ACTION)),
                )
            }
            RoutingFailure.GROUP_LIMITED -> {
                val group = quotaGroup(connection, exactModelId, binding)
                state.copy(
                    groupNotBeforeAt =
                    state.groupNotBeforeAt + (group to maxOf(due, state.groupNotBeforeAt[group] ?: 0L)),
                )
            }
            RoutingFailure.SERVICE_UNAVAILABLE -> state.copy(
                serviceNotBeforeAt =
                state.serviceNotBeforeAt + (service to maxOf(due, state.serviceNotBeforeAt[service] ?: 0L)),
            )
        }
    }
}

sealed interface ProfileRetryDecision {
    data class RetryAt(val notBeforeAt: Long) : ProfileRetryDecision
    data object Stop : ProfileRetryDecision
}

object ProfileRetryPolicy {
    fun next(
        policy: ExecutionPolicy,
        failure: TranslationFailure,
        attempts: Int,
        firstFailureAt: Long,
        now: Long,
        jitterFraction: Double = 0.5,
    ): ProfileRetryDecision {
        require(
            attempts >= 0 && firstFailureAt >= 0 && now >= 0 && jitterFraction.isFinite() && jitterFraction in 0.0..1.0,
        ) {
            "Invalid retry facts"
        }
        if (failure.kind == TranslationFailureKind.WAITING && !failure.attempted && attempts == 0) {
            return ProfileRetryDecision.RetryAt(maxOf(safeProfileAdd(now, 1000), failure.retryAfterAt ?: 0L))
        }
        if (policy.retryMode == ProfileRetryMode.OFF) return ProfileRetryDecision.Stop
        if (policy.retryMode == ProfileRetryMode.WAIT_FOR_SERVICE &&
            failure.kind in
            setOf(
                TranslationFailureKind.TRANSIENT,
                TranslationFailureKind.RATE_LIMIT,
                TranslationFailureKind.WAITING,
            )
        ) {
            val cooldown =
                policy.waitCooldownMinMillis +
                    ((policy.waitCooldownMaxMillis - policy.waitCooldownMinMillis) * jitterFraction).toLong()
            return ProfileRetryDecision.RetryAt(maxOf(safeProfileAdd(now, cooldown), failure.retryAfterAt ?: 0L))
        }
        val due = TranslationRetryPolicy(policy.maxAttempts, policy.repairAttempts, policy.maxRecoveryMillis, {
            jitterFraction
        })
            .nextAttemptAt(failure, attempts, firstFailureAt, now)
        return due?.let { ProfileRetryDecision.RetryAt(it) } ?: ProfileRetryDecision.Stop
    }
}

/** Caller must persist reservation and route cursor atomically before any HTTP call. */
@Serializable
data class RequestBudgetLedger(
    val scopeId: String,
    val reservedRequestIds: Set<String> = emptySet(),
    val completedFragmentIds: Set<String> = emptySet(),
    val reservedTokens: Long = 0,
    val hasUnknownTokenReservations: Boolean = false,
) {
    init {
        requireProfileToken(scopeId, 200)
        reservedRequestIds.forEach { requireProfileToken(it, 200) }
        completedFragmentIds.forEach { requireProfileToken(it, 200) }
        require(reservedTokens >= 0) { "Invalid reserved token total" }
    }
    override fun toString(): String =
        "RequestBudgetLedger(requests=${reservedRequestIds.size}, completed=${completedFragmentIds.size})"
}

enum class RequestBudgetBlock { REQUEST_LIMIT, TOKEN_LIMIT, UNKNOWN_TOKEN_USAGE, OVERFLOW }
sealed interface RequestReservationDecision {
    data class Reserved(
        val ledger: RequestBudgetLedger,
        val pendingFragmentIds: Set<String>,
    ) : RequestReservationDecision
    data class AlreadyReserved(val ledger: RequestBudgetLedger) : RequestReservationDecision
    data class NoWork(val ledger: RequestBudgetLedger) : RequestReservationDecision
    data class Blocked(val reason: RequestBudgetBlock) : RequestReservationDecision
}

object RequestBudgetPolicy {
    fun reserve(
        policy: ExecutionPolicy,
        ledger: RequestBudgetLedger,
        scopeId: String,
        requestId: String,
        fragmentIds: Set<String>,
        tokenReservation: Long? = null,
    ): RequestReservationDecision {
        require(scopeId == ledger.scopeId) { "Budget ledger belongs to a different execution" }
        requireProfileToken(requestId, 200)
        fragmentIds.forEach { requireProfileToken(it, 200) }
        require(tokenReservation == null || tokenReservation > 0) { "Invalid reserved token estimate" }
        val pending = fragmentIds - ledger.completedFragmentIds
        if (pending.isEmpty()) return RequestReservationDecision.NoWork(ledger)
        if (requestId in ledger.reservedRequestIds) return RequestReservationDecision.AlreadyReserved(ledger)
        if (policy.maxRequests != null &&
            ledger.reservedRequestIds.size.toLong() >= policy.maxRequests
        ) {
            return RequestReservationDecision.Blocked(RequestBudgetBlock.REQUEST_LIMIT)
        }
        if (policy.maxReservedTokens != null &&
            (tokenReservation == null || ledger.hasUnknownTokenReservations)
        ) {
            return RequestReservationDecision.Blocked(RequestBudgetBlock.UNKNOWN_TOKEN_USAGE)
        }
        val tokens = tokenReservation ?: 0L
        if (ledger.reservedTokens >
            Long.MAX_VALUE - tokens
        ) {
            return RequestReservationDecision.Blocked(RequestBudgetBlock.OVERFLOW)
        }
        val reservedTokens = ledger.reservedTokens + tokens
        if (policy.maxReservedTokens != null &&
            reservedTokens > policy.maxReservedTokens
        ) {
            return RequestReservationDecision.Blocked(RequestBudgetBlock.TOKEN_LIMIT)
        }
        return RequestReservationDecision.Reserved(
            ledger.copy(
                reservedRequestIds =
                ledger.reservedRequestIds + requestId,
                reservedTokens = reservedTokens,
                hasUnknownTokenReservations =
                ledger.hasUnknownTokenReservations || tokenReservation == null,
            ),
            pending,
        )
    }

    /** Only verified checkpoint fragments may be marked; their IDs must belong to this execution scope. */
    fun markComplete(ledger: RequestBudgetLedger, fragmentIds: Set<String>): RequestBudgetLedger {
        fragmentIds.forEach { requireProfileToken(it, 200) }
        return ledger.copy(completedFragmentIds = ledger.completedFragmentIds + fragmentIds)
    }
}

private fun safeProfileAdd(value: Long, delta: Long): Long = if (value >
    Long.MAX_VALUE - delta
) {
    Long.MAX_VALUE
} else {
    value + delta
}
