package eu.kanade.presentation.more.settings.screen

import eu.kanade.domain.ui.model.AppTheme

private val previewAuraUnlockables = setOf(
    "aura_harem",
    "aura_level_up",
    "aura_matrix",
    "aura_trinity_orbit",
    "aura_deep_focus",
    "aura_shadow_monarch",
    "aura_ascendant_gold",
)

private val previewPresetUnlockables = setOf(
    "title_trinity_initiate",
    "title_finisher",
    "title_closer",
    "title_deep_reader",
    "title_rank_4",
    "profile_nickname_effect_aurora_crown",
    "profile_nickname_effect_glitch_rune",
    "profile_nickname_effect_cipher",
    "profile_nickname_effect_trinity_prism",
    "profile_nickname_effect_shadow_crown",
    "profile_nickname_effect_rank_sigils",
    "avatar_frame_neon",
    "avatar_frame_hologram",
    "avatar_frame_prismatic",
    "avatar_frame_trinity_orbit",
    "avatar_frame_deep_archive",
    "avatar_frame_hybrid_scroll",
    "avatar_frame_ascendant",
    "home_badge_orbit",
    "home_badge_crown",
    "home_badge_shuriken",
    "home_badge_trinity",
    "home_badge_finisher",
    "home_badge_immersion",
    "home_badge_ascendant",
    "special_background_petal_storm",
    "special_background_neon_orbit",
    "special_background_trinity_constellation",
    "special_background_deep_space_archive",
    "special_background_shadow_realm",
)

private val previewThemeUnlockables by lazy {
    val hiddenThemes = AppTheme.entries
        .filter(AppTheme::isHidden)
        .flatMap(::themeUnlockableIds)
        .toSet()
    hiddenThemes
}

internal fun shouldShowTreasury(
    isDebugBuild: Boolean,
    unlockedUnlockables: Set<String>,
): Boolean {
    return isDebugBuild || unlockedUnlockables.isNotEmpty()
}

internal fun visibleUnlockablesForTreasuryPreview(
    debugBypassLocks: Boolean,
    unlockedUnlockables: Set<String>,
): Set<String> {
    if (!debugBypassLocks) return unlockedUnlockables

    return unlockedUnlockables + previewThemeUnlockables + previewAuraUnlockables + previewPresetUnlockables
}

internal fun isThemePreviewUnlocked(
    theme: AppTheme,
    unlockedUnlockables: Set<String>,
): Boolean {
    return themeUnlockableIds(theme).any(unlockedUnlockables::contains)
}

internal fun themeUnlockableIds(theme: AppTheme): Set<String> {
    return setOf(
        "theme_${theme.name}",
        "theme_${theme.name.lowercase()}",
    )
}
