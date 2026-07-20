package eu.kanade.tachiyomi.ui.browse.novel.migration.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
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
fun NovelMigrationListScreenContent(
    items: ImmutableList<NovelMigrationListScreenModel.MigratingNovel>,
    finishedCount: Int,
    migrationComplete: Boolean,
    isMigrating: Boolean,
    migrationProgress: Float,
    onItemClick: (Novel) -> Unit,
    onSearchManually: (NovelMigrationListScreenModel.MigratingNovel) -> Unit,
    onCancelSearch: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onMigrateNow: (Long) -> Unit,
    onCopyNow: (Long) -> Unit,
    onOpenBulkMigrateDialog: () -> Unit,
    onOpenBulkCopyDialog: () -> Unit,
    onOpenOptions: () -> Unit,
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
                                title = stringResource(MR.strings.action_settings),
                                icon = Icons.Outlined.Settings,
                                onClick = onOpenOptions,
                                enabled = !isMigrating,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.action_migrate),
                                icon = migrateIcon,
                                onClick = onOpenBulkMigrateDialog,
                                enabled = migrationComplete && !isMigrating,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.copy),
                                icon = copyIcon,
                                onClick = onOpenBulkCopyDialog,
                                enabled = migrationComplete && !isMigrating,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        FastScrollLazyColumn(contentPadding = contentPadding + topSmallPaddingValues) {
            item(key = "migration-progress") {
                MigrationProgressHeader(
                    totalCount = items.size,
                    finishedCount = finishedCount,
                    migrationComplete = migrationComplete,
                    isMigrating = isMigrating,
                    migrationProgress = migrationProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .padding(bottom = MaterialTheme.padding.small),
                )
            }

            items(items, key = { it.novel.id }) { item ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemFastScroll(this)
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .padding(bottom = MaterialTheme.padding.medium),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = MaterialTheme.padding.small,
                                end = MaterialTheme.padding.extraSmall,
                                top = MaterialTheme.padding.small,
                                bottom = MaterialTheme.padding.small,
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
                            novel = item.novel,
                            source = item.source,
                            chapterCount = item.chapterCount,
                            latestChapter = item.latestChapter,
                            onClick = { onItemClick(item.novel) },
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(0.18f),
                        )

                        val result = item.searchResult
                        MigrationListItemResult(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.Top)
                                .fillMaxHeight(),
                            result = result,
                            searchLabel = item.searchLabel,
                            onItemClick = onItemClick,
                        )

                        MigrationListItemAction(
                            modifier = Modifier.weight(0.18f),
                            result = result,
                            onSearchManually = { onSearchManually(item) },
                            onCancelSearch = { onCancelSearch(item.novel.id) },
                            onSkip = { onSkip(item.novel.id) },
                            onMigrateNow = { onMigrateNow(item.novel.id) },
                            onCopyNow = { onCopyNow(item.novel.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MigrationProgressHeader(
    totalCount: Int,
    finishedCount: Int,
    migrationComplete: Boolean,
    isMigrating: Boolean,
    migrationProgress: Float,
    modifier: Modifier = Modifier,
) {
    val searchProgress = if (totalCount == 0) {
        0f
    } else {
        finishedCount.toFloat() / totalCount.toFloat()
    }.coerceIn(0f, 1f)
    val progress = if (isMigrating) migrationProgress.coerceIn(0f, 1f) else searchProgress
    val statusText = when {
        isMigrating -> stringResource(MR.strings.action_migrate)
        migrationComplete -> stringResource(MR.strings.download_engine_done)
        else -> stringResource(MR.strings.action_search)
    }

    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(MR.strings.action_migrate),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "$statusText · ${finishedCount.coerceAtMost(totalCount)}/$totalCount",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.padding(MaterialTheme.padding.extraSmall))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MigrationListItem(
    modifier: Modifier,
    novel: Novel,
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
                data = novel.asNovelCover(),
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
                text = novel.title,
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
                text = stringResource(
                    MR.strings.migration_latest_chapter,
                    formattedLatestChapter ?: stringResource(MR.strings.migration_unknown_latest_chapter),
                ),
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
    result: NovelMigrationListScreenModel.SearchResult,
    searchLabel: String?,
    onItemClick: (Novel) -> Unit,
) {
    Box(modifier.height(IntrinsicSize.Min)) {
        when (result) {
            NovelMigrationListScreenModel.SearchResult.Searching -> {
                Column(
                    modifier = Modifier
                        .widthIn(max = 150.dp)
                        .fillMaxSize()
                        .aspectRatio(2f / 3f)
                        .padding(MaterialTheme.padding.small),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    searchLabel?.let {
                        Text(
                            text = stringResource(MR.strings.migration_checking_source, it),
                            modifier = Modifier.padding(top = MaterialTheme.padding.small),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            NovelMigrationListScreenModel.SearchResult.NotFound -> {
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
                        text = stringResource(MR.strings.migration_no_match_found),
                        modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            is NovelMigrationListScreenModel.SearchResult.Success -> {
                MigrationListItem(
                    modifier = Modifier.fillMaxSize(),
                    novel = result.novel,
                    source = result.source,
                    chapterCount = result.chapterCount,
                    latestChapter = result.latestChapter,
                    onClick = { onItemClick(result.novel) },
                )
            }
        }
    }
}

@Composable
private fun MigrationListItemAction(
    modifier: Modifier,
    result: NovelMigrationListScreenModel.SearchResult,
    onSearchManually: () -> Unit,
    onCancelSearch: () -> Unit,
    onSkip: () -> Unit,
    onMigrateNow: () -> Unit,
    onCopyNow: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val closeMenu = { menuExpanded = false }

    Box(modifier) {
        when (result) {
            NovelMigrationListScreenModel.SearchResult.Searching -> {
                IconButton(onClick = onCancelSearch) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = null)
                }
            }

            NovelMigrationListScreenModel.SearchResult.NotFound,
            is NovelMigrationListScreenModel.SearchResult.Success,
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
                        text = { Text(stringResource(MR.strings.migration_search_manually)) },
                        onClick = {
                            closeMenu()
                            onSearchManually()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.migration_skip_entry)) },
                        onClick = {
                            closeMenu()
                            onSkip()
                        },
                    )
                    if (result is NovelMigrationListScreenModel.SearchResult.Success) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.migration_migrate_now)) },
                            onClick = {
                                closeMenu()
                                onMigrateNow()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.migration_copy_now)) },
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
