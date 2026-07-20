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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.entries.anime.interactor.MigrateAnimeUseCase
import eu.kanade.presentation.components.IndicatorSize
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.ui.browse.anime.migration.AnimeMigrationFlags
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsStateWithLifecycle()

    val flags = remember { AnimeMigrationFlags.getFlags(oldAnime, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }
    val canMigrate = remember { oldAnime.fetchType == newAnime.fetchType }

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
                                scope.launchIO {
                                    screenModel.migrateAnime(
                                        oldAnime,
                                        newAnime,
                                        false,
                                        AnimeMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                    )
                                    withUIContext { onPopScreen() }
                                }
                            },
                        ) {
                            Text(text = stringResource(MR.strings.copy))
                        }
                        TextButton(
                            onClick = {
                                scope.launchIO {
                                    screenModel.migrateAnime(
                                        oldAnime,
                                        newAnime,
                                        true,
                                        AnimeMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                    )

                                    withUIContext { onPopScreen() }
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

    suspend fun migrateAnime(
        oldAnime: Anime,
        newAnime: Anime,
        replace: Boolean,
        flags: Int,
    ) {
        migrateFlags.set(flags)

        mutableState.update { it.copy(isMigrating = true) }

        try {
            migrateAnimeUseCase.migrateAnime(
                oldAnime = oldAnime,
                newAnime = newAnime,
                replace = replace,
                flags = flags,
            )
        } catch (_: Throwable) {
            // Explicitly stop if an error occurred; the dialog normally gets popped at the end
            // anyway
            mutableState.update { it.copy(isMigrating = false) }
        }
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}
