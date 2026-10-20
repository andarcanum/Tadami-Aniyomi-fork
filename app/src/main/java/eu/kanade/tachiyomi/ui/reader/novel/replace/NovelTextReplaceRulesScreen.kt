package eu.kanade.tachiyomi.ui.reader.novel.replace

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.presentation.more.settings.LocalSettingsUiStyle
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.settingsSubtitleColor
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.auroraHeaderIconSurface
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource

class NovelTextReplaceRulesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        NovelTextReplaceRulesScreenContent(onBack = { navigator.pop() })
    }
}

@Composable
private fun NovelTextReplaceRulesScreenContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val colors = AuroraTheme.colors
    val uiStyle = rememberResolvedSettingsUiStyle()
    val isAurora = uiStyle == SettingsUiStyle.Aurora
    val hazeState = remember { HazeState() }
    val prefs = remember { Injekt.get<NovelReaderPreferences>() }
    var rules by remember { mutableStateOf(prefs.replaceRules()) }
    var editorRule by remember { mutableStateOf<ReplaceRule?>(null) }
    var editorIsNew by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showTest by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    fun commit(next: List<ReplaceRule>) {
        rules = next
        prefs.setReplaceRules(next)
    }

    val actionIconSurface: Modifier = if (isAurora) {
        Modifier.auroraHeaderIconSurface(colors)
    } else {
        Modifier
    }

    CompositionLocalProvider(LocalSettingsUiStyle provides uiStyle) {
        Box(modifier = Modifier.fillMaxSize()) {
            SettingsScaffold(
                title = stringResource(AYMR.strings.novel_reader_text_replace),
                uiStyle = uiStyle,
                onBackPressed = onBack,
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                editorRule = ReplaceRule(
                                    id = (rules.maxOfOrNull { it.id } ?: 0L) + 1,
                                    order = rules.size,
                                )
                                editorIsNew = true
                            },
                            modifier = actionIconSurface,
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringResource(AYMR.strings.novel_reader_text_replace_add),
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = actionIconSurface,
                            ) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = null)
                            }
                            if (isAurora) {
                                AuroraEntryDropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    AuroraEntryDropdownMenuItem(
                                        text = context.contextStringResource(
                                            AYMR.strings.novel_reader_text_replace_import,
                                        ),
                                        onClick = {
                                            menuExpanded = false
                                            showImport = true
                                        },
                                    )
                                    AuroraEntryDropdownMenuItem(
                                        text = context.contextStringResource(
                                            AYMR.strings.novel_reader_text_replace_export,
                                        ),
                                        onClick = {
                                            menuExpanded = false
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(ClipData.newPlainText(null, prefs.exportReplaceRules())),
                                                )
                                            }
                                            Toast.makeText(
                                                context,
                                                context.contextStringResource(
                                                    AYMR.strings.novel_reader_text_replace_export_copied,
                                                ),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    )
                                    AuroraEntryDropdownMenuItem(
                                        text = context.contextStringResource(
                                            AYMR.strings.novel_reader_text_replace_test,
                                        ),
                                        onClick = {
                                            menuExpanded = false
                                            showTest = true
                                        },
                                    )
                                }
                            } else {
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(AYMR.strings.novel_reader_text_replace_import)) },
                                        onClick = {
                                            menuExpanded = false
                                            showImport = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(AYMR.strings.novel_reader_text_replace_export)) },
                                        onClick = {
                                            menuExpanded = false
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(ClipData.newPlainText(null, prefs.exportReplaceRules())),
                                                )
                                            }
                                            Toast.makeText(
                                                context,
                                                context.contextStringResource(
                                                    AYMR.strings.novel_reader_text_replace_export_copied,
                                                ),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(AYMR.strings.novel_reader_text_replace_test)) },
                                        onClick = {
                                            menuExpanded = false
                                            showTest = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .hazeSource(hazeState),
                ) {
                    if (rules.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_text_replace_empty),
                                color = settingsSubtitleColor(),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                                BasePreferenceWidget(
                                    title = rule.name.ifBlank { rule.pattern },
                                    subcomponent = {
                                        Text(
                                            text = "\"${rule.pattern}\" → \"${rule.replacement}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = settingsSubtitleColor(),
                                            maxLines = 2,
                                        )
                                    },
                                    onClick = {
                                        editorRule = rule
                                        editorIsNew = false
                                    },
                                    widget = {
                                        Switch(
                                            checked = rule.isEnabled,
                                            onCheckedChange = { enabled ->
                                                commit(
                                                    rules.toMutableList().also {
                                                        it[index] =
                                                            rule.copy(isEnabled = enabled)
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                                if (index < rules.lastIndex) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }

            editorRule?.let { rule ->
                RuleEditorDialog(
                    hazeState = hazeState,
                    initial = rule,
                    isNew = editorIsNew,
                    onDismiss = { editorRule = null },
                    onSave = { updated ->
                        val next = if (editorIsNew) {
                            rules + updated
                        } else {
                            rules.map { if (it.id == rule.id) updated else it }
                        }
                        commit(next)
                        editorRule = null
                    },
                    onDelete = {
                        commit(rules.filterNot { it.id == rule.id })
                        editorRule = null
                    },
                )
            }

            if (showImport) {
                ImportRulesDialog(
                    hazeState = hazeState,
                    onDismiss = { showImport = false },
                    onImport = { raw ->
                        prefs.importReplaceRules(raw)
                            .onSuccess { imported ->
                                commit(imported)
                                Toast.makeText(
                                    context,
                                    context.contextStringResource(
                                        AYMR.strings.novel_reader_text_replace_import_done,
                                        imported.size,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    context.contextStringResource(AYMR.strings.novel_reader_text_replace_import_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        showImport = false
                    },
                )
            }

            if (showTest) {
                TestRulesDialog(hazeState = hazeState, rules = rules, onDismiss = { showTest = false })
            }
        }
    }
}

/**
 * Frosted haze glass panel with a dim scrim — the same language as the nickname editor dialog.
 * Blurs only what sits under the panel (the [hazeSource] of this screen), nothing else.
 */
@Composable
private fun RulesGlassDialog(
    hazeState: HazeState,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val cardShape = RoundedCornerShape(28.dp)

    val cardFrostBase = when {
        !colors.isEInk && colors.isDark ->
            Color.White.copy(alpha = 0.06f).compositeOver(colors.background.copy(alpha = 0.40f))
        !colors.isEInk -> Color.White.copy(alpha = 0.55f)
        colors.isDark ->
            Color.White.copy(alpha = 0.10f).compositeOver(colors.background.copy(alpha = 0.90f))
        else -> Color.White.copy(alpha = 0.92f)
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = when {
                            colors.isEInk -> 0.55f
                            colors.isDark -> 0.45f
                            else -> 0.30f
                        },
                    ),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = 0.40f),
                    spotColor = Color.Black.copy(alpha = 0.28f),
                )
                .clip(cardShape)
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = HazeBlurStyle {
                        backgroundColor(colors.background)
                        colorEffects(listOf(HazeColorEffect.tint(colors.surface.copy(alpha = 0.55f))))
                        blurRadius(24.dp)
                        noiseFactor(0.12f)
                    },
                )
                .background(cardFrostBase)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}, // absorb scrim dismiss
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons()
            }
        }
    }
}

@Composable
private fun RuleEditorDialog(
    hazeState: HazeState,
    initial: ReplaceRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReplaceRule) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AuroraTheme.colors
    var name by remember(initial) { mutableStateOf(initial.name) }
    var pattern by remember(initial) { mutableStateOf(initial.pattern) }
    var replacement by remember(initial) { mutableStateOf(initial.replacement) }
    var isRegex by remember(initial) { mutableStateOf(initial.isRegex) }
    var isEnabled by remember(initial) { mutableStateOf(initial.isEnabled) }
    var scopeTitle by remember(initial) { mutableStateOf(initial.scopeTitle) }
    var scopeContent by remember(initial) { mutableStateOf(initial.scopeContent) }

    val patternValid = pattern.isNotBlank() && (!isRegex || runCatching { Regex(pattern) }.isSuccess)

    RulesGlassDialog(
        hazeState = hazeState,
        title = stringResource(
            if (isNew) {
                AYMR.strings.novel_reader_text_replace_add
            } else {
                AYMR.strings.novel_reader_text_replace_edit
            },
        ),
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RulesTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = AYMR.strings.novel_reader_text_replace_name,
                )
                RulesTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = AYMR.strings.novel_reader_text_replace_pattern,
                    placeholder = AYMR.strings.novel_reader_text_replace_pattern_hint,
                    isError = !patternValid,
                    supportingText = if (!patternValid) {
                        AYMR.strings.novel_reader_text_replace_invalid_pattern
                    } else {
                        null
                    },
                )
                RulesTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = AYMR.strings.novel_reader_text_replace_replacement,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isRegex, onCheckedChange = { isRegex = it })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_text_replace_regex),
                        color = colors.textPrimary,
                    )
                }
                Text(
                    text = stringResource(AYMR.strings.novel_reader_text_replace_scope),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                ScopeOption(
                    label = AYMR.strings.novel_reader_text_replace_scope_content,
                    selected = scopeContent && !scopeTitle,
                    onClick = {
                        scopeContent = true
                        scopeTitle = false
                    },
                )
                ScopeOption(
                    label = AYMR.strings.novel_reader_text_replace_scope_title,
                    selected = scopeTitle && !scopeContent,
                    onClick = {
                        scopeTitle = true
                        scopeContent = false
                    },
                )
                ScopeOption(
                    label = AYMR.strings.novel_reader_text_replace_scope_both,
                    selected = scopeTitle && scopeContent,
                    onClick = {
                        scopeTitle = true
                        scopeContent = true
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_text_replace_enabled),
                        color = colors.textPrimary,
                    )
                }
            }
        },
        buttons = {
            if (!isNew) {
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(AYMR.strings.novel_reader_text_replace_delete),
                        tint = colors.textSecondary,
                    )
                }
            }
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_text_replace_cancel),
                    color = colors.textSecondary,
                )
            }
            TextButton(
                enabled = patternValid,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            pattern = pattern,
                            replacement = replacement,
                            isRegex = isRegex,
                            isEnabled = isEnabled,
                            scopeTitle = scopeTitle,
                            scopeContent = scopeContent,
                        ),
                    )
                },
            ) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_text_replace_save),
                    color = colors.accent,
                )
            }
        },
    )
}

@Composable
private fun ScopeOption(
    label: StringResource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(label),
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun ImportRulesDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    val colors = AuroraTheme.colors
    var raw by remember { mutableStateOf("") }
    RulesGlassDialog(
        hazeState = hazeState,
        title = stringResource(AYMR.strings.novel_reader_text_replace_import),
        onDismiss = onDismiss,
        content = {
            RulesTextField(
                value = raw,
                onValueChange = { raw = it },
                placeholder = AYMR.strings.novel_reader_text_replace_import_prompt,
                minLines = 6,
            )
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_text_replace_cancel),
                    color = colors.textSecondary,
                )
            }
            TextButton(
                enabled = raw.isNotBlank(),
                onClick = { onImport(raw) },
            ) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_text_replace_import),
                    color = colors.accent,
                )
            }
        },
    )
}

@Composable
private fun TestRulesDialog(
    hazeState: HazeState,
    rules: List<ReplaceRule>,
    onDismiss: () -> Unit,
) {
    val colors = AuroraTheme.colors
    var sample by remember {
        mutableStateOf(
            "Глава 12. Рейнар встал и посмотрел на Лайлу. " +
                "本章未完，请点击下一页继续阅读",
        )
    }
    var result by remember(sample, rules) { mutableStateOf(sample) }
    LaunchedEffect(sample, rules) {
        result = withContext(Dispatchers.Default) {
            applyReplaceRulesToText(sample, rules.filter { it.isEnabled && it.isValid() })
        }
    }
    RulesGlassDialog(
        hazeState = hazeState,
        title = stringResource(AYMR.strings.novel_reader_text_replace_test),
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RulesTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    label = AYMR.strings.novel_reader_text_replace_test_sample,
                    minLines = 4,
                )
                Text(
                    text = stringResource(AYMR.strings.novel_reader_text_replace_test_result),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_text_replace_cancel),
                    color = colors.textSecondary,
                )
            }
        },
    )
}

/**
 * Rounded glass input field — same language as the nickname editor dialog: transparent
 * unfocused border, soft fill, accent focus.
 */
@Composable
private fun RulesTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource? = null,
    placeholder: StringResource? = null,
    isError: Boolean = false,
    supportingText: StringResource? = null,
    minLines: Int = 1,
) {
    val colors = AuroraTheme.colors
    val fieldFill = if (colors.isDark) {
        Color.White.copy(alpha = 0.07f)
    } else {
        Color.Black.copy(alpha = 0.04f)
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(stringResource(it)) } },
        placeholder = placeholder?.let { { Text(stringResource(it)) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(stringResource(it)) } },
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent.copy(alpha = 0.45f),
            unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.accent,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedContainerColor = fieldFill,
            unfocusedContainerColor = fieldFill,
            errorContainerColor = fieldFill,
            errorBorderColor = colors.error,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
