package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings

@Composable
fun GoogleTranslationDialog(
    readerSettings: NovelReaderSettings,
    isTranslating: Boolean,
    translationProgress: Int,
    isVisible: Boolean,
    hasCache: Boolean,
    logs: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
    onToggleVisibility: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Google Translate")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = readerSettings.googleTranslationSourceLang,
                    onValueChange = {},
                    label = { Text("Source language") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTranslating,
                    singleLine = true,
                )

                OutlinedTextField(
                    value = readerSettings.googleTranslationTargetLang,
                    onValueChange = {},
                    label = { Text("Target language") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTranslating,
                    singleLine = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enabled")
                    Switch(
                        checked = readerSettings.googleTranslationEnabled,
                        onCheckedChange = {},
                        enabled = !isTranslating,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Auto-translate on chapter load")
                    Switch(
                        checked = readerSettings.googleTranslationAutoStart,
                        onCheckedChange = {},
                        enabled = !isTranslating,
                    )
                }

                if (isTranslating) {
                    LinearProgressIndicator(
                        progress = { translationProgress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Translating: $translationProgress%")
                }

                if (hasCache) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onToggleVisibility,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isVisible) "Original" else "Translation")
                        }
                        OutlinedButton(
                            onClick = onClear,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Clear")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        isTranslating -> {
                            Button(
                                onClick = onStop,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Stop")
                            }
                        }
                        hasCache && !isVisible -> {
                            Button(
                                onClick = onToggleVisibility,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Show Translation")
                            }
                        }
                        hasCache && isVisible -> {
                            Button(
                                onClick = onToggleVisibility,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Hide Translation")
                            }
                        }
                        else -> {
                            Button(
                                onClick = onStart,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Start Translation")
                            }
                        }
                    }

                    if (hasCache && !isTranslating) {
                        OutlinedButton(
                            onClick = {
                                onClear()
                                onStart()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Re-translate")
                        }
                    }
                }

                if (logs.isNotEmpty()) {
                    Text("Log:", modifier = Modifier.padding(top = 8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        logs.takeLast(20).forEach { log ->
                            Text(log, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
