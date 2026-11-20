package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.player.components.SubtitleStylePreviewAndPresets
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubtitlesBorderStyle
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.MissingResourceException

object PlayerSettingsSubtitleScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_player_subtitle

    @Composable
    override fun getPreferences(): List<Preference> {
        val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }

        val langPref = subtitlePreferences.preferredSubLanguages()
        val exactMatchPref = subtitlePreferences.preferExactSubtitleMatch()
        val whitelist = subtitlePreferences.subtitleWhitelist()
        val blacklist = subtitlePreferences.subtitleBlacklist()

        val fontSizePref = subtitlePreferences.subtitleFontSize()
        val fontSize by fontSizePref.collectAsState()
        val boldPref = subtitlePreferences.boldSubtitles()
        val italicPref = subtitlePreferences.italicSubtitles()
        val borderStylePref = subtitlePreferences.borderStyleSubtitles()
        val subtitlePosPref = subtitlePreferences.subtitlePos()
        val subtitlePos by subtitlePosPref.collectAsState()
        val overrideAssPref = subtitlePreferences.overrideSubsASS()

        val subtitleTranslationEnabled = subtitlePreferences.subtitleTranslationEnabled()
        val isTranslationEnabled by subtitleTranslationEnabled.collectAsState()
        val subtitleTranslationProvider = subtitlePreferences.subtitleTranslationProvider()
        val activeTranslationProvider by subtitleTranslationProvider.collectAsState()
        val subtitleTranslationSourceLanguage = subtitlePreferences.subtitleTranslationSourceLanguage()
        val subtitleTranslationTargetLanguage = subtitlePreferences.subtitleTranslationTargetLanguage()
        val subtitleTranslationAllowAi = subtitlePreferences.subtitleTranslationAllowAiProviders()
        val subtitleTranslationCache = subtitlePreferences.subtitleTranslationCacheEnabled()

        val translationItems = buildList<Preference.PreferenceItem<out Any>> {
            add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = subtitleTranslationEnabled,
                    title = stringResource(AYMR.strings.pref_player_subtitle_translation),
                    subtitle = stringResource(AYMR.strings.pref_player_subtitle_translation_summary),
                ),
            )
            if (isTranslationEnabled) {
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = subtitleTranslationProvider,
                        entries = persistentMapOf(
                            "google" to stringResource(AYMR.strings.player_subtitle_translation_provider_google),
                            "ai" to stringResource(AYMR.strings.player_subtitle_translation_provider_ai),
                        ),
                        title = stringResource(AYMR.strings.pref_player_subtitle_translation_provider),
                        subtitle = "%s",
                        onValueChanged = { value ->
                            value == "google" || value == "ai"
                        },
                    ),
                )
                if (activeTranslationProvider == "ai") {
                    add(
                        Preference.PreferenceItem.InfoPreference(
                            title = stringResource(AYMR.strings.pref_player_subtitle_translation_ai_info),
                        ),
                    )
                } else {
                    add(
                        Preference.PreferenceItem.InfoPreference(
                            title = stringResource(AYMR.strings.pref_player_subtitle_translation_google_info),
                        ),
                    )
                }
                add(
                    Preference.PreferenceItem.EditTextInfoPreference(
                        preference = subtitleTranslationSourceLanguage,
                        dialogSubtitle = stringResource(AYMR.strings.pref_player_subtitle_translation_source_lang_summary),
                        title = stringResource(AYMR.strings.pref_player_subtitle_translation_source_lang),
                    ),
                )
                add(
                    Preference.PreferenceItem.EditTextInfoPreference(
                        preference = subtitleTranslationTargetLanguage,
                        dialogSubtitle = stringResource(AYMR.strings.pref_player_subtitle_translation_target_lang_summary),
                        title = stringResource(AYMR.strings.pref_player_subtitle_translation_target_lang),
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = subtitleTranslationAllowAi,
                        title = stringResource(AYMR.strings.pref_player_subtitle_translation_allow_ai),
                        subtitle = stringResource(AYMR.strings.pref_player_subtitle_translation_allow_ai_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = subtitleTranslationCache,
                        title = stringResource(AYMR.strings.pref_player_subtitle_translation_cache),
                    ),
                )
            }
        }.toImmutableList()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.player_subtitle_group_appearance),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.CustomPreference(
                        title = stringResource(AYMR.strings.player_subtitle_group_appearance),
                        content = {
                            SubtitleStylePreviewAndPresets(
                                subtitlePreferences = subtitlePreferences,
                                applyLiveToMpv = false,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = fontSize.coerceIn(24, 96),
                        valueRange = 24..96,
                        title = stringResource(AYMR.strings.player_sheets_sub_typography_font_size),
                        subtitle = fontSize.toString(),
                        onValueChanged = {
                            fontSizePref.set(it)
                            true
                        },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = borderStylePref,
                        entries = SubtitlesBorderStyle.entries.associateWith {
                            stringResource(it.titleRes)
                        }.toImmutableMap(),
                        title = stringResource(AYMR.strings.player_sheets_sub_typography_border_style),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = boldPref,
                        title = stringResource(AYMR.strings.player_subtitle_bold),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = italicPref,
                        title = stringResource(AYMR.strings.player_subtitle_italic),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = subtitlePos.coerceIn(0, 150),
                        valueRange = 0..150,
                        title = stringResource(AYMR.strings.player_sheets_sub_position),
                        subtitle = subtitlePos.toString(),
                        onValueChanged = {
                            subtitlePosPref.set(it)
                            true
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = overrideAssPref,
                        title = stringResource(AYMR.strings.player_sheets_sub_override_ass),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.player_subtitle_group_tracks),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.EditTextInfoPreference(
                        preference = langPref,
                        dialogSubtitle = stringResource(AYMR.strings.pref_player_subtitle_lang_info),
                        title = stringResource(AYMR.strings.pref_player_subtitle_lang),
                        validate = { pref ->
                            val langs = pref.split(",").filter(String::isNotEmpty).map(String::trim)
                            langs.forEach {
                                try {
                                    val locale = Locale.forLanguageTag(it)
                                    if (locale.isO3Language == locale.language &&
                                        locale.language == locale.getDisplayName(Locale.ENGLISH)
                                    ) {
                                        throw MissingResourceException("", "", "")
                                    }
                                } catch (_: MissingResourceException) {
                                    return@EditTextInfoPreference false
                                }
                            }
                            true
                        },
                        errorMessage = { pref ->
                            val langs = pref.split(",").filter(String::isNotEmpty).map(String::trim)
                            langs.forEach {
                                try {
                                    val locale = Locale.forLanguageTag(it)
                                    if (locale.isO3Language == locale.language &&
                                        locale.language == locale.getDisplayName(Locale.ENGLISH)
                                    ) {
                                        throw MissingResourceException("", "", "")
                                    }
                                } catch (_: MissingResourceException) {
                                    return@EditTextInfoPreference stringResource(
                                        AYMR.strings.pref_player_subtitle_invalid_lang,
                                        it,
                                    )
                                }
                            }
                            ""
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = exactMatchPref,
                        title = stringResource(AYMR.strings.pref_player_subtitle_exact_match),
                        subtitle = stringResource(AYMR.strings.pref_player_subtitle_exact_match_summary),
                    ),
                    Preference.PreferenceItem.EditTextInfoPreference(
                        preference = whitelist,
                        dialogSubtitle = stringResource(AYMR.strings.pref_player_subtitle_whitelist_info),
                        title = stringResource(AYMR.strings.pref_player_subtitle_whitelist),
                    ),
                    Preference.PreferenceItem.EditTextInfoPreference(
                        preference = blacklist,
                        dialogSubtitle = stringResource(AYMR.strings.pref_player_subtitle_blacklist_info),
                        title = stringResource(AYMR.strings.pref_player_subtitle_blacklist),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_player_subtitle_translation),
                preferenceItems = translationItems,
            ),
        )
    }
}
