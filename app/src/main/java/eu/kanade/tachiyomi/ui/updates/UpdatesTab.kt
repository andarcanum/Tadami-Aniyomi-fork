package eu.kanade.tachiyomi.ui.updates

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.tadami.aurora.R
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.presentation.more.settings.screen.LibraryUpdatePacingScreen
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.updates.anime.AnimeUpdatesAuroraContent
import eu.kanade.presentation.updates.manga.MangaUpdatesAuroraContent
import eu.kanade.presentation.updates.novel.NovelUpdatesAuroraContent
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.ui.download.DownloadsTab
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.ui.updates.anime.AnimeUpdatesScreenModel
import eu.kanade.tachiyomi.ui.updates.anime.animeUpdatesTab
import eu.kanade.tachiyomi.ui.updates.manga.MangaUpdatesScreenModel
import eu.kanade.tachiyomi.ui.updates.manga.mangaUpdatesTab
import eu.kanade.tachiyomi.ui.updates.novel.NovelUpdatesScreenModel
import eu.kanade.tachiyomi.ui.updates.novel.novelUpdatesTab
import eu.kanade.tachiyomi.util.system.isRunningFlow
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object UpdatesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            val index: UShort = when (currentNavigationStyle()) {
                NavStyle.MOVE_UPDATES_TO_MORE -> 5u
                NavStyle.MOVE_HISTORY_TO_MORE -> 2u
                NavStyle.MOVE_BROWSE_TO_MORE -> 2u
                NavStyle.MOVE_MANGA_TO_MORE -> 1u
            }
            return TabOptions(
                index = index,
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(DownloadsTab)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().collectAsState()
        val showAnimeSection by uiPreferences.showAnimeSection().collectAsState()
        val showMangaSection by uiPreferences.showMangaSection().collectAsState()
        val showNovelSection by uiPreferences.showNovelSection().collectAsState()
        val fromMore = currentNavigationStyle() == NavStyle.MOVE_UPDATES_TO_MORE
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        LaunchedEffect(Unit) {
            clearUpdatesBadgeCounts(libraryPreferences)
        }

        if (theme.isAuroraStyle) {
            val navigator = LocalNavigator.currentOrThrow
            val internalErrorMessage = stringResource(MR.strings.internal_error)
            val updatingAnimeMessage = stringResource(AYMR.strings.aurora_updating_anime)
            val updatingMangaMessage = stringResource(AYMR.strings.aurora_updating_manga)
            val updatingNovelMessage = stringResource(MR.strings.updating_library)
            val updatingAllLibraryMessage = stringResource(AYMR.strings.aurora_updating_library)
            val updateAlreadyRunningMessage = stringResource(MR.strings.update_already_running)
            val scope = rememberCoroutineScope()
            val playerPreferences = remember { Injekt.get<PlayerPreferences>() }

            val isAnimeUpdating by remember(context) {
                context.workManager.isRunningFlow("AnimeLibraryUpdate")
            }.collectAsState(initial = false)
            val isMangaUpdating by remember(context) {
                context.workManager.isRunningFlow("LibraryUpdate")
            }.collectAsState(initial = false)
            val isNovelUpdating by remember(context) {
                context.workManager.isRunningFlow("NovelLibraryUpdate")
            }.collectAsState(initial = false)

            val animeScreenModel = rememberScreenModel { AnimeUpdatesScreenModel() }
            val animeState by animeScreenModel.state.collectAsState()
            val mangaScreenModel = rememberScreenModel { MangaUpdatesScreenModel() }
            val mangaState by mangaScreenModel.state.collectAsState()
            val novelScreenModel = rememberScreenModel { NovelUpdatesScreenModel() }
            val novelState by novelScreenModel.state.collectAsState()

            var selectedTab by rememberSaveable { mutableIntStateOf(TAB_ANIME) }
            var refreshingTabId by rememberSaveable { mutableStateOf<Int?>(null) }

            LaunchedEffect(isAnimeUpdating, isMangaUpdating, isNovelUpdating) {
                if (refreshingTabId == TAB_ANIME && !isAnimeUpdating) refreshingTabId = null
                if (refreshingTabId == TAB_MANGA && !isMangaUpdating) refreshingTabId = null
                if (refreshingTabId == TAB_NOVEL && !isNovelUpdating) refreshingTabId = null
            }

            fun showUpdateToast(started: Boolean, startedMessage: String) {
                val message = resolveUpdateToastMessage(
                    started = started,
                    startedMessage = startedMessage,
                    alreadyRunningMessage = updateAlreadyRunningMessage,
                )
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }

            val tabIds = remember(showAnimeSection, showMangaSection, showNovelSection) {
                buildList {
                    if (showAnimeSection) {
                        add(TAB_ANIME)
                    }
                    if (showMangaSection) {
                        add(TAB_MANGA)
                    }
                    if (showNovelSection) {
                        add(TAB_NOVEL)
                    }
                }
            }

            val tabs = persistentListOf<TabContent>().builder().apply {
                if (showAnimeSection) {
                    add(
                        TabContent(
                            titleRes = AYMR.strings.label_anime,
                            content = { contentPadding, _ ->
                                AnimeUpdatesAuroraContent(
                                    items = animeState.items,
                                    onAnimeClicked = {
                                        navigator.push(eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen(it))
                                    },
                                    onEpisodeClicked = { animeId, episodeId ->
                                        scope.launchIO {
                                            MainActivity.startPlayerActivity(
                                                context = context,
                                                animeId = animeId,
                                                episodeId = episodeId,
                                                extPlayer = playerPreferences.alwaysUseExternalPlayer().get(),
                                            )
                                        }
                                    },
                                    onRefresh = animeScreenModel::updateLibrary,
                                    contentPadding = PaddingValues(
                                        bottom = contentPadding.calculateBottomPadding(),
                                    ),
                                )
                            },
                        ),
                    )
                }
                if (showMangaSection) {
                    add(
                        TabContent(
                            titleRes = AYMR.strings.label_manga,
                            content = { contentPadding, _ ->
                                MangaUpdatesAuroraContent(
                                    items = mangaState.items,
                                    onMangaClicked = {
                                        navigator.push(eu.kanade.tachiyomi.ui.entries.manga.MangaScreen(it))
                                    },
                                    onChapterClicked = { mangaId, chapterId ->
                                        val intent = ReaderActivity.newIntent(context, mangaId, chapterId)
                                        context.startActivity(intent)
                                    },
                                    onRefresh = mangaScreenModel::updateLibrary,
                                    contentPadding = PaddingValues(
                                        bottom = contentPadding.calculateBottomPadding(),
                                    ),
                                )
                            },
                        ),
                    )
                }
                if (showNovelSection) {
                    add(
                        TabContent(
                            titleRes = AYMR.strings.label_novel,
                            content = { contentPadding, _ ->
                                NovelUpdatesAuroraContent(
                                    items = novelState.items,
                                    onNovelClicked = { navigator.push(NovelScreen(it)) },
                                    onChapterClicked = { navigator.push(NovelReaderScreen(it)) },
                                    onRefresh = {
                                        val started = NovelLibraryUpdateJob.startNow(context)
                                        showUpdateToast(
                                            started = started,
                                            startedMessage = updatingNovelMessage,
                                        )
                                    },
                                    contentPadding = PaddingValues(
                                        bottom = contentPadding.calculateBottomPadding(),
                                    ),
                                )
                            },
                        ),
                    )
                }
            }.build()

            val initialPage = tabIds.indexOf(selectedTab).coerceAtLeast(0)
            val state = rememberPagerState(initialPage) { tabs.size }

            fun refreshCurrentTab() {
                refreshingTabId = selectedTab
                when (selectedTab) {
                    TAB_ANIME -> animeScreenModel.updateLibrary()
                    TAB_MANGA -> mangaScreenModel.updateLibrary()
                    TAB_NOVEL -> {
                        val started = NovelLibraryUpdateJob.startNow(context)
                        showUpdateToast(
                            started = started,
                            startedMessage = currentTabUpdatingMessage(
                                tabId = selectedTab,
                                animeMessage = updatingAnimeMessage,
                                mangaMessage = updatingMangaMessage,
                                novelMessage = updatingNovelMessage,
                            ),
                        )
                    }
                }
            }

            LaunchedEffect(state.currentPage, tabIds) {
                selectedTab = tabIds.getOrElse(state.currentPage) { TAB_ANIME }
            }

            LaunchedEffect(tabIds, selectedTab) {
                val targetIndex = tabIds.indexOf(selectedTab).takeIf { it >= 0 } ?: 0
                if (state.currentPage != targetIndex) {
                    state.scrollToPage(targetIndex)
                }
            }

            fun refreshAllTabs() {
                refreshingTabId = null
                val animeStarted = if (showAnimeSection) AnimeLibraryUpdateJob.startNow(context) else false
                val mangaStarted = if (showMangaSection) MangaLibraryUpdateJob.startNow(context) else false
                val novelStarted = if (showNovelSection) NovelLibraryUpdateJob.startNow(context) else false
                showUpdateToast(
                    started = animeStarted || mangaStarted || novelStarted,
                    startedMessage = updatingAllLibraryMessage,
                )
            }

            LaunchedEffect(animeScreenModel) {
                animeScreenModel.events.collect { event ->
                    when (event) {
                        AnimeUpdatesScreenModel.Event.InternalError -> {
                            Toast.makeText(context, internalErrorMessage, Toast.LENGTH_SHORT).show()
                        }
                        is AnimeUpdatesScreenModel.Event.LibraryUpdateTriggered -> {
                            showUpdateToast(
                                started = event.started,
                                startedMessage = updatingAnimeMessage,
                            )
                        }
                    }
                }
            }

            LaunchedEffect(mangaScreenModel) {
                mangaScreenModel.events.collect { event ->
                    when (event) {
                        MangaUpdatesScreenModel.Event.InternalError -> {
                            Toast.makeText(context, internalErrorMessage, Toast.LENGTH_SHORT).show()
                        }
                        is MangaUpdatesScreenModel.Event.LibraryUpdateTriggered -> {
                            showUpdateToast(
                                started = event.started,
                                startedMessage = updatingMangaMessage,
                            )
                        }
                    }
                }
            }

            TabbedScreenAurora(
                titleRes = null,
                tabs = tabs,
                state = state,
                isMangaTab = { tabIds.getOrNull(it) == TAB_MANGA },
                showTabs = false,
                extraHeaderContent = {
                    val currentPage = state.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
                    val currentTabId = tabIds.getOrElse(currentPage) { TAB_ANIME }
                    val isCurrentTabUpdating = when (currentTabId) {
                        TAB_ANIME -> isAnimeUpdating
                        TAB_MANGA -> isMangaUpdating
                        TAB_NOVEL -> isNovelUpdating
                        else -> false
                    }
                    val isAnyUpdating = isAnimeUpdating || isMangaUpdating || isNovelUpdating
                    val isSyncSpinning = isAnyUpdating && refreshingTabId != currentTabId
                    val isRefreshSpinning = isCurrentTabUpdating && refreshingTabId == currentTabId

                    AuroraUpdatesPinnedHeader(
                        tabs = tabs,
                        selectedIndex = currentPage,
                        subtitle = if (shouldShowAuroraUpdatesSubtitle(currentTabId)) {
                            stringResource(AYMR.strings.aurora_new_episodes_subtitle)
                        } else {
                            null
                        },
                        isRefreshSpinning = isRefreshSpinning,
                        isSyncSpinning = isSyncSpinning,
                        onTabSelected = { page ->
                            if (page in tabs.indices && state.currentPage != page) {
                                scope.launch {
                                    switchAuroraUpdatesPage(
                                        state = state,
                                        page = page,
                                    )
                                }
                            }
                        },
                        onRefreshCurrent = ::refreshCurrentTab,
                        onRefreshAll = ::refreshAllTabs,
                        onOpenPacingSettings = {
                            navigator.push(LibraryUpdatePacingScreen)
                        },
                    )
                },
            )
        } else {
            TabbedScreen(
                titleRes = MR.strings.label_recent_updates,
                tabs = listOfNotNull(
                    animeUpdatesTab(context, fromMore).takeIf { showAnimeSection },
                    mangaUpdatesTab(context, fromMore).takeIf { showMangaSection },
                    novelUpdatesTab(context, fromMore).takeIf { showNovelSection },
                ).toPersistentList(),
            )
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

private const val TAB_ANIME = 0
private const val TAB_MANGA = 1
private const val TAB_NOVEL = 2

internal fun shouldShowAuroraUpdatesSubtitle(tabId: Int): Boolean = false

internal fun clearUpdatesBadgeCounts(libraryPreferences: LibraryPreferences) {
    libraryPreferences.newAnimeUpdatesCount().set(0)
    libraryPreferences.newMangaUpdatesCount().set(0)
    libraryPreferences.newNovelUpdatesCount().set(0)
}

internal fun currentTabUpdatingMessage(
    tabId: Int,
    animeMessage: String,
    mangaMessage: String,
    novelMessage: String,
): String = when (tabId) {
    TAB_MANGA -> mangaMessage
    TAB_NOVEL -> novelMessage
    else -> animeMessage
}

internal fun resolveUpdateToastMessage(
    started: Boolean,
    startedMessage: String,
    alreadyRunningMessage: String,
): String = if (started) startedMessage else alreadyRunningMessage

internal suspend fun switchAuroraUpdatesPage(
    state: PagerState,
    page: Int,
) {
    if (state.currentPage == page) return
    state.animateScrollToPage(page)
}

@Composable
private fun AuroraUpdatesPinnedHeader(
    tabs: kotlinx.collections.immutable.ImmutableList<TabContent>,
    selectedIndex: Int,
    subtitle: String?,
    isRefreshSpinning: Boolean,
    isSyncSpinning: Boolean,
    onTabSelected: (Int) -> Unit,
    onRefreshCurrent: () -> Unit,
    onRefreshAll: () -> Unit,
    onOpenPacingSettings: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val selected = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    var pacingMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "update_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
        ),
        label = "icon_rotation",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(AYMR.strings.aurora_updates),
                    fontSize = 22.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val tabContainerColor = if (colors.background.luminance() < 0.5f) {
                    Color.White.copy(alpha = 0.05f)
                } else {
                    Color.Black.copy(alpha = 0.03f)
                }
                IconButton(
                    onClick = onRefreshCurrent,
                    modifier = Modifier
                        .background(tabContainerColor, CircleShape)
                        .size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(AYMR.strings.aurora_refresh_current_tab),
                        tint = colors.textPrimary,
                        modifier = Modifier.rotate(if (isRefreshSpinning) rotation else 0f),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onRefreshAll,
                    modifier = Modifier
                        .background(colors.accent, CircleShape)
                        .size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = stringResource(AYMR.strings.aurora_refresh_all_tabs),
                        tint = colors.textOnAccent,
                        modifier = Modifier.rotate(if (isSyncSpinning) rotation else 0f),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    IconButton(
                        onClick = {
                            pacingMenuExpanded = true
                        },
                        modifier = Modifier
                            .background(tabContainerColor, CircleShape)
                            .size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                            tint = colors.textPrimary,
                        )
                    }
                    AuroraEntryDropdownMenu(
                        expanded = pacingMenuExpanded,
                        onDismissRequest = { pacingMenuExpanded = false },
                    ) {
                        AuroraEntryDropdownMenuItem(
                            text = stringResource(MR.strings.action_timeout_settings),
                            leadingIcon = Icons.Filled.Tune,
                            onClick = {
                                pacingMenuExpanded = false
                                onOpenPacingSettings()
                            },
                        )
                    }
                }
            }
        }

        if (tabs.size > 1) {
            Spacer(modifier = Modifier.size(12.dp))
            AuroraTabRow(
                tabs = tabs,
                selectedIndex = selected,
                onTabSelected = onTabSelected,
                scrollable = false,
            )
        }
    }
}
