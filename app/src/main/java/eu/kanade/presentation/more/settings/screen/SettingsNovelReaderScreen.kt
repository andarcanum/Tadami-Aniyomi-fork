package eu.kanade.presentation.more.settings.screen

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.presentation.reader.novel.NOVEL_READER_BACKGROUND_PRESET_AGED_PAGE_ID
import eu.kanade.presentation.reader.novel.NOVEL_READER_BACKGROUND_PRESET_CRUMPLED_SHEET_ID
import eu.kanade.presentation.reader.novel.NOVEL_READER_BACKGROUND_PRESET_DARK_WOOD_ID
import eu.kanade.presentation.reader.novel.NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID
import eu.kanade.presentation.reader.novel.NOVEL_READER_BACKGROUND_PRESET_NIGHT_VELVET_ID
import eu.kanade.presentation.reader.novel.NovelReaderBackgroundCard
import eu.kanade.presentation.reader.novel.NovelReaderFontOption
import eu.kanade.presentation.reader.novel.NovelReaderFontSource
import eu.kanade.presentation.reader.novel.autoScrollSpeedToInterval
import eu.kanade.presentation.reader.novel.buildNovelReaderBackgroundCardsFromCustomItems
import eu.kanade.presentation.reader.novel.buildNovelReaderFontCatalog
import eu.kanade.presentation.reader.novel.ensureLegacyNovelReaderBackgroundItem
import eu.kanade.presentation.reader.novel.importNovelReaderCustomBackgroundItem
import eu.kanade.presentation.reader.novel.importNovelReaderCustomFont
import eu.kanade.presentation.reader.novel.intervalToAutoScrollSpeed
import eu.kanade.presentation.reader.novel.novelPageTransitionStyleEntries
import eu.kanade.presentation.reader.novel.novelPageTransitionStyleSubtitle
import eu.kanade.presentation.reader.novel.novelReaderBackgroundPresets
import eu.kanade.presentation.reader.novel.novelReaderPresetThemes
import eu.kanade.presentation.reader.novel.areChapterSwipeControlsEnabled
import eu.kanade.presentation.reader.novel.readNovelReaderCustomBackgroundItems
import eu.kanade.presentation.reader.novel.removeNovelReaderCustomBackgroundItem
import eu.kanade.presentation.reader.novel.removeNovelReaderCustomFont
import eu.kanade.presentation.reader.novel.renameNovelReaderCustomBackgroundItem
import eu.kanade.presentation.reader.novel.replaceNovelReaderCustomBackgroundItem
import eu.kanade.presentation.reader.novel.resolveCustomBackgroundDeletion
import eu.kanade.presentation.reader.novel.resolveNovelReaderSettingsSurfaceStrategy
import eu.kanade.presentation.reader.novel.resolveRendererSettingsAvailability
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderChapterDiskCache
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderChapterDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import android.graphics.Color as AndroidColor

object SettingsNovelReaderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_category_novel_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val prefs = remember { Injekt.get<NovelReaderPreferences>() }
        return listOf(
            getDisplayGroup(prefs),
            getThemeGroup(prefs),
            getNavigationGroup(prefs),
            getAccessibilityGroup(prefs),
            getAdvancedGroup(prefs),
        )
    }

    @Composable
    private fun getDisplayGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val fontSizePref = prefs.fontSize()
        val fontSize by fontSizePref.collectAsState()
        val lineHeightPref = prefs.lineHeight()
        val lineHeight by lineHeightPref.collectAsState()
        val marginPref = prefs.margin()
        val margin by marginPref.collectAsState()
        val paragraphSpacingPref = prefs.paragraphSpacing()
        val paragraphSpacing by paragraphSpacingPref.collectAsState()
        val fontFamilyPref = prefs.fontFamily()
        val selectedFontFamily by fontFamilyPref.collectAsState()
        val forceBoldTextPref = prefs.forceBoldText()
        val forceItalicTextPref = prefs.forceItalicText()
        val textShadowPref = prefs.textShadow()
        val textShadowEnabled by textShadowPref.collectAsState()
        val textShadowColorPref = prefs.textShadowColor()
        val textShadowColor by textShadowColorPref.collectAsState()
        val textShadowBlurPref = prefs.textShadowBlur()
        val textShadowBlur by textShadowBlurPref.collectAsState()
        val textShadowXPref = prefs.textShadowX()
        val textShadowX by textShadowXPref.collectAsState()
        val textShadowYPref = prefs.textShadowY()
        val textShadowY by textShadowYPref.collectAsState()
        val geminiEnabled by prefs.geminiEnabled().collectAsState()
        val fontImportFailedMessage = stringResource(AYMR.strings.novel_reader_font_import_failed)
        val surfaceStrategy = remember { resolveNovelReaderSettingsSurfaceStrategy() }
        var fontCatalogVersion by remember { mutableIntStateOf(0) }
        val readerFontCatalog = remember(fontCatalogVersion, selectedFontFamily) {
            buildNovelReaderFontCatalog(context)
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
            fontFamilyPref.set(importedFont.id)
            fontCatalogVersion += 1
        }
        val settingsSurfaceSummary = if (surfaceStrategy.globalOnlyFamilies.isNotEmpty()) {
            stringResource(AYMR.strings.novel_reader_global_settings_quick_dialog_summary)
        } else {
            stringResource(AYMR.strings.novel_reader_global_settings_quick_dialog_summary)
        }

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.geminiEnabled(),
                    title = stringResource(AYMR.strings.novel_reader_gemini_enabled),
                    subtitle = stringResource(AYMR.strings.novel_reader_gemini_enabled_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.geminiPromptMode(),
                    entries = persistentMapOf(
                        GeminiPromptMode.CLASSIC to stringResource(
                            AYMR.strings.novel_reader_gemini_prompt_mode_classic,
                        ),
                        GeminiPromptMode.ADULT_18 to stringResource(AYMR.strings.novel_reader_gemini_prompt_mode_adult),
                    ),
                    title = stringResource(AYMR.strings.novel_reader_gemini_prompt_mode),
                    enabled = geminiEnabled,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = fontSize,
                    title = stringResource(AYMR.strings.novel_reader_font_size),
                    subtitle = "${fontSize}sp",
                    valueRange = 12..28,
                    onValueChanged = {
                        fontSizePref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (lineHeight * 10).toInt(),
                    title = stringResource(AYMR.strings.novel_reader_line_height),
                    subtitle = String.format("%.1f", lineHeight),
                    valueRange = 12..20,
                    onValueChanged = {
                        lineHeightPref.set(it / 10f)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = margin,
                    title = stringResource(AYMR.strings.novel_reader_margins),
                    subtitle = "${margin}dp",
                    valueRange = 0..50,
                    onValueChanged = {
                        marginPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.textAlign(),
                    entries = TextAlign.entries
                        .associate { it to getTextAlignString(it) }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.novel_reader_text_align),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = paragraphSpacing,
                    title = stringResource(AYMR.strings.novel_reader_paragraph_spacing),
                    subtitle = "${paragraphSpacing}dp",
                    valueRange = 0..32,
                    onValueChanged = {
                        paragraphSpacingPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.forceParagraphIndent(),
                    title = stringResource(AYMR.strings.novel_reader_force_paragraph_indent),
                    subtitle = stringResource(AYMR.strings.novel_reader_force_paragraph_indent_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = forceBoldTextPref,
                    title = stringResource(AYMR.strings.novel_reader_force_bold_text),
                    subtitle = stringResource(AYMR.strings.novel_reader_force_bold_text_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = forceItalicTextPref,
                    title = stringResource(AYMR.strings.novel_reader_force_italic_text),
                    subtitle = stringResource(AYMR.strings.novel_reader_force_italic_text_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = textShadowPref,
                    title = stringResource(AYMR.strings.novel_reader_text_shadow),
                    subtitle = stringResource(AYMR.strings.novel_reader_text_shadow_summary),
                ),
                Preference.PreferenceItem.EditTextInfoPreference(
                    preference = textShadowColorPref,
                    title = stringResource(AYMR.strings.novel_reader_text_shadow_color),
                    subtitle = "%s",
                    dialogSubtitle = stringResource(AYMR.strings.novel_reader_text_shadow_color_summary),
                    validate = ::isValidColorOrBlank,
                    enabled = textShadowEnabled,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (textShadowBlur * 2f).toInt(),
                    title = stringResource(AYMR.strings.novel_reader_text_shadow_blur),
                    subtitle = String.format("%.1f", textShadowBlur),
                    valueRange = 0..40,
                    enabled = textShadowEnabled,
                    onValueChanged = {
                        textShadowBlurPref.set((it / 2f).coerceIn(0f, 20f))
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (textShadowX * 2f).toInt(),
                    title = stringResource(AYMR.strings.novel_reader_text_shadow_x),
                    subtitle = String.format("%.1f", textShadowX),
                    valueRange = -40..40,
                    enabled = textShadowEnabled,
                    onValueChanged = {
                        textShadowXPref.set((it / 2f).coerceIn(-20f, 20f))
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (textShadowY * 2f).toInt(),
                    title = stringResource(AYMR.strings.novel_reader_text_shadow_y),
                    subtitle = String.format("%.1f", textShadowY),
                    valueRange = -40..40,
                    enabled = textShadowEnabled,
                    onValueChanged = {
                        textShadowYPref.set((it / 2f).coerceIn(-20f, 20f))
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.preserveSourceTextAlignInNative(),
                    title = stringResource(AYMR.strings.novel_reader_preserve_source_text_align_native),
                    subtitle = stringResource(AYMR.strings.novel_reader_preserve_source_text_align_native_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_settings_surface_strategy),
                    subtitle = settingsSurfaceSummary,
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.novel_reader_font_family),
                ) {
                    BasePreferenceWidget(
                        title = stringResource(AYMR.strings.novel_reader_font_family),
                        subcomponent = {
                            NovelReaderFontPreviewRow(
                                selectedFontId = selectedFontFamily,
                                fonts = readerFontCatalog,
                                onSelect = { fontFamilyPref.set(it) },
                                onImport = {
                                    fontPicker.launch(arrayOf("font/*", "application/octet-stream", "*/*"))
                                },
                                onRemoveImported = { option ->
                                    removeNovelReaderCustomFont(option.filePath)
                                    if (selectedFontFamily == option.id) {
                                        fontFamilyPref.set("")
                                    }
                                    fontCatalogVersion += 1
                                },
                            )
                        },
                    )
                },
            ),
        )
    }

    @Composable
    private fun getThemeGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val appearanceModePref = prefs.appearanceMode()
        val appearanceMode by appearanceModePref.collectAsState()
        val bgPref = prefs.backgroundColor()
        val bg by bgPref.collectAsState()
        val textPref = prefs.textColor()
        val text by textPref.collectAsState()
        val backgroundSourcePref = prefs.backgroundSource()
        val backgroundSource by backgroundSourcePref.collectAsState()
        val backgroundPresetIdPref = prefs.backgroundPresetId()
        val backgroundPresetId by backgroundPresetIdPref.collectAsState()
        val customBackgroundPathPref = prefs.customBackgroundPath()
        val customBackgroundPath by customBackgroundPathPref.collectAsState()
        val customBackgroundIdPref = prefs.customBackgroundId()
        val customBackgroundId by customBackgroundIdPref.collectAsState()
        val nativeTextureStrengthPref = prefs.nativeTextureStrengthPercent()
        val nativeTextureStrength by nativeTextureStrengthPref.collectAsState()
        val pageEdgeShadowPref = prefs.pageEdgeShadow()
        val pageEdgeShadow by pageEdgeShadowPref.collectAsState()
        val pageEdgeShadowAlphaPref = prefs.pageEdgeShadowAlpha()
        val pageEdgeShadowAlpha by pageEdgeShadowAlphaPref.collectAsState()
        val customThemesPref = prefs.customThemes()
        val customThemes by customThemesPref.collectAsState()
        val importFailedMessage = stringResource(AYMR.strings.novel_reader_background_custom_import_failed)
        var backgroundCatalogVersion by remember { mutableIntStateOf(0) }
        var renameTargetId by remember { mutableStateOf<String?>(null) }
        var renameInput by remember { mutableStateOf("") }
        var pendingReplaceCustomId by remember { mutableStateOf<String?>(null) }

        val customBackgroundItems = remember(
            customBackgroundId,
            customBackgroundPath,
            backgroundCatalogVersion,
        ) {
            if (
                customBackgroundPath.isNotBlank() &&
                customBackgroundId.isNotBlank() &&
                customBackgroundId == customBackgroundPath
            ) {
                ensureLegacyNovelReaderBackgroundItem(
                    context = context,
                    legacyPath = customBackgroundPath,
                    preferredId = customBackgroundId,
                )
            }
            readNovelReaderCustomBackgroundItems(context)
        }
        val backgroundCards = remember(customBackgroundItems) {
            buildNovelReaderBackgroundCardsFromCustomItems(customBackgroundItems)
        }
        val selectedCustomBackgroundId = customBackgroundId.ifBlank { customBackgroundPath }
        val renameTarget = remember(renameTargetId, customBackgroundItems) {
            customBackgroundItems.firstOrNull { it.id == renameTargetId }
        }

        val backgroundPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val imported = importNovelReaderCustomBackgroundItem(context, uri).getOrNull()
            if (imported == null) {
                Toast.makeText(context, importFailedMessage, Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            appearanceModePref.set(eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode.BACKGROUND)
            backgroundSourcePref.set(NovelReaderBackgroundSource.CUSTOM)
            customBackgroundIdPref.set(imported.id)
            customBackgroundPathPref.set(imported.absolutePath)
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
            if (selectedCustomBackgroundId == targetId) {
                customBackgroundPathPref.set(replaced.absolutePath)
            }
            backgroundCatalogVersion += 1
        }

        val currentTheme = currentTheme(bg, text)
        val isPreset = currentTheme != null && novelReaderPresetThemes.contains(currentTheme)
        val isCustom = currentTheme != null && customThemes.contains(currentTheme)

        val items = mutableListOf<Preference.PreferenceItem<out Any>>(
            Preference.PreferenceItem.ListPreference(
                preference = appearanceModePref,
                entries = persistentMapOf(
                    eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode.THEME to
                        stringResource(AYMR.strings.novel_reader_appearance_mode_theme),
                    eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode.BACKGROUND to
                        stringResource(AYMR.strings.novel_reader_appearance_mode_background),
                ),
                title = stringResource(AYMR.strings.novel_reader_appearance_mode),
            ),
        )

        if (appearanceMode == eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode.THEME) {
            items += listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.theme(),
                    entries = persistentMapOf(
                        NovelReaderTheme.SYSTEM to stringResource(AYMR.strings.novel_reader_theme_system),
                        NovelReaderTheme.LIGHT to stringResource(AYMR.strings.novel_reader_theme_light),
                        NovelReaderTheme.DARK to stringResource(AYMR.strings.novel_reader_theme_dark),
                    ),
                    title = stringResource(AYMR.strings.novel_reader_theme),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.backgroundTexture(),
                    entries = persistentMapOf(
                        NovelReaderBackgroundTexture.NONE to
                            stringResource(AYMR.strings.novel_reader_background_texture_none),
                        NovelReaderBackgroundTexture.PAPER_GRAIN to
                            stringResource(AYMR.strings.novel_reader_background_texture_paper_grain),
                        NovelReaderBackgroundTexture.LINEN to
                            stringResource(AYMR.strings.novel_reader_background_texture_linen),
                        NovelReaderBackgroundTexture.PARCHMENT to
                            stringResource(AYMR.strings.novel_reader_background_texture_parchment),
                    ),
                    title = stringResource(AYMR.strings.novel_reader_background_texture),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.oledEdgeGradient(),
                    title = stringResource(AYMR.strings.novel_reader_oled_edge_gradient),
                    subtitle = stringResource(AYMR.strings.novel_reader_oled_edge_gradient_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = nativeTextureStrength,
                    title = stringResource(AYMR.strings.novel_reader_native_texture_strength),
                    subtitle = "$nativeTextureStrength%",
                    valueRange = 0..200,
                    onValueChanged = {
                        nativeTextureStrengthPref.set(it.coerceIn(0, 200))
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_native_texture_strength_summary),
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.novel_reader_theme_presets),
                ) {
                    BasePreferenceWidget(
                        title = stringResource(AYMR.strings.novel_reader_theme_presets),
                        subcomponent = {
                            NovelReaderThemePresetRow(
                                selectedTheme = currentTheme,
                                onSelect = { preset ->
                                    bgPref.set(preset.backgroundColor)
                                    textPref.set(preset.textColor)
                                },
                            )
                        },
                    )
                },
                Preference.PreferenceItem.EditTextInfoPreference(
                    preference = bgPref,
                    title = stringResource(AYMR.strings.novel_reader_background_color),
                    subtitle = "%s",
                    dialogSubtitle = stringResource(AYMR.strings.novel_reader_color_input_hint),
                    validate = ::isValidColorOrBlank,
                ),
                Preference.PreferenceItem.EditTextInfoPreference(
                    preference = textPref,
                    title = stringResource(AYMR.strings.novel_reader_text_color),
                    subtitle = "%s",
                    dialogSubtitle = stringResource(AYMR.strings.novel_reader_color_input_hint),
                    validate = ::isValidColorOrBlank,
                ),
            )

            if (currentTheme != null && !isPreset && !isCustom) {
                items += Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_save_custom_theme),
                    subtitle = "${currentTheme.backgroundColor} / ${currentTheme.textColor}",
                    onClick = {
                        customThemesPref.set(listOf(currentTheme) + customThemes.filterNot { it == currentTheme })
                    },
                )
            }

            if (currentTheme != null && isCustom) {
                items += Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_delete_custom_theme),
                    subtitle = "${currentTheme.backgroundColor} / ${currentTheme.textColor}",
                    onClick = {
                        customThemesPref.set(customThemes.filterNot { it == currentTheme })
                    },
                )
            }
        } else {
            items += Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.novel_reader_theme),
                subtitle = stringResource(AYMR.strings.novel_reader_theme_controls_disabled_summary),
            )
            items += Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.novel_reader_background_presets),
            ) {
                BasePreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_background_presets),
                    subcomponent = {
                        NovelReaderBackgroundCatalogRow(
                            cards = backgroundCards,
                            selectedSource = backgroundSource,
                            selectedPresetId = backgroundPresetId,
                            selectedCustomId = selectedCustomBackgroundId,
                            onSelectPreset = { presetId ->
                                appearanceModePref.set(
                                    eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode.BACKGROUND,
                                )
                                backgroundSourcePref.set(NovelReaderBackgroundSource.PRESET)
                                backgroundPresetIdPref.set(presetId)
                            },
                            onSelectCustom = { customId, customPath ->
                                appearanceModePref.set(
                                    eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode.BACKGROUND,
                                )
                                backgroundSourcePref.set(NovelReaderBackgroundSource.CUSTOM)
                                customBackgroundIdPref.set(customId)
                                customBackgroundPathPref.set(customPath)
                            },
                            onRenameCustom = { customId, currentName ->
                                renameTargetId = customId
                                renameInput = currentName
                            },
                            onReplaceCustom = { customId ->
                                pendingReplaceCustomId = customId
                                replaceBackgroundPicker.launch("image/*")
                            },
                            onDeleteCustom = { customId ->
                                val removed = removeNovelReaderCustomBackgroundItem(context, customId)
                                    .getOrDefault(false)
                                if (!removed) {
                                    Toast.makeText(context, importFailedMessage, Toast.LENGTH_SHORT).show()
                                    return@NovelReaderBackgroundCatalogRow
                                }
                                if (selectedCustomBackgroundId == customId) {
                                    val remaining = readNovelReaderCustomBackgroundItems(context)
                                    val deletion = resolveCustomBackgroundDeletion(
                                        selectedId = selectedCustomBackgroundId,
                                        deletedId = customId,
                                        remainingCustomIds = remaining.map { it.id },
                                        fallbackPresetId = backgroundPresetId.ifBlank {
                                            NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID
                                        },
                                    )
                                    customBackgroundIdPref.set(deletion.nextCustomId)
                                    customBackgroundPathPref.set(
                                        remaining.firstOrNull { it.id == deletion.nextCustomId }
                                            ?.absolutePath
                                            .orEmpty(),
                                    )
                                    if (deletion.keepCustomSource) {
                                        backgroundSourcePref.set(NovelReaderBackgroundSource.CUSTOM)
                                    } else {
                                        backgroundPresetIdPref.set(deletion.fallbackPresetId)
                                        backgroundSourcePref.set(NovelReaderBackgroundSource.PRESET)
                                    }
                                }
                                backgroundCatalogVersion += 1
                            },
                            onUpload = {
                                backgroundPicker.launch("image/*")
                            },
                        )
                    },
                )
            }
            items += Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.novel_reader_background_texture),
                subtitle = stringResource(AYMR.strings.novel_reader_background_controls_disabled_summary),
            )
        }

        items += Preference.PreferenceItem.SwitchPreference(
            preference = pageEdgeShadowPref,
            title = stringResource(AYMR.strings.novel_reader_page_edge_shadow),
            subtitle = stringResource(AYMR.strings.novel_reader_page_edge_shadow_summary),
        )
        items += Preference.PreferenceItem.SliderPreference(
            value = (pageEdgeShadowAlpha * 100f).toInt(),
            title = stringResource(AYMR.strings.novel_reader_page_edge_shadow_alpha),
            subtitle = "${(pageEdgeShadowAlpha * 100f).toInt()}%",
            valueRange = 5..100,
            enabled = pageEdgeShadow,
            onValueChanged = {
                pageEdgeShadowAlphaPref.set((it / 100f).coerceIn(0.05f, 1f))
                true
            },
        )

        renameTarget?.let { target ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { renameTargetId = null },
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
                                renameTargetId = null
                            }
                        },
                    ) {
                        Text(text = stringResource(AYMR.strings.editor_action_rename))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTargetId = null }) {
                        Text(text = stringResource(AYMR.strings.novel_reader_background_action_cancel))
                    }
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_theme_settings),
            preferenceItems = items.toList().toImmutableList(),
        )
    }

    @Composable
    private fun getNavigationGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val swipeGesturesPref = prefs.swipeGestures()
        val swipeGestures by swipeGesturesPref.collectAsState()
        val pageReaderPref = prefs.pageReader()
        val pageReader by pageReaderPref.collectAsState()
        val pageTransitionStylePref = prefs.pageTransitionStyle()
        val bionicReadingPref = prefs.bionicReading()
        val bionicReading by bionicReadingPref.collectAsState()
        val pageTransitionEntries = novelPageTransitionStyleEntries()
        val rendererAvailability = remember(pageReader, bionicReading) {
            resolveRendererSettingsAvailability(
                pageReaderEnabled = pageReader,
                showWebView = false,
                bionicReadingEnabled = bionicReading,
            )
        }
        val chapterSwipeControlsEnabled = remember(swipeGestures, pageReader) {
            areChapterSwipeControlsEnabled(
                swipeGesturesEnabled = swipeGestures,
                pageReaderEnabled = pageReader,
            )
        }
        val autoScrollIntervalPref = prefs.autoScrollInterval()
        val autoScrollInterval by autoScrollIntervalPref.collectAsState()
        val autoScrollSpeed = intervalToAutoScrollSpeed(autoScrollInterval)
        val autoScrollOffsetPref = prefs.autoScrollOffset()
        val autoScrollOffset by autoScrollOffsetPref.collectAsState()
        val cacheReadChaptersPref = prefs.cacheReadChapters()
        val cacheReadChapters by cacheReadChaptersPref.collectAsState()
        val cacheReadChaptersUnlimitedPref = prefs.cacheReadChaptersUnlimited()
        val cacheReadChaptersUnlimited by cacheReadChaptersUnlimitedPref.collectAsState()
        val chapterCacheRefreshTick = remember { mutableIntStateOf(0) }
        val chapterCacheStats by produceState(
            initialValue = NovelReaderChapterDiskCacheStore.stats(),
            cacheReadChapters,
            cacheReadChaptersUnlimited,
            chapterCacheRefreshTick.intValue,
        ) {
            if (!cacheReadChaptersUnlimited) {
                NovelReaderChapterDiskCacheStore.trimToCurrentLimits()
            }
            value = NovelReaderChapterDiskCacheStore.stats()
        }
        val chapterCacheLimitSizeText = remember(context) {
            Formatter.formatFileSize(context, NovelReaderChapterDiskCache.DEFAULT_MAX_TOTAL_BYTES)
        }
        val chapterCacheSizeText = remember(chapterCacheStats.totalBytes, context) {
            Formatter.formatFileSize(context, chapterCacheStats.totalBytes)
        }
        val chapterCacheSummary = if (cacheReadChaptersUnlimited) {
            stringResource(
                AYMR.strings.novel_reader_chapter_cache_size_summary_unlimited,
                chapterCacheSizeText,
                chapterCacheStats.entryCount.toString(),
            )
        } else {
            stringResource(
                AYMR.strings.novel_reader_chapter_cache_size_summary_limited,
                chapterCacheSizeText,
                chapterCacheStats.entryCount.toString(),
                chapterCacheLimitSizeText,
                NovelReaderChapterDiskCache.DEFAULT_MAX_ENTRIES.toString(),
            )
        }
        fun rendererSubtitle(baseSubtitle: String, enabled: Boolean, reason: String): String {
            return if (enabled) {
                baseSubtitle
            } else {
                "$baseSubtitle $reason"
            }
        }
        val preferWebViewSubtitle = rendererSubtitle(
            baseSubtitle = stringResource(AYMR.strings.novel_reader_prefer_webview_renderer_summary),
            enabled = rendererAvailability.preferWebViewEnabled,
            reason = stringResource(AYMR.strings.novel_reader_renderer_disabled_page_mode_summary),
        )
        val richNativeDisableReason = when {
            pageReader -> stringResource(AYMR.strings.novel_reader_renderer_disabled_page_mode_summary)
            bionicReading -> stringResource(AYMR.strings.novel_reader_renderer_disabled_bionic_summary)
            else -> stringResource(AYMR.strings.novel_reader_renderer_disabled_webview_summary)
        }
        val richNativeSubtitle = rendererSubtitle(
            baseSubtitle = stringResource(AYMR.strings.novel_reader_rich_native_renderer_experimental_summary),
            enabled = rendererAvailability.richNativeEnabled,
            reason = richNativeDisableReason,
        )

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_navigation),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.useVolumeButtons(),
                    title = stringResource(AYMR.strings.novel_reader_volume_buttons),
                    subtitle = stringResource(AYMR.strings.novel_reader_volume_buttons_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.verticalSeekbar(),
                    title = stringResource(AYMR.strings.novel_reader_vertical_seekbar),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = swipeGesturesPref,
                    title = stringResource(AYMR.strings.novel_reader_swipe_gestures),
                    subtitle = stringResource(AYMR.strings.novel_reader_swipe_gestures_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.swipeToNextChapter(),
                    title = stringResource(AYMR.strings.novel_reader_swipe_to_next),
                    enabled = chapterSwipeControlsEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.swipeToPrevChapter(),
                    title = stringResource(AYMR.strings.novel_reader_swipe_to_prev),
                    enabled = chapterSwipeControlsEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.tapToScroll(),
                    title = stringResource(AYMR.strings.novel_reader_tap_to_scroll),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = pageReaderPref,
                    title = stringResource(AYMR.strings.novel_reader_page_mode),
                    subtitle = stringResource(AYMR.strings.novel_reader_page_mode_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = pageTransitionStylePref,
                    entries = pageTransitionEntries,
                    title = stringResource(AYMR.strings.novel_reader_page_transition_style),
                    subtitleProvider = { value: NovelPageTransitionStyle, entries ->
                        novelPageTransitionStyleSubtitle(value, entries)
                    },
                    enabled = pageReader,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.preferWebViewRenderer(),
                    title = stringResource(AYMR.strings.novel_reader_prefer_webview_renderer),
                    subtitle = preferWebViewSubtitle,
                    enabled = rendererAvailability.preferWebViewEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.richNativeRendererExperimental(),
                    title = stringResource(AYMR.strings.novel_reader_rich_native_renderer_experimental),
                    subtitle = richNativeSubtitle,
                    enabled = rendererAvailability.richNativeEnabled,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = autoScrollSpeed,
                    title = stringResource(AYMR.strings.novel_reader_auto_scroll_speed),
                    subtitle = autoScrollSpeed.toString(),
                    valueRange = 1..100,
                    enabled = true,
                    onValueChanged = {
                        autoScrollIntervalPref.set(autoScrollSpeedToInterval(it.coerceIn(1, 100)))
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = autoScrollOffset,
                    title = stringResource(AYMR.strings.novel_reader_auto_scroll_offset),
                    subtitle = autoScrollOffset.toString(),
                    valueRange = 0..2000,
                    enabled = true,
                    onValueChanged = {
                        autoScrollOffsetPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.prefetchNextChapter(),
                    title = stringResource(AYMR.strings.novel_reader_prefetch_next_chapter),
                    subtitle = stringResource(AYMR.strings.novel_reader_prefetch_next_chapter_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.cacheReadChapters(),
                    title = stringResource(AYMR.strings.novel_reader_cache_read_chapters),
                    subtitle = stringResource(AYMR.strings.novel_reader_cache_read_chapters_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = cacheReadChaptersUnlimitedPref,
                    title = stringResource(AYMR.strings.novel_reader_cache_read_chapters_unlimited),
                    subtitle = stringResource(AYMR.strings.novel_reader_cache_read_chapters_unlimited_summary),
                    enabled = cacheReadChapters,
                    onValueChanged = { enabled ->
                        if (!enabled) {
                            NovelReaderChapterDiskCacheStore.trimToCurrentLimits(unlimitedOverride = false)
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_chapter_cache_size),
                    subtitle = chapterCacheSummary,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_clear_chapter_cache),
                    subtitle = stringResource(AYMR.strings.novel_reader_clear_chapter_cache_summary),
                    enabled = chapterCacheStats.entryCount > 0,
                    onClick = {
                        NovelReaderChapterDiskCacheStore.clear()
                        chapterCacheRefreshTick.intValue++
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getAccessibilityGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val showKindleInfoBlockPref = prefs.showKindleInfoBlock()
        val showKindleInfoBlock by showKindleInfoBlockPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_accessibility),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.fullScreenMode(),
                    title = stringResource(AYMR.strings.novel_reader_fullscreen),
                    subtitle = stringResource(AYMR.strings.novel_reader_fullscreen_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.keepScreenOn(),
                    title = stringResource(AYMR.strings.novel_reader_keep_screen_on),
                    subtitle = stringResource(AYMR.strings.novel_reader_keep_screen_on_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.showScrollPercentage(),
                    title = stringResource(AYMR.strings.novel_reader_show_scroll_percentage),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.showBatteryAndTime(),
                    title = stringResource(AYMR.strings.novel_reader_show_battery_time),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showKindleInfoBlockPref,
                    title = stringResource(AYMR.strings.novel_reader_show_kindle_info_block),
                    subtitle = stringResource(AYMR.strings.novel_reader_show_kindle_info_block_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.showTimeToEnd(),
                    title = stringResource(AYMR.strings.novel_reader_show_time_to_end),
                    enabled = showKindleInfoBlock,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.showWordCount(),
                    title = stringResource(AYMR.strings.novel_reader_show_word_count),
                    enabled = showKindleInfoBlock,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.bionicReading(),
                    title = stringResource(AYMR.strings.novel_reader_bionic_reading),
                ),
            ),
        )
    }

    @Composable
    private fun getAdvancedGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val translationProviderPref = prefs.translationProvider()
        val translationProvider by translationProviderPref.collectAsState()
        val privateProviderLabel = if (GeminiPrivateBridge.isInstalled()) {
            GeminiPrivateBridge.providerLabel()
        } else {
            "Gemini Private"
        }
        val items = mutableListOf<Preference.PreferenceItem<out Any>>(
            Preference.PreferenceItem.ListPreference(
                preference = translationProviderPref,
                entries = persistentMapOf(
                    NovelTranslationProvider.GEMINI to
                        stringResource(AYMR.strings.novel_reader_translation_provider_gemini),
                    NovelTranslationProvider.GEMINI_PRIVATE to privateProviderLabel,
                    NovelTranslationProvider.OPENROUTER to
                        stringResource(AYMR.strings.novel_reader_translation_provider_openrouter),
                    NovelTranslationProvider.DEEPSEEK to
                        stringResource(AYMR.strings.novel_reader_translation_provider_deepseek),
                ),
                title = stringResource(AYMR.strings.novel_reader_translation_provider),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = prefs.geminiAutoTranslateEnglishSource(),
                title = stringResource(AYMR.strings.novel_reader_translation_auto_english_title),
                subtitle = stringResource(AYMR.strings.novel_reader_translation_auto_english_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = prefs.geminiPrefetchNextChapterTranslation(),
                title = stringResource(AYMR.strings.novel_reader_translation_prefetch_next_title),
                subtitle = stringResource(AYMR.strings.novel_reader_translation_prefetch_next_summary),
            ),
        )

        if (translationProvider == NovelTranslationProvider.OPENROUTER) {
            items += Preference.PreferenceItem.EditTextInfoPreference(
                preference = prefs.openRouterBaseUrl(),
                dialogSubtitle = null,
                title = stringResource(AYMR.strings.novel_reader_openrouter_base_url),
                subtitle = "%s",
            )
            items += Preference.PreferenceItem.EditTextInfoPreference(
                preference = prefs.openRouterApiKey(),
                dialogSubtitle = null,
                title = stringResource(AYMR.strings.novel_reader_openrouter_api_key),
                subtitle = "%s",
            )
            items += Preference.PreferenceItem.EditTextInfoPreference(
                preference = prefs.openRouterModel(),
                dialogSubtitle = null,
                title = stringResource(AYMR.strings.novel_reader_openrouter_model),
                subtitle = "%s",
            )
        }

        if (translationProvider == NovelTranslationProvider.DEEPSEEK) {
            items += Preference.PreferenceItem.EditTextInfoPreference(
                preference = prefs.deepSeekBaseUrl(),
                dialogSubtitle = null,
                title = stringResource(AYMR.strings.novel_reader_deepseek_base_url),
                subtitle = "%s",
            )
            items += Preference.PreferenceItem.EditTextInfoPreference(
                preference = prefs.deepSeekApiKey(),
                dialogSubtitle = null,
                title = stringResource(AYMR.strings.novel_reader_deepseek_api_key),
                subtitle = "%s",
            )
            items += Preference.PreferenceItem.EditTextInfoPreference(
                preference = prefs.deepSeekModel(),
                dialogSubtitle = null,
                title = stringResource(AYMR.strings.novel_reader_deepseek_model),
                subtitle = "%s",
            )
        }

        items += Preference.PreferenceItem.MultiLineEditTextPreference(
            preference = prefs.customCSS(),
            title = stringResource(AYMR.strings.novel_reader_custom_css),
            subtitle = stringResource(AYMR.strings.novel_reader_custom_css_hint),
            canBeBlank = true,
        )
        items += Preference.PreferenceItem.MultiLineEditTextPreference(
            preference = prefs.customJS(),
            title = stringResource(AYMR.strings.novel_reader_custom_js),
            subtitle = stringResource(AYMR.strings.novel_reader_custom_js_hint),
            canBeBlank = true,
        )

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_advanced),
            preferenceItems = items.toImmutableList(),
        )
    }

    @Composable
    private fun getTextAlignString(textAlign: TextAlign): String {
        return when (textAlign) {
            TextAlign.SOURCE -> stringResource(AYMR.strings.novel_reader_text_align_source)
            TextAlign.LEFT -> stringResource(AYMR.strings.novel_reader_text_align_left)
            TextAlign.CENTER -> stringResource(AYMR.strings.novel_reader_text_align_center)
            TextAlign.JUSTIFY -> stringResource(AYMR.strings.novel_reader_text_align_justify)
            TextAlign.RIGHT -> stringResource(AYMR.strings.novel_reader_text_align_right)
        }
    }

    private fun currentTheme(backgroundColor: String, textColor: String): NovelReaderColorTheme? {
        if (backgroundColor.isBlank() || textColor.isBlank()) return null
        if (!isValidColorOrBlank(backgroundColor) || !isValidColorOrBlank(textColor)) return null
        return NovelReaderColorTheme(backgroundColor = backgroundColor, textColor = textColor)
    }

    private fun isValidColorOrBlank(value: String): Boolean {
        if (value.isBlank()) return true
        return value.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))
    }
}

@Composable
private fun NovelReaderFontPreviewRow(
    selectedFontId: String,
    fonts: List<NovelReaderFontOption>,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onRemoveImported: (NovelReaderFontOption) -> Unit,
) {
    val builtInFonts = remember(fonts) { fonts.filter { it.source == NovelReaderFontSource.BUILT_IN } }
    val localFonts = remember(fonts) { fonts.filter { it.source == NovelReaderFontSource.LOCAL_PRIVATE } }
    val importedFonts = remember(fonts) { fonts.filter { it.source == NovelReaderFontSource.USER_IMPORTED } }

    LazyRow(
        modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("import_font") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.clickable(onClick = onImport),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(MR.strings.action_add),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        items(builtInFonts + localFonts + importedFonts, key = { it.id }) { option ->
            val fontFamily = option.fontResId?.let { FontFamily(Font(it)) }
            val isSelected = option.id == selectedFontId
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.clickable { onSelect(option.id) },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Aa",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = fontFamily),
                    )
                    Text(
                        text = option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = fontFamily),
                    )
                    if (isSelected) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_font_section_selected),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (option.source == NovelReaderFontSource.LOCAL_PRIVATE) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_font_section_local),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (option.source == NovelReaderFontSource.USER_IMPORTED) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_font_section_imported),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (option.source == NovelReaderFontSource.USER_IMPORTED) {
                        Text(
                            text = stringResource(MR.strings.action_delete),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { onRemoveImported(option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelReaderThemePresetRow(
    selectedTheme: NovelReaderColorTheme?,
    onSelect: (NovelReaderColorTheme) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(novelReaderPresetThemes, key = { "${it.backgroundColor}:${it.textColor}" }) { theme ->
            NovelReaderThemePreviewTile(
                theme = theme,
                selected = selectedTheme == theme,
                onClick = { onSelect(theme) },
            )
        }
    }
}

@Composable
private fun NovelReaderBackgroundPresetRow(
    selectedPresetId: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(novelReaderBackgroundPresets, key = { it.id }) { preset ->
            val selected = preset.id == selectedPresetId
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.clickable { onSelect(preset.id) },
            ) {
                Column(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(width = 148.dp, height = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Image(
                        painter = painterResource(id = preset.imageResId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(height = 92.dp, width = 136.dp),
                    )
                    Text(
                        text = readerBackgroundPresetTitle(preset.id),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = readerBackgroundPresetDescription(preset.id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelReaderBackgroundCatalogRow(
    cards: List<NovelReaderBackgroundCard>,
    selectedSource: NovelReaderBackgroundSource,
    selectedPresetId: String,
    selectedCustomId: String,
    onSelectPreset: (String) -> Unit,
    onSelectCustom: (String, String) -> Unit,
    onRenameCustom: (String, String) -> Unit,
    onReplaceCustom: (String) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onUpload: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                val selected = if (card.isBuiltIn) {
                    selectedSource == NovelReaderBackgroundSource.PRESET && selectedPresetId == card.id
                } else {
                    selectedSource == NovelReaderBackgroundSource.CUSTOM && selectedCustomId == card.id
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.clickable {
                        if (card.isBuiltIn) {
                            onSelectPreset(card.id)
                        } else {
                            val customItem = card.customItem ?: return@clickable
                            onSelectCustom(customItem.id, customItem.absolutePath)
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(
                                width = 160.dp,
                                height = if (card.isBuiltIn) 164.dp else 214.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (card.isBuiltIn) {
                            val preset = card.preset ?: return@Column
                            Image(
                                painter = painterResource(id = preset.imageResId),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .size(height = 92.dp, width = 148.dp),
                            )
                        } else {
                            val custom = card.customItem ?: return@Column
                            AsyncImage(
                                model = File(custom.absolutePath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .size(height = 92.dp, width = 148.dp),
                            )
                        }
                        Text(
                            text = if (card.isBuiltIn) {
                                readerBackgroundPresetTitle(card.id)
                            } else {
                                card.customItem?.displayName.orEmpty()
                            },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (card.isBuiltIn) {
                                readerBackgroundPresetDescription(card.id)
                            } else {
                                card.customItem?.absolutePath.orEmpty()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!card.isBuiltIn) {
                            val custom = card.customItem ?: return@Column
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReaderBackgroundActionChip(
                                    label = stringResource(AYMR.strings.editor_action_rename),
                                    onClick = { onRenameCustom(custom.id, custom.displayName) },
                                )
                                ReaderBackgroundActionChip(
                                    label = stringResource(AYMR.strings.novel_reader_background_action_replace),
                                    onClick = { onReplaceCustom(custom.id) },
                                )
                                ReaderBackgroundActionChip(
                                    label = stringResource(AYMR.strings.editor_action_delete),
                                    highlighted = true,
                                    onClick = { onDeleteCustom(custom.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .padding(horizontal = PrefsHorizontalPadding)
                .fillMaxWidth()
                .clickable(onClick = onUpload),
        ) {
            Text(
                text = stringResource(AYMR.strings.novel_reader_background_upload),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Text(
            text = stringResource(AYMR.strings.novel_reader_background_upload_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
        )
    }
}

@Composable
private fun ReaderBackgroundActionChip(
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun readerBackgroundPresetTitle(presetId: String): String {
    return when (presetId) {
        NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_linen_paper_title)
        NOVEL_READER_BACKGROUND_PRESET_AGED_PAGE_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_aged_page_title)
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
private fun readerBackgroundPresetDescription(presetId: String): String {
    return when (presetId) {
        NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_linen_paper_description)
        NOVEL_READER_BACKGROUND_PRESET_AGED_PAGE_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_aged_page_description)
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
private fun NovelReaderThemePreviewTile(
    theme: NovelReaderColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = parseNovelReaderPreviewColor(theme.backgroundColor)
        ?: MaterialTheme.colorScheme.surface
    val foreground = parseNovelReaderPreviewColor(theme.textColor)
        ?: MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 32.dp)
                    .background(color = background, shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Aa",
                    color = foreground,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(color = background, shape = CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(color = foreground, shape = CircleShape),
                )
            }
        }
    }
}

private fun parseNovelReaderPreviewColor(value: String): Color? {
    return runCatching { Color(AndroidColor.parseColor(value)) }.getOrNull()
}
