package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AnimeMetadataSource
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun themeMode() = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        AppTheme.AURORA,
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    fun relativeTime() = preferenceStore.getBoolean("relative_time_v2", true)

    fun dateFormat() = preferenceStore.getString("app_date_format", "")

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    fun startScreen() = preferenceStore.getEnum("start_screen", StartScreen.HOME)

    fun showAnimeSection() = preferenceStore.getBoolean("aurora_show_anime_section", true)

    fun showMangaSection() = preferenceStore.getBoolean("aurora_show_manga_section", true)

    fun navStyle() = preferenceStore.getEnum("bottom_rail_nav_style", NavStyle.MOVE_HISTORY_TO_MORE)

    /**
     * Source for anime metadata (posters, ratings, type, status).
     * Default is ANILIST for better coverage and quality.
     */
    fun animeMetadataSource() = preferenceStore.getEnum(
        "anime_metadata_source",
        AnimeMetadataSource.ANILIST,
    )

    /**
     * Whether the metadata authentication hint has been shown.
     * Used to show the hint only once.
     */
    fun metadataAuthHintShown() = preferenceStore.getBoolean("metadata_auth_hint_shown", false)

    @Deprecated("Use animeMetadataSource() instead", ReplaceWith("animeMetadataSource()"))
    fun useShikimoriRating() = preferenceStore.getBoolean("use_shikimori_rating", true)

    @Deprecated("Use animeMetadataSource() instead", ReplaceWith("animeMetadataSource()"))
    fun useShikimoriCovers() = preferenceStore.getBoolean("use_shikimori_covers", true)

    fun showOriginalTitle() = preferenceStore.getBoolean("show_original_title", true)

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
