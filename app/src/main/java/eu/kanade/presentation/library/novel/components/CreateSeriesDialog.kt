package eu.kanade.presentation.library.novel.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CreateSeriesDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(name)
                    onDismissRequest()
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(stringResource(AYMR.strings.action_create_series))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(MR.strings.name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                )
            }
        },
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
