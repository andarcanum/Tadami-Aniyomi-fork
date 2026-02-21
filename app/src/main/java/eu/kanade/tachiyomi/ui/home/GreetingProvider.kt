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
    )

    private val morningGreetings = listOf(
        candidate("morning", AYMR.strings.aurora_greeting_morning),
        candidate("morning_rise", AYMR.strings.aurora_greeting_morning_rise),
        candidate("morning_binge", AYMR.strings.aurora_greeting_morning_binge),
        candidate("morning_coffee", AYMR.strings.aurora_greeting_morning_coffee),
        candidate("morning_early_bird", AYMR.strings.aurora_greeting_morning_early_bird),
        candidate("morning_fresh", AYMR.strings.aurora_greeting_morning_fresh),
        candidate("morning_energetic", AYMR.strings.aurora_greeting_morning_energetic),
    )

    private val afternoonGreetings = listOf(
        candidate("afternoon", AYMR.strings.aurora_greeting_afternoon),
        candidate("afternoon_lunch", AYMR.strings.aurora_greeting_afternoon_lunch),
        candidate("afternoon_ara", AYMR.strings.aurora_greeting_afternoon_ara),
        candidate("afternoon_sugoi", AYMR.strings.aurora_greeting_afternoon_sugoi),
        candidate("afternoon_break", AYMR.strings.aurora_greeting_afternoon_break),
        candidate("afternoon_relax", AYMR.strings.aurora_greeting_afternoon_relax),
    )

    private val eveningGreetings = listOf(
        candidate("evening", AYMR.strings.aurora_greeting_evening),
        candidate("evening_vibes", AYMR.strings.aurora_greeting_evening_vibes),
        candidate("evening_chill", AYMR.strings.aurora_greeting_evening_chill),
        candidate("evening_cozy", AYMR.strings.aurora_greeting_evening_cozy),
        candidate("evening_relax_time", AYMR.strings.aurora_greeting_evening_relax_time),
        candidate("evening_sunset", AYMR.strings.aurora_greeting_evening_sunset),
        candidate("evening_after_work", AYMR.strings.aurora_greeting_evening_after_work),
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
    )

    private val firstTimeGreetings = listOf(
        candidate("welcome_family", AYMR.strings.aurora_greeting_welcome_family),
        candidate("first_time", AYMR.strings.aurora_greeting_first_time),
    )

    private val absenceLongGreetings = listOf(
        candidate("absence_long_time", AYMR.strings.aurora_greeting_long_time),
        candidate("absence_long_reunion", AYMR.strings.aurora_greeting_absence_long_reunion),
        candidate("absence_long_you_back", AYMR.strings.aurora_greeting_youre_back),
    )

    private val absenceMidGreetings = listOf(
        candidate("absence_mid_missed", AYMR.strings.aurora_greeting_missed_you),
        candidate("absence_mid_return", AYMR.strings.aurora_greeting_absence_mid_return),
        candidate("absence_mid_back_more", AYMR.strings.aurora_greeting_back_for_more),
    )

    private val frequentUserGreetings = listOf(
        candidate("frequent_hello_again", AYMR.strings.aurora_greeting_frequent_hello_again),
        candidate("frequent_live_here", AYMR.strings.aurora_greeting_frequent_live_here),
        candidate("frequent_quick_return", AYMR.strings.aurora_greeting_frequent_quick_return),
    )

    private val weekendGreetings = listOf(
        candidate("weekend_time", AYMR.strings.aurora_greeting_weekend_time),
        candidate("saturday_perfect", AYMR.strings.aurora_greeting_saturday_perfect),
        candidate("sunday_marathon", AYMR.strings.aurora_greeting_sunday_marathon),
        candidate("weekend_relax", AYMR.strings.aurora_greeting_weekend_relax),
    )

    private val weekdayGreetingsByDay = mapOf(
        Calendar.MONDAY to listOf(candidate("weekday_monday", AYMR.strings.aurora_greeting_weekday_monday)),
        Calendar.TUESDAY to listOf(candidate("weekday_tuesday", AYMR.strings.aurora_greeting_weekday_tuesday)),
        Calendar.WEDNESDAY to listOf(candidate("weekday_wednesday", AYMR.strings.aurora_greeting_weekday_wednesday)),
        Calendar.THURSDAY to listOf(candidate("weekday_thursday", AYMR.strings.aurora_greeting_weekday_thursday)),
        Calendar.FRIDAY to listOf(candidate("weekday_friday", AYMR.strings.aurora_greeting_weekday_friday)),
    )
    private val weekdayFallbackGreetings = listOf(candidate("weekday_generic", AYMR.strings.aurora_greeting_weekday_generic))

    private val streakGreetings = listOf(
        candidate("streak_continues", AYMR.strings.aurora_greeting_streak_continues),
        candidate("streak_7_days", AYMR.strings.aurora_greeting_streak_7_days),
        candidate("streak_loyal", AYMR.strings.aurora_greeting_streak_loyal),
        candidate("streak_unstoppable", AYMR.strings.aurora_greeting_streak_unstoppable),
        candidate("streak_daily", AYMR.strings.aurora_greeting_streak_daily),
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
    )

    private val libraryGreetings = listOf(
        candidate("library_impressive", AYMR.strings.aurora_greeting_library_impressive),
        candidate("library_50", AYMR.strings.aurora_greeting_library_50),
        candidate("library_growing", AYMR.strings.aurora_greeting_library_growing),
        candidate("library_true_collector", AYMR.strings.aurora_greeting_library_true_collector),
        candidate("library_100", AYMR.strings.aurora_greeting_library_100),
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
        )
    }

    internal fun selectGreetingForContext(
        context: GreetingContext,
        recentGreetingIds: List<String> = emptyList(),
        recentScenarioIds: List<String> = emptyList(),
    ): GreetingSelection {
        if (context.isFirstTime) {
            return chooseGreeting(
                scenarioId = "first_time",
                candidates = firstTimeGreetings,
                context = context,
                recentGreetingIds = recentGreetingIds,
            )
        }

        if (context.daysSinceLastOpen >= ABSENCE_LONG_DAYS) {
            return chooseGreeting(
                scenarioId = "absence_long",
                candidates = absenceLongGreetings,
                context = context,
                recentGreetingIds = recentGreetingIds,
            )
        }

        if (context.daysSinceLastOpen >= ABSENCE_MID_DAYS) {
            return chooseGreeting(
                scenarioId = "absence_mid",
                candidates = absenceMidGreetings,
                context = context,
                recentGreetingIds = recentGreetingIds,
            )
        }

        if (isFrequentUser(context)) {
            return chooseGreeting(
                scenarioId = "frequent_tease",
                candidates = frequentUserGreetings,
                context = context,
                recentGreetingIds = recentGreetingIds,
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

        if (isWeekend(context.dayOfWeek)) {
            scenarios += GreetingScenario(
                id = "weekend",
                weight = 25,
                candidates = weekendGreetings,
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
            seed = createSeed(context, selectedScenario.id),
        )

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
    ): GreetingSelection {
        val candidate = pickCandidate(
            candidates = candidates,
            recentGreetingIds = recentGreetingIds,
            seed = createSeed(context, scenarioId),
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
        val lastScenarioId = recentScenarioIds.firstOrNull() ?: return scenarios
        if (scenarios.size <= 1) return scenarios
        val alternatives = scenarios.filterNot { it.id == lastScenarioId }
        return alternatives.ifEmpty { scenarios }
    }

    private fun isWeekend(dayOfWeek: Int): Boolean {
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
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
            streak >= 14 -> listOf(
                candidate("streak_unstoppable", AYMR.strings.aurora_greeting_streak_unstoppable),
                candidate("streak_loyal", AYMR.strings.aurora_greeting_streak_loyal),
                candidate("streak_daily", AYMR.strings.aurora_greeting_streak_daily),
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
            count >= 100 -> listOf(candidate("achievement_legendary", AYMR.strings.aurora_greeting_achievement_legendary))
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
            episodes >= 100 -> statsGreetings
            else -> null
        }
    }

    private fun checkLibraryMilestone(size: Int): List<GreetingCandidate>? {
        return when {
            size >= 100 -> listOf(
                candidate("library_100", AYMR.strings.aurora_greeting_library_100),
                candidate("library_true_collector", AYMR.strings.aurora_greeting_library_true_collector),
            )
            size >= 50 -> listOf(
                candidate("library_50", AYMR.strings.aurora_greeting_library_50),
                candidate("library_impressive", AYMR.strings.aurora_greeting_library_impressive),
            )
            size >= 10 -> libraryGreetings
            else -> null
        }
    }

    private fun pickCandidate(
        candidates: List<GreetingCandidate>,
        recentGreetingIds: List<String>,
        seed: Long,
    ): GreetingCandidate {
        if (candidates.isEmpty()) {
            return candidate("welcome_back", AYMR.strings.aurora_welcome_back)
        }

        val recentSet = recentGreetingIds.toSet()
        val available = candidates.filterNot { it.id in recentSet }
        val pool = if (available.isNotEmpty()) available else candidates
        return deterministicPick(pool, seed)
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

