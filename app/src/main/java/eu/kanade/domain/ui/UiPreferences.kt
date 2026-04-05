package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.NavTransitionMode
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import tachiyomi.domain.metadata.model.MetadataSource
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

    fun showNovelSection() = preferenceStore.getBoolean("aurora_show_novel_section", true)

    fun showMangaScanlatorBranches() = preferenceStore.getBoolean("show_manga_scanlator_branches", false)

    fun entryAutoJumpToNextAnime() = preferenceStore.getBoolean("entry_auto_jump_to_next_anime", false)

    fun entryAutoJumpToNextManga() = preferenceStore.getBoolean("entry_auto_jump_to_next_manga", false)

    fun entryAutoJumpToNextNovel() = preferenceStore.getBoolean("entry_auto_jump_to_next_novel", false)

    fun navStyle() = preferenceStore.getEnum("bottom_rail_nav_style", NavStyle.MOVE_HISTORY_TO_MORE)

    fun navigationTransitionMode() = preferenceStore.getEnum(
        "navigation_transition_mode",
        NavTransitionMode.MODERN,
    )

    /**
     * Source for external metadata (posters, ratings, type, status).
     * Default is ANILIST for better coverage and quality.
     */
    fun metadataSource() = preferenceStore.getEnum(
        "anime_metadata_source",
        MetadataSource.ANILIST,
    )

    @Deprecated("Use metadataSource() instead", ReplaceWith("metadataSource()"))
    fun animeMetadataSource() = metadataSource()

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

    fun showAchievementNotifications() = preferenceStore.getBoolean("show_achievement_notifications", true)

    fun animatedAuroraBackground() = preferenceStore.getBoolean("animated_aurora_background", true)

    fun eInkMode() = preferenceStore.getBoolean("e_ink_mode", false)

    fun appUiFontId() = preferenceStore.getString("app_ui_font_id", DEFAULT_APP_UI_FONT_ID)

    fun coverTitleFontId() = preferenceStore.getString("cover_title_font_id", DEFAULT_COVER_TITLE_FONT_ID)

    companion object {
        const val DEFAULT_APP_UI_FONT_ID = ""
        const val DEFAULT_COVER_TITLE_FONT_ID = ""

        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
