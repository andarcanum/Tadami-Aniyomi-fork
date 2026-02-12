package eu.kanade.presentation.entries.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ScanlatorBranchSelector(
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedEntries = remember(scanlatorChapterCounts) {
        scanlatorChapterCounts.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.key },
        )
    }
    if (sortedEntries.size < 2) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(MR.strings.scanlator),
            style = MaterialTheme.typography.titleSmall,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all_scanlators") {
                FilterChip(
                    selected = selectedScanlator == null,
                    onClick = { onScanlatorSelected(null) },
                    label = {
                        Text(text = stringResource(MR.strings.all))
                    },
                )
            }

            items(
                items = sortedEntries,
                key = { it.key },
            ) { entry ->
                val chapterCountText = pluralStringResource(
                    MR.plurals.manga_num_chapters,
                    entry.value,
                    entry.value,
                )
                FilterChip(
                    selected = selectedScanlator == entry.key,
                    onClick = { onScanlatorSelected(entry.key) },
                    label = {
                        Text(
                            text = "${entry.key} - $chapterCountText",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}
