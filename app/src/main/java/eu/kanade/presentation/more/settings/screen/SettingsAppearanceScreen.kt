package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AnimeMetadataSource
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.widget.AppThemeModePreferenceWidget
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.model.AchievementEvent
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

        return listOf(
            getThemeGroup(uiPreferences = uiPreferences),
            getDisplayGroup(uiPreferences = uiPreferences),
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
        val showAnimeSection by showAnimeSectionPref.collectAsState()
        val showMangaSection by showMangaSectionPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_app_language),
                    onClick = { navigator.push(AppLanguageScreen()) },
                ),
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
                Preference.PreferenceItem.SwitchPreference(
                    preference = showAnimeSectionPref,
                    title = stringResource(AYMR.strings.pref_show_anime_section),
                    subtitle = if (!showMangaSection) {
                        stringResource(AYMR.strings.pref_show_section_required)
                    } else {
                        null
                    },
                    onValueChanged = { newValue ->
                        newValue || showMangaSection
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showMangaSectionPref,
                    title = stringResource(AYMR.strings.pref_show_manga_section),
                    subtitle = if (!showAnimeSection) {
                        stringResource(AYMR.strings.pref_show_section_required)
                    } else {
                        null
                    },
                    onValueChanged = { newValue ->
                        newValue || showAnimeSection
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.navStyle(),
                    entries = NavStyle.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.pref_bottom_nav_style),
                    onValueChanged = { true },
                ),
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
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.relativeTime(),
                    title = stringResource(MR.strings.pref_relative_format),
                    subtitle = stringResource(
                        MR.strings.pref_relative_format_summary,
                        stringResource(MR.strings.relative_time_today),
                        formattedNow,
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.showOriginalTitle(),
                    title = stringResource(AYMR.strings.pref_show_original_title),
                    subtitle = stringResource(AYMR.strings.pref_show_original_title_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.showAchievementNotifications(),
                    title = stringResource(AYMR.strings.pref_show_achievement_notifications),
                    subtitle = stringResource(AYMR.strings.pref_show_achievement_notifications_summary),
                ),
            ),
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
                    preference = uiPreferences.animeMetadataSource(),
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

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)
