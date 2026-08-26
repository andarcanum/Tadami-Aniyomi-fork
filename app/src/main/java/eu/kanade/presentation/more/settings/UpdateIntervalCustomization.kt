package eu.kanade.presentation.more.settings

/**
 * Entries key that opens the custom interval slider dialog instead of being saved.
 * Must not collide with: -2 (use general), -1 (on app start), 0 (never), presets 12..168.
 */
const val CUSTOM_UPDATE_INTERVAL_ENTRY = -3

const val MIN_CUSTOM_UPDATE_INTERVAL_HOURS = 1
const val MAX_CUSTOM_UPDATE_INTERVAL_HOURS = 24

/** Neutral slider start when the current value is not a plain hour count. */
const val DEFAULT_CUSTOM_UPDATE_INTERVAL_HOURS = 12

/** Battery hint is shown for intervals strictly below this. */
const val BATTERY_WARNING_THRESHOLD_HOURS = 6

/**
 * Initial slider position: the current interval when it is a plain hour count,
 * otherwise the neutral default (spec: sentinels and presets > 24h start at 12).
 */
fun initialCustomUpdateInterval(current: Int): Int =
    if (current in MIN_CUSTOM_UPDATE_INTERVAL_HOURS..MAX_CUSTOM_UPDATE_INTERVAL_HOURS) {
        current
    } else {
        DEFAULT_CUSTOM_UPDATE_INTERVAL_HOURS
    }

/**
 * Subtitle fallback for interval values missing from the entries map («Every N hours»).
 * Returns null for non-positive values so callers keep default ListPreference behavior.
 */
fun customUpdateIntervalSubtitle(hours: Int, template: String): String? =
    if (hours > 0) template.format(hours) else null

fun showBatteryWarning(hours: Int): Boolean = hours < BATTERY_WARNING_THRESHOLD_HOURS
