package eu.kanade.presentation.more.settings.screen.anixart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartSourceHints
import tachiyomi.data.anixart.AnixartStatus
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.util.Screen as ParentScreen

/**
 * The Anixart import wizard. A [java.io.InputStream] provider is passed in from
 * the settings entry point (which owns the SAF file picker), so this screen is
 * agnostic of how the file was chosen.
 */
class AnixartImportScreen(
    private val openStream: () -> java.io.InputStream,
) : ParentScreen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { AnixartImportScreenModel(openStream) }
        val state by model.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(AYMR.strings.anixart_import_title),
                    navigateUp = navigator::pop,
                )
            },
        ) { padding ->
            when (val s = state) {
                is AnixartImportScreenModel.State.Loading -> Centered(padding) { CircularProgressIndicator() }
                is AnixartImportScreenModel.State.Matching -> Centered(padding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(AYMR.strings.anixart_import_searching) +
                                " ${s.current}/${s.total}",
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }

                is AnixartImportScreenModel.State.Error -> Centered(padding) {
                    Text(
                        stringResource(
                            when (s.messageKey) {
                                AnixartImportScreenModel.ErrorKind.INVALID -> AYMR.strings.anixart_import_error_invalid
                                AnixartImportScreenModel.ErrorKind.EMPTY -> AYMR.strings.anixart_import_empty
                            },
                        ),
                    )
                }

                is AnixartImportScreenModel.State.PickSources -> PickSources(padding, s, model)
                is AnixartImportScreenModel.State.Review -> Review(padding, s, model)
                is AnixartImportScreenModel.State.Importing -> Centered(padding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(AYMR.strings.anixart_import_importing) +
                                " ${s.current}/${s.total}",
                        )
                    }
                }
                is AnixartImportScreenModel.State.Done -> Centered(padding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(AYMR.strings.anixart_import_done))
                        if (s.backgroundJob) {
                            Text(
                                stringResource(AYMR.strings.anixart_import_background_started),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            Text(
                                stringResource(
                                    AYMR.strings.anixart_import_report,
                                    s.report.added,
                                    s.report.alreadyInLibrary,
                                    s.report.failed,
                                ),
                            )
                            Text(
                                stringResource(
                                    AYMR.strings.anixart_import_matching_report,
                                    s.matchingReport.auto,
                                    s.matchingReport.needsReview,
                                    s.matchingReport.noMatch,
                                ),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            s.trackerReport?.let { tracker ->
                                Text(
                                    stringResource(
                                        AYMR.strings.anixart_import_tracker_report,
                                        tracker.synced,
                                        tracker.skipped,
                                        tracker.failed,
                                    ),
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        Button(onClick = navigator::pop) { Text("OK") }
                    }
                }
            }
        }
    }

    @Composable
    private fun CategorySpinner(
        label: String,
        selectedCategoryName: String,
        categories: List<Category>,
        onCategorySelected: (Long?) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            ListItem(
                headlineContent = { Text(label) },
                supportingContent = { Text(selectedCategoryName) },
                trailingContent = {
                    Text("▼", style = MaterialTheme.typography.bodyMedium)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(AYMR.strings.anixart_import_category_none)) },
                    onClick = {
                        onCategorySelected(null)
                        expanded = false
                    },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onCategorySelected(category.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun PickSources(
        padding: PaddingValues,
        s: AnixartImportScreenModel.State.PickSources,
        model: AnixartImportScreenModel,
    ) {
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Text(
                        stringResource(AYMR.strings.anixart_import_legal_notice),
                        modifier = Modifier.padding(16.dp),
                    )
                    Text(
                        stringResource(AYMR.strings.anixart_import_export_hint),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (s.preflight.missingOriginalCount > 0) {
                        Text(
                            stringResource(
                                AYMR.strings.anixart_import_warning_missing_original,
                                s.preflight.missingOriginalCount,
                                s.preflight.totalRows,
                            ),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (s.preflight.largeImport) {
                        Text(
                            stringResource(
                                AYMR.strings.anixart_import_warning_large,
                                s.preflight.totalRows,
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp),
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
                items(AnixartStatus.entries.toList()) { status ->
                    val checked = status in s.statusFilter
                    ListItem(
                        headlineContent = { Text(statusLabel(status)) },
                        leadingContent = {
                            Checkbox(checked = checked, onCheckedChange = { model.toggleStatusFilter(status) })
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(AYMR.strings.anixart_import_sync_shikimori)) },
                        leadingContent = {
                            Checkbox(
                                checked = s.syncToShikimori,
                                onCheckedChange = model::setSyncToShikimori,
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        stringResource(AYMR.strings.anixart_import_category_mapping_title),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    val catId = s.favoriteCategoryId
                    val catName = s.categories.firstOrNull { it.id == catId }?.name
                        ?: stringResource(AYMR.strings.anixart_import_category_none)
                    CategorySpinner(
                        label = stringResource(AYMR.strings.anixart_import_status_favorite),
                        selectedCategoryName = catName,
                        categories = s.categories,
                        onCategorySelected = { model.setFavoriteCategoryMapping(it) },
                    )
                }
                item {
                    val catId = s.statusCategoryIds[AnixartStatus.WATCHING]
                    val catName = s.categories.firstOrNull { it.id == catId }?.name
                        ?: stringResource(AYMR.strings.anixart_import_category_none)
                    CategorySpinner(
                        label = stringResource(AYMR.strings.anixart_import_status_watching),
                        selectedCategoryName = catName,
                        categories = s.categories,
                        onCategorySelected = { model.setCategoryMapping(AnixartStatus.WATCHING, it) },
                    )
                }
                item {
                    val catId = s.statusCategoryIds[AnixartStatus.COMPLETED]
                    val catName = s.categories.firstOrNull { it.id == catId }?.name
                        ?: stringResource(AYMR.strings.anixart_import_category_none)
                    CategorySpinner(
                        label = stringResource(AYMR.strings.anixart_import_status_completed),
                        selectedCategoryName = catName,
                        categories = s.categories,
                        onCategorySelected = { model.setCategoryMapping(AnixartStatus.COMPLETED, it) },
                    )
                }
                item {
                    val catId = s.statusCategoryIds[AnixartStatus.PLAN_TO_WATCH]
                    val catName = s.categories.firstOrNull { it.id == catId }?.name
                        ?: stringResource(AYMR.strings.anixart_import_category_none)
                    CategorySpinner(
                        label = stringResource(AYMR.strings.anixart_import_status_plan_to_watch),
                        selectedCategoryName = catName,
                        categories = s.categories,
                        onCategorySelected = { model.setCategoryMapping(AnixartStatus.PLAN_TO_WATCH, it) },
                    )
                }
                item {
                    val catId = s.statusCategoryIds[AnixartStatus.DROPPED]
                    val catName = s.categories.firstOrNull { it.id == catId }?.name
                        ?: stringResource(AYMR.strings.anixart_import_category_none)
                    CategorySpinner(
                        label = stringResource(AYMR.strings.anixart_import_status_dropped),
                        selectedCategoryName = catName,
                        categories = s.categories,
                        onCategorySelected = { model.setCategoryMapping(AnixartStatus.DROPPED, it) },
                    )
                }
            }
            Button(
                onClick = model::startMatching,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                enabled = s.sources.any { it.selected },
            ) {
                Text(stringResource(AYMR.strings.anixart_import_start_matching))
            }
        }
    }

    @Composable
    private fun ReviewItemRow(
        index: Int,
        item: AnixartImportScreenModel.ReviewItem,
        model: AnixartImportScreenModel,
    ) {
        var menuExpanded by remember { mutableStateOf(false) }
        val selectedCandidate = item.result.ranked.firstOrNull { it.candidate.id == item.selectedId }?.candidate

        val badgeColor = when (item.result.confidence) {
            AnixartMatcher.Confidence.AUTO -> MaterialTheme.colorScheme.primaryContainer
            AnixartMatcher.Confidence.NEEDS_REVIEW -> MaterialTheme.colorScheme.tertiaryContainer
            AnixartMatcher.Confidence.NO_MATCH -> MaterialTheme.colorScheme.errorContainer
        }
        val badgeTextColor = when (item.result.confidence) {
            AnixartMatcher.Confidence.AUTO -> MaterialTheme.colorScheme.onPrimaryContainer
            AnixartMatcher.Confidence.NEEDS_REVIEW -> MaterialTheme.colorScheme.onTertiaryContainer
            AnixartMatcher.Confidence.NO_MATCH -> MaterialTheme.colorScheme.onErrorContainer
        }
        val badgeText = when (item.result.confidence) {
            AnixartMatcher.Confidence.AUTO -> stringResource(AYMR.strings.anixart_import_group_exact)
            AnixartMatcher.Confidence.NEEDS_REVIEW -> stringResource(AYMR.strings.anixart_import_group_review)
            AnixartMatcher.Confidence.NO_MATCH -> stringResource(AYMR.strings.anixart_import_group_nomatch)
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = {
                    Text(
                        item.row.originalTitle.ifEmpty { item.row.russianTitle },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Column(modifier = Modifier.padding(top = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val bestText = selectedCandidate?.displayTitle
                                ?: stringResource(AYMR.strings.anixart_import_group_nomatch)
                            Text(
                                text = bestText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(badgeColor)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeTextColor,
                                )
                            }
                        }
                        item.matchedQuery?.let { query ->
                            Text(
                                stringResource(AYMR.strings.anixart_import_matched_query, query),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        item.matchedSourceName?.let { source ->
                            Text(
                                stringResource(AYMR.strings.anixart_import_matched_source, source),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (item.result.confidence == AnixartMatcher.Confidence.NO_MATCH) {
                            TextButton(
                                onClick = { model.openManualSearch(index) },
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Text(stringResource(AYMR.strings.shikimori_import_manual_search))
                            }
                        }
                    }
                },
                leadingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.enabled && item.selectedId != null,
                            onCheckedChange = { model.setEnabled(index, it) },
                        )
                        val thumb = selectedCandidate?.thumbnailUrl
                        if (thumb != null) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(36.dp, 54.dp)
                                    .clip(MaterialTheme.shapes.extraSmall),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(36.dp, 54.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("?", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuExpanded = true },
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(AYMR.strings.anixart_import_change_match),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    enabled = false,
                    onClick = {},
                )
                DropdownMenuItem(
                    text = { Text(stringResource(AYMR.strings.shikimori_import_manual_search)) },
                    onClick = {
                        model.openManualSearch(index)
                        menuExpanded = false
                    },
                )
                item.result.ranked.forEach { scored ->
                    val cand = scored.candidate
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(cand.displayTitle)
                                Text(
                                    stringResource(AYMR.strings.anixart_import_score_match, scored.score),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        },
                        onClick = {
                            model.setSelection(index, cand.id)
                            menuExpanded = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(AYMR.strings.anixart_import_group_nomatch)) },
                    onClick = {
                        model.setSelection(index, null)
                        menuExpanded = false
                    },
                )
            }
        }
    }

    @Composable
    private fun Review(
        padding: PaddingValues,
        s: AnixartImportScreenModel.State.Review,
        model: AnixartImportScreenModel,
    ) {
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(s.items) { index, item ->
                    ReviewItemRow(index = index, item = item, model = model)
                }
            }
            Button(
                onClick = {
                    model.startImport()
                },
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                enabled = model.selectedCount() > 0,
            ) {
                Text(stringResource(AYMR.strings.anixart_import_action_import, model.selectedCount()))
            }
        }
        s.manualSearch?.let { manual ->
            ManualSearchDialog(manual, model)
        }
    }

    @Composable
    private fun ManualSearchDialog(
        manual: AnixartImportScreenModel.State.ManualSearchState,
        model: AnixartImportScreenModel,
    ) {
        AlertDialog(
            onDismissRequest = model::dismissManualSearch,
            title = { Text(stringResource(AYMR.strings.shikimori_import_manual_search_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = manual.query,
                        onValueChange = model::setManualSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(AYMR.strings.shikimori_import_manual_search_hint)) },
                        singleLine = true,
                        enabled = !manual.loading,
                    )
                    if (manual.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .align(Alignment.CenterHorizontally),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = model::runManualSearch,
                    enabled = manual.query.isNotBlank() && !manual.loading,
                ) {
                    Text(stringResource(AYMR.strings.anixart_import_start_matching))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = model::dismissManualSearch,
                    enabled = !manual.loading,
                ) {
                    Text(stringResource(AYMR.strings.novel_reader_background_action_cancel))
                }
            },
        )
    }

    @Composable
    private fun statusLabel(status: AnixartStatus): String = when (status) {
        AnixartStatus.WATCHING -> stringResource(AYMR.strings.anixart_import_status_watching)
        AnixartStatus.COMPLETED -> stringResource(AYMR.strings.anixart_import_status_completed)
        AnixartStatus.PLAN_TO_WATCH -> stringResource(AYMR.strings.anixart_import_status_plan_to_watch)
        AnixartStatus.DROPPED -> stringResource(AYMR.strings.anixart_import_status_dropped)
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
