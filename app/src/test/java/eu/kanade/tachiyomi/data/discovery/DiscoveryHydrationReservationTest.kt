package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryHydrationReservationTest {
    private val media = DiscoveryMediaType.NOVEL
    private fun item() = DiscoveryRowItem(
        "Shared", "shared", null, null, null, "fixture", 1.0, sourceId = 7L, sourceUrl = "/shared",
    )

    @Test
    fun `coalesced rows share one real lookup with a budget of one`(): Unit = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 1)
        val hydrator = DiscoveryGenreHydrator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val first = async {
            hydrator.hydrate(media, 7L, listOf(item()), budget) {
                calls++
                entered.complete(Unit)
                release.await()
                listOf("Fantasy")
            }
        }
        runCurrent()
        entered.isCompleted shouldBe true
        val second = async {
            hydrator.hydrate(media, 7L, listOf(item()), budget) {
                calls++
                error("coalesced lookup must use cache")
            }
        }
        runCurrent()
        second.isCompleted shouldBe false
        release.complete(Unit)
        first.await().verified shouldBe 1
        second.await().verified shouldBe 1
        calls shouldBe 1
        budget.detailsExhausted shouldBe true
    }

    @Test
    fun `cached metadata works even when the next allowance is zero`(): Unit = runTest {
        val hydrator = DiscoveryGenreHydrator()
        hydrator.hydrate(media, 7L, listOf(item()), DiscoveryVerificationBudget(maxDetails = 1)) {
            listOf("Fantasy")
        }
        val result = hydrator.hydrate(media, 7L, listOf(item()), DiscoveryVerificationBudget(maxDetails = 0)) {
            error("cached result must not spend an allowance")
        }
        result.verified shouldBe 1
        result.unattempted shouldBe 0
    }

    @Test
    fun `expiry while waiting for capacity neither starts nor charges a lookup`(): Unit = runTest {
        var now = 0L
        val budget = DiscoveryVerificationBudget(
            budgetMs = 1_000L,
            maxDetails = 1,
            maxConcurrentDetails = 1,
            clock = { now },
        )
        budget.detailPermits.acquire()
        var calls = 0
        val pending = async {
            DiscoveryGenreHydrator().hydrate(media, 7L, listOf(item()), budget) {
                calls++
                listOf("Fantasy")
            }
        }
        runCurrent()
        now = 2_000L
        budget.detailPermits.release()
        val result = pending.await()
        calls shouldBe 0
        budget.detailsExhausted shouldBe false
        result.unattempted shouldBe 1
        result.transientFailures shouldBe 0
    }
}
