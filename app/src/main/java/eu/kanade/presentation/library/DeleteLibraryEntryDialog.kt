package eu.kanade.presentation.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource

/** Media type of the entries being removed; drives the checkbox copy (H1). */
enum class DeleteLibraryEntryType { Manga, Anime, Novel }

@Composable
fun DeleteLibraryEntryDialog(
    containsLocalEntry: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (Boolean, Boolean) -> Unit,
    entryType: DeleteLibraryEntryType,
) {
    var list by remember {
        mutableStateOf(
            buildList<CheckboxState.State<StringResource>> {
                // H1: the binary isManga flag made the novel dialog show manga/anime copy.
                val checkbox1 = when (entryType) {
                    DeleteLibraryEntryType.Manga -> AYMR.strings.manga_from_library
                    DeleteLibraryEntryType.Anime -> AYMR.strings.anime_from_library
                    DeleteLibraryEntryType.Novel -> AYMR.strings.novel_from_library
                }
                add(CheckboxState.State.None(checkbox1))
                if (!containsLocalEntry) {
                    val checkbox2 = when (entryType) {
                        DeleteLibraryEntryType.Manga -> MR.strings.downloaded_chapters
                        DeleteLibraryEntryType.Anime -> AYMR.strings.downloaded_episodes
                        // Novel chapters are chapters; reuse the existing string instead of
                        // adding a duplicate "downloaded_novels" resource.
                        DeleteLibraryEntryType.Novel -> MR.strings.downloaded_chapters
                    }
                    add(CheckboxState.State.None(checkbox2))
                }
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                enabled = list.any { it.isChecked },
                onClick = {
                    onDismissRequest()
                    onConfirm(
                        list[0].isChecked,
                        list.getOrElse(1) { CheckboxState.State.None(0) }.isChecked,
                    )
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_remove))
        },
        text = {
            Column {
                list.forEach { state ->
                    LabeledCheckbox(
                        label = stringResource(state.value),
                        checked = state.isChecked,
                        onCheckedChange = {
                            val index = list.indexOf(state)
                            if (index != -1) {
                                val mutableList = list.toMutableList()
                                mutableList[index] = state.next() as CheckboxState.State<StringResource>
                                list = mutableList.toList()
                            }
                        },
                    )
                }
            }
        },
    )
}
