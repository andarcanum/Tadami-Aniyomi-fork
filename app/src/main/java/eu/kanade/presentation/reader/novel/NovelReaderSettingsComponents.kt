package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import kotlin.math.abs

@Composable
fun LnReaderSliderRow(
    label: String,
    valueText: (Float) -> String,
    committedValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftValue by rememberSaveable { mutableStateOf(committedValue) }
    var previousCommittedValue by rememberSaveable { mutableStateOf(committedValue) }

    LaunchedEffect(committedValue) {
        val synced = syncLnReaderSliderDraft(
            committedValue = committedValue,
            previousCommittedValue = previousCommittedValue,
            currentDraftValue = draftValue,
        )
        draftValue = synced.draftValue
        previousCommittedValue = synced.committedValue
    }

    val colors = AuroraTheme.colors
    val contentAlpha = if (enabled) 1f else 0.38f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NovelGlassContentPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary.copy(alpha = contentAlpha),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
            Text(
                text = valueText(draftValue),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary.copy(alpha = contentAlpha),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Slider(
            value = draftValue,
            onValueChange = { draftValue = it },
            onValueChangeFinished = {
                resolveLnReaderSliderCommitValue(
                    committedValue = committedValue,
                    draftValue = draftValue,
                )?.let(onCommit)
            },
            enabled = enabled,
            valueRange = range,
            steps = steps,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.textSecondary.copy(alpha = 0.24f),
                disabledThumbColor = colors.accent.copy(alpha = 0.38f),
                disabledActiveTrackColor = colors.accent.copy(alpha = 0.24f),
            ),
        )
    }
}

internal data class LnReaderSliderDraftState(
    val committedValue: Float,
    val draftValue: Float,
)

internal fun syncLnReaderSliderDraft(
    committedValue: Float,
    previousCommittedValue: Float,
    currentDraftValue: Float,
): LnReaderSliderDraftState {
    return if (abs(committedValue - previousCommittedValue) > 0.0001f) {
        LnReaderSliderDraftState(
            committedValue = committedValue,
            draftValue = committedValue,
        )
    } else {
        LnReaderSliderDraftState(
            committedValue = previousCommittedValue,
            draftValue = currentDraftValue,
        )
    }
}

internal fun resolveLnReaderSliderCommitValue(
    committedValue: Float,
    draftValue: Float,
): Float? {
    return draftValue.takeIf { abs(it - committedValue) > 0.0001f }
}
