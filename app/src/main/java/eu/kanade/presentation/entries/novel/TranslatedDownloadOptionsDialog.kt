package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TranslatedDownloadOptionsDialog(
    onDismissRequest: () -> Unit,
    onReDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onReDownload) {
                Text(text = stringResource(AYMR.strings.novel_translated_download_redownload))
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(AYMR.strings.novel_translated_download_delete))
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            }
        },
        title = {
            Text(text = stringResource(AYMR.strings.novel_translated_download_options_title))
        },
        text = {
            Text(text = stringResource(AYMR.strings.novel_translated_download_options_message))
        },
    )
}
