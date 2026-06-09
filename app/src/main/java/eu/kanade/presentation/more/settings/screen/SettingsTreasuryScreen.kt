package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tadami.aurora.BuildConfig
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.components.allAuraPalettes
import eu.kanade.presentation.components.resolveAuraPalette
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_SHAPE
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.settingsTitleColor
import eu.kanade.presentation.more.settings.widget.AppThemePreviewItem
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.theme.resolveAuroraElevation
import eu.kanade.tachiyomi.ui.home.components.AvatarFrameDecorations
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
            val map = mutableMapOf<String, Achievement>()
            achievements.forEach { achievement ->
                achievement.rewards?.forEach { reward ->
                    map[reward.id] = achievement
                }
                achievement.unlockableId?.let { uid ->
                    map[uid] = achievement
                }
            }
            map
        }

        val profileEffectPresets = listOf(
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_aurora_crown",
                title =
                unlockableManager.getUnlockableNameRes("profile_nickname_effect_aurora_crown")?.let {
                    stringResource(it)
                }
                    ?: unlockableManager.getUnlockableName("profile_nickname_effect_aurora_crown"),
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
                title =
                unlockableManager.getUnlockableNameRes("profile_nickname_effect_glitch_rune")?.let {
                    stringResource(it)
                }
                    ?: unlockableManager.getUnlockableName("profile_nickname_effect_glitch_rune"),
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
                title =
                unlockableManager.getUnlockableNameRes("profile_nickname_effect_cipher")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("profile_nickname_effect_cipher"),
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
                title = unlockableManager.getUnlockableNameRes("avatar_frame_neon")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("avatar_frame_neon"),
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
                title = unlockableManager.getUnlockableNameRes("avatar_frame_hologram")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("avatar_frame_hologram"),
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
                title = unlockableManager.getUnlockableNameRes("avatar_frame_prismatic")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("avatar_frame_prismatic"),
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
                title = unlockableManager.getUnlockableNameRes("home_badge_orbit")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("home_badge_orbit"),
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
                title = unlockableManager.getUnlockableNameRes("home_badge_crown")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("home_badge_crown"),
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
                title = unlockableManager.getUnlockableNameRes("home_badge_shuriken")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("home_badge_shuriken"),
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
                title =
                unlockableManager.getUnlockableNameRes("special_background_petal_storm")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("special_background_petal_storm"),
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
                title =
                unlockableManager.getUnlockableNameRes("special_background_neon_orbit")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("special_background_neon_orbit"),
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

        val name by userProfilePreferences.name().collectAsStateWithLifecycle()
        val avatarUrl by userProfilePreferences.avatarUrl().collectAsStateWithLifecycle()
        val nicknameFontPreset = remember(userProfilePreferences) {
            eu.kanade.tachiyomi.ui.home.NicknameFontPreset.fromKey(userProfilePreferences.nicknameFont().get())
        }
        val nicknameFontSize = userProfilePreferences.nicknameFontSize().get()
        val nicknameColorPreset = remember(userProfilePreferences) {
            eu.kanade.tachiyomi.ui.home.NicknameColorPreset.fromKey(userProfilePreferences.nicknameColor().get())
        }
        val nicknameCustomColorHex = userProfilePreferences.nicknameCustomColorHex().get()
        val nicknameOutline = userProfilePreferences.nicknameOutline().get()
        val nicknameOutlineWidth = userProfilePreferences.nicknameOutlineWidth().get()
        val nicknameGlow = userProfilePreferences.nicknameGlow().get()

        val activeNicknameStyle = remember(
            nicknameFontPreset,
            nicknameFontSize,
            nicknameColorPreset,
            nicknameOutline,
            nicknameOutlineWidth,
            nicknameGlow,
            nicknameEffectKey,
            nicknameCustomColorHex,
        ) {
            eu.kanade.tachiyomi.ui.home.NicknameStyle(
                font = nicknameFontPreset,
                fontSize = nicknameFontSize,
                color = nicknameColorPreset,
                outline = nicknameOutline,
                outlineWidth = nicknameOutlineWidth,
                glow = nicknameGlow,
                effect = eu.kanade.tachiyomi.ui.home.NicknameEffectPreset.fromKey(nicknameEffectKey),
                customColorHex = nicknameCustomColorHex,
            )
        }

        preferences.add(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.aurora_nickname_preview),
                preferenceItems = listOf(
                    Preference.PreferenceItem.CustomPreference(title = "Nickname Preview") {
                        val colors = AuroraTheme.colors
                        val decoratedName = remember(name) {
                            name.trim().ifEmpty { "User Name" }
                        }

                        val cardBgColor = if (colors.isDark) {
                            colors.glass.copy(alpha = 0.08f)
                        } else {
                            Color.White
                        }
                        val cardElevation = if (colors.isDark || colors.isEInk) {
                            0.dp
                        } else {
                            resolveAuroraElevation(colors, AuroraSurfaceLevel.Glass)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = AURORA_SETTINGS_CARD_SHAPE,
                            colors = CardDefaults.cardColors(
                                containerColor = cardBgColor,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                            border = if (colors.isDark) {
                                BorderStroke(1.dp, colors.divider)
                            } else {
                                null
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val avatarModifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (avatarFrameStyleKey != "none") {
                                                Modifier.padding(2.dp)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clip(CircleShape)

                                    if (avatarUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = avatarUrl,
                                            contentDescription = null,
                                            modifier = avatarModifier,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            modifier = avatarModifier,
                                            tint = colors.accent,
                                        )
                                    }

                                    AvatarFrameDecorations(
                                        styleKey = avatarFrameStyleKey,
                                        accentColor = colors.accent,
                                    )
                                }

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    eu.kanade.tachiyomi.ui.home.StyledNicknameText(
                                        text = decoratedName,
                                        nicknameStyle = activeNicknameStyle,
                                        badgeStyleKey = homeBadgeStyleKey,
                                    )
                                }
                            }
                        }
                    },
                ).toImmutableList(),
            ),
        )

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

private data class TreasuryExclusiveThemeSpec(
    val theme: AppTheme,
    val rarity: StringResource,
    val tagline: StringResource,
    val accentColor: Color,
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
        TreasuryExclusiveThemeSpec(
            theme = AppTheme.ONYX_GOLD,
            rarity = AYMR.strings.treasury_exclusive_rarity_mythic,
            tagline = AYMR.strings.treasury_tagline_onyx_gold,
            accentColor = Color(0xFFFFD36E),
        ),
        TreasuryExclusiveThemeSpec(
            theme = AppTheme.SAKURA_NOIR,
            rarity = AYMR.strings.treasury_exclusive_rarity_secret,
            tagline = AYMR.strings.treasury_tagline_sakura_noir,
            accentColor = Color(0xFFFF78B7),
        ),
        TreasuryExclusiveThemeSpec(
            theme = AppTheme.NEBULA_TIDE,
            rarity = AYMR.strings.treasury_exclusive_rarity_transcendent,
            tagline = AYMR.strings.treasury_tagline_nebula_tide,
            accentColor = Color(0xFF46F4FF),
        ),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        treasuryThemes.forEach { spec ->
            val theme = spec.theme
            val rewardId = "theme_${theme.name}"
            val isUnlocked = isThemePreviewUnlocked(theme, unlockedUnlockables)
            val achievementTitle = rewardToAchievementMap[rewardId]?.title ?: "Achievement"
            val isSelected = appTheme == theme

            Card(
                modifier = Modifier
                    .width(180.dp)
                    .alpha(if (isUnlocked) 1f else 0.62f)
                    .heightIn(min = 460.dp),
                shape = AURORA_SETTINGS_CARD_SHAPE,
                colors = CardDefaults.cardColors(
                    containerColor = if (AuroraTheme.colors.isDark) {
                        Color.White.copy(alpha = 0.045f)
                    } else {
                        Color.White
                    },
                ),
                border = BorderStroke(
                    width = if (isSelected && isUnlocked) 2.dp else 1.dp,
                    color = spec.accentColor.copy(alpha = if (isUnlocked) 0.72f else 0.32f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
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

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(
                                    color = spec.accentColor.copy(alpha = if (isUnlocked) 0.92f else 0.62f),
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = stringResource(spec.rarity).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF08080C),
                                maxLines = 1,
                            )
                        }

                        if (!isUnlocked) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Black.copy(alpha = 0.48f), shape = RoundedCornerShape(17.dp))
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

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = theme.titleRes?.let { stringResource(it) } ?: theme.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = settingsTitleColor(),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(spec.tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                        minLines = 3,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isUnlocked) {
                            if (isSelected) {
                                stringResource(
                                    AYMR.strings.treasury_exclusive_active,
                                )
                            } else {
                                stringResource(AYMR.strings.treasury_exclusive_unlocked)
                            }
                        } else {
                            stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUnlocked) spec.accentColor else MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        auraPalettes.forEach { aura ->
            val isUnlocked = unlockedUnlockables.contains(aura.id)
            val isEnabled = enabledAuras.contains(aura.id)
            val achievementTitle = rewardToAchievementMap[aura.id]?.title ?: "Achievement"
            val rewardIconResId = remember(aura.id) {
                getRewardIconResourceId(aura.id, context)
            }

            val colors = AuroraTheme.colors
            val cardBgColor = if (colors.isDark) {
                colors.glass.copy(alpha = 0.08f)
            } else {
                Color.White
            }
            val cardElevation = if (colors.isDark || colors.isEInk) {
                0.dp
            } else {
                resolveAuroraElevation(colors, AuroraSurfaceLevel.Glass)
            }
            val cardBorder = if (isEnabled && isUnlocked) {
                BorderStroke(2.dp, aura.accentColor)
            } else {
                null
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
                shape = AURORA_SETTINGS_CARD_SHAPE,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                border = cardBorder,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isUnlocked) {
                            Icon(
                                painter = painterResource(id = rewardIconResId),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(2.dp),
                                tint = Color.Unspecified,
                            )
                            if (isEnabled) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopEnd,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Active",
                                        tint = aura.accentColor,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp),
                                            ),
                                    )
                                }
                            }
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = rewardIconResId),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().padding(2.dp).alpha(0.35f),
                                    tint = Color.Gray,
                                )
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = unlockableManager.getUnlockableNameRes(aura.id)?.let { stringResource(it) }
                                ?: unlockableManager.getUnlockableName(aura.id),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = settingsTitleColor(),
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isUnlocked) {
                                resolveAuraPalette(aura.id)?.description ?: aura.description
                            } else {
                                stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp,
                            letterSpacing = 0.2.sp,
                            color = if (isUnlocked) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        presets.forEach { preset ->
            val isUnlocked = unlockedUnlockables.contains(preset.unlockableId)
            val isActive = isUnlocked && preset.isActive()
            val achievementTitle = rewardToAchievementMap[preset.unlockableId]?.title ?: "Achievement"
            val rewardIconResId = remember(preset.unlockableId) {
                getRewardIconResourceId(preset.unlockableId, context)
            }

            val colors = AuroraTheme.colors
            val cardBgColor = if (colors.isDark) {
                colors.glass.copy(alpha = 0.08f)
            } else {
                Color.White
            }
            val cardElevation = if (colors.isDark || colors.isEInk) {
                0.dp
            } else {
                resolveAuroraElevation(colors, AuroraSurfaceLevel.Glass)
            }
            val cardBorder = if (isActive && isUnlocked) {
                BorderStroke(2.dp, preset.accentColor)
            } else {
                null
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
                shape = AURORA_SETTINGS_CARD_SHAPE,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                border = cardBorder,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isUnlocked) {
                            Icon(
                                painter = painterResource(id = rewardIconResId),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(2.dp),
                                tint = Color.Unspecified,
                            )
                            if (isActive) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopEnd,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Active",
                                        tint = preset.accentColor,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp),
                                            ),
                                    )
                                }
                            }
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = rewardIconResId),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().padding(2.dp).alpha(0.35f),
                                    tint = Color.Gray,
                                )
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = settingsTitleColor(),
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isUnlocked) {
                                preset.description
                            } else {
                                stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp,
                            letterSpacing = 0.2.sp,
                            color = if (isUnlocked) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }

                    if (isUnlocked) {
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
                    }
                }
            }
        }
    }
}

private fun getRewardIconResourceId(rewardId: String, context: android.content.Context): Int {
    val formattedId = when (rewardId) {
        "special_background_petal_storm" -> "ic_reward_background_petal_storm"
        "special_background_neon_orbit" -> "ic_reward_background_neon_orbit"
        "profile_nickname_effect_aurora_crown" -> "ic_reward_nickname_aurora_crown"
        "profile_nickname_effect_glitch_rune" -> "ic_reward_nickname_glitch_rune"
        "profile_nickname_effect_cipher" -> "ic_reward_nickname_cipher"
        "avatar_frame_neon" -> "ic_reward_frame_neon"
        "avatar_frame_hologram" -> "ic_reward_frame_hologram"
        "avatar_frame_prismatic" -> "ic_reward_frame_prismatic"
        "home_badge_orbit" -> "ic_reward_badge_orbit"
        "home_badge_crown" -> "ic_reward_badge_crown"
        "home_badge_shuriken" -> "ic_reward_badge_shuriken"
        else -> "ic_reward_$rewardId"
    }

    return try {
        val resourceId = context.resources.getIdentifier(
            formattedId,
            "drawable",
            context.packageName,
        )
        if (resourceId != 0) {
            resourceId
        } else {
            com.tadami.aurora.R.drawable.ic_badge_default
        }
    } catch (e: Exception) {
        com.tadami.aurora.R.drawable.ic_badge_default
    }
}
