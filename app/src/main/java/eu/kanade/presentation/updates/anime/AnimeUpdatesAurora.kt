package eu.kanade.presentation.updates.anime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.updates.aurora.AuroraUpdatesGroupCard
import eu.kanade.presentation.updates.aurora.buildAuroraUpdatesGroups
import eu.kanade.tachiyomi.ui.updates.anime.AnimeUpdatesItem
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

@Composable
fun AnimeUpdatesAuroraContent(
    items: List<AnimeUpdatesItem>,
    onAnimeClicked: (Long) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    val colors = AuroraTheme.colors
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var expandedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()
    val groupedItems = remember(items) {
        buildAuroraUpdatesGroups(
            items = items,
            dateSelector = { it.update.dateFetch.toLocalDate() },
            titleIdSelector = { it.update.animeId },
            titleSelector = { it.update.animeTitle },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        PullRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    onRefresh()
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
            enabled = true,
            indicatorPadding = contentPadding,
        ) {
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                if (items.isEmpty()) {
                    item {
                        EmptyUpdatesState(onRefresh = onRefresh)
                    }
                } else {
                    groupedItems.forEach { section ->
                        item(key = "date_${section.date}") {
                            DateHeader(date = section.date)
                        }

                        items(
                            items = section.groups,
                            key = { group -> "group_${group.key}" },
                        ) { group ->
                            if (group.itemCount <= 1) {
                                val item = group.items.first()
                                AuroraUpdateCard(
                                    item = item,
                                    onClick = onAnimeClicked,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            } else {
                                val isExpanded = group.key in expandedGroups
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    AuroraUpdatesGroupCard(
                                        title = group.title,
                                        countText = stringResource(
                                            AYMR.strings.aurora_new_episodes_count,
                                            group.itemCount,
                                        ),
                                        coverData = group.items.firstOrNull()?.update?.coverData,
                                        expanded = isExpanded,
                                        onClick = {
                                            expandedGroups = if (isExpanded) {
                                                expandedGroups - group.key
                                            } else {
                                                expandedGroups + group.key
                                            }
                                        },
                                    )

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        group.items.forEach { updateItem ->
                                            AuroraUpdateCard(
                                                item = updateItem,
                                                onClick = onAnimeClicked,
                                                modifier = Modifier.padding(bottom = 8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val colors = AuroraTheme.colors
    Text(
        text = relativeDateText(date),
        style = MaterialTheme.typography.titleSmall,
        color = colors.accent,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyUpdatesState(onRefresh: () -> Unit) {
    val colors = AuroraTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = 0.3f),
                            colors.accent.copy(alpha = 0.1f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(colors.accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(AYMR.strings.aurora_no_new_episodes),
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(AYMR.strings.aurora_library_up_to_date),
            color = colors.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRefresh),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.accent.copy(alpha = 0.15f),
            ),
            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(AYMR.strings.aurora_update_library),
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
fun AuroraUpdateCard(
    item: AnimeUpdatesItem,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(item.update.animeId) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: Thumbnail (60x90dp portrait)
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Gray.copy(alpha = 0.3f)),
        ) {
            AsyncImage(
                model = item.update.coverData,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Center: Content (title + episode + badge)
        Column(modifier = Modifier.weight(1f)) {
            // Title
            Text(
                text = item.update.animeTitle,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Episode name
            Text(
                text = item.update.episodeName,
                color = colors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // "NEW" Badge - ONLY if unseen
            if (!item.update.seen) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(colors.accent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(AYMR.strings.aurora_new_badge),
                        color = colors.textOnAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right: Play button
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = colors.textOnAccent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
