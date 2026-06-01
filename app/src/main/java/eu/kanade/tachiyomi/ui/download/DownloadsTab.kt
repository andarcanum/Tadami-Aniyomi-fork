package eu.kanade.tachiyomi.ui.download

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.tadami.aurora.R
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.NestedMenuItem
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.download.DownloadEngineCard
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.more.settings.AuroraTopBarLayout
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.engine.DownloadEngineFacade
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.ui.download.DownloadEngineScreenModel
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
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as stringResourceCtx
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as preferenceCollectAsState

private fun openDownloadFolder(context: android.content.Context, subdirectory: String? = null) {
    val storageManager: StorageManager = Injekt.get()
    val dir = if (subdirectory != null) {
        storageManager.getDownloadsDirectory()?.createDirectory(subdirectory)
    } else {
        storageManager.getDownloadsDirectory()
    }

    if (dir == null) {
        Toast.makeText(
            context,
            context.stringResourceCtx(AYMR.strings.download_folder_not_set),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(dir.uri, "resource/folder")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
            data = dir.uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.stringResourceCtx(AYMR.strings.no_file_manager_found),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

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
        val context = LocalContext.current
        val isRussian = context.resources.configuration.locales.get(0).language == "ru"
        val scope = rememberCoroutineScope()
        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().preferenceCollectAsState()
        val isAurora = theme.isAuroraStyle
        val auroraColors = AuroraTheme.colors
        val animeScreenModel = rememberScreenModel { AnimeDownloadQueueScreenModel() }
        val mangaScreenModel = rememberScreenModel { MangaDownloadQueueScreenModel() }
        val novelScreenModel = rememberScreenModel { NovelDownloadQueueScreenModel() }
        // Shared download engine facade combining all three backends
        val engineFacade = remember {
            DownloadEngineFacade(
                animeManager = Injekt.get<AnimeDownloadManager>(),
                mangaManager = Injekt.get<MangaDownloadManager>(),
            )
        }
        val engineScreenModel = rememberScreenModel { DownloadEngineScreenModel(engineFacade) }
        val engineSnapshot by engineScreenModel.state.collectAsStateWithLifecycle()
        val animeDownloadList by animeScreenModel.state.collectAsStateWithLifecycle()
        val mangaDownloadList by mangaScreenModel.state.collectAsStateWithLifecycle()
        val novelDownloadsState by novelScreenModel.state.collectAsStateWithLifecycle()
        val animeDownloadCount by remember {
            derivedStateOf {
                animeDownloadList.sumOf { header ->
                    header.subItems.count { it.download.status != AnimeDownload.State.DOWNLOADED }
                }
            }
        }
        val mangaDownloadCount by remember {
            derivedStateOf {
                mangaDownloadList.sumOf { header ->
                    header.subItems.count { it.download.status != MangaDownload.State.DOWNLOADED }
                }
            }
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
        val nestedScrollConnection = scrollBehavior.nestedScrollConnection

        val screenContent: @Composable () -> Unit = {
            Scaffold(
                containerColor = if (isAurora) Color.Transparent else MaterialTheme.colorScheme.background,
                topBar = {
                    if (isAurora) {
                        AuroraTopBarLayout(
                            title = stringResource(MR.strings.label_download_queue),
                            titleContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isRussian) {
                                            "Очередь"
                                        } else {
                                            stringResource(
                                                MR.strings.label_download_queue,
                                            )
                                        },
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = auroraColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, false),
                                    )
                                    if (currentDownloadCount > 0) {
                                        Pill(
                                            text = "$currentDownloadCount",
                                            modifier = Modifier.padding(start = 4.dp),
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            },
                            onNavigateUp = navigator::pop,
                            actions = {
                                when (queueTabs[state.currentPage]) {
                                    DownloadQueueTab.ANIME -> AnimeActions(
                                        animeScreenModel = animeScreenModel,
                                        animeDownloadList = animeDownloadList,
                                        isAurora = true,
                                        onOpenFolder = { openDownloadFolder(context) },
                                    )
                                    DownloadQueueTab.MANGA -> MangaActions(
                                        mangaScreenModel = mangaScreenModel,
                                        mangaDownloadList = mangaDownloadList,
                                        isAurora = true,
                                        onOpenFolder = { openDownloadFolder(context) },
                                    )
                                    DownloadQueueTab.NOVEL -> NovelActions(
                                        isAurora = true,
                                        onOpenFolder = { openDownloadFolder(context, "novels") },
                                    )
                                }
                            },
                        )
                    } else {
                        AppBar(
                            titleContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(MR.strings.label_download_queue),
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f, false),
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (currentDownloadCount > 0) {
                                        Pill(
                                            text = "$currentDownloadCount",
                                            modifier = Modifier.padding(start = 4.dp),
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            },
                            navigateUp = navigator::pop,
                            actions = {
                                when (queueTabs[state.currentPage]) {
                                    DownloadQueueTab.ANIME -> AnimeActions(
                                        animeScreenModel = animeScreenModel,
                                        animeDownloadList = animeDownloadList,
                                        isAurora = false,
                                        onOpenFolder = { openDownloadFolder(context) },
                                    )
                                    DownloadQueueTab.MANGA -> MangaActions(
                                        mangaScreenModel = mangaScreenModel,
                                        mangaDownloadList = mangaDownloadList,
                                        isAurora = false,
                                        onOpenFolder = { openDownloadFolder(context) },
                                    )
                                    DownloadQueueTab.NOVEL -> NovelActions(
                                        isAurora = false,
                                        onOpenFolder = { openDownloadFolder(context, "novels") },
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior,
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

                    // Shared download engine card
                    DownloadEngineCard(
                        snapshot = engineSnapshot,
                        onPauseAll = engineScreenModel::pauseAll,
                        onResumeAll = engineScreenModel::resumeAll,
                        onCancelAll = engineScreenModel::cancelAll,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

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

        if (isAurora) {
            AuroraBackground {
                screenContent()
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                screenContent()
            }
        }
    }

    @Composable
    private fun AnimeActions(
        animeScreenModel: AnimeDownloadQueueScreenModel,
        animeDownloadList: List<AnimeDownloadHeaderItem>,
        isAurora: Boolean,
        onOpenFolder: () -> Unit,
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
                    AuroraTopBarIconButton(
                        onClick = { sortExpanded = true },
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = stringResource(MR.strings.action_sort),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    AuroraTopBarIconButton(
                        onClick = { overflowExpanded = true },
                        icon = Icons.Filled.MoreVert,
                        contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                    )
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(AYMR.strings.action_open_download_folder)) },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                            onClick = {
                                onOpenFolder()
                                overflowExpanded = false
                            },
                        )
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
                            title = stringResource(AYMR.strings.action_open_download_folder),
                            onClick = onOpenFolder,
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
        onOpenFolder: () -> Unit,
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
                    AuroraTopBarIconButton(
                        onClick = { sortExpanded = true },
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = stringResource(MR.strings.action_sort),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    AuroraTopBarIconButton(
                        onClick = { overflowExpanded = true },
                        icon = Icons.Filled.MoreVert,
                        contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                    )
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(AYMR.strings.action_open_download_folder)) },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                            onClick = {
                                onOpenFolder()
                                overflowExpanded = false
                            },
                        )
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
                            title = stringResource(AYMR.strings.action_open_download_folder),
                            onClick = onOpenFolder,
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

    @Composable
    private fun NovelActions(
        isAurora: Boolean,
        onOpenFolder: () -> Unit,
    ) {
        if (isAurora) {
            AuroraTopBarIconButton(
                onClick = onOpenFolder,
                icon = Icons.Filled.FolderOpen,
                contentDescription = stringResource(AYMR.strings.action_open_download_folder),
            )
        } else {
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(AYMR.strings.action_open_download_folder),
                        icon = Icons.Filled.FolderOpen,
                        onClick = onOpenFolder,
                    ),
                ),
            )
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
