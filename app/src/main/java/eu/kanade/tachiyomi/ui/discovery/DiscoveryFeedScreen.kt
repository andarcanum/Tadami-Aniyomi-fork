package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.novel.interactor.GetEnabledNovelSources
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.theme.auroraHeaderIconSurface
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.discovery.CompositeTrendingSource
import eu.kanade.tachiyomi.data.discovery.DiscoveryCoverRecovery
import eu.kanade.tachiyomi.data.discovery.DiscoveryMeta
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.toDirectEntryScreenOrNull
import eu.kanade.tachiyomi.ui.entries.suggestions.toGlobalSearchScreen
import eu.kanade.tachiyomi.ui.home.HomeDiscoveryExposureTracker
import eu.kanade.tachiyomi.ui.home.discoveryReasonText
import eu.kanade.tachiyomi.ui.home.toHomeHubDiscoveryItem
import eu.kanade.tachiyomi.ui.home.trackDiscoveryExposure
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable
import androidx.compose.foundation.lazy.items as lazyRowItems
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreenInterface
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as prefCollectAsStateWithLifecycle

/**
 * Полный экран ленты «Для тебя» v3 (компоновка V3): Aurora-glass тулбар, табы
 * сигналов [Микс|Похоже|Твой вкус|Свежее|Источник], чипсы провайдеров, сетка 3×N
 * унифицированных карточек (стиль «Недавно добавленные») с чипами-обоснованиями,
 * «+» добавить в библиотеку, лонг-пресс скрыть с Undo.
 */
class DiscoveryFeedScreen(val initialMediaKey: String) : Screen(), Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val appContext = remember(context) { context.applicationContext }
        val initialMedia = remember(initialMediaKey) {
            DiscoveryMediaType.fromKey(initialMediaKey) ?: DiscoveryMediaType.ANIME
        }
        val screenModel = rememberScreenModel {
            DiscoveryFeedScreenModel(initialMedia, context = appContext)
        }
        LaunchedEffect(Unit) { screenModel.start() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var tab by remember { mutableStateOf(FeedSignalTab.MIX) }
        var provider by remember { mutableStateOf<String?>(null) }
        val hazeState = remember { HazeState() }
        var sheetItem by remember { mutableStateOf<DiscoverySuggestion?>(null) }
        var sheetMeta by remember { mutableStateOf<DiscoveryMeta?>(null) }
        var sheetMetaLoading by remember { mutableStateOf(false) }
        var longPressItem by remember { mutableStateOf<DiscoverySuggestion?>(null) }
        val trendingSource = remember { CompositeTrendingSource() }

        val lifecycleOwner = LocalLifecycleOwner.current
        var isResumed by remember(lifecycleOwner) {
            mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                isResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val exposureTracker = remember(screenModel, scope) {
            HomeDiscoveryExposureTracker(scope) { homeItem ->
                screenModel.markItemExposed(homeItem.cleanTitle)
            }
        }
        LaunchedEffect(isResumed) {
            if (!isResumed) {
                exposureTracker.onPauseOrInactive()
            }
        }

        val navigateFor: (DiscoverySuggestion) -> Unit = { item ->
            scope.launch {
                if (item.rowType == DiscoveryRowType.SOURCE) {
                    // Ряд источника: открываем каталог источника напрямую, без глобального поиска.
                    val sourceId = when (state.mediaType) {
                        DiscoveryMediaType.ANIME -> Injekt.get<GetEnabledAnimeSources>()
                            .subscribe().first().firstOrNull { it.name == item.provider }?.id
                        DiscoveryMediaType.MANGA -> Injekt.get<GetEnabledMangaSources>()
                            .subscribe().first().firstOrNull { it.name == item.provider }?.id
                        DiscoveryMediaType.NOVEL -> Injekt.get<GetEnabledNovelSources>()
                            .subscribe().first().firstOrNull { it.name == item.provider }?.id
                    }
                    if (sourceId != null) {
                        navigator.push(
                            when (state.mediaType) {
                                DiscoveryMediaType.ANIME -> BrowseAnimeSourceScreen(sourceId, item.title)
                                DiscoveryMediaType.MANGA -> BrowseMangaSourceScreen(sourceId, item.title)
                                DiscoveryMediaType.NOVEL -> BrowseNovelSourceScreen(sourceId, item.title)
                            },
                        )
                        return@launch
                    }
                }
                val suggestionItem = item.toSuggestionItem()
                navigator.push(
                    suggestionItem.toDirectEntryScreenOrNull()
                        ?: suggestionItem.toGlobalSearchScreen(),
                )
            }
        }

        LaunchedEffect(sheetItem) {
            val item = sheetItem ?: return@LaunchedEffect
            sheetMeta = null
            sheetMetaLoading = true
            sheetMeta = runCatching { trendingSource.fetchMeta(item.title, state.mediaType) }.getOrNull()
            sheetMetaLoading = false
        }

        val colors = AuroraTheme.colors
        val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
        val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
        var topBarHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val topBarHeightDp = with(density) { topBarHeightPx.toDp() }

        Box(Modifier.fillMaxSize().background(colors.background)) {
            // Выбранный таб могли скрыть в настройках после открытия экрана — контент
            // и табы показывают effectiveTab (fallback MIX), чтобы не было пустого разрыва.
            val effectiveTab = if (tab in state.visibleTabs) tab else FeedSignalTab.MIX
            val feedItems = remember(state, effectiveTab, provider) {
                screenModel.displayedItems(effectiveTab, provider)
            }
            FeedBody(
                state = state,
                hazeState = hazeState,
                items = feedItems,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (topBarHeightDp > 0.dp) topBarHeightDp + 8.dp else 140.dp,
                    bottom = 24.dp,
                ),
                exposureTracker = exposureTracker,
                isResumed = isResumed,
                // Прямое открытие plugin-bound карточки; guard от повторного тапа
                // на время резолва, fallback в шторку при неполной привязке/ошибке.
                onItemClick = { item ->
                    if (state.openingItem != item) {
                        // Taste Engine: клик = слабый позитивный сигнал (guard исключает двойную запись).
                        screenModel.recordClick(item)
                        scope.launch {
                            screenModel.setOpenPending(item)
                            // finally: pending сбрасывается и при отмене (rotation/back),
                            // иначе guard залипнет на пережившем config change ScreenModel.
                            val entryId = try {
                                screenModel.resolveEntryId(item)
                            } finally {
                                screenModel.setOpenPending(null)
                            }
                            if (entryId != null) {
                                navigator.push(
                                    when (item.mediaType) {
                                        DiscoveryMediaType.ANIME -> AnimeScreen(entryId, fromSource = true)
                                        DiscoveryMediaType.MANGA -> MangaScreen(entryId, fromSource = true)
                                        DiscoveryMediaType.NOVEL -> NovelScreen(entryId, fromSource = true)
                                    },
                                )
                            } else {
                                sheetItem = item
                            }
                        }
                    }
                },
                onItemLongClick = { longPressItem = it },
                onItemAdd = { screenModel.addToLibrary(it) },
                onRetry = { screenModel.refreshNow() },
                modifier = Modifier
                    .fillMaxSize()
                    .auroraCenteredMaxWidth(contentMaxWidthDp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onSizeChanged { topBarHeightPx = it.height }
                    .then(
                        if (colors.isEInk) {
                            Modifier
                                .background(colors.surface)
                                .border(1.dp, colors.divider)
                        } else {
                            Modifier.hazeBlur(
                                input = HazeInput.Sources(hazeState),
                                style = HazeBlurStyle {
                                    backgroundColor(colors.background)
                                    colorEffects(
                                        listOf(
                                            HazeColorEffect.tint(
                                                colors.surface.copy(alpha = if (colors.isDark) 0.72f else 0.82f),
                                            ),
                                        ),
                                    )
                                    blurRadius(22.dp)
                                    noiseFactor(0.10f)
                                },
                            )
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraCenteredMaxWidth(contentMaxWidthDp),
                ) {
                    FeedToolbar(
                        state = state,
                        onBack = { navigator.pop() },
                        onRefresh = {
                            if (state.isRefreshing) {
                                screenModel.cancelRefresh()
                                context.toast(context.contextStringResource(MR.strings.action_cancel))
                            } else {
                                val cooldown = screenModel.rotateOrRefreshFeed(effectiveTab)
                                if (cooldown > 0L) {
                                    context.toast(
                                        context.contextStringResource(AYMR.strings.for_you_refresh_cooldown, cooldown),
                                    )
                                }
                            }
                        },
                    )
                    if (state.failedRows.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(AYMR.strings.for_you_failed_banner),
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    val tabCounts = remember(state) {
                        FeedSignalTab.entries.associateWith { itemsForTab(state, it).size }
                    }
                    // tab здесь — effectiveTab (fallback MIX вычислен выше, в Content).
                    FeedTabs(
                        tab = effectiveTab,
                        counts = tabCounts,
                        onTab = {
                            tab = it
                            screenModel.onTabSelected(it)
                        },
                        visibleTabs = state.visibleTabs,
                    )
                    FeedProviderChips(
                        options = providerOptions(state),
                        selected = provider,
                        onSelect = { provider = it },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            FeedSnackbar(
                state = state,
                onUndo = { screenModel.undoHide() },
                onDismissHidden = { screenModel.dismissHiddenSnackbar() },
                onDismissAdded = { screenModel.dismissAddedSnackbar() },
                onUndoTag = { screenModel.undoBlacklistTag() },
                onDismissTag = { screenModel.dismissTagSnackbar() },
            )
            // «+» промахнулся (внешний провайдер или нет точного совпадения в источнике
            // рекомендации): уходим в каталог источника/глобальный поиск вместо тупика.
            LaunchedEffect(state.searchFallbackItem) {
                val item = state.searchFallbackItem ?: return@LaunchedEffect
                screenModel.dismissSearchFallback()
                context.toast(context.contextStringResource(AYMR.strings.for_you_select_source_to_read))
                navigateFor(item)
            }
            sheetItem?.let { item ->
                DiscoveryPreviewSheet(
                    item = item,
                    meta = sheetMeta,
                    isMetaLoading = sheetMetaLoading,
                    coverMediaType = state.mediaType,
                    onDismiss = { sheetItem = null },
                    onAdd = {
                        screenModel.addToLibrary(item)
                        sheetItem = null
                    },
                    onFind = {
                        sheetItem = null
                        navigateFor(item)
                    },
                    onHide = {
                        screenModel.hide(item, effectiveTab)
                        sheetItem = null
                    },
                    hazeState = hazeState,
                )
            }
            longPressItem?.let { lpItem ->
                eu.kanade.tachiyomi.ui.home.DiscoveryHideOptionsSheet(
                    itemTitle = lpItem.title,
                    tag = eu.kanade.tachiyomi.ui.home.firstBlacklistTag(lpItem.genres, lpItem.rowType, lpItem.reason),
                    onHide = {
                        screenModel.hide(lpItem, effectiveTab)
                        longPressItem = null
                    },
                    onBlacklistTag = { tag ->
                        screenModel.blacklistTag(tag)
                        longPressItem = null
                    },
                    onDismiss = { longPressItem = null },
                    onMoreLikeThis = {
                        longPressItem = null
                        screenModel.recordLike(lpItem)
                    },
                    onMarkConsumed = {
                        longPressItem = null
                        screenModel.recordConsumed(lpItem, effectiveTab)
                    },
                )
            }
        }
    }
}

@Composable
private fun FeedToolbar(
    state: DiscoveryFeedUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val (labelKind, labelValue) = resolveUpdatedLabel(state.lastUpdatedAt, System.currentTimeMillis())
    val updatedLabel = if (state.isRefreshing && !state.progressText.isNullOrBlank()) {
        state.progressText
    } else {
        when (labelKind) {
            UpdatedLabelKind.MINUTES ->
                stringResource(AYMR.strings.for_you_updated_minutes, labelValue?.toInt() ?: 0)
            UpdatedLabelKind.HOURS ->
                stringResource(AYMR.strings.for_you_updated_hours, labelValue?.toInt() ?: 0)
            UpdatedLabelKind.NEVER -> stringResource(AYMR.strings.for_you_updated_never)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .auroraHeaderIconSurface(colors = colors)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 22.dp),
                    onClick = {
                        appHaptics.tap()
                        onBack()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(MR.strings.action_bar_up_description),
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                stringResource(AYMR.strings.aurora_for_you),
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(updatedLabel, color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .auroraHeaderIconSurface(colors = colors)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 22.dp),
                    onClick = {
                        appHaptics.tap()
                        onRefresh()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isRefreshing) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp,
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(MR.strings.action_cancel),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(AYMR.strings.for_you_refresh),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun FeedTabs(
    tab: FeedSignalTab,
    counts: Map<FeedSignalTab, Int>,
    onTab: (FeedSignalTab) -> Unit,
    visibleTabs: Set<FeedSignalTab> = FeedSignalTab.entries.toSet(),
) {
    val colors = AuroraTheme.colors
    val isDark = colors.isDark
    val appHaptics = LocalAppHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Выключенные в настройках ряды (или внешние провайдеры) — таб не рисуем:
        // иначе при «только плагины» SIMILAR/FRESH были бы перманентно пустыми.
        FeedSignalTab.entries.filter { it in visibleTabs }.forEach { signal ->
            val label = when (signal) {
                FeedSignalTab.MIX -> stringResource(AYMR.strings.for_you_tab_mix)
                FeedSignalTab.SIMILAR -> stringResource(AYMR.strings.for_you_tab_similar)
                FeedSignalTab.TASTE -> stringResource(AYMR.strings.for_you_tab_taste)
                FeedSignalTab.FRESH -> stringResource(AYMR.strings.for_you_tab_fresh)
                FeedSignalTab.SOURCE -> stringResource(AYMR.strings.for_you_tab_source)
            }
            val active = tab == signal
            val count = counts[signal] ?: 0

            val topBgAlpha = when {
                active -> if (isDark) 0.28f else 0.18f
                isDark -> 0.08f
                else -> 0.05f
            }
            val bottomBgAlpha = when {
                active -> if (isDark) 0.08f else 0.05f
                isDark -> 0.02f
                else -> 0.01f
            }
            val topBorderAlpha = when {
                active -> if (isDark) 0.85f else 0.95f
                isDark -> 0.22f
                else -> 0.18f
            }
            val bottomBorderAlpha = when {
                active -> if (isDark) 0.25f else 0.18f
                isDark -> 0.06f
                else -> 0.04f
            }

            val bgBrush = if (active) {
                Brush.verticalGradient(
                    listOf(
                        colors.accent.copy(alpha = topBgAlpha),
                        colors.accent.copy(alpha = bottomBgAlpha),
                    ),
                )
            } else {
                val tint = if (isDark) Color.White else Color.Black
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = topBgAlpha),
                        tint.copy(alpha = bottomBgAlpha),
                    ),
                )
            }

            val borderBrush = if (active) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = topBorderAlpha),
                        colors.accent.copy(alpha = bottomBorderAlpha),
                    ),
                )
            } else {
                val borderTint = if (isDark) Color.White else Color.Black
                Brush.verticalGradient(
                    listOf(
                        borderTint.copy(alpha = topBorderAlpha),
                        borderTint.copy(alpha = bottomBorderAlpha),
                    ),
                )
            }

            val chipShape = RoundedCornerShape(50)

            Row(
                Modifier
                    .clip(chipShape)
                    .background(bgBrush, chipShape)
                    .border(1.dp, borderBrush, chipShape)
                    .clickable {
                        appHaptics.tap()
                        onTab(signal)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    label,
                    color = if (active) {
                        if (colors.isEInk) {
                            colors.textOnAccent
                        } else if (isDark) {
                            colors.textPrimary
                        } else {
                            colors.accent
                        }
                    } else {
                        colors.textSecondary
                    },
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (count > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) {
                                    Brush.verticalGradient(listOf(colors.accent, colors.accentVariant))
                                } else {
                                    Brush.verticalGradient(
                                        listOf(
                                            (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f),
                                            (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f),
                                        ),
                                    )
                                },
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = count.toString(),
                            color = if (active) colors.textOnAccent else colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedProviderChips(
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val colors = AuroraTheme.colors
    val isDark = colors.isDark
    val appHaptics = LocalAppHaptics.current
    if (options.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        lazyRowItems(items = listOf(null) + options, key = { it ?: "all" }) { option ->
            val active = option == selected

            val topBgAlpha = if (active) 0.24f else 0.06f
            val bottomBgAlpha = if (active) 0.08f else 0.02f
            val topBorderAlpha = if (active) 0.85f else 0.20f
            val bottomBorderAlpha = if (active) 0.25f else 0.05f

            val bgBrush = if (active) {
                Brush.verticalGradient(
                    listOf(
                        colors.accent.copy(alpha = topBgAlpha),
                        colors.accent.copy(alpha = bottomBgAlpha),
                    ),
                )
            } else {
                val tint = if (isDark) Color.White else Color.Black
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = topBgAlpha),
                        tint.copy(alpha = bottomBgAlpha),
                    ),
                )
            }

            val borderBrush = if (active) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = topBorderAlpha),
                        colors.accent.copy(alpha = bottomBorderAlpha),
                    ),
                )
            } else {
                val borderTint = if (isDark) Color.White else Color.Black
                Brush.verticalGradient(
                    listOf(
                        borderTint.copy(alpha = topBorderAlpha),
                        borderTint.copy(alpha = bottomBorderAlpha),
                    ),
                )
            }
            val chipShape = RoundedCornerShape(50)

            Box(
                Modifier
                    .clip(chipShape)
                    .background(bgBrush, chipShape)
                    .border(1.dp, borderBrush, chipShape)
                    .clickable {
                        appHaptics.tap()
                        onSelect(option)
                    }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    option ?: stringResource(AYMR.strings.home_all_sources),
                    color = if (active) {
                        if (colors.isEInk) {
                            colors.textOnAccent
                        } else if (isDark) {
                            colors.textPrimary
                        } else {
                            colors.accent
                        }
                    } else {
                        colors.textSecondary
                    },
                    fontSize = 11.5.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FeedBody(
    state: DiscoveryFeedUiState,
    hazeState: HazeState,
    items: List<DiscoverySuggestion>,
    contentPadding: PaddingValues,
    exposureTracker: HomeDiscoveryExposureTracker,
    isResumed: Boolean,
    onItemClick: (DiscoverySuggestion) -> Unit,
    onItemLongClick: (DiscoverySuggestion) -> Unit,
    onItemAdd: (DiscoverySuggestion) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
    val showReasons by discoveryPreferences.showReasons().prefCollectAsStateWithLifecycle()
    val similarTemplate = stringResource(AYMR.strings.for_you_reason_similar)
    val trendTemplate = stringResource(AYMR.strings.for_you_reason_trending)
    val nextTemplate = stringResource(AYMR.strings.for_you_reason_season_next)

    when {
        state.isLoading -> Box(
            modifier
                .padding(contentPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.accent)
        }

        items.isEmpty() -> Column(
            modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(AYMR.strings.for_you_empty_title),
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(AYMR.strings.for_you_empty_subtitle),
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    appHaptics.tap()
                    onRetry()
                },
                enabled = !state.isRefreshing,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    stringResource(AYMR.strings.for_you_retry),
                    color = colors.textOnAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            modifier = modifier
                .then(if (colors.isEInk) Modifier else Modifier.hazeSource(hazeState)),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items = items, key = { it.rowType.key + ":" + it.cleanTitle }) { item ->
                val homeItem = remember(item) { item.toHomeHubDiscoveryItem() }
                val reason = if (showReasons) {
                    discoveryReasonText(homeItem, similarTemplate, trendTemplate, nextTemplate)
                } else {
                    null
                }
                FeedCard(
                    item = item,
                    reason = reason,
                    isAdding = item.title in state.addingTitles,
                    coverMediaType = state.mediaType,
                    coverRecoveryTick = state.coverRecoveryTick,
                    modifier = Modifier.trackDiscoveryExposure(
                        tracker = exposureTracker,
                        item = homeItem,
                        isSectionActive = true,
                        isResumed = isResumed,
                    ),
                    onClick = {
                        appHaptics.tap()
                        onItemClick(item)
                    },
                    onLongClick = {
                        appHaptics.tap()
                        onItemLongClick(item)
                    },
                    onAdd = {
                        appHaptics.tap()
                        onItemAdd(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun FeedCard(
    item: DiscoverySuggestion,
    reason: String?,
    isAdding: Boolean,
    coverMediaType: DiscoveryMediaType,
    coverRecoveryTick: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val isDark = colors.isDark
    val context = LocalContext.current
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Portrait)
    val coverReloadTick = rememberCoverReloadTick()
    val coverRequest = remember(context, item.coverUrl, item.provider, coverReloadTick, coverRecoveryTick) {
        buildAuroraCoverImageRequest(
            context,
            discoveryCoverData(
                coverMediaType,
                item.provider,
                item.coverUrl,
                DiscoveryCoverRecovery.get(coverMediaType, item.cleanTitle),
            ),
        )
    }
    val containerShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)

    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.02f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.08f),
                Color.Black.copy(alpha = 0.02f),
            ),
        )
    }

    Column(
        modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .clip(containerShape)
            .background(if (isDark) colors.glass.copy(alpha = 0.10f) else colors.cardBackground)
            .border(1.dp, borderBrush, containerShape)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(posterShape)
                .background(colors.cardBackground),
        ) {
            AsyncImage(
                model = coverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
                modifier = Modifier.fillMaxSize(),
                error = fallbackPainter,
                fallback = fallbackPainter,
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(48.dp)
                    .clickable(enabled = !isAdding, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.textOnAccent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(AYMR.strings.for_you_add_library),
                            tint = colors.textOnAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Text(
            item.title,
            color = colors.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        if (reason != null) {
            Text(
                reason,
                color = colors.accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun FeedSnackbar(
    state: DiscoveryFeedUiState,
    onUndo: () -> Unit,
    onDismissHidden: () -> Unit,
    onDismissAdded: () -> Unit,
    onUndoTag: () -> Unit,
    onDismissTag: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val hiddenTitle = state.hiddenSnackbarTitle
    val addedTitle = state.addedSnackbarTitle
    val tagSnack = state.tagSnackbar
    LaunchedEffect(hiddenTitle) {
        if (hiddenTitle != null) {
            delay(4000)
            onDismissHidden()
        }
    }
    LaunchedEffect(addedTitle) {
        if (addedTitle != null) {
            delay(4000)
            onDismissAdded()
        }
    }
    LaunchedEffect(tagSnack) {
        if (tagSnack != null) {
            delay(4000)
            onDismissTag()
        }
    }
    val message = when {
        hiddenTitle != null -> stringResource(AYMR.strings.for_you_hidden_snackbar)
        tagSnack != null -> stringResource(AYMR.strings.for_you_tag_blacklist_undo, tagSnack.second, tagSnack.first)
        addedTitle != null -> stringResource(AYMR.strings.for_you_added_snackbar, addedTitle)
        else -> null
    } ?: return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, color = colors.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                if (hiddenTitle != null || tagSnack != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_undo),
                        color = colors.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            appHaptics.tap()
                            if (tagSnack != null) onUndoTag() else onUndo()
                        },
                    )
                }
            }
        }
    }
}
