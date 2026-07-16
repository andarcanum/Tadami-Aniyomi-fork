package eu.kanade.presentation.more.settings.screen.anixart

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import eu.kanade.presentation.entries.components.aurora.AuroraGlassCtaSurface
import eu.kanade.presentation.entries.components.aurora.AuroraHeroCtaMode
import eu.kanade.presentation.entries.components.aurora.GlassmorphismCard
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.more.settings.AuroraTopBarTitleText
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartSourceHints
import tachiyomi.data.anixart.AnixartStatus
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.util.Screen as ParentScreen

/**
 * Anixart import wizard — Aurora glass UI (parity with [eu.kanade.presentation.more.settings.screen.shikimori.ShikimoriImportScreen]):
 * Settings-style top-bar icons (44.dp soft circles), haze-backed list rows, solid outlines,
 * and [AuroraGlassCtaSurface] primary actions.
 */
class AnixartImportScreen(
    private val openStream: () -> java.io.InputStream,
) : ParentScreen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { AnixartImportScreenModel(openStream) }
        val state by model.state.collectAsState()
        val colors = AuroraTheme.colors
        val hazeState = remember { HazeState() }

        AuroraBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    AnixartImportTopBar(
                        title = stringResource(AYMR.strings.anixart_import_title),
                        onBack = navigator::pop,
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .hazeSource(hazeState),
                ) {
                    when (val s = state) {
                        is AnixartImportScreenModel.State.Loading -> Centered {
                            CircularProgressIndicator(color = colors.accent)
                        }
                        is AnixartImportScreenModel.State.Matching -> Centered {
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
                        is AnixartImportScreenModel.State.Error -> Centered {
                            Text(
                                text = stringResource(
                                    when (s.messageKey) {
                                        AnixartImportScreenModel.ErrorKind.INVALID ->
                                            AYMR.strings.anixart_import_error_invalid
                                        AnixartImportScreenModel.ErrorKind.EMPTY ->
                                            AYMR.strings.anixart_import_empty
                                    },
                                ),
                                color = colors.textPrimary,
                            )
                        }
                        is AnixartImportScreenModel.State.PickSources -> PickSources(s, model, hazeState)
                        is AnixartImportScreenModel.State.Review -> Review(s, model, hazeState)
                        is AnixartImportScreenModel.State.Importing -> Centered {
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
                        is AnixartImportScreenModel.State.Done -> Centered {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(AYMR.strings.anixart_import_done),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (s.backgroundJob) {
                                    Text(
                                        text = stringResource(AYMR.strings.anixart_import_background_started),
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                } else {
                                    Text(
                                        text = stringResource(
                                            AYMR.strings.anixart_import_report,
                                            s.report.added,
                                            s.report.alreadyInLibrary,
                                            s.report.failed,
                                        ),
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(top = 8.dp),
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
                                    s.trackerReport?.let { tracker ->
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.anixart_import_tracker_report,
                                                tracker.synced,
                                                tracker.skipped,
                                                tracker.failed,
                                            ),
                                            color = colors.textSecondary,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
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

    @Composable
    private fun AnixartImportTopBar(
        title: String,
        onBack: () -> Unit,
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
        }
    }

    @Composable
    private fun CategorySpinner(
        hazeState: HazeState,
        label: String,
        selectedCategoryName: String,
        categories: List<Category>,
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
    private fun PickSources(
        s: AnixartImportScreenModel.State.PickSources,
        model: AnixartImportScreenModel,
        hazeState: HazeState,
    ) {
        val colors = AuroraTheme.colors
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    GlassmorphismCard(
                        modifier = Modifier.padding(top = 8.dp),
                        cornerRadius = 20.dp,
                        innerPadding = 16.dp,
                    ) {
                        Column {
                            Text(
                                text = stringResource(AYMR.strings.anixart_import_legal_notice),
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(AYMR.strings.anixart_import_export_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            if (s.preflight.missingOriginalCount > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(
                                        AYMR.strings.anixart_import_warning_missing_original,
                                        s.preflight.missingOriginalCount,
                                        s.preflight.totalRows,
                                    ),
                                    color = colors.warning,
                                )
                            }
                            if (s.preflight.largeImport) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(
                                        AYMR.strings.anixart_import_warning_large,
                                        s.preflight.totalRows,
                                    ),
                                    color = colors.warning,
                                )
                            }
                        }
                    }
                }
                item {
                    AuroraSectionHeader(stringResource(AYMR.strings.anixart_import_status_filter_title))
                }
                items(AnixartStatus.entries.toList()) { status ->
                    val checked = status in s.statusFilter
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
                                    checked = checked,
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
                    GlassListRow(
                        hazeState = hazeState,
                        onClick = { model.setSyncToShikimori(!s.syncToShikimori) },
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(AYMR.strings.anixart_import_sync_shikimori),
                                    color = colors.textPrimary,
                                )
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = s.syncToShikimori,
                                    onCheckedChange = model::setSyncToShikimori,
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
                items(s.sources, key = { it.id }) { src ->
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
                item {
                    AuroraSectionHeader(stringResource(AYMR.strings.anixart_import_category_mapping_title))
                }
                item {
                    val catId = s.favoriteCategoryId
                    val catName = s.categories.firstOrNull { it.id == catId }?.name
                        ?: stringResource(AYMR.strings.anixart_import_category_none)
                    CategorySpinner(
                        hazeState = hazeState,
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
                        hazeState = hazeState,
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
                        hazeState = hazeState,
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
                        hazeState = hazeState,
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
                        hazeState = hazeState,
                        label = stringResource(AYMR.strings.anixart_import_status_dropped),
                        selectedCategoryName = catName,
                        categories = s.categories,
                        onCategorySelected = { model.setCategoryMapping(AnixartStatus.DROPPED, it) },
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
    private fun ReviewItemRow(
        index: Int,
        item: AnixartImportScreenModel.ReviewItem,
        model: AnixartImportScreenModel,
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
                            text = item.row.originalTitle.ifEmpty { item.row.russianTitle },
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
                            val thumb = selectedCandidate?.thumbnailUrl
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
                                    Text(
                                        text = "?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
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
    private fun Review(
        s: AnixartImportScreenModel.State.Review,
        model: AnixartImportScreenModel,
        hazeState: HazeState,
    ) {
        val colors = AuroraTheme.colors
        Column(modifier = Modifier.fillMaxSize()) {
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
                    ReviewItemRow(index = index, item = item, model = model, hazeState = hazeState)
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
        manual: AnixartImportScreenModel.State.ManualSearchState,
        model: AnixartImportScreenModel,
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
    private fun statusLabel(status: AnixartStatus): String = when (status) {
        AnixartStatus.WATCHING -> stringResource(AYMR.strings.anixart_import_status_watching)
        AnixartStatus.COMPLETED -> stringResource(AYMR.strings.anixart_import_status_completed)
        AnixartStatus.PLAN_TO_WATCH -> stringResource(AYMR.strings.anixart_import_status_plan_to_watch)
        AnixartStatus.DROPPED -> stringResource(AYMR.strings.anixart_import_status_dropped)
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

// ─── Aurora glass helpers (shared language with Shikimori import) ─────────────

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
private fun AuroraPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AuroraGlassCtaSurface(
            mode = AuroraHeroCtaMode.Aurora,
            onClick = { if (enabled) onClick() },
            shape = ImportPillShape,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
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
