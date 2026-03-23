package eu.kanade.tachiyomi.ui.player

internal fun sanitizeVisiblePlaybackPreferences(
    preferences: PlaybackSelectionPreferences,
): PlaybackSelectionPreferences {
    return preferences
}

internal fun hasVisiblePlaybackPreferences(
    preferences: PlaybackSelectionPreferences,
): Boolean {
    val sanitized = sanitizeVisiblePlaybackPreferences(preferences)

    return sanitized.preferredPlayer != PlaybackPlayerPreference.AUTO ||
        sanitized.preferredDubbingCdn.isNotBlank() ||
        sanitized.preferredDubbingKodik.isNotBlank() ||
        sanitized.preferredDubbingParlorate.isNotBlank() ||
        !sanitized.preferredQualityCdn.equals("best", ignoreCase = true) ||
        !sanitized.preferredQualityKodik.equals("best", ignoreCase = true) ||
        !sanitized.preferredQualityParlorate.equals("best", ignoreCase = true)
}
