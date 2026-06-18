package eu.kanade.presentation.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private const val MIN_DELAY_MS = 0
private const val MAX_DELAY_MS = 30_000
private const val MIN_JITTER_MS = 0
private const val MAX_JITTER_MS = 10_000
private const val MIN_TIMEOUT_MS = 5_000
private const val MAX_TIMEOUT_MS = 180_000
private const val MIN_FAILURE_COOLDOWN_MS = 0
private const val MAX_FAILURE_COOLDOWN_MS = 300_000

/**
 * Advanced anti-rate-limit controls for the novel download queue.
 *
 * Values are intentionally stored in milliseconds to match the queue backend,
 * but the UI validates and clamps them so an accidental typo cannot create an
 * unusable downloader configuration.
 */
@Composable
fun NovelDownloadThrottleSettingsDialog(
    onDismissRequest: () -> Unit,
    preferences: DownloadPreferences = Injekt.get(),
) {
    var delayMsText by remember { mutableStateOf(preferences.novelDownloadDelayMs().get().toString()) }
    var jitterMsText by remember { mutableStateOf(preferences.novelDownloadJitterMs().get().toString()) }
    var timeoutMsText by remember { mutableStateOf(preferences.novelDownloadTimeoutMs().get().toString()) }
    var cooldownMsText by remember { mutableStateOf(preferences.novelDownloadFailureCooldownMs().get().toString()) }

    fun normalizedInt(value: String, min: Int, max: Int, fallback: Int): Int {
        return value.toIntOrNull()?.coerceIn(min, max) ?: fallback
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(AYMR.strings.novel_download_throttle_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(AYMR.strings.novel_download_throttle_summary),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThrottleNumberField(
                        value = delayMsText,
                        onValueChange = { delayMsText = it.onlyDigits() },
                        label = stringResource(AYMR.strings.novel_download_throttle_delay_ms),
                        modifier = Modifier.weight(1f),
                    )
                    ThrottleNumberField(
                        value = jitterMsText,
                        onValueChange = { jitterMsText = it.onlyDigits() },
                        label = stringResource(AYMR.strings.novel_download_throttle_jitter_ms),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThrottleNumberField(
                        value = timeoutMsText,
                        onValueChange = { timeoutMsText = it.onlyDigits() },
                        label = stringResource(AYMR.strings.novel_download_throttle_timeout_ms),
                        modifier = Modifier.weight(1f),
                    )
                    ThrottleNumberField(
                        value = cooldownMsText,
                        onValueChange = { cooldownMsText = it.onlyDigits() },
                        label = stringResource(AYMR.strings.novel_download_throttle_cooldown_ms),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(
                        AYMR.strings.novel_download_throttle_limits,
                        MIN_DELAY_MS,
                        MAX_DELAY_MS,
                        MIN_TIMEOUT_MS,
                        MAX_TIMEOUT_MS,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val delayMs = normalizedInt(
                        delayMsText,
                        MIN_DELAY_MS,
                        MAX_DELAY_MS,
                        preferences.novelDownloadDelayMs().get(),
                    )
                    val jitterMs = normalizedInt(
                        jitterMsText,
                        MIN_JITTER_MS,
                        MAX_JITTER_MS,
                        preferences.novelDownloadJitterMs().get(),
                    )
                    val timeoutMs = normalizedInt(
                        timeoutMsText,
                        MIN_TIMEOUT_MS,
                        MAX_TIMEOUT_MS,
                        preferences.novelDownloadTimeoutMs().get(),
                    )
                    val cooldownMs = normalizedInt(
                        cooldownMsText,
                        MIN_FAILURE_COOLDOWN_MS,
                        MAX_FAILURE_COOLDOWN_MS,
                        preferences.novelDownloadFailureCooldownMs().get(),
                    )
                    preferences.novelDownloadDelayMs().set(delayMs)
                    preferences.novelDownloadJitterMs().set(jitterMs)
                    preferences.novelDownloadTimeoutMs().set(timeoutMs)
                    preferences.novelDownloadFailureCooldownMs().set(cooldownMs)
                    onDismissRequest()
                },
            ) {
                Text(stringResource(AYMR.strings.novel_download_throttle_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(AYMR.strings.novel_download_throttle_cancel))
            }
        },
    )
}

@Composable
private fun ThrottleNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun String.onlyDigits(): String = filter(Char::isDigit)
