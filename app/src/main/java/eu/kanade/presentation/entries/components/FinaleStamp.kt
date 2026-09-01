package eu.kanade.presentation.entries.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The double-ring «finished» stamp shared by the reader finale plate and the title screen.
 * A keepsake mark, not a status chip: label, optional date, and the ✦ tadami  line.
 */
@Composable
fun FinaleStamp(
    label: String,
    date: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val labelSize = size.value * 0.083f // ~8sp at 96dp
    val dateSize = size.value * 0.135f // ~13sp at 96dp
    Box(
        modifier = modifier
            .size(size)
            .rotate(-12f)
            .border(size / 48f, accent, CircleShape)
            .padding(size * 0.052f)
            .border(size / 96f, accent.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(size * 0.031f),
        ) {
            Text(
                text = label,
                fontSize = labelSize.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (labelSize * 0.3f).sp,
                maxLines = 1,
                color = accent,
            )
            if (date != null) {
                Text(
                    text = date,
                    fontSize = dateSize.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    color = accent,
                )
            }
            Text(
                text = "✦ tadami ✦",
                fontSize = labelSize.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = accent.copy(alpha = 0.85f),
            )
        }
    }
}
