package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Compact in-reader footnote popover using [AuroraFrostDialog] so users can read
 * intra-chapter annotations (`#fn`, `#note`) without losing their current page position.
 */
@Composable
internal fun NovelFootnoteDialog(
    footnote: NovelFootnoteDialogData,
    onDismiss: () -> Unit,
    onOpenExternalUrl: ((String) -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val title = if (!footnote.titleNumber.isNullOrBlank()) {
        stringResource(AYMR.strings.novel_reader_footnote_title, footnote.titleNumber)
    } else {
        stringResource(AYMR.strings.novel_reader_footnote_title_default)
    }

    AuroraFrostDialog(
        onDismiss = onDismiss,
        title = title,
        closeLabel = stringResource(MR.strings.action_close),
        contentSpacing = 10.dp,
        footer = {
            val externalUrl = footnote.resolvedExternalUrl
            if (!footnote.found && !externalUrl.isNullOrBlank() && onOpenExternalUrl != null) {
                AuroraFrostCancel(
                    label = stringResource(AYMR.strings.novel_reader_footnote_open_external),
                    onClick = {
                        onDismiss()
                        onOpenExternalUrl(externalUrl)
                    },
                )
            }
            AuroraFrostConfirm(
                label = stringResource(MR.strings.action_close),
                onClick = onDismiss,
            )
        },
    ) {
        Text(
            text = "#${footnote.anchorId}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (colors.isEInk) colors.textPrimary else colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (colors.isEInk) {
                        colors.surface
                    } else if (colors.isDark) {
                        colors.accent.copy(alpha = 0.14f)
                    } else {
                        colors.accent.copy(alpha = 0.12f)
                    },
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Text(
            text = if (footnote.found) {
                footnote.bodyText
            } else {
                stringResource(AYMR.strings.novel_reader_footnote_missing)
            },
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = if (footnote.found) colors.textPrimary else colors.textSecondary,
        )
    }
}
