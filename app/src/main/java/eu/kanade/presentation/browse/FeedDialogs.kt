package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.browse.feed.BaseFeedScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.TreeMap

/** One selectable listing (latest/popular or a saved search) in the feed add dialog. */
data class FeedSelectionOption(
    val label: String,
    val listingType: FeedListingType,
    val savedSearch: SavedSearch? = null,
)

fun buildFeedSelectionOptions(
    sourceSupportsLatest: Boolean,
    savedSearches: List<SavedSearch>,
    latestLabel: String,
    popularLabel: String,
): List<FeedSelectionOption> = buildList {
    if (sourceSupportsLatest) {
        add(FeedSelectionOption(label = latestLabel, listingType = FeedListingType.LATEST))
    }
    add(FeedSelectionOption(label = popularLabel, listingType = FeedListingType.POPULAR))
    savedSearches.forEach { search ->
        add(
            FeedSelectionOption(
                label = search.name,
                listingType = FeedListingType.SAVED_SEARCH,
                savedSearch = search,
            ),
        )
    }
}

@Composable
fun FeedAddSourceDialog(
    sources: List<BaseFeedScreenModel.FeedSourceCandidate>,
    onDismiss: () -> Unit,
    onAdd: (BaseFeedScreenModel.FeedSourceCandidate) -> Unit,
) {
    val grouped = remember(sources) {
        TreeMap<String, MutableList<BaseFeedScreenModel.FeedSourceCandidate>>().apply {
            sources.forEach { source ->
                val langName = LocaleHelper.getLocalizedDisplayName(source.lang)
                getOrPut(langName) { mutableListOf() }.add(source)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.feed_add)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                grouped.forEach { (lang, langSources) ->
                    item(key = "header_$lang") {
                        Text(
                            text = lang,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    items(langSources, key = { it.id }) { source ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAdd(source) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = source.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = LocaleHelper.getLocalizedDisplayName(source.lang),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
fun FeedAddSearchDialog(
    source: BaseFeedScreenModel.FeedSourceCandidate,
    savedSearches: List<SavedSearch>,
    onDismiss: () -> Unit,
    onAdd: (FeedListingType, SavedSearch?) -> Unit,
) {
    val latestLabel = stringResource(AYMR.strings.feed_latest)
    val popularLabel = stringResource(AYMR.strings.feed_popular)

    val options = remember(savedSearches, source.supportsLatest, latestLabel, popularLabel) {
        buildFeedSelectionOptions(
            sourceSupportsLatest = source.supportsLatest,
            savedSearches = savedSearches,
            latestLabel = latestLabel,
            popularLabel = popularLabel,
        )
    }
    var selected by remember { mutableStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(source.name) },
        text = {
            LazyColumn {
                items(options.size) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = index }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == index, onClick = { selected = index })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(options[index].label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selected >= 0) {
                        val option = options[selected]
                        onAdd(option.listingType, option.savedSearch)
                    }
                },
                enabled = selected >= 0,
            ) {
                Text(stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
fun FeedDeleteSourceDialog(
    source: BaseFeedScreenModel.FeedSourceCandidate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.feed_delete_source_title)) },
        text = { Text(stringResource(AYMR.strings.feed_delete_source_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
