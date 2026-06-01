package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tadami.aurora.BuildConfig
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.components.allAuraPalettes
import eu.kanade.presentation.components.resolveAuraPalette
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.settingsTitleColor
import eu.kanade.presentation.more.settings.widget.AppThemePreviewItem
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.TachiyomiTheme
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTreasuryScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.label_treasury

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val unlockableManager = remember { Injekt.get<UnlockableManager>() }
        val userProfilePreferences = remember { Injekt.get<UserProfilePreferences>() }

        val debugBypassLocksPref = uiPreferences.debugBypassTreasuryLocks()
        val debugBypassLocks by debugBypassLocksPref.collectAsStateWithLifecycle()

        val nicknameEffectKey by userProfilePreferences.nicknameEffect().collectAsStateWithLifecycle()
        val avatarFrameStyleKey by userProfilePreferences.avatarFrameStyle().collectAsStateWithLifecycle()
        val homeBadgeStyleKey by userProfilePreferences.homeBadgeStyle().collectAsStateWithLifecycle()
        val specialBackgroundStyleKey by uiPreferences.specialBackgroundStyle().collectAsStateWithLifecycle()

        val unlockedUnlockables = visibleUnlockablesForTreasuryPreview(
            debugBypassLocks = debugBypassLocks,
            unlockedUnlockables = unlockableManager.getUnlockedUnlockables(),
        )

        val achievementRepository = remember {
            Injekt.get<tachiyomi.domain.achievement.repository.AchievementRepository>()
        }
        val achievements by achievementRepository.getAll().collectAsStateWithLifecycle(initialValue = emptyList())

        val rewardToAchievementMap = remember(achievements) {
            val achievementsById = achievements.associateBy { it.id }
            val map = mutableMapOf<String, Achievement>()
            achievements.forEach { achievement ->
                achievement.rewards?.forEach { reward ->
                    map[reward.id] = achievement
                }
                achievement.unlockableId?.let { uid ->
                    map[uid] = achievement
                }
            }
            val fallbackRewardSources = mapOf(
                "theme_ONYX_GOLD" to "secret_onepiece",
                "theme_SAKURA_NOIR" to "secret_hall_unlocked",
                "theme_NEBULA_TIDE" to "secret_goku",
                "aura_harem" to "secret_harem_king",
                "aura_matrix" to "secret_goku",
                "aura_level_up" to "master_achiever",
                "profile_nickname_effect_aurora_crown" to "master_achiever",
                "profile_nickname_effect_glitch_rune" to "secret_crybaby",
                "profile_nickname_effect_cipher" to "secret_jojo",
                "avatar_frame_neon" to "secret_jojo",
                "avatar_frame_hologram" to "secret_goku",
                "avatar_frame_prismatic" to "secret_onepiece",
                "home_badge_orbit" to "read_100_novel_chapters",
                "home_badge_crown" to "secret_hall_unlocked",
                "home_badge_shuriken" to "achievement_collector",
                "special_background_petal_storm" to "secret_crybaby",
                "special_background_neon_orbit" to "secret_jojo",
            )
            fallbackRewardSources.forEach { (rewardId, achievementId) ->
                if (map[rewardId] == null) {
                    achievementsById[achievementId]?.let { map[rewardId] = it }
                }
            }
            map
        }

        val profileEffectPresets = listOf(
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_aurora_crown",
                title = unlockableManager.getUnlockableName("profile_nickname_effect_aurora_crown"),
                description = stringResource(AYMR.strings.treasury_reward_aurora_crown_description),
                accentColor = Color(0xFFFFD54F),
                isActive = { nicknameEffectKey == "aurora_crown" },
                onApply = {
                    userProfilePreferences.nicknameEffect().set("aurora_crown")
                },
                onDeactivate = {
                    userProfilePreferences.nicknameEffect().set("none")
                },
            ),
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_glitch_rune",
                title = unlockableManager.getUnlockableName("profile_nickname_effect_glitch_rune"),
                description = stringResource(AYMR.strings.treasury_reward_glitch_rune_description),
                accentColor = Color(0xFF40C4FF),
                isActive = { nicknameEffectKey == "glitch_rune" },
                onApply = {
                    userProfilePreferences.nicknameEffect().set("glitch_rune")
                },
                onDeactivate = {
                    userProfilePreferences.nicknameEffect().set("none")
                },
            ),
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_cipher",
                title = unlockableManager.getUnlockableName("profile_nickname_effect_cipher"),
                description = stringResource(AYMR.strings.treasury_reward_cipher_description),
                accentColor = Color(0xFF69F0AE),
                isActive = { nicknameEffectKey == "cipher" },
                onApply = {
                    userProfilePreferences.nicknameEffect().set("cipher")
                },
                onDeactivate = {
                    userProfilePreferences.nicknameEffect().set("none")
                },
            ),
        )

        val avatarFramePresets = listOf(
            TreasuryPreset(
                unlockableId = "avatar_frame_neon",
                title = unlockableManager.getUnlockableName("avatar_frame_neon"),
                description = stringResource(AYMR.strings.treasury_reward_neon_frame_description),
                accentColor = Color(0xFF00E5FF),
                isActive = { avatarFrameStyleKey == "neon" },
                onApply = {
                    userProfilePreferences.avatarFrameStyle().set("neon")
                },
                onDeactivate = {
                    userProfilePreferences.avatarFrameStyle().set("none")
                },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_hologram",
                title = unlockableManager.getUnlockableName("avatar_frame_hologram"),
                description = stringResource(AYMR.strings.treasury_reward_hologram_frame_description),
                accentColor = Color(0xFFB388FF),
                isActive = { avatarFrameStyleKey == "hologram" },
                onApply = {
                    userProfilePreferences.avatarFrameStyle().set("hologram")
                },
                onDeactivate = {
                    userProfilePreferences.avatarFrameStyle().set("none")
                },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_prismatic",
                title = unlockableManager.getUnlockableName("avatar_frame_prismatic"),
                description = stringResource(AYMR.strings.treasury_reward_prismatic_frame_description),
                accentColor = Color(0xFFFF8A65),
                isActive = { avatarFrameStyleKey == "prismatic" },
                onApply = {
                    userProfilePreferences.avatarFrameStyle().set("prismatic")
                },
                onDeactivate = {
                    userProfilePreferences.avatarFrameStyle().set("none")
                },
            ),
        )

        val homePresets = listOf(
            TreasuryPreset(
                unlockableId = "home_badge_orbit",
                title = unlockableManager.getUnlockableName("home_badge_orbit"),
                description = stringResource(AYMR.strings.treasury_reward_orbit_badge_description),
                accentColor = Color(0xFF64B5F6),
                isActive = { homeBadgeStyleKey == "orbit" },
                onApply = {
                    userProfilePreferences.homeBadgeStyle().set("orbit")
                },
                onDeactivate = {
                    userProfilePreferences.homeBadgeStyle().set("none")
                },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_crown",
                title = unlockableManager.getUnlockableName("home_badge_crown"),
                description = stringResource(AYMR.strings.treasury_reward_crown_badge_description),
                accentColor = Color(0xFFFFC107),
                isActive = { homeBadgeStyleKey == "crown" },
                onApply = {
                    userProfilePreferences.homeBadgeStyle().set("crown")
                },
                onDeactivate = {
                    userProfilePreferences.homeBadgeStyle().set("none")
                },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_shuriken",
                title = unlockableManager.getUnlockableName("home_badge_shuriken"),
                description = stringResource(AYMR.strings.treasury_reward_shuriken_badge_description),
                accentColor = Color(0xFFEF5350),
                isActive = { homeBadgeStyleKey == "shuriken" },
                onApply = {
                    userProfilePreferences.homeBadgeStyle().set("shuriken")
                },
                onDeactivate = {
                    userProfilePreferences.homeBadgeStyle().set("none")
                },
            ),
        )

        val specialBackgroundPresets = listOf(
            TreasuryPreset(
                unlockableId = "special_background_petal_storm",
                title = unlockableManager.getUnlockableName("special_background_petal_storm"),
                description = stringResource(AYMR.strings.treasury_reward_petal_storm_background_description),
                accentColor = Color(0xFFFF8FB1),
                isActive = { specialBackgroundStyleKey == "petal_storm" },
                onApply = {
                    uiPreferences.specialBackgroundStyle().set("petal_storm")
                },
                onDeactivate = {
                    uiPreferences.specialBackgroundStyle().set("none")
                },
            ),
            TreasuryPreset(
                unlockableId = "special_background_neon_orbit",
                title = unlockableManager.getUnlockableName("special_background_neon_orbit"),
                description = stringResource(AYMR.strings.treasury_reward_neon_orbit_background_description),
                accentColor = Color(0xFF6EF6FF),
                isActive = { specialBackgroundStyleKey == "neon_orbit" },
                onApply = {
                    uiPreferences.specialBackgroundStyle().set("neon_orbit")
                },
                onDeactivate = {
                    uiPreferences.specialBackgroundStyle().set("none")
                },
            ),
        )

        val preferences = mutableListOf<Preference>()

        if (BuildConfig.DEBUG) {
            preferences.add(
                Preference.PreferenceGroup(
                    title = stringResource(AYMR.strings.pref_category_debug),
                    preferenceItems = listOf(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = debugBypassLocksPref,
                            title = stringResource(AYMR.strings.pref_debug_bypass_treasury_locks),
                            subtitle = stringResource(AYMR.strings.pref_debug_bypass_treasury_locks_summary),
                        ),
                    ).toImmutableList(),
                ),
            )
        }

        preferences.add(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.treasury_unlocked_themes),
                preferenceItems = listOf(
                    Preference.PreferenceItem.CustomPreference(title = stringResource(AYMR.strings.treasury_themes)) {
                        TreasuryThemeSelector(
                            uiPreferences = uiPreferences,
                            unlockedUnlockables = unlockedUnlockables,
                            rewardToAchievementMap = rewardToAchievementMap,
                        )
                    },
                ).toImmutableList(),
            ),
        )

        preferences.add(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.treasury_auras),
                preferenceItems = listOf(
                    Preference.PreferenceItem.CustomPreference(title = stringResource(AYMR.strings.treasury_auras)) {
                        TreasuryAuraSelector(
                            uiPreferences = uiPreferences,
                            unlockableManager = unlockableManager,
                            unlockedUnlockables = unlockedUnlockables,
                            rewardToAchievementMap = rewardToAchievementMap,
                        )
                    },
                ).toImmutableList(),
            ),
        )

        preferences.add(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.treasury_visual_effects),
                preferenceItems = listOf(
                    Preference.PreferenceItem.CustomPreference(
                        title = stringResource(AYMR.strings.treasury_background_effects),
                    ) {
                        TreasuryToggleSelector(
                            presets = specialBackgroundPresets,
                            unlockedUnlockables = unlockedUnlockables,
                            rewardToAchievementMap = rewardToAchievementMap,
                        )
                    },
                ).toImmutableList(),
            ),
        )

        preferences.add(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.treasury_profile_and_avatar),
                preferenceItems = listOf(
                    Preference.PreferenceItem.CustomPreference(
                        title = stringResource(AYMR.strings.treasury_profile_effects),
                    ) {
                        TreasuryToggleSelector(
                            presets = profileEffectPresets,
                            unlockedUnlockables = unlockedUnlockables,
                            rewardToAchievementMap = rewardToAchievementMap,
                        )
                    },
                    Preference.PreferenceItem.CustomPreference(
                        title = stringResource(AYMR.strings.treasury_avatar_frames),
                    ) {
                        TreasuryToggleSelector(
                            presets = avatarFramePresets,
                            unlockedUnlockables = unlockedUnlockables,
                            rewardToAchievementMap = rewardToAchievementMap,
                        )
                    },
                ).toImmutableList(),
            ),
        )

        preferences.add(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.treasury_home_hub_rewards),
                preferenceItems = listOf(
                    Preference.PreferenceItem.CustomPreference(
                        title = stringResource(AYMR.strings.treasury_home_hub_rewards),
                    ) {
                        TreasuryToggleSelector(
                            presets = homePresets,
                            unlockedUnlockables = unlockedUnlockables,
                            rewardToAchievementMap = rewardToAchievementMap,
                        )
                    },
                ).toImmutableList(),
            ),
        )

        return preferences
    }
}

private data class TreasuryPreset(
    val unlockableId: String,
    val title: String,
    val description: String,
    val accentColor: Color,
    val isActive: () -> Boolean,
    val onApply: () -> Unit,
    val onDeactivate: () -> Unit,
)

@Composable
private fun TreasuryThemeSelector(
    uiPreferences: UiPreferences,
    unlockedUnlockables: Set<String>,
    rewardToAchievementMap: Map<String, Achievement>,
) {
    val context = LocalContext.current
    val appTheme by uiPreferences.appTheme().collectAsStateWithLifecycle()
    val amoled by uiPreferences.themeDarkAmoled().collectAsStateWithLifecycle()

    val treasuryThemes = listOf(
        AppTheme.ONYX_GOLD,
        AppTheme.SAKURA_NOIR,
        AppTheme.NEBULA_TIDE,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        treasuryThemes.forEach { theme ->
            val rewardId = "theme_${theme.name}"
            val isUnlocked = isThemePreviewUnlocked(theme, unlockedUnlockables)
            val achievementTitle = rewardToAchievementMap[rewardId]?.title ?: "Achievement"
            val isSelected = appTheme == theme

            Column(
                modifier = Modifier
                    .width(156.dp)
                    .alpha(if (isUnlocked) 1f else 0.5f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f),
                    contentAlignment = Alignment.Center,
                ) {
                    TachiyomiTheme(appTheme = theme, amoled = amoled) {
                        AppThemePreviewItem(
                            selected = isSelected && isUnlocked,
                            onClick = {
                                if (isUnlocked) {
                                    uiPreferences.appTheme().set(theme)
                                    (context as? Activity)?.let { ActivityCompat.recreate(it) }
                                }
                            },
                        )
                    }

                    if (!isUnlocked) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(17.dp))
                                .clip(RoundedCornerShape(17.dp))
                                .clickable(enabled = false) {},
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = theme.titleRes?.let { stringResource(it) } ?: theme.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = settingsTitleColor(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isUnlocked) {
                        if (isSelected) "Active" else "Unlocked"
                    } else {
                        stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUnlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TreasuryAuraSelector(
    uiPreferences: UiPreferences,
    unlockableManager: UnlockableManager,
    unlockedUnlockables: Set<String>,
    rewardToAchievementMap: Map<String, Achievement>,
) {
    val enabledAuras by uiPreferences.enabledAuras().collectAsStateWithLifecycle()
    val auraPalettes = remember { allAuraPalettes() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        auraPalettes.forEach { aura ->
            val isUnlocked = unlockedUnlockables.contains(aura.id)
            val isEnabled = enabledAuras.contains(aura.id)
            val achievementTitle = rewardToAchievementMap[aura.id]?.title ?: "Achievement"

            val cardBgColor = if (AuroraTheme.colors.isDark) {
                AuroraTheme.colors.glass.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isUnlocked) 1f else 0.5f)
                    .clickable(enabled = isUnlocked) {
                        uiPreferences.enabledAuras().set(
                            if (isEnabled) {
                                emptySet()
                            } else {
                                setOf(aura.id)
                            },
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = if (isEnabled && isUnlocked) {
                    BorderStroke(2.dp, aura.accentColor)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = aura.accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isUnlocked) {
                                if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Star
                            } else {
                                Icons.Default.Lock
                            },
                            contentDescription = null,
                            tint = if (isEnabled && isUnlocked) {
                                aura.accentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = unlockableManager.getUnlockableName(aura.id),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = settingsTitleColor(),
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (isUnlocked) {
                                resolveAuraPalette(aura.id)?.description ?: aura.description
                            } else {
                                stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isUnlocked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }

                    if (isUnlocked) {
                        androidx.compose.material3.Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                uiPreferences.enabledAuras().set(if (checked) setOf(aura.id) else emptySet())
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = aura.accentColor,
                                checkedTrackColor = aura.accentColor.copy(alpha = 0.3f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TreasuryToggleSelector(
    presets: List<TreasuryPreset>,
    unlockedUnlockables: Set<String>,
    rewardToAchievementMap: Map<String, Achievement>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        presets.forEach { preset ->
            val isUnlocked = unlockedUnlockables.contains(preset.unlockableId)
            val isActive = isUnlocked && preset.isActive()
            val achievementTitle = rewardToAchievementMap[preset.unlockableId]?.title ?: "Achievement"
            val cardBgColor = if (AuroraTheme.colors.isDark) {
                AuroraTheme.colors.glass.copy(alpha = 0.06f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isUnlocked) 1f else 0.5f)
                    .clickable(enabled = isUnlocked) {
                        if (isActive) {
                            preset.onDeactivate()
                        } else {
                            preset.onApply()
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = if (isActive) {
                                    preset.accentColor.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                },
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = when {
                                !isUnlocked -> Icons.Default.Lock
                                isActive -> Icons.Default.CheckCircle
                                else -> Icons.Default.Star
                            },
                            contentDescription = null,
                            tint = when {
                                !isUnlocked -> MaterialTheme.colorScheme.onSurfaceVariant
                                isActive -> preset.accentColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = settingsTitleColor(),
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (isUnlocked) {
                                preset.description
                            } else {
                                stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isUnlocked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }

                    if (isUnlocked) {
                        Column(horizontalAlignment = Alignment.End) {
                            Switch(
                                checked = isActive,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        preset.onApply()
                                    } else {
                                        preset.onDeactivate()
                                    }
                                },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = preset.accentColor,
                                    checkedTrackColor = preset.accentColor.copy(alpha = 0.28f),
                                ),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isActive) {
                                    stringResource(AYMR.strings.treasury_toggle_active)
                                } else {
                                    stringResource(AYMR.strings.treasury_toggle_inactive)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) {
                                    preset.accentColor
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
