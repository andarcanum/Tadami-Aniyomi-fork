package eu.kanade.presentation.more.settings.screen.data

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.sync.getCloudSyncOptions
import eu.kanade.tachiyomi.data.sync.setCloudSyncOptions
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CloudSyncOptionsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { CloudSyncOptionsScreenModel() }
        val state by model.state.collectAsStateWithLifecycle()
        val uiStyle = rememberResolvedSettingsUiStyle()
        val listState = rememberLazyListState()

        SettingsScaffold(
            title = stringResource(AYMR.strings.pref_cloud_sync_options),
            uiStyle = uiStyle,
            onBackPressed = navigator::pop,
            topBarCanScroll = { listState.canScroll() },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                state = listState,
                actionLabel = stringResource(AYMR.strings.aurora_nickname_apply),
                actionEnabled = state.options.canCreate(),
                onClickAction = {
                    navigator.pop()
                },
            ) {
                item {
                    SectionCard(MR.strings.label_library) {
                        Options(BackupOptions.libraryOptions, state, model)
                    }
                }

                item {
                    SectionCard(MR.strings.label_settings) {
                        Options(BackupOptions.settingsOptions, state, model)
                    }
                }

                item {
                    SectionCard(MR.strings.label_extensions) {
                        Options(BackupOptions.extensionOptions, state, model)
                    }
                }

                item {
                    SectionCard(AYMR.strings.achievements) {
                        Options(BackupOptions.achievementsOptions, state, model)
                    }
                }

                item {
                    SectionCard(MR.strings.label_backup) {
                        Options(BackupOptions.compatOptions, state, model)
                    }
                }
            }
        }
    }

    @Composable
    private fun Options(
        options: ImmutableList<BackupOptions.Entry>,
        state: CloudSyncOptionsScreenModel.State,
        model: CloudSyncOptionsScreenModel,
    ) {
        options.forEach { option ->
            LabeledCheckbox(
                label = stringResource(option.label),
                checked = option.getter(state.options),
                onCheckedChange = {
                    model.toggle(option.setter, it)
                },
                enabled = option.enabled(state.options),
            )
        }
    }
}

private class CloudSyncOptionsScreenModel(
    private val syncPreferences: SyncPreferences = Injekt.get(),
) : StateScreenModel<CloudSyncOptionsScreenModel.State>(
    State(options = syncPreferences.getCloudSyncOptions()),
) {

    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        mutableState.update {
            val newOptions = setter(it.options, enabled)
            syncPreferences.setCloudSyncOptions(newOptions)
            it.copy(options = newOptions)
        }
    }

    data class State(
        val options: BackupOptions,
    )
}
