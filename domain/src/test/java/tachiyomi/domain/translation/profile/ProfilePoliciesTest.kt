package tachiyomi.domain.translation.profile

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.TranslationProfilesTest.Companion.KEY_A
import tachiyomi.domain.translation.profile.TranslationProfilesTest.Companion.KEY_B
import tachiyomi.domain.translation.profile.TranslationProfilesTest.Companion.connection

class ProfilePoliciesTest {
    private fun ready() = CredentialRoutingState(
        health = mapOf(KEY_A to CredentialHealth(), KEY_B to CredentialHealth()),
    )

    @Test
    fun `priority and round robin choose eligible approved refs without changing a model or connection`() {
        val primary = connection(listOf(CredentialBinding(KEY_A, priority = 10), CredentialBinding(KEY_B)))
        val selected = CredentialRoutingPolicy.route(
            primary,
            "exact-model",
            ready(),
            0,
        ) as CredentialRouteDecision.Selected
        selected.credentialId shouldBe KEY_A
        val roundRobin = connection().copy(routingMode = CredentialRoutingMode.ROUND_ROBIN)
        val first = CredentialRoutingPolicy.route(
            roundRobin,
            "exact-model",
            ready(),
            0,
        ) as CredentialRouteDecision.Selected
        val second = CredentialRoutingPolicy.route(
            roundRobin,
            "exact-model",
            first.nextState,
            0,
        ) as CredentialRouteDecision.Selected
        val third = CredentialRoutingPolicy.route(
            roundRobin,
            "exact-model",
            second.nextState,
            0,
        ) as CredentialRouteDecision.Selected
        listOf(first.credentialId, second.credentialId, third.credentialId) shouldBe listOf(KEY_A, KEY_B, KEY_A)
        CredentialRoutingPolicy.route(connection(), "exact-model", CredentialRoutingState(), 0) shouldBe
            CredentialRouteDecision.NeedsAction
    }

    @Test
    fun `401 disables only its credential ref and does not authorize an automatic retry`() {
        val profile = connection()
        val state = CredentialRoutingPolicy.onFailure(
            profile,
            "model",
            KEY_A,
            RoutingFailure.CREDENTIAL_REJECTED,
            0,
            ready(),
            0,
        )
        state.health.getValue(KEY_A).status shouldBe CredentialHealthStatus.NEEDS_ACTION
        state.health.getValue(KEY_B).status shouldBe CredentialHealthStatus.READY
        ProfileRetryPolicy.next(
            ExecutionPolicy(),
            TranslationFailure(TranslationFailureKind.AUTHORIZATION, "safe"),
            1,
            0,
            0,
        ) shouldBe
            ProfileRetryDecision.Stop
    }

    @Test
    fun `429 blocks a shared quota group rather than rotating keys of the same project`() {
        val profile = connection()
        val state = CredentialRoutingPolicy.onFailure(
            profile,
            "model",
            KEY_A,
            RoutingFailure.GROUP_LIMITED,
            10_000,
            ready(),
            0,
        )
        CredentialRoutingPolicy.quotaGroup(profile, "model", profile.credentialBindings[0]) shouldBe
            CredentialRoutingPolicy.quotaGroup(profile, "model", profile.credentialBindings[1])
        CredentialRoutingPolicy.route(profile, "model", state, 1) shouldBe CredentialRouteDecision.Waiting(10_000)
        val separateConnection = profile.copy(id = "00000000-0000-0000-0000-000000000003")
        CredentialRoutingPolicy.route(separateConnection, "model", state, 1) shouldBe
            CredentialRouteDecision.Waiting(10_000)
        (
            CredentialRoutingPolicy.route(
                profile,
                "model",
                state,
                10_000,
            ) as CredentialRouteDecision.Selected
            ).credentialId shouldBe
            KEY_A
    }

    @Test
    fun `only explicit independent quota groups can remain eligible after429 while503 blocks all`() {
        val profile =
            connection(
                listOf(
                    CredentialBinding(KEY_A, 10, quotaGroupLabel = "project-a"),
                    CredentialBinding(KEY_B, quotaGroupLabel = "project-b"),
                ),
            )
        val limited = CredentialRoutingPolicy.onFailure(
            profile,
            "model",
            KEY_A,
            RoutingFailure.GROUP_LIMITED,
            10_000,
            ready(),
            0,
        )
        (
            CredentialRoutingPolicy.route(
                profile,
                "model",
                limited,
                1,
            ) as CredentialRouteDecision.Selected
            ).credentialId shouldBe
            KEY_B
        val unavailable = CredentialRoutingPolicy.onFailure(
            profile,
            "model",
            KEY_A,
            RoutingFailure.SERVICE_UNAVAILABLE,
            20_000,
            limited,
            1,
        )
        CredentialRoutingPolicy.route(profile, "model", unavailable, 2) shouldBe CredentialRouteDecision.Waiting(20_000)
    }

    @Test
    fun `disabled missing and foreign refs do not turn into anonymous fallback`() {
        val profile = connection().copy(credentialBindings = listOf(CredentialBinding(KEY_A, enabled = false)))
        CredentialRoutingPolicy.route(profile, "model", ready(), 0) shouldBe CredentialRouteDecision.NeedsAction
        shouldThrow<IllegalArgumentException> {
            CredentialRoutingPolicy.onFailure(
                profile,
                "model",
                KEY_B,
                RoutingFailure.CREDENTIAL_REJECTED,
                0,
                ready(),
                0,
            )
        }
        val anonymous = connection(emptyList()).copy(auth = ProfileAuth.NONE)
        (
            CredentialRoutingPolicy.route(
                anonymous,
                "model",
                CredentialRoutingState(),
                0,
            ) as CredentialRouteDecision.Selected
            ).credentialId shouldBe
            null
    }

    @Test
    fun `bounded retries stop at eight or ten minutes and honor a longer server minimum`() {
        val failure = TranslationFailure(TranslationFailureKind.TRANSIENT, "safe")
        ProfileRetryPolicy.next(ExecutionPolicy(), failure, 7, 0, 0, 0.0) shouldBe ProfileRetryDecision.RetryAt(30_000)
        ProfileRetryPolicy.next(ExecutionPolicy(), failure, 8, 0, 0) shouldBe ProfileRetryDecision.Stop
        ProfileRetryPolicy.next(ExecutionPolicy(), failure, 1, 0, 600_000) shouldBe ProfileRetryDecision.Stop
        ProfileRetryPolicy.next(ExecutionPolicy(), failure.copy(retryAfterAt = 90_000), 1, 0, 0, 0.0) shouldBe
            ProfileRetryDecision.RetryAt(90_000)
        ProfileRetryPolicy.next(ExecutionPolicy(ProfileRetryMode.OFF), failure, 1, 0, 0) shouldBe
            ProfileRetryDecision.Stop
    }

    @Test
    fun `service waiting is explicit paced and never makes structural repairs unbounded`() {
        val policy = ExecutionPolicy(ProfileRetryMode.WAIT_FOR_SERVICE)
        val transient = TranslationFailure(TranslationFailureKind.TRANSIENT, "safe")
        ProfileRetryPolicy.next(policy, transient, 1000, 0, 86_400_000, 0.0) shouldBe
            ProfileRetryDecision.RetryAt(86_700_000)
        ProfileRetryPolicy.next(policy, transient, 1000, 0, 86_400_000, 1.0) shouldBe
            ProfileRetryDecision.RetryAt(87_300_000)
        ProfileRetryPolicy.next(
            policy,
            TranslationFailure(TranslationFailureKind.INVALID_RESPONSE, "safe"),
            3,
            0,
            0,
        ) shouldBe
            ProfileRetryDecision.Stop
    }

    @Test
    fun `request reservations are charged before send and lost responses never refund or grant a duplicate`() {
        val policy = ExecutionPolicy(maxRequests = 1)
        val first = RequestBudgetPolicy.reserve(
            policy,
            RequestBudgetLedger("run-1"),
            "run-1",
            "attempt-1",
            setOf("fragment-1"),
            100,
        ) as RequestReservationDecision.Reserved
        first.ledger.reservedRequestIds shouldBe setOf("attempt-1")
        first.ledger.reservedTokens shouldBe 100L
        RequestBudgetPolicy.reserve(policy, first.ledger, "run-1", "attempt-1", setOf("fragment-1"), 100) shouldBe
            RequestReservationDecision.AlreadyReserved(first.ledger)
        RequestBudgetPolicy.reserve(policy, first.ledger, "run-1", "attempt-2", setOf("fragment-1"), 100) shouldBe
            RequestReservationDecision.Blocked(RequestBudgetBlock.REQUEST_LIMIT)
    }

    @Test
    fun `successful fragments are not sent or charged anew and ledger scope prevents cross chapter reuse`() {
        val completed = RequestBudgetPolicy.markComplete(RequestBudgetLedger("run-1"), setOf("done"))
        RequestBudgetPolicy.reserve(ExecutionPolicy(), completed, "run-1", "request-1", setOf("done")) shouldBe
            RequestReservationDecision.NoWork(completed)
        val mixed = RequestBudgetPolicy.reserve(
            ExecutionPolicy(),
            completed,
            "run-1",
            "request-1",
            setOf("done", "pending"),
        ) as RequestReservationDecision.Reserved
        mixed.pendingFragmentIds shouldBe setOf("pending")
        mixed.ledger.reservedRequestIds.size shouldBe 1
        shouldThrow<IllegalArgumentException> {
            RequestBudgetPolicy.reserve(ExecutionPolicy(), completed, "run-2", "request-1", setOf("done"))
        }
    }

    @Test
    fun `reserved token limits require a declared bound and do not pretend to be a money ceiling`() {
        val policy = ExecutionPolicy(maxReservedTokens = 150)
        val ledger = RequestBudgetLedger("run-1")
        RequestBudgetPolicy.reserve(policy, ledger, "run-1", "request-1", setOf("a")) shouldBe
            RequestReservationDecision.Blocked(RequestBudgetBlock.UNKNOWN_TOKEN_USAGE)
        val first = RequestBudgetPolicy.reserve(
            policy,
            ledger,
            "run-1",
            "request-1",
            setOf("a"),
            100,
        ) as RequestReservationDecision.Reserved
        RequestBudgetPolicy.reserve(policy, first.ledger, "run-1", "request-2", setOf("b"), 100) shouldBe
            RequestReservationDecision.Blocked(RequestBudgetBlock.TOKEN_LIMIT)
        val unknown = RequestBudgetLedger("run-1", hasUnknownTokenReservations = true)
        RequestBudgetPolicy.reserve(policy, unknown, "run-1", "request-1", setOf("a"), 100) shouldBe
            RequestReservationDecision.Blocked(RequestBudgetBlock.UNKNOWN_TOKEN_USAGE)
    }

    @Test
    fun `isLocalEndpoint recognizes only plain http private or loopback literals`() {
        ProfileEndpointPolicy.isLocalEndpoint("http://localhost:11434/api") shouldBe true
        ProfileEndpointPolicy.isLocalEndpoint("http://127.0.0.1:8080/v1") shouldBe true
        ProfileEndpointPolicy.isLocalEndpoint("http://192.168.1.50:11434") shouldBe true
        ProfileEndpointPolicy.isLocalEndpoint("http://[::1]:11434/api") shouldBe true
        ProfileEndpointPolicy.isLocalEndpoint("https://localhost:11434/api") shouldBe false
        ProfileEndpointPolicy.isLocalEndpoint("http://remote.example/v1") shouldBe false
        ProfileEndpointPolicy.isLocalEndpoint("http://user@localhost:11434/api") shouldBe false
        ProfileEndpointPolicy.isLocalEndpoint("http://localhost:11434/api?q=1") shouldBe false
        ProfileEndpointPolicy.isLocalEndpoint("not a uri") shouldBe false
    }

    @Test
    fun `retry policy honors jitter fraction in bounded and service wait modes and fast waiting in off mode`() {
        val failure = TranslationFailure(TranslationFailureKind.TRANSIENT, "safe")
        val minBounded = ProfileRetryPolicy.next(ExecutionPolicy(), failure, 1, 0, 0, 0.0)
            as ProfileRetryDecision.RetryAt
        val maxBounded = ProfileRetryPolicy.next(ExecutionPolicy(), failure, 1, 0, 0, 1.0)
            as ProfileRetryDecision.RetryAt
        (maxBounded.notBeforeAt > minBounded.notBeforeAt) shouldBe true

        val waiting = TranslationFailure(
            TranslationFailureKind.WAITING,
            "rotatable",
            retryAfterAt = 5_000,
            attempted = false,
        )
        ProfileRetryPolicy.next(ExecutionPolicy(ProfileRetryMode.OFF), waiting, 0, 0, 100) shouldBe
            ProfileRetryDecision.RetryAt(5_000)
        ProfileRetryPolicy.next(ExecutionPolicy(ProfileRetryMode.OFF), waiting, 1, 0, 100) shouldBe
            ProfileRetryDecision.Stop
    }
}
