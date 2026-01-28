package tachiyomi.domain.achievement.model

import java.time.LocalDate

data class DayActivity(
    val date: LocalDate,
    val level: Int,
    val type: ActivityType,
)

enum class ActivityType {
    READING,
    WATCHING,
    APP_OPEN,
}
