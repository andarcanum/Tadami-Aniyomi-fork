package eu.kanade.tachiyomi.ui.more

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.presentation.more.MoreScreenAurora
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.presentation.achievement.screen.AchievementScreenVoyager
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.download.DownloadsTab
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
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState as preferenceCollectAsState

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
        val downloadQueueState by screenModel.downloadQueueState.collectAsState(DownloadQueueState.Stopped)
        val uriHandler = LocalUriHandler.current

        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().preferenceCollectAsState()

        if (theme.isAuroraStyle) {
            val downloadedOnly by screenModel.downloadedOnlyFlow.collectAsState()
            val incognitoMode by screenModel.incognitoModeFlow.collectAsState()

            MoreScreenAurora(
                downloadQueueStateProvider = { downloadQueueState },
                downloadedOnly = downloadedOnly,
                onDownloadedOnlyChange = { screenModel.toggleDownloadedOnly() },
                incognitoMode = incognitoMode,
                onIncognitoModeChange = { screenModel.toggleIncognitoMode() },
                onDownloadClick = { navigator.push(DownloadsTab) },
                onCategoriesClick = { navigator.push(CategoriesTab) },
                onDataStorageClick = { navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage)) },
                onPlayerSettingsClick = { navigator.push(PlayerSettingsScreen(mainSettings = false)) },
                onSettingsClick = { navigator.push(SettingsScreen()) },
                onAboutClick = { navigator.push(AboutScreen) },
                onStatsClick = { navigator.push(StatsTab) },
                onAchievementsClick = { navigator.push(AchievementScreenVoyager) },
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
            )
        } else {
            val navStyle = currentNavigationStyle()
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
                onClickStorage = { navigator.push(StorageTab) },
                onClickDataAndStorage = { navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage)) },
                onClickPlayerSettings = { navigator.push(PlayerSettingsScreen(mainSettings = false)) },
                onClickSettings = { navigator.push(SettingsScreen()) },
                onClickAbout = { navigator.push(AboutScreen) },
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
