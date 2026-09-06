package eu.kanade.tachiyomi.ui.browse.novel.migration.search

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
import eu.kanade.domain.entries.novel.interactor.MigrateNovelUseCase
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.mergeMigrationFlags
import eu.kanade.tachiyomi.ui.browse.novel.migration.NovelMigrationFlags
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun MigrateNovelDialog(
    oldNovel: Novel,
    newNovel: Novel,
    screenModel: MigrateNovelDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val state by screenModel.state.collectAsStateWithLifecycle()

    val flags = remember { NovelMigrationFlags.getFlags(oldNovel, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }

    // BMG-3 (F-H1 port): migrations run in the SCREEN MODEL scope (migrateFromDialog) - the
    // dialog's rememberCoroutineScope died with the composition, so leaving the screen
    // mid-migration cancelled the use case between its writes. The use case's critical section
    // is NonCancellable; the pop-back must only fire while this dialog is still composed.
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
                        Text(text = stringResource(MR.strings.action_show_manga))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            screenModel.migrateFromDialog(
                                oldNovel = oldNovel,
                                newNovel = newNovel,
                                replace = false,
                                flags = NovelMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
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
                                oldNovel = oldNovel,
                                newNovel = newNovel,
                                replace = true,
                                flags = NovelMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
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

internal class MigrateNovelDialogScreenModel(
    private val migrateNovelUseCase: MigrateNovelUseCase = MigrateNovelUseCase(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrateNovelDialogScreenModel.State>(State()) {

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags_novel", Int.MAX_VALUE)
    }

    init {
        // BMG-2/РЕШ-B8: one-time reset of bits polluted by the old config sheet (single-entry
        // path may run before the sheet was ever opened).
        NovelMigrationFlags.ensureBitCollisionReset(preferenceStore)
    }

    /**
     * BMG-3: runs the migration in the screen model scope so a disposed dialog composition no
     * longer cancels the use case between its writes; [onMigrated] is invoked on the main thread
     * ONLY on success (BMG-16: the caller used to navigate away as if it succeeded).
     */
    fun migrateFromDialog(
        oldNovel: Novel,
        newNovel: Novel,
        replace: Boolean,
        flags: Int,
        onMigrated: () -> Unit,
    ) {
        screenModelScope.launchIO {
            val succeeded = migrateNovel(oldNovel, newNovel, replace, flags)
            if (succeeded) {
                withUIContext { onMigrated() }
            }
        }
    }

    suspend fun migrateNovel(
        oldNovel: Novel,
        newNovel: Novel,
        replace: Boolean,
        flags: Int,
    ): Boolean {
        // BMG-4 (F-M1 port): `flags` is narrowed to THIS entry's applicable checkboxes; writing
        // it back verbatim permanently dropped the non-applicable bits (notes, custom cover,
        // delete-downloaded) from the stored defaults. Merge instead (manga etalon).
        val storedFlags = migrateFlags.get()
        val applicableFlags = NovelMigrationFlags.getFlags(oldNovel, storedFlags)
        val applicableMask = NovelMigrationFlags.getSelectedFlagsBitMap(
            selectedFlags = applicableFlags.map { true },
            flags = applicableFlags,
        )
        migrateFlags.set(mergeMigrationFlags(storedFlags, applicableMask, flags))

        mutableState.update { it.copy(isMigrating = true) }

        return try {
            migrateNovelUseCase.migrateNovel(
                oldNovel = oldNovel,
                newNovel = newNovel,
                replace = replace,
                flags = flags,
            )
            true
        } catch (error: Throwable) {
            // NEW-6 port: rethrow cancellation instead of swallowing it.
            if (error is CancellationException) throw error
            // BMG-16: the failure used to be swallowed with zero diagnostics and the caller
            // navigated away as if the migration succeeded. Log it and keep the dialog open.
            logcat(LogPriority.ERROR, error) { "Failed to migrate novel ${oldNovel.id} -> ${newNovel.id}" }
            mutableState.update { it.copy(isMigrating = false) }
            false
        }
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}
