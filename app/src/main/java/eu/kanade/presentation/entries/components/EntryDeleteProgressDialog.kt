package eu.kanade.presentation.entries.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostDialog
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Modal progress for a running batch deletion (manga chapters / anime episodes / novel chapters).
 * The footer pill only hides the dialog — the underlying deletion keeps running.
 */
@Composable
fun EntryDeleteProgressDialog(
    title: String,
    progressText: String,
    deleted: Int,
    total: Int,
    onHide: () -> Unit,
) {
    AuroraFrostDialog(
        onDismiss = onHide,
        title = title,
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
                text = progressText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LinearProgressIndicator(
                progress = { if (total > 0) deleted.toFloat() / total else 0f },
            )
        }
    }
}
