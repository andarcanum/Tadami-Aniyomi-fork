package eu.kanade.domain.source.service

import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.model.IncognitoPolicy
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.library.model.LibraryDisplayMode

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    enum class MigrationStrategy {
        FIRST_SOURCE,
        MOST_CHAPTERS,
    }

    // Common options

    fun sourceDisplayMode() = preferenceStore.getObject(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun enabledLanguages() = preferenceStore.getStringSet(
        "source_languages",
        LocaleHelper.getDefaultEnabledLanguages(),
    )

    fun showNsfwSource() = preferenceStore.getBoolean("show_nsfw_source", true)

    fun migrationSortingMode() = preferenceStore.getEnum(
        "pref_migration_sorting",
        SetMigrateSorting.Mode.ALPHABETICAL,
    )

    fun migrationSortingDirection() = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    fun migrationFlags() = preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    fun migrationFlagsAnime() = preferenceStore.getInt("migrate_flags_anime", Int.MAX_VALUE)

    fun migrationFlagsNovel() = preferenceStore.getInt("migrate_flags_novel", Int.MAX_VALUE)

    fun migrationSources() = preferenceStore.getStringSet("migration_sources", emptySet())

    fun migrationSourcesAnime() = preferenceStore.getStringSet("migration_sources_anime", emptySet())

    fun migrationSourcesNovel() = preferenceStore.getStringSet("migration_sources_novel", emptySet())

    fun migrationStrategy() = if (migrationPrioritizeByChapters().get()) {
        MigrationStrategy.MOST_CHAPTERS
    } else {
        MigrationStrategy.FIRST_SOURCE
    }

    fun migrationSearchKeywords() = migrationBooleanPreference(
        key = "migration_search_keywords",
        defaultValue = true,
        legacyKey = "migration_deep_search_mode",
        legacyDefaultValue = false,
    )

    fun migrationExtraSearchParam() = preferenceStore.getBoolean("migration_extra_search_param", true)

    fun migrationSkipNextTime() = preferenceStore.getBoolean("migration_skip_next_time", false)

    fun migrationHideNotFound() = migrationBooleanPreference(
        key = "migration_hide_not_found",
        defaultValue = false,
        legacyKey = "migration_hide_unmatched",
        legacyDefaultValue = false,
    )

    fun migrationOnlyNewChapters() = migrationBooleanPreference(
        key = "migration_only_new_chapters",
        defaultValue = false,
        legacyKey = "migration_hide_without_updates",
        legacyDefaultValue = false,
    )

    fun migrationHideUnmatched() = migrationHideNotFound()

    fun migrationHideWithoutUpdates() = migrationOnlyNewChapters()

    fun migrationDeepSearchMode() = migrationSearchKeywords()

    fun migrationPrioritizeByChapters() = preferenceStore.getBoolean("migration_prioritize_by_chapters", false)

    fun animeExtensionRepos() = preferenceStore.getStringSet("anime_extension_repos", emptySet())

    fun mangaExtensionRepos() = preferenceStore.getStringSet("extension_repos", emptySet())

    fun animeInstalledExtensionRepos() = preferenceStore.getStringSet("anime_installed_extension_repos", emptySet())

    fun mangaInstalledExtensionRepos() = preferenceStore.getStringSet("manga_installed_extension_repos", emptySet())

    fun novelInstalledExtensionRepos() = preferenceStore.getStringSet("novel_installed_extension_repos", emptySet())

    fun trustedExtensions() = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    fun globalSearchFilterState() = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    // Mixture Sources

    fun disabledAnimeSources() = preferenceStore.getStringSet("hidden_anime_catalogues", emptySet())
    fun disabledMangaSources() = preferenceStore.getStringSet("hidden_catalogues", emptySet())
    fun disabledNovelSources() = preferenceStore.getStringSet("hidden_novel_catalogues", emptySet())

    fun incognitoPolicy() = preferenceStore.getEnum("incognito_policy", IncognitoPolicy.MANUAL_ONLY)

    fun incognitoAnimeExtensions() = preferenceStore.getStringSet("incognito_anime_extensions", emptySet())
    fun incognitoMangaExtensions() = preferenceStore.getStringSet("incognito_manga_extensions", emptySet())
    fun incognitoNovelExtensions() = preferenceStore.getStringSet("incognito_novel_extensions", emptySet())

    fun pinnedAnimeSources() = preferenceStore.getStringSet("pinned_anime_catalogues", emptySet())
    fun pinnedMangaSources() = preferenceStore.getStringSet("pinned_catalogues", emptySet())
    fun pinnedNovelSources() = preferenceStore.getStringSet("pinned_novel_catalogues", emptySet())

    fun verticalPinnedLayout() = preferenceStore.getBoolean("vertical_pinned_layout", false)

    fun mangaFeedSources() = preferenceStore.getStringSet(
        Preference.appStateKey("manga_feed_sources"),
        emptySet(),
    )

    fun animeFeedSources() = preferenceStore.getStringSet(
        Preference.appStateKey("anime_feed_sources"),
        emptySet(),
    )

    fun novelFeedSources() = preferenceStore.getStringSet(
        Preference.appStateKey("novel_feed_sources"),
        emptySet(),
    )

    fun lastUsedAnimeSource() = preferenceStore.getLong(
        Preference.appStateKey("last_anime_catalogue_source"),
        -1,
    )
    fun lastUsedMangaSource() = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )
    fun lastUsedNovelSource() = preferenceStore.getLong(
        Preference.appStateKey("last_novel_catalogue_source"),
        -1,
    )

    fun animeExtensionUpdatesCount() = preferenceStore.getInt("animeext_updates_count", 0)
    fun mangaExtensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)
    fun novelExtensionUpdatesCount() = preferenceStore.getInt("novelext_updates_count", 0)
    fun browseExtensionUpdatesSeenCount() = preferenceStore.getInt(
        Preference.appStateKey("browse_extension_updates_seen_count"),
        0,
    )

    fun hideInAnimeLibraryItems() = preferenceStore.getBoolean(
        "browse_hide_in_anime_library_items",
        false,
    )

    fun hideInMangaLibraryItems() = preferenceStore.getBoolean(
        "browse_hide_in_library_items",
        false,
    )

    fun hideInLibraryFeedItems() = preferenceStore.getBoolean("feed_hide_in_library_items", false)

    fun hideInNovelLibraryItems() = preferenceStore.getBoolean(
        "browse_hide_in_novel_library_items",
        false,
    )

    // SY -->

    // fun enableSourceBlacklist() = preferenceStore.getBoolean("eh_enable_source_blacklist", true)

    // fun sourcesTabCategories() = preferenceStore.getStringSet("sources_tab_categories", mutableSetOf())

    // fun sourcesTabCategoriesFilter() = preferenceStore.getBoolean("sources_tab_categories_filter", false)

    // fun sourcesTabSourcesInCategories() = preferenceStore.getStringSet("sources_tab_source_categories", mutableSetOf())

    fun dataSaver() = preferenceStore.getEnum("data_saver", DataSaver.NONE)

    fun dataSaverIgnoreJpeg() = preferenceStore.getBoolean("ignore_jpeg", false)

    fun dataSaverIgnoreGif() = preferenceStore.getBoolean("ignore_gif", true)

    fun dataSaverImageQuality() = preferenceStore.getInt("data_saver_image_quality", 80)

    fun dataSaverImageFormatJpeg() = preferenceStore.getBoolean(
        "data_saver_image_format_jpeg",
        false,
    )

    fun dataSaverServer() = preferenceStore.getString("data_saver_server", "")

    fun dataSaverColorBW() = preferenceStore.getBoolean("data_saver_color_bw", false)

    fun dataSaverExcludedSources() = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    fun dataSaverDownloader() = preferenceStore.getBoolean("data_saver_downloader", true)

    enum class DataSaver {
        NONE,
        BANDWIDTH_HERO,
        WSRV_NL,
        RESMUSH_IT,
    }
    // SY <--

    private fun migrationBooleanPreference(
        key: String,
        defaultValue: Boolean,
        legacyKey: String,
        legacyDefaultValue: Boolean,
    ): Preference<Boolean> {
        val preference = preferenceStore.getBoolean(key, defaultValue)
        if (preference.isSet()) return preference

        val legacyPreference = preferenceStore.getBoolean(legacyKey, legacyDefaultValue)
        if (legacyPreference.isSet()) {
            preference.set(legacyPreference.get())
        }
        return preference
    }

    fun entrySuggestionsEnabled() = preferenceStore.getBoolean("entry_suggestions_enabled", true)

    /**
     * F3.2 — Suggestion source toggles for NOVEL catalogues.
     *
     * Each flag independently controls whether the corresponding source
     * participates in the "Similar works" pipeline for novels. The defaults
     * preserve the previous behaviour (everything on); turning a flag off
     * excludes that source from the candidate set, which is the standard
     * user-facing escape hatch for noisy recommendations.
     */
    fun suggestionsUseMangaUpdatesNovel() = preferenceStore.getBoolean(
        "suggestions_use_mangaupdates_novel",
        true,
    )

    fun suggestionsUseNovelUpdates() = preferenceStore.getBoolean(
        "suggestions_use_novelupdates",
        true,
    )

    fun suggestionsPopularBackfillEnabled() = preferenceStore.getBoolean(
        "suggestions_popular_backfill_enabled",
        true,
    )

    fun importEpubAddToLibrary() = preferenceStore.getBoolean("pref_epub_import_add_to_library", true)
}
