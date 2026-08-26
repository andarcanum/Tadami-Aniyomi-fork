package eu.kanade.presentation.more.settings.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.MAX_CUSTOM_UPDATE_INTERVAL_HOURS
import eu.kanade.presentation.more.settings.MIN_CUSTOM_UPDATE_INTERVAL_HOURS
import eu.kanade.presentation.more.settings.initialCustomUpdateInterval
import eu.kanade.presentation.more.settings.showBatteryWarning
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/**
 * V1 «Classic M3» slider dialog for a custom library update interval (1..24 hours).
 * Approved prototype: docs/prototypes/prototype_custom_update_interval.html
 */
@Composable
fun CustomUpdateIntervalDialog(
    initialHours: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hours by rememberSaveable {
        mutableIntStateOf(
            initialCustomUpdateInterval(initialHours),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(MR.strings.update_custom_interval_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Slider(
                        value = hours.toFloat(),
                        onValueChange = {
                            hours =
                                it.roundToInt().coerceIn(
                                    MIN_CUSTOM_UPDATE_INTERVAL_HOURS,
                                    MAX_CUSTOM_UPDATE_INTERVAL_HOURS,
                                )
                        },
                        valueRange =
                        MIN_CUSTOM_UPDATE_INTERVAL_HOURS.toFloat()..MAX_CUSTOM_UPDATE_INTERVAL_HOURS.toFloat(),
                        steps = MAX_CUSTOM_UPDATE_INTERVAL_HOURS - MIN_CUSTOM_UPDATE_INTERVAL_HOURS - 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(MR.strings.hour_short, hours),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = showBatteryWarning(hours)) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(MR.strings.update_interval_battery_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hours) }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
