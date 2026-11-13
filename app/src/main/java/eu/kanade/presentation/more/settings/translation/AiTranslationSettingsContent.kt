@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.kanade.presentation.more.settings.translation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.settingsAccentColor
import eu.kanade.presentation.more.settings.settingsSubtitleColor
import eu.kanade.presentation.more.settings.settingsTitleColor
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.theme.resolveAuroraSelectionContainerColor
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialRoutingMode
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileRetryMode
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationProtocol
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/** Stateless presentation. CRUD/probes/ownership and persistence are callback responsibilities. */
@Composable
fun AiTranslationSettingsContent(
    state: AiTranslationUiState,
    onAction: (AiTranslationAction) -> Unit,
    onSaveCredential: (credentialId: String?, alias: String, secret: String?) -> Unit,
    onBackPressed: () -> Unit,
    uiStyle: SettingsUiStyle = SettingsUiStyle.Aurora,
) {
    val colors = AuroraTheme.colors
    val limitsProfileId = state.defaultProfileId ?: state.profiles.firstOrNull()?.id
    val tabs = remember {
        listOf(
            TabContent(AYMR.strings.ai_translation_jobs, content = { _, _ -> }),
            TabContent(AYMR.strings.ai_translation_profiles, content = { _, _ -> }),
            TabContent(AYMR.strings.ai_translation_connections, content = { _, _ -> }),
        ).toImmutableList()
    }
    SettingsScaffold(
        title = stringResource(AYMR.strings.ai_translation_title),
        uiStyle = uiStyle,
        onBackPressed = onBackPressed,
        // Top bar stays pinned: AuroraTabRow sits fixed above the grid, so a collapsing bar
        // would leave an empty gap above the tabs and could hide on short or empty tabs.
        topBarCanScroll = { false },
        titleContent = {
            Column {
                Text(
                    text = stringResource(AYMR.strings.ai_translation_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = settingsTitleColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(AYMR.strings.ai_translation_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = settingsSubtitleColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            if (limitsProfileId != null) {
                if (uiStyle == SettingsUiStyle.Aurora) {
                    AuroraTopBarIconButton(
                        onClick = { onAction(AiTranslationAction.EditRecovery(limitsProfileId)) },
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(AYMR.strings.ai_translation_limits),
                        tint = colors.accent,
                    )
                } else {
                    IconButton(
                        onClick = { onAction(AiTranslationAction.EditRecovery(limitsProfileId)) },
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            stringResource(AYMR.strings.ai_translation_limits),
                            tint = settingsAccentColor(),
                        )
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val adaptive = rememberAuroraAdaptiveSpec(maxWidth.value.toInt())
            val fontScale = LocalDensity.current.fontScale
            val compactTabs = maxWidth < 420.dp || fontScale >= 1.2f
            val columns = if (maxWidth >= 600.dp && fontScale < 1.5f) 2 else 1
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    AuroraTabRow(
                        tabs = tabs,
                        selectedIndex = state.tab.ordinal,
                        onTabSelected = {
                            onAction(
                                AiTranslationAction.SelectTab(
                                    AiTranslationTab.entries[it],
                                ),
                            )
                        },
                        scrollable = fontScale >= 1.4f,
                        compact = compactTabs,
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = maxOf(16, adaptive.contentHorizontalPaddingDp).dp,
                        vertical = 14.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TranslationCard(contentPadding = PaddingValues(0.dp)) {
                            AiTranslationToggleRow(
                                label = stringResource(AYMR.strings.ai_translation_title) + " · " +
                                    stringResource(AYMR.strings.ai_translation_enabled),
                                checked = state.aiTranslationEnabled,
                                onClick = {
                                    onAction(AiTranslationAction.SetAiTranslationEnabled(!state.aiTranslationEnabled))
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { CentreToolbar(state, onAction) }
                    when (state.loadState) {
                        AiTranslationLoadState.LOADING -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = if (colors.isEInk) colors.textPrimary else colors.accent,
                                )
                                Text(stringResource(AYMR.strings.ai_translation_loading))
                            }
                        }
                        AiTranslationLoadState.ERROR -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                TranslationNotice(stringResource(AYMR.strings.ai_translation_load_error))
                                AiTranslationSecondaryButton(
                                    text = stringResource(AYMR.strings.ai_translation_reload),
                                    onClick = { onAction(AiTranslationAction.Reload) },
                                )
                            }
                        }
                        AiTranslationLoadState.READY -> when (state.tab) {
                            AiTranslationTab.JOBS -> {
                                if (state.jobs.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        TranslationNotice(stringResource(AYMR.strings.ai_translation_empty_jobs))
                                    }
                                }
                                items(state.jobs, key = { it.id }) { JobCard(it, state.nowMillis, onAction) }
                            }
                            AiTranslationTab.PROFILES -> {
                                if (state.profiles.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        TranslationNotice(stringResource(AYMR.strings.ai_translation_empty_profiles))
                                    }
                                }
                                items(state.profiles, key = { it.id }) { profile ->
                                    TranslationCard {
                                        CardHeading(profile.name, profile.exactModelId, Icons.Default.Translate) {
                                            onAction(AiTranslationAction.EditProfile(profile.id))
                                        }
                                        Text(
                                            profile.languages.source + " → " + profile.languages.target,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                        if (profile.id == state.defaultProfileId) {
                                            AiTranslationStatusChip(
                                                label = stringResource(AYMR.strings.ai_translation_default_profile),
                                                tint = colors.accent,
                                            )
                                        } else {
                                            AiTranslationSecondaryButton(
                                                text = stringResource(AYMR.strings.ai_translation_use_default),
                                                onClick = {
                                                    onAction(AiTranslationAction.SelectDefaultProfile(profile.id))
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            AiTranslationTab.CONNECTIONS -> {
                                if (state.connections.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        TranslationNotice(stringResource(AYMR.strings.ai_translation_empty_connections))
                                    }
                                }
                                items(state.connections, key = { it.id }) { connection ->
                                    TranslationCard {
                                        CardHeading(connection.name, connection.baseUrl, Icons.Default.Settings) {
                                            onAction(AiTranslationAction.EditConnection(connection.id))
                                        }
                                        val probeStatus = state.probeStates[connection.id]
                                        AiTranslationStatusChip(
                                            label = probeTitle(probeStatus),
                                            tint = probeTint(probeStatus, colors),
                                        )
                                        if (connection.allowInsecureLocalHTTP) {
                                            TranslationNotice(
                                                stringResource(AYMR.strings.ai_translation_http_warning),
                                            )
                                        }
                                    }
                                }
                                if (
                                    state.migrationStatus == AiTranslationMigrationStatus.FAILED ||
                                    !state.vaultAvailable
                                ) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        TranslationCard {
                                            CardHeading(
                                                stringResource(AYMR.strings.ai_translation_protected_storage),
                                                stringResource(AYMR.strings.ai_translation_key_local),
                                                Icons.Default.Key,
                                            ) {
                                                onAction(AiTranslationAction.ReviewMigration)
                                            }
                                            if (state.migrationStatus != AiTranslationMigrationStatus.UNKNOWN) {
                                                Text(
                                                    stringResource(
                                                        when (state.migrationStatus) {
                                                            AiTranslationMigrationStatus.RUNNING ->
                                                                AYMR.strings.ai_translation_migration_running
                                                            AiTranslationMigrationStatus.VERIFIED ->
                                                                AYMR.strings.ai_translation_migration_verified
                                                            else -> AYMR.strings.ai_translation_migration_failed
                                                        },
                                                    ),
                                                )
                                            }
                                            if (!state.vaultAvailable) {
                                                TranslationNotice(
                                                    stringResource(AYMR.strings.ai_translation_vault_unavailable),
                                                )
                                            }
                                            AiTranslationSecondaryButton(
                                                text = stringResource(AYMR.strings.ai_translation_migration),
                                                onClick = { onAction(AiTranslationAction.ReviewMigration) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    AiTranslationEditorHost(state, onAction, onSaveCredential)
}

@Composable
private fun CentreToolbar(state: AiTranslationUiState, onAction: (AiTranslationAction) -> Unit) {
    var showBulkDialog by remember { mutableStateOf(false) }
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (state.tab == AiTranslationTab.JOBS) {
                stringResource(
                    AYMR.strings.ai_translation_job_count,
                    state.jobs.size,
                    state.jobs.count { it.needsAction },
                )
            } else {
                stringResource(
                    if (state.tab == AiTranslationTab.PROFILES) {
                        AYMR.strings.ai_translation_profile_hint
                    } else {
                        AYMR.strings.ai_translation_connection_hint
                    },
                )
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        if (state.tab == AiTranslationTab.JOBS) {
            IconButton(onClick = { showBulkDialog = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(AYMR.strings.ai_translation_all_owned),
                    tint = colors.textPrimary,
                )
            }
        } else {
            AiTranslationPrimaryButton(
                text = stringResource(AYMR.strings.ai_translation_add),
                icon = Icons.Default.Add,
                onClick = {
                    onAction(
                        if (state.tab == AiTranslationTab.PROFILES) {
                            AiTranslationAction.EditProfile(null)
                        } else {
                            AiTranslationAction.EditConnection(null)
                        },
                    )
                },
            )
        }
    }
    if (showBulkDialog) {
        AuroraFrostDialog(
            onDismiss = { showBulkDialog = false },
            title = stringResource(AYMR.strings.ai_translation_all_owned),
            closeLabel = stringResource(AYMR.strings.ai_translation_close),
            contentSpacing = 12.dp,
            footer = {
                AuroraFrostCancel(stringResource(AYMR.strings.ai_translation_cancel)) {
                    showBulkDialog = false
                }
            },
        ) {
            listOf(
                AiTranslationJobCommand.PAUSE,
                AiTranslationJobCommand.RESUME,
                AiTranslationJobCommand.CANCEL,
            ).forEach { command ->
                AiTranslationOptionCard(
                    title = commandTitle(command),
                    subtitle = null,
                    selected = false,
                    onClick = {
                        showBulkDialog = false
                        val request = AiTranslationAction.RequestJobCommand(
                            null,
                            command,
                            AiTranslationJobScope.ALL_OWNED,
                        )
                        if (command == AiTranslationJobCommand.CANCEL) {
                            onAction(AiTranslationAction.EditorChanged(AiTranslationEditor.Confirmation(request)))
                        } else {
                            onAction(request)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TranslationCard(
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = resolveAuroraTranslationGlassContainerColor(colors),
        border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(colors)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun CardHeading(title: String, subtitle: String, icon: ImageVector, onEdit: () -> Unit) {
    val colors = AuroraTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(14.dp), color = resolveAuroraSelectionContainerColor(colors)) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.padding(8.dp).size(24.dp))
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(AYMR.strings.ai_translation_details))
        }
    }
}

@Composable
private fun JobCard(job: AiTranslationJobUi, now: Long, onAction: (AiTranslationAction) -> Unit) {
    val colors = AuroraTheme.colors
    TranslationCard {
        CardHeading(
            job.title,
            listOf(job.chapterLabel, job.profileLabel).filter { it.isNotBlank() }.joinToString(" · "),
            if (job.needsAction) Icons.Default.Key else Icons.Default.Translate,
        ) {
            onAction(AiTranslationAction.EditorChanged(AiTranslationEditor.JobDetails(job)))
        }
        AiTranslationStatusChip(label = jobTitle(job), tint = jobTint(job, colors))
        AiTranslationJobProgress(job, now)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (job.needsAction) {
                AiTranslatorChoiceChip(
                    text = stringResource(AYMR.strings.ai_translation_fix_connection),
                    selected = true,
                    onClick = { onAction(AiTranslationAction.SelectTab(AiTranslationTab.CONNECTIONS)) },
                )
            }
            job.commands().forEach { command ->
                AiTranslatorChoiceChip(
                    text = commandTitle(command),
                    selected = command == AiTranslationJobCommand.RESUME,
                    onClick = { requestJobCommand(job.id, command, onAction) },
                )
            }
        }
    }
}

internal fun requestJobCommand(id: String, command: AiTranslationJobCommand, onAction: (AiTranslationAction) -> Unit) {
    val request = AiTranslationAction.RequestJobCommand(id, command)
    if (command == AiTranslationJobCommand.CANCEL || command == AiTranslationJobCommand.RETRANSLATE) {
        onAction(AiTranslationAction.EditorChanged(AiTranslationEditor.Confirmation(request)))
    } else {
        onAction(request)
    }
}

@Composable
internal fun AiTranslationJobProgress(job: AiTranslationJobUi, nowMillis: Long) {
    val colors = AuroraTheme.colors
    val count = if (job.totalCount > 0) {
        stringResource(
            AYMR.strings.ai_translation_verified_count,
            job.verifiedCount,
            job.totalCount,
        )
    } else {
        stringResource(AYMR.strings.ai_translation_coverage_unknown)
    }
    LinearProgressIndicator(
        progress = { job.displayPercent / 100f },
        color = colors.accent,
        trackColor = colors.divider,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = count },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(count, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(job.displayPercent.toString() + "%", style = MaterialTheme.typography.bodySmall)
    }
    if (job.fragments.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            job.fragments.take(6).forEach { fragment ->
                val verified = fragment.status == AiTranslationFragmentStatus.VERIFIED
                val text = stringResource(
                    when (fragment.status) {
                        AiTranslationFragmentStatus.VERIFIED -> AYMR.strings.ai_translation_fragment_verified
                        AiTranslationFragmentStatus.WAITING -> AYMR.strings.ai_translation_fragment_waiting
                        AiTranslationFragmentStatus.PENDING -> AYMR.strings.ai_translation_fragment_pending
                        AiTranslationFragmentStatus.FAILED -> AYMR.strings.ai_translation_fragment_failed
                    },
                    fragment.index,
                )
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, colors.divider),
                    color = if (verified && colors.isEInk) {
                        colors.textPrimary
                    } else if (verified) {
                        resolveAuroraSelectionContainerColor(colors)
                    } else {
                        colors.surface
                    },
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (verified && colors.isEInk) colors.background else colors.textSecondary,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            if (job.fragments.size > 6) {
                val remainder = job.fragments.size - 6
                Text(
                    pluralStringResource(
                        AYMR.plurals.ai_translation_more_fragments,
                        remainder,
                        remainder,
                    ),
                )
            }
        }
    }
    if (job.effectiveStatus == AiTranslationJobStatus.WAITING) {
        Text(stringResource(AYMR.strings.ai_translation_saved_parts), style = MaterialTheme.typography.bodySmall)
        val minutes = aiTranslationWaitMinutes(job.notBeforeAt, nowMillis)
        Text(
            if (minutes != null) {
                stringResource(AYMR.strings.ai_translation_wait_hint, minutes)
            } else {
                stringResource(AYMR.strings.ai_translation_wait_due)
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun TranslationNotice(text: String) {
    val colors = AuroraTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = resolveAuroraTranslationGlassContainerColor(colors, subtle = true),
        border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(colors)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
internal fun jobTitle(job: AiTranslationJobUi): String = stringResource(
    if (job.errorClass == TranslationFailureKind.AUTHORIZATION && job.needsAction) {
        AYMR.strings.ai_translation_key_required
    } else {
        when (job.effectiveStatus) {
            AiTranslationJobStatus.QUEUED -> AYMR.strings.ai_translation_status_queued
            AiTranslationJobStatus.RUNNING -> AYMR.strings.ai_translation_status_running
            AiTranslationJobStatus.WAITING -> AYMR.strings.ai_translation_status_waiting
            AiTranslationJobStatus.PAUSED -> AYMR.strings.ai_translation_status_paused
            AiTranslationJobStatus.COMPLETED -> AYMR.strings.ai_translation_status_completed
            AiTranslationJobStatus.PARTIAL -> AYMR.strings.ai_translation_status_partial
            AiTranslationJobStatus.FAILED -> AYMR.strings.ai_translation_status_failed
            AiTranslationJobStatus.CANCELLED -> AYMR.strings.ai_translation_status_cancelled
        }
    },
)

@Composable
internal fun commandTitle(command: AiTranslationJobCommand): String = stringResource(
    when (command) {
        AiTranslationJobCommand.DETAILS -> AYMR.strings.ai_translation_details
        AiTranslationJobCommand.OPEN -> AYMR.strings.ai_translation_open
        AiTranslationJobCommand.PAUSE -> AYMR.strings.ai_translation_pause
        AiTranslationJobCommand.RESUME -> AYMR.strings.ai_translation_resume
        AiTranslationJobCommand.CANCEL -> AYMR.strings.ai_translation_cancel
        AiTranslationJobCommand.RETRY_MISSING -> AYMR.strings.ai_translation_retry_missing
        AiTranslationJobCommand.RETRANSLATE -> AYMR.strings.ai_translation_retranslate
    },
)

@Composable
internal fun probeTitle(status: AiTranslationProbeStatus?): String = stringResource(
    when (status) {
        AiTranslationProbeStatus.VERIFIED -> AYMR.strings.ai_translation_probe_verified
        AiTranslationProbeStatus.RUNNING -> AYMR.strings.ai_translation_probe_running
        AiTranslationProbeStatus.FAILED -> AYMR.strings.ai_translation_probe_failed
        else -> AYMR.strings.ai_translation_probe_unknown
    },
)

internal fun probeTint(status: AiTranslationProbeStatus?, colors: AuroraColors): Color = when (status) {
    AiTranslationProbeStatus.VERIFIED -> colors.success
    AiTranslationProbeStatus.RUNNING -> colors.accent
    AiTranslationProbeStatus.FAILED -> colors.error
    else -> colors.textSecondary
}

internal fun jobTint(job: AiTranslationJobUi, colors: AuroraColors): Color = when {
    job.needsAction || job.effectiveStatus == AiTranslationJobStatus.FAILED -> colors.error
    job.effectiveStatus == AiTranslationJobStatus.COMPLETED -> colors.success
    job.effectiveStatus in setOf(AiTranslationJobStatus.RUNNING, AiTranslationJobStatus.QUEUED) -> colors.accent
    job.effectiveStatus in setOf(
        AiTranslationJobStatus.WAITING,
        AiTranslationJobStatus.PAUSED,
        AiTranslationJobStatus.PARTIAL,
    ) -> colors.warning
    else -> colors.textSecondary
}

@Composable
internal fun AiTranslationEditorHost(
    state: AiTranslationUiState,
    onAction: (AiTranslationAction) -> Unit,
    onSaveCredential: (String?, String, String?) -> Unit,
) {
    val dismiss = { onAction(AiTranslationAction.DismissEditor) }
    val colors = AuroraTheme.colors
    when (val editor = state.editor) {
        null -> Unit
        is AiTranslationEditor.Connection -> ConnectionEditor(state, editor, onAction, dismiss)
        is AiTranslationEditor.Profile -> ProfileEditor(state, editor.draft, onAction, dismiss)
        is AiTranslationEditor.Models -> ModelEditor(state, editor, onAction, dismiss)
        is AiTranslationEditor.Credential -> CredentialEditor(state, editor, onAction, onSaveCredential, dismiss)
        is AiTranslationEditor.Recovery -> RecoveryEditor(editor, onAction, dismiss)
        is AiTranslationEditor.JobDetails -> AuroraFrostDialog(
            onDismiss = dismiss,
            title = editor.job.chapterLabel,
            closeLabel = stringResource(AYMR.strings.ai_translation_close),
            contentSpacing = 12.dp,
            footer = { AuroraFrostCancel(stringResource(AYMR.strings.ai_translation_close), dismiss) },
        ) {
            Text(editor.job.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(editor.job.profileLabel, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            AiTranslationStatusChip(label = jobTitle(editor.job), tint = jobTint(editor.job, colors))
            AiTranslationJobProgress(editor.job, state.nowMillis)
            TranslationNotice(stringResource(AYMR.strings.ai_translation_owner_hint))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                editor.job.commands().forEach { command ->
                    AiTranslatorChoiceChip(
                        text = commandTitle(command),
                        selected = command == AiTranslationJobCommand.RESUME,
                        onClick = { requestJobCommand(editor.job.id, command, onAction) },
                    )
                }
            }
        }
        is AiTranslationEditor.Confirmation -> EditorDialog(
            stringResource(AYMR.strings.ai_translation_confirm_command),
            dismiss,
            true,
            {
                onAction(AiTranslationAction.ConfirmJobCommand(editor.request))
                dismiss()
            },
        ) {
            Text(commandTitle(editor.request.command), style = MaterialTheme.typography.titleMedium)
            if (editor.request.scope == AiTranslationJobScope.ALL_OWNED) {
                Text(
                    stringResource(AYMR.strings.ai_translation_all_owned),
                )
            }
            TranslationNotice(
                stringResource(
                    if (editor.request.command == AiTranslationJobCommand.RETRANSLATE) {
                        AYMR.strings.ai_translation_retranslate_hint
                    } else {
                        AYMR.strings.ai_translation_owner_hint
                    },
                ),
            )
        }
        is AiTranslationEditor.DeleteConfirmation -> EditorDialog(
            stringResource(AYMR.strings.ai_translation_delete_confirmation),
            dismiss,
            true,
            {
                onAction(
                    when (editor.kind) {
                        AiTranslationDeleteKind.CONNECTION -> AiTranslationAction.DeleteConnection(editor.id)
                        AiTranslationDeleteKind.PROFILE -> AiTranslationAction.DeleteProfile(editor.id)
                        AiTranslationDeleteKind.CREDENTIAL -> AiTranslationAction.DeleteCredential(editor.id)
                    },
                )
                dismiss()
            },
        ) { TranslationNotice(stringResource(AYMR.strings.ai_translation_delete_hint)) }
        AiTranslationEditor.Migration -> EditorDialog(
            stringResource(AYMR.strings.ai_translation_migration),
            dismiss,
            state.vaultAvailable,
            {
                onAction(AiTranslationAction.StartMigration)
                dismiss()
            },
            confirmLabel = stringResource(AYMR.strings.ai_translation_start_migration),
        ) {
            TranslationNotice(stringResource(AYMR.strings.ai_translation_migration_hint))
            if (!state.vaultAvailable) TranslationNotice(stringResource(AYMR.strings.ai_translation_vault_unavailable))
        }
    }
}

@Composable
private fun EditorDialog(
    title: String,
    dismiss: () -> Unit,
    enabled: Boolean,
    save: () -> Unit,
    confirmLabel: String = stringResource(AYMR.strings.ai_translation_save),
    content: @Composable ColumnScope.() -> Unit,
) {
    AuroraFrostDialog(
        onDismiss = dismiss,
        title = title,
        closeLabel = stringResource(AYMR.strings.ai_translation_close),
        contentSpacing = 12.dp,
        footer = { EditorFooter(dismiss, save, enabled, confirmLabel) },
        content = content,
    )
}

@Composable
private fun RowScope.EditorFooter(
    dismiss: () -> Unit,
    save: () -> Unit,
    enabled: Boolean,
    confirmLabel: String = stringResource(AYMR.strings.ai_translation_save),
) {
    if (LocalDensity.current.fontScale >= 1.3f) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                AuroraFrostConfirm(
                    confirmLabel,
                    save,
                    enabled,
                    Modifier.fillMaxWidth().heightIn(min = 44.dp),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                AuroraFrostCancel(stringResource(AYMR.strings.ai_translation_cancel), dismiss)
            }
        }
    } else {
        AuroraFrostCancel(stringResource(AYMR.strings.ai_translation_cancel), dismiss)
        AuroraFrostConfirm(
            confirmLabel,
            save,
            enabled,
            Modifier.heightIn(min = 44.dp),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    changed: (String) -> Unit,
    error: Boolean = false,
    numeric: Boolean = false,
    multiline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AiTranslationOutlinedTextField(
        value = value,
        onValueChange = changed,
        label = { Text(label) },
        isError = error,
        modifier = modifier.fillMaxWidth(),
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        maxLines = if (multiline) 5 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        supportingText = if (error) ({ Text(stringResource(AYMR.strings.ai_translation_error_field)) }) else null,
    )
}

@Composable
private fun EditorHelperCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = AuroraTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@Composable
private fun EditorDangerZone(
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = AuroraTheme.colors.divider)
        AiTranslationDangerButton(
            text = label,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConnectionEditor(
    state: AiTranslationUiState,
    editor: AiTranslationEditor.Connection,
    onAction: (AiTranslationAction) -> Unit,
    dismiss: () -> Unit,
) {
    val draft = editor.draft
    val close = {
        val parentProfile = editor.returnTo
        if (parentProfile != null) {
            onAction(AiTranslationAction.EditorChanged(AiTranslationEditor.Profile(parentProfile)))
        } else {
            dismiss()
        }
    }
    val changed = { value: AiConnectionDraft ->
        onAction(
            AiTranslationAction.EditorChanged(
                editor.copy(draft = value),
            ),
        )
    }
    val result = draft.toDomainOrNull()
    val errors = draft.errors()
    EditorDialog(stringResource(AYMR.strings.ai_translation_connection_editor), close, result != null, {
        result?.let { onAction(AiTranslationAction.SaveConnection(it)) }
    }) {
        Field(
            stringResource(AYMR.strings.ai_translation_name),
            draft.name,
            { changed(draft.copy(name = it)) },
            AiTranslationFormError.NAME in errors,
        )
        AiTranslationPickerField(
            label = stringResource(AYMR.strings.ai_translation_flavor),
            value = flavorTitle(draft.presetFlavor),
            options = PresetFlavor.entries.map { it to flavorTitle(it) },
            selectedOption = draft.presetFlavor,
            onSelect = { flavor ->
                changed(
                    when (flavor) {
                        PresetFlavor.NONE -> draft.copy(presetFlavor = PresetFlavor.NONE)
                        PresetFlavor.GEMINI_PRIVATE -> draft.copy(
                            presetFlavor = flavor,
                            protocol = TranslationProtocol.GEMINI_GENERATE,
                            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                            auth = ProfileAuth.X_GOOG_API_KEY,
                            allowInsecureLocalHTTP = false,
                        )
                        PresetFlavor.OPENROUTER -> draft.copy(
                            presetFlavor = flavor,
                            protocol = TranslationProtocol.OPENAI_CHAT,
                            baseUrl = "https://openrouter.ai/api/v1",
                            auth = ProfileAuth.BEARER,
                            allowInsecureLocalHTTP = false,
                        )
                        PresetFlavor.DEEPSEEK -> draft.copy(
                            presetFlavor = flavor,
                            protocol = TranslationProtocol.OPENAI_CHAT,
                            baseUrl = "https://api.deepseek.com",
                            auth = ProfileAuth.BEARER,
                            allowInsecureLocalHTTP = false,
                        )
                        PresetFlavor.MISTRAL -> draft.copy(
                            presetFlavor = flavor,
                            protocol = TranslationProtocol.OPENAI_CHAT,
                            baseUrl = "https://api.mistral.ai/v1",
                            auth = ProfileAuth.BEARER,
                            allowInsecureLocalHTTP = false,
                        )
                        PresetFlavor.NVIDIA -> draft.copy(
                            presetFlavor = flavor,
                            protocol = TranslationProtocol.OPENAI_CHAT,
                            baseUrl = "https://integrate.api.nvidia.com/v1",
                            auth = ProfileAuth.BEARER,
                            allowInsecureLocalHTTP = false,
                        )
                        PresetFlavor.OLLAMA -> draft.copy(
                            presetFlavor = flavor,
                            protocol = TranslationProtocol.OLLAMA_NATIVE,
                            baseUrl = "https://ollama.com/api",
                            auth = ProfileAuth.BEARER,
                            allowInsecureLocalHTTP = false,
                        )
                    },
                )
            },
        )
        AiTranslationChipGroup(
            label = stringResource(AYMR.strings.ai_translation_protocol),
            options = TranslationProtocol.entries.map { it to protocolTitle(it) },
            selected = draft.protocol,
            onSelect = { protocol ->
                val root = when (protocol) {
                    TranslationProtocol.GEMINI_GENERATE -> "https://generativelanguage.googleapis.com/v1beta"
                    TranslationProtocol.OPENAI_CHAT -> "https://api.example.com/v1"
                    TranslationProtocol.OLLAMA_NATIVE -> "http://127.0.0.1:11434/api"
                }
                changed(
                    draft.copy(
                        protocol = protocol,
                        baseUrl = root,
                        presetFlavor = PresetFlavor.NONE,
                        allowInsecureLocalHTTP = false,
                        auth = if (protocol == TranslationProtocol.GEMINI_GENERATE) {
                            ProfileAuth.X_GOOG_API_KEY
                        } else if (protocol == TranslationProtocol.OLLAMA_NATIVE) {
                            ProfileAuth.NONE
                        } else {
                            ProfileAuth.BEARER
                        },
                    ),
                )
            },
        )
        Field(
            stringResource(AYMR.strings.ai_translation_api_root),
            draft.baseUrl,
            { changed(draft.copy(baseUrl = it)) },
            AiTranslationFormError.ENDPOINT in errors,
        )
        EditorHelperCaption(stringResource(AYMR.strings.ai_translation_root_hint))
        AiTranslationChipGroup(
            label = stringResource(AYMR.strings.ai_translation_auth),
            options = ProfileAuth.entries.map { it to authTitle(it) },
            selected = draft.auth,
            onSelect = { changed(draft.copy(auth = it)) },
        )
        AiTranslationChipGroup(
            label = stringResource(AYMR.strings.ai_translation_parallelism),
            options = listOf(1, 2, 4, 8).map { it to it.toString() },
            selected = draft.maxConcurrency,
            onSelect = { changed(draft.copy(maxConcurrency = it)) },
        )
        AiTranslationToggleRow(
            label = stringResource(AYMR.strings.ai_translation_allow_http),
            checked = draft.allowInsecureLocalHTTP,
            onClick = { changed(draft.copy(allowInsecureLocalHTTP = !draft.allowInsecureLocalHTTP)) },
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        )
        if (draft.allowInsecureLocalHTTP) TranslationNotice(stringResource(AYMR.strings.ai_translation_http_warning))
        Text(
            text = stringResource(AYMR.strings.ai_translation_keys),
            style = MaterialTheme.typography.titleSmall,
            color = AuroraTheme.colors.textPrimary,
        )
        val colors = AuroraTheme.colors
        draft.bindings.forEach { binding ->
            val metadata = state.credentials.firstOrNull { it.id == binding.credentialId }
            val waitMinutes = aiTranslationWaitMinutes(metadata?.notBeforeAt, state.nowMillis)
            val healthLabel = when {
                metadata?.healthStatus == CredentialHealthStatus.NEEDS_ACTION ->
                    stringResource(AYMR.strings.ai_translation_key_needs_action)
                waitMinutes != null ->
                    stringResource(AYMR.strings.ai_translation_key_cooldown, waitMinutes)
                metadata?.healthStatus == CredentialHealthStatus.READY ->
                    stringResource(AYMR.strings.ai_translation_key_ready)
                else -> null
            }
            val healthTint = when {
                metadata?.healthStatus == CredentialHealthStatus.NEEDS_ACTION -> colors.error
                waitMinutes != null -> colors.warning
                metadata?.healthStatus == CredentialHealthStatus.READY -> colors.success
                else -> colors.textSecondary
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = resolveAuroraTranslationGlassContainerColor(colors, subtle = true),
                border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(colors)),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = (metadata?.alias.orEmpty()) + " · •••• " + metadata?.maskedSuffix.orEmpty(),
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                            if (healthLabel != null) {
                                AiTranslationStatusChip(label = healthLabel, tint = healthTint)
                            }
                        }
                        IconButton(
                            onClick = {
                                onAction(
                                    AiTranslationAction.EditorChanged(
                                        AiTranslationEditor.Credential(
                                            binding.credentialId,
                                            metadata?.alias.orEmpty(),
                                            draft.id,
                                            draft,
                                            editor.returnTo,
                                        ),
                                    ),
                                )
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                stringResource(AYMR.strings.ai_translation_edit),
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                changed(
                                    draft.copy(
                                        bindings = draft.bindings.filterNot { it.credentialId == binding.credentialId },
                                    ),
                                )
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(AYMR.strings.ai_translation_unlink_key),
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    AiTranslationToggleRow(
                        label = stringResource(AYMR.strings.ai_translation_enabled),
                        checked = binding.enabled,
                        onClick = {
                            changed(
                                draft.copy(
                                    bindings = draft.bindings.map {
                                        if (it.credentialId == binding.credentialId) {
                                            it.copy(enabled = !binding.enabled)
                                        } else {
                                            it
                                        }
                                    },
                                ),
                            )
                        },
                        contentPadding = PaddingValues(vertical = 4.dp),
                    )
                    Field(stringResource(AYMR.strings.ai_translation_priority), binding.priority, { value ->
                        changed(
                            draft.copy(
                                bindings = draft.bindings.map {
                                    if (it.credentialId == binding.credentialId) it.copy(priority = value) else it
                                },
                            ),
                        )
                    }, binding.toDomainOrNull() == null, numeric = true)
                    Field(stringResource(AYMR.strings.ai_translation_quota_group), binding.quotaGroupLabel, { value ->
                        changed(
                            draft.copy(
                                bindings = draft.bindings.map {
                                    if (it.credentialId ==
                                        binding.credentialId
                                    ) {
                                        it.copy(quotaGroupLabel = value)
                                    } else {
                                        it
                                    }
                                },
                            ),
                        )
                    })
                }
            }
        }
        AiTranslationSecondaryButton(
            text = stringResource(AYMR.strings.ai_translation_add_key),
            icon = Icons.Default.Add,
            onClick = {
                onAction(
                    AiTranslationAction.EditorChanged(
                        AiTranslationEditor.Credential(null, "", draft.id, draft, editor.returnTo),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        EditorHelperCaption(stringResource(AYMR.strings.ai_translation_group_hint))
        AiTranslationChipGroup(
            label = stringResource(AYMR.strings.ai_translation_distribution),
            options = CredentialRoutingMode.entries.map { it to routingTitle(it) },
            selected = draft.routingMode,
            onSelect = { changed(draft.copy(routingMode = it)) },
        )
        val probeStatus = state.probeStates[draft.id]
        val diagnosticsTint = probeTint(probeStatus, colors)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = resolveAuroraTranslationGlassContainerColor(colors, subtle = true),
            border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(colors)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(diagnosticsTint, CircleShape),
                        )
                        Text(
                            text = probeTitle(probeStatus),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                    }
                    AiTranslationSecondaryButton(
                        text = stringResource(AYMR.strings.ai_translation_test_short),
                        onClick = { onAction(AiTranslationAction.TestConnection(draft.id)) },
                        enabled = result != null && probeStatus != AiTranslationProbeStatus.RUNNING,
                        compact = true,
                    )
                }
                Text(
                    text = stringResource(AYMR.strings.ai_translation_probe_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        if (state.connections.any { it.id == draft.id }) {
            EditorDangerZone(
                label = stringResource(AYMR.strings.ai_translation_delete),
                onClick = {
                    onAction(
                        AiTranslationAction.EditorChanged(
                            AiTranslationEditor.DeleteConfirmation(AiTranslationDeleteKind.CONNECTION, draft.id),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun ProfileEditor(
    state: AiTranslationUiState,
    draft: AiProfileDraft,
    onAction: (AiTranslationAction) -> Unit,
    dismiss: () -> Unit,
) {
    val changed = { value: AiProfileDraft ->
        onAction(AiTranslationAction.EditorChanged(AiTranslationEditor.Profile(value)))
    }
    val result = draft.toDomainOrNull()
    val errors = draft.errors()
    val connection = state.connections.firstOrNull { it.id == draft.connectionId }
    var connectionPickerTrigger by remember { mutableIntStateOf(0) }
    EditorDialog(stringResource(AYMR.strings.ai_translation_profile_editor), dismiss, result != null, {
        result?.let {
            onAction(AiTranslationAction.SaveProfile(it, draft.pendingParallelism))
            dismiss()
        }
    }) {
        Field(
            stringResource(AYMR.strings.ai_translation_name),
            draft.name,
            { changed(draft.copy(name = it)) },
            AiTranslationFormError.NAME in errors,
        )
        AiTranslationPickerField(
            label = stringResource(AYMR.strings.ai_translation_connection),
            value = connection?.name.orEmpty(),
            options = state.connections.map { it.id to it.name },
            selectedOption = draft.connectionId.takeIf { it.isNotBlank() },
            emptyText = stringResource(AYMR.strings.ai_translation_empty_connections),
            emptyActionLabel = stringResource(AYMR.strings.ai_translation_add),
            onEmptyAction = { onAction(AiTranslationAction.EditConnection(null, returnTo = draft)) },
            openSignal = connectionPickerTrigger,
            onSelect = {
                changed(
                    draft.copy(
                        connectionId = it,
                        model = null,
                        discoveredReasoningContract = null,
                        reasoning = ReasoningChoice.ApiDefault,
                        reasoningBudget = "",
                        supportedFields = emptySet(),
                    ),
                )
            },
        )
        val aurora = AuroraTheme.colors
        val modelDisplayText = if (draft.connectionId.isNotBlank()) {
            draft.model?.exactModelId ?: stringResource(AYMR.strings.ai_translation_select_model_placeholder)
        } else {
            stringResource(AYMR.strings.ai_translation_select_connection_first)
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    when {
                        draft.connectionId.isNotBlank() ->
                            onAction(AiTranslationAction.EditorChanged(AiTranslationEditor.Models(draft)))
                        state.connections.isNotEmpty() -> connectionPickerTrigger += 1
                        else -> onAction(AiTranslationAction.EditConnection(null, returnTo = draft))
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = resolveAuroraTranslationGlassContainerColor(aurora, subtle = true),
            border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(AYMR.strings.ai_translation_model_picker),
                        style = MaterialTheme.typography.labelSmall,
                        color = aurora.textSecondary,
                    )
                    Text(
                        text = modelDisplayText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = aurora.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = aurora.textSecondary,
                )
            }
        }
        AiTranslationLanguagePairRow(
            sourceValue = draft.sourceLanguage,
            onSourceChange = { changed(draft.copy(sourceLanguage = it)) },
            targetValue = draft.targetLanguage,
            onTargetChange = { changed(draft.copy(targetLanguage = it)) },
            onSwap = {
                changed(
                    draft.copy(
                        sourceLanguage = draft.targetLanguage,
                        targetLanguage = draft.sourceLanguage,
                    ),
                )
            },
            isError = AiTranslationFormError.LANGUAGE in errors,
        )
        TranslationNotice(stringResource(AYMR.strings.ai_translation_profile_reader_hint))
        var showAdvanced by remember(draft.id) { mutableStateOf(false) }
        LaunchedEffect(errors) {
            if (AiTranslationFormError.GENERATION in errors) {
                showAdvanced = true
            }
        }
        val arrowRotation by animateFloatAsState(
            targetValue = if (showAdvanced) 180f else 0f,
            label = "advanced_chevron",
        )
        Surface(
            onClick = { showAdvanced = !showAdvanced },
            shape = RoundedCornerShape(16.dp),
            color = resolveAuroraTranslationGlassContainerColor(aurora, subtle = true),
            border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(AYMR.strings.ai_translation_advanced_defaults),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = aurora.textPrimary,
                    )
                    Text(
                        text = stringResource(AYMR.strings.ai_translation_advanced_defaults_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = aurora.textSecondary,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = aurora.textSecondary,
                    modifier = Modifier.rotate(arrowRotation),
                )
            }
        }
        AnimatedVisibility(visible = showAdvanced) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AiTranslationChipGroup(
                    label = stringResource(AYMR.strings.ai_translation_style),
                    options = listOf("PROFESSIONAL", "LITERARY", "CONVERSATIONAL", "VULGAR_18", "MINIMAL").map {
                        it to styleTitle(it)
                    },
                    selected = draft.styleId,
                    onSelect = { changed(draft.copy(styleId = it)) },
                )
                AiTranslationChipGroup(
                    label = stringResource(AYMR.strings.ai_translation_prompt_mode),
                    options = listOf(
                        "CLASSIC" to stringResource(AYMR.strings.ai_translation_mode_classic),
                        "ADULT_18" to stringResource(AYMR.strings.ai_translation_mode_adult),
                    ),
                    selected = draft.modeId,
                    onSelect = { changed(draft.copy(modeId = it)) },
                )
                AiTranslationReasoningSelector(
                    contract = draft.model?.reasoningContract,
                    discoveredContract = draft.discoveredReasoningContract ?: draft.model?.reasoningContract,
                    reasoning = draft.reasoning,
                    budgetInput = draft.reasoningBudget,
                    allowBudget = connection == null ||
                        connection.protocol == TranslationProtocol.GEMINI_GENERATE ||
                        connection.presetFlavor == PresetFlavor.OPENROUTER,
                    allowOverride = draft.model != null && connection?.presetFlavor != PresetFlavor.NVIDIA,
                    onSelect = { contract, reasoning, budget ->
                        changed(
                            draft.copy(
                                model = draft.model?.copy(
                                    reasoningContract = contract,
                                    generation = draft.model.generation.copy(reasoning = reasoning),
                                ),
                                reasoning = reasoning,
                                reasoningBudget = budget,
                            ),
                        )
                    },
                    onBudgetInputChange = { changed(draft.copy(reasoningBudget = it)) },
                )
                if (AiGenerationField.TEMPERATURE in draft.supportedFields) {
                    Field(
                        stringResource(AYMR.strings.ai_translation_temperature),
                        draft.temperature,
                        { changed(draft.copy(temperature = it)) },
                        AiTranslationFormError.GENERATION in errors,
                        numeric = true,
                    )
                }
                if (AiGenerationField.TOP_P in draft.supportedFields) {
                    Field(
                        stringResource(AYMR.strings.ai_translation_top_p),
                        draft.topP,
                        { changed(draft.copy(topP = it)) },
                        numeric = true,
                    )
                }
                if (AiGenerationField.TOP_K in draft.supportedFields) {
                    Field(
                        stringResource(AYMR.strings.ai_translation_top_k),
                        draft.topK,
                        { changed(draft.copy(topK = it)) },
                        numeric = true,
                    )
                }
                if (AiGenerationField.OUTPUT_TOKENS in draft.supportedFields) {
                    Field(
                        stringResource(AYMR.strings.ai_translation_output_tokens),
                        draft.maxOutputTokens,
                        { changed(draft.copy(maxOutputTokens = it)) },
                        numeric = true,
                    )
                }
                Field(
                    stringResource(AYMR.strings.ai_translation_modifiers),
                    draft.modifiersText,
                    { changed(draft.copy(modifiersText = it)) },
                )
                Field(
                    stringResource(AYMR.strings.ai_translation_custom_prompt),
                    draft.customText,
                    { changed(draft.copy(customText = it)) },
                    multiline = true,
                )
                AiTranslationSecondaryButton(
                    text = stringResource(AYMR.strings.ai_translation_recovery_editor),
                    onClick = {
                        onAction(
                            AiTranslationAction.EditorChanged(
                                AiTranslationEditor.Recovery(
                                    draft.id,
                                    draft.connectionId,
                                    AiRecoveryDraft(
                                        draft.execution,
                                        parallelism = draft.pendingParallelism ?: connection?.maxConcurrency ?: 2,
                                    ),
                                    draft,
                                ),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                EditorHelperCaption(stringResource(AYMR.strings.ai_translation_profile_hint))
            }
        }
        if (state.profiles.any { it.id == draft.id }) {
            EditorDangerZone(
                label = stringResource(AYMR.strings.ai_translation_delete),
                onClick = {
                    onAction(
                        AiTranslationAction.EditorChanged(
                            AiTranslationEditor.DeleteConfirmation(AiTranslationDeleteKind.PROFILE, draft.id),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
internal fun AiTranslationReasoningSelector(
    contract: ReasoningContract?,
    discoveredContract: ReasoningContract?,
    reasoning: ReasoningChoice,
    budgetInput: String,
    allowBudget: Boolean,
    allowOverride: Boolean,
    onSelect: (ReasoningContract, ReasoningChoice, String) -> Unit,
    onBudgetInputChange: (String) -> Unit,
    onApplyBudget: ((ReasoningContract.Budget, Int) -> Unit)? = null,
) {
    if (contract == null || contract == ReasoningContract.None) return
    val apiDefault = stringResource(AYMR.strings.ai_translation_api_default)
    val isOverriddenUnknown = discoveredContract == ReasoningContract.Unknown &&
        contract != ReasoningContract.Unknown
    if (discoveredContract == ReasoningContract.Unknown) {
        TranslationNotice(
            stringResource(
                if (isOverriddenUnknown) {
                    AYMR.strings.ai_translation_reason_unverified_hint
                } else {
                    AYMR.strings.ai_translation_unknown_model
                },
            ),
        )
        if (allowOverride) {
            val selectedOverride = when (contract) {
                is ReasoningContract.Levels -> ReasoningContract.Levels(listOf("minimal", "low", "medium", "high"))
                ReasoningContract.Toggle -> ReasoningContract.Toggle
                is ReasoningContract.Budget -> ReasoningContract.Budget(
                    0,
                    32768,
                    canDisable = true,
                    canUseDynamic = true,
                )
                else -> ReasoningContract.Unknown
            }
            val overrideOptions = buildList {
                add(ReasoningContract.Unknown to apiDefault)
                add(
                    ReasoningContract.Levels(listOf("minimal", "low", "medium", "high")) to
                        stringResource(AYMR.strings.ai_translation_reason_mode_levels),
                )
                add(ReasoningContract.Toggle to stringResource(AYMR.strings.ai_translation_reason_mode_toggle))
                if (allowBudget) {
                    add(
                        ReasoningContract.Budget(0, 32768, canDisable = true, canUseDynamic = true) to
                            stringResource(AYMR.strings.ai_translation_reason_budget),
                    )
                }
            }
            AiTranslationChipGroup(
                label = stringResource(AYMR.strings.ai_translation_reason_override),
                options = overrideOptions,
                selected = selectedOverride,
                onSelect = { nextContract ->
                    when (nextContract) {
                        is ReasoningContract.Levels -> onSelect(
                            nextContract,
                            ReasoningChoice.Level("low"),
                            "",
                        )
                        ReasoningContract.Toggle -> onSelect(
                            nextContract,
                            ReasoningChoice.Toggle(true),
                            "",
                        )
                        is ReasoningContract.Budget -> onSelect(
                            nextContract,
                            ReasoningChoice.Budget(8192),
                            "8192",
                        )
                        else -> onSelect(ReasoningContract.Unknown, ReasoningChoice.ApiDefault, "")
                    }
                },
            )
        }
        if (contract == ReasoningContract.Unknown) return
    }
    when (contract) {
        ReasoningContract.Toggle -> AiTranslationChipGroup(
            label = stringResource(AYMR.strings.ai_translation_reasoning),
            options = listOf(
                ReasoningChoice.ApiDefault to apiDefault,
                ReasoningChoice.Toggle(false) to stringResource(AYMR.strings.ai_translation_reason_off),
                ReasoningChoice.Toggle(true) to stringResource(AYMR.strings.ai_translation_reason_on),
            ),
            selected = reasoning,
            onSelect = { choice -> onSelect(contract, choice, "") },
        )
        is ReasoningContract.Levels -> AiTranslationChipGroup(
            label = stringResource(AYMR.strings.ai_translation_reasoning),
            options = listOf(ReasoningChoice.ApiDefault to apiDefault) + contract.options.map {
                ReasoningChoice.Level(it) to reasoningTitle(it)
            },
            selected = reasoning,
            onSelect = { choice -> onSelect(contract, choice, "") },
        )
        is ReasoningContract.Budget -> {
            val defaultBudget = when {
                contract.accepts(contract.minimum) -> contract.minimum
                contract.canUseDynamic -> -1
                else -> contract.minimum.coerceAtLeast(1)
            }
            AiTranslationChipGroup(
                label = stringResource(AYMR.strings.ai_translation_reasoning),
                options = listOf(
                    false to apiDefault,
                    true to stringResource(AYMR.strings.ai_translation_reason_budget),
                ),
                selected = reasoning is ReasoningChoice.Budget,
                onSelect = { useBudget ->
                    if (useBudget) {
                        onSelect(contract, ReasoningChoice.Budget(defaultBudget), defaultBudget.toString())
                    } else {
                        onSelect(contract, ReasoningChoice.ApiDefault, "")
                    }
                },
            )
            if (reasoning is ReasoningChoice.Budget) {
                val parsed = budgetInput.trim().toIntOrNull()
                val invalid = parsed == null || !contract.accepts(parsed)
                Field(
                    stringResource(AYMR.strings.ai_translation_reason_budget),
                    budgetInput,
                    onBudgetInputChange,
                    error = invalid,
                    numeric = true,
                )
                if (onApplyBudget != null) {
                    AiTranslationSecondaryButton(
                        text = stringResource(AYMR.strings.ai_translation_save),
                        onClick = { parsed?.let { onApplyBudget(contract, it) } },
                        enabled = !invalid && parsed != reasoning.tokens,
                    )
                }
            }
            val colors = AuroraTheme.colors
            Text(
                stringResource(AYMR.strings.ai_translation_reason_range, contract.minimum, contract.maximum),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            if (contract.canDisable) {
                Text(
                    stringResource(AYMR.strings.ai_translation_reason_disable),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (contract.canUseDynamic) {
                Text(
                    stringResource(AYMR.strings.ai_translation_reason_dynamic),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        ReasoningContract.None, ReasoningContract.Unknown -> Unit
    }
}

@Composable
private fun ModelEditor(
    state: AiTranslationUiState,
    editor: AiTranslationEditor.Models,
    onAction: (AiTranslationAction) -> Unit,
    dismiss: () -> Unit,
) {
    val colors = AuroraTheme.colors
    LaunchedEffect(editor.profile.connectionId) {
        if (editor.profile.connectionId.isNotBlank() &&
            state.catalogConnectionId != editor.profile.connectionId &&
            !state.catalogLoading
        ) {
            onAction(AiTranslationAction.RefreshModels(editor.profile.connectionId))
        }
    }
    AuroraFrostDialog(
        onDismiss = {
            if (editor.applyImmediately) {
                dismiss()
            } else {
                onAction(AiTranslationAction.EditorChanged(AiTranslationEditor.Profile(editor.profile)))
            }
        },
        title = stringResource(AYMR.strings.ai_translation_model_picker),
        closeLabel = stringResource(AYMR.strings.ai_translation_close),
        contentSpacing = 12.dp,
    ) {
        Field(
            stringResource(AYMR.strings.ai_translation_model_search),
            editor.query,
            { onAction(AiTranslationAction.EditorChanged(editor.copy(query = it))) },
        )
        if (state.catalogLoading) {
            LinearProgressIndicator(
                color = if (colors.isEInk) colors.textPrimary else colors.accent,
                trackColor = colors.divider,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val filtered = state.models.filter {
            state.catalogConnectionId == editor.profile.connectionId &&
                (it.label.contains(editor.query, true) || it.selection.exactModelId.contains(editor.query, true))
        }
        filtered.forEach { option ->
            val selected = option.selection.exactModelId == editor.profile.model?.exactModelId
            AiTranslationOptionCard(
                title = option.label,
                subtitle = option.selection.exactModelId,
                selected = selected,
                onClick = {
                    if (editor.applyImmediately) {
                        onAction(AiTranslationAction.SelectProfileModel(editor.profile.id, option.selection))
                    } else {
                        onAction(
                            AiTranslationAction.EditorChanged(
                                AiTranslationEditor.Profile(editor.profile.selectModelWithAutoName(option)),
                            ),
                        )
                    }
                },
            )
        }
        val trimmedQuery = editor.query.trim()
        if (trimmedQuery.isNotEmpty() && filtered.none { it.selection.exactModelId == trimmedQuery }) {
            val manual = runCatching { ModelSelection(trimmedQuery) }.getOrNull()
            AiTranslationSecondaryButton(
                text = stringResource(AYMR.strings.ai_translation_add_model_id, trimmedQuery),
                onClick = {
                    manual?.let {
                        if (editor.applyImmediately) {
                            onAction(AiTranslationAction.SelectProfileModel(editor.profile.id, it))
                        } else {
                            onAction(
                                AiTranslationAction.EditorChanged(
                                    AiTranslationEditor.Profile(
                                        editor.profile.selectModelWithAutoName(
                                            AiTranslationModelUi(it, it.exactModelId),
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                },
                enabled = manual != null,
                modifier = Modifier.fillMaxWidth(),
            )
            TranslationNotice(stringResource(AYMR.strings.ai_translation_unknown_model))
        }
        AiTranslationPrimaryButton(
            text = stringResource(AYMR.strings.ai_translation_refresh_models),
            onClick = { onAction(AiTranslationAction.RefreshModels(editor.profile.connectionId)) },
            enabled = editor.profile.connectionId.isNotBlank() && !state.catalogLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun AiProfileDraft.selectModelWithAutoName(option: AiTranslationModelUi): AiProfileDraft =
    selectModel(option).let { next ->
        if (next.name.isBlank()) {
            next.copy(name = option.label.takeIf { it.isNotBlank() } ?: option.selection.exactModelId)
        } else {
            next
        }
    }

@Composable
private fun CredentialEditor(
    state: AiTranslationUiState,
    editor: AiTranslationEditor.Credential,
    onAction: (AiTranslationAction) -> Unit,
    onSave: (String?, String, String?) -> Unit,
    dismiss: () -> Unit,
) {
    val secret = remember(editor.id, editor.connectionId) { AiCredentialSecretDraft() }
    DisposableEffect(secret) { onDispose { secret.clear() } }
    val close = {
        secret.clear()
        if (editor.returnTo != null) {
            onAction(
                AiTranslationAction.EditorChanged(
                    AiTranslationEditor.Connection(editor.returnTo, editor.returnToProfile),
                ),
            )
        } else {
            dismiss()
        }
    }
    EditorDialog(
        stringResource(AYMR.strings.ai_translation_credential_editor),
        close,
        state.vaultAvailable && !state.editorSaving && secret.canSubmit(editor.id, editor.alias),
        {
            val value = secret.takeAndClear()
            // The caller returns to the public connection draft after verified encrypted save.
            onSave(editor.id, editor.alias, value)
        },
    ) {
        Field(
            stringResource(AYMR.strings.ai_translation_alias),
            editor.alias,
            { onAction(AiTranslationAction.EditorChanged(editor.copy(alias = it))) },
        )
        AiTranslationOutlinedTextField(
            value = secret.value,
            onValueChange = secret::update,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(stringResource(AYMR.strings.ai_translation_secret)) },
            placeholder = { Text(stringResource(AYMR.strings.ai_translation_secret_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        TranslationNotice(stringResource(AYMR.strings.ai_translation_key_local))
        if (!state.vaultAvailable) TranslationNotice(stringResource(AYMR.strings.ai_translation_vault_unavailable))
        if (editor.id != null) {
            EditorDangerZone(
                label = stringResource(AYMR.strings.ai_translation_delete),
                onClick = {
                    secret.clear()
                    onAction(
                        AiTranslationAction.EditorChanged(
                            AiTranslationEditor.DeleteConfirmation(AiTranslationDeleteKind.CREDENTIAL, editor.id),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun RecoveryEditor(
    editor: AiTranslationEditor.Recovery,
    onAction: (AiTranslationAction) -> Unit,
    dismiss: () -> Unit,
) {
    val draft = editor.draft
    val result = draft.toDomainOrNull()
    val changed = { value: AiRecoveryDraft -> onAction(AiTranslationAction.EditorChanged(editor.copy(draft = value))) }
    val close = {
        if (editor.returnTo != null) {
            onAction(
                AiTranslationAction.EditorChanged(
                    AiTranslationEditor.Profile(editor.returnTo),
                ),
            )
        } else {
            dismiss()
        }
    }
    EditorDialog(stringResource(AYMR.strings.ai_translation_recovery_editor), close, result != null, {
        result?.let { policy ->
            onAction(AiTranslationAction.SaveRecovery(editor.profileId, policy, editor.connectionId, draft.parallelism))
            if (editor.returnTo == null) {
                dismiss()
            }
        }
    }) {
        ProfileRetryMode.entries.forEach { mode ->
            AiTranslationOptionCard(
                title = retryTitle(mode),
                subtitle = retryHint(mode),
                selected = draft.policy.retryMode == mode,
                onClick = { changed(draft.copy(policy = draft.policy.copy(retryMode = mode))) },
            )
        }
        Field(
            stringResource(AYMR.strings.ai_translation_request_limit),
            draft.maxRequests,
            { changed(draft.copy(maxRequests = it)) },
            result == null,
            numeric = true,
        )
        EditorHelperCaption(stringResource(AYMR.strings.ai_translation_reservation_hint))
        Field(
            stringResource(AYMR.strings.ai_translation_token_limit),
            draft.maxReservedTokens,
            { changed(draft.copy(maxReservedTokens = it)) },
            numeric = true,
        )
        AiTranslationChipGroup(
            label = stringResource(AYMR.strings.ai_translation_parallelism),
            options = listOf(1, 2, 4, 8).map { it to it.toString() },
            selected = draft.parallelism,
            onSelect = { changed(draft.copy(parallelism = it)) },
        )
        TranslationNotice(
            stringResource(
                if (draft.policy.retryMode == ProfileRetryMode.WAIT_FOR_SERVICE) {
                    AYMR.strings.ai_translation_wait_cost_hint
                } else {
                    AYMR.strings.ai_translation_connection_hint
                },
            ),
        )
        EditorHelperCaption(stringResource(AYMR.strings.ai_translation_no_fallback))
    }
}

@Composable
internal fun flavorTitle(flavor: PresetFlavor): String = when (flavor) {
    PresetFlavor.NONE -> stringResource(AYMR.strings.ai_translation_flavor_none)
    PresetFlavor.GEMINI_PRIVATE -> "Gemini API"
    PresetFlavor.OPENROUTER -> "OpenRouter"
    PresetFlavor.DEEPSEEK -> "DeepSeek"
    PresetFlavor.MISTRAL -> "Mistral"
    PresetFlavor.NVIDIA -> "NVIDIA NIM"
    PresetFlavor.OLLAMA -> stringResource(AYMR.strings.ai_translation_flavor_ollama)
}

@Composable
internal fun protocolTitle(protocol: TranslationProtocol): String = when (protocol) {
    TranslationProtocol.OPENAI_CHAT -> stringResource(AYMR.strings.ai_translation_protocol_openai)
    TranslationProtocol.GEMINI_GENERATE -> stringResource(AYMR.strings.ai_translation_protocol_gemini)
    TranslationProtocol.OLLAMA_NATIVE -> stringResource(AYMR.strings.ai_translation_protocol_ollama)
}

@Composable
private fun authTitle(auth: ProfileAuth): String = when (auth) {
    ProfileAuth.BEARER -> stringResource(AYMR.strings.ai_translation_auth_bearer)
    ProfileAuth.X_GOOG_API_KEY -> stringResource(AYMR.strings.ai_translation_auth_google)
    ProfileAuth.NONE -> stringResource(AYMR.strings.ai_translation_auth_none)
}

@Composable
internal fun routingTitle(mode: CredentialRoutingMode): String = stringResource(
    if (mode == CredentialRoutingMode.PRIORITY) {
        AYMR.strings.ai_translation_priority_mode
    } else {
        AYMR.strings.ai_translation_round_robin
    },
)

@Composable
internal fun retryTitle(mode: ProfileRetryMode): String = stringResource(
    when (mode) {
        ProfileRetryMode.OFF -> AYMR.strings.ai_translation_retry_off
        ProfileRetryMode.BOUNDED -> AYMR.strings.ai_translation_retry_bounded
        ProfileRetryMode.WAIT_FOR_SERVICE -> AYMR.strings.ai_translation_retry_wait
    },
)

@Composable
internal fun retryHint(mode: ProfileRetryMode): String = stringResource(
    when (mode) {
        ProfileRetryMode.OFF -> AYMR.strings.ai_translation_retry_off_hint
        ProfileRetryMode.BOUNDED -> AYMR.strings.ai_translation_retry_bounded_hint
        ProfileRetryMode.WAIT_FOR_SERVICE -> AYMR.strings.ai_translation_retry_wait_hint
    },
)

@Composable
internal fun styleTitle(id: String): String = when (id) {
    "PROFESSIONAL" -> stringResource(AYMR.strings.ai_translation_style_professional)
    "LITERARY" -> stringResource(AYMR.strings.ai_translation_style_literary)
    "CONVERSATIONAL" -> stringResource(AYMR.strings.ai_translation_style_conversational)
    "VULGAR_18" -> stringResource(AYMR.strings.ai_translation_style_vulgar)
    "MINIMAL" -> stringResource(AYMR.strings.ai_translation_style_minimal)
    else -> id
}

@Composable
private fun reasoningTitle(id: String): String = when (id) {
    "none", "off" -> stringResource(AYMR.strings.ai_translation_reason_off)
    "minimal" -> stringResource(AYMR.strings.ai_translation_reason_minimal)
    "low" -> stringResource(AYMR.strings.ai_translation_reason_low)
    "medium" -> stringResource(AYMR.strings.ai_translation_reason_medium)
    "high" -> stringResource(AYMR.strings.ai_translation_reason_high)
    else -> id
}
