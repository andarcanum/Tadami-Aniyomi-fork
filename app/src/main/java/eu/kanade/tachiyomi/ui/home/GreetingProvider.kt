package eu.kanade.tachiyomi.ui.home

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR
import java.util.Calendar
import kotlin.math.max

object GreetingProvider {

    data class GreetingContext(
        val nowMillis: Long,
        val hourOfDay: Int,
        val dayOfWeek: Int,
        val lastOpenedTime: Long,
        val achievementCount: Int = 0,
        val episodesWatched: Int = 0,
        val librarySize: Int = 0,
        val currentStreak: Int = 0,
        val isFirstTime: Boolean = false,
        val totalLaunches: Long = 0L,
    ) {
        val daysSinceLastOpen: Int
            get() {
                if (lastOpenedTime <= 0L) return Int.MAX_VALUE
                val elapsed = max(0L, nowMillis - lastOpenedTime)
                return (elapsed / DAY_MS).toInt()
            }
    }

    data class GreetingSelection(
        val greeting: StringResource,
        val greetingId: String,
        val scenarioId: String,
    )

    private data class GreetingCandidate(
        val id: String,
        val value: StringResource,
    )

    private data class GreetingScenario(
        val id: String,
        val weight: Int,
        val candidates: List<GreetingCandidate>,
    )

    private const val DAY_MS = 24L * 60 * 60 * 1000L
    private const val ABSENCE_MID_DAYS = 3
    private const val ABSENCE_LONG_DAYS = 7
    private const val FREQUENT_USER_MIN_LAUNCHES = 25L
    private const val RECENT_SCENARIO_AVOID_COUNT = 2

    private fun candidate(id: String, value: StringResource) = GreetingCandidate(id = id, value = value)

    private val generalGreetings = listOf(
        candidate("ready", AYMR.strings.aurora_greeting_ready),
        candidate("whats_next", AYMR.strings.aurora_greeting_whats_next),
        candidate("dive_in", AYMR.strings.aurora_greeting_dive_in),
        candidate("binge_time", AYMR.strings.aurora_greeting_binge_time),
        candidate("queue_awaits", AYMR.strings.aurora_greeting_queue_awaits),
        candidate("got_minute", AYMR.strings.aurora_greeting_got_minute),
        candidate("pick_good", AYMR.strings.aurora_greeting_pick_good),
        candidate("new_episodes", AYMR.strings.aurora_greeting_new_episodes),
        candidate("back_for_more", AYMR.strings.aurora_greeting_back_for_more),
        candidate("nani", AYMR.strings.aurora_greeting_nani),
        candidate("yare", AYMR.strings.aurora_greeting_yare),
        candidate("lets_go", AYMR.strings.aurora_greeting_lets_go),
        candidate("anime_time", AYMR.strings.aurora_greeting_anime_time),
        candidate("main_character", AYMR.strings.aurora_greeting_main_character),
        candidate("manga_scroll", AYMR.strings.aurora_greeting_manga_scroll),
        candidate("manhwa_system", AYMR.strings.aurora_greeting_manhwa_system),
        candidate("manga_black_and_white", AYMR.strings.aurora_greeting_manga_black_and_white),
        candidate("novel_wall_of_text", AYMR.strings.aurora_greeting_novel_wall_of_text),
        candidate("manga_left_to_right", AYMR.strings.aurora_greeting_manga_left_to_right),
        candidate("trope_tsundere", AYMR.strings.aurora_greeting_trope_tsundere),
        candidate("trope_yandere", AYMR.strings.aurora_greeting_trope_yandere),
        candidate("trope_chuunibyou", AYMR.strings.aurora_greeting_trope_chuunibyou),
        candidate("trope_imouto", AYMR.strings.aurora_greeting_trope_imouto),
        candidate("trope_maid", AYMR.strings.aurora_greeting_trope_maid),
        candidate("general_gacha", AYMR.strings.aurora_greeting_general_gacha),
        candidate("general_plot_armor", AYMR.strings.aurora_greeting_general_plot_armor),
        candidate("general_bankai", AYMR.strings.aurora_greeting_general_bankai),
        candidate("general_titan", AYMR.strings.aurora_greeting_general_titan),
        candidate("general_isekai_truck", AYMR.strings.aurora_greeting_general_isekai_truck),
        candidate("general_susume", AYMR.strings.aurora_greeting_general_susume),
        candidate("general_magic_girl", AYMR.strings.aurora_greeting_general_magic_girl),
        candidate("general_mecha", AYMR.strings.aurora_greeting_general_mecha),
        candidate("general_power_level", AYMR.strings.aurora_greeting_general_power_level),
        candidate("general_cultured", AYMR.strings.aurora_greeting_general_cultured),
    )

    private val morningGreetings = listOf(
        candidate("morning", AYMR.strings.aurora_greeting_morning),
        candidate("morning_rise", AYMR.strings.aurora_greeting_morning_rise),
        candidate("morning_binge", AYMR.strings.aurora_greeting_morning_binge),
        candidate("morning_coffee", AYMR.strings.aurora_greeting_morning_coffee),
        candidate("morning_early_bird", AYMR.strings.aurora_greeting_morning_early_bird),
        candidate("morning_fresh", AYMR.strings.aurora_greeting_morning_fresh),
        candidate("morning_energetic", AYMR.strings.aurora_greeting_morning_energetic),
        candidate("morning_toast", AYMR.strings.aurora_greeting_morning_toast),
        candidate("morning_alarm", AYMR.strings.aurora_greeting_morning_alarm),
        candidate("morning_isekai", AYMR.strings.aurora_greeting_morning_isekai),
    )

    private val afternoonGreetings = listOf(
        candidate("afternoon", AYMR.strings.aurora_greeting_afternoon),
        candidate("afternoon_lunch", AYMR.strings.aurora_greeting_afternoon_lunch),
        candidate("afternoon_ara", AYMR.strings.aurora_greeting_afternoon_ara),
        candidate("afternoon_sugoi", AYMR.strings.aurora_greeting_afternoon_sugoi),
        candidate("afternoon_break", AYMR.strings.aurora_greeting_afternoon_break),
        candidate("afternoon_relax", AYMR.strings.aurora_greeting_afternoon_relax),
        candidate("afternoon_siesta", AYMR.strings.aurora_greeting_afternoon_siesta),
        candidate("afternoon_tea", AYMR.strings.aurora_greeting_afternoon_tea),
    )

    private val eveningGreetings = listOf(
        candidate("evening", AYMR.strings.aurora_greeting_evening),
        candidate("evening_vibes", AYMR.strings.aurora_greeting_evening_vibes),
        candidate("evening_chill", AYMR.strings.aurora_greeting_evening_chill),
        candidate("evening_cozy", AYMR.strings.aurora_greeting_evening_cozy),
        candidate("evening_relax_time", AYMR.strings.aurora_greeting_evening_relax_time),
        candidate("evening_sunset", AYMR.strings.aurora_greeting_evening_sunset),
        candidate("evening_after_work", AYMR.strings.aurora_greeting_evening_after_work),
        candidate("evening_boss_defeated", AYMR.strings.aurora_greeting_evening_boss_defeated),
        candidate("evening_comf", AYMR.strings.aurora_greeting_evening_comf),
        candidate("evening_dinner", AYMR.strings.aurora_greeting_evening_dinner),
    )

    private val nightGreetings = listOf(
        candidate("night_owl", AYMR.strings.aurora_greeting_night_owl),
        candidate("still_up", AYMR.strings.aurora_greeting_still_up),
        candidate("late_night", AYMR.strings.aurora_greeting_late_night),
        candidate("night_one_more", AYMR.strings.aurora_greeting_night_one_more),
        candidate("night_sleep", AYMR.strings.aurora_greeting_night_sleep),
        candidate("night_senpai", AYMR.strings.aurora_greeting_night_senpai),
        candidate("night_just_one", AYMR.strings.aurora_greeting_night_just_one),
        candidate("night_midnight", AYMR.strings.aurora_greeting_night_midnight),
        candidate("night_starry", AYMR.strings.aurora_greeting_night_starry),
        candidate("night_ninja", AYMR.strings.aurora_greeting_night_ninja),
        candidate("night_vampire", AYMR.strings.aurora_greeting_night_vampire),
        candidate("night_red_eyes", AYMR.strings.aurora_greeting_night_red_eyes),
        candidate("night_3am", AYMR.strings.aurora_greeting_night_3am),
    )

    private val firstTimeGreetings = listOf(
        candidate("welcome_family", AYMR.strings.aurora_greeting_welcome_family),
        candidate("first_time", AYMR.strings.aurora_greeting_first_time),
    )

    private val absenceLongGreetings = listOf(
        candidate("absence_long_time", AYMR.strings.aurora_greeting_long_time),
        candidate("absence_long_reunion", AYMR.strings.aurora_greeting_absence_long_reunion),
        candidate("absence_long_you_back", AYMR.strings.aurora_greeting_youre_back),
        candidate("absence_long_back_for_more", AYMR.strings.aurora_greeting_back_for_more),
        candidate("absence_long_whats_next", AYMR.strings.aurora_greeting_whats_next),
        candidate("absence_spider", AYMR.strings.aurora_greeting_absence_spider),
        candidate("absence_training_arc", AYMR.strings.aurora_greeting_absence_training_arc),
        candidate("absence_amnesia", AYMR.strings.aurora_greeting_absence_amnesia),
    )

    private val absenceMidGreetings = listOf(
        candidate("absence_mid_missed", AYMR.strings.aurora_greeting_missed_you),
        candidate("absence_mid_return", AYMR.strings.aurora_greeting_absence_mid_return),
        candidate("absence_mid_back_more", AYMR.strings.aurora_greeting_absence_mid_back_for_more),
        candidate("absence_mid_you_back", AYMR.strings.aurora_greeting_youre_back),
        candidate("absence_mid_ready", AYMR.strings.aurora_greeting_ready),
        candidate("absence_spider", AYMR.strings.aurora_greeting_absence_spider),
        candidate("absence_training_arc", AYMR.strings.aurora_greeting_absence_training_arc),
        candidate("absence_amnesia", AYMR.strings.aurora_greeting_absence_amnesia),
    )

    private val frequentUserGreetings = listOf(
        candidate("frequent_hello_again", AYMR.strings.aurora_greeting_frequent_hello_again),
        candidate("frequent_live_here", AYMR.strings.aurora_greeting_frequent_live_here),
        candidate("frequent_quick_return", AYMR.strings.aurora_greeting_frequent_quick_return),
        candidate("frequent_back_for_more", AYMR.strings.aurora_greeting_back_for_more),
        candidate("frequent_queue_awaits", AYMR.strings.aurora_greeting_queue_awaits),
        candidate("frequent_touch_grass", AYMR.strings.aurora_greeting_frequent_touch_grass),
        candidate("frequent_coffee_iv", AYMR.strings.aurora_greeting_frequent_coffee_iv),
        candidate("frequent_resident", AYMR.strings.aurora_greeting_frequent_resident),
    )

    private val saturdayGreetings = listOf(
        candidate("weekend_time", AYMR.strings.aurora_greeting_weekend_time),
        candidate("saturday_perfect", AYMR.strings.aurora_greeting_saturday_perfect),
        candidate("weekend_relax", AYMR.strings.aurora_greeting_weekend_relax),
        candidate("weekend_pajamas", AYMR.strings.aurora_greeting_weekend_pajamas),
        candidate("weekend_marathon_prep", AYMR.strings.aurora_greeting_weekend_marathon_prep),
        candidate("weekend_no_regrets", AYMR.strings.aurora_greeting_weekend_no_regrets),
    )

    private val sundayGreetings = listOf(
        candidate("weekend_time", AYMR.strings.aurora_greeting_weekend_time),
        candidate("sunday_marathon", AYMR.strings.aurora_greeting_sunday_marathon),
        candidate("weekend_relax", AYMR.strings.aurora_greeting_weekend_relax),
        candidate("weekend_pajamas", AYMR.strings.aurora_greeting_weekend_pajamas),
        candidate("weekend_marathon_prep", AYMR.strings.aurora_greeting_weekend_marathon_prep),
        candidate("weekend_no_regrets", AYMR.strings.aurora_greeting_weekend_no_regrets),
    )

    private val weekendFallbackGreetings = listOf(
        candidate("weekend_time", AYMR.strings.aurora_greeting_weekend_time),
        candidate("weekend_relax", AYMR.strings.aurora_greeting_weekend_relax),
        candidate("weekend_marathon_prep", AYMR.strings.aurora_greeting_weekend_marathon_prep),
        candidate("weekend_no_regrets", AYMR.strings.aurora_greeting_weekend_no_regrets),
    )

    private val weekdayMondayGreetings = listOf(
        candidate("weekday_monday", AYMR.strings.aurora_greeting_weekday_monday),
        candidate("weekday_generic", AYMR.strings.aurora_greeting_weekday_generic),
        candidate("weekday_monday_lets_go", AYMR.strings.aurora_greeting_lets_go),
        candidate("weekday_monday_pick_good", AYMR.strings.aurora_greeting_pick_good),
        candidate("weekday_survival", AYMR.strings.aurora_greeting_weekday_survival),
    )
    private val weekdayTuesdayGreetings = listOf(
        candidate("weekday_tuesday", AYMR.strings.aurora_greeting_weekday_tuesday),
        candidate("weekday_generic", AYMR.strings.aurora_greeting_weekday_generic),
        candidate("weekday_tuesday_got_minute", AYMR.strings.aurora_greeting_got_minute),
        candidate("weekday_tuesday_queue", AYMR.strings.aurora_greeting_queue_awaits),
        candidate("weekday_motivation", AYMR.strings.aurora_greeting_weekday_motivation),
    )
    private val weekdayWednesdayGreetings = listOf(
        candidate("weekday_wednesday", AYMR.strings.aurora_greeting_weekday_wednesday),
        candidate("weekday_generic", AYMR.strings.aurora_greeting_weekday_generic),
        candidate("weekday_wednesday_ready", AYMR.strings.aurora_greeting_ready),
        candidate("weekday_wednesday_whats_next", AYMR.strings.aurora_greeting_whats_next),
        candidate("weekday_survival", AYMR.strings.aurora_greeting_weekday_survival),
    )
    private val weekdayThursdayGreetings = listOf(
        candidate("weekday_thursday", AYMR.strings.aurora_greeting_weekday_thursday),
        candidate("weekday_generic", AYMR.strings.aurora_greeting_weekday_generic),
        candidate("weekday_thursday_binge", AYMR.strings.aurora_greeting_binge_time),
        candidate("weekday_thursday_dive_in", AYMR.strings.aurora_greeting_dive_in),
        candidate("weekday_motivation", AYMR.strings.aurora_greeting_weekday_motivation),
    )
    private val weekdayFridayGreetings = listOf(
        candidate("weekday_friday", AYMR.strings.aurora_greeting_weekday_friday),
        candidate("weekday_generic", AYMR.strings.aurora_greeting_weekday_generic),
        candidate("weekday_friday_anime_time", AYMR.strings.aurora_greeting_anime_time),
        candidate("weekday_friday_main_character", AYMR.strings.aurora_greeting_main_character),
        candidate("weekday_friday_new_episodes", AYMR.strings.aurora_greeting_new_episodes),
        candidate("weekday_survival", AYMR.strings.aurora_greeting_weekday_survival),
    )
    private val weekdayGreetingsByDay = mapOf(
        Calendar.MONDAY to weekdayMondayGreetings,
        Calendar.TUESDAY to weekdayTuesdayGreetings,
        Calendar.WEDNESDAY to weekdayWednesdayGreetings,
        Calendar.THURSDAY to weekdayThursdayGreetings,
        Calendar.FRIDAY to weekdayFridayGreetings,
    )
    private val weekdayFallbackGreetings = listOf(
        candidate("weekday_generic", AYMR.strings.aurora_greeting_weekday_generic),
        candidate("weekday_fallback_ready", AYMR.strings.aurora_greeting_ready),
        candidate("weekday_fallback_whats_next", AYMR.strings.aurora_greeting_whats_next),
        candidate("weekday_survival", AYMR.strings.aurora_greeting_weekday_survival),
        candidate("weekday_motivation", AYMR.strings.aurora_greeting_weekday_motivation),
    )

    private val streakGreetings = listOf(
        candidate("streak_continues", AYMR.strings.aurora_greeting_streak_continues),
        candidate("streak_7_days", AYMR.strings.aurora_greeting_streak_7_days),
        candidate("streak_loyal", AYMR.strings.aurora_greeting_streak_loyal),
        candidate("streak_unstoppable", AYMR.strings.aurora_greeting_streak_unstoppable),
        candidate("streak_daily", AYMR.strings.aurora_greeting_streak_daily),
        candidate("streak_dedication", AYMR.strings.aurora_greeting_streak_dedication),
    )

    private val achievementGreetings = listOf(
        candidate("achievement_hunter", AYMR.strings.aurora_greeting_achievement_hunter),
        candidate("achievement_10", AYMR.strings.aurora_greeting_achievement_10),
        candidate("achievement_collector", AYMR.strings.aurora_greeting_achievement_collector),
        candidate("achievement_master", AYMR.strings.aurora_greeting_achievement_master),
        candidate("achievement_50", AYMR.strings.aurora_greeting_achievement_50),
        candidate("achievement_legendary", AYMR.strings.aurora_greeting_achievement_legendary),
    )

    private val statsGreetings = listOf(
        candidate("stats_100_eps", AYMR.strings.aurora_greeting_stats_100_eps),
        candidate("stats_marathoner", AYMR.strings.aurora_greeting_stats_marathoner),
        candidate("stats_500_eps", AYMR.strings.aurora_greeting_stats_500_eps),
        candidate("stats_beginner_critic", AYMR.strings.aurora_greeting_stats_beginner_critic),
        candidate("stats_expert", AYMR.strings.aurora_greeting_stats_expert),
        candidate("stats_1000_eps", AYMR.strings.aurora_greeting_stats_1000_eps),
        candidate("stats_pro_viewer", AYMR.strings.aurora_greeting_stats_pro_viewer),
        candidate("stats_impressive", AYMR.strings.aurora_greeting_stats_impressive),
        candidate("stats_404_eps", AYMR.strings.aurora_greeting_stats_404_eps),
    )

    private val libraryGreetings = listOf(
        candidate("library_impressive", AYMR.strings.aurora_greeting_library_impressive),
        candidate("library_50", AYMR.strings.aurora_greeting_library_50),
        candidate("library_growing", AYMR.strings.aurora_greeting_library_growing),
        candidate("library_true_collector", AYMR.strings.aurora_greeting_library_true_collector),
        candidate("library_100", AYMR.strings.aurora_greeting_library_100),
        candidate("library_hoarder", AYMR.strings.aurora_greeting_library_hoarder),
        candidate("library_black_hole", AYMR.strings.aurora_greeting_library_black_hole),
    )

    fun getGreeting(
        lastOpenedTime: Long,
        achievementCount: Int = 0,
        episodesWatched: Int = 0,
        librarySize: Int = 0,
        currentStreak: Int = 0,
        isFirstTime: Boolean = false,
    ): StringResource {
        return selectGreeting(
            lastOpenedTime = lastOpenedTime,
            achievementCount = achievementCount,
            episodesWatched = episodesWatched,
            librarySize = librarySize,
            currentStreak = currentStreak,
            isFirstTime = isFirstTime,
        ).greeting
    }

    fun selectGreeting(
        lastOpenedTime: Long,
        achievementCount: Int = 0,
        episodesWatched: Int = 0,
        librarySize: Int = 0,
        currentStreak: Int = 0,
        isFirstTime: Boolean = false,
        totalLaunches: Long = 0L,
        recentGreetingIds: List<String> = emptyList(),
        recentScenarioIds: List<String> = emptyList(),
        blockedGreetingIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
    ): GreetingSelection {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val context = GreetingContext(
            nowMillis = nowMillis,
            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
            lastOpenedTime = lastOpenedTime,
            achievementCount = achievementCount,
            episodesWatched = episodesWatched,
            librarySize = librarySize,
            currentStreak = currentStreak,
            isFirstTime = isFirstTime,
            totalLaunches = totalLaunches,
        )

        return selectGreetingForContext(
            context = context,
            recentGreetingIds = recentGreetingIds,
            recentScenarioIds = recentScenarioIds,
            blockedGreetingIds = blockedGreetingIds,
        )
    }

    internal fun selectGreetingForContext(
        context: GreetingContext,
        recentGreetingIds: List<String> = emptyList(),
        recentScenarioIds: List<String> = emptyList(),
        blockedGreetingIds: Set<String> = emptySet(),
    ): GreetingSelection {
        if (context.isFirstTime) {
            return chooseGreeting(
                scenarioId = "first_time",
                candidates = firstTimeGreetings,
                context = context,
                recentGreetingIds = recentGreetingIds,
                blockedGreetingIds = blockedGreetingIds,
            )
        }

        if (context.daysSinceLastOpen >= ABSENCE_LONG_DAYS) {
            return chooseGreeting(
                scenarioId = "absence_long",
                candidates = absenceLongGreetings,
                context = context,
                recentGreetingIds = recentGreetingIds,
                blockedGreetingIds = blockedGreetingIds,
            )
        }

        if (context.daysSinceLastOpen >= ABSENCE_MID_DAYS) {
            return chooseGreeting(
                scenarioId = "absence_mid",
                candidates = absenceMidGreetings,
                context = context,
                recentGreetingIds = recentGreetingIds,
                blockedGreetingIds = blockedGreetingIds,
            )
        }

        val scenarios = mutableListOf<GreetingScenario>()

        val milestoneCandidates = getMilestoneCandidates(
            currentStreak = context.currentStreak,
            achievementCount = context.achievementCount,
            episodesWatched = context.episodesWatched,
            librarySize = context.librarySize,
        )
        if (milestoneCandidates.isNotEmpty()) {
            scenarios += GreetingScenario(
                id = "milestone",
                weight = 20,
                candidates = milestoneCandidates,
            )
        }

        if (isFrequentUser(context)) {
            scenarios += GreetingScenario(
                id = "frequent_tease",
                weight = 14,
                candidates = frequentUserGreetings,
            )
        }

        if (isWeekend(context.dayOfWeek)) {
            scenarios += GreetingScenario(
                id = "weekend",
                weight = 25,
                candidates = getWeekendCandidates(context.dayOfWeek),
            )
        } else {
            scenarios += GreetingScenario(
                id = "weekday",
                weight = 18,
                candidates = weekdayGreetingsByDay[context.dayOfWeek] ?: weekdayFallbackGreetings,
            )
        }

        scenarios += GreetingScenario(
            id = "time_of_day",
            weight = 22,
            candidates = getTimeOfDayCandidates(context.hourOfDay),
        )
        scenarios += GreetingScenario(
            id = "general",
            weight = 10,
            candidates = generalGreetings,
        )

        val filteredScenarios = avoidRecentScenarioIfPossible(scenarios, recentScenarioIds)
        val selectedScenario = weightedPick(
            items = filteredScenarios,
            seed = createSeed(context, "scenario"),
            weight = { it.weight },
        )

        val selectedCandidate = pickCandidate(
            candidates = selectedScenario.candidates,
            recentGreetingIds = recentGreetingIds,
            blockedGreetingIds = blockedGreetingIds,
            seed = createSeed(context, selectedScenario.id),
        )

        if (selectedCandidate == null) {
            return pickGeneralFallback(
                context = context,
                recentGreetingIds = recentGreetingIds,
                blockedGreetingIds = blockedGreetingIds,
            )
        }

        return GreetingSelection(
            greeting = selectedCandidate.value,
            greetingId = selectedCandidate.id,
            scenarioId = selectedScenario.id,
        )
    }

    private fun chooseGreeting(
        scenarioId: String,
        candidates: List<GreetingCandidate>,
        context: GreetingContext,
        recentGreetingIds: List<String>,
        blockedGreetingIds: Set<String>,
    ): GreetingSelection {
        val candidate = pickCandidate(
            candidates = candidates,
            recentGreetingIds = recentGreetingIds,
            blockedGreetingIds = blockedGreetingIds,
            seed = createSeed(context, scenarioId),
        ) ?: return pickGeneralFallback(
            context = context,
            recentGreetingIds = recentGreetingIds,
            blockedGreetingIds = blockedGreetingIds,
        )
        return GreetingSelection(
            greeting = candidate.value,
            greetingId = candidate.id,
            scenarioId = scenarioId,
        )
    }

    private fun avoidRecentScenarioIfPossible(
        scenarios: List<GreetingScenario>,
        recentScenarioIds: List<String>,
    ): List<GreetingScenario> {
        if (recentScenarioIds.isEmpty()) return scenarios
        if (scenarios.size <= 1) return scenarios
        val recentToAvoid = recentScenarioIds.take(RECENT_SCENARIO_AVOID_COUNT).toSet()
        val alternatives = scenarios.filterNot { it.id in recentToAvoid }
        return when {
            alternatives.isNotEmpty() -> alternatives
            else -> {
                val fallback = scenarios.filterNot { it.id == recentScenarioIds.first() }
                fallback.ifEmpty { scenarios }
            }
        }
    }

    private fun isWeekend(dayOfWeek: Int): Boolean {
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }

    private fun getWeekendCandidates(dayOfWeek: Int): List<GreetingCandidate> {
        return when (dayOfWeek) {
            Calendar.SATURDAY -> saturdayGreetings
            Calendar.SUNDAY -> sundayGreetings
            else -> weekendFallbackGreetings
        }
    }

    private fun isFrequentUser(context: GreetingContext): Boolean {
        return context.totalLaunches >= FREQUENT_USER_MIN_LAUNCHES && context.daysSinceLastOpen <= 1
    }

    private fun getTimeOfDayCandidates(hourOfDay: Int): List<GreetingCandidate> {
        return when (hourOfDay) {
            in 5..11 -> morningGreetings
            in 12..16 -> afternoonGreetings
            in 17..21 -> eveningGreetings
            else -> nightGreetings
        }
    }

    private fun getMilestoneCandidates(
        currentStreak: Int,
        achievementCount: Int,
        episodesWatched: Int,
        librarySize: Int,
    ): List<GreetingCandidate> {
        checkStreakMilestone(currentStreak)?.let { return it }
        checkAchievementMilestone(achievementCount)?.let { return it }
        checkStatsMilestone(episodesWatched)?.let { return it }
        checkLibraryMilestone(librarySize)?.let { return it }
        return emptyList()
    }

    private fun checkStreakMilestone(streak: Int): List<GreetingCandidate>? {
        return when {
            streak >= 100 -> listOf(
                candidate("streak_100", AYMR.strings.aurora_greeting_streak_100),
                candidate("streak_dedication", AYMR.strings.aurora_greeting_streak_dedication),
                candidate("streak_unstoppable", AYMR.strings.aurora_greeting_streak_unstoppable),
            )
            streak >= 30 -> listOf(
                candidate("streak_30", AYMR.strings.aurora_greeting_streak_30),
                candidate("streak_dedication", AYMR.strings.aurora_greeting_streak_dedication),
                candidate("streak_unstoppable", AYMR.strings.aurora_greeting_streak_unstoppable),
            )
            streak >= 14 -> listOf(
                candidate("streak_unstoppable", AYMR.strings.aurora_greeting_streak_unstoppable),
                candidate("streak_loyal", AYMR.strings.aurora_greeting_streak_loyal),
                candidate("streak_daily", AYMR.strings.aurora_greeting_streak_daily),
                candidate("streak_dedication", AYMR.strings.aurora_greeting_streak_dedication),
            )
            streak >= 7 -> listOf(
                candidate("streak_7_days", AYMR.strings.aurora_greeting_streak_7_days),
                candidate("streak_continues", AYMR.strings.aurora_greeting_streak_continues),
            )
            streak >= 3 -> streakGreetings
            else -> null
        }
    }

    private fun checkAchievementMilestone(count: Int): List<GreetingCandidate>? {
        return when {
            count >= 100 -> listOf(
                candidate("achievement_legendary", AYMR.strings.aurora_greeting_achievement_legendary),
            )
            count >= 50 -> listOf(
                candidate("achievement_50", AYMR.strings.aurora_greeting_achievement_50),
                candidate("achievement_master", AYMR.strings.aurora_greeting_achievement_master),
            )
            count >= 10 -> achievementGreetings
            else -> null
        }
    }

    private fun checkStatsMilestone(episodes: Int): List<GreetingCandidate>? {
        return when {
            episodes >= 1000 -> listOf(
                candidate("stats_1000_eps", AYMR.strings.aurora_greeting_stats_1000_eps),
                candidate("stats_pro_viewer", AYMR.strings.aurora_greeting_stats_pro_viewer),
            )
            episodes >= 500 -> listOf(
                candidate("stats_500_eps", AYMR.strings.aurora_greeting_stats_500_eps),
                candidate("stats_marathoner", AYMR.strings.aurora_greeting_stats_marathoner),
            )
            episodes >= 404 -> listOf(
                candidate("stats_404_eps", AYMR.strings.aurora_greeting_stats_404_eps),
                candidate("stats_marathoner", AYMR.strings.aurora_greeting_stats_marathoner),
            )
            episodes >= 100 -> statsGreetings
            else -> null
        }
    }

    private fun checkLibraryMilestone(size: Int): List<GreetingCandidate>? {
        return when {
            size >= 100 -> listOf(
                candidate("library_100", AYMR.strings.aurora_greeting_library_100),
                candidate("library_true_collector", AYMR.strings.aurora_greeting_library_true_collector),
                candidate("library_black_hole", AYMR.strings.aurora_greeting_library_black_hole),
            )
            size >= 50 -> listOf(
                candidate("library_50", AYMR.strings.aurora_greeting_library_50),
                candidate("library_impressive", AYMR.strings.aurora_greeting_library_impressive),
                candidate("library_hoarder", AYMR.strings.aurora_greeting_library_hoarder),
            )
            size >= 10 -> libraryGreetings
            else -> null
        }
    }

    private fun pickCandidate(
        candidates: List<GreetingCandidate>,
        recentGreetingIds: List<String>,
        blockedGreetingIds: Set<String>,
        seed: Long,
    ): GreetingCandidate? {
        if (candidates.isEmpty()) return null

        val recentSet = recentGreetingIds.toSet()
        val available = candidates.filterNot { it.id in recentSet || it.id in blockedGreetingIds }
        if (available.isNotEmpty()) return deterministicPick(available, seed)

        val relaxedByRecent = candidates.filterNot { it.id in blockedGreetingIds }
        if (relaxedByRecent.isNotEmpty()) return deterministicPick(relaxedByRecent, seed)

        return null
    }

    private fun pickGeneralFallback(
        context: GreetingContext,
        recentGreetingIds: List<String>,
        blockedGreetingIds: Set<String>,
    ): GreetingSelection {
        val seed = createSeed(context, "general_fallback")
        val candidate = pickCandidate(
            candidates = generalGreetings,
            recentGreetingIds = recentGreetingIds,
            blockedGreetingIds = blockedGreetingIds,
            seed = seed,
        ) ?: deterministicPick(generalGreetings, seed)
        return GreetingSelection(
            greeting = candidate.value,
            greetingId = candidate.id,
            scenarioId = "general_fallback",
        )
    }

    private fun <T> weightedPick(
        items: List<T>,
        seed: Long,
        weight: (T) -> Int,
    ): T {
        require(items.isNotEmpty()) { "Cannot pick from empty list" }
        val totalWeight = items.sumOf { max(0, weight(it)) }
        if (totalWeight <= 0) return deterministicPick(items, seed)

        val point = Math.floorMod(seed, totalWeight.toLong()).toInt()
        var cursor = 0
        for (item in items) {
            cursor += max(0, weight(item))
            if (point < cursor) return item
        }
        return items.last()
    }

    private fun <T> deterministicPick(items: List<T>, seed: Long): T {
        require(items.isNotEmpty()) { "Cannot pick from empty list" }
        val index = Math.floorMod(seed, items.size.toLong()).toInt()
        return items[index]
    }

    private fun createSeed(context: GreetingContext, salt: String): Long {
        var seed = context.nowMillis / (2 * 60 * 60 * 1000L)
        seed = seed * 31 + context.hourOfDay
        seed = seed * 31 + context.dayOfWeek
        seed = seed * 31 + context.daysSinceLastOpen
        seed = seed * 31 + context.totalLaunches
        seed = seed * 31 + context.currentStreak
        seed = seed * 31 + context.achievementCount
        seed = seed * 31 + context.episodesWatched
        seed = seed * 31 + context.librarySize
        seed = seed * 31 + salt.hashCode()
        return seed
    }
}
