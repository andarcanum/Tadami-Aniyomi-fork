package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.translation.MlKitModelManager
import eu.kanade.tachiyomi.ui.reader.novel.translation.MlKitTranslationModelInfo
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

@Composable
fun MlKitTranslationModelsDialog(
    onDismiss: () -> Unit,
) {
    val modelManager = remember { MlKitModelManager() }
    val coroutineScope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<MlKitTranslationModelInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    fun reloadModels() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                models = modelManager.listModels()
            } catch (error: Throwable) {
                errorMessage = error.message ?: error::class.java.simpleName
            } finally {
                isLoading = false
            }
        }
    }

    fun mutateModels(operation: suspend () -> Unit) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                operation()
                models = modelManager.listModels()
            } catch (error: Throwable) {
                errorMessage = error.message ?: error::class.java.simpleName
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadModels()
    }

    val filteredModels = remember(query, models) {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isBlank()) {
            models
        } else {
            models.filter { model ->
                model.displayName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    model.languageCode.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
        }
    }

    val downloadedCount = remember(models) {
        models.count { it.isDownloaded }
    }
    val removableCount = remember(models) {
        models.count { it.canDelete }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(AYMR.strings.novel_reader_mlkit_models_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_mlkit_models_description),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = stringResource(AYMR.strings.novel_reader_mlkit_downloaded_models),
                    style = MaterialTheme.typography.titleSmall,
                )

                if (downloadedCount == 0 && !isLoading) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_mlkit_no_models),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(AYMR.strings.novel_reader_mlkit_search_models)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading,
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = filteredModels,
                        key = { it.languageCode },
                    ) { model ->
                        MlKitTranslationModelRow(
                            model = model,
                            enabled = !isLoading,
                            onDownload = { mutateModels { modelManager.download(model.languageCode) } },
                            onDelete = { mutateModels { modelManager.delete(model.languageCode) } },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(AYMR.strings.novel_reader_mlkit_close))
            }
        },
        dismissButton = if (removableCount > 0) {
            {
                TextButton(onClick = { showDeleteAllConfirm = true }) {
                    Text(stringResource(AYMR.strings.novel_reader_mlkit_delete_all))
                }
            }
        } else {
            null
        },
    )

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = {
                Text(text = stringResource(AYMR.strings.novel_reader_mlkit_delete_all_models))
            },
            text = {
                Text(text = stringResource(AYMR.strings.novel_reader_mlkit_delete_all_confirm))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllConfirm = false
                        mutateModels { modelManager.deleteAll() }
                    },
                ) {
                    Text(stringResource(AYMR.strings.novel_reader_mlkit_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(stringResource(AYMR.strings.novel_reader_mlkit_cancel))
                }
            },
        )
    }
}

@Composable
private fun MlKitTranslationModelRow(
    model: MlKitTranslationModelInfo,
    enabled: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val statusLabel = when {
                model.isBuiltIn -> stringResource(AYMR.strings.novel_reader_mlkit_model_status_built_in)
                model.isDownloaded -> stringResource(AYMR.strings.novel_reader_mlkit_model_status_downloaded)
                else -> stringResource(AYMR.strings.novel_reader_mlkit_model_status_not_downloaded)
            }
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${model.languageCode} - $statusLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            model.isBuiltIn -> {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_mlkit_model_status_built_in),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            model.isDownloaded -> {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = enabled,
                ) {
                    Text(stringResource(AYMR.strings.novel_reader_mlkit_model_delete))
                }
            }
            else -> {
                Button(
                    onClick = onDownload,
                    enabled = enabled,
                ) {
                    Text(stringResource(AYMR.strings.novel_reader_mlkit_model_download))
                }
            }
        }
    }
}
