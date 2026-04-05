package eu.kanade.tachiyomi.ui.browse.manga.migration.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.formatChapterNumber
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun MigrationListScreenContent(
    items: ImmutableList<MigrationListScreenModel.MigratingManga>,
    finishedCount: Int,
    migrationComplete: Boolean,
    isMigrating: Boolean,
    migrationProgress: Float,
    onItemClick: (Manga) -> Unit,
    onSearchManually: (MigrationListScreenModel.MigratingManga) -> Unit,
    onSkip: (Long) -> Unit,
    onMigrateNow: (Long) -> Unit,
    onCopyNow: (Long) -> Unit,
    onBulkMigrate: () -> Unit,
    onBulkCopy: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            val migrateIcon = if (items.size == 1) Icons.Outlined.Done else Icons.Outlined.DoneAll
            val copyIcon = if (items.size == 1) Icons.Outlined.ContentCopy else Icons.Outlined.CopyAll

            AppBar(
                title = if (items.isNotEmpty()) {
                    "${finishedCount.coerceAtMost(items.size)}/${items.size}"
                } else {
                    stringResource(MR.strings.action_migrate)
                },
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_migrate),
                                icon = migrateIcon,
                                onClick = onBulkMigrate,
                                enabled = migrationComplete && !isMigrating,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.copy),
                                icon = copyIcon,
                                onClick = onBulkCopy,
                                enabled = migrationComplete && !isMigrating,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        Column {
            if (isMigrating) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { migrationProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .padding(top = MaterialTheme.padding.small)
                        .fillMaxWidth(),
                )
            }

            FastScrollLazyColumn(contentPadding = contentPadding + topSmallPaddingValues) {
                items(items, key = { it.manga.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemFastScroll(this)
                            .padding(
                                start = MaterialTheme.padding.medium,
                                end = MaterialTheme.padding.small,
                            )
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MigrationListItem(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.Top)
                                .fillMaxHeight(),
                            manga = item.manga,
                            source = item.source,
                            chapterCount = item.chapterCount,
                            latestChapter = item.latestChapter,
                            onClick = { onItemClick(item.manga) },
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.weight(0.2f),
                        )

                        val result = item.searchResult
                        MigrationListItemResult(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.Top)
                                .fillMaxHeight(),
                            result = result,
                            onItemClick = onItemClick,
                        )

                        MigrationListItemAction(
                            modifier = Modifier.weight(0.2f),
                            result = result,
                            onSearchManually = { onSearchManually(item) },
                            onSkip = { onSkip(item.manga.id) },
                            onMigrateNow = { onMigrateNow(item.manga.id) },
                            onCopyNow = { onCopyNow(item.manga.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MigrationListItem(
    modifier: Modifier,
    manga: Manga,
    source: String,
    chapterCount: Int,
    latestChapter: Double?,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = 150.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            ItemCover.Book(
                modifier = Modifier.fillMaxWidth(),
                data = manga.asMangaCover(),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    )
                    .fillMaxHeight(0.4f)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
            Text(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart),
                text = manga.title,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.labelMedium,
            )
            BadgeGroup(modifier = Modifier.padding(4.dp)) {
                Badge(text = "$chapterCount")
            }
        }

        Column(
            modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = source,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
            )
            val formattedLatestChapter = remember(latestChapter) {
                latestChapter?.let(::formatChapterNumber)
            }
            Text(
                text = "Latest chapter: ${formattedLatestChapter ?: "?"}",
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MigrationListItemResult(
    modifier: Modifier,
    result: MigrationListScreenModel.SearchResult,
    onItemClick: (Manga) -> Unit,
) {
    Box(modifier.height(IntrinsicSize.Min)) {
        when (result) {
            MigrationListScreenModel.SearchResult.Searching -> {
                Box(
                    modifier = Modifier
                        .widthIn(max = 150.dp)
                        .fillMaxSize()
                        .aspectRatio(2f / 3f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            MigrationListScreenModel.SearchResult.NotFound -> {
                Column(
                    Modifier
                        .widthIn(max = 150.dp)
                        .fillMaxSize()
                        .padding(4.dp),
                ) {
                    Image(
                        painter = rememberThemeAwareCoverErrorPainter(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(MaterialTheme.shapes.extraSmall),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        text = stringResource(MR.strings.information_no_entries_found),
                        modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            is MigrationListScreenModel.SearchResult.Success -> {
                MigrationListItem(
                    modifier = Modifier.fillMaxSize(),
                    manga = result.manga,
                    source = result.source,
                    chapterCount = result.chapterCount,
                    latestChapter = result.latestChapter,
                    onClick = { onItemClick(result.manga) },
                )
            }
        }
    }
}

@Composable
private fun MigrationListItemAction(
    modifier: Modifier,
    result: MigrationListScreenModel.SearchResult,
    onSearchManually: () -> Unit,
    onSkip: () -> Unit,
    onMigrateNow: () -> Unit,
    onCopyNow: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val closeMenu = { menuExpanded = false }

    Box(modifier) {
        when (result) {
            MigrationListScreenModel.SearchResult.Searching -> {
                IconButton(onClick = onSkip) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = null)
                }
            }

            MigrationListScreenModel.SearchResult.NotFound,
            is MigrationListScreenModel.SearchResult.Success,
            -> {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = closeMenu,
                    offset = DpOffset(8.dp, (-56).dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("Search manually") },
                        onClick = {
                            closeMenu()
                            onSearchManually()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Skip") },
                        onClick = {
                            closeMenu()
                            onSkip()
                        },
                    )
                    if (result is MigrationListScreenModel.SearchResult.Success) {
                        DropdownMenuItem(
                            text = { Text("Migrate now") },
                            onClick = {
                                closeMenu()
                                onMigrateNow()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy now") },
                            onClick = {
                                closeMenu()
                                onCopyNow()
                            },
                        )
                    }
                }
            }
        }
    }
}
