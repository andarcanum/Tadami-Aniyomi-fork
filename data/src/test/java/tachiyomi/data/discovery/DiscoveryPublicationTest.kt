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
import tachiyomi.domain.discovery.model.DiscoveryBatchReceipt
import tachiyomi.domain.discovery.model.DiscoveryFactKind
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPreparedPool
import tachiyomi.domain.discovery.model.DiscoveryPublicationOutcome
import tachiyomi.domain.discovery.model.DiscoveryPublishReceipt
import tachiyomi.domain.discovery.model.DiscoveryQueryEvidence
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle

class DiscoveryPublicationTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: DiscoveryRepositoryImpl
    private lateinit var knownProvider: MutableKnownTitlesProvider
    private val media = DiscoveryMediaType.MANGA

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(
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
    ): DiscoveryPublishReceipt {
        val expected = expectedGeneration ?: repository.publication(media).generation
        val token = repository.reserveGeneration(media, requestId, expected, "cfg")!!
        return repository.publishGeneration(token, pool)
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

    private fun keyOf(number: Int): String = "key:$number"
}
