package eu.kanade.presentation.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.anime.AnimeSourceUiModel
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_HORIZONTAL_INSET
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_SHAPE
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalIsDefaultAppUiFont
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.resolveAuroraAdaptiveSpec
import eu.kanade.presentation.util.isTabletUi
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
    AuroraBackground {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val auroraAdaptiveSpec = resolveAuroraAdaptiveSpec(
                isTabletUi = isTabletUi(),
                containerWidthDp = maxWidth.value.toInt(),
            )
            val columnCount = when (auroraAdaptiveSpec.deviceClass) {
                AuroraDeviceClass.Phone -> 2
                AuroraDeviceClass.TabletCompact -> 3
                AuroraDeviceClass.TabletExpanded -> 4
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                modifier = Modifier
                    .fillMaxSize()
                    .auroraCenteredMaxWidth(auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp),
                contentPadding = PaddingValues(bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(columnCount) }) {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                item(span = { GridItemSpan(columnCount) }) {
                    BrowseAuroraHeader(
                        onSearchClick = onGlobalSearchClick,
                    )
                }

                item(span = { GridItemSpan(columnCount) }) {
                    QuickActionsSection(
                        onGlobalSearchClick = onGlobalSearchClick,
                        onExtensionsClick = onExtensionsClick,
                        onMigrateClick = onMigrateClick,
                    )
                }

                val pinnedSources = animeSources.filterIsInstance<AnimeSourceUiModel.Item>()
                    .filter { Pin.Actual in it.source.pin }

                if (pinnedSources.isNotEmpty()) {
                    item(span = { GridItemSpan(columnCount) }) {
                        SourcesSectionHeader(title = stringResource(AYMR.strings.aurora_pinned_sources))
                    }
                    item(span = { GridItemSpan(columnCount) }) {
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
                                span = { GridItemSpan(columnCount) },
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

                item(span = { GridItemSpan(columnCount) }) { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

internal data class BrowseQuickActionsRenderSpec(
    val horizontalInset: Dp,
    val verticalInset: Dp,
    val itemGap: Dp,
    val primaryBreakGap: Dp,
    val minCardHeight: Dp,
    val leadingIconContainerSize: Dp,
)

internal fun resolveBrowseQuickActionsRenderSpec(): BrowseQuickActionsRenderSpec {
    return BrowseQuickActionsRenderSpec(
        horizontalInset = AURORA_SETTINGS_CARD_HORIZONTAL_INSET,
        verticalInset = 16.dp,
        itemGap = 12.dp,
        primaryBreakGap = 4.dp,
        minCardHeight = 72.dp,
        leadingIconContainerSize = 48.dp,
    )
}

@Composable
private fun BrowseAuroraHeader(
    onSearchClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val tabContainerColor = if (colors.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.05f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }

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
                .background(tabContainerColor, CircleShape)
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
    val spec = resolveBrowseQuickActionsRenderSpec()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spec.horizontalInset, vertical = spec.verticalInset),
        verticalArrangement = Arrangement.spacedBy(spec.itemGap),
    ) {
        QuickActionCard(
            icon = Icons.Outlined.Explore,
            iconTint = AuroraTheme.colors.accent,
            title = stringResource(AYMR.strings.aurora_global_search),
            minHeight = spec.minCardHeight,
            leadingIconContainerSize = spec.leadingIconContainerSize,
            onClick = onGlobalSearchClick,
        )
        Spacer(modifier = Modifier.height(spec.primaryBreakGap))
        QuickActionCard(
            icon = Icons.Filled.Extension,
            title = stringResource(AYMR.strings.aurora_extensions),
            minHeight = spec.minCardHeight,
            leadingIconContainerSize = spec.leadingIconContainerSize,
            onClick = onExtensionsClick,
        )
        QuickActionCard(
            icon = Icons.Filled.SwapHoriz,
            title = stringResource(AYMR.strings.aurora_migrate),
            minHeight = spec.minCardHeight,
            leadingIconContainerSize = spec.leadingIconContainerSize,
            onClick = onMigrateClick,
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    iconTint: Color? = null,
    minHeight: Dp,
    leadingIconContainerSize: Dp,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val useMediumWeight = LocalIsDefaultAppUiFont.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clickable(onClick = onClick),
        shape = AURORA_SETTINGS_CARD_SHAPE,
        colors = CardDefaults.cardColors(
            containerColor = resolveAuroraMoreCardContainerColor(colors.glass, colors.isDark),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(leadingIconContainerSize)
                    .background(
                        colors.textPrimary.copy(alpha = 0.10f),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: colors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (useMediumWeight) FontWeight.Medium else FontWeight.SemiBold,
                ),
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
    val tabContainerColor = if (colors.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.05f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }

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
            containerColor = tabContainerColor,
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
