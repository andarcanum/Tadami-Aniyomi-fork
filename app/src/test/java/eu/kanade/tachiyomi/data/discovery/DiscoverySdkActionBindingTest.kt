package eu.kanade.tachiyomi.data.discovery

import dev.mihon.injekt.patchInjekt
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.discovery.DiscoveryDirectOpenResolver
import eu.kanade.tachiyomi.ui.discovery.discoveryCoverData
import eu.kanade.tachiyomi.util.manga.MangaSourceUpdateLock
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.addSingleton
import java.util.concurrent.atomic.AtomicInteger

class DiscoverySdkActionBindingTest {
    private lateinit var previousInjekt: InjektScope
    private val clients = mutableListOf<OkHttpClient>()

    @Before fun isolate() {
        previousInjekt = Injekt
        patchInjekt()
    }

    @After fun close() {
        clients.forEach {
            it.dispatcher.executorService.shutdownNow()
            it.connectionPool.evictAll()
        }
        Injekt =
            previousInjekt
    }

    @Test
    fun `cover never acquires obsolete source credentials while the selected raw owner has another SDK object`() {
        val fixture = fixture(rawMatches = false)
        discoveryCoverData(item()) shouldBe null
        fixture.writes.get() shouldBe 0
    }

    @Test
    fun `add does not materialize through a stale registered binding authorized only by numeric id`() = runTest {
        val fixture = fixture(rawMatches = false)
        DiscoveryLibraryAdder().add(item()) shouldBe false
        fixture.writes.get() shouldBe 0
    }

    @Test
    fun `direct open does not materialize through a stale registered binding authorized only by numeric id`() =
        runTest {
            val fixture = fixture(rawMatches = false)
            DiscoveryDirectOpenResolver(
                NetworkToLocalManga(fixture.repository),
                mockk(),
                mockk(),
                fixture.provider,
            ).resolveEntryId(item()) shouldBe null
            fixture.writes.get() shouldBe 0
        }

    @Test
    fun `default deferred manga metadata guard rechecks exclusion after the shared URL lock`() = runTest {
        val fixture = fixture(rawMatches = true)
        val locked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = async {
            MangaSourceUpdateLock.withLock(7L, "/entry") {
                locked.complete(Unit)
                release.await()
            }
        }
        locked.await()
        val metadata = async { TasteSignalRecorder.fetchSourceGenres(DiscoveryMediaType.MANGA, 7L, "/entry") }
        runCurrent()
        fixture.sdkCalls.get() shouldBe 0
        fixture.selected.value = false
        release.complete(Unit)
        holder.await()
        runCatching { metadata.await() }
        fixture.sdkCalls.get() shouldBe 0
    }

    @Test
    fun `already cancelled default metadata caller never starts a matching SDK request`() = runTest {
        val fixture = fixture(rawMatches = true)
        val caller = async {
            kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]!!.cancel()
            runCatching { TasteSignalRecorder.fetchSourceGenres(DiscoveryMediaType.MANGA, 7L, "/entry") }
        }
        runCatching { caller.await() }
        fixture.sdkCalls.get() shouldBe 0
    }

    private fun fixture(rawMatches: Boolean): Fixture {
        val selected = MutableStateFlow(true)
        val source = mockk<HttpSource>()
        val client = OkHttpClient().also { clients += it }
        every { source.id } returns 7L
        every { source.headers } returns Headers.Builder().add("X-Source", "obsolete").build()
        every { source.client } returns client
        every { source.baseUrl } returns "https://obsolete.invalid"
        val sdkCalls = AtomicInteger()
        val writes = AtomicInteger()
        coEvery { source.getMangaUpdate(any(), any(), true, false) } answers {
            sdkCalls.incrementAndGet()
            SMangaUpdate(
                SManga.create().apply {
                    title = "Entry"
                    url = "/entry"
                    genre = "Action"
                },
                emptyList(),
            )
        }
        val manager = mockk<MangaSourceManager>()
        every { manager.get(7L) } returns source
        every { manager.getOrStub(7L) } returns source
        Injekt.addSingleton<MangaSourceManager>(manager)
        val provider = object : DiscoverySourcePolicyProvider, DiscoverySdkBindingAuthority {
            override fun current(
                mediaType: DiscoveryMediaType,
            ) = DiscoverySourcePolicy(
                mediaType,
                "raw-owner",
                true,
                if (selected.value) setOf("raw-owner") else emptySet(),
                mapOf("raw-owner" to setOf(7L)),
            )
            override fun observe(mediaType: DiscoveryMediaType) = flowOf(current(mediaType))
            override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = current(mediaType)
            override fun isCurrentSourceBinding(
                media: DiscoveryMediaType,
                sourceId: Long,
                sdk: Any,
                capturedRevision: String?,
            ) = rawMatches && sdk === source && current(media).allowsNative(sourceId)
        }
        Injekt.addSingleton<DiscoverySourcePolicyProvider>(provider)
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaByUrlAndSourceId(any(), any()) } returns null
        coEvery { repository.insertManga(any()) } answers {
            writes.incrementAndGet()
            101L
        }
        coEvery {
            repository.insertNetworkMangas(any(), any())
        } answers {
            writes.incrementAndGet()
            firstArg<List<Manga>>()
        }
        Injekt.addSingleton(NetworkToLocalManga(repository))
        return Fixture(provider, repository, selected, sdkCalls, writes)
    }
    private data class Fixture(
        val provider: DiscoverySourcePolicyProvider,
        val repository: MangaRepository,
        val selected: MutableStateFlow<Boolean>,
        val sdkCalls: AtomicInteger,
        val writes: AtomicInteger,
    )
    private fun item() =
        DiscoverySuggestion(
            1L,
            DiscoveryMediaType.MANGA,
            DiscoveryRowType.SOURCE,
            "Entry",
            "entry",
            "https://obsolete.invalid/entry.jpg",
            null,
            null,
            "Duplicate source",
            1.0,
            0L,
            1L,
            7L,
            "/entry",
            listOf("Action"),
        )
}
