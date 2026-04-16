package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.domain.ui.model.AnimeMetadataSource
import eu.kanade.domain.ui.model.AuroraTitleHeroCtaMode
import eu.kanade.domain.ui.model.HomeHeroCtaMode
import eu.kanade.domain.ui.model.HomeHubRecentCardMode
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.entries.translation.googleTranslationSourceLanguageFamilyOptions
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.settingsSubtitleColor
import eu.kanade.presentation.more.settings.widget.AppThemeModePreferenceWidget
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.reader.novel.NovelReaderFontOption
import eu.kanade.presentation.reader.novel.NovelReaderFontSource
import eu.kanade.presentation.reader.novel.buildNovelReaderFontCatalog
import eu.kanade.presentation.reader.novel.importNovelReaderCustomFont
import eu.kanade.presentation.reader.novel.removeNovelReaderCustomFont
import eu.kanade.presentation.theme.rememberAppFontFamily
import eu.kanade.tachiyomi.ui.home.HomeHeaderLayoutEditorScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

object SettingsAppearanceScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_appearance

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val userProfilePreferences = remember { Injekt.get<UserProfilePreferences>() }

        return listOf(
            getThemeGroup(uiPreferences = uiPreferences),
            getDisplayGroup(
                uiPreferences = uiPreferences,
                userProfilePreferences = userProfilePreferences,
            ),
            getMetadataGroup(uiPreferences = uiPreferences),
        )
    }

    @Composable
    private fun getThemeGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val themeModePref = uiPreferences.themeMode()
        val themeMode by themeModePref.collectAsState()

        val appThemePref = uiPreferences.appTheme()
        val appTheme by appThemePref.collectAsState()

        val amoledPref = uiPreferences.themeDarkAmoled()
        val amoled by amoledPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_theme),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_app_theme),
                ) {
                    Column {
                        AppThemeModePreferenceWidget(
                            value = themeMode,
                            onItemClick = {
                                themeModePref.set(it)
                                setAppCompatDelegateThemeMode(it)
                            },
                        )

                        AppThemePreferenceWidget(
                            value = appTheme,
                            amoled = amoled,
                            onItemClick = {
                                appThemePref.set(it)
                                // Track theme change for achievement
                                val achievementHandler = Injekt.get<AchievementHandler>()
                                achievementHandler.trackFeatureUsed(AchievementEvent.Feature.THEME_CHANGE)
                            },
                        )
                    }
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = amoledPref,
                    title = stringResource(MR.strings.pref_dark_theme_pure_black),
                    enabled = themeMode != ThemeMode.LIGHT,
                    onValueChanged = {
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(
        uiPreferences: UiPreferences,
        userProfilePreferences: UserProfilePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val now = remember { LocalDate.now() }

        val dateFormat by uiPreferences.dateFormat().collectAsState()
        val formattedNow = remember(dateFormat) {
            UiPreferences.dateFormat(dateFormat).format(now)
        }

        val showAnimeSectionPref = uiPreferences.showAnimeSection()
        val showMangaSectionPref = uiPreferences.showMangaSection()
        val showNovelSectionPref = uiPreferences.showNovelSection()
        val auroraEntryTranslationPref = uiPreferences.auroraEntryTranslationEnabled()
        val auroraEntryTranslationLanguagesPref = uiPreferences.auroraEntryTranslationSourceLanguages()
        val showMangaScanlatorBranchesPref = uiPreferences.showMangaScanlatorBranches()
        val appUiFontPref = uiPreferences.appUiFontId()
        val coverTitleFontPref = uiPreferences.coverTitleFontId()
        val showAnimeSection by showAnimeSectionPref.collectAsState()
        val showMangaSection by showMangaSectionPref.collectAsState()
        val showNovelSection by showNovelSectionPref.collectAsState()
        val auroraEntryTranslationEnabled by auroraEntryTranslationPref.collectAsState()
        val appUiFontId by appUiFontPref.collectAsState()
        val coverTitleFontId by coverTitleFontPref.collectAsState()
        val canResetAppearanceFonts = shouldEnableAppearanceFontsReset(
            appUiFontId = appUiFontId,
            coverTitleFontId = coverTitleFontId,
        )
        var fontCatalogVersion by rememberSaveable { mutableIntStateOf(0) }
        val fontCatalog = remember(context, fontCatalogVersion) {
            buildNovelReaderFontCatalog(context)
        }
        val importedFonts = remember(fontCatalog) {
            fontCatalog.filter { it.source == NovelReaderFontSource.USER_IMPORTED }
        }
        var isFontSettingsExpanded by rememberSaveable { mutableStateOf(false) }
        val importFontLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val importedFont = importNovelReaderCustomFont(context, uri).getOrNull()
            if (importedFont == null) {
                context.toast(AYMR.strings.novel_reader_font_import_failed)
                return@rememberLauncherForActivityResult
            }
            fontCatalogVersion += 1
        }
        val greetingFontSizePref = userProfilePreferences.greetingFontSize()
        val greetingFontSize by greetingFontSizePref.collectAsState()
        val greetingAlphaPref = userProfilePreferences.greetingAlpha()
        val greetingAlpha by greetingAlphaPref.collectAsState()
        val greetingColorPref = userProfilePreferences.greetingColor()
        val greetingColor by greetingColorPref.collectAsState()
        var isAuroraCustomizationExpanded by rememberSaveable { mutableStateOf(true) }
        var isGreetingSettingsExpanded by rememberSaveable { mutableStateOf(false) }
        val auroraCustomizationTitle = stringResource(AYMR.strings.pref_aurora_customization)
        val greetingSettingsToggleTitle = stringResource(AYMR.strings.aurora_change_greeting_style)
        val fontSettingsToggleTitle = stringResource(AYMR.strings.pref_fonts_settings)
        val greetingCustomizationItems = if (isGreetingSettingsExpanded) {
            listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = userProfilePreferences.greetingFont(),
                    entries = mapOf(
                        "default" to stringResource(AYMR.strings.aurora_nickname_font_default),
                        "montserrat" to stringResource(AYMR.strings.aurora_nickname_font_montserrat),
                        "lora" to stringResource(AYMR.strings.aurora_nickname_font_lora),
                        "nunito" to stringResource(AYMR.strings.aurora_nickname_font_nunito),
                        "pt_serif" to stringResource(AYMR.strings.aurora_nickname_font_pt_serif),
                    ).toImmutableMap(),
                    title = stringResource(AYMR.strings.aurora_greeting_font),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = greetingFontSize.coerceIn(10, 26),
                    title = stringResource(AYMR.strings.aurora_greeting_font_size, greetingFontSize.toString()),
                    valueRange = 10..26,
                    steps = 15,
                    onValueChanged = {
                        greetingFontSizePref.set(it.coerceIn(10, 26))
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = greetingAlpha.coerceIn(10, 100),
                    title = stringResource(AYMR.strings.aurora_greeting_alpha, "$greetingAlpha%"),
                    valueRange = 10..100,
                    steps = 89,
                    onValueChanged = {
                        greetingAlphaPref.set(it.coerceIn(10, 100))
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = greetingColorPref,
                    entries = mapOf(
                        "theme" to stringResource(AYMR.strings.aurora_nickname_color_theme),
                        "accent" to stringResource(AYMR.strings.aurora_nickname_color_accent),
                        "gold" to stringResource(AYMR.strings.aurora_nickname_color_gold),
                        "cyan" to stringResource(AYMR.strings.aurora_nickname_color_cyan),
                        "pink" to stringResource(AYMR.strings.aurora_nickname_color_pink),
                        "custom" to stringResource(AYMR.strings.aurora_nickname_color_custom),
                    ).toImmutableMap(),
                    title = stringResource(AYMR.strings.aurora_greeting_color),
                ),
                Preference.PreferenceItem.EditTextInfoPreference(
                    preference = userProfilePreferences.greetingCustomColorHex(),
                    dialogSubtitle = stringResource(AYMR.strings.aurora_greeting_custom_color_hint),
                    title = stringResource(AYMR.strings.aurora_greeting_custom_color),
                    enabled = greetingColor == "custom",
                    onValueChanged = { value ->
                        val compact = value.replace(" ", "")
                        val normalized = when {
                            compact.isEmpty() -> "#"
                            compact.startsWith("#") -> compact
                            else -> "#$compact"
                        }
                        userProfilePreferences.greetingCustomColorHex().set(normalized)
                        false
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = userProfilePreferences.greetingDecoration(),
                    entries = mapOf(
                        "auto" to stringResource(AYMR.strings.aurora_greeting_decoration_auto),
                        "none" to stringResource(AYMR.strings.aurora_greeting_decoration_none),
                        "sparkle" to stringResource(AYMR.strings.aurora_greeting_decoration_sparkle),
                        "hearts" to stringResource(AYMR.strings.aurora_greeting_decoration_hearts),
                        "stars" to stringResource(AYMR.strings.aurora_greeting_decoration_stars),
                        "flowers" to stringResource(AYMR.strings.aurora_greeting_decoration_flowers),
                    ).toImmutableMap(),
                    title = stringResource(AYMR.strings.aurora_greeting_decoration),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = userProfilePreferences.greetingItalic(),
                    title = stringResource(AYMR.strings.aurora_greeting_italic),
                ),
            )
        } else {
            emptyList()
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = buildList<Preference.PreferenceItem<out Any>> {
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_app_language),
                        onClick = { navigator.push(AppLanguageScreen()) },
                    ),
                )
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = uiPreferences.tabletUiMode(),
                        entries = TabletUiMode.entries
                            .associateWith { stringResource(it.titleRes) }
                            .toImmutableMap(),
                        title = stringResource(MR.strings.pref_tablet_ui_mode),
                        onValueChanged = {
                            context.toast(MR.strings.requires_app_restart)
                            true
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = uiPreferences.startScreen(),
                        entries = StartScreen.entries
                            .associateWith { stringResource(it.titleRes) }
                            .toImmutableMap(),
                        title = stringResource(AYMR.strings.pref_start_screen),
                        onValueChanged = {
                            context.toast(MR.strings.requires_app_restart)
                            true
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showAnimeSectionPref,
                        title = stringResource(AYMR.strings.pref_show_anime_section),
                        subtitle = if (!showMangaSection && !showNovelSection) {
                            stringResource(AYMR.strings.pref_show_section_required)
                        } else {
                            null
                        },
                        onValueChanged = { newValue ->
                            newValue || showMangaSection || showNovelSection
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showMangaSectionPref,
                        title = stringResource(AYMR.strings.pref_show_manga_section),
                        subtitle = if (!showAnimeSection && !showNovelSection) {
                            stringResource(AYMR.strings.pref_show_section_required)
                        } else {
                            null
                        },
                        onValueChanged = { newValue ->
                            newValue || showAnimeSection || showNovelSection
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showNovelSectionPref,
                        title = stringResource(AYMR.strings.pref_show_novel_section),
                        subtitle = if (!showAnimeSection && !showMangaSection) {
                            stringResource(AYMR.strings.pref_show_section_required)
                        } else {
                            null
                        },
                        onValueChanged = { newValue ->
                            newValue || showAnimeSection || showMangaSection
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showMangaScanlatorBranchesPref,
                        title = stringResource(AYMR.strings.pref_show_manga_scanlator_branches),
                        subtitle = stringResource(AYMR.strings.pref_show_manga_scanlator_branches_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = uiPreferences.navStyle(),
                        entries = NavStyle.entries
                            .associateWith { stringResource(it.titleRes) }
                            .toImmutableMap(),
                        title = stringResource(AYMR.strings.pref_bottom_nav_style),
                        onValueChanged = { true },
                    ),
                )
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = uiPreferences.dateFormat(),
                        entries = DateFormats
                            .associateWith {
                                val formattedDate = UiPreferences.dateFormat(it).format(now)
                                "${it.ifEmpty { stringResource(MR.strings.label_default) }} ($formattedDate)"
                            }
                            .toImmutableMap(),
                        title = stringResource(MR.strings.pref_date_format),
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.relativeTime(),
                        title = stringResource(MR.strings.pref_relative_format),
                        subtitle = stringResource(
                            MR.strings.pref_relative_format_summary,
                            stringResource(MR.strings.relative_time_today),
                            formattedNow,
                        ),
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.showOriginalTitle(),
                        title = stringResource(AYMR.strings.pref_show_original_title),
                        subtitle = stringResource(AYMR.strings.pref_show_original_title_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = auroraEntryTranslationPref,
                        title = stringResource(AYMR.strings.pref_aurora_entry_translation),
                        subtitle = stringResource(AYMR.strings.pref_aurora_entry_translation_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.MultiSelectListPreference(
                        preference = auroraEntryTranslationLanguagesPref,
                        entries = googleTranslationSourceLanguageFamilyOptions()
                            .associate { it.code to it.label }
                            .toImmutableMap(),
                        title = stringResource(AYMR.strings.pref_aurora_entry_translation_source_languages),
                        subtitle = stringResource(
                            AYMR.strings.pref_aurora_entry_translation_source_languages_summary,
                        ),
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.showAchievementNotifications(),
                        title = stringResource(AYMR.strings.pref_show_achievement_notifications),
                        subtitle = stringResource(AYMR.strings.pref_show_achievement_notifications_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.animatedAuroraBackground(),
                        title = stringResource(AYMR.strings.pref_animated_aurora_background),
                        subtitle = stringResource(AYMR.strings.pref_animated_aurora_background_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.eInkMode(),
                        title = stringResource(AYMR.strings.pref_e_ink_mode),
                        subtitle = stringResource(AYMR.strings.pref_e_ink_mode_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_customize_home_header_layout),
                        subtitle = stringResource(AYMR.strings.pref_customize_home_header_layout_summary),
                        onClick = { navigator.push(HomeHeaderLayoutEditorScreen()) },
                    ),
                )
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = auroraCustomizationTitle,
                        onClick = {
                            isAuroraCustomizationExpanded =
                                toggleAuroraCustomizationExpanded(isAuroraCustomizationExpanded)
                        },
                        widget = { ExpandablePreferenceChevron(expanded = isAuroraCustomizationExpanded) },
                    ),
                )
                if (isAuroraCustomizationExpanded) {
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = userProfilePreferences.homeHubRecentCardMode(),
                            entries = resolveHomeHubRecentCardModeOptions()
                                .associate { it.key to stringResource(it.titleRes) }
                                .toImmutableMap(),
                            title = stringResource(AYMR.strings.pref_home_recent_card_mode),
                            subtitleProvider = { value, entries ->
                                stringResource(
                                    AYMR.strings.pref_home_recent_card_mode_summary,
                                    entries[value].orEmpty(),
                                )
                            },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = userProfilePreferences.homeHeroCtaMode(),
                            entries = resolveHomeHeroCtaModeOptions()
                                .associate { it.key to stringResource(it.titleRes) }
                                .toImmutableMap(),
                            title = stringResource(AYMR.strings.pref_home_hero_cta_mode),
                            subtitleProvider = { value, entries ->
                                stringResource(
                                    AYMR.strings.pref_home_hero_cta_mode_summary,
                                    entries[value].orEmpty(),
                                )
                            },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = userProfilePreferences.auroraTitleHeroCtaMode(),
                            entries = resolveAuroraTitleHeroCtaModeOptions()
                                .associate { it.key to stringResource(it.titleRes) }
                                .toImmutableMap(),
                            title = stringResource(AYMR.strings.pref_aurora_title_hero_cta_mode),
                            subtitleProvider = { value, entries ->
                                stringResource(
                                    AYMR.strings.pref_aurora_title_hero_cta_mode_summary,
                                    entries[value].orEmpty(),
                                )
                            },
                        ),
                    )
                }
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = fontSettingsToggleTitle,
                        onClick = { isFontSettingsExpanded = !isFontSettingsExpanded },
                        widget = { ExpandablePreferenceChevron(expanded = isFontSettingsExpanded) },
                    ),
                )
                if (isFontSettingsExpanded) {
                    add(
                        Preference.PreferenceItem.CustomPreference(
                            title = stringResource(AYMR.strings.pref_app_font_family),
                        ) {
                            FontListPreferenceWidget(
                                title = stringResource(AYMR.strings.pref_app_font_family),
                                value = appUiFontId,
                                fontCatalog = fontCatalog,
                                onValueChange = { appUiFontPref.set(it) },
                            )
                        },
                    )
                    add(
                        Preference.PreferenceItem.CustomPreference(
                            title = stringResource(AYMR.strings.pref_cover_title_font_family),
                        ) {
                            FontListPreferenceWidget(
                                title = stringResource(AYMR.strings.pref_cover_title_font_family),
                                value = coverTitleFontId,
                                fontCatalog = fontCatalog,
                                onValueChange = { coverTitleFontPref.set(it) },
                            )
                        },
                    )
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(AYMR.strings.pref_font_reset_defaults),
                            subtitle = stringResource(AYMR.strings.pref_font_reset_defaults_summary),
                            enabled = canResetAppearanceFonts,
                            onClick = {
                                appUiFontPref.set(UiPreferences.DEFAULT_APP_UI_FONT_ID)
                                coverTitleFontPref.set(UiPreferences.DEFAULT_COVER_TITLE_FONT_ID)
                            },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(AYMR.strings.pref_font_import),
                            subtitle = stringResource(AYMR.strings.pref_font_import_summary),
                            onClick = {
                                importFontLauncher.launch(arrayOf("*/*"))
                            },
                        ),
                    )
                    if (importedFonts.isEmpty()) {
                        add(
                            Preference.PreferenceItem.InfoPreference(
                                title = stringResource(AYMR.strings.novel_reader_font_section_empty_imported),
                            ),
                        )
                    } else {
                        importedFonts.forEach { font ->
                            add(
                                Preference.PreferenceItem.TextPreference(
                                    title = stringResource(AYMR.strings.pref_font_remove_named, font.label),
                                    onClick = {
                                        if (removeNovelReaderCustomFont(font.filePath).isSuccess) {
                                            if (appUiFontId == font.id) {
                                                appUiFontPref.set(UiPreferences.DEFAULT_APP_UI_FONT_ID)
                                            }
                                            if (coverTitleFontId == font.id) {
                                                coverTitleFontPref.set(UiPreferences.DEFAULT_COVER_TITLE_FONT_ID)
                                            }
                                            fontCatalogVersion += 1
                                        }
                                    },
                                ),
                            )
                        }
                    }
                }
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = greetingSettingsToggleTitle,
                        onClick = {
                            isGreetingSettingsExpanded = toggleGreetingSettingsExpanded(isGreetingSettingsExpanded)
                        },
                        widget = { ExpandablePreferenceChevron(expanded = isGreetingSettingsExpanded) },
                    ),
                )
                addAll(greetingCustomizationItems)
            }.toPersistentList(),
        )
    }

    @Composable
    private fun getMetadataGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_metadata),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.metadataSource(),
                    entries = AnimeMetadataSource.entries
                        .associateWith {
                            when (it) {
                                AnimeMetadataSource.ANILIST -> "Anilist"
                                AnimeMetadataSource.SHIKIMORI -> "Shikimori"
                                AnimeMetadataSource.NONE -> stringResource(MR.strings.off)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.pref_anime_metadata_source),
                    subtitle = stringResource(AYMR.strings.pref_anime_metadata_source_summary),
                ),
            ),
        )
    }
}

@Composable
private fun FontListPreferenceWidget(
    title: String,
    value: String,
    fontCatalog: List<NovelReaderFontOption>,
    onValueChange: (String) -> Unit,
) {
    val fallbackLabel = stringResource(MR.strings.label_default)
    val entries = remember(fontCatalog, fallbackLabel) {
        fontCatalog.associate { font ->
            font.id to if (font.label.isBlank()) fallbackLabel else font.label
        }
    }
    val subtitle = remember(value, fontCatalog, fallbackLabel) {
        fontCatalog.firstOrNull { it.id == value }?.label
            ?.takeIf { it.isNotBlank() }
            ?: fallbackLabel
    }

    ListPreferenceWidget(
        value = value,
        title = title,
        subtitle = subtitle,
        icon = null,
        entries = entries,
        entryTextStyle = { fontId ->
            val fontOption = fontCatalog.firstOrNull { it.id == fontId }
            if (fontOption == null) {
                null
            } else {
                val fontFamily = rememberAppFontFamily(fontOption)
                MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily)
            }
        },
        onValueChange = onValueChange,
    )
}

@Composable
private fun ExpandablePreferenceChevron(expanded: Boolean) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = settingsSubtitleColor(),
        modifier = Modifier
            .rotate(if (expanded) 90f else 0f)
            .size(20.dp),
    )
}

internal fun toggleGreetingSettingsExpanded(currentlyExpanded: Boolean): Boolean {
    return !currentlyExpanded
}

internal fun toggleAuroraCustomizationExpanded(currentlyExpanded: Boolean): Boolean {
    return !currentlyExpanded
}

internal fun resolveHomeHeroCtaModeOptions(): List<HomeHeroCtaMode> {
    return listOf(
        HomeHeroCtaMode.Aurora,
        HomeHeroCtaMode.Classic,
    )
}

internal fun resolveHomeHubRecentCardModeOptions(): List<HomeHubRecentCardMode> {
    return listOf(
        HomeHubRecentCardMode.Aurora,
        HomeHubRecentCardMode.Classic,
    )
}

internal fun resolveAuroraTitleHeroCtaModeOptions(): List<AuroraTitleHeroCtaMode> {
    return listOf(
        AuroraTitleHeroCtaMode.Aurora,
        AuroraTitleHeroCtaMode.Classic,
    )
}

internal fun shouldEnableAppearanceFontsReset(
    appUiFontId: String,
    coverTitleFontId: String,
): Boolean {
    return appUiFontId != UiPreferences.DEFAULT_APP_UI_FONT_ID ||
        coverTitleFontId != UiPreferences.DEFAULT_COVER_TITLE_FONT_ID
}

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)
