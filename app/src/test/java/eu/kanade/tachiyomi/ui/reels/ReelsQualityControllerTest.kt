package eu.kanade.tachiyomi.ui.reels

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeFeedVideoQualitySource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedReplayItemResult
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualitiesResult
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityResult
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityVariant
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.reels.anime.model.ReelsQualityPreference

class ReelsQualityControllerTest {

    private val sourceVariant = variant("source", 1080, 1920, "h264", isSource = true)
    private val hd720 = variant("alt:7", 720, 1280, "h264")
    private val fullHd = variant("alt:8", 1080, 1920, "h264")
    private val sd480 = variant("alt:9", 480, 854, "h264")
    private val item = ShortVideoItem(
        id = "tdlib:-100123/100",
        videoUrl = "http://127.0.0.1:1/stream/source.mp4",
        posterUrl = "poster",
    )
    private val owner = ReelsQualityOwner("owner-token", 1L, "42")

    @Test
    fun `catalog is staged then resolved into an actual binding`(): Unit = runBlocking {
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            resolve = { id, _ -> resolved(id, hd720) },
        )
        val controller = controller()
        controller.attach(source, owner)
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))

        val actual = controller.prepare(owner, item, forceSd = false, recheckOnActivation = false)

        actual shouldNotBe null
        actual!!.decision.variant shouldBe hd720
        actual.videoUrl shouldBe "url:${hd720.id}"
        actual.cacheIdentity shouldBe "identity:${hd720.id}"
        actual.cacheKey.startsWith("q29:") shouldBe true
        controller.playback(item.id) shouldBe actual
        val state = controller.itemState(item.id)!!
        state.catalogStatus shouldBe ReelsQualityCatalogStatus.READY
        state.actual shouldBe actual
        state.failure shouldBe null
    }

    @Test
    fun `missing exact resolution falls back to source`(): Unit = runBlocking {
        val source = FakeQualitySource(
            catalog = available(sourceVariant, sd480),
            resolve = { id, variantId -> resolved(id, sourceVariant, variantId) },
        )
        val controller = controller()
        controller.attach(source, owner)
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))

        val actual = controller.prepare(owner, item, forceSd = false, recheckOnActivation = false)!!

        actual.decision.variant shouldBe sourceVariant
        actual.decision.fallbackToSource shouldBe true
    }

    @Test
    fun `manual override outranks data saver and may select a higher resolution`(): Unit = runBlocking {
        val source = FakeQualitySource(
            catalog = available(sourceVariant, sd480, fullHd),
            resolve = { id, variantId -> resolved(id, fullHd, variantId) },
        )
        val controller = controller()
        controller.attach(source, owner)
        controller.setOverride(owner, item.id, ReelsQualityPreference.Resolution(1080))

        val actual = controller.prepare(owner, item, forceSd = true, recheckOnActivation = false)!!

        actual.decision.requested shouldBe ReelsQualityPreference.Resolution(1080)
        actual.decision.manual shouldBe true
        actual.decision.variant shouldBe fullHd
    }

    @Test
    fun `global preference is persisted and starts in source`(): Unit = runBlocking {
        val store = MapPreferenceStore()
        val preferences = SourcePreferences(store)
        preferences.reelsVideoQuality(1L).set("720")
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            resolve = { id, variantId -> resolved(id, hd720, variantId) },
        )
        val controller = ReelsQualityController(preferences) { true }
        controller.attach(source, owner)
        controller.state.value.global shouldBe ReelsQualityPreference.Resolution(720)

        controller.setGlobal(owner, ReelsQualityPreference.Resolution(1080))

        preferences.reelsVideoQuality(1L).get() shouldBe "1080"
        controller.state.value.global shouldBe ReelsQualityPreference.Resolution(1080)
    }

    @Test
    fun `catalog failures stay typed and never become a source-only success`(): Unit = runBlocking {
        val failures = listOf(
            FeedVideoQualitiesResult.Retry(123L) to ReelsQualityFailure.Retry(123L),
            FeedVideoQualitiesResult.NeedsAuth to ReelsQualityFailure.NeedsAuth,
            FeedVideoQualitiesResult.ContextChanged to ReelsQualityFailure.ContextChanged,
            FeedVideoQualitiesResult.Unavailable to ReelsQualityFailure.Unavailable,
            FeedVideoQualitiesResult.Unsupported to ReelsQualityFailure.Unsupported,
        )
        failures.forEach { (catalogResult, expected) ->
            val controller = controller()
            controller.attach(FakeQualitySource(catalog = catalogResult), owner)

            val actual = controller.prepare(owner, item, forceSd = false, recheckOnActivation = false)

            actual shouldBe null
            val state = controller.itemState(item.id)!!
            state.actual shouldBe null
            state.failure shouldBe expected
            state.switching shouldBe false
        }
    }

    @Test
    fun `resolve failure keeps catalog options and reports typed failure`(): Unit = runBlocking {
        val controller = controller()
        controller.attach(
            FakeQualitySource(
                catalog = available(sourceVariant, hd720),
                resolve = { _, _ -> FeedVideoQualityResult.Retry(999L) },
            ),
            owner,
        )

        controller.prepare(owner, item, forceSd = false, recheckOnActivation = false) shouldBe null

        val state = controller.itemState(item.id)!!
        state.options.size shouldBe 2
        state.actual shouldBe null
        state.failure shouldBe ReelsQualityFailure.Retry(999L)
        state.catalogStatus shouldBe ReelsQualityCatalogStatus.ERROR
    }

    @Test
    fun `owner mismatch cannot publish or mutate state`(): Unit = runBlocking {
        val controller = controller()
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            resolve = { id, variantId -> resolved(id, hd720, variantId) },
        )
        controller.attach(source, owner)

        controller.prepare(ReelsQualityOwner("other", 1L, "42"), item, false, false) shouldBe null
        controller.setGlobal(ReelsQualityOwner("other", 1L, "42"), ReelsQualityPreference.Resolution(720))
        controller.playback(item.id) shouldBe null
        controller.state.value.global shouldBe ReelsQualityPreference.Source
    }

    @Test
    fun `cache key is stable per identity and isolated by owner`() {
        val first = ReelsQualityController.cacheKey(owner, item.id, "identity-a")
        val again = ReelsQualityController.cacheKey(owner, item.id, "identity-a")
        val otherIdentity = ReelsQualityController.cacheKey(owner, item.id, "identity-b")
        val otherOwner = ReelsQualityController.cacheKey(owner.copy(token = "other"), item.id, "identity-a")

        first shouldBe again
        first shouldNotBe otherIdentity
        first shouldBe otherOwner
        first.startsWith("q29:") shouldBe true
    }

    @Test
    fun `captureDownload uses the actual binding and freezes headers`(): Unit = runBlocking {
        val controller = controller()
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            resolve = { id, variantId -> resolved(id, hd720, variantId) },
        )
        controller.attach(source, owner)
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))
        val actual = controller.prepare(owner, item, false, false)!!
        val headers = mapOf("User-Agent" to "test")

        val snapshot = controller.captureDownload(owner, item, headers)!!

        snapshot.item.videoUrl shouldBe actual.videoUrl
        snapshot.variant shouldBe actual.decision.variant
        snapshot.cacheIdentity shouldBe actual.cacheIdentity
        snapshot.headers shouldBe headers
        (snapshot.headers === headers) shouldBe false
    }

    @Test
    fun `rehydrateDownload rejects changed byte identity`(): Unit = runBlocking {
        val controller = controller()
        controller.attach(
            FakeQualitySource(
                catalog = available(sourceVariant, hd720),
                resolve = { id, variantId -> resolved(id, hd720, variantId) },
            ),
            owner,
        )
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))
        val actual = controller.prepare(owner, item, false, false)!!
        val snapshot = controller.captureDownload(owner, item, emptyMap())!!

        controller.rehydrateDownload(snapshot, 5L)!!.cacheIdentity shouldBe actual.cacheIdentity
        controller.rehydrateDownload(snapshot.copy(cacheIdentity = "stale"), 5L) shouldBe null
    }

    @Test
    fun `playback failure is revision fenced`(): Unit = runBlocking {
        val controller = controller()
        controller.attach(
            FakeQualitySource(
                catalog = available(sourceVariant, hd720),
                resolve = { id, variantId -> resolved(id, hd720, variantId) },
            ),
            owner,
        )
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))
        val actual = controller.prepare(owner, item, false, false)!!

        controller.onPlaybackFailure(owner, item.id, actual.revision + 1)
        controller.itemState(item.id)!!.failure shouldBe null

        controller.onPlaybackFailure(owner, item.id, actual.revision)
        controller.itemState(item.id)!!.failure shouldBe ReelsQualityFailure.Retry(null)
    }

    @Test
    fun `detach clears owner and overrides`(): Unit = runBlocking {
        val controller = controller()
        controller.attach(
            FakeQualitySource(
                catalog = available(sourceVariant, hd720),
                resolve = { id, variantId -> resolved(id, hd720, variantId) },
            ),
            owner,
        )
        controller.prepare(owner, item, false, false)
        controller.detach()

        controller.state.value.owner shouldBe null
        controller.playback(item.id) shouldBe null
        controller.supports(item.id) shouldBe false
    }

    @Test
    fun `prepare window covers the active page and four forward items in order`(): Unit = runBlocking {
        val resolveOrder = mutableListOf<String>()
        val items = (0..6).map { index -> item.copy(id = "tdlib:-100123/$index") }
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            catalogByItem = { id -> availableFor(id, sourceVariant, hd720) },
            resolve = { id, variantId ->
                resolveOrder += id
                resolved(id, hd720, variantId)
            },
        )
        val controller = controller()
        controller.attach(source, owner)
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))

        controller.prepareWindow(owner, items, fromIndex = 0, toIndex = 4, forceSd = false)

        resolveOrder shouldBe items.take(5).map { it.id }
        items.take(5).forEach { controller.playback(it.id) shouldNotBe null }
        controller.playback(items[5].id) shouldBe null
        controller.playback(items[6].id) shouldBe null
    }

    @Test
    fun `prepare window stops at the end of the loaded items`(): Unit = runBlocking {
        val resolveOrder = mutableListOf<String>()
        val items = (0..2).map { index -> item.copy(id = "tdlib:-100123/$index") }
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            catalogByItem = { id -> availableFor(id, sourceVariant, hd720) },
            resolve = { id, variantId ->
                resolveOrder += id
                resolved(id, hd720, variantId)
            },
        )
        val controller = controller()
        controller.attach(source, owner)
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))

        controller.prepareWindow(owner, items, fromIndex = 0, toIndex = 4, forceSd = false)

        resolveOrder shouldBe items.map { it.id }
    }

    @Test
    fun `prepare window reuses already resolved bindings without another resolve`(): Unit = runBlocking {
        val resolveOrder = mutableListOf<String>()
        val items = (0..4).map { index -> item.copy(id = "tdlib:-100123/$index") }
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            catalogByItem = { id -> availableFor(id, sourceVariant, hd720) },
            resolve = { id, variantId ->
                resolveOrder += id
                resolved(id, hd720, variantId)
            },
        )
        val controller = controller()
        controller.attach(source, owner)
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))
        controller.prepare(owner, items[0], forceSd = false, recheckOnActivation = false) shouldNotBe null
        resolveOrder.clear()

        controller.prepareWindow(owner, items, fromIndex = 0, toIndex = 4, forceSd = false)

        resolveOrder shouldBe items.drop(1).map { it.id }
    }

    @Test
    fun `prepare window skips items without the quality capability`(): Unit = runBlocking {
        val resolveOrder = mutableListOf<String>()
        val items = listOf(
            item.copy(id = "tdlib:-100123/0"),
            item.copy(id = "web:1"),
            item.copy(id = "tdlib:-100123/2"),
        )
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            catalogByItem = { id -> availableFor(id, sourceVariant, hd720) },
            resolve = { id, variantId ->
                resolveOrder += id
                resolved(id, hd720, variantId)
            },
        )
        val controller = controller()
        controller.attach(source, owner)
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))

        controller.prepareWindow(owner, items, fromIndex = 0, toIndex = 4, forceSd = false)

        resolveOrder shouldBe listOf("tdlib:-100123/0", "tdlib:-100123/2")
    }

    @Test
    fun `prepare window refuses a foreign owner`(): Unit = runBlocking {
        val resolveOrder = mutableListOf<String>()
        val items = (0..4).map { index -> item.copy(id = "tdlib:-100123/$index") }
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            catalogByItem = { id -> availableFor(id, sourceVariant, hd720) },
            resolve = { id, variantId ->
                resolveOrder += id
                resolved(id, hd720, variantId)
            },
        )
        val controller = controller()
        controller.attach(source, owner)

        controller.prepareWindow(
            expectedOwner = owner.copy(token = "other"),
            items = items,
            fromIndex = 0,
            toIndex = 4,
            forceSd = false,
        )

        resolveOrder.shouldBeEmpty()
    }

    @Test
    fun `prepare window applies the automatic SD request to every prepared item`(): Unit = runBlocking {
        val items = (0..2).map { index -> item.copy(id = "tdlib:-100123/$index") }
        val source = FakeQualitySource(
            catalog = available(sourceVariant, sd480),
            catalogByItem = { id -> availableFor(id, sourceVariant, sd480) },
            resolve = { id, variantId -> resolved(id, sd480, variantId) },
        )
        val controller = controller()
        controller.attach(source, owner)

        controller.prepareWindow(owner, items, fromIndex = 0, toIndex = 2, forceSd = true)

        items.forEach { prepared ->
            controller.playback(prepared.id)!!.decision.requested shouldBe
                ReelsQualityPreference.Resolution(480)
        }
    }

    @Test
    fun `changing the global preference re resolves an already bound item`(): Unit = runBlocking {
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            resolve = { id, variantId ->
                resolved(id, if (variantId == hd720.id) hd720 else sourceVariant)
            },
        )
        val controller = controller()
        controller.attach(source, owner)

        val first = controller.prepare(owner, item, false, false)!!
        first.decision.variant shouldBe sourceVariant

        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))
        val actual = controller.prepare(owner, item, false, false)

        actual shouldNotBe null
        actual!!.decision.variant shouldBe hd720
    }

    @Test
    fun `cancelled prepare is retried and clears switching`(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            resolve = { id, _ ->
                started.complete(Unit)
                gate.await()
                resolved(id, hd720)
            },
        )
        val controller = controller()
        controller.attach(source, owner)
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))

        val job = launch { controller.prepare(owner, item, false, false) }
        started.await()
        controller.itemState(item.id)!!.switching shouldBe true
        job.cancelAndJoin()
        controller.itemState(item.id)!!.switching shouldBe false
        controller.itemState(item.id)!!.actual shouldBe null

        gate.complete(Unit)
        val actual = controller.prepare(owner, item, false, false)

        actual shouldNotBe null
        controller.itemState(item.id)!!.switching shouldBe false
        controller.itemState(item.id)!!.actual shouldBe actual
    }

    @Test
    fun `blank account id sets NeedsAuth failure state instead of dropping update`(): Unit = runBlocking {
        val source = FakeQualitySource(catalog = available(sourceVariant, hd720))
        val blankAccountOwner = ReelsQualityOwner("owner-blank", 1L, "")
        val controller = controller()
        controller.attach(source, blankAccountOwner)

        val actual = controller.prepare(blankAccountOwner, item, forceSd = false, recheckOnActivation = false)

        actual shouldBe null
        val itemState = controller.itemState(item.id)!!
        itemState.catalogStatus shouldBe ReelsQualityCatalogStatus.ERROR
        itemState.failure shouldBe ReelsQualityFailure.NeedsAuth
        itemState.switching shouldBe false
    }

    @Test
    fun `recheckOnActivation bypasses cached binding and resolves fresh url`(): Unit = runBlocking {
        var generation = 0
        val source = FakeQualitySource(
            catalog = available(sourceVariant, hd720),
            resolve = { id, variantId ->
                generation++
                FeedVideoQualityResult.Available(
                    itemId = id,
                    accountId = "42",
                    variant = if (variantId == hd720.id) hd720 else sourceVariant,
                    videoUrl = "https://cdn.example/v$generation/$variantId.mp4",
                    cacheIdentity = "id:$variantId",
                )
            },
        )
        val controller = controller()
        controller.attach(source, owner)
        controller.setGlobal(owner, ReelsQualityPreference.Resolution(720))

        val first = controller.prepare(owner, item, forceSd = false, recheckOnActivation = false)!!
        val cached = controller.prepare(owner, item, forceSd = false, recheckOnActivation = false)!!
        val refreshed = controller.prepare(owner, item, forceSd = false, recheckOnActivation = true)!!

        first.videoUrl shouldBe cached.videoUrl
        refreshed.videoUrl shouldNotBe first.videoUrl
    }

    private fun controller() = ReelsQualityController(SourcePreferences(MapPreferenceStore())) { true }

    private fun available(vararg variants: FeedVideoQualityVariant) =
        FeedVideoQualitiesResult.Available(item.id, "42", variants.toList())

    private fun availableFor(itemId: String, vararg variants: FeedVideoQualityVariant) =
        FeedVideoQualitiesResult.Available(itemId, "42", variants.toList())

    private fun resolved(
        itemId: String,
        variant: FeedVideoQualityVariant,
        requestedId: String = variant.id,
    ) = FeedVideoQualityResult.Available(
        itemId = itemId,
        accountId = "42",
        variant = variant,
        videoUrl = "url:${variant.id}",
        cacheIdentity = "identity:${variant.id}",
    )

    private fun variant(
        id: String,
        width: Int,
        height: Int,
        codec: String?,
        isSource: Boolean = false,
    ) = FeedVideoQualityVariant(
        id = id,
        width = width,
        height = height,
        codec = codec,
        sizeBytes = 1_000L,
        isSource = isSource,
    )

    private class FakeQualitySource(
        private val catalog: FeedVideoQualitiesResult,
        private val resolve: suspend (itemId: String, variantId: String) -> FeedVideoQualityResult = { _, _ ->
            FeedVideoQualityResult.Unavailable
        },
        private val catalogByItem: ((String) -> FeedVideoQualitiesResult)? = null,
    ) : AnimeFeedVideoQualitySource {
        override fun accountScopeId(): String = "42"
        override val replayContextChanges: Flow<FeedReplayContext?> =
            flowOf(FeedReplayContext("42", "tg_v29"))
        override suspend fun prepareReplay(): FeedReplayContext = FeedReplayContext("42", "tg_v29")
        override suspend fun resolveReplayItem(
            itemId: String,
            expectedContext: FeedReplayContext,
            filters: AnimeFilterList,
        ): FeedReplayItemResult = FeedReplayItemResult.Unavailable
        override fun supportsVideoQuality(itemId: String): Boolean = itemId.startsWith("tdlib:")
        override suspend fun getVideoQualities(
            itemId: String,
            expectedAccountId: String,
        ): FeedVideoQualitiesResult = catalogByItem?.invoke(itemId) ?: catalog
        override suspend fun resolveVideoQuality(
            itemId: String,
            variantId: String,
            expectedAccountId: String,
        ): FeedVideoQualityResult = resolve(itemId, variantId)
    }

    private class MapPreferenceStore : PreferenceStore {
        private val map = mutableMapOf<String, Any>()

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = pref(key, defaultValue)
        override fun getInt(key: String, defaultValue: Int): Preference<Int> = pref(key, defaultValue)
        override fun getLong(key: String, defaultValue: Long): Preference<Long> = pref(key, defaultValue)
        override fun getFloat(key: String, defaultValue: Float): Preference<Float> = pref(key, defaultValue)
        override fun getString(key: String, defaultValue: String): Preference<String> = pref(key, defaultValue)
        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            pref(key, defaultValue)
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = pref(key, defaultValue)

        override fun getAll(): Map<String, *> = map

        @Suppress("UNCHECKED_CAST")
        private fun <T> pref(key: String, defaultValue: T): Preference<T> = object : Preference<T> {
            override fun key(): String = key
            override fun get(): T = (map[key] as? T) ?: defaultValue
            override fun set(value: T) {
                if (value == null) map.remove(key) else map[key] = value as Any
            }
            override fun isSet(): Boolean = map.containsKey(key)
            override fun delete() {
                map.remove(key)
            }
            override fun defaultValue(): T = defaultValue
            override fun changes(): Flow<T> = MutableStateFlow(get())
            override fun stateIn(scope: kotlinx.coroutines.CoroutineScope): kotlinx.coroutines.flow.StateFlow<T> =
                MutableStateFlow(get()).asStateFlow()
        }
    }
}
