package eu.kanade.tachiyomi.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.NestedMenuItem
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadHeaderItem
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadQueueScreenModel
import eu.kanade.tachiyomi.ui.download.anime.animeDownloadTab
import eu.kanade.tachiyomi.ui.download.manga.MangaDownloadHeaderItem
import eu.kanade.tachiyomi.ui.download.manga.MangaDownloadQueueScreenModel
import eu.kanade.tachiyomi.ui.download.manga.mangaDownloadTab
import eu.kanade.tachiyomi.ui.download.novel.NovelDownloadQueueScreenModel
import eu.kanade.tachiyomi.ui.download.novel.novelDownloadTab
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState as preferenceCollectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object DownloadsTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 6u,
                title = stringResource(MR.strings.label_download_queue),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().preferenceCollectAsState()
        val isAurora = theme.isAuroraStyle
        val auroraColors = AuroraTheme.colors
        val animeScreenModel = rememberScreenModel { AnimeDownloadQueueScreenModel() }
        val mangaScreenModel = rememberScreenModel { MangaDownloadQueueScreenModel() }
        val novelScreenModel = rememberScreenModel { NovelDownloadQueueScreenModel() }
        val animeDownloadList by animeScreenModel.state.collectAsState()
        val mangaDownloadList by mangaScreenModel.state.collectAsState()
        val novelDownloadsState by novelScreenModel.state.collectAsState()
        val animeDownloadCount by remember {
            derivedStateOf { animeDownloadList.sumOf { it.subItems.size } }
        }
        val mangaDownloadCount by remember {
            derivedStateOf { mangaDownloadList.sumOf { it.subItems.size } }
        }
        val novelDownloadCount by remember(novelDownloadsState.queueCount) {
            derivedStateOf { novelDownloadsState.queueCount }
        }

        val queueTabs = downloadQueueTabs()
        val state = rememberPagerState { queueTabs.size }
        val snackbarHostState = remember { SnackbarHostState() }
        val currentDownloadCount by remember(
            state.currentPage,
            queueTabs,
            animeDownloadCount,
            mangaDownloadCount,
            novelDownloadCount,
        ) {
            derivedStateOf {
                when (queueTabs[state.currentPage]) {
                    DownloadQueueTab.ANIME -> animeDownloadCount
                    DownloadQueueTab.MANGA -> mangaDownloadCount
                    DownloadQueueTab.NOVEL -> novelDownloadCount
                }
            }
        }

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        var fabExpanded by remember { mutableStateOf(true) }
        val nestedScrollConnection = remember {
            // All this lines just for fab state :/
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    fabExpanded = available.y >= 0
                    return scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    return scrollBehavior.nestedScrollConnection.onPostScroll(consumed, available, source)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    return scrollBehavior.nestedScrollConnection.onPreFling(available)
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    return scrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isAurora) {
                        Modifier.background(auroraColors.backgroundGradient)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Scaffold(
                containerColor = if (isAurora) Color.Transparent else MaterialTheme.colorScheme.background,
                topBar = {
                    AppBar(
                        titleContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(MR.strings.label_download_queue),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, false),
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isAurora) auroraColors.textPrimary else Color.Unspecified,
                                )
                                if (currentDownloadCount > 0) {
                                    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
                                    Pill(
                                        text = "$currentDownloadCount",
                                        modifier = Modifier.padding(start = 4.dp),
                                        color = if (isAurora) {
                                            auroraColors.accent.copy(alpha = 0.24f)
                                        } else {
                                            MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha)
                                        },
                                        contentColor = if (isAurora) {
                                            auroraColors.textPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onBackground
                                        },
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        },
                        navigateUp = navigator::pop,
                        backgroundColor = if (isAurora) Color.Transparent else null,
                        actions = {
                            when (queueTabs[state.currentPage]) {
                                DownloadQueueTab.ANIME -> AnimeActions(
                                    animeScreenModel = animeScreenModel,
                                    animeDownloadList = animeDownloadList,
                                    isAurora = isAurora,
                                )
                                DownloadQueueTab.MANGA -> MangaActions(
                                    mangaScreenModel = mangaScreenModel,
                                    mangaDownloadList = mangaDownloadList,
                                    isAurora = isAurora,
                                )
                                DownloadQueueTab.NOVEL -> Unit
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
                floatingActionButton = {
                    AnimatedVisibility(
                        visible = when (queueTabs[state.currentPage]) {
                            DownloadQueueTab.ANIME -> animeDownloadList.isNotEmpty()
                            DownloadQueueTab.MANGA -> mangaDownloadList.isNotEmpty()
                            DownloadQueueTab.NOVEL -> novelDownloadsState.queueCount > 0
                        },
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        val animeIsRunning by animeScreenModel.isDownloaderRunning.collectAsState()
                        val mangaIsRunning by mangaScreenModel.isDownloaderRunning.collectAsState()
                        ExtendedFloatingActionButton(
                            text = {
                                val id = when (queueTabs[state.currentPage]) {
                                    DownloadQueueTab.ANIME -> if (animeIsRunning) {
                                        MR.strings.action_pause
                                    } else {
                                        MR.strings.action_resume
                                    }
                                    DownloadQueueTab.MANGA -> if (mangaIsRunning) {
                                        MR.strings.action_pause
                                    } else {
                                        MR.strings.action_resume
                                    }
                                    DownloadQueueTab.NOVEL -> when {
                                        novelDownloadsState.isQueueRunning -> MR.strings.action_pause
                                        novelDownloadsState.pendingCount > 0 -> MR.strings.action_resume
                                        else -> MR.strings.action_retry
                                    }
                                }
                                Text(text = stringResource(id))
                            },
                            icon = {
                                val icon = when (queueTabs[state.currentPage]) {
                                    DownloadQueueTab.ANIME -> if (animeIsRunning) {
                                        Icons.Outlined.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    }
                                    DownloadQueueTab.MANGA -> if (mangaIsRunning) {
                                        Icons.Outlined.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    }
                                    DownloadQueueTab.NOVEL -> if (novelDownloadsState.isQueueRunning) {
                                        Icons.Outlined.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    }
                                }
                                Icon(imageVector = icon, contentDescription = null)
                            },
                            onClick = {
                                when (queueTabs[state.currentPage]) {
                                    DownloadQueueTab.ANIME -> if (animeIsRunning) {
                                        animeScreenModel.pauseDownloads()
                                    } else {
                                        animeScreenModel.startDownloads()
                                    }

                                    DownloadQueueTab.MANGA -> if (mangaIsRunning) {
                                        mangaScreenModel.pauseDownloads()
                                    } else {
                                        mangaScreenModel.startDownloads()
                                    }
                                    DownloadQueueTab.NOVEL -> when {
                                        novelDownloadsState.isQueueRunning -> novelScreenModel.pauseDownloads()
                                        novelDownloadsState.pendingCount > 0 -> novelScreenModel.startDownloads()
                                        else -> novelScreenModel.retryFailed()
                                    }
                                }
                            },
                            containerColor = if (isAurora) auroraColors.accent else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isAurora) auroraColors.textOnAccent else MaterialTheme.colorScheme.onPrimaryContainer,
                            expanded = fabExpanded,
                        )
                    }
                },
            ) { contentPadding ->
                Column(
                    modifier = Modifier.padding(
                        top = contentPadding.calculateTopPadding(),
                        start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                        end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                    ),
                ) {
                    if (isAurora) {
                        val auroraTabs = remember(animeDownloadCount, mangaDownloadCount, novelDownloadCount) {
                            persistentListOf(
                                TabContent(
                                    titleRes = AYMR.strings.label_anime,
                                    badgeNumber = animeDownloadCount,
                                    content = { _, _ -> },
                                ),
                                TabContent(
                                    titleRes = AYMR.strings.label_manga,
                                    badgeNumber = mangaDownloadCount,
                                    content = { _, _ -> },
                                ),
                                TabContent(
                                    titleRes = AYMR.strings.label_novel,
                                    badgeNumber = novelDownloadCount,
                                    content = { _, _ -> },
                                ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .zIndex(1f),
                        ) {
                            AuroraTabRow(
                                tabs = auroraTabs,
                                selectedIndex = state.currentPage,
                                onTabSelected = { index ->
                                    scope.launch { state.animateScrollToPage(index) }
                                },
                                scrollable = false,
                            )
                        }
                    } else {
                        PrimaryTabRow(
                            selectedTabIndex = state.currentPage,
                            modifier = Modifier.zIndex(1f),
                        ) {
                            queueTabs.forEachIndexed { index, tab ->
                                val (label, badgeCount) = when (tab) {
                                    DownloadQueueTab.ANIME -> AYMR.strings.label_anime to animeDownloadCount
                                    DownloadQueueTab.MANGA -> AYMR.strings.label_manga to mangaDownloadCount
                                    DownloadQueueTab.NOVEL -> AYMR.strings.label_novel to novelDownloadCount
                                }
                                Tab(
                                    selected = state.currentPage == index,
                                    onClick = { scope.launch { state.animateScrollToPage(index) } },
                                    text = {
                                        TabText(
                                            text = stringResource(label),
                                            badgeCount = badgeCount,
                                        )
                                    },
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    HorizontalPager(
                        modifier = Modifier.fillMaxSize(),
                        state = state,
                        verticalAlignment = Alignment.Top,
                        pageNestedScrollConnection = nestedScrollConnection,
                    ) { page ->
                        when (queueTabs[page]) {
                            DownloadQueueTab.ANIME -> animeDownloadTab(
                                nestedScrollConnection,
                            ).content(
                                PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                                snackbarHostState,
                            )
                            DownloadQueueTab.MANGA -> mangaDownloadTab(
                                nestedScrollConnection,
                            ).content(
                                PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                                snackbarHostState,
                            )
                            DownloadQueueTab.NOVEL -> novelDownloadTab(
                                nestedScrollConnection,
                            ).content(
                                PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                                snackbarHostState,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AnimeActions(
        animeScreenModel: AnimeDownloadQueueScreenModel,
        animeDownloadList: List<AnimeDownloadHeaderItem>,
        isAurora: Boolean,
    ) {
        if (animeDownloadList.isNotEmpty()) {
            var sortExpanded by remember { mutableStateOf(false) }
            var overflowExpanded by remember { mutableStateOf(false) }
            val onDismissRequest = { sortExpanded = false }
            val colors = AuroraTheme.colors
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = onDismissRequest,
            ) {
                NestedMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_order_by_upload_date)) },
                    children = { closeMenu ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(MR.strings.action_newest)) },
                            onClick = {
                                animeScreenModel.reorderQueue(
                                    { it.download.episode.dateUpload },
                                    true,
                                )
                                closeMenu()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(MR.strings.action_oldest)) },
                            onClick = {
                                animeScreenModel.reorderQueue(
                                    { it.download.episode.dateUpload },
                                    false,
                                )
                                closeMenu()
                            },
                        )
                    },
                )
                NestedMenuItem(
                    text = {
                        Text(
                            text = stringResource(AYMR.strings.action_order_by_episode_number),
                        )
                    },
                    children = { closeMenu ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(MR.strings.action_asc)) },
                            onClick = {
                                animeScreenModel.reorderQueue(
                                    { it.download.episode.episodeNumber },
                                    false,
                                )
                                closeMenu()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(MR.strings.action_desc)) },
                            onClick = {
                                animeScreenModel.reorderQueue(
                                    { it.download.episode.episodeNumber },
                                    true,
                                )
                                closeMenu()
                            },
                        )
                    },
                )
            }

            if (isAurora) {
                Box {
                    IconButton(
                        onClick = { sortExpanded = true },
                        modifier = Modifier
                            .background(colors.glass, CircleShape)
                            .size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Sort,
                            contentDescription = stringResource(MR.strings.action_sort),
                            tint = colors.textPrimary,
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier
                            .background(colors.glass, CircleShape)
                            .size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                            tint = colors.textPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.action_cancel_all)) },
                            onClick = {
                                animeScreenModel.clearQueue()
                                overflowExpanded = false
                            },
                        )
                    }
                }
            } else {
                AppBarActions(
                    persistentListOf(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_sort),
                            icon = Icons.AutoMirrored.Outlined.Sort,
                            onClick = { sortExpanded = true },
                        ),
                        AppBar.OverflowAction(
                            title = stringResource(MR.strings.action_cancel_all),
                            onClick = { animeScreenModel.clearQueue() },
                        ),
                    ),
                )
            }
        }
    }

    @Composable
    private fun MangaActions(
        mangaScreenModel: MangaDownloadQueueScreenModel,
        mangaDownloadList: List<MangaDownloadHeaderItem>,
        isAurora: Boolean,
    ) {
        if (mangaDownloadList.isNotEmpty()) {
            var sortExpanded by remember { mutableStateOf(false) }
            var overflowExpanded by remember { mutableStateOf(false) }
            val onDismissRequest = { sortExpanded = false }
            val colors = AuroraTheme.colors
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = onDismissRequest,
            ) {
                NestedMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_order_by_upload_date)) },
                    children = { closeMenu ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(MR.strings.action_newest)) },
                            onClick = {
                                mangaScreenModel.reorderQueue(
                                    { it.download.chapter.dateUpload },
                                    true,
                                )
                                closeMenu()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(MR.strings.action_oldest)) },
                            onClick = {
                                mangaScreenModel.reorderQueue(
                                    { it.download.chapter.dateUpload },
                                    false,
                                )
                                closeMenu()
                            },
                        )
                    },
                )
                NestedMenuItem(
                    text = {
                        Text(
                            text = stringResource(MR.strings.action_order_by_chapter_number),
                        )
                    },
                    children = { closeMenu ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(MR.strings.action_asc)) },
                            onClick = {
                                mangaScreenModel.reorderQueue(
                                    { it.download.chapter.chapterNumber },
                                    false,
                                )
                                closeMenu()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(MR.strings.action_desc)) },
                            onClick = {
                                mangaScreenModel.reorderQueue(
                                    { it.download.chapter.chapterNumber },
                                    true,
                                )
                                closeMenu()
                            },
                        )
                    },
                )
            }

            if (isAurora) {
                Box {
                    IconButton(
                        onClick = { sortExpanded = true },
                        modifier = Modifier
                            .background(colors.glass, CircleShape)
                            .size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Sort,
                            contentDescription = stringResource(MR.strings.action_sort),
                            tint = colors.textPrimary,
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier
                            .background(colors.glass, CircleShape)
                            .size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                            tint = colors.textPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.action_cancel_all)) },
                            onClick = {
                                mangaScreenModel.clearQueue()
                                overflowExpanded = false
                            },
                        )
                    }
                }
            } else {
                AppBarActions(
                    persistentListOf(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_sort),
                            icon = Icons.AutoMirrored.Outlined.Sort,
                            onClick = { sortExpanded = true },
                        ),
                        AppBar.OverflowAction(
                            title = stringResource(MR.strings.action_cancel_all),
                            onClick = { mangaScreenModel.clearQueue() },
                        ),
                    ),
                )
            }
        }
    }
}

internal enum class DownloadQueueTab {
    ANIME,
    MANGA,
    NOVEL,
}

internal fun downloadQueueTabs(): List<DownloadQueueTab> {
    return listOf(
        DownloadQueueTab.ANIME,
        DownloadQueueTab.MANGA,
        DownloadQueueTab.NOVEL,
    )
}
