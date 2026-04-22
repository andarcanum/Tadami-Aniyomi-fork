package eu.kanade.presentation.entries.components.aurora

internal object AuroraZIndex {
    /** Background poster and scrim layer. */
    const val BACKGROUND = 0f

    /** Main content and cards. */
    const val BASE = 1f

    /** Hero content, top chrome, and other prominent entry overlays. */
    const val HERO = 2f

    /** Selection stacks and bottom bulk-action UI. */
    const val SELECTION = 3f

    /** Temporary overlays that should sit above the main content stack. */
    const val OVERLAY = 4f

    /** Snackbars and other transient system-level feedback. */
    const val SNACKBAR = 5f
}
