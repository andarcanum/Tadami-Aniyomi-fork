package eu.kanade.presentation.achievement.components

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

data class AchievementTimeStrings(
    val hoursMinutes: StringResource,
    val hours: StringResource,
    val minutes: StringResource,
)

@Composable
fun achievementTimeStrings(): AchievementTimeStrings {
    return AchievementTimeStrings(
        hoursMinutes = MR.strings.achievement_hours_minutes_alt,
        hours = MR.strings.achievement_hours,
        minutes = MR.strings.achievement_minutes_alt,
    )
}

internal fun formatAchievementTimeMinutes(
    minutes: Int,
    hoursMinutesText: String,
    hoursText: String,
    minutesText: String,
): String {
    val hours = minutes / 60
    val mins = minutes % 60

    return when {
        hours > 0 && mins > 0 -> hoursMinutesText
        hours > 0 -> hoursText
        else -> minutesText
    }
}
