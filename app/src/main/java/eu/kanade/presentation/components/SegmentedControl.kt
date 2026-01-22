package eu.kanade.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme

/**
 * Aniview Premium Segmented Control
 * Capsule-shaped switcher for Anime/Manga selection
 * Matches the design from aniview_library_grid_view.png
 */
@Composable
fun AniviewSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index

            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) {
                    Color(0xFF1E3A5F) // Dark blue background for selected
                } else {
                    Color.Transparent
                },
                animationSpec = tween(durationMillis = 200),
                label = "segmentBackgroundColor",
            )

            val textColor by animateColorAsState(
                targetValue = if (isSelected) {
                    colors.accent // Electric blue for selected text
                } else {
                    colors.textSecondary
                },
                animationSpec = tween(durationMillis = 200),
                label = "segmentTextColor",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .clickable { onItemSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Compact variant for smaller spaces
 */
@Composable
fun AniviewSegmentedControlCompact(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = modifier
            .height(36.dp)
            .background(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (isSelected) Color(0xFF1E3A5F) else Color.Transparent,
                    )
                    .clickable { onItemSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item,
                    color = if (isSelected) colors.accent else colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
