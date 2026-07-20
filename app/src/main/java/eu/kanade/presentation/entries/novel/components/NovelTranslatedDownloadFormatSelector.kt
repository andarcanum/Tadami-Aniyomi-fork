package eu.kanade.presentation.entries.novel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

@Composable
internal fun NovelTranslatedDownloadFormatSelector(
    format: NovelTranslatedDownloadFormat,
    onFormatSelected: (NovelTranslatedDownloadFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val track = if (colors.isEInk) {
        colors.surface
    } else {
        colors.textPrimary.copy(alpha = if (colors.isDark) 0.06f else 0.05f)
    }
    val rim = if (colors.isEInk) {
        colors.textPrimary
    } else {
        colors.textPrimary.copy(alpha = 0.10f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(track, CircleShape)
            .border(1.dp, rim, CircleShape)
            .padding(3.dp),
    ) {
        FormatSegment(
            selected = format == NovelTranslatedDownloadFormat.TXT,
            text = stringResource(AYMR.strings.novel_translated_download_format_txt),
            onClick = {
                appHaptics.tap()
                onFormatSelected(NovelTranslatedDownloadFormat.TXT)
            },
            modifier = Modifier.weight(1f),
        )
        FormatSegment(
            selected = format == NovelTranslatedDownloadFormat.DOCX,
            text = stringResource(AYMR.strings.novel_translated_download_format_docx),
            onClick = {
                appHaptics.tap()
                onFormatSelected(NovelTranslatedDownloadFormat.DOCX)
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FormatSegment(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val accent = if (colors.isEInk) colors.textPrimary else colors.accent
    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier
                        .background(accent, CircleShape)
                        .border(
                            1.dp,
                            if (colors.isEInk) colors.textPrimary else accent.copy(alpha = 0.45f),
                            CircleShape,
                        )
                } else {
                    Modifier
                },
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                selected && colors.isEInk -> colors.background
                selected -> colors.textOnAccent
                else -> colors.textSecondary
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
