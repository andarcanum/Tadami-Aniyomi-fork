package eu.kanade.presentation.more.settings.screen.anilist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
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
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.presentation.theme.resolveAuroraTopBarScrimColor
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.data.anilist.AnilistImportMediaType
import tachiyomi.data.anilist.AnilistImportStatus
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartSourceHints
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.util.Screen as ParentScreen

/**
 * AniList import wizard — Aurora glass (parity with [ShikimoriImportScreen]):
 * grouped section cards, sticky Anime/Manga tab scrim, haze sections,
 * Settings-style 44.dp top-bar icons, clear CTA states.
 */
class AnilistImportScreen : ParentScreen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { AnilistImportScreenModel() }
        val state by model.state.collectAsState()
        val colors = AuroraTheme.colors
        val hazeState = remember { HazeState() }
        val isPickSources = state is AnilistImportScreenModel.State.PickSources

        AuroraBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(resolveAuroraTopBarScrimColor(colors)),
                    ) {
                        AnilistImportTopBar(
                            title = stringResource(AYMR.strings.anilist_import_title),
                            subtitle = if (isPickSources) "Step 1 · Sources" else null,
                            onBack = navigator::pop,
                        )
                        // Sticky media tabs on scrim so list never paints under them.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(resolveAuroraTopBarScrimColor(colors))
                                .padding(bottom = 10.dp),
                        ) {
                            MediaTypeTabs(
                                selected = state.mediaType,
                                enabled = state is AnilistImportScreenModel.State.PickSources ||
                                    state is AnilistImportScreenModel.State.Loading ||
                                    state is AnilistImportScreenModel.State.Error,
                                onSelect = model::switchMediaType,
                            )
                        }
                    }
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .hazeSource(hazeState),
                ) {
                    when (val s = state) {
                        is AnilistImportScreenModel.State.Loading -> Centered {
                            CircularProgressIndicator(color = colors.accent)
                        }
                        is AnilistImportScreenModel.State.Matching -> Centered {
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
                        is AnilistImportScreenModel.State.Error -> Centered {
                            Text(
                                text = stringResource(errorMessageFor(s.messageKey, s.mediaType)),
                                color = colors.textPrimary,
                            )
                        }
                        is AnilistImportScreenModel.State.PickSources -> PickSources(s, model, hazeState)
                        is AnilistImportScreenModel.State.Review -> Review(s, model, hazeState)
                        is AnilistImportScreenModel.State.Importing -> Centered {
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
                        is AnilistImportScreenModel.State.Done -> Centered {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(AYMR.strings.anixart_import_done),
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (s.backgroundJob) {
                                    Text(
                                        text = stringResource(AYMR.strings.anilist_import_background_started),
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                } else {
                                    Text(
                                        text = stringResource(
                                            AYMR.strings.anilist_import_report,
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
                                    enabled = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AnilistImportTopBar(
        title: String,
        subtitle: String?,
        onBack: () -> Unit,
    ) {
        val colors = AuroraTheme.colors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuroraTopBarIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(MR.strings.action_bar_up_description),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    @Composable
    private fun MediaTypeTabs(
        selected: AnilistImportMediaType,
        enabled: Boolean,
        onSelect: (AnilistImportMediaType) -> Unit,
    ) {
        val mediaTypes = remember {
            listOf(
                AnilistImportMediaType.ANIME,
                AnilistImportMediaType.MANGA,
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
        s: AnilistImportScreenModel.State.PickSources,
        model: AnilistImportScreenModel,
        hazeState: HazeState,
    ) {
        val colors = AuroraTheme.colors
        val statuses = remember(s.mediaType) { AnilistImportStatus.forMediaType(s.mediaType) }
        val selectedStatusCount = s.statusFilter.size
        val selectedSourceCount = s.sources.count { it.selected }
        val canMatch = selectedSourceCount > 0 && selectedStatusCount > 0

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (s.largeImport) {
                    item {
                        GlassmorphismCard(
                            horizontalPadding = 0.dp,
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

                // ── Section A: Statuses ──────────────────────────────────────
                item {
                    GlassSection(
                        hazeState = hazeState,
                        title = stringResource(AYMR.strings.anixart_import_status_filter_title),
                    ) {
                        statuses.forEachIndexed { index, status ->
                            FlatCheckRow(
                                label = statusLabel(status, s.mediaType),
                                checked = status in s.statusFilter,
                                onToggle = { model.toggleStatusFilter(status) },
                            )
                            if (index < statuses.lastIndex) {
                                SectionDivider()
                            }
                        }
                    }
                }

                // ── Section B: Sources (flat — no language groups) ───────────
                item {
                    GlassSection(
                        hazeState = hazeState,
                        title = stringResource(AYMR.strings.anixart_import_select_sources),
                    ) {
                        s.sources.forEachIndexed { index, src ->
                            val warning = src.recommendation == AnixartSourceHints.Recommendation.WARNING
                            FlatCheckRow(
                                label = src.name,
                                checked = src.selected,
                                onToggle = { model.toggleSource(src.id) },
                                supporting = if (warning) {
                                    stringResource(AYMR.strings.anixart_import_source_warning)
                                } else {
                                    null
                                },
                                supportingColor = if (warning) colors.error else colors.textSecondary,
                            )
                            if (index < s.sources.lastIndex) {
                                SectionDivider()
                            }
                        }
                    }
                }

                // ── Section C: Category mapping ──────────────────────────────
                item {
                    GlassSection(
                        hazeState = hazeState,
                        title = stringResource(AYMR.strings.anixart_import_category_mapping_title),
                    ) {
                        statuses.forEachIndexed { index, status ->
                            val catId = s.statusCategoryIds[status]
                            val catName = s.categories.firstOrNull { it.id == catId }?.name
                                ?: stringResource(AYMR.strings.anixart_import_category_none)
                            CategoryMappingRow(
                                label = statusLabel(status, s.mediaType),
                                selectedCategoryName = catName,
                                categories = s.categories,
                                onCategorySelected = { model.setCategoryMapping(status, it) },
                            )
                            if (index < statuses.lastIndex) {
                                SectionDivider()
                            }
                        }
                    }
                }
            }

            // Bottom CTA with fade + summary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                colors.background.copy(alpha = 0.92f),
                                colors.background,
                            ),
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "$selectedStatusCount statuses · $selectedSourceCount sources",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                if (!canMatch) {
                    Text(
                        text = when {
                            selectedStatusCount == 0 -> "Select at least one status"
                            else -> "Select at least one source"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                AuroraPrimaryButton(
                    label = stringResource(AYMR.strings.anixart_import_start_matching),
                    onClick = model::startMatching,
                    enabled = canMatch,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Composable
    private fun CategoryMappingRow(
        label: String,
        selectedCategoryName: String,
        categories: List<AnilistImportScreenModel.CategoryUi>,
        onCategorySelected: (Long?) -> Unit,
    ) {
        val colors = AuroraTheme.colors
        var expanded by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = selectedCategoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textSecondary,
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
        s: AnilistImportScreenModel.State.Review,
        model: AnilistImportScreenModel,
        hazeState: HazeState,
    ) {
        val colors = AuroraTheme.colors
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = stringResource(
                            AYMR.strings.anixart_import_matching_report,
                            s.matchingReport.auto,
                            s.matchingReport.needsReview,
                            s.matchingReport.noMatch,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                itemsIndexed(s.items) { index, item ->
                    ReviewItemRow(index, item, model, hazeState)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AuroraPrimaryButton(
                    label = stringResource(AYMR.strings.anixart_import_action_import, model.selectedCount()),
                    onClick = model::startImport,
                    enabled = model.selectedCount() > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        s.manualSearch?.let { manual ->
            ManualSearchDialog(manual, model)
        }
    }

    @Composable
    private fun ManualSearchDialog(
        manual: AnilistImportScreenModel.State.ManualSearchState,
        model: AnilistImportScreenModel,
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
        item: AnilistImportScreenModel.ReviewItem,
        model: AnilistImportScreenModel,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .importGlass(
                        hazeState = hazeState,
                        colors = colors,
                        shape = ImportSectionShape,
                        tint = sectionTint(colors),
                    )
                    .clickable { menuExpanded = true }
                    .padding(12.dp),
            ) {
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
                                .padding(end = 10.dp)
                                .size(36.dp, 54.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(36.dp, 54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.textPrimary.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("?", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.entry.english ?: item.entry.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Medium,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
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
                }
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
    private fun statusLabel(status: AnilistImportStatus, mediaType: AnilistImportMediaType): String = when (status) {
        AnilistImportStatus.CURRENT -> if (mediaType == AnilistImportMediaType.ANIME) {
            stringResource(AYMR.strings.anixart_import_status_watching)
        } else {
            stringResource(AYMR.strings.shikimori_import_status_reading)
        }
        AnilistImportStatus.COMPLETED -> stringResource(AYMR.strings.anixart_import_status_completed)
        AnilistImportStatus.PLANNING -> stringResource(AYMR.strings.shikimori_import_status_planned)
        AnilistImportStatus.PAUSED -> stringResource(AYMR.strings.shikimori_import_status_on_hold)
        AnilistImportStatus.DROPPED -> stringResource(AYMR.strings.anixart_import_status_dropped)
        AnilistImportStatus.REPEATING -> if (mediaType == AnilistImportMediaType.ANIME) {
            stringResource(AYMR.strings.shikimori_import_status_rewatching)
        } else {
            stringResource(AYMR.strings.shikimori_import_status_rereading)
        }
    }

    private fun errorMessageFor(
        kind: AnilistImportScreenModel.ErrorKind,
        mediaType: AnilistImportMediaType,
    ) = when (kind) {
        AnilistImportScreenModel.ErrorKind.NOT_LOGGED_IN -> AYMR.strings.anilist_import_not_logged_in
        AnilistImportScreenModel.ErrorKind.EMPTY -> emptyMessageFor(mediaType)
        AnilistImportScreenModel.ErrorKind.NETWORK -> AYMR.strings.anilist_import_error_network
        AnilistImportScreenModel.ErrorKind.RATE_LIMITED -> AYMR.strings.anilist_import_error_rate_limited
    }

    private fun emptyMessageFor(mediaType: AnilistImportMediaType) = when (mediaType) {
        AnilistImportMediaType.ANIME -> AYMR.strings.anilist_import_empty_anime
        AnilistImportMediaType.MANGA -> AYMR.strings.anilist_import_empty_manga
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

// ─── Section / glass primitives ──────────────────────────────────────────────

private val ImportSectionShape = RoundedCornerShape(20.dp)
private val ImportPillShape = RoundedCornerShape(999.dp)

private fun sectionTint(colors: AuroraColors): Color {
    return colors.surface.copy(alpha = if (colors.isDark) 0.48f else 0.58f)
}

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
    tint: Color = sectionTint(colors),
    blurRadius: Dp = 22.dp,
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
private fun GlassSection(
    hazeState: HazeState,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .importGlass(
                hazeState = hazeState,
                colors = colors,
                shape = ImportSectionShape,
                tint = sectionTint(colors),
            ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider(color = importBorderColor(colors).copy(alpha = 0.6f))
        content()
    }
}

@Composable
private fun SectionDivider() {
    val colors = AuroraTheme.colors
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = importBorderColor(colors).copy(alpha = 0.45f),
    )
}

@Composable
private fun FlatCheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    supporting: String? = null,
    supportingColor: Color = AuroraTheme.colors.textSecondary,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(
                start = 12.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = auroraCheckboxColors(),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                )
            }
        }
    }
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
    if (enabled) {
        AuroraGlassCtaSurface(
            mode = AuroraHeroCtaMode.Aurora,
            onClick = onClick,
            shape = ImportPillShape,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            interactionSource = interaction,
            modifier = modifier.fillMaxWidth(),
        ) { contentColor ->
            Text(
                text = label,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(ImportPillShape)
                .background(
                    colors.textSecondary.copy(alpha = if (colors.isDark) 0.14f else 0.12f),
                )
                .border(
                    BorderStroke(1.dp, importBorderColor(colors)),
                    ImportPillShape,
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = colors.textSecondary.copy(alpha = 0.55f),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun auroraCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = AuroraTheme.colors.accent,
    uncheckedColor = AuroraTheme.colors.textSecondary.copy(alpha = 0.55f),
    checkmarkColor = AuroraTheme.colors.textOnAccent,
)
