package eu.kanade.tachiyomi.data.achievement.localization

import android.content.Context
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.data.achievement.localization.AchievementLocalizedText
import tachiyomi.data.achievement.localization.AchievementTextResolver
import tachiyomi.data.achievement.model.AchievementJson
import tachiyomi.i18n.aniyomi.AYMR

internal data class AchievementTextResourceRefs(
    val title: StringResource?,
    val description: StringResource?,
)

class AchievementTextResolverImpl(
    private val context: Context,
) : AchievementTextResolver {

    override fun resolve(achievement: AchievementJson): AchievementLocalizedText {
        val refs = achievementTextResourceRefs(achievement.id)
        return AchievementLocalizedText(
            title = refs.title?.let(context::stringResource) ?: achievement.title,
            description = refs.description?.let(context::stringResource) ?: achievement.description,
        )
    }
}

internal fun achievementTextResourceRefs(achievementId: String): AchievementTextResourceRefs {
    return when (achievementId) {
        "first_chapter" -> text(
            AYMR.strings.achievement_first_chapter_title,
            AYMR.strings.achievement_first_chapter_desc,
        )

        "read_10_chapters" -> mangaReadResources(10)
        "read_50_chapters" -> mangaReadResources(50)
        "read_100_chapters" -> mangaReadResources(100)
        "read_500_chapters" -> mangaReadResources(500)
        "read_1000_chapters" -> mangaReadResources(1000)
        "complete_1_manga" -> mangaCompletionResources(1)
        "complete_10_manga" -> mangaCompletionResources(10)
        "complete_50_manga" -> mangaCompletionResources(50)
        "read_long_manga" -> text(
            AYMR.strings.achievement_manga_long_haul_title,
            AYMR.strings.achievement_manga_long_haul_desc,
        )

        "first_novel_chapter" -> text(
            AYMR.strings.achievement_first_novel_chapter_title,
            AYMR.strings.achievement_first_novel_chapter_desc,
        )
        "read_10_novel_chapters" -> novelReadResources(10)
        "read_50_novel_chapters" -> novelReadResources(50)
        "read_100_novel_chapters" -> novelReadResources(100)
        "read_500_novel_chapters" -> novelReadResources(500)
        "read_1000_novel_chapters" -> novelReadResources(1000)
        "complete_1_novel" -> novelCompletionResources(1)
        "complete_10_novel" -> novelCompletionResources(10)
        "complete_50_novel" -> novelCompletionResources(50)
        "read_long_novel" -> text(
            AYMR.strings.achievement_read_long_novel_title,
            AYMR.strings.achievement_read_long_novel_desc,
        )

        "first_episode" -> text(
            AYMR.strings.achievement_first_episode_title,
            AYMR.strings.achievement_first_episode_desc,
        )
        "watch_10_episodes" -> animeWatchResources(10)
        "watch_50_episodes" -> animeWatchResources(50)
        "watch_100_episodes" -> animeWatchResources(100)
        "watch_500_episodes" -> animeWatchResources(500)
        "watch_1000_episodes" -> animeWatchResources(1000)
        "complete_1_anime" -> animeCompletionResources(1)
        "complete_10_anime" -> animeCompletionResources(10)
        "complete_50_anime" -> animeCompletionResources(50)

        "genre_explorer" -> genreResources(5)
        "genre_explorer_complete" -> genreResources(10)
        "genre_explorer_ultimate" -> genreResources(20)

        "week_warrior" -> streakResources(7)
        "month_master" -> text(
            AYMR.strings.achievement_month_master_title,
            AYMR.strings.achievement_month_master_desc,
        )
        "season_champion" -> text(
            AYMR.strings.achievement_season_champion_title,
            AYMR.strings.achievement_season_champion_desc,
        )
        "yearly_devotee" -> text(
            AYMR.strings.achievement_yearly_devoted_title,
            AYMR.strings.achievement_yearly_devoted_desc,
        )

        "library_collector" -> libraryResources(100)
        "library_hoarder" -> libraryResources(500)
        "library_titan" -> text(
            AYMR.strings.achievement_library_titan_title,
            AYMR.strings.achievement_library_titan_desc,
        )
        "library_god" -> text(
            AYMR.strings.achievement_library_deity_title,
            AYMR.strings.achievement_library_deity_desc,
        )

        "content_master" -> text(
            AYMR.strings.achievement_content_master_title,
            AYMR.strings.achievement_content_master_desc,
        )
        "content_god" -> text(
            AYMR.strings.achievement_content_deity_title,
            AYMR.strings.achievement_content_deity_desc,
        )
        "content_overlord" -> text(
            AYMR.strings.achievement_universe_overlord_title,
            AYMR.strings.achievement_universe_overlord_desc,
        )

        "master_achiever" -> text(
            AYMR.strings.achievement_master_achiever_title,
            AYMR.strings.achievement_master_achiever_desc,
        )
        "achievement_hunter" -> text(
            AYMR.strings.achievement_achievement_hunter_title,
            AYMR.strings.achievement_achievement_hunter_desc,
        )
        "achievement_collector" -> text(
            AYMR.strings.achievement_achievement_collector_title,
            AYMR.strings.achievement_achievement_collector_desc,
        )
        "achievement_completionist" -> text(
            AYMR.strings.achievement_completionist_title,
            AYMR.strings.achievement_completionist_desc,
        )

        "balanced_fan" -> text(
            AYMR.strings.achievement_balanced_fan_title,
            AYMR.strings.achievement_balanced_fan_desc,
        )
        "hybrid_connoisseur" -> text(
            AYMR.strings.achievement_hybrid_appreciator_title,
            AYMR.strings.achievement_hybrid_appreciator_desc,
        )
        "perfect_balance" -> text(
            AYMR.strings.achievement_perfect_balance_title,
            AYMR.strings.achievement_perfect_balance_desc,
        )

        "night_owl" -> timeResources("night_owl")
        "early_bird" -> timeResources("early_bird")
        "marathon_reader" -> timeResources("marathon_reader")

        "download_starter" -> featureResources("download_starter")
        "chapter_collector" -> featureResources("chapter_collector")
        "trophy_hunter" -> featureResources("trophy_hunter")
        "search_user" -> featureResources("search_user")
        "advanced_explorer" -> featureResources("advanced_explorer")
        "filter_master" -> featureResources("filter_master")
        "backup_master" -> featureResources("backup_master")
        "settings_explorer" -> featureResources("settings_explorer")
        "stats_viewer" -> featureResources("stats_viewer")
        "theme_changer" -> featureResources("theme_changer")
        "persistent_clicker" -> featureResources("persistent_clicker")
        "secret_hall_unlocked" -> featureResources("secret_hall_unlocked")

        "secret_crybaby" -> text(
            AYMR.strings.achievement_secret_crybaby_title,
            AYMR.strings.achievement_secret_crybaby_desc,
        )
        "secret_harem_king" -> text(
            AYMR.strings.achievement_secret_harem_king_title,
            AYMR.strings.achievement_secret_harem_king_desc,
        )
        "secret_isekai_truck" -> text(
            AYMR.strings.achievement_secret_truck_victim_title,
            AYMR.strings.achievement_secret_truck_victim_desc,
        )
        "secret_chad" -> text(
            AYMR.strings.achievement_secret_chad_reader_title,
            AYMR.strings.achievement_secret_chad_reader_desc,
        )
        "secret_shonen" -> text(
            AYMR.strings.achievement_secret_power_of_friendship_title,
            AYMR.strings.achievement_secret_power_of_friendship_desc,
        )
        "secret_deku" -> text(
            AYMR.strings.achievement_secret_plus_ultra_title,
            AYMR.strings.achievement_secret_plus_ultra_desc,
        )
        "secret_eren" -> text(
            AYMR.strings.achievement_secret_tatakae_title,
            AYMR.strings.achievement_secret_tatakae_desc,
        )
        "secret_lelouch" -> text(
            AYMR.strings.achievement_secret_all_according_to_plan_title,
            AYMR.strings.achievement_secret_all_according_to_plan_desc,
        )
        "secret_saitama" -> text(
            AYMR.strings.achievement_secret_average_enjoyer_title,
            AYMR.strings.achievement_secret_average_enjoyer_desc,
        )
        "secret_jojo" -> text(
            AYMR.strings.achievement_secret_it_was_me_dio_title,
            AYMR.strings.achievement_secret_it_was_me_dio_desc,
        )
        "secret_onepiece" -> text(
            AYMR.strings.achievement_secret_king_of_pirates_title,
            AYMR.strings.achievement_secret_king_of_pirates_desc,
        )
        "secret_goku" -> text(
            AYMR.strings.achievement_secret_not_even_my_final_form_title,
            AYMR.strings.achievement_secret_not_even_my_final_form_desc,
        )

        else -> AchievementTextResourceRefs(
            title = null,
            description = null,
        )
    }
}

private fun text(
    title: StringResource,
    description: StringResource,
): AchievementTextResourceRefs {
    return AchievementTextResourceRefs(
        title = title,
        description = description,
    )
}

private fun mangaReadResources(chapters: Int): AchievementTextResourceRefs {
    return when (chapters) {
        10 -> text(
            AYMR.strings.achievement_read_10_chapters_title,
            AYMR.strings.achievement_read_10_chapters_desc,
        )
        50 -> text(
            AYMR.strings.achievement_manga_reader_title,
            AYMR.strings.achievement_manga_reader_desc,
        )
        100 -> text(
            AYMR.strings.achievement_read_100_chapters_title,
            AYMR.strings.achievement_read_100_chapters_desc,
        )
        500 -> text(
            AYMR.strings.achievement_manga_fan_title,
            AYMR.strings.achievement_manga_fan_desc,
        )
        1000 -> text(
            AYMR.strings.achievement_manga_legend_title,
            AYMR.strings.achievement_manga_legend_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}

private fun mangaCompletionResources(count: Int): AchievementTextResourceRefs {
    return when (count) {
        1 -> text(
            AYMR.strings.achievement_manga_first_finish_title,
            AYMR.strings.achievement_manga_first_finish_desc,
        )
        10 -> text(
            AYMR.strings.achievement_manga_story_collector_title,
            AYMR.strings.achievement_manga_story_collector_desc,
        )
        50 -> text(
            AYMR.strings.achievement_manga_finale_seeker_title,
            AYMR.strings.achievement_manga_finale_seeker_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}

private fun novelReadResources(chapters: Int): AchievementTextResourceRefs {
    return when (chapters) {
        10 -> text(
            AYMR.strings.achievement_read_10_novel_chapters_title,
            AYMR.strings.achievement_read_10_novel_chapters_desc,
        )
        50 -> text(
            AYMR.strings.achievement_read_50_novel_chapters_title,
            AYMR.strings.achievement_read_50_novel_chapters_desc,
        )
        100 -> text(
            AYMR.strings.achievement_read_100_novel_chapters_title,
            AYMR.strings.achievement_read_100_novel_chapters_desc,
        )
        500 -> text(
            AYMR.strings.achievement_read_500_novel_chapters_title,
            AYMR.strings.achievement_read_500_novel_chapters_desc,
        )
        1000 -> text(
            AYMR.strings.achievement_read_1000_novel_chapters_title,
            AYMR.strings.achievement_read_1000_novel_chapters_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}

private fun novelCompletionResources(count: Int): AchievementTextResourceRefs {
    return when (count) {
        1 -> text(
            AYMR.strings.achievement_complete_1_novel_title,
            AYMR.strings.achievement_complete_1_novel_desc,
        )
        10 -> text(
            AYMR.strings.achievement_complete_10_novel_title,
            AYMR.strings.achievement_complete_10_novel_desc,
        )
        50 -> text(
            AYMR.strings.achievement_complete_50_novel_title,
            AYMR.strings.achievement_complete_50_novel_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}

private fun animeWatchResources(episodes: Int): AchievementTextResourceRefs {
    return when (episodes) {
        10 -> text(
            AYMR.strings.achievement_anime_marathoner_title,
            AYMR.strings.achievement_anime_marathoner_desc,
        )
        50 -> text(
            AYMR.strings.achievement_anime_binger_title,
            AYMR.strings.achievement_anime_binger_desc,
        )
        100 -> text(
            AYMR.strings.achievement_watch_100_episodes_title,
            AYMR.strings.achievement_watch_100_episodes_desc,
        )
        500 -> text(
            AYMR.strings.achievement_anime_addict_title,
            AYMR.strings.achievement_anime_addict_desc,
        )
        1000 -> text(
            AYMR.strings.achievement_anime_legend_title,
            AYMR.strings.achievement_anime_legend_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}

private fun animeCompletionResources(count: Int): AchievementTextResourceRefs {
    return when (count) {
        1 -> text(
            AYMR.strings.achievement_anime_first_finish_title,
            AYMR.strings.achievement_anime_first_finish_desc,
        )
        10 -> text(
            AYMR.strings.achievement_anime_story_seeker_title,
            AYMR.strings.achievement_anime_story_seeker_desc,
        )
        50 -> text(
            AYMR.strings.achievement_anime_season_conqueror_title,
            AYMR.strings.achievement_anime_season_conqueror_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}

private fun genreResources(count: Int): AchievementTextResourceRefs {
    return when (count) {
        5 -> text(
            AYMR.strings.achievement_genre_explorer_title,
            AYMR.strings.achievement_genre_explorer_desc,
        )
        10 -> text(
            AYMR.strings.achievement_genre_explorer_complete_title,
            AYMR.strings.achievement_genre_explorer_complete_desc,
        )
        20 -> text(
            AYMR.strings.achievement_genre_god_title,
            AYMR.strings.achievement_genre_god_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}

private fun streakResources(days: Int): AchievementTextResourceRefs {
    return when (days) {
        7 -> text(
            AYMR.strings.achievement_week_warrior_title,
            AYMR.strings.achievement_week_warrior_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}

private fun libraryResources(count: Int): AchievementTextResourceRefs {
    return when (count) {
        100 -> text(
            AYMR.strings.achievement_library_collector_title,
            AYMR.strings.achievement_library_collector_desc,
        )
        500 -> text(
            AYMR.strings.achievement_library_keeper_title,
            AYMR.strings.achievement_library_keeper_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}

private fun timeResources(achievementId: String): AchievementTextResourceRefs {
    return when (achievementId) {
        "night_owl" -> text(
            AYMR.strings.achievement_night_owl_title,
            AYMR.strings.achievement_night_owl_desc,
        )
        "early_bird" -> text(
            AYMR.strings.achievement_early_bird_title,
            AYMR.strings.achievement_early_bird_desc,
        )
        "marathon_reader" -> text(
            AYMR.strings.achievement_marathon_reader_title,
            AYMR.strings.achievement_marathon_reader_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}

private fun featureResources(achievementId: String): AchievementTextResourceRefs {
    return when (achievementId) {
        "download_starter" -> text(
            AYMR.strings.achievement_download_starter_title,
            AYMR.strings.achievement_download_starter_desc,
        )
        "chapter_collector" -> text(
            AYMR.strings.achievement_chapter_collector_title,
            AYMR.strings.achievement_chapter_collector_desc,
        )
        "trophy_hunter" -> text(
            AYMR.strings.achievement_trophy_hunter_title,
            AYMR.strings.achievement_trophy_hunter_desc,
        )
        "search_user" -> text(
            AYMR.strings.achievement_search_user_title,
            AYMR.strings.achievement_search_user_desc,
        )
        "advanced_explorer" -> text(
            AYMR.strings.achievement_advanced_explorer_title,
            AYMR.strings.achievement_advanced_explorer_desc,
        )
        "filter_master" -> text(
            AYMR.strings.achievement_filter_master_title,
            AYMR.strings.achievement_filter_master_desc,
        )
        "backup_master" -> text(
            AYMR.strings.achievement_backup_master_title,
            AYMR.strings.achievement_backup_master_desc,
        )
        "settings_explorer" -> text(
            AYMR.strings.achievement_settings_explorer_title,
            AYMR.strings.achievement_settings_explorer_desc,
        )
        "stats_viewer" -> text(
            AYMR.strings.achievement_stats_viewer_title,
            AYMR.strings.achievement_stats_viewer_desc,
        )
        "theme_changer" -> text(
            AYMR.strings.achievement_theme_changer_title,
            AYMR.strings.achievement_theme_changer_desc,
        )
        "persistent_clicker" -> text(
            AYMR.strings.achievement_persistent_clicker_title,
            AYMR.strings.achievement_persistent_clicker_desc,
        )
        "secret_hall_unlocked" -> text(
            AYMR.strings.achievement_secret_hall_unlocked_title,
            AYMR.strings.achievement_secret_hall_unlocked_desc,
        )
        else -> AchievementTextResourceRefs(null, null)
    }
}
