package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaStatsCard(
    detailsSnapshot: MangaDetailsSnapshot,
    modifier: Modifier = Modifier,
) {
    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 20.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatRow(
                label = "Source",
                value = detailsSnapshot.sourceTitle ?: stringResource(MR.strings.not_applicable),
            )
            StatRow(
                label = "Translation",
                value = detailsSnapshot.translationText ?: stringResource(MR.strings.not_applicable),
            )
            StatRow(
                label = stringResource(tachiyomi.i18n.aniyomi.AYMR.strings.aurora_rating),
                value = detailsSnapshot.ratingText ?: stringResource(MR.strings.not_applicable),
                ratingValue = detailsSnapshot.ratingValue,
            )
            StatRow(
                label = "State",
                value = detailsSnapshot.statusText,
            )
            StatRow(
                label = "Chapters",
                value = detailsSnapshot.progress?.chaptersText ?: stringResource(MR.strings.not_applicable),
            )
            StatRow(
                label = "Progress",
                value = detailsSnapshot.progress?.progressText ?: stringResource(MR.strings.not_applicable),
            )
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    ratingValue: Float? = null,
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (ratingValue != null) {
            val starRating = ratingValue.coerceIn(0f, 1f) * 5f
            repeat(5) { index ->
                val isFilled = starRating >= (index + 1).toFloat()
                Icon(
                    imageVector = if (isFilled) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                    tint = if (isFilled) Color(0xFFFACC15) else colors.textSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(12.dp),
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
        }
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
