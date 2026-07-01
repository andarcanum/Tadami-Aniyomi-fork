package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun PageIndicatorText(
    currentPage: Int,
    totalPages: Int,
    estimatedMinutesLeft: Int? = null,
) {
    if (currentPage <= 0 || totalPages <= 0) return

    val timeLeftText = when (estimatedMinutesLeft) {
        null -> ""
        0 -> " (${stringResource(MR.strings.reading_time_left_less_than_minute)})"
        else -> " (${stringResource(MR.strings.reading_time_left_minute, estimatedMinutesLeft)})"
    }

    val text = "$currentPage / $totalPages$timeLeftText"

    val style = TextStyle(
        color = Color(235, 235, 235),
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
    val strokeStyle = style.copy(
        color = Color(45, 45, 45),
        drawStyle = Stroke(width = 4f),
    )

    Box(
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = strokeStyle,
        )

        Text(
            text = text,
            style = style,
        )
    }
}

@PreviewLightDark
@Composable
private fun PageIndicatorTextPreview() {
    TachiyomiPreviewTheme {
        Surface {
            PageIndicatorText(currentPage = 10, totalPages = 69)
        }
    }
}
