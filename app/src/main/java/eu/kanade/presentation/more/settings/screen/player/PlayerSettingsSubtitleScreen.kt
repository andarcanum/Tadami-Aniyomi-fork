package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
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
        val subtitleTranslationEnabled = subtitlePreferences.subtitleTranslationEnabled()
        val subtitleTranslationProvider = subtitlePreferences.subtitleTranslationProvider()
        val subtitleTranslationSourceLanguage = subtitlePreferences.subtitleTranslationSourceLanguage()
        val subtitleTranslationTargetLanguage = subtitlePreferences.subtitleTranslationTargetLanguage()
        val subtitleTranslationAllowAi = subtitlePreferences.subtitleTranslationAllowAiProviders()
        val subtitleTranslationCache = subtitlePreferences.subtitleTranslationCacheEnabled()

        return listOf(
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
            Preference.PreferenceItem.SwitchPreference(
                preference = subtitleTranslationEnabled,
                title = stringResource(AYMR.strings.pref_player_subtitle_translation),
                subtitle = stringResource(AYMR.strings.pref_player_subtitle_translation_summary),
            ),
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
            Preference.PreferenceItem.InfoPreference(
                title = stringResource(AYMR.strings.pref_player_subtitle_translation_google_info),
            ),
            Preference.PreferenceItem.InfoPreference(
                title = stringResource(AYMR.strings.pref_player_subtitle_translation_ai_info),
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                preference = subtitleTranslationSourceLanguage,
                dialogSubtitle = stringResource(AYMR.strings.pref_player_subtitle_translation_source_lang_summary),
                title = stringResource(AYMR.strings.pref_player_subtitle_translation_source_lang),
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                preference = subtitleTranslationTargetLanguage,
                dialogSubtitle = stringResource(AYMR.strings.pref_player_subtitle_translation_target_lang_summary),
                title = stringResource(AYMR.strings.pref_player_subtitle_translation_target_lang),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = subtitleTranslationAllowAi,
                title = stringResource(AYMR.strings.pref_player_subtitle_translation_allow_ai),
                subtitle = stringResource(AYMR.strings.pref_player_subtitle_translation_allow_ai_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = subtitleTranslationCache,
                title = stringResource(AYMR.strings.pref_player_subtitle_translation_cache),
            ),
        )
    }
}
