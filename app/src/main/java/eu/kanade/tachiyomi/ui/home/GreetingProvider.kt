package eu.kanade.tachiyomi.ui.home

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR
import java.util.Calendar
import kotlin.random.Random

object GreetingProvider {

    // Random greetings
    private val randomGreetings = listOf(
        AYMR.strings.aurora_greeting_ready,
        AYMR.strings.aurora_greeting_whats_next,
        AYMR.strings.aurora_greeting_dive_in,
        AYMR.strings.aurora_greeting_binge_time,
        AYMR.strings.aurora_greeting_queue_awaits,
        AYMR.strings.aurora_greeting_got_minute,
        AYMR.strings.aurora_greeting_pick_good,
        AYMR.strings.aurora_greeting_new_episodes,
        AYMR.strings.aurora_greeting_back_for_more,
        AYMR.strings.aurora_greeting_nani,
        AYMR.strings.aurora_greeting_yare,
        AYMR.strings.aurora_greeting_lets_go,
        AYMR.strings.aurora_greeting_anime_time,
        AYMR.strings.aurora_greeting_main_character,
    )

    // Morning greetings (5-11 AM)
    private val morningGreetings = listOf(
        AYMR.strings.aurora_greeting_morning,
        AYMR.strings.aurora_greeting_morning_rise,
        AYMR.strings.aurora_greeting_morning_binge,
        AYMR.strings.aurora_greeting_morning_coffee,
        AYMR.strings.aurora_greeting_morning_early_bird,
        AYMR.strings.aurora_greeting_morning_fresh,
        AYMR.strings.aurora_greeting_morning_energetic,
    )

    // Afternoon greetings (12-4 PM)
    private val afternoonGreetings = listOf(
        AYMR.strings.aurora_greeting_afternoon,
        AYMR.strings.aurora_greeting_afternoon_lunch,
        AYMR.strings.aurora_greeting_afternoon_ara,
        AYMR.strings.aurora_greeting_afternoon_sugoi,
        AYMR.strings.aurora_greeting_afternoon_break,
        AYMR.strings.aurora_greeting_afternoon_relax,
    )

    // Evening greetings (5-8 PM)
    private val eveningGreetings = listOf(
        AYMR.strings.aurora_greeting_evening,
        AYMR.strings.aurora_greeting_evening_vibes,
        AYMR.strings.aurora_greeting_evening_chill,
        AYMR.strings.aurora_greeting_evening_cozy,
        AYMR.strings.aurora_greeting_evening_relax_time,
        AYMR.strings.aurora_greeting_evening_sunset,
        AYMR.strings.aurora_greeting_evening_after_work,
    )

    // Night greetings (9 PM - 4 AM)
    private val nightGreetings = listOf(
        AYMR.strings.aurora_greeting_night_owl,
        AYMR.strings.aurora_greeting_still_up,
        AYMR.strings.aurora_greeting_late_night,
        AYMR.strings.aurora_greeting_night_one_more,
        AYMR.strings.aurora_greeting_night_sleep,
        AYMR.strings.aurora_greeting_night_senpai,
        AYMR.strings.aurora_greeting_night_just_one,
        AYMR.strings.aurora_greeting_night_midnight,
        AYMR.strings.aurora_greeting_night_starry,
    )

    // Absence greetings
    private val absenceGreetings = listOf(
        AYMR.strings.aurora_greeting_long_time,
        AYMR.strings.aurora_greeting_missed_you,
        AYMR.strings.aurora_greeting_youre_back,
    )

    // Streak-based greetings
    private val streakGreetings = listOf(
        AYMR.strings.aurora_greeting_streak_continues,
        AYMR.strings.aurora_greeting_streak_7_days,
        AYMR.strings.aurora_greeting_streak_loyal,
        AYMR.strings.aurora_greeting_streak_unstoppable,
        AYMR.strings.aurora_greeting_streak_daily,
    )

    // Achievement-based greetings
    private val achievementGreetings = listOf(
        AYMR.strings.aurora_greeting_achievement_hunter,
        AYMR.strings.aurora_greeting_achievement_10,
        AYMR.strings.aurora_greeting_achievement_collector,
        AYMR.strings.aurora_greeting_achievement_master,
        AYMR.strings.aurora_greeting_achievement_50,
        AYMR.strings.aurora_greeting_achievement_legendary,
    )

    // Statistics-based greetings
    private val statsGreetings = listOf(
        AYMR.strings.aurora_greeting_stats_100_eps,
        AYMR.strings.aurora_greeting_stats_marathoner,
        AYMR.strings.aurora_greeting_stats_500_eps,
        AYMR.strings.aurora_greeting_stats_beginner_critic,
        AYMR.strings.aurora_greeting_stats_expert,
        AYMR.strings.aurora_greeting_stats_1000_eps,
        AYMR.strings.aurora_greeting_stats_pro_viewer,
        AYMR.strings.aurora_greeting_stats_impressive,
    )

    // Library-based greetings
    private val libraryGreetings = listOf(
        AYMR.strings.aurora_greeting_library_impressive,
        AYMR.strings.aurora_greeting_library_50,
        AYMR.strings.aurora_greeting_library_growing,
        AYMR.strings.aurora_greeting_library_true_collector,
        AYMR.strings.aurora_greeting_library_100,
    )

    // Weekend greetings
    private val weekendGreetings = listOf(
        AYMR.strings.aurora_greeting_weekend_time,
        AYMR.strings.aurora_greeting_saturday_perfect,
        AYMR.strings.aurora_greeting_sunday_marathon,
        AYMR.strings.aurora_greeting_weekend_relax,
    )

    // First time greetings
    private val firstTimeGreetings = listOf(
        AYMR.strings.aurora_greeting_welcome_family,
        AYMR.strings.aurora_greeting_first_time,
    )

    private const val THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000L
    private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L

    /**
     * Get a random greeting from a list using date-based seed.
     * This ensures the greeting changes daily but stays stable within a day.
     */
    private fun getDateBasedRandom(list: List<StringResource>): StringResource {
        if (list.isEmpty()) return AYMR.strings.aurora_welcome_back
        if (list.size == 1) return list[0]

        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        val seed = (year * 1000 + dayOfYear).toLong()

        return list[Random(seed).nextInt(list.size)]
    }

    /**
     * Get greeting based on user context and statistics.
     *
     * Probabilistic balancing system:
     * 1. Critical events (first time, 7+ days absence) - ALWAYS 100%
     * 2. Milestone greetings - 30% probability
     * 3. Regular greetings (time of day + random) - 70% probability
     *
     * This ensures variety even for users with constant streaks/achievements.
     */
    fun getGreeting(
        lastOpenedTime: Long,
        achievementCount: Int = 0,
        episodesWatched: Int = 0,
        librarySize: Int = 0,
        currentStreak: Int = 0,
        isFirstTime: Boolean = false,
    ): StringResource {
        val currentTime = System.currentTimeMillis()

        // 1. CRITICAL EVENTS - Always 100% priority
        if (isFirstTime) {
            return firstTimeGreetings.random()
        }

        if (lastOpenedTime > 0) {
            val absenceDuration = currentTime - lastOpenedTime
            if (absenceDuration > SEVEN_DAYS_MS) {
                return absenceGreetings.random()
            }
            if (absenceDuration > THREE_DAYS_MS) {
                return absenceGreetings.random()
            }
        }

        // 2. PROBABILISTIC SYSTEM - 30% milestone / 70% regular
        val milestoneGreeting = getMilestoneGreeting(
            currentStreak = currentStreak,
            achievementCount = achievementCount,
            episodesWatched = episodesWatched,
            librarySize = librarySize,
        )

        if (milestoneGreeting != null) {
            // Generate random number 0-99
            val random = Random.nextInt(100)

            if (random < 30) {
                // 30% - show milestone greeting
                return milestoneGreeting
            }
            // 70% - fall through to regular greetings
        }

        // 3. REGULAR GREETINGS - Weekend + Time of day + Random
        if (isWeekend()) {
            return weekendGreetings.random()
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeBasedGreetings = when (hour) {
            in 5..11 -> morningGreetings
            in 12..16 -> afternoonGreetings
            in 17..20 -> eveningGreetings
            else -> nightGreetings
        }

        val allOptions = timeBasedGreetings + randomGreetings
        return allOptions.random()
    }

    /**
     * Collect milestone greeting if user qualifies for any.
     * Checks in priority order: streak → achievement → stats → library.
     * Returns null if no milestones are active.
     */
    private fun getMilestoneGreeting(
        currentStreak: Int,
        achievementCount: Int,
        episodesWatched: Int,
        librarySize: Int,
    ): StringResource? {
        checkStreakMilestone(currentStreak)?.let { return it }
        checkAchievementMilestone(achievementCount)?.let { return it }
        checkStatsMilestone(episodesWatched)?.let { return it }
        checkLibraryMilestone(librarySize)?.let { return it }
        return null
    }

    private fun isWeekend(): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }

    private fun checkStreakMilestone(streak: Int): StringResource? {
        return when {
            streak >= 14 -> getDateBasedRandom(
                listOf(
                    AYMR.strings.aurora_greeting_streak_unstoppable,
                    AYMR.strings.aurora_greeting_streak_loyal,
                    AYMR.strings.aurora_greeting_streak_daily,
                ),
            )
            streak >= 7 -> getDateBasedRandom(
                listOf(
                    AYMR.strings.aurora_greeting_streak_7_days,
                    AYMR.strings.aurora_greeting_streak_continues,
                ),
            )
            streak >= 3 -> getDateBasedRandom(streakGreetings)
            else -> null
        }
    }

    private fun checkAchievementMilestone(count: Int): StringResource? {
        return when {
            count >= 100 -> AYMR.strings.aurora_greeting_achievement_legendary // единственный вариант
            count >= 50 -> getDateBasedRandom(
                listOf(
                    AYMR.strings.aurora_greeting_achievement_50,
                    AYMR.strings.aurora_greeting_achievement_master,
                ),
            )
            count >= 10 -> getDateBasedRandom(achievementGreetings)
            else -> null
        }
    }

    private fun checkStatsMilestone(episodes: Int): StringResource? {
        return when {
            episodes >= 1000 -> getDateBasedRandom(
                listOf(
                    AYMR.strings.aurora_greeting_stats_1000_eps,
                    AYMR.strings.aurora_greeting_stats_pro_viewer,
                ),
            )
            episodes >= 500 -> getDateBasedRandom(
                listOf(
                    AYMR.strings.aurora_greeting_stats_500_eps,
                    AYMR.strings.aurora_greeting_stats_marathoner,
                ),
            )
            episodes >= 100 -> getDateBasedRandom(statsGreetings)
            else -> null
        }
    }

    private fun checkLibraryMilestone(size: Int): StringResource? {
        return when {
            size >= 100 -> getDateBasedRandom(
                listOf(
                    AYMR.strings.aurora_greeting_library_100,
                    AYMR.strings.aurora_greeting_library_true_collector,
                ),
            )
            size >= 50 -> getDateBasedRandom(
                listOf(
                    AYMR.strings.aurora_greeting_library_50,
                    AYMR.strings.aurora_greeting_library_impressive,
                ),
            )
            size >= 10 -> getDateBasedRandom(libraryGreetings)
            else -> null
        }
    }
}
