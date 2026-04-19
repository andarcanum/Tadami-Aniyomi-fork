package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.screen.resolveSearchableSettingsBackPress
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.tachiyomi.ui.updates.pacing.LibraryUpdatePacingMediaType
import eu.kanade.tachiyomi.ui.updates.pacing.LibraryUpdatePacingScreenModel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.OutlinedNumericChooser
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

data object LibraryUpdatePacingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val handleBack = LocalBackPress.current
        val uiStyle = rememberResolvedSettingsUiStyle()
        val screenModel = rememberScreenModel { LibraryUpdatePacingScreenModel() }
        val state by screenModel.state.collectAsState()
        val listState = rememberLazyListState()
        val title = stringResource(MR.strings.label_library_update_pacing)

        SettingsScaffold(
            title = title,
            uiStyle = uiStyle,
            onBackPressed = resolveSearchableSettingsBackPress(
                handleBack = handleBack,
                navigatorPop = navigator::pop,
            ),
            topBarCanScroll = { listState.canScroll() },
        ) { contentPadding ->
            LibraryUpdatePacingContent(
                state = state,
                contentPadding = contentPadding,
                listState = listState,
                onTimeoutChanged = screenModel::setTimeoutSeconds,
                onSearchQueryChanged = screenModel::onSearchQueryChanged,
                onToggleSource = screenModel::toggleSourceSelection,
            )
        }
    }
}

@Composable
private fun LibraryUpdatePacingContent(
    state: eu.kanade.tachiyomi.ui.updates.pacing.LibraryUpdatePacingState,
    contentPadding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onTimeoutChanged: (Int) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSource: (LibraryUpdatePacingMediaType, Long) -> Unit,
) {
    val colors = AuroraTheme.colors

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SettingsItemsPaddings.Horizontal,
                        vertical = SettingsItemsPaddings.Vertical,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.action_timeout_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(MR.strings.label_library_update_pacing_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                OutlinedNumericChooser(
                    label = stringResource(MR.strings.label_library_update_pacing_timeout),
                    placeholder = "0",
                    suffix = "s",
                    value = state.timeoutSeconds,
                    step = 1,
                    min = 0,
                    onValueChanged = onTimeoutChanged,
                )
            }
        }

        item {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SettingsItemsPaddings.Horizontal,
                        vertical = SettingsItemsPaddings.Vertical,
                    ),
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                label = { Text(text = stringResource(MR.strings.label_library_update_pacing_search)) },
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                singleLine = true,
            )
        }

        item {
            Text(
                text = stringResource(
                    MR.strings.label_library_update_pacing_selected,
                    state.selectedSourceCount,
                    state.totalSourceCount,
                ),
                modifier = Modifier.padding(
                    horizontal = SettingsItemsPaddings.Horizontal,
                    vertical = 2.dp,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }

        if (state.filteredSources.isEmpty()) {
            item {
                Text(
                    text = if (state.searchQuery.isBlank()) {
                        stringResource(MR.strings.label_library_update_pacing_empty)
                    } else {
                        stringResource(MR.strings.label_library_update_pacing_empty_search)
                    },
                    modifier = Modifier.padding(
                        horizontal = SettingsItemsPaddings.Horizontal,
                        vertical = 12.dp,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        } else {
            LibraryUpdatePacingMediaType.entries.forEach { mediaType ->
                val sources = state.filteredSources.filter { it.mediaType == mediaType }
                if (sources.isNotEmpty()) {
                    item {
                        HeadingItem(
                            text = when (mediaType) {
                                LibraryUpdatePacingMediaType.ANIME -> stringResource(AYMR.strings.label_anime)
                                LibraryUpdatePacingMediaType.MANGA -> stringResource(AYMR.strings.label_manga)
                                LibraryUpdatePacingMediaType.NOVEL -> stringResource(AYMR.strings.label_novel)
                            },
                        )
                    }
                    items(
                        items = sources,
                        key = { it.sourceKey },
                    ) { item ->
                        SourceRow(
                            item = item,
                            onToggle = {
                                onToggleSource(item.mediaType, item.sourceId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    item: eu.kanade.tachiyomi.ui.updates.pacing.LibraryUpdatePacingSourceItem,
    onToggle: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                appHaptics.tap()
                onToggle()
            }
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.selected,
            onCheckedChange = null,
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (item.mediaType) {
                    LibraryUpdatePacingMediaType.ANIME -> "Anime"
                    LibraryUpdatePacingMediaType.MANGA -> "Manga"
                    LibraryUpdatePacingMediaType.NOVEL -> "Novel"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
