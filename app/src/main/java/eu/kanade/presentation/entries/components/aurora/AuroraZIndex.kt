package eu.kanade.presentation.entries.components.aurora

internal object AuroraZIndex {
    /** Background poster and scrim layer. */
    const val Background = 0f
    /** Main content and cards. */
    const val Base = 1f
    /** Hero content, top chrome, and other prominent entry overlays. */
    const val Hero = 2f
    /** Selection stacks and bottom bulk-action UI. */
    const val Selection = 3f
    /** Temporary overlays that should sit above the main content stack. */
    const val Overlay = 4f
    /** Snackbars and other transient system-level feedback. */
    const val Snackbar = 5f
}
