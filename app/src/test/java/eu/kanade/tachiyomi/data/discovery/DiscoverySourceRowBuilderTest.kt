package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType
import java.io.IOException

class DiscoverySourceRowBuilderTest {

    private class FakeCatalog(
        private val itemsBySource: Map<Long, List<DiscoveryRowItem>> = emptyMap(),
        private val latestBySource: Map<Long, List<DiscoveryRowItem>> = emptyMap(),
        private val failing: Set<Long> = emptySet(),
        private val failingLatest: Set<Long> = emptySet(),
        private val itemsByPage: Map<Pair<Long, Int>, List<DiscoveryRowItem>> = emptyMap(),
    ) : DiscoverySourceCatalog {
        val requested = mutableListOf<Long>()
        val requestedPopular = mutableListOf<Pair<Long, Int>>()
        val requestedLatest = mutableListOf<Pair<Long, Int>>()

        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> {
            requested += sourceId
            requestedPopular += sourceId to page
            if (sourceId in failing) throw IOException("boom $sourceId")
            return itemsByPage[sourceId to page] ?: itemsBySource[sourceId].orEmpty()
        }

        override suspend fun popularWithGenres(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = emptyList()

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> {
            requestedLatest += sourceId to page
            if (sourceId in failingLatest) throw IOException("latest boom $sourceId")
            return itemsByPage[sourceId to page] ?: latestBySource[sourceId].orEmpty()
        }
    }

    private fun items(source: String, n: Int) = (1..n).map {
        DiscoveryRowItem("$source Item$it", "$source item$it".lowercase(), null, null, null, source, 1.0 - it * 0.01)
    }

    private fun context(
        sourceIds: List<Long> = emptyList(),
        sourceId: Long = -1L,
        pageOffset: Int = 1,
    ) = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.MANGA,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        sourceId = sourceId,
        sourceIds = sourceIds,
        pageOffset = pageOffset,
    )

    @Test
    fun `harvest pages fetch consecutive catalogue pages per source`() = runTest {
        val catalog = FakeCatalog(
            itemsBySource = mapOf(1L to items("S1", 10)),
            latestBySource = mapOf(1L to items("S1", 10)),
        )

        DiscoverySourceRowBuilder(catalog, rowCap = 50).build(
            context(sourceIds = listOf(1L), pageOffset = 5).copy(harvestPages = 3),
        )

        catalog.requestedPopular.map { it.second }.distinct().sorted() shouldBe listOf(5, 6, 7)
        catalog.requestedLatest.map { it.second }.distinct().sorted() shouldBe listOf(5, 6, 7)
    }

    @Test
    fun `failed dominant source slots are backfilled from fetched surplus`() = runTest {
        val catalog = FakeCatalog(
            itemsBySource = mapOf(2L to items("S2", 50)),
            failing = setOf(1L),
            failingLatest = setOf(1L),
        )
        val result = DiscoverySourceRowBuilder(catalog, rowCap = 50).build(
            context(sourceIds = listOf(1L, 2L)).copy(sourceWeights = mapOf(1L to 99.0, 2L to 1.0)),
        )
        result.size shouldBe 50
        result.map { it.cleanTitle }.distinct().size shouldBe 50
        catalog.requestedPopular.size shouldBe 2
        catalog.requestedLatest.size shouldBe 2
    }

    @Test
    fun `sparse dominant source slots are backfilled from fetched surplus`() = runTest {
        val catalog = FakeCatalog(itemsBySource = mapOf(1L to items("S1", 2), 2L to items("S2", 50)))
        val result = DiscoverySourceRowBuilder(catalog, rowCap = 50).build(
            context(sourceIds = listOf(1L, 2L)).copy(sourceWeights = mapOf(1L to 99.0, 2L to 1.0)),
        )
        result.size shouldBe 50
        result.count { it.provider == "S1" } shouldBe 2
    }

    @Test
    fun `single weighted source fills up to row cap`() = runTest {
        val catalog = FakeCatalog(mapOf(1L to items("S1", 25)))
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L)))
        result.size shouldBe 20
        result.all { it.provider == "S1" } shouldBe true
    }

    @Test
    fun `three sources interleave round-robin and cap at 20`() = runTest {
        val catalog = FakeCatalog(
            mapOf(
                1L to items("S1", 10),
                2L to items("S2", 10),
                3L to items("S3", 10),
            ),
        )
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L, 2L, 3L)))
        result.size shouldBe 20
        result.take(3).map { it.provider } shouldBe listOf("S1", "S2", "S3")
        result.count { it.provider == "S1" } shouldBe 7
        result.count { it.provider == "S2" } shouldBe 7
        result.count { it.provider == "S3" } shouldBe 6
    }

    @Test
    fun `failing source is skipped without failing the row`() = runTest {
        val catalog = FakeCatalog(
            itemsBySource = mapOf(2L to items("S2", 10)),
            failing = setOf(1L),
        )
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L, 2L)))
        result.size shouldBe 10
        result.all { it.provider == "S2" } shouldBe true
    }

    @Test
    fun `manual refresh rotates source order`() = runTest {
        val catalog = FakeCatalog(
            mapOf(
                1L to items("S1", 10),
                2L to items("S2", 10),
                3L to items("S3", 10),
            ),
        )
        val result = DiscoverySourceRowBuilder(catalog).build(
            context(sourceIds = listOf(1L, 2L, 3L), pageOffset = 2),
        )
        result.take(3).map { it.provider } shouldBe listOf("S2", "S3", "S1")
    }

    @Test
    fun `empty weighted list falls back to legacy single sourceId`() = runTest {
        val catalog = FakeCatalog(mapOf(7L to items("Legacy", 5)))
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceId = 7L))
        result.size shouldBe 5
        catalog.requested shouldBe listOf(7L)
    }

    @Test
    fun `row mixes latest and popular in four to one ratio, latest first`() = runTest {
        val catalog = FakeCatalog(
            itemsBySource = mapOf(1L to items("P", 30)),
            latestBySource = mapOf(1L to items("L", 30)),
        )
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L)))
        result.size shouldBe 20
        result.count { it.provider == "L" } shouldBe 16
        result.count { it.provider == "P" } shouldBe 4
        result.take(16).all { it.provider == "L" } shouldBe true
    }

    @Test
    fun `popular duplicates of latest titles are dropped without holes`() = runTest {
        val catalog = FakeCatalog(
            itemsBySource = mapOf(1L to items("S1", 25)),
            latestBySource = mapOf(1L to items("S1", 25)),
        )
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L)))
        result.size shouldBe 20
        result.map { it.cleanTitle }.distinct().size shouldBe 20
    }

    @Test
    fun `latest is requested with pageOffset for manual refresh pagination`() = runTest {
        val catalog = FakeCatalog(latestBySource = mapOf(1L to items("L", 30)))
        DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L), pageOffset = 2))
        catalog.requestedLatest shouldBe listOf(1L to 2)
    }

    @Test
    fun `popular is requested with pageOffset for manual refresh pagination`() = runTest {
        val catalog = FakeCatalog(itemsBySource = mapOf(1L to items("P", 30)))
        DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L), pageOffset = 3))
        catalog.requestedPopular shouldBe listOf(1L to 3)
    }

    @Test
    fun `popular failure with working latest keeps row alive`() = runTest {
        val catalog = FakeCatalog(
            latestBySource = mapOf(1L to items("L", 10)),
            failing = setOf(1L),
        )
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L)))
        result.size shouldBe 10
        result.all { it.provider == "L" } shouldBe true
    }

    @Test
    fun `latest failure falls back to popular quota`() = runTest {
        val catalog = FakeCatalog(
            itemsBySource = mapOf(1L to items("P", 25)),
            failingLatest = setOf(1L),
        )
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L)))
        result.size shouldBe 20
        result.all { it.provider == "P" } shouldBe true
    }

    @Test
    fun `all sources failing throws so cache is preserved`() = runTest {
        val catalog = FakeCatalog(failing = setOf(1L, 2L))
        val result = runCatching {
            DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L, 2L)))
        }
        result.isFailure shouldBe true
    }

    @Test
    fun `source row stops after the first page without genre matches`() = runTest {
        val catalog = FakeCatalog(
            itemsByPage = mapOf(
                1L to 1 to listOf(
                    DiscoveryRowItem(
                        "Drama", "drama", null, null, null, "s", 1.0,
                        sourceId = 1L, sourceUrl = "/drama", genres = listOf("Drama"),
                    ),
                ),
                1L to 2 to listOf(
                    DiscoveryRowItem(
                        "Fantasy", "fantasy", null, null, null, "s", 1.0,
                        sourceId = 1L, sourceUrl = "/fantasy", genres = listOf("Fantasy"),
                    ),
                ),
            ),
        )

        DiscoverySourceRowBuilder(catalog, rowCap = 50).build(
            context(sourceIds = listOf(1L), pageOffset = 1).copy(
                genreFilterSpec = GenreFilterSpec(ignoredGenres = setOf("Drama")),
                harvestPages = 5,
            ),
        )

        catalog.requestedPopular.map { it.second }.distinct() shouldBe listOf(1)
        catalog.requestedLatest.map { it.second }.distinct() shouldBe listOf(1)
    }

    @Test
    fun `source row pages past retired matches and admits the next page`() = runTest {
        val retired = (1..10).map {
            DiscoveryRowItem(
                "Retired $it", "retired $it", null, null, null, "s", 1.0,
                sourceId = 1L, sourceUrl = "/retired-$it", genres = listOf("Fantasy"),
            )
        }
        val fresh = (1..5).map {
            DiscoveryRowItem(
                "Fresh $it", "fresh $it", null, null, null, "s", 1.0,
                sourceId = 1L, sourceUrl = "/fresh-$it", genres = listOf("Fantasy"),
            )
        }
        val catalog = FakeCatalog(
            itemsByPage = mapOf(1L to 1 to retired, 1L to 2 to fresh),
        )

        val result = DiscoverySourceRowBuilder(catalog, rowCap = 50).build(
            context(sourceIds = listOf(1L), pageOffset = 1).copy(
                harvestPages = 5,
                recentCleanTitles = retired.mapTo(HashSet()) { it.cleanTitle },
            ),
        )

        catalog.requestedPopular.map { it.second }.contains(2) shouldBe true
        result.map { it.cleanTitle }.toSet() shouldBe
            (1..5).mapTo(HashSet()) { "fresh $it" }
    }

    @Test
    fun `an empty page stops the source immediately`() = runTest {
        val catalog = FakeCatalog(
            itemsByPage = mapOf(
                1L to 1 to listOf(
                    DiscoveryRowItem(
                        "Fresh", "fresh", null, null, null, "s", 1.0,
                        sourceId = 1L, sourceUrl = "/fresh", genres = listOf("Fantasy"),
                    ),
                ),
            ),
        )

        DiscoverySourceRowBuilder(catalog, rowCap = 50).build(
            context(sourceIds = listOf(1L), pageOffset = 1).copy(harvestPages = 10),
        )

        catalog.requestedPopular.map { it.second } shouldBe listOf(1, 2)
        catalog.requestedLatest.map { it.second } shouldBe listOf(1, 2)
    }

    @Test
    fun `two pages of retired matches stop the source`() = runTest {
        fun retired(number: Int) = DiscoveryRowItem(
            "Retired $number", "retired $number", null, null, null, "s", 1.0,
            sourceId = 1L, sourceUrl = "/retired-$number", genres = listOf("Fantasy"),
        )
        val catalog = FakeCatalog(
            itemsByPage = mapOf(
                1L to 1 to listOf(
                    DiscoveryRowItem(
                        "Fresh", "fresh", null, null, null, "s", 1.0,
                        sourceId = 1L, sourceUrl = "/fresh", genres = listOf("Fantasy"),
                    ),
                ),
                1L to 2 to listOf(retired(2)),
                1L to 3 to listOf(retired(3)),
            ),
        )

        val result = DiscoverySourceRowBuilder(catalog, rowCap = 50).build(
            context(sourceIds = listOf(1L), pageOffset = 1).copy(
                harvestPages = 10,
                recentCleanTitles = setOf("retired 2", "retired 3"),
            ),
        )

        catalog.requestedPopular.map { it.second } shouldBe listOf(1, 2, 3)
        result.map { it.cleanTitle } shouldBe listOf("fresh")
    }

    @Test
    fun `no sources at all yields empty row`() = runTest {
        val result = DiscoverySourceRowBuilder(FakeCatalog()).build(context())
        result shouldBe emptyList()
    }

    @Test
    fun `builder row type is SOURCE`() {
        DiscoverySourceRowBuilder(FakeCatalog()).rowType shouldBe DiscoveryRowType.SOURCE
    }

    @Test
    fun `fifteen sources query latest and popular for each and cap at 50 with stable round robin`() = runTest {
        val sourceIds = (1L..15L).toList()
        val latestMap = sourceIds.associateWith { id -> items("S$id-L", 10) }
        val popularMap = sourceIds.associateWith { id -> items("S$id-P", 10) }
        val catalog = FakeCatalog(itemsBySource = popularMap, latestBySource = latestMap)

        val builder = DiscoverySourceRowBuilder(catalog, rowCap = 50)
        val result = builder.build(context(sourceIds = sourceIds))

        catalog.requestedLatest.map { it.first }.distinct() shouldBe sourceIds
        catalog.requestedPopular.map { it.first }.distinct() shouldBe sourceIds
        result.size shouldBe 50
        result.take(15).map { it.provider.substringBefore("-") } shouldBe sourceIds.map { "S$it" }

        val rotated = builder.build(context(sourceIds = sourceIds, pageOffset = 2))
        rotated.size shouldBe 50
        rotated.take(15).map { it.provider.substringBefore("-") } shouldBe
            (sourceIds.drop(1) + sourceIds.take(1)).map { "S$it" }
    }

    @Test
    fun `blacklisted and ignored genres are filtered from source row`() = runTest {
        val popular = listOf(
            DiscoveryRowItem("Clean Title", "clean title", null, null, null, "S1", 1.0, genres = listOf("Fantasy")),
            DiscoveryRowItem("Ecchi Title", "ecchi title", null, null, null, "S1", 0.9, genres = listOf("Ecchi")),
            DiscoveryRowItem("Horror Title", "horror title", null, null, null, "S1", 0.8, genres = listOf("Horror")),
        )
        val catalog = FakeCatalog(itemsBySource = mapOf(1L to popular), latestBySource = emptyMap())
        val builder = DiscoverySourceRowBuilder(catalog, rowCap = 10)
        val ctx = context(sourceIds = listOf(1L)).copy(
            blacklistedTags = setOf("Ecchi"),
            ignoredGenres = setOf("Horror"),
        )
        val result = builder.build(ctx)
        result.map { it.title } shouldBe listOf("Clean Title")
    }
}
