package eu.kanade.presentation.libraryUpdateError

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SwapCalls
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.more.settings.AuroraSettingsTopBarChrome
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.more.settings.AuroraTopBarTitleText
import eu.kanade.presentation.more.settings.SettingsAuroraBackground
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.auroraCardStyle
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorRunType
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorItem
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorScreenState
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.presentation.core.util.selectedBackground
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun LibraryUpdateErrorScreen(
    state: LibraryUpdateErrorScreenState,
    onTabSelected: (LibraryUpdateErrorMedia) -> Unit,
    onRetryVisibleErrors: () -> Unit,
    onClick: (LibraryUpdateErrorItem) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onMigrateSelected: (() -> Unit)?,
    onErrorsDelete: () -> Unit,
    onErrorDelete: (Long) -> Unit,
    onErrorSelected: (LibraryUpdateErrorItem, Boolean) -> Unit,
    navigateUp: () -> Unit,
) {
    val uiStyle = rememberResolvedSettingsUiStyle()
    val pagerState = rememberPagerState(
        initialPage = libraryUpdateErrorMediaDisplayIndexOf(state.selectedMedia).coerceAtLeast(0),
    ) { libraryUpdateErrorMediaDisplayOrder.size }

    val scope = rememberCoroutineScope()
    val onDisplayTabSelected: (LibraryUpdateErrorMedia) -> Unit = { media ->
        val page = libraryUpdateErrorMediaDisplayIndexOf(media)
        if (page >= 0 && pagerState.currentPage != page) {
            scope.launch { pagerState.scrollToPage(page) }
        }
        if (state.selectedMedia != media) {
            onTabSelected(media)
        }
    }

    // Sync from pagerState.currentPage to state.selectedMedia.
    // Do not drive the pager from state here: tab clicks already move the pager directly,
    // and bidirectional animated syncing makes quick tab switching race with swipe settling.
    LaunchedEffect(pagerState.currentPage) {
        val media = libraryUpdateErrorMediaDisplayOrder[pagerState.currentPage]
        if (state.selectedMedia != media) {
            onTabSelected(media)
        }
    }

    val onBackPressed = {
        if (state.selectionMode) {
            onSelectAll(false)
        } else {
            navigateUp()
        }
    }

    BackHandler(enabled = state.selectionMode, onBack = { onSelectAll(false) })

    when (uiStyle) {
        SettingsUiStyle.Classic -> {
            Scaffold(
                topBar = { scrollBehavior ->
                    AppBar(
                        title = stringResource(AYMR.strings.label_library_update_errors),
                        navigateUp = onBackPressed,
                        actions = {
                            if (state.visibleItems.isNotEmpty()) {
                                LibraryUpdateErrorRetryButton(
                                    isRetrying = state.isRetryingVisible,
                                    onClick = onRetryVisibleErrors,
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
                bottomBar = {
                    LibraryUpdateErrorSelectionBottomBar(
                        visible = state.selectionMode,
                        selectedCount = state.selected.size,
                        onSelectAll = { onSelectAll(true) },
                        onInvertSelection = onInvertSelection,
                        onMigrateSelected = onMigrateSelected,
                        onDelete = onErrorsDelete,
                    )
                },
            ) { contentPadding ->
                LibraryUpdateErrorContent(
                    state = state,
                    contentPadding = contentPadding,
                    uiStyle = uiStyle,
                    pagerState = pagerState,
                    onTabSelected = onDisplayTabSelected,
                    onClick = onClick,
                    onErrorDelete = onErrorDelete,
                    onErrorSelected = onErrorSelected,
                )
            }
        }
        SettingsUiStyle.Aurora -> {
            val layoutDirection = LocalLayoutDirection.current
            val topBarState = rememberTopAppBarState()
            val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
            Scaffold(
                topBarScrollBehavior = topBarScrollBehavior,
                containerColor = Color.Transparent,
                topBar = { scrollBehavior ->
                    AuroraSettingsTopBarChrome(scrollBehavior) {
                        AuroraLibraryUpdateErrorTopBar(
                            state = state,
                            onBackPressed = onBackPressed,
                            onRetryVisibleErrors = onRetryVisibleErrors,
                        )
                    }
                },
                bottomBar = {
                    LibraryUpdateErrorSelectionBottomBar(
                        visible = state.selectionMode,
                        selectedCount = state.selected.size,
                        onSelectAll = { onSelectAll(true) },
                        onInvertSelection = onInvertSelection,
                        onMigrateSelected = onMigrateSelected,
                        onDelete = onErrorsDelete,
                    )
                },
            ) { contentPadding ->
                SettingsAuroraBackground(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LibraryUpdateErrorContent(
                            state = state,
                            contentPadding = PaddingValues(
                                start = contentPadding.calculateLeftPadding(layoutDirection),
                                top = contentPadding.calculateTopPadding(),
                                end = contentPadding.calculateRightPadding(layoutDirection),
                                bottom = contentPadding.calculateBottomPadding() + 16.dp,
                            ),
                            uiStyle = uiStyle,
                            pagerState = pagerState,
                            onTabSelected = onDisplayTabSelected,
                            onClick = onClick,
                            onErrorDelete = onErrorDelete,
                            onErrorSelected = onErrorSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryUpdateErrorRetryButton(
    isRetrying: Boolean,
    onClick: () -> Unit,
    aurora: Boolean = false,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "library_update_error_retry_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "library_update_error_retry_icon_rotation",
    )
    val contentDescription = stringResource(MR.strings.action_update_library)
    val iconModifier = Modifier.rotate(if (isRetrying) rotation else 0f)

    if (aurora) {
        AuroraTopBarIconButton(
            onClick = onClick,
            icon = Icons.Outlined.Refresh,
            contentDescription = contentDescription,
            iconRotation = if (isRetrying) rotation else 0f,
        )
    } else {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = contentDescription,
                modifier = iconModifier,
            )
        }
    }
}

@Composable
private fun AuroraLibraryUpdateErrorTopBar(
    state: LibraryUpdateErrorScreenState,
    onBackPressed: () -> Unit,
    onRetryVisibleErrors: () -> Unit,
) {
    val title = stringResource(AYMR.strings.label_library_update_errors)
    val icon = Icons.AutoMirrored.Filled.ArrowBack
    val contentDescription = stringResource(MR.strings.action_bar_up_description)

    val actions = @Composable {
        if (state.visibleItems.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryUpdateErrorRetryButton(
                    isRetrying = state.isRetryingVisible,
                    onClick = onRetryVisibleErrors,
                    aurora = true,
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuroraTopBarIconButton(
            onClick = onBackPressed,
            icon = icon,
            contentDescription = contentDescription,
        )

        AuroraTopBarTitleText(
            title = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp),
        )

        actions()
    }
}

@Composable
private fun LibraryUpdateErrorSelectionBottomBar(
    visible: Boolean,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onMigrateSelected: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = ZeroCornerSize,
                bottomStart = ZeroCornerSize,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Text(
                    text = selectedCount.toString(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryUpdateErrorBottomAction(
                        title = stringResource(MR.strings.action_select_all),
                        icon = Icons.Outlined.SelectAll,
                        onClick = onSelectAll,
                        modifier = Modifier.weight(1f),
                    )
                    LibraryUpdateErrorBottomAction(
                        title = stringResource(MR.strings.action_select_inverse),
                        icon = Icons.Outlined.FlipToBack,
                        onClick = onInvertSelection,
                        modifier = Modifier.weight(1f),
                    )
                    if (onMigrateSelected != null) {
                        LibraryUpdateErrorBottomAction(
                            title = stringResource(MR.strings.action_migrate),
                            icon = Icons.Outlined.SwapCalls,
                            onClick = onMigrateSelected,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LibraryUpdateErrorBottomAction(
                        title = stringResource(MR.strings.action_delete),
                        icon = Icons.Outlined.DeleteOutline,
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryUpdateErrorBottomAction(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun LibraryUpdateErrorContent(
    state: LibraryUpdateErrorScreenState,
    contentPadding: PaddingValues,
    uiStyle: SettingsUiStyle,
    pagerState: PagerState,
    onTabSelected: (LibraryUpdateErrorMedia) -> Unit,
    onClick: (LibraryUpdateErrorItem) -> Unit,
    onErrorDelete: (Long) -> Unit,
    onErrorSelected: (LibraryUpdateErrorItem, Boolean) -> Unit,
) {
    val colors = AuroraTheme.colors
    val layoutDirection = LocalLayoutDirection.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
            ),
    ) {
        LibraryUpdateErrorTabs(
            selected = libraryUpdateErrorMediaDisplayOrder[pagerState.currentPage],
            counts = libraryUpdateErrorMediaDisplayOrder.associateWith { state.count(it) },
            onTabSelected = onTabSelected,
            uiStyle = uiStyle,
        )

        if (uiStyle == SettingsUiStyle.Aurora) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val media = libraryUpdateErrorMediaDisplayOrder[page]
            val itemsForMedia = remember(state.items, media) {
                state.items.filter { it.record.media == media }
            }
            val isLoading = state.isLoading

            when {
                isLoading -> LoadingScreen(modifier = Modifier.fillMaxSize())
                itemsForMedia.isEmpty() -> EmptyScreen(
                    message = stringResource(AYMR.strings.info_empty_library_update_errors),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> {
                    val uiModels = remember(itemsForMedia) {
                        itemsForMedia
                            .sortedWith(
                                compareBy<LibraryUpdateErrorItem> {
                                    it.record.message
                                }.thenBy { it.record.title },
                            )
                            .groupBy { it.record.message }
                            .flatMap { (message, errors) ->
                                listOf(LibraryUpdateErrorUiModel.Header(message, errors.size)) +
                                    errors.map { LibraryUpdateErrorUiModel.Item(it) }
                            }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = contentPadding.calculateBottomPadding() + 8.dp,
                        ),
                    ) {
                        items(
                            items = uiModels,
                            key = { model ->
                                when (model) {
                                    is LibraryUpdateErrorUiModel.Header -> "header-${media.name}-${model.errorMessage}"
                                    is LibraryUpdateErrorUiModel.Item -> "error-${model.item.record.id}"
                                }
                            },
                        ) { model ->
                            when (model) {
                                is LibraryUpdateErrorUiModel.Header -> {
                                    if (uiStyle == SettingsUiStyle.Aurora) {
                                        Text(
                                            text = "${model.errorMessage} (${model.count})",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 8.dp),
                                            color = colors.textSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    } else {
                                        ListGroupHeader(
                                            text = "${model.errorMessage} (${model.count})",
                                        )
                                    }
                                }
                                is LibraryUpdateErrorUiModel.Item -> LibraryUpdateErrorRow(
                                    item = model.item,
                                    selectionMode = state.selectionMode,
                                    uiStyle = uiStyle,
                                    onClick = onClick,
                                    onSelected = onErrorSelected,
                                    onDelete = onErrorDelete,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryUpdateErrorTabs(
    selected: LibraryUpdateErrorMedia,
    counts: Map<LibraryUpdateErrorMedia, Int>,
    onTabSelected: (LibraryUpdateErrorMedia) -> Unit,
    uiStyle: SettingsUiStyle,
) {
    if (uiStyle == SettingsUiStyle.Aurora) {
        val tabs = remember(counts) {
            persistentListOf(
                TabContent(
                    titleRes = AYMR.strings.library_update_error_tab_anime,
                    badgeNumber = counts[LibraryUpdateErrorMedia.Anime],
                    content = { _, _ -> },
                ),
                TabContent(
                    titleRes = AYMR.strings.library_update_error_tab_manga,
                    badgeNumber = counts[LibraryUpdateErrorMedia.Manga],
                    content = { _, _ -> },
                ),
                TabContent(
                    titleRes = AYMR.strings.library_update_error_tab_novel,
                    badgeNumber = counts[LibraryUpdateErrorMedia.Novel],
                    content = { _, _ -> },
                ),
            )
        }
        AuroraTabRow(
            tabs = tabs,
            selectedIndex = libraryUpdateErrorMediaDisplayIndexOf(selected),
            onTabSelected = { index -> onTabSelected(libraryUpdateErrorMediaDisplayOrder[index]) },
            scrollable = false,
        )
    } else {
        PrimaryScrollableTabRow(selectedTabIndex = libraryUpdateErrorMediaDisplayIndexOf(selected)) {
            libraryUpdateErrorMediaDisplayOrder.forEach { media ->
                Tab(
                    selected = media == selected,
                    onClick = { onTabSelected(media) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = media.title())
                            val count = counts[media].orEmpty()
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge { Text(text = count.toString()) }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryUpdateErrorRow(
    item: LibraryUpdateErrorItem,
    selectionMode: Boolean,
    uiStyle: SettingsUiStyle,
    onClick: (LibraryUpdateErrorItem) -> Unit,
    onSelected: (LibraryUpdateErrorItem, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val record = item.record
    val colors = AuroraTheme.colors

    val rowContent = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectedBackground(item.selected)
                .combinedClickable(
                    onClick = {
                        if (selectionMode) {
                            onSelected(item, !item.selected)
                        } else {
                            onClick(item)
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelected(item, !item.selected)
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemCover.Book(
                data = record.thumbnailUrl,
                modifier = Modifier
                    .width(44.dp)
                    .height(66.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.secondaryItemAlpha(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val runTypeStr = when (record.runType) {
                        LibraryUpdateErrorRunType.Manual -> stringResource(AYMR.strings.library_update_error_manual)
                        LibraryUpdateErrorRunType.Automatic -> stringResource(
                            AYMR.strings.library_update_error_automatic,
                        )
                    }
                    Text(
                        text = "${record.sourceName} • $runTypeStr",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.retrying) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 4.dp)
                        .size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = { onDelete(record.id) }) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    tint = colors.textPrimary,
                    contentDescription = stringResource(MR.strings.action_delete),
                )
            }
        }
    }

    if (uiStyle == SettingsUiStyle.Aurora) {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val darkRimLightEnabled by uiPreferences.auroraDarkRimLightEnabled().collectAsState()
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .auroraCardStyle(colors, RoundedCornerShape(16.dp), applyDarkRimLight = darkRimLightEnabled),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (!colors.isDark && !colors.isEInk) {
                    Color.Transparent
                } else {
                    resolveAuroraMoreCardContainerColor(colors)
                },
            ),
            border = when {
                item.selected -> BorderStroke(2.dp, colors.accent)
                colors.isEInk -> BorderStroke(1.dp, resolveAuroraMoreCardBorderColor(colors))
                else -> null
            },
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
            ),
        ) {
            rowContent()
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            rowContent()
        }
    }
}

@Composable
private fun LibraryUpdateErrorMedia.title(): String {
    return when (this) {
        LibraryUpdateErrorMedia.Manga -> stringResource(AYMR.strings.library_update_error_tab_manga)
        LibraryUpdateErrorMedia.Anime -> stringResource(AYMR.strings.library_update_error_tab_anime)
        LibraryUpdateErrorMedia.Novel -> stringResource(AYMR.strings.library_update_error_tab_novel)
    }
}

private fun Int?.orEmpty(): Int = this ?: 0

private val libraryUpdateErrorMediaDisplayOrder = listOf(
    LibraryUpdateErrorMedia.Anime,
    LibraryUpdateErrorMedia.Manga,
    LibraryUpdateErrorMedia.Novel,
)

private fun libraryUpdateErrorMediaDisplayIndexOf(media: LibraryUpdateErrorMedia): Int {
    return libraryUpdateErrorMediaDisplayOrder.indexOf(media)
}
