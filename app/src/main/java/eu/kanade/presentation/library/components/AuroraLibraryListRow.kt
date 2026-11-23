package eu.kanade.presentation.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.browse.components.chromeAccentDetails
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.library.AuroraLibraryListRowMetrics
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import java.util.Locale

private const val SERIES_META_SEPARATOR = "·"

@Immutable
internal data class AuroraLibraryListBadgeData(
    val downloadLabel: String? = null,
    val unreadCount: Long? = null,
    val isLocal: Boolean = false,
    val language: String? = null,
) {
    val hasAny: Boolean
        get() = !downloadLabel.isNullOrBlank() ||
            (unreadCount != null && unreadCount > 0L) ||
            isLocal ||
            !language.isNullOrBlank()
}

/**
 * Compact / Comfortable horizontal list row for Aurora library (Anime, Manga, Novel),
 * aligned with Browse*SourceListRow visual grammar while preserving library badges,
 * series cues, pinned state, multi-select, continue action, and optional progress bar.
 */
@Composable
internal fun AuroraLibraryListRow(
    title: String,
    coverData: Any?,
    metrics: AuroraLibraryListRowMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seriesHeaderText: String? = null,
    subtitle: String? = null,
    progressPercent: Int? = null,
    badgeData: AuroraLibraryListBadgeData = AuroraLibraryListBadgeData(),
    isPinned: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val appHaptics = LocalAppHaptics.current
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val coverReloadTick = rememberCoverReloadTick()
    val coverRequest = remember(context, coverData, coverReloadTick) {
        buildAuroraCoverImageRequest(context, coverData)
    }

    val isSeries = !seriesHeaderText.isNullOrBlank()
    val coverShape = RoundedCornerShape(metrics.coverCornerRadiusDp.dp)

    val rowBackground = when {
        !isSelected -> Color.Transparent
        colors.isEInk -> colors.surface
        else -> colors.accent.copy(alpha = if (colors.isDark) 0.16f else 0.11f)
    }

    val rowBorderModifier = if (isSelected && colors.isEInk) {
        Modifier.border(
            border = BorderStroke(2.dp, colors.textPrimary),
            shape = RoundedCornerShape(10.dp),
        )
    } else {
        Modifier
    }

    val selectionIndicatorModifier = if (isSelected && !colors.isEInk) {
        val indicatorColor = colors.accent
        Modifier.drawBehind {
            val barWidth = 3.dp.toPx()
            val insetY = 6.dp.toPx()
            val barHeight = (size.height - insetY * 2f).coerceAtLeast(0f)
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - barWidth else 0f
            drawRoundRect(
                color = indicatorColor,
                topLeft = Offset(left, insetY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth, barWidth),
            )
        }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .then(rowBorderModifier)
            .then(selectionIndicatorModifier)
            .combinedClickable(
                onClick = {
                    appHaptics.tap()
                    onClick()
                },
                onLongClick = onLongClick,
            )
            .padding(
                horizontal = metrics.horizontalPaddingDp.dp,
                vertical = metrics.verticalPaddingDp.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.contentSpacingDp.dp),
    ) {
        AuroraLibraryListCoverThumbnail(
            coverRequest = coverRequest,
            placeholderPainter = placeholderPainter,
            widthDp = metrics.coverWidthDp,
            heightDp = metrics.coverHeightDp,
            coverShape = coverShape,
            isSeries = isSeries,
            isPinned = isPinned,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                maxLines = metrics.titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = metrics.titleFontSizeSp.sp,
                    lineHeight = (metrics.titleFontSizeSp + 4f).sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )

            if (isSeries || !subtitle.isNullOrBlank()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSeries) {
                        Text(
                            text = seriesHeaderText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = SERIES_META_SEPARATOR,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }

            if (metrics.showProgressBar && progressPercent != null) {
                val normalizedProgress = progressPercent.coerceIn(0, 100) / 100f
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(0.75f)
                        .widthIn(max = 180.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            if (colors.isEInk) {
                                colors.divider
                            } else {
                                colors.textPrimary.copy(alpha = 0.12f)
                            },
                        ),
                ) {
                    if (normalizedProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(normalizedProgress)
                                .background(colors.accent, CircleShape),
                        )
                    }
                }
            }
        }

        if (badgeData.hasAny || onClickContinueViewing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (badgeData.hasAny) {
                    AuroraLibraryListBadgeGroup(badgeData = badgeData)
                }

                if (onClickContinueViewing != null) {
                    val btnShape = RoundedCornerShape(9.dp)
                    Box(
                        modifier = Modifier
                            .size(metrics.continueButtonSizeDp.dp)
                            .clip(btnShape)
                            .background(
                                if (colors.isEInk) {
                                    colors.surface
                                } else {
                                    colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.12f)
                                },
                            )
                            .border(
                                width = if (colors.isEInk) 1.5.dp else 1.dp,
                                color = if (colors.isEInk) {
                                    colors.textPrimary
                                } else {
                                    colors.accent.copy(alpha = 0.30f)
                                },
                                shape = btnShape,
                            )
                            .clickable {
                                appHaptics.tap()
                                onClickContinueViewing()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(MR.strings.action_resume),
                            tint = if (colors.isEInk) colors.textPrimary else colors.accent,
                            modifier = Modifier.size(metrics.continueIconSizeDp.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroraLibraryListCoverThumbnail(
    coverRequest: Any?,
    placeholderPainter: androidx.compose.ui.graphics.painter.Painter,
    widthDp: Int,
    heightDp: Int,
    coverShape: RoundedCornerShape,
    isSeries: Boolean,
    isPinned: Boolean,
) {
    val colors = AuroraTheme.colors
    val borderColor = if (colors.isEInk) {
        colors.textPrimary
    } else {
        colors.textPrimary.copy(alpha = 0.10f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isSeries) {
            Box(
                modifier = Modifier
                    .width((widthDp - 12).coerceAtLeast(18).dp)
                    .height(2.dp)
                    .background(
                        color = if (colors.isEInk) {
                            colors.textPrimary.copy(alpha = 0.55f)
                        } else {
                            colors.accent.copy(alpha = 0.45f)
                        },
                        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                    ),
            )
            Spacer(modifier = Modifier.height(1.dp))
            Box(
                modifier = Modifier
                    .width((widthDp - 6).coerceAtLeast(24).dp)
                    .height(2.dp)
                    .background(
                        color = if (colors.isEInk) {
                            colors.textPrimary
                        } else {
                            colors.accent.copy(alpha = 0.72f)
                        },
                        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .size(
                    width = widthDp.dp,
                    height = if (isSeries) (heightDp - 5).coerceAtLeast(36).dp else heightDp.dp,
                )
                .clip(coverShape)
                .border(width = 1.dp, color = borderColor, shape = coverShape),
        ) {
            AsyncImage(
                model = coverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = placeholderPainter,
                fallback = placeholderPainter,
            )

            if (isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(16.dp)
                        .background(
                            color = colors.accent,
                            shape = RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = colors.textOnAccent,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AuroraLibraryListBadgeGroup(
    badgeData: AuroraLibraryListBadgeData,
) {
    val colors = AuroraTheme.colors
    val secondaryBg = if (colors.isEInk) {
        colors.surface
    } else if (colors.isDark) {
        colors.textPrimary.copy(alpha = 0.14f)
    } else {
        colors.textPrimary.copy(alpha = 0.10f)
    }
    val secondaryText = colors.textPrimary

    BadgeGroup {
        badgeData.downloadLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Badge(
                text = label,
                color = secondaryBg,
                textColor = secondaryText,
                shape = RoundedCornerShape(4.dp),
            )
        }
        badgeData.unreadCount?.takeIf { it > 0L }?.let { unread ->
            Badge(
                text = unread.toString(),
                color = colors.accent,
                textColor = colors.textOnAccent,
                shape = RoundedCornerShape(4.dp),
            )
        }
        if (badgeData.isLocal) {
            Badge(
                text = stringResource(AYMR.strings.aurora_local),
                color = secondaryBg,
                textColor = secondaryText,
                shape = RoundedCornerShape(4.dp),
            )
        } else {
            badgeData.language?.takeIf { it.isNotBlank() }?.let { lang ->
                Badge(
                    text = lang.uppercase(Locale.ROOT),
                    color = secondaryBg,
                    textColor = secondaryText,
                    shape = RoundedCornerShape(4.dp),
                )
            }
        }
    }
}

/**
 * Two-layer glowing gradient row divider matching Browse*SourceListRowDivider,
 * with opaque high-contrast fallback for E-Ink profiles.
 */
@Composable
internal fun AuroraLibraryListRowDivider(
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    if (colors.isEInk) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(colors.divider),
        )
        return
    }

    val chrome = chromeAccentDetails()
    val haloAlpha = if (colors.isDark) 0.12f else 0.08f
    val coreAlpha = if (colors.isDark) 0.45f else 0.30f

    Column(modifier = modifier.padding(horizontal = 14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.Transparent,
                            chrome.copy(alpha = haloAlpha),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.Transparent,
                            chrome.copy(alpha = coreAlpha),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}
