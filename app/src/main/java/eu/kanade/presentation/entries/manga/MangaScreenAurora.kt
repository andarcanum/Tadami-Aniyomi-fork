package eu.kanade.presentation.entries.manga

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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
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
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadIndicator
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.temporal.ChronoUnit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaScreenAuroraImpl(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onCoverClicked: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onSettingsClicked: (() -> Unit)?,
) {
    val manga = state.manga
    val chapters = state.chapterListItems
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    
    val nextUpdateDays = remember(nextUpdate) {
        if (nextUpdate != null) {
            val now = Instant.now()
            now.until(nextUpdate, ChronoUnit.DAYS).toInt().coerceAtLeast(0)
        } else {
            null
        }
    }
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var descriptionOverflows by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        AsyncImage(
            model = remember(manga.id, manga.thumbnailUrl, manga.coverLastModified) {
                ImageRequest.Builder(context)
                    .data(manga.asMangaCover())
                    .placeholderMemoryCacheKey(manga.thumbnailUrl)
                    .build()
            },
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
                        .size(44.dp)
                        .background(colors.accent.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = colors.accent)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var downloadExpanded by remember { mutableStateOf(false) }
                    var showMenu by remember { mutableStateOf(false) }

                    // Filter Button
                    IconButton(
                        onClick = onFilterButtonClicked,
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.accent.copy(alpha = 0.2f), CircleShape)
                    ) {
                        val filterTint = if (state.filterActive) colors.accent else colors.accent.copy(alpha = 0.7f)
                        Icon(Icons.Default.FilterList, contentDescription = null, tint = filterTint)
                    }

                    // Download Button + Menu
                    if (onDownloadActionClicked != null) {
                        Box {
                            IconButton(
                                onClick = { downloadExpanded = !downloadExpanded },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(colors.accent.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, tint = colors.accent)
                            }
                            eu.kanade.presentation.components.DropdownMenu(
                                expanded = downloadExpanded,
                                onDismissRequest = { downloadExpanded = false }
                            ) {
                                EntryDownloadDropdownMenu(
                                    expanded = true,
                                    onDismissRequest = { downloadExpanded = false },
                                    onDownloadClicked = { onDownloadActionClicked.invoke(it) },
                                    isManga = true
                                )
                            }
                        }
                    }

                    // Overflow Menu
                    Box {
                        IconButton(
                            onClick = { showMenu = !showMenu },
                            modifier = Modifier
                                .size(44.dp)
                                .background(colors.accent.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = colors.accent)
                        }
                        eu.kanade.presentation.components.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(text = stringResource(MR.strings.action_webview_refresh)) },
                                onClick = {
                                    onRefresh()
                                    showMenu = false
                                },
                            )
                            if (onShareClicked != null) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(text = stringResource(MR.strings.action_share)) },
                                    onClick = {
                                        onShareClicked()
                                        showMenu = false
                                    },
                                )
                            }
                            if (onSettingsClicked != null) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(text = stringResource(AYMR.strings.settings)) },
                                    onClick = {
                                        onSettingsClicked()
                                        showMenu = false
                                    },
                                )
                            }
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
                            model = remember(manga.id, manga.thumbnailUrl, manga.coverLastModified) {
                                ImageRequest.Builder(context)
                                    .data(manga.asMangaCover())
                                    .placeholderMemoryCacheKey(manga.thumbnailUrl)
                                    .build()
                            },
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

                // Action Block
                item {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.glass)
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Favorite
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAddToLibraryClicked() }
                                .padding(horizontal = 4.dp)
                        ) {
                            Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    if (manga.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (manga.favorite) stringResource(MR.strings.in_library) else stringResource(MR.strings.add_to_library),
                                color = colors.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                lineHeight = 12.sp
                            )
                        }
                        
                        // Webview
                        if (onWebViewClicked != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onWebViewClicked() }
                                    .padding(horizontal = 4.dp)
                            ) {
                                Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Public, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Webview",
                                    color = colors.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 2,
                                    lineHeight = 12.sp
                                )
                            }
                        }

                        // Next Update
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onEditFetchIntervalClicked?.invoke() }
                                .padding(horizontal = 4.dp)
                        ) {
                            Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.HourglassEmpty,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (nextUpdateDays) {
                                    null -> stringResource(MR.strings.not_applicable)
                                    0 -> stringResource(MR.strings.manga_interval_expected_update_soon)
                                    else -> pluralStringResource(
                                        MR.plurals.day,
                                        count = nextUpdateDays,
                                        nextUpdateDays,
                                    )
                                },
                                color = colors.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                lineHeight = 12.sp
                            )
                        }

                        // Tracking
                        if (onTrackingClicked != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onTrackingClicked() }
                                    .padding(horizontal = 4.dp)
                            ) {
                                val trackingCount = state.trackingCount
                                Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (trackingCount == 0) Icons.Outlined.Sync else Icons.Outlined.Done,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (trackingCount == 0) stringResource(MR.strings.manga_tracking_tab) else pluralStringResource(MR.plurals.num_trackers, count = trackingCount, trackingCount),
                                    color = colors.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 2,
                                    lineHeight = 12.sp
                                )
                            }
                        }
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
                            text = manga.title,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textPrimary,
                            lineHeight = 36.sp
                        )
                        
                        // Genre
                        if (manga.genre.isNullOrEmpty().not()) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                manga.genre!!.take(3).forEachIndexed { index, genre ->
                                    Text(
                                        text = genre,
                                        color = colors.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (index < minOf(manga.genre!!.size, 3) - 1) {
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
                                Text(manga.status.toString(), color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                Text(stringResource(AYMR.strings.aurora_status), color = colors.textSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
                             }
                        }

                        // Description
                        Text(
                            text = manga.description ?: stringResource(AYMR.strings.aurora_no_description),
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

                        // Continue Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colors.accent)
                                .clickable(onClick = onContinueReading),
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

                // Chapters Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(AYMR.strings.aurora_chapters_header), color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(AYMR.strings.aurora_chapter_count, chapters.size), color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                }

                // Chapter List
                items(chapters) { item ->
                    if (item is ChapterList.Item) {
                        val chapter = item.chapter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.glass)
                                .clickable { onChapterClicked(chapter) }
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
                                // Use manga thumbnail as placeholder
                                AsyncImage(
                                    model = remember(manga.id, manga.thumbnailUrl, manga.coverLastModified) {
                                        ImageRequest.Builder(context)
                                            .data(manga.asMangaCover())
                                            .placeholderMemoryCacheKey(manga.thumbnailUrl)
                                            .build()
                                    },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                if (chapter.read) {
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
                                    text = chapter.name,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(AYMR.strings.aurora_chapter_progress, (chapter.chapterNumber % 1000).toInt()),
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )
                                if (chapter.read) {
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
                            if (onDownloadChapter != null) {
                                ChapterDownloadIndicator(
                                    enabled = true,
                                    downloadStateProvider = { item.downloadState },
                                    downloadProgressProvider = { item.downloadProgress },
                                    onClick = { onDownloadChapter(listOf(item), it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
