package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reels.ReelsDurationFilter
import eu.kanade.tachiyomi.ui.reels.ReelsLocalFilterState
import eu.kanade.tachiyomi.ui.reels.ReelsVideoSort
import eu.kanade.tachiyomi.ui.reels.ReelsWatchFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableSet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Bottom sheet for client-side Reels filtering & sorting (duration, newest/sort, tags/categories,
 * optional watch-status filter), plus an entry point to source-defined server filters when the
 * plugin implements `AnimeCategoryFeedOrderSource`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReelsLocalFilterSheet(
    currentFilter: ReelsLocalFilterState,
    availableTags: ImmutableList<String> = persistentListOf(),
    showWatchFilter: Boolean = false,
    showSourceFiltersAction: Boolean = false,
    onOpenSourceFilters: () -> Unit = {},
    onApply: (ReelsLocalFilterState) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(currentFilter) { mutableStateOf(currentFilter) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(MR.strings.reels_filter_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (draft.isActive || currentFilter.isActive) {
                    TextButton(
                        onClick = {
                            draft = ReelsLocalFilterState()
                            onReset()
                        },
                    ) {
                        Text(stringResource(MR.strings.action_reset))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showSourceFiltersAction) {
                    OutlinedButton(
                        onClick = onOpenSourceFilters,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(MR.strings.reels_filter_source_filters))
                    }
                }

                // Sort section
                FilterSectionHeader(stringResource(MR.strings.reels_filter_section_sort))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val sortOptions = listOf(
                        ReelsVideoSort.Default to stringResource(MR.strings.reels_filter_sort_default),
                        ReelsVideoSort.Newest to stringResource(MR.strings.reels_filter_sort_newest),
                        ReelsVideoSort.Longest to stringResource(MR.strings.reels_filter_sort_longest),
                        ReelsVideoSort.Shortest to stringResource(MR.strings.reels_filter_sort_shortest),
                        ReelsVideoSort.Popular to stringResource(MR.strings.reels_filter_sort_popular),
                    )
                    sortOptions.forEach { (option, label) ->
                        FilterChip(
                            selected = draft.sort == option,
                            onClick = { draft = draft.copy(sort = option) },
                            label = { Text(label) },
                        )
                    }
                }

                // Duration section
                FilterSectionHeader(stringResource(MR.strings.reels_filter_section_duration))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val durationOptions = listOf(
                        ReelsDurationFilter.All to stringResource(MR.strings.reels_filter_duration_all),
                        ReelsDurationFilter.Short to stringResource(MR.strings.reels_filter_duration_short),
                        ReelsDurationFilter.Medium to stringResource(MR.strings.reels_filter_duration_medium),
                        ReelsDurationFilter.Long to stringResource(MR.strings.reels_filter_duration_long),
                    )
                    durationOptions.forEach { (option, label) ->
                        FilterChip(
                            selected = draft.duration == option,
                            onClick = { draft = draft.copy(duration = option) },
                            label = { Text(label) },
                        )
                    }
                }

                // Watch status section (album grid)
                if (showWatchFilter) {
                    FilterSectionHeader(stringResource(MR.strings.reels_filter_section_watch))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val watchOptions = listOf(
                            ReelsWatchFilter.All to stringResource(MR.strings.reels_filter_watch_all),
                            ReelsWatchFilter.Unwatched to stringResource(MR.strings.reels_filter_watch_unwatched),
                            ReelsWatchFilter.Watched to stringResource(MR.strings.reels_filter_watch_watched),
                        )
                        watchOptions.forEach { (option, label) ->
                            FilterChip(
                                selected = draft.watchFilter == option,
                                onClick = { draft = draft.copy(watchFilter = option) },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                // Tags / categories section
                if (availableTags.isNotEmpty()) {
                    FilterSectionHeader(stringResource(MR.strings.reels_filter_section_tags))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        availableTags.forEach { tag ->
                            val selected = draft.selectedTags.any { it.equals(tag, ignoreCase = true) }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val next = if (selected) {
                                        draft.selectedTags.filterNot { it.equals(tag, ignoreCase = true) }
                                    } else {
                                        draft.selectedTags + tag
                                    }
                                    draft = draft.copy(selectedTags = next.toImmutableSet())
                                },
                                label = { Text("#$tag") },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(MR.strings.reels_filter_apply))
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
