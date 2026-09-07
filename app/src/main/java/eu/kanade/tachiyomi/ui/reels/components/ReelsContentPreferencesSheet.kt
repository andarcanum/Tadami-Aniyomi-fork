package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.animesource.model.ContentPreferenceOption
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Content-preferences editor (contract v20, AnimeContentPreferencesSource): the account's
 * content-type toggles ("which content types appear in my feed"). The option list comes
 * from the service at runtime — nothing is hardcoded client-side.
 */
@Composable
fun ReelsContentPreferencesSheet(
    preferences: List<ContentPreferenceOption>?,
    isLoading: Boolean,
    error: String?,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var enabled by remember(preferences) {
        mutableStateOf(preferences?.filter { it.enabled }?.map { it.id }?.toSet() ?: emptySet())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(MR.strings.reels_content_prefs),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 8.dp),
                        )
                        TextButton(onClick = { onSave(enabled.toList()) }) {
                            Text(stringResource(MR.strings.action_save))
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    }
                },
            ) { padding ->
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                when {
                    isLoading -> LoadingScreen()
                    preferences.isNullOrEmpty() ->
                        EmptyScreen(
                            stringRes = MR.strings.reels_content_prefs_empty,
                            modifier = Modifier.fillMaxSize(),
                        )
                    else ->
                        LazyColumn(
                            contentPadding = padding,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(preferences, key = { it.id }) { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = option.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = option.id in enabled,
                                        onCheckedChange = { on ->
                                            enabled = if (on) enabled + option.id else enabled - option.id
                                        },
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}
