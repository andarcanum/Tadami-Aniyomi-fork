package eu.kanade.presentation.more.settings.screen.shikimori

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import tachiyomi.data.anixart.AnixartSourceHints
import tachiyomi.data.shikimori.ShikimoriImportStatus
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.util.Screen as ParentScreen

class ShikimoriImportScreen : ParentScreen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ShikimoriImportScreenModel() }
        val state by model.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(AYMR.strings.shikimori_import_title),
                    navigateUp = navigator::pop,
                )
            },
        ) { padding ->
            when (val s = state) {
                is ShikimoriImportScreenModel.State.Loading -> Centered(padding) { CircularProgressIndicator() }
                is ShikimoriImportScreenModel.State.Matching -> Centered(padding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(AYMR.strings.anixart_import_searching) + " ${s.current}/${s.total}",
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                is ShikimoriImportScreenModel.State.Error -> Centered(padding) {
                    Text(
                        stringResource(
                            when (s.messageKey) {
                                ShikimoriImportScreenModel.ErrorKind.NOT_LOGGED_IN ->
                                    AYMR.strings.shikimori_import_not_logged_in
                                ShikimoriImportScreenModel.ErrorKind.EMPTY ->
                                    AYMR.strings.shikimori_import_empty
                            },
                        ),
                    )
                }
                is ShikimoriImportScreenModel.State.PickSources -> PickSources(padding, s, model)
                is ShikimoriImportScreenModel.State.Review -> Review(padding, s, model)
                is ShikimoriImportScreenModel.State.Importing -> Centered(padding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(stringResource(AYMR.strings.anixart_import_importing) + " ${s.current}/${s.total}")
                    }
                }
                is ShikimoriImportScreenModel.State.Done -> Centered(padding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(AYMR.strings.anixart_import_done))
                        Text(
                            stringResource(
                                AYMR.strings.shikimori_import_report,
                                s.report.added,
                                s.report.alreadyInLibrary,
                                s.report.failed,
                                s.report.trackerBound,
                            ),
                        )
                        Button(onClick = navigator::pop) { Text("OK") }
                    }
                }
            }
        }
    }

    @Composable
    private fun PickSources(
        padding: PaddingValues,
        s: ShikimoriImportScreenModel.State.PickSources,
        model: ShikimoriImportScreenModel,
    ) {
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f)) {
                if (s.largeImport) {
                    item {
                        Text(
                            stringResource(AYMR.strings.anixart_import_warning_large, s.entries.size),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                item {
                    Text(
                        stringResource(AYMR.strings.anixart_import_status_filter_title),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(ShikimoriImportStatus.entries.toList()) { status ->
                    ListItem(
                        headlineContent = { Text(statusLabel(status)) },
                        leadingContent = {
                            Checkbox(
                                checked = status in s.statusFilter,
                                onCheckedChange = { model.toggleStatusFilter(status) },
                            )
                        },
                    )
                }
                item {
                    Text(
                        stringResource(AYMR.strings.anixart_import_select_sources),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(s.sources, key = { it.id }) { src ->
                    val warning = src.recommendation == AnixartSourceHints.Recommendation.WARNING
                    ListItem(
                        headlineContent = { Text(src.name) },
                        supportingContent = if (warning) {
                            {
                                Text(
                                    stringResource(AYMR.strings.anixart_import_source_warning),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else {
                            null
                        },
                        leadingContent = {
                            Checkbox(checked = src.selected, onCheckedChange = { model.toggleSource(src.id) })
                        },
                    )
                }
            }
            Button(
                onClick = model::startMatching,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                enabled = s.sources.any { it.selected },
            ) {
                Text(stringResource(AYMR.strings.anixart_import_searching))
            }
        }
    }

    @Composable
    private fun Review(
        padding: PaddingValues,
        s: ShikimoriImportScreenModel.State.Review,
        model: ShikimoriImportScreenModel,
    ) {
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(s.items) { index, item ->
                    ReviewItemRow(index, item, model)
                }
            }
            Button(
                onClick = model::startImport,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                enabled = model.selectedCount() > 0,
            ) {
                Text(stringResource(AYMR.strings.anixart_import_action_import, model.selectedCount()))
            }
        }
    }

    @Composable
    private fun ReviewItemRow(
        index: Int,
        item: ShikimoriImportScreenModel.ReviewItem,
        model: ShikimoriImportScreenModel,
    ) {
        var menuExpanded by remember { mutableStateOf(false) }
        val selected = item.result.ranked.firstOrNull { it.candidate.id == item.selectedId }?.candidate
        ListItem(
            headlineContent = {
                Text(
                    item.entry.russian ?: item.entry.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column {
                    Text(selected?.displayTitle ?: stringResource(AYMR.strings.anixart_import_group_nomatch))
                    item.matchedQuery?.let {
                        Text(
                            stringResource(AYMR.strings.anixart_import_matched_query, it),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            },
            leadingContent = {
                Checkbox(
                    checked = item.enabled && item.selectedId != null,
                    onCheckedChange = { model.setEnabled(index, it) },
                )
            },
            modifier = Modifier.clickable { menuExpanded = true },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            item.result.ranked.forEach { scored ->
                DropdownMenuItem(
                    text = { Text(scored.candidate.displayTitle) },
                    onClick = {
                        model.setSelection(index, scored.candidate.id)
                        menuExpanded = false
                    },
                )
            }
        }
    }

    @Composable
    private fun statusLabel(status: ShikimoriImportStatus): String = when (status) {
        ShikimoriImportStatus.WATCHING -> stringResource(AYMR.strings.anixart_import_status_watching)
        ShikimoriImportStatus.COMPLETED -> stringResource(AYMR.strings.anixart_import_status_completed)
        ShikimoriImportStatus.PLANNED -> stringResource(AYMR.strings.shikimori_import_status_planned)
        ShikimoriImportStatus.ON_HOLD -> stringResource(AYMR.strings.shikimori_import_status_on_hold)
        ShikimoriImportStatus.DROPPED -> stringResource(AYMR.strings.anixart_import_status_dropped)
        ShikimoriImportStatus.REWATCHING -> stringResource(AYMR.strings.shikimori_import_status_rewatching)
    }

    @Composable
    private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}
