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

// Code is a mix between PlayerActivity from mpvKt and the former
// PlayerActivity from Aniyomi.

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.hippo.unifile.UniFile
import com.tadami.aurora.databinding.PlayerLayoutBinding
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.serialize
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.discord.DiscordPresenceInfo
import eu.kanade.tachiyomi.data.discord.DiscordPresenceManager
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.player.controls.PlayerControls
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.torrent.TorrentPlaybackResolver
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils.Companion.getStringRes
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.util.AppHapticsProvider
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.ceil
import kotlin.math.floor

// Legacy bridge: this activity still hosts the player while the Compose migration remains partial.
class PlayerActivity : BaseActivity() {
    private val viewModel by viewModels<PlayerViewModel>(factoryProducer = { PlayerViewModelProviderFactory(this) })
    private val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }
    private val playerObserver by lazy { PlayerObserver(this) }
    val player by lazy { binding.player }
    val windowInsetsController by lazy { WindowCompat.getInsetsController(window, window.decorView) }
    val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var mediaSession: MediaSession? = null

    @Volatile
    private var didOverrideDisplayMode = false
    private var baselineDisplayModeId: Int? = null

    @Volatile
    private var isViewModelReady = false
    private val gesturePreferences: GesturePreferences by lazy { viewModel.gesturePreferences }
    private val playerPreferences: PlayerPreferences by lazy { viewModel.playerPreferences }
    private val playerStartupCoordinator by lazy {
        PlayerStartupCoordinator(
            chaptersProvider = { viewModel.chapters.value },
            currentPositionProvider = { viewModel.pos.value },
            setWaitingSkipIntro = { viewModel.waitingSkipIntro = it },
            aniSkipResponse = { duration -> viewModel.aniSkipResponse(duration) },
            updateChapters = viewModel::updateChapters,
            setChapter = viewModel::setChapter,
        )
    }
    private val audioPreferences: AudioPreferences = Injekt.get()
    private val advancedPlayerPreferences: AdvancedPlayerPreferences = Injekt.get()
    private val decoderPreferences: DecoderPreferences = Injekt.get()
    private val networkPreferences: NetworkPreferences = Injekt.get()
    private val storageManager: StorageManager = Injekt.get()
    private val uiPreferences: UiPreferences = Injekt.get()
    private val torrentPlaybackResolver by lazy { TorrentPlaybackResolver(contentResolver) }

    private var audioFocusRequest: AudioFocusRequestCompat? = null

    private var presenceStartedAt: Long = 0L
    private var presenceJob: Job? = null
    private var initJob: Job? = null
    private var fileLoadedJob: Job? = null
    private var customButtonsJob: Job? = null
    private var changeEpisodeJob: Job? = null
    private var torrentResolveJob: Job? = null
    private val mpvLifecycleLock = ReentrantReadWriteLock()

    @Volatile
    private var isMpvInitialized = false

    @Volatile
    private var isMpvDestroyed = false

    private var isDucked = false
    private var preDuckVolume: Double? = null
    private var restoreAudioFocus: () -> Unit = {}

    private var pipRect: Rect? = null
    val isPipSupported by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    val isPipSupportedAndEnabled: Boolean
        get() = isPipSupported && playerPreferences.enablePip().get()

    internal fun <T> withActiveMpv(
        requireNotExiting: Boolean = false,
        block: () -> T,
    ): T? {
        return mpvLifecycleLock.read {
            if (isMpvDestroyed || !isMpvInitialized || isDestroyed) {
                null
            } else if (requireNotExiting && player.isExiting) {
                null
            } else {
                block()
            }
        }
    }

    internal fun updatePipParamsSafely() {
        if (!isPipSupported || isDestroyed || isFinishing) return
        runCatching {
            setPictureInPictureParams(createPipParams())
        }.onFailure { e ->
            logcat(LogPriority.WARN, e) { "Failed to update PiP params" }
        }
    }

    internal fun enterPipModeSafely(finishOnFailure: Boolean = false): Boolean {
        if (!isPipSupportedAndEnabled || isDestroyed || isFinishing) {
            if (finishOnFailure) finish()
            return false
        }
        val entered = runCatching {
            enterPictureInPictureMode(createPipParams())
        }.onFailure { e ->
            logcat(LogPriority.WARN, e) { "Failed to enter PiP mode" }
        }.getOrDefault(false)
        if (!entered && finishOnFailure) {
            finish()
        }
        return entered
    }

    private var pipReceiver: BroadcastReceiver? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        var initialized = false
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                viewModel.pause()
                updateKeepScreenOnFlag(isPaused = true)
            }
        }
    }

    companion object {
        fun newIntent(
            context: Context,
            animeId: Long?,
            episodeId: Long?,
            hostList: List<Hoster>? = null,
            hostIndex: Int? = null,
            vidIndex: Int? = null,
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra("animeId", animeId)
                putExtra("episodeId", episodeId)
                hostIndex?.let { putExtra("hostIndex", it) }
                vidIndex?.let { putExtra("vidIndex", it) }
                hostList?.let { putExtra("hostList", it.serialize()) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        internal const val MPV_DIR = "mpv"
        private const val MPV_SCRIPTS_DIR = "scripts"
        private const val MPV_SCRIPTS_OPTS_DIR = "script-opts"
        private const val MPV_SHADERS_DIR = "shaders"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val animeId = intent.extras?.getLong("animeId") ?: -1
        val episodeId = intent.extras?.getLong("episodeId") ?: -1
        val hostList = intent.extras?.getString("hostList") ?: ""
        val hostIndex = intent.extras?.getInt("hostIndex") ?: -1
        val vidIndex = intent.extras?.getInt("vidIndex") ?: -1
        if (animeId == -1L || episodeId == -1L) {
            finish()
            return
        }
        NotificationReceiver.dismissNotification(
            this,
            animeId.hashCode(),
            Notifications.ID_NEW_EPISODES,
        )

        viewModel.saveCurrentEpisodeWatchingProgress()

        initJob?.cancel()
        initJob = lifecycleScope.launch {
            viewModel.updateIsLoadingEpisode(true)
            viewModel.updateIsLoadingHosters(true)

            val initResult = viewModel.init(animeId, episodeId, hostList, hostIndex, vidIndex)
            if (!initResult.second.getOrDefault(false)) {
                val exception = initResult.second.exceptionOrNull() ?: IllegalStateException(
                    "Unknown error",
                )
                viewModel.updateIsLoadingHosters(false)
                viewModel.updateIsLoadingEpisode(false)
                viewModel.updateIsLoading(false)
                withUIContext {
                    setInitialEpisodeError(exception)
                }
                return@launch
            }

            viewModel.updateIsLoadingHosters(false)

            val source = viewModel.currentSource.value ?: return@launch
            viewModel.loadHosters(
                source = source,
                hosterList = initResult.first.hosterList ?: emptyList(),
                hosterIndex = initResult.first.videoIndex.first,
                videoIndex = initResult.first.videoIndex.second,
            )
        }

        setIntent(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        registerSecureActivity(this)
        super.onCreate(savedInstanceState)

        // Defer achievement notifications while in player
        eu.kanade.presentation.achievement.components.AchievementBannerManager.setInReaderOrPlayer(true)

        setContentView(binding.root)

        setupPlayerMPV()
        viewModel.attachActivity(this)
        isViewModelReady = true
        setupPlayerAudio()
        setupMediaSession()
        setupPlayerOrientation()

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    is PlayerViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is PlayerViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.seconds)
                    }
                    is PlayerViewModel.Event.SetArtResult -> {
                        onSetAsArtResult(event.result, event.artType)
                    }
                }
            }
            .launchIn(lifecycleScope)

        binding.controls.setComposeContent {
            val hapticFeedbackMode by uiPreferences.hapticFeedbackMode().collectAsStateWithLifecycle()
            val eInkProfile by uiPreferences.eInkProfile().collectAsStateWithLifecycle()
            val anime by viewModel.currentAnime.collectAsStateWithLifecycle()

            AppHapticsProvider(
                hapticFeedbackMode = hapticFeedbackMode,
                isEInkMode = eInkProfile.isEnabled,
            ) {
                PlayerControls(
                    viewModel = viewModel,
                    onBackPress = {
                        if (isPipSupportedAndEnabled &&
                            player.paused == false &&
                            playerPreferences.pipOnExit().get()
                        ) {
                            enterPipModeSafely(finishOnFailure = true)
                        } else {
                            finish()
                        }
                    },
                    modifier = Modifier.onGloballyPositioned {
                        pipRect = run {
                            val boundsInWindow = it.boundsInWindow()
                            Rect(
                                boundsInWindow.left.toInt(),
                                boundsInWindow.top.toInt(),
                                boundsInWindow.right.toInt(),
                                boundsInWindow.bottom.toInt(),
                            )
                        }
                    },
                )
            }
        }

        onNewIntent(this.intent)
    }

    override fun onDestroy() {
        presenceJob?.cancel()
        presenceJob = null
        initJob?.cancel()
        initJob = null
        fileLoadedJob?.cancel()
        fileLoadedJob = null
        customButtonsJob?.cancel()
        customButtonsJob = null
        changeEpisodeJob?.cancel()
        changeEpisodeJob = null
        torrentResolveJob?.cancel()
        torrentResolveJob = null
        viewModel.cancelPlayerJobs()
        Injekt.get<DiscordPresenceManager>().clearSession(this)

        // Allow achievement notifications when exiting player
        eu.kanade.presentation.achievement.components.AchievementBannerManager.setInReaderOrPlayer(false)

        player.isExiting = true
        isDucked = false
        preDuckVolume = null
        restoreAudioFocus = {}

        audioFocusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
        }
        audioFocusRequest = null

        mediaSession?.let {
            it.isActive = false
            it.release()
        }

        unregisterPipReceiver()

        if (noisyReceiver.initialized) {
            unregisterReceiver(noisyReceiver)
            noisyReceiver.initialized = false
        }

        resetDisplayRefreshRateIfNeeded()

        mpvLifecycleLock.write {
            if (!isMpvDestroyed && isMpvInitialized) {
                isMpvDestroyed = true
                isMpvInitialized = false
                MPVLib.removeLogObserver(playerObserver)
                MPVLib.removeObserver(playerObserver)
                player.destroy()
            } else {
                isMpvDestroyed = true
                isMpvInitialized = false
            }
        }

        viewModel.detachActivity(this)

        super.onDestroy()
    }

    override fun onPause() {
        viewModel.saveCurrentEpisodeWatchingProgress()

        if (isInPictureInPictureMode) {
            super.onPause()
            return
        }

        player.isExiting = true
        if (isFinishing) {
            viewModel.deletePendingEpisodes()
            withActiveMpv(requireNotExiting = false) {
                MPVLib.command(arrayOf("stop"))
            }
        } else {
            viewModel.pause()
        }

        super.onPause()
    }

    override fun onStop() {
        window.attributes.screenBrightness.let {
            if (playerPreferences.rememberPlayerBrightness().get() && it != -1f) {
                playerPreferences.playerBrightnessValue().set(it)
            }
        }

        viewModel.pause()
        viewModel.saveCurrentEpisodeWatchingProgress()

        if (!isInPictureInPictureMode) {
            viewModel.cancelInFlightPrefetch()
            resetDisplayRefreshRateIfNeeded()
            updateKeepScreenOnFlag(isPaused = true)
        }

        if (isInPictureInPictureMode && powerManager.isInteractive) {
            viewModel.deletePendingEpisodes()
            if (!isFinishing && !isChangingConfigurations) {
                finish()
            }
        }

        presenceJob?.cancel()
        presenceJob = null
        Injekt.get<DiscordPresenceManager>().clearSession(this)
        super.onStop()
    }

    private fun observePresenceEpisode() {
        presenceJob?.cancel()
        val manager = Injekt.get<DiscordPresenceManager>()
        presenceJob = combine(viewModel.currentAnime, viewModel.currentEpisode) { anime, episode ->
            anime to episode
        }
            .onEach { (anime, episode) ->
                if (anime == null || episode == null) return@onEach
                val incognito = Injekt.get<GetAnimeIncognitoState>().await(anime.source)
                if (incognito) {
                    manager.clearSession(this)
                } else {
                    manager.setSession(
                        this,
                        DiscordPresenceInfo(
                            mediaKind = DiscordPresenceInfo.MediaKind.ANIME,
                            title = anime.title,
                            primaryNumber = episode.episode_number.toDouble(),
                            secondaryLine = episode.name,
                            startedAt = presenceStartedAt,
                        ),
                    )
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onUserLeaveHint() {
        if (isPipSupportedAndEnabled && player.paused == false && playerPreferences.pipOnExit().get()) {
            enterPipModeSafely()
        }
        super.onUserLeaveHint()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.sheetShown.value != Sheets.None) {
            viewModel.showSheet(Sheets.None)
            return
        }
        if (viewModel.panelShown.value != Panels.None) {
            viewModel.showPanel(Panels.None)
            return
        }
        if (viewModel.dialogShown.value != Dialogs.None) {
            viewModel.showDialog(Dialogs.None)
            return
        }
        if (isPipSupportedAndEnabled && player.paused == false && playerPreferences.pipOnExit().get()) {
            enterPipModeSafely(finishOnFailure = true)
        } else {
            super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        presenceStartedAt = System.currentTimeMillis()
        observePresenceEpisode()
        updatePipParamsSafely()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        updateKeepScreenOnFlag(isPaused = viewModel.paused.value)
        binding.root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LOW_PROFILE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (playerPreferences.playerFullscreen().get()) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                }
            }
        }

        if (playerPreferences.rememberPlayerBrightness().get()) {
            playerPreferences.playerBrightnessValue().get().let {
                if (it != -1f) viewModel.changeBrightnessTo(it)
            }
        }
    }

    private fun executeMPVCommand(commands: Array<String>) {
        withActiveMpv(requireNotExiting = true) {
            MPVLib.command(commands)
        }
    }

    private fun UniFile.writeTextIfChanged(text: String) {
        val expectedBytes = text.toByteArray()
        if (this.length() == expectedBytes.size.toLong()) {
            val matches = runCatching {
                this.openInputStream().use { it.readBytes().contentEquals(expectedBytes) }
            }.getOrDefault(false)
            if (matches) return
        }
        this.openOutputStream().use {
            it.write(expectedBytes)
        }
    }

    private fun setupPlayerMPV() {
        val logLevel = if (networkPreferences.verboseLogging().get()) "info" else "warn"

        val mpvDir = UniFile.fromFile(applicationContext.filesDir)!!.createDirectory(MPV_DIR)!!

        val mpvConfFile = mpvDir.createFile("mpv.conf")!!
        advancedPlayerPreferences.mpvConf().get().let { mpvConfFile.writeTextIfChanged(it) }
        val mpvInputFile = mpvDir.createFile("input.conf")!!
        advancedPlayerPreferences.mpvInput().get().let { mpvInputFile.writeTextIfChanged(it) }

        copyUserFiles(mpvDir)
        val anime4kPreset = decoderPreferences.anime4kShaderPreset().get()
        player.stagedAnime4kShaderOption = if (copyAnime4kShaders(mpvDir, anime4kPreset)) {
            anime4kPreset.mpvShaderOption()
        } else {
            if (anime4kPreset != Anime4KShaderPreset.Off) {
                logcat(LogPriority.WARN) {
                    "Anime4K preset $anime4kPreset could not be staged and was disabled"
                }
            }
            null
        }
        copyAssets(mpvDir)
        // Stage the user fonts synchronously so they are on disk before MPV initializes its
        // subtitle renderer; the fonts directory is registered right after initialize below.
        val fontsDirectory = PlayerFontBridge.copyFontsDirectory(storageManager, mpvDir)

        player.initialize(
            configDir = mpvDir.filePath!!,
            cacheDir = applicationContext.cacheDir.path,
            logLvl = logLevel,
        )
        isMpvDestroyed = false
        isMpvInitialized = true
        PlayerFontBridge.setFontsDirectories(fontsDirectory)
        MPVLib.addLogObserver(playerObserver)
        MPVLib.addObserver(playerObserver)
    }

    private fun copyUserFiles(mpvDir: UniFile) {
        val scriptsDir = mpvDir.createDirectory(MPV_SCRIPTS_DIR)
        val scriptOptsDir = mpvDir.createDirectory(MPV_SCRIPTS_OPTS_DIR)
        val shadersDir = mpvDir.createDirectory(MPV_SHADERS_DIR)

        val userFilesEnabled = advancedPlayerPreferences.mpvUserFiles().get()
        syncUserDirectory(
            sourceDir = if (userFilesEnabled) storageManager.getScriptsDirectory() else null,
            targetDir = scriptsDir,
            preserveNames = setOf("aniyomi.lua", "custombuttons.lua"),
        )
        syncUserDirectory(
            sourceDir = if (userFilesEnabled) storageManager.getScriptOptsDirectory() else null,
            targetDir = scriptOptsDir,
        )
        syncUserDirectory(
            sourceDir = if (userFilesEnabled) storageManager.getShadersDirectory() else null,
            targetDir = shadersDir,
            preserveNames = setOf("anime4k"),
        )

        // Copy over the bridge file only when missing or size changed
        val luaFile = scriptsDir?.createFile("aniyomi.lua") ?: return
        runCatching {
            assets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING).use { input ->
                val expectedSize = input.available().toLong()
                if (expectedSize > 0L && luaFile.length() == expectedSize) {
                    return@use
                }
                luaFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.onFailure { e ->
            logcat(LogPriority.ERROR, e) { "Failed to stage aniyomi.lua" }
        }
    }

    private fun syncUserDirectory(
        sourceDir: UniFile?,
        targetDir: UniFile?,
        preserveNames: Set<String> = emptySet(),
    ) {
        if (targetDir == null) return
        val sourceFiles = sourceDir?.listFiles()?.filter { it.isFile && it.name != null }.orEmpty()
        val keptNames = HashSet<String>(sourceFiles.size + preserveNames.size)
        keptNames.addAll(preserveNames)

        sourceFiles.forEach { file ->
            val name = file.name ?: return@forEach
            if (name in preserveNames) return@forEach
            keptNames.add(name)

            val existing = targetDir.findFile(name)
            val sourceLength = file.length()
            val sourceLastModified = file.lastModified()
            if (
                existing != null &&
                existing.isFile &&
                sourceLength >= 0L &&
                existing.length() == sourceLength &&
                (sourceLastModified <= 0L || existing.lastModified() >= sourceLastModified)
            ) {
                return@forEach
            }

            val outFile = existing ?: targetDir.createFile(name) ?: return@forEach
            runCatching {
                file.openInputStream().use { input ->
                    outFile.openOutputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.onFailure { e ->
                logcat(LogPriority.WARN, e) { "Failed to sync user MPV file: $name" }
            }
        }

        targetDir.listFiles()?.forEach { existing ->
            val name = existing.name ?: return@forEach
            if (name !in keptNames) {
                existing.delete()
            }
        }
    }

    private fun copyAnime4kShaders(mpvDir: UniFile, preset: Anime4KShaderPreset): Boolean {
        if (preset == Anime4KShaderPreset.Off) return true

        val targetRoot = mpvDir.createDirectory(MPV_SHADERS_DIR)?.createDirectory("anime4k")
            ?: return false

        for (shaderPath in preset.shaderPaths()) {
            val targetFile = createAnime4kShaderTarget(targetRoot, shaderPath) ?: run {
                logcat(LogPriority.WARN) { "Failed to prepare Anime4K shader target: $shaderPath" }
                return false
            }

            try {
                assets.open("shaders/anime4k/$shaderPath", AssetManager.ACCESS_STREAMING).use { input ->
                    val expectedLength = input.available().toLong()
                    if (expectedLength > 0L && targetFile.length() == expectedLength) {
                        return@use
                    }
                    targetFile.openOutputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                logcat(LogPriority.WARN) { "Failed to copy Anime4K shader: $shaderPath" }
                return false
            }
        }

        return true
    }

    private fun createAnime4kShaderTarget(root: UniFile, shaderPath: String): UniFile? {
        val parentPath = shaderPath.substringBeforeLast('/', "")
        var current = root

        if (parentPath.isNotEmpty()) {
            for (segment in parentPath.split('/')) {
                if (segment.isEmpty()) continue
                current = current.createDirectory(segment) ?: return null
            }
        }

        return current.createFile(shaderPath.substringAfterLast('/'))
    }

    private fun copyAssets(mpvDir: UniFile) {
        val assetManager = this.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
        for (filename in files) {
            var ins: InputStream? = null
            var out: OutputStream? = null
            try {
                ins = assetManager.open(filename, AssetManager.ACCESS_STREAMING)
                val outFile = mpvDir.createFile(filename)!!
                // Note that .available() officially returns an *estimated* number of bytes available
                // this is only true for generic streams, asset streams return the full file size
                if (outFile.length() == ins.available().toLong()) {
                    logcat(LogPriority.VERBOSE) { "Skipping copy of asset file (exists same size): $filename" }
                    continue
                }
                out = outFile.openOutputStream()
                ins.copyTo(out)
                logcat(LogPriority.WARN) { "Copied asset file: $filename" }
            } catch (e: IOException) {
                logcat(LogPriority.ERROR, e) { "Failed to copy asset file: $filename" }
            } finally {
                ins?.close()
                out?.close()
            }
        }
    }

    fun setupCustomButtons(
        buttons: List<CustomButton>,
        enabled: Boolean = true,
    ) {
        customButtonsJob?.cancel()
        customButtonsJob = lifecycleScope.launchIO {
            PlayerCustomButtonBridge.setupCustomButtons(
                filesDir = applicationContext.filesDir,
                buttons = buttons,
                primaryButtonId = viewModel.primaryButton.value?.id ?: 0L,
                enabled = enabled,
            )
        }
    }

    private fun setupPlayerAudio() {
        with(audioPreferences) {
            AudioFilterChainBuilder.applyProperties(
                audioChannels = audioChannels().get(),
                normalizeAudio = audioNormalization().get(),
            )

            val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).also {
                it.setAudioAttributes(
                    AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build(),
                )
                it.setOnAudioFocusChangeListener(audioFocusChangeListener)
            }.build()
            AudioManagerCompat.requestAudioFocus(audioManager, request).let {
                if (it == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return@let
                audioFocusRequest = request
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
        when (it) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                if (player.isExiting || isMpvDestroyed || isFinishing || isDestroyed) return@OnAudioFocusChangeListener
                val oldRestore = restoreAudioFocus
                val wasPlayerPaused = player.paused ?: viewModel.paused.value
                viewModel.pause()
                restoreAudioFocus = {
                    oldRestore()
                    if (!wasPlayerPaused) viewModel.unpause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (player.isExiting || isMpvDestroyed || isFinishing || isDestroyed) return@OnAudioFocusChangeListener
                if (!isDucked) {
                    withActiveMpv(requireNotExiting = true) {
                        val currentVol = MPVLib.getPropertyDouble("volume") ?: 100.0
                        preDuckVolume = currentVol
                        isDucked = true
                        MPVLib.setPropertyDouble("volume", currentVol * 0.5)
                    }
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isDucked) {
                    val savedVolume = preDuckVolume
                    isDucked = false
                    preDuckVolume = null
                    if (savedVolume != null) {
                        withActiveMpv {
                            MPVLib.setPropertyDouble("volume", savedVolume)
                        }
                    }
                }
                val restore = restoreAudioFocus
                restoreAudioFocus = {}
                if (!player.isExiting &&
                    !isMpvDestroyed &&
                    !isFinishing &&
                    !isDestroyed &&
                    (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || isInPictureInPictureMode)
                ) {
                    restore()
                }
            }

            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                logcat(LogPriority.DEBUG) { "didn't get audio focus" }
            }
        }
    }

    override fun onResume() {
        if (!player.isExiting) {
            super.onResume()
            syncKeepScreenOnFromPlayer()
            return
        }

        player.isExiting = false
        super.onResume()

        viewModel.currentVolume.update {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also {
                if (it < viewModel.maxVolume) viewModel.changeMPVVolumeTo(100)
            }
        }
        syncKeepScreenOnFromPlayer()
        if (!isInPictureInPictureMode &&
            !viewModel.isLoadingEpisode.value &&
            viewModel.currentVideo.value != null
        ) {
            matchDisplayRefreshRateIfEnabled()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            viewModel.changeVideoAspect(playerPreferences.aspectState().get())
        } else {
            viewModel.hideControls()
        }
        super.onConfigurationChanged(newConfig)
    }

    fun showToast(message: String) {
        runOnUiThread { toast(message) }
    }

    internal fun onPlaybackError(errorMessage: String) {
        if (player.isExiting) return
        val fellBack = viewModel.tryFallbackAfterPlaybackError(errorMessage)
        if (!fellBack) {
            viewModel.updateIsLoadingEpisode(false)
            viewModel.updateIsLoading(false)
            toast(errorMessage, Toast.LENGTH_LONG)
        }
    }

    // A bunch of observers

    internal fun onObserverEvent(property: String, value: Long) {
        if (player.isExiting) return
        when (property) {
            "time-pos" -> {
                viewModel.updatePlayBackPos(value.toFloat())
                viewModel.setChapter(value.toFloat())
            }
            "demuxer-cache-time" -> viewModel.updateReadAhead(value = value)
            "cache-buffering-state" -> viewModel.updateBufferingPercent(value.toInt())
            "cache-speed" -> if (viewModel.isLoading.value) viewModel.updateCacheSpeed(value)
            "volume" -> viewModel.setMPVVolume(value.toInt())
            "volume-max" -> viewModel.volumeBoostCap = value.toInt() - 100
            // "chapter" -> viewModel.updateChapter(value)
            "duration" -> viewModel.duration.update { value.toFloat() }
            "user-data/current-anime/intro-length" -> viewModel.setAnimeSkipIntroLength(value)
        }
    }

    internal fun onObserverEvent(property: String) {
        if (player.isExiting) return
        when (property) {
            "chapter-list" -> {
                viewModel.loadChapters()
                viewModel.updateChapter(0)
            }
            "track-list" -> viewModel.loadTracks()
        }
    }

    internal fun onObserverEvent(property: String, value: Boolean) {
        if (player.isExiting) return
        when (property) {
            "pause" -> {
                if (value && player.paused == true) {
                    viewModel.pause()
                    updateKeepScreenOnFlag(isPaused = true)
                } else if (!value && player.paused == false) {
                    viewModel.unpause()
                    updateKeepScreenOnFlag(isPaused = false)
                }

                updatePipParamsSafely()
            }

            "paused-for-cache" -> {
                viewModel.updateIsLoading(value)
            }

            "seeking" -> {
                viewModel.updateIsLoading(value)
            }

            "eof-reached" -> {
                endFile(value)
            }
        }
    }

    val trackId: (String) -> Int? = ::parseMpvTrackId

    internal fun onObserverEvent(property: String, value: String) {
        if (player.isExiting) return
        when (property.substringBeforeLast("/")) {
            "aid" -> trackId(value)?.let { viewModel.updateAudio(it) }
            "sid" -> trackId(value)?.let { viewModel.updateSubtitle(it, viewModel.selectedSubtitles.value.second) }
            "secondary-sid" -> trackId(value)?.let {
                viewModel.updateSubtitle(viewModel.selectedSubtitles.value.first, it)
            }
            "hwdec", "hwdec-current" -> {
                if (viewModel.currentVideo.value != null) {
                    viewModel.getDecoder()
                }
            }
            "user-data/aniyomi" -> viewModel.handleLuaInvocation(property, value)
        }
    }

    @SuppressLint("NewApi")
    internal fun onObserverEvent(property: String, value: Double) {
        if (player.isExiting) return
        when (property) {
            "speed" -> viewModel.playbackSpeed.update { value.toFloat() }
            "video-params/aspect" -> if (isPipSupportedAndEnabled) {
                updatePipParamsSafely()
            }
        }
    }

    internal fun event(eventId: Int) {
        if (player.isExiting) return
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                fileLoadedJob?.cancel()
                fileLoadedJob = lifecycleScope.launchIO { fileLoaded() }
            }
            MPVLib.mpvEventId.MPV_EVENT_SEEK -> viewModel.isLoading.update { true }
            MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> {
                player.isExiting = false
                val pausedForCache = withActiveMpv { MPVLib.getPropertyBoolean("paused-for-cache") } == true
                val seeking = withActiveMpv { MPVLib.getPropertyBoolean("seeking") } == true
                if (!pausedForCache && !seeking) {
                    viewModel.updateIsLoading(false)
                }
            }
        }
    }

    fun createPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val anime = viewModel.currentAnime.value
            val episode = viewModel.currentEpisode.value

            if (anime != null && episode != null) {
                builder.setTitle(anime.title).setSubtitle(episode.name)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = isPipSupportedAndEnabled && playerPreferences.pipOnExit().get()
            builder.setAutoEnterEnabled(player.paused == false && autoEnter)
            builder.setSeamlessResizeEnabled(player.paused == false && autoEnter)
        }
        builder.setActions(
            createPipActions(
                context = this,
                isPaused = player.paused ?: true,
                replaceWithPrevious = playerPreferences.pipReplaceWithPrevious().get(),
                playlistCount = viewModel.currentPlaylist.value.size,
                playlistPosition = viewModel.getCurrentEpisodeIndex(),
            ),
        )
        builder.setSourceRectHint(pipRect)
        // Avoid querying mpv video geometry before a stream has finished loading, which
        // spams mpv_get_property errors during Activity startup and episode switches.
        if (!viewModel.isLoadingEpisode.value && viewModel.currentVideo.value != null) {
            val height = player.videoH
            val aspect = player.getVideoOutAspect()
            if (height != null && height > 0 && aspect != null && aspect > 0.0) {
                val width = (height * aspect).toInt()
                if (width > 0) {
                    val ratio = width.toFloat() / height.toFloat()
                    if (ratio in 0.42f..2.38f) {
                        builder.setAspectRatio(Rational(width, height))
                    }
                }
            }
        }
        return builder.build()
    }

    private fun unregisterPipReceiver() {
        pipReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
            pipReceiver = null
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            unregisterPipReceiver()
            matchDisplayRefreshRateIfEnabled()
        } else {
            resetDisplayRefreshRateIfNeeded()
            unregisterPipReceiver()
            updatePipParamsSafely()
            viewModel.hideControls()
            viewModel.hideSeekBar()
            viewModel.isBrightnessSliderShown.update { false }
            viewModel.isVolumeSliderShown.update { false }
            viewModel.sheetShown.update { Sheets.None }
            pipReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || intent.action != PIP_INTENTS_FILTER) return
                    when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
                        PIP_PAUSE -> viewModel.pause()
                        PIP_PLAY -> viewModel.unpause()
                        PIP_NEXT -> viewModel.changeEpisode(false)
                        PIP_PREVIOUS -> viewModel.changeEpisode(true)
                        PIP_SKIP -> viewModel.seekBy(10)
                    }
                    updatePipParamsSafely()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER))
            }
        }

        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private fun setupPlayerOrientation() {
        if (player.isExiting) return
        requestedOrientation = when (playerPreferences.defaultPlayerOrientationType().get()) {
            PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            PlayerOrientation.Video -> if ((player.getVideoOutAspect() ?: 0.0) > 1.0) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

            PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.changeVolumeBy(1)
                viewModel.displayVolumeSlider()
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.changeVolumeBy(-1)
                viewModel.displayVolumeSlider()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleRightDoubleTap()
            KeyEvent.KEYCODE_SPACE -> viewModel.pauseUnpause()
            KeyEvent.KEYCODE_MEDIA_STOP -> finishAndRemoveTask()

            KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()

            // other keys should be bound by the user in input.conf ig
            else -> {
                event?.let { player.onKey(it) }
                super.onKeyDown(keyCode, event)
            }
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (player.onKey(event!!)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun setupMediaSession() {
        val previousAction = gesturePreferences.mediaPreviousGesture().get()
        val playAction = gesturePreferences.mediaPlayPauseGesture().get()
        val nextAction = gesturePreferences.mediaNextGesture().get()

        mediaSession = MediaSession(this, "PlayerActivity").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPlay()
                                viewModel.unpause()
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            SingleActionGesture.Custom -> {
                                withActiveMpv(requireNotExiting = true) {
                                    MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPlay.keyCode))
                                }
                            }

                            SingleActionGesture.Switch -> {}
                        }
                    }

                    override fun onPause() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPause()
                                viewModel.pause()
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            SingleActionGesture.Custom -> {
                                withActiveMpv(requireNotExiting = true) {
                                    MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPlay.keyCode))
                                }
                            }

                            SingleActionGesture.Switch -> {}
                        }
                    }

                    override fun onSkipToPrevious() {
                        when (previousAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.leftSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                withActiveMpv(requireNotExiting = true) {
                                    MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPrevious.keyCode))
                                }
                            }

                            SingleActionGesture.Switch -> viewModel.changeEpisode(true)
                        }
                    }

                    override fun onSkipToNext() {
                        when (nextAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.rightSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                withActiveMpv(requireNotExiting = true) {
                                    MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaNext.keyCode))
                                }
                            }

                            SingleActionGesture.Switch -> viewModel.changeEpisode(false)
                        }
                    }

                    override fun onStop() {
                        super.onStop()
                        isActive = false
                        viewModel.pause()
                        finish()
                    }
                },
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_STOP or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_SKIP_TO_NEXT,
                    )
                    .build(),
            )
            isActive = true
        }

        val filter = IntentFilter().apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }
        registerReceiver(noisyReceiver, filter)
        noisyReceiver.initialized = true
    }

    // ==== END MPVKT ====

    override fun onSaveInstanceState(outState: Bundle) {
        if (!isChangingConfigurations) {
            viewModel.onSaveInstanceStateNonConfigurationChange()
        }
        super.onSaveInstanceState(outState)
    }

    /**
     * Switches to the episode based on [episodeId],
     * @param episodeId id of the episode to switch the player to
     * @param autoPlay whether the episode is switching due to auto play
     */
    internal fun reloadPlayerPreservingProgress() {
        viewModel.sheetShown.update { _ -> Sheets.None }
        viewModel.panelShown.update { _ -> Panels.None }
        viewModel.saveCurrentEpisodeWatchingProgress()
        val animeId = viewModel.currentAnime.value?.id
        val episodeId = viewModel.currentEpisode.value?.id
        if (animeId != null && episodeId != null) {
            intent.putExtra("animeId", animeId)
            intent.putExtra("episodeId", episodeId)
            intent.removeExtra("hostList")
            intent.removeExtra("hostIndex")
            intent.removeExtra("vidIndex")
        }
        viewModel.prepareForReload(resetQuality = true)
        recreate()
    }

    internal fun changeEpisode(episodeId: Long?, autoPlay: Boolean = false) {
        viewModel.saveCurrentEpisodeWatchingProgress()
        viewModel.sheetShown.update { _ -> Sheets.None }
        viewModel.panelShown.update { _ -> Panels.None }
        viewModel.pause()
        viewModel.isLoading.update { _ -> true }
        viewModel.resetHosterState()

        changeEpisodeJob?.cancel()
        changeEpisodeJob = lifecycleScope.launch {
            viewModel.updateIsLoadingEpisode(true)
            viewModel.updateIsLoadingHosters(true)
            viewModel.cancelHosterVideoLinksJob()

            val pipEpisodeToasts = playerPreferences.pipEpisodeToasts().get()
            val switchMethod = viewModel.loadEpisode(episodeId)

            viewModel.updateHasPreviousEpisode(
                viewModel.getCurrentEpisodeIndex() != 0,
            )
            viewModel.updateHasNextEpisode(
                viewModel.getCurrentEpisodeIndex() != viewModel.currentPlaylist.value.size - 1,
            )

            viewModel.updateIsLoadingHosters(false)

            when (switchMethod) {
                null -> {
                    if (viewModel.currentAnime.value != null && !autoPlay) {
                        launchUI { toast(AYMR.strings.no_next_episode) }
                    }
                    viewModel.updateIsLoadingEpisode(false)
                    viewModel.updateIsLoading(false)
                }

                else -> {
                    if (switchMethod.hosterList != null) {
                        when {
                            switchMethod.hosterList.isEmpty() -> {
                                viewModel.updateIsLoadingEpisode(false)
                                viewModel.updateIsLoading(false)
                                setInitialEpisodeError(
                                    PlayerViewModel.ExceptionWithStringResource(
                                        "Hoster list is empty",
                                        AYMR.strings.no_hosters,
                                    ),
                                )
                            }
                            else -> {
                                viewModel.loadHosters(
                                    source = switchMethod.source,
                                    hosterList = switchMethod.hosterList,
                                    hosterIndex = -1,
                                    videoIndex = -1,
                                )
                            }
                        }
                    } else {
                        logcat(LogPriority.ERROR) { "Error getting links" }
                        viewModel.updateIsLoadingEpisode(false)
                        viewModel.updateIsLoading(false)
                        launchUI { toast(AYMR.strings.no_hosters) }
                    }

                    if (isInPictureInPictureMode && pipEpisodeToasts) {
                        launchUI { toast(switchMethod.episodeTitle) }
                    }
                }
            }
        }
    }

    fun setVideo(video: Video?, position: Long? = null) {
        if (player.isExiting || isMpvDestroyed) return
        if (video == null) return

        setHttpOptions(video)

        if (viewModel.isLoadingEpisode.value) {
            viewModel.currentEpisode.value?.let { episode ->
                val preservePos = playerPreferences.preserveWatchingPosition().get()
                val resumePosition = position
                    ?: if (episode.seen && !preservePos) {
                        0L
                    } else {
                        episode.last_second_seen
                    }
                withActiveMpv(requireNotExiting = true) {
                    MPVLib.command(
                        arrayOf("set", "start", MpvStartupOptions.formatStartArgumentFromMillis(resumePosition)),
                    )
                }
            }
        } else {
            player.timePos?.let { currentPos ->
                withActiveMpv(requireNotExiting = true) {
                    MPVLib.command(
                        arrayOf("set", "start", MpvStartupOptions.formatStartArgumentFromSeconds(currentPos)),
                    )
                }
            }
        }

        val videoOptions = video.mpvArgs.joinToString(",") { (option, value) ->
            "$option=\"$value\""
        }

        if (torrentPlaybackResolver.isTorrentLikeUrl(video.videoUrl)) {
            torrentResolveJob?.cancel()
            torrentResolveJob = lifecycleScope.launchIO {
                try {
                    val playUrl = torrentPlaybackResolver.resolve(video.videoUrl, video.videoTitle)
                    withUIContext {
                        if (player.isExiting || isMpvDestroyed || isDestroyed || isFinishing) {
                            return@withUIContext
                        }
                        loadMpvFile(playUrl, videoOptions)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Failed to resolve torrent stream" }
                    withUIContext {
                        if (!isDestroyed && !isFinishing) {
                            toast(e.message ?: stringResource(AYMR.strings.torrserver_disabled))
                            finish()
                        }
                    }
                }
            }
        } else {
            loadMpvFile(parseVideoUrl(video.videoUrl), videoOptions)
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the videos of the episode. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialEpisodeError(error: Throwable) {
        if (error is PlayerViewModel.ExceptionWithStringResource) {
            toast(error.stringResource)
        } else {
            toast(error.message)
        }
        logcat(LogPriority.ERROR, error)
        finish()
    }

    private fun loadMpvFile(url: String?, videoOptions: String) {
        withActiveMpv(requireNotExiting = true) {
            MPVLib.command(
                arrayOf(
                    "loadfile",
                    url,
                    "replace",
                    "0",
                    videoOptions,
                ),
            )
        }
    }

    fun parseVideoUrl(videoUrl: String?): String? {
        return videoUrl?.toUri()?.resolveUri(this)
            ?: videoUrl
    }

    fun setHttpOptions(video: Video) {
        if (viewModel.isEpisodeOnline() != true) return
        val source = viewModel.currentSource.value as? AnimeHttpSource ?: return

        val options = toPlaybackHttpOptions(
            sourceHeaders = source.headers,
            videoHeaders = video.headers,
        )

        val httpHeaderString = options.headers.map {
            it.key + ": " + it.value.replace(",", "\\,")
        }.joinToString(",")

        withActiveMpv(requireNotExiting = true) {
            MPVLib.setOptionString("user-agent", options.userAgent.orEmpty())
            MPVLib.setOptionString("referrer", options.referrer.orEmpty())
            MPVLib.setOptionString("http-header-fields", httpHeaderString)
        }

        // need to fix the cache
        // MPVLib.setOptionString("cache-on-disk", "yes")
        // val cacheDir = File(applicationContext.filesDir, "media").path
        // MPVLib.setOptionString("cache-dir", cacheDir)
    }

    /**
     * Called from the presenter when a screenshot is ready to be shared. It shows Android's
     * default sharing tool.
     */
    private fun onShareImageResult(uri: Uri, seconds: String) {
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value ?: return

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = stringResource(AYMR.strings.share_screenshot_info, anime.title, episode.name, seconds),
        )
        startActivity(Intent.createChooser(intent, stringResource(MR.strings.action_share)))
    }

    /**
     * Called from the presenter when a screenshot is saved or fails. It shows a message
     * or logs the event depending on the [result].
     */
    private fun onSaveImageResult(result: PlayerViewModel.SaveImageResult) {
        when (result) {
            is PlayerViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is PlayerViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a screenshot is set as art or fails.
     * It shows a different message depending on the [result].
     */
    private fun onSetAsArtResult(result: SetAsArt, artType: ArtType) {
        toast(
            when (result) {
                SetAsArt.Success ->
                    when (artType) {
                        ArtType.Cover -> MR.strings.cover_updated
                        ArtType.Background -> AYMR.strings.background_updated
                        ArtType.Thumbnail -> AYMR.strings.thumbnail_updated
                    }
                SetAsArt.AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                SetAsArt.Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    // TODO: exception java.util.ConcurrentModificationException:
    //  UPDATE: MAY HAVE BEEN FIXED
    // at java.lang.Object java.util.ArrayList$Itr.next() (ArrayList.java:860)
    // at void eu.kanade.tachiyomi.ui.player.PlayerActivity.fileLoaded() (PlayerActivity.kt:1874)
    // at void eu.kanade.tachiyomi.ui.player.PlayerActivity.event(int) (PlayerActivity.kt:1566)
    // at void is.xyz.mpv.MPVLib.event(int) (MPVLib.java:86)
    private suspend fun fileLoaded() {
        if (player.isExiting || isMpvDestroyed) return
        setMpvOptions()
        setMpvMediaTitle()
        withUIContext {
            if (!player.isExiting && !isMpvDestroyed && !isDestroyed) {
                setupPlayerOrientation()
            }
        }
        matchDisplayRefreshRateIfEnabled()
        setupChapters()
        viewModel.resetSubtitleSelection()
        setupTracks()
        if (player.isExiting || isMpvDestroyed) return
        val duration = withActiveMpv(requireNotExiting = true) { player.duration }
        playerStartupCoordinator.handleAniSkip(
            playerDuration = duration,
            waitingSkipIntro = playerPreferences.waitingTimeIntroSkip().get(),
            introSkipEnabled = viewModel.introSkipEnabled,
            aniSkipEnabled = playerPreferences.aniSkipEnabled().get(),
            disableAniSkipOnChapters = playerPreferences.disableAniSkipOnChapters().get(),
        )
    }

    private fun setMpvOptions() {
        if (player.isExiting || isMpvDestroyed) return
        val video = viewModel.currentVideo.value ?: return

        // Only check for `MPV_ARGS_TAG` on downloaded videos
        if (listOf("file", "content", "data").none { video.videoUrl.startsWith(it) }) {
            return
        }

        try {
            val rawMetadata = withActiveMpv(requireNotExiting = true) {
                MPVLib.getPropertyString("metadata")
            } ?: return
            val metadata = Json.decodeFromString<Map<String, String>>(rawMetadata)

            val opts = metadata[Video.MPV_ARGS_TAG]
                ?.split(";")
                ?.map { it.split("=", limit = 2) }
                ?: return

            withActiveMpv(requireNotExiting = true) {
                opts.forEach { (option, value) ->
                    MPVLib.setPropertyString(option, value)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to read video metadata" }
        }
    }

    private suspend fun setupTracks() {
        if (player.isExiting || isMpvDestroyed) return
        viewModel.isLoadingTracks.update { _ -> true }

        val audioTracks = viewModel.currentVideo.value?.audioTracks?.takeIf { it.isNotEmpty() }
        val subtitleTracks = viewModel.currentVideo.value?.subtitleTracks?.takeIf { it.isNotEmpty() }

        audioTracks?.forEach { audio ->
            if (audio.url.isNotBlank()) {
                executeMPVCommand(arrayOf("audio-add", audio.url, "auto", audio.lang))
            }
        }
        subtitleTracks?.forEach { sub ->
            if (sub.url.isNotBlank()) {
                executeMPVCommand(arrayOf("sub-add", sub.url, "auto", sub.lang))
            }
        }

        viewModel.awaitAndFinishTracks(
            hasExternalTracks = audioTracks != null || subtitleTracks != null,
        )
    }

    private fun setupChapters() {
        if (player.isExiting || isMpvDestroyed) return

        val timestamps = viewModel.currentVideo.value?.timestamps?.takeIf { it.isNotEmpty() }
            ?.map { timestamp ->
                if (timestamp.name.isEmpty() && timestamp.type != ChapterType.Other) {
                    timestamp.copy(
                        name = timestamp.type.getStringRes()?.let(::stringResource) ?: "",
                    )
                } else {
                    timestamp
                }
            }
            ?: return

        val duration = withActiveMpv(requireNotExiting = true) { player.duration }
        viewModel.updateChapters(
            ChapterUtils.mergeChapters(
                currentChapters = viewModel.chapters.value,
                stamps = timestamps,
                duration = duration,
            ),
        )
        viewModel.setChapter(viewModel.pos.value)
    }

    private fun setMpvMediaTitle() {
        if (player.isExiting || isMpvDestroyed) return
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value ?: return

        val epNumber = episode.episode_number.let { number ->
            if (ceil(number) == floor(number)) number.toInt() else number
        }.toString().padStart(2, '0')

        val title = stringResource(
            AYMR.strings.mpv_media_title,
            anime.title,
            epNumber,
            episode.name,
        )

        withActiveMpv(requireNotExiting = true) {
            // Write to mpv table
            MPVLib.setPropertyString("user-data/current-anime/episode-title", episode.name)
            MPVLib.setPropertyString("force-media-title", title)
        }
    }

    internal fun shouldDispatchCacheSpeed(): Boolean {
        return isViewModelReady && !player.isExiting && viewModel.shouldObserveCacheSpeed()
    }

    private fun syncKeepScreenOnFromPlayer() {
        val isPaused = if (viewModel.currentVideo.value != null) {
            withActiveMpv { player.paused } ?: viewModel.paused.value
        } else {
            viewModel.paused.value
        }
        updateKeepScreenOnFlag(isPaused = isPaused)
    }

    private fun updateKeepScreenOnFlag(isPaused: Boolean) {
        if (isPaused) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun matchDisplayRefreshRateIfEnabled() {
        if (player.isExiting || isMpvDestroyed) return
        if (!decoderPreferences.matchDisplayRefreshRate().get()) {
            if (didOverrideDisplayMode) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && !player.isExiting) {
                        resetDisplayRefreshRateIfNeeded()
                    }
                }
            }
            return
        }
        val videoFps = withActiveMpv(requireNotExiting = true) {
            MPVLib.getPropertyDouble("container-fps")
        }
        if (videoFps == null || videoFps <= 0.0) {
            if (didOverrideDisplayMode) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && !player.isExiting) {
                        resetDisplayRefreshRateIfNeeded()
                    }
                }
            }
            return
        }
        runOnUiThread {
            if (isFinishing || isDestroyed || player.isExiting) return@runOnUiThread
            val activeDisplay = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }
            }.getOrNull() ?: return@runOnUiThread

            val currentMode = activeDisplay.mode ?: return@runOnUiThread
            val modes = activeDisplay.supportedModes.map { mode ->
                DisplayModeInfo(
                    modeId = mode.modeId,
                    physicalWidth = mode.physicalWidth,
                    physicalHeight = mode.physicalHeight,
                    refreshRate = mode.refreshRate,
                )
            }
            val optimalModeId = DisplayRefreshRateMatcher.selectOptimalModeId(
                videoFps = videoFps,
                availableModes = modes,
                currentModeId = currentMode.modeId,
            )
            if (optimalModeId == null) {
                resetDisplayRefreshRateIfNeeded()
                return@runOnUiThread
            }
            if (optimalModeId == currentMode.modeId) {
                return@runOnUiThread
            }
            if (!didOverrideDisplayMode) {
                baselineDisplayModeId = currentMode.modeId
            } else if (optimalModeId == baselineDisplayModeId) {
                resetDisplayRefreshRateIfNeeded()
                return@runOnUiThread
            }

            window.attributes = window.attributes.apply {
                preferredDisplayModeId = optimalModeId
            }
            didOverrideDisplayMode = true
        }
    }

    private fun resetDisplayRefreshRateIfNeeded() {
        if (!didOverrideDisplayMode) return
        didOverrideDisplayMode = false
        baselineDisplayModeId = null
        runCatching {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = 0
            }
        }
    }

    private fun endFile(eofReached: Boolean) {
        if (!eofReached) return
        if (playerPreferences.autoplayEnabled().get()) {
            viewModel.changeEpisode(previous = false, autoPlay = true)
        } else {
            viewModel.pause()
        }
    }
}

internal fun parseMpvTrackId(value: String): Int? {
    return when (value) {
        "auto" -> null
        "no" -> -1
        else -> value.toIntOrNull()
    }
}
