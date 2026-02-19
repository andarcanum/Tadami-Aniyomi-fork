package eu.kanade.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.theme.AuroraTheme

@Composable
fun AuroraCard(
    title: String,
    coverData: Any?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
    isSelected: Boolean = false,
    aspectRatio: Float = 2f / 3f, // Default to portrait
    coverHeightFraction: Float = 0.65f, // Image takes 65% of height
    imagePadding: Dp = 0.dp,
    titleMaxLines: Int = 2,
) {
    val colors = AuroraTheme.colors
    val normalizedCoverHeightFraction = coverHeightFraction.coerceIn(0.01f, 1f)
    val showTextContent = normalizedCoverHeightFraction < 1f

    Card(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.glass,
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                colors.accent
            } else if (colors.isDark) {
                Color.Transparent
            } else {
                Color.LightGray.copy(alpha = 0.4f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Cover Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (showTextContent) normalizedCoverHeightFraction else 1f)
                    .background(Color.Black.copy(alpha = 0.1f)) // Placeholder bg
                    .padding(imagePadding),
            ) {
                AsyncImage(
                    model = coverData,
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
                    error = rememberVectorPainter(Icons.Default.BrokenImage),
                )

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

                if (onClickContinueViewing != null) {
                    FilledIconButton(
                        onClick = onClickContinueViewing,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(30.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.accent.copy(alpha = 0.9f),
                            contentColor = colors.textOnAccent,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
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
