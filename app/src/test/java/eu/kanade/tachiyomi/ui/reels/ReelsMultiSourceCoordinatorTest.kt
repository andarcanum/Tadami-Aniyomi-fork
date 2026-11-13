package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeFeedErrorClassifierSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedError
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsMultiSourceCoordinatorTest {

    private fun item(id: String, author: String? = "creator-$id") = ShortVideoItem(
        id = id,
        videoUrl = "https://example.com/$id.mp4",
        posterUrl = "https://example.com/$id.jpg",
        author = author,
    )

    private class FakeFeedSource(
        override val id: Long,
        override val name: String = "Plugin $id",
        private val onFetch: suspend (page: Int, cursor: String?) -> FeedPage,
    ) : AnimeFeedSource {
        override val lang: String = "all"
        val requests = mutableListOf<Pair<Int, String?>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
            requests += page to cursor
            return onFetch(page, cursor)
        }
    }

    private class ClassifiedErrorSource(
        override val id: Long,
        override val name: String = "Classified $id",
        private val classifier: (Throwable) -> FeedError?,
        private val onFetch: suspend (page: Int, cursor: String?) -> FeedPage,
    ) : AnimeFeedSource, AnimeFeedErrorClassifierSource {
        override val lang: String = "all"
        var callCount: Int = 0

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
            callCount++
            return onFetch(page, cursor)
        }

        override fun classifyFeedError(error: Throwable): FeedError? = classifier(error)
    }

    private fun passAllFilter(): suspend (Long, List<ShortVideoItem>) -> MixFilterVerdict =
        { _, candidates -> MixFilterVerdict(kept = candidates) }

    @Test
    fun `jittered micro-batch round robin groups up to burst size without repeating source across cycle boundary`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val s1 = FakeFeedSource(1L) { _, _ -> FeedPage((1..6).map { item("s1-$it") }, hasNextPage = false) }
            val s2 = FakeFeedSource(2L) { _, _ -> FeedPage((1..6).map { item("s2-$it") }, hasNextPage = false) }
            val s3 = FakeFeedSource(3L) { _, _ -> FeedPage((1..6).map { item("s3-$it") }, hasNextPage = false) }

            val coordinator = ReelsMultiSourceCoordinator(
                ioDispatcher = dispatcher,
                random = Random(42L),
                nowMonoMs = { testScheduler.currentTime },
                batchSize = 12,
            )
            coordinator.reset(
                parentScope = this,
                sources = listOf(s1, s2, s3),
                excludedSourceIds = emptySet(),
                burst = 2,
            )

            val batch = coordinator.nextBatch(isInitial = true, filterCandidates = passAllFilter())
            batch.slots.shouldHaveSize(12)

            // Verify consecutive runs per source are <= burstSize = 2 while >1 source had remaining buffered clips.
            val runs = mutableListOf<Pair<Long, Int>>()
            for (slot in batch.slots) {
                val last = runs.lastOrNull()
                if (last != null && last.first == slot.sourceId) {
                    runs[runs.lastIndex] = last.first to (last.second + 1)
                } else {
                    runs += slot.sourceId to 1
                }
            }
            runs.all { it.second in 1..2 } shouldBe true
            runs.zipWithNext().none { (a, b) -> a.first == b.first } shouldBe true

            // Pull the remaining 6 items (2 per source) and verify no boundary repeat with batch 1's tail.
            val batch2 = coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
            batch2.slots.shouldHaveSize(6)
            (batch2.slots.first().sourceId != batch.slots.last().sourceId) shouldBe true
            batch2.hasMore shouldBe false
            coordinator.stop()
        }

    @Test
    fun `lazy pull-on-demand only refetches sources whose buffer falls below burst size`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fatSource = FakeFeedSource(10L) { page, _ ->
            if (page == 1) {
                FeedPage((1..10).map { item("fat-$it") }, hasNextPage = true)
            } else {
                FeedPage(listOf(item("fat-p2")), hasNextPage = false)
            }
        }
        val thinSource = FakeFeedSource(20L) { page, _ ->
            when (page) {
                1 -> FeedPage(listOf(item("thin-1a"), item("thin-1b")), hasNextPage = true)
                2 -> FeedPage(listOf(item("thin-2a"), item("thin-2b")), hasNextPage = true)
                else -> FeedPage(listOf(item("thin-3a")), hasNextPage = false)
            }
        }

        val coordinator = ReelsMultiSourceCoordinator(
            ioDispatcher = dispatcher,
            random = Random(7L),
            nowMonoMs = { testScheduler.currentTime },
            batchSize = 6,
        )
        coordinator.reset(this, listOf(fatSource, thinSource), emptySet(), burst = 2)

        // Batch 1 (size 6): fat has 10 (pulls 1 page), thin has 2 (pulls 1 page).
        // Burst = 2: Cycle 1 emits 2+2=4, Cycle 2 emits 2 from fat (6 total). Thin now has 0 in buffer (< 2), fat has 6 (>= 2).
        val first = coordinator.nextBatch(isInitial = true, filterCandidates = passAllFilter())
        first.slots.shouldHaveSize(6)
        fatSource.requests shouldContainExactly listOf(1 to null)
        thinSource.requests shouldContainExactly listOf(1 to null)

        // Batch 2 MUST refetch ONLY thin (page 2), leaving fat untouched at page 1!
        val second = coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        second.slots.shouldHaveSize(6)
        fatSource.requests shouldContainExactly listOf(1 to null)
        thinSource.requests shouldContainExactly listOf(1 to null, 2 to null)
        coordinator.stop()
    }

    @Test
    fun `cursor-based and integer-page sources maintain independent contract v17 pagination cursors`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cursorSource = FakeFeedSource(101L) { _, cursor ->
            when (cursor) {
                null -> FeedPage(listOf(item("c-1")), hasNextPage = true, nextCursor = "tok-A")
                "tok-A" -> FeedPage(listOf(item("c-2")), hasNextPage = true, nextCursor = "tok-B")
                else -> FeedPage(listOf(item("c-3")), hasNextPage = false, nextCursor = null)
            }
        }
        val pageSource = FakeFeedSource(102L) { page, _ ->
            FeedPage(listOf(item("p-$page")), hasNextPage = page < 3, nextCursor = null)
        }

        val coordinator = ReelsMultiSourceCoordinator(
            ioDispatcher = dispatcher,
            random = Random(3L),
            nowMonoMs = { testScheduler.currentTime },
            batchSize = 2,
        )
        coordinator.reset(this, listOf(cursorSource, pageSource), emptySet(), burst = 1)

        repeat(3) {
            coordinator.nextBatch(isInitial = it == 0, filterCandidates = passAllFilter())
        }

        cursorSource.requests shouldContainExactly listOf(1 to null, 2 to "tok-A", 3 to "tok-B")
        pageSource.requests shouldContainExactly listOf(1 to null, 2 to null, 3 to null)
        coordinator.stop()
    }

    @Test
    fun `progressive startup emits fast source after grace window before slow source times out`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val hungGate = CompletableDeferred<FeedPage>()
        val fastSource = FakeFeedSource(1L) { _, _ ->
            delay(10L)
            FeedPage(listOf(item("fast-1"), item("fast-2")), hasNextPage = false)
        }
        val hungSource = FakeFeedSource(2L) { _, _ ->
            hungGate.await()
        }

        val coordinator = ReelsMultiSourceCoordinator(
            ioDispatcher = dispatcher,
            random = Random(1L),
            nowMonoMs = { testScheduler.currentTime },
            sourceTimeoutMs = 5_000L,
            startupGraceMs = 500L,
            progressiveMinSources = 2,
        )
        coordinator.reset(this, listOf(fastSource, hungSource), emptySet(), burst = 2)

        val deferredBatch = async {
            coordinator.nextBatch(isInitial = true, filterCandidates = passAllFilter())
        }

        // Fast source responds at 10ms, grace period expires at 510ms -> batch returns while hung source is still pending.
        testScheduler.advanceTimeBy(1_000L)
        testScheduler.runCurrent()

        deferredBatch.isCompleted shouldBe true
        val initialBatch = deferredBatch.await()
        initialBatch.slots.map { it.sourceId to it.item.id } shouldContainExactly listOf(
            1L to "fast-1",
            1L to "fast-2",
        )
        initialBatch.hasMore shouldBe true

        // Once sourceTimeoutMs (5_000ms) elapses, hung source times out and transitions into cooldown.
        testScheduler.advanceTimeBy(4_500L)
        testScheduler.runCurrent()
        coordinator.currentStatuses()[2L] shouldBe MixSourceStatus.COOLDOWN
        coordinator.stop()
    }

    @Test
    fun `auth errors isolate plugin in NEEDS_AUTH while rate limits cool down and recover`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var wallTime = 1_000L
        var rateLimitedShouldPass = false
        val authSource = ClassifiedErrorSource(
            id = 201L,
            classifier = { FeedError.AuthRequired },
        ) { _, _ ->
            throw IllegalStateException("Unauthorized")
        }
        val http403Source = FakeFeedSource(202L) { _, _ ->
            throw IllegalStateException("HTTP 403 Forbidden")
        }
        val rateLimitedSource = ClassifiedErrorSource(
            id = 203L,
            classifier = { FeedError.RateLimited(retryAtEpochMillis = 11_000L) },
        ) { _, _ ->
            if (!rateLimitedShouldPass) {
                throw IllegalStateException("429 Too Many Requests")
            }
            FeedPage(listOf(item("recovered-1")), hasNextPage = false)
        }
        val healthySource = FakeFeedSource(204L) { _, _ ->
            FeedPage(listOf(item("ok-1")), hasNextPage = true)
        }

        val coordinator = ReelsMultiSourceCoordinator(
            ioDispatcher = dispatcher,
            nowMonoMs = { testScheduler.currentTime },
            nowWallMs = { wallTime },
            startupGraceMs = 200L,
        )
        coordinator.reset(
            this,
            listOf(authSource, http403Source, rateLimitedSource, healthySource),
            emptySet(),
            burst = 1,
        )

        val b1 = coordinator.nextBatch(isInitial = true, filterCandidates = passAllFilter())
        coordinator.awaitPendingFetches()
        b1.statuses[201L] shouldBe MixSourceStatus.NEEDS_AUTH
        b1.statuses[202L] shouldBe MixSourceStatus.NEEDS_AUTH
        b1.statuses[203L] shouldBe MixSourceStatus.COOLDOWN
        b1.statuses[204L] shouldBe MixSourceStatus.ACTIVE

        // Still inside 10_000ms cooldown: neither authSource nor rateLimitedSource is called.
        testScheduler.advanceTimeBy(5_000L)
        wallTime += 5_000L
        val callsBeforeRecovery = rateLimitedSource.callCount
        coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        rateLimitedSource.callCount shouldBe callsBeforeRecovery
        authSource.callCount shouldBe 1

        // Advance past 10_000ms cooldown: rateLimitedSource recovers cleanly, authSource stays quarantined.
        rateLimitedShouldPass = true
        testScheduler.advanceTimeBy(6_000L)
        wallTime += 6_000L
        val b3 = coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        b3.slots.any { it.sourceId == 203L && it.item.id == "recovered-1" } shouldBe true
        b3.statuses[201L] shouldBe MixSourceStatus.NEEDS_AUTH
        authSource.callCount shouldBe 1
        coordinator.stop()
    }

    @Test
    fun `circuit breaker trips permanently after configured consecutive transient failures`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var brokenCalls = 0
        val brokenSource = FakeFeedSource(301L) { _, _ ->
            brokenCalls++
            throw IllegalStateException("Socket timeout")
        }
        val healthySource = FakeFeedSource(302L) { page, _ ->
            FeedPage(listOf(item("good-$page")), hasNextPage = true)
        }

        val coordinator = ReelsMultiSourceCoordinator(
            ioDispatcher = dispatcher,
            nowMonoMs = { testScheduler.currentTime },
            circuitBreakerThreshold = 3,
            transientCooldownMs = 1_000L,
            batchSize = 1,
        )
        coordinator.reset(this, listOf(brokenSource, healthySource), emptySet(), burst = 1)

        // Failure 1 -> COOLDOWN (1_000ms)
        coordinator.nextBatch(isInitial = true, filterCandidates = passAllFilter())
        coordinator.awaitPendingFetches()
        coordinator.currentStatuses()[301L] shouldBe MixSourceStatus.COOLDOWN

        // Failure 2 -> COOLDOWN (2_000ms)
        testScheduler.advanceTimeBy(1_500L)
        coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        coordinator.awaitPendingFetches()
        coordinator.currentStatuses()[301L] shouldBe MixSourceStatus.COOLDOWN

        // Failure 3 -> CIRCUIT_OPEN (exhausted for remainder of session)
        testScheduler.advanceTimeBy(2_500L)
        coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        coordinator.awaitPendingFetches()
        coordinator.currentStatuses()[301L] shouldBe MixSourceStatus.CIRCUIT_OPEN
        brokenCalls shouldBe 3

        // Subsequent pulls after long delay never hit brokenSource again.
        testScheduler.advanceTimeBy(60_000L)
        val b4 = coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        brokenCalls shouldBe 3
        b4.slots.shouldHaveSize(1)
        b4.slots.first().sourceId shouldBe 302L
        coordinator.stop()
    }

    @Test
    fun `empty filtered refills stop at maxEmptyRefills cap and count filtered unseen items`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val allSeenSource = FakeFeedSource(401L) { page, _ ->
            FeedPage(
                videos = listOf(item("old-${page}a"), item("old-${page}b")),
                hasNextPage = true,
            )
        }

        val coordinator = ReelsMultiSourceCoordinator(
            ioDispatcher = dispatcher,
            nowMonoMs = { testScheduler.currentTime },
            maxEmptyRefills = 3,
        )
        coordinator.reset(this, listOf(allSeenSource), emptySet(), burst = 2)

        val batch = coordinator.nextBatch(isInitial = true) { _, candidates ->
            MixFilterVerdict(kept = emptyList(), unseenFilteredCount = candidates.size)
        }

        batch.slots.shouldBeEmpty()
        batch.hasMore shouldBe false
        batch.consumedHiddenCount shouldBe 6
        batch.statuses[401L] shouldBe MixSourceStatus.EXHAUSTED
        allSeenSource.requests shouldHaveSize 3
        coordinator.stop()
    }

    @Test
    fun `composite deduplication isolates per source so colliding item ids across plugins are both kept`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val s1 = FakeFeedSource(501L) { page, _ ->
            when (page) {
                1 -> FeedPage(listOf(item("shared-42"), item("shared-42")), hasNextPage = true)
                else -> FeedPage(listOf(item("shared-42"), item("unique-a")), hasNextPage = false)
            }
        }
        val s2 = FakeFeedSource(502L) { _, _ ->
            FeedPage(listOf(item("shared-42")), hasNextPage = false)
        }

        val coordinator = ReelsMultiSourceCoordinator(
            ioDispatcher = dispatcher,
            random = Random(9L),
            nowMonoMs = { testScheduler.currentTime },
        )
        coordinator.reset(this, listOf(s1, s2), emptySet(), burst = 1)

        val b1 = coordinator.nextBatch(isInitial = true, filterCandidates = passAllFilter())
        val b2 = coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        val emitted = (b1.slots + b2.slots).map { it.sourceId to it.item.id }.toSet()

        emitted shouldBe setOf(
            501L to "shared-42",
            501L to "unique-a",
            502L to "shared-42",
        )
        coordinator.stop()
    }

    @Test
    fun `dynamic exclude re-include and burst size changes preserve cursors and purge hidden items`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val s1 = FakeFeedSource(601L) { page, _ ->
            when (page) {
                1 -> FeedPage(
                    listOf(item("a1", "bad-author"), item("a2", "good"), item("a3", "good")),
                    hasNextPage = true,
                )
                else -> FeedPage(listOf(item("a4", "good")), hasNextPage = false)
            }
        }
        val s2 = FakeFeedSource(602L) { _, _ ->
            FeedPage(listOf(item("b1"), item("b2"), item("b3")), hasNextPage = false)
        }

        val coordinator = ReelsMultiSourceCoordinator(
            ioDispatcher = dispatcher,
            random = Random(5L),
            nowMonoMs = { testScheduler.currentTime },
            batchSize = 2,
        )
        coordinator.reset(this, listOf(s1, s2), emptySet(), burst = 1)

        // First batch loads page 1 into buffers (a1, a2, a3 and b1, b2, b3), emits 2 total (1 per source).
        val first = coordinator.nextBatch(isInitial = true, filterCandidates = passAllFilter())
        first.slots.shouldHaveSize(2)

        // Dynamically purge "bad-author" and exclude source 602L, then increase burst to 3.
        coordinator.purgeHidden(601L, hiddenVideoIds = setOf("a3"), hiddenAuthors = setOf("bad-author"))
        coordinator.updateExcludedSources(setOf(602L))
        coordinator.setBurstSize(3)

        val second = coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        second.slots.none { it.sourceId == 602L } shouldBe true
        second.slots.none { it.item.id == "a1" || it.item.id == "a3" } shouldBe true
        coordinator.currentStatuses()[602L] shouldBe MixSourceStatus.EXCLUDED

        // Exclude 601L as well -> buffer cleared, hasMore becomes false; re-including 601L resumes without losing page=2.
        coordinator.updateExcludedSources(setOf(601L, 602L))
        val emptyBatch = coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        emptyBatch.slots.shouldBeEmpty()
        emptyBatch.hasMore shouldBe false

        coordinator.updateExcludedSources(setOf(602L))
        val resumed = coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        // If any page 2 was needed, s1 started from page 2 (never restarted at page 1!).
        s1.requests.count { it.first == 1 } shouldBe 1
        resumed.statuses[601L] shouldBe MixSourceStatus.EXHAUSTED
        coordinator.stop()
    }

    @Test
    fun `fast source does not monopolize batch while slower peer is still fetching`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fastSource = FakeFeedSource(701L) { _, _ ->
            delay(10L)
            FeedPage((1..10).map { item("fast-$it") }, hasNextPage = false)
        }
        val slowSource = FakeFeedSource(702L) { _, _ ->
            delay(1_500L)
            FeedPage((1..4).map { item("slow-$it") }, hasNextPage = false)
        }

        val coordinator = ReelsMultiSourceCoordinator(
            ioDispatcher = dispatcher,
            random = Random(9L),
            nowMonoMs = { testScheduler.currentTime },
            sourceTimeoutMs = 5_000L,
            startupGraceMs = 500L,
            progressiveMinSources = 2,
            batchSize = 18,
        )
        coordinator.reset(this, listOf(fastSource, slowSource), emptySet(), burst = 2)

        val deferredFirst = async {
            coordinator.nextBatch(isInitial = true, filterCandidates = passAllFilter())
        }
        testScheduler.advanceTimeBy(1_000L)
        testScheduler.runCurrent()

        deferredFirst.isCompleted shouldBe true
        val first = deferredFirst.await()
        // Only a single burst (2 items) from fastSource is emitted while slowSource is still fetching.
        first.slots.map { it.sourceId to it.item.id } shouldContainExactly listOf(
            701L to "fast-1",
            701L to "fast-2",
        )
        first.hasMore shouldBe true

        // Once slowSource finishes at 1_500ms, batch 2 puts slowSource first (no boundary repeat) and mixes both.
        testScheduler.advanceTimeBy(600L)
        testScheduler.runCurrent()
        val second = coordinator.nextBatch(isInitial = false, filterCandidates = passAllFilter())
        second.slots.first().sourceId shouldBe 702L
        second.slots.any { it.sourceId == 702L } shouldBe true
        second.slots.any { it.sourceId == 701L } shouldBe true
        coordinator.stop()
    }
}
