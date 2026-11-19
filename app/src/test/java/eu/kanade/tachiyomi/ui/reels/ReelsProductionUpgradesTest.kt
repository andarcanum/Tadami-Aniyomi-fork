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
import eu.kanade.tachiyomi.ui.reels.controllers.ReelsPlaybackSettingsController
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ReelsProductionUpgradesTest {

    private fun item(
        id: String,
        author: String? = "authorA",
        tags: List<String> = listOf("tag1"),
    ): ShortVideoItem = ShortVideoItem(
        id = id,
        title = "Title $id",
        author = author,
        videoUrl = "https://cdn.example/$id.mp4",
        posterUrl = "https://cdn.example/$id.jpg",
        tags = tags,
    )

    @Test
    fun `preloaded page reporting running before onPageChanged preserves isActuallyPlaying`() {
        val prefs = mockk<SourcePreferences>(relaxed = true)
        val state = MutableStateFlow(ReelsFeedScreenModel.State(currentSourceId = 1L))
        val controller = ReelsPlaybackSettingsController(
            scope = TestScope(),
            sourcePreferences = prefs,
            sessionSound = ReelsSessionSoundState(),
            mutableState = state,
        )

        // Preloaded page 1 becomes active in UI and synchronously reports running = true
        // BEFORE LaunchedEffect(pagerState) invokes onPageChanged(1).
        controller.setPlaybackRunning(running = true, pageIndex = 1)
        state.value.isActuallyPlaying shouldBe true

        // Now onPageChanged(1) runs in the same frame: must NOT reset isActuallyPlaying to false!
        val transition = controller.resolvePageTransitionState(
            current = state.value,
            targetIndex = 1,
            cause = ReelsTransitionCause.USER_FORWARD,
        )
        transition.isActuallyPlaying shouldBe true
        state.value = state.value.copy(
            activeIndex = 1,
            isPlaying = transition.isPlaying,
            userPaused = transition.userPaused,
            isActuallyPlaying = transition.isActuallyPlaying,
        )

        // Late deactivation callback from outgoing page 0 must NOT clobber active page 1!
        controller.setPlaybackRunning(running = false, pageIndex = 0)
        state.value.isActuallyPlaying shouldBe true

        // Deactivation from active page 1 DOES clear isActuallyPlaying.
        controller.setPlaybackRunning(running = false, pageIndex = 1)
        state.value.isActuallyPlaying shouldBe false
    }

    @Test
    fun `manual user swipe clears sticky pause while programmatic page change preserves it`() {
        val prefs = mockk<SourcePreferences>(relaxed = true)
        val state = MutableStateFlow(
            ReelsFeedScreenModel.State(currentSourceId = 1L, isPlaying = false, userPaused = true),
        )
        val controller = ReelsPlaybackSettingsController(
            scope = TestScope(),
            sourcePreferences = prefs,
            sessionSound = ReelsSessionSoundState(),
            mutableState = state,
        )

        // Programmatic page change preserves deliberate pause.
        val prog = controller.resolvePageTransitionState(
            current = state.value,
            targetIndex = 1,
            cause = ReelsTransitionCause.PROGRAMMATIC,
        )
        prog.isPlaying shouldBe false
        prog.userPaused shouldBe true

        // Manual swipe auto-resumes playback (TikTok / Reels / Shorts standard).
        val manual = controller.resolvePageTransitionState(
            current = state.value,
            targetIndex = 2,
            cause = ReelsTransitionCause.USER_FORWARD,
        )
        manual.isPlaying shouldBe true
        manual.userPaused shouldBe false
    }

    @Test
    fun `hardware volume up unmutes muted feed and marks session sound decided`() {
        val prefs = mockk<SourcePreferences>(relaxed = true)
        val sound = ReelsSessionSoundState()
        val state = MutableStateFlow(
            ReelsFeedScreenModel.State(currentSourceId = 1L, isMuted = true, showUnmuteHint = true),
        )
        val controller = ReelsPlaybackSettingsController(
            scope = TestScope(),
            sourcePreferences = prefs,
            sessionSound = sound,
            mutableState = state,
        )

        controller.unmuteIfMuted()
        state.value.isMuted shouldBe false
        state.value.showUnmuteHint shouldBe false
        sound.decided shouldBe true
    }

    @Test
    fun `buildUniqueReelsPageKeys deduplicates colliding item ids for VerticalPager`() {
        val state = ReelsFeedScreenModel.State(
            currentSourceId = 10L,
            items = listOf(item("clip-1"), item("clip-2"), item("clip-1"), item("clip-1")).toImmutableList(),
        )

        val keys = buildUniqueReelsPageKeys(state)
        keys shouldContainExactly listOf("clip-1", "clip-2", "clip-1#dup1", "clip-1#dup2")
    }

    @Test
    fun `ReelsTasteEngine preserves cold-start order then ranks by positive and negative signals with diversity`() {
        val engine = ReelsTasteEngine()
        val batch = listOf(
            item("1", author = "boring", tags = listOf("news")),
            item("2", author = "fav", tags = listOf("anime")),
            item("3", author = "fav", tags = listOf("anime")),
            item("4", author = "fav", tags = listOf("anime")),
            item("5", author = "other", tags = listOf("music")),
        )

        // Cold start: untouched order
        engine.rerankItems(batch).map { it.id } shouldContainExactly listOf("1", "2", "3", "4", "5")

        // Record strong positive signals for "fav" / "anime" and negative for "boring"
        engine.recordFollow("fav")
        engine.recordLike(item("x", author = "fav", tags = listOf("anime")))
        engine.recordFastSkip(item("y", author = "boring", tags = listOf("news")))

        engine.score(batch[1]) shouldBeGreaterThan 0.0
        engine.score(batch[0]) shouldBeLessThan 0.0

        val ranked = engine.rerankItems(batch)
        // First two slots go to "fav", but the 3rd consecutive "fav" is broken up by MMR diversity!
        ranked[0].author shouldBe "fav"
        ranked[1].author shouldBe "fav"
        ranked[2].author shouldBe "other"

        // Hiding an author or blocking a tag excludes matching items completely
        engine.recordHideAuthor("boring")
        val afterHide = engine.rerankItems(batch)
        afterHide.none { it.author == "boring" } shouldBe true
    }

    @Test
    fun `ReelsBoundedSet evicts oldest entries when capacity is exceeded`() {
        val set = ReelsBoundedSet<Int>(maxSize = 3)
        set.add(1)
        set.add(2)
        set.add(3)
        set.add(4)

        set.size shouldBe 3
        (1 in set) shouldBe false
        (2 in set && 3 in set && 4 in set) shouldBe true
    }

    @Test
    fun `ReelsQualityController supports codec fallback and window memory pruning`() = runTest {
        val prefs = mockk<SourcePreferences>(relaxed = true)
        every { prefs.reelsVideoQuality(any()).get() } returns "source"
        val variants = listOf(
            FeedVideoQualityVariant(
                id = "src",
                width = 1080,
                height = 1920,
                codec = "h264",
                sizeBytes = 2_000L,
                isSource = true,
            ),
            FeedVideoQualityVariant(
                id = "720",
                width = 720,
                height = 1280,
                codec = "h264",
                sizeBytes = 1_000L,
                isSource = false,
            ),
        )
        val source = object : AnimeFeedVideoQualitySource {
            override fun accountScopeId(): String = "acc1"
            override suspend fun prepareReplay(): FeedReplayContext? = null
            override val replayContextChanges: StateFlow<FeedReplayContext?> = MutableStateFlow(null)
            override suspend fun resolveReplayItem(
                itemId: String,
                expectedContext: FeedReplayContext,
                filters: AnimeFilterList,
            ): FeedReplayItemResult = FeedReplayItemResult.Unavailable
            override fun supportsVideoQuality(itemId: String): Boolean = true
            override suspend fun getVideoQualities(itemId: String, expectedAccountId: String) =
                FeedVideoQualitiesResult.Available(itemId, expectedAccountId, variants)
            override suspend fun resolveVideoQuality(itemId: String, variantId: String, expectedAccountId: String) =
                FeedVideoQualityResult.Available(
                    itemId = itemId,
                    accountId = expectedAccountId,
                    variant = variants.first { it.id == variantId },
                    videoUrl = "https://cdn.example/$itemId-$variantId.mp4",
                    cacheIdentity = "$itemId-$variantId",
                )
        }
        val controller = ReelsQualityController(prefs) { true }
        val owner = ReelsQualityOwner("tok", 1L, "acc1")
        controller.attach(source, owner)

        for (i in 1..70) {
            controller.prepare(owner, item("item-$i"), forceSd = false, recheckOnActivation = false)
        }
        controller.state.value.items.size shouldBe 70

        // Mark item-1's current variant ("src") incompatible -> returns true since "720" exists
        controller.markCurrentVariantIncompatible(owner, "item-1") shouldBe true

        // Prune outside a 3-item window
        controller.pruneOutsideWindow(setOf("item-1", "item-2", "item-3"))
        controller.state.value.items.keys shouldBe setOf("item-1", "item-2", "item-3")
    }

    @Test
    fun `ReelsHlsDownloader parses master and media playlists and rejects encrypted streams`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=500000
            low/prog.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1800000
            high/prog.m3u8
        """.trimIndent()
        val parsedMaster = ReelsHlsDownloader.parsePlaylist("https://cdn.example/hls/master.m3u8", master)
        parsedMaster shouldBe ReelsHlsDownloader.ParsedPlaylist.Master("https://cdn.example/hls/high/prog.m3u8")

        val media = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:2.0,
            seg1.m4s
            #EXTINF:2.0,
            seg2.m4s
        """.trimIndent()
        val parsedMedia = ReelsHlsDownloader.parsePlaylist("https://cdn.example/hls/high/prog.m3u8", media)
        parsedMedia shouldBe ReelsHlsDownloader.ParsedPlaylist.Media(
            initSegmentUrl = "https://cdn.example/hls/high/init.mp4",
            segmentUrls = listOf(
                "https://cdn.example/hls/high/seg1.m4s",
                "https://cdn.example/hls/high/seg2.m4s",
            ),
            isFmp4 = true,
        )

        val encrypted = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://cdn.example/key"
            #EXTINF:2.0,
            seg1.ts
        """.trimIndent()
        ReelsHlsDownloader.parsePlaylist("https://cdn.example/hls/enc.m3u8", encrypted) shouldBe
            ReelsHlsDownloader.ParsedPlaylist.Unsupported
    }
}
