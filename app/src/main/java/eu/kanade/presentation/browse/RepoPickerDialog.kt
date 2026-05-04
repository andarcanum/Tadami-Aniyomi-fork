package eu.kanade.presentation.browse

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
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.Comparator

@Composable
fun <T> RepoPickerDialog(
    titleRes: StringResource,
    newestContentDescriptionRes: StringResource,
    itemName: String,
    options: List<T>,
    onSelectOption: (T) -> Unit,
    onDismiss: () -> Unit,
    optionLabel: (T) -> String,
    optionVersionText: (T) -> String,
    comparator: Comparator<T>,
) {
    val newestOption = options.maxWithOrNull(comparator)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(titleRes))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = itemName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { option ->
                    RepoOptionItem(
                        label = optionLabel(option),
                        versionText = optionVersionText(option),
                        isNewest = newestOption != null && comparator.compare(option, newestOption) == 0,
                        newestContentDescriptionRes = newestContentDescriptionRes,
                        onClick = { onSelectOption(option) },
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
    label: String,
    versionText: String,
    isNewest: Boolean,
    newestContentDescriptionRes: StringResource,
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
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = versionText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isNewest) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.NewReleases,
                contentDescription = stringResource(newestContentDescriptionRes),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
