package eu.kanade.presentation.entries.anime

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DubbingSelectionDialog(
    availableDubbings: List<String>,
    currentDubbing: String,
    currentQuality: String,
    onDismissRequest: () -> Unit,
    onConfirm: (dubbing: String, quality: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDubbing by remember { mutableStateOf(currentDubbing.ifBlank { availableDubbings.firstOrNull() ?: "" }) }
    var selectedQuality by remember { mutableStateOf(currentQuality.ifBlank { "best" }) }

    val qualityOptions = listOf("best", "1080p", "720p", "480p", "360p")
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    AdaptiveSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    vertical = TabbedDialogPaddings.Vertical,
                    horizontal = TabbedDialogPaddings.Horizontal,
                )
                .fillMaxWidth(),
        ) {
            Text(
                modifier = Modifier.padding(bottom = 16.dp, top = 8.dp),
                text = stringResource(MR.strings.label_dubbing),
                style = MaterialTheme.typography.headlineMedium,
            )

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Translation",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            items(availableDubbings) { dubbing ->
                                DubbingRadioItem(
                                    text = dubbing,
                                    selected = selectedDubbing == dubbing,
                                    onClick = { selectedDubbing = dubbing },
                                )
                            }
                        }
                    }

                    VerticalDivider()

                    Column(modifier = Modifier.weight(0.6f)) {
                        Text(
                            text = "Quality",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            items(qualityOptions) { quality ->
                                DubbingRadioItem(
                                    text = if (quality == "best") "Best Available" else quality,
                                    selected = selectedQuality == quality,
                                    onClick = { selectedQuality = quality },
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Voice Translation",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    availableDubbings.forEach { dubbing ->
                        DubbingRadioItem(
                            text = dubbing,
                            selected = selectedDubbing == dubbing,
                            onClick = { selectedDubbing = dubbing },
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text(
                        text = "Quality",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    qualityOptions.forEach { quality ->
                        DubbingRadioItem(
                            text = if (quality == "best") "Best Available" else quality,
                            selected = selectedQuality == quality,
                            onClick = { selectedQuality = quality },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(MR.strings.action_cancel))
                }
                Button(
                    onClick = { onConfirm(selectedDubbing, selectedQuality) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(MR.strings.action_save))
                }
            }
        }
    }
}

@Composable
private fun DubbingRadioItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
