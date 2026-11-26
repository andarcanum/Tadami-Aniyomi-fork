package eu.kanade.presentation.more.settings.translation

import android.content.ClipData
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.resolveAuroraTabContainerColor
import eu.kanade.presentation.reader.settings.AuroraChipFlow
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraBorderColor
import eu.kanade.presentation.theme.resolveAuroraControlContainerColor
import eu.kanade.presentation.theme.resolveAuroraSelectionContainerColor
import kotlinx.coroutines.launch
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import kotlin.math.abs

/** Shared generation sampling preset across both legacy-free reader sheet and fallback setup dialog. */
internal data class AiTranslationGenerationPreset(
    val id: String,
    val title: String,
    val temperature: Float,
    val topP: Float,
    val topK: Int?,
    val scenario: String,
    val advantage: String,
)

@Composable
internal fun aiDefaultGenerationPresets(): List<AiTranslationGenerationPreset> = listOf(
    AiTranslationGenerationPreset(
        id = "anchor_plus",
        title = stringResource(AYMR.strings.novel_reader_gemini_generation_anchor_plus_title),
        temperature = 0.62f,
        topP = 0.9f,
        topK = 36,
        scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_anchor_plus_scenario),
        advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_anchor_plus_advantage),
    ),
    AiTranslationGenerationPreset(
        id = "authorial",
        title = stringResource(AYMR.strings.novel_reader_gemini_generation_authorial_title),
        temperature = 0.76f,
        topP = 0.93f,
        topK = 48,
        scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_authorial_scenario),
        advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_authorial_advantage),
    ),
    AiTranslationGenerationPreset(
        id = "dialogue_plus",
        title = stringResource(AYMR.strings.novel_reader_gemini_generation_dialogue_plus_title),
        temperature = 0.88f,
        topP = 0.95f,
        topK = 56,
        scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_dialogue_plus_scenario),
        advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_dialogue_plus_advantage),
    ),
    AiTranslationGenerationPreset(
        id = "private_pulse",
        title = stringResource(AYMR.strings.novel_reader_gemini_generation_private_pulse_title),
        temperature = 0.98f,
        topP = 0.97f,
        topK = 72,
        scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_private_pulse_scenario),
        advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_private_pulse_advantage),
    ),
    AiTranslationGenerationPreset(
        id = "unbound",
        title = stringResource(AYMR.strings.novel_reader_gemini_generation_unbound_title),
        temperature = 1.08f,
        topP = 0.985f,
        topK = 96,
        scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_unbound_scenario),
        advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_unbound_advantage),
    ),
)

@Composable
internal fun aiDeepSeekGenerationPresets(): List<AiTranslationGenerationPreset> = listOf(
    AiTranslationGenerationPreset(
        id = "deepseek_balanced",
        title = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_balanced_title),
        temperature = 1.3f,
        topP = 0.9f,
        topK = null,
        scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_balanced_scenario),
        advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_balanced_advantage),
    ),
    AiTranslationGenerationPreset(
        id = "deepseek_expressive",
        title = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_expressive_title),
        temperature = 1.4f,
        topP = 0.93f,
        topK = null,
        scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_expressive_scenario),
        advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_expressive_advantage),
    ),
    AiTranslationGenerationPreset(
        id = "deepseek_creative",
        title = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_creative_title),
        temperature = 1.5f,
        topP = 0.95f,
        topK = null,
        scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_creative_scenario),
        advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_creative_advantage),
    ),
)

internal fun resolveSelectedGenerationPresetId(
    isDeepSeek: Boolean,
    presets: List<AiTranslationGenerationPreset>,
    temperature: Float,
    topP: Float,
    topK: Int?,
): String {
    if (presets.isEmpty()) return ""
    val epsilon = 0.0001f
    presets.firstOrNull { preset ->
        val tempMatch = abs(preset.temperature - temperature) <= epsilon
        val topPMatch = abs(preset.topP - topP) <= epsilon
        val topKMatch = when {
            isDeepSeek -> true
            preset.topK == null || topK == null -> true
            else -> preset.topK == topK
        }
        tempMatch && topPMatch && topKMatch
    }?.let { return it.id }

    return presets.minByOrNull { preset ->
        val topKDistance = when {
            isDeepSeek -> 0f
            preset.topK == null || topK == null -> 0f
            else -> abs((topK - preset.topK).toFloat()) / 100f
        }
        abs(preset.temperature - temperature) + abs(preset.topP - topP) + topKDistance
    }?.id ?: presets.first().id
}

/** Matches [ProfilePromptRenderer] runtime rules: OpenRouter/NVIDIA/Ollama always render CLASSIC prompts. */
internal fun PresetFlavor.supportsAdultPromptMode(): Boolean = when (this) {
    PresetFlavor.OPENROUTER, PresetFlavor.NVIDIA, PresetFlavor.OLLAMA -> false
    PresetFlavor.NONE, PresetFlavor.GEMINI_PRIVATE, PresetFlavor.DEEPSEEK, PresetFlavor.MISTRAL -> true
}

@Composable
internal fun resolveAuroraTranslationGlassContainerColor(
    colors: AuroraColors = AuroraTheme.colors,
    selected: Boolean = false,
    subtle: Boolean = false,
): Color = when {
    selected && colors.eInkProfile == EInkProfile.MONOCHROME -> resolveAuroraSelectionContainerColor(colors)
    selected -> colors.accent.copy(alpha = if (colors.isEInk || colors.isDark) 0.14f else 0.12f)
    colors.isEInk -> resolveAuroraControlContainerColor(colors)
    colors.isDark -> resolveAuroraTabContainerColor(colors)
    else -> Color.White.copy(alpha = if (subtle) 0.42f else 0.60f)
}

@Composable
internal fun resolveAuroraTranslationGlassBorderBrush(
    colors: AuroraColors = AuroraTheme.colors,
    selected: Boolean = false,
): Brush = when {
    selected -> SolidColor(if (colors.isEInk) colors.textPrimary else colors.accent.copy(alpha = 0.55f))
    colors.isEInk -> SolidColor(resolveAuroraBorderColor(colors, false))
    else -> auroraMenuRimLightBrush(colors)
}

@Composable
internal fun AiTranslationOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val aurora = AuroraTheme.colors
    val focusedContainer = if (aurora.isEInk) {
        resolveAuroraControlContainerColor(aurora)
    } else if (aurora.isDark) {
        Color.White.copy(alpha = 0.03f)
    } else {
        Color.White.copy(alpha = 0.35f)
    }
    val unfocusedContainer = if (aurora.isEInk) {
        resolveAuroraControlContainerColor(aurora)
    } else {
        Color.Transparent
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = aurora.textPrimary,
            unfocusedTextColor = aurora.textPrimary,
            focusedBorderColor = if (aurora.isEInk) aurora.textPrimary else aurora.accent,
            unfocusedBorderColor = if (aurora.isEInk) {
                resolveAuroraBorderColor(aurora, false)
            } else {
                auroraRimColor()
            },
            focusedLabelColor = if (aurora.isEInk) aurora.textPrimary else aurora.accent,
            unfocusedLabelColor = aurora.textSecondary,
            cursorColor = if (aurora.isEInk) aurora.textPrimary else aurora.accent,
            focusedContainerColor = focusedContainer,
            unfocusedContainerColor = unfocusedContainer,
        ),
    )
}

@Composable
internal fun AiTranslatorChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val aurora = AuroraTheme.colors
    val selectedBg = when (aurora.eInkProfile) {
        EInkProfile.MONOCHROME -> aurora.textPrimary
        EInkProfile.COLOR -> aurora.accent
        EInkProfile.OFF -> aurora.accent.copy(alpha = 0.16f)
    }
    val selectedBorder = when (aurora.eInkProfile) {
        EInkProfile.MONOCHROME -> aurora.textPrimary
        EInkProfile.COLOR -> aurora.accent
        EInkProfile.OFF -> aurora.accent.copy(alpha = 0.55f)
    }
    val selectedText = when (aurora.eInkProfile) {
        EInkProfile.MONOCHROME -> aurora.background
        EInkProfile.COLOR -> aurora.textOnAccent
        EInkProfile.OFF -> aurora.accent
    }
    val baseAlpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                selectedBorder
            } else if (aurora.isEInk) {
                resolveAuroraBorderColor(aurora, false)
            } else {
                auroraRimColor()
            },
        ),
        color = if (selected) {
            selectedBg
        } else if (aurora.isEInk) {
            resolveAuroraControlContainerColor(aurora)
        } else if (aurora.isDark) {
            Color.White.copy(alpha = 0.04f)
        } else {
            Color.Black.copy(alpha = 0.03f)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = (if (selected) selectedText else aurora.textPrimary).copy(alpha = baseAlpha),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun AiTranslatorFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val aurora = AuroraTheme.colors
    val selectedContainer = if (aurora.eInkProfile == EInkProfile.MONOCHROME) {
        aurora.textPrimary
    } else if (aurora.eInkProfile == EInkProfile.COLOR) {
        aurora.accent
    } else if (aurora.isDark) {
        Color.White.copy(alpha = 0.09f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    val textColor = when {
        !selected -> aurora.textPrimary
        aurora.eInkProfile == EInkProfile.MONOCHROME -> aurora.background
        aurora.eInkProfile == EInkProfile.COLOR -> aurora.textOnAccent
        else -> aurora.textPrimary
    }
    val checkTint = when {
        aurora.eInkProfile == EInkProfile.MONOCHROME && selected -> aurora.background
        aurora.eInkProfile == EInkProfile.COLOR && selected -> aurora.textOnAccent
        else -> aurora.accent
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                if (aurora.isEInk) aurora.textPrimary else aurora.accent.copy(alpha = 0.50f)
            } else if (aurora.isEInk) {
                resolveAuroraBorderColor(aurora, false)
            } else {
                auroraRimColor()
            },
        ),
        color = if (selected) {
            selectedContainer
        } else if (aurora.isEInk) {
            resolveAuroraControlContainerColor(aurora)
        } else if (aurora.isDark) {
            Color.White.copy(alpha = 0.03f)
        } else {
            Color.Black.copy(alpha = 0.02f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = checkTint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
internal fun <T> AiTranslationChipGroup(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AuroraTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        AuroraChipFlow {
            options.forEach { (value, title) ->
                AiTranslatorChoiceChip(
                    text = title,
                    selected = value == selected,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

/** Aurora frost modal replacement for Android DropdownMenu in settings & editors. */
@Composable
internal fun <T> AiTranslationPickerField(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    selectedOption: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    emptyText: String? = null,
    emptyActionLabel: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    openSignal: Int = 0,
) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(openSignal) {
        if (openSignal > 0 && enabled) {
            open = true
        }
    }
    val aurora = AuroraTheme.colors
    val canOpen = enabled && (options.isNotEmpty() || emptyText != null || onEmptyAction != null)
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = canOpen, onClick = { open = true }),
            shape = RoundedCornerShape(12.dp),
            color = resolveAuroraTranslationGlassContainerColor(aurora, subtle = true),
            border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = aurora.textSecondary,
                    )
                    Text(
                        text = value.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (canOpen) aurora.textPrimary else aurora.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!supportingText.isNullOrBlank()) {
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = aurora.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = aurora.textSecondary,
                )
            }
        }
    }
    if (open) {
        AiTranslationDockedSheet(
            onDismiss = { open = false },
            title = label,
            closeLabel = stringResource(AYMR.strings.ai_translation_close),
            contentSpacing = 10.dp,
            footer = {
                if (options.isEmpty() && emptyActionLabel != null && onEmptyAction != null) {
                    AiTranslationDockedFooter(
                        cancelLabel = stringResource(AYMR.strings.ai_translation_cancel),
                        confirmLabel = emptyActionLabel,
                        onCancel = { open = false },
                        onConfirm = {
                            open = false
                            onEmptyAction()
                        },
                    )
                } else {
                    AiTranslationSecondaryButton(
                        text = stringResource(AYMR.strings.ai_translation_cancel),
                        onClick = { open = false },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                }
            },
        ) {
            if (options.isEmpty()) {
                if (!emptyText.isNullOrBlank()) {
                    TranslationNotice(emptyText)
                }
            } else {
                options.forEach { (option, title) ->
                    AiTranslationOptionCard(
                        title = title,
                        subtitle = null,
                        selected = option == selectedOption,
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                    )
                }
                if (emptyActionLabel != null && onEmptyAction != null) {
                    AiTranslationSecondaryButton(
                        text = emptyActionLabel,
                        icon = Icons.Default.Add,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            open = false
                            onEmptyAction()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiTranslationOptionCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val aurora = AuroraTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = resolveAuroraTranslationGlassContainerColor(aurora, selected = selected, subtle = true),
        border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora, selected = selected)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) aurora.accent else aurora.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
        }
    }
}

@Composable
internal fun AiTranslationProfileCard(
    title: String,
    subtitle: String,
    statusLabel: String,
    statusReady: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aurora = AuroraTheme.colors
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = resolveAuroraTranslationGlassContainerColor(aurora, selected = selected, subtle = true),
        border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora, selected = selected)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = aurora.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(AYMR.strings.ai_translation_edit_profile),
                        tint = if (selected) aurora.accent else aurora.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = aurora.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (statusReady) aurora.success else aurora.warning),
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected && !aurora.isEInk) aurora.accent else aurora.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = aurora.accent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiTranslationPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val aurora = AuroraTheme.colors
    val container = when (aurora.eInkProfile) {
        EInkProfile.MONOCHROME -> aurora.textPrimary
        EInkProfile.COLOR, EInkProfile.OFF -> aurora.accent
    }
    val content = when (aurora.eInkProfile) {
        EInkProfile.MONOCHROME -> aurora.background
        EInkProfile.COLOR, EInkProfile.OFF -> aurora.textOnAccent
    }
    val disabledContainer = when {
        aurora.isEInk -> resolveAuroraControlContainerColor(aurora)
        aurora.isDark -> aurora.accent.copy(alpha = 0.16f)
        else -> aurora.accent.copy(alpha = 0.12f)
    }
    val disabledContent = when {
        aurora.isEInk -> aurora.textSecondary
        else -> aurora.accent.copy(alpha = 0.72f)
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (compact) 40.dp else 48.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = if (compact) {
            PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        } else {
            PaddingValues(horizontal = 18.dp, vertical = 10.dp)
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent,
        ),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 16.dp else 18.dp),
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun AiTranslationSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val aurora = AuroraTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (compact) 40.dp else 46.dp),
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
        contentPadding = if (compact) {
            PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        } else {
            PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = resolveAuroraTranslationGlassContainerColor(aurora, subtle = true),
            contentColor = aurora.textPrimary,
        ),
        border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora)),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                tint = aurora.textPrimary,
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = text,
            color = aurora.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun AiTranslationStatusChip(
    label: String,
    tint: Color = AuroraTheme.colors.textSecondary,
    modifier: Modifier = Modifier,
) {
    val aurora = AuroraTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (aurora.isEInk) {
            resolveAuroraControlContainerColor(aurora)
        } else {
            tint.copy(alpha = 0.14f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (aurora.isEInk) resolveAuroraBorderColor(aurora, false) else tint.copy(alpha = 0.35f),
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (aurora.isEInk) aurora.textPrimary else tint,
            maxLines = 1,
        )
    }
}

@Composable
internal fun AiTranslationTerminalLogsSection(
    logs: List<String>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aurora = AuroraTheme.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(AYMR.strings.novel_reader_ai_translator_logs_count).format(logs.size),
                style = MaterialTheme.typography.labelMedium,
                color = aurora.textPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        val text = logs.joinToString("\n")
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
                        }
                    },
                    enabled = logs.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(AYMR.strings.novel_reader_dictionary_history_action_copy))
                }
                TextButton(onClick = onClearLogs, enabled = logs.isNotEmpty()) {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(AYMR.strings.novel_reader_gemini_action_clear))
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = if (aurora.isEInk) resolveAuroraBorderColor(aurora, false) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                )
                .background(
                    when {
                        aurora.isEInk -> resolveAuroraControlContainerColor(aurora)
                        aurora.isDark -> Color(0xFF07090F)
                        else -> Color(0xFF1E232E)
                    },
                )
                .padding(10.dp)
                .heightIn(max = 140.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_ai_translator_logs_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (aurora.isEInk) aurora.textSecondary else Color(0xFF9CA3AF),
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                logs.forEach { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (aurora.isEInk) aurora.textPrimary else Color(0xFFA7F3D0),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiTranslationCustomPromptDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(initialText) { mutableStateOf(initialText) }
    val aurora = AuroraTheme.colors
    AuroraFrostDialog(
        onDismiss = onDismiss,
        title = stringResource(AYMR.strings.novel_reader_ai_translator_custom_modifier_title),
        closeLabel = stringResource(AYMR.strings.ai_translation_close),
        contentSpacing = 12.dp,
        footer = {
            if (draft.isNotBlank() || initialText.isNotBlank()) {
                TextButton(onClick = onClear) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_gemini_action_clear),
                        color = aurora.error,
                    )
                }
            }
            AuroraFrostCancel(stringResource(AYMR.strings.novel_reader_ai_translator_cancel), onDismiss)
            AuroraFrostConfirm(
                label = stringResource(AYMR.strings.novel_reader_ai_translator_save),
                onClick = { onSave(draft) },
                enabled = true,
            )
        },
    ) {
        AiTranslationOutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = {
                Text(stringResource(AYMR.strings.novel_reader_ai_translator_custom_instructions))
            },
            minLines = 4,
            maxLines = 8,
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(AYMR.strings.novel_reader_ai_translator_custom_modifier_hint),
            style = MaterialTheme.typography.bodySmall,
            color = aurora.textSecondary,
        )
    }
}

@Composable
internal fun AiTranslationDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val aurora = AuroraTheme.colors
    val danger = if (aurora.isEInk) aurora.textPrimary else aurora.error
    val container = if (aurora.isEInk) {
        resolveAuroraTranslationGlassContainerColor(aurora, subtle = true)
    } else {
        danger.copy(alpha = if (aurora.isDark) 0.12f else 0.08f)
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 46.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = danger,
        ),
        border = BorderStroke(1.dp, danger.copy(alpha = if (aurora.isEInk) 0.7f else 0.28f)),
    ) {
        Text(
            text = text,
            color = danger,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun AiTranslationToggleRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
) {
    val aurora = AuroraTheme.colors
    val accent = if (aurora.isEInk) aurora.textPrimary else aurora.accent
    val contentAlpha = if (enabled) 1f else 0.42f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = aurora.textPrimary.copy(alpha = contentAlpha),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = aurora.textSecondary.copy(alpha = contentAlpha),
                )
            }
        }
        val trackColor = if (checked) {
            if (aurora.isEInk) aurora.textPrimary else accent.copy(alpha = if (enabled) 0.45f else 0.25f)
        } else {
            aurora.textPrimary.copy(alpha = if (aurora.isEInk) 0.35f else 0.14f)
        }
        val knobColor = when {
            checked && aurora.isEInk -> aurora.background
            checked -> Color.White
            else -> aurora.textSecondary
        }
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(22.dp)
                .background(trackColor, CircleShape),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(16.dp)
                    .background(knobColor.copy(alpha = if (enabled) 1f else 0.5f), CircleShape),
            )
        }
    }
}

@Composable
internal fun AiTranslationLanguagePairRow(
    sourceValue: String,
    onSourceChange: (String) -> Unit,
    targetValue: String,
    onTargetChange: (String) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val aurora = AuroraTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(AYMR.strings.ai_translation_source_short),
                style = MaterialTheme.typography.labelSmall,
                color = aurora.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp),
            )
            Spacer(Modifier.width(44.dp))
            Text(
                text = stringResource(AYMR.strings.ai_translation_target_short),
                style = MaterialTheme.typography.labelSmall,
                color = aurora.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiTranslationOutlinedTextField(
                value = sourceValue,
                onValueChange = onSourceChange,
                isError = isError,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = onSwap,
                shape = RoundedCornerShape(12.dp),
                color = if (aurora.isEInk) {
                    resolveAuroraTranslationGlassContainerColor(aurora, subtle = true)
                } else {
                    aurora.accent.copy(alpha = if (aurora.isDark) 0.14f else 0.10f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (aurora.isEInk) {
                        resolveAuroraBorderColor(aurora, false)
                    } else {
                        aurora.accent.copy(alpha = 0.45f)
                    },
                ),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = stringResource(AYMR.strings.ai_translation_swap_languages),
                        tint = if (aurora.isEInk) aurora.textPrimary else aurora.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            AiTranslationOutlinedTextField(
                value = targetValue,
                onValueChange = onTargetChange,
                isError = isError,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (isError) {
            Text(
                text = stringResource(AYMR.strings.ai_translation_error_field),
                style = MaterialTheme.typography.bodySmall,
                color = aurora.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Full-width bottom-anchored Aurora frosted sheet (Variant A) for AI Translator editors.
 * Provides +40dp horizontal width over centered 92% modals, a 44x44dp close hitbox,
 * hairline dividers, IME-safe sticky footer, and full E-Ink compatibility.
 */
@Composable
internal fun AiTranslationDockedSheet(
    onDismiss: () -> Unit,
    title: String,
    closeLabel: String,
    subtitle: String? = null,
    contentSpacing: Dp = 12.dp,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val sheetBackground = when {
        colors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
        colors.isDark && colors.isAmoled -> Color(0xFF0A0B10).copy(alpha = 0.94f)
        colors.isDark -> Color(0xFF13161D).copy(alpha = 0.94f)
        else -> Color(0xFFFDFEFF).copy(alpha = 0.96f)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            val window = dialogWindow ?: return@DisposableEffect onDispose {}
            val previousSoftInput = window.attributes.softInputMode
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose {
                window.setSoftInputMode(previousSoftInput)
            }
        }
        SideEffect {
            dialogWindow?.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            if (!colors.isEInk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow?.let { w ->
                    runCatching { w.setBackgroundBlurRadius(48) }
                    val lp = w.attributes
                    lp.blurBehindRadius = 28
                    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    w.attributes = lp
                }
            }
            @Suppress("DEPRECATION")
            dialogWindow?.setDimAmount(if (colors.isDark) 0.45f else 0.42f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = 760.dp)
                    .padding(top = 24.dp)
                    .clip(sheetShape)
                    .border(
                        width = 1.dp,
                        brush = if (colors.isEInk) {
                            SolidColor(resolveAuroraBorderColor(colors, false))
                        } else {
                            auroraMenuRimLightBrush(colors)
                        },
                        shape = sheetShape,
                    )
                    .background(sheetBackground, sheetShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                // Drag handle & sheet header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 10.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (colors.isEInk) {
                                    colors.divider
                                } else {
                                    colors.textSecondary.copy(alpha = 0.35f)
                                },
                            ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    appHaptics.tap()
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        resolveAuroraTranslationGlassContainerColor(colors, subtle = true),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .border(
                                        1.dp,
                                        resolveAuroraTranslationGlassBorderBrush(colors),
                                        RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = closeLabel,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.divider.copy(alpha = if (colors.isEInk) 1f else 0.65f))

                // Scrollable body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing),
                ) {
                    content()
                }

                // Sticky hairline-separated footer
                if (footer != null) {
                    HorizontalDivider(color = colors.divider.copy(alpha = if (colors.isEInk) 1f else 0.65f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        content = footer,
                    )
                } else {
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

/**
 * Balanced 48dp equal-height sticky footer (1 : 1.5 width ratio, unified 12dp radius).
 * Stacks vertically automatically at large accessibility font scales (>= 1.3f).
 */
@Composable
internal fun AiTranslationDockedFooter(
    cancelLabel: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    if (LocalDensity.current.fontScale >= 1.3f) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AiTranslationPrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
            AiTranslationSecondaryButton(
                text = cancelLabel,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiTranslationSecondaryButton(
                text = cancelLabel,
                onClick = onCancel,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
            AiTranslationPrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.weight(1.5f).heightIn(min = 48.dp),
            )
        }
    }
}

/** Grouped inset card (16dp radius) for logically separating editor sections. */
@Composable
internal fun AiTranslationSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    contentSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val aurora = AuroraTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = resolveAuroraTranslationGlassContainerColor(aurora, subtle = true),
        border = BorderStroke(1.dp, resolveAuroraTranslationGlassBorderBrush(aurora)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            if (!title.isNullOrBlank() || headerAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = aurora.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    headerAction?.invoke()
                }
            }
            content()
        }
    }
}

/**
 * Compact inline action button for section headers (+ Add key) and diagnostic rows (Test).
 * Keeps a clean 36dp visual silhouette wrapped inside a compliant 44dp touch hitbox.
 */
@Composable
internal fun AiTranslationInlineActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accented: Boolean = false,
) {
    val aurora = AuroraTheme.colors
    val contentAlpha = if (enabled) 1f else 0.45f
    val containerColor = when {
        aurora.isEInk -> resolveAuroraControlContainerColor(aurora)
        accented -> aurora.accent.copy(alpha = if (aurora.isDark) 0.16f else 0.11f)
        else -> resolveAuroraTranslationGlassContainerColor(aurora, subtle = true)
    }
    val borderBrush = when {
        aurora.isEInk -> SolidColor(resolveAuroraBorderColor(aurora, false))
        accented -> SolidColor(aurora.accent.copy(alpha = 0.55f))
        else -> resolveAuroraTranslationGlassBorderBrush(aurora)
    }
    val contentColor = when {
        aurora.isEInk -> aurora.textPrimary
        accented -> aurora.accent
        else -> aurora.textPrimary
    }

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = containerColor,
            border = BorderStroke(1.dp, borderBrush),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.heightIn(min = 36.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = contentAlpha),
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Clean single-row segmented control track (12dp outer / 8dp inner radius) for 2..4 options.
 * Automatically falls back to wrapping chip flow at large accessibility font scales or >4 items.
 */
@Composable
internal fun <T> AiTranslationSegmentedGroup(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    if (options.size !in 2..4 || fontScale >= 1.35f) {
        AiTranslationChipGroup(
            label = label,
            options = options,
            selected = selected,
            onSelect = onSelect,
            modifier = modifier,
        )
        return
    }
    val aurora = AuroraTheme.colors
    val trackColor = when {
        aurora.isEInk -> resolveAuroraControlContainerColor(aurora)
        aurora.isDark -> Color(0xFF0C0E13)
        else -> Color(0xFFE8EEF5)
    }
    val activeContainer = when {
        aurora.eInkProfile == EInkProfile.MONOCHROME -> aurora.textPrimary
        aurora.eInkProfile == EInkProfile.COLOR -> aurora.accent
        aurora.isDark -> Color(0xFF212632)
        else -> Color.White
    }
    val activeText = when {
        aurora.eInkProfile == EInkProfile.MONOCHROME -> aurora.background
        aurora.eInkProfile == EInkProfile.COLOR -> aurora.textOnAccent
        else -> aurora.accent
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = aurora.textSecondary,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = trackColor,
            border = BorderStroke(
                width = 1.dp,
                color = if (aurora.isEInk) resolveAuroraBorderColor(aurora, false) else aurora.divider,
            ),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEach { (value, title) ->
                    val isSelected = value == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) activeContainer else Color.Transparent)
                            .clickable(role = Role.RadioButton) { onSelect(value) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) activeText else aurora.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
