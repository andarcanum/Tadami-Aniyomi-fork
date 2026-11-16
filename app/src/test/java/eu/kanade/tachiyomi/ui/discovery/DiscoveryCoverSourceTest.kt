package eu.kanade.tachiyomi.ui.discovery

import dev.mihon.injekt.patchInjekt
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
import eu.kanade.tachiyomi.data.discovery.DiscoverySdkBindingAuthority
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.addSingleton
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class DiscoveryCoverSourceTest {

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
    fun `bound cover chooses selected source credentials when provider names collide`() {
        val fixture = fixture()
        try {
            val model = discoveryCoverData(item()) as AuroraPosterRequest
            model.headers?.get("X-Source") shouldBe "selected"
            model.refererUrl shouldBe "https://selected.invalid"
            model.client!!.newCall(
                Request.Builder().url(requireNotNull(model.primaryUrl)).build(),
            ).execute().use { response ->
                response.body.string() shouldBe "selected"
            }
            fixture.requests.toList() shouldBe listOf("selected")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `queued cover requests reject exclusion and replacement before native interceptors run`() {
        val fixture = fixture()
        try {
            val stale = discoveryCoverData(item()) as AuroraPosterRequest
            val request = Request.Builder().url(requireNotNull(stale.primaryUrl)).build()
            val staleClient = stale.client!!
            val beforeExclusion = staleClient.newCall(request)
            fixture.policies.value = policy(selected = emptySet())
            shouldThrow<IOException> { beforeExclusion.execute() }
            fixture.requests.toList() shouldBe emptyList()
            discoveryCoverData(item()) shouldBe null

            fixture.policies.value = policy()
            val beforeReplacement = staleClient.newCall(request)
            fixture.currentSource.set(fixture.replacement)
            shouldThrow<IOException> { beforeReplacement.execute() }
            fixture.requests.toList() shouldBe emptyList()

            val fresh = discoveryCoverData(item()) as AuroraPosterRequest
            fresh.headers?.get("X-Source") shouldBe "replacement"
            fresh.client!!.newCall(request).execute().use { response ->
                response.body.string() shouldBe "replacement"
            }
            fixture.requests.toList() shouldBe listOf("replacement")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `external and legacy covers do not acquire native credentials or lose recovery semantics`() {
        val fixture = fixture()
        try {
            val external = item().copy(
                rowType = DiscoveryRowType.LIKE,
                sourceId = null,
                sourceUrl = null,
                provider = "anilist_trend",
            )
            discoveryCoverData(external) shouldBe "https://fixture.invalid/page.jpg"
            val recovery = discoveryCoverData(external, "https://fixture.invalid/recovery.jpg") as AuroraPosterRequest
            recovery.primaryUrl shouldBe "https://fixture.invalid/page.jpg"
            recovery.fallbackUrl shouldBe "https://fixture.invalid/recovery.jpg"
            recovery.headers shouldBe null
            recovery.client shouldBe null
            discoveryCoverData(external.copy(coverUrl = null), "https://fixture.invalid/recovery.jpg") shouldBe
                "https://fixture.invalid/recovery.jpg"
            discoveryCoverData(item().copy(sourceId = null, sourceUrl = null)) shouldBe null
            discoveryCoverData(
                DiscoveryMediaType.MANGA,
                "Duplicate provider name",
                "https://fixture.invalid/page.jpg",
            ) shouldBe "https://fixture.invalid/page.jpg"
            fixture.lookups.toList() shouldBe emptyList()
            fixture.requests.toList() shouldBe emptyList()
        } finally {
            fixture.close()
        }
    }

    private class Fixture(
        val policies: MutableStateFlow<DiscoverySourcePolicy>,
        val currentSource: AtomicReference<MangaSource>,
        val replacement: HttpSource,
        val lookups: CopyOnWriteArrayList<Long>,
        val requests: CopyOnWriteArrayList<String>,
        private val clients: List<OkHttpClient>,
    ) {
        fun close() {
            clients.forEach { client ->
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    private fun fixture(): Fixture {
        val requests = CopyOnWriteArrayList<String>()
        val clients = mutableListOf<OkHttpClient>()
        fun source(id: Long, label: String): HttpSource {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                requests += label
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(label.toResponseBody("text/plain".toMediaType())).build()
            }.build()
            clients += client
            return mockk<HttpSource>().also { source ->
                every { source.id } returns id
                every { source.name } returns "Duplicate provider name"
                every { source.headers } returns Headers.Builder().add("X-Source", label).build()
                every { source.client } returns client
                every { source.baseUrl } returns "https://$label.invalid"
            }
        }
        val selected = source(7L, "selected")
        val excluded = source(42L, "excluded")
        val replacement = source(7L, "replacement")
        val currentSource = AtomicReference<MangaSource>(selected)
        val lookups = CopyOnWriteArrayList<Long>()
        val manager = mockk<MangaSourceManager>()
        every { manager.get(7L) } answers {
            lookups += 7L
            currentSource.get()
        }
        every { manager.get(42L) } answers {
            lookups += 42L
            excluded
        }
        every { manager.getOnlineSources() } answers { listOfNotNull(excluded, currentSource.get() as? HttpSource) }
        val policies = MutableStateFlow(policy())
        val provider = object : DiscoverySourcePolicyProvider, DiscoverySdkBindingAuthority {
            override fun isCurrentSourceBinding(
                media: DiscoveryMediaType,
                sourceId: Long,
                source: Any,
                capturedRevision: String?,
            ) =
                media == DiscoveryMediaType.MANGA &&
                    policies.value.allowsNative(sourceId) &&
                    currentSource.get() === source
            override fun current(mediaType: DiscoveryMediaType) = policies.value
            override fun observe(mediaType: DiscoveryMediaType) = policies
            override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = policies.value
        }
        Injekt.addSingleton<MangaSourceManager>(manager)
        Injekt.addSingleton<DiscoverySourcePolicyProvider>(provider)
        return Fixture(policies, currentSource, replacement, lookups, requests, clients)
    }

    @Test
    fun `repeated cover resolutions for unchanged source reuse guarded OkHttpClient`() {
        val fixture = fixture()
        try {
            val first = discoveryCoverData(item()) as AuroraPosterRequest
            val second = discoveryCoverData(item().copy(id = 2L, title = "Second")) as AuroraPosterRequest
            val request = Request.Builder().url(requireNotNull(first.primaryUrl)).build()
            first.client!!.newCall(request).execute().close()
            second.client!!.newCall(request).execute().close()
            fixture.requests.toList() shouldBe listOf("selected", "selected")
            (first.client === second.client) shouldBe true
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `guarded client cache does not prevent replaced source GC`() {
        val fixture = fixture()
        try {
            val network = mockk<NetworkHelper>()
            every { network.client } returns fixture.replacement.client
            Injekt.addSingleton<NetworkHelper>(network)

            val weakSource = cacheCoverClientForTemporarySource(fixture)
            fixture.currentSource.set(fixture.replacement)
            var collected = weakSource.get() == null
            var attempts = 0
            while (!collected && attempts < 25) {
                System.gc()
                Thread.sleep(20)
                collected = weakSource.get() == null
                attempts++
            }
            (discoveryCoverData(item()) != null) shouldBe true
            collected shouldBe true
        } finally {
            fixture.close()
        }
    }

    private fun cacheCoverClientForTemporarySource(fixture: Fixture): java.lang.ref.WeakReference<Any> {
        val temporary = object : MangaSource {
            override val id = 7L
            override val name = "Duplicate provider name"
        }
        fixture.currentSource.set(temporary)
        val model = discoveryCoverData(item()) as AuroraPosterRequest
        val request = Request.Builder().url(requireNotNull(model.primaryUrl)).build()
        model.client!!.newCall(request).execute().close()
        return java.lang.ref.WeakReference(temporary)
    }

    private fun policy(selected: Set<String> = setOf("extension.a")) = DiscoverySourcePolicy(
        mediaType = DiscoveryMediaType.MANGA,
        revision = "fixture",
        inventoryComplete = true,
        allowedPluginKeys = selected,
        pluginMembers = mapOf("extension.a" to setOf(7L), "extension.b" to setOf(42L)),
        allowedExternalProviders = mapOf(DiscoveryRowType.LIKE to setOf(DiscoveryExternalProvider.ANILIST)),
    )

    private fun item() = DiscoverySuggestion(
        id = 1L, mediaType = DiscoveryMediaType.MANGA, rowType = DiscoveryRowType.SOURCE,
        title = "Fixture Title", cleanTitle = "fixture title",
        coverUrl = "https://fixture.invalid/page.jpg", reason = null, seedTitle = null,
        provider = "Duplicate provider name", score = 1.0, position = 0L, createdAt = 1L,
        sourceId = 7L, sourceUrl = "/title", genres = listOf("Fantasy"),
    )
}
