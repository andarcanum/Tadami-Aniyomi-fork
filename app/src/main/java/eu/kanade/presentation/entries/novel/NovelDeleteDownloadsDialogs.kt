package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/** Unified confirmation for deleting downloaded novel chapters (selected / all / read). */
@Composable
fun NovelDeleteDownloadsConfirmDialog(
    isReadMode: Boolean,
    count: Int,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AuroraFrostDialog(
        onDismiss = onDismissRequest,
        title = stringResource(
            if (isReadMode) AYMR.strings.novel_delete_read_title else AYMR.strings.novel_delete_downloaded_title,
        ),
        innerScroll = false,
        footer = {
            AuroraFrostCancel(
                label = stringResource(MR.strings.action_cancel),
                onClick = onDismissRequest,
            )
            AuroraFrostConfirm(
                label = stringResource(MR.strings.action_delete),
                onClick = onConfirm,
            )
        },
    ) {
        Text(
            text = stringResource(AYMR.strings.novel_delete_downloaded_confirm_body, count),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Modal progress for a running batch deletion; the footer pill hides it — the batch keeps running. */
@Composable
fun NovelDeleteDownloadsProgressDialog(
    deleted: Int,
    total: Int,
    onHide: () -> Unit,
) {
    AuroraFrostDialog(
        onDismiss = onHide,
        title = stringResource(AYMR.strings.novel_delete_downloaded_title),
        innerScroll = false,
        footer = {
            AuroraFrostCancel(
                label = stringResource(MR.strings.action_cancel),
                onClick = onHide,
            )
        },
    ) {
        Column {
            Text(
                text = stringResource(AYMR.strings.novel_delete_downloaded_progress, deleted, total),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LinearProgressIndicator(
                progress = { if (total > 0) deleted.toFloat() / total else 0f },
            )
        }
    }
}
