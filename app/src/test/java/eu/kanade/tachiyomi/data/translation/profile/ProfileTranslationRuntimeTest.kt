package eu.kanade.tachiyomi.data.translation.profile

import eu.kanade.tachiyomi.data.backup.translation.CONNECTION
import eu.kanade.tachiyomi.data.backup.translation.KEY_A
import eu.kanade.tachiyomi.data.backup.translation.KEY_B
import eu.kanade.tachiyomi.data.backup.translation.PROFILE
import eu.kanade.tachiyomi.data.backup.translation.RecordingTranslationVault
import eu.kanade.tachiyomi.data.backup.translation.TranslationBackupFixture
import eu.kanade.tachiyomi.data.translation.toTranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.translation.PreparedProfileRequest
import eu.kanade.tachiyomi.ui.reader.novel.translation.ProfileCompatibleApi
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.data.translation.TranslationRunRepositoryImpl
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.CredentialRoutingMode
import tachiyomi.domain.translation.profile.ExecutionPolicy
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.ProfileRetryMode
import tachiyomi.domain.translation.profile.RoutingFailure
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol
import tachiyomi.domain.translation.service.TranslationPassState
import tachiyomi.domain.translation.service.TranslationRequestGate
import java.util.concurrent.atomic.AtomicInteger

class ProfileTranslationRuntimeTest {

    @Test
    fun `401 authorization failure immediately fails over to next key even when retry mode is off`() =
        runBlocking<Unit> {
            Fixture(ExecutionPolicy(ProfileRetryMode.OFF)).use { fixture ->
                val seenKeys = mutableListOf<String>()
                val runtime = fixture.runtime { key ->
                    seenKeys += key
                    if (key == "secret-a") {
                        throw TranslationAttemptException(
                            TranslationFailure(TranslationFailureKind.AUTHORIZATION, "401"),
                        )
                    }
                    listOf("Translated line")
                }

                val result = fixture.execute(runtime)

                result.state shouldBe TranslationPassState.COMPLETE
                result.fragments.single().translatedText shouldBe "Translated line"
                seenKeys shouldBe listOf("secret-a", "secret-b")
                val state = fixture.db.profiles.readRoutingState()
                state.health[KEY_A]?.status shouldBe CredentialHealthStatus.NEEDS_ACTION
                state.health[KEY_B]?.status shouldBe CredentialHealthStatus.READY
            }
        }

    @Test
    fun `401 authorization failure fails over immediately in wait for service mode without cooldown delay`() =
        runBlocking<Unit> {
            Fixture(ExecutionPolicy(ProfileRetryMode.WAIT_FOR_SERVICE)).use { fixture ->
                val attemptTimestamps = mutableListOf<Long>()
                val runtime = fixture.runtime { key ->
                    attemptTimestamps += fixture.now
                    if (key == "secret-a") {
                        throw TranslationAttemptException(
                            TranslationFailure(TranslationFailureKind.AUTHORIZATION, "401"),
                        )
                    }
                    listOf("Translated immediately")
                }

                val result = fixture.execute(runtime)

                result.state shouldBe TranslationPassState.COMPLETE
                attemptTimestamps shouldBe listOf(1_000L, 1_000L)
            }
        }

    @Test
    fun `401 across all enabled credentials stops cleanly without infinite retry`() = runBlocking<Unit> {
        Fixture(ExecutionPolicy(ProfileRetryMode.WAIT_FOR_SERVICE)).use { fixture ->
            val seenKeys = mutableListOf<String>()
            val runtime = fixture.runtime { key ->
                seenKeys += key
                throw TranslationAttemptException(
                    TranslationFailure(TranslationFailureKind.AUTHORIZATION, "401 for $key"),
                )
            }

            val result = fixture.execute(runtime)

            result.state shouldBe TranslationPassState.FAILED
            seenKeys shouldBe listOf("secret-a", "secret-b")
            val state = fixture.db.profiles.readRoutingState()
            state.health[KEY_A]?.status shouldBe CredentialHealthStatus.NEEDS_ACTION
            state.health[KEY_B]?.status shouldBe CredentialHealthStatus.NEEDS_ACTION
        }
    }

    @Test
    fun `401 with second key cooling down returns fast waiting without burning attempts`() = runBlocking<Unit> {
        Fixture(
            ExecutionPolicy(ProfileRetryMode.OFF),
            bindings = listOf(
                CredentialBinding(KEY_A, priority = 10, quotaGroupLabel = "grp-a"),
                CredentialBinding(KEY_B, priority = 1, quotaGroupLabel = "grp-b"),
            ),
        ).use { fixture ->
            fixture.db.profiles.recordRoutingFailure(
                fixture.connection,
                MODEL_ID,
                KEY_B,
                RoutingFailure.GROUP_LIMITED,
                5_000L,
                fixture.now,
            )
            val seenKeys = mutableListOf<String>()
            val runtime = fixture.runtime { key ->
                seenKeys += key
                throw TranslationAttemptException(
                    TranslationFailure(TranslationFailureKind.AUTHORIZATION, "401"),
                )
            }

            val result = fixture.execute(runtime)

            seenKeys shouldBe listOf("secret-a")
            result.state shouldBe TranslationPassState.WAITING
            result.nextAttemptAt shouldBe 5_000L
            result.fragments.single().attempts shouldBe 0
        }
    }

    @Test
    fun `429 rate limit immediately fails over to an independent quota group`() = runBlocking<Unit> {
        Fixture(
            ExecutionPolicy(ProfileRetryMode.OFF),
            bindings = listOf(
                CredentialBinding(KEY_A, priority = 10, quotaGroupLabel = "grp-a"),
                CredentialBinding(KEY_B, priority = 1, quotaGroupLabel = "grp-b"),
            ),
        ).use { fixture ->
            val seenKeys = mutableListOf<String>()
            val runtime = fixture.runtime { key ->
                seenKeys += key
                if (key == "secret-a") {
                    throw TranslationAttemptException(
                        TranslationFailure(TranslationFailureKind.RATE_LIMIT, "429", retryAfterAt = 15_000L),
                    )
                }
                listOf("Translated via grp-b")
            }

            val result = fixture.execute(runtime)

            result.state shouldBe TranslationPassState.COMPLETE
            result.fragments.single().translatedText shouldBe "Translated via grp-b"
            seenKeys shouldBe listOf("secret-a", "secret-b")
        }
    }

    @Test
    fun `429 across all quota groups falls back to waiting and stops looping`() = runBlocking<Unit> {
        Fixture(
            ExecutionPolicy(ProfileRetryMode.BOUNDED),
            bindings = listOf(
                CredentialBinding(KEY_A, priority = 10, quotaGroupLabel = "grp-a"),
                CredentialBinding(KEY_B, priority = 1, quotaGroupLabel = "grp-b"),
            ),
        ).use { fixture ->
            val seenKeys = mutableListOf<String>()
            val runtime = fixture.runtime { key ->
                seenKeys += key
                throw TranslationAttemptException(
                    TranslationFailure(TranslationFailureKind.RATE_LIMIT, "429", retryAfterAt = 12_000L),
                )
            }

            val result = fixture.execute(runtime)

            seenKeys shouldBe listOf("secret-a", "secret-b")
            result.state shouldBe TranslationPassState.WAITING
            result.failure?.kind shouldBe TranslationFailureKind.RATE_LIMIT
            (result.nextAttemptAt!! >= 12_000L) shouldBe true
        }
    }

    @Test
    fun `injected jitter varies nextAttemptAt in wait for service mode`() = runBlocking<Unit> {
        val low = Fixture(ExecutionPolicy(ProfileRetryMode.WAIT_FOR_SERVICE)).use { fixture ->
            val runtime = fixture.runtime(jitter = { 0.0 }) {
                throw TranslationAttemptException(TranslationFailure(TranslationFailureKind.TRANSIENT, "503"))
            }
            fixture.execute(runtime)
        }
        val high = Fixture(ExecutionPolicy(ProfileRetryMode.WAIT_FOR_SERVICE)).use { fixture ->
            val runtime = fixture.runtime(jitter = { 1.0 }) {
                throw TranslationAttemptException(TranslationFailure(TranslationFailureKind.TRANSIENT, "503"))
            }
            fixture.execute(runtime)
        }

        low.state shouldBe TranslationPassState.WAITING
        high.state shouldBe TranslationPassState.WAITING
        (high.nextAttemptAt!! > low.nextAttemptAt!!) shouldBe true
    }

    private class Fixture(
        execution: ExecutionPolicy,
        bindings: List<CredentialBinding> = listOf(
            CredentialBinding(KEY_A, priority = 10),
            CredentialBinding(KEY_B, priority = 1),
        ),
    ) : AutoCloseable {
        val db = TranslationBackupFixture()
        val runs = TranslationRunRepositoryImpl(db.handler, Json)
        val vault = RecordingTranslationVault().apply {
            values[KEY_A] = "secret-a"
            values[KEY_B] = "secret-b"
        }
        var now = 1_000L
        private val seq = AtomicInteger(1)

        val connection = ConnectionProfile(
            CONNECTION,
            "Fixture",
            TranslationProtocol.OPENAI_CHAT,
            "https://example.test/v1",
            ProfileAuth.BEARER,
            credentialBindings = bindings,
            routingMode = CredentialRoutingMode.PRIORITY,
        )
        val profile = TranslationProfile(
            PROFILE,
            "Fixture",
            CONNECTION,
            ModelSelection(MODEL_ID),
            TranslationLanguages("English", "Russian"),
            execution = execution,
        )
        val snapshot = NovelReaderPreferences(InMemoryPreferenceStore(), Json).resolveSettings(7)
            .withTranslationProfile(profile, connection)
            .toTranslationQueueProfileSnapshot()
            .copy(systemPromptSnapshot = "SYS")

        init {
            runBlocking {
                db.profiles.importConfiguration(
                    ProfileCatalog(
                        connections = listOf(connection),
                        profiles = listOf(profile),
                        credentials = listOf(
                            CredentialMetadata(KEY_A, "Key A", "a", 1),
                            CredentialMetadata(KEY_B, "Key B", "b", 1),
                        ),
                    ),
                )
            }
        }

        fun runtime(
            jitter: () -> Double = { 0.5 },
            onRequest: suspend (String) -> List<String?>,
        ): ProfileTranslationRuntime {
            val api = mockk<ProfileCompatibleApi>()
            coEvery {
                api.dispatch(any(), any<PreparedProfileRequest>(), any(), any(), any(), any())
            } coAnswers {
                val expectedCount = thirdArg<Int>()
                val resolveCredential = arg<suspend () -> CredentialSecret?>(3)
                val beforeAttempt = arg<suspend () -> Unit>(5)
                beforeAttempt()
                val secret = resolveCredential()
                val key = secret?.use { it.reveal() } ?: ""
                val result = onRequest(key)
                result.take(expectedCount)
            }
            return ProfileTranslationRuntime(
                repository = db.profiles,
                credentials = vault,
                api = api,
                gate = TranslationRequestGate(),
                clock = { now },
                requestId = { "req-${seq.getAndIncrement()}" },
                jitter = jitter,
            )
        }

        suspend fun execute(
            runtime: ProfileTranslationRuntime,
        ): tachiyomi.domain.translation.service.TranslationPassResult {
            val run = createActiveRun()
            val fragment = TranslationFragment(0, 0, 0, 1, "Source paragraph")
            runs.prepare(run, "source-v1", "fp", 1, listOf(fragment), now) shouldBe true
            return runtime.translateFragments(
                run = run,
                snapshot = snapshot,
                fragments = listOf(fragment),
                beforeRequest = { true },
                checkpoint = {},
            )
        }

        private suspend fun createActiveRun(): TranslationRun {
            val snapshotJson = JsonObject(
                mapOf(
                    "snapshotVersion" to JsonPrimitive(2),
                    "connectionProfile" to Json.encodeToJsonElement(connection),
                    "translationProfile" to Json.encodeToJsonElement(profile),
                ),
            ).toString()
            val chapterId = seq.getAndIncrement().toLong()
            runs.enqueue(listOf(chapterId), 1L, snapshotJson, "fp", "", false, now = now)
            return runs.claimNext(now)!!
        }

        override fun close() = db.close()
    }

    private companion object {
        const val MODEL_ID = "fixture-model"
    }
}
