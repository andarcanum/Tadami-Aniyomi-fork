package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.media.AudioManager
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.SavedStateHandle
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutConfig
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutOrientation
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutRegion
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutSlot
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.download.service.DownloadPreferences
import java.io.File

class PlayerViewModelRegressionTest {

    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `subtitle selection resets for a new file`() {
        val context = createContext()

        context.viewModel.updateSubtitle(sid = 7, secondarySid = 9)
        context.viewModel.selectedSubtitles.value shouldBe Pair(7, 9)

        context.viewModel.resetSubtitleSelection()

        context.viewModel.selectedSubtitles.value shouldBe Pair(-1, -1)
    }

    @Test
    fun `player layout config follows preference changes`() {
        val context = createContext()
        val updatedLayout = PlayerLayoutConfig().withRegion(
            orientation = PlayerLayoutOrientation.Portrait,
            slot = PlayerLayoutSlot.CustomButton,
            region = PlayerLayoutRegion.Hidden,
        ).toPreferenceValue()

        testDispatcher.scheduler.advanceUntilIdle()

        context.viewModel.playerLayoutConfig.value shouldBe
            PlayerLayoutConfig.fromPreferenceValue(context.layoutFlow.value)

        context.layoutFlow.value = updatedLayout
        testDispatcher.scheduler.advanceUntilIdle()

        context.viewModel.playerLayoutConfig.value shouldBe
            PlayerLayoutConfig.fromPreferenceValue(updatedLayout)
    }

    @Test
    fun `custom buttons reload when the toggle is enabled later`() {
        val context = createContext()

        testDispatcher.scheduler.advanceUntilIdle()
        verifyCustomButtonSetup(context.activity, context.customButtons, enabled = false)

        context.showCustomButtonsFlow.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        verifyCustomButtonSetup(context.activity, context.customButtons, enabled = true)
    }

    @Test
    fun `custom buttons are not loaded twice when the toggle cycles off and on`() {
        val context = createContext()

        testDispatcher.scheduler.advanceUntilIdle()
        verifyCustomButtonSetup(context.activity, context.customButtons, enabled = false)

        context.showCustomButtonsFlow.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        context.showCustomButtonsFlow.value = false
        testDispatcher.scheduler.advanceUntilIdle()

        context.showCustomButtonsFlow.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.verify(exactly = 1, timeout = 1_000) {
            context.activity.setupCustomButtons(
                buttons = context.customButtons,
                enabled = true,
            )
        }
    }

    private fun createContext(): TestContext {
        val layoutFlow = MutableStateFlow(PlayerLayoutConfig().toPreferenceValue())
        val showCustomButtonsFlow = MutableStateFlow(false)
        val customButtons = listOf(
            CustomButton(
                id = 7L,
                name = "Open",
                isFavorite = true,
                sortIndex = 0L,
                content = "return ${'$'}id",
                longPressContent = "return ${'$'}isPrimary",
                onStartup = "startup ${'$'}id",
            ),
        )

        val playerPreferences = mockk<PlayerPreferences> {
            every { playerSpeed() } returns floatPreference(1f)
            every { hideControls() } returns booleanPreference(false)
            every { playerLayoutConfig() } returns stringFlowPreference(layoutFlow)
            every { enableSkipIntro() } returns booleanPreference(true)
            every { autoSkipIntro() } returns booleanPreference(false)
            every { enableNetflixStyleIntroSkip() } returns booleanPreference(false)
            every { waitingTimeIntroSkip() } returns intPreference(5)
            every { showSystemStatusBar() } returns booleanPreference(false)
            every { showCustomButtons() } returns booleanFlowPreference(showCustomButtonsFlow)
        }

        val gesturePreferences = mockk<GesturePreferences> {
            every { skipLengthPreference() } returns intPreference(10)
            every { playerSmoothSeek() } returns booleanPreference(false)
            every { showSeekBar() } returns booleanPreference(false)
        }

        val downloadPreferences = mockk<DownloadPreferences> {
            every { autoDownloadWhileWatching() } returns intPreference(0)
        }

        val uiPreferences = mockk<UiPreferences> {
            every { relativeTime() } returns booleanPreference(true)
            every { dateFormat() } returns stringFlowPreference(MutableStateFlow(""))
        }

        val getCustomButtons = mockk<GetCustomButtons>()
        coEvery { getCustomButtons.getAll() } returns customButtons

        val activity = mockk<PlayerActivity>(relaxed = true) {
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
            sourceManager = mockk(relaxed = true),
            downloadManager = mockk(relaxed = true),
            imageSaver = mockk(relaxed = true),
            downloadPreferences = downloadPreferences,
            trackPreferences = mockk(relaxed = true),
            trackEpisode = mockk(relaxed = true),
            getAnime = mockk(relaxed = true),
            getNextEpisodes = mockk(relaxed = true),
            getEpisodesByAnimeId = mockk(relaxed = true),
            getAnimeCategories = mockk(relaxed = true),
            getTracks = mockk(relaxed = true),
            upsertHistory = mockk(relaxed = true),
            updateEpisode = mockk(relaxed = true),
            setAnimeViewerFlags = mockk(relaxed = true),
            playerPreferences = playerPreferences,
            gesturePreferences = gesturePreferences,
            basePreferences = mockk(relaxed = true),
            getCustomButtons = getCustomButtons,
            trackSelect = mockk(relaxed = true),
            getIncognitoState = mockk(relaxed = true),
            libraryPreferences = mockk(relaxed = true),
            preferenceStore = mockk(relaxed = true),
            uiPreferences = uiPreferences,
            eventBus = mockk(relaxed = true),
            activityDataRepository = mockk(relaxed = true),
        )

        return TestContext(
            viewModel = viewModel,
            activity = activity,
            layoutFlow = layoutFlow,
            showCustomButtonsFlow = showCustomButtonsFlow,
            customButtons = customButtons,
        )
    }

    private fun verifyCustomButtonSetup(
        activity: PlayerActivity,
        buttons: List<CustomButton>,
        enabled: Boolean,
    ) {
        io.mockk.verify(timeout = 1_000) {
            activity.setupCustomButtons(buttons = buttons, enabled = enabled)
        }
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

    private fun stringFlowPreference(flow: MutableStateFlow<String>): Preference<String> = mockk {
        every { get() } answers { flow.value }
        every { changes() } returns flow
    }

    private data class TestContext(
        val viewModel: PlayerViewModel,
        val activity: PlayerActivity,
        val layoutFlow: MutableStateFlow<String>,
        val showCustomButtonsFlow: MutableStateFlow<Boolean>,
        val customButtons: List<CustomButton>,
    )
}
