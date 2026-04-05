package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign

internal data class NovelPageBoundaryPreviewData(
    val chapterLabel: String,
    val chapterName: String,
    val chapterHint: String,
)

internal fun createNovelPageBoundaryPreviewData(
    chapterLabel: String,
    chapterName: String?,
    chapterHint: String,
): NovelPageBoundaryPreviewData {
    return NovelPageBoundaryPreviewData(
        chapterLabel = chapterLabel,
        chapterName = (chapterName?.takeIf { it.isNotBlank() } ?: chapterLabel).trim(),
        chapterHint = chapterHint,
    )
}

@Composable
internal fun NovelChapterArtDivider(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth(0.65f).height(24.dp)) {
        val width = size.width
        val height = size.height
        val midX = width / 2
        val midY = height / 2
        val gap = 24.dp.toPx()

        val leftBrush = Brush.horizontalGradient(
            0f to Color.Transparent,
            midX - gap to color.copy(alpha = 0.55f),
        )
        val rightBrush = Brush.horizontalGradient(
            midX + gap to color.copy(alpha = 0.55f),
            width to Color.Transparent,
        )

        drawLine(
            brush = leftBrush,
            start = Offset(0f, midY),
            end = Offset(midX - gap, midY),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            brush = rightBrush,
            start = Offset(midX + gap, midY),
            end = Offset(width, midY),
            strokeWidth = 1.dp.toPx(),
        )

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(midX, midY - 6.dp.toPx())
            lineTo(midX + 6.dp.toPx(), midY)
            lineTo(midX, midY + 6.dp.toPx())
            lineTo(midX - 6.dp.toPx(), midY)
            close()
        }
        drawPath(path, color.copy(alpha = 0.75f))
    }
}

@Composable
internal fun NovelPageBoundaryPreviewContent(
    preview: NovelPageBoundaryPreviewData,
    textColor: Color,
    chapterTitleTextColor: Color,
    textBackground: Color,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
) {
    val labelColor = textColor.copy(alpha = 0.42f)
    val hintColor = textColor.copy(alpha = 0.52f)
    val titleFontFamily = chapterTitleTypeface?.let { FontFamily(it) } ?: textTypeface?.let { FontFamily(it) }
    val bodyFontFamily = textTypeface?.let { FontFamily(it) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (textBackground.luminance() > 0.05f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.02f),
                                Color.Black.copy(alpha = 0.07f),
                            ),
                            radius = 2000f,
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = statusBarTopPadding + contentPadding * 2,
                    bottom = contentPadding * 2,
                    start = contentPadding,
                    end = contentPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = preview.chapterLabel.uppercase().map { "$it " }.joinToString("").trim(),
                color = labelColor,
                fontFamily = bodyFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp,
                textAlign = ComposeTextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                NovelChapterArtDivider(color = labelColor)

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = preview.chapterName,
                    color = chapterTitleTextColor,
                    fontFamily = titleFontFamily,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp,
                    textAlign = ComposeTextAlign.Center,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.12f),
                            offset = Offset(0f, 2f),
                            blurRadius = 4f,
                        ),
                    ),
                )

                Spacer(modifier = Modifier.height(36.dp))

                NovelChapterArtDivider(color = labelColor)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(labelColor.copy(alpha = 0.35f), CircleShape),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = preview.chapterHint,
                    color = hintColor,
                    fontFamily = bodyFontFamily,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = ComposeTextAlign.Center,
                    modifier = Modifier.alpha(0.8f),
                )
            }
        }
    }
}
