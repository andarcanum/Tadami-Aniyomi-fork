package eu.kanade.presentation.entries.novel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun NovelTranslatedDownloadFormatSelector(
    format: NovelTranslatedDownloadFormat,
    onFormatSelected: (NovelTranslatedDownloadFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = colorScheme.primary
    val onAccentColor = colorScheme.onPrimary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colorScheme.surfaceVariant.copy(alpha = 0.75f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FormatButton(
            selected = format == NovelTranslatedDownloadFormat.TXT,
            text = stringResource(AYMR.strings.novel_translated_download_format_txt),
            onClick = { onFormatSelected(NovelTranslatedDownloadFormat.TXT) },
            selectedColor = accentColor,
            selectedContentColor = onAccentColor,
            unselectedContentColor = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        FormatButton(
            selected = format == NovelTranslatedDownloadFormat.DOCX,
            text = stringResource(AYMR.strings.novel_translated_download_format_docx),
            onClick = { onFormatSelected(NovelTranslatedDownloadFormat.DOCX) },
            selectedColor = accentColor,
            selectedContentColor = onAccentColor,
            unselectedContentColor = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FormatButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    selectedColor: Color,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) selectedColor else Color.Transparent,
            contentColor = if (selected) selectedContentColor else unselectedContentColor,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        Text(
            text = if (selected) "* $text" else text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
