@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.presentation.reader.novel

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.reader.settings.AuroraFieldLabel
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraToggleRow
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTypographyPreset
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

@Composable
fun ReadingTab(
    settings: eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings,
    sourceId: Long,
    overrideEnabled: Boolean,
    preferences: NovelReaderPreferences,
) {
    val context = LocalContext.current

    fun <T> update(
        value: T,
        copyOverride: (NovelReaderOverride, T) -> NovelReaderOverride,
        setGlobal: (T) -> Unit,
    ) {
        if (overrideEnabled) {
            preferences.updateSourceOverride(sourceId) { copyOverride(it, value) }
        } else {
            setGlobal(value)
        }
    }

    val selectedTheme = resolveNovelReaderColorTheme(settings.backgroundColor.orEmpty(), settings.textColor.orEmpty())
    val isPreset = selectedTheme != null && novelReaderPresetThemes.contains(selectedTheme)
    val isCustom = selectedTheme != null && settings.customThemes.contains(selectedTheme)
    val colorTiles = remember(settings.customThemes) {
        (settings.customThemes + novelReaderPresetThemes).distinctBy { "${it.backgroundColor}:${it.textColor}" }
    }
    val importFailedMessage = stringResource(AYMR.strings.novel_reader_background_custom_import_failed)
    val fontImportFailedMessage = stringResource(AYMR.strings.novel_reader_font_import_failed)
    val appearanceControlState = remember(settings.appearanceMode) {
        resolveAppearanceControlState(settings.appearanceMode)
    }
    var backgroundCatalogVersion by remember { mutableIntStateOf(0) }
    var fontCatalogVersion by remember { mutableIntStateOf(0) }
    var renameTarget by remember { mutableStateOf<NovelReaderCustomBackgroundItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var pendingReplaceCustomId by remember { mutableStateOf<String?>(null) }
    val readerFontCatalog = remember(fontCatalogVersion) {
        buildNovelReaderFontCatalog(context)
    }

    val customBackgroundItems = remember(
        settings.customBackgroundId,
        settings.customBackgroundPath,
        backgroundCatalogVersion,
    ) {
        readNovelReaderCustomBackgroundItems(context)
    }
    val backgroundCards = remember(customBackgroundItems) {
        buildNovelReaderBackgroundCardsFromCustomItems(customBackgroundItems)
    }

    LaunchedEffect(
        settings.customBackgroundId,
        settings.customBackgroundPath,
    ) {
        if (settings.customBackgroundPath.isBlank()) return@LaunchedEffect
        if (settings.customBackgroundId.isBlank()) {
            update(
                settings.customBackgroundPath,
                { o, v -> o.copy(customBackgroundId = v) },
                { preferences.customBackgroundId().set(it) },
            )
            return@LaunchedEffect
        }
        if (settings.customBackgroundId == settings.customBackgroundPath) {
            val migrated = ensureLegacyNovelReaderBackgroundItem(
                context = context,
                legacyPath = settings.customBackgroundPath,
                preferredId = settings.customBackgroundId,
            ).getOrNull()
            if (migrated != null) {
                update(
                    migrated.absolutePath,
                    { o, v -> o.copy(customBackgroundPath = v) },
                    { preferences.customBackgroundPath().set(it) },
                )
                backgroundCatalogVersion += 1
            }
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val importedItem = importNovelReaderCustomBackgroundItem(context, uri).getOrNull()
        if (importedItem == null) {
            Toast.makeText(
                context,
                importFailedMessage,
                Toast.LENGTH_SHORT,
            ).show()
            return@rememberLauncherForActivityResult
        }
        update(
            NovelReaderAppearanceMode.BACKGROUND,
            { o, v -> o.copy(appearanceMode = v) },
            { preferences.appearanceMode().set(it) },
        )
        update(
            NovelReaderBackgroundSource.CUSTOM,
            { o, v -> o.copy(backgroundSource = v) },
            { preferences.backgroundSource().set(it) },
        )
        update(
            importedItem.id,
            { o, v -> o.copy(customBackgroundId = v) },
            { preferences.customBackgroundId().set(it) },
        )
        update(
            importedItem.absolutePath,
            { o, v -> o.copy(customBackgroundPath = v) },
            { preferences.customBackgroundPath().set(it) },
        )
        backgroundCatalogVersion += 1
    }
    val replaceBackgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val targetId = pendingReplaceCustomId
        pendingReplaceCustomId = null
        if (uri == null || targetId.isNullOrBlank()) return@rememberLauncherForActivityResult
        val replaced = replaceNovelReaderCustomBackgroundItem(
            context = context,
            id = targetId,
            uri = uri,
        ).getOrNull()
        if (replaced == null) {
            Toast.makeText(context, importFailedMessage, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (settings.customBackgroundId == targetId) {
            update(
                replaced.absolutePath,
                { o, v -> o.copy(customBackgroundPath = v) },
                { preferences.customBackgroundPath().set(it) },
            )
        }
        backgroundCatalogVersion += 1
    }
    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val importedFont = importNovelReaderCustomFont(context, uri).getOrNull()
        if (importedFont == null) {
            Toast.makeText(context, fontImportFailedMessage, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        update(importedFont.id, { o, v -> o.copy(fontFamily = v) }, { preferences.fontFamily().set(it) })
        fontCatalogVersion += 1
    }

    val typographyPresetEntries = novelReaderTypographyPresetEntries()
    val typographyPresetOrder = remember {
        listOf(
            NovelReaderTypographyPreset.SUPERGOLDEN,
            NovelReaderTypographyPreset.GOLDEN,
            NovelReaderTypographyPreset.CUSTOM,
        )
    }
    val selectedFontOption = remember(readerFontCatalog, settings.fontFamily) {
        readerFontCatalog.firstOrNull { it.id == settings.fontFamily }
            ?: readerFontCatalog.firstOrNull()
    }
    val previewFontFamily = remember(selectedFontOption, context, settings.forceBoldText, settings.forceItalicText) {
        selectedFontOption?.let { option ->
            resolveNovelReaderComposeFontFamily(
                font = option,
                typeface = loadNovelReaderTypeface(
                    context = context,
                    font = option,
                    forceBoldText = settings.forceBoldText,
                    forceItalicText = settings.forceItalicText,
                ),
            )
        }
    }
    val previewTextAlign = when (settings.textAlign) {
        TextAlign.SOURCE -> null
        TextAlign.LEFT -> ComposeTextAlign.Start
        TextAlign.CENTER -> ComposeTextAlign.Center
        TextAlign.JUSTIFY -> ComposeTextAlign.Justify
        TextAlign.RIGHT -> ComposeTextAlign.End
    }
    val previewPaper = if (AuroraTheme.colors.isDark) {
        Color(0xFF1C1A16)
    } else {
        Color(0xFFF3E7D0)
    }
    val previewInk = if (AuroraTheme.colors.isDark) {
        Color(0xFFE8DFD0)
    } else {
        Color(0xFF1A1612)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        NovelLiveTypePreview(
            sampleText = stringResource(AYMR.strings.novel_reader_live_preview_sample),
            badgeLabel = stringResource(AYMR.strings.novel_reader_live_preview_badge),
            fontSizeSp = settings.fontSize,
            lineHeightEm = settings.lineHeight,
            textAlign = previewTextAlign,
            forceBold = settings.forceBoldText,
            forceItalic = settings.forceItalicText,
            forceIndent = settings.forceParagraphIndent,
            textShadow = settings.textShadow,
            fontFamily = previewFontFamily,
            textColor = previewInk,
            paperColor = previewPaper,
        )

        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_typography)) {
            NovelChipStrip(
                options = typographyPresetOrder.map { preset ->
                    (typographyPresetEntries[preset].orEmpty()) to (settings.typographyPreset == preset)
                },
                onSelectIndex = { index ->
                    val preset = typographyPresetOrder[index]
                    update(
                        preset,
                        { o, v -> o.copy(typographyPreset = v) },
                        { preferences.typographyPreset().set(it) },
                    )
                },
            )
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_font_size),
                valueText = { "${it.roundToInt()}sp" },
                committedValue = settings.fontSize.toFloat(),
                range = 12f..28f,
                steps = 15,
                onCommit = {
                    update(it.roundToInt(), { o, v -> o.copy(fontSize = v) }, { preferences.fontSize().set(it) })
                },
            )
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_line_height),
                valueText = { String.format("%.2f", it) },
                committedValue = settings.lineHeight,
                range = 1.2f..2f,
                steps = 16,
                onCommit = {
                    if (settings.typographyPreset != NovelReaderTypographyPreset.CUSTOM) {
                        update(
                            NovelReaderTypographyPreset.CUSTOM,
                            { o, v -> o.copy(typographyPreset = v) },
                            { preferences.typographyPreset().set(it) },
                        )
                    }
                    update(it, { o, v -> o.copy(lineHeight = v) }, { preferences.lineHeight().set(it) })
                },
            )
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_paragraph_spacing),
                valueText = { "${it.roundToInt()}dp" },
                committedValue = settings.paragraphSpacing.toFloat(),
                range = 0f..32f,
                steps = 31,
                onCommit = {
                    if (settings.typographyPreset != NovelReaderTypographyPreset.CUSTOM) {
                        update(
                            NovelReaderTypographyPreset.CUSTOM,
                            { o, v -> o.copy(typographyPreset = v) },
                            { preferences.typographyPreset().set(it) },
                        )
                    }
                    update(
                        it.roundToInt(),
                        { o, v -> o.copy(paragraphSpacingDp = v) },
                        { preferences.paragraphSpacing().set(it) },
                    )
                },
            )
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_margins),
                valueText = { "${it.roundToInt()}dp" },
                committedValue = settings.margin.toFloat(),
                range = 0f..50f,
                steps = 49,
                onCommit = {
                    if (settings.typographyPreset != NovelReaderTypographyPreset.CUSTOM) {
                        update(
                            NovelReaderTypographyPreset.CUSTOM,
                            { o, v -> o.copy(typographyPreset = v) },
                            { preferences.typographyPreset().set(it) },
                        )
                    }
                    update(it.roundToInt(), { o, v -> o.copy(margin = v) }, { preferences.margin().set(it) })
                },
            )
        }

        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_text_align)) {
            NovelCaptionedAlignRow(
                selected = settings.textAlign,
                options = listOf(
                    Triple(
                        TextAlign.SOURCE,
                        Icons.Outlined.Public,
                        stringResource(AYMR.strings.novel_reader_text_align_source_short),
                    ),
                    Triple(
                        TextAlign.LEFT,
                        Icons.AutoMirrored.Filled.FormatAlignLeft,
                        stringResource(AYMR.strings.novel_reader_text_align_left_short),
                    ),
                    Triple(
                        TextAlign.CENTER,
                        Icons.Filled.FormatAlignCenter,
                        stringResource(AYMR.strings.novel_reader_text_align_center_short),
                    ),
                    Triple(
                        TextAlign.JUSTIFY,
                        Icons.Filled.FormatAlignJustify,
                        stringResource(AYMR.strings.novel_reader_text_align_justify_short),
                    ),
                    Triple(
                        TextAlign.RIGHT,
                        Icons.AutoMirrored.Filled.FormatAlignRight,
                        stringResource(AYMR.strings.novel_reader_text_align_right_short),
                    ),
                ),
                onSelect = { align ->
                    update(align, { o, v -> o.copy(textAlign = v) }, { preferences.textAlign().set(it) })
                },
            )
        }

        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_style)) {
            NovelStyleChipGrid(
                items = listOf(
                    NovelStyleChipItem(
                        label = stringResource(AYMR.strings.novel_reader_style_chip_indent),
                        selected = settings.forceParagraphIndent,
                        leading = "¶",
                        onClick = {
                            update(
                                !settings.forceParagraphIndent,
                                { o, v -> o.copy(forceParagraphIndent = v) },
                                { preferences.forceParagraphIndent().set(it) },
                            )
                        },
                    ),
                    NovelStyleChipItem(
                        label = stringResource(AYMR.strings.novel_reader_style_chip_bold),
                        selected = settings.forceBoldText,
                        leading = "B",
                        onClick = {
                            update(
                                !settings.forceBoldText,
                                { o, v -> o.copy(forceBoldText = v) },
                                { preferences.forceBoldText().set(it) },
                            )
                        },
                    ),
                    NovelStyleChipItem(
                        label = stringResource(AYMR.strings.novel_reader_style_chip_italic),
                        selected = settings.forceItalicText,
                        leading = "I",
                        onClick = {
                            update(
                                !settings.forceItalicText,
                                { o, v -> o.copy(forceItalicText = v) },
                                { preferences.forceItalicText().set(it) },
                            )
                        },
                    ),
                    NovelStyleChipItem(
                        label = stringResource(AYMR.strings.novel_reader_style_chip_shadow),
                        selected = settings.textShadow,
                        leading = "S",
                        onClick = {
                            update(
                                !settings.textShadow,
                                { o, v -> o.copy(textShadow = v) },
                                { preferences.textShadow().set(it) },
                            )
                        },
                    ),
                ),
            )
            if (settings.textShadow) {
                EditTextPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_text_shadow_color),
                    subtitle = stringResource(AYMR.strings.novel_reader_text_shadow_color_summary),
                    icon = null,
                    value = settings.textShadowColor.orEmpty(),
                    onConfirm = { value ->
                        if (!isValidNovelReaderColorOrBlank(value)) return@EditTextPreferenceWidget false
                        update(value.trim(), { o, v ->
                            o.copy(textShadowColor = v)
                        }, { preferences.textShadowColor().set(it) })
                        true
                    },
                    canBeBlank = true,
                )
                LnReaderSliderRow(
                    label = stringResource(AYMR.strings.novel_reader_text_shadow_blur),
                    valueText = { String.format("%.1f", it) },
                    committedValue = settings.textShadowBlur,
                    range = 0f..20f,
                    steps = 39,
                    onCommit = {
                        update(it, { o, v -> o.copy(textShadowBlur = v) }, { preferences.textShadowBlur().set(it) })
                    },
                )
                LnReaderSliderRow(
                    label = stringResource(AYMR.strings.novel_reader_text_shadow_x),
                    valueText = { String.format("%.1f", it) },
                    committedValue = settings.textShadowX,
                    range = -20f..20f,
                    steps = 79,
                    onCommit = {
                        update(it, { o, v -> o.copy(textShadowX = v) }, { preferences.textShadowX().set(it) })
                    },
                )
                LnReaderSliderRow(
                    label = stringResource(AYMR.strings.novel_reader_text_shadow_y),
                    valueText = { String.format("%.1f", it) },
                    committedValue = settings.textShadowY,
                    range = -20f..20f,
                    steps = 79,
                    onCommit = {
                        update(it, { o, v -> o.copy(textShadowY = v) }, { preferences.textShadowY().set(it) })
                    },
                )
            }
        }

        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_font_family)) {
            FontExamplesRow(
                selected = settings.fontFamily,
                fonts = readerFontCatalog,
                onSelect = { font ->
                    update(font, { o, v -> o.copy(fontFamily = v) }, { preferences.fontFamily().set(it) })
                },
                onImport = {
                    fontPicker.launch(arrayOf("font/*", "application/octet-stream", "*/*"))
                },
                onRemoveImported = { font ->
                    removeNovelReaderCustomFont(font.filePath)
                    if (settings.fontFamily == font.id) {
                        update("", { o, v -> o.copy(fontFamily = v) }, { preferences.fontFamily().set(it) })
                    }
                    fontCatalogVersion += 1
                },
            )
        }

        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_appearance)) {
            AuroraFieldLabel(stringResource(AYMR.strings.novel_reader_appearance_mode))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NovelGlassContentPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NovelChoiceCard(
                    selected = settings.appearanceMode == NovelReaderAppearanceMode.THEME,
                    onClick = {
                        update(
                            NovelReaderAppearanceMode.THEME,
                            { o, v -> o.copy(appearanceMode = v) },
                            { preferences.appearanceMode().set(it) },
                        )
                    },
                    title = stringResource(AYMR.strings.novel_reader_appearance_mode_theme),
                    icon = Icons.Outlined.Palette,
                    modifier = Modifier.weight(1f),
                )
                NovelChoiceCard(
                    selected = settings.appearanceMode == NovelReaderAppearanceMode.BACKGROUND,
                    onClick = {
                        update(
                            NovelReaderAppearanceMode.BACKGROUND,
                            { o, v -> o.copy(appearanceMode = v) },
                            { preferences.appearanceMode().set(it) },
                        )
                    },
                    title = stringResource(AYMR.strings.novel_reader_appearance_mode_background),
                    icon = Icons.Outlined.Image,
                    modifier = Modifier.weight(1f),
                )
            }

            if (appearanceControlState.themeControlsEnabled) {
                AuroraFieldLabel(stringResource(AYMR.strings.novel_reader_theme))
                NovelChipStrip(
                    options = listOf(
                        NovelReaderTheme.SYSTEM to stringResource(AYMR.strings.novel_reader_theme_system),
                        NovelReaderTheme.LIGHT to stringResource(AYMR.strings.novel_reader_theme_light),
                        NovelReaderTheme.DARK to stringResource(AYMR.strings.novel_reader_theme_dark),
                    ).map { (mode, label) -> label to (settings.theme == mode) },
                    onSelectIndex = { index ->
                        val mode = listOf(
                            NovelReaderTheme.SYSTEM,
                            NovelReaderTheme.LIGHT,
                            NovelReaderTheme.DARK,
                        )[index]
                        val selection = resolveThemeModeSelection(mode)
                        update(selection.theme, { o, v -> o.copy(theme = v) }, { preferences.theme().set(it) })
                        update(
                            selection.backgroundColor,
                            { o, v -> o.copy(backgroundColor = v) },
                            { preferences.backgroundColor().set(it) },
                        )
                        update(
                            selection.textColor,
                            { o, v -> o.copy(textColor = v) },
                            { preferences.textColor().set(it) },
                        )
                    },
                )

                AuroraFieldLabel(stringResource(AYMR.strings.novel_reader_background_texture))
                NovelChipStrip(
                    options = NovelReaderBackgroundTexture.entries.map { option ->
                        val label = when (option) {
                            NovelReaderBackgroundTexture.NONE ->
                                stringResource(AYMR.strings.novel_reader_background_texture_none)
                            NovelReaderBackgroundTexture.PAPER_GRAIN ->
                                stringResource(AYMR.strings.novel_reader_background_texture_paper_grain)
                            NovelReaderBackgroundTexture.LINEN ->
                                stringResource(AYMR.strings.novel_reader_background_texture_linen)
                            NovelReaderBackgroundTexture.PARCHMENT ->
                                stringResource(AYMR.strings.novel_reader_background_texture_parchment)
                        }
                        label to (settings.backgroundTexture == option)
                    },
                    onSelectIndex = { index ->
                        val option = NovelReaderBackgroundTexture.entries[index]
                        update(
                            option,
                            { o, v -> o.copy(backgroundTexture = v) },
                            { preferences.backgroundTexture().set(it) },
                        )
                    },
                )

                LnReaderSliderRow(
                    label = stringResource(AYMR.strings.novel_reader_native_texture_strength),
                    valueText = { "${it.roundToInt()}%" },
                    committedValue = settings.nativeTextureStrengthPercent.toFloat(),
                    range = 0f..200f,
                    steps = 199,
                    onCommit = { value ->
                        val rounded = value.roundToInt()
                        update(
                            rounded,
                            { o, v -> o.copy(nativeTextureStrengthPercent = v) },
                            { preferences.nativeTextureStrengthPercent().set(it) },
                        )
                    },
                )
                NovelGlassHint(stringResource(AYMR.strings.novel_reader_native_texture_strength_summary))

                AuroraToggleRow(
                    label = stringResource(AYMR.strings.novel_reader_oled_edge_gradient),
                    subtitle = stringResource(AYMR.strings.novel_reader_oled_edge_gradient_summary),
                    checked = settings.oledEdgeGradient,
                    onClick = {
                        update(
                            !settings.oledEdgeGradient,
                            { o, v -> o.copy(oledEdgeGradient = v) },
                            { preferences.oledEdgeGradient().set(it) },
                        )
                    },
                )

                AuroraFieldLabel(stringResource(AYMR.strings.novel_reader_theme_presets))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = NovelGlassContentPadding, vertical = 6.dp),
                ) {
                    items(colorTiles) { theme ->
                        ThemeTile(
                            theme = theme,
                            selected = selectedTheme == theme,
                            onClick = {
                                update(
                                    theme.backgroundColor,
                                    { o, v -> o.copy(backgroundColor = v) },
                                    { preferences.backgroundColor().set(it) },
                                )
                                update(
                                    theme.textColor,
                                    { o, v -> o.copy(textColor = v) },
                                    { preferences.textColor().set(it) },
                                )
                            },
                        )
                    }
                }

                EditTextPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_background_color),
                    subtitle = "%s",
                    icon = null,
                    value = settings.backgroundColor.orEmpty(),
                    onConfirm = { value ->
                        if (!isValidNovelReaderColorOrBlank(value)) return@EditTextPreferenceWidget false
                        update(value, { o, v -> o.copy(backgroundColor = v) }, { preferences.backgroundColor().set(it) })
                        true
                    },
                    canBeBlank = true,
                )

                EditTextPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_text_color),
                    subtitle = "%s",
                    icon = null,
                    value = settings.textColor.orEmpty(),
                    onConfirm = { value ->
                        if (!isValidNovelReaderColorOrBlank(value)) return@EditTextPreferenceWidget false
                        update(value, { o, v -> o.copy(textColor = v) }, { preferences.textColor().set(it) })
                        true
                    },
                    canBeBlank = true,
                )

                if (selectedTheme != null && !isPreset && !isCustom) {
                    val colors = AuroraTheme.colors
                    val shape = RoundedCornerShape(14.dp)
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_save_custom_theme),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NovelGlassContentPadding, vertical = 4.dp)
                            .clip(shape)
                            .background(colors.accent)
                            .clickable {
                                val newThemes =
                                    listOf(selectedTheme) + settings.customThemes.filterNot { it == selectedTheme }
                                update(newThemes, { o, v ->
                                    o.copy(customThemes = v)
                                }, { preferences.customThemes().set(it) })
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
                if (selectedTheme != null && isCustom) {
                    val colors = AuroraTheme.colors
                    val shape = RoundedCornerShape(14.dp)
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_delete_custom_theme),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NovelGlassContentPadding, vertical = 4.dp)
                            .clip(shape)
                            .background(
                                if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            )
                            .border(1.dp, auroraRimColor(), shape)
                            .clickable {
                                val newThemes = settings.customThemes.filterNot { it == selectedTheme }
                                update(newThemes, { o, v ->
                                    o.copy(customThemes = v)
                                }, { preferences.customThemes().set(it) })
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            } else {
                NovelGlassHint(stringResource(AYMR.strings.novel_reader_theme_controls_disabled_summary))
            }
        }

        if (appearanceControlState.backgroundControlsEnabled) {
            AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_backgrounds)) {
            Text(
                text = stringResource(AYMR.strings.novel_reader_background_presets),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                val selectedCustomId = settings.customBackgroundId.ifBlank { settings.customBackgroundPath }
                items(backgroundCards, key = { it.id }) { card ->
                    val selected = if (card.isBuiltIn) {
                        settings.backgroundSource == NovelReaderBackgroundSource.PRESET &&
                            settings.backgroundPresetId == card.id
                    } else {
                        settings.backgroundSource == NovelReaderBackgroundSource.CUSTOM &&
                            selectedCustomId == card.id
                    }
                    if (card.isBuiltIn) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .size(width = 178.dp, height = 186.dp)
                                .clickable {
                                    update(
                                        NovelReaderAppearanceMode.BACKGROUND,
                                        { o, v -> o.copy(appearanceMode = v) },
                                        { preferences.appearanceMode().set(it) },
                                    )
                                    update(
                                        NovelReaderBackgroundSource.PRESET,
                                        { o, v -> o.copy(backgroundSource = v) },
                                        { preferences.backgroundSource().set(it) },
                                    )
                                    update(
                                        card.id,
                                        { o, v -> o.copy(backgroundPresetId = v) },
                                        { preferences.backgroundPresetId().set(it) },
                                    )
                                },
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                val preset = card.preset ?: return@Column
                                Image(
                                    painter = painterResource(id = preset.imageResId),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .size(height = 90.dp, width = 162.dp),
                                )
                                Text(
                                    text = backgroundPresetTitle(card.id),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = backgroundPresetDescription(card.id),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        val customItem = card.customItem ?: return@items
                        NovelReaderCustomBackgroundCard(
                            customItem = customItem,
                            selected = selected,
                            onSelect = {
                                update(
                                    NovelReaderAppearanceMode.BACKGROUND,
                                    { o, v -> o.copy(appearanceMode = v) },
                                    { preferences.appearanceMode().set(it) },
                                )
                                update(
                                    NovelReaderBackgroundSource.CUSTOM,
                                    { o, v -> o.copy(backgroundSource = v) },
                                    { preferences.backgroundSource().set(it) },
                                )
                                update(
                                    customItem.id,
                                    { o, v -> o.copy(customBackgroundId = v) },
                                    { preferences.customBackgroundId().set(it) },
                                )
                                update(
                                    customItem.absolutePath,
                                    { o, v -> o.copy(customBackgroundPath = v) },
                                    { preferences.customBackgroundPath().set(it) },
                                )
                            },
                            onRename = {
                                renameTarget = customItem
                                renameInput = customItem.displayName
                            },
                            onReplace = {
                                pendingReplaceCustomId = customItem.id
                                replaceBackgroundPicker.launch("image/*")
                            },
                            onDelete = {
                                val removed = removeNovelReaderCustomBackgroundItem(
                                    context = context,
                                    id = customItem.id,
                                ).getOrDefault(false)
                                if (!removed) {
                                    Toast.makeText(
                                        context,
                                        importFailedMessage,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@NovelReaderCustomBackgroundCard
                                }
                                val selectedId = settings.customBackgroundId
                                    .ifBlank { settings.customBackgroundPath }
                                if (selectedId == customItem.id) {
                                    val remaining = readNovelReaderCustomBackgroundItems(context)
                                    val deletion = resolveCustomBackgroundDeletion(
                                        selectedId = selectedId,
                                        deletedId = customItem.id,
                                        remainingCustomIds = remaining.map { it.id },
                                        fallbackPresetId = settings.backgroundPresetId
                                            .ifBlank { NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID },
                                    )
                                    update(
                                        deletion.nextCustomId,
                                        { o, v -> o.copy(customBackgroundId = v) },
                                        { preferences.customBackgroundId().set(it) },
                                    )
                                    val nextPath = remaining
                                        .firstOrNull { it.id == deletion.nextCustomId }
                                        ?.absolutePath
                                        .orEmpty()
                                    update(
                                        nextPath,
                                        { o, v -> o.copy(customBackgroundPath = v) },
                                        { preferences.customBackgroundPath().set(it) },
                                    )
                                    if (deletion.keepCustomSource) {
                                        update(
                                            NovelReaderBackgroundSource.CUSTOM,
                                            { o, v -> o.copy(backgroundSource = v) },
                                            { preferences.backgroundSource().set(it) },
                                        )
                                    } else {
                                        update(
                                            deletion.fallbackPresetId,
                                            { o, v -> o.copy(backgroundPresetId = v) },
                                            { preferences.backgroundPresetId().set(it) },
                                        )
                                        update(
                                            NovelReaderBackgroundSource.PRESET,
                                            { o, v -> o.copy(backgroundSource = v) },
                                            { preferences.backgroundSource().set(it) },
                                        )
                                    }
                                }
                                backgroundCatalogVersion += 1
                            },
                        )
                    }
                }
            }
            val uploadColors = AuroraTheme.colors
            val uploadShape = RoundedCornerShape(14.dp)
            Text(
                text = stringResource(AYMR.strings.novel_reader_background_upload),
                style = MaterialTheme.typography.labelLarge,
                color = uploadColors.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NovelGlassContentPadding, vertical = 6.dp)
                    .clip(uploadShape)
                    .background(uploadColors.accent)
                    .clickable { backgroundPicker.launch("image/*") }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            NovelGlassHint(stringResource(AYMR.strings.novel_reader_background_upload_hint))
            } // backgrounds glass section
        } else {
            Text(
                text = stringResource(AYMR.strings.novel_reader_background_controls_disabled_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }

        renameTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(text = stringResource(AYMR.strings.editor_action_rename)) },
                text = {
                    TextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val renamed = renameNovelReaderCustomBackgroundItem(
                                context = context,
                                id = target.id,
                                displayName = renameInput,
                            ).getOrNull()
                            if (renamed == null) {
                                Toast.makeText(context, importFailedMessage, Toast.LENGTH_SHORT).show()
                            } else {
                                backgroundCatalogVersion += 1
                                renameTarget = null
                            }
                        },
                    ) {
                        Text(text = stringResource(AYMR.strings.editor_action_rename))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text(text = stringResource(AYMR.strings.novel_reader_background_action_cancel))
                    }
                },
            )
        }

        AuroraGlassSection {
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_page_edge_shadow),
                subtitle = stringResource(AYMR.strings.novel_reader_page_edge_shadow_summary),
                checked = settings.pageEdgeShadow,
                onClick = {
                    update(
                        !settings.pageEdgeShadow,
                        { o, v -> o.copy(pageEdgeShadow = v) },
                        { preferences.pageEdgeShadow().set(it) },
                    )
                },
            )
            if (settings.pageEdgeShadow) {
                LnReaderSliderRow(
                    label = stringResource(AYMR.strings.novel_reader_page_edge_shadow_alpha),
                    valueText = { "${(it * 100).roundToInt()}%" },
                    committedValue = settings.pageEdgeShadowAlpha,
                    range = 0.05f..1f,
                    steps = 18,
                    onCommit = {
                        update(it, { o, v ->
                            o.copy(pageEdgeShadowAlpha = v)
                        }, { preferences.pageEdgeShadowAlpha().set(it) })
                    },
                )
            }
        }
    }
}


@Composable
private fun FontExamplesRow(
    selected: String,
    fonts: List<NovelReaderFontOption>,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onRemoveImported: (NovelReaderFontOption) -> Unit,
) {
    val context = LocalContext.current
    val builtInFonts = remember(fonts) { fonts.filter { it.source == NovelReaderFontSource.BUILT_IN } }
    val localFonts = remember(fonts) { fonts.filter { it.source == NovelReaderFontSource.LOCAL_PRIVATE } }
    val importedFonts = remember(fonts) { fonts.filter { it.source == NovelReaderFontSource.USER_IMPORTED } }
    var localExpanded by rememberSaveable { mutableStateOf(false) }
    var importedExpanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NovelFontCardRow(
            selectedId = selected,
            fonts = builtInFonts,
            resolveFamily = { option ->
                resolveNovelReaderComposeFontFamily(
                    font = option,
                    typeface = loadNovelReaderTypeface(context, option),
                )
            },
            onSelect = onSelect,
        )
        ReaderFontSection(
            title = stringResource(AYMR.strings.novel_reader_font_section_local),
            count = localFonts.size,
            expanded = localExpanded,
            selected = selected,
            selectedInSection = localFonts.any { it.id == selected },
            onToggle = { localExpanded = !localExpanded },
            emptyLabel = stringResource(AYMR.strings.novel_reader_font_section_empty_local),
            selectedLabel = stringResource(AYMR.strings.novel_reader_font_section_selected),
            fonts = localFonts,
            onSelect = onSelect,
            onRemoveImported = onRemoveImported,
            context = context,
        )
        ReaderFontSection(
            title = stringResource(AYMR.strings.novel_reader_font_section_imported),
            count = importedFonts.size,
            expanded = importedExpanded,
            selected = selected,
            selectedInSection = importedFonts.any { it.id == selected },
            onToggle = { importedExpanded = !importedExpanded },
            emptyLabel = stringResource(AYMR.strings.novel_reader_font_section_empty_imported),
            selectedLabel = stringResource(AYMR.strings.novel_reader_font_section_selected),
            actionLabel = stringResource(AYMR.strings.novel_reader_font_add),
            onAction = onImport,
            fonts = importedFonts,
            onSelect = onSelect,
            onRemoveImported = onRemoveImported,
            context = context,
        )
    }
}

@Composable
private fun ReaderFontSection(
    title: String,
    count: Int,
    expanded: Boolean,
    selected: String,
    selectedInSection: Boolean,
    onToggle: () -> Unit,
    emptyLabel: String,
    selectedLabel: String,
    fonts: List<NovelReaderFontOption>,
    onSelect: (String) -> Unit,
    onRemoveImported: (NovelReaderFontOption) -> Unit,
    context: android.content.Context,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NovelFontFolderRow(
            title = title,
            count = count,
            expanded = expanded,
            selectedInSection = selectedInSection,
            selectedLabel = selectedLabel,
            onToggle = onToggle,
            actionLabel = actionLabel,
            onAction = onAction,
        )
        if (expanded) {
            if (fonts.isEmpty()) {
                NovelGlassHint(emptyLabel)
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = NovelGlassContentPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fonts.forEach { option ->
                        key(option.id) {
                            val typeface = remember(option.id) { loadNovelReaderTypeface(context, option) }
                            val fontFamily = remember(option.id, typeface) {
                                resolveNovelReaderComposeFontFamily(option, typeface)
                            }
                            val isSelected = option.id == selected
                            val shape = RoundedCornerShape(14.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(
                                        when {
                                            isSelected -> colors.accent.copy(alpha = 0.18f)
                                            colors.isDark -> Color.White.copy(alpha = 0.06f)
                                            else -> Color.Black.copy(alpha = 0.04f)
                                        },
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) colors.accent else auroraRimColor(),
                                        shape = shape,
                                    )
                                    .clickable { onSelect(option.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = fontFamily),
                                    color = if (isSelected) colors.accent else colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (option.source == NovelReaderFontSource.USER_IMPORTED) {
                                    IconButton(onClick = { onRemoveImported(option) }) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = stringResource(
                                                AYMR.strings.novel_reader_font_remove,
                                            ),
                                            tint = colors.textSecondary,
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

@Composable
private fun ThemeTile(
    theme: NovelReaderColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val background = parseNovelReaderColor(theme.backgroundColor) ?: colors.surface
    val foreground = parseNovelReaderColor(theme.textColor) ?: colors.textPrimary
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.accent else auroraRimColor(),
                shape = CircleShape,
            )
            .padding(3.dp)
            .background(color = background, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "A",
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun backgroundPresetTitle(presetId: String): String {
    return when (presetId) {
        NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_linen_paper_title)
        NOVEL_READER_BACKGROUND_PRESET_AGED_PAGE_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_aged_page_title)
        NOVEL_READER_BACKGROUND_PRESET_AGED_PARCHMENT_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_aged_parchment_title)
        NOVEL_READER_BACKGROUND_PRESET_CRUMPLED_SHEET_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_crumpled_sheet_title)
        NOVEL_READER_BACKGROUND_PRESET_NIGHT_VELVET_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_night_velvet_title)
        NOVEL_READER_BACKGROUND_PRESET_DARK_WOOD_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_dark_wood_title)
        else -> presetId
    }
}

@Composable
private fun backgroundPresetDescription(presetId: String): String {
    return when (presetId) {
        NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_linen_paper_description)
        NOVEL_READER_BACKGROUND_PRESET_AGED_PAGE_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_aged_page_description)
        NOVEL_READER_BACKGROUND_PRESET_AGED_PARCHMENT_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_aged_parchment_description)
        NOVEL_READER_BACKGROUND_PRESET_CRUMPLED_SHEET_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_crumpled_sheet_description)
        NOVEL_READER_BACKGROUND_PRESET_NIGHT_VELVET_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_night_velvet_description)
        NOVEL_READER_BACKGROUND_PRESET_DARK_WOOD_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_dark_wood_description)
        else -> ""
    }
}

@Composable
private fun novelReaderTypographyPresetEntries(): ImmutableMap<NovelReaderTypographyPreset, String> {
    return persistentMapOf(
        NovelReaderTypographyPreset.CUSTOM to stringResource(AYMR.strings.novel_reader_typography_scale_preset_custom),
        NovelReaderTypographyPreset.SUPERGOLDEN to
            stringResource(AYMR.strings.novel_reader_typography_scale_preset_supergolden),
        NovelReaderTypographyPreset.GOLDEN to stringResource(AYMR.strings.novel_reader_typography_scale_preset_golden),
    )
}
