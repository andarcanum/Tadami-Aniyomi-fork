package eu.kanade.tachiyomi.ui.browse.manga.migration.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.manga.migration.config.MigrationConfigScreenSheet
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class MigrationListScreen(
    private val mangaIds: Collection<Long>,
    private val sourceIds: Collection<Long>,
    private val extraSearchQuery: String?,
) : Screen() {

    private var matchOverride: Pair<Long, Long>? = null

    fun addMatchOverride(current: Long, target: Long) {
        matchOverride = current to target
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrationListScreenModel(mangaIds, sourceIds, extraSearchQuery) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        LaunchedEffect(matchOverride) {
            val (current, target) = matchOverride ?: return@LaunchedEffect
            screenModel.useMangaForMigration(current = current, target = target)
            matchOverride = null
        }

        MigrationListScreenContent(
            items = state.items,
            finishedCount = state.finishedCount,
            migrationComplete = state.migrationComplete,
            isMigrating = state.isMigrating,
            migrationProgress = state.migrationProgress,
            onItemClick = { navigator.push(MangaScreen(it.id)) },
            onSearchManually = { navigator.push(MigrateMangaSearchScreen(it.manga.id)) },
            onCancelSearch = screenModel::cancelSearch,
            onSkip = screenModel::removeManga,
            onMigrateNow = { screenModel.migrateNow(it, replace = true) },
            onCopyNow = { screenModel.migrateNow(it, replace = false) },
            onOpenBulkMigrateDialog = { screenModel.showMigrateDialog(copy = false) },
            onOpenBulkCopyDialog = { screenModel.showMigrateDialog(copy = true) },
            onOpenOptions = screenModel::openOptionsDialog,
        )

        when (val dialog = state.dialog) {
            is MigrationListScreenModel.Dialog.Migrate -> {
                MigrationConfirmDialog(
                    copy = dialog.copy,
                    totalCount = dialog.totalCount,
                    skippedCount = dialog.skippedCount,
                    onDismissRequest = screenModel::dismissDialog,
                    onConfirm = {
                        if (dialog.copy) {
                            screenModel.copyMangas()
                        } else {
                            screenModel.migrateMangas()
                        }
                    },
                )
            }
            MigrationListScreenModel.Dialog.Exit -> {
                MigrationExitDialog(
                    isMigrating = state.isMigrating,
                    onDismissRequest = screenModel::dismissDialog,
                    onConfirm = {
                        if (state.isMigrating) {
                            screenModel.cancelMigrate()
                        }
                        navigator.pop()
                    },
                )
            }
            MigrationListScreenModel.Dialog.Options -> {
                MigrationConfigScreenSheet(
                    preferences = screenModel.sourcePreferences,
                    onDismissRequest = screenModel::dismissDialog,
                    onStartMigration = {
                        screenModel.onMigrationOptionsUpdated()
                    },
                )
            }
            null -> Unit
        }

        if (state.isMigrating) {
            MigrationProgressDialog(
                progress = state.migrationProgress,
                onCancel = screenModel::cancelMigrate,
            )
        }

        BackHandler(enabled = true) {
            if (state.items.isEmpty() && !state.isMigrating) {
                navigator.pop()
            } else {
                screenModel.showExitDialog()
            }
        }
    }
}

@Composable
private fun MigrationConfirmDialog(
    copy: Boolean,
    totalCount: Int,
    skippedCount: Int,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val actionableCount = (totalCount - skippedCount).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = if (copy) {
                    stringResource(MR.strings.migration_confirm_copy_title)
                } else {
                    stringResource(MR.strings.migration_confirm_migrate_title)
                },
            )
        },
        text = {
            Text(
                text = stringResource(
                    MR.strings.migration_confirm_body,
                    actionableCount,
                    totalCount,
                    skippedCount,
                ),
            )
        },
        confirmButton = {
            TextButton(
                enabled = actionableCount > 0,
                onClick = onConfirm,
            ) {
                Text(text = if (copy) stringResource(MR.strings.copy) else stringResource(MR.strings.action_migrate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun MigrationExitDialog(
    isMigrating: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.migration_exit_title)) },
        text = {
            Text(
                text = if (isMigrating) {
                    stringResource(MR.strings.migration_exit_body_migrating)
                } else {
                    stringResource(MR.strings.migration_exit_body)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun MigrationProgressDialog(
    progress: Float,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(MR.strings.migration_progress_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        MR.strings.migration_progress_body,
                        (progress.coerceIn(0f, 1f) * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.padding.medium),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
