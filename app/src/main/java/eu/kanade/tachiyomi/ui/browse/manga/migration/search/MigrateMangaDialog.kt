package eu.kanade.tachiyomi.ui.browse.manga.migration.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.manga.interactor.MigrateMangaUseCase
import eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun MigrateMangaDialog(
    oldManga: Manga,
    newManga: Manga,
    screenModel: MigrateMangaDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val state by screenModel.state.collectAsStateWithLifecycle()

    val flags = remember { MangaMigrationFlags.getFlags(oldManga, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }

    // F-H1: migrations run in the SCREEN MODEL scope (migrateFromDialog) - the dialog's
    // rememberCoroutineScope died with the composition, so leaving the screen mid-migration
    // cancelled the use case between its writes. The use case's critical section is
    // NonCancellable, so a dispose can now only abort the network phase cleanly. The pop-back
    // below must only fire while this dialog is still composed.
    var isComposed by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose { isComposed = false }
    }

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    flags.forEachIndexed { index, flag ->
                        LabeledCheckbox(
                            label = stringResource(flag.titleId),
                            checked = selectedFlags[index],
                            onCheckedChange = { selectedFlags[index] = it },
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onClickTitle()
                        },
                    ) {
                        Text(text = stringResource(AYMR.strings.action_show_manga))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            screenModel.migrateFromDialog(
                                oldManga = oldManga,
                                newManga = newManga,
                                replace = false,
                                flags = MangaMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                            ) {
                                if (isComposed) onPopScreen()
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.copy))
                    }
                    TextButton(
                        onClick = {
                            screenModel.migrateFromDialog(
                                oldManga = oldManga,
                                newManga = newManga,
                                replace = true,
                                flags = MangaMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                            ) {
                                if (isComposed) onPopScreen()
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.migrate))
                    }
                }
            },
        )
    }
}

internal class MigrateMangaDialogScreenModel(
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val migrateMangaUseCase: MigrateMangaUseCase = MigrateMangaUseCase(),
) : StateScreenModel<MigrateMangaDialogScreenModel.State>(State()) {

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    /**
     * F-H1: runs the migration in the screen model scope so a disposed dialog composition no
     * longer cancels the use case between its writes; [onMigrated] is invoked on the main thread
     * afterwards (the caller guards it against a disposed composition).
     * BMG-16: onMigrated now fires ONLY on success - a failed migration used to be swallowed and
     * the caller navigated to the new entry as if everything worked.
     */
    fun migrateFromDialog(
        oldManga: Manga,
        newManga: Manga,
        replace: Boolean,
        flags: Int,
        onMigrated: () -> Unit,
    ) {
        screenModelScope.launchIO {
            val succeeded = migrateManga(oldManga, newManga, replace, flags)
            if (succeeded) {
                withUIContext { onMigrated() }
            }
        }
    }

    suspend fun migrateManga(
        oldManga: Manga,
        newManga: Manga,
        replace: Boolean,
        flags: Int,
    ): Boolean {
        // F-M1: `flags` is the bitmap narrowed to THIS entry's applicable checkboxes (getFlags
        // hides notes/custom cover/delete-downloaded when the entry lacks them). Persisting it
        // verbatim silently cleared those bits in the stored defaults, so the next dialog lost
        // the custom-cover/notes preselection. Merge instead: stored bits of non-applicable
        // flags are kept. (The batch path avoids the problem by never persisting the narrowed
        // bitmap - MigrationListScreenModel.getMigrationFlags.)
        val storedFlags = migrateFlags.get()
        val applicableFlags = MangaMigrationFlags.getFlags(oldManga, storedFlags)
        val applicableMask = MangaMigrationFlags.getSelectedFlagsBitMap(
            selectedFlags = applicableFlags.map { true },
            flags = applicableFlags,
        )
        migrateFlags.set(mergeMigrationFlags(storedFlags, applicableMask, flags))
        mutableState.update { it.copy(isMigrating = true) }

        return try {
            migrateMangaUseCase.migrateManga(
                oldManga = oldManga,
                newManga = newManga,
                replace = replace,
                flags = flags,
            )
            true
        } catch (error: Throwable) {
            // NEW-6: rethrow cancellation - swallowing it kept isMigrating spinning when the
            // dialog's host was disposed mid-migration.
            if (error is CancellationException) throw error
            // BMG-16: log the failure (it used to vanish without a trace) and keep the dialog
            // open so the user sees that nothing happened.
            logcat(LogPriority.ERROR, error) { "Failed to migrate manga ${oldManga.id} -> ${newManga.id}" }
            mutableState.update { it.copy(isMigrating = false) }
            false
        }
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}

/**
 * Merges the dialog's narrowed selection back into the stored migration flags: bits of flags
 * that were not applicable to the migrated entry keep their stored value, applicable bits take
 * the user's selection, anything outside the known mask is ignored.
 */
internal fun mergeMigrationFlags(storedFlags: Int, applicableMask: Int, selectedFlags: Int): Int {
    return (storedFlags and applicableMask.inv()) or (selectedFlags and applicableMask)
}
