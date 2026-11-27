package tachiyomi.data.translation.profile

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.data.novel.createTestNovelDatabase
import tachiyomi.data.translation.TranslationRunRepositoryImpl
import tachiyomi.domain.translation.model.TranslationActionPauseReason
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.CredentialRoutingMode
import tachiyomi.domain.translation.profile.ExecutionPolicy
import tachiyomi.domain.translation.profile.LegacyCredentialReference
import tachiyomi.domain.translation.profile.LegacyCredentialScope
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.ProfileAssignment
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.ProfileRequestReservation
import tachiyomi.domain.translation.profile.ProfileStorageException
import tachiyomi.domain.translation.profile.ProfileStorageFailure
import tachiyomi.domain.translation.profile.RequestBudgetBlock
import tachiyomi.domain.translation.profile.RoutingFailure
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol
import tachiyomi.novel.data.NovelDatabase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class TranslationProfileRepositoryImplTest {
    @Test
    fun `nested catalog configuration metadata references and assignments round trip`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val bundle = catalog()
            fixture.repo.importConfiguration(bundle)
            fixture.repo.getCatalog() shouldBe bundle
            fixture.repo.getConnection(CONNECTION) shouldBe bundle.connections.single()
            fixture.repo.getProfile(PROFILE) shouldBe bundle.profiles.single()
            fixture.repo.getCredentialMetadata(KEY_A) shouldBe bundle.credentials[0]
            fixture.repo.getLegacyCredentialReference("GEMINI", LegacyCredentialScope.SOURCE, -42)
                .shouldBeInstanceOf<LegacyCredentialReference>().credentialId shouldBe KEY_A
            fixture.repo.observeCatalog().first() shouldBe bundle
        }
    }

    @Test
    fun `configuration import is idempotent and never infers credential readiness`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.repo.importConfiguration(catalog())
            fixture.repo.importConfiguration(catalog())
            fixture.repo.getCatalog().credentials.size shouldBe 2
            fixture.repo.readRoutingState().health shouldBe emptyMap()
            val run = fixture.running()
            fixture.reserve(run.id, run.generation, "one") shouldBe ProfileRequestReservation.NeedsAction
            fixture.repo.readLedger(run.id, run.generation).reservedRequestIds shouldBe emptySet()
            fixture.repo.setCredentialHealth(KEY_A, CredentialHealth(CredentialHealthStatus.READY))
            fixture.repo.importConfiguration(catalog())
            fixture.repo.readRoutingState().health[KEY_A]?.status shouldBe CredentialHealthStatus.READY
        }
    }

    @Test
    fun `dangling connection profile assignment and legacy references are rejected atomically`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.expectInvalid { fixture.repo.saveProfile(profile()) }
            fixture.expectInvalid { fixture.repo.saveConnection(connection()) }
            fixture.expectInvalid {
                fixture.repo.saveAssignment(ProfileAssignment(ProfileAssignmentScope.NOVEL, 7, PROFILE))
            }
            fixture.expectInvalid {
                fixture.repo.saveLegacyCredentialReference(
                    LegacyCredentialReference("GEMINI", LegacyCredentialScope.GLOBAL, 0, KEY_A),
                )
            }
            fixture.expectInvalid { fixture.repo.importConfiguration(catalog().copy(credentials = emptyList())) }
            fixture.repo.getCatalog() shouldBe ProfileCatalog()
        }
    }

    @Test
    fun `delete profile clears assignments but never credential metadata or vault references`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.repo.importConfiguration(catalog())
            fixture.repo.deleteConnection(CONNECTION) shouldBe false
            fixture.repo.deleteProfile(PROFILE) shouldBe true
            val after = fixture.repo.getCatalog()
            after.assignments shouldBe emptyList()
            after.credentials.size shouldBe 2
            after.legacyReferences.size shouldBe 1
            fixture.repo.deleteConnection(CONNECTION) shouldBe true
            fixture.repo.getCatalog().credentials.size shouldBe 2
        }
    }

    @Test
    fun `profile resolution respects explicit novel source global precedence`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val global = profile(PROFILE)
            val source = profile(PROFILE_TWO)
            val novel = profile(PROFILE_THREE)
            val explicit = profile(PROFILE_FOUR)
            fixture.repo.importConfiguration(
                catalog().copy(
                    profiles = listOf(global, source, novel, explicit),
                    assignments = listOf(
                        ProfileAssignment(ProfileAssignmentScope.GLOBAL, 0, global.id),
                        ProfileAssignment(ProfileAssignmentScope.SOURCE, -42, source.id),
                        ProfileAssignment(ProfileAssignmentScope.NOVEL, 7, novel.id),
                    ),
                ),
            )
            fixture.repo.resolveProfile(explicit.id, 7, -42)?.id shouldBe explicit.id
            fixture.repo.resolveProfile(novelId = 7, sourceId = -42)?.id shouldBe novel.id
            fixture.repo.resolveProfile(novelId = 8, sourceId = -42)?.id shouldBe source.id
            fixture.repo.resolveProfile(novelId = 8, sourceId = 1)?.id shouldBe global.id
            fixture.repo.resolveProfile(MISSING, 7, -42) shouldBe null
            fixture.repo.removeAssignment(ProfileAssignmentScope.NOVEL, 7) shouldBe true
            fixture.repo.resolveProfile(novelId = 7, sourceId = -42)?.id shouldBe source.id
        }
    }

    @Test
    fun `failed import rolls back every configuration table without echoing SQL cause`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.driver.execute(
                null,
                """
                CREATE TRIGGER reject_fixture_profile BEFORE INSERT ON translation_profiles
                BEGIN SELECT RAISE(ABORT, 'fixture-sensitive-message'); END
                """.trimIndent(),
                0,
            )
            var failure: ProfileStorageException? = null
            try {
                fixture.repo.importConfiguration(catalog())
            } catch (
                error: ProfileStorageException,
            ) {
                failure = error
            }
            failure.shouldBeInstanceOf<ProfileStorageException>().kind shouldBe ProfileStorageFailure.UNAVAILABLE
            failure?.cause shouldBe null
            failure?.message?.contains("fixture-sensitive") shouldBe false
            fixture.repo.getCatalog() shouldBe ProfileCatalog()
        }
    }

    @Test
    fun `corrupt unsupported configuration record fails generically without payload echo`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.driver.execute(null, "INSERT INTO translation_connections VALUES (?, ?)", 2) {
                bindString(0, CONNECTION)
                bindString(1, "{\"version\":2,\"value\":\"fixture-private-text\"}")
            }
            var failure: ProfileStorageException? = null
            try {
                fixture.repo.getConnection(CONNECTION)
            } catch (error: ProfileStorageException) {
                failure = error
            }
            failure.shouldBeInstanceOf<ProfileStorageException>().kind shouldBe ProfileStorageFailure.CORRUPT
            failure?.cause shouldBe null
            failure?.message?.contains("fixture-private") shouldBe false
        }
    }

    @Test
    fun `route cursor ledger and replay acknowledgment are durable before delivery`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            val first = fixture.reserve(run.id, run.generation, "one")
                .shouldBeInstanceOf<ProfileRequestReservation.Reserved>()
            first.credentialId shouldBe KEY_A
            first.ledger.reservedRequestIds shouldBe setOf("one")
            first.ledger.reservedTokens shouldBe 10
            fixture.reserve(run.id, run.generation, "one")
                .shouldBeInstanceOf<ProfileRequestReservation.AlreadyReserved>()
            fixture.repo.readLedger(run.id, run.generation).reservedRequestIds shouldBe setOf("one")
            val reloaded = TranslationProfileRepositoryImpl(fixture.handler, Json)
            val next = reloaded.reserveRequest(
                run.id, run.generation, connection(), "fixture-model", ExecutionPolicy(), "two", setOf("0"), 10, 2,
            ).shouldBeInstanceOf<ProfileRequestReservation.Reserved>()
            next.credentialId shouldBe KEY_B
            next.ledger.reservedRequestIds shouldBe setOf("one", "two")
            next.ledger.reservedTokens shouldBe 20
        }
    }

    @Test
    fun `waiting and needs action never consume request or token budgets`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.repo.importConfiguration(catalog())
            val run = fixture.running()
            fixture.reserve(run.id, run.generation, "one") shouldBe ProfileRequestReservation.NeedsAction
            fixture.ready()
            fixture.repo.recordRoutingFailure(
                connection(),
                "fixture-model",
                KEY_A,
                RoutingFailure.SERVICE_UNAVAILABLE,
                9_000,
                1,
            )
            fixture.reserve(run.id, run.generation, "one", now = 2)
                .shouldBeInstanceOf<ProfileRequestReservation.Waiting>().notBeforeAt shouldBe 9_000
            fixture.repo.readLedger(run.id, run.generation).reservedRequestIds shouldBe emptySet()
        }
    }

    @Test
    fun `shared quota cooldown blocks all bound refs`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            fixture.repo.recordRoutingFailure(
                connection(),
                "fixture-model",
                KEY_A,
                RoutingFailure.GROUP_LIMITED,
                5_000,
                1,
            )
            fixture.reserve(run.id, run.generation, "one", now = 2)
                .shouldBeInstanceOf<ProfileRequestReservation.Waiting>().notBeforeAt shouldBe 5_000
            fixture.repo.readLedger(run.id, run.generation).reservedTokens shouldBe 0
        }
    }

    @Test
    fun `request budgets stop new requests but lost responses never refund reservations`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            val policy = ExecutionPolicy(maxRequests = 1)
            fixture.reserve(run.id, run.generation, "one", policy = policy)
                .shouldBeInstanceOf<ProfileRequestReservation.Reserved>()
            fixture.repo.recordRoutingFailure(
                connection(),
                "fixture-model",
                KEY_A,
                RoutingFailure.CREDENTIAL_REJECTED,
                0,
                2,
            )
            fixture.reserve(run.id, run.generation, "two", policy = policy)
                .shouldBeInstanceOf<ProfileRequestReservation.BudgetStop>().reason shouldBe
                RequestBudgetBlock.REQUEST_LIMIT
            fixture.repo.readLedger(run.id, run.generation).reservedTokens shouldBe 10
            fixture.repo.readLedger(run.id, run.generation).reservedRequestIds shouldBe setOf("one")
        }
    }

    @Test
    fun `unknown token usage cannot silently bypass an explicit token budget`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            val result = fixture.repo.reserveRequest(
                run.id, run.generation, connection(), "fixture-model", ExecutionPolicy(maxReservedTokens = 20),
                "one", setOf("0"), null, 1,
            ).shouldBeInstanceOf<ProfileRequestReservation.BudgetStop>()
            result.reason shouldBe RequestBudgetBlock.UNKNOWN_TOKEN_USAGE
            fixture.repo.readLedger(run.id, run.generation).reservedRequestIds shouldBe emptySet()
        }
    }

    @Test
    fun `completed fragments and stale generations cannot create fresh paid reservations`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            fixture.reserve(run.id, run.generation, "one")
                .shouldBeInstanceOf<ProfileRequestReservation.Reserved>()
            fixture.repo.markCompletedFragments(run.id, run.generation, setOf("0")) shouldBe true
            fixture.reserve(run.id, run.generation, "two")
                .shouldBeInstanceOf<ProfileRequestReservation.NoWork>()
            fixture.runs.cancelChapter(run.chapterId, 2)
            fixture.repo.markCompletedFragments(run.id, run.generation, setOf("1")) shouldBe false
            fixture.reserve(run.id, run.generation, "three") shouldBe ProfileRequestReservation.Stale
            fixture.repo.readLedger(run.id, run.generation).reservedRequestIds shouldBe setOf("one")
        }
    }

    @Test
    fun `frozen connection or policy changes within a ledger cannot silently reroute work`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            fixture.reserve(run.id, run.generation, "one")
                .shouldBeInstanceOf<ProfileRequestReservation.Reserved>()
            fixture.repo.reserveRequest(
                run.id, run.generation, connection().copy(baseUrl = "https://other.example/v1"),
                "fixture-model", ExecutionPolicy(), "two", setOf("0"), 10, 2,
            ) shouldBe ProfileRequestReservation.NeedsAction
            fixture.repo.readLedger(run.id, run.generation).reservedRequestIds shouldBe setOf("one")
        }
    }

    @Test
    fun `concurrent reservations serialize cursor and conservative budget updates`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            val results = coroutineScope {
                val one = async { fixture.reserve(run.id, run.generation, "one") }
                val two = async { fixture.reserve(run.id, run.generation, "two") }
                listOf(one.await(), two.await()).map { it.shouldBeInstanceOf<ProfileRequestReservation.Reserved>() }
            }
            results.map { it.credentialId }.toSet() shouldBe setOf(KEY_A, KEY_B)
            fixture.repo.readLedger(run.id, run.generation).reservedTokens shouldBe 20
            fixture.repo.readLedger(run.id, run.generation).reservedRequestIds.size shouldBe 2
        }
    }

    @Test
    fun `profile writers share the original run publication mutation gate`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val entries = AtomicInteger()
            val handler = object : NovelDatabaseHandler by fixture.handler {
                override suspend fun <T> await(inTransaction: Boolean, block: suspend (NovelDatabase) -> T): T {
                    if (inTransaction) entries.incrementAndGet()
                    return fixture.handler.await(inTransaction, block)
                }
            }
            val profiles = TranslationProfileRepositoryImpl(handler, Json)
            profiles.saveCredentialMetadata(metadata(KEY_A))
            val run = fixture.running()
            fixture.runs.prepare(
                run,
                "fixture-source",
                "run-profile",
                1,
                listOf(TranslationFragment(0, 0, 0, 1, "fixture-source", translatedText = "fixture-result")),
                1,
            )
            val entered = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            val publication = async {
                fixture.runs.publish(run, 2) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            try {
                withTimeout(5_000) { entered.await() }
                entries.set(0)
                val update = async(start = CoroutineStart.UNDISPATCHED) {
                    profiles.saveCredentialMetadata(metadata(KEY_B))
                }
                entries.get() shouldBe 0
                release.countDown()
                publication.await() shouldBe true
                update.await()
                profiles.getCredentialMetadata(KEY_B)?.id shouldBe KEY_B
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `failed reservation rolls back ledger and route cursor before any HTTP admission`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            fixture.driver.execute(
                null,
                """
                CREATE TRIGGER reject_fixture_reservation BEFORE INSERT ON translation_request_reservations
                BEGIN SELECT RAISE(ABORT, 'fixture-reservation-failure'); END
                """.trimIndent(),
                0,
            )
            var error: ProfileStorageException? = null
            try {
                fixture.reserve(run.id, run.generation, "one")
            } catch (
                failure: ProfileStorageException,
            ) {
                error = failure
            }
            error.shouldBeInstanceOf<ProfileStorageException>().kind shouldBe ProfileStorageFailure.UNAVAILABLE
            fixture.repo.readLedger(run.id, run.generation).reservedRequestIds shouldBe emptySet()
            fixture.repo.readLedger(run.id, run.generation).reservedTokens shouldBe 0
            fixture.repo.readRoutingState().lastSelectedByConnection shouldBe emptyMap()
        }
    }

    @Test
    fun `corrupted ledger totals fail closed instead of refunding a paid reservation`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            fixture.reserve(run.id, run.generation, "one")
                .shouldBeInstanceOf<ProfileRequestReservation.Reserved>()
            fixture.driver.execute(null, "UPDATE translation_request_ledgers SET reserved_tokens = 0", 0)
            var error: ProfileStorageException? = null
            try {
                fixture.repo.readLedger(run.id, run.generation)
            } catch (
                failure: ProfileStorageException,
            ) {
                error = failure
            }
            error.shouldBeInstanceOf<ProfileStorageException>().kind shouldBe ProfileStorageFailure.CORRUPT
            error?.cause shouldBe null
        }
    }

    @Test
    fun `explicit unauthenticated connection reserves without inventing a credential`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val run = fixture.running()
            val open = connection().copy(auth = ProfileAuth.NONE, credentialBindings = emptyList())
            val reservation = fixture.repo.reserveRequest(
                run.id, run.generation, open, "fixture-model", ExecutionPolicy(),
                "one", setOf("0"), 10, 1,
            ).shouldBeInstanceOf<ProfileRequestReservation.Reserved>()
            reservation.credentialId shouldBe null
            reservation.ledger.reservedRequestIds shouldBe setOf("one")
            fixture.repo.readRoutingState().health shouldBe emptyMap()
        }
    }

    @Test
    fun `explicit independent quota labels permit another ref without sharing cooldown`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            val independent = connection().copy(
                credentialBindings = listOf(
                    CredentialBinding(KEY_A, quotaGroupLabel = "a"),
                    CredentialBinding(KEY_B, quotaGroupLabel = "b"),
                ),
            )
            fixture.repo.recordRoutingFailure(
                independent,
                "fixture-model",
                KEY_A,
                RoutingFailure.GROUP_LIMITED,
                5_000,
                1,
            )
            val reservation = fixture.repo.reserveRequest(
                run.id, run.generation, independent, "fixture-model", ExecutionPolicy(),
                "one", setOf("0"), 10, 2,
            ).shouldBeInstanceOf<ProfileRequestReservation.Reserved>()
            reservation.credentialId shouldBe KEY_B
        }
    }

    @Test
    fun `unused credential retirement clears health and rejects same UUID restoration`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            repo.saveCredentialMetadata(metadata(MISSING))
            repo.setCredentialHealth(MISSING, CredentialHealth(CredentialHealthStatus.READY))
            repo.deleteCredentialMetadataIfUnused(MISSING) shouldBe true
            repo.getCredentialMetadata(MISSING) shouldBe null
            repo.readRoutingState().health.containsKey(MISSING) shouldBe false
            fixture.expectInvalid { repo.saveCredentialMetadata(metadata(MISSING)) }
            fixture.expectInvalid { repo.importConfiguration(ProfileCatalog(credentials = listOf(metadata(MISSING)))) }
            val run = fixture.running()
            val stale = connection().copy(credentialBindings = listOf(CredentialBinding(MISSING)))
            repo.reserveRequest(
                run.id, run.generation, stale, "fixture-model", ExecutionPolicy(),
                "one", setOf("0"), 10, 1,
            ) shouldBe ProfileRequestReservation.NeedsAction
            repo.readLedger(run.id, run.generation).reservedRequestIds shouldBe emptySet()
        }
    }

    @Test
    fun `connection and legacy credential references prevent metadata deletion`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.repo.importConfiguration(catalog())
            fixture.repo.deleteCredentialMetadataIfUnused(KEY_A) shouldBe false
            fixture.repo.getCredentialMetadata(KEY_A)?.id shouldBe KEY_A
            fixture.repo.deleteProfile(PROFILE)
            fixture.repo.deleteConnection(CONNECTION)
            fixture.repo.deleteCredentialMetadataIfUnused(KEY_A) shouldBe false
            fixture.repo.deleteCredentialMetadataIfUnused(KEY_B) shouldBe true
        }
    }

    @Test
    fun `active typed frozen snapshot references protect a credential without catalog bindings`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.repo.saveCredentialMetadata(metadata(MISSING))
            val frozenConnection = connection().copy(credentialBindings = listOf(CredentialBinding(MISSING)))
            val snapshot = JsonObject(
                mapOf(
                    "snapshotVersion" to JsonPrimitive(2),
                    "systemPromptSnapshot" to JsonPrimitive("frozen native prompt"),
                    "geminiRequestShape" to JsonPrimitive("PRIVATE_TAGGED"),
                    "connectionProfile" to Json.encodeToJsonElement(frozenConnection),
                    "translationProfile" to Json.encodeToJsonElement(profile()),
                ),
            ).toString()
            fixture.runs.enqueue(listOf(1), 1, snapshot, "frozen-profile", "", false, now = 1)
            fixture.repo.deleteCredentialMetadataIfUnused(MISSING) shouldBe false
            fixture.runs.cancelChapter(1, 2)
            fixture.repo.deleteCredentialMetadataIfUnused(MISSING) shouldBe true
        }
    }

    @Test
    fun `unknown or corrupt C snapshot fails closed for unused credential deletion`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.repo.saveCredentialMetadata(metadata(MISSING))
            val base = mapOf(
                "snapshotVersion" to JsonPrimitive(2),
                "connectionProfile" to Json.encodeToJsonElement(
                    connection().copy(credentialBindings = emptyList()),
                ),
                "translationProfile" to Json.encodeToJsonElement(profile()),
            )
            val corrupt = listOf(
                """{"snapshotVersion":99,"connectionProfile":{}}""",
                "not-json",
                JsonObject(base + ("systemPromptSnapshot" to JsonPrimitive(42))).toString(),
                JsonObject(base + ("systemPromptSnapshot" to JsonPrimitive("x".repeat(100_001)))).toString(),
                JsonObject(base + ("geminiRequestShape" to JsonPrimitive("UNKNOWN"))).toString(),
                JsonObject(base + ("apiKey" to JsonPrimitive("fixture-not-real"))).toString(),
            )
            corrupt.forEachIndexed { index, snapshot ->
                fixture.runs.enqueue(listOf(1), 1, snapshot, "unknown-profile", "", false, now = index.toLong())
                fixture.repo.deleteCredentialMetadataIfUnused(MISSING) shouldBe false
                fixture.repo.getCredentialMetadata(MISSING)?.id shouldBe MISSING
                fixture.runs.cancelChapter(1, index + 1L)
            }
        }
    }

    @Test
    fun `successful probe resets action health but never still future quota or service cooldowns`() =
        runBlocking<Unit> {
            Fixture().use { fixture ->
                fixture.ready()
                fixture.repo.setCredentialHealth(KEY_A, CredentialHealth(CredentialHealthStatus.NEEDS_ACTION, 9_000))
                fixture.repo.recordRoutingFailure(
                    connection(),
                    "fixture-model",
                    KEY_B,
                    RoutingFailure.GROUP_LIMITED,
                    5_000,
                    1,
                )
                fixture.repo.recordRoutingFailure(
                    connection(),
                    "fixture-model",
                    KEY_B,
                    RoutingFailure.SERVICE_UNAVAILABLE,
                    8_000,
                    1,
                )
                val before = fixture.repo.readRoutingState()
                fixture.repo.resetServiceHealth(connection(), "fixture-model", KEY_A, 2)
                val after = fixture.repo.readRoutingState()
                after.health[KEY_A]?.status shouldBe CredentialHealthStatus.READY
                after.health[KEY_A]?.notBeforeAt shouldBe 9_000
                after.groupNotBeforeAt shouldBe before.groupNotBeforeAt
                after.serviceNotBeforeAt shouldBe before.serviceNotBeforeAt
            }
        }

    @Test
    fun `new binding and credential deletion race cannot commit a dangling restored ID`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.repo.saveCredentialMetadata(metadata(MISSING))
            val bound = connection().copy(credentialBindings = listOf(CredentialBinding(MISSING)))
            val (saved, deleted) = coroutineScope {
                val save = async {
                    try {
                        fixture.repo.saveConnection(bound)
                        true
                    } catch (_: ProfileStorageException) {
                        false
                    }
                }
                val remove = async { fixture.repo.deleteCredentialMetadataIfUnused(MISSING) }
                save.await() to remove.await()
            }
            (saved && deleted) shouldBe false
            if (saved) fixture.repo.getCredentialMetadata(MISSING)?.id shouldBe MISSING
            if (deleted) fixture.repo.getConnection(CONNECTION) shouldBe null
        }
    }

    @Test
    fun `manual action resume preserves spent budget and parks once again without refunds`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val run = fixture.running()
            val policy = ExecutionPolicy(maxRequests = 1)
            fixture.reserve(run.id, run.generation, "one", policy = policy)
                .shouldBeInstanceOf<ProfileRequestReservation.Reserved>()
            fixture.runs.parkForAction(1, run.generation, TranslationActionPauseReason.BUDGET_LIMIT, 2) shouldBe true
            val charged = fixture.repo.readLedger(run.id, run.generation)
            fixture.runs.claimNext(3) shouldBe null
            fixture.runs.resumeActionPaused(1, run.generation, 4) shouldBe true
            val resumed = fixture.runs.claimNext(5)
                .shouldBeInstanceOf<tachiyomi.domain.translation.model.TranslationRun>()
            resumed.generation shouldBe run.generation
            fixture.repo.readLedger(run.id, run.generation) shouldBe charged
            fixture.reserve(run.id, run.generation, "two", policy = policy)
                .shouldBeInstanceOf<ProfileRequestReservation.BudgetStop>()
            fixture.runs.parkForAction(1, run.generation, TranslationActionPauseReason.BUDGET_LIMIT, 6) shouldBe true
            fixture.runs.claimNext(7) shouldBe null
            fixture.repo.readLedger(run.id, run.generation) shouldBe charged
        }
    }

    @Test
    fun `pruneOrphanNovelAssignments removes deleted novels while keeping live and global scopes`() =
        runBlocking<Unit> {
            Fixture().use { fixture ->
                fixture.driver.execute(
                    null,
                    """
                    INSERT OR IGNORE INTO novels (
                        _id, source, url, title, status, favorite,
                        initialized, viewer, chapter_flags, cover_last_modified, date_added
                    ) VALUES (77, 1, '/n/77', 'Live', 0, 1, 1, 0, 0, 0, 0)
                    """.trimIndent(),
                    0,
                )
                fixture.repo.importConfiguration(catalog())
                fixture.repo.saveAssignment(ProfileAssignment(ProfileAssignmentScope.SOURCE, 10L, PROFILE))
                fixture.repo.saveAssignment(ProfileAssignment(ProfileAssignmentScope.NOVEL, 77L, PROFILE))
                fixture.repo.saveAssignment(ProfileAssignment(ProfileAssignmentScope.NOVEL, 999L, PROFILE))

                fixture.repo.pruneOrphanNovelAssignments() shouldBe 1
                fixture.repo.pruneOrphanNovelAssignments() shouldBe 0
                fixture.repo.getCatalog().assignments.toSet() shouldBe setOf(
                    ProfileAssignment(ProfileAssignmentScope.GLOBAL, 0L, PROFILE),
                    ProfileAssignment(ProfileAssignmentScope.SOURCE, 10L, PROFILE),
                    ProfileAssignment(ProfileAssignmentScope.NOVEL, 77L, PROFILE),
                )
            }
        }

    @Test
    fun `observeRoutingState emits updated routing state after health mutations`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.ready()
            val updated = withTimeout(5_000) {
                coroutineScope {
                    val awaited = async(start = CoroutineStart.UNDISPATCHED) {
                        fixture.repo.observeRoutingState().first { state ->
                            state.health[KEY_A]?.status == CredentialHealthStatus.NEEDS_ACTION
                        }
                    }
                    fixture.repo.setCredentialHealth(
                        KEY_A,
                        CredentialHealth(CredentialHealthStatus.NEEDS_ACTION, 4_000),
                    )
                    awaited.await()
                }
            }
            updated.health[KEY_A] shouldBe CredentialHealth(CredentialHealthStatus.NEEDS_ACTION, 4_000)
        }
    }

    @Test
    fun `importConfiguration preserves local connections profiles and assignments on same id collisions`() =
        runBlocking<Unit> {
            Fixture().use { fixture ->
                val repo = fixture.repo
                repo.importConfiguration(catalog())
                val keyC = "00000000-0000-0000-0000-000000000013"
                val extraConnectionId = "00000000-0000-0000-0000-000000000009"
                val extraProfile = TranslationProfile(
                    PROFILE_TWO,
                    "Imported Second",
                    CONNECTION,
                    ModelSelection("other-model"),
                    TranslationLanguages("Japanese", "English"),
                )
                val incoming = ProfileCatalog(
                    connections = listOf(
                        connection().copy(
                            name = "Overwritten Connection",
                            baseUrl = "https://other.test/v1",
                            credentialBindings = listOf(CredentialBinding(KEY_B), CredentialBinding(keyC)),
                        ),
                        connection().copy(
                            id = extraConnectionId,
                            name = "Brand New Connection",
                            credentialBindings = emptyList(),
                            auth = ProfileAuth.NONE,
                        ),
                    ),
                    profiles = listOf(
                        profile().copy(name = "Overwritten Profile", model = ModelSelection("wrong-model")),
                        extraProfile,
                    ),
                    credentials = listOf(
                        metadata(KEY_A).copy(alias = "Overwritten Alias"),
                        metadata(keyC).copy(alias = "Imported Key C"),
                    ),
                    assignments = listOf(
                        ProfileAssignment(ProfileAssignmentScope.GLOBAL, 0L, PROFILE_TWO),
                        ProfileAssignment(ProfileAssignmentScope.SOURCE, 55L, PROFILE_TWO),
                    ),
                    legacyReferences = listOf(
                        LegacyCredentialReference("GEMINI", LegacyCredentialScope.SOURCE, -42, keyC),
                        LegacyCredentialReference("OPENROUTER", LegacyCredentialScope.GLOBAL, 0, keyC),
                    ),
                )

                repo.importConfiguration(incoming)

                val merged = repo.getCatalog()
                val preservedConnection = merged.connections.first { it.id == CONNECTION }
                preservedConnection.name shouldBe "Fixture"
                preservedConnection.baseUrl shouldBe "https://example.test/v1"
                preservedConnection.credentialBindings.map { it.credentialId } shouldBe listOf(KEY_A, KEY_B, keyC)
                merged.connections.any { it.id == extraConnectionId } shouldBe true
                merged.profiles.first { it.id == PROFILE } shouldBe profile()
                merged.profiles.first { it.id == PROFILE_TWO } shouldBe extraProfile
                merged.credentials.first { it.id == KEY_A } shouldBe metadata(KEY_A)
                merged.credentials.first { it.id == keyC }.alias shouldBe "Imported Key C"
                merged.assignments.toSet() shouldBe setOf(
                    ProfileAssignment(ProfileAssignmentScope.GLOBAL, 0L, PROFILE),
                    ProfileAssignment(ProfileAssignmentScope.SOURCE, 55L, PROFILE_TWO),
                )
                merged.legacyReferences.toSet() shouldBe setOf(
                    LegacyCredentialReference("GEMINI", LegacyCredentialScope.SOURCE, -42, KEY_A),
                    LegacyCredentialReference("OPENROUTER", LegacyCredentialScope.GLOBAL, 0, keyC),
                )
            }
        }

    private class Fixture : AutoCloseable {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createTestNovelDatabase(driver)
        val handler = AndroidNovelDatabaseHandler(database, driver)
        val repo = TranslationProfileRepositoryImpl(handler, Json)
        val runs = TranslationRunRepositoryImpl(handler, Json)
        suspend fun ready() {
            repo.importConfiguration(catalog())
            repo.setCredentialHealth(KEY_A, CredentialHealth(CredentialHealthStatus.READY))
            repo.setCredentialHealth(KEY_B, CredentialHealth(CredentialHealthStatus.READY))
        }
        suspend fun running(): tachiyomi.domain.translation.model.TranslationRun {
            runs.enqueue(listOf(1), 1, "{}", "run-profile", "", false, now = 1)
            return requireNotNull(runs.claimNext(1))
        }
        suspend fun reserve(
            id: Long,
            generation: Long,
            request: String,
            now: Long = 1,
            policy: ExecutionPolicy = ExecutionPolicy(),
        ): ProfileRequestReservation = repo.reserveRequest(
            id, generation, connection(), "fixture-model", policy, request, setOf("0"), 10, now,
        )
        suspend fun expectInvalid(block: suspend () -> Unit) {
            var error: ProfileStorageException? = null
            try {
                block()
            } catch (failure: ProfileStorageException) {
                error = failure
            }
            error.shouldBeInstanceOf<ProfileStorageException>().kind shouldBe ProfileStorageFailure.INVALID_REFERENCE
        }
        override fun close() = driver.close()
    }

    private companion object {
        const val CONNECTION = "00000000-0000-0000-0000-000000000001"
        const val PROFILE = "00000000-0000-0000-0000-000000000002"
        const val PROFILE_TWO = "00000000-0000-0000-0000-000000000003"
        const val PROFILE_THREE = "00000000-0000-0000-0000-000000000004"
        const val PROFILE_FOUR = "00000000-0000-0000-0000-000000000005"
        const val KEY_A = "00000000-0000-0000-0000-000000000011"
        const val KEY_B = "00000000-0000-0000-0000-000000000012"
        const val MISSING = "00000000-0000-0000-0000-000000000099"
        fun metadata(id: String) = CredentialMetadata(id, "Fixture", "1234", 1)
        fun connection() = ConnectionProfile(
            CONNECTION,
            "Fixture",
            TranslationProtocol.OPENAI_CHAT,
            "https://example.test/v1",
            ProfileAuth.BEARER,
            credentialBindings = listOf(CredentialBinding(KEY_A), CredentialBinding(KEY_B)),
            routingMode = CredentialRoutingMode.ROUND_ROBIN,
        )
        fun profile(id: String = PROFILE) = TranslationProfile(
            id,
            "Fixture",
            CONNECTION,
            ModelSelection("fixture-model"),
            TranslationLanguages("English", "Russian"),
        )
        fun catalog() = ProfileCatalog(
            connections = listOf(connection()),
            profiles = listOf(profile()),
            credentials = listOf(metadata(KEY_A), metadata(KEY_B)),
            assignments = listOf(ProfileAssignment(ProfileAssignmentScope.GLOBAL, 0, PROFILE)),
            legacyReferences = listOf(LegacyCredentialReference("GEMINI", LegacyCredentialScope.SOURCE, -42, KEY_A)),
        )
    }
}
