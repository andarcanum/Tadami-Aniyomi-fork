package eu.kanade.presentation.reader.novel

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.entries.translation.googleTranslationLanguageSuggestions
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraToggleRow
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationPhase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@Composable
fun GoogleTranslationDialog(
    readerSettings: NovelReaderSettings,
    isTranslating: Boolean,
    translationProgress: Int,
    translationPhase: TranslationPhase = TranslationPhase.IDLE,
    isVisible: Boolean,
    hasCache: Boolean,
    isRateLimited: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
    onToggleVisibility: () -> Unit,
    onClear: () -> Unit,
    onSetAutoStart: (Boolean) -> Unit,
    onSetSourceLang: (String) -> Unit,
    onSetTargetLang: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var sourceLang by remember(readerSettings.googleTranslationSourceLang) {
        mutableStateOf(readerSettings.googleTranslationSourceLang)
    }
    var targetLang by remember(readerSettings.googleTranslationTargetLang) {
        mutableStateOf(readerSettings.googleTranslationTargetLang)
    }
    LaunchedEffect(readerSettings.googleTranslationSourceLang) {
        sourceLang = readerSettings.googleTranslationSourceLang
    }
    LaunchedEffect(readerSettings.googleTranslationTargetLang) {
        targetLang = readerSettings.googleTranslationTargetLang
    }
    var sourceExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    var autoStartDraft by remember {
        mutableStateOf(readerSettings.googleTranslationAutoStart)
    }
    var previousAutoStartCommitted by remember {
        mutableStateOf<Boolean?>(null)
    }
    LaunchedEffect(readerSettings.googleTranslationAutoStart) {
        val synced = syncGoogleTranslationToggleDraft(
            committedValue = readerSettings.googleTranslationAutoStart,
            previousCommittedValue = previousAutoStartCommitted,
            currentDraftValue = autoStartDraft,
        )
        previousAutoStartCommitted = synced.committedValue
        autoStartDraft = synced.draftValue
    }

    val aurora = AuroraTheme.colors
    val baseScheme = MaterialTheme.colorScheme
    var sheetReveal by remember { mutableFloatStateOf(0f) }
    val supportsBlurBehind = eu.kanade.presentation.util.rememberSupportsBlurBehind(aurora.isEInk)

    val sheetContainer = remember(aurora.isDark, aurora.isEInk, supportsBlurBehind) {
        when {
            aurora.isEInk -> baseScheme.surfaceContainerHigh
            !supportsBlurBehind -> aurora.surface
            aurora.isDark -> Color.Black.copy(alpha = 0.70f)
            else -> Color.White.copy(alpha = 0.88f)
        }
    }
    val auroraScheme = remember(baseScheme, aurora, sheetContainer) {
        baseScheme.copy(
            primary = aurora.accent,
            onPrimary = if (aurora.isDark) aurora.background else Color.White,
            surfaceContainerHigh = sheetContainer,
            surfaceContainerHighest = sheetContainer,
            secondaryContainer = aurora.accent.copy(alpha = 0.22f),
            onSecondaryContainer = aurora.accent,
        )
    }
    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = ZeroCornerSize,
        bottomEnd = ZeroCornerSize,
    )
    val pageMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp

    MaterialTheme(
        colorScheme = auroraScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        AdaptiveSheet(
            onDismissRequest = onDismiss,
            modifier = Modifier.border(
                width = 1.dp,
                color = auroraRimColor(),
                shape = sheetShape,
            ),
            containerColor = sheetContainer,
            scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
            applyStatusBarsPadding = false,
            onRevealChange = { sheetReveal = it },
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            val revealState = rememberUpdatedState(sheetReveal)

            DisposableEffect(window, supportsBlurBehind) {
                val w = window
                if (w != null && supportsBlurBehind) {
                    w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                    w.setDimAmount(0f)
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                }
                onDispose {
                    if (w != null && supportsBlurBehind) {
                        w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                        w.setDimAmount(0f)
                        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
            }

            LaunchedEffect(window, supportsBlurBehind) {
                val w = window ?: return@LaunchedEffect
                if (!supportsBlurBehind) return@LaunchedEffect
                snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                    .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                    .distinctUntilChanged()
                    .collect { step ->
                        applyGoogleTranslationSheetWindowFx(
                            window = w,
                            reveal = step / 20f,
                        )
                    }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = pageMaxHeight)
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (aurora.isDark) {
                                    Color.White.copy(alpha = 0.22f)
                                } else {
                                    Color.Black.copy(alpha = 0.18f)
                                },
                            ),
                    )
                }

                Text(
                    text = stringResource(AYMR.strings.novel_reader_google_translate),
                    style = MaterialTheme.typography.titleMedium,
                    color = aurora.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                // Settings section card
                AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_google_translate)) {
                    GoogleTranslationLanguageField(
                        value = sourceLang,
                        onValueChange = {
                            sourceLang = it
                            onSetSourceLang(it)
                        },
                        label = stringResource(AYMR.strings.novel_reader_google_translate_source),
                        placeholder = "auto",
                        expanded = sourceExpanded,
                        onExpandedChange = { sourceExpanded = it },
                        enabled = !isTranslating,
                    )

                    GoogleTranslationLanguageField(
                        value = targetLang,
                        onValueChange = {
                            targetLang = it
                            onSetTargetLang(it)
                        },
                        label = stringResource(AYMR.strings.novel_reader_google_translate_target),
                        placeholder = "ru",
                        expanded = targetExpanded,
                        onExpandedChange = { targetExpanded = it },
                        enabled = !isTranslating,
                    )

                    AuroraToggleRow(
                        label = stringResource(AYMR.strings.novel_reader_google_translate_auto_start),
                        subtitle = stringResource(AYMR.strings.novel_reader_google_translate_backend_simple),
                        checked = autoStartDraft,
                        enabled = !isTranslating,
                        onClick = {
                            val newValue = !autoStartDraft
                            autoStartDraft = newValue
                            onSetAutoStart(newValue)
                        },
                    )

                    if (isTranslating) {
                        LinearProgressIndicator(
                            progress = { translationProgress.coerceIn(0, 100) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                        val progressText = when (translationPhase) {
                            TranslationPhase.IDLE ->
                                stringResource(AYMR.strings.novel_reader_google_translate_preparing)
                            TranslationPhase.TRANSLATING ->
                                stringResource(
                                    AYMR.strings.novel_reader_google_translate_progress,
                                    translationProgress.coerceIn(0, 100),
                                )
                        }
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = aurora.textSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                if (isRateLimited) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_google_translate_rate_limited),
                        style = MaterialTheme.typography.bodyMedium,
                        color = aurora.textSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }

                if (hasCache) {
                    AuroraGlassSection {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onToggleVisibility,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text(
                                    if (isVisible) {
                                        stringResource(AYMR.strings.novel_reader_google_translate_original)
                                    } else {
                                        stringResource(AYMR.strings.novel_reader_google_translate_translated)
                                    },
                                    maxLines = 2,
                                )
                            }
                            OutlinedButton(
                                onClick = onClear,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text(
                                    stringResource(AYMR.strings.novel_reader_google_translate_clear),
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Clean action buttons floating directly on glass sheet surface (no nested glass card wrapper)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when {
                            isTranslating -> {
                                Button(
                                    onClick = onStop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                ) {
                                    Text(
                                        stringResource(AYMR.strings.novel_reader_google_translate_stop),
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            hasCache && !isVisible -> {
                                Button(
                                    onClick = onToggleVisibility,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                ) {
                                    Text(
                                        stringResource(AYMR.strings.novel_reader_google_translate_show_translation),
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            hasCache && isVisible -> {
                                Button(
                                    onClick = onToggleVisibility,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                ) {
                                    Text(
                                        stringResource(AYMR.strings.novel_reader_google_translate_hide_translation),
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            isRateLimited -> {
                                // A 429 storm left the chapter partially translated; offer the
                                // explicit retry instead of a plain Start so the state is honest.
                                Button(
                                    onClick = onResume,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                ) {
                                    Text(
                                        stringResource(AYMR.strings.novel_reader_google_translate_resume),
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            else -> {
                                Button(
                                    onClick = onStart,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                ) {
                                    Text(
                                        stringResource(AYMR.strings.novel_reader_google_translate_start),
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }

                        if (hasCache && !isTranslating) {
                            OutlinedButton(
                                onClick = {
                                    onClear()
                                    onStart()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                            ) {
                                Text(
                                    stringResource(AYMR.strings.novel_reader_google_translate_retranslate),
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                    ) {
                        Text(
                            stringResource(AYMR.strings.novel_reader_selected_text_translation_action_close),
                            color = aurora.textSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun applyGoogleTranslationSheetWindowFx(
    window: Window,
    reveal: Float,
) {
    val glass = ((reveal - 0.18f) / 0.82f).coerceIn(0f, 1f)
    val radius = if (glass <= 0.02f) {
        0
    } else {
        (44f * glass).roundToInt().coerceIn(1, 48)
    }
    val attrs = window.attributes
    if (attrs.blurBehindRadius != radius) {
        window.attributes = attrs.apply { blurBehindRadius = radius }
    }
    window.setDimAmount(0.18f * glass)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoogleTranslationLanguageField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    val suggestions = remember(value) {
        googleTranslationLanguageSuggestions(value)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = {
            if (enabled && suggestions.isNotEmpty()) {
                onExpandedChange(!expanded)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                onExpandedChange(it.isNotBlank() && googleTranslationLanguageSuggestions(it).isNotEmpty())
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && suggestions.isNotEmpty())
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
        )

        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { onExpandedChange(false) },
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = {
                        Text("${suggestion.canonicalName} (${suggestion.code})")
                    },
                    onClick = {
                        onValueChange(suggestion.canonicalName)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

data class GoogleTranslationToggleDraftState(
    val committedValue: Boolean,
    val draftValue: Boolean,
)

fun syncGoogleTranslationToggleDraft(
    committedValue: Boolean,
    previousCommittedValue: Boolean?,
    currentDraftValue: Boolean,
): GoogleTranslationToggleDraftState {
    val draftValue = if (previousCommittedValue == null || committedValue != previousCommittedValue) {
        committedValue
    } else {
        currentDraftValue
    }
    return GoogleTranslationToggleDraftState(
        committedValue = committedValue,
        draftValue = draftValue,
    )
}
