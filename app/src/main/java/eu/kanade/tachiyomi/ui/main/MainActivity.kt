package eu.kanade.tachiyomi.ui.main

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.animation.doOnEnd
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.core.view.WindowInsetsControllerCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import cafe.adriel.voyager.navigator.currentOrThrow
import com.tadami.aurora.BuildConfig
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.interactor.ForegroundIncognitoState
import eu.kanade.domain.source.interactor.NovelReaderIncognitoState
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.source.novel.interactor.GetNovelIncognitoState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.achievement.components.AchievementGroupNotification
import eu.kanade.presentation.achievement.components.AchievementListDialog
import eu.kanade.presentation.achievement.components.AchievementPopupSizeTokens
import eu.kanade.presentation.achievement.components.AchievementUnlockBanner
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.DownloadedOnlyBannerBackgroundColor
import eu.kanade.presentation.components.IncognitoModeBannerBackgroundColor
import eu.kanade.presentation.components.IndexingBannerBackgroundColor
import eu.kanade.presentation.more.UpdatedChangelogScreen
import eu.kanade.presentation.more.settings.screen.SettingsTreasuryScreen
import eu.kanade.presentation.more.settings.screen.browse.AnimeExtensionStoreScreen
import eu.kanade.presentation.more.settings.screen.browse.MangaExtensionStoreScreen
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionStoreScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.reader.novel.NovelReaderBackdropSession
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.data.updater.GITHUB_REPO
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.data.updater.resolveUpdatedChangelogPrompt
import eu.kanade.tachiyomi.extension.anime.api.AnimeExtensionApi
import eu.kanade.tachiyomi.extension.manga.api.MangaExtensionApi
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.deeplink.DeepLinkScreenType
import eu.kanade.tachiyomi.ui.deeplink.anime.DeepLinkAnimeScreen
import eu.kanade.tachiyomi.ui.deeplink.manga.DeepLinkMangaScreen
import eu.kanade.tachiyomi.ui.deeplink.novel.DeepLinkNovelScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isNavigationBarNeedsScrim
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.updaterEnabled
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import mihon.core.migration.Migrator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.AppUpdatePreferences
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.util.AppHapticsProvider
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MainActivity : BaseActivity() {

    private val libraryPreferences: LibraryPreferences by injectLazy()
    private val preferences: BasePreferences by injectLazy()

    private val animeDownloadCache: AnimeDownloadCache by injectLazy()
    private val downloadCache: MangaDownloadCache by injectLazy()
    private val chapterCache: ChapterCache by injectLazy()

    private val getAnimeIncognitoState: GetAnimeIncognitoState by injectLazy()
    private val getMangaIncognitoState: GetMangaIncognitoState by injectLazy()
    private val getNovelIncognitoState: GetNovelIncognitoState by injectLazy()
    private val activityDataRepository: ActivityDataRepository by injectLazy()

    // To be checked by splash screen. If true then splash screen will be removed.
    var ready = false

    // Session time tracking for achievements
    private var appSessionStartTime: Long? = null

    private var navigator: Navigator? = null

    init {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isLaunch = savedInstanceState == null

        // Prevent splash screen showing up on configuration changes
        val splashScreen = if (isLaunch) installSplashScreen() else null

        super.onCreate(savedInstanceState)

        updateMainActivityWindowBackground(
            readerBackdropColor = NovelReaderBackdropSession.backgroundColor,
            fallbackColorArgb = resolveMainActivityThemeBackgroundArgb(),
        )

        // Await preference migrations without blocking the main thread.
        // Keep splash up until migrations are either complete or the startup timeout is reached.
        // Always release the migrator so a failed migration cannot leave startup state stuck.
        val migrationReady = mutableStateOf(false)
        lifecycleScope.launch {
            val migrationStart = System.currentTimeMillis()
            val result = runCatching {
                withTimeout(MIGRATION_STARTUP_TIMEOUT) {
                    Migrator.await()
                }
            }

            result.exceptionOrNull()?.let { error ->
                logcat(LogPriority.ERROR, error) {
                    "Preference migration failed or timed out after ${System.currentTimeMillis() - migrationStart}ms"
                }
            }

            runCatching { Migrator.release() }
                .onFailure { error ->
                    logcat(LogPriority.ERROR, error) { "Failed to release preference migrator" }
                }
            migrationReady.value = true
        }

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        setComposeContent {
            val context = LocalContext.current

            // Get haptic/eInk for provider (light), theme moved inside for launch perf
            val uiPreferencesHaptic = remember { Injekt.get<UiPreferences>() }
            val hapticFeedbackMode by uiPreferencesHaptic.hapticFeedbackMode().collectAsStateWithLifecycle()
            val eInkProfile by uiPreferencesHaptic.eInkProfile().collectAsStateWithLifecycle()

            AppHapticsProvider(
                hapticFeedbackMode = hapticFeedbackMode,
                isEInkMode = eInkProfile.isEnabled,
            ) {
                // PERF: move theme collect inside to lighten root composition on launch
                val uiPreferences = remember { Injekt.get<UiPreferences>() }
                val theme by uiPreferences.appTheme().collectAsStateWithLifecycle()
                val isAurora = theme.isAuroraStyle

                Navigator(
                    screen = HomeScreen,
                    disposeBehavior = NavigatorDisposeBehavior(
                        disposeNestedNavigators = false,
                        disposeSteps = true,
                    ),
                ) { navigator ->

                    // Make splash dismiss as soon as the main home content starts rendering its first (light) frame.
                    // Previously ready was set only at the end of handleIntentAction, which delayed splash removal.
                    if (isLaunch) {
                        ready = true
                    }

                    // PERF: move incognito and download/indexing inside to reduce root composition cost on app launch
                    var incognito by remember { mutableStateOf(false) }
                    var incognitoAnime by remember { mutableStateOf(false) }
                    var incognitoNovel by remember { mutableStateOf(false) }

                    val globalIncognito by preferences.incognitoMode().collectAsStateWithLifecycle()
                    val novelReaderIncognito by NovelReaderIncognitoState.active.collectAsStateWithLifecycle()
                    val effectiveIncognito =
                        globalIncognito || incognito || incognitoAnime || incognitoNovel || novelReaderIncognito
                    LaunchedEffect(effectiveIncognito) {
                        ForegroundIncognitoState.set(effectiveIncognito)
                    }

                    val downloadOnly by preferences.downloadedOnly().collectAsStateWithLifecycle()
                    val indexing by produceState(initialValue = false) {
                        val cache = withContext(Dispatchers.IO) { downloadCache }
                        cache.isInitializing.collect { value = it }
                    }
                    val indexingAnime by produceState(initialValue = false) {
                        val cache = withContext(Dispatchers.IO) { animeDownloadCache }
                        cache.isInitializing.collect { value = it }
                    }
                    val isSystemInDarkTheme = isSystemInDarkTheme()
                    val statusBarBackgroundColor = when {
                        indexing || indexingAnime -> IndexingBannerBackgroundColor
                        downloadOnly -> DownloadedOnlyBannerBackgroundColor
                        effectiveIncognito -> IncognitoModeBannerBackgroundColor
                        else -> MaterialTheme.colorScheme.surface
                    }
                    LaunchedEffect(isSystemInDarkTheme, statusBarBackgroundColor, navigator.lastItem, isAurora) {
                        if (!shouldMainActivityApplyEdgeToEdge(navigator.lastItem)) return@LaunchedEffect
                        // Draw edge-to-edge and set system bars color to transparent
                        val lightStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK)
                        val transparentLightStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                        val darkStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                        val isHomeScreen = navigator.lastItem == HomeScreen
                        val isLightStatusBar = statusBarBackgroundColor.luminance() > 0.5
                        val statusBarStyleMode = resolveMainStatusBarStyleMode(
                            isHomeScreen = isHomeScreen,
                            isAurora = isAurora,
                            isLightStatusBarBackground = isLightStatusBar,
                        )
                        enableEdgeToEdge(
                            statusBarStyle = when (statusBarStyleMode) {
                                MainStatusBarStyleMode.LIGHT -> lightStyle
                                MainStatusBarStyleMode.TRANSPARENT_LIGHT -> transparentLightStyle
                                MainStatusBarStyleMode.DARK -> darkStyle
                            },
                            navigationBarStyle = if (isSystemInDarkTheme) darkStyle else lightStyle,
                        )
                        // Safety net: ensure status bar appearance via direct API,
                        // guarding against enableEdgeToEdge not re-applying on repeated calls
                        // on some platform versions (e.g. Android 15 / API 35).
                        WindowInsetsControllerCompat(window, window.decorView)
                            .isAppearanceLightStatusBars = statusBarStyleMode != MainStatusBarStyleMode.DARK
                    }

                    LaunchedEffect(navigator) {
                        this@MainActivity.navigator = navigator

                        if (isLaunch) {
                            // Set start screen
                            handleIntentAction(intent, navigator)

                            // Reset Incognito Mode on relaunch
                            preferences.incognitoMode().set(false)
                        }
                    }
                    // PERF: defer per-screen incognito tracking off the initial composition pass.
                    // These only matter when the user actually navigates to a source screen.
                    LaunchedEffect(navigator.lastItem) {
                        val item = navigator.lastItem
                        val sourceId = (item as? BrowseMangaSourceScreen)?.sourceId
                        if (sourceId != null) {
                            getMangaIncognitoState.subscribe(sourceId)
                                .collectLatest { incognito = it }
                        } else {
                            incognito = false
                        }
                    }

                    LaunchedEffect(navigator.lastItem) {
                        val item = navigator.lastItem
                        val sourceId = (item as? BrowseAnimeSourceScreen)?.sourceId
                        if (sourceId != null) {
                            getAnimeIncognitoState.subscribe(sourceId)
                                .collectLatest { incognitoAnime = it }
                        } else {
                            incognitoAnime = false
                        }
                    }

                    LaunchedEffect(navigator.lastItem) {
                        val item = navigator.lastItem
                        val sourceId = (item as? BrowseNovelSourceScreen)?.sourceId
                        if (sourceId != null) {
                            getNovelIncognitoState.subscribe(sourceId)
                                .collectLatest { incognitoNovel = it }
                        } else {
                            incognitoNovel = false
                        }
                    }

                    val readerBackdropColor = when (val currentScreen = navigator.lastItem) {
                        is NovelReaderScreen -> {
                            currentScreen.resolveInitialBackdropColor() ?: NovelReaderBackdropSession.backgroundColor
                        }
                        else -> null
                    }
                    val themeBackgroundArgb = MaterialTheme.colorScheme.background.toArgb()
                    SideEffect {
                        updateMainActivityWindowBackground(
                            readerBackdropColor = readerBackdropColor,
                            fallbackColorArgb = themeBackgroundArgb,
                        )
                        if (navigator.lastItem !is NovelReaderScreen) {
                            NovelReaderBackdropSession.update(null)
                        }
                    }
                    val scaffoldInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                    Scaffold(
                        containerColor = readerBackdropColor ?: MaterialTheme.colorScheme.background,
                        topBar = {
                            AppStateBanners(
                                downloadedOnlyMode = downloadOnly,
                                incognitoMode = effectiveIncognito,
                                indexing = indexing || indexingAnime,
                                modifier = Modifier.windowInsetsPadding(scaffoldInsets),
                            )
                        },
                        contentWindowInsets = scaffoldInsets,
                    ) { contentPadding ->
                        // Consume insets already used by app state banners
                        Box {
                            // Shows current screen
                            DefaultNavigatorScreenTransition(
                                navigator = navigator,
                                modifier = Modifier
                                    .padding(contentPadding)
                                    .consumeWindowInsets(contentPadding),
                            )
                            // Achievement unlock banner overlay
                            AchievementUnlockBanner(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(top = AchievementPopupSizeTokens.overlayTopPadding),
                            )
                            // Achievement group notification (for multiple achievements after reader/player)
                            var showAchievementsList by remember { mutableStateOf(false) }
                            var pendingAchievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }

                            AchievementGroupNotification(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(top = AchievementPopupSizeTokens.overlayTopPadding),
                                onViewAll = { achievements ->
                                    // Get achievements directly from notification
                                    pendingAchievements = achievements
                                    showAchievementsList = true
                                },
                            )

                            // Achievement list dialog
                            if (showAchievementsList && pendingAchievements.isNotEmpty()) {
                                AchievementListDialog(
                                    achievements = pendingAchievements,
                                    onDismiss = {
                                        showAchievementsList = false
                                        pendingAchievements = emptyList()
                                    },
                                )
                            }
                            // Draw navigation bar scrim when needed
                            if (remember { isNavigationBarNeedsScrim() }) {
                                Spacer(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                        .alpha(0.8f)
                                        .background(MaterialTheme.colorScheme.surfaceContainer),
                                )
                            }
                            // Aurora easter egg overlay
                            eu.kanade.presentation.easteregg.aurora.AuroraEchoOverlay()
                        }
                    }

                    // PERF: defer non-UI side effects off the *initial* composition pass.
                    // Main Home UI (Scaffold + HomeHubTab + cache) gets its first frame faster.
                    var deferSideEffects by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        deferSideEffects = true
                    }

                    if (deferSideEffects) {
                        // Pop source-related screens when incognito mode is turned off
                        LaunchedEffect(Unit) {
                            preferences.incognitoMode().changes()
                                .drop(1)
                                .filter { !it }
                                .onEach {
                                    val currentScreen = navigator.lastItem
                                    if ((
                                            currentScreen is BrowseMangaSourceScreen ||
                                                (currentScreen is MangaScreen && currentScreen.fromSource)
                                            ) ||
                                        (
                                            currentScreen is BrowseAnimeSourceScreen ||
                                                (currentScreen is AnimeScreen && currentScreen.fromSource)
                                            )
                                    ) {
                                        navigator.popUntilRoot()
                                    }
                                }
                                .launchIn(this)
                        }

                        HandleOnNewIntent(context = context, navigator = navigator)

                        CheckForUpdates()
                        ShowOnboarding()

                        // PERF: moved the migration/changelog inside the Navigator to lighten
                        // the outer setComposeContent composition (faster to HomeScreen.Content)
                        var showChangelog by remember { mutableStateOf(false) }
                        var installedRelease by remember { mutableStateOf<Release?>(null) }
                        LaunchedEffect(migrationReady.value) {
                            if (migrationReady.value) {
                                val shouldShowChangelog = withContext(Dispatchers.IO) {
                                    val appUpdatePreferences = Injekt.get<AppUpdatePreferences>()
                                    val seenVersionPreference =
                                        appUpdatePreferences.lastSeenUpdatedChangelogVersionCode()
                                    val pendingPreviousVersionPreference =
                                        appUpdatePreferences.pendingUpdatedChangelogPreviousVersionCode()
                                    val decision = resolveUpdatedChangelogPrompt(
                                        currentVersionCode = BuildConfig.VERSION_CODE,
                                        lastSeenVersionCode = seenVersionPreference.get(),
                                        pendingPreviousVersionCode = pendingPreviousVersionPreference.get(),
                                        isDebug = BuildConfig.DEBUG,
                                    )

                                    if (decision.nextSeenVersionCode != seenVersionPreference.get()) {
                                        seenVersionPreference.set(decision.nextSeenVersionCode)
                                    }
                                    if (decision.nextPendingPreviousVersionCode !=
                                        pendingPreviousVersionPreference.get()
                                    ) {
                                        pendingPreviousVersionPreference.set(
                                            decision.nextPendingPreviousVersionCode,
                                        )
                                    }

                                    decision.shouldPrompt
                                }

                                if (shouldShowChangelog) {
                                    installedRelease = withContext(Dispatchers.IO) {
                                        runCatching {
                                            Injekt.get<GetApplicationRelease>().awaitCurrent(
                                                GetApplicationRelease.Arguments(
                                                    isPreview = isPreviewBuildType,
                                                    commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                                                    versionName = BuildConfig.VERSION_NAME,
                                                    repository = GITHUB_REPO,
                                                    forceCheck = true,
                                                ),
                                            )
                                        }.getOrNull()
                                    }
                                    showChangelog = true
                                }
                            }
                        }
                        if (showChangelog) {
                            val release = installedRelease
                            UpdatedChangelogScreen(
                                versionName = release?.version ?: "v${BuildConfig.VERSION_NAME}",
                                releaseDate = release?.releaseDate.orEmpty(),
                                changelogInfo = release?.info.orEmpty(),
                                onOpenInBrowser = { openInBrowser(release?.releaseLink ?: RELEASE_URL) },
                                onDismiss = { showChangelog = false },
                            )
                        }
                    } // end deferSideEffects
                }
            }
        }

        val startTime = System.currentTimeMillis()
        splashScreen?.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            elapsed <= SPLASH_MIN_DURATION || (!ready || !migrationReady.value) && elapsed <= SPLASH_MAX_DURATION
        }
        setSplashScreenExitAnimation(splashScreen)

        if (isLaunch && libraryPreferences.autoClearItemCache().get()) {
            lifecycleScope.launchIO {
                chapterCache.clear()
            }
        }

        externalPlayerResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val animeId = savedInstanceState?.getLong(SAVED_STATE_ANIME_KEY)
                val episodeId = savedInstanceState?.getLong(SAVED_STATE_EPISODE_KEY)

                if (animeId != null && episodeId != null) {
                    lifecycleScope.launch {
                        ExternalIntents.externalIntents.initAnime(animeId, episodeId)
                    }
                }

                ExternalIntents.externalIntents.onActivityResult(result.data)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        restoreOrientationAfterPlayerExit?.let { orientation ->
            requestedOrientation = orientation
            restoreOrientationAfterPlayerExit = null
        }
        lifecycleScope.launchIO {
            val todayLevel = activityDataRepository
                .getActivityData(days = 1)
                .first()
                .lastOrNull()
                ?.level
                ?: 0
            if (todayLevel == 0) {
                activityDataRepository.recordAppOpen()
            }
        }
        // Start session time tracking
        appSessionStartTime = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        // Save session time to activity data
        appSessionStartTime?.let { startTime ->
            val duration = System.currentTimeMillis() - startTime
            // Only record if session was longer than 5 seconds (filter out accidental opens)
            if (duration > 5000) {
                lifecycleScope.launch {
                    activityDataRepository.recordAppSession(duration)
                }
            }
        }
        appSessionStartTime = null
    }

    override fun onStop() {
        super.onStop()
        eu.kanade.tachiyomi.ui.home.HomeHubScreenModel.saveOnExit()
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        when (val screen = navigator?.lastItem) {
            is AssistContentScreen -> {
                screen.onProvideAssistUrl()?.let { outContent.webUri = it.toUri() }
            }
        }
    }

    @Composable
    private fun HandleOnNewIntent(context: Context, navigator: Navigator) {
        LaunchedEffect(Unit) {
            callbackFlow {
                val componentActivity = context as ComponentActivity
                val consumer = Consumer<Intent> { trySend(it) }
                componentActivity.addOnNewIntentListener(consumer)
                awaitClose { componentActivity.removeOnNewIntentListener(consumer) }
            }
                .collectLatest { handleIntentAction(it, navigator) }
        }
    }

    @Composable
    private fun CheckForUpdates() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        // App updates
        LaunchedEffect(Unit) {
            if (updaterEnabled) {
                try {
                    val updateScreen = withContext(Dispatchers.IO) {
                        val appUpdatePreferences = Injekt.get<AppUpdatePreferences>()
                        val interval = appUpdatePreferences.appUpdateInterval().get()

                        val screen = if (interval == -1) {
                            when (val result = AppUpdateChecker().checkForUpdate(context)) {
                                is GetApplicationRelease.Result.NewUpdate -> NewUpdateScreen(
                                    versionName = result.release.version,
                                    releaseDate = result.release.releaseDate,
                                    changelogInfo = result.release.info,
                                    releaseLink = result.release.releaseLink,
                                    downloadLink = result.release.downloadLink,
                                )
                                else -> null
                            }
                        } else {
                            null
                        }

                        // Set up periodic check (will cancel if interval <= 0)
                        AppUpdateJob.setupTask(context)
                        screen
                    }
                    updateScreen?.let(navigator::push)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
            }
        }

        // Extensions updates
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                runCatching { AnimeExtensionApi().checkForUpdatesIfDue(context) }
                    .onFailure { error ->
                        logcat(LogPriority.WARN, error) { "Anime extension update check failed" }
                    }
                runCatching { MangaExtensionApi().checkForUpdatesIfDue(context) }
                    .onFailure { error ->
                        logcat(LogPriority.WARN, error) { "Manga extension update check failed" }
                    }
            }
        }
    }

    @Composable
    private fun ShowOnboarding() {
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            if (!preferences.shownOnboardingFlow().get() && navigator.lastItem !is OnboardingScreen) {
                navigator.push(OnboardingScreen())
            }
        }
    }

    /**
     * Sets custom splash screen exit animation on devices prior to Android 12.
     *
     * When custom animation is used, status and navigation bar color will be set to transparent and will be restored
     * after the animation is finished.
     */
    @Suppress("Deprecation")
    private fun setSplashScreenExitAnimation(splashScreen: SplashScreen?) {
        val root = findViewById<View>(android.R.id.content)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && splashScreen != null) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            splashScreen.setOnExitAnimationListener { splashProvider ->
                // For some reason the SplashScreen applies (incorrect) Y translation to the iconView
                splashProvider.iconView.translationY = 0F

                val activityAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = LinearOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        root.translationY = value * 16.dpToPx
                    }
                }

                val splashAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = FastOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        splashProvider.view.alpha = value
                    }
                    doOnEnd {
                        splashProvider.remove()
                    }
                }

                activityAnim.start()
                splashAnim.start()
            }
        }
    }

    private fun handleIntentAction(intent: Intent, navigator: Navigator): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(
                applicationContext,
                notificationId,
                intent.getIntExtra("groupId", 0),
            )
        }

        val tabToOpen = when (intent.action) {
            Constants.SHORTCUT_HOME -> HomeScreen.Tab.HomeHub
            Constants.SHORTCUT_ANIMELIB -> HomeScreen.Tab.AnimeLib()
            Constants.SHORTCUT_LIBRARY -> HomeScreen.Tab.Library()
            Constants.SHORTCUT_MANGA -> {
                val idToOpen = intent.extras?.getLong(Constants.MANGA_EXTRA) ?: return false
                navigator.popUntilRoot()
                HomeScreen.Tab.Library(idToOpen)
            }
            Constants.SHORTCUT_NOVEL -> {
                navigator.popUntilRoot()
                HomeScreen.Tab.NovelLib()
            }
            Constants.SHORTCUT_ANIME -> {
                val idToOpen = intent.extras?.getLong(Constants.ANIME_EXTRA) ?: return false
                navigator.popUntilRoot()
                HomeScreen.Tab.AnimeLib(idToOpen)
            }
            Constants.SHORTCUT_UPDATES -> HomeScreen.Tab.Updates
            Constants.SHORTCUT_HISTORY -> HomeScreen.Tab.History
            Constants.SHORTCUT_SOURCES -> HomeScreen.Tab.Browse(false)
            Constants.SHORTCUT_ANIMEEXTENSIONS -> HomeScreen.Tab.Browse(true, true)
            Constants.SHORTCUT_EXTENSIONS -> HomeScreen.Tab.Browse(true)
            Constants.SHORTCUT_DOWNLOADS -> {
                navigator.popUntilRoot()
                HomeScreen.Tab.More(toDownloads = true)
            }
            Constants.SHORTCUT_ANIME_DOWNLOADS -> {
                navigator.popUntilRoot()
                HomeScreen.Tab.More(toDownloads = true)
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY)
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)

                if (!query.isNullOrEmpty()) {
                    navigator.popUntilRoot()

                    val screenType = runCatching {
                        intent.getStringExtra(INTENT_SEARCH_TYPE).orEmpty()
                            .ifBlank { "ANIME" }
                            .let(DeepLinkScreenType::valueOf)
                    }.getOrDefault(DeepLinkScreenType.ANIME)

                    when (screenType) {
                        DeepLinkScreenType.MANGA -> {
                            navigator.push(GlobalMangaSearchScreen(query))
                            navigator.push(DeepLinkMangaScreen(query))
                        }
                        DeepLinkScreenType.ANIME -> {
                            navigator.push(GlobalAnimeSearchScreen(query))
                            navigator.push(DeepLinkAnimeScreen(query))
                        }
                        DeepLinkScreenType.NOVEL -> {
                            navigator.push(GlobalNovelSearchScreen(query))
                            navigator.push(DeepLinkNovelScreen(query))
                        }
                    }
                }
                null
            }
            INTENT_SEARCH -> { // Used by extensions (url intent handlers)
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    navigator.popUntilRoot()
                    navigator.push(GlobalMangaSearchScreen(query, filter))
                }
                null
            }
            INTENT_ANIMESEARCH -> { // Same as above
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    navigator.popUntilRoot()
                    navigator.push(GlobalAnimeSearchScreen(query, filter))
                }
                null
            }
            INTENT_NOVELSEARCH -> { // Same as above
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    navigator.popUntilRoot()
                    navigator.push(GlobalNovelSearchScreen(query))
                }
                null
            }
            INTENT_OPEN_NOVEL_CHAPTER -> {
                val chapterId = intent.getLongExtra(INTENT_NOVEL_CHAPTER_ID, -1L)
                if (chapterId > -1L) {
                    navigator.popUntilRoot()
                    navigator.push(NovelReaderScreen(chapterId))
                }
                null
            }
            Intent.ACTION_VIEW -> {
                // Handling opening of backup files
                if (intent.data.toString().endsWith(".tachibk")) {
                    navigator.popUntilRoot()
                    navigator.push(RestoreBackupScreen(intent.data.toString()))
                }
                // Deep link to add anime extension repo (legacy)
                else if (intent.scheme == "aniyomi" && intent.data?.host == "add-repo") {
                    intent.data?.getQueryParameter("url")?.let { repoUrl ->
                        navigator.popUntilRoot()
                        navigator.push(AnimeExtensionStoreScreen(repoUrl))
                    }
                } // Deep link to add extension store (anime)
                else if (intent.scheme == "aniyomi" && intent.data?.host == "extension-store") {
                    intent.data?.getQueryParameter("url")?.let { storeUrl ->
                        navigator.popUntilRoot()
                        navigator.push(AnimeExtensionStoreScreen(storeUrl))
                    }
                } // Deep link to add extension repo (legacy)
                else if (intent.scheme in setOf("tachiyomi", "tadami") && intent.data?.host == "add-repo") {
                    intent.data?.getQueryParameter("url")?.let { repoUrl ->
                        navigator.popUntilRoot()
                        navigator.push(MangaExtensionStoreScreen(repoUrl))
                    }
                } // Deep link to add extension store (manga)
                else if (intent.scheme in setOf("tachiyomi", "tadami") && intent.data?.host == "extension-store") {
                    intent.data?.getQueryParameter("url")?.let { storeUrl ->
                        navigator.popUntilRoot()
                        navigator.push(MangaExtensionStoreScreen(storeUrl))
                    }
                } // Deep link to add novel extension store
                else if (intent.scheme in setOf("tachiyomi", "tadami") &&
                    intent.data?.host == "novel-extension-store"
                ) {
                    intent.data?.getQueryParameter("url")?.let { storeUrl ->
                        navigator.popUntilRoot()
                        navigator.push(NovelExtensionStoreScreen(storeUrl))
                    }
                }
                null
            }
            INTENT_OPEN_TREASURY -> {
                val justUnlocked = intent.getBooleanExtra("just_unlocked", false)
                eu.kanade.presentation.more.settings.screen.SettingsTreasuryScreen.shouldShowVoidBroadcastBanner =
                    justUnlocked
                navigator.popUntilRoot()
                navigator.push(SettingsTreasuryScreen)
                null
            }
            else -> {
                ready = true
                return false
            }
        }

        if (tabToOpen != null) {
            lifecycleScope.launch { HomeScreen.openTab(tabToOpen) }
        }

        ready = true
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        ExternalIntents.externalIntents.animeId?.let {
            outState.putLong(SAVED_STATE_ANIME_KEY, it)
        }
        ExternalIntents.externalIntents.episodeId?.let {
            outState.putLong(SAVED_STATE_EPISODE_KEY, it)
        }
    }

    companion object {
        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_ANIMESEARCH = "eu.kanade.tachiyomi.ANIMESEARCH"
        const val INTENT_NOVELSEARCH = "eu.kanade.tachiyomi.NOVELSEARCH"
        const val INTENT_OPEN_NOVEL_CHAPTER = "eu.kanade.tachiyomi.OPEN_NOVEL_CHAPTER"
        const val INTENT_OPEN_TREASURY = "eu.kanade.tachiyomi.OPEN_TREASURY"
        const val INTENT_NOVEL_CHAPTER_ID = "novel_chapter_id"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
        const val INTENT_SEARCH_TYPE = "type"

        const val SAVED_STATE_ANIME_KEY = "saved_state_anime_key"
        const val SAVED_STATE_EPISODE_KEY = "saved_state_episode_key"

        private var externalPlayerResult: ActivityResultLauncher<Intent>? = null
        private var restoreOrientationAfterPlayerExit: Int? = null

        suspend fun startPlayerActivity(
            context: Context,
            animeId: Long,
            episodeId: Long,
            extPlayer: Boolean,
            video: Video? = null,
            hosterIndex: Int = -1,
            videoIndex: Int = -1,
            hosterList: List<Hoster>? = null,
        ) {
            if (extPlayer) {
                val intent = try {
                    ExternalIntents.newIntent(context, animeId, episodeId, video)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                    withUIContext { Injekt.get<Application>().toast(e.message) }
                    null
                } ?: return
                externalPlayerResult?.launch(intent) ?: return
            } else {
                val currentOrientation = (context as? Activity)?.requestedOrientation
                    ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                restoreOrientationAfterPlayerExit = currentOrientation
                context.startActivity(
                    PlayerActivity.newIntent(
                        context,
                        animeId,
                        episodeId,
                        hosterList,
                        hosterIndex,
                        videoIndex,
                    ),
                )
            }
        }
    }

    private fun resolveMainActivityThemeBackgroundArgb(): Int {
        val typedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
        return try {
            typedArray.getColor(0, Color.WHITE)
        } finally {
            typedArray.recycle()
        }
    }

    private fun updateMainActivityWindowBackground(
        readerBackdropColor: androidx.compose.ui.graphics.Color?,
        fallbackColorArgb: Int,
    ) {
        window.setBackgroundDrawable(
            ColorDrawable(
                resolveMainActivityWindowBackgroundArgb(
                    readerBackdropColor = readerBackdropColor,
                    fallbackColorArgb = fallbackColorArgb,
                ),
            ),
        )
    }
}

internal enum class MainStatusBarStyleMode {
    LIGHT,
    TRANSPARENT_LIGHT,
    DARK,
}

internal fun resolveMainStatusBarStyleMode(
    isHomeScreen: Boolean,
    isAurora: Boolean,
    isLightStatusBarBackground: Boolean,
): MainStatusBarStyleMode {
    return if (isLightStatusBarBackground) {
        MainStatusBarStyleMode.TRANSPARENT_LIGHT
    } else {
        MainStatusBarStyleMode.DARK
    }
}

internal fun shouldMainActivityApplyEdgeToEdge(screen: Any?): Boolean {
    // Novel reader still needs the host window to remain edge-to-edge after activity recreation.
    return true
}

internal fun resolveMainActivityWindowBackgroundArgb(
    readerBackdropColor: androidx.compose.ui.graphics.Color?,
    fallbackColorArgb: Int,
): Int {
    return readerBackdropColor?.toArgb() ?: fallbackColorArgb
}

// Splash screen
private const val SPLASH_MIN_DURATION = 500 // ms
private const val SPLASH_MAX_DURATION = 5000 // ms
private const val SPLASH_EXIT_ANIM_DURATION = 400L // ms
private const val MIGRATION_STARTUP_TIMEOUT = 15_000L // ms
