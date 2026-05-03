package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelRepoPickerDialog(
    pluginName: String,
    options: List<NovelPlugin.Available>,
    onSelectPlugin: (NovelPlugin.Available) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxVersion = options.maxOfOrNull { it.version } ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(AYMR.strings.novel_repo_picker_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = pluginName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { plugin ->
                    RepoOptionItem(
                        plugin = plugin,
                        isNewest = plugin.version == maxVersion,
                        onClick = { onSelectPlugin(plugin) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun RepoOptionItem(
    plugin: NovelPlugin.Available,
    isNewest: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.repoName.ifBlank { plugin.repoUrl },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "v${plugin.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isNewest) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.NewReleases,
                contentDescription = stringResource(AYMR.strings.novel_repo_picker_newest),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
