package eu.kanade.presentation.browse.novel

import androidx.compose.runtime.Composable
import eu.kanade.presentation.browse.RepoPickerDialog
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun NovelRepoPickerDialog(
    pluginName: String,
    options: List<NovelPlugin.Available>,
    onSelectPlugin: (NovelPlugin.Available) -> Unit,
    onDismiss: () -> Unit,
) {
    RepoPickerDialog(
        titleRes = AYMR.strings.novel_repo_picker_title,
        newestContentDescriptionRes = AYMR.strings.novel_repo_picker_newest,
        itemName = pluginName,
        options = options,
        onSelectOption = onSelectPlugin,
        onDismiss = onDismiss,
        optionLabel = { plugin -> plugin.repoName.ifBlank { plugin.repoUrl } },
        optionVersionText = { plugin -> "v${plugin.version}" },
        comparator = compareBy<NovelPlugin.Available> { it.version },
    )
}
