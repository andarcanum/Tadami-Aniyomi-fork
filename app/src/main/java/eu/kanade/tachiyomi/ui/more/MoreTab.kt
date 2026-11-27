package eu.kanade.tachiyomi.ui.more

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.tadami.aurora.BuildConfig
import com.tadami.aurora.R
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.achievement.screen.AchievementScreenVoyager
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.presentation.more.MoreScreenAurora
import eu.kanade.presentation.more.settings.screen.HelpScreen
import eu.kanade.presentation.more.settings.screen.SettingsNovelReaderScreen
import eu.kanade.presentation.more.settings.screen.SettingsReaderScreen
import eu.kanade.presentation.more.settings.screen.SettingsTreasuryScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.download.DownloadsTab
import eu.kanade.tachiyomi.ui.library.novel.quotes.NovelQuotesLibraryScreen
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.tachiyomi.ui.more.DebugAppUpdatePreviewScreen
import eu.kanade.tachiyomi.ui.more.DebugUpdatedChangelogPreviewScreen
import eu.kanade.tachiyomi.ui.reels.ReelsFeedScreen
import eu.kanade.tachiyomi.ui.reels.ReelsSessionSource
import eu.kanade.tachiyomi.ui.setting.PlayerSettingsScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.StatsTab
import eu.kanade.tachiyomi.ui.storage.StorageTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as preferenceCollectAsState

data object MoreTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_more_enter)
            return TabOptions(
                index = 4u,
                title = stringResource(MR.strings.label_more),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(SettingsScreen())
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MoreScreenModel() }
        val downloadQueueState by screenModel.downloadQueueState.collectAsStateWithLifecycle(DownloadQueueState.Stopped)

        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().preferenceCollectAsState()
        val navStyle = currentNavigationStyle()

        // Manual entry point for the Frame resonance Grid (read on every recomposition so
        // it appears as soon as the third carrier latches).
        val app = remember { Injekt.get<android.app.Application>() }
        val latticeManager = remember {
            eu.kanade.domain.easteregg.lattice.LatticeProtocolManager.get(app)
        }
        // Disappears again once the rewards are granted: from then on the grid is reachable
        // from the Achievements screen instead.
        val latticeGridAvailable = latticeManager.shouldShowMoreEntry()

        val animeSourceManager = remember { Injekt.get<AnimeSourceManager>() }
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val animeSources by animeSourceManager.sources.collectAsStateWithLifecycle(emptyList())
        val showReelsVideoFeed by uiPreferences.showReelsVideoFeed().preferenceCollectAsState()
        val lastUsedReelsSourceId by sourcePreferences.lastUsedReelsSource().preferenceCollectAsState()
        // Respect the Browse sources toggle: a disabled feed source must not be reachable here.
        val disabledSources by sourcePreferences.disabledAnimeSources().preferenceCollectAsState()
        // The session choice wins over the disk preference: under an incognito policy the
        // disk write is skipped on purpose, and re-entering the feed must still return to
        // the source the user picked in this process (device report: it opened the first
        // installed source instead). Read outside remember so the snapshot state registers
        // as a recomposition dependency and the memo below re-runs on every selection change.
        val sessionReelsSourceId = ReelsSessionSource.lastSourceId
        val targetReelsSourceId = remember(animeSources, lastUsedReelsSourceId, disabledSources, sessionReelsSourceId) {
            val feeds = animeSources.filterIsInstance<AnimeFeedSource>()
                .filterNot { it.id.toString() in disabledSources }
            if (feeds.isEmpty()) {
                null
            } else {
                val candidateIds = feeds.map { it.id }.toSet()
                val canUseMix = feeds.size > 1
                when {
                    sessionReelsSourceId == SourcePreferences.REELS_ALL_SOURCES_ID && canUseMix ->
                        SourcePreferences.REELS_ALL_SOURCES_ID
                    sessionReelsSourceId != null && sessionReelsSourceId in candidateIds ->
                        sessionReelsSourceId
                    lastUsedReelsSourceId == SourcePreferences.REELS_ALL_SOURCES_ID && canUseMix ->
                        SourcePreferences.REELS_ALL_SOURCES_ID
                    lastUsedReelsSourceId in candidateIds ->
                        lastUsedReelsSourceId
                    else -> feeds.first().id
                }
            }
        }
        val showReelsEntry = showReelsVideoFeed && targetReelsSourceId != null

        // «More» menu customization: which entries exist right now, and how the user ordered them.
        val moreMenuOrder by uiPreferences.moreMenuOrder().preferenceCollectAsState()
        val moreMenuHidden by uiPreferences.moreMenuHidden().preferenceCollectAsState()
        val moreEntryAvailability = remember(showReelsEntry, latticeGridAvailable) {
            availableMoreEntryIds(
                showReelsEntry = showReelsEntry,
                latticeGridAvailable = latticeGridAvailable,
                isDebugBuild = BuildConfig.DEBUG,
            )
        }
        val moreMenuVisibleIds = remember(moreEntryAvailability, moreMenuOrder, moreMenuHidden) {
            resolveMoreMenuLayout(
                available = moreEntryAvailability,
                savedOrderRaw = moreMenuOrder,
                hiddenRaw = moreMenuHidden,
            ).visible
        }

        if (theme.isAuroraStyle) {
            val downloadedOnly by screenModel.downloadedOnlyFlow.collectAsStateWithLifecycle()
            val incognitoMode by screenModel.incognitoModeFlow.collectAsStateWithLifecycle()
            // Resolved here because `navStyle.moreTab.options` is only readable inside the tab navigator.
            val movedTabTitle = navStyle.moreTab.options.title

            MoreScreenAurora(
                navStyle = navStyle,
                onClickAlt = { navigator.push(navStyle.moreTab) },
                downloadQueueStateProvider = { downloadQueueState },
                downloadedOnly = downloadedOnly,
                onDownloadedOnlyChange = { screenModel.toggleDownloadedOnly() },
                incognitoMode = incognitoMode,
                onIncognitoModeChange = { screenModel.toggleIncognitoMode() },
                onDownloadClick = { navigator.push(DownloadsTab) },
                onCategoriesClick = { navigator.push(CategoriesTab) },
                onDataStorageClick = { navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage)) },
                onPlayerSettingsClick = { navigator.push(PlayerSettingsScreen(mainSettings = false)) },
                onMangaReaderSettingsClick = { navigator.push(SettingsReaderScreen) },
                onNovelReaderSettingsClick = { navigator.push(SettingsNovelReaderScreen) },
                onNovelQuotesClick = { navigator.push(NovelQuotesLibraryScreen()) },
                onSettingsClick = { navigator.push(SettingsScreen()) },
                onAboutClick = { navigator.push(AboutScreen) },
                onHelpClick = { navigator.push(HelpScreen) },
                onDebugCrystalClick = {
                    navigator.push(eu.kanade.presentation.easteregg.crystal.CrystalMemorialDebugScreen())
                },
                onDebugAppUpdatePreviewClick = { navigator.push(DebugAppUpdatePreviewScreen()) },
                onDebugUpdatedChangelogPreviewClick = { navigator.push(DebugUpdatedChangelogPreviewScreen()) },
                onDebugResetAuroraHeartClick = {
                    screenModel.screenModelScope.launchIO {
                        val manager = Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>()
                        manager.debugReset()

                        runCatching {
                            val repo = Injekt.get<tachiyomi.domain.achievement.repository.AchievementRepository>()
                            repo.deleteAchievement("aurora_heart")
                        }
                    }
                },
                onDebugResetLatticeResonanceClick = {
                    screenModel.screenModelScope.launchIO {
                        val app = Injekt.get<android.app.Application>()
                        eu.kanade.domain.easteregg.lattice.LatticeProtocolManager.get(app).debugReset()
                        runCatching {
                            val repo = Injekt.get<tachiyomi.domain.achievement.repository.AchievementRepository>()
                            repo.deleteAchievement("lattice_resonance")
                        }
                    }
                },
                onDebugForceLatticeBreachClick = {
                    screenModel.screenModelScope.launchIO {
                        val app = Injekt.get<android.app.Application>()
                        eu.kanade.domain.easteregg.lattice.LatticeProtocolManager.get(app).debugForceBreach()
                    }
                },
                onStatsClick = { navigator.push(StatsTab) },
                onLibraryUpdateErrorsClick = { navigator.push(LibraryUpdateErrorScreen()) },
                onAchievementsClick = { navigator.push(AchievementScreenVoyager) },
                onTreasuryClick = { navigator.push(SettingsTreasuryScreen) },
                latticeGridAvailable = latticeGridAvailable,
                onOpenLatticeGridClick = {
                    screenModel.screenModelScope.launchIO { latticeManager.requestBreach() }
                },
                showReelsEntry = showReelsEntry,
                onReelsClick = { targetReelsSourceId?.let { navigator.push(ReelsFeedScreen(it)) } },
                visibleEntryIds = moreMenuVisibleIds,
                onCustomizeMenuClick = {
                    navigator.push(
                        MoreMenuCustomizeScreen(
                            availableIds = moreEntryAvailability,
                            movedTabTitle = movedTabTitle,
                        ),
                    )
                },
            )
        } else {
            MoreScreen(
                downloadQueueStateProvider = { downloadQueueState },
                downloadedOnly = screenModel.getDownloadedOnly(),
                onDownloadedOnlyChange = { screenModel.toggleDownloadedOnly() },
                incognitoMode = screenModel.getIncognitoMode(),
                onIncognitoModeChange = { screenModel.toggleIncognitoMode() },
                navStyle = navStyle,
                onClickAlt = { navigator.push(navStyle.moreTab) },
                onClickDownloadQueue = { navigator.push(DownloadsTab) },
                onClickCategories = { navigator.push(CategoriesTab) },
                onClickStats = { navigator.push(StatsTab) },
                onClickLibraryUpdateErrors = { navigator.push(LibraryUpdateErrorScreen()) },
                onClickStorage = { navigator.push(StorageTab) },
                onClickDataAndStorage = { navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage)) },
                onClickPlayerSettings = { navigator.push(PlayerSettingsScreen(mainSettings = false)) },
                onClickMangaReaderSettings = { navigator.push(SettingsReaderScreen) },
                onClickNovelReaderSettings = { navigator.push(SettingsNovelReaderScreen) },
                onClickNovelQuotes = { navigator.push(NovelQuotesLibraryScreen()) },
                onClickSettings = { navigator.push(SettingsScreen()) },
                onClickAbout = { navigator.push(AboutScreen) },
                onClickHelp = { navigator.push(HelpScreen) },
                onClickDebugCrystal = {
                    navigator.push(eu.kanade.presentation.easteregg.crystal.CrystalMemorialDebugScreen())
                },
                onClickDebugAppUpdatePreview = { navigator.push(DebugAppUpdatePreviewScreen()) },
                onClickDebugUpdatedChangelogPreview = { navigator.push(DebugUpdatedChangelogPreviewScreen()) },
                onClickDebugResetAuroraHeart = {
                    screenModel.screenModelScope.launchIO {
                        val manager = Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>()
                        manager.debugReset()

                        runCatching {
                            val repo = Injekt.get<tachiyomi.domain.achievement.repository.AchievementRepository>()
                            repo.deleteAchievement("aurora_heart")
                        }
                    }
                },
                onClickDebugResetLatticeResonance = {
                    screenModel.screenModelScope.launchIO {
                        val app = Injekt.get<android.app.Application>()
                        eu.kanade.domain.easteregg.lattice.LatticeProtocolManager.get(app).debugReset()
                        runCatching {
                            val repo = Injekt.get<tachiyomi.domain.achievement.repository.AchievementRepository>()
                            repo.deleteAchievement("lattice_resonance")
                        }
                    }
                },
                onClickDebugForceLatticeBreach = {
                    screenModel.screenModelScope.launchIO {
                        val app = Injekt.get<android.app.Application>()
                        eu.kanade.domain.easteregg.lattice.LatticeProtocolManager.get(app).debugForceBreach()
                    }
                },
                latticeGridAvailable = latticeGridAvailable,
                onClickOpenLatticeGrid = {
                    screenModel.screenModelScope.launchIO { latticeManager.requestBreach() }
                },
                showReelsEntry = showReelsEntry,
                onClickReels = { targetReelsSourceId?.let { navigator.push(ReelsFeedScreen(it)) } },
            )
        }
    }
}

class MoreScreenModel(
    private val downloadManager: MangaDownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
) : ScreenModel {

    val downloadedOnlyFlow: StateFlow<Boolean> = preferences.downloadedOnly().changes()
        .stateIn(screenModelScope, SharingStarted.Eagerly, preferences.downloadedOnly().get())

    val incognitoModeFlow: StateFlow<Boolean> = preferences.incognitoMode().changes()
        .stateIn(screenModelScope, SharingStarted.Eagerly, preferences.incognitoMode().get())

    fun getDownloadedOnly(): Boolean = preferences.downloadedOnly().get()
    fun getIncognitoMode(): Boolean = preferences.incognitoMode().get()

    fun toggleDownloadedOnly() {
        preferences.downloadedOnly().set(!getDownloadedOnly())
    }

    fun toggleIncognitoMode() {
        preferences.incognitoMode().set(!getIncognitoMode())
    }

    private var _downloadQueueState: MutableStateFlow<DownloadQueueState> = MutableStateFlow(
        DownloadQueueState.Stopped,
    )
    val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()

    init {
        // Handle running/paused status change and queue progress updating
        screenModelScope.launchIO {
            combine(
                downloadManager.isDownloaderRunning,
                downloadManager.queueState,
            ) { isRunningManga, mangaDownloadQueue -> Pair(isRunningManga, mangaDownloadQueue.size) }
                .collectLatest { (isDownloadingManga, mangaDownloadQueueSize) ->
                    combine(
                        animeDownloadManager.isDownloaderRunning,
                        animeDownloadManager.queueState,
                    ) { isRunningAnime, animeDownloadQueue ->
                        Pair(
                            isRunningAnime,
                            animeDownloadQueue.size,
                        )
                    }
                        .collectLatest { (isDownloadingAnime, animeDownloadQueueSize) ->
                            val isDownloading = isDownloadingAnime || isDownloadingManga
                            val downloadQueueSize = mangaDownloadQueueSize + animeDownloadQueueSize
                            val pendingDownloadExists = downloadQueueSize != 0
                            _downloadQueueState.value = when {
                                !pendingDownloadExists -> DownloadQueueState.Stopped
                                !isDownloading -> DownloadQueueState.Paused(downloadQueueSize)
                                else -> DownloadQueueState.Downloading(downloadQueueSize)
                            }
                        }
                }
        }
    }
}

sealed interface DownloadQueueState {
    data object Stopped : DownloadQueueState
    data class Paused(val pending: Int) : DownloadQueueState
    data class Downloading(val pending: Int) : DownloadQueueState
}
