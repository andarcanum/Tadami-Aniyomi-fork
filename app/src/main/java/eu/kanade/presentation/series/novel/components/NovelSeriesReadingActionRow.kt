package eu.kanade.presentation.series.novel.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.components.resolveAuroraCtaLabelShadowSpec
import eu.kanade.presentation.components.toComposeShadow

@Composable
fun NovelSeriesReadingActionRow(
    label: String,
    hint: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val labelShadow = resolveAuroraCtaLabelShadowSpec(enabled = true).toComposeShadow()
    val containerAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.58f,
        animationSpec = tween(durationMillis = 180),
        label = "series_reading_cta_alpha",
    )
    val surfaceAlpha = if (colors.isDark) 0.50f else 0.88f
    val innerGlowBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to Color.Transparent,
            0.46f to AuroraTheme.colors.accent.copy(alpha = 0.12f),
            0.78f to AuroraTheme.colors.accent.copy(alpha = 0.34f),
            1.00f to AuroraTheme.colors.accent.copy(alpha = 0.56f),
        ),
    )
    val highlightBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to Color.White.copy(alpha = 0.16f),
            0.34f to Color.White.copy(alpha = 0.08f),
            0.68f to Color.Transparent,
            1.00f to Color.Transparent,
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.accent.copy(alpha = surfaceAlpha))
            .background(
                brush = innerGlowBrush,
                alpha = 1f,
            )
            .background(
                brush = highlightBrush,
                alpha = 1f,
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (enabled) 0.16f else 0.06f),
                        Color.Transparent,
                    ),
                ),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(containerAlpha)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.62f),
                modifier = Modifier.size(22.dp),
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Crossfade(
                    targetState = label,
                    label = "series_reading_cta_label",
                ) { currentLabel ->
                    Text(
                        text = currentLabel,
                        color = Color.White.copy(alpha = if (enabled) 1f else 0.62f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = labelShadow),
                    )
                }

                if (!hint.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = hint,
                        color = Color.White.copy(alpha = if (enabled) 0.78f else 0.42f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
