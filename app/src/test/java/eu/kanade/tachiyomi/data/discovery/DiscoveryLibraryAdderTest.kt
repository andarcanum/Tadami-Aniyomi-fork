package eu.kanade.tachiyomi.data.discovery

import dev.mihon.injekt.patchInjekt
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.model.Source
import tachiyomi.domain.source.manga.repository.MangaSourceRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get
import java.util.concurrent.CopyOnWriteArrayList

class DiscoveryLibraryAdderTest {

    private lateinit var previousInjekt: InjektScope

    @Before
    fun isolateInjekt() {
        previousInjekt = Injekt
        patchInjekt()
    }

    @After
    fun restoreInjekt() {
        Injekt = previousInjekt
    }

    @Test
    fun `bound add uses selected source id and url when provider names collide`(): Unit = runBlocking {
        val nativeQueries = CopyOnWriteArrayList<Long>()
        val inserted = CopyOnWriteArrayList<Manga>()
        val autoFavoriteRequests = CopyOnWriteArrayList<Boolean>()
        val title = "Same source-name title"
        val selected = mockk<CatalogueSource>()
        val excluded = mockk<CatalogueSource>()
        every { selected.id } returns 7L
        every { excluded.id } returns 42L
        every { selected.getFilterList() } returns FilterList()
        every { excluded.getFilterList() } returns FilterList()
        coEvery { selected.getSearchManga(1, title, any()) } answers {
            nativeQueries += 7L
            MangasPage(
                listOf(
                    SManga.create().apply {
                        this.title = title
                        url = "/selected-entry"
                    },
                ),
                hasNextPage = false,
            )
        }
        coEvery { excluded.getSearchManga(1, title, any()) } answers {
            nativeQueries += 42L
            MangasPage(
                listOf(
                    SManga.create().apply {
                        this.title = title
                        url = "/excluded-entry"
                    },
                ),
                hasNextPage = false,
            )
        }
        val manager = mockk<MangaSourceManager>()
        every { manager.get(7L) } returns selected
        every { manager.get(42L) } returns excluded
        every { manager.getOrStub(7L) } returns selected
        every { manager.getOrStub(42L) } returns excluded
        val sourcePreferences = SourcePreferences(PersistingPreferenceStore())
        sourcePreferences.enabledLanguages().set(setOf("en"))
        val sourceRepository = mockk<MangaSourceRepository>()
        every { sourceRepository.getMangaSources() } returns flowOf(
            listOf(
                Source(42L, "en", "Duplicate provider name", supportsLatest = true, isStub = false),
                Source(7L, "en", "Duplicate provider name", supportsLatest = true, isStub = false),
            ),
        )
        val mangaRepository = mockk<MangaRepository>()
        coEvery { mangaRepository.insertNetworkMangas(any(), any()) } answers {
            val mangas = firstArg<List<Manga>>()
            val autoFavorite = secondArg<Boolean>()
            inserted.addAll(mangas)
            autoFavoriteRequests += autoFavorite
            mangas.mapIndexed { index, manga -> manga.copy(id = index + 1L, favorite = autoFavorite) }
        }
        val policy = DiscoverySourcePolicy(
            mediaType = DiscoveryMediaType.MANGA,
            revision = "selected-a",
            inventoryComplete = true,
            allowedPluginKeys = setOf("extension.a"),
            pluginMembers = mapOf("extension.a" to setOf(7L), "extension.b" to setOf(42L)),
        )
        val policyProvider = fixedDiscoveryQueryPolicyProvider(policy, mapOf(7L to selected))
        Injekt.addSingleton<DiscoverySourcePolicyProvider>(policyProvider)
        Injekt.addSingleton<MangaSourceManager>(manager)

        Injekt.addSingleton(GetEnabledMangaSources(sourceRepository, sourcePreferences))
        Injekt.addSingleton(NetworkToLocalManga(mangaRepository))
        val item = DiscoverySuggestion(
            id = 1L,
            mediaType = DiscoveryMediaType.MANGA,
            rowType = DiscoveryRowType.SOURCE,
            title = title,
            cleanTitle = "same source name title",
            coverUrl = "https://fixture.invalid/selected.jpg",
            reason = null,
            seedTitle = null,
            provider = "Duplicate provider name",
            score = 1.0,
            position = 0L,
            createdAt = 1L,
            sourceId = 7L,
            sourceUrl = "/selected-entry",
            genres = listOf("Fantasy"),
        )

        withTimeout(10_000L) {
            DiscoveryLibraryAdder().add(item) shouldBe true
        }
        inserted.map { it.source to it.url } shouldBe listOf(7L to "/selected-entry")
        autoFavoriteRequests.toList() shouldBe listOf(true)
        nativeQueries.toList() shouldBe emptyList()
    }

    @Test
    fun `excluded unbound and external recommendations never start native add side effects`(): Unit = runBlocking {
        val lookups = CopyOnWriteArrayList<Long>()
        val manager = mockk<MangaSourceManager>()
        every { manager.get(any()) } answers {
            lookups += firstArg<Long>()
            mockk<CatalogueSource>()
        }
        Injekt.addSingleton<MangaSourceManager>(manager)
        val policy = nativePolicy(DiscoveryMediaType.MANGA)
        val adder = DiscoveryLibraryAdder { fixedPolicyProvider(policy) }
        val external = item().copy(
            rowType = DiscoveryRowType.LIKE,
            provider = "anilist_trend",
            sourceId = null,
            sourceUrl = null,
        )
        policy.allows(external) shouldBe true
        val invalid = listOf(
            item().copy(sourceId = 42L, sourceUrl = "/excluded-entry"),
            item().copy(sourceId = null),
            item().copy(sourceId = 0L),
            item().copy(sourceId = -42L),
            item().copy(sourceUrl = null),
            item().copy(sourceUrl = "  "),
            external,
        )
        for (candidate in invalid) adder.add(candidate) shouldBe false
        adder.addFromProvider(DiscoveryMediaType.MANGA, "Fixture Title", "Duplicate provider name") shouldBe false
        lookups.toList() shouldBe emptyList()
    }

    @Test
    fun `source excluded during resolution is rechecked before materialization`(): Unit = runBlocking {
        val policies = MutableStateFlow(nativePolicy(DiscoveryMediaType.MANGA))
        val provider = object : DiscoverySourcePolicyProvider, DiscoverySdkBindingAuthority {
            override fun isCurrentSourceBinding(
                media: DiscoveryMediaType,
                sourceId: Long,
                source: Any,
                capturedRevision: String?,
            ) = current(media).allowsNative(sourceId) && Injekt.get<MangaSourceManager>().get(sourceId) === source
            override fun current(mediaType: DiscoveryMediaType) = policies.value
            override fun observe(mediaType: DiscoveryMediaType) = policies
            override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = policies.value
        }
        val source = mockk<CatalogueSource>()
        val manager = mockk<MangaSourceManager>()
        every { manager.get(7L) } answers {
            policies.value = nativePolicy(DiscoveryMediaType.MANGA, selected = emptySet())
            source
        }
        var materializations = 0
        val repository = mockk<MangaRepository>()
        coEvery { repository.insertNetworkMangas(any(), any()) } answers {
            materializations++
            firstArg<List<Manga>>()
        }
        Injekt.addSingleton<MangaSourceManager>(manager)
        Injekt.addSingleton(NetworkToLocalManga(repository))
        DiscoveryLibraryAdder { provider }.add(item()) shouldBe false
        materializations shouldBe 0
    }

    @Test
    fun `bound anime and novel additions retain their own media identity and favorite flag`(): Unit = runBlocking {
        val writes = CopyOnWriteArrayList<Triple<DiscoveryMediaType, Long, String>>()
        val favoriteFlags = CopyOnWriteArrayList<Boolean>()
        val animeManager = mockk<AnimeSourceManager>()
        val novelManager = mockk<NovelSourceManager>()
        every { animeManager.get(7L) } returns mockk<AnimeCatalogueSource>()
        every { novelManager.get(7L) } returns mockk<NovelCatalogueSource>()
        val animeRepository = mockk<AnimeRepository>()
        val novelRepository = mockk<NovelRepository>()
        coEvery { animeRepository.insertNetworkAnimes(any(), any()) } answers {
            val entries = firstArg<List<Anime>>()
            writes.addAll(entries.map { Triple(DiscoveryMediaType.ANIME, it.source, it.url) })
            favoriteFlags += secondArg<Boolean>()
            entries
        }
        coEvery { novelRepository.insertNetworkNovels(any(), any()) } answers {
            val entries = firstArg<List<Novel>>()
            writes.addAll(entries.map { Triple(DiscoveryMediaType.NOVEL, it.source, it.url) })
            favoriteFlags += secondArg<Boolean>()
            entries
        }
        Injekt.addSingleton<AnimeSourceManager>(animeManager)
        Injekt.addSingleton<NovelSourceManager>(novelManager)
        Injekt.addSingleton(NetworkToLocalAnime(animeRepository, animeManager))
        Injekt.addSingleton(NetworkToLocalNovel(novelRepository))
        val provider = object : DiscoverySourcePolicyProvider, DiscoverySdkBindingAuthority {
            override fun isCurrentSourceBinding(
                media: DiscoveryMediaType,
                sourceId: Long,
                source: Any,
                capturedRevision: String?,
            ): Boolean = current(media).allowsNative(sourceId) && when (media) {
                DiscoveryMediaType.ANIME -> animeManager.get(sourceId) === source
                DiscoveryMediaType.NOVEL -> novelManager.get(sourceId) === source
                DiscoveryMediaType.MANGA -> false
            }
            override fun current(mediaType: DiscoveryMediaType) = nativePolicy(mediaType)
            override fun observe(mediaType: DiscoveryMediaType) = flowOf(current(mediaType))
            override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = current(mediaType)
        }
        val adder = DiscoveryLibraryAdder { provider }
        adder.add(item(DiscoveryMediaType.ANIME).copy(sourceUrl = "/anime-entry")) shouldBe true
        adder.add(item(DiscoveryMediaType.NOVEL).copy(sourceUrl = "/novel-entry")) shouldBe true
        writes.toList() shouldBe listOf(
            Triple(DiscoveryMediaType.ANIME, 7L, "/anime-entry"),
            Triple(DiscoveryMediaType.NOVEL, 7L, "/novel-entry"),
        )
        favoriteFlags.toList() shouldBe listOf(true, true)
    }

    @Test
    fun `native materialization cancellation propagates to the caller`(): Unit = runBlocking {
        val manager = mockk<MangaSourceManager>()
        every { manager.get(7L) } returns mockk<CatalogueSource>()
        val repository = mockk<MangaRepository>()
        coEvery { repository.insertNetworkMangas(any(), any()) } throws CancellationException("fixture cancellation")
        Injekt.addSingleton<MangaSourceManager>(manager)
        Injekt.addSingleton(NetworkToLocalManga(repository))
        val adder = DiscoveryLibraryAdder { fixedPolicyProvider(nativePolicy(DiscoveryMediaType.MANGA)) }
        shouldThrow<CancellationException> { adder.add(item()) }
    }

    private fun nativePolicy(
        mediaType: DiscoveryMediaType,
        selected: Set<String> = setOf("extension.a"),
    ) = DiscoverySourcePolicy(
        mediaType = mediaType,
        revision = "fixture",
        inventoryComplete = true,
        allowedPluginKeys = selected,
        pluginMembers = mapOf("extension.a" to setOf(7L), "extension.b" to setOf(42L)),
        allowedExternalProviders = mapOf(DiscoveryRowType.LIKE to setOf(DiscoveryExternalProvider.ANILIST)),
    )

    private fun fixedPolicyProvider(
        policy: DiscoverySourcePolicy,
    ) = object : DiscoverySourcePolicyProvider, DiscoverySdkBindingAuthority {
        override fun isCurrentSourceBinding(
            media: DiscoveryMediaType,
            sourceId: Long,
            source: Any,
            capturedRevision: String?,
        ) =
            media == policy.mediaType && policy.allowsNative(sourceId) &&
                Injekt.get<MangaSourceManager>().get(sourceId) === source
        override fun current(mediaType: DiscoveryMediaType) = policy
        override fun observe(mediaType: DiscoveryMediaType) = flowOf(policy)
        override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = policy
    }

    private fun item(mediaType: DiscoveryMediaType = DiscoveryMediaType.MANGA) = DiscoverySuggestion(
        id = 1L, mediaType = mediaType, rowType = DiscoveryRowType.SOURCE,
        title = "Fixture Title", cleanTitle = "fixture title",
        coverUrl = "https://fixture.invalid/selected.jpg",
        reason = null,
        seedTitle = null,
        provider = "Duplicate provider name", score = 1.0, position = 0L, createdAt = 1L,
        sourceId = 7L, sourceUrl = "/selected-entry", genres = listOf("Fantasy"),
    )
}
