package eu.kanade.presentation.entries.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aniyomi.domain.anime.SeasonAnime
import coil3.compose.AsyncImage
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeScreenAuroraImpl(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onCoverClicked: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onSeasonClicked: (SeasonAnime) -> Unit,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,
    onDubbingClicked: (() -> Unit)? = null,
    selectedDubbing: String? = null,
    onDownloadLongClick: ((Episode) -> Unit)? = null,
) {
    val anime = state.anime
    val episodes = state.episodeListItems
    val colors = AuroraTheme.colors
    
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var descriptionOverflows by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        AsyncImage(
            model = anime.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(100.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.cardGradient)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = navigateUp,
                    modifier = Modifier
                        .size(48.dp)
                        .background(colors.glass, CircleShape)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = colors.textPrimary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(
                        onClick = onAddToLibraryClicked,
                        modifier = Modifier
                            .size(48.dp)
                            .background(colors.glass, CircleShape)
                    ) {
                        Icon(
                            if (anime.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (anime.favorite) Color.Red else colors.textPrimary
                        )
                    }
                    if (onWebViewClicked != null) {
                        IconButton(
                            onClick = onWebViewClicked,
                            modifier = Modifier
                                .size(48.dp)
                                .background(colors.glass, CircleShape)
                        ) {
                            Icon(
                                Icons.Outlined.Public,
                                contentDescription = stringResource(MR.strings.action_open_in_web_view),
                                tint = colors.textPrimary
                            )
                        }
                    }
                    if (onShareClicked != null) {
                        IconButton(
                            onClick = onShareClicked,
                            modifier = Modifier
                                .size(48.dp)
                                .background(colors.glass, CircleShape)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, tint = colors.textPrimary)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(onClick = onCoverClicked)
                    ) {
                        AsyncImage(
                            model = anime.thumbnailUrl,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                    )
                                )
                        )
                    }
                }

                // Info Card
                item {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.glass)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = anime.title,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textPrimary,
                            lineHeight = 36.sp
                        )
                        
                        // Genre
                        if (anime.genre.isNullOrEmpty().not()) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                anime.genre!!.take(3).forEachIndexed { index, genre ->
                                    Text(
                                        text = genre,
                                        color = colors.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (index < minOf(anime.genre!!.size, 3) - 1) {
                                        Text("•", color = colors.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Stats Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                                .background(Color.Transparent, shape = RoundedCornerShape(0.dp)),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             // Status/Rating Placeholder
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("4.9", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                }
                                Text(stringResource(AYMR.strings.aurora_rating), color = colors.textSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
                             }
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(anime.status.toString(), color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                Text(stringResource(AYMR.strings.aurora_status), color = colors.textSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
                             }
                        }

                        // Description
                        Text(
                            text = anime.description ?: stringResource(AYMR.strings.aurora_no_description),
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { descriptionExpanded = !descriptionExpanded },
                            onTextLayout = { result ->
                                descriptionOverflows = result.hasVisualOverflow || descriptionExpanded
                            },
                        )
                        
                        if (descriptionOverflows || descriptionExpanded) {
                            Icon(
                                imageVector = if (descriptionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable { descriptionExpanded = !descriptionExpanded }
                                    .padding(top = 4.dp),
                            )
                        }

                        // Dubbing Button
                        if (onDubbingClicked != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.glass)
                                    .clickable(onClick = onDubbingClicked),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Translate, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(selectedDubbing ?: stringResource(AYMR.strings.aurora_select_dubbing), color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        // Continue Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colors.accent)
                                .clickable(onClick = onContinueWatching),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.textOnAccent, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(AYMR.strings.aurora_continue), color = colors.textOnAccent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                // Episodes Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(AYMR.strings.aurora_episodes_header), color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(AYMR.strings.aurora_episode_count, episodes.size), color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                }

                // Episode List
                items(episodes) { item ->
                    if (item is EpisodeList.Item) {
                        val episode = item.episode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    brush = if (colors.isDark) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.2f),
                                                Color.Black.copy(alpha = 0.4f),
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(
                                                colors.glass.copy(alpha = 0.5f),
                                                colors.glass.copy(alpha = 0.5f),
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { onEpisodeClicked(episode, false) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail placeholder
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .aspectRatio(16f/9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.3f))
                            ) {
                                // Use anime thumbnail as placeholder if episode thumb is missing (which is common)
                                AsyncImage(
                                    model = anime.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                if (episode.seen) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.textPrimary)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = episode.name,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(AYMR.strings.aurora_episode_progress, (episode.episodeNumber % 1000).toInt()),
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )
                                if (episode.seen) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(colors.divider)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(colors.accent)
                                        )
                                    }
                                }
                            }

                            // Download Button
                            if (onDownloadEpisode != null) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            onClick = { onDownloadEpisode(listOf(item), EpisodeDownloadAction.START) },
                                            onLongClick = { onDownloadLongClick?.invoke(item.episode) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, tint = colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}