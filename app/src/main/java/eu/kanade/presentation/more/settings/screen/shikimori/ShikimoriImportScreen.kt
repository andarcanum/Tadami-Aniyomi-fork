package eu.kanade.presentation.more.settings.screen.shikimori

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.entries.components.aurora.AuroraGlassCtaSurface
import eu.kanade.presentation.entries.components.aurora.AuroraHeroCtaMode
import eu.kanade.presentation.entries.components.aurora.GlassmorphismCard
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.more.settings.AuroraTopBarTitleText
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartSourceHints
import tachiyomi.data.shikimori.ShikimoriImportMediaType
import tachiyomi.data.shikimori.ShikimoriImportStatus
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.util.Screen as ParentScreen

/**
 * Shikimori library import wizard — Aurora glass UI:
 * [AuroraTabRow] media tabs, Settings-style top-bar icons (44.dp soft circles),
 * haze-backed list rows / search / CTAs with solid outlines.
 */
class ShikimoriImportScreen : ParentScreen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ShikimoriImportScreenModel() }
        val state by model.state.collectAsState()
        val colors = AuroraTheme.colors
        val hazeState = remember { HazeState() }

        AuroraBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    ShikimoriImportTopBar(
                        title = stringResource(AYMR.strings.shikimori_import_title),
                        onBack = navigator::pop,
                        actions = {
                            val pickState = state as? ShikimoriImportScreenModel.State.PickSources
                            if (pickState != null) {
                                var menuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    AuroraTopBarIconButton(
                                        onClick = { menuExpanded = true },
                                        icon = Icons.Default.MoreVert,
                                        contentDescription = stringResource(MR.strings.action_menu),
                                    )
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
                                                            colors = auroraCheckboxColors(),
                                                            modifier = Modifier.padding(end = 8.dp),
                                                        )
                                                        Text(
                                                            text = LocaleHelper.getSourceDisplayName(lang, context),
                                                            color = colors.textPrimary,
                                                        )
                                                    }
                                                },
                                                onClick = { model.toggleLanguageEnabled(lang) },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    MediaTypeTabs(
                        selected = state.mediaType,
                        enabled = state is ShikimoriImportScreenModel.State.PickSources ||
                            state is ShikimoriImportScreenModel.State.Loading ||
                            state is ShikimoriImportScreenModel.State.Error,
                        onSelect = model::switchMediaType,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .hazeSource(hazeState),
                    ) {
                        when (val s = state) {
                            is ShikimoriImportScreenModel.State.Loading -> Centered {
                                CircularProgressIndicator(color = colors.accent)
                            }
                            is ShikimoriImportScreenModel.State.Matching -> Centered {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = colors.accent)
                                    Text(
                                        text = stringResource(AYMR.strings.anixart_import_searching) +
                                            " ${s.current}/${s.total}",
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(top = 16.dp),
                                    )
                                }
                            }
                            is ShikimoriImportScreenModel.State.Error -> Centered {
                                Text(
                                    text = stringResource(errorMessageFor(s.messageKey, s.mediaType)),
                                    color = colors.textPrimary,
                                )
                            }
                            is ShikimoriImportScreenModel.State.PickSources -> PickSources(s, model, hazeState)
                            is ShikimoriImportScreenModel.State.Review -> Review(s, model, hazeState)
                            is ShikimoriImportScreenModel.State.Importing -> Centered {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = colors.accent)
                                    Text(
                                        text = stringResource(AYMR.strings.anixart_import_importing) +
                                            " ${s.current}/${s.total}",
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                            }
                            is ShikimoriImportScreenModel.State.Done -> Centered {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(AYMR.strings.anixart_import_done),
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (s.backgroundJob) {
                                        Text(
                                            text = stringResource(AYMR.strings.shikimori_import_background_started),
                                            color = colors.textSecondary,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.shikimori_import_report,
                                                s.report.added,
                                                s.report.alreadyInLibrary,
                                                s.report.failed,
                                                s.report.trackerBound,
                                            ),
                                            color = colors.textSecondary,
                                        )
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.anixart_import_matching_report,
                                                s.matchingReport.auto,
                                                s.matchingReport.needsReview,
                                                s.matchingReport.noMatch,
                                            ),
                                            color = colors.textSecondary,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    AuroraPrimaryButton(
                                        label = stringResource(AYMR.strings.action_ok),
                                        onClick = navigator::pop,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ShikimoriImportTopBar(
        title: String,
        onBack: () -> Unit,
        actions: @Composable () -> Unit = {},
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuroraTopBarIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(MR.strings.action_bar_up_description),
            )
            AuroraTopBarTitleText(
                title = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp),
            )
            actions()
        }
    }

    @Composable
    private fun MediaTypeTabs(
        selected: ShikimoriImportMediaType,
        enabled: Boolean,
        onSelect: (ShikimoriImportMediaType) -> Unit,
    ) {
        val mediaTypes = remember {
            listOf(
                ShikimoriImportMediaType.ANIME,
                ShikimoriImportMediaType.MANGA,
                ShikimoriImportMediaType.RANOBE,
            )
        }
        val tabs = remember {
            persistentListOf(
                TabContent(
                    titleRes = AYMR.strings.shikimori_import_tab_anime,
                    content = { _, _ -> },
                ),
                TabContent(
                    titleRes = AYMR.strings.shikimori_import_tab_manga,
                    content = { _, _ -> },
                ),
                TabContent(
                    titleRes = AYMR.strings.shikimori_import_tab_ranobe,
                    content = { _, _ -> },
                ),
            )
        }
        val selectedIndex = mediaTypes.indexOf(selected).coerceAtLeast(0)
        AuroraTabRow(
            tabs = tabs,
            selectedIndex = selectedIndex,
            onTabSelected = { index ->
                if (enabled) {
                    mediaTypes.getOrNull(index)?.let(onSelect)
                }
            },
            scrollable = false,
        )
    }

    @Composable
    private fun PickSources(
        s: ShikimoriImportScreenModel.State.PickSources,
        model: ShikimoriImportScreenModel,
        hazeState: HazeState,
    ) {
        val colors = AuroraTheme.colors
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

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                if (s.largeImport) {
                    item {
                        GlassmorphismCard(
                            modifier = Modifier.padding(top = 8.dp),
                            cornerRadius = 20.dp,
                            innerPadding = 16.dp,
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.anixart_import_warning_large, s.entries.size),
                                color = colors.warning,
                            )
                        }
                    }
                }
                item {
                    AuroraSectionHeader(stringResource(AYMR.strings.anixart_import_status_filter_title))
                }
                items(ShikimoriImportStatus.forMediaType(s.mediaType)) { status ->
                    GlassListRow(
                        hazeState = hazeState,
                        onClick = { model.toggleStatusFilter(status) },
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(statusLabel(status), color = colors.textPrimary)
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = status in s.statusFilter,
                                    onCheckedChange = { model.toggleStatusFilter(status) },
                                    colors = auroraCheckboxColors(),
                                )
                            },
                            colors = auroraTransparentListItemColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    AuroraSectionHeader(stringResource(AYMR.strings.anixart_import_select_sources))
                }
                item {
                    GlassSearchField(
                        hazeState = hazeState,
                        value = s.searchQuery,
                        onValueChange = model::search,
                        onClear = { model.search("") },
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
                            color = colors.textSecondary,
                        )
                    }
                }
                sortedLangs.forEach { lang ->
                    val sources = groupedSources[lang] ?: emptyList()
                    val isCollapsed = lang in s.collapsedLanguages
                    val allSelected = sources.all { it.selected }

                    item(key = "lang-header-$lang") {
                        SourceHeader(
                            hazeState = hazeState,
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
                            GlassListRow(
                                hazeState = hazeState,
                                onClick = { model.toggleSource(src.id) },
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(src.name, color = colors.textPrimary)
                                    },
                                    supportingContent = if (warning) {
                                        {
                                            Text(
                                                text = stringResource(AYMR.strings.anixart_import_source_warning),
                                                color = colors.error,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    leadingContent = {
                                        Checkbox(
                                            checked = src.selected,
                                            onCheckedChange = { model.toggleSource(src.id) },
                                            colors = auroraCheckboxColors(),
                                        )
                                    },
                                    colors = auroraTransparentListItemColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                item {
                    AuroraSectionHeader(stringResource(AYMR.strings.anixart_import_category_mapping_title))
                }
                items(ShikimoriImportStatus.forMediaType(s.mediaType)) { status ->
                    val catId = s.statusCategoryIds[status]
                    val catName = s.categories.firstOrNull { it.id == catId }?.name
                        ?: stringResource(AYMR.strings.anixart_import_category_none)
                    CategorySpinner(
                        hazeState = hazeState,
                        label = statusLabel(status),
                        selectedCategoryName = catName,
                        categories = s.categories,
                        onCategorySelected = { model.setCategoryMapping(status, it) },
                    )
                }
            }
            AuroraPrimaryButton(
                label = stringResource(AYMR.strings.anixart_import_start_matching),
                onClick = model::startMatching,
                enabled = s.sources.any { it.selected },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
            )
        }
    }

    @Composable
    private fun SourceHeader(
        hazeState: HazeState,
        language: String,
        isCollapsed: Boolean,
        onToggleCollapse: () -> Unit,
        allSelected: Boolean,
        onToggleSelectAll: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val context = LocalContext.current
        val colors = AuroraTheme.colors
        GlassListRow(
            hazeState = hazeState,
            onClick = onToggleCollapse,
            modifier = modifier,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = onToggleSelectAll,
                    colors = auroraCheckboxColors(),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = LocaleHelper.getSourceDisplayName(language, context),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (isCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            }
        }
    }

    @Composable
    private fun CategorySpinner(
        hazeState: HazeState,
        label: String,
        selectedCategoryName: String,
        categories: List<ShikimoriImportScreenModel.CategoryUi>,
        onCategorySelected: (Long?) -> Unit,
    ) {
        val colors = AuroraTheme.colors
        var expanded by remember { mutableStateOf(false) }
        Box {
            GlassListRow(
                hazeState = hazeState,
                onClick = { expanded = true },
            ) {
                ListItem(
                    headlineContent = { Text(label, color = colors.textPrimary) },
                    supportingContent = {
                        Text(selectedCategoryName, color = colors.textSecondary)
                    },
                    trailingContent = {
                        Text("▼", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    },
                    colors = auroraTransparentListItemColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
        hazeState: HazeState,
    ) {
        val colors = AuroraTheme.colors
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    Text(
                        text = stringResource(
                            AYMR.strings.anixart_import_matching_report,
                            s.matchingReport.auto,
                            s.matchingReport.needsReview,
                            s.matchingReport.noMatch,
                        ),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                itemsIndexed(s.items) { index, item ->
                    ReviewItemRow(index, item, model, hazeState)
                }
            }
            AuroraPrimaryButton(
                label = stringResource(AYMR.strings.anixart_import_action_import, model.selectedCount()),
                onClick = model::startImport,
                enabled = model.selectedCount() > 0,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
            )
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
        val colors = AuroraTheme.colors
        AlertDialog(
            onDismissRequest = model::dismissManualSearch,
            containerColor = resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Strong),
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text(stringResource(AYMR.strings.shikimori_import_manual_search_title)) },
            text = {
                Column {
                    TextField(
                        value = manual.query,
                        onValueChange = model::setManualSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(importFallbackPanel(colors)),
                        placeholder = {
                            Text(
                                text = stringResource(AYMR.strings.shikimori_import_manual_search_hint),
                                color = colors.textSecondary,
                            )
                        },
                        singleLine = true,
                        enabled = !manual.loading,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = colors.accent,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                        ),
                    )
                    if (manual.loading) {
                        CircularProgressIndicator(
                            color = colors.accent,
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
                    Text(
                        text = stringResource(AYMR.strings.anixart_import_start_matching),
                        color = colors.accent,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = model::dismissManualSearch,
                    enabled = !manual.loading,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_background_action_cancel),
                        color = colors.textSecondary,
                    )
                }
            },
        )
    }

    @Composable
    private fun ReviewItemRow(
        index: Int,
        item: ShikimoriImportScreenModel.ReviewItem,
        model: ShikimoriImportScreenModel,
        hazeState: HazeState,
    ) {
        var menuExpanded by remember { mutableStateOf(false) }
        val colors = AuroraTheme.colors
        val selectedCandidate = item.result.ranked.firstOrNull { it.candidate.id == item.selectedId }?.candidate

        val badgeColor = when (item.result.confidence) {
            AnixartMatcher.Confidence.AUTO -> colors.accent.copy(alpha = 0.22f)
            AnixartMatcher.Confidence.NEEDS_REVIEW -> colors.warning.copy(alpha = 0.22f)
            AnixartMatcher.Confidence.NO_MATCH -> colors.error.copy(alpha = 0.22f)
        }
        val badgeTextColor = when (item.result.confidence) {
            AnixartMatcher.Confidence.AUTO -> colors.accent
            AnixartMatcher.Confidence.NEEDS_REVIEW -> colors.warning
            AnixartMatcher.Confidence.NO_MATCH -> colors.error
        }
        val badgeText = when (item.result.confidence) {
            AnixartMatcher.Confidence.AUTO -> stringResource(AYMR.strings.anixart_import_group_exact)
            AnixartMatcher.Confidence.NEEDS_REVIEW -> stringResource(AYMR.strings.anixart_import_group_review)
            AnixartMatcher.Confidence.NO_MATCH -> stringResource(AYMR.strings.anixart_import_group_nomatch)
        }

        Box {
            GlassListRow(
                hazeState = hazeState,
                onClick = { menuExpanded = true },
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = item.entry.russian ?: item.entry.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.textPrimary,
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
                                    color = colors.textSecondary,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(badgeColor)
                                        .border(
                                            BorderStroke(1.dp, badgeTextColor.copy(alpha = 0.35f)),
                                            RoundedCornerShape(8.dp),
                                        )
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
                                    text = stringResource(AYMR.strings.anixart_import_matched_query, query),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                            item.matchedSourceName?.let { source ->
                                Text(
                                    text = stringResource(AYMR.strings.anixart_import_matched_source, source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                            if (item.result.confidence == AnixartMatcher.Confidence.NO_MATCH) {
                                TextButton(
                                    onClick = { model.openManualSearch(index) },
                                    modifier = Modifier.padding(top = 2.dp),
                                ) {
                                    Text(
                                        text = stringResource(AYMR.strings.shikimori_import_manual_search),
                                        color = colors.accent,
                                    )
                                }
                            }
                        }
                    },
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.enabled && item.selectedId != null,
                                onCheckedChange = { model.setEnabled(index, it) },
                                colors = auroraCheckboxColors(),
                            )
                            val thumb = selectedCandidate?.thumbnailUrl ?: item.entry.thumbnailUrl
                            if (thumb != null) {
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(36.dp, 54.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(36.dp, 54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.textPrimary.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("?", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                }
                            }
                        }
                    },
                    colors = auroraTransparentListItemColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(AYMR.strings.anixart_import_change_match),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
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
                                    text = stringResource(AYMR.strings.anixart_import_score_match, scored.score),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
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
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassmorphismCard(cornerRadius = 24.dp, innerPadding = 24.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content()
                }
            }
        }
    }
}

// ─── Aurora glass helpers ────────────────────────────────────────────────────

private val ImportRowShape = RoundedCornerShape(16.dp)
private val ImportPillShape = RoundedCornerShape(999.dp)

private fun importBorderColor(colors: AuroraColors, emphasized: Boolean = false): Color {
    return when {
        colors.isEInk -> if (emphasized) colors.divider else colors.divider.copy(alpha = 0.7f)
        colors.isDark -> Color.White.copy(alpha = if (emphasized) 0.16f else 0.10f)
        else -> Color.Black.copy(alpha = if (emphasized) 0.10f else 0.06f)
    }
}

private fun importFallbackPanel(colors: AuroraColors): Color {
    return if (colors.isDark) {
        Color.White.copy(alpha = 0.10f).compositeOver(colors.background)
    } else {
        Color.White.copy(alpha = 0.92f)
    }
}

private fun Modifier.importGlass(
    hazeState: HazeState,
    colors: AuroraColors,
    shape: Shape,
    tint: Color = colors.surface.copy(alpha = if (colors.isDark) 0.36f else 0.48f),
    blurRadius: Dp = 20.dp,
    outline: Color = importBorderColor(colors, emphasized = true),
): Modifier {
    if (colors.isEInk) {
        return this
            .clip(shape)
            .background(importFallbackPanel(colors), shape)
            .border(BorderStroke(1.dp, outline), shape)
    }
    return this
        .clip(shape)
        .hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = colors.background,
                tint = HazeTint(tint),
                blurRadius = blurRadius,
                noiseFactor = 0.10f,
            ),
        )
        .border(BorderStroke(1.dp, outline), shape)
}

@Composable
private fun GlassListRow(
    hazeState: HazeState,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .importGlass(hazeState = hazeState, colors = colors, shape = ImportRowShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

@Composable
private fun GlassSearchField(
    hazeState: HazeState,
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = AuroraTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .importGlass(
                hazeState = hazeState,
                colors = colors,
                shape = RoundedCornerShape(16.dp),
                tint = colors.surface.copy(alpha = if (colors.isDark) 0.32f else 0.45f),
            ),
        placeholder = {
            Text(
                text = stringResource(MR.strings.action_search),
                color = colors.textSecondary,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = colors.accent,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
        ),
    )
}

@Composable
private fun AuroraPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AuroraTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.then(
            if (!enabled) Modifier else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        AuroraGlassCtaSurface(
            mode = AuroraHeroCtaMode.Aurora,
            onClick = { if (enabled) onClick() },
            shape = ImportPillShape,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!enabled) Modifier else Modifier),
        ) { contentColor ->
            Text(
                text = label,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.40f),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun auroraTransparentListItemColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
)

@Composable
private fun auroraCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = AuroraTheme.colors.accent,
    uncheckedColor = AuroraTheme.colors.textSecondary.copy(alpha = 0.55f),
    checkmarkColor = AuroraTheme.colors.textOnAccent,
)

@Composable
private fun AuroraSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = AuroraTheme.colors.accent,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
    )
}
