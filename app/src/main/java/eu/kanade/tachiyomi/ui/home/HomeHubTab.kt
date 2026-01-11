package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
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
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import tachiyomi.i18n.aniyomi.AYMR
import androidx.activity.result.contract.ActivityResultContracts
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.entries.anime.AnimeScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.library.anime.LibraryAnime

import androidx.compose.animation.Crossfade

object HomeHubTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val title = stringResource(AYMR.strings.aurora_home)
            val icon = rememberVectorPainter(Icons.Filled.Home)
            return remember {
                TabOptions(
                    index = 0u,
                    title = title,
                    icon = icon
                )
            }
        }

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { HomeHubScreenModel() }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                screenModel.updateUserAvatar(uri.toString())
            }
        }

        var showNameDialog by remember { mutableStateOf(false) }
        if (showNameDialog) {
            NicknameDialog(
                currentName = state.userName,
                onDismiss = { showNameDialog = false },
                onConfirm = { 
                    screenModel.updateUserName(it)
                    showNameDialog = false
                }
            )
        }

        val tabNavigator = LocalTabNavigator.current

        Crossfade(targetState = state.isLoading, label = "HomeHubLoading") { isLoading ->
            if (isLoading) {
                HomeHubSkeleton()
            } else {
                HomeHubContent(
                    state = state,
                    onAnimeClick = { navigator.push(eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen(it)) },
                    onPlayHero = { screenModel.playHeroEpisode(context) },
                    onAvatarClick = { photoPickerLauncher.launch("image/*") },
                    onNameClick = { showNameDialog = true },
                    onSearchClick = {
                        val lastSourceId = screenModel.getLastUsedAnimeSourceId()
                        if (lastSourceId != -1L) {
                            navigator.push(BrowseAnimeSourceScreen(lastSourceId, null))
                        } else {
                            navigator.push(GlobalAnimeSearchScreen(""))
                        }
                    },
                    onBrowseSourcesClick = { tabNavigator.current = BrowseTab },
                    onAddExtensionClick = {
                        tabNavigator.current = BrowseTab
                        BrowseTab.showAnimeExtension()
                    }
                )
            }
        }
    }
}

@Composable
fun NicknameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.aurora_change_nickname)) },
        text = { 
            OutlinedTextField(
                value = text, 
                onValueChange = { text = it }, 
                singleLine = true
            ) 
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HomeHubContent(
    state: HomeHubScreenModel.State,
    onAnimeClick: (Long) -> Unit,
    onPlayHero: () -> Unit,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBrowseSourcesClick: () -> Unit = {},
    onAddExtensionClick: () -> Unit = {}
) {
    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1e1b4b),
                Color(0xFF101b22)
            )
        )
    }
    
    val tabNavigator = LocalTabNavigator.current
    val isEmpty = state.heroItem == null && state.history.isEmpty() && state.recommendations.isEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
    ) {
        item {
            TopBarSection(
                userName = state.userName,
                userAvatar = state.userAvatar,
                onAvatarClick = onAvatarClick,
                onNameClick = onNameClick,
                onSearchClick = onSearchClick
            )
        }

        if (isEmpty) {
            item {
                WelcomeHeroSection(
                    onBrowseSourcesClick = onBrowseSourcesClick,
                    onAddExtensionClick = onAddExtensionClick
                )
            }
        } else {
            item {
                if (state.heroItem != null) {
                    HeroSection(
                        anime = state.heroItem,
                        onPlayClick = onPlayHero,
                        onAnimeClick = { onAnimeClick(state.heroItem.animeId) }
                    )
                }
            }

            item {
                if (state.history.isNotEmpty()) {
                    ContinueWatchingSection(
                        history = state.history, 
                        onAnimeClick = onAnimeClick,
                        onViewAllClick = { tabNavigator.current = HistoriesTab }
                    )
                }
            }

            item {
                if (state.recommendations.isNotEmpty()) {
                    RecommendedSection(
                        recommendations = state.recommendations, 
                        onAnimeClick = onAnimeClick,
                        onMoreClick = { tabNavigator.current = AnimeLibraryTab }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun TopBarSection(
    userName: String,
    userAvatar: String,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val isDefaultUser = userName == "User"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onAvatarClick)
        ) {
            if (userAvatar.isNotEmpty()) {
                AsyncImage(
                    model = userAvatar,
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Profile",
                    tint = Color(0xFF279df1),
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .background(Color(0xFF279df1), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.clickable(onClick = onNameClick)) {
            Text(
                text = stringResource(AYMR.strings.aurora_welcome_back),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF279df1),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (isDefaultUser) {
                Text(
                    text = stringResource(AYMR.strings.aurora_tap_to_change),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        
        IconButton(
            onClick = onSearchClick,
            modifier = Modifier
                .background(Color(0xFFFFFFFF).copy(alpha = 0.1f), CircleShape)
                .size(40.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White)
        }
    }
}

@Composable
fun WelcomeHeroSection(
    onBrowseSourcesClick: () -> Unit,
    onAddExtensionClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = Color(0xFF279df1),
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(AYMR.strings.aurora_welcome_title),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(AYMR.strings.aurora_welcome_subtitle),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onBrowseSourcesClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF279df1)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(AYMR.strings.aurora_browse_sources), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onAddExtensionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.Extension, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(AYMR.strings.aurora_add_extension), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HeroSection(
    anime: AnimeHistoryWithRelations,
    onPlayClick: () -> Unit,
    onAnimeClick: () -> Unit
) {
    val overlayGradient = remember {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color(0xFF101b22)),
            startY = 0f,
            endY = 1000f
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onAnimeClick)
    ) {
        AsyncImage(
            model = anime.coverData,
            contentDescription = "Hero",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayGradient)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(AYMR.strings.aurora_continue_watching_header),
                color = Color(0xFF279df1),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0xFF279df1).copy(alpha = 0.2f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = anime.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF279df1), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(AYMR.strings.aurora_episode_progress, (anime.episodeNumber % 1000).toInt()),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF279df1)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(AYMR.strings.aurora_play), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ContinueWatchingSection(
    history: List<AnimeHistoryWithRelations>,
    onAnimeClick: (Long) -> Unit,
    onViewAllClick: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(AYMR.strings.aurora_continue_watching), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                stringResource(AYMR.strings.aurora_view_all), 
                color = Color(0xFF279df1), 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onViewAllClick)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)) {
            items(history, key = { it.animeId }) { item ->
                ContinueWatchingCard(item, onAnimeClick)
                Spacer(modifier = Modifier.width(16.dp))
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: AnimeHistoryWithRelations,
    onAnimeClick: (Long) -> Unit
) {
    Column(modifier = Modifier
        .width(280.dp)
        .clickable { onAnimeClick(item.animeId) }
    ) {
        Box(
            modifier = Modifier
                .height(160.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = item.coverData,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                 Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(4.dp)
                        .background(Color(0xFF279df1))
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    item.title, 
                    color = Color.White, 
                    fontWeight = FontWeight.SemiBold, 
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(AYMR.strings.aurora_episode_number, 
                        (item.episodeNumber % 1000).toInt().toString()), 
                    color = Color.White.copy(alpha = 0.5f), 
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun RecommendedSection(
    recommendations: List<LibraryAnime>,
    onAnimeClick: (Long) -> Unit,
    onMoreClick: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 32.dp, start = 24.dp, end = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(AYMR.strings.aurora_recently_added), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                stringResource(AYMR.strings.aurora_more), 
                color = Color(0xFF279df1), 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onMoreClick)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        val chunked = recommendations.chunked(2)
        chunked.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowItems.forEach { item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAnimeClick(item.anime.id) }
                    ) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.DarkGray)
                        ) {
                             AsyncImage(
                                model = item.anime.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(4.dp)
                            ) {
                                 Text("★", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            item.anime.title, 
                            color = Color.White, 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}