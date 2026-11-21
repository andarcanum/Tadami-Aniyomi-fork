/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player

// Code is a mix between PlayerViewModel from mpvKt and the former
// PlayerViewModel from Aniyomi.

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Immutable
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.shouldRecordAnimeCompletion
import eu.kanade.domain.items.episode.model.toDbEpisode
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.interactor.ForegroundIncognitoState
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.screen.player.custombutton.CustomButtonFetchState
import eu.kanade.presentation.more.settings.screen.player.custombutton.getButtons
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.data.database.models.anime.isRecognizedNumber
import eu.kanade.tachiyomi.data.database.models.anime.toDomainEpisode
import eu.kanade.tachiyomi.data.database.models.manga.isRecognizedNumber
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.getChangedAt
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutConfig
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.player.subtitle.translation.FFmpegEmbeddedSubtitleExtractor
import eu.kanade.tachiyomi.ui.player.subtitle.translation.FFprobeEmbeddedSubtitleStreamResolver
import eu.kanade.tachiyomi.ui.player.subtitle.translation.LocalEmbeddedSubtitleExtractionRequest
import eu.kanade.tachiyomi.ui.player.subtitle.translation.PlayerSubtitleTranslationTrack
import eu.kanade.tachiyomi.ui.player.subtitle.translation.PlayerSubtitleTranslationTrackKind
import eu.kanade.tachiyomi.ui.player.subtitle.translation.PlayerSubtitleTranslationUiState
import eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleFormat
import eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationCoordinator
import eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationPlayerBridge
import eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationProgress
import eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationProviderId
import eu.kanade.tachiyomi.ui.player.utils.AniSkipApi
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils.Companion.getStringRes
import eu.kanade.tachiyomi.ui.player.utils.TrackSelect
import eu.kanade.tachiyomi.ui.reader.SaveImageNotifier
import eu.kanade.tachiyomi.util.editBackground
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.editThumbnail
import eu.kanade.tachiyomi.util.episode.filterDownloadedEpisodes
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.toast
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.service.getEpisodeSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.InputStream
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

class PlayerViewModelProviderFactory(
    private val activity: PlayerActivity,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return PlayerViewModel(activity, extras.createSavedStateHandle()) as T
    }
}

class PlayerViewModel @JvmOverloads constructor(
    activity: PlayerActivity,
    private val savedState: SavedStateHandle,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val trackEpisode: TrackEpisode = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val getTracks: GetAnimeTracks = Injekt.get(),
    private val upsertHistory: UpsertAnimeHistory = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val setAnimeViewerFlags: SetAnimeViewerFlags = Injekt.get(),
    internal val playerPreferences: PlayerPreferences = Injekt.get(),
    internal val gesturePreferences: GesturePreferences = Injekt.get(),
    private val subtitlePreferences: SubtitlePreferences = Injekt.get(),
    private val subtitleTranslationCoordinator: SubtitleTranslationCoordinator = Injekt.get(),
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val getCustomButtons: GetCustomButtons = Injekt.get(),
    private val trackSelect: TrackSelect = Injekt.get(),
    private val getIncognitoState: GetAnimeIncognitoState = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
    uiPreferences: UiPreferences = Injekt.get(),
    private val eventBus: AchievementEventBus = Injekt.get(),
    private val activityDataRepository: ActivityDataRepository = Injekt.get(),
    private val discoveryRepository: tachiyomi.domain.discovery.repository.DiscoveryRepository = Injekt.get(),
) : ViewModel() {

    private val appContext: Context = activity.applicationContext

    @Volatile
    private var attachedActivity: PlayerActivity? = activity

    private val activeActivity: PlayerActivity?
        get() = attachedActivity?.takeUnless { it.isDestroyed }

    fun attachActivity(activity: PlayerActivity) {
        attachedActivity = activity
    }

    fun detachActivity(activity: PlayerActivity) {
        if (attachedActivity === activity) {
            attachedActivity = null
        }
    }

    private inline fun <T> withAttachedActivity(block: (PlayerActivity) -> T): T? {
        return activeActivity?.let(block)
    }

    private inline fun <T> withActiveMpv(
        requireNotExiting: Boolean = false,
        crossinline block: () -> T,
    ): T? {
        return activeActivity?.withActiveMpv(requireNotExiting) { block() }
    }

    private fun resolveString(res: StringResource): String {
        return (activeActivity ?: appContext).stringResource(res)
    }

    private fun resolveString(res: StringResource, vararg args: Any): String {
        return (activeActivity ?: appContext).stringResource(res, *args)
    }

    private fun showToastMessage(message: String) {
        withAttachedActivity { it.showToast(message) }
    }

    private suspend fun showErrorToast(error: Throwable) {
        val message = when (error) {
            is ExceptionWithStringResource -> runCatching { resolveString(error.stringResource) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: error.message?.takeIf { it.isNotBlank() }
                ?: "No available videos"
            else -> error.message?.takeIf { it.isNotBlank() }
                ?: runCatching { resolveString(AYMR.strings.no_available_videos) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                ?: "No available videos"
        }
        withUIContext {
            showToastMessage(message)
        }
    }

    /**
     * Taste Engine: «просмотрено» — открытие плеера помечает тайтл consumed.
     * viewModelScope доступен после конструктора — вызов только из init-пути.
     */
    private fun markDiscoveryConsumed(title: String, sourceId: Long) {
        viewModelScope.launchIO {
            eu.kanade.tachiyomi.data.discovery.TasteSignalRecorder.recordConsumed(
                repository = discoveryRepository,
                mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
                title = title,
                sourceId = sourceId,
                enrichmentScope = this,
            )
        }
    }

    private val _currentPlaylist = MutableStateFlow<List<Episode>>(emptyList())
    val currentPlaylist = _currentPlaylist.asStateFlow()

    private val _hasPreviousEpisode = MutableStateFlow(false)
    val hasPreviousEpisode = _hasPreviousEpisode.asStateFlow()

    private val _hasNextEpisode = MutableStateFlow(false)
    val hasNextEpisode = _hasNextEpisode.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode = _currentEpisode.asStateFlow()

    private val _currentAnime = MutableStateFlow<Anime?>(null)
    val currentAnime = _currentAnime.asStateFlow()

    private val _currentSource = MutableStateFlow<AnimeSource?>(null)
    val currentSource = _currentSource.asStateFlow()

    private val _isEpisodeOnline = MutableStateFlow(false)
    val isEpisodeOnline = _isEpisodeOnline.asStateFlow()

    private val _isLoadingEpisode = MutableStateFlow(false)
    val isLoadingEpisode = _isLoadingEpisode.asStateFlow()

    private val _currentDecoder = MutableStateFlow(
        runCatching {
            MPVLib.getPropertyString("hwdec")?.let(::getDecoderFromValue) ?: Decoder.Auto
        }.getOrElse { Decoder.Auto },
    )
    val currentDecoder = _currentDecoder.asStateFlow()

    val mediaTitle = MutableStateFlow("")
    val animeTitle = MutableStateFlow("")

    val isLoading = MutableStateFlow(true)
    val bufferingPercent = MutableStateFlow<Int?>(null)
    val cacheSpeedBytesPerSec = MutableStateFlow<Long?>(null)
    val videoZoom = MutableStateFlow(0f)
    val repeatEpisode = MutableStateFlow(false)
    val abLoopState = MutableStateFlow(AbLoopState())
    val playbackSpeed = MutableStateFlow(playerPreferences.playerSpeed().get())

    private val _subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val subtitleTracks = _subtitleTracks.asStateFlow()
    private val _selectedSubtitles = MutableStateFlow(Pair(-1, -1))
    val selectedSubtitles = _selectedSubtitles.asStateFlow()
    private val _subtitleTranslationProgress = MutableStateFlow<SubtitleTranslationProgress?>(null)
    val subtitleTranslationProgress = _subtitleTranslationProgress.asStateFlow()
    private val _subtitleTranslationUiState = MutableStateFlow(PlayerSubtitleTranslationUiState())
    val subtitleTranslationUiState = _subtitleTranslationUiState.asStateFlow()
    private var subtitleTranslationJob: Job? = null

    private val _audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks = _audioTracks.asStateFlow()
    private val _selectedAudio = MutableStateFlow(-1)
    val selectedAudio = _selectedAudio.asStateFlow()

    val isLoadingTracks = MutableStateFlow(true)

    private val _hosterList = MutableStateFlow<List<Hoster>>(emptyList())
    val hosterList = _hosterList.asStateFlow()
    private val _isLoadingHosters = MutableStateFlow(true)
    val isLoadingHosters = _isLoadingHosters.asStateFlow()
    private val _hosterState = MutableStateFlow<List<HosterState>>(emptyList())
    val hosterState = _hosterState.asStateFlow()
    private val _hosterExpandedList = MutableStateFlow<List<Boolean>>(emptyList())
    val hosterExpandedList = _hosterExpandedList.asStateFlow()
    private val _selectedHosterVideoIndex = MutableStateFlow(Pair(-1, -1))
    val selectedHosterVideoIndex = _selectedHosterVideoIndex.asStateFlow()

    private val episodeSessionId = AtomicLong(0L)
    private val autoFallbackAttempted = mutableSetOf<Pair<Int, Int>>()
    private val isAutoFallingBack = AtomicBoolean(false)
    private val progressPersistencePolicy = PlaybackProgressPersistencePolicy()
    private val nextEpisodePrefetcher = NextEpisodePrefetcher()

    private var chaptersJob: Job? = null
    private var hosterActionJob: Job? = null
    private var fallbackJob: Job? = null
    private val hosterLoadJobs = mutableListOf<Job>()

    @Volatile
    private var downloadAheadTriggeredForEpisodeId: Long? = null
    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo = _currentVideo.asStateFlow()

    private val _chapters = MutableStateFlow<List<IndexedSegment>>(emptyList())
    val chapters = _chapters.asStateFlow()
    private val _currentChapter = MutableStateFlow<IndexedSegment?>(null)
    val currentChapter = _currentChapter.asStateFlow()
    private val _skipIntroText = MutableStateFlow<String?>(null)
    val skipIntroText = _skipIntroText.asStateFlow()

    private val _pos = MutableStateFlow(0f)
    val pos = _pos.asStateFlow()

    val duration = MutableStateFlow(0f)

    private val _readAhead = MutableStateFlow(0f)
    val readAhead = _readAhead.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused = _paused.asStateFlow()

    // False because the video shouldn't start paused
    private val _pausedState = MutableStateFlow<Boolean?>(false)
    val pausedState = _pausedState.asStateFlow()

    private val _controlsShown = MutableStateFlow(!playerPreferences.hideControls().get())
    val controlsShown = _controlsShown.asStateFlow()
    private val _seekBarShown = MutableStateFlow(!playerPreferences.hideControls().get())
    val seekBarShown = _seekBarShown.asStateFlow()
    private val _areControlsLocked = MutableStateFlow(false)
    val areControlsLocked = _areControlsLocked.asStateFlow()

    val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
    val isBrightnessSliderShown = MutableStateFlow(false)
    val isVolumeSliderShown = MutableStateFlow(false)
    val currentBrightness = MutableStateFlow(
        runCatching {
            Settings.System.getFloat(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                .normalize(0f, 255f, 0f, 1f)
        }.getOrElse { 0f },
    )
    val currentVolume = MutableStateFlow(activity.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    val currentMPVVolume = MutableStateFlow(
        runCatching { MPVLib.getPropertyInt("volume") }.getOrElse { 0 },
    )
    var volumeBoostCap: Int = runCatching { MPVLib.getPropertyInt("volume-max") }.getOrElse { 0 }

    // Pair(startingPosition, seekAmount)
    val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)

    val isDynamicSpeedActive = MutableStateFlow(false)
    val preGesturePlaybackSpeed = MutableStateFlow(1f)
    val gesturePlaybackSpeed = MutableStateFlow(1f)

    val sheetShown = MutableStateFlow(Sheets.None)
    val panelShown = MutableStateFlow(Panels.None)
    val dialogShown = MutableStateFlow<Dialogs>(Dialogs.None)

    private val _dismissSheet = MutableStateFlow(false)
    val dismissSheet = _dismissSheet.asStateFlow()

    private val _seekText = MutableStateFlow<String?>(null)
    val seekText = _seekText.asStateFlow()
    private val _doubleTapSeekAmount = MutableStateFlow(0)
    val doubleTapSeekAmount = _doubleTapSeekAmount.asStateFlow()
    private val _isSeekingForwards = MutableStateFlow(false)
    val isSeekingForwards = _isSeekingForwards.asStateFlow()

    private var timerJob: Job? = null
    private val _remainingTime = MutableStateFlow(0)
    val remainingTime = _remainingTime.asStateFlow()

    val cachePath: String = activity.cacheDir.path

    private val _customButtons = MutableStateFlow<CustomButtonFetchState>(CustomButtonFetchState.Loading)
    val customButtons = _customButtons.asStateFlow()

    private val _primaryButtonTitle = MutableStateFlow("")
    val primaryButtonTitle = _primaryButtonTitle.asStateFlow()

    private val _primaryButton = MutableStateFlow<CustomButton?>(null)
    val primaryButton = _primaryButton.asStateFlow()
    private val _playerLayoutConfig = MutableStateFlow(
        PlayerLayoutConfig.fromPreferenceValue(playerPreferences.playerLayoutConfig().get()),
    )
    val playerLayoutConfig = _playerLayoutConfig.asStateFlow()

    @Volatile
    private var customButtonsCache: List<CustomButton>? = null
    private var customButtonsScriptLoaded = false
    private var foregroundIncognitoJob: Job? = null

    init {
        observePlayerPreferenceChanges()

        viewModelScope.launchIO {
            try {
                val buttons = getCustomButtons.getAll()
                customButtonsCache = buttons
                buttons.firstOrNull { it.isFavorite }?.let {
                    _primaryButton.update { _ -> it }
                    // If the button text is not empty, it has been set buy a lua script in which
                    // case we don't want to override it
                    if (_primaryButtonTitle.value.isEmpty()) {
                        setPrimaryCustomButtonTitle(it)
                    }
                }
                val showCustomButtons = playerPreferences.showCustomButtons().get()
                customButtonsScriptLoaded = showCustomButtons
                withAttachedActivity {
                    it.setupCustomButtons(
                        buttons = buttons,
                        enabled = showCustomButtons,
                    )
                }
                if (!showCustomButtons && playerPreferences.showCustomButtons().get()) {
                    reloadCustomButtons()
                }
                _customButtons.update { _ -> CustomButtonFetchState.Success(buttons.toImmutableList()) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                _customButtons.update { _ -> CustomButtonFetchState.Error(e.message ?: "Unable to fetch buttons") }
            }
        }
    }

    private fun observeForegroundIncognito(sourceId: Long?) {
        foregroundIncognitoJob?.cancel()
        foregroundIncognitoJob = viewModelScope.launch {
            getIncognitoState.subscribe(sourceId).collect { active ->
                ForegroundIncognitoState.set(this@PlayerViewModel, active)
            }
        }
    }

    private fun observePlayerPreferenceChanges() {
        playerPreferences.playerLayoutConfig()
            .changes()
            .drop(1)
            .onEach { value ->
                _playerLayoutConfig.value = PlayerLayoutConfig.fromPreferenceValue(value)
            }
            .launchIn(viewModelScope)

        playerPreferences.showCustomButtons()
            .changes()
            .drop(1)
            .filter { it }
            .onEach {
                reloadCustomButtons()
            }
            .launchIn(viewModelScope)
    }

    private fun reloadCustomButtons() {
        if (customButtonsScriptLoaded) return
        val buttons = customButtonsCache ?: return
        customButtonsScriptLoaded = true
        withAttachedActivity {
            it.setupCustomButtons(
                buttons = buttons,
                enabled = true,
            )
        }
    }

    fun cancelPlayerJobs() {
        trackLoadingJob?.cancel()
        trackLoadingJob = null
        chaptersJob?.cancel()
        chaptersJob = null
        subtitleTranslationJob?.cancel()
        subtitleTranslationJob = null
        getHosterVideoLinksJob?.cancel()
        getHosterVideoLinksJob = null
        hosterActionJob?.cancel()
        hosterActionJob = null
        fallbackJob?.cancel()
        fallbackJob = null
        synchronized(hosterLoadJobs) {
            hosterLoadJobs.forEach { it.cancel() }
            hosterLoadJobs.clear()
        }
    }

    /**
     * Starts a sleep timer/cancels the current timer if [seconds] is less than 1.
     */
    fun startTimer(seconds: Int) {
        timerJob?.cancel()
        _remainingTime.value = seconds
        if (seconds < 1) return
        timerJob = viewModelScope.launch {
            for (time in seconds downTo 0) {
                _remainingTime.value = time
                delay(1000)
            }
            pause()
            withUIContext { Injekt.get<Application>().toast(AYMR.strings.toast_sleep_timer_ended) }
        }
    }

    fun isEpisodeOnline(): Boolean? {
        val anime = currentAnime.value ?: return null
        val episode = currentEpisode.value ?: return null
        val source = currentSource.value ?: return null
        return source is AnimeHttpSource &&
            !EpisodeLoader.isDownload(
                episode.toDomainEpisode()!!,
                anime,
            )
    }

    fun updateIsLoadingEpisode(value: Boolean) {
        _isLoadingEpisode.update { _ -> value }
    }

    private fun updateEpisodeList(episodeList: List<Episode>) {
        _currentPlaylist.update { _ -> filterEpisodeList(episodeList) }
    }

    fun getDecoder() {
        val hwdec = activeActivity?.player?.hwdecActive ?: return
        _currentDecoder.update { getDecoderFromValue(hwdec) }
    }

    fun updateDecoder(decoder: Decoder) {
        withActiveMpv {
            MPVLib.setPropertyString("hwdec", decoder.value)
        }
    }

    val getTrackLanguage: (Int) -> String = {
        if (it != -1) {
            MPVLib.getPropertyString("track-list/$it/lang") ?: ""
        } else {
            resolveString(MR.strings.off)
        }
    }
    val getTrackTitle: (Int) -> String = {
        if (it != -1) {
            MPVLib.getPropertyString("track-list/$it/title") ?: ""
        } else {
            resolveString(MR.strings.off)
        }
    }
    val getTrackMPVId: (Int) -> Int = {
        if (it != -1) {
            MPVLib.getPropertyInt("track-list/$it/id") ?: -1
        } else {
            -1
        }
    }
    val getTrackType: (Int) -> String? = {
        MPVLib.getPropertyString("track-list/$it/type")
    }

    private val trackLoadingMutex = Mutex()
    private var trackLoadingJob: Job? = null
    private val trackListRevision = MutableStateFlow(0L)
    private val initialTracksSelected = AtomicBoolean(false)

    private suspend fun readTracksFromMpv(): Pair<List<VideoTrack>, List<VideoTrack>>? {
        val possibleTrackTypes = listOf("audio", "sub")
        val subTracks = mutableListOf<VideoTrack>()
        val audioTracks = mutableListOf(
            VideoTrack(-1, resolveString(MR.strings.off), null),
        )
        val loaded = trackLoadingMutex.withLock {
            val ctx = currentCoroutineContext()
            ctx.ensureActive()
            withActiveMpv(requireNotExiting = true) {
                try {
                    val tracksCount = MPVLib.getPropertyInt("track-list/count") ?: 0
                    for (i in 0..<tracksCount) {
                        if (!ctx.isActive) return@withActiveMpv false
                        val type = getTrackType(i)
                        if (!possibleTrackTypes.contains(type) || type == null) continue
                        when (type) {
                            "sub" -> subTracks.add(
                                VideoTrack(getTrackMPVId(i), getTrackTitle(i), getTrackLanguage(i)),
                            )
                            "audio" -> audioTracks.add(
                                VideoTrack(getTrackMPVId(i), getTrackTitle(i), getTrackLanguage(i)),
                            )
                            else -> error("Unrecognized track type")
                        }
                    }
                    true
                } catch (e: NullPointerException) {
                    logcat(LogPriority.ERROR) { "Couldn't load tracks, probably cause mpv was destroyed" }
                    false
                }
            } == true
        }
        return if (loaded) subTracks to audioTracks else null
    }

    fun loadTracks() {
        trackListRevision.update { it + 1 }
        trackLoadingJob?.cancel()
        trackLoadingJob = viewModelScope.launchIO {
            // Coalesce burst `track-list` observer notifications during file load / track-add
            // and serialize JNI reads so two IO threads never query `track-list/*` concurrently.
            delay(25)
            val (subTracks, audioTracks) = readTracksFromMpv() ?: return@launchIO
            ensureActive()
            _subtitleTracks.update { subTracks }
            _audioTracks.update { audioTracks }

            if (!isLoadingTracks.value && !initialTracksSelected.get()) {
                withUIContext {
                    onFinishLoadingTracks()
                }
            }
        }
    }

    internal suspend fun awaitAndFinishTracks(hasExternalTracks: Boolean) {
        if (hasExternalTracks) {
            val startRevision = trackListRevision.value
            withTimeoutOrNull(800L) {
                trackListRevision.first { it > startRevision }
                delay(50)
            }
        }
        trackLoadingJob?.cancel()
        trackLoadingJob = null
        readTracksFromMpv()?.let { (subTracks, audioTracks) ->
            _subtitleTracks.update { subTracks }
            _audioTracks.update { audioTracks }
        }
        withUIContext {
            onFinishLoadingTracks()
        }
    }

    /**
     * When all subtitle/audio tracks are loaded, select the preferred one based on preferences,
     * or select the first one in the list if trackSelect fails.
     */
    fun onFinishLoadingTracks() {
        initialTracksSelected.set(true)
        val activity = activeActivity
        if (activity != null && !activity.player.isExiting) {
            val selectedSubs = selectedSubtitles.value
            if (selectedSubs.first == -1 && selectedSubs.second == -1) {
                val preferredSubtitle = trackSelect.getPreferredTrackIndex(subtitleTracks.value)
                (preferredSubtitle ?: subtitleTracks.value.firstOrNull())?.let {
                    activity.player.sid = it.id
                    activity.player.secondarySid = -1
                }
            }

            val preferredAudio = trackSelect.getPreferredTrackIndex(audioTracks.value, subtitle = false)
            (preferredAudio ?: audioTracks.value.getOrNull(1))?.let {
                activity.player.aid = it.id
            }
        }

        isLoadingTracks.update { false }
        updateIsLoadingEpisode(false)
        isLoading.update { false }
        setPausedState()
    }

    @Immutable
    data class VideoTrack(
        val id: Int,
        val name: String,
        val language: String?,
    )

    fun loadChapters() {
        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launchIO {
            val chapters = withActiveMpv(requireNotExiting = true) {
                val list = mutableListOf<IndexedSegment>()
                val count = MPVLib.getPropertyInt("chapter-list/count") ?: 0
                for (i in 0 until count) {
                    if (!isActive) break
                    val title = MPVLib.getPropertyString("chapter-list/$i/title")
                    val time = MPVLib.getPropertyInt("chapter-list/$i/time") ?: 0
                    list.add(
                        IndexedSegment(
                            name = title,
                            start = time.toFloat(),
                            index = 0,
                        ),
                    )
                }
                list
            } ?: return@launchIO
            ensureActive()
            updateChapters(chapters.sortedBy { it.start })
        }
    }

    fun updateChapters(chapters: List<IndexedSegment>) {
        _chapters.update { _ -> chapters }
    }

    fun selectChapter(index: Int) {
        val time = chapters.value[index].start
        seekTo(time.toInt())
    }

    fun updateChapter(index: Long) {
        if (chapters.value.isEmpty() || index == -1L) return
        _currentChapter.update { chapters.value.getOrNull(index.toInt()) ?: return }
    }

    fun addAudio(uri: Uri) {
        val activity = activeActivity ?: return
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(activity) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(activity) else null
        withActiveMpv(requireNotExiting = true) {
            if (name == null) {
                MPVLib.command(arrayOf("audio-add", path, "cached"))
            } else {
                MPVLib.command(arrayOf("audio-add", path, "cached", name))
            }
        }
    }

    fun selectAudio(id: Int) {
        withAttachedActivity { it.player.aid = id }
    }

    fun updateAudio(id: Int) {
        _selectedAudio.update { id }
    }

    fun addSubtitle(uri: Uri) {
        val activity = activeActivity ?: return
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(activity) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(activity) else null
        withActiveMpv(requireNotExiting = true) {
            if (name == null) {
                MPVLib.command(arrayOf("sub-add", path, "cached"))
            } else {
                MPVLib.command(arrayOf("sub-add", path, "cached", name))
            }
        }
    }

    fun translatePreferredSubtitle() {
        openSubtitleTranslationSheet()
    }

    fun openSubtitleTranslationSheet() {
        if (!subtitlePreferences.subtitleTranslationEnabled().get()) {
            showToastMessage(resolveString(AYMR.strings.player_subtitle_translation_disabled))
            return
        }
        val tracks = buildSubtitleTranslationTracks()
        if (tracks.none { it.enabled }) {
            showToastMessage(resolveString(AYMR.strings.player_subtitle_translation_no_external_track))
        }
        val preferred = pickSubtitleTrackForTranslation(currentVideo.value?.subtitleTracks.orEmpty())
        _subtitleTranslationUiState.value = PlayerSubtitleTranslationUiState(
            tracks = tracks,
            selectedTrackId =
            tracks.firstOrNull { it.url == preferred?.url }?.id ?: tracks.firstOrNull { it.enabled }?.id,
            providerId = when (subtitlePreferences.subtitleTranslationProvider().get().lowercase()) {
                "ai" -> SubtitleTranslationProviderId.Ai
                else -> SubtitleTranslationProviderId.Google
            },
            sourceLanguage = subtitlePreferences.subtitleTranslationSourceLanguage().get().ifBlank { "auto" },
            targetLanguage = subtitlePreferences.subtitleTranslationTargetLanguage().get().ifBlank {
                java.util.Locale.getDefault().language
            },
            useCache = subtitlePreferences.subtitleTranslationCacheEnabled().get(),
            allowAi = subtitlePreferences.subtitleTranslationAllowAiProviders().get(),
            progress = null,
            isTranslating = false,
            error = null,
        )
        sheetShown.value = Sheets.SubtitleTranslation
        refreshEmbeddedSubtitleTranslationTracks()
    }

    fun selectSubtitleTranslationTrack(id: Int) {
        _subtitleTranslationUiState.update { state ->
            if (state.isTranslating) state else state.copy(selectedTrackId = id, error = null)
        }
    }

    fun selectSubtitleTranslationProvider(providerId: SubtitleTranslationProviderId) {
        _subtitleTranslationUiState.update { state ->
            if (state.isTranslating) return@update state
            if (providerId == SubtitleTranslationProviderId.Ai && !state.allowAi) {
                state.copy(error = resolveString(AYMR.strings.player_subtitle_translation_ai_requires_opt_in))
            } else {
                subtitlePreferences.subtitleTranslationProvider().set(
                    when (providerId) {
                        SubtitleTranslationProviderId.Google -> "google"
                        SubtitleTranslationProviderId.Ai -> "ai"
                    },
                )
                state.copy(providerId = providerId, error = null)
            }
        }
    }

    fun toggleSubtitleTranslationCache(enabled: Boolean) {
        subtitlePreferences.subtitleTranslationCacheEnabled().set(enabled)
        _subtitleTranslationUiState.update { state ->
            if (state.isTranslating) state else state.copy(useCache = enabled)
        }
    }

    fun setSubtitleTranslationSourceLanguage(language: String) {
        subtitlePreferences.subtitleTranslationSourceLanguage().set(language)
        _subtitleTranslationUiState.update { state ->
            if (state.isTranslating) state else state.copy(sourceLanguage = language, error = null)
        }
    }

    fun setSubtitleTranslationTargetLanguage(language: String) {
        subtitlePreferences.subtitleTranslationTargetLanguage().set(language)
        _subtitleTranslationUiState.update { state ->
            if (state.isTranslating) state else state.copy(targetLanguage = language, error = null)
        }
    }

    fun cancelSubtitleTranslation() {
        subtitleTranslationJob?.cancel()
        subtitleTranslationJob = null
        _subtitleTranslationProgress.value = null
        _subtitleTranslationUiState.update { it.copy(isTranslating = false, progress = null) }
    }

    fun startSubtitleTranslation() {
        val state = subtitleTranslationUiState.value
        if (state.isTranslating) return
        val selectedTrack = state.selectedTrack
        if (selectedTrack == null || !selectedTrack.enabled) {
            _subtitleTranslationUiState.update {
                it.copy(error = resolveString(AYMR.strings.player_subtitle_translation_no_external_track))
            }
            return
        }
        val externalTrack = selectedTrack.toExternalTrack()
        val canTranslateEmbedded = selectedTrack.kind == PlayerSubtitleTranslationTrackKind.Embedded &&
            selectedTrack.streamSpecifier != null
        if (externalTrack == null && !canTranslateEmbedded) {
            _subtitleTranslationUiState.update {
                it.copy(
                    error =
                    selectedTrack.disabledReason
                        ?: resolveString(AYMR.strings.player_subtitle_translation_embedded_unavailable),
                )
            }
            return
        }
        if (state.targetLanguage.isBlank()) {
            _subtitleTranslationUiState.update {
                it.copy(
                    error = resolveString(AYMR.strings.pref_player_subtitle_translation_target_lang_summary),
                )
            }
            return
        }
        if (state.providerId == SubtitleTranslationProviderId.Ai && !state.allowAi) {
            _subtitleTranslationUiState.update {
                it.copy(error = resolveString(AYMR.strings.player_subtitle_translation_ai_requires_opt_in))
            }
            return
        }
        subtitleTranslationJob?.cancel()
        subtitleTranslationJob = viewModelScope.launchIO {
            _subtitleTranslationUiState.update { it.copy(isTranslating = true, progress = null, error = null) }
            runCatching {
                val bridge = SubtitleTranslationPlayerBridge(
                    networkHelper = networkHelper,
                    contentResolver = appContext.contentResolver,
                    cacheDir = appContext.cacheDir,
                    coordinator = subtitleTranslationCoordinator,
                )
                val translatedFile = if (externalTrack != null) {
                    bridge.translateExternalTrack(
                        track = externalTrack,
                        sourceLanguage = state.sourceLanguage,
                        targetLanguage = state.targetLanguage,
                        providerId = state.providerId,
                        useCache = state.useCache,
                    ) { progress ->
                        _subtitleTranslationProgress.value = progress
                        _subtitleTranslationUiState.update { it.copy(progress = progress) }
                    }
                } else {
                    val videoUri = currentVideo.value?.videoUrl?.let(Uri::parse)
                        ?: error(resolveString(AYMR.strings.player_subtitle_translation_embedded_unavailable))
                    val extracted = FFmpegEmbeddedSubtitleExtractor(
                        context = appContext,
                        outputDir = File(appContext.cacheDir, "subtitle_translations/embedded"),
                    ).extract(
                        LocalEmbeddedSubtitleExtractionRequest(
                            videoUri = videoUri,
                            streamSpecifier = requireNotNull(selectedTrack.streamSpecifier),
                            outputFormat = SubtitleFormat.Ass,
                            sourceIdentity = currentVideo.value?.videoUrl.orEmpty(),
                        ),
                    )
                    bridge.translateRawSubtitle(
                        raw = extracted.rawText,
                        formatHint = extracted.formatHint,
                        sourceLanguage = state.sourceLanguage,
                        targetLanguage = state.targetLanguage,
                        providerId = state.providerId,
                        sourceIdentity = extracted.sourceIdentity,
                        useCache = state.useCache,
                    ) { progress ->
                        _subtitleTranslationProgress.value = progress
                        _subtitleTranslationUiState.update { it.copy(progress = progress) }
                    }
                }
                ensureActive()
                withActiveMpv(requireNotExiting = true) {
                    MPVLib.command(
                        arrayOf(
                            "sub-add",
                            translatedFile.absolutePath,
                            "cached",
                            "Translated ${state.targetLanguage.ifBlank { java.util.Locale.getDefault().language }}",
                        ),
                    )
                }
                withUIContext {
                    showToastMessage(resolveString(AYMR.strings.player_subtitle_translation_added))
                }
                _subtitleTranslationUiState.update { it.copy(isTranslating = false, progress = null, error = null) }
                _subtitleTranslationProgress.value = null
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Subtitle translation failed" }
                _subtitleTranslationUiState.update {
                    it.copy(
                        isTranslating = false,
                        progress = null,
                        error =
                        error.message ?: resolveString(AYMR.strings.player_subtitle_translation_failed),
                    )
                }
                _subtitleTranslationProgress.value = null
                withUIContext {
                    showToastMessage(
                        error.message ?: resolveString(AYMR.strings.player_subtitle_translation_failed),
                    )
                }
            }
        }
    }

    private fun refreshEmbeddedSubtitleTranslationTracks() {
        val rawVideoUrl = currentVideo.value?.videoUrl ?: return
        if (rawVideoUrl.startsWith("http://", ignoreCase = true) ||
            rawVideoUrl.startsWith("https://", ignoreCase = true)
        ) {
            return
        }
        val videoUri = Uri.parse(rawVideoUrl)
        viewModelScope.launchIO {
            runCatching {
                FFprobeEmbeddedSubtitleStreamResolver(appContext).resolve(videoUri)
            }.onSuccess { streams ->
                if (streams.isEmpty()) return@onSuccess
                _subtitleTranslationUiState.update { state ->
                    val updatedTracks = state.tracks.map { track ->
                        if (track.kind != PlayerSubtitleTranslationTrackKind.Embedded) return@map track
                        val embeddedOrder = track.id - 10_000
                        val stream = streams.getOrNull(embeddedOrder) ?: return@map track
                        track.copy(
                            language = track.language.ifBlank { stream.language.orEmpty() },
                            streamSpecifier = stream.ffmpegSpecifier,
                            enabled = true,
                            disabledReason = null,
                        )
                    }
                    state.copy(tracks = updatedTracks)
                }
            }.onFailure { error ->
                logcat(LogPriority.DEBUG, error) { "Embedded subtitle probing failed" }
            }
        }
    }

    private fun buildSubtitleTranslationTracks(): List<PlayerSubtitleTranslationTrack> {
        val external = currentVideo.value?.subtitleTracks.orEmpty().mapIndexed { index, track ->
            PlayerSubtitleTranslationTrack(
                id = index,
                title = track.lang.ifBlank { "External subtitle ${index + 1}" },
                language = track.lang,
                url = track.url,
                kind = PlayerSubtitleTranslationTrackKind.External,
                enabled = true,
            )
        }
        val embedded = subtitleTracks.value.mapIndexed { index, track ->
            PlayerSubtitleTranslationTrack(
                id = 10_000 + index,
                title = track.name,
                language = track.language.orEmpty(),
                url = null,
                kind = PlayerSubtitleTranslationTrackKind.Embedded,
                enabled = false,
                disabledReason = resolveString(AYMR.strings.player_subtitle_translation_embedded_unavailable),
            )
        }
        return external + embedded
    }

    private fun pickSubtitleTrackForTranslation(tracks: List<Track>): Track? {
        if (tracks.isEmpty()) return null
        val preferredLanguages = subtitlePreferences.preferredSubLanguages().get()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        return preferredLanguages.firstNotNullOfOrNull { preferred ->
            tracks.firstOrNull { track -> track.lang.lowercase().startsWith(preferred) }
        } ?: tracks.first()
    }

    fun selectSub(id: Int) {
        val selectedSubs = selectedSubtitles.value
        _selectedSubtitles.update {
            when (id) {
                selectedSubs.first -> Pair(selectedSubs.second, -1)
                selectedSubs.second -> Pair(selectedSubs.first, -1)
                else -> {
                    if (selectedSubs.first != -1) {
                        Pair(selectedSubs.first, id)
                    } else {
                        Pair(id, -1)
                    }
                }
            }
        }
        withAttachedActivity { activity ->
            activity.player.secondarySid = _selectedSubtitles.value.second
            activity.player.sid = _selectedSubtitles.value.first
        }
    }

    fun updateSubtitle(sid: Int, secondarySid: Int) {
        _selectedSubtitles.update { Pair(sid, secondarySid) }
    }

    internal fun resetSubtitleSelection() {
        initialTracksSelected.set(false)
        _selectedSubtitles.update { Pair(-1, -1) }
    }

    fun updatePlayBackPos(pos: Float) {
        onSecondReached(pos.toInt(), duration.value.toInt())
        _pos.update { pos }
    }

    fun updateReadAhead(value: Long) {
        _readAhead.update { value.toFloat() }
    }

    private fun updatePausedState() {
        if (pausedState.value == null) {
            _pausedState.update { _ -> paused.value }
        }
    }

    private fun setPausedState() {
        pausedState.value?.let {
            if (it) {
                pause()
            } else {
                unpause()
            }

            _pausedState.update { _ -> null }
        }
    }

    fun pauseUnpause() {
        if (paused.value) {
            unpause()
        } else {
            pause()
        }
    }

    fun pause() {
        withAttachedActivity { activity ->
            activity.withActiveMpv {
                activity.player.paused = true
            }
            activity.updatePipParamsSafely()
        }
        _paused.update { true }
    }

    fun unpause() {
        withAttachedActivity { activity ->
            activity.withActiveMpv {
                activity.player.paused = false
            }
            Unit
        }
        _paused.update { false }
    }

    fun showControls() {
        if (sheetShown.value != Sheets.None ||
            panelShown.value != Panels.None ||
            dialogShown.value != Dialogs.None
        ) {
            return
        }
        if (playerPreferences.showSystemStatusBar().get()) {
            withAttachedActivity { it.windowInsetsController.show(WindowInsetsCompat.Type.statusBars()) }
        }
        _controlsShown.update { true }
    }

    fun hideControls() {
        withAttachedActivity { it.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars()) }
        _controlsShown.update { false }
    }

    fun hideSeekBar() {
        _seekBarShown.update { false }
    }

    fun showSeekBar() {
        if (sheetShown.value != Sheets.None) return
        _seekBarShown.update { true }
    }

    fun lockControls() {
        _areControlsLocked.update { true }
    }

    fun unlockControls() {
        _areControlsLocked.update { false }
    }

    fun dismissSheet() {
        _dismissSheet.update { _ -> true }
    }

    private fun resetDismissSheet() {
        _dismissSheet.update { _ -> false }
    }

    fun showSheet(sheet: Sheets) {
        sheetShown.update { sheet }
        if (sheet == Sheets.None) {
            resetDismissSheet()
            showControls()
        } else {
            hideControls()
            panelShown.update { Panels.None }
            dialogShown.update { Dialogs.None }
        }
    }

    fun showPanel(panel: Panels) {
        panelShown.update { panel }
        if (panel == Panels.None) {
            showControls()
        } else {
            hideControls()
            sheetShown.update { Sheets.None }
            dialogShown.update { Dialogs.None }
        }
    }

    fun showDialog(dialog: Dialogs) {
        dialogShown.update { dialog }
        if (dialog == Dialogs.None) {
            showControls()
        } else {
            hideControls()
            sheetShown.update { Sheets.None }
            panelShown.update { Panels.None }
        }
    }

    fun seekBy(offset: Int, precise: Boolean = false) {
        withActiveMpv(requireNotExiting = true) {
            MPVLib.command(arrayOf("seek", offset.toString(), if (precise) "relative+exact" else "relative"))
        }
    }

    fun seekTo(position: Int, precise: Boolean = true) {
        val activity = activeActivity ?: return
        if (position !in 0..(activity.player.duration ?: 0)) return
        withActiveMpv(requireNotExiting = true) {
            MPVLib.command(arrayOf("seek", position.toString(), if (precise) "absolute" else "absolute+keyframes"))
        }
    }

    fun changeBrightnessTo(
        brightness: Float,
    ) {
        currentBrightness.update { _ -> brightness.coerceIn(-0.75f, 1f) }
        withAttachedActivity { activity ->
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = brightness.coerceIn(0f, 1f)
            }
        }
    }

    fun displayBrightnessSlider() {
        isBrightnessSliderShown.update { true }
    }

    private val audioManager: AudioManager?
        get() = activeActivity?.audioManager
            ?: appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    val maxVolume = activity.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    fun changeVolumeBy(change: Int) {
        val mpvVolume = withActiveMpv { MPVLib.getPropertyInt("volume") } ?: currentMPVVolume.value
        if (volumeBoostCap > 0 && currentVolume.value == maxVolume) {
            if (mpvVolume == 100 && change < 0) changeVolumeTo(currentVolume.value + change)
            val finalMPVVolume = (mpvVolume + change).coerceAtLeast(100)
            if (finalMPVVolume in 100..volumeBoostCap + 100) {
                changeMPVVolumeTo(finalMPVVolume)
                return
            }
        }
        changeVolumeTo(currentVolume.value + change)
    }

    fun changeVolumeTo(volume: Int) {
        val newVolume = volume.coerceIn(0..maxVolume)
        audioManager?.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVolume,
            0,
        )
        currentVolume.update { newVolume }
    }

    fun changeMPVVolumeTo(volume: Int) {
        withActiveMpv {
            MPVLib.setPropertyInt("volume", volume)
        }
    }

    fun setMPVVolume(volume: Int) {
        if (volume != currentMPVVolume.value) displayVolumeSlider()
        currentMPVVolume.update { volume }
    }

    fun displayVolumeSlider() {
        isVolumeSliderShown.update { true }
    }

    fun setAutoPlay(value: Boolean) {
        val textRes = if (value) {
            AYMR.strings.enable_auto_play
        } else {
            AYMR.strings.disable_auto_play
        }
        playerUpdate.update { PlayerUpdates.ShowTextResource(textRes) }
        playerPreferences.autoplayEnabled().set(value)
    }

    @Suppress("DEPRECATION")
    fun updateBufferingPercent(percent: Int?) {
        bufferingPercent.update { percent?.coerceIn(0, 100) }
    }

    fun updateCacheSpeed(bytesPerSec: Long) {
        cacheSpeedBytesPerSec.update { bytesPerSec.takeIf { it > 0L } }
    }

    fun setVideoZoom(zoom: Float) {
        val clamped = zoom.coerceIn(-0.4f, 1.0f)
        videoZoom.update { clamped }
        withActiveMpv {
            MPVLib.setPropertyDouble("video-zoom", clamped.toDouble())
        }
    }

    fun resetVideoZoom() {
        setVideoZoom(0f)
    }

    fun toggleRepeatEpisode() {
        val enabled = !repeatEpisode.value
        repeatEpisode.update { enabled }
        withActiveMpv {
            MPVLib.setPropertyString("loop-file", if (enabled) "inf" else "no")
        }
    }

    fun cycleAbLoop() {
        val next = abLoopState.value.advance(pos.value.toDouble())
        abLoopState.update { next }
        withActiveMpv {
            if (next.pointA != null) {
                MPVLib.setPropertyDouble("ab-loop-a", next.pointA)
            } else {
                MPVLib.setPropertyString("ab-loop-a", "no")
            }
            if (next.pointB != null) {
                MPVLib.setPropertyDouble("ab-loop-b", next.pointB)
            } else {
                MPVLib.setPropertyString("ab-loop-b", "no")
            }
        }
    }

    fun clearAbLoop() {
        abLoopState.update { it.clear() }
        withActiveMpv {
            MPVLib.setPropertyString("ab-loop-a", "no")
            MPVLib.setPropertyString("ab-loop-b", "no")
        }
    }

    fun changeVideoAspect(aspect: VideoAspect) {
        var ratio = -1.0
        var pan = 1.0
        when (aspect) {
            VideoAspect.Crop -> {
                pan = 1.0
            }

            VideoAspect.Fit -> {
                pan = 0.0
            }

            VideoAspect.Stretch -> {
                val activity = activeActivity ?: return
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.getRealMetrics(dm)
                ratio = dm.widthPixels / dm.heightPixels.toDouble()
                pan = 0.0
            }
        }
        withActiveMpv {
            MPVLib.setPropertyDouble("panscan", pan)
            if (ratio < 0.0) {
                MPVLib.setPropertyString("video-aspect-override", "no")
            } else {
                MPVLib.setPropertyDouble("video-aspect-override", ratio)
            }
        }
        if (playerPreferences.aspectState().get() != aspect) {
            playerPreferences.aspectState().set(aspect)
            playerUpdate.update { PlayerUpdates.AspectRatio }
        }
    }

    fun cycleScreenRotations() {
        withAttachedActivity { activity ->
            activity.requestedOrientation = when (activity.requestedOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                -> {
                    playerPreferences.defaultPlayerOrientationType().set(PlayerOrientation.SensorPortrait)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }

                else -> {
                    playerPreferences.defaultPlayerOrientationType().set(PlayerOrientation.SensorLandscape)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }
    }

    fun handleLuaInvocation(property: String, value: String) {
        val data = value
            .removePrefix("\"")
            .removeSuffix("\"")
            .ifEmpty { return }

        when (property.substringAfterLast("/")) {
            "show_text" -> playerUpdate.update { PlayerUpdates.ShowText(data) }
            "toggle_ui" -> {
                when (data) {
                    "show" -> showControls()
                    "toggle" -> {
                        if (controlsShown.value) hideControls() else showControls()
                    }
                    "hide" -> {
                        sheetShown.update { Sheets.None }
                        panelShown.update { Panels.None }
                        dialogShown.update { Dialogs.None }
                        hideControls()
                    }
                }
            }
            "show_panel" -> {
                when (data) {
                    "subtitle_settings" -> showPanel(Panels.SubtitleSettings)
                    "subtitle_delay" -> showPanel(Panels.SubtitleDelay)
                    "audio_delay" -> showPanel(Panels.AudioDelay)
                    "video_filters" -> showPanel(Panels.VideoFilters)
                }
            }
            "set_button_title" -> {
                _primaryButtonTitle.update { _ -> data }
            }
            "reset_button_title" -> {
                _customButtons.value.getButtons().firstOrNull { it.isFavorite }?.let {
                    setPrimaryCustomButtonTitle(it)
                }
            }
            "switch_episode" -> {
                when (data) {
                    "n" -> changeEpisode(false)
                    "p" -> changeEpisode(true)
                }
            }
            "launch_int_picker" -> {
                val (title, nameFormat, start, stop, step, pickerProperty) = data.split("|")
                val defaultValue = withActiveMpv { MPVLib.getPropertyInt(pickerProperty) } ?: 0
                showDialog(
                    Dialogs.IntegerPicker(
                        defaultValue = defaultValue,
                        minValue = start.toInt(),
                        maxValue = stop.toInt(),
                        step = step.toInt(),
                        nameFormat = nameFormat,
                        title = title,
                        onChange = {
                            withActiveMpv {
                                MPVLib.setPropertyInt(pickerProperty, it)
                            }
                        },
                        onDismissRequest = { showDialog(Dialogs.None) },
                    ),
                )
            }
            "pause" -> {
                when (data) {
                    "pause" -> pause()
                    "unpause" -> unpause()
                    "pauseunpause" -> pauseUnpause()
                }
            }
            "seek_to_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekToWithText(seekValue.toInt(), text)
            }
            "seek_by_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekByWithText(seekValue.toInt(), text)
            }
            "seek_by" -> seekByWithText(data.toInt(), null)
            "seek_to" -> seekToWithText(data.toInt(), null)
            "toggle_button" -> {
                fun showButton() {
                    if (_primaryButton.value == null) {
                        _primaryButton.update {
                            customButtons.value.getButtons().firstOrNull { it.isFavorite }
                        }
                    }
                }

                when (data) {
                    "show" -> showButton()
                    "hide" -> _primaryButton.update { null }
                    "toggle" -> if (_primaryButton.value == null) showButton() else _primaryButton.update { null }
                }
            }

            "software_keyboard" -> when (data) {
                "show" -> forceShowSoftwareKeyboard()
                "hide" -> forceHideSoftwareKeyboard()
                "toggle" -> if (inputMethodManager?.isActive == true) {
                    forceHideSoftwareKeyboard()
                } else {
                    forceShowSoftwareKeyboard()
                }
            }
        }

        withActiveMpv {
            MPVLib.setPropertyString(property, "")
        }
    }

    private operator fun <T> List<T>.component6(): T = get(5)

    private val inputMethodManager: InputMethodManager?
        get() = activeActivity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    @Suppress("DEPRECATION")
    private fun forceShowSoftwareKeyboard() {
        inputMethodManager?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    @Suppress("DEPRECATION")
    private fun forceHideSoftwareKeyboard() {
        inputMethodManager?.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }

    private val doubleTapToSeekDuration = gesturePreferences.skipLengthPreference().get()
    private val preciseSeek = gesturePreferences.playerSmoothSeek().get()
    private val showSeekBar = gesturePreferences.showSeekBar().get()

    private fun seekToWithText(seekValue: Int, text: String?) {
        _isSeekingForwards.value = seekValue > 0
        _doubleTapSeekAmount.value = seekValue - pos.value.toInt()
        _seekText.update { _ -> text }
        seekTo(seekValue, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    private fun seekByWithText(value: Int, text: String?) {
        _doubleTapSeekAmount.update {
            if ((value < 0 && it < 0) || pos.value + value > duration.value) 0 else it + value
        }
        _seekText.update { text }
        _isSeekingForwards.value = value > 0
        seekBy(value, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun updateSeekAmount(amount: Int) {
        _doubleTapSeekAmount.update { _ -> amount }
    }

    fun updateSeekText(value: String?) {
        _seekText.update { _ -> value }
    }

    fun leftSeek() {
        if (pos.value > 0) {
            _doubleTapSeekAmount.value -= doubleTapToSeekDuration
        }
        _isSeekingForwards.value = false
        seekBy(-doubleTapToSeekDuration, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun rightSeek() {
        if (pos.value < duration.value) {
            _doubleTapSeekAmount.value += doubleTapToSeekDuration
        }
        _isSeekingForwards.value = true
        seekBy(doubleTapToSeekDuration, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun resetHosterState() {
        episodeSessionId.incrementAndGet()
        cancelPlayerJobs()
        _pausedState.update { _ -> false }
        _currentVideo.update { _ -> null }
        _chapters.update { _ -> emptyList() }
        _currentChapter.update { _ -> null }
        _skipIntroText.update { _ -> null }
        clearAbLoop()
        _hosterState.update { _ -> emptyList() }
        _hosterList.update { _ -> emptyList() }
        _hosterExpandedList.update { _ -> emptyList() }
        _selectedHosterVideoIndex.update { _ -> Pair(-1, -1) }
        synchronized(autoFallbackAttempted) {
            autoFallbackAttempted.clear()
        }
        isAutoFallingBack.set(false)
        downloadAheadTriggeredForEpisodeId = null
        progressPersistencePolicy.reset()
    }

    fun prepareForReload(resetQuality: Boolean = false) {
        episodeSessionId.incrementAndGet()
        cancelPlayerJobs()
        _pausedState.update { false }
        _currentVideo.update { null }
        _chapters.update { emptyList() }
        _currentChapter.update { null }
        _skipIntroText.update { null }
        _selectedSubtitles.update { Pair(-1, -1) }
        _selectedAudio.update { -1 }
        _selectedHosterVideoIndex.update { Pair(-1, -1) }
        if (resetQuality) {
            qualityIndex = Pair(-1, -1)
        }
        isLoading.update { true }
        updateIsLoadingEpisode(true)
        isLoadingTracks.update { true }
        synchronized(autoFallbackAttempted) {
            autoFallbackAttempted.clear()
        }
        isAutoFallingBack.set(false)
    }

    fun changeEpisode(previous: Boolean, autoPlay: Boolean = false) {
        if (previous && !hasPreviousEpisode.value) {
            showToastMessage(resolveString(AYMR.strings.no_prev_episode))
            return
        }

        if (!previous && !hasNextEpisode.value) {
            showToastMessage(resolveString(AYMR.strings.no_next_episode))
            return
        }

        withAttachedActivity { activity ->
            activity.changeEpisode(
                episodeId = getAdjacentEpisodeId(previous = previous),
                autoPlay = autoPlay,
            )
        }
    }

    fun handleLeftDoubleTap() {
        when (gesturePreferences.leftDoubleTapGesture().get()) {
            SingleActionGesture.Seek -> {
                leftSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                withActiveMpv(requireNotExiting = true) {
                    MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapLeft.keyCode))
                }
            }
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> changeEpisode(true)
        }
    }

    fun handleCenterDoubleTap() {
        when (gesturePreferences.centerDoubleTapGesture().get()) {
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                withActiveMpv(requireNotExiting = true) {
                    MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapCenter.keyCode))
                }
            }
            SingleActionGesture.Seek -> {}
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> {}
        }
    }

    fun handleRightDoubleTap() {
        when (gesturePreferences.rightDoubleTapGesture().get()) {
            SingleActionGesture.Seek -> {
                rightSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                withActiveMpv(requireNotExiting = true) {
                    MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapRight.keyCode))
                }
            }
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> changeEpisode(false)
        }
    }

    override fun onCleared() {
        cancelPlayerJobs()
        foregroundIncognitoJob?.cancel()
        nextEpisodePrefetcher.cancelAndClear()
        ForegroundIncognitoState.set(this, false)
        attachedActivity = null
        if (currentEpisode.value != null) {
            saveWatchingProgress(currentEpisode.value!!)
            episodeToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
    }

    // ====== OLD ======

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    private val incognitoMode: Boolean by lazy { getIncognitoState.await(currentAnime.value?.source) }

    private fun shouldPauseHistory(): Boolean {
        return getIncognitoState.shouldPauseHistory(
            currentAnime.value?.source,
            currentAnime.value?.favorite == true,
        )
    }
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileWatching().get()

    internal val relativeTime = uiPreferences.relativeTime().get()
    internal val dateFormat = UiPreferences.dateFormat(uiPreferences.dateFormat().get())

    /**
     * The position in the current video. Used to restore from process kill.
     */
    private var episodePosition = savedState.get<Long>("episode_position")
        set(value) {
            savedState["episode_position"] = value
            field = value
        }

    /**
     * The current video's quality index. Used to restore from process kill.
     */
    private var qualityIndex = savedState.get<Pair<Int, Int>>("quality_index") ?: Pair(-1, -1)
        set(value) {
            savedState["quality_index"] = value
            field = value
        }

    /**
     * The episode id of the currently loaded episode. Used to restore from process kill.
     */
    private var episodeId = savedState.get<Long>("episode_id") ?: -1L
        set(value) {
            savedState["episode_id"] = value
            field = value
        }

    private var episodeToDownload: AnimeDownload? = null

    /**
     * The time the episode was started watching
     */
    private var episodeReadStartTime: Long? = null

    private fun filterEpisodeList(episodes: List<Episode>): List<Episode> {
        val anime = currentAnime.value ?: return episodes
        val selectedEpisode = episodes.find { it.id == episodeId }
            ?: error("Requested episode of id $episodeId not found in episode list")

        val episodesForPlayer = episodes.filterNot {
            (anime.unseenFilterRaw == Anime.EPISODE_SHOW_SEEN && !it.seen) ||
                (anime.unseenFilterRaw == Anime.EPISODE_SHOW_UNSEEN && it.seen) ||
                (
                    anime.downloadedFilterRaw == Anime.EPISODE_SHOW_DOWNLOADED &&
                        !downloadManager.isEpisodeDownloaded(
                            it.name,
                            it.scanlator,
                            anime.title,
                            anime.source,
                        )
                    ) ||
                (
                    anime.downloadedFilterRaw == Anime.EPISODE_SHOW_NOT_DOWNLOADED &&
                        downloadManager.isEpisodeDownloaded(
                            it.name,
                            it.scanlator,
                            anime.title,
                            anime.source,
                        )
                    ) ||
                (anime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_BOOKMARKED && !it.bookmark) ||
                (anime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_NOT_BOOKMARKED && it.bookmark) ||
                (anime.fillermarkedFilterRaw == Anime.EPISODE_SHOW_FILLERMARKED && !it.fillermark) ||
                (anime.fillermarkedFilterRaw == Anime.EPISODE_SHOW_NOT_FILLERMARKED && it.fillermark)
        }.toMutableList()

        if (episodesForPlayer.all { it.id != episodeId }) {
            episodesForPlayer += listOf(selectedEpisode)
        }

        return episodesForPlayer
    }

    fun getCurrentEpisodeIndex(): Int {
        return currentPlaylist.value.indexOfFirst { currentEpisode.value?.id == it.id }
    }

    private fun getAdjacentEpisodeId(previous: Boolean): Long {
        val newIndex = if (previous) getCurrentEpisodeIndex() - 1 else getCurrentEpisodeIndex() + 1

        return when {
            previous && getCurrentEpisodeIndex() == 0 -> -1L
            !previous && currentPlaylist.value.lastIndex == getCurrentEpisodeIndex() -> -1L
            else -> currentPlaylist.value.getOrNull(newIndex)?.id ?: -1L
        }
    }

    fun updateHasNextEpisode(value: Boolean) {
        _hasNextEpisode.update { _ -> value }
    }

    fun updateHasPreviousEpisode(value: Boolean) {
        _hasPreviousEpisode.update { _ -> value }
    }

    fun showEpisodeListDialog() {
        if (currentAnime.value != null) {
            showDialog(Dialogs.EpisodeList)
        }
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active episode.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        val currentEpisode = currentEpisode.value ?: return
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(currentEpisode)
        }
    }

    // ====== Initialize anime, episode, hoster, and video list ======

    fun updateIsLoadingHosters(value: Boolean) {
        _isLoadingHosters.update { _ -> value }
    }

    /**
     * Whether this viewModel is initialized with the correct episode.
     */
    private fun needsInit(animeId: Long, episodeId: Long): Boolean {
        return currentAnime.value?.id != animeId ||
            currentEpisode.value?.id != episodeId ||
            currentHosterList == null
    }

    data class InitResult(
        val hosterList: List<Hoster>?,
        val videoIndex: Pair<Int, Int>,
        val position: Long?,
    )

    private var currentHosterList: List<Hoster>? = null

    class ExceptionWithStringResource(
        message: String,
        val stringResource: StringResource,
    ) : Exception(message)

    suspend fun init(
        animeId: Long,
        initialEpisodeId: Long,
        hostList: String,
        hostIndex: Int,
        vidIndex: Int,
    ): Pair<InitResult, Result<Boolean>> {
        val defaultResult = InitResult(currentHosterList, qualityIndex, null)
        if (!needsInit(animeId, initialEpisodeId)) {
            prepareForReload(resetQuality = false)
            if (hostIndex >= 0 && vidIndex >= 0) {
                qualityIndex = Pair(hostIndex, vidIndex)
            }
            currentAnime.value?.let { anime ->
                observeForegroundIncognito(anime.source)
                val categoriesStr = getAnimeCategories.await(anime.id).joinToString { it.name }
                withActiveMpv(requireNotExiting = true) {
                    MPVLib.setPropertyString("user-data/current-anime/anime-title", anime.title)
                    MPVLib.setPropertyInt("user-data/current-anime/intro-length", getAnimeSkipIntroLength())
                    MPVLib.setPropertyString("user-data/current-anime/category", categoriesStr)
                    currentEpisode.value?.let { ep ->
                        MPVLib.setPropertyDouble("user-data/current-anime/episode-number", ep.episode_number.toDouble())
                    }
                }
            }
            return Pair(InitResult(currentHosterList, qualityIndex, episodePosition), Result.success(true))
        }
        return try {
            episodeSessionId.incrementAndGet()
            currentHosterList = null
            val anime = getAnime.await(animeId)
            if (anime != null) {
                _currentAnime.update { _ -> anime }
                observeForegroundIncognito(anime.source)
                // Taste Engine: «просмотрено» — открытие плеера помечает тайтл consumed
                // (нейтральный вес 0: вкус не трогает, тайтл уходит из ленты «Для вас»).
                // Хук на открытие, не на запись истории — работает и в инкогнито.
                markDiscoveryConsumed(anime.title, anime.source)
                animeTitle.update { _ -> anime.title }
                sourceManager.isInitialized.first { it }
                episodeId = initialEpisodeId

                checkTrackers(anime)

                updateEpisodeList(initEpisodeList(anime))

                val episode = currentPlaylist.value.first { it.id == episodeId }
                val source = sourceManager.getOrStub(anime.source)

                _currentEpisode.update { _ -> episode }
                _currentSource.update { _ -> source }

                updateEpisode(episode)

                _hasPreviousEpisode.update { _ -> getCurrentEpisodeIndex() != 0 }
                _hasNextEpisode.update { _ -> getCurrentEpisodeIndex() != currentPlaylist.value.size - 1 }

                // Write to mpv table
                val categoriesStr = getAnimeCategories.await(anime.id).joinToString { it.name }
                withActiveMpv(requireNotExiting = true) {
                    MPVLib.setPropertyString("user-data/current-anime/anime-title", anime.title)
                    MPVLib.setPropertyInt("user-data/current-anime/intro-length", getAnimeSkipIntroLength())
                    MPVLib.setPropertyString("user-data/current-anime/category", categoriesStr)
                }

                val currentEp = currentEpisode.value
                    ?: throw ExceptionWithStringResource("No episode loaded", AYMR.strings.no_episode_loaded)
                if (hostList.isNotBlank()) {
                    currentHosterList = hostList.toHosterList().ifEmpty {
                        currentHosterList = null
                        throw ExceptionWithStringResource(
                            "Hoster selected from empty list",
                            AYMR.strings.select_hoster_from_empty_list,
                        )
                    }
                    qualityIndex = Pair(hostIndex, vidIndex)
                } else {
                    EpisodeLoader.getHosters(currentEp.toDomainEpisode()!!, anime, source)
                        .takeIf { it.isNotEmpty() }
                        ?.also { currentHosterList = it }
                        ?: run {
                            currentHosterList = null
                            throw ExceptionWithStringResource("Hoster list is empty", AYMR.strings.no_hosters)
                        }
                }

                val result = InitResult(
                    hosterList = currentHosterList,
                    videoIndex = qualityIndex,
                    position = episodePosition,
                )
                Pair(result, Result.success(true))
            } else {
                // Unlikely but okay
                Pair(defaultResult, Result.success(false))
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Pair(defaultResult, Result.failure(e))
        }
    }

    private fun updateEpisode(episode: Episode) {
        mediaTitle.update { _ -> episode.name }
        _isEpisodeOnline.update { _ -> isEpisodeOnline() == true }
        withActiveMpv(requireNotExiting = true) {
            MPVLib.setPropertyDouble("user-data/current-anime/episode-number", episode.episode_number.toDouble())
        }
        episodeReadStartTime = System.currentTimeMillis()
    }

    private suspend fun initEpisodeList(anime: Anime): List<Episode> {
        val episodes = getEpisodesByAnimeId.await(anime.id)

        return episodes
            .sortedWith(getEpisodeSort(anime, sortDescending = false))
            .run {
                if (basePreferences.downloadedOnly().get()) {
                    filterDownloadedEpisodes(anime)
                } else {
                    this
                }
            }
            .map { it.toDbEpisode() }
    }

    private var hasTrackers: Boolean = false
    private suspend fun checkTrackers(anime: Anime) {
        val tracks = getTracks.await(anime.id)
        hasTrackers = tracks.isNotEmpty()
    }

    private var getHosterVideoLinksJob: Job? = null

    fun cancelHosterVideoLinksJob() {
        getHosterVideoLinksJob?.cancel()
    }

    private fun getPlaybackSelectionPreferences(anime: Anime?): PlaybackSelectionPreferences {
        if (anime == null) return PlaybackSelectionPreferences()
        return PlaybackSelectionPreferences(
            preferredPlayer = PlaybackPlayerPreference.fromPreference(
                preferenceStore.getString("anime_player_pref_${anime.id}", PlaybackPlayerPreference.AUTO.name).get(),
            ),
            preferredDubbingCdn = preferenceStore.getString("anime_dubbing_pref_cdn_${anime.id}", "").get(),
            preferredDubbingKodik = preferenceStore.getString("anime_dubbing_pref_kodik_${anime.id}", "").get(),
            preferredDubbingParlorate = preferenceStore.getString("anime_dubbing_pref_parlorate_${anime.id}", "").get(),
            preferredQualityCdn = preferenceStore.getString("anime_quality_pref_cdn_${anime.id}", "best").get(),
            preferredQualityKodik = preferenceStore.getString("anime_quality_pref_kodik_${anime.id}", "best").get(),
            preferredQualityParlorate = preferenceStore.getString(
                "anime_quality_pref_parlorate_${anime.id}",
                "best",
            ).get(),
        )
    }

    private fun hasExplicitPlaybackPreferences(preferences: PlaybackSelectionPreferences): Boolean {
        return preferences.preferredPlayer != PlaybackPlayerPreference.AUTO ||
            preferences.preferredDubbingCdn.isNotBlank() ||
            preferences.preferredDubbingKodik.isNotBlank() ||
            preferences.preferredDubbingParlorate.isNotBlank() ||
            !preferences.preferredQualityCdn.equals("best", ignoreCase = true) ||
            !preferences.preferredQualityKodik.equals("best", ignoreCase = true) ||
            !preferences.preferredQualityParlorate.equals("best", ignoreCase = true)
    }

    private fun findVideoByPlaybackPreferences(
        hosterStates: List<HosterState>,
        preferences: PlaybackSelectionPreferences,
    ): Pair<Int, Int> {
        val selection = PlaybackSelectionResolver.resolve(hosterStates.mapToHosters(), preferences)
        return when (selection) {
            is PlaybackSelection.Selected -> selection.hosterIndex to selection.videoIndex
            PlaybackSelection.None -> Pair(-1, -1)
        }
    }

    private fun findLegacyVideoByDubbingAndQuality(
        hosterStates: List<HosterState>,
        preferredDubbing: String,
        preferredQuality: String,
    ): Pair<Int, Int> {
        if (preferredDubbing.isBlank()) return Pair(-1, -1)

        val qualityOrder = listOf("1080p", "720p", "480p", "360p")
        val candidates = mutableListOf<Triple<Int, Int, Int>>()

        hosterStates.forEachIndexed { hIdx, hState ->
            if (hState is HosterState.Ready && hState.name.equals(preferredDubbing, ignoreCase = true)) {
                hState.videoList.forEachIndexed { vIdx, video ->
                    val title = video.videoTitle.lowercase()
                    val qualityRank = when {
                        preferredQuality.equals("best", ignoreCase = true) -> {
                            qualityOrder.indexOfFirst { title.contains(it.lowercase()) }.let {
                                if (it ==
                                    -1
                                ) {
                                    999
                                } else {
                                    it
                                }
                            }
                        }
                        title.contains(preferredQuality.lowercase()) -> 0
                        else -> {
                            qualityOrder.indexOfFirst { title.contains(it.lowercase()) }.let {
                                if (it ==
                                    -1
                                ) {
                                    999
                                } else {
                                    it + 1
                                }
                            }
                        }
                    }
                    candidates.add(Triple(hIdx, vIdx, qualityRank))
                }
            }
        }

        val best = candidates.minByOrNull { it.third } ?: return Pair(-1, -1)
        return best.first to best.second
    }

    private fun List<HosterState>.mapToHosters(): List<Hoster> {
        return mapNotNull { state ->
            val ready = state as? HosterState.Ready ?: return@mapNotNull null
            Hoster(
                hosterName = ready.name,
                videoList = ready.videoList,
                playerId = ready.playerId,
                playerLabel = ready.playerLabel,
                dubbingId = ready.dubbingId,
                dubbingLabel = ready.dubbingLabel,
                sortOrder = ready.sortOrder,
            )
        }
    }

    private fun filterHostersBySourcePreference(
        hosterList: List<Hoster>,
        preferences: PlaybackSelectionPreferences,
    ): List<Hoster> {
        if (preferences.preferredPlayer != PlaybackPlayerPreference.AUTO) return hosterList

        return when (playerPreferences.hosterSourceFilter().get()) {
            "cdn_only" -> hosterList.filter { hoster ->
                hoster.playerId.equals("cdn", ignoreCase = true) ||
                    hoster.hosterName.startsWith("[C]") ||
                    !hoster.hosterName.startsWith("[")
            }
            "kodik_only" -> hosterList.filter { hoster ->
                hoster.playerId.equals("kodik", ignoreCase = true) ||
                    hoster.hosterName.startsWith("[K]")
            }
            else -> hosterList
        }
    }

    /**
     * Set the video list for hosters.
     */
    fun loadHosters(source: AnimeSource, hosterList: List<Hoster>, hosterIndex: Int, videoIndex: Int) {
        val playbackPreferences = getPlaybackSelectionPreferences(_currentAnime.value)
        val filteredHosterList = filterHostersBySourcePreference(hosterList, playbackPreferences)

        logcat(LogPriority.DEBUG) {
            "loadHosters: hosterIndex=$hosterIndex, videoIndex=$videoIndex, hosterListSize=${filteredHosterList.size}"
        }
        filteredHosterList.forEachIndexed { idx, h ->
            logcat(LogPriority.DEBUG) {
                "loadHosters hoster[$idx]: name=${h.hosterName}, videoListSize=${h.videoList?.size}"
            }
        }
        val hasFoundPreferredVideo = AtomicBoolean(false)

        _hosterList.update { _ -> filteredHosterList }
        _hosterExpandedList.update { _ ->
            List(filteredHosterList.size) { true }
        }

        getHosterVideoLinksJob?.cancel()
        val sessionId = episodeSessionId.get()
        val preselectedHoster = if (hosterIndex >= 0 && videoIndex >= 0) {
            filteredHosterList.getOrNull(hosterIndex)
        } else {
            null
        }
        val preselectedVideo = preselectedHoster?.videoList?.getOrNull(videoIndex)
        val hasValidPreselection = preselectedVideo != null
        val useFastLazyStartup = !hasValidPreselection &&
            filteredHosterList.count { !it.lazy && it.videoList == null } >
            HosterStartupPlanner.EAGER_HOSTER_LOAD_THRESHOLD

        getHosterVideoLinksJob = viewModelScope.launchIO {
            _hosterState.update { current ->
                if (episodeSessionId.get() != sessionId) return@update current
                filteredHosterList.map { hoster ->
                    if (hoster.lazy || (useFastLazyStartup && hoster.videoList == null)) {
                        HosterState.Idle(
                            name = hoster.hosterName,
                            playerId = hoster.playerId,
                            playerLabel = hoster.playerLabel,
                            dubbingId = hoster.dubbingId,
                            dubbingLabel = hoster.dubbingLabel,
                            sortOrder = hoster.sortOrder,
                        )
                    } else if (hoster.videoList == null) {
                        HosterState.Loading(
                            name = hoster.hosterName,
                            playerId = hoster.playerId,
                            playerLabel = hoster.playerLabel,
                            dubbingId = hoster.dubbingId,
                            dubbingLabel = hoster.dubbingLabel,
                            sortOrder = hoster.sortOrder,
                        )
                    } else {
                        val videoList = hoster.videoList!!
                        HosterState.Ready(
                            name = hoster.hosterName,
                            videoList = videoList,
                            videoState = List(videoList.size) { Video.State.QUEUE },
                            playerId = hoster.playerId,
                            playerLabel = hoster.playerLabel,
                            dubbingId = hoster.dubbingId,
                            dubbingLabel = hoster.dubbingLabel,
                            sortOrder = hoster.sortOrder,
                        )
                    }
                }
            }

            try {
                if (hasValidPreselection && preselectedVideo != null) {
                    logcat(LogPriority.DEBUG) { "loadHosters: Using preselected video: ${preselectedVideo.videoTitle}" }
                    hasFoundPreferredVideo.set(true)
                    loadVideo(
                        source = source,
                        video = preselectedVideo,
                        hosterIndex = hosterIndex,
                        videoIndex = videoIndex,
                        expectedSessionId = sessionId,
                    )
                } else if (useFastLazyStartup) {
                    // Prioritize hosters by metadata (preferred player/dubbing) and start playback
                    // as soon as the first viable hoster resolves. Remaining hosters stay Idle
                    // (loaded on demand in QualitySheet or on runtime playback fallback) instead
                    // of blocking startup on all N hosters and allocating hundreds of MB in JS.
                    val orderedCandidates = HosterStartupPlanner.orderIndicesForStartup(
                        hosters = filteredHosterList,
                        preferences = playbackPreferences,
                    )
                    logcat(LogPriority.DEBUG) {
                        "loadHosters: fast-start mode (${filteredHosterList.size} hosters), " +
                            "order=${orderedCandidates.take(5)}"
                    }

                    var started = false
                    for (candidateIdx in orderedCandidates) {
                        ensureActive()
                        if (episodeSessionId.get() != sessionId) return@launchIO
                        val hoster = filteredHosterList[candidateIdx]
                        val readyState = when (val existing = _hosterState.value.getOrNull(candidateIdx)) {
                            is HosterState.Ready -> existing
                            else -> {
                                _hosterState.updateAt(
                                    candidateIdx,
                                    HosterState.Loading(
                                        name = hoster.hosterName,
                                        playerId = hoster.playerId,
                                        playerLabel = hoster.playerLabel,
                                        dubbingId = hoster.dubbingId,
                                        dubbingLabel = hoster.dubbingLabel,
                                        sortOrder = hoster.sortOrder,
                                    ),
                                    expectedSessionId = sessionId,
                                )
                                val loaded = EpisodeLoader.loadHosterVideos(source, hoster)
                                _hosterState.updateAt(candidateIdx, loaded, expectedSessionId = sessionId)
                                loaded as? HosterState.Ready
                            }
                        }

                        if (readyState != null && readyState.videoList.isNotEmpty()) {
                            val selectedVideoIdx = HosterStartupPlanner.selectVideoIndexForHoster(
                                hoster = readyState,
                                preferences = playbackPreferences,
                            )
                            if (selectedVideoIdx != null) {
                                val success = loadVideo(
                                    source = source,
                                    video = readyState.videoList[selectedVideoIdx],
                                    hosterIndex = candidateIdx,
                                    videoIndex = selectedVideoIdx,
                                    allowDeferredFallback = true,
                                    expectedSessionId = sessionId,
                                )
                                if (success) {
                                    hasFoundPreferredVideo.set(true)
                                    started = true
                                    break
                                }
                            }
                        }
                    }

                    if (!started) {
                        logcat(LogPriority.DEBUG) {
                            "loadHosters: fast-start exhausted all ${orderedCandidates.size} candidates"
                        }
                        throw ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos)
                    }
                } else {
                    val hosterLoadSemaphore = Semaphore(HosterLoader.MAX_CONCURRENT_HOSTER_LOADS)
                    coroutineScope {
                        filteredHosterList.mapIndexed { hosterIdx, hoster ->
                            async {
                                val hosterState = hosterLoadSemaphore.withPermit {
                                    EpisodeLoader.loadHosterVideos(source, hoster)
                                }
                                if (episodeSessionId.get() != sessionId) return@async

                                _hosterState.updateAt(hosterIdx, hosterState, expectedSessionId = sessionId)

                                if (hosterState is HosterState.Ready) {
                                    val prefIndex = hosterState.videoList.indexOfFirst { it.preferred }
                                    if (!hasExplicitPlaybackPreferences(playbackPreferences) &&
                                        prefIndex != -1 &&
                                        !hasValidPreselection
                                    ) {
                                        if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                                            if (selectedHosterVideoIndex.value == Pair(-1, -1)) {
                                                val success =
                                                    loadVideo(
                                                        source,
                                                        hosterState.videoList[prefIndex],
                                                        hosterIdx,
                                                        prefIndex,
                                                        expectedSessionId = sessionId,
                                                    )
                                                if (!success) {
                                                    hasFoundPreferredVideo.set(false)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }.awaitAll()

                        if (episodeSessionId.get() != sessionId) return@coroutineScope

                        if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                            var (hosterIdx, videoIdx) = findVideoByPlaybackPreferences(
                                hosterState.value,
                                playbackPreferences,
                            )

                            logcat(LogPriority.DEBUG) {
                                "loadHosters: playback resolver returned hosterIdx=$hosterIdx, videoIdx=$videoIdx"
                            }

                            if (hosterIdx == -1) {
                                val legacyDubbing = playbackPreferences.preferredDubbingCdn.ifBlank {
                                    playbackPreferences.preferredDubbingKodik
                                }
                                val legacyQuality = if (legacyDubbing == playbackPreferences.preferredDubbingKodik) {
                                    playbackPreferences.preferredQualityKodik
                                } else {
                                    playbackPreferences.preferredQualityCdn
                                }
                                val legacy = findLegacyVideoByDubbingAndQuality(
                                    hosterState.value,
                                    legacyDubbing,
                                    legacyQuality,
                                )
                                hosterIdx = legacy.first
                                videoIdx = legacy.second
                            }

                            if (hosterIdx == -1) {
                                val best = HosterLoader.selectBestVideo(hosterState.value)
                                hosterIdx = best.first
                                videoIdx = best.second
                                logcat(LogPriority.DEBUG) {
                                    "loadHosters: Using selectBestVideo fallback: " +
                                        "hosterIdx=$hosterIdx, videoIdx=$videoIdx"
                                }
                            }

                            if (hosterIdx == -1) {
                                logcat(LogPriority.DEBUG) {
                                    "loadHosters: no playable hoster after resolver and fallback checks; " +
                                        "hosters=${hosterState.value.size}"
                                }
                                throw ExceptionWithStringResource(
                                    "No available videos",
                                    AYMR.strings.no_available_videos,
                                )
                            }

                            val video = (hosterState.value.getOrNull(hosterIdx) as? HosterState.Ready)
                                ?.videoList?.getOrNull(videoIdx)
                                ?: throw ExceptionWithStringResource(
                                    "No available videos",
                                    AYMR.strings.no_available_videos,
                                )

                            loadVideo(source, video, hosterIdx, videoIdx, expectedSessionId = sessionId)
                        }
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "loadHosters failed" }
                if (episodeSessionId.get() == sessionId) {
                    updateIsLoadingEpisode(false)
                    isLoading.update { false }
                    updateIsLoadingHosters(false)
                    showErrorToast(e)
                }
            }
        }
    }

    private suspend fun loadVideo(
        source: AnimeSource?,
        video: Video,
        hosterIndex: Int,
        videoIndex: Int,
        resumePositionMs: Long? = null,
        allowDeferredFallback: Boolean = false,
        expectedSessionId: Long = episodeSessionId.get(),
    ): Boolean {
        if (episodeSessionId.get() != expectedSessionId) return false
        val selectedHosterState = (_hosterState.value.getOrNull(hosterIndex) as? HosterState.Ready) ?: return false
        updateIsLoadingEpisode(true)

        val oldSelectedIndex = _selectedHosterVideoIndex.value
        _selectedHosterVideoIndex.update { _ -> Pair(hosterIndex, videoIndex) }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, video, Video.State.LOAD_VIDEO),
            expectedSessionId = expectedSessionId,
        )

        // Pause until everything has loaded
        updatePausedState()
        pause()

        val resolvedVideo = if (selectedHosterState.videoState.getOrNull(videoIndex) != Video.State.READY) {
            HosterLoader.getResolvedVideo(source, video)
        } else {
            video
        }

        if (episodeSessionId.get() != expectedSessionId) return false

        if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
            logcat(LogPriority.DEBUG) {
                "loadVideo: resolve failed " +
                    "hosterIndex=$hosterIndex " +
                    "videoIndex=$videoIndex " +
                    "hoster=${selectedHosterState.name} " +
                    "title=${video.videoTitle} " +
                    "url=${video.videoUrl.take(180)} " +
                    "initialized=${video.initialized} " +
                    "currentVideoSet=${currentVideo.value != null}"
            }
            if (currentVideo.value == null) {
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                    expectedSessionId = expectedSessionId,
                )

                val (newHosterIdx, newVideoIdx) = HosterLoader.selectBestVideo(hosterState.value)
                if (newHosterIdx == -1) {
                    logcat(LogPriority.DEBUG) {
                        "loadVideo: no fallback videos after resolve failure; " +
                            "loadingHosters=${_hosterState.value.count { it is HosterState.Loading }}"
                    }
                    if (allowDeferredFallback || _hosterState.value.any { it is HosterState.Loading }) {
                        _selectedHosterVideoIndex.update { _ -> Pair(-1, -1) }
                        return false
                    } else {
                        logcat(LogPriority.DEBUG) {
                            "loadVideo: throwing No available videos after all hosters were exhausted"
                        }
                        throw ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos)
                    }
                }

                val newVideo = (hosterState.value.getOrNull(newHosterIdx) as? HosterState.Ready)
                    ?.videoList?.getOrNull(newVideoIdx)
                    ?: return false

                return loadVideo(
                    source = source,
                    video = newVideo,
                    hosterIndex = newHosterIdx,
                    videoIndex = newVideoIdx,
                    expectedSessionId = expectedSessionId,
                )
            } else {
                _selectedHosterVideoIndex.update { _ -> oldSelectedIndex }
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                    expectedSessionId = expectedSessionId,
                )
                return false
            }
        }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, resolvedVideo, Video.State.READY),
            expectedSessionId = expectedSessionId,
        )

        _currentVideo.update { _ -> resolvedVideo }

        qualityIndex = Pair(hosterIndex, videoIndex)

        withAttachedActivity { it.setVideo(resolvedVideo, position = resumePositionMs) }
        return true
    }

    /**
     * Attempts to automatically switch to the next viable video/hoster after a runtime MPV error.
     * Returns true if a fallback attempt was scheduled (or already in progress), false if no
     * fallback is available and the caller should surface the error directly.
     */
    internal fun tryFallbackAfterPlaybackError(errorMessage: String): Boolean {
        if (isEpisodeOnline() != true) return false

        val failedIndex = _selectedHosterVideoIndex.value
        if (failedIndex.first < 0 || failedIndex.second < 0) return false

        if (!isAutoFallingBack.compareAndSet(false, true)) {
            return true
        }

        val sessionId = episodeSessionId.get()
        val failedHosterState = _hosterState.value.getOrNull(failedIndex.first) as? HosterState.Ready
        val failedVideo = failedHosterState?.videoList?.getOrNull(failedIndex.second)
        if (failedHosterState != null && failedVideo != null) {
            _hosterState.updateAt(
                failedIndex.first,
                failedHosterState.getChangedAt(failedIndex.second, failedVideo, Video.State.ERROR),
                expectedSessionId = sessionId,
            )
        }

        val attemptedSnapshot = synchronized(autoFallbackAttempted) {
            autoFallbackAttempted.add(failedIndex)
            autoFallbackAttempted.toSet()
        }

        val nextIndex = PlaybackFallbackSelector.selectNext(
            hosterStates = _hosterState.value,
            failedIndex = failedIndex,
            attemptedIndices = attemptedSnapshot,
        )
        val resumePositionMs = (pos.value * 1000f).toLong().takeIf { it > 0L }

        if (nextIndex != null) {
            val nextHoster = _hosterState.value.getOrNull(nextIndex.first) as? HosterState.Ready
            val nextVideo = nextHoster?.videoList?.getOrNull(nextIndex.second)
            if (nextHoster == null || nextVideo == null) {
                isAutoFallingBack.set(false)
                return false
            }

            playerUpdate.update {
                PlayerUpdates.ShowText("${nextHoster.name}: ${nextVideo.videoTitle}")
            }

            fallbackJob?.cancel()
            fallbackJob = viewModelScope.launchIO {
                try {
                    val switched = loadVideo(
                        source = currentSource.value,
                        video = nextVideo,
                        hosterIndex = nextIndex.first,
                        videoIndex = nextIndex.second,
                        resumePositionMs = resumePositionMs,
                        expectedSessionId = sessionId,
                    )
                    if (!switched && episodeSessionId.get() == sessionId) {
                        updateIsLoadingEpisode(false)
                        isLoading.update { false }
                        withUIContext { showToastMessage(errorMessage) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Auto-fallback failed after playback error: $errorMessage" }
                    if (episodeSessionId.get() == sessionId) {
                        updateIsLoadingEpisode(false)
                        isLoading.update { false }
                        withUIContext { showToastMessage(errorMessage) }
                    }
                } finally {
                    isAutoFallingBack.set(false)
                }
            }
            return true
        }

        // Fast-start mode keeps non-primary hosters Idle until needed; load the next untried
        // Idle hoster on demand when runtime playback fails.
        if (attemptedSnapshot.size >= PlaybackFallbackSelector.MAX_AUTO_FALLBACK_ATTEMPTS) {
            isAutoFallingBack.set(false)
            return false
        }
        val excludedHosters = attemptedSnapshot.map { it.first }.toSet() + failedIndex.first
        val nextIdleHosterIdx = HosterStartupPlanner.selectNextIdleHosterIndex(
            hosterStates = _hosterState.value,
            excludedHosterIndices = excludedHosters,
        )
        val source = currentSource.value
        val idleHoster = nextIdleHosterIdx?.let { hosterList.value.getOrNull(it) }
        if (nextIdleHosterIdx == null || source == null || idleHoster == null) {
            isAutoFallingBack.set(false)
            return false
        }

        val playbackPreferences = getPlaybackSelectionPreferences(_currentAnime.value)
        fallbackJob?.cancel()
        fallbackJob = viewModelScope.launchIO {
            try {
                _hosterState.updateAt(
                    nextIdleHosterIdx,
                    HosterState.Loading(
                        name = idleHoster.hosterName,
                        playerId = idleHoster.playerId,
                        playerLabel = idleHoster.playerLabel,
                        dubbingId = idleHoster.dubbingId,
                        dubbingLabel = idleHoster.dubbingLabel,
                        sortOrder = idleHoster.sortOrder,
                    ),
                    expectedSessionId = sessionId,
                )
                val loaded = EpisodeLoader.loadHosterVideos(
                    source = source,
                    hoster = idleHoster,
                    force = true,
                )
                if (episodeSessionId.get() != sessionId) return@launchIO
                _hosterState.updateAt(nextIdleHosterIdx, loaded, expectedSessionId = sessionId)

                val ready = loaded as? HosterState.Ready
                val nextVideoIdx = ready?.let {
                    HosterStartupPlanner.selectVideoIndexForHoster(it, playbackPreferences)
                }
                val nextVideo = if (ready != null && nextVideoIdx != null) {
                    ready.videoList.getOrNull(nextVideoIdx)
                } else {
                    null
                }

                if (ready != null && nextVideoIdx != null && nextVideo != null) {
                    playerUpdate.update {
                        PlayerUpdates.ShowText("${ready.name}: ${nextVideo.videoTitle}")
                    }
                    val switched = loadVideo(
                        source = source,
                        video = nextVideo,
                        hosterIndex = nextIdleHosterIdx,
                        videoIndex = nextVideoIdx,
                        resumePositionMs = resumePositionMs,
                        allowDeferredFallback = true,
                        expectedSessionId = sessionId,
                    )
                    if (!switched && episodeSessionId.get() == sessionId) {
                        updateIsLoadingEpisode(false)
                        isLoading.update { false }
                        withUIContext { showToastMessage(errorMessage) }
                    }
                } else if (episodeSessionId.get() == sessionId) {
                    updateIsLoadingEpisode(false)
                    isLoading.update { false }
                    withUIContext { showToastMessage(errorMessage) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "On-demand auto-fallback failed after playback error: $errorMessage" }
                if (episodeSessionId.get() == sessionId) {
                    updateIsLoadingEpisode(false)
                    isLoading.update { false }
                    withUIContext { showToastMessage(errorMessage) }
                }
            } finally {
                isAutoFallingBack.set(false)
            }
        }
        return true
    }

    fun onVideoClicked(hosterIndex: Int, videoIndex: Int) {
        val hosterState = _hosterState.value.getOrNull(hosterIndex) as? HosterState.Ready
        val video = hosterState?.videoList
            ?.getOrNull(videoIndex)
            ?: return // Shouldn't happen, but just in case™

        val videoState = hosterState.videoState
            .getOrNull(videoIndex)
            ?: return

        if (videoState == Video.State.ERROR) {
            return
        }

        val sessionId = episodeSessionId.get()
        hosterActionJob?.cancel()
        hosterActionJob = viewModelScope.launchIO {
            try {
                val success = loadVideo(
                    source = currentSource.value,
                    video = video,
                    hosterIndex = hosterIndex,
                    videoIndex = videoIndex,
                    expectedSessionId = sessionId,
                )
                if (episodeSessionId.get() != sessionId) return@launchIO
                if (success) {
                    if (sheetShown.value == Sheets.QualityTracks) {
                        dismissSheet()
                    }
                } else {
                    updateIsLoadingEpisode(false)
                    isLoading.update { false }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "onVideoClicked failed" }
                if (episodeSessionId.get() == sessionId) {
                    updateIsLoadingEpisode(false)
                    isLoading.update { false }
                    updateIsLoadingHosters(false)
                    showErrorToast(e)
                }
            }
        }
    }

    fun onHosterClicked(index: Int) {
        val currentState = hosterState.value.getOrNull(index) ?: return
        when (currentState) {
            is HosterState.Ready -> {
                val expanded = _hosterExpandedList.value.getOrNull(index) ?: return
                _hosterExpandedList.updateAt(index, !expanded)
            }
            is HosterState.Idle, is HosterState.Error -> {
                val source = currentSource.value ?: return
                val hoster = hosterList.value.getOrNull(index) ?: return
                val sessionId = episodeSessionId.get()
                _hosterState.updateAt(
                    index,
                    HosterState.Loading(
                        name = hoster.hosterName,
                        playerId = hoster.playerId,
                        playerLabel = hoster.playerLabel,
                        dubbingId = hoster.dubbingId,
                        dubbingLabel = hoster.dubbingLabel,
                        sortOrder = hoster.sortOrder,
                    ),
                    expectedSessionId = sessionId,
                )

                val job = viewModelScope.launchIO {
                    try {
                        val loaded = EpisodeLoader.loadHosterVideos(
                            source = source,
                            hoster = hoster,
                            force = true,
                        )
                        if (episodeSessionId.get() != sessionId) return@launchIO
                        _hosterState.updateAt(index, loaded, expectedSessionId = sessionId)
                        if (loaded is HosterState.Ready) {
                            _hosterExpandedList.updateAt(index, true, expectedSessionId = sessionId)
                        }
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.ERROR, e) { "onHosterClicked failed" }
                    }
                }
                synchronized(hosterLoadJobs) {
                    hosterLoadJobs.removeAll { !it.isActive }
                    hosterLoadJobs.add(job)
                }
            }
            is HosterState.Loading -> {}
        }
    }

    private fun <T> MutableStateFlow<List<T>>.updateAt(
        index: Int,
        newValue: T,
        expectedSessionId: Long? = null,
    ) {
        this.update { values ->
            if (expectedSessionId != null && episodeSessionId.get() != expectedSessionId) {
                return@update values
            }
            if (index !in values.indices) {
                return@update values
            }
            values.toMutableList().apply {
                this[index] = newValue
            }
        }
    }

    data class EpisodeLoadResult(
        val hosterList: List<Hoster>?,
        val episodeTitle: String,
        val source: AnimeSource,
    )

    suspend fun loadEpisode(episodeId: Long?): EpisodeLoadResult? {
        val anime = currentAnime.value ?: return null
        val source = sourceManager.getOrStub(anime.source)

        val chosenEpisode = currentPlaylist.value.firstOrNull { ep -> ep.id == episodeId } ?: return null

        val sessionId = episodeSessionId.incrementAndGet()
        currentHosterList = null
        _currentEpisode.update { _ -> chosenEpisode }
        updateEpisode(chosenEpisode)

        return withIOContext {
            try {
                val currentEpisode =
                    currentEpisode.value
                        ?: throw ExceptionWithStringResource("No episode loaded", AYMR.strings.no_episode_loaded)
                val targetEpisodeId = currentEpisode.id!!
                val loadedHosters = nextEpisodePrefetcher.consumeOrNull(targetEpisodeId)
                    ?: EpisodeLoader.getHosters(
                        currentEpisode.toDomainEpisode()!!,
                        anime,
                        source,
                    )
                if (episodeSessionId.get() != sessionId) {
                    throw CancellationException("Episode session changed")
                }
                currentHosterList = loadedHosters

                this@PlayerViewModel.episodeId = targetEpisodeId
            } catch (e: Throwable) {
                // Extension code may throw linkage Errors (e.g. NoSuchMethodError on
                // ABI drift); degrade to an empty episode load result, not a crash.
                if (e is CancellationException) throw e
                currentHosterList = null
                logcat(LogPriority.ERROR, e) { e.message ?: "Error getting links" }
            }

            EpisodeLoadResult(
                hosterList = currentHosterList,
                episodeTitle = anime.title + " - " + chosenEpisode.name,
                source = source,
            )
        }
    }

    /**
     * Called every time a second is reached in the player. Used to mark the flag of episode being
     * seen, update tracking services, enqueue downloaded episode deletion and download next episode.
     */
    private fun onSecondReached(position: Int, duration: Int) {
        if (isLoadingEpisode.value) return
        val currentEp = currentEpisode.value ?: return
        if (episodeId == -1L) return
        if (duration == 0) return

        val seconds = position * 1000L
        val totalSeconds = duration * 1000L
        // Save last second seen and mark as seen if needed
        currentEp.last_second_seen = seconds
        currentEp.total_seconds = totalSeconds

        episodePosition = seconds

        val progress = playerPreferences.progressPreference().get()
        val shouldTrack = !incognitoMode || hasTrackers
        val reachedCompletion = !currentEp.seen && seconds >= totalSeconds * progress && shouldTrack
        if (reachedCompletion) {
            viewModelScope.launchNonCancellable {
                updateEpisodeProgressOnComplete(currentEp)
            }
        }

        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (
            reachedCompletion ||
            progressPersistencePolicy.shouldPersist(
                episodeId = episodeId,
                positionMs = seconds,
                nowElapsedMs = nowElapsedMs,
            )
        ) {
            progressPersistencePolicy.recordPersisted(
                episodeId = episodeId,
                positionMs = seconds,
                nowElapsedMs = nowElapsedMs,
            )
            saveWatchingProgress(currentEp)
        }

        val watchedRatio = seconds.toDouble() / totalSeconds
        if (watchedRatio > 0.35 && downloadAheadTriggeredForEpisodeId != episodeId) {
            downloadAheadTriggeredForEpisodeId = episodeId
            downloadNextEpisodes()
        }

        if (watchedRatio >= NextEpisodePrefetcher.PREFETCH_PROGRESS_THRESHOLD) {
            prefetchNextEpisodeHosters()
        }
    }

    private fun prefetchNextEpisodeHosters() {
        val anime = currentAnime.value ?: return
        val currentIdx = getCurrentEpisodeIndex()
        if (currentIdx < 0 || currentIdx >= currentPlaylist.value.lastIndex) return
        val nextEpisode = currentPlaylist.value.getOrNull(currentIdx + 1) ?: return
        val nextEpisodeId = nextEpisode.id ?: return
        val domainEpisode = nextEpisode.toDomainEpisode() ?: return
        val source = sourceManager.getOrStub(anime.source)
        nextEpisodePrefetcher.prefetchIfNeeded(
            scope = viewModelScope,
            episodeId = nextEpisodeId,
        ) {
            EpisodeLoader.getHosters(domainEpisode, anime, source)
        }
    }

    private suspend fun updateEpisodeProgressOnComplete(currentEp: Episode) {
        if (currentEp.seen) return

        currentEp.seen = true

        // Emit EpisodeWatched event for achievement tracking only once, when the episode
        // actually transitions from unseen to seen. This method is called every second after
        // the configured completion threshold, so recording unconditionally here inflates the
        // "episodes watched" stats on the achievements screen.
        val animeId = currentAnime.value?.id ?: 0L
        eventBus.tryEmit(
            AchievementEvent.EpisodeWatched(
                animeId = animeId,
                episodeNumber = currentEp.episode_number.toInt(),
            ),
        )
        runCatching {
            if (eu.kanade.domain.easteregg.aurora.AuroraNight.isVeilThin()) {
                val manager = Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>()
                manager.registerNightAction()
            }
        }

        // Record watching activity for stats
        val episodeId = currentEp.id ?: 0L
        if (episodeId > 0) {
            activityDataRepository.recordWatching(
                id = episodeId,
                episodesCount = 1,
                durationMs = episodeReadStartTime?.let { System.currentTimeMillis() - it } ?: 0L,
            )
        }

        // Check for anime completion
        val allEpisodes = currentPlaylist.value
        if (allEpisodes.all { it.seen }) {
            eventBus.tryEmit(AchievementEvent.AnimeCompleted(animeId))
            recordAnimeCompletionIfNeeded(currentEp, allEpisodes)
        }

        updateTrackEpisodeSeen(currentEp)
        deleteEpisodeIfNeeded(currentEp)

        val markDuplicateAsSeen = libraryPreferences.markDuplicateSeenEpisodeAsSeen().get()
            .contains(LibraryPreferences.MARK_DUPLICATE_EPISODE_SEEN_EXISTING)
        if (!markDuplicateAsSeen) return

        val duplicateUnseenEpisodes = currentPlaylist.value
            .mapNotNull { episode ->
                if (
                    !episode.seen &&
                    episode.isRecognizedNumber &&
                    episode.episode_number == currentEp.episode_number
                ) {
                    EpisodeUpdate(id = episode.id!!, seen = true)
                } else {
                    null
                }
            }
        updateEpisode.awaitAll(duplicateUnseenEpisodes)
    }

    /**
     * Persists the first-witnessed completion timestamp (keepsake for the title-screen
     * finished stamp; travels through backups). Independent of any celebration UI.
     * The caller's `if (currentEp.seen) return` guard makes this run exactly once per
     * unseen→seen transition, so `episodeWasUnseen` is trivially true here.
     */
    private fun recordAnimeCompletionIfNeeded(currentEp: Episode, episodes: List<Episode>) {
        val currentAnime = currentAnime.value ?: return
        if (!shouldRecordAnimeCompletion(
                currentAnime,
                episodes,
                finishedEpisodeIsLast = currentEp.id == episodes.lastOrNull()?.id,
            )
        ) {
            return
        }
        val timestamp = System.currentTimeMillis()
        viewModelScope.launchNonCancellable {
            updateAnime.await(AnimeUpdate(id = currentAnime.id, completedAt = timestamp))
        }
    }

    private fun downloadNextEpisodes() {
        if (downloadAheadAmount == 0) return
        val anime = currentAnime.value ?: return

        // Only download ahead if current + next episode is already downloaded too to avoid jank
        val currentIdx = getCurrentEpisodeIndex()
        if (currentIdx < 0 || currentIdx >= currentPlaylist.value.lastIndex) return
        val currentDomainEpisode = currentEpisode.value?.toDomainEpisode() ?: return
        val nextEpisode = currentPlaylist.value.getOrNull(currentIdx + 1) ?: return
        val nextDomainEpisode = nextEpisode.toDomainEpisode() ?: return
        val nextEpisodeId = nextEpisode.id ?: return

        viewModelScope.launchIO {
            val episodesAreDownloaded =
                EpisodeLoader.isDownload(currentDomainEpisode, anime) &&
                    EpisodeLoader.isDownload(nextDomainEpisode, anime)
            if (!episodesAreDownloaded) {
                return@launchIO
            }
            val episodesToDownload = getNextEpisodes.await(anime.id, nextEpisodeId)
                .take(downloadAheadAmount)
            downloadManager.downloadEpisodes(anime, episodesToDownload)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last episode actually exists.
     * If both conditions are satisfied enqueues episode for delete
     * @param chosenEpisode current episode, which is going to be marked as seen.
     */
    private fun deleteEpisodeIfNeeded(chosenEpisode: Episode) {
        // Determine which episode should be deleted and enqueue
        val currentEpisodePosition = currentPlaylist.value.indexOf(chosenEpisode)
        val removeAfterSeenSlots = downloadPreferences.removeAfterReadSlots().get()
        val episodeToDelete = currentPlaylist.value.getOrNull(
            currentEpisodePosition - removeAfterSeenSlots,
        )
        // If episode is completely seen no need to download it
        episodeToDownload = null

        // Check if deleting option is enabled and episode exists
        if (removeAfterSeenSlots != -1 && episodeToDelete != null) {
            enqueueDeleteSeenEpisodes(episodeToDelete)
        }
    }

    fun saveCurrentEpisodeWatchingProgress() {
        currentEpisode.value?.let { ep ->
            ep.id?.let { id ->
                progressPersistencePolicy.recordPersisted(
                    episodeId = id,
                    positionMs = ep.last_second_seen,
                    nowElapsedMs = SystemClock.elapsedRealtime(),
                )
            }
            saveWatchingProgress(ep)
        }
    }

    /**
     * Called when episode is changed in player or when activity is paused.
     */
    private fun saveWatchingProgress(episode: Episode) {
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(episode)
            saveEpisodeHistory(episode)
        }
    }

    /**
     * Saves this [episode] progress (last second seen and whether it's seen).
     * If incognito mode isn't on or has at least 1 tracker
     */
    private suspend fun saveEpisodeProgress(episode: Episode) {
        if (!shouldPauseHistory() || hasTrackers) {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episode.id!!,
                    seen = episode.seen,
                    bookmark = episode.bookmark,
                    fillermark = episode.fillermark,
                    lastSecondSeen = episode.last_second_seen,
                    totalSeconds = episode.total_seconds,
                ),
            )
        }
    }

    /**
     * Saves this [episode] last seen history if incognito mode isn't on.
     */
    private suspend fun saveEpisodeHistory(episode: Episode) {
        if (!shouldPauseHistory()) {
            val episodeId = episode.id!!
            val seenAt = Date()
            upsertHistory.await(
                AnimeHistoryUpdate(episodeId, seenAt),
            )
        }
    }

    /**
     * Bookmarks the currently active episode.
     */
    fun bookmarkEpisode(episodeId: Long?, bookmarked: Boolean) {
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId!!,
                    bookmark = bookmarked,
                ),
            )
        }
    }

    /**
     * Fillermarks the currently active episode.
     */
    fun fillermarkEpisode(episodeId: Long?, fillermarked: Boolean) {
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId!!,
                    fillermark = fillermarked,
                ),
            )
        }
    }

    fun takeScreenshot(cachePath: String, showSubtitles: Boolean): InputStream? {
        val filename = cachePath + "/${System.currentTimeMillis()}_mpv_screenshot_tmp.png"
        val subtitleFlag = if (showSubtitles) "subtitles" else "video"

        withActiveMpv {
            MPVLib.command(arrayOf("screenshot-to-file", filename, subtitleFlag))
        }
        val tempFile = File(filename).takeIf { it.exists() } ?: return null
        val newFile = File("$cachePath/mpv_screenshot.png")

        newFile.delete()
        tempFile.renameTo(newFile)
        return newFile.takeIf { it.exists() }?.inputStream()
    }

    /**
     * Saves the screenshot on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(imageStream: () -> InputStream, timePos: Int?) {
        val anime = currentAnime.value ?: return

        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(anime, seconds) ?: return

        // Pictures directory.
        val relativePath = DiskUtil.buildValidFilename(anime.title)

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Pictures(relativePath),
                    ),
                )
                notifier.onComplete(uri)
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
            } catch (e: Throwable) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    /**
     * Shares the screenshot and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(imageStream: () -> InputStream, timePos: Int?) {
        val anime = currentAnime.value ?: return

        val context = Injekt.get<Application>()
        val destDir = context.cacheImageDir

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(anime, seconds) ?: return

        try {
            viewModelScope.launchIO {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(Event.ShareImage(uri, seconds))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    /**
     * Sets the screenshot as art and notifies the UI of the result.
     */
    fun setAsArt(artType: ArtType, imageStream: () -> InputStream) {
        val anime = currentAnime.value ?: return
        val episode = currentEpisode.value ?: return

        viewModelScope.launchNonCancellable {
            val result = try {
                when (artType) {
                    ArtType.Cover -> anime.editCover(Injekt.get(), imageStream())
                    ArtType.Background -> anime.editBackground(Injekt.get(), imageStream())
                    ArtType.Thumbnail -> episode.editThumbnail(anime, Injekt.get(), imageStream())
                }

                if (anime.isLocal() || anime.favorite) {
                    SetAsArt.Success
                } else {
                    SetAsArt.AddToLibraryFirst
                }
            } catch (e: Exception) {
                SetAsArt.Error
            }
            eventChannel.send(Event.SetArtResult(result, artType))
        }
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    private fun updateTrackEpisodeSeen(episode: Episode) {
        if (incognitoMode || !hasTrackers) return
        if (!trackPreferences.autoUpdateTrack().get()) return

        val anime = currentAnime.value ?: return
        val context = Injekt.get<Application>()

        viewModelScope.launchNonCancellable {
            trackEpisode.await(context, anime.id, episode.episode_number.toDouble())
        }
    }

    /**
     * Enqueues this [episode] to be deleted when [deletePendingEpisodes] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteSeenEpisodes(episode: Episode) {
        if (!episode.seen) return
        val anime = currentAnime.value ?: return
        viewModelScope.launchNonCancellable {
            downloadManager.enqueueEpisodesToDelete(listOf(episode.toDomainEpisode()!!), anime)
        }
    }

    /**
     * Deletes all the pending episodes. This operation will run in a background thread and errors
     * are ignored.
     */
    fun deletePendingEpisodes() {
        viewModelScope.launchNonCancellable {
            downloadManager.deletePendingEpisodes()
        }
    }

    /**
     * Returns the skipIntroLength used by this anime or the default one.
     */
    fun getAnimeSkipIntroLength(): Int {
        val default = gesturePreferences.defaultIntroLength().get()
        val anime = currentAnime.value ?: return default
        val skipIntroLength = anime.skipIntroLength
        val skipIntroDisable = anime.skipIntroDisable
        return when {
            skipIntroDisable -> 0
            skipIntroLength <= 0 -> default
            else -> anime.skipIntroLength
        }
    }

    /**
     * Updates the skipIntroLength for the open anime.
     */
    fun setAnimeSkipIntroLength(skipIntroLength: Long) {
        val anime = currentAnime.value ?: return
        if (!anime.favorite) return
        // Skip unnecessary database operation
        if (skipIntroLength == getAnimeSkipIntroLength().toLong()) return
        viewModelScope.launchIO {
            setAnimeViewerFlags.awaitSetSkipIntroLength(anime.id, skipIntroLength)
            _currentAnime.update { _ -> getAnime.await(anime.id) }
        }
    }

    /**
     * Generate a filename for the given [anime] and [timePos]
     */
    private fun generateFilename(
        anime: Anime,
        timePos: String,
    ): String? {
        val episode = currentEpisode.value ?: return null
        val filenameSuffix = " - $timePos"
        return DiskUtil.buildValidFilename(
            "${anime.title} - ${episode.name}".takeBytes(
                DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
            ),
        ) + filenameSuffix
    }

    /**
     * Returns the response of the AniSkipApi for this episode.
     * just works if tracking is enabled.
     */
    suspend fun aniSkipResponse(playerDuration: Int?): List<TimeStamp>? {
        val animeId = currentAnime.value?.id ?: return null
        val duration = playerDuration ?: return null
        val episodeNumber = currentEpisode.value?.episode_number?.toInt() ?: return null
        val tracks = getTracks.await(animeId)
        if (tracks.isEmpty()) {
            logcat { "AniSkip: No tracks found for anime $animeId" }
            return null
        }

        val trackerManager = Injekt.get<TrackerManager>()
        val aniSkipApi = AniSkipApi(clientProvider = { networkHelper.client })

        return withIOContext {
            for (track in tracks) {
                val tracker = trackerManager.get(track.trackerId)
                val malId = when (tracker) {
                    is MyAnimeList -> track.remoteId
                    is Anilist -> aniSkipApi.getMalIdFromAL(track.remoteId)
                    else -> null
                }?.takeIf { it > 0L } ?: continue

                val result = aniSkipApi.getResult(malId.toInt(), episodeNumber, duration.toLong())
                if (!result.isNullOrEmpty()) {
                    return@withIOContext result
                }
            }
            null
        }
    }

    val introSkipEnabled = playerPreferences.enableSkipIntro().get()
    private val autoSkip = playerPreferences.autoSkipIntro().get()
    private val netflixStyle = playerPreferences.enableNetflixStyleIntroSkip().get()

    private val defaultWaitingTime = playerPreferences.waitingTimeIntroSkip().get()
    var waitingSkipIntro = defaultWaitingTime

    fun setChapter(position: Float) {
        getCurrentChapter(position)?.let { (chapterIndex, chapter) ->
            if (currentChapter.value != chapter) {
                _currentChapter.update { _ -> chapter }
            }

            if (!introSkipEnabled) {
                return
            }

            if (chapter.chapterType == ChapterType.Other) {
                _skipIntroText.update { _ -> null }
                waitingSkipIntro = defaultWaitingTime
            } else {
                val nextChapterPos = PlaybackProgressPersistencePolicy.resolveChapterSkipTarget(
                    chapters = chapters.value,
                    chapterIndex = chapterIndex,
                    currentPosition = pos.value,
                    duration = duration.value,
                )

                if (netflixStyle) {
                    // show a toast with the seconds before the skip
                    if (waitingSkipIntro == defaultWaitingTime) {
                        showToastMessage(
                            "Skip Intro: ${resolveString(
                                AYMR.strings.player_aniskip_dontskip_toast,
                                chapter.name,
                                waitingSkipIntro,
                            )}",
                        )
                    }
                    showSkipIntroButton(chapter, nextChapterPos, waitingSkipIntro)
                    waitingSkipIntro--
                } else if (autoSkip) {
                    seekToWithText(
                        seekValue = nextChapterPos.toInt(),
                        text = resolveString(AYMR.strings.player_intro_skipped, chapter.name),
                    )
                } else {
                    updateSkipIntroButton(chapter.chapterType)
                }
            }
        }
    }

    private fun updateSkipIntroButton(chapterType: ChapterType) {
        val skipButtonString = chapterType.getStringRes()

        _skipIntroText.update { _ ->
            skipButtonString?.let {
                resolveString(
                    AYMR.strings.player_skip_action,
                    resolveString(skipButtonString),
                )
            }
        }
    }

    private fun showSkipIntroButton(chapter: IndexedSegment, nextChapterPos: Float, waitingTime: Int) {
        if (waitingTime > -1) {
            if (waitingTime > 0) {
                _skipIntroText.update { _ -> resolveString(AYMR.strings.player_aniskip_dontskip) }
            } else {
                seekToWithText(
                    seekValue = nextChapterPos.toInt(),
                    text = resolveString(AYMR.strings.player_aniskip_skip, chapter.name),
                )
            }
        } else {
            // when waitingTime is -1, it means that the user cancelled the skip
            updateSkipIntroButton(chapter.chapterType)
        }
    }

    fun onSkipIntro() {
        getCurrentChapter()?.let { (chapterIndex, chapter) ->
            // this stops the counter
            if (waitingSkipIntro > 0 && netflixStyle) {
                waitingSkipIntro = -1
                return
            }

            val nextChapterPos = PlaybackProgressPersistencePolicy.resolveChapterSkipTarget(
                chapters = chapters.value,
                chapterIndex = chapterIndex,
                currentPosition = pos.value,
                duration = duration.value,
            )

            seekToWithText(
                seekValue = nextChapterPos.toInt(),
                text = resolveString(AYMR.strings.player_aniskip_skip, chapter.name),
            )
        }
    }

    private fun getCurrentChapter(position: Float? = null): IndexedValue<IndexedSegment>? {
        return chapters.value.withIndex()
            .filter { it.value.start <= (position ?: pos.value) }
            .maxByOrNull { it.value.start }
    }

    fun setPrimaryCustomButtonTitle(button: CustomButton) {
        _primaryButtonTitle.update { _ -> button.name }
    }

    sealed class Event {
        data class SetArtResult(val result: SetAsArt, val artType: ArtType) : Event()
        data class SavedImage(val result: SaveImageResult) : Event()
        data class ShareImage(val uri: Uri, val seconds: String) : Event()
    }
}

fun CustomButton.execute() {
    MPVLib.command(arrayOf("script-message", "call_button_$id"))
}

fun CustomButton.executeLongPress() {
    MPVLib.command(arrayOf("script-message", "call_button_${id}_long"))
}

fun Float.normalize(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
    return (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
}
