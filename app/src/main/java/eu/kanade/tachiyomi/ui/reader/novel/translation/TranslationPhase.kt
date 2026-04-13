package eu.kanade.tachiyomi.ui.reader.novel.translation

/**
 * Represents the current phase of chapter translation.
 * Used to provide structured progress updates to the UI.
 */
enum class TranslationPhase {
    /** Translation service is downloading or resolving models */
    PREPARING_MODEL,

    /** Actual translation of chapter blocks is in progress */
    TRANSLATING,

    /** Translation completed or not started */
    IDLE,
}
