package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.media.AudioManager
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubtitlesBorderStyle
import eu.kanade.tachiyomi.ui.player.controls.components.panels.resolveSubtitleJustification
import eu.kanade.tachiyomi.ui.player.controls.components.panels.resolveSubtitlesBorderStyle
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutConfig
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitleJustification
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.player.utils.TrackSelect
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import java.io.File

class PlayerReliabilityFixesTest {

    @TempDir
    lateinit var tempDir: File

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val createdViewModels = mutableListOf<PlayerViewModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        createdViewModels.forEach {
            it.cancelPlayerJobs()
            it.viewModelScope.cancel()
        }
        createdViewModels.clear()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `parseMpvTrackId handles special values and invalid strings safely`() {
        parseMpvTrackId("auto") shouldBe null
        parseMpvTrackId("no") shouldBe -1
        parseMpvTrackId("3") shouldBe 3
        parseMpvTrackId("invalid") shouldBe null
        parseMpvTrackId("") shouldBe null
    }

    @Test
    fun `subtitle typography enum lookups fall back safely for unknown or null values`() {
        resolveSubtitleJustification("left") shouldBe SubtitleJustification.Left
        resolveSubtitleJustification("center") shouldBe SubtitleJustification.Center
        resolveSubtitleJustification("unknown") shouldBe SubtitleJustification.Auto
        resolveSubtitleJustification(null) shouldBe SubtitleJustification.Auto

        resolveSubtitlesBorderStyle("opaque-box") shouldBe SubtitlesBorderStyle.OpaqueBox
        resolveSubtitlesBorderStyle("unknown") shouldBe SubtitlesBorderStyle.OutlineAndShadow
        resolveSubtitlesBorderStyle(null) shouldBe SubtitlesBorderStyle.OutlineAndShadow
    }

    @Test
    fun `loadHosters catches empty video error, clears loading flags, and shows toast`() {
        val ctx = createContext()
        val source = mockk<AnimeSource>(relaxed = true)

        ctx.viewModel.updateIsLoadingEpisode(true)
        ctx.viewModel.isLoading.value = true
        ctx.viewModel.updateIsLoadingHosters(true)

        ctx.viewModel.loadHosters(
            source = source,
            hosterList = emptyList(),
            hosterIndex = -1,
            videoIndex = -1,
        )

        verify(timeout = 2_000) { ctx.activity.showToast(any()) }
        awaitUntil {
            !ctx.viewModel.isLoadingEpisode.value &&
                !ctx.viewModel.isLoading.value &&
                !ctx.viewModel.isLoadingHosters.value
        }

        ctx.viewModel.isLoadingEpisode.value shouldBe false
        ctx.viewModel.isLoading.value shouldBe false
        ctx.viewModel.isLoadingHosters.value shouldBe false
    }

    @Test
    fun `onVideoClicked handles out of bounds safely and recovers from resolve failure`() {
        val ctx = createContext()
        mockkObject(HosterLoader.Companion)
        coEvery { HosterLoader.getResolvedVideo(any(), any()) } throws RuntimeException("Network down")

        // Out-of-bounds click is a safe no-op
        ctx.viewModel.onVideoClicked(hosterIndex = 99, videoIndex = 0)

        val video = Video(videoUrl = "https://example.com/v.mp4", videoTitle = "1080p")
        val hoster = Hoster(hosterName = "Test", videoList = listOf(video))
        val source = mockk<AnimeSource>(relaxed = true)

        ctx.viewModel.loadHosters(
            source = source,
            hosterList = listOf(hoster),
            hosterIndex = 0,
            videoIndex = 0,
        )

        verify(timeout = 2_000) { ctx.activity.showToast("Network down") }
        awaitUntil {
            !ctx.viewModel.isLoadingEpisode.value &&
                !ctx.viewModel.isLoading.value
        }
    }

    @Test
    fun `detachActivity and attachActivity route calls to the surviving recreated activity`() {
        val ctx = createContext()
        val newActivity = mockk<PlayerActivity>(relaxed = true) {
            every { isDestroyed } returns false
        }

        ctx.viewModel.detachActivity(ctx.activity)
        ctx.viewModel.changeEpisode(previous = true)
        verify(exactly = 0) { ctx.activity.showToast(any()) }

        ctx.viewModel.attachActivity(newActivity)
        // Detaching old activity after newActivity attached must NOT detach newActivity
        ctx.viewModel.detachActivity(ctx.activity)

        ctx.viewModel.changeEpisode(previous = true)
        verify(exactly = 1) { newActivity.showToast(any()) }
    }

    @Test
    fun `resetHosterState clears transient episode playback state`() {
        val ctx = createContext()
        ctx.viewModel.updateChapters(listOf(IndexedSegment(name = "OP", start = 10f, index = 0)))
        ctx.viewModel.updateChapter(0)

        ctx.viewModel.resetHosterState()

        ctx.viewModel.currentVideo.value shouldBe null
        ctx.viewModel.chapters.value shouldBe emptyList()
        ctx.viewModel.currentChapter.value shouldBe null
        ctx.viewModel.skipIntroText.value shouldBe null
        ctx.viewModel.hosterState.value shouldBe emptyList()
        ctx.viewModel.hosterList.value shouldBe emptyList()
        ctx.viewModel.selectedHosterVideoIndex.value shouldBe Pair(-1, -1)
    }

    @Test
    fun `loadEpisode clears stale currentHosterList before fetching new episode hosters`() = runBlocking {
        val ctx = createContext()
        mockkObject(EpisodeLoader.Companion)
        every { EpisodeLoader.isDownload(any(), any()) } returns false

        val ep1 = Episode.create().copy(id = 1L, animeId = 10L, name = "Ep 1", url = "/ep1", episodeNumber = 1.0)
        val ep2 = Episode.create().copy(id = 2L, animeId = 10L, name = "Ep 2", url = "/ep2", episodeNumber = 2.0)
        val anime = Anime.create().copy(id = 10L, title = "Test Anime", source = 42L)
        val ep1Hosters = listOf(Hoster(hosterName = "Hoster1", videoList = listOf(Video("u1", "720p"))))

        coEvery { ctx.getAnime.await(10L) } returns anime
        coEvery { ctx.getEpisodesByAnimeId.await(10L) } returns listOf(ep1, ep2)
        coEvery { EpisodeLoader.getHosters(match { it.id == 1L }, any(), any()) } returns ep1Hosters
        coEvery { EpisodeLoader.getHosters(match { it.id == 2L }, any(), any()) } throws RuntimeException("Timeout")

        val initPair = ctx.viewModel.init(
            animeId = 10L,
            initialEpisodeId = 1L,
            hostList = "",
            hostIndex = -1,
            vidIndex = -1,
        )
        initPair.second.isSuccess shouldBe true
        initPair.first.hosterList shouldBe ep1Hosters

        val ep2Result = ctx.viewModel.loadEpisode(2L)
        ep2Result?.hosterList shouldBe null
    }

    @Test
    fun `onFinishLoadingTracks clears isLoadingTracks, isLoadingEpisode, and isLoading`() {
        val ctx = createContext()
        ctx.viewModel.isLoadingTracks.value = true
        ctx.viewModel.updateIsLoadingEpisode(true)
        ctx.viewModel.isLoading.value = true

        ctx.viewModel.onFinishLoadingTracks()

        ctx.viewModel.isLoadingTracks.value shouldBe false
        ctx.viewModel.isLoadingEpisode.value shouldBe false
        ctx.viewModel.isLoading.value shouldBe false
    }

    private fun awaitUntil(timeoutMs: Long = 2_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
    }

    private fun createContext(): TestContext {
        val layoutFlow = MutableStateFlow(PlayerLayoutConfig().toPreferenceValue())
        val showCustomButtonsFlow = MutableStateFlow(false)

        val playerPreferences = mockk<PlayerPreferences>(relaxed = true) {
            every { playerSpeed() } returns floatPreference(1f)
            every { hideControls() } returns booleanPreference(false)
            every { playerLayoutConfig() } returns stringFlowPreference(layoutFlow)
            every { enableSkipIntro() } returns booleanPreference(true)
            every { autoSkipIntro() } returns booleanPreference(false)
            every { enableNetflixStyleIntroSkip() } returns booleanPreference(false)
            every { waitingTimeIntroSkip() } returns intPreference(5)
            every { showSystemStatusBar() } returns booleanPreference(false)
            every { showCustomButtons() } returns booleanFlowPreference(showCustomButtonsFlow)
            every { hosterSourceFilter() } returns stringPreference("all")
        }

        val gesturePreferences = mockk<GesturePreferences>(relaxed = true) {
            every { skipLengthPreference() } returns intPreference(10)
            every { playerSmoothSeek() } returns booleanPreference(false)
            every { showSeekBar() } returns booleanPreference(false)
            every { defaultIntroLength() } returns intPreference(85)
        }

        val downloadPreferences = mockk<DownloadPreferences>(relaxed = true) {
            every { autoDownloadWhileWatching() } returns intPreference(0)
        }

        val subtitlePreferences = mockk<SubtitlePreferences>(relaxed = true) {
            every { preferredSubLanguages() } returns stringPreference("")
            every { subtitleTranslationEnabled() } returns booleanPreference(false)
        }

        val uiPreferences = mockk<UiPreferences>(relaxed = true) {
            every { relativeTime() } returns booleanPreference(true)
            every { dateFormat() } returns stringFlowPreference(MutableStateFlow(""))
        }

        val basePreferences = mockk<BasePreferences>(relaxed = true) {
            every { downloadedOnly() } returns booleanPreference(false)
        }

        val sourceManager = mockk<AnimeSourceManager>(relaxed = true) {
            every { isInitialized } returns MutableStateFlow(true)
            every { getOrStub(any()) } returns mockk(relaxed = true)
        }

        val downloadManager = mockk<AnimeDownloadManager>(relaxed = true) {
            every { isEpisodeDownloaded(any(), any(), any(), any()) } returns false
        }

        val getAnime = mockk<GetAnime>(relaxed = true)
        val getEpisodesByAnimeId = mockk<GetEpisodesByAnimeId>(relaxed = true)
        val getAnimeCategories = mockk<GetAnimeCategories>(relaxed = true) {
            coEvery { await(any()) } returns emptyList()
        }
        val getTracks = mockk<GetAnimeTracks>(relaxed = true) {
            coEvery { await(any()) } returns emptyList()
        }
        val getCustomButtons = mockk<GetCustomButtons> {
            coEvery { getAll() } returns emptyList()
        }
        val getIncognitoState = mockk<GetAnimeIncognitoState>(relaxed = true) {
            every { subscribe(any()) } returns flowOf(false)
            every { await(any()) } returns false
        }
        val preferenceStore = mockk<PreferenceStore>(relaxed = true) {
            every { getString(any(), any()) } answers { stringPreference(secondArg()) }
        }

        val activity = mockk<PlayerActivity>(relaxed = true) {
            every { isDestroyed } returns false
            every { withActiveMpv(any(), any<() -> Any?>()) } returns null
            every { cacheDir } returns tempDir
            every { contentResolver } returns mockk(relaxed = true)
            every { audioManager } returns mockk(relaxed = true) {
                every { getStreamVolume(AudioManager.STREAM_MUSIC) } returns 0
            }
            every { getSystemService(Context.INPUT_METHOD_SERVICE) } returns
                mockk<InputMethodManager>(relaxed = true)
        }

        val viewModel = PlayerViewModel(
            activity = activity,
            savedState = SavedStateHandle(),
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            imageSaver = mockk(relaxed = true),
            downloadPreferences = downloadPreferences,
            trackPreferences = mockk(relaxed = true),
            trackEpisode = mockk(relaxed = true),
            getAnime = getAnime,
            getNextEpisodes = mockk(relaxed = true),
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            getAnimeCategories = getAnimeCategories,
            getTracks = getTracks,
            upsertHistory = mockk(relaxed = true),
            updateEpisode = mockk(relaxed = true),
            updateAnime = mockk(relaxed = true),
            setAnimeViewerFlags = mockk(relaxed = true),
            playerPreferences = playerPreferences,
            gesturePreferences = gesturePreferences,
            subtitlePreferences = subtitlePreferences,
            subtitleTranslationCoordinator = mockk(relaxed = true),
            networkHelper = mockk(relaxed = true),
            basePreferences = basePreferences,
            getCustomButtons = getCustomButtons,
            trackSelect = mockk<TrackSelect>(relaxed = true),
            getIncognitoState = getIncognitoState,
            libraryPreferences = mockk(relaxed = true),
            preferenceStore = preferenceStore,
            uiPreferences = uiPreferences,
            eventBus = mockk(relaxed = true),
            activityDataRepository = mockk(relaxed = true),
            discoveryRepository = mockk(relaxed = true),
        ).also { createdViewModels += it }

        return TestContext(
            viewModel = viewModel,
            activity = activity,
            getAnime = getAnime,
            getEpisodesByAnimeId = getEpisodesByAnimeId,
        )
    }

    private fun booleanPreference(value: Boolean): Preference<Boolean> = mockk {
        every { get() } returns value
    }

    private fun booleanFlowPreference(flow: MutableStateFlow<Boolean>): Preference<Boolean> = mockk {
        every { get() } answers { flow.value }
        every { changes() } returns flow
    }

    private fun floatPreference(value: Float): Preference<Float> = mockk {
        every { get() } returns value
    }

    private fun intPreference(value: Int): Preference<Int> = mockk {
        every { get() } returns value
    }

    private fun stringPreference(value: String): Preference<String> = mockk {
        every { get() } returns value
    }

    private fun stringFlowPreference(flow: MutableStateFlow<String>): Preference<String> = mockk {
        every { get() } answers { flow.value }
        every { changes() } returns flow
    }

    private data class TestContext(
        val viewModel: PlayerViewModel,
        val activity: PlayerActivity,
        val getAnime: GetAnime,
        val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    )
}
