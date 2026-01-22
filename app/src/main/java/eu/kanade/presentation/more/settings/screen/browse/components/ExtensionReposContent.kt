package eu.kanade.presentation.more.settings.screen.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ExtensionReposContent(
    repos: ImmutableSet<ExtensionRepo>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onOpenWebsite: (ExtensionRepo) -> Unit,
    onClickDelete: (String) -> Unit,
    onAddRepo: (String) -> Unit,
    officialRepos: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val officialReposState = officialRepos.entries.map { (url, name) ->
        val baseUrl = url.removeSuffix("/index.min.json")
        OfficialRepoUi(
            name = name,
            indexUrl = url,
            baseUrl = baseUrl,
            isEnabled = repos.any { it.baseUrl == baseUrl },
        )
    }
    val officialBaseUrls = officialReposState.map { it.baseUrl }.toSet()
    val customRepos = repos.filter { it.baseUrl !in officialBaseUrls }.toImmutableSet()

    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = modifier,
    ) {
        if (officialReposState.isNotEmpty()) {
            item {
                Text(
                    text = "Official Repositories",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                )
            }

            officialReposState.forEach { repo ->
                item {
                    OfficialRepoListItem(
                        name = repo.name,
                        url = repo.indexUrl,
                        isEnabled = repo.isEnabled,
                        onToggle = { if (it) onAddRepo(repo.indexUrl) else onClickDelete(repo.baseUrl) },
                    )
                }
            }
        }

        if (customRepos.isNotEmpty()) {
            item {
                if (officialReposState.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "Custom Repositories",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                )
            }

            customRepos.forEach {
                item {
                    ExtensionRepoListItem(
                        modifier = Modifier.animateItem(),
                        repo = it,
                        onOpenWebsite = { onOpenWebsite(it) },
                        onDelete = { onClickDelete(it.baseUrl) },
                    )
                }
            }
        }
    }
}

private data class OfficialRepoUi(
    val name: String,
    val indexUrl: String,
    val baseUrl: String,
    val isEnabled: Boolean,
)

@Composable
private fun OfficialRepoListItem(
    name: String,
    url: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.Label, contentDescription = null)
                Text(
                    text = name,
                    modifier = Modifier.padding(start = MaterialTheme.padding.medium),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun ExtensionRepoListItem(
    repo: ExtensionRepo,
    onOpenWebsite: () -> Unit,
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

            IconButton(
                onClick = {
                    val url = "${repo.baseUrl}/index.min.json"
                    context.copyToClipboard(url, url)
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(MR.strings.action_copy_to_clipboard),
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
