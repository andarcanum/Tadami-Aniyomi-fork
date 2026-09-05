package eu.kanade.presentation.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoScrollActionFab(
    autoScrollEnabled: Boolean,
    showFab: Boolean,
    contentDescription: String? = null,
    longClickLabel: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = showFab,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        // B-A4: the FAB hardcoded Color.White border/tint over a translucent secondaryContainer;
        // on e-ink (and light aurora surfaces) that was unreadable. Theme-aware colors now:
        // e-ink gets opaque surface + text colors, glass modes keep the white-on-translucent look.
        val aurora = AuroraTheme.colors
        val iconTint = if (aurora.isEInk) aurora.textPrimary else Color.White
        val containerColor = if (aurora.isEInk) {
            aurora.surface
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        }
        val borderColor = (if (aurora.isEInk) aurora.textSecondary else Color.White).copy(alpha = 0.2f)
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(
                    width = 0.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(16.dp),
                )
                .clip(RoundedCornerShape(16.dp))
                .background(containerColor)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = longClickLabel,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (autoScrollEnabled) {
                    Icons.Outlined.Pause
                } else {
                    Icons.Outlined.PlayArrow
                },
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(24.dp)
                    .offset(x = if (!autoScrollEnabled) 1.dp else 0.dp),
                tint = iconTint,
            )
        }
    }
}
