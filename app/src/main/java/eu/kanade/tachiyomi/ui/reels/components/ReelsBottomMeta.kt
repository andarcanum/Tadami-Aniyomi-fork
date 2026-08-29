package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReelsBottomMeta(
    item: ShortVideoItem,
    onTagClick: (String) -> Unit,
    // Non-null (capability- and author-gated by the caller) makes the author chip open the
    // creator's page (contract v18).
    onAuthorClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Author Row
        Row(
            modifier = Modifier.then(
                onAuthorClick?.let { click ->
                    Modifier.clickable(onClick = click)
                } ?: Modifier,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val initial = item.author?.firstOrNull()?.uppercaseChar()?.toString() ?: "V"
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(AuroraTheme.colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text =
                item.author?.let { "@$it" } ?: (item.title ?: stringResource(MR.strings.reels_default_video_title)),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.isAuthorVerified) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = AuroraTheme.colors.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            // "No sound" badge for silent clips
            if (!item.hasAudio) {
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.reels_no_audio),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Title / Description
        if (!item.title.isNullOrBlank() && item.title != item.author) {
            Text(
                text = item.title.orEmpty(),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Metrics row (views / likes)
        if (item.viewsCount != null || item.likesCount != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item.viewsCount?.let { views ->
                    MetricItem(icon = Icons.Filled.PlayArrow, value = formatCompactCount(views))
                }
                item.likesCount?.let { likes ->
                    MetricItem(icon = Icons.Filled.Favorite, value = formatCompactCount(likes))
                }
            }
        }

        // Tag Chips
        if (item.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item.tags.take(4).forEach { tag ->
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .clickable { onTagClick(tag) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "#$tag",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    icon: ImageVector,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 1234 -> 1.2K, 1234567 -> 1.2M, etc. */
fun formatCompactCount(value: Long): String {
    // Explicit (default) locale: user-facing compact counts localize the decimal separator
    // intentionally instead of relying on the implicit default.
    val locale = Locale.getDefault()
    return when {
        value >= 1_000_000_000 -> "%.1fB".format(locale, value / 1_000_000_000.0)
        value >= 1_000_000 -> "%.1fM".format(locale, value / 1_000_000.0)
        value >= 1_000 -> "%.1fK".format(locale, value / 1_000.0)
        else -> value.toString()
    }
}
