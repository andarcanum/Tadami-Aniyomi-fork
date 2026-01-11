package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

object HomeHubTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val title = stringResource(AYMR.strings.aurora_home)
            val icon = rememberVectorPainter(Icons.Filled.Home)
            return remember { TabOptions(index = 0u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { HomeHubScreenModel() }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val tabNavigator = LocalTabNavigator.current

        LaunchedEffect(screenModel) {
            HomeHubScreenModel.setInstance(screenModel)
            screenModel.startLiveUpdates()
        }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { screenModel.updateUserAvatar(it.toString()) } }

        var showNameDialog by remember { mutableStateOf(false) }
        if (showNameDialog) {
            NameDialog(
                currentName = state.userName,
                onDismiss = { showNameDialog = false },
                onConfirm = { screenModel.updateUserName(it); showNameDialog = false }
            )
        }

        val lastSourceName = remember { screenModel.getLastUsedAnimeSourceName() }

        HomeHubScreen(
            state = state,
            lastSourceName = lastSourceName,
            onAnimeClick = { navigator.push(AnimeScreen(it)) },
            onPlayHero = { screenModel.playHeroEpisode(context) },
            onAvatarClick = { photoPickerLauncher.launch("image/*") },
            onNameClick = { showNameDialog = true },
            onSearchClick = {
                val sourceId = screenModel.getLastUsedAnimeSourceId()
                if (sourceId != -1L) navigator.push(BrowseAnimeSourceScreen(sourceId, null))
                else navigator.push(GlobalAnimeSearchScreen(""))
            },
            onSourceClick = {
                val sourceId = screenModel.getLastUsedAnimeSourceId()
                if (sourceId != -1L) navigator.push(BrowseAnimeSourceScreen(sourceId, null))
                else tabNavigator.current = BrowseTab
            },
            onBrowseClick = { tabNavigator.current = BrowseTab },
            onExtensionClick = { tabNavigator.current = BrowseTab; BrowseTab.showAnimeExtension() },
            onHistoryClick = { tabNavigator.current = HistoriesTab },
            onLibraryClick = { tabNavigator.current = AnimeLibraryTab }
        )
    }
}

@Composable
private fun HomeHubScreen(
    state: HomeHubScreenModel.State,
    lastSourceName: String?,
    onAnimeClick: (Long) -> Unit,
    onPlayHero: () -> Unit,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSourceClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onExtensionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLibraryClick: () -> Unit
) {
    val colors = AuroraTheme.colors
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
            .statusBarsPadding()
    ) {
        item(key = "topbar") {
            TopBar(
                userName = state.userName,
                userAvatar = state.userAvatar,
                onAvatarClick = onAvatarClick,
                onNameClick = onNameClick,
                onSearchClick = onSearchClick
            )
        }

        if (state.showWelcome) {
            item(key = "welcome") {
                WelcomeSection(onBrowseClick = onBrowseClick, onExtensionClick = onExtensionClick)
            }
        } else {
            state.hero?.let { hero ->
                item(key = "hero") {
                    HeroSection(
                        hero = hero,
                        onPlayClick = onPlayHero,
                        onAnimeClick = { onAnimeClick(hero.animeId) }
                    )
                }
            }

            item(key = "quick_source") {
                QuickSourceButton(sourceName = lastSourceName, onClick = onSourceClick)
            }

            if (state.history.isNotEmpty()) {
                item(key = "history") {
                    HistoryRow(
                        history = state.history,
                        onAnimeClick = onAnimeClick,
                        onViewAllClick = onHistoryClick
                    )
                }
            }

            if (state.recommendations.isNotEmpty()) {
                item(key = "recommendations") {
                    RecommendationsGrid(
                        recommendations = state.recommendations,
                        onAnimeClick = onAnimeClick,
                        onMoreClick = onLibraryClick
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TopBar(
    userName: String,
    userAvatar: String,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val colors = AuroraTheme.colors
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp).height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clickable(onClick = onAvatarClick)) {
            if (userAvatar.isNotEmpty()) {
                AsyncImage(
                    model = userAvatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(Icons.Filled.AccountCircle, null, tint = colors.accent, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).size(14.dp).background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CameraAlt, null, tint = colors.textOnAccent, modifier = Modifier.size(8.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        Row(
            modifier = Modifier.clickable(onClick = onNameClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(AYMR.strings.aurora_welcome_back), style = MaterialTheme.typography.labelSmall, color = colors.accent, fontWeight = FontWeight.Bold)
                Text(userName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                if (userName == "User") {
                    Text(stringResource(AYMR.strings.aurora_tap_to_change), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = onSearchClick,
            modifier = Modifier.background(colors.glass, CircleShape).size(40.dp)
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.textPrimary)
        }
    }
}

@Composable
private fun WelcomeSection(onBrowseClick: () -> Unit, onExtensionClick: () -> Unit) {
    val colors = AuroraTheme.colors
    
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp))
            .background(colors.cardBackground).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.VideoLibrary, null, tint = colors.accent, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(AYMR.strings.aurora_welcome_title), color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(AYMR.strings.aurora_welcome_subtitle), color = colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(32.dp))

            Button(onClick = onBrowseClick, colors = ButtonDefaults.buttonColors(containerColor = colors.accent), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Search, null, tint = colors.textOnAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(AYMR.strings.aurora_browse_sources), color = colors.textOnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            Button(onClick = onExtensionClick, colors = ButtonDefaults.buttonColors(containerColor = colors.glass), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Extension, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(AYMR.strings.aurora_add_extension), color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroSection(
    hero: HomeHubScreenModel.HeroData,
    onPlayClick: () -> Unit,
    onAnimeClick: () -> Unit
) {
    val colors = AuroraTheme.colors
    val overlayGradient = remember(colors) { 
        Brush.verticalGradient(listOf(Color.Transparent, colors.gradientEnd), startY = 0f, endY = 1000f) 
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(500.dp).padding(16.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onAnimeClick)
    ) {
        AsyncImage(model = hero.coverData, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(overlayGradient))

        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(AYMR.strings.aurora_continue_watching_header),
                color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(colors.accent.copy(alpha = 0.2f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(hero.title, color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(AYMR.strings.aurora_episode_progress, (hero.episodeNumber % 1000).toInt()), color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(20.dp))

            Button(onClick = onPlayClick, colors = ButtonDefaults.buttonColors(containerColor = colors.accent), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), modifier = Modifier.height(52.dp)) {
                Icon(Icons.Filled.PlayArrow, null, tint = colors.textOnAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(AYMR.strings.aurora_play), color = colors.textOnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = colors.glass),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = sourceName ?: stringResource(AYMR.strings.aurora_open_source),
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistoryRow(
    history: List<HomeHubScreenModel.HistoryData>,
    onAnimeClick: (Long) -> Unit,
    onViewAllClick: () -> Unit
) {
    val colors = AuroraTheme.colors
    
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(AYMR.strings.aurora_recently_watched), color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(AYMR.strings.aurora_more), color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onViewAllClick))
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(history, key = { it.animeId }) { item ->
                HistoryCard(item, onAnimeClick)
            }
        }
    }
}

@Composable
private fun HistoryCard(item: HomeHubScreenModel.HistoryData, onAnimeClick: (Long) -> Unit) {
    val colors = AuroraTheme.colors
    
    Column(Modifier.width(280.dp).clickable { onAnimeClick(item.animeId) }) {
        Box(Modifier.height(160.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)) {
            AsyncImage(model = item.coverData, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter).background(colors.divider)) {
                Box(Modifier.fillMaxWidth(0.5f).height(4.dp).background(colors.accent))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(AYMR.strings.aurora_episode_number, (item.episodeNumber % 1000).toInt().toString()), color = colors.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun RecommendationsGrid(
    recommendations: List<HomeHubScreenModel.RecommendationData>,
    onAnimeClick: (Long) -> Unit,
    onMoreClick: () -> Unit
) {
    val colors = AuroraTheme.colors
    
    Column(modifier = Modifier.padding(top = 32.dp, start = 24.dp, end = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(AYMR.strings.aurora_recently_added), color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(AYMR.strings.aurora_more), color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onMoreClick))
        }
        Spacer(Modifier.height(16.dp))

        recommendations.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowItems.forEach { item ->
                    Column(Modifier.weight(1f).clickable { onAnimeClick(item.animeId) }) {
                        Box(Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)).background(colors.surface)) {
                            AsyncImage(model = item.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(item.title, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
