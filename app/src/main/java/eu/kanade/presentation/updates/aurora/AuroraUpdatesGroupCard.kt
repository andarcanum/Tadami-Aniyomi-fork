package eu.kanade.presentation.updates.aurora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraBorderColor
import eu.kanade.presentation.theme.resolveAuroraControlContainerColor

@Composable
fun AuroraUpdatesGroupCard(
    title: String,
    countText: String,
    coverData: Any?,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val tabContainerColor = if (!colors.isDark && !colors.isEInk) {
        Color.White
    } else {
        resolveAuroraControlContainerColor(colors)
    }
    val rimBrush = when {
        colors.isDark -> Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.24f),
                colors.accent.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.06f),
            ),
        )
        colors.isEInk -> Brush.verticalGradient(
            colors = listOf(
                resolveAuroraBorderColor(colors, emphasized = true),
                resolveAuroraBorderColor(colors, emphasized = false),
            ),
        )
        else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val coverReloadTick = rememberCoverReloadTick()
    val coverRequest = remember(context, coverData, coverReloadTick) {
        buildAuroraCoverImageRequest(context, coverData)
    }
    val isLightTheme = !colors.isDark && !colors.isEInk
    val cardShape = RoundedCornerShape(16.dp)

    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = coverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
                modifier = Modifier
                    .width(48.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.cardBackground),
                error = placeholderPainter,
                fallback = placeholderPainter,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = countText,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    if (isLightTheme) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .drawBehind {
                    val radius = 16.dp.toPx()
                    val cornerRadius = CornerRadius(radius, radius)
                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()
                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.035f),
                        topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                        size = Size(width = size.width - neutralInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                    drawRoundRect(
                        color = colors.accent.copy(alpha = 0.025f),
                        topLeft = Offset(x = warmInset, y = warmOffsetY),
                        size = Size(width = size.width - warmInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                }
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.68f),
                            Color.White.copy(alpha = 0.60f),
                        ),
                    ),
                    shape = cardShape,
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                    ),
                    shape = cardShape,
                )
                .clip(cardShape)
                .clickable { onClick() },
        ) {
            cardContent()
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = tabContainerColor),
            border = if (colors.isDark || colors.isEInk) {
                BorderStroke(
                    width = 1.dp,
                    brush = rimBrush,
                )
            } else {
                null
            },
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
            ),
        ) {
            cardContent()
        }
    }
}
