package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.browse.AnimeExtensionReposScreen
import eu.kanade.presentation.more.settings.screen.browse.MangaExtensionReposScreen
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionReposScreen
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import kotlinx.collections.immutable.persistentListOf
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepoCount
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepoCount
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepoCount
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsBrowseScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.browse

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        val suggestionsEnabledPref = sourcePreferences.entrySuggestionsEnabled()
        val suggestionsEnabled by suggestionsEnabledPref.collectAsStateWithLifecycle()

        val suggestionsInlinePref = uiPreferences.entrySuggestionsExpandInline()
        val suggestionsInline by suggestionsInlinePref.collectAsStateWithLifecycle()

        val epubImportPref = sourcePreferences.importEpubAddToLibrary()
        val getMangaExtensionRepoCount = remember { Injekt.get<GetMangaExtensionRepoCount>() }
        val getAnimeExtensionRepoCount = remember { Injekt.get<GetAnimeExtensionRepoCount>() }
        val getNovelExtensionRepoCount = remember { Injekt.get<GetNovelExtensionRepoCount>() }

        val mangaReposCount by getMangaExtensionRepoCount.subscribe().collectAsStateWithLifecycle(0)
        val animeReposCount by getAnimeExtensionRepoCount.subscribe().collectAsStateWithLifecycle(0)
        val novelReposCount by getNovelExtensionRepoCount.subscribe().collectAsStateWithLifecycle(0)

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.label_sources),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.hideInAnimeLibraryItems(),
                        title = stringResource(AYMR.strings.pref_hide_in_anime_library_items),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.hideInMangaLibraryItems(),
                        title = stringResource(AYMR.strings.pref_hide_in_manga_library_items),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.hideInNovelLibraryItems(),
                        title = stringResource(AYMR.strings.pref_hide_in_novel_library_items),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.verticalPinnedLayout(),
                        title = stringResource(AYMR.strings.pref_vertical_pinned_layout),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.label_anime_extension_repos),
                        subtitle = pluralStringResource(
                            MR.plurals.num_repos,
                            animeReposCount,
                            animeReposCount,
                        ),
                        onClick = {
                            navigator.push(AnimeExtensionReposScreen())
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.label_manga_extension_repos),
                        subtitle = pluralStringResource(
                            MR.plurals.num_repos,
                            mangaReposCount,
                            mangaReposCount,
                        ),
                        onClick = {
                            navigator.push(MangaExtensionReposScreen())
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.label_novel_extension_repos),
                        subtitle = pluralStringResource(
                            MR.plurals.num_repos,
                            novelReposCount,
                            novelReposCount,
                        ),
                        onClick = {
                            navigator.push(NovelExtensionReposScreen())
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.feed),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.hideFeedTab(),
                        title = stringResource(AYMR.strings.pref_feed_hide_tab),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.feedTabInFront(),
                        title = stringResource(AYMR.strings.pref_feed_tab_in_front),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.hideInLibraryFeedItems(),
                        title = stringResource(AYMR.strings.pref_feed_hide_in_library_items),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_entry_suggestions),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = suggestionsEnabledPref,
                        title = stringResource(MR.strings.pref_entry_suggestions),
                        subtitle = stringResource(MR.strings.pref_entry_suggestions_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = suggestionsInlinePref,
                        title = stringResource(MR.strings.pref_entry_suggestions_inline),
                        subtitle = stringResource(MR.strings.pref_entry_suggestions_inline_summary),
                        enabled = suggestionsEnabled,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.entrySuggestionsInOverflow(),
                        title = stringResource(MR.strings.pref_entry_suggestions_overflow),
                        subtitle = stringResource(MR.strings.pref_entry_suggestions_overflow_summary),
                        enabled = suggestionsEnabled && !suggestionsInline,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_epub_import),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = epubImportPref,
                        title = stringResource(MR.strings.pref_epub_import_add_to_library),
                        subtitle = stringResource(MR.strings.pref_epub_import_add_to_library_summary),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_nsfw_content),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.showNsfwSource(),
                        title = stringResource(MR.strings.pref_show_nsfw_source),
                        subtitle = stringResource(MR.strings.requires_app_restart),
                        onValueChanged = {
                            (context as FragmentActivity).authenticate(
                                title = context.stringResource(MR.strings.pref_category_nsfw_content),
                            )
                        },
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        stringResource(MR.strings.parental_controls_info),
                    ),
                ),
            ),
        )
    }
}
