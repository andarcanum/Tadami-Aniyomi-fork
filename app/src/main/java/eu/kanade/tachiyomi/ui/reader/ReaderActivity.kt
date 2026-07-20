package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.transition.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.hippo.unifile.UniFile
import com.tadami.aurora.R
import com.tadami.aurora.databinding.ReaderActivityBinding
import eu.kanade.core.util.ifMangaSourcesLoaded
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.reader.DisplayRefreshHost
import eu.kanade.presentation.reader.OrientationSelectDialog
import eu.kanade.presentation.reader.PageIndicatorText
import eu.kanade.presentation.reader.ReaderChapterListItem
import eu.kanade.presentation.reader.ReaderChapterListSheet
import eu.kanade.presentation.reader.ReaderContentOverlay
import eu.kanade.presentation.reader.ReaderPageActionsDialog
import eu.kanade.presentation.reader.ReadingModeSelectDialog
import eu.kanade.presentation.reader.appbars.BottomBarButtonFlags
import eu.kanade.presentation.reader.appbars.ReaderAppBars
import eu.kanade.presentation.reader.components.AutoScrollActionFab
import eu.kanade.presentation.reader.manga.MangaSeriesInterstitialOverlay
import eu.kanade.presentation.reader.settings.ReaderSettingsDialog
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import eu.kanade.tachiyomi.util.system.isNightMode
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.util.AppHapticsProvider
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import tachiyomi.presentation.core.i18n.stringResource as composeStringResource

// Legacy bridge: this activity still hosts the reader while the Compose migration remains partial.
class ReaderActivity : BaseActivity() {

    companion object {
        fun newIntent(
            context: Context,
            mangaId: Long?,
            chapterId: Long?,
            seriesId: Long? = null,
        ): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                if (seriesId != null) {
                    putExtra(Constants.SERIES_EXTRA, seriesId)
                }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    private val readerPreferences = Injekt.get<ReaderPreferences>()
    private val preferences = Injekt.get<BasePreferences>()
    private val uiPreferences = Injekt.get<UiPreferences>()

    lateinit var binding: ReaderActivityBinding

    val viewModel by viewModels<ReaderViewModel>()
    private var assistUrl: String? = null
    private var seriesId: Long? = null

    private val hasCutout by lazy { hasDisplayCutout() }

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    private var menuToggleToast: Toast? = null
    private var readingModeToast: Toast? = null
    private val displayRefreshHost = DisplayRefreshHost()

    private val windowInsetsController by lazy { WindowInsetsControllerCompat(window, binding.root) }

    private var loadingIndicator: ReaderProgressIndicator? = null

    var isScrollingThroughPages = false
        private set

    private fun isEInkMode(): Boolean = uiPreferences.eInkProfile().get().isEnabled

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val reduceMotion = isEInkMode()
        registerSecureActivity(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                if (reduceMotion) 0 else R.anim.shared_axis_x_push_enter,
                if (reduceMotion) 0 else R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                if (reduceMotion) 0 else R.anim.shared_axis_x_push_enter,
                if (reduceMotion) 0 else R.anim.shared_axis_x_push_exit,
            )
        }

        super.onCreate(savedInstanceState)

        if (reduceMotion) {
            window.sharedElementEnterTransition = null
            window.sharedElementReturnTransition = null
        }

        // Defer achievement notifications while in reader
        eu.kanade.presentation.achievement.components.AchievementBannerManager.setInReaderOrPlayer(true)

        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyReaderSystemBarIconStyle(viewModel.state.value.menuVisible)
        seriesId = intent.extras?.getLong(Constants.SERIES_EXTRA, -1)?.takeIf { it > 0 }

        if (viewModel.needsInit()) {
            val manga = intent.extras?.getLong("manga", -1) ?: -1L
            val chapter = intent.extras?.getLong("chapter", -1) ?: -1L
            if (manga == -1L || chapter == -1L) {
                finish()
                return
            }
            NotificationReceiver.dismissNotification(
                this,
                manga.hashCode(),
                Notifications.ID_NEW_CHAPTERS,
            )

            lifecycleScope.launchNonCancellable {
                val initResult = viewModel.init(manga, chapter, seriesId)
                if (!initResult.getOrDefault(false)) {
                    val exception = initResult.exceptionOrNull() ?: IllegalStateException(
                        "Unknown err",
                    )
                    withUIContext {
                        setInitialChapterError(exception)
                    }
                }
            }
        }

        config = ReaderConfig()
        initializeMenu()

        // Finish when incognito mode is disabled
        preferences.incognitoMode().changes()
            .drop(1)
            .onEach { if (!it) finish() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.isLoadingAdjacentChapter }
            .distinctUntilChanged()
            .onEach(::setProgressDialog)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { updateViewer() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderViewModel.Event.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }
                    ReaderViewModel.Event.PageChanged -> {
                        if (readerPreferences.flashOnPageChange().get() && isEInkMode()) {
                            displayRefreshHost.flash()
                        }
                    }
                    is ReaderViewModel.Event.SetOrientation -> {
                        setOrientation(event.orientation)
                    }
                    is ReaderViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.page)
                    }
                    is ReaderViewModel.Event.CopyImage -> {
                        onCopyImageResult(event.uri)
                    }
                    is ReaderViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    /**
     * Called when the activity is destroyed. Cleans up the viewer, configuration and any view.
     */
    override fun onDestroy() {
        // Allow achievement notifications when exiting reader
        eu.kanade.presentation.achievement.components.AchievementBannerManager.setInReaderOrPlayer(false)
        super.onDestroy()
        viewModel.state.value.viewer?.destroy()
        config = null
        menuToggleToast?.cancel()
        readingModeToast?.cancel()
    }

    override fun onPause() {
        (viewModel.state.value.viewer as? WebtoonViewer)?.let(viewModel::saveWebtoonScrollProgressOnExit)
        viewModel.flushReadTimer()
        viewModel.pauseAutoScroll()
        super.onPause()
    }

    /**
     * Set menu visibility again on activity resume to apply immersive mode again if needed.
     * Helps with rotations.
     */
    override fun onResume() {
        super.onResume()
        viewModel.restartReadTimer()
        setMenuVisibility(viewModel.state.value.menuVisible)
    }

    /**
     * Called when the window focus changes. It sets the menu visibility to the last known state
     * to apply immersive mode again if needed.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setMenuVisibility(viewModel.state.value.menuVisible)
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    /**
     * Called when the user clicks the back key or the button on the toolbar. The call is
     * delegated to the presenter.
     */
    override fun finish() {
        viewModel.onActivityFinish()
        super.finish()
        val reduceMotion = isEInkMode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                if (reduceMotion) 0 else R.anim.shared_axis_x_pop_enter,
                if (reduceMotion) 0 else R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                if (reduceMotion) 0 else R.anim.shared_axis_x_pop_enter,
                if (reduceMotion) 0 else R.anim.shared_axis_x_pop_exit,
            )
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_N) {
            loadNextChapter()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_P) {
            loadPreviousChapter()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Dispatches a key event. If the viewer doesn't handle it, call the default implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = viewModel.state.value.viewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    /**
     * Dispatches a generic motion event. If the viewer doesn't handle it, call the default
     * implementation.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = viewModel.state.value.viewer?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    /**
     * Initializes the reader menu. It sets up click listeners and the initial visibility.
     */
    private fun initializeMenu() {
        binding.pageNumber.setComposeContent {
            val hapticFeedbackMode by uiPreferences.hapticFeedbackMode().collectAsStateWithLifecycle()
            val eInkProfile by uiPreferences.eInkProfile().collectAsStateWithLifecycle()

            AppHapticsProvider(
                hapticFeedbackMode = hapticFeedbackMode,
                isEInkMode = eInkProfile.isEnabled,
            ) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val showPageNumber by viewModel.readerPreferences.showPageNumber().collectAsStateWithLifecycle()
                val showReadingTimeLeft by viewModel.readerPreferences.showReadingTimeLeft()
                    .collectAsStateWithLifecycle()

                if (!state.menuVisible && showPageNumber) {
                    PageIndicatorText(
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                        estimatedMinutesLeft = if (showReadingTimeLeft) state.estimatedMinutesLeft else null,
                    )
                }
            }
        }

        binding.dialogRoot.setComposeContent {
            val hapticFeedbackMode by uiPreferences.hapticFeedbackMode().collectAsStateWithLifecycle()
            val eInkProfile by uiPreferences.eInkProfile().collectAsStateWithLifecycle()

            AppHapticsProvider(
                hapticFeedbackMode = hapticFeedbackMode,
                isEInkMode = eInkProfile.isEnabled,
            ) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val settingsScreenModel = remember {
                    ReaderSettingsScreenModel(
                        readerState = viewModel.state,
                        hasDisplayCutout = hasCutout,
                        onChangeReadingMode = viewModel::setReadingModePreference,
                        onChangeOrientation = viewModel::setOrientationPreference,
                        onSetSeriesViewerOverride = viewModel::setSeriesViewerOverrideEnabled,
                        isSeriesViewerOverrideEnabled = viewModel::isSeriesViewerOverrideEnabled,
                    )
                }

                if (!ifMangaSourcesLoaded()) {
                    return@AppHapticsProvider
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val isHttpSource = viewModel.getSource() is HttpSource
                    val isFullscreen by readerPreferences.fullscreen().collectAsStateWithLifecycle()
                    val flashOnPageChange by readerPreferences.flashOnPageChange().collectAsStateWithLifecycle()

                    val colorOverlayEnabled by readerPreferences.colorFilter().collectAsStateWithLifecycle()
                    val colorOverlay by readerPreferences.colorFilterValue().collectAsStateWithLifecycle()
                    val colorOverlayMode by readerPreferences.colorFilterMode().collectAsStateWithLifecycle()
                    val colorOverlayBlendMode = remember(colorOverlayMode) {
                        ReaderPreferences.ColorFilterMode.getOrNull(colorOverlayMode)?.second
                    }

                    val cropBorderPaged by readerPreferences.cropBorders().collectAsStateWithLifecycle()
                    val cropBorderWebtoon by readerPreferences.cropBordersWebtoon().collectAsStateWithLifecycle()
                    val isPagerType = ReadingMode.isPagerType(viewModel.getMangaReadingMode())
                    val cropEnabled = if (isPagerType) cropBorderPaged else cropBorderWebtoon

                    // Navigator customization preferences
                    val showNavigator by readerPreferences.showNavigator().collectAsStateWithLifecycle()
                    val navigatorShowPageNumbers by readerPreferences
                        .navigatorShowPageNumbers()
                        .collectAsStateWithLifecycle()
                    val navigatorShowChapterButtons by readerPreferences
                        .navigatorShowChapterButtons()
                        .collectAsStateWithLifecycle()
                    val navigatorSliderColor by readerPreferences.navigatorSliderColor().collectAsStateWithLifecycle()
                    val navigatorBackgroundAlpha by readerPreferences
                        .navigatorBackgroundAlpha()
                        .collectAsStateWithLifecycle()
                    val navigatorHeight by readerPreferences.navigatorHeight().collectAsStateWithLifecycle()
                    val navigatorCornerRadius by readerPreferences
                        .navigatorCornerRadius()
                        .collectAsStateWithLifecycle()
                    val navigatorShowTickMarks by readerPreferences
                        .navigatorShowTickMarks()
                        .collectAsStateWithLifecycle()

                    // Bottom bar button visibility preferences
                    val showBottomBarReadingMode by readerPreferences
                        .showBottomBarReadingMode()
                        .collectAsStateWithLifecycle()
                    val showBottomBarOrientation by readerPreferences
                        .showBottomBarOrientation()
                        .collectAsStateWithLifecycle()
                    val showBottomBarCropBorders by readerPreferences
                        .showBottomBarCropBorders()
                        .collectAsStateWithLifecycle()
                    val showBottomBarChapterList by readerPreferences
                        .showBottomBarChapterList()
                        .collectAsStateWithLifecycle()
                    val showBottomBarSettings by readerPreferences
                        .showBottomBarSettings()
                        .collectAsStateWithLifecycle()
                    val bottomBarButtonsOrder by readerPreferences
                        .bottomBarButtonsOrder()
                        .collectAsStateWithLifecycle()
                    val showToolbarWebViewButton by readerPreferences
                        .showToolbarWebViewButton()
                        .collectAsStateWithLifecycle()
                    val showToolbarShareButton by readerPreferences
                        .showToolbarShareButton()
                        .collectAsStateWithLifecycle()
                    val buttonsOrderList = remember(bottomBarButtonsOrder) {
                        bottomBarButtonsOrder.split(",").filter { it.isNotBlank() }
                    }

                    val showAutoScrollFloatingButton by
                        readerPreferences.showAutoScrollFloatingButton().collectAsStateWithLifecycle()
                    val pageActionButtonColorPref = remember { readerPreferences.pageActionButtonColor() }
                    val pageActionButtonColor by pageActionButtonColorPref.collectAsStateWithLifecycle()
                    val pageActionLabelColorPref = remember { readerPreferences.pageActionLabelColor() }
                    val pageActionLabelColor by pageActionLabelColorPref.collectAsStateWithLifecycle()
                    val bottomBarPosition by readerPreferences.bottomBarPosition().collectAsStateWithLifecycle()

                    // Auto-scroll effect - start/stop based on state
                    LaunchedEffect(state.autoScrollEnabled, state.autoScrollSpeed, state.viewer) {
                        val viewer = state.viewer
                        if (viewer != null && state.autoScrollEnabled) {
                            // Hide menu when auto-scroll starts
                            setMenuVisibility(false)
                            when (viewer) {
                                is PagerViewer -> viewer.startAutoScroll(state.autoScrollSpeed)
                                is WebtoonViewer -> viewer.startAutoScroll(state.autoScrollSpeed)
                            }
                        } else if (viewer != null) {
                            when (viewer) {
                                is PagerViewer -> viewer.stopAutoScroll()
                                is WebtoonViewer -> viewer.stopAutoScroll()
                            }
                        }
                    }

                    ReaderContentOverlay(
                        brightness = state.brightnessOverlayValue,
                        color = colorOverlay.takeIf { colorOverlayEnabled },
                        colorBlendMode = colorOverlayBlendMode,
                    )

                    ReaderAppBars(
                        visible = state.menuVisible,
                        fullscreen = isFullscreen,

                        mangaTitle = state.manga?.title,
                        chapterTitle = state.currentChapter?.chapter?.name,
                        navigateUp = onBackPressedDispatcher::onBackPressed,
                        onClickTopAppBar = ::openMangaScreen,
                        bookmarked = state.bookmarked,
                        onToggleBookmarked = viewModel::toggleChapterBookmark,
                        onOpenInWebView = ::openChapterInWebView.takeIf { isHttpSource },
                        showWebViewButton = showToolbarWebViewButton,
                        showShareButton = showToolbarShareButton,
                        onOpenInBrowser = ::openChapterInBrowser.takeIf { isHttpSource },
                        onShare = ::shareChapter.takeIf { isHttpSource },

                        viewer = state.viewer,

                        onNextChapter = ::loadNextChapter,
                        enabledNext = state.viewerChapters?.nextChapter != null,
                        onPreviousChapter = ::loadPreviousChapter,
                        enabledPrevious = state.viewerChapters?.prevChapter != null,
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                        onPageIndexChange = {
                            isScrollingThroughPages = true
                            moveToPageIndex(it)
                        },

                        readingMode = ReadingMode.fromPreference(
                            viewModel.getMangaReadingMode(resolveDefault = false),
                        ),
                        onClickReadingMode = viewModel::openReadingModeSelectDialog,
                        orientation = ReaderOrientation.fromPreference(
                            viewModel.getMangaOrientation(resolveDefault = false),
                        ),
                        onClickOrientation = viewModel::openOrientationModeSelectDialog,
                        cropEnabled = cropEnabled,
                        onClickCropBorder = {
                            val enabled = viewModel.toggleCropBorders()
                            menuToggleToast?.cancel()
                            menuToggleToast = toast(if (enabled) MR.strings.on else MR.strings.off)
                        },
                        onClickChapterList = viewModel::openChapterListDialog,
                        onClickSettings = viewModel::openSettingsDialog,

                        // Auto-scroll options
                        autoScrollEnabled = state.autoScrollEnabled,
                        autoScrollSpeed = state.autoScrollSpeed,
                        onToggleAutoScroll = viewModel::toggleAutoScroll,
                        onSpeedChange = viewModel::setAutoScrollSpeed,
                        showAutoScrollFloatingButton = showAutoScrollFloatingButton,
                        onToggleAutoScrollFloatingButton = {
                            readerPreferences.showAutoScrollFloatingButton().set(it)
                        },
                        isAutoScrollExpanded = state.isAutoScrollExpanded,
                        onToggleExpand = viewModel::toggleAutoScrollExpand,

                        bottomBarPosition = bottomBarPosition,

                        visibleButtons = BottomBarButtonFlags(
                            readingMode = showBottomBarReadingMode,
                            orientation = showBottomBarOrientation,
                            cropBorders = showBottomBarCropBorders,
                            chapterList = showBottomBarChapterList,
                            settings = showBottomBarSettings,
                        ),
                        buttonsOrder = buttonsOrderList,

                        // Navigator customization options
                        showNavigator = showNavigator,
                        navigatorShowPageNumbers = navigatorShowPageNumbers,
                        navigatorShowChapterButtons = navigatorShowChapterButtons,
                        navigatorSliderColor = navigatorSliderColor,
                        navigatorBackgroundAlpha = navigatorBackgroundAlpha,
                        navigatorHeight = navigatorHeight,
                        navigatorCornerRadius = navigatorCornerRadius,
                        navigatorShowTickMarks = navigatorShowTickMarks,
                    )

                    if (flashOnPageChange && eInkProfile.isEnabled) {
                        DisplayRefreshHost(
                            hostState = displayRefreshHost,
                        )
                    }

                    val onDismissRequest = viewModel::closeDialog
                    when (state.dialog) {
                        is ReaderViewModel.Dialog.Loading -> {
                            AlertDialog(
                                onDismissRequest = {},
                                confirmButton = {},
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator()
                                        Text(stringResource(MR.strings.loading))
                                    }
                                },
                            )
                        }
                        is ReaderViewModel.Dialog.Settings -> {
                            ReaderSettingsDialog(
                                onDismissRequest = onDismissRequest,
                                onShowMenus = { setMenuVisibility(true) },
                                onHideMenus = { setMenuVisibility(false) },
                                screenModel = settingsScreenModel,
                            )
                        }
                        is ReaderViewModel.Dialog.ReadingModeSelect -> {
                            ReadingModeSelectDialog(
                                onDismissRequest = onDismissRequest,
                                screenModel = settingsScreenModel,
                                onChange = { stringRes ->
                                    menuToggleToast?.cancel()
                                    if (!readerPreferences.showReadingMode().get()) {
                                        menuToggleToast = toast(stringRes)
                                    }
                                },
                            )
                        }
                        is ReaderViewModel.Dialog.OrientationModeSelect -> {
                            OrientationSelectDialog(
                                onDismissRequest = onDismissRequest,
                                screenModel = settingsScreenModel,
                                onChange = { stringRes ->
                                    menuToggleToast?.cancel()
                                    menuToggleToast = toast(stringRes)
                                },
                            )
                        }
                        is ReaderViewModel.Dialog.PageActions -> {
                            ReaderPageActionsDialog(
                                onDismissRequest = onDismissRequest,
                                onSetAsCover = viewModel::setAsCover,
                                onShare = viewModel::shareImage,
                                onSave = viewModel::saveImage,
                                buttonColorValue = pageActionButtonColor,
                                labelColorValue = pageActionLabelColor,
                                onButtonColorChange = pageActionButtonColorPref::set,
                                onLabelColorChange = pageActionLabelColorPref::set,
                            )
                        }
                        is ReaderViewModel.Dialog.ChapterList -> {
                            val currentChapterId = state.currentChapter?.chapter?.id
                            val chapterListItems = state.chapterList.map { chapter ->
                                ReaderChapterListItem(
                                    id = chapter.chapter.id!!,
                                    title = chapter.chapter.name,
                                    dateText = chapter.chapter.date_upload.takeIf { it > 0 }?.let {
                                        relativeDateTimeText(it)
                                    },
                                    scanlator = chapter.chapter.scanlator?.takeIf { it.isNotBlank() },
                                    isCurrent = chapter.chapter.id == currentChapterId,
                                )
                            }
                            ReaderChapterListSheet(
                                items = chapterListItems,
                                onDismissRequest = onDismissRequest,
                                onChapterClick = { chapterId ->
                                    onDismissRequest()
                                    lifecycleScope.launch {
                                        viewModel.jumpToChapter(chapterId)
                                    }
                                },
                                onDownloadClick = { chapterId ->
                                    lifecycleScope.launch {
                                        viewModel.downloadChapter(chapterId)
                                    }
                                },
                            )
                        }
                        is ReaderViewModel.Dialog.AutoWebtoonModeSuggestion -> {
                            AlertDialog(
                                onDismissRequest = viewModel::dismissAutoWebtoonModeSuggestion,
                                title = {
                                    Text(stringResource(MR.strings.reader_auto_webtoon_detected_title))
                                },
                                text = {
                                    Text(stringResource(MR.strings.reader_auto_webtoon_detected_message))
                                },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = viewModel::acceptAutoWebtoonModeSuggestion,
                                    ) {
                                        Text(stringResource(MR.strings.reader_auto_webtoon_detected_confirm))
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = viewModel::dismissAutoWebtoonModeSuggestion,
                                    ) {
                                        Text(stringResource(MR.strings.action_cancel))
                                    }
                                },
                            )
                        }
                        null -> {}
                    }

                    state.seriesInterstitialState?.let { seriesState ->
                        val onContinue = seriesState.nextManga?.manga?.id?.let { nextMangaId ->
                            seriesState.nextChapterId?.let { nextChapterId ->
                                {
                                    viewModel.clearSeriesInterstitial()
                                    startActivity(
                                        ReaderActivity.newIntent(
                                            this@ReaderActivity,
                                            nextMangaId,
                                            nextChapterId,
                                            seriesId,
                                        ),
                                    )
                                    finish()
                                }
                            }
                        }

                        MangaSeriesInterstitialOverlay(
                            state = seriesState,
                            onBackToSeries = {
                                viewModel.clearSeriesInterstitial()
                                finish()
                            },
                            onContinue = onContinue,
                        )
                    }

                    AutoScrollActionFab(
                        autoScrollEnabled = state.autoScrollEnabled,
                        showFab = showAutoScrollFloatingButton && !state.menuVisible,
                        onClick = { viewModel.toggleAutoScroll() },
                        onLongClick = {
                            setMenuVisibility(true)
                            viewModel.toggleAutoScrollExpand()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    )
                }
            }
        }

        // Set initial visibility
        setMenuVisibility(viewModel.state.value.menuVisible)
    }

    /**
     * Sets the visibility of the menu according to [visible].
     */
    private fun setMenuVisibility(visible: Boolean) {
        viewModel.showMenus(visible)
        applyReaderSystemBarIconStyle(visible)
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            // Pause auto-scroll when menu is shown
            viewModel.pauseAutoScroll()
        } else {
            if (readerPreferences.fullscreen().get()) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        updateViewerInset(
            readerPreferences.fullscreen().get(),
            readerPreferences.cutoutShort().get(),
            visible,
        )
    }

    /**
     * Called from the presenter when a manga is ready. Used to instantiate the appropriate viewer.
     */
    private fun updateViewer() {
        val prevViewer = viewModel.state.value.viewer
        val newViewer = ReadingMode.toViewer(viewModel.getMangaReadingMode(), this)

        if (isEInkMode() || window.sharedElementEnterTransition !is MaterialContainerTransform) {
            setOrientation(viewModel.getMangaOrientation())
        } else {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.doOnEnd {
                setOrientation(viewModel.getMangaOrientation())
            }
        }

        // Destroy previous viewer if there was one
        if (prevViewer != null) {
            prevViewer.destroy()
            binding.viewerContainer.removeAllViews()
        }
        viewModel.onViewerLoaded(newViewer)
        updateViewerInset(
            readerPreferences.fullscreen().get(),
            readerPreferences.cutoutShort().get(),
            viewModel.state.value.menuVisible,
        )

        // Touch cooldown: pause auto-scroll briefly on any touch in the active viewer.
        newViewer.getView().setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                when (newViewer) {
                    is PagerViewer -> newViewer.setAutoScrollCooldown(2000L)
                    is WebtoonViewer -> newViewer.setAutoScrollCooldown(2000L)
                }
            }
            false
        }
        binding.viewerContainer.addView(newViewer.getView())

        if (readerPreferences.showReadingMode().get()) {
            if (viewModel.isMangaReadingModeAutoWebtoon()) {
                toast(MR.strings.reader_auto_webtoon_mode_enabled)
            } else {
                showReadingModeToast(viewModel.getMangaReadingMode())
            }
        }

        loadingIndicator = ReaderProgressIndicator(this)
        binding.readerContainer.addView(loadingIndicator)

        startPostponedEnterTransition()
    }

    private fun openMangaScreen() {
        viewModel.manga?.id?.let { id ->
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = Constants.SHORTCUT_MANGA
                    putExtra(Constants.MANGA_EXTRA, id)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }
    }

    private fun openChapterInWebView() {
        val manga = viewModel.manga ?: return
        val source = viewModel.getSource() ?: return
        assistUrl?.let {
            val intent = WebViewActivity.newIntent(this@ReaderActivity, it, source.id, manga.title)
            startActivity(intent)
        }
    }

    private fun openChapterInBrowser() {
        assistUrl?.let {
            openInBrowser(it.toUri(), forceDefaultBrowser = false)
        }
    }

    private fun shareChapter() {
        assistUrl?.let {
            val intent = it.toUri().toShareIntent(this, type = "text/plain")
            startActivity(Intent.createChooser(intent, stringResource(MR.strings.action_share)))
        }
    }

    private fun showReadingModeToast(mode: Int) {
        try {
            readingModeToast?.cancel()
            readingModeToast = toast(ReadingMode.fromPreference(mode).stringRes)
        } catch (e: ArrayIndexOutOfBoundsException) {
            logcat(LogPriority.ERROR) { "Unknown reading mode: $mode" }
        }
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the toolbar, and
     * hides or disables the reader prev/next buttons if there's a prev or next chapter
     */
    @SuppressLint("RestrictedApi")
    private fun setChapters(viewerChapters: ViewerChapters) {
        binding.readerContainer.removeView(loadingIndicator)
        viewModel.state.value.viewer?.setChapters(viewerChapters)

        lifecycleScope.launchIO {
            viewModel.getChapterUrl()?.let { url ->
                assistUrl = url
            }
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        finish()
        toast(error.message)
    }

    /**
     * Called from the presenter whenever it's loading the next or previous chapter. It shows or
     * dismisses a non-cancellable dialog to prevent user interaction according to the value of
     * [show]. This is only used when the next/previous buttons on the toolbar are clicked; the
     * other cases are handled with chapter transitions on the viewers and chapter preloading.
     */
    private fun setProgressDialog(show: Boolean) {
        if (show) {
            viewModel.showLoadingDialog()
        } else {
            viewModel.closeDialog()
        }
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    private fun moveToPageIndex(index: Int) {
        val viewer = viewModel.state.value.viewer ?: return
        val currentChapter = viewModel.state.value.currentChapter ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page)
    }

    /**
     * Tells the presenter to load the next chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadNextChapter() {
        lifecycleScope.launch {
            viewModel.loadNextChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Tells the presenter to load the previous chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadPreviousChapter() {
        lifecycleScope.launch {
            viewModel.loadPreviousChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    fun onPageSelected(page: ReaderPage) {
        viewModel.onPageSelected(page)
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(page: ReaderPage) {
        viewModel.openPageDialog(page)
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launchIO { viewModel.preload(chapter) }
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
        setMenuVisibility(!viewModel.state.value.menuVisible)
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (!viewModel.state.value.menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the viewer to hide the menu.
     */
    fun hideMenu() {
        if (viewModel.state.value.menuVisible) {
            setMenuVisibility(false)
        }
    }

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    private fun onShareImageResult(uri: Uri, page: ReaderPage) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = stringResource(MR.strings.share_page_info, manga.title, chapter.name, page.number),
        )
        startActivity(Intent.createChooser(intent, stringResource(MR.strings.action_share)))
    }

    private fun onCopyImageResult(uri: Uri) {
        val clipboardManager = applicationContext.getSystemService<ClipboardManager>() ?: return
        val clipData = ClipData.newUri(applicationContext.contentResolver, "", uri)
        clipboardManager.setPrimaryClip(clipData)
    }

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is ReaderViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> MR.strings.cover_updated
                AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    private fun setOrientation(orientation: Int) {
        val newOrientation = ReaderOrientation.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
    }

    /**
     * Updates viewer inset depending on fullscreen reader preferences.
     */
    private fun updateViewerInset(fullscreen: Boolean, cutoutShort: Boolean, menuVisible: Boolean) {
        if (!::binding.isInitialized) return
        val view = binding.viewerContainer

        view.applyInsetsPadding(ViewCompat.getRootWindowInsets(view), fullscreen, cutoutShort, menuVisible)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            v.applyInsetsPadding(windowInsets, fullscreen, cutoutShort, menuVisible)
            windowInsets
        }
    }

    private fun View.applyInsetsPadding(
        windowInsets: WindowInsetsCompat?,
        fullscreen: Boolean,
        cutoutShort: Boolean,
        menuVisible: Boolean,
    ) {
        val insets = when {
            !fullscreen || menuVisible -> windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())
            !cutoutShort -> windowInsets?.getInsets(WindowInsetsCompat.Type.displayCutout())
            else -> null
        }
            ?: Insets.NONE

        setPadding(insets.left, insets.top, insets.right, insets.bottom)
    }

    /**
     * Class that handles the user preferences of the reader.
     */
    private inner class ReaderConfig {

        private fun getCombinedPaint(grayscale: Boolean, invertedColors: Boolean): Paint {
            return Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        if (grayscale) {
                            setSaturation(0f)
                        }
                        if (invertedColors) {
                            postConcat(
                                ColorMatrix(
                                    floatArrayOf(
                                        -1f, 0f, 0f, 0f, 255f,
                                        0f, -1f, 0f, 0f, 255f,
                                        0f, 0f, -1f, 0f, 255f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
        }

        private val grayBackgroundColor = Color.rgb(0x20, 0x21, 0x25)

        // Initializes the reader subscriptions.
        init {
            readerPreferences.readerTheme().changes()
                .onEach { theme ->
                    binding.readerContainer.setBackgroundColor(
                        when (theme) {
                            0 -> Color.WHITE
                            2 -> grayBackgroundColor
                            3 -> automaticBackgroundColor()
                            else -> Color.BLACK
                        },
                    )
                }
                .launchIn(lifecycleScope)

            preferences.displayProfile().changes()
                .onEach { setDisplayProfile(it) }
                .launchIn(lifecycleScope)

            merge(
                readerPreferences.grayscale().changes(),
                readerPreferences.invertedColors().changes(),
                uiPreferences.eInkProfile().changes(),
            )
                .onEach {
                    setLayerPaint(
                        readerPreferences.grayscale().get(),
                        readerPreferences.invertedColors().get(),
                    )
                }
                .launchIn(lifecycleScope)

            merge(
                readerPreferences.sharpening().changes(),
                readerPreferences.denoise().changes(),
                readerPreferences.binarization().changes(),
            )
                .onEach {
                    applyRenderEffects()
                }
                .launchIn(lifecycleScope)

            readerPreferences.cutoutShort().changes()
                .onEach(::setCutoutShort)
                .launchIn(lifecycleScope)

            readerPreferences.keepScreenOn().changes()
                .onEach(::setKeepScreenOn)
                .launchIn(lifecycleScope)

            readerPreferences.customBrightness().changes()
                .onEach(::setCustomBrightness)
                .launchIn(lifecycleScope)

            combine(
                readerPreferences.fullscreen().changes(),
                readerPreferences.cutoutShort().changes(),
            ) { fullscreen, cutoutShort -> fullscreen to cutoutShort }
                .onEach { (fullscreen, cutoutShort) ->
                    WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
                    updateViewerInset(fullscreen, cutoutShort, viewModel.state.value.menuVisible)
                    setMenuVisibility(viewModel.state.value.menuVisible)
                }
                .launchIn(lifecycleScope)

            // Apply initial state
            setLayerPaint(
                readerPreferences.grayscale().get(),
                readerPreferences.invertedColors().get(),
            )
            applyRenderEffects()
        }

        /**
         * Picks background color for [ReaderActivity] based on light/dark theme preference
         */
        private fun automaticBackgroundColor(): Int {
            return if (baseContext.isNightMode()) {
                grayBackgroundColor
            } else {
                Color.WHITE
            }
        }

        /**
         * Sets the display profile to [path].
         */
        private fun setDisplayProfile(path: String) {
            val file = UniFile.fromUri(baseContext, path.toUri())
            if (file != null && file.exists()) {
                val inputStream = file.openInputStream()
                val outputStream = ByteArrayOutputStream()
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                val data = outputStream.toByteArray()
                SubsamplingScaleImageView.setDisplayProfile(data)
                TachiyomiImageDecoder.displayProfile = data
            }
        }

        private fun setCutoutShort(enabled: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

            window.attributes.layoutInDisplayCutoutMode = when (enabled) {
                true -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                false -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            // Trigger relayout
            setMenuVisibility(viewModel.state.value.menuVisible)
        }

        /**
         * Sets the keep screen on mode according to [enabled].
         */
        private fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        /**
         * Sets the custom brightness overlay according to [enabled].
         */
        private fun setCustomBrightness(enabled: Boolean) {
            if (enabled) {
                readerPreferences.customBrightnessValue().changes()
                    .sample(100)
                    .onEach(::setCustomBrightnessValue)
                    .launchIn(lifecycleScope)
            } else {
                setCustomBrightnessValue(0)
            }
        }

        /**
         * Sets the brightness of the screen. Range is [-75, 100].
         * From -75 to -1 a semi-transparent black view is overlaid with the minimum brightness.
         * From 1 to 100 it sets that value as brightness.
         * 0 sets system brightness and hides the overlay.
         */
        private fun setCustomBrightnessValue(value: Int) {
            // Calculate and set reader brightness.
            val readerBrightness = when {
                value > 0 -> {
                    value / 100f
                }
                value < 0 -> {
                    0.01f
                }
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = window.attributes.apply { screenBrightness = readerBrightness }

            viewModel.setBrightnessOverlayValue(value)
        }
        private fun setLayerPaint(grayscale: Boolean, invertedColors: Boolean) {
            val paint = if (grayscale || invertedColors) getCombinedPaint(grayscale, invertedColors) else null
            binding.viewerContainer.setLayerType(LAYER_TYPE_HARDWARE, paint)
        }

        private fun applyRenderEffects() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val sharpeningVal = readerPreferences.sharpening().get() / 100f
                val denoiseVal = readerPreferences.denoise().get() / 100f
                val binarizationVal = readerPreferences.binarization().get() / 100f

                var effect: android.graphics.RenderEffect? = null

                if (sharpeningVal > 0f) {
                    val sharpShader = android.graphics.RuntimeShader(SHARPEN_SHADER)
                    sharpShader.setFloatUniform("sharpness", sharpeningVal)
                    effect = android.graphics.RenderEffect.createRuntimeShaderEffect(sharpShader, "inputShader")
                }

                if (denoiseVal > 0f) {
                    val denoiseShader = android.graphics.RuntimeShader(DENOISE_SHADER)
                    denoiseShader.setFloatUniform("denoise", denoiseVal)
                    val denoiseEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
                        denoiseShader,
                        "inputShader",
                    )
                    effect = if (effect != null) {
                        android.graphics.RenderEffect.createChainEffect(denoiseEffect, effect)
                    } else {
                        denoiseEffect
                    }
                }

                if (binarizationVal > 0f) {
                    val binarizationShader = android.graphics.RuntimeShader(BINARIZATION_SHADER)
                    binarizationShader.setFloatUniform("binarization", binarizationVal)
                    val binarizationEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
                        binarizationShader,
                        "inputShader",
                    )
                    effect = if (effect != null) {
                        android.graphics.RenderEffect.createChainEffect(binarizationEffect, effect)
                    } else {
                        binarizationEffect
                    }
                }

                binding.viewerContainer.setRenderEffect(effect)
            }
        }
    }

    private fun applyReaderSystemBarIconStyle(menuVisible: Boolean) {
        val lightStatusBars = resolveReaderLightStatusBars(
            menuVisible = menuVisible,
            fullscreen = readerPreferences.fullscreen().get(),
            defaultLightStatusBars = resources.getBoolean(R.bool.lightStatusBar),
        )
        windowInsetsController.isAppearanceLightStatusBars = lightStatusBars
    }

    private var meltdownSwipeCount = 0
    private var meltdownLastActivatedMs = 0L
    private val meltdownSwipeState = mutableStateOf(0)
    private var meltdownEscalationView: android.view.View? = null

    fun onMeltdownTransitionActivated() {
        // Throttle: count at most once per 800 ms so WebtoonViewer scroll events
        // (called every frame) don't increment the counter hundreds of times per swipe.
        val now = System.currentTimeMillis()
        if (now - meltdownLastActivatedMs < 800L) return
        meltdownLastActivatedMs = now

        val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
        val currentStage = uiPreferences.meltdownStage().get()
        if (currentStage != 2) return

        // Check offline: use NetworkCapabilities for API 23+ reliability
        val isOffline = runCatching {
            val connectivityManager = getSystemService(
                android.content.Context.CONNECTIVITY_SERVICE,
            ) as? android.net.ConnectivityManager
            if (connectivityManager == null) return@runCatching true
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) return@runCatching true
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            capabilities == null ||
                (
                    !capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
                        !capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        !capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
        }.getOrDefault(true)
        if (!isOffline) return

        // Check AMOLED dark theme via app preference, not system uiMode
        // User must have AMOLED mode enabled AND dark theme forced (not just system auto)
        val isAmoledDark = uiPreferences.themeDarkAmoled().get()
        val themeMode = uiPreferences.themeMode().get()
        val isSystemNight = (
            resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            ) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDarkActive = when (themeMode) {
            eu.kanade.domain.ui.model.ThemeMode.DARK -> true
            eu.kanade.domain.ui.model.ThemeMode.LIGHT -> false
            else -> isSystemNight
        }
        if (!isAmoledDark || !isDarkActive) return

        // No need to check manga status: reaching the end of available chapters
        // (transition.to == null, triggered by PagerViewer/WebtoonViewer) is sufficient.
        // The user IS at the last available chapter — that's the ritual condition.

        meltdownSwipeCount++
        meltdownSwipeState.value = meltdownSwipeCount
        ensureMeltdownEscalationOverlay()
        if (meltdownSwipeCount >= 5) {
            // Оверлей остаётся виден (дыра 100%) всё время паузы;
            // убираем его непосредственно перед показом терминала.
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.delay(eu.kanade.presentation.components.BREACH_ACT_GAP_MS.toLong())
                removeMeltdownEscalationOverlay()
                triggerMeltdownFinalRitual()
            }
        }
    }

    private fun ensureMeltdownEscalationOverlay() {
        if (meltdownEscalationView != null) return
        val view = androidx.compose.ui.platform.ComposeView(this@ReaderActivity).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setContent {
                eu.kanade.presentation.components.MeltdownSwipeGlitch(
                    swipe = meltdownSwipeState.value,
                )
                eu.kanade.presentation.components.MeltdownFractureOverlay(
                    swipe = meltdownSwipeState.value,
                )
            }
        }
        meltdownEscalationView = view
        binding.root.addView(view)
    }

    private fun removeMeltdownEscalationOverlay() {
        meltdownEscalationView?.let { binding.root.removeView(it) }
        meltdownEscalationView = null
        meltdownSwipeCount = 0
        meltdownSwipeState.value = 0
    }

    private fun triggerMeltdownFinalRitual() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val composeView = androidx.compose.ui.platform.ComposeView(this@ReaderActivity).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setContent {
                    val view = androidx.compose.ui.platform.LocalView.current
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black),
                    ) {
                        var showGlitch by remember { mutableStateOf(true) }
                        var showDialog by remember { mutableStateOf(false) }
                        var showBreach by remember { mutableStateOf(false) }
                        var capturedScreenshot by remember { mutableStateOf<Bitmap?>(null) }
                        var justUnlocked by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(800)
                            showDialog = true
                        }

                        if (showGlitch) {
                            eu.kanade.presentation.components.VoidRitualGlitch()
                        }

                        if (showDialog) {
                            eu.kanade.presentation.components.TerminalGlitchDialog(
                                title = composeStringResource(AYMR.strings.meltdown_terminal_title),
                                message = composeStringResource(AYMR.strings.meltdown_terminal_message),
                                buttonText = composeStringResource(AYMR.strings.meltdown_terminal_confirm),
                                dismissButtonText = composeStringResource(AYMR.strings.meltdown_terminal_dismiss),
                                holdToConfirm = true,
                                onConfirm = {
                                    // Снимаем скриншот через LocalView в момент нажатия (точно как на Этапе 2)
                                    try {
                                        val bmp = Bitmap.createBitmap(
                                            view.width.coerceAtLeast(1),
                                            view.height.coerceAtLeast(1),
                                            Bitmap.Config.ARGB_8888,
                                        )
                                        val canvas = android.graphics.Canvas(bmp)
                                        view.draw(canvas)
                                        capturedScreenshot = bmp
                                    } catch (e: Exception) {
                                        // ignore/fallback
                                    }

                                    showDialog = false
                                    showGlitch = false

                                    // Разблокируем ачивку и награды в фоновом режиме перед запуском финала
                                    lifecycleScope.launch {
                                        val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
                                        uiPreferences.meltdownStage().set(0)

                                        runCatching {
                                            val achievementRepository =
                                                Injekt.get<AchievementRepository>()
                                            val allAchievements = achievementRepository.getAll().first()
                                            val achievement = allAchievements.firstOrNull {
                                                it.id == "void_broadcast_unlocked"
                                            }
                                            if (achievement != null) {
                                                val progressFlow = achievementRepository
                                                    .getProgress("void_broadcast_unlocked").first()
                                                if (progressFlow == null || !progressFlow.isUnlocked) {
                                                    justUnlocked = true
                                                    val newProgress = AchievementProgress.createStandard(
                                                        achievementId = "void_broadcast_unlocked",
                                                        progress = 1,
                                                        maxProgress = 1,
                                                        isUnlocked = true,
                                                        unlockedAt = System.currentTimeMillis(),
                                                    )
                                                    achievementRepository.insertOrUpdateProgress(newProgress)

                                                    val pointsManager =
                                                        Injekt.get<tachiyomi.data.achievement.handler.PointsManager>()
                                                    pointsManager.incrementUnlocked()

                                                    val unlockableManager = Injekt.get<UnlockableManager>()
                                                    unlockableManager.unlockAchievementRewards(achievement)
                                                }
                                            }
                                        }

                                        showBreach = true
                                    }
                                },
                                onDismiss = {
                                    binding.root.removeView(this@apply)
                                },
                            )
                        }

                        if (showBreach) {
                            val revealUiPrefs = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
                            val revealProfilePrefs =
                                Injekt.get<eu.kanade.domain.ui.UserProfilePreferences>()

                            val voidRewards = listOf(
                                eu.kanade.presentation.components.VoidReward(
                                    tag = composeStringResource(AYMR.strings.meltdown_reward_theme_tag),
                                    name = composeStringResource(AYMR.strings.meltdown_reward_theme_name),
                                    lore = composeStringResource(AYMR.strings.meltdown_reward_theme_lore),
                                    onApply = {
                                        revealUiPrefs.appTheme()
                                            .set(eu.kanade.domain.ui.model.AppTheme.VOID_RED)
                                    },
                                ),
                                eu.kanade.presentation.components.VoidReward(
                                    tag = composeStringResource(AYMR.strings.meltdown_reward_nick_tag),
                                    name = composeStringResource(AYMR.strings.meltdown_reward_nick_name),
                                    lore = composeStringResource(AYMR.strings.meltdown_reward_nick_lore),
                                    onApply = {
                                        revealProfilePrefs.nicknameEffect().set("glitch_rune_red")
                                    },
                                ),
                                eu.kanade.presentation.components.VoidReward(
                                    tag = composeStringResource(AYMR.strings.meltdown_reward_frame_tag),
                                    name = composeStringResource(AYMR.strings.meltdown_reward_frame_name),
                                    lore = composeStringResource(AYMR.strings.meltdown_reward_frame_lore),
                                    onApply = {
                                        revealProfilePrefs.avatarFrameStyle().set("glitch_red")
                                    },
                                ),
                                eu.kanade.presentation.components.VoidReward(
                                    tag = composeStringResource(AYMR.strings.meltdown_reward_aura_tag),
                                    name = composeStringResource(AYMR.strings.meltdown_reward_aura_name),
                                    lore = composeStringResource(AYMR.strings.meltdown_reward_aura_lore),
                                    onApply = {
                                        revealUiPrefs.enabledAuras().set(setOf("aura_void_broadcast_red"))
                                    },
                                ),
                                eu.kanade.presentation.components.VoidReward(
                                    tag = composeStringResource(AYMR.strings.meltdown_reward_background_tag),
                                    name = composeStringResource(AYMR.strings.meltdown_reward_background_name),
                                    lore = composeStringResource(AYMR.strings.meltdown_reward_background_lore),
                                    onApply = {
                                        revealUiPrefs.specialBackgroundStyle().set("void_weeping_red")
                                    },
                                ),
                            )

                            eu.kanade.presentation.components.VoidRealityBreachFinale(
                                rewards = voidRewards,
                                screenshot = capturedScreenshot,
                                justUnlocked = justUnlocked,
                                onEnterTreasury = {
                                    val intent = Intent(this@ReaderActivity, MainActivity::class.java).apply {
                                        action = MainActivity.INTENT_OPEN_TREASURY
                                        putExtra("just_unlocked", justUnlocked)
                                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    startActivity(intent)
                                    finish()
                                },
                            )
                        }
                    }
                }
            }
            binding.root.addView(composeView)
        }
    }
}

@Composable
private fun CrtShutdownAnimation(onAnimationFinished: () -> Unit) {
    val scaleY = remember { Animatable(1f) }
    val scaleX = remember { Animatable(1f) }
    val dotAlpha = remember { Animatable(1f) }
    val flicker = rememberInfiniteTransition(label = "crt_flicker")
    val flickerAlpha by flicker.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flicker",
    )

    LaunchedEffect(Unit) {
        scaleY.animateTo(
            targetValue = 0.008f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        )
        scaleX.animateTo(
            targetValue = 0.01f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        )
        dotAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        )
        onAnimationFinished()
    }

    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        if (scaleY.value > 0.008f || scaleX.value > 0.01f) {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth(scaleX.value)
                    .fillMaxHeight(scaleY.value)
                    .background(androidx.compose.ui.graphics.Color(0xFFFF003C).copy(alpha = flickerAlpha)),
            )
        }
        if (scaleY.value <= 0.008f && dotAlpha.value > 0f) {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .size(8.dp)
                    .graphicsLayer(alpha = dotAlpha.value)
                    .background(androidx.compose.ui.graphics.Color.White, CircleShape)
                    .border(2.dp, androidx.compose.ui.graphics.Color(0xFFFF003C), CircleShape),
            )
        }
    }
}

internal fun resolveReaderLightStatusBars(
    menuVisible: Boolean,
    fullscreen: Boolean,
    defaultLightStatusBars: Boolean,
): Boolean {
    if (!menuVisible && fullscreen) {
        // Reader content is predominantly dark in immersive mode; keep icons light.
        return false
    }
    return defaultLightStatusBars
}

private const val SHARPEN_SHADER = """
    uniform shader inputShader;
    uniform float sharpness;

    half4 main(float2 coords) {
        half4 center = inputShader.eval(coords);
        half4 left = inputShader.eval(coords + float2(-1.0, 0.0));
        half4 right = inputShader.eval(coords + float2(1.0, 0.0));
        half4 top = inputShader.eval(coords + float2(0.0, -1.0));
        half4 bottom = inputShader.eval(coords + float2(0.0, 1.0));

        half4 sharp = center * 5.0 - (left + right + top + bottom);
        return center + (sharp - center) * sharpness;
    }
"""

private const val DENOISE_SHADER = """
    uniform shader inputShader;
    uniform float denoise;

    half4 main(float2 coords) {
        half4 color = inputShader.eval(coords);
        half4 sum = color;
        float count = 1.0;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                if (x == 0 && y == 0) continue;
                half4 neighbor = inputShader.eval(coords + float2(x, y));
                if (distance(neighbor.rgb, color.rgb) < 0.15) {
                    sum += neighbor;
                    count += 1.0;
                }
            }
        }
        half4 smoothColor = sum / count;
        return color + (smoothColor - color) * denoise;
    }
"""

private const val BINARIZATION_SHADER = """
    uniform shader inputShader;
    uniform float binarization;

    half4 main(float2 coords) {
        half4 center = inputShader.eval(coords);
        float centerLuma = (center.r + center.g + center.b) * 0.3333;

        float sum = centerLuma;
        float minVal = centerLuma;
        float maxVal = centerLuma;

        // Разреженная сетка 3x3 с шагом 2.0 (всего 8 дополнительных выборок вместо 24)
        float2 offsets[8];
        offsets[0] = float2(-2.0, -2.0);
        offsets[1] = float2( 0.0, -2.0);
        offsets[2] = float2( 2.0, -2.0);
        offsets[3] = float2(-2.0,  0.0);
        offsets[4] = float2( 2.0,  0.0);
        offsets[5] = float2(-2.0,  2.0);
        offsets[6] = float2( 0.0,  2.0);
        offsets[7] = float2( 2.0,  2.0);

        for (int i = 0; i < 8; i++) {
            half4 col = inputShader.eval(coords + offsets[i]);
            float luma = (col.r + col.g + col.b) * 0.3333;
            sum += luma;
            minVal = min(minVal, luma);
            maxVal = max(maxVal, luma);
        }

        float mean = sum * 0.1111; // 1.0 / 9.0
        float contrast = maxVal - minVal;

        // Локальная бинаризация
        float binaryLuma = step(mean, centerLuma);

        // Бинаризация для областей с очень низким контрастом (сплошной фон)
        float lowContrastLuma = step(0.5, centerLuma);

        // Безветвленный выбор (branch-free) на основе уровня контраста
        float isLowContrast = step(contrast, 0.15);
        float finalLuma = mix(binaryLuma, lowContrastLuma, isLowContrast);

        // Смешивание с оригиналом на основе ползунка интенсивности
        float mixedLuma = mix(centerLuma, finalLuma, binarization);
        return half4(mixedLuma, mixedLuma, mixedLuma, center.a);
    }
"""
