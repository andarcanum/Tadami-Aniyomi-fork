package eu.kanade.presentation.library.anime.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.AuroraBlurBehindDialog
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
    val colors = AuroraTheme.colors

    AuroraBlurBehindDialog(onDismissRequest = onDismissRequest) {
        Text(
            text = stringResource(AYMR.strings.action_create_series),
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(MR.strings.name)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = true,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onDismissRequest,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
            ) {
                Text(stringResource(MR.strings.action_cancel))
            }
            TextButton(
                onClick = {
                    onCreate(name)
                    onDismissRequest()
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
