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

internal const val ALL_SCANLATOR_BRANCH_KEY = "__all__"

internal data class ScanlatorBranchSelectorItem(
    val key: String,
    val chapterCount: Int?,
)

internal fun resolveScanlatorBranchSelectorItems(
    scanlatorChapterCounts: Map<String, Int>,
    showAllOption: Boolean,
): List<ScanlatorBranchSelectorItem> {
    val sortedItems = scanlatorChapterCounts.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.key },
        )
        .map { entry ->
            ScanlatorBranchSelectorItem(
                key = entry.key,
                chapterCount = entry.value,
            )
        }
    if (sortedItems.size < 2) return emptyList()
    return buildList {
        if (showAllOption) {
            add(
                ScanlatorBranchSelectorItem(
                    key = ALL_SCANLATOR_BRANCH_KEY,
                    chapterCount = null,
                ),
            )
        }
        addAll(sortedItems)
    }
}

@Composable
fun ScanlatorBranchSelector(
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    showAllOption: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val selectorItems = remember(scanlatorChapterCounts, showAllOption) {
        resolveScanlatorBranchSelectorItems(
            scanlatorChapterCounts = scanlatorChapterCounts,
            showAllOption = showAllOption,
        )
    }
    if (selectorItems.isEmpty()) return

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
            items(
                items = selectorItems,
                key = { it.key },
            ) { item ->
                val isAllOption = item.key == ALL_SCANLATOR_BRANCH_KEY
                FilterChip(
                    selected = if (isAllOption) {
                        selectedScanlator == null
                    } else {
                        selectedScanlator == item.key
                    },
                    onClick = {
                        onScanlatorSelected(
                            if (isAllOption) {
                                null
                            } else {
                                item.key
                            },
                        )
                    },
                    label = {
                        Text(
                            text = if (isAllOption) {
                                stringResource(MR.strings.all)
                            } else {
                                val chapterCount = item.chapterCount ?: 0
                                val chapterCountText = pluralStringResource(
                                    MR.plurals.manga_num_chapters,
                                    chapterCount,
                                    chapterCount,
                                )
                                "${item.key} - $chapterCountText"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}
