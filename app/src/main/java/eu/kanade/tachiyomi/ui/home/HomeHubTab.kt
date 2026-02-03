package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AuroraCard
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.LocalTabState
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.more.settings.screen.browse.AnimeExtensionReposScreen
import eu.kanade.presentation.more.settings.screen.browse.MangaExtensionReposScreen
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.domain.entries.EntryCover
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.injectLazy

private enum class HomeHubSection {
    Anime,
    Manga,
}

private data class HomeHubUiState(
    val hero: HomeHubHero? = null,
    val history: List<HomeHubHistory> = emptyList(),
    val recommendations: List<HomeHubRecommendation> = emptyList(),
    val userName: String,
    val userAvatar: String,
    val greeting: dev.icerock.moko.resources.StringResource,
    val showWelcome: Boolean,
)

private data class HomeHubHero(
    val entryId: Long,
    val title: String,
    val progressNumber: Double,
    val coverData: EntryCover,
)

private data class HomeHubHistory(
    val entryId: Long,
    val title: String,
    val progressNumber: Double,
    val coverData: EntryCover,
)

private data class HomeHubRecommendation(
    val entryId: Long,
    val title: String,
    val coverData: EntryCover?,
    val subtitle: String? = null,
)

object HomeHubTab : Tab {

    private val uiPreferences: UiPreferences by injectLazy()

    override val options: TabOptions
        @Composable
        get() {
            val title = stringResource(AYMR.strings.aurora_home)
            val isSelected = LocalTabNavigator.current.current is HomeHubTab
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_home_enter)
            return TabOptions(
                index = 0u,
                title = title,
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() {
        val showAnimeSection by uiPreferences.showAnimeSection().collectAsState()
        val showMangaSection by uiPreferences.showMangaSection().collectAsState()

        val sections = remember(showAnimeSection, showMangaSection) {
            buildList {
                if (showAnimeSection) add(HomeHubSection.Anime)
                if (showMangaSection) add(HomeHubSection.Manga)
            }.ifEmpty { listOf(HomeHubSection.Anime) }
        }

        var selectedSection by rememberSaveable { mutableStateOf(sections.first()) }
        LaunchedEffect(sections) {
            if (selectedSection !in sections) {
                selectedSection = sections.first()
            }
        }

        var animeSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
        var mangaSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }

        // Get screen models to access user data
        val animeScreenModel = HomeHubTab.rememberScreenModel { HomeHubScreenModel() }
        val mangaScreenModel = HomeHubTab.rememberScreenModel { MangaHomeHubScreenModel() }
        val animeState by animeScreenModel.state.collectAsState()
        val mangaState by mangaScreenModel.state.collectAsState()

        // Use current section's state for user info
        val currentUserName = when (selectedSection) {
            HomeHubSection.Anime -> animeState.userName
            HomeHubSection.Manga -> mangaState.userName
        }
        val currentUserAvatar = when (selectedSection) {
            HomeHubSection.Anime -> animeState.userAvatar
            HomeHubSection.Manga -> mangaState.userAvatar
        }

        // Photo picker for avatar
        val photoPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            uri?.let {
                when (selectedSection) {
                    HomeHubSection.Anime -> animeScreenModel.updateUserAvatar(it.toString())
                    HomeHubSection.Manga -> mangaScreenModel.updateUserAvatar(it.toString())
                }
            }
        }

        // Name dialog
        var showNameDialog by remember { mutableStateOf(false) }
        if (showNameDialog) {
            val currentName = when (selectedSection) {
                HomeHubSection.Anime -> animeState.userName
                HomeHubSection.Manga -> mangaState.userName
            }
            NameDialog(
                currentName = currentName,
                onDismiss = { showNameDialog = false },
                onConfirm = { newName ->
                    when (selectedSection) {
                        HomeHubSection.Anime -> animeScreenModel.updateUserName(newName)
                        HomeHubSection.Manga -> mangaScreenModel.updateUserName(newName)
                    }
                    showNameDialog = false
                },
            )
        }

        val tabs = sections.map { section ->
            when (section) {
                HomeHubSection.Anime -> TabContent(
                    titleRes = AYMR.strings.label_anime,
                    searchEnabled = true,
                    content = { contentPadding, _ ->
                        AnimeHomeHub(
                            contentPadding = contentPadding,
                            searchQuery = animeSearchQuery,
                        )
                    },
                )
                HomeHubSection.Manga -> TabContent(
                    titleRes = AYMR.strings.label_manga,
                    searchEnabled = true,
                    content = { contentPadding, _ ->
                        MangaHomeHub(
                            contentPadding = contentPadding,
                            searchQuery = mangaSearchQuery,
                        )
                    },
                )
            }
        }.toPersistentList()

        val initialIndex = sections.indexOf(selectedSection).coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = initialIndex) { tabs.size }

        LaunchedEffect(sections, pagerState) {
            val targetIndex = sections.indexOf(selectedSection).coerceAtLeast(0)
            if (targetIndex in tabs.indices && pagerState.currentPage != targetIndex) {
                pagerState.scrollToPage(targetIndex)
            }
        }

        LaunchedEffect(pagerState.currentPage, sections) {
            sections.getOrNull(pagerState.currentPage)?.let { selectedSection = it }
        }

        TabbedScreenAurora(
            titleRes = null,
            tabs = tabs,
            state = pagerState,
            mangaSearchQuery = mangaSearchQuery,
            onChangeMangaSearchQuery = { mangaSearchQuery = it },
            animeSearchQuery = animeSearchQuery,
            onChangeAnimeSearchQuery = { animeSearchQuery = it },
            isMangaTab = { index -> sections.getOrNull(index) == HomeHubSection.Manga },
            showCompactHeader = true,
            userName = currentUserName,
            userAvatar = currentUserAvatar,
            onAvatarClick = { photoPickerLauncher.launch("image/*") },
            onNameClick = { showNameDialog = true },
            showTabs = false,
            applyStatusBarsPadding = false,
        )
    }
}

private fun HomeHubScreenModel.State.toUiState(): HomeHubUiState {
    return HomeHubUiState(
        hero = hero?.let {
            HomeHubHero(
                entryId = it.animeId,
                title = it.title,
                progressNumber = it.episodeNumber,
                coverData = it.coverData,
            )
        },
        history = history.map {
            HomeHubHistory(
                entryId = it.animeId,
                title = it.title,
                progressNumber = it.episodeNumber,
                coverData = it.coverData,
            )
        },
        recommendations = recommendations.map {
            HomeHubRecommendation(
                entryId = it.animeId,
                title = it.title,
                coverData = it.coverData,
                subtitle = "${it.seenCount}/${it.totalCount} эп.",
            )
        },
        userName = userName,
        userAvatar = userAvatar,
        greeting = greeting,
        showWelcome = showWelcome,
    )
}

private fun MangaHomeHubScreenModel.State.toUiState(): HomeHubUiState {
    return HomeHubUiState(
        hero = hero?.let {
            HomeHubHero(
                entryId = it.mangaId,
                title = it.title,
                progressNumber = it.chapterNumber,
                coverData = it.coverData,
            )
        },
        history = history.map {
            HomeHubHistory(
                entryId = it.mangaId,
                title = it.title,
                progressNumber = it.chapterNumber,
                coverData = it.coverData,
            )
        },
        recommendations = recommendations.map {
            val readCount = it.totalCount - it.unreadCount
            HomeHubRecommendation(
                entryId = it.mangaId,
                title = it.title,
                coverData = it.coverData,
                subtitle = "$readCount/${it.totalCount} гл.",
            )
        },
        userName = userName,
        userAvatar = userAvatar,
        greeting = greeting,
        showWelcome = showWelcome,
    )
}

@Composable
private fun AnimeHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
) {
    val screenModel = HomeHubTab.rememberScreenModel { HomeHubScreenModel() }
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val tabNavigator = LocalTabNavigator.current

    LaunchedEffect(screenModel) {
        HomeHubScreenModel.setInstance(screenModel)
        screenModel.startLiveUpdates()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { screenModel.updateUserAvatar(it.toString()) } }

    var showNameDialog by remember { mutableStateOf(false) }
    if (showNameDialog) {
        NameDialog(
            currentName = state.userName,
            onDismiss = { showNameDialog = false },
            onConfirm = {
                screenModel.updateUserName(it)
                showNameDialog = false
            },
        )
    }

    val lastSourceName = remember { screenModel.getLastUsedAnimeSourceName() }

    HomeHubScreen(
        state = state.toUiState(),
        searchQuery = searchQuery,
        lastSourceName = lastSourceName,
        contentPadding = contentPadding,
        heroActionLabelRes = AYMR.strings.aurora_play,
        heroProgressLabelRes = AYMR.strings.aurora_episode_progress,
        onEntryClick = { navigator.push(AnimeScreen(it)) },
        onPlayHero = { screenModel.playHeroEpisode(context) },
        onAvatarClick = { photoPickerLauncher.launch("image/*") },
        onNameClick = { showNameDialog = true },
        onSourceClick = {
            val sourceId = screenModel.getLastUsedAnimeSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseAnimeSourceScreen(sourceId, null))
            } else {
                tabNavigator.current = BrowseTab
            }
        },
        onBrowseClick = { navigator.push(AnimeExtensionReposScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showAnimeExtension()
        },
        onHistoryClick = { tabNavigator.current = HistoriesTab },
        onLibraryClick = { tabNavigator.current = AnimeLibraryTab },
    )
}

@Composable
private fun MangaHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
) {
    val screenModel = HomeHubTab.rememberScreenModel { MangaHomeHubScreenModel() }
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val tabNavigator = LocalTabNavigator.current

    LaunchedEffect(screenModel) {
        MangaHomeHubScreenModel.setInstance(screenModel)
        screenModel.startLiveUpdates()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { screenModel.updateUserAvatar(it.toString()) } }

    var showNameDialog by remember { mutableStateOf(false) }
    if (showNameDialog) {
        NameDialog(
            currentName = state.userName,
            onDismiss = { showNameDialog = false },
            onConfirm = {
                screenModel.updateUserName(it)
                showNameDialog = false
            },
        )
    }

    val lastSourceName = remember { screenModel.getLastUsedMangaSourceName() }

    HomeHubScreen(
        state = state.toUiState(),
        searchQuery = searchQuery,
        lastSourceName = lastSourceName,
        contentPadding = contentPadding,
        heroActionLabelRes = AYMR.strings.aurora_read,
        heroProgressLabelRes = AYMR.strings.aurora_chapter_progress,
        onEntryClick = { navigator.push(MangaScreen(it)) },
        onPlayHero = { screenModel.readHeroChapter(context) },
        onAvatarClick = { photoPickerLauncher.launch("image/*") },
        onNameClick = { showNameDialog = true },
        onSourceClick = {
            val sourceId = screenModel.getLastUsedMangaSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseMangaSourceScreen(sourceId, null))
            } else {
                tabNavigator.current = BrowseTab
            }
        },
        onBrowseClick = { navigator.push(MangaExtensionReposScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showExtension()
        },
        onHistoryClick = { tabNavigator.current = HistoriesTab },
        onLibraryClick = { tabNavigator.current = MangaLibraryTab },
    )
}

@Composable
private fun HomeHubScreen(
    state: HomeHubUiState,
    searchQuery: String?,
    lastSourceName: String?,
    contentPadding: PaddingValues,
    heroActionLabelRes: dev.icerock.moko.resources.StringResource,
    heroProgressLabelRes: dev.icerock.moko.resources.StringResource,
    onEntryClick: (Long) -> Unit,
    onPlayHero: () -> Unit,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit,
    onSourceClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onExtensionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLibraryClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val trimmedQuery = searchQuery?.trim().orEmpty()
    val isFiltering = trimmedQuery.isNotEmpty()
    val matchesQuery: (String) -> Boolean = { title ->
        !isFiltering || title.contains(trimmedQuery, ignoreCase = true)
    }

    val listState = rememberLazyListState()

    val hero = state.hero?.takeIf { matchesQuery(it.title) }
    val history = state.history.filter { matchesQuery(it.title) }
    val recommendations = state.recommendations.filter { matchesQuery(it.title) }
    val showWelcome = state.showWelcome && !isFiltering

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        // Status bar spacer
        item(key = "status_bar_spacer") {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        }

        // Inline Header with avatar, username, and tabs
        item(key = "inline_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(20.dp))

                // Row with Username (Left) + Avatar (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Greeting + Name
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNameClick)
                            .padding(end = 16.dp),
                    ) {
                        Text(
                            text = stringResource(state.greeting),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = state.userName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Avatar
                    Box(Modifier.size(48.dp).clickable(onClick = onAvatarClick)) {
                        if (state.userAvatar.isNotEmpty()) {
                            AsyncImage(
                                model = state.userAvatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Icon(
                                Icons.Filled.AccountCircle,
                                null,
                                tint = colors.accent,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (state.userAvatar.isEmpty()) {
                            Box(
                                modifier = Modifier.align(
                                    Alignment.BottomEnd,
                                ).size(16.dp).background(colors.accent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.CameraAlt,
                                    null,
                                    tint = colors.textOnAccent,
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Tab Switcher (only if more than 1 tab)
                val tabState = LocalTabState.current
                if (tabState != null && tabState.tabs.size > 1) {
                    AuroraTabRow(
                        tabs = tabState.tabs,
                        selectedIndex = tabState.selectedIndex,
                        onTabSelected = tabState.onTabSelected,
                        scrollable = false,
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }

        if (showWelcome) {
            item(key = "welcome") {
                WelcomeSection(onBrowseClick = onBrowseClick, onExtensionClick = onExtensionClick)
            }
        } else {
            hero?.let { heroData ->
                item(key = "hero") {
                    HeroSection(
                        hero = heroData,
                        actionLabelRes = heroActionLabelRes,
                        progressLabelRes = heroProgressLabelRes,
                        onPlayClick = onPlayHero,
                        onEntryClick = { onEntryClick(heroData.entryId) },
                    )
                }
            }

            item(key = "quick_source") {
                QuickSourceButton(sourceName = lastSourceName, onClick = onSourceClick)
            }

            if (history.isNotEmpty()) {
                item(key = "history") {
                    HistoryRow(
                        history = history,
                        onEntryClick = onEntryClick,
                        onViewAllClick = onHistoryClick,
                    )
                }
            }

            if (recommendations.isNotEmpty()) {
                item(key = "recommendations") {
                    RecommendationsGrid(
                        recommendations = recommendations,
                        onEntryClick = onEntryClick,
                        onMoreClick = onLibraryClick,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun WelcomeSection(onBrowseClick: () -> Unit, onExtensionClick: () -> Unit) {
    val colors = AuroraTheme.colors

    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp))
            .background(colors.cardBackground).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.VideoLibrary, null, tint = colors.accent, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(AYMR.strings.aurora_welcome_title),
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(AYMR.strings.aurora_welcome_subtitle),
                color = colors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onBrowseClick,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Search, null, tint = colors.textOnAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(AYMR.strings.aurora_browse_sources),
                    color = colors.textOnAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onExtensionClick,
                colors = ButtonDefaults.buttonColors(containerColor = colors.glass),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Extension, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(AYMR.strings.aurora_add_extension),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun HeroSection(
    hero: HomeHubHero,
    actionLabelRes: dev.icerock.moko.resources.StringResource,
    progressLabelRes: dev.icerock.moko.resources.StringResource,
    onPlayClick: () -> Unit,
    onEntryClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val overlayGradient = remember(colors) {
        Brush.verticalGradient(
            listOf(Color.Transparent, colors.gradientEnd.copy(alpha = 0.8f)),
            startY = 0f,
            endY = 1000f,
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(
            440.dp,
        ).padding(16.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onEntryClick),
    ) {
        AsyncImage(
            model = hero.coverData,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(overlayGradient))

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Badge hidden as per design update
            Text(
                hero.title,
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontFamily = FontFamily(Font(eu.kanade.tachiyomi.R.font.montserrat_bold)),
                lineHeight = 34.sp,
                style = TextStyle(lineBreak = LineBreak.Heading),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(progressLabelRes, (hero.progressNumber % 1000).toInt()),
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(start = 22.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                modifier = Modifier.height(52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = colors.textOnAccent, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(actionLabelRes),
                    color = colors.textOnAccent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun QuickSourceButton(sourceName: String?, onClick: () -> Unit) {
    val colors = AuroraTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = colors.glass),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = sourceName ?: stringResource(AYMR.strings.aurora_open_source),
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HistoryRow(
    history: List<HomeHubHistory>,
    onEntryClick: (Long) -> Unit,
    onViewAllClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Column(modifier = Modifier.padding(top = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(AYMR.strings.aurora_recently_watched),
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                stringResource(AYMR.strings.aurora_more),
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onViewAllClick),
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(history, key = { it.entryId }) { item ->
                AuroraCard(
                    modifier = Modifier.width(128.dp).aspectRatio(0.68f),
                    title = item.title,
                    coverData = item.coverData,
                    subtitle = stringResource(
                        AYMR.strings.aurora_episode_number,
                        (item.progressNumber % 1000).toInt().toString(),
                    ),
                    onClick = { onEntryClick(item.entryId) },
                    imagePadding = 6.dp,
                )
            }
        }
    }
}

@Composable
private fun RecommendationsGrid(
    recommendations: List<HomeHubRecommendation>,
    onEntryClick: (Long) -> Unit,
    onMoreClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Column(modifier = Modifier.padding(top = 32.dp, start = 24.dp, end = 24.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(AYMR.strings.aurora_recently_added),
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                stringResource(AYMR.strings.aurora_more),
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onMoreClick),
            )
        }
        Spacer(Modifier.height(16.dp))

        // Horizontal scrollable row instead of grid
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(recommendations, key = { it.entryId }) { item ->
                AuroraCard(
                    modifier = Modifier.width(128.dp).aspectRatio(0.68f),
                    title = item.title,
                    coverData = item.coverData,
                    subtitle = item.subtitle,
                    onClick = { onEntryClick(item.entryId) },
                    imagePadding = 6.dp,
                )
            }
        }
    }
}

@Composable
private fun NameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.aurora_change_nickname)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
