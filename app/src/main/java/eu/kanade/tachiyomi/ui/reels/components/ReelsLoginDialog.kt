package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Login / account dialog for a feed source implementing AnimeFeedLoginSource (contract v19).
 *
 * Logged out: an email + password form with an inline error line; the confirm button is
 * disabled while credentials are missing or a login is in flight. Logged in: shows the
 * account label and a "Log out" action.
 */
@Composable
fun ReelsLoginDialog(
    loggedInAccount: String?,
    isLoggingIn: Boolean,
    loginError: String?,
    onLogin: (email: String, password: String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (loggedInAccount != null) MR.strings.reels_account else MR.strings.reels_login,
                ),
            )
        },
        text = {
            if (loggedInAccount != null) {
                Text(stringResource(MR.strings.reels_logged_in_as, loggedInAccount))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(MR.strings.reels_login_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(MR.strings.reels_login_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (loginError != null) {
                        Text(
                            text = loginError,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (loggedInAccount != null) {
                TextButton(onClick = onLogout) {
                    Text(stringResource(MR.strings.reels_logout))
                }
            } else {
                TextButton(
                    enabled = !isLoggingIn && email.isNotBlank() && password.isNotBlank(),
                    onClick = { onLogin(email.trim(), password) },
                ) {
                    Text(stringResource(MR.strings.reels_login_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
