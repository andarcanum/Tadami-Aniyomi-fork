package eu.kanade.tachiyomi.ui.reels

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.SourceFilterAnimeDialog
import eu.kanade.tachiyomi.ui.reels.components.ReelsEmptySearchState
import eu.kanade.tachiyomi.ui.reels.components.ReelsErrorState
import eu.kanade.tachiyomi.ui.reels.components.ReelsNextPageLoader
import eu.kanade.tachiyomi.ui.reels.components.ReelsSourcePickerSheet
import eu.kanade.tachiyomi.ui.reels.components.ReelsTopBar
import eu.kanade.tachiyomi.ui.reels.components.ReelsVideoPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data class ReelsFeedScreen(
    val sourceId: Long,
    // Non-empty => offline playlist mode (opened from the Favorites screen).
    val initialFavorites: List<ReelsFavorite> = emptyList(),
    val initialPage: Int = 0,
) : Screen {
    // Voyager disposes screens (and their ScreenModels) by screen.key. The default key is only
    // the class name, so a popped offline playlist would share the live feed's key and never be
    // disposed -> models accumulate in ScreenModelStore. A deterministic content-based key makes
    // each pushed playlist unique so it is correctly disposed on pop. Do NOT use a random UUID
    // (the saveable state would orphan models across recreation).
    override val key: String
        get() = "ReelsFeedScreen:$sourceId:$initialPage:${initialFavorites.hashCode()}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        // The screen's content-based `key` (see above) already distinguishes the live feed from
        // an offline playlist, so a plain rememberScreenModel yields the right model per screen
        // and Voyager disposes each correctly on pop.
        val screenModel = rememberScreenModel {
            ReelsFeedScreenModel(
                initialSourceId = sourceId,
                initialFavorites = initialFavorites,
                initialPage = initialPage,
            )
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        // Preload gating must be reactive: a plain context.isOnWifi() call here would be
        // recomputed (stale) on every recomposition instead of tracking network changes.
        var isOnWifi by remember { mutableStateOf(context.isOnWifi()) }
        DisposableEffect(Unit) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    isOnWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                }
            }
            cm?.registerDefaultNetworkCallback(callback)
            onDispose {
                cm?.unregisterNetworkCallback(callback)
            }
        }
        val preloadAllowed = state.preloadEnabled && (!state.preloadWifiOnly || isOnWifi)
        var chromeVisible by remember { mutableStateOf(true) }

        // Immersive: auto-hide the top bar after 3s of playback; any tap reveals it.
        LaunchedEffect(chromeVisible, state.isPlaying) {
            if (chromeVisible && state.isPlaying) {
                delay(3000)
                chromeVisible = false
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            when {
                state.isLoading && state.items.isEmpty() -> {
                    LoadingScreen(modifier = Modifier.fillMaxSize())
                }
                state.error != null && state.items.isEmpty() && !state.isLoading -> {
                    ReelsErrorState(
                        message = state.error.orEmpty(),
                        onRetry = { screenModel.loadFeed(reset = true) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.items.isEmpty() && !state.isLoading && state.searchQuery.isNotBlank() -> {
                    ReelsEmptySearchState(
                        onResetSearch = screenModel::clearSearch,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.items.isEmpty() && !state.isLoading -> {
                    EmptyScreen(
                        stringRes = MR.strings.source_empty_screen,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    val pagerState = rememberPagerState(
                        initialPage = initialPage.coerceIn(0, (state.items.size - 1).coerceAtLeast(0)),
                        pageCount = { state.items.size },
                    )

                    // Any full feed replacement (search / filters / source switch) bumps the
                    // generation; scroll to the requested page (0 on fresh loads, the remembered
                    // position when a cleared search restores the base feed). The applied
                    // generation is saved so re-entering composition (rotation, push/pop)
                    // does not reset the user's position by re-applying a stale target.
                    var appliedGeneration by rememberSaveable { mutableIntStateOf(-1) }
                    LaunchedEffect(state.feedGeneration) {
                        if (state.feedGeneration != appliedGeneration) {
                            appliedGeneration = state.feedGeneration
                            if (pagerState.pageCount > 0) {
                                pagerState.scrollToPage(
                                    state.targetPageIndex.coerceIn(0, pagerState.pageCount - 1),
                                )
                            }
                        }
                    }

                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.settledPage }
                            .distinctUntilChanged()
                            .collect { page ->
                                screenModel.onPageChanged(page)
                            }
                    }

                    // Mid-feed append failures keep the feed usable; surface them transiently
                    // instead of replacing the whole screen with the error state.
                    LaunchedEffect(state.pageError) {
                        state.pageError?.let { message ->
                            snackbarHostState.showSnackbar(message)
                            screenModel.onPageErrorShown()
                        }
                    }

                    // Vertical Pager for reels video cards
                    VerticalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val item = state.items.getOrNull(page)
                        if (item != null) {
                            ReelsVideoPage(
                                item = item,
                                // Activate only the settled page: during fast flings intermediate
                                // pages must not spin up a player and start downloading.
                                isActive = (page == pagerState.settledPage),
                                // Preload only the forward neighbor: buffering both neighbors
                                // kept up to three decoders alive and competed for bandwidth.
                                // A back-swipe replays quickly from the disk cache instead.
                                isPreload = preloadAllowed && page == pagerState.settledPage + 1,
                                isPlaying = state.isPlaying,
                                isMuted = state.isMuted,
                                isLiked = (item.id in state.likedIds),
                                isHdQuality = state.isHdQuality,
                                isAutoAdvance = state.isAutoAdvance,
                                isCropMode = state.isCropMode,
                                onTogglePlayPause = {
                                    chromeVisible = true
                                    screenModel.togglePlayPause()
                                },
                                onToggleLike = { screenModel.toggleLike(item) },
                                onToggleMute = screenModel::toggleMute,
                                onShare = {
                                    // Prefer the watch page URL; raw CDN links can expire.
                                    val shareUrl = item.webUrl ?: item.videoUrlHd
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                },
                                onTagClick = { tag ->
                                    screenModel.search(tag)
                                },
                                onVideoCompleted = {
                                    coroutineScope.launch {
                                        if (pagerState.currentPage < state.items.size - 1) {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                },
                                onPlaybackError = { msg ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                                headers = state.sourceHeaders,
                            )
                        }
                    }

                    // Next-page loading indicator
                    if (state.isLoading && state.items.isNotEmpty()) {
                        ReelsNextPageLoader(modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
            }

            // Top Bar Overlay; auto-hidden in immersive mode, revealed on tap or when a
            // search/filter/source sheet is open.
            AnimatedVisibility(
                visible = chromeVisible || state.isSearchBarOpen || state.isFilterDialogOpen ||
                    state.isSourcePickerOpen,
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                ReelsTopBar(
                    sourceName = if (state.isOffline) {
                        stringResource(MR.strings.reels_favorites_title)
                    } else {
                        state.sourceName
                    },
                    sourceIcon = state.sourceIcons[state.currentSourceId],
                    searchQuery = state.searchQuery,
                    isSearchBarOpen = state.isSearchBarOpen,
                    isAutoAdvance = state.isAutoAdvance,
                    isCropMode = state.isCropMode,
                    isHdQuality = state.isHdQuality,
                    preloadEnabled = state.preloadEnabled,
                    preloadWifiOnly = state.preloadWifiOnly,
                    isOffline = state.isOffline,
                    showSearch = state.supportsTags,
                    showSourcePicker = !state.isOffline && state.availableSources.size > 1,
                    onBackClick = { navigator.pop() },
                    onOpenSourcePicker = { screenModel.toggleSourcePicker(true) },
                    onToggleAutoAdvance = screenModel::toggleAutoAdvance,
                    onToggleCropMode = screenModel::toggleCropMode,
                    onToggleQuality = screenModel::toggleQuality,
                    onTogglePreload = screenModel::togglePreload,
                    onTogglePreloadWifiOnly = screenModel::togglePreloadWifiOnly,
                    onToggleSearchBar = { screenModel.toggleSearchBar(!state.isSearchBarOpen) },
                    onOpenFilterDialog = { screenModel.toggleFilterDialog(true) },
                    onOpenFavorites = { navigator.push(ReelsFavoritesScreen()) },
                    onSearch = screenModel::search,
                    onClearSearch = screenModel::clearSearch,
                    modifier = Modifier,
                )
            }

            // Filter Bottom Sheet Dialog
            if (state.isFilterDialogOpen) {
                SourceFilterAnimeDialog(
                    onDismissRequest = { screenModel.toggleFilterDialog(false) },
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = screenModel::applyFilters,
                    onUpdate = screenModel::setFilters,
                )
            }

            // Source Switcher Bottom Sheet Dialog
            if (state.isSourcePickerOpen) {
                ReelsSourcePickerSheet(
                    onDismissRequest = { screenModel.toggleSourcePicker(false) },
                    sources = state.availableSources,
                    currentSourceId = state.currentSourceId,
                    onSelectSource = screenModel::switchSource,
                    icons = state.sourceIcons,
                )
            }

            // Playback errors surface here (e.g. expired CDN link) instead of a silent poster.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun Context.isOnWifi(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
