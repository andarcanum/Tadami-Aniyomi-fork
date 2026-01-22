package eu.kanade.presentation.browse

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.anime.AnimeSourceUiModel
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BrowseScreenAurora(
    animeSources: ImmutableList<AnimeSourceUiModel>,
    onAnimeSourceClick: (AnimeSource) -> Unit,
    onAnimeSourceLongClick: (AnimeSource) -> Unit,
    onGlobalSearchClick: () -> Unit,
    onExtensionsClick: () -> Unit,
    onMigrateClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(20.dp))
            }

            item(span = { GridItemSpan(2) }) {
                BrowseAuroraHeader(
                    onSearchClick = onGlobalSearchClick,
                )
            }

            item(span = { GridItemSpan(2) }) {
                QuickActionsSection(
                    onGlobalSearchClick = onGlobalSearchClick,
                    onExtensionsClick = onExtensionsClick,
                    onMigrateClick = onMigrateClick,
                )
            }

            val pinnedSources = animeSources.filterIsInstance<AnimeSourceUiModel.Item>()
                .filter { Pin.Actual in it.source.pin }

            if (pinnedSources.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    SourcesSectionHeader(title = stringResource(AYMR.strings.aurora_pinned_sources))
                }
                item(span = { GridItemSpan(2) }) {
                    PinnedSourcesRow(
                        sources = pinnedSources.map { it.source },
                        onSourceClick = onAnimeSourceClick,
                        onSourceLongClick = onAnimeSourceLongClick,
                    )
                }
            }

            val pinnedSourceIds = pinnedSources.map { it.source.id }.toSet()

            animeSources.forEach { item ->
                when (item) {
                    is AnimeSourceUiModel.Header -> {
                        item(
                            span = { GridItemSpan(2) },
                            key = "header_${item.language}",
                        ) {
                            SourcesSectionHeader(
                                title = getLanguageDisplayNameComposable(item.language),
                                showDivider = true,
                            )
                        }
                    }
                    is AnimeSourceUiModel.Item -> {
                        if (item.source.id !in pinnedSourceIds) {
                            item(key = "source_${item.source.id}") {
                                SourceGridItem(
                                    source = item.source,
                                    onClick = { onAnimeSourceClick(item.source) },
                                    onPinClick = { onAnimeSourceLongClick(item.source) },
                                )
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(2) }) { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BrowseAuroraHeader(
    onSearchClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(AYMR.strings.aurora_browse),
                fontSize = 22.sp,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(AYMR.strings.aurora_discover_sources),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        IconButton(
            onClick = onSearchClick,
            modifier = Modifier
                .background(colors.glass, CircleShape)
                .size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(AYMR.strings.aurora_global_search),
                tint = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun QuickActionsSection(
    onGlobalSearchClick: () -> Unit,
    onExtensionsClick: () -> Unit,
    onMigrateClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickActionCard(
            icon = Icons.Outlined.Explore,
            title = stringResource(AYMR.strings.aurora_global_search),
            modifier = Modifier.weight(1f),
            onClick = onGlobalSearchClick,
        )
        QuickActionCard(
            icon = Icons.Filled.Extension,
            title = stringResource(AYMR.strings.aurora_extensions),
            modifier = Modifier.weight(1f),
            onClick = onExtensionsClick,
        )
        QuickActionCard(
            icon = Icons.Filled.SwapHoriz,
            title = stringResource(AYMR.strings.aurora_migrate),
            modifier = Modifier.weight(1f),
            onClick = onMigrateClick,
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.glass,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SourcesSectionHeader(title: String, showDivider: Boolean = false) {
    val colors = AuroraTheme.colors

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        if (showDivider) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = title,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(3.dp)
                .background(colors.accent, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun PinnedSourcesRow(
    sources: List<AnimeSource>,
    onSourceClick: (AnimeSource) -> Unit,
    onSourceLongClick: (AnimeSource) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sources, key = { it.id }) { source ->
            PinnedSourceCard(
                source = source,
                onClick = { onSourceClick(source) },
                onLongClick = { onSourceLongClick(source) },
            )
        }
    }
}

@Composable
private fun PinnedSourceCard(
    source: AnimeSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Card(
        modifier = Modifier
            .width(140.dp)
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.accent.copy(alpha = 0.15f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = source.name,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.lang.uppercase(),
                color = colors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SourceGridItem(
    source: AnimeSource,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val isPinned = Pin.Actual in source.pin
    val successColor = Color(0xFF22c55e)

    // Use padding for grid spacing simulation if needed, but LazyVerticalGrid handles it
    // We add padding here to ensure content isn't touching the edges if used outside grid,
    // but inside grid we rely on arrangement.
    // However, to match the layout logic, we'll keep the card container.

    // We need to apply padding to the items on the edges of the grid.
    // The grid has 12.dp spacing. The outer padding is handled by contentPadding.
    // But we need to make sure the items look good.

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp) // Fixed height for grid uniformity
            .padding(horizontal = 8.dp) // Slight horizontal padding to prevent touching
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.glass,
        ),
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = 0.3f),
                                colors.gradientStart.copy(alpha = 0.5f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = source.name.take(2).uppercase(),
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }

            // Text content
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = source.name,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.lang.uppercase(),
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun getLanguageDisplayNameComposable(code: String): String {
    return when (code) {
        "last_used" -> stringResource(AYMR.strings.aurora_source_last_used)
        "pinned" -> stringResource(AYMR.strings.aurora_source_pinned)
        "all" -> stringResource(AYMR.strings.aurora_source_all)
        "other" -> stringResource(AYMR.strings.aurora_source_other)
        "en" -> "English"
        "ja" -> "日本語"
        "zh" -> "中文"
        "ko" -> "한국어"
        "ru" -> "Русский"
        "es" -> "Español"
        "fr" -> "Français"
        "de" -> "Deutsch"
        "pt" -> "Português"
        "it" -> "Italiano"
        "ar" -> "العربية"
        "tr" -> "Türkçe"
        "pl" -> "Polski"
        "vi" -> "Tiếng Việt"
        "th" -> "ไทย"
        "id" -> "Indonesia"
        "hi" -> "हिन्दी"
        else -> code.uppercase()
    }
}
