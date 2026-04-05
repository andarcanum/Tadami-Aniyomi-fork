package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun DiscreteSliderPreferenceWidget(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    helperText: String? = null,
) {
    val minValue = valueRange.first
    val maxValue = valueRange.last
    val stepCount = (maxValue - minValue - 1).coerceAtLeast(0)
    val haptic = LocalHapticFeedback.current
    var draftValue by rememberSaveable(title, minValue, maxValue) {
        mutableIntStateOf(value.coerceIn(minValue, maxValue))
    }

    LaunchedEffect(value, minValue, maxValue) {
        draftValue = value.coerceIn(minValue, maxValue)
    }

    BasePreferenceWidget(
        title = title,
        subcomponent = {
            Column(
                modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Slider(
                        value = draftValue.toFloat(),
                        onValueChange = { newValue ->
                            val roundedValue = newValue.roundToInt().coerceIn(minValue, maxValue)
                            if (roundedValue != draftValue) {
                                draftValue = roundedValue
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onValueChangeFinished = {
                            if (draftValue != value) {
                                onValueChange(draftValue)
                            }
                        },
                        valueRange = minValue.toFloat()..maxValue.toFloat(),
                        steps = stepCount,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            activeTickColor = MaterialTheme.colorScheme.primary,
                            inactiveTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = draftValue.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                if (helperText != null) {
                    Text(
                        text = helperText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
