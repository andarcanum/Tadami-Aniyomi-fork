package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.MigrateAnimeUseCase
import eu.kanade.presentation.components.IndicatorSize
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.ui.browse.anime.migration.AnimeMigrationFlags
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.mergeMigrationFlags
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun MigrateAnimeDialog(
    oldAnime: Anime,
    newAnime: Anime,
    screenModel: MigrateAnimeDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onClickSeasons: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val state by screenModel.state.collectAsStateWithLifecycle()

    val flags = remember { AnimeMigrationFlags.getFlags(oldAnime, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }
    val canMigrate = remember { oldAnime.fetchType == newAnime.fetchType }

    // BMG-3 (F-H1 port): migrations run in the SCREEN MODEL scope (migrateFromDialog) - the
    // dialog's rememberCoroutineScope died with the composition, so leaving the screen
    // mid-migration cancelled the use case between its writes (both entries could stay in the
    // library). The use case's critical section is NonCancellable; the pop-back must only fire
    // while this dialog is still composed. Manga etalon: MigrateMangaDialog (M6).
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
                    if (canMigrate) {
                        flags.forEachIndexed { index, flag ->
                            LabeledCheckbox(
                                label = stringResource(flag.titleId),
                                checked = selectedFlags[index],
                                onCheckedChange = { selectedFlags[index] = it },
                            )
                        }
                    } else {
                        val message = if (oldAnime.fetchType == FetchType.Seasons) {
                            AYMR.strings.label_cant_migrate_season
                        } else {
                            AYMR.strings.label_cant_migrate_episode
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(IndicatorSize),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
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
                        Text(text = stringResource(AYMR.strings.action_show_anime))
                    }

                    if (newAnime.fetchType != FetchType.Episodes) {
                        TextButton(
                            onClick = {
                                onDismissRequest()
                                onClickSeasons()
                            },
                        ) {
                            Text(text = stringResource(AYMR.strings.label_show_seasons))
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (canMigrate) {
                        TextButton(
                            onClick = {
                                screenModel.migrateFromDialog(
                                    oldAnime = oldAnime,
                                    newAnime = newAnime,
                                    replace = false,
                                    flags = AnimeMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
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
                                    oldAnime = oldAnime,
                                    newAnime = newAnime,
                                    replace = true,
                                    flags = AnimeMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                ) {
                                    if (isComposed) onPopScreen()
                                }
                            },
                        ) {
                            Text(text = stringResource(MR.strings.migrate))
                        }
                    }
                }
            },
        )
    }
}

internal class MigrateAnimeDialogScreenModel(
    private val migrateAnimeUseCase: MigrateAnimeUseCase = MigrateAnimeUseCase(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrateAnimeDialogScreenModel.State>(State()) {

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags_anime", Int.MAX_VALUE)
    }

    /**
     * BMG-3: runs the migration in the screen model scope so a disposed dialog composition no
     * longer cancels the use case between its writes; [onMigrated] is invoked on the main thread
     * ONLY on success (BMG-16: the caller used to navigate away as if the migration succeeded
     * even when it threw).
     */
    fun migrateFromDialog(
        oldAnime: Anime,
        newAnime: Anime,
        replace: Boolean,
        flags: Int,
        onMigrated: () -> Unit,
    ) {
        screenModelScope.launchIO {
            val succeeded = migrateAnime(oldAnime, newAnime, replace, flags)
            if (succeeded) {
                withUIContext { onMigrated() }
            }
        }
    }

    suspend fun migrateAnime(
        oldAnime: Anime,
        newAnime: Anime,
        replace: Boolean,
        flags: Int,
    ): Boolean {
        // BMG-4 (F-M1 port): `flags` is narrowed to THIS entry's applicable checkboxes; writing
        // it back verbatim permanently dropped the non-applicable bits (custom background,
        // delete-downloaded) from the stored defaults. Merge instead (manga etalon).
        val storedFlags = migrateFlags.get()
        val applicableFlags = AnimeMigrationFlags.getFlags(oldAnime, storedFlags)
        val applicableMask = AnimeMigrationFlags.getSelectedFlagsBitMap(
            selectedFlags = applicableFlags.map { true },
            flags = applicableFlags,
        )
        migrateFlags.set(mergeMigrationFlags(storedFlags, applicableMask, flags))

        mutableState.update { it.copy(isMigrating = true) }

        return try {
            migrateAnimeUseCase.migrateAnime(
                oldAnime = oldAnime,
                newAnime = newAnime,
                replace = replace,
                flags = flags,
            )
            true
        } catch (error: Throwable) {
            // NEW-6 port: rethrow cancellation - swallowing it kept isMigrating spinning when
            // the host was disposed mid-migration.
            if (error is CancellationException) throw error
            // BMG-16: the failure used to be swallowed with zero diagnostics and the caller
            // navigated away as if the migration succeeded. Log it and keep the dialog open
            // (isMigrating resets) so the user sees that nothing happened.
            logcat(LogPriority.ERROR, error) { "Failed to migrate anime ${oldAnime.id} -> ${newAnime.id}" }
            mutableState.update { it.copy(isMigrating = false) }
            false
        }
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}
