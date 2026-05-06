package eu.kanade.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraBorderColor
import eu.kanade.presentation.theme.resolveAuroraSelectionBorderColor
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import tachiyomi.presentation.core.util.LocalAppHaptics

@Composable
fun AuroraCard(
    title: String,
    coverData: Any?,
    modifier: Modifier = Modifier,
    seriesHeaderText: String? = null,
    subtitle: String? = null,
    badge: @Composable (() -> Unit)? = null,
    topEndBadge: @Composable (() -> Unit)? = null,
    menuContent: (@Composable ColumnScope.(closeMenu: () -> Unit) -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
    isSelected: Boolean = false,
    aspectRatio: Float = 2f / 3f, // Default to portrait
    coverHeightFraction: Float = 0.65f, // Image takes 65% of height
    imagePadding: Dp = 0.dp,
    titleMaxLines: Int = 2,
    gridColumns: Int? = null,
    customCover: @Composable (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val appHaptics = LocalAppHaptics.current
    var showMenu by remember { mutableStateOf(false) }
    val normalizedCoverHeightFraction = coverHeightFraction.coerceIn(0.01f, 1f)
    val showTextContent = normalizedCoverHeightFraction < 1f
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val coverRequest = remember(coverData) {
        buildAuroraCoverImageRequest(context, coverData)
    }

    Card(
        modifier = modifier
            .combinedClickable(
                onClick = {
                    appHaptics.tap()
                    onClick()
                },
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (colors.isDark) {
                colors.glass.copy(alpha = 0.06f)
            } else {
                resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
            },
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                if (colors.isDark) colors.accent else resolveAuroraSelectionBorderColor(colors)
            } else {
                resolveAuroraBorderColor(colors, emphasized = false)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Cover Image
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (showTextContent) normalizedCoverHeightFraction else 1f)
                    .padding(imagePadding),
            ) {
                val overlaySpec = resolveAuroraCardOverlaySpec(
                    gridColumns = gridColumns,
                    cardWidthDp = maxWidth.value,
                )

                if (customCover != null) {
                    customCover()
                } else {
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(
                                if (imagePadding >
                                    0.dp
                                ) {
                                    RoundedCornerShape(8.dp)
                                } else if (showTextContent) {
                                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                                } else {
                                    RoundedCornerShape(12.dp)
                                },
                            ),
                        error = placeholderPainter,
                        fallback = placeholderPainter,
                    )
                }

                // Badge overlay (e.g. Unread count)
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    ) {
                        badge()
                    }
                }

                if (topEndBadge != null || menuContent != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        topEndBadge?.invoke()

                        if (menuContent != null) {
                            Box {
                                FilledIconButton(
                                    onClick = {
                                        appHaptics.tap()
                                        showMenu = true
                                    },
                                    modifier = Modifier.size(28.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = colors.surface.copy(alpha = 0.9f),
                                        contentColor = colors.textPrimary,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }

                                AuroraEntryDropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    menuContent { showMenu = false }
                                }
                            }
                        }
                    }
                }

                if (onClickContinueViewing != null) {
                    FilledIconButton(
                        onClick = {
                            appHaptics.tap()
                            onClickContinueViewing()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(overlaySpec.buttonSizeDp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.accent.copy(alpha = 0.9f),
                            contentColor = colors.textOnAccent,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(overlaySpec.buttonIconSizeDp),
                        )
                    }
                }
            }

            if (showTextContent) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight((1f - normalizedCoverHeightFraction).coerceAtLeast(0.01f))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    if (!seriesHeaderText.isNullOrBlank()) {
                        Text(
                            text = seriesHeaderText,
                            color = colors.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 12.sp,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )

                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
