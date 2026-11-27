package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedReplayItemResult
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualitiesResult
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityResult
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityVariant
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnimeFeedContractV29Test {

    private val context = FeedReplayContext(accountId = "tg_42", continuityKey = "feed_global_unseen")
    private val sourceVariant = FeedVideoQualityVariant(
        id = "source",
        width = 1080,
        height = 1920,
        codec = "h264",
        sizeBytes = 42_000_000L,
        isSource = true,
    )
    private val altVariant = FeedVideoQualityVariant(
        id = "alt:7",
        width = 720,
        height = 1280,
        codec = "h265",
        sizeBytes = null,
        isSource = false,
    )

    private class LegacyFeedSource : AnimeFeedSource {
        override val id: Long = 1L
        override val name: String = "legacy"

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(videos = emptyList(), hasNextPage = false)
    }

    private inner class VideoQualitySource : AnimeFeedVideoQualitySource {
        override fun accountScopeId(): String = "tg_42"

        override val replayContextChanges: Flow<FeedReplayContext?> = flowOf(context)

        override suspend fun prepareReplay(): FeedReplayContext = context

        override suspend fun resolveReplayItem(
            itemId: String,
            expectedContext: FeedReplayContext,
            filters: AnimeFilterList,
        ): FeedReplayItemResult = FeedReplayItemResult.Unavailable

        override fun supportsVideoQuality(itemId: String): Boolean = itemId.startsWith("tdlib:")

        override suspend fun getVideoQualities(
            itemId: String,
            expectedAccountId: String,
        ): FeedVideoQualitiesResult = FeedVideoQualitiesResult.Available(
            itemId = itemId,
            accountId = expectedAccountId,
            variants = listOf(sourceVariant, altVariant),
        )

        override suspend fun resolveVideoQuality(
            itemId: String,
            variantId: String,
            expectedAccountId: String,
        ): FeedVideoQualityResult = FeedVideoQualityResult.Available(
            itemId = itemId,
            accountId = expectedAccountId,
            variant = if (variantId == sourceVariant.id) sourceVariant else altVariant,
            videoUrl = "http://127.0.0.1:1/stream/1.mp4",
            cacheIdentity = "unique-$variantId",
        )
    }

    @Test
    fun qualityCapabilityIsAdditiveAndInstanceofDetected() {
        val legacy: Any = LegacyFeedSource()
        assertTrue(legacy is AnimeFeedSource)
        assertFalse(legacy is AnimeFeedReplaySource)
        assertFalse(legacy is AnimeFeedVideoQualitySource)

        val quality: Any = VideoQualitySource()
        assertTrue(quality is AnimeFeedVideoQualitySource)
        assertTrue(quality is AnimeFeedReplaySource)
        assertTrue(quality is AnimeFeedAccountIdentitySource)
    }

    @Test
    fun supportsVideoQualityIsSynchronousWhileCatalogAndResolveAreSuspend() {
        val supports = AnimeFeedVideoQualitySource::class.java.methods
            .single { it.name == "supportsVideoQuality" }
        assertFalse(supports.parameterTypes.any { it.name == "kotlin.coroutines.Continuation" })

        listOf("getVideoQualities", "resolveVideoQuality").forEach { name ->
            val method = AnimeFeedVideoQualitySource::class.java.methods.single { it.name == name }
            assertTrue(
                method.parameterTypes.any { it.name == "kotlin.coroutines.Continuation" },
                "$name must stay suspend",
            )
        }

        val source: AnimeFeedVideoQualitySource = VideoQualitySource()
        assertTrue(source.supportsVideoQuality("tdlib:-100123/77"))
        assertFalse(source.supportsVideoQuality("web:channel/77"))
    }

    @Test
    fun catalogAndResolveResultsHaveTypedTerminalStates() {
        assertEquals(
            setOf("Available", "Retry", "NeedsAuth", "ContextChanged", "Unavailable", "Unsupported"),
            FeedVideoQualitiesResult::class.sealedSubclasses.mapNotNull { it.simpleName }.toSet(),
        )
        assertEquals(
            setOf("Available", "Retry", "NeedsAuth", "ContextChanged", "Unavailable", "Unsupported"),
            FeedVideoQualityResult::class.sealedSubclasses.mapNotNull { it.simpleName }.toSet(),
        )

        val available = FeedVideoQualitiesResult.Available(
            itemId = "tdlib:-100123/77",
            accountId = "tg_42",
            variants = listOf(sourceVariant, altVariant),
        )
        assertIs<FeedVideoQualitiesResult.Available>(available)
        assertEquals(listOf(sourceVariant, altVariant), available.variants)

        val retry = FeedVideoQualitiesResult.Retry(retryAtEpochMillis = 123L)
        assertEquals(123L, retry.retryAtEpochMillis)
        assertIs<FeedVideoQualitiesResult.NeedsAuth>(FeedVideoQualitiesResult.NeedsAuth)
        assertIs<FeedVideoQualitiesResult.ContextChanged>(FeedVideoQualitiesResult.ContextChanged)
        assertIs<FeedVideoQualitiesResult.Unavailable>(FeedVideoQualitiesResult.Unavailable)
        assertIs<FeedVideoQualitiesResult.Unsupported>(FeedVideoQualitiesResult.Unsupported)

        val resolved = FeedVideoQualityResult.Available(
            itemId = "tdlib:-100123/77",
            accountId = "tg_42",
            variant = altVariant,
            videoUrl = "http://127.0.0.1:1/stream/7.mp4",
            cacheIdentity = "unique-7",
        )
        assertIs<FeedVideoQualityResult.Available>(resolved)
        assertEquals(altVariant, resolved.variant)
    }

    @Test
    fun variantRoundTripsThroughJsonAndJavaSerialization() {
        val serializable: java.io.Serializable = altVariant
        assertIs<FeedVideoQualityVariant>(serializable)

        val json = Json.encodeToString(altVariant)
        assertEquals(altVariant, Json.decodeFromString<FeedVideoQualityVariant>(json))
        assertTrue(json.contains("\"id\":\"alt:7\""))
        assertTrue(json.contains("\"isSource\":false"))
    }

    @Test
    fun legacySourceKeepsItsNonQualityPath() {
        val legacy: Any = LegacyFeedSource()
        assertFalse(legacy is AnimeFeedVideoQualitySource)
        assertFalse(legacy is AnimeFeedReplaySource)
        assertFalse(legacy is AnimeFeedAccountIdentitySource)
        assertEquals(
            ShortVideoItem(
                id = "legacy:1",
                videoUrl = "https://cdn.example.invalid/1.mp4",
                posterUrl = "https://cdn.example.invalid/1.jpg",
            ).videoUrl,
            "https://cdn.example.invalid/1.mp4",
        )
    }
}
