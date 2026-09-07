package eu.kanade.tachiyomi

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.tadami.aurora.BuildConfig
import com.tadami.aurora.R
import dev.mihon.injekt.patchInjekt
import eu.kanade.domain.DomainModule
import eu.kanade.domain.SYDomainModule
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.tutorial.TutorialPreferences
import eu.kanade.domain.tutorial.model.TutorialMode
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.achievement.components.AchievementBannerManager
import eu.kanade.presentation.components.CoverReloadSignal
import eu.kanade.presentation.tutorial.CoachTipRegistry
import eu.kanade.tachiyomi.crash.CrashActivity
import eu.kanade.tachiyomi.crash.GlobalExceptionHandler
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.coil.AnimeCoverKeyer
import eu.kanade.tachiyomi.data.coil.AnimeImageFetcher
import eu.kanade.tachiyomi.data.coil.AnimeKeyer
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequestFetcher
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequestKeyer
import eu.kanade.tachiyomi.data.coil.BufferedSourceFetcher
import eu.kanade.tachiyomi.data.coil.FallbackUriFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer
import eu.kanade.tachiyomi.data.coil.MangaKeyer
import eu.kanade.tachiyomi.data.coil.NovelCoverFetcher
import eu.kanade.tachiyomi.data.coil.NovelCoverKeyer
import eu.kanade.tachiyomi.data.coil.NovelKeyer
import eu.kanade.tachiyomi.data.coil.NovelPluginImageFetcher
import eu.kanade.tachiyomi.data.coil.NovelPluginImageKeyer
import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImageFetcher
import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImageKeyer
import eu.kanade.tachiyomi.data.coil.StringCoverUriMapper
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.updater.AppUpdateFileManager
import eu.kanade.tachiyomi.di.AppModule
import eu.kanade.tachiyomi.di.PreferenceModule
import eu.kanade.tachiyomi.di.bootstrapInjektModules
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.installer.PendingApkInstallStore
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory
import eu.kanade.tachiyomi.extension.novel.kotlin.sweepOrphanedNovelPluginDownloads
import eu.kanade.tachiyomi.extension.novel.runtime.NovelRuntimeCacheTrimCallbacks
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.networkStateFlow
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import logcat.logcat
import mihon.core.migration.Migrator
import mihon.core.migration.migrations.migrations
import okio.Path.Companion.toOkioPath
import org.conscrypt.Conscrypt
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.data.achievement.loader.AchievementLoader
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.widget.entries.anime.AnimeWidgetManager
import tachiyomi.presentation.widget.entries.manga.MangaWidgetManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.security.Security
import java.util.concurrent.atomic.AtomicLong
import tachiyomi.core.common.util.system.logcat as systemLogcat

class App : Application(), DefaultLifecycleObserver, SingletonImageLoader.Factory {

    private val basePreferences: BasePreferences by injectLazy()
    private val networkPreferences: NetworkPreferences by injectLazy()
    private val appUpdateFileManager: AppUpdateFileManager by injectLazy()
    private val sessionManager: tachiyomi.data.achievement.handler.SessionManager by injectLazy()

    private val disableIncognitoReceiver = DisableIncognitoReceiver()
    private val achievementScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isMainProcess = false

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // Register the Injekt graph before any ContentProvider runs: WorkManager initializes
        // in a provider and may dispatch a pending library-update worker right after process
        // death, before onCreate() would have imported the modules. Without this the workers
        // died with InjektionException in a fresh background process (crash log #bug 0.60).
        isMainProcess = bootstrapInjektModules()
    }

    @SuppressLint("LaunchActivityFromNotification")
    @OptIn(DelicateCoilApi::class)
    override fun onCreate() {
        LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
        super<Application>.onCreate()

        GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)

        if (BuildConfig.DEBUG) {
            MainThreadWatchdog().start()
        }

        // TLS 1.3 support for Android < 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        // Avoid potential crashes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && packageName != getProcessName()) {
            WebView.setDataDirectorySuffix(getProcessName())
        }

        // Warm up the WebView default user agent on the main thread before any source loads.
        // Some sources call WebSettings.getDefaultUserAgent() while being constructed on a
        // background thread. If Chromium needs the main thread at the same time, startup can
        // stall behind the splash.
        if (isMainProcess) {
            try {
                WebSettings.getDefaultUserAgent(this)
                // Initialize the WebView cookie provider here too: CookieManager.getInstance()
                // crashes with NPE inside WebViewFactory when first called from a background
                // thread before the app is fully attached (Android 16, crash log 0.60.4).
                CookieManager.getInstance()
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR) { "Failed to warm up WebView user agent: ${e.message}" }
            }
        }

        // Modules were imported in attachBaseContext (see bootstrapInjektModules) so the DI
        // graph already exists here, including for workers dispatched by WorkManager's
        // ContentProvider before onCreate ran.

        if (isMainProcess) {
            // Setup Aurora easter egg unlock hook
            eu.kanade.domain.easteregg.aurora.AuroraEchoBus.onUnlocked = { payload ->
                achievementScope.launch {
                    val repo = Injekt
                        .get<tachiyomi.domain.achievement.repository.AchievementRepository>()
                    val pointsManager = Injekt
                        .get<tachiyomi.data.achievement.handler.PointsManager>()
                    val userProfileManager = Injekt
                        .get<tachiyomi.data.achievement.UserProfileManager>()
                    val activityDataRepository = Injekt
                        .get<tachiyomi.domain.achievement.repository.ActivityDataRepository>()

                    repo.insertOrUpdateProgress(
                        tachiyomi.domain.achievement.model.AchievementProgress.createStandard(
                            achievementId = "aurora_heart",
                            progress = 1,
                            maxProgress = 1,
                            isUnlocked = true,
                            unlockedAt = System.currentTimeMillis(),
                        ),
                    )
                    pointsManager.addPoints(payload.bonusPoints ?: 0)
                    pointsManager.incrementUnlocked()
                    activityDataRepository.recordAchievementUnlock()

                    userProfileManager.unlockTheme("AURORA_PRIME")

                    val unlockableManager = Injekt.get<tachiyomi.data.achievement.UnlockableManager>()
                    unlockableManager.setUnlockableUnlocked("theme_AURORA_PRIME")
                    unlockableManager.setUnlockableUnlocked("special_navbar_aurora_celestial")
                }
            }

            // Render pipeline maintenance (opaque service hook)
            eu.kanade.domain.easteregg.lattice.LatticeProtocolManager.get(this).onAppStart()
            eu.kanade.domain.easteregg.lattice.LatticeSignalBus.onUnlocked = { payload ->
                achievementScope.launch {
                    val repo = Injekt
                        .get<tachiyomi.domain.achievement.repository.AchievementRepository>()
                    val pointsManager = Injekt
                        .get<tachiyomi.data.achievement.handler.PointsManager>()
                    val userProfileManager = Injekt
                        .get<tachiyomi.data.achievement.UserProfileManager>()
                    val activityDataRepository = Injekt
                        .get<tachiyomi.domain.achievement.repository.ActivityDataRepository>()

                    repo.insertOrUpdateProgress(
                        tachiyomi.domain.achievement.model.AchievementProgress.createStandard(
                            achievementId = payload.achievementId ?: "lattice_resonance",
                            progress = 1,
                            maxProgress = 1,
                            isUnlocked = true,
                            unlockedAt = System.currentTimeMillis(),
                        ),
                    )
                    pointsManager.addPoints(payload.bonusPoints ?: 0)
                    pointsManager.incrementUnlocked()
                    activityDataRepository.recordAchievementUnlock()

                    payload.themeId?.let { userProfileManager.unlockTheme(it) }

                    val unlockableManager = Injekt.get<tachiyomi.data.achievement.UnlockableManager>()
                    payload.unlockables.forEach { unlockableManager.setUnlockableUnlocked(it) }
                }
            }
        }
        SingletonImageLoader.setUnsafe { context -> newImageLoader(context) }

        if (isMainProcess) {
            applicationScope.launch {
                var wasOnline = runCatching { activeNetworkState().isOnline }.getOrDefault(true)
                runCatching {
                    networkStateFlow()
                        .map { it.isOnline }
                        .distinctUntilChanged()
                        .collect { online ->
                            if (online && !wasOnline) {
                                CoverReloadSignal.bump()
                            }
                            wasOnline = online
                        }
                }
            }
        }

        if (isMainProcess) {
            Handler(Looper.getMainLooper()).post {
                achievementScope.launch {
                    runCatching {
                        appUpdateFileManager.cleanupIfInstalledVersionReached(
                            isPreview = isPreviewBuildType,
                            installedCommitCount = BuildConfig.COMMIT_COUNT.toInt(),
                            installedVersionName = BuildConfig.VERSION_NAME,
                        )
                    }.onFailure { error ->
                        this@App.systemLogcat(LogPriority.ERROR, error) { "App update cleanup failed" }
                    }
                }

                // Register memory-pressure callback that trims novel plugin runtime caches
                registerComponentCallbacks(
                    NovelRuntimeCacheTrimCallbacks(
                        sourceFactory = Injekt.get<NovelPluginSourceFactory>(),
                    ),
                )
            }
        }

        setupNotificationChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val scope = ProcessLifecycleOwner.get().lifecycleScope

        // Show notification to disable Incognito Mode when it's enabled
        basePreferences.incognitoMode().changes()
            .onEach { enabled ->
                if (enabled) {
                    disableIncognitoReceiver.register()
                    notify(
                        Notifications.ID_INCOGNITO_MODE,
                        Notifications.CHANNEL_INCOGNITO_MODE,
                    ) {
                        setContentTitle(stringResource(MR.strings.pref_incognito_mode))
                        setContentText(stringResource(MR.strings.notification_incognito_text))
                        setSmallIcon(R.drawable.ic_glasses_24dp)
                        setOngoing(true)

                        val pendingIntent = PendingIntent.getBroadcast(
                            this@App,
                            0,
                            Intent(ACTION_DISABLE_INCOGNITO_MODE),
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        setContentIntent(pendingIntent)
                    }
                } else {
                    disableIncognitoReceiver.unregister()
                    cancelNotification(Notifications.ID_INCOGNITO_MODE)
                }
            }
            .launchIn(ProcessLifecycleOwner.get().lifecycleScope)

        basePreferences.hardwareBitmapThreshold().let { preference ->
            if (!preference.isSet()) preference.set(GLUtil.DEVICE_TEXTURE_LIMIT)
        }

        basePreferences.hardwareBitmapThreshold().changes()
            .onEach { ImageUtil.hardwareBitmapThreshold = it }
            .launchIn(scope)

        setAppCompatDelegateThemeMode(Injekt.get<UiPreferences>().themeMode().get())

        if (isMainProcess) {
            // Updates widget update
            with(MangaWidgetManager(Injekt.get(), Injekt.get())) {
                init(ProcessLifecycleOwner.get().lifecycleScope)
            }

            with(AnimeWidgetManager(Injekt.get(), Injekt.get())) {
                init(ProcessLifecycleOwner.get().lifecycleScope)
            }

            // Defer achievement initialization past the first frame.
            // Handler.post fires in the next main-thread message-queue cycle, after onCreate() has
            // returned and the first activity draw pass has been scheduled. All Injekt modules are
            // fully imported by then, so DI resolution inside achievementScope (Dispatchers.IO)
            // is safe. Achievement events can only come from user actions that haven't happened yet.
            Handler(Looper.getMainLooper()).post {
                // Initialize achievements from JSON
                achievementScope.launch {
                    try {
                        val loader = Injekt.get<tachiyomi.data.achievement.loader.AchievementLoader>()
                        loader.loadAchievements()
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) { "Error during achievement initialization: ${e.message}" }
                    }
                }

                // Clean up legacy phantom theme unlockables (theme_achievement_* no longer exist)
                achievementScope.launch {
                    try {
                        val unlockableManager = Injekt.get<tachiyomi.data.achievement.UnlockableManager>()
                        val userProfileManager = Injekt.get<tachiyomi.data.achievement.UserProfileManager>()
                        val validThemeIds = eu.kanade.domain.ui.model.AppTheme.entries
                            .filter { it.titleRes != null }
                            .map { it.name.uppercase() }
                            .toSet()

                        unlockableManager.removeUnlockable("theme_achievement_gold")
                        unlockableManager.removeUnlockable("theme_achievement_sapphire")

                        val staleThemes = userProfileManager.getUnlockedThemes()
                            .filter { it.uppercase() !in validThemeIds }
                        staleThemes.forEach { themeId ->
                            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Removing stale legacy theme: $themeId" }
                            userProfileManager.removeTheme(themeId)
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) { "Error during legacy theme cleanup: ${e.message}" }
                    }
                }

                // Migrate legacy activity data from SharedPreferences to database (v4 → v5)
                achievementScope.launch {
                    try {
                        val migrator = eu.kanade.tachiyomi.data.backup.restore.LegacyActivityDataMigrator(
                            context = this@App,
                            repository = Injekt.get(),
                        )

                        if (migrator.isMigrationNeeded()) {
                            logcat(LogPriority.INFO) { "[MIGRATION] Starting legacy activity data migration..." }
                            val result = migrator.migrate()

                            if (result.success) {
                                logcat(LogPriority.INFO) {
                                    "[MIGRATION] Migration completed: ${result.recordsMigrated} records migrated, " +
                                        "${result.recordsFailed} failed in ${result.duration}ms"
                                }
                                // Optional: Clear legacy data after successful migration
                                // migrator.clearLegacyData()
                            } else {
                                logcat(LogPriority.ERROR) { "[MIGRATION] Migration failed: ${result.error}" }
                            }
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) { "[MIGRATION] Error during legacy data migration: ${e.message}" }
                    }
                }

                // Start achievement handler
                achievementScope.launch {
                    try {
                        logcat(LogPriority.INFO) {
                            "[ACHIEVEMENTS-INIT] About to get AchievementHandler from Injekt..."
                        }
                        val achievementHandler = Injekt.get<tachiyomi.data.achievement.handler.AchievementHandler>()
                        logcat(LogPriority.INFO) { "[ACHIEVEMENTS-INIT] AchievementHandler obtained successfully" }

                        // Set up callback to show unlock banners
                        achievementHandler.unlockCallback =
                            object : tachiyomi.data.achievement.handler.AchievementHandler.AchievementUnlockCallback {
                                override fun onAchievementUnlocked(
                                    achievement: tachiyomi.domain.achievement.model.Achievement,
                                ) {
                                    AchievementBannerManager.showAchievement(achievement)
                                }
                            }

                        logcat(LogPriority.INFO) { "[ACHIEVEMENTS-INIT] Calling achievementHandler.start()..." }
                        achievementHandler.start()
                        logcat(LogPriority.INFO) { "[ACHIEVEMENTS-INIT] AchievementHandler started successfully" }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) {
                            "[ACHIEVEMENTS-INIT] Failed to start achievement handler: ${e.message}"
                        }
                        logcat(LogPriority.ERROR) {
                            "[ACHIEVEMENTS-INIT] Failed to start achievement handler: ${e.stackTraceToString()}"
                        }
                    }
                }
            }
        }

        if (!LogcatLogger.isInstalled && networkPreferences.verboseLogging().get()) {
            LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
        }

        if (isMainProcess) {
            BackupCreateJob.clearStaleProgressNotification(this)
        }

        if (isMainProcess) {
            initializeMigrator()
        }
    }

    private fun initializeMigrator() {
        val preferenceStore = Injekt.get<PreferenceStore>()
        val preference = preferenceStore.getInt(Preference.appStateKey("last_version_code"), 0)
        val oldVersionCode = preference.get()
        val seenUpdatedChangelogVersionCode = preferenceStore.getInt(
            Preference.appStateKey("last_seen_updated_changelog_version_code"),
            0,
        )
        val pendingUpdatedChangelogPreviousVersionCode = preferenceStore.getInt(
            Preference.appStateKey("pending_updated_changelog_previous_version_code"),
            0,
        )
        if (oldVersionCode > 0 &&
            BuildConfig.VERSION_CODE > oldVersionCode &&
            seenUpdatedChangelogVersionCode.get() < BuildConfig.VERSION_CODE
        ) {
            pendingUpdatedChangelogPreviousVersionCode.set(oldVersionCode)
        }

        if (oldVersionCode > 0) {
            if (!basePreferences.shownOnboardingFlow().get()) {
                basePreferences.shownOnboardingFlow().set(true)
                val tutorialPreferences = Injekt.get<TutorialPreferences>()
                tutorialPreferences.tutorialMode().set(TutorialMode.OFF)
                tutorialPreferences.tourCompleted().set(true)
                tutorialPreferences.shownTips().set(
                    CoachTipRegistry.tips.map { it.id }.toSet(),
                )
            }
        }

        logcat { "Migration from $oldVersionCode to ${BuildConfig.VERSION_CODE}" }
        Migrator.initialize(
            old = oldVersionCode,
            new = BuildConfig.VERSION_CODE,
            migrations = migrations,
            onMigrationComplete = {
                logcat { "Updating last version to ${BuildConfig.VERSION_CODE}" }
                preference.set(BuildConfig.VERSION_CODE)
            },
        )
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(this).apply {
            val callFactoryLazy = lazy { Injekt.get<NetworkHelper>().client }
            components {
                // Mapper
                add(StringCoverUriMapper())
                // NetworkFetcher.Factory
                add(OkHttpNetworkFetcherFactory(callFactory = { callFactoryLazy.value }))
                add(FallbackUriFetcher.Factory(callFactoryLazy))
                // Decoder.Factory
                add(TachiyomiImageDecoder.Factory())
                // Fetcher.Factory
                add(BufferedSourceFetcher.Factory())
                add(MangaCoverFetcher.MangaFactory(callFactoryLazy))
                add(MangaCoverFetcher.MangaCoverFactory(callFactoryLazy))
                add(AnimeImageFetcher.AnimeFactory(callFactoryLazy))
                add(AnimeImageFetcher.AnimeCoverFactory(callFactoryLazy))
                add(NovelCoverFetcher.NovelFactory(callFactoryLazy))
                add(NovelCoverFetcher.NovelCoverFactory(callFactoryLazy))
                add(AuroraPosterRequestFetcher.Factory(callFactoryLazy))
                add(NovelReaderRefererImageFetcher.Factory(callFactoryLazy))
                add(NovelPluginImageFetcher.Factory())
                // Keyer
                add(AnimeKeyer())
                add(MangaKeyer())
                add(NovelKeyer())
                add(AnimeCoverKeyer())
                add(MangaCoverKeyer())
                add(NovelCoverKeyer())
                add(AuroraPosterRequestKeyer())
                add(NovelReaderRefererImageKeyer())
                add(NovelPluginImageKeyer())
            }

            val crossfadeMs = (300 * this@App.animatorDurationScale).toInt()
            // Long animations delay first paint of each cover; cap the scaled value.
            crossfade(crossfadeMs.coerceAtMost(MAX_CROSSFADE_MS))
            allowRgb565(DeviceUtil.isLowRamDevice(this@App))
            memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this@App, 0.25)
                    .build()
            }
            diskCache {
                DiskCache.Builder()
                    .directory(this@App.cacheDir.resolve("coil_cache").toOkioPath())
                    .maxSizeBytes(diskCacheSizeBytes(this@App))
                    .build()
            }
            if (networkPreferences.verboseLogging().get()) logger(DebugLogger())

            // Coil spawns a new thread for every image load by default
            val isLowRam = DeviceUtil.isLowRamDevice(this@App)
            fetcherCoroutineContext(
                Dispatchers.IO.limitedParallelism(
                    if (isLowRam) 8 else COVER_FETCH_PARALLELISM,
                ),
            )
            decoderCoroutineContext(Dispatchers.IO.limitedParallelism(if (isLowRam) 3 else 4))
        }
            .build()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (isMainProcess) {
            SecureActivityDelegate.onApplicationStart()
            sessionManager.onSessionStart()
            applicationScope.launch {
                PendingApkInstallStore(basePreferences).resumeIfPermissionGranted(this@App)
                // Creating the extension managers synchronously loads every installed extension on
                // the main thread (MangaExtensionManager.initExtensions), so only touch them when
                // there are orphaned downloads to actually recover.
                if (
                    basePreferences.mangaExtensionActiveDownloads().get().isNotEmpty() ||
                    basePreferences.animeExtensionActiveDownloads().get().isNotEmpty()
                ) {
                    runCatching { Injekt.get<MangaExtensionManager>().resumeOrphanedDownloads() }
                    runCatching { Injekt.get<AnimeExtensionManager>().resumeOrphanedDownloads() }
                }
                // Truncated or abandoned novel-plugin downloads are re-downloadable; sweep them.
                runCatching {
                    val swept = sweepOrphanedNovelPluginDownloads(this@App)
                    if (swept.isNotEmpty()) {
                        logcat(LogPriority.INFO) { "Deleted orphaned novel plugin downloads: ${swept.size}" }
                    }
                }
            }
            // I15: startNow is suspend (blocking WM guard) - run the app-start triggers on the
            // application scope instead of the lifecycle callback thread.
            applicationScope.launch {
                val libraryPreferences = Injekt.get<tachiyomi.domain.library.service.LibraryPreferences>()
                val autoUpdateInterval = libraryPreferences.autoUpdateInterval().get()
                if (autoUpdateInterval == -1) {
                    // I7 (Q9): per-media "Never" (0) and per-media intervals override the global
                    // "At app start" - the scheduler honored them, this foreground path started
                    // all three sections unconditionally.
                    fun runsAtAppStart(mediaPref: Int): Boolean {
                        val effective = if (mediaPref == -2) autoUpdateInterval else mediaPref
                        return effective == -1
                    }
                    if (runsAtAppStart(libraryPreferences.mangaUpdateInterval().get())) {
                        MangaLibraryUpdateJob.startNow(this@App)
                    }
                    if (runsAtAppStart(libraryPreferences.animeUpdateInterval().get())) {
                        AnimeLibraryUpdateJob.startNow(this@App)
                    }
                    if (runsAtAppStart(libraryPreferences.novelUpdateInterval().get())) {
                        NovelLibraryUpdateJob.startNow(this@App)
                    }
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (isMainProcess) {
            SecureActivityDelegate.onApplicationStopped()
            sessionManager.onSessionEnd()
        }
    }

    override fun getPackageName(): String {
        try {
            // Override the value passed as X-Requested-With in WebView requests
            val stackTrace = Looper.getMainLooper().thread.stackTrace
            val isChromiumCall = stackTrace.any { trace ->
                trace.className.lowercase() in setOf("org.chromium.base.buildinfo", "org.chromium.base.apkinfo") &&
                    trace.methodName.lowercase() in setOf("getall", "getpackagename", "<init>")
            }

            if (isChromiumCall) return WebViewUtil.spoofedPackageName(applicationContext)
        } catch (_: Exception) {
        }

        return super.getPackageName()
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to modify notification channels: ${e.message}" }
        }
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            basePreferences.incognitoMode().set(false)
        }

        fun register() {
            if (!registered) {
                ContextCompat.registerReceiver(
                    this@App,
                    this,
                    IntentFilter(ACTION_DISABLE_INCOGNITO_MODE),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                unregisterReceiver(this)
                registered = false
            }
        }
    }
}

private const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"

/** Parallel cover fetches on non-low-RAM devices (was 16; grids of 30+ cells). */
private const val COVER_FETCH_PARALLELISM = 24

/** Coil disk cache for covers/posters on devices that can afford it. */
private fun diskCacheSizeBytes(context: android.content.Context): Long {
    val isLowRam = eu.kanade.tachiyomi.util.system.DeviceUtil.isLowRamDevice(context)
    return if (isLowRam) 128L * 1024 * 1024 else 256L * 1024 * 1024
}

/** Crossfade above this value delays first paint more than it helps. */
private const val MAX_CROSSFADE_MS = 300

private class MainThreadWatchdog(
    private val intervalMs: Long = 500,
    private val timeoutMs: Long = 5_000,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val lastTick = AtomicLong(SystemClock.uptimeMillis())
    private val lastReported = AtomicLong(0L)

    private val ticker = object : Runnable {
        override fun run() {
            lastTick.set(SystemClock.uptimeMillis())
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        handler.post(ticker)
        Thread {
            while (true) {
                SystemClock.sleep(intervalMs)
                val now = SystemClock.uptimeMillis()
                val delta = now - lastTick.get()
                if (delta > timeoutMs && now - lastReported.get() > timeoutMs) {
                    lastReported.set(now)
                    val mainThread = Looper.getMainLooper().thread
                    val stack = mainThread.stackTrace.joinToString(separator = "\n") { it.toString() }
                    logcat(LogPriority.ERROR) {
                        "ANR watchdog: main thread blocked ${delta}ms\n$stack"
                    }
                }
            }
        }.apply {
            name = "main-thread-watchdog"
            isDaemon = true
            start()
        }
    }
}
