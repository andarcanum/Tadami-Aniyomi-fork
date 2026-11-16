package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.source.manga.service.MangaSourceManager

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryNativeGenreQueryTest {

    @Test
    fun `required ANY source row reaches the second genre of a single-select source`(): Unit = runTest {
        val fixture = fixture(FilterShape.SELECT) { query ->
            if (query.genres == listOf("Drama")) {
                MangasPage(listOf(manga("Drama-only pick", "/drama-only")), hasNextPage = false)
            } else {
                MangasPage(emptyList(), hasNextPage = false)
            }
        }
        val builder = DiscoverySourceRowBuilder(fixture.catalog, rowCap = 4)

        val items = builder.build(fixture.context(budget()))

        items.map { it.title } shouldBe listOf("Drama-only pick")
        items.single().sourceId shouldBe 7L
        items.single().sourceUrl shouldBe "/drama-only"
        items.single().genres shouldBe listOf("Drama")
    }

    @Test
    fun `required ANY source row unions alternatives instead of enabling an AND checkbox conjunction`(): Unit =
        runTest {
            val fixture = fixture(FilterShape.AND_CHECKBOXES) { query ->
                if (query.genres == listOf("Drama")) {
                    MangasPage(listOf(manga("Drama checkbox pick", "/drama-checkbox")), hasNextPage = false)
                } else {
                    MangasPage(emptyList(), hasNextPage = false)
                }
            }
            val builder = DiscoverySourceRowBuilder(fixture.catalog, rowCap = 4)

            val items = builder.build(fixture.context(budget()))

            items.map { it.title } shouldBe listOf("Drama checkbox pick")
            items.single().genres shouldBe listOf("Drama")
        }

    @Test
    fun `exhausted ANY head does not stop another genre continuation`(): Unit =
        runTest {
            val fixture = fixture(FilterShape.SELECT) { query ->
                when {
                    query.genres != listOf("Drama") -> MangasPage(emptyList(), hasNextPage = false)
                    query.page == 1 -> MangasPage(listOf(manga("Drama page one", "/drama-1")), hasNextPage = true)
                    query.page == 2 -> MangasPage(listOf(manga("Drama page two", "/drama-2")), hasNextPage = false)
                    else -> MangasPage(emptyList(), hasNextPage = false)
                }
            }
            val builder = DiscoverySourceRowBuilder(fixture.catalog, rowCap = 4)

            val items = builder.build(fixture.context(budget()))

            items.map { it.title } shouldBe listOf("Drama page one", "Drama page two")
            fixture.requests.filter { it.genres == listOf("Drama") }.map { it.page } shouldBe listOf(1, 2)
            fixture.requests.filter { it.genres == listOf("Fantasy") }.map { it.page } shouldBe listOf(1)
            builder.isDegraded shouldBe false
            builder.exhaustionEvidence!!.all {
                it.transportOk && it.rawEnd && !it.budgetStopped && it.pending == 0 && it.unattempted == 0
            } shouldBe true
        }

    @Test
    fun `budget-stopped ANY does not become verified empty from exhausted siblings`(): Unit =
        runTest {
            val fixture = fixture(FilterShape.SELECT) { query ->
                when {
                    query.genres != listOf("Drama") -> MangasPage(emptyList(), hasNextPage = false)
                    query.page == 1 -> MangasPage(
                        listOf(manga("Drama before timeout", "/drama-before-timeout")),
                        hasNextPage = true,
                    )
                    else -> awaitCancellation()
                }
            }
            val builder = DiscoverySourceRowBuilder(fixture.catalog, rowCap = 4)
            val budget = DiscoveryVerificationBudget(
                budgetMs = 100,
                maxDetails = 0,
                clock = { testScheduler.currentTime },
            )

            val items = builder.build(fixture.context(budget))

            items.map { it.title } shouldBe listOf("Drama before timeout")
            builder.isDegraded shouldBe true
            builder.exhaustionEvidence!!.all {
                it.transportOk && it.rawEnd && !it.budgetStopped && it.pending == 0 && it.unattempted == 0
            } shouldBe false
            (testScheduler.currentTime <= 100L) shouldBe true
        }

    private enum class FilterShape { SELECT, AND_CHECKBOXES }
    private data class Query(val genres: List<String>, val page: Int)

    private class Fixture(
        val catalog: AppDiscoverySourceCatalog,
        val requests: List<Query>,
        val sourcePolicy: DiscoverySourcePolicy,
    ) {
        fun context(budget: DiscoveryVerificationBudget) = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            libraryCleanTitles = emptySet(),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            sourceId = 7L,
            sourceIds = sourcePolicy.representativeSourceIds,
            sourcePolicy = sourcePolicy,
            genreFilterSpec = GenreFilterSpec(
                requiredGenres = linkedSetOf("Fantasy", "Drama"),
                requiredMatchMode = RequiredMatchMode.ANY,
                requiredEnforcement = RequiredEnforcement.STRICT,
            ),
            verificationBudget = budget,
        )
    }

    private fun fixture(shape: FilterShape, response: suspend (Query) -> MangasPage): Fixture {
        val sourcePolicy = DiscoverySourcePolicy(
            mediaType = DiscoveryMediaType.MANGA,
            revision = "native-fixture-7",
            inventoryComplete = true,
            allowedPluginKeys = setOf("fixture.extension"),
            pluginMembers = mapOf("fixture.extension" to setOf(7L)),
            representativeSourceIds = listOf(7L),
        )
        val source = mockk<CatalogueSource>()
        every { source.id } returns 7L
        every { source.name } returns "Native fixture"
        every { source.lang } returns "en"
        every { source.supportsLatest } returns false
        every { source.getFilterList() } answers {
            when (shape) {
                FilterShape.SELECT -> FilterList(
                    object : Filter.Select<String>("Genres", arrayOf("All", "Fantasy", "Drama")) {},
                )
                FilterShape.AND_CHECKBOXES -> FilterList(
                    object : Filter.Group<Filter.CheckBox>(
                        "Genres",
                        listOf(
                            object : Filter.CheckBox("Fantasy") {},
                            object : Filter.CheckBox("Drama") {},
                        ),
                    ) {},
                )
            }
        }
        val requests = mutableListOf<Query>()
        coEvery { source.getSearchManga(any(), "", any()) } coAnswers {
            val filters = thirdArg<FilterList>()
            val genres = when (shape) {
                FilterShape.SELECT -> filters.filterIsInstance<Filter.Select<*>>().mapNotNull { select ->
                    (select.values[select.state] as? String)?.takeUnless { it == "All" }
                }
                FilterShape.AND_CHECKBOXES -> filters.filterIsInstance<Filter.Group<*>>()
                    .flatMap { it.state }
                    .filterIsInstance<Filter.CheckBox>()
                    .filter { it.state }
                    .map { it.name }
            }
            val query = Query(genres, firstArg())
            requests += query
            response(query)
        }
        val manager = mockk<MangaSourceManager>()
        every { manager.getOrStub(7L) } returns source
        return Fixture(
            AppDiscoverySourceCatalog(
                mangaSourceManagerProvider = { manager },
                sourcePolicyProvider = { fixedDiscoveryQueryPolicyProvider(sourcePolicy, mapOf(7L to source)) },
            ),
            requests,
            sourcePolicy,
        )
    }

    private fun budget() = DiscoveryVerificationBudget(maxDetails = 0, clock = { 0L })

    private fun manga(title: String, url: String): SManga = SManga.create().apply {
        this.title = title
        this.url = url
        genre = "Drama"
        status = SManga.ONGOING
    }
}

internal fun discoveryQueryPolicyFixture(
    mediaType: DiscoveryMediaType,
    nativeIds: List<Long>,
    external: Map<DiscoveryRowType, Set<DiscoveryExternalProvider>> = emptyMap(),
): DiscoverySourcePolicy = DiscoverySourcePolicy(
    mediaType = mediaType,
    revision = "query-fixture-" + mediaType.key + "-" + nativeIds.joinToString(","),
    inventoryComplete = true,
    allowedPluginKeys = if (nativeIds.isEmpty()) emptySet() else setOf("fixture.extension"),
    pluginMembers = if (nativeIds.isEmpty()) emptyMap() else mapOf("fixture.extension" to nativeIds.toSet()),
    representativeSourceIds = nativeIds,
    allowedExternalProviders = external,
)

internal fun fixedDiscoveryQueryPolicyProvider(
    policy: DiscoverySourcePolicy,
    bindings: Map<Long, Any> = emptyMap(),
): DiscoverySourcePolicyProvider =
    object : DiscoverySourcePolicyProvider, DiscoverySdkBindingAuthority {
        override fun isCurrentSourceBinding(
            media: DiscoveryMediaType,
            sourceId: Long,
            source: Any,
            capturedRevision: String?,
        ): Boolean =
            media == policy.mediaType &&
                policy.allowsNative(sourceId) &&
                (capturedRevision == null || capturedRevision == policy.revision) &&
                bindings[sourceId] === source
        override fun current(
            mediaType: DiscoveryMediaType,
        ): DiscoverySourcePolicy = if (mediaType == policy.mediaType) {
            policy
        } else {
            DiscoverySourcePolicy(mediaType, "unavailable", false, emptySet(), emptyMap())
        }
        override fun observe(mediaType: DiscoveryMediaType) = flowOf(current(mediaType))
        override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = current(mediaType)
    }
