package eu.kanade.presentation.more.settings.screen.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.collections.immutable.ImmutableSet
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Discord

@Composable
fun ExtensionStoreContent(
    repos: ImmutableSet<ExtensionRepo>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onOpenWebsite: (ExtensionRepo) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickRename: (ExtensionRepo) -> Unit,
    modifier: Modifier = Modifier,
) {
    // BEXT-10/РЕШ-B5: the "official stores" section was dead - the only officialRepos value
    // ever passed was an EMPTY map (OFFICIAL_ANIME_REPOS = emptyMap(); manga/novel used the
    // default), so OfficialRepoUi/OfficialRepoListItem and the label_official_stores header
    // were unreachable. The onAddRepo parameter existed solely for the official toggles.
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = modifier,
    ) {
        if (repos.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(MR.strings.label_custom_stores),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                )
            }

            repos.forEach {
                item {
                    ExtensionRepoListItem(
                        modifier = Modifier.animateItem(),
                        repo = it,
                        onOpenWebsite = { onOpenWebsite(it) },
                        onRename = { onClickRename(it) },
                        onDelete = { onClickDelete(it.baseUrl) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionRepoListItem(
    repo: ExtensionRepo,
    onOpenWebsite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.Label, contentDescription = null)
            Text(
                text = repo.name,
                modifier = Modifier.padding(start = MaterialTheme.padding.medium),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onOpenWebsite) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(MR.strings.action_open_in_browser),
                )
            }

            val discordUrl = repo.discord
            if (!discordUrl.isNullOrBlank()) {
                IconButton(onClick = { context.openInBrowser(discordUrl) }) {
                    Icon(
                        imageVector = CustomIcons.Discord,
                        contentDescription = stringResource(MR.strings.action_open_discord),
                    )
                }
            }

            IconButton(
                onClick = {
                    // Novel plugin repos are indexed by plugins.min.json and store indexes by their
                    // own url, so a fabricated index.min.json would 404 when pasted back.
                    val url = repo.indexUrl ?: "${repo.baseUrl}/index.min.json"
                    context.copyToClipboard(url, url)
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(MR.strings.action_copy_to_clipboard),
                )
            }

            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(MR.strings.action_rename_store),
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                )
            }
        }
    }
}
