package eu.kanade.tachiyomi.animesource.model

/**
 * One account content-preference toggle (contract v20): the service's content-type filters
 * (e.g. "which content types appear in my feed"). [id] is the service-side key, [label] a
 * human-readable name, [enabled] the current account state.
 */
data class ContentPreferenceOption(
    val id: String,
    val label: String,
    val enabled: Boolean,
)
