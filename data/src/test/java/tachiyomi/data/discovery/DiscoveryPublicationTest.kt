package tachiyomi.data.discovery

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.domain.discovery.model.DISCOVERY_RETIRE_MS
import tachiyomi.domain.discovery.model.DiscoveryBatchReceipt
import tachiyomi.domain.discovery.model.DiscoveryFactKind
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPreparedPool
import tachiyomi.domain.discovery.model.DiscoveryPublicationOutcome
import tachiyomi.domain.discovery.model.DiscoveryPublishReceipt
import tachiyomi.domain.discovery.model.DiscoveryQueryEvidence
import tachiyomi.domain.discovery.model.DiscoveryRetirementPolicy
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourceCursor
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.model.DiscoveryTitleMemory
import tachiyomi.domain.discovery.model.DiscoveryTopUpReceipt
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle

class DiscoveryPublicationTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: Database
    private lateinit var repository: DiscoveryRepositoryImpl
    private lateinit var knownProvider: MutableKnownTitlesProvider
    private val media = DiscoveryMediaType.MANGA

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        database = Database(
            driver = driver,
            chaptersAdapter = data.Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            historyAdapter = data.History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = data.Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        knownProvider = MutableKnownTitlesProvider()
        repository = DiscoveryRepositoryImpl(
            AndroidMangaDatabaseHandler(
                db = database,
                driver = driver,
                queryDispatcher = Dispatchers.Default,
                transactionDispatcher = Dispatchers.Default,
            ),
            knownTitlesProvider = knownProvider,
            candidateValidator = AllowAllCandidateValidator,
        )
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    private fun suggestion(number: Int): DiscoverySuggestion = DiscoverySuggestion(
        id = number.toLong(),
        mediaType = media,
        rowType = DiscoveryRowType.SOURCE,
        title = "Title $number",
        cleanTitle = normalizeDiscoveryTitle("Title $number"),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "source",
        score = 1.0,
        position = number.toLong(),
        createdAt = 0L,
        sourceId = 1L,
        sourceUrl = "/title-$number",
        identityKey = "key:$number",
    )

    private suspend fun publish(
        requestId: String,
        pool: DiscoveryPreparedPool,
        expectedGeneration: Long? = null,
        retirement: DiscoveryRetirementPolicy = DiscoveryRetirementPolicy.RETIRE_ALL,
    ): DiscoveryPublishReceipt {
        val expected = expectedGeneration ?: repository.publication(media).generation
        val token = repository.reserveGeneration(media, requestId, expected, "cfg")!!
        return repository.publishGeneration(token, pool, retirement)
    }

    @Test
    fun `published generation replaces the pool and next batch never returns retired titles`() = runTest {
        val first = publish("req-1", DiscoveryPreparedPool.Fresh((1..25).map(::suggestion), partial = false))
        val firstSnapshot = (first as DiscoveryPublishReceipt.Published).snapshot
        firstSnapshot.outcome shouldBe DiscoveryPublicationOutcome.READY
        firstSnapshot.suggestions.size shouldBe 25

        val batch = repository.nextHomeBatch(media, firstSnapshot.generation, (1..25).map { "key:$it" })
        val batchSnapshot = (batch as DiscoveryBatchReceipt.Available).snapshot
        batchSnapshot.homeBatchKeys.size shouldBe 20

        val second = publish("req-2", DiscoveryPreparedPool.Fresh((26..30).map(::suggestion), partial = false))
        val secondSnapshot = (second as DiscoveryPublishReceipt.Published).snapshot
        secondSnapshot.suggestions.all { it.identityKey in (26..30).map(::keyOf) } shouldBe true

        val eligibility = repository.eligibility(media)
        (1..25).forEach { number ->
            (keyOf(number) in eligibility.excludedIdentityKeys) shouldBe true
        }
    }

    @Test
    fun `verified empty publishes exhaustion and retires the old pool`() = runTest {
        publish("req-1", DiscoveryPreparedPool.Fresh((1..3).map(::suggestion), partial = false))
        val empty = DiscoveryPreparedPool.VerifiedEmpty(
            expectedQueryKeys = setOf("source:1:popular"),
            evidence = listOf(
                DiscoveryQueryEvidence(
                    queryKey = "source:1:popular",
                    transportOk = true,
                    rawEnd = true,
                    pending = 0,
                    unattempted = 0,
                    metadataFailures = 0,
                    budgetStopped = false,
                    duplicatePageStopped = false,
                ),
            ),
        )
        val receipt = publish("req-2", empty)

        val snapshot = (receipt as DiscoveryPublishReceipt.Published).snapshot
        snapshot.outcome shouldBe DiscoveryPublicationOutcome.EXHAUSTED
        snapshot.suggestions.shouldBeEmpty()
        val eligibility = repository.eligibility(media)
        (1..3).forEach { number ->
            (keyOf(number) in eligibility.excludedIdentityKeys) shouldBe true
        }
    }

    @Test
    fun `unverifiable empty result does not publish`() = runTest {
        publish("req-1", DiscoveryPreparedPool.Fresh((1..3).map(::suggestion), partial = false))
        val before = repository.publication(media)

        val invalid = DiscoveryPreparedPool.VerifiedEmpty(
            expectedQueryKeys = setOf("source:1:popular"),
            evidence = listOf(
                DiscoveryQueryEvidence(
                    queryKey = "source:1:popular",
                    transportOk = false,
                    rawEnd = false,
                    pending = 1,
                    unattempted = 1,
                    metadataFailures = 0,
                    budgetStopped = true,
                    duplicatePageStopped = false,
                ),
            ),
        )
        val receipt = publish("req-2", invalid)

        receipt shouldBe DiscoveryPublishReceipt.NoAdmissibleItems
        repository.publication(media).generation shouldBe before.generation
    }

    @Test
    fun `stale owner cannot publish a superseded generation`() = runTest {
        val expected = repository.publication(media).generation
        val staleToken = repository.reserveGeneration(media, "req-old", expected, "cfg")!!
        val freshToken = repository.reserveGeneration(media, "req-new", expected, "cfg")!!

        val receipt = repository.publishGeneration(
            staleToken,
            DiscoveryPreparedPool.Fresh(listOf(suggestion(1)), partial = false),
        )

        receipt shouldBe DiscoveryPublishReceipt.Superseded(0L)
        val freshReceipt = repository.publishGeneration(
            freshToken,
            DiscoveryPreparedPool.Fresh(listOf(suggestion(2)), partial = false),
        )
        (freshReceipt as DiscoveryPublishReceipt.Published).snapshot.suggestions.single().identityKey shouldBe "key:2"
    }

    @Test
    fun `fact memory is independent of taste reset`() = runTest {
        val identity = DiscoveryTitleIdentity(
            mediaType = media,
            identityKey = "key:7",
            title = "Title 7",
            primaryCleanTitle = "title 7",
        )
        repository.recordFact(identity, DiscoveryFactKind.OPENED)
        repository.recordFact(identity, DiscoveryFactKind.STARTED)

        repository.clearTaste(media)

        val memory = repository.observeTitleMemory(media).first().single()
        memory.opened.active shouldBe true
        memory.started.active shouldBe true
        repository.getTasteRecords(media).shouldBeEmpty()
        (identity.identityKey in repository.eligibility(media).excludedIdentityKeys) shouldBe true
    }

    @Test
    fun `clearing a fact keeps the other facts and stops excluding the title`() = runTest {
        val identity = DiscoveryTitleIdentity(
            mediaType = media,
            identityKey = "key:8",
            title = "Title 8",
            primaryCleanTitle = "title 8",
        )
        repository.recordFact(identity, DiscoveryFactKind.OPENED)
        repository.recordFact(identity, DiscoveryFactKind.EXPLICIT_CONSUMED)

        repository.clearFact(media, identity.identityKey, DiscoveryFactKind.OPENED)
        repository.clearFact(media, identity.identityKey, DiscoveryFactKind.EXPLICIT_CONSUMED)

        val memory = repository.observeTitleMemory(media).first().single()
        memory.opened.active shouldBe false
        memory.consumed.active shouldBe false
        (identity.identityKey in repository.eligibility(media).excludedIdentityKeys) shouldBe false
    }

    @Test
    fun `known titles are excluded before the batch limit`() = runTest {
        val published = publish("req-1", DiscoveryPreparedPool.Fresh((1..25).map(::suggestion), partial = false))
        val generation = (published as DiscoveryPublishReceipt.Published).snapshot.generation
        knownProvider.titles[media] = setOf(
            DiscoveryTitleIdentity(
                mediaType = media,
                identityKey = "key:1",
                title = "Title 1",
                primaryCleanTitle = "title 1",
                sourceId = 1L,
                sourceUrl = "/title-1",
            ),
        )

        val batch = repository.nextHomeBatch(media, generation, (1..25).map { "key:$it" })

        val keys = (batch as DiscoveryBatchReceipt.Available).snapshot.homeBatchKeys
        ("key:1" in keys) shouldBe false
        keys.size shouldBe 20
    }

    @Test
    fun `publication round-trips source cursors through storage`() = runTest {
        val cursors = listOf(
            DiscoverySourceCursor(
                sourceId = 0L,
                queryKey = "cfg-1",
                nextPage = 7,
                exhausted = false,
            ),
        )
        val receipt = publish(
            "req-1",
            DiscoveryPreparedPool.Fresh(
                suggestions = listOf(suggestion(1)),
                partial = false,
                cursors = cursors,
            ),
        )

        (receipt as DiscoveryPublishReceipt.Published).snapshot.cursors shouldBe cursors
        repository.publication(media).cursors shouldBe cursors
    }

    @Test
    fun `clearRetirementBlocks frees blocked titles but keeps consumed facts`() = runTest {
        val now = System.currentTimeMillis()
        val items = (1..3).map(::suggestion)
        publish("req-1", DiscoveryPreparedPool.Fresh(items, partial = false))

        database.discovery_title_memoryQueries.ensureRow(
            media_type = media.key,
            identity_key = keyOf(1),
            title = "Title 1",
            primary_clean_title = normalizeDiscoveryTitle("Title 1"),
            source_id = 1L,
            source_url = "/title-1",
            trusted_aliases = "",
            metadata_changed_at = now,
        )
        database.discovery_title_memoryQueries.ensureRow(
            media_type = media.key,
            identity_key = keyOf(2),
            title = "Title 2",
            primary_clean_title = normalizeDiscoveryTitle("Title 2"),
            source_id = 1L,
            source_url = "/title-2",
            trusted_aliases = "",
            metadata_changed_at = now,
        )
        database.discovery_title_memoryQueries.ensureRow(
            media_type = media.key,
            identity_key = keyOf(3),
            title = "Title 3",
            primary_clean_title = normalizeDiscoveryTitle("Title 3"),
            source_id = 1L,
            source_url = "/title-3",
            trusted_aliases = "",
            metadata_changed_at = now,
        )
        database.discovery_title_memoryQueries.retireBlocked(now + 7 * 86_400_000L, media.key, keyOf(1))
        database.discovery_title_memoryQueries.retireBlocked(now + 7 * 86_400_000L, media.key, keyOf(2))
        database.discovery_title_memoryQueries.retireBlocked(now + 7 * 86_400_000L, media.key, keyOf(3))

        repository.recordFact(
            DiscoveryTitleIdentity(
                mediaType = media,
                identityKey = keyOf(1),
                title = "Title 1",
                primaryCleanTitle = normalizeDiscoveryTitle("Title 1"),
            ),
            DiscoveryFactKind.EXPLICIT_CONSUMED,
        )

        val cleared = repository.clearRetirementBlocks()
        cleared shouldBe 3

        val memoryList = repository.observeTitleMemory(media).first()
        val memoryMap = memoryList.associateBy { it.identity.identityKey }
        memoryMap[keyOf(2)]?.blockedUntil shouldBe 0L
        memoryMap[keyOf(3)]?.blockedUntil shouldBe 0L

        val eligibility = repository.eligibility(media)
        (keyOf(1) in eligibility.excludedIdentityKeys) shouldBe true
        (keyOf(2) in eligibility.excludedIdentityKeys) shouldBe false
        (keyOf(3) in eligibility.excludedIdentityKeys) shouldBe false
    }

    @Test
    fun `clearRetirementBlocks is idempotent`() = runTest {
        val now = System.currentTimeMillis()
        database.discovery_title_memoryQueries.ensureRow(
            media_type = media.key,
            identity_key = keyOf(1),
            title = "Title 1",
            primary_clean_title = normalizeDiscoveryTitle("Title 1"),
            source_id = 1L,
            source_url = "/title-1",
            trusted_aliases = "",
            metadata_changed_at = now,
        )
        database.discovery_title_memoryQueries.retireBlocked(now + 7 * 86_400_000L, media.key, keyOf(1))

        repository.clearRetirementBlocks() shouldBe 1
        repository.clearRetirementBlocks() shouldBe 0
    }

    @Test
    fun `restore clamps long blocks`() = runTest {
        val now = System.currentTimeMillis()
        val identity = DiscoveryTitleIdentity(
            mediaType = media,
            identityKey = keyOf(1),
            title = "Title 1",
            primaryCleanTitle = normalizeDiscoveryTitle("Title 1"),
        )
        val record = DiscoveryTitleMemory(
            identity = identity,
            blockedUntil = now + 7 * 86_400_000L,
        )
        repository.restoreTitleMemory(listOf(record))

        val memory = repository.observeTitleMemory(media).first().first { it.identity.identityKey == keyOf(1) }
        (memory.blockedUntil <= now + tachiyomi.domain.discovery.model.DISCOVERY_RETIRE_MS + 10_000L) shouldBe true
        (memory.blockedUntil > now + 24 * 3_600_000L) shouldBe true
    }

    @Test
    fun `background publish retires only seen titles for 48 hours and keeps unseen available`() = runTest {
        val gen1Items = (1..5).map(::suggestion)
        val receipt1 = publish("req-1", DiscoveryPreparedPool.Fresh(gen1Items, partial = false))
        val gen1CommittedAt = (receipt1 as DiscoveryPublishReceipt.Published).snapshot.committedAt

        repository.recordExposure(media, listOf(gen1Items[0].cleanTitle, gen1Items[1].cleanTitle))

        val gen2Items = (6..10).map(::suggestion)
        val receipt2 = publish(
            "req-2",
            DiscoveryPreparedPool.Fresh(gen2Items, partial = false),
            retirement = DiscoveryRetirementPolicy.SEEN_ONLY,
        )
        val gen2CommittedAt = (receipt2 as DiscoveryPublishReceipt.Published).snapshot.committedAt

        val memoryList = repository.observeTitleMemory(media).first()
        val memoryMap = memoryList.associateBy { it.identity.identityKey }

        memoryMap[keyOf(1)]?.blockedUntil shouldBe (gen2CommittedAt + DISCOVERY_RETIRE_MS)
        memoryMap[keyOf(2)]?.blockedUntil shouldBe (gen2CommittedAt + DISCOVERY_RETIRE_MS)

        (memoryMap[keyOf(3)]?.blockedUntil ?: 0L) shouldBe 0L
        (memoryMap[keyOf(4)]?.blockedUntil ?: 0L) shouldBe 0L
        (memoryMap[keyOf(5)]?.blockedUntil ?: 0L) shouldBe 0L

        val eligibility = repository.eligibility(media)
        (keyOf(1) in eligibility.excludedIdentityKeys) shouldBe true
        (keyOf(2) in eligibility.excludedIdentityKeys) shouldBe true
        (keyOf(3) in eligibility.excludedIdentityKeys) shouldBe false
        (keyOf(4) in eligibility.excludedIdentityKeys) shouldBe false
        (keyOf(5) in eligibility.excludedIdentityKeys) shouldBe false
    }

    @Test
    fun `manual publish retires all old pool titles for 48 hours`() = runTest {
        val gen1Items = (1..5).map(::suggestion)
        publish("req-1", DiscoveryPreparedPool.Fresh(gen1Items, partial = false))

        val gen2Items = (6..10).map(::suggestion)
        val receipt2 = publish(
            "req-2",
            DiscoveryPreparedPool.Fresh(gen2Items, partial = false),
            retirement = DiscoveryRetirementPolicy.RETIRE_ALL,
        )
        val gen2CommittedAt = (receipt2 as DiscoveryPublishReceipt.Published).snapshot.committedAt

        val memoryList = repository.observeTitleMemory(media).first()
        val memoryMap = memoryList.associateBy { it.identity.identityKey }
        (1..5).forEach { num ->
            memoryMap[keyOf(num)]?.blockedUntil shouldBe (gen2CommittedAt + DISCOVERY_RETIRE_MS)
        }
    }

    @Test
    fun `home batch titles count as seen in background publish`() = runTest {
        val gen1Items = (1..5).map(::suggestion)
        val snap1 = (
            publish(
                "req-1",
                DiscoveryPreparedPool.Fresh(gen1Items, partial = false),
            ) as DiscoveryPublishReceipt.Published
            ).snapshot

        repository.nextHomeBatch(media, snap1.generation, listOf(keyOf(3)))

        val gen2Items = (6..10).map(::suggestion)
        val receipt2 = publish(
            "req-2",
            DiscoveryPreparedPool.Fresh(gen2Items, partial = false),
            retirement = DiscoveryRetirementPolicy.SEEN_ONLY,
        )
        val gen2CommittedAt = (receipt2 as DiscoveryPublishReceipt.Published).snapshot.committedAt

        val memoryMap = repository.observeTitleMemory(media).first().associateBy { it.identity.identityKey }
        memoryMap[keyOf(3)]?.blockedUntil shouldBe (gen2CommittedAt + DISCOVERY_RETIRE_MS)
        (memoryMap[keyOf(1)]?.blockedUntil ?: 0L) shouldBe 0L
    }

    @Test
    fun `unseen titles from background publish can return in a later generation`() = runTest {
        val itemsA = (1..3).map(::suggestion)
        publish("req-1", DiscoveryPreparedPool.Fresh(itemsA, partial = false))

        val itemsB = (4..6).map(::suggestion)
        publish(
            "req-2",
            DiscoveryPreparedPool.Fresh(itemsB, partial = false),
            retirement = DiscoveryRetirementPolicy.SEEN_ONLY,
        )

        val receipt3 = publish(
            "req-3",
            DiscoveryPreparedPool.Fresh(itemsA, partial = false),
            retirement = DiscoveryRetirementPolicy.SEEN_ONLY,
        )
        val snap3 = (receipt3 as DiscoveryPublishReceipt.Published).snapshot
        snap3.suggestions.map { it.identityKey } shouldBe itemsA.map { it.identityKey }
    }

    @Test
    fun `top up appends after existing cards and keeps generation and batch`() = runTest {
        val initialItems = (1..5).map(::suggestion)
        val snap1 = (
            publish(
                "req-1",
                DiscoveryPreparedPool.Fresh(initialItems, partial = false),
            ) as DiscoveryPublishReceipt.Published
            ).snapshot

        val batchReceipt = repository.nextHomeBatch(media, snap1.generation, (1..5).map { keyOf(it) })
        val batchSnapshot = (batchReceipt as DiscoveryBatchReceipt.Available).snapshot

        val additions = (6..8).map(::suggestion)
        val topUpReceipt = repository.topUpPool(media, snap1.generation, additions, emptyList(), 60)
        (topUpReceipt is DiscoveryTopUpReceipt.ToppedUp) shouldBe true

        val toppedUp = topUpReceipt as DiscoveryTopUpReceipt.ToppedUp
        toppedUp.added shouldBe 3
        toppedUp.snapshot.generation shouldBe snap1.generation
        toppedUp.snapshot.homeBatchKeys shouldBe batchSnapshot.homeBatchKeys
        toppedUp.snapshot.issuedKeys shouldBe batchSnapshot.issuedKeys
        toppedUp.snapshot.suggestions.map { it.identityKey } shouldBe (1..8).map { keyOf(it) }
    }

    @Test
    fun `top up prunes consumed rows`() = runTest {
        val initialItems = (1..5).map(::suggestion)
        val snap1 = (
            publish(
                "req-1",
                DiscoveryPreparedPool.Fresh(initialItems, partial = false),
            ) as DiscoveryPublishReceipt.Published
            ).snapshot

        repository.recordFact(
            DiscoveryTitleIdentity(
                mediaType = media,
                identityKey = keyOf(1),
                title = "Title 1",
                primaryCleanTitle = normalizeDiscoveryTitle("Title 1"),
            ),
            DiscoveryFactKind.EXPLICIT_CONSUMED,
        )

        val additions = (6..7).map(::suggestion)
        val topUpReceipt = repository.topUpPool(media, snap1.generation, additions, emptyList(), 60)
        val toppedUp = topUpReceipt as DiscoveryTopUpReceipt.ToppedUp

        toppedUp.added shouldBe 2
        toppedUp.snapshot.suggestions.none { it.identityKey == keyOf(1) } shouldBe true
        toppedUp.snapshot.suggestions.map { it.identityKey } shouldBe listOf(2, 3, 4, 5, 6, 7).map { keyOf(it) }
        toppedUp.eligibleAfter shouldBe 6
    }

    @Test
    fun `top up respects maxPoolSize`() = runTest {
        val initialItems = (1..58).map(::suggestion)
        val snap1 = (
            publish(
                "req-1",
                DiscoveryPreparedPool.Fresh(initialItems, partial = false),
            ) as DiscoveryPublishReceipt.Published
            ).snapshot

        val additions = (59..63).map(::suggestion)
        val topUpReceipt = repository.topUpPool(media, snap1.generation, additions, emptyList(), 60)
        val toppedUp = topUpReceipt as DiscoveryTopUpReceipt.ToppedUp

        toppedUp.added shouldBe 2
        toppedUp.snapshot.suggestions.size shouldBe 60
        toppedUp.snapshot.suggestions.map { it.identityKey } shouldBe (1..60).map { keyOf(it) }
    }

    @Test
    fun `top up rejects keys already in pool and known titles`() = runTest {
        val initialItems = (1..5).map(::suggestion)
        val snap1 = (
            publish(
                "req-1",
                DiscoveryPreparedPool.Fresh(initialItems, partial = false),
            ) as DiscoveryPublishReceipt.Published
            ).snapshot

        knownProvider.titles[media] = setOf(
            DiscoveryTitleIdentity(
                mediaType = media,
                identityKey = keyOf(6),
                title = "Title 6",
                primaryCleanTitle = normalizeDiscoveryTitle("Title 6"),
                sourceId = 1L,
                sourceUrl = "/title-6",
            ),
        )

        val additions = listOf(suggestion(1), suggestion(6), suggestion(7))
        val topUpReceipt = repository.topUpPool(media, snap1.generation, additions, emptyList(), 60)
        val toppedUp = topUpReceipt as DiscoveryTopUpReceipt.ToppedUp

        toppedUp.added shouldBe 1
        toppedUp.snapshot.suggestions.last().identityKey shouldBe keyOf(7)
    }

    @Test
    fun `top up on stale generation is Superseded and while reserved is Busy`() = runTest {
        val initialItems = (1..5).map(::suggestion)
        val snap1 = (
            publish(
                "req-1",
                DiscoveryPreparedPool.Fresh(initialItems, partial = false),
            ) as DiscoveryPublishReceipt.Published
            ).snapshot

        val staleReceipt = repository.topUpPool(media, snap1.generation - 1, (6..7).map(::suggestion), emptyList(), 60)
        staleReceipt shouldBe DiscoveryTopUpReceipt.Superseded(snap1.generation)

        repository.reserveGeneration(media, "req-reserve", snap1.generation, "cfg")
        val busyReceipt = repository.topUpPool(media, snap1.generation, (6..7).map(::suggestion), emptyList(), 60)
        busyReceipt shouldBe DiscoveryTopUpReceipt.Busy
    }

    @Test
    fun `top up merges cursors`() = runTest {
        val cursor1 = DiscoverySourceCursor(sourceId = 1L, queryKey = "cfg-1", nextPage = 2, exhausted = false)
        val receipt = publish(
            "req-1",
            DiscoveryPreparedPool.Fresh(
                suggestions = listOf(suggestion(1)),
                partial = false,
                cursors = listOf(cursor1),
            ),
        )
        val snap1 = (receipt as DiscoveryPublishReceipt.Published).snapshot

        val updatedCursor1 = DiscoverySourceCursor(sourceId = 1L, queryKey = "cfg-1", nextPage = 3, exhausted = false)
        val cursor2 = DiscoverySourceCursor(sourceId = 2L, queryKey = "cfg-2", nextPage = 1, exhausted = false)
        val topUpReceipt = repository.topUpPool(
            media,
            snap1.generation,
            listOf(suggestion(2)),
            listOf(updatedCursor1, cursor2),
            60,
        )
        val toppedUp = topUpReceipt as DiscoveryTopUpReceipt.ToppedUp
        toppedUp.snapshot.cursors shouldBe listOf(updatedCursor1, cursor2)
        repository.publication(media).cursors shouldBe listOf(updatedCursor1, cursor2)
    }

    @Test
    fun `top up does not retire anything`() = runTest {
        val initialItems = (1..5).map(::suggestion)
        val snap1 = (
            publish(
                "req-1",
                DiscoveryPreparedPool.Fresh(initialItems, partial = false),
            ) as DiscoveryPublishReceipt.Published
            ).snapshot

        repository.topUpPool(media, snap1.generation, (6..7).map(::suggestion), emptyList(), 60)

        val memoryList = repository.observeTitleMemory(media).first()
        memoryList.none { it.blockedUntil > 0L } shouldBe true
    }

    private fun keyOf(number: Int): String = "key:$number"
}
