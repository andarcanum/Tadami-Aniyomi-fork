package eu.kanade.tachiyomi.data.translation.profile

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.profile.LegacyCredentialReference
import tachiyomi.domain.translation.profile.LegacyCredentialScope
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import java.util.concurrent.atomic.AtomicInteger

class TranslationProfileServicesMirrorTest {

    @Test
    fun `catalog mirror recovers from transient exceptions with capped backoff and resets on emission`() =
        runBlocking<Unit> {
            val expectedCatalog = ProfileCatalog(
                legacyReferences = listOf(
                    LegacyCredentialReference(
                        "GEMINI",
                        LegacyCredentialScope.GLOBAL,
                        0,
                        "00000000-0000-0000-0000-000000000011",
                    ),
                ),
            )
            val attempts = AtomicInteger(0)
            val repository = mockk<TranslationProfileRepository> {
                every { observeCatalog() } answers {
                    flow {
                        when (attempts.incrementAndGet()) {
                            1, 2 -> throw IllegalStateException("Transient DB failure")
                            3 -> {
                                emit(expectedCatalog)
                                throw IllegalStateException("Transient failure after success")
                            }
                            else -> {
                                emit(expectedCatalog)
                                throw CancellationException("Stop test loop")
                            }
                        }
                    }
                }
            }
            val recordedCatalogs = mutableListOf<ProfileCatalog?>()
            val recordedDelays = mutableListOf<Long>()

            val cancelled = try {
                runCatalogMirrorLoop(
                    repository = repository,
                    onCatalog = { recordedCatalogs += it },
                    initialBackoffMillis = 100L,
                    maxBackoffMillis = 150L,
                    delayFn = { recordedDelays += it },
                )
                false
            } catch (_: CancellationException) {
                true
            }

            cancelled shouldBe true
            attempts.get() shouldBe 4
            recordedDelays shouldBe listOf(100L, 150L, 100L)
            recordedCatalogs shouldBe listOf(null, null, expectedCatalog, null, expectedCatalog)
        }
}
