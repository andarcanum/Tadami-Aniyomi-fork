package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Action buttons card with favorite, webview, tracking, and share options.
 * Compact design to avoid drawing too much attention.
 */
@Composable
fun MangaActionCard(
    manga: Manga,
    trackingCount: Int,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onShareClicked: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 6.dp,
        innerPadding = 12.dp,
        cornerRadius = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            // Favorite button
            ActionButton(
                icon = {
                    Icon(
                        if (manga.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = if (manga.favorite) {
                    stringResource(MR.strings.in_library)
                } else {
                    stringResource(MR.strings.add_to_library)
                },
                onClick = onAddToLibraryClicked,
                modifier = Modifier.weight(1f),
            )

            // Webview button (renamed from Source)
            if (onWebViewClicked != null) {
                ActionButton(
                    icon = {
                        Icon(
                            Icons.Outlined.Public,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = "Webview",
                    onClick = onWebViewClicked,
                    modifier = Modifier.weight(1f),
                )
            }

            // Tracking button
            if (onTrackingClicked != null) {
                ActionButton(
                    icon = {
                        Icon(
                            if (trackingCount == 0) Icons.Outlined.Sync else Icons.Outlined.Done,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = if (trackingCount == 0) {
                        stringResource(MR.strings.manga_tracking_tab)
                    } else {
                        pluralStringResource(MR.plurals.num_trackers, count = trackingCount, trackingCount)
                    },
                    onClick = onTrackingClicked,
                    modifier = Modifier.weight(1f),
                )
            }

            // Share button
            if (onShareClicked != null) {
                ActionButton(
                    icon = {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = stringResource(MR.strings.action_share),
                    onClick = onShareClicked,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = colors.textPrimary.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 12.sp,
        )
    }
}
