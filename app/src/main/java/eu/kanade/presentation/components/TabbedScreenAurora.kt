package eu.kanade.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

data class TabState(
    val tabs: ImmutableList<TabContent>,
    val selectedIndex: Int,
    val onTabSelected: (Int) -> Unit,
)
val LocalTabState = compositionLocalOf<TabState?> { null }

@Composable
fun TabbedScreenAurora(
    titleRes: StringResource?,
    tabs: ImmutableList<TabContent>,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState { tabs.size },
    mangaSearchQuery: String? = null,
    onChangeMangaSearchQuery: (String?) -> Unit = {},
    scrollable: Boolean = false,
    animeSearchQuery: String? = null,
    onChangeAnimeSearchQuery: (String?) -> Unit = {},
    isMangaTab: (Int) -> Boolean = { it % 2 == 1 },
    showCompactHeader: Boolean = false,
    userName: String? = null,
    userAvatar: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    onNameClick: (() -> Unit)? = null,
    applyStatusBarsPadding: Boolean = true,
    showTabs: Boolean = true,
    instantTabSwitching: Boolean = false,
    highlightSearchAction: Boolean = false,
    highlightedActionTitle: String? = null,
    extraHeaderContent: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val switchThresholdPx = with(density) { 120.dp.toPx() }
    val maxBouncePx = with(density) { 16.dp.toPx() }
    var instantSelectedPage by rememberSaveable { mutableIntStateOf(0) }
    var previousInstantTabSwitching by remember { mutableStateOf(instantTabSwitching) }
    val currentPage = if (tabs.isEmpty()) {
        0
    } else if (instantTabSwitching) {
        instantSelectedPage.coerceIn(0, tabs.lastIndex)
    } else {
        state.currentPage.coerceIn(0, tabs.lastIndex)
    }

    val isMangaPage = isMangaTab(currentPage)
    val activeSearchQuery = if (isMangaPage) mangaSearchQuery else animeSearchQuery
    val onChangeSearchQuery = if (isMangaPage) onChangeMangaSearchQuery else onChangeAnimeSearchQuery
    val isSearchActive = activeSearchQuery != null

    val colors = AuroraTheme.colors
    var edgeBounceTargetPx by remember { mutableFloatStateOf(0f) }
    val edgeBounceOffsetPx by animateFloatAsState(
        targetValue = edgeBounceTargetPx,
        label = "auroraEdgeBounce",
    )

    val onTabSelected: (Int) -> Unit = { index ->
        if (tabs.isNotEmpty()) {
            val targetIndex = index.coerceIn(0, tabs.lastIndex)
            if (instantTabSwitching) {
                instantSelectedPage = targetIndex
            } else {
                scope.launch {
                    state.animateScrollToPage(targetIndex)
                }
            }
        }
    }

    LaunchedEffect(instantTabSwitching, tabs.size) {
        if (tabs.isEmpty()) return@LaunchedEffect
        val lastIndex = tabs.lastIndex
        if (instantTabSwitching) {
            instantSelectedPage = state.currentPage.coerceIn(0, lastIndex)
        } else if (state.currentPage > lastIndex) {
            state.scrollToPage(lastIndex)
        }
    }
    LaunchedEffect(instantTabSwitching, state.currentPage, tabs.size) {
        if (!instantTabSwitching || tabs.isEmpty()) return@LaunchedEffect
        val targetIndex = state.currentPage.coerceIn(0, tabs.lastIndex)
        if (instantSelectedPage != targetIndex) {
            instantSelectedPage = targetIndex
        }
    }
    LaunchedEffect(instantTabSwitching, instantSelectedPage, state.currentPage, tabs.size) {
        if (tabs.isEmpty()) return@LaunchedEffect

        if (instantTabSwitching) {
            val targetIndex = instantSelectedPage.coerceIn(0, tabs.lastIndex)
            if (state.currentPage != targetIndex) {
                state.scrollToPage(targetIndex)
            }
        } else {
            // Sync pager only when transitioning from instant switching to pager mode.
            if (previousInstantTabSwitching) {
                val targetIndex = instantSelectedPage.coerceIn(0, tabs.lastIndex)
                if (state.currentPage != targetIndex) {
                    state.scrollToPage(targetIndex)
                }
            }
        }

        previousInstantTabSwitching = instantTabSwitching
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (applyStatusBarsPadding) {
                Spacer(modifier = Modifier.statusBarsPadding())
            }

            if (!showCompactHeader && titleRes != null) {
                AuroraTabHeader(
                    title = stringResource(titleRes),
                    isSearchActive = isSearchActive,
                    searchQuery = activeSearchQuery ?: "",
                    onSearchClick = { onChangeSearchQuery("") },
                    onSearchClose = { onChangeSearchQuery(null) },
                    onSearchQueryChange = { onChangeSearchQuery(it) },
                    tabs = tabs,
                    currentPage = currentPage,
                    navigateUp = null, // Top-level tabs generally don't have up navigation in this context
                    highlightSearchAction = highlightSearchAction,
                    highlightedActionTitle = highlightedActionTitle,
                )
            }

            extraHeaderContent()

            // Add tabs for Browse
            if (showTabs) {
                AuroraTabRow(
                    tabs = tabs,
                    selectedIndex = currentPage,
                    onTabSelected = onTabSelected,
                    scrollable = scrollable,
                )
            }

            if (instantTabSwitching) {
                val tabState = TabState(
                    tabs = tabs,
                    selectedIndex = currentPage,
                    onTabSelected = onTabSelected,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentPage, tabs.size, switchThresholdPx, maxBouncePx) {
                            var totalDragPx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    totalDragPx = 0f
                                    edgeBounceTargetPx = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDragPx += dragAmount
                                },
                                onDragCancel = {
                                    totalDragPx = 0f
                                    edgeBounceTargetPx = 0f
                                },
                                onDragEnd = {
                                    val decision = resolveInstantTabSwitch(
                                        currentIndex = currentPage,
                                        lastIndex = tabs.lastIndex,
                                        totalDragPx = totalDragPx,
                                        switchThresholdPx = switchThresholdPx,
                                    )

                                    if (decision.shouldBounceEdge) {
                                        val direction = if (totalDragPx > 0f) 1f else -1f
                                        scope.launch {
                                            edgeBounceTargetPx = direction * maxBouncePx
                                            delay(90)
                                            edgeBounceTargetPx = 0f
                                        }
                                    } else {
                                        edgeBounceTargetPx = 0f
                                    }

                                    if (decision.targetIndex != currentPage) {
                                        onTabSelected(decision.targetIndex)
                                    }

                                    totalDragPx = 0f
                                },
                            )
                        }
                        .offset { IntOffset(edgeBounceOffsetPx.roundToInt(), 0) },
                ) {
                    if (tabs.isNotEmpty()) {
                        val page = currentPage
                        key(page) {
                            CompositionLocalProvider(LocalTabState provides tabState) {
                                tabs[page].content(
                                    PaddingValues(bottom = 16.dp),
                                    snackbarHostState,
                                )
                            }
                        }
                    }
                }
            } else {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val tabState = TabState(
                        tabs = tabs,
                        selectedIndex = state.currentPage,
                        onTabSelected = onTabSelected,
                    )
                    CompositionLocalProvider(LocalTabState provides tabState) {
                        tabs[page].content(
                            PaddingValues(bottom = 16.dp),
                            snackbarHostState,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun AuroraTabHeader(
    title: String,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    tabs: ImmutableList<TabContent>,
    currentPage: Int,
    navigateUp: (() -> Unit)?,
    highlightSearchAction: Boolean,
    highlightedActionTitle: String?,
) {
    val colors = AuroraTheme.colors
    val currentTab = tabs.getOrNull(currentPage)
    val actions = currentTab?.actions.orEmpty()
    val iconActions = actions.filterIsInstance<AppBar.Action>()
    val overflowActions = actions.filterIsInstance<AppBar.OverflowAction>()
    var showOverflowMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigateUp != null) {
            IconButton(
                onClick = navigateUp,
                modifier = Modifier
                    .background(colors.glass, CircleShape)
                    .size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(MR.strings.action_bar_up_description),
                    tint = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        if (isSearchActive) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                placeholder = {
                    Text(
                        text = stringResource(MR.strings.action_search),
                        color = colors.textSecondary,
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.glass,
                    unfocusedContainerColor = colors.glass,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onSearchClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    }
                },
            )
        } else {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentTab?.searchEnabled == true) {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .background(colors.glass, CircleShape)
                            .size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(MR.strings.action_search),
                            tint = if (highlightSearchAction) colors.accent else colors.textPrimary,
                        )
                    }
                }

                iconActions.forEach { appBarAction ->
                    IconButton(
                        onClick = appBarAction.onClick,
                        modifier = Modifier
                            .background(colors.glass, CircleShape)
                            .size(44.dp),
                    ) {
                        Icon(
                            imageVector = appBarAction.icon,
                            contentDescription = appBarAction.title,
                            tint = if (appBarAction.title == highlightedActionTitle) {
                                colors.accent
                            } else {
                                colors.textPrimary
                            },
                        )
                    }
                }

                if (overflowActions.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .background(colors.glass, CircleShape)
                                .size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(
                                    MR.strings.action_menu_overflow_description,
                                ),
                                tint = colors.textPrimary,
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            overflowActions.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.title, fontWeight = FontWeight.Normal) },
                                    onClick = {
                                        action.onClick()
                                        showOverflowMenu = false
                                    },
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
internal fun AuroraTabRow(
    tabs: ImmutableList<TabContent>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    scrollable: Boolean,
) {
    val colors = AuroraTheme.colors
    val scrollState = rememberScrollState()

    // Segmented pill container - adaptive glass background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                colors.glass,
                RoundedCornerShape(28.dp),
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier),
            horizontalArrangement = if (scrollable) Arrangement.Start else Arrangement.SpaceEvenly,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                AuroraTab(
                    text = stringResource(tab.titleRes),
                    isSelected = isSelected,
                    badgeCount = tab.badgeNumber,
                    onClick = { onTabSelected(index) },
                    modifier = if (scrollable) Modifier.padding(horizontal = 4.dp) else Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun AuroraTab(
    text: String,
    isSelected: Boolean,
    badgeCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    // Segmented tab style: lighter filled background for active tab (good contrast on dark mode)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                style = TextStyle(hyphens = Hyphens.None),
            )

            if (badgeCount != null && badgeCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = colors.textOnAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
