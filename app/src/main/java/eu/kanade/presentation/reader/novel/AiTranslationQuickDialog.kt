package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.translation.AiTranslationAction
import eu.kanade.presentation.more.settings.translation.AiTranslationCustomPromptDialog
import eu.kanade.presentation.more.settings.translation.AiTranslationDangerButton
import eu.kanade.presentation.more.settings.translation.AiTranslationJobCommand
import eu.kanade.presentation.more.settings.translation.AiTranslationJobProgress
import eu.kanade.presentation.more.settings.translation.AiTranslationJobStatus
import eu.kanade.presentation.more.settings.translation.AiTranslationLanguagePairRow
import eu.kanade.presentation.more.settings.translation.AiTranslationOutlinedTextField
import eu.kanade.presentation.more.settings.translation.AiTranslationPrimaryButton
import eu.kanade.presentation.more.settings.translation.AiTranslationProbeStatus
import eu.kanade.presentation.more.settings.translation.AiTranslationProfileCard
import eu.kanade.presentation.more.settings.translation.AiTranslationQuickUiState
import eu.kanade.presentation.more.settings.translation.AiTranslationReasoningSelector
import eu.kanade.presentation.more.settings.translation.AiTranslationSecondaryButton
import eu.kanade.presentation.more.settings.translation.AiTranslationStatusChip
import eu.kanade.presentation.more.settings.translation.AiTranslationTerminalLogsSection
import eu.kanade.presentation.more.settings.translation.AiTranslationToggleRow
import eu.kanade.presentation.more.settings.translation.AiTranslatorChoiceChip
import eu.kanade.presentation.more.settings.translation.AiTranslatorFilterChip
import eu.kanade.presentation.more.settings.translation.TranslationNotice
import eu.kanade.presentation.more.settings.translation.aiDeepSeekGenerationPresets
import eu.kanade.presentation.more.settings.translation.aiDefaultGenerationPresets
import eu.kanade.presentation.more.settings.translation.aiTranslationWaitMinutes
import eu.kanade.presentation.more.settings.translation.flavorTitle
import eu.kanade.presentation.more.settings.translation.jobTint
import eu.kanade.presentation.more.settings.translation.jobTitle
import eu.kanade.presentation.more.settings.translation.probeTint
import eu.kanade.presentation.more.settings.translation.probeTitle
import eu.kanade.presentation.more.settings.translation.protocolTitle
import eu.kanade.presentation.more.settings.translation.requestJobCommand
import eu.kanade.presentation.more.settings.translation.resolveAuroraTranslationGlassBorderBrush
import eu.kanade.presentation.more.settings.translation.resolveAuroraTranslationGlassContainerColor
import eu.kanade.presentation.more.settings.translation.resolveSelectedGenerationPresetId
import eu.kanade.presentation.more.settings.translation.retryHint
import eu.kanade.presentation.more.settings.translation.retryTitle
import eu.kanade.presentation.more.settings.translation.routingTitle
import eu.kanade.presentation.more.settings.translation.supportsAdultPromptMode
import eu.kanade.presentation.reader.components.AuroraReaderSheet
import eu.kanade.presentation.reader.settings.AuroraChipFlow
import eu.kanade.presentation.reader.settings.AuroraFieldLabel
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraTabRow
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptModifiers
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiThinkingContract
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationStylePresets
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveGeminiModelCapabilities
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileRetryMode
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale
import kotlin.math.roundToInt

/** Unified 3-tab Aurora reader sheet backed directly by TranslationProfile, ConnectionProfile and Queue state. */
@Composable
fun AiTranslationQuickDialog(
    state: AiTranslationQuickUiState,
    onAction: (AiTranslationAction) -> Unit,
    onDismiss: () -> Unit,
    isTranslationVisible: Boolean = false,
    hasTranslationCache: Boolean = false,
    onToggleTranslationVisibility: () -> Unit = {},
    onClearChapterCache: () -> Unit = {},
    onClearAllCache: () -> Unit = {},
    autoTranslateEnglishSource: Boolean = false,
    onSetAutoTranslateEnglishSource: (Boolean) -> Unit = {},
    prefetchNextChapterTranslation: Boolean = false,
    onSetPrefetchNextChapterTranslation: (Boolean) -> Unit = {},
    disableCache: Boolean = false,
    onSetDisableCache: (Boolean) -> Unit = {},
    privatePythonLikeMode: Boolean = false,
    onSetPrivatePythonLikeMode: (Boolean) -> Unit = {},
    logs: List<String> = emptyList(),
    onClearLogs: () -> Unit = {},
) {
    val tabTitles = persistentListOf(
        stringResource(MR.strings.ai_translator_tab_basics),
        stringResource(MR.strings.ai_translator_tab_prompt),
        stringResource(MR.strings.ai_translator_tab_more),
    )
    val pagerState = rememberPagerState { tabTitles.size }
    val scope = rememberCoroutineScope()
    val pageMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.76f).dp
    var showCustomPromptDialog by remember { mutableStateOf(false) }

    val profile = state.profile
    val connection = state.connection

    AuroraReaderSheet(onDismissRequest = onDismiss) {
        AuroraTabRow(
            titles = tabTitles,
            selectedIndex = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
        )

        HorizontalPager(
            modifier = Modifier.heightIn(max = pageMaxHeight),
            state = pagerState,
            verticalAlignment = Alignment.Top,
            beyondViewportPageCount = 0,
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = pageMaxHeight)
                    .padding(vertical = 6.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (page) {
                    0 -> QuickBasicsTab(
                        state = state,
                        profile = profile,
                        connection = connection,
                        isTranslationVisible = isTranslationVisible,
                        hasTranslationCache = hasTranslationCache,
                        onToggleTranslationVisibility = onToggleTranslationVisibility,
                        onClearChapterCache = onClearChapterCache,
                        onOpenMoreTab = { scope.launch { pagerState.animateScrollToPage(2) } },
                        onAction = onAction,
                    )
                    1 -> QuickPromptTab(
                        state = state,
                        profile = profile,
                        connection = connection,
                        onOpenCustomPrompt = { showCustomPromptDialog = true },
                        onAction = onAction,
                    )
                    else -> QuickMoreTab(
                        state = state,
                        connection = connection,
                        autoTranslateEnglishSource = autoTranslateEnglishSource,
                        onSetAutoTranslateEnglishSource = onSetAutoTranslateEnglishSource,
                        prefetchNextChapterTranslation = prefetchNextChapterTranslation,
                        onSetPrefetchNextChapterTranslation = onSetPrefetchNextChapterTranslation,
                        disableCache = disableCache,
                        onSetDisableCache = onSetDisableCache,
                        privatePythonLikeMode = privatePythonLikeMode,
                        onSetPrivatePythonLikeMode = onSetPrivatePythonLikeMode,
                        onClearAllCache = onClearAllCache,
                        logs = logs,
                        onClearLogs = onClearLogs,
                        onAction = onAction,
                    )
                }
            }
        }
    }

    if (showCustomPromptDialog && profile != null) {
        AiTranslationCustomPromptDialog(
            initialText = profile.prompt.customText,
            onDismiss = { showCustomPromptDialog = false },
            onClear = {
                onAction(AiTranslationAction.SetProfilePrompt(profile.id, profile.prompt.copy(customText = "")))
                showCustomPromptDialog = false
            },
            onSave = { updated ->
                onAction(AiTranslationAction.SetProfilePrompt(profile.id, profile.prompt.copy(customText = updated)))
                showCustomPromptDialog = false
            },
        )
    }
}

@Composable
private fun QuickBasicsTab(
    state: AiTranslationQuickUiState,
    profile: TranslationProfile?,
    connection: ConnectionProfile?,
    isTranslationVisible: Boolean,
    hasTranslationCache: Boolean,
    onToggleTranslationVisibility: () -> Unit,
    onClearChapterCache: () -> Unit,
    onOpenMoreTab: () -> Unit,
    onAction: (AiTranslationAction) -> Unit,
) {
    val aurora = AuroraTheme.colors
    val job = state.job
    val paused = job?.effectiveStatus == AiTranslationJobStatus.PAUSED
    val active = job?.effectiveStatus in setOf(
        AiTranslationJobStatus.QUEUED,
        AiTranslationJobStatus.RUNNING,
        AiTranslationJobStatus.WAITING,
    )

    // 1. Hero Status & Actions
    AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_ai_translator_status_title)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (state.chapterLabel.isNotBlank()) {
                        Text(
                            text = state.chapterLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = aurora.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (state.sourceLabel.isNotBlank()) {
                        Text(
                            text = state.sourceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = aurora.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val badgeLabel = if (job != null) {
                    jobTitle(job)
                } else if (state.canStart) {
                    stringResource(AYMR.strings.ai_translation_key_ready)
                } else {
                    stringResource(AYMR.strings.ai_translation_key_required)
                }
                val badgeTint = when {
                    job != null -> jobTint(job, aurora)
                    state.canStart -> aurora.success
                    else -> aurora.warning
                }
                AiTranslationStatusChip(label = badgeLabel, tint = badgeTint)
            }

            if (state.loading) {
                LinearProgressIndicator(
                    color = if (aurora.isEInk) aurora.textPrimary else aurora.accent,
                    trackColor = aurora.divider,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (job != null) {
                AiTranslationJobProgress(job, state.nowMillis)
            }

            val matchesProfile = state.jobMatchesCurrentProfile
            val canRetrySameProfile = job != null && !job.needsAction && matchesProfile
            val isCompletedSameProfile = job?.effectiveStatus == AiTranslationJobStatus.COMPLETED && matchesProfile
            val primaryEnabled = when {
                paused || active -> true
                isCompletedSameProfile -> profile != null && state.canStart && !state.loading
                canRetrySameProfile -> state.canStart && !state.loading
                else ->
                    profile != null && state.canStart && !state.loading &&
                        (job?.needsAction != true || !matchesProfile)
            }
            val primaryLabel = stringResource(
                when {
                    paused -> AYMR.strings.ai_translation_resume
                    active -> AYMR.strings.ai_translation_pause
                    isCompletedSameProfile -> AYMR.strings.ai_translation_retranslate
                    canRetrySameProfile -> AYMR.strings.ai_translation_retry_missing
                    else -> AYMR.strings.ai_translation_translate_chapter
                },
            )
            val primaryIcon = when {
                paused -> Icons.Filled.PlayArrow
                active -> Icons.Filled.Pause
                isCompletedSameProfile || canRetrySameProfile -> Icons.Filled.Refresh
                else -> Icons.Filled.Translate
            }
            val onPrimary = {
                when {
                    paused && job != null -> onAction(
                        AiTranslationAction.RequestJobCommand(job.id, AiTranslationJobCommand.RESUME),
                    )
                    active && job != null -> onAction(
                        AiTranslationAction.RequestJobCommand(job.id, AiTranslationJobCommand.PAUSE),
                    )
                    isCompletedSameProfile && profile != null -> onAction(
                        AiTranslationAction.TranslateCurrentChapter(profile.id, forceRetranslate = true),
                    )
                    canRetrySameProfile && job != null -> onAction(
                        AiTranslationAction.RequestJobCommand(job.id, AiTranslationJobCommand.RETRY_MISSING),
                    )
                    profile != null -> onAction(
                        AiTranslationAction.TranslateCurrentChapter(
                            profile.id,
                            forceRetranslate = job != null,
                        ),
                    )
                }
            }

            AiTranslationPrimaryButton(
                text = primaryLabel,
                onClick = onPrimary,
                enabled = primaryEnabled,
                icon = primaryIcon,
                modifier = Modifier.fillMaxWidth(),
            )

            val utilityButtons = buildList<Pair<String, () -> Unit>> {
                if ((active || paused) && job != null) {
                    add(
                        stringResource(AYMR.strings.ai_translation_cancel) to {
                            requestJobCommand(job.id, AiTranslationJobCommand.CANCEL, onAction)
                        },
                    )
                }
                if (job?.needsAction == true) {
                    add(
                        stringResource(AYMR.strings.ai_translation_fix_connection) to {
                            if (connection != null) {
                                onAction(AiTranslationAction.EditConnection(connection.id))
                            } else {
                                onOpenMoreTab()
                            }
                        },
                    )
                }
                if (hasTranslationCache) {
                    add(
                        stringResource(
                            if (isTranslationVisible) {
                                AYMR.strings.ai_translation_show_original
                            } else {
                                AYMR.strings.ai_translation_show_translation
                            },
                        ) to onToggleTranslationVisibility,
                    )
                    add(
                        stringResource(AYMR.strings.novel_reader_ai_translator_clear_chapter_cache) to
                            onClearChapterCache,
                    )
                }
            }

            utilityButtons.chunked(2).forEach { rowButtons ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowButtons.forEach { (label, onClick) ->
                        AiTranslationSecondaryButton(
                            text = label,
                            onClick = onClick,
                            compact = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }

    // 2. Variant A — 2x2 Glass Profile Cards
    AuroraGlassSection(
        title = stringResource(AYMR.strings.ai_translation_profiles),
        headerAction = {
            AiTranslatorChoiceChip(
                text = stringResource(AYMR.strings.ai_translation_new_profile),
                selected = false,
                onClick = { onAction(AiTranslationAction.EditProfile(null)) },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(AYMR.strings.ai_translation_profile_hint),
                style = MaterialTheme.typography.labelSmall,
                color = aurora.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.profiles.isEmpty()) {
                TranslationNotice(stringResource(AYMR.strings.ai_translation_missing_profile))
                AiTranslationPrimaryButton(
                    text = stringResource(AYMR.strings.ai_translation_new_profile),
                    icon = Icons.Default.Add,
                    onClick = { onAction(AiTranslationAction.EditProfile(null)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                state.profiles.chunked(2).forEach { rowProfiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowProfiles.forEach { item ->
                            val itemConn = state.connections.firstOrNull { it.id == item.connectionId }
                            val enabledCreds = itemConn?.credentialBindings.orEmpty().filter { it.enabled }
                            val anyCredNeedsAction = enabledCreds.isNotEmpty() && enabledCreds.all { binding ->
                                state.credentials.firstOrNull { it.id == binding.credentialId }?.healthStatus ==
                                    CredentialHealthStatus.NEEDS_ACTION
                            }
                            val ready = itemConn != null &&
                                (itemConn.auth == ProfileAuth.NONE || enabledCreds.isNotEmpty()) &&
                                !anyCredNeedsAction
                            AiTranslationProfileCard(
                                title = item.name,
                                subtitle = item.exactModelId,
                                statusLabel = itemConn?.name ?: "—",
                                statusReady = ready,
                                selected = item.id == profile?.id,
                                onClick = { onAction(AiTranslationAction.SelectCurrentProfile(item.id)) },
                                onEditClick = { onAction(AiTranslationAction.EditProfile(item.id)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowProfiles.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (profile != null) {
        // 3. Languages with quick Swap
        AuroraGlassSection(title = stringResource(AYMR.strings.ai_translation_languages)) {
            var sourceInput by remember(profile.id, profile.languages.source) {
                mutableStateOf(profile.languages.source)
            }
            var targetInput by remember(profile.id, profile.languages.target) {
                mutableStateOf(profile.languages.target)
            }
            LaunchedEffect(profile.id, sourceInput, targetInput) {
                val src = sourceInput.trim()
                val dst = targetInput.trim()
                if (src.isNotEmpty() && dst.isNotEmpty() &&
                    (src != profile.languages.source || dst != profile.languages.target)
                ) {
                    delay(350)
                    onAction(AiTranslationAction.UpdateProfileLanguages(profile.id, src, dst))
                }
            }
            AiTranslationLanguagePairRow(
                sourceValue = sourceInput,
                onSourceChange = { sourceInput = it },
                targetValue = targetInput,
                onTargetChange = { targetInput = it },
                onSwap = {
                    val prevSrc = sourceInput
                    val prevDst = targetInput
                    val nextSrc = prevDst.trim()
                    val nextDst = prevSrc.trim()
                    sourceInput = prevDst
                    targetInput = prevSrc
                    if (nextSrc.isNotEmpty() && nextDst.isNotEmpty()) {
                        onAction(AiTranslationAction.UpdateProfileLanguages(profile.id, nextSrc, nextDst))
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        // 4. Model & Thinking
        AuroraGlassSection(title = stringResource(AYMR.strings.ai_translation_model_picker)) {
            AuroraFieldLabel(stringResource(AYMR.strings.ai_translation_model_picker_summary))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onAction(AiTranslationAction.OpenProfileModelPicker(profile.id)) },
                    shape = RoundedCornerShape(16.dp),
                    color = resolveAuroraTranslationGlassContainerColor(aurora, subtle = true),
                    border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora)),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
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
                                text = profile.exactModelId.ifBlank {
                                    stringResource(AYMR.strings.ai_translation_select_model_placeholder)
                                },
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
                IconButton(
                    onClick = { connection?.let { onAction(AiTranslationAction.RefreshModels(it.id)) } },
                    enabled = connection != null && !state.catalogLoading,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (state.catalogLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = if (aurora.isEInk) aurora.textPrimary else aurora.accent,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(AYMR.strings.ai_translation_refresh_models),
                            tint = if (aurora.isEInk) aurora.textPrimary else aurora.accent,
                        )
                    }
                }
            }

            val cachedContract = state.models.firstOrNull {
                state.catalogConnectionId == profile.connectionId &&
                    it.selection.exactModelId == profile.exactModelId
            }?.selection?.reasoningContract
            val discoveredContract =
                cachedContract ?: if (connection?.protocol == TranslationProtocol.GEMINI_GENERATE) {
                    when (val geminiContract = resolveGeminiModelCapabilities(profile.exactModelId).thinking) {
                        GeminiThinkingContract.Unknown -> ReasoningContract.Unknown
                        GeminiThinkingContract.Unsupported -> ReasoningContract.None
                        GeminiThinkingContract.Toggle -> ReasoningContract.Toggle
                        is GeminiThinkingContract.Levels -> ReasoningContract.Levels(geminiContract.options)
                        is GeminiThinkingContract.TokenBudget -> ReasoningContract.Budget(
                            geminiContract.minimum,
                            geminiContract.maximum,
                            geminiContract.canDisable,
                            geminiContract.canUseDynamic,
                        )
                    }
                } else {
                    profile.model.reasoningContract
                }
            var budgetInput by remember(profile.id, profile.exactModelId, profile.generation.reasoning) {
                mutableStateOf(
                    (profile.generation.reasoning as? ReasoningChoice.Budget)?.tokens?.toString().orEmpty(),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiTranslationReasoningSelector(
                    contract = profile.model.reasoningContract,
                    discoveredContract = discoveredContract,
                    reasoning = profile.generation.reasoning,
                    budgetInput = budgetInput,
                    allowBudget = connection == null ||
                        connection.protocol == TranslationProtocol.GEMINI_GENERATE ||
                        connection.presetFlavor == PresetFlavor.OPENROUTER,
                    allowOverride = connection?.presetFlavor != PresetFlavor.NVIDIA,
                    onSelect = { nextContract, nextReasoning, nextBudget ->
                        budgetInput = nextBudget
                        onAction(
                            AiTranslationAction.SetProfileReasoning(
                                profile.id,
                                nextContract,
                                nextReasoning,
                            ),
                        )
                    },
                    onBudgetInputChange = { budgetInput = it },
                    onApplyBudget = { budgetContract, tokens ->
                        onAction(
                            AiTranslationAction.SetProfileReasoning(
                                profile.id,
                                budgetContract,
                                ReasoningChoice.Budget(tokens),
                            ),
                        )
                    },
                )
            }
        }

        // 5. Recovery & 503 + Limits modal link
        AuroraGlassSection(title = stringResource(AYMR.strings.ai_translation_recovery_503_title)) {
            AuroraChipFlow {
                ProfileRetryMode.entries.forEach { mode ->
                    val chipLabel = when (mode) {
                        ProfileRetryMode.OFF -> retryTitle(ProfileRetryMode.OFF)
                        ProfileRetryMode.BOUNDED -> stringResource(AYMR.strings.ai_translation_retry_bounded_short)
                        ProfileRetryMode.WAIT_FOR_SERVICE -> stringResource(
                            AYMR.strings.ai_translation_retry_wait_short,
                        )
                    }
                    AiTranslatorChoiceChip(
                        text = chipLabel,
                        selected = profile.execution.retryMode == mode,
                        onClick = { onAction(AiTranslationAction.SetProfileRetryMode(profile.id, mode)) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (job != null && job.execution == null) {
                    Text(
                        text = stringResource(AYMR.strings.ai_translation_frozen_recovery),
                        style = MaterialTheme.typography.bodySmall,
                        color = aurora.warning,
                    )
                } else {
                    Text(
                        text = retryHint(profile.execution.retryMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = aurora.textSecondary,
                    )
                }
                val reqCap = profile.execution.maxRequests?.toString() ?: "∞"
                val concLabel = stringResource(AYMR.strings.ai_translation_parallelism) +
                    ": ${connection?.maxConcurrency ?: 2} · " +
                    stringResource(AYMR.strings.ai_translation_request_limit) + ": $reqCap"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onAction(AiTranslationAction.EditRecovery(profile.id)) },
                    shape = RoundedCornerShape(14.dp),
                    color = resolveAuroraTranslationGlassContainerColor(aurora, subtle = true),
                    border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora)),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(AYMR.strings.ai_translation_limits_concurrency),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = aurora.textPrimary,
                            )
                            Text(
                                text = concLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = aurora.textSecondary,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = aurora.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPromptTab(
    state: AiTranslationQuickUiState,
    profile: TranslationProfile?,
    connection: ConnectionProfile?,
    onOpenCustomPrompt: () -> Unit,
    onAction: (AiTranslationAction) -> Unit,
) {
    if (profile == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TranslationNotice(stringResource(AYMR.strings.ai_translation_missing_profile))
            AiTranslationPrimaryButton(
                text = stringResource(AYMR.strings.ai_translation_new_profile),
                icon = Icons.Default.Add,
                onClick = { onAction(AiTranslationAction.EditProfile(null)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    val aurora = AuroraTheme.colors
    val adultPromptSupported = connection?.presetFlavor?.supportsAdultPromptMode() != false

    // 1. Prompt Filtering Mode
    AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_gemini_prompt_mode)) {
        AuroraFieldLabel(stringResource(AYMR.strings.novel_reader_ai_translator_prompt_mode_summary))
        val modeOptions = buildList {
            add("CLASSIC" to stringResource(AYMR.strings.novel_reader_gemini_prompt_mode_classic))
            if (adultPromptSupported || profile.prompt.modeId == "ADULT_18") {
                add("ADULT_18" to stringResource(AYMR.strings.novel_reader_gemini_prompt_mode_adult_short))
            }
        }
        AuroraChipFlow {
            modeOptions.forEach { (id, label) ->
                AiTranslatorChoiceChip(
                    text = label,
                    selected = profile.prompt.modeId == id,
                    onClick = {
                        onAction(
                            AiTranslationAction.SetProfilePrompt(
                                profile.id,
                                profile.prompt.copy(modeId = id),
                            ),
                        )
                    },
                )
            }
        }
        if (!adultPromptSupported) {
            AuroraFieldLabel(stringResource(AYMR.strings.novel_reader_gemini_prompt_mode_adult_unsupported))
        }
    }

    // 2. Style Persona Presets
    val stylePresets = remember { NovelTranslationStylePresets.all }
    AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_ai_translator_style_title)) {
        AuroraFieldLabel(stringResource(AYMR.strings.novel_reader_ai_translator_style_summary))
        AuroraChipFlow {
            stylePresets.forEach { preset ->
                AiTranslatorChoiceChip(
                    text = stringResource(preset.titleRes),
                    selected = profile.prompt.styleId == preset.id.name,
                    onClick = {
                        onAction(
                            AiTranslationAction.SetProfilePrompt(
                                profile.id,
                                profile.prompt.copy(styleId = preset.id.name),
                            ),
                        )
                    },
                )
            }
        }
        val selectedStyle = stylePresets.firstOrNull { it.id.name == profile.prompt.styleId }
        if (selectedStyle != null) {
            PresetDetailCard(
                title = stringResource(selectedStyle.titleRes),
                scenario = stringResource(AYMR.strings.novel_reader_ai_translator_style_scenario_prefix)
                    .format(stringResource(selectedStyle.scenarioRes)),
                advantage = stringResource(AYMR.strings.novel_reader_ai_translator_style_advantage_prefix)
                    .format(stringResource(selectedStyle.advantageRes)),
            )
        }
    }

    // 3. Genre Modifiers + Custom Prompt
    AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_gemini_prompt_modifiers)) {
        AuroraFieldLabel(stringResource(AYMR.strings.novel_reader_gemini_prompt_modifiers_hint))
        AuroraChipFlow {
            GeminiPromptModifiers.all.forEach { modifier ->
                val selected = modifier.id in profile.prompt.modifierIds
                AiTranslatorFilterChip(
                    label = stringResource(modifier.labelRes),
                    selected = selected,
                    onClick = {
                        val updated = if (selected) {
                            profile.prompt.modifierIds - modifier.id
                        } else {
                            profile.prompt.modifierIds + modifier.id
                        }
                        onAction(
                            AiTranslationAction.SetProfilePrompt(
                                profile.id,
                                profile.prompt.copy(modifierIds = updated),
                            ),
                        )
                    },
                )
            }
            AiTranslatorFilterChip(
                label = if (profile.prompt.customText.isBlank()) {
                    stringResource(AYMR.strings.novel_reader_ai_translator_custom_modifier_add)
                } else {
                    stringResource(AYMR.strings.novel_reader_ai_translator_custom_modifier_active)
                },
                selected = profile.prompt.customText.isNotBlank(),
                onClick = onOpenCustomPrompt,
            )
        }
    }

    // 4. Generation Sampling Presets & Sliders
    val isDeepSeek = connection?.presetFlavor == PresetFlavor.DEEPSEEK
    val presets = if (isDeepSeek) aiDeepSeekGenerationPresets() else aiDefaultGenerationPresets()
    val defaultTemp = if (isDeepSeek) 1.3f else 0.7f
    val defaultTopP = 0.9f
    val defaultTopK = 40
    var tempSlider by remember(profile.id, profile.generation.temperature, isDeepSeek) {
        mutableFloatStateOf(profile.generation.temperature?.toFloat() ?: defaultTemp)
    }
    var topPSlider by remember(profile.id, profile.generation.topP) {
        mutableFloatStateOf(profile.generation.topP?.toFloat() ?: defaultTopP)
    }
    var topKSlider by remember(profile.id, profile.generation.topK) {
        mutableFloatStateOf((profile.generation.topK ?: defaultTopK).toFloat())
    }
    val selectedPresetId = resolveSelectedGenerationPresetId(
        isDeepSeek = isDeepSeek,
        presets = presets,
        temperature = tempSlider,
        topP = topPSlider,
        topK = profile.generation.topK,
    )

    val supportsTopK = state.models.firstOrNull {
        state.catalogConnectionId == profile.connectionId &&
            it.selection.exactModelId == profile.exactModelId
    }?.supportedGenerationFields?.contains(AiGenerationFieldSupportedCheck.TOP_K) ?: when (connection?.protocol) {
        TranslationProtocol.OLLAMA_NATIVE -> true
        TranslationProtocol.GEMINI_GENERATE -> resolveGeminiModelCapabilities(profile.exactModelId).topK != null
        else -> false
    }

    AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_ai_translator_generation_title)) {
        AuroraFieldLabel(stringResource(AYMR.strings.ai_translation_preset_balance_summary))
        AuroraChipFlow {
            presets.forEach { preset ->
                AiTranslatorChoiceChip(
                    text = preset.title,
                    selected = preset.id == selectedPresetId,
                    onClick = {
                        tempSlider = preset.temperature
                        topPSlider = preset.topP
                        if (preset.topK != null && supportsTopK) {
                            topKSlider = preset.topK.toFloat()
                        }
                        onAction(
                            AiTranslationAction.SetProfileGeneration(
                                profile.id,
                                profile.generation.copy(
                                    temperature = preset.temperature.toDouble(),
                                    topP = preset.topP.toDouble(),
                                    topK = if (supportsTopK) preset.topK else null,
                                ),
                            ),
                        )
                    },
                )
            }
        }
        val activePreset = presets.firstOrNull { it.id == selectedPresetId }
        if (activePreset != null) {
            PresetDetailCard(
                title = activePreset.title,
                scenario = stringResource(AYMR.strings.novel_reader_ai_translator_generation_scenario_prefix)
                    .format(activePreset.scenario),
                advantage = stringResource(AYMR.strings.novel_reader_ai_translator_generation_advantage_prefix)
                    .format(activePreset.advantage),
            )
        }
        SamplingSliderRow(
            label = stringResource(AYMR.strings.novel_reader_gemini_temperature),
            valueText = String.format(Locale.US, "%.2f", tempSlider),
            value = tempSlider,
            valueRange = 0.0f..2.0f,
            onValueChange = { tempSlider = ((it * 100f).roundToInt() / 100f) },
            onValueChangeFinished = {
                onAction(
                    AiTranslationAction.SetProfileGeneration(
                        profile.id,
                        profile.generation.copy(temperature = tempSlider.toDouble()),
                    ),
                )
            },
        )
        SamplingSliderRow(
            label = stringResource(AYMR.strings.novel_reader_gemini_top_p),
            valueText = String.format(Locale.US, "%.2f", topPSlider),
            value = topPSlider,
            valueRange = 0.0f..1.0f,
            onValueChange = { topPSlider = ((it * 100f).roundToInt() / 100f) },
            onValueChangeFinished = {
                onAction(
                    AiTranslationAction.SetProfileGeneration(
                        profile.id,
                        profile.generation.copy(topP = topPSlider.toDouble()),
                    ),
                )
            },
        )
        if (supportsTopK) {
            SamplingSliderRow(
                label = stringResource(AYMR.strings.novel_reader_gemini_top_k),
                valueText = topKSlider.roundToInt().toString(),
                value = topKSlider,
                valueRange = 1f..100f,
                onValueChange = { topKSlider = it.roundToInt().toFloat() },
                onValueChangeFinished = {
                    onAction(
                        AiTranslationAction.SetProfileGeneration(
                            profile.id,
                            profile.generation.copy(topK = topKSlider.roundToInt()),
                        ),
                    )
                },
            )
        }
        if (profile.model.outputTokenField != OutputTokenField.OMIT) {
            var tokenInput by remember(profile.id, profile.generation.maxOutputTokens) {
                mutableStateOf(profile.generation.maxOutputTokens?.toString().orEmpty())
            }
            val parsedTokens = tokenInput.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
            val maxAllowedTokens = if (connection?.protocol ==
                TranslationProtocol.GEMINI_GENERATE
            ) {
                65_536
            } else {
                Int.MAX_VALUE
            }
            val tokensValid = tokenInput.isBlank() || (parsedTokens != null && parsedTokens in 1..maxAllowedTokens)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiTranslationOutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text(stringResource(AYMR.strings.ai_translation_output_tokens)) },
                    isError = !tokensValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                AiTranslationSecondaryButton(
                    text = stringResource(AYMR.strings.ai_translation_save),
                    enabled = tokensValid && parsedTokens != profile.generation.maxOutputTokens,
                    onClick = {
                        onAction(
                            AiTranslationAction.SetProfileGeneration(
                                profile.id,
                                profile.generation.copy(maxOutputTokens = parsedTokens),
                            ),
                        )
                    },
                )
            }
        }
    }
}

private typealias AiGenerationFieldSupportedCheck =
    eu.kanade.presentation.more.settings.translation.AiGenerationField

@Composable
private fun QuickMoreTab(
    state: AiTranslationQuickUiState,
    connection: ConnectionProfile?,
    autoTranslateEnglishSource: Boolean,
    onSetAutoTranslateEnglishSource: (Boolean) -> Unit,
    prefetchNextChapterTranslation: Boolean,
    onSetPrefetchNextChapterTranslation: (Boolean) -> Unit,
    disableCache: Boolean,
    onSetDisableCache: (Boolean) -> Unit,
    privatePythonLikeMode: Boolean,
    onSetPrivatePythonLikeMode: (Boolean) -> Unit,
    onClearAllCache: () -> Unit,
    logs: List<String>,
    onClearLogs: () -> Unit,
    onAction: (AiTranslationAction) -> Unit,
) {
    val aurora = AuroraTheme.colors

    QuickNavigationBanner(
        title = stringResource(AYMR.strings.ai_translation_open_full_settings),
        subtitle = stringResource(AYMR.strings.ai_translation_open_full_settings_summary),
        leadingIcon = Icons.Filled.Settings,
        onClick = { onAction(AiTranslationAction.OpenFullSettings) },
    )

    // 1. Reader Automation & Cache
    AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_ai_translator_more_automation_title)) {
        AiTranslationToggleRow(
            label = stringResource(AYMR.strings.novel_reader_translation_auto_english_title),
            subtitle = stringResource(AYMR.strings.novel_reader_ai_translator_more_auto_english_summary),
            checked = autoTranslateEnglishSource,
            onClick = { onSetAutoTranslateEnglishSource(!autoTranslateEnglishSource) },
        )
        AiTranslationToggleRow(
            label = stringResource(AYMR.strings.novel_reader_translation_prefetch_next_title),
            subtitle = stringResource(AYMR.strings.novel_reader_ai_translator_more_prefetch_summary),
            checked = prefetchNextChapterTranslation,
            onClick = { onSetPrefetchNextChapterTranslation(!prefetchNextChapterTranslation) },
        )
        AiTranslationToggleRow(
            label = stringResource(AYMR.strings.novel_reader_ai_translator_more_cache_title),
            subtitle = stringResource(AYMR.strings.novel_reader_ai_translator_more_cache_summary),
            checked = !disableCache,
            onClick = { onSetDisableCache(!disableCache) },
        )
        if (connection?.presetFlavor == PresetFlavor.GEMINI_PRIVATE) {
            AiTranslationToggleRow(
                label = stringResource(AYMR.strings.novel_reader_gemini_private_python_like_mode),
                subtitle = stringResource(AYMR.strings.novel_reader_ai_translator_more_private_python_summary),
                checked = privatePythonLikeMode,
                onClick = { onSetPrivatePythonLikeMode(!privatePythonLikeMode) },
            )
        }
        AiTranslationDangerButton(
            text = stringResource(AYMR.strings.novel_reader_ai_translator_clear_all_cache),
            onClick = onClearAllCache,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    // 2. Active Connection & Key Pool
    if (connection == null) {
        AuroraGlassSection(title = stringResource(AYMR.strings.ai_translation_connection_pool_title)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TranslationNotice(stringResource(AYMR.strings.ai_translation_empty_connections))
                AiTranslationPrimaryButton(
                    text = stringResource(AYMR.strings.ai_translation_add),
                    icon = Icons.Default.Add,
                    onClick = { onAction(AiTranslationAction.EditConnection(null)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        val enabledKeys = connection.credentialBindings.count { it.enabled }
        AuroraGlassSection(title = stringResource(AYMR.strings.ai_translation_connection_pool_title)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = connection.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = aurora.textPrimary,
                        )
                        Text(
                            text =
                            "${flavorTitle(connection.presetFlavor)} · ${protocolTitle(connection.protocol)} · " +
                                routingTitle(connection.routingMode),
                            style = MaterialTheme.typography.labelSmall,
                            color = aurora.textSecondary,
                        )
                    }
                    val probeStatus = state.probeStates[connection.id]
                    AiTranslationStatusChip(
                        label = probeTitle(probeStatus),
                        tint = probeTint(probeStatus, aurora),
                    )
                }

                if (connection.auth != ProfileAuth.NONE) {
                    Text(
                        text = stringResource(
                            AYMR.strings.ai_translation_active_keys_count,
                            enabledKeys,
                            connection.credentialBindings.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = aurora.textSecondary,
                    )
                    connection.credentialBindings.forEach { binding ->
                        val cred = state.credentials.firstOrNull { it.id == binding.credentialId }
                        val waitMinutes = aiTranslationWaitMinutes(cred?.notBeforeAt, state.nowMillis)
                        val healthLabel = when {
                            cred?.healthStatus == CredentialHealthStatus.NEEDS_ACTION ->
                                stringResource(AYMR.strings.ai_translation_key_needs_action)
                            waitMinutes != null ->
                                stringResource(AYMR.strings.ai_translation_key_cooldown, waitMinutes)
                            cred?.healthStatus == CredentialHealthStatus.READY ->
                                stringResource(AYMR.strings.ai_translation_key_ready)
                            else -> null
                        }
                        val healthTint = when {
                            cred?.healthStatus == CredentialHealthStatus.NEEDS_ACTION -> aurora.error
                            waitMinutes != null -> aurora.warning
                            cred?.healthStatus == CredentialHealthStatus.READY -> aurora.success
                            else -> aurora.textSecondary
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = resolveAuroraTranslationGlassContainerColor(aurora, subtle = true),
                            border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora)),
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(vertical = 2.dp)) {
                                AiTranslationToggleRow(
                                    label = (cred?.alias ?: binding.credentialId.take(8)) +
                                        " · •••• " + cred?.maskedSuffix.orEmpty(),
                                    subtitle = binding.quotaGroupLabel.takeIf { it.isNotBlank() && it != "shared" },
                                    checked = binding.enabled,
                                    onClick = {
                                        onAction(
                                            AiTranslationAction.SetConnectionCredentialEnabled(
                                                connection.id,
                                                binding.credentialId,
                                                !binding.enabled,
                                            ),
                                        )
                                    },
                                )
                                if (healthLabel != null) {
                                    Box(Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                                        AiTranslationStatusChip(label = healthLabel, tint = healthTint)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiTranslationSecondaryButton(
                        text = stringResource(AYMR.strings.ai_translation_test_connection),
                        enabled = state.probeStates[connection.id] != AiTranslationProbeStatus.RUNNING,
                        onClick = { onAction(AiTranslationAction.TestConnection(connection.id)) },
                        modifier = Modifier.weight(1f),
                    )
                    AiTranslationSecondaryButton(
                        text = stringResource(AYMR.strings.ai_translation_configure_connection),
                        onClick = { onAction(AiTranslationAction.EditConnection(connection.id)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // 3. Queue & All Jobs Banner
    QuickNavigationBanner(
        title = stringResource(AYMR.strings.ai_translation_queue_summary, state.activeJobsCount),
        onClick = { onAction(AiTranslationAction.OpenAllJobs) },
    )

    // 4. Live Terminal Diagnostics
    AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_ai_translator_logs_title)) {
        AiTranslationTerminalLogsSection(
            logs = logs,
            onClearLogs = onClearLogs,
        )
    }
}

@Composable
private fun QuickNavigationBanner(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val aurora = AuroraTheme.colors
    AuroraGlassSection(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (aurora.isEInk) aurora.textPrimary else aurora.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = aurora.textPrimary,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = aurora.textSecondary,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (aurora.isEInk) aurora.textPrimary else aurora.accent,
            )
        }
    }
}

@Composable
private fun PresetDetailCard(
    title: String,
    scenario: String,
    advantage: String,
) {
    val aurora = AuroraTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = resolveAuroraTranslationGlassContainerColor(aurora, subtle = true),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (aurora.isEInk) aurora.textPrimary else aurora.accent),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = aurora.textPrimary,
                )
            }
            Text(
                text = scenario,
                style = MaterialTheme.typography.bodySmall,
                color = aurora.textSecondary,
            )
            Text(
                text = advantage,
                style = MaterialTheme.typography.bodySmall,
                color = aurora.textSecondary,
            )
        }
    }
}

@Composable
private fun SamplingSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val aurora = AuroraTheme.colors
    val accent = if (aurora.isEInk) aurora.textPrimary else aurora.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = aurora.textSecondary,
        )
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = resolveAuroraTranslationGlassContainerColor(aurora, subtle = true),
            border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Text(
                text = valueText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = aurora.divider,
        ),
    )
}
