package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import eu.kanade.presentation.components.AuroraRadioItem
import eu.kanade.presentation.components.AuroraSwitchItem
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun ColumnScope.GroupPage(
    groupPreference: Preference<Int>,
    globalGroupPreference: Preference<Boolean>,
    globalGroupByPreference: Preference<Int>,
) {
    val isGlobal by globalGroupPreference.collectAsState()

    AuroraSwitchItem(
        label = stringResource(MR.strings.pref_library_group_apply_to_all),
        pref = globalGroupPreference,
    )

    if (isGlobal) {
        val globalGroupType by globalGroupByPreference.collectAsState()
        AuroraRadioItem(
            label = stringResource(MR.strings.label_default),
            selected = globalGroupType == LibraryGroup.BY_DEFAULT,
            onClick = { globalGroupByPreference.set(LibraryGroup.BY_DEFAULT) },
        )
        AuroraRadioItem(
            label = stringResource(MR.strings.action_group_by_source),
            selected = globalGroupType == LibraryGroup.BY_SOURCE,
            onClick = { globalGroupByPreference.set(LibraryGroup.BY_SOURCE) },
        )
        AuroraRadioItem(
            label = stringResource(MR.strings.action_group_by_status),
            selected = globalGroupType == LibraryGroup.BY_STATUS,
            onClick = { globalGroupByPreference.set(LibraryGroup.BY_STATUS) },
        )
        AuroraRadioItem(
            label = stringResource(MR.strings.action_group_by_track_status),
            selected = globalGroupType == LibraryGroup.BY_TRACK_STATUS,
            onClick = { globalGroupByPreference.set(LibraryGroup.BY_TRACK_STATUS) },
        )
        AuroraRadioItem(
            label = stringResource(MR.strings.action_group_ungrouped),
            selected = globalGroupType == LibraryGroup.UNGROUPED,
            onClick = { globalGroupByPreference.set(LibraryGroup.UNGROUPED) },
        )
    } else {
        val groupType by groupPreference.collectAsState()
        AuroraRadioItem(
            label = stringResource(MR.strings.label_default),
            selected = groupType == LibraryGroup.BY_DEFAULT,
            onClick = { groupPreference.set(LibraryGroup.BY_DEFAULT) },
        )
        AuroraRadioItem(
            label = stringResource(MR.strings.action_group_by_source),
            selected = groupType == LibraryGroup.BY_SOURCE,
            onClick = { groupPreference.set(LibraryGroup.BY_SOURCE) },
        )
        AuroraRadioItem(
            label = stringResource(MR.strings.action_group_by_status),
            selected = groupType == LibraryGroup.BY_STATUS,
            onClick = { groupPreference.set(LibraryGroup.BY_STATUS) },
        )
        AuroraRadioItem(
            label = stringResource(MR.strings.action_group_by_track_status),
            selected = groupType == LibraryGroup.BY_TRACK_STATUS,
            onClick = { groupPreference.set(LibraryGroup.BY_TRACK_STATUS) },
        )
        AuroraRadioItem(
            label = stringResource(MR.strings.action_group_ungrouped),
            selected = groupType == LibraryGroup.UNGROUPED,
            onClick = { groupPreference.set(LibraryGroup.UNGROUPED) },
        )
    }
}
