package eu.kanade.presentation.more.settings.screen.shikimori

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartSourceHints
import tachiyomi.data.shikimori.ShikimoriImportMediaType
import tachiyomi.data.shikimori.ShikimoriImportStatus
import tachiyomi.i18n.MR
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
                    actions = {
                        val pickState = state as? ShikimoriImportScreenModel.State.PickSources
                        if (pickState != null) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    val context = LocalContext.current
                                    val allLangs = remember(pickState.sources) {
                                        pickState.sources.map { it.lang }.distinct().sorted()
                                    }
                                    allLangs.forEach { lang ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = lang in pickState.enabledLanguages,
                                                        onCheckedChange = null,
                                                        modifier = Modifier.padding(end = 8.dp),
                                                    )
                                                    Text(LocaleHelper.getSourceDisplayName(lang, context))
                                                }
                                            },
                                            onClick = {
                                                model.toggleLanguageEnabled(lang)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                MediaTypeTabs(
                    selected = state.mediaType,
                    enabled = state is ShikimoriImportScreenModel.State.PickSources ||
                        state is ShikimoriImportScreenModel.State.Loading ||
                        state is ShikimoriImportScreenModel.State.Error,
                    onSelect = model::switchMediaType,
                )
                when (val s = state) {
                    is ShikimoriImportScreenModel.State.Loading -> Centered { CircularProgressIndicator() }
                    is ShikimoriImportScreenModel.State.Matching -> Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                stringResource(AYMR.strings.anixart_import_searching) + " ${s.current}/${s.total}",
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    is ShikimoriImportScreenModel.State.Error -> Centered {
                        Text(stringResource(errorMessageFor(s.messageKey, s.mediaType)))
                    }
                    is ShikimoriImportScreenModel.State.PickSources -> PickSources(s, model)
                    is ShikimoriImportScreenModel.State.Review -> Review(s, model)
                    is ShikimoriImportScreenModel.State.Importing -> Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(stringResource(AYMR.strings.anixart_import_importing) + " ${s.current}/${s.total}")
                        }
                    }
                    is ShikimoriImportScreenModel.State.Done -> Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(AYMR.strings.anixart_import_done))
                            if (s.backgroundJob) {
                                Text(
                                    stringResource(AYMR.strings.shikimori_import_background_started),
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            } else {
                                Text(
                                    stringResource(
                                        AYMR.strings.shikimori_import_report,
                                        s.report.added,
                                        s.report.alreadyInLibrary,
                                        s.report.failed,
                                        s.report.trackerBound,
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
                            }
                            Button(onClick = navigator::pop) {
                                Text(stringResource(AYMR.strings.action_ok))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MediaTypeTabs(
        selected: ShikimoriImportMediaType,
        enabled: Boolean,
        onSelect: (ShikimoriImportMediaType) -> Unit,
    ) {
        val tabs = listOf(
            ShikimoriImportMediaType.ANIME to AYMR.strings.shikimori_import_tab_anime,
            ShikimoriImportMediaType.MANGA to AYMR.strings.shikimori_import_tab_manga,
            ShikimoriImportMediaType.RANOBE to AYMR.strings.shikimori_import_tab_ranobe,
        )
        val selectedIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        PrimaryTabRow(selectedTabIndex = selectedIndex) {
            tabs.forEachIndexed { index, (type, labelRes) ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { if (enabled) onSelect(type) },
                    enabled = enabled,
                    text = { Text(stringResource(labelRes)) },
                )
            }
        }
    }

    @Composable
    private fun PickSources(
        s: ShikimoriImportScreenModel.State.PickSources,
        model: ShikimoriImportScreenModel,
    ) {
        val filteredSources = remember(s.sources, s.searchQuery, s.enabledLanguages) {
            s.sources.filter {
                it.lang in s.enabledLanguages &&
                    (
                        s.searchQuery.isBlank() ||
                            it.name.contains(s.searchQuery, ignoreCase = true) ||
                            it.lang.contains(s.searchQuery, ignoreCase = true)
                        )
            }
        }

        val groupedSources = remember(filteredSources) {
            filteredSources.groupBy { it.lang }
        }

        val sortedLangs = remember(groupedSources) {
            groupedSources.keys.sortedWith { lang1, lang2 ->
                when {
                    lang1 == "" && lang2 != "" -> 1
                    lang2 == "" && lang1 != "" -> -1
                    else -> lang1.compareTo(lang2)
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
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
                items(ShikimoriImportStatus.forMediaType(s.mediaType)) { status ->
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
                item {
                    OutlinedTextField(
                        value = s.searchQuery,
                        onValueChange = { model.search(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(MR.strings.action_search)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (s.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { model.search("") }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                    )
                                }
                            }
                        },
                        singleLine = true,
                    )
                }
                if (sortedLangs.isEmpty() && s.searchQuery.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(MR.strings.no_results_found),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                sortedLangs.forEach { lang ->
                    val sources = groupedSources[lang] ?: emptyList()
                    val isCollapsed = lang in s.collapsedLanguages
                    val allSelected = sources.all { it.selected }

                    item(key = "lang-header-$lang") {
                        SourceHeader(
                            language = lang,
                            isCollapsed = isCollapsed,
                            onToggleCollapse = { model.toggleLanguage(lang) },
                            allSelected = allSelected,
                            onToggleSelectAll = { select -> model.toggleLanguageSources(lang, select) },
                        )
                    }

                    if (!isCollapsed) {
                        items(sources, key = { "src-${it.id}" }) { src ->
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
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
                item {
                    Text(
                        stringResource(AYMR.strings.anixart_import_category_mapping_title),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(ShikimoriImportStatus.forMediaType(s.mediaType)) { status ->
                    val catId = s.statusCategoryIds[status]
                    val catName = s.categories.firstOrNull { it.id == catId }?.name
                        ?: stringResource(AYMR.strings.anixart_import_category_none)
                    CategorySpinner(
                        label = statusLabel(status),
                        selectedCategoryName = catName,
                        categories = s.categories,
                        onCategorySelected = { model.setCategoryMapping(status, it) },
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
    private fun SourceHeader(
        language: String,
        isCollapsed: Boolean,
        onToggleCollapse: () -> Unit,
        allSelected: Boolean,
        onToggleSelectAll: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val context = LocalContext.current
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleCollapse)
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = allSelected,
                onCheckedChange = onToggleSelectAll,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = LocaleHelper.getSourceDisplayName(language, context),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (isCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun CategorySpinner(
        label: String,
        selectedCategoryName: String,
        categories: List<ShikimoriImportScreenModel.CategoryUi>,
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
    private fun Review(
        s: ShikimoriImportScreenModel.State.Review,
        model: ShikimoriImportScreenModel,
    ) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Text(
                        stringResource(
                            AYMR.strings.anixart_import_matching_report,
                            s.matchingReport.auto,
                            s.matchingReport.needsReview,
                            s.matchingReport.noMatch,
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
        s.manualSearch?.let { manual ->
            ManualSearchDialog(manual, model)
        }
    }

    @Composable
    private fun ManualSearchDialog(
        manual: ShikimoriImportScreenModel.State.ManualSearchState,
        model: ShikimoriImportScreenModel,
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
    private fun ReviewItemRow(
        index: Int,
        item: ShikimoriImportScreenModel.ReviewItem,
        model: ShikimoriImportScreenModel,
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
                        item.entry.russian ?: item.entry.name,
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
                        val thumb = selectedCandidate?.thumbnailUrl ?: item.entry.thumbnailUrl
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
    private fun statusLabel(status: ShikimoriImportStatus): String = when (status) {
        ShikimoriImportStatus.WATCHING -> stringResource(AYMR.strings.anixart_import_status_watching)
        ShikimoriImportStatus.READING -> stringResource(AYMR.strings.shikimori_import_status_reading)
        ShikimoriImportStatus.COMPLETED -> stringResource(AYMR.strings.anixart_import_status_completed)
        ShikimoriImportStatus.PLANNED -> stringResource(AYMR.strings.shikimori_import_status_planned)
        ShikimoriImportStatus.ON_HOLD -> stringResource(AYMR.strings.shikimori_import_status_on_hold)
        ShikimoriImportStatus.DROPPED -> stringResource(AYMR.strings.anixart_import_status_dropped)
        ShikimoriImportStatus.REWATCHING -> stringResource(AYMR.strings.shikimori_import_status_rewatching)
        ShikimoriImportStatus.REREADING -> stringResource(AYMR.strings.shikimori_import_status_rereading)
    }

    private fun errorMessageFor(
        kind: ShikimoriImportScreenModel.ErrorKind,
        mediaType: ShikimoriImportMediaType,
    ) = when (kind) {
        ShikimoriImportScreenModel.ErrorKind.NOT_LOGGED_IN -> AYMR.strings.shikimori_import_not_logged_in
        ShikimoriImportScreenModel.ErrorKind.EMPTY -> emptyMessageFor(mediaType)
        ShikimoriImportScreenModel.ErrorKind.NETWORK -> AYMR.strings.shikimori_import_error_network
        ShikimoriImportScreenModel.ErrorKind.RATE_LIMITED -> AYMR.strings.shikimori_import_error_rate_limited
    }

    private fun emptyMessageFor(mediaType: ShikimoriImportMediaType) = when (mediaType) {
        ShikimoriImportMediaType.ANIME -> AYMR.strings.shikimori_import_empty_anime
        ShikimoriImportMediaType.MANGA -> AYMR.strings.shikimori_import_empty_manga
        ShikimoriImportMediaType.RANOBE -> AYMR.strings.shikimori_import_empty_ranobe
    }

    @Composable
    private fun Centered(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}
