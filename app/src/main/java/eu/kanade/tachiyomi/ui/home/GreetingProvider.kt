package eu.kanade.tachiyomi.ui.home

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR
import java.util.Calendar

object GreetingProvider {
    
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
    
    private val morningGreetings = listOf(
        AYMR.strings.aurora_greeting_morning,
        AYMR.strings.aurora_greeting_morning_rise,
        AYMR.strings.aurora_greeting_morning_binge,
        AYMR.strings.aurora_greeting_morning_coffee,
    )
    
    private val afternoonGreetings = listOf(
        AYMR.strings.aurora_greeting_afternoon,
        AYMR.strings.aurora_greeting_afternoon_lunch,
        AYMR.strings.aurora_greeting_afternoon_ara,
        AYMR.strings.aurora_greeting_afternoon_sugoi,
    )
    
    private val eveningGreetings = listOf(
        AYMR.strings.aurora_greeting_evening,
        AYMR.strings.aurora_greeting_evening_vibes,
        AYMR.strings.aurora_greeting_evening_chill,
        AYMR.strings.aurora_greeting_evening_cozy,
    )
    
    private val nightGreetings = listOf(
        AYMR.strings.aurora_greeting_night_owl,
        AYMR.strings.aurora_greeting_still_up,
        AYMR.strings.aurora_greeting_late_night,
        AYMR.strings.aurora_greeting_night_one_more,
        AYMR.strings.aurora_greeting_night_sleep,
        AYMR.strings.aurora_greeting_night_senpai,
        AYMR.strings.aurora_greeting_night_just_one,
    )
    
    private val absenceGreetings = listOf(
        AYMR.strings.aurora_greeting_long_time,
        AYMR.strings.aurora_greeting_missed_you,
        AYMR.strings.aurora_greeting_youre_back,
    )
    
    private const val THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000L
    
    fun getGreeting(lastOpenedTime: Long): StringResource {
        val currentTime = System.currentTimeMillis()
        
        if (lastOpenedTime > 0 && (currentTime - lastOpenedTime) > THREE_DAYS_MS) {
            return absenceGreetings.random()
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
}
