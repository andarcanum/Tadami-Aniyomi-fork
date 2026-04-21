package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsEInkScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_e_ink_profile

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val readerPreferences = remember { Injekt.get<ReaderPreferences>() }
        val novelReaderPreferences = remember { Injekt.get<NovelReaderPreferences>() }

        val eInkProfilePref = uiPreferences.eInkProfile()
        val eInkAutoOptimizationPref = uiPreferences.eInkAutoOptimization()
        val eInkProfile by eInkProfilePref.collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_e_ink_profile),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = eInkProfilePref,
                        entries = EInkProfile.entries
                            .associateWith { stringResource(it.titleRes) }
                            .toImmutableMap(),
                        title = stringResource(AYMR.strings.pref_e_ink_profile),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = eInkAutoOptimizationPref,
                        title = stringResource(AYMR.strings.pref_e_ink_auto_optimization),
                        subtitle = stringResource(AYMR.strings.pref_e_ink_auto_optimization_summary),
                        enabled = eInkProfile.isEnabled,
                        onValueChanged = { enabled ->
                            if (enabled) {
                                seedEInkAutoOptimizationDefaults(
                                    eInkProfile = eInkProfile,
                                    readerPreferences = readerPreferences,
                                    novelReaderPreferences = novelReaderPreferences,
                                )
                                context.toast(AYMR.strings.pref_e_ink_auto_optimization_applied)
                            }
                            true
                        },
                    ),
                ),
            ),
        )
    }
}
