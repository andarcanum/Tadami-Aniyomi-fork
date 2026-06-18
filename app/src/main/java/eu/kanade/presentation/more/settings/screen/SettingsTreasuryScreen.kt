package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_SHAPE
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.auroraCardStyle
import eu.kanade.presentation.more.settings.settingsTitleColor
import eu.kanade.presentation.more.settings.widget.AppThemePreviewItem
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.home.components.AvatarFrameDecorations
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.ui.graphics.drawscope.scale as drawScopeScale

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
        val profileTitleKey by userProfilePreferences.profileTitle().collectAsStateWithLifecycle()
        val specialBackgroundStyleKey by uiPreferences.specialBackgroundStyle().collectAsStateWithLifecycle()
        val amoled by uiPreferences.themeDarkAmoled().collectAsStateWithLifecycle()

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

        val titlePresets = listOf(
            TreasuryPreset(
                unlockableId = "title_trinity_initiate",
                title = stringResource(AYMR.strings.treasury_title_trinity_initiate_title),
                description = stringResource(AYMR.strings.treasury_title_trinity_initiate_desc),
                accentColor = Color(0xFF9C7CFF),
                isActive = { profileTitleKey == "title_trinity_initiate" },
                onApply = { userProfilePreferences.profileTitle().set("title_trinity_initiate") },
                onDeactivate = { userProfilePreferences.profileTitle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "title_finisher",
                title = stringResource(AYMR.strings.treasury_title_finisher_title),
                description = stringResource(AYMR.strings.treasury_title_finisher_desc),
                accentColor = Color(0xFFFFD36E),
                isActive = { profileTitleKey == "title_finisher" },
                onApply = { userProfilePreferences.profileTitle().set("title_finisher") },
                onDeactivate = { userProfilePreferences.profileTitle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "title_closer",
                title = stringResource(AYMR.strings.treasury_title_closer_title),
                description = stringResource(AYMR.strings.treasury_title_closer_desc),
                accentColor = Color(0xFFFFB86B),
                isActive = { profileTitleKey == "title_closer" },
                onApply = { userProfilePreferences.profileTitle().set("title_closer") },
                onDeactivate = { userProfilePreferences.profileTitle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "title_deep_reader",
                title = stringResource(AYMR.strings.treasury_title_deep_reader_title),
                description = stringResource(AYMR.strings.treasury_title_deep_reader_desc),
                accentColor = Color(0xFF5DE7D8),
                isActive = { profileTitleKey == "title_deep_reader" },
                onApply = { userProfilePreferences.profileTitle().set("title_deep_reader") },
                onDeactivate = { userProfilePreferences.profileTitle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "title_rank_4",
                title = stringResource(AYMR.strings.treasury_title_rank_4_title),
                description = stringResource(AYMR.strings.treasury_title_rank_4_desc),
                accentColor = Color(0xFFFFE08A),
                isActive = { profileTitleKey == "title_rank_4" },
                onApply = { userProfilePreferences.profileTitle().set("title_rank_4") },
                onDeactivate = { userProfilePreferences.profileTitle().set("none") },
            ),
        )

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
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_trinity_prism",
                title = stringResource(AYMR.strings.treasury_nickname_trinity_prism_title),
                description = stringResource(AYMR.strings.treasury_nickname_trinity_prism_desc),
                accentColor = Color(0xFF9C7CFF),
                isActive = { nicknameEffectKey == "trinity_prism" },
                onApply = { userProfilePreferences.nicknameEffect().set("trinity_prism") },
                onDeactivate = { userProfilePreferences.nicknameEffect().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_shadow_crown",
                title = stringResource(AYMR.strings.treasury_nickname_shadow_crown_title),
                description = stringResource(AYMR.strings.treasury_nickname_shadow_crown_desc),
                accentColor = Color(0xFFB36BFF),
                isActive = { nicknameEffectKey == "shadow_crown" },
                onApply = { userProfilePreferences.nicknameEffect().set("shadow_crown") },
                onDeactivate = { userProfilePreferences.nicknameEffect().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_rank_sigils",
                title = stringResource(AYMR.strings.treasury_nickname_rank_sigils_title),
                description = stringResource(AYMR.strings.treasury_nickname_rank_sigils_desc),
                accentColor = Color(0xFFFFE08A),
                isActive = { nicknameEffectKey == "rank_sigils" },
                onApply = { userProfilePreferences.nicknameEffect().set("rank_sigils") },
                onDeactivate = { userProfilePreferences.nicknameEffect().set("none") },
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
            TreasuryPreset(
                unlockableId = "avatar_frame_trinity_orbit",
                title = stringResource(AYMR.strings.treasury_frame_trinity_orbit_title),
                description = stringResource(AYMR.strings.treasury_frame_trinity_orbit_desc),
                accentColor = Color(0xFF9C7CFF),
                isActive = { avatarFrameStyleKey == "trinity_orbit" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("trinity_orbit") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_deep_archive",
                title = stringResource(AYMR.strings.treasury_frame_deep_archive_title),
                description = stringResource(AYMR.strings.treasury_frame_deep_archive_desc),
                accentColor = Color(0xFF5DE7D8),
                isActive = { avatarFrameStyleKey == "deep_archive" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("deep_archive") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_hybrid_scroll",
                title = stringResource(AYMR.strings.treasury_frame_hybrid_scroll_title),
                description = stringResource(AYMR.strings.treasury_frame_hybrid_scroll_desc),
                accentColor = Color(0xFFFFB86B),
                isActive = { avatarFrameStyleKey == "hybrid_scroll" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("hybrid_scroll") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_ascendant",
                title = stringResource(AYMR.strings.treasury_frame_ascendant_title),
                description = stringResource(AYMR.strings.treasury_frame_ascendant_desc),
                accentColor = Color(0xFFFFE08A),
                isActive = { avatarFrameStyleKey == "ascendant" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("ascendant") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
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
            TreasuryPreset(
                unlockableId = "home_badge_trinity",
                title = stringResource(AYMR.strings.treasury_badge_trinity_title),
                description = stringResource(AYMR.strings.treasury_badge_trinity_desc),
                accentColor = Color(0xFF9C7CFF),
                isActive = { homeBadgeStyleKey == "trinity" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("trinity") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_finisher",
                title = stringResource(AYMR.strings.treasury_badge_finisher_title),
                description = stringResource(AYMR.strings.treasury_badge_finisher_desc),
                accentColor = Color(0xFFFFD36E),
                isActive = { homeBadgeStyleKey == "finisher" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("finisher") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_immersion",
                title = stringResource(AYMR.strings.treasury_badge_immersion_title),
                description = stringResource(AYMR.strings.treasury_badge_immersion_desc),
                accentColor = Color(0xFF5DE7D8),
                isActive = { homeBadgeStyleKey == "immersion" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("immersion") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_ascendant",
                title = stringResource(AYMR.strings.treasury_badge_ascendant_title),
                description = stringResource(AYMR.strings.treasury_badge_ascendant_desc),
                accentColor = Color(0xFFFFE08A),
                isActive = { homeBadgeStyleKey == "ascendant" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("ascendant") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
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
            TreasuryPreset(
                unlockableId = "special_background_trinity_constellation",
                title = stringResource(AYMR.strings.treasury_bg_trinity_constellation_title),
                description = stringResource(AYMR.strings.treasury_bg_trinity_constellation_desc),
                accentColor = Color(0xFF9C7CFF),
                isActive = { specialBackgroundStyleKey == "trinity_constellation" },
                onApply = { uiPreferences.specialBackgroundStyle().set("trinity_constellation") },
                onDeactivate = { uiPreferences.specialBackgroundStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "special_background_deep_space_archive",
                title = stringResource(AYMR.strings.treasury_bg_deep_space_archive_title),
                description = stringResource(AYMR.strings.treasury_bg_deep_space_archive_desc),
                accentColor = Color(0xFF5DE7D8),
                isActive = { specialBackgroundStyleKey == "deep_space_archive" },
                onApply = { uiPreferences.specialBackgroundStyle().set("deep_space_archive") },
                onDeactivate = { uiPreferences.specialBackgroundStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "special_background_shadow_realm",
                title = stringResource(AYMR.strings.treasury_bg_shadow_realm_title),
                description = stringResource(AYMR.strings.treasury_bg_shadow_realm_desc),
                accentColor = Color(0xFFB36BFF),
                isActive = { specialBackgroundStyleKey == "shadow_realm" },
                onApply = { uiPreferences.specialBackgroundStyle().set("shadow_realm") },
                onDeactivate = { uiPreferences.specialBackgroundStyle().set("none") },
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
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.treasury_nickname_preview),
            ) {
                val colors = AuroraTheme.colors
                val defaultUserName = stringResource(AYMR.strings.treasury_default_user_name)
                val decoratedName = remember(name, defaultUserName) {
                    name.trim().ifEmpty { defaultUserName }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .auroraCardStyle(colors, AURORA_SETTINGS_CARD_SHAPE, applyDarkRimLight = true)
                        .semantics(mergeDescendants = true) {
                            contentDescription = decoratedName
                        },
                    shape = AURORA_SETTINGS_CARD_SHAPE,
                    colors = CardDefaults.cardColors(
                        containerColor = if (!colors.isDark && !colors.isEInk) {
                            Color.Transparent
                        } else {
                            resolveAuroraMoreCardContainerColor(colors)
                        },
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = if (colors.isEInk) {
                        BorderStroke(
                            width = 1.dp,
                            color = resolveAuroraMoreCardBorderColor(colors),
                        )
                    } else {
                        null
                    },
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val infiniteTransition = rememberInfiniteTransition(label = "identity-preview-blob")
                        val wavePhase by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = (2 * kotlin.math.PI).toFloat(),
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 6000,
                                    easing = androidx.compose.animation.core.LinearEasing,
                                ),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "identity-preview-blob-phase",
                        )

                        Canvas(modifier = Modifier.matchParentSize()) {
                            val width = size.width
                            val height = size.height
                            val cosPhase = kotlin.math.cos(wavePhase.toDouble()).toFloat()
                            val sinPhase = kotlin.math.sin(wavePhase.toDouble()).toFloat()
                            val alphaMultiplier = if (colors.isDark) 0.14f else 0.08f

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        TreasuryViolet.copy(alpha = 0.85f * alphaMultiplier),
                                        Color.Transparent,
                                    ),
                                    center = Offset(
                                        width * 0.25f + width * 0.12f * cosPhase,
                                        height * 0.50f + height * 0.25f * sinPhase,
                                    ),
                                    radius = size.minDimension * 0.65f,
                                ),
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        TreasuryCyan.copy(alpha = 0.65f * alphaMultiplier),
                                        Color.Transparent,
                                    ),
                                    center = Offset(
                                        width * 0.75f - width * 0.15f * cosPhase,
                                        height * 0.50f - height * 0.20f * sinPhase,
                                    ),
                                    radius = size.minDimension * 0.70f,
                                ),
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        TreasuryGold.copy(alpha = 0.55f * alphaMultiplier),
                                        Color.Transparent,
                                    ),
                                    center = Offset(
                                        width * 0.50f + width * 0.10f * sinPhase,
                                        height * 0.40f + height * 0.15f * cosPhase,
                                    ),
                                    radius = size.minDimension * 0.50f,
                                ),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(76.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val auraTransition = rememberInfiniteTransition(label = "avatar-aura")
                                val auraRotation by auraTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(
                                            durationMillis = 4000,
                                            easing = androidx.compose.animation.core.LinearEasing,
                                        ),
                                        repeatMode = RepeatMode.Restart,
                                    ),
                                    label = "aura-rotation",
                                )

                                if (avatarFrameStyleKey != "none") {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = 1f
                                                scaleY = 1f
                                                rotationZ = auraRotation
                                            },
                                    ) {
                                        val radius = size.minDimension / 2f - 2.dp.toPx()
                                        drawCircle(
                                            brush = Brush.sweepGradient(
                                                colors = listOf(
                                                    TreasuryViolet,
                                                    TreasuryCyan,
                                                    TreasuryGold,
                                                    TreasuryViolet,
                                                ),
                                                center = center,
                                            ),
                                            radius = radius,
                                            style = Stroke(width = 3.dp.toPx()),
                                        )
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    colors.accent.copy(alpha = 0.25f),
                                                    Color.Transparent,
                                                ),
                                                center = center,
                                                radius = radius + 8.dp.toPx(),
                                            ),
                                        )
                                    }
                                }

                                val avatarModifier = Modifier
                                    .size(if (avatarFrameStyleKey != "none") 62.dp else 70.dp)
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

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                eu.kanade.tachiyomi.ui.home.StyledNicknameText(
                                    text = decoratedName,
                                    nicknameStyle = activeNicknameStyle,
                                    badgeStyleKey = homeBadgeStyleKey,
                                )
                                if (profileTitleKey != "none") {
                                    Text(
                                        text = profileTitleDisplayName(profileTitleKey),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )

        val treasuryProgress = remember(
            titlePresets,
            profileEffectPresets,
            avatarFramePresets,
            homePresets,
            specialBackgroundPresets,
            unlockedUnlockables,
        ) {
            calculateTreasuryRewardProgress(
                unlockedUnlockables = unlockedUnlockables,
                presetIds = buildList {
                    addAll(titlePresets.map { it.unlockableId })
                    addAll(profileEffectPresets.map { it.unlockableId })
                    addAll(avatarFramePresets.map { it.unlockableId })
                    addAll(homePresets.map { it.unlockableId })
                    addAll(specialBackgroundPresets.map { it.unlockableId })
                },
                auraIds = allAuraPalettes().map { it.id },
                hiddenThemes = AppTheme.entries.filter(AppTheme::isHidden),
            )
        }
        val totalTreasuryRewards = treasuryProgress.total
        val unlockedTreasuryRewards = treasuryProgress.unlocked

        preferences.add(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.treasury_vault_group_title),
            ) {
                TreasuryVaultHero(
                    unlocked = unlockedTreasuryRewards,
                    total = totalTreasuryRewards,
                    activeTheme = uiPreferences.appTheme().get().name.replace("_", " "),
                    activeAura = uiPreferences.enabledAuras().get().firstOrNull()
                        ?.removePrefix("aura_")
                        ?.replace("_", " ")
                        ?: "none",
                    amoled = amoled,
                )
            },
        )

        if (BuildConfig.DEBUG) {
            preferences.add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = debugBypassLocksPref,
                    title = stringResource(AYMR.strings.pref_debug_bypass_treasury_locks),
                    subtitle = stringResource(AYMR.strings.pref_debug_bypass_treasury_locks_summary),
                ),
            )
        }

        preferences.add(
            Preference.PreferenceItem.CustomPreference(title = stringResource(AYMR.strings.treasury_themes)) {
                TreasuryThemeSelector(
                    uiPreferences = uiPreferences,
                    unlockedUnlockables = unlockedUnlockables,
                    rewardToAchievementMap = rewardToAchievementMap,
                )
            },
        )

        preferences.add(
            Preference.PreferenceItem.CustomPreference(title = stringResource(AYMR.strings.treasury_auras)) {
                TreasuryAuraSelector(
                    uiPreferences = uiPreferences,
                    unlockableManager = unlockableManager,
                    unlockedUnlockables = unlockedUnlockables,
                    rewardToAchievementMap = rewardToAchievementMap,
                    amoled = amoled,
                )
            },
        )

        preferences.add(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.treasury_background_effects),
            ) {
                TreasuryToggleSelector(
                    title = stringResource(AYMR.strings.treasury_background_effects),
                    subtitle = stringResource(AYMR.strings.treasury_background_effects_subtitle),
                    presets = specialBackgroundPresets,
                    unlockedUnlockables = unlockedUnlockables,
                    rewardToAchievementMap = rewardToAchievementMap,
                    amoled = amoled,
                )
            },
        )

        preferences.add(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.treasury_profile_titles),
            ) {
                TreasuryToggleSelector(
                    title = stringResource(AYMR.strings.treasury_profile_titles),
                    subtitle = stringResource(AYMR.strings.treasury_profile_titles_subtitle),
                    presets = titlePresets,
                    unlockedUnlockables = unlockedUnlockables,
                    rewardToAchievementMap = rewardToAchievementMap,
                    amoled = amoled,
                )
            },
        )

        preferences.add(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.treasury_profile_effects),
            ) {
                TreasuryToggleSelector(
                    title = stringResource(AYMR.strings.treasury_profile_effects),
                    subtitle = stringResource(AYMR.strings.treasury_profile_effects_subtitle),
                    presets = profileEffectPresets,
                    unlockedUnlockables = unlockedUnlockables,
                    rewardToAchievementMap = rewardToAchievementMap,
                    amoled = amoled,
                )
            },
        )

        preferences.add(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.treasury_avatar_frames),
            ) {
                TreasuryToggleSelector(
                    title = stringResource(AYMR.strings.treasury_avatar_frames),
                    subtitle = stringResource(AYMR.strings.treasury_avatar_frames_subtitle),
                    presets = avatarFramePresets,
                    unlockedUnlockables = unlockedUnlockables,
                    rewardToAchievementMap = rewardToAchievementMap,
                    amoled = amoled,
                )
            },
        )

        preferences.add(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.treasury_home_hub_rewards),
            ) {
                TreasuryToggleSelector(
                    title = stringResource(AYMR.strings.treasury_home_hub_rewards),
                    subtitle = stringResource(AYMR.strings.treasury_home_hub_rewards_subtitle),
                    presets = homePresets,
                    unlockedUnlockables = unlockedUnlockables,
                    rewardToAchievementMap = rewardToAchievementMap,
                    amoled = amoled,
                )
            },
        )

        return preferences
    }
}

@Composable
private fun profileTitleDisplayName(titleId: String): String {
    return when (titleId) {
        "title_trinity_initiate" -> stringResource(AYMR.strings.treasury_title_trinity_initiate_title)
        "title_finisher" -> stringResource(AYMR.strings.treasury_title_finisher_title)
        "title_closer" -> stringResource(AYMR.strings.treasury_title_closer_title)
        "title_deep_reader" -> stringResource(AYMR.strings.treasury_title_deep_reader_title)
        "title_rank_4" -> stringResource(AYMR.strings.treasury_title_rank_4_title)
        else -> titleId.removePrefix("title_").replace("_", " ").replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }
}

@Composable
private fun TreasuryVaultHero(
    unlocked: Int,
    total: Int,
    activeTheme: String,
    activeAura: String,
    amoled: Boolean,
) {
    val colors = AuroraTheme.colors
    val percent = if (total == 0) 0 else (unlocked * 100 / total)
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val compact = screenWidth < 380
    val isAmoled = colors.isDark && amoled
    val nightStart = if (colors.isDark) {
        if (isAmoled) Color.Black else Color(0xFF080912)
    } else {
        Color(0xFFF8F2FF)
    }
    val nightEnd = if (colors.isDark) {
        if (isAmoled) Color.Black else Color(0xFF151022)
    } else {
        Color(0xFFFFFBF0)
    }
    val titleColor = if (colors.isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val bodyColor = if (colors.isDark) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant

    val shape = RoundedCornerShape(30.dp)
    val borderColor = if (colors.isDark) {
        if (isAmoled) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.075f)
    } else {
        Color.Black.copy(alpha = 0.055f)
    }
    val orbSize = if (compact) 104.dp else 122.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .drawBehind {
                if (!colors.isDark && !colors.isEInk) {
                    val radius = 30.dp.toPx()
                    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)

                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()

                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    // 1. Neutral shadow
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.035f),
                        topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                        size = androidx.compose.ui.geometry.Size(
                            width = size.width - neutralInset * 2,
                            height = size.height,
                        ),
                        cornerRadius = cornerRadius,
                    )

                    // 2. Accent glow (colors.accent)
                    drawRoundRect(
                        color = colors.accent.copy(alpha = 0.025f),
                        topLeft = Offset(x = warmInset, y = warmOffsetY),
                        size = androidx.compose.ui.geometry.Size(
                            width = size.width - warmInset * 2,
                            height = size.height,
                        ),
                        cornerRadius = cornerRadius,
                    )
                }
            }
            .background(
                Brush.verticalGradient(
                    listOf(
                        nightStart,
                        nightEnd,
                        TreasuryGold.copy(
                            alpha = if (colors.isDark) {
                                if (isAmoled) 0.07f else 0.10f
                            } else {
                                0.16f
                            },
                        ),
                    ),
                ),
                shape = shape,
            )
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 22.dp, vertical = 24.dp),
    ) {
        TreasuryConstellationBackdrop(
            modifier = Modifier.matchParentSize(),
            isDark = colors.isDark,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(AYMR.strings.treasury_vault_header),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TreasuryGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(AYMR.strings.treasury_vault_headline),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        lineHeight = 31.sp,
                        maxLines = if (compact) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                TreasuryCoreOrb(
                    percent = percent,
                    modifier = Modifier.size(orbSize),
                )
            }

            Text(
                text = stringResource(AYMR.strings.treasury_vault_progress, unlocked, total, percent),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            TreasuryVaultDock(
                activeTheme = activeTheme,
                activeAura = activeAura,
            )
        }
    }
}

@Composable
private fun TreasuryConstellationBackdrop(
    modifier: Modifier = Modifier,
    isDark: Boolean,
) {
    Canvas(modifier = modifier) {
        val alpha = if (isDark) 1f else 0.72f

        // Keep the atmosphere, but push large blobs away from the headline.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(TreasuryViolet.copy(alpha = 0.16f * alpha), Color.Transparent),
                center = Offset(size.width * 0.68f, size.height * 0.22f),
                radius = size.minDimension * 0.50f,
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(TreasuryGold.copy(alpha = 0.14f * alpha), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.18f),
                radius = size.minDimension * 0.42f,
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(TreasuryCyan.copy(alpha = 0.10f * alpha), Color.Transparent),
                center = Offset(size.width * 0.78f, size.height * 0.82f),
                radius = size.minDimension * 0.54f,
            ),
        )

        val stars = listOf(
            Offset(size.width * 0.42f, size.height * 0.22f),
            Offset(size.width * 0.55f, size.height * 0.34f),
            Offset(size.width * 0.72f, size.height * 0.28f),
            Offset(size.width * 0.86f, size.height * 0.42f),
            Offset(size.width * 0.62f, size.height * 0.72f),
            Offset(size.width * 0.82f, size.height * 0.78f),
        )
        stars.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = Color.White.copy(alpha = 0.045f * alpha),
                start = start,
                end = end,
                strokeWidth = 0.8.dp.toPx(),
            )
        }
        stars.forEachIndexed { index, star ->
            drawCircle(
                color = if (index % 3 == 0) {
                    TreasuryGold.copy(alpha = 0.50f * alpha)
                } else {
                    Color.White.copy(alpha = 0.30f * alpha)
                },
                radius = if (index % 3 == 0) 1.9.dp.toPx() else 1.15.dp.toPx(),
                center = star,
            )
        }
    }
}

@Composable
private fun TreasuryCoreOrb(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb-pulse")
    val ringPulseState = infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ring-pulse",
    )
    val ringAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ring-alpha",
    )

    val isComplete = percent >= 100

    val completeRotationState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "complete-rotation",
    )

    val completeGlowRadiusState = infiniteTransition.animateFloat(
        initialValue = 1.2f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "complete-glow-radius",
    )

    val innerBackingColors = remember(isComplete) {
        if (isComplete) {
            listOf(
                TreasuryCyan.copy(alpha = 0.45f),
                TreasuryViolet.copy(alpha = 0.30f),
                TreasuryGold.copy(alpha = 0.15f),
                Color.Transparent,
            )
        } else {
            listOf(
                TreasuryGold.copy(alpha = 0.35f),
                TreasuryViolet.copy(alpha = 0.15f),
                Color.Transparent,
            )
        }
    }

    val sphereColors = remember(isComplete) {
        if (isComplete) {
            listOf(
                Color.White,
                TreasuryGold.copy(alpha = 0.95f),
                TreasuryViolet.copy(alpha = 0.85f),
                TreasuryCyan.copy(alpha = 0.75f),
                Color(0xFF0F0A1C),
            )
        } else {
            listOf(
                Color.White,
                TreasuryGold.copy(alpha = 0.9f),
                TreasuryViolet.copy(alpha = 0.7f),
                Color(0xFF1B1328),
            )
        }
    }

    val sweepColors = remember {
        listOf(
            TreasuryViolet,
            TreasuryCyan,
            TreasuryGold,
            TreasuryViolet,
        )
    }

    val sphereBrush = remember(isComplete, sphereColors) {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                val baseRadius = size.minDimension * 0.40f
                return RadialGradientShader(
                    colors = sphereColors,
                    colorStops = null,
                    center = Offset(size.width * 0.4f, size.height * 0.4f),
                    radius = baseRadius,
                    tileMode = TileMode.Clamp,
                )
            }
        }
    }

    val innerBackingBrush = remember(isComplete, innerBackingColors) {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                val baseRadius = size.minDimension * 0.40f
                val innerBackingRadius = if (isComplete) {
                    baseRadius * completeGlowRadiusState.value
                } else {
                    baseRadius *
                        1.2f
                }
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                return RadialGradientShader(
                    colors = innerBackingColors,
                    colorStops = null,
                    center = centerOffset,
                    radius = innerBackingRadius,
                    tileMode = TileMode.Clamp,
                )
            }
        }
    }

    val sweepBrush = remember(sweepColors) {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                return SweepGradientShader(
                    colors = sweepColors,
                    colorStops = null,
                    center = centerOffset,
                )
            }
        }
    }

    val progressDescription = stringResource(AYMR.strings.treasury_vault_progress_description, percent)
    Box(
        modifier = modifier.semantics {
            contentDescription = progressDescription
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val centerOffset = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.minDimension * 0.40f
            val ringAlpha = ringAlphaState.value
            val ringPulse = ringPulseState.value

            // Pulsing outer orbit ring
            drawCircle(
                color = if (isComplete) TreasuryCyan.copy(alpha = ringAlpha) else TreasuryGold.copy(alpha = ringAlpha),
                radius = baseRadius * ringPulse,
                style = Stroke(width = 1.2.dp.toPx()),
            )

            // Inner glass shadow / backing
            drawCircle(
                brush = innerBackingBrush,
            )

            // 3D Glass Sphere Body (radial gradient shifted slightly up-left for lighting)
            drawCircle(
                brush = sphereBrush,
            )

            // Lens highlight reflection (specular gloss)
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = baseRadius * 0.22f,
                center = Offset(size.width * 0.38f, size.height * 0.38f),
            )

            // Visual Progress Arc & Orbiting Comet around the Sphere (No numbers)
            val sweepAngle = (percent.toFloat() / 100f) * 360f

            if (isComplete) {
                rotate(degrees = completeRotationState.value, pivot = centerOffset) {
                    // Active progress arc rotating at 100%
                    drawArc(
                        brush = sweepBrush,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - baseRadius * 1.28f, centerOffset.y - baseRadius * 1.28f),
                        size = androidx.compose.ui.geometry.Size(baseRadius * 2.56f, baseRadius * 2.56f),
                        style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )

                    // Orbiting moon showing the progress with particles trail
                    for (i in 0..4) {
                        val angle = -90f - i * 12f
                        val radAngle = Math.toRadians(angle.toDouble())
                        val moonX = centerOffset.x + kotlin.math.cos(radAngle).toFloat() * (baseRadius * 1.28f)
                        val moonY = centerOffset.y + kotlin.math.sin(radAngle).toFloat() * (baseRadius * 1.28f)
                        val opacity = (1.0f - (i * 0.18f)).coerceIn(0f, 1f)
                        val sizeMultiplier = (1.0f - (i * 0.12f)).coerceIn(0.2f, 1f)

                        drawCircle(
                            color = TreasuryGold.copy(alpha = 0.45f * opacity),
                            radius = 5.dp.toPx() * sizeMultiplier,
                            center = Offset(moonX, moonY),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = opacity),
                            radius = 2.5.dp.toPx() * sizeMultiplier,
                            center = Offset(moonX, moonY),
                        )
                    }
                }
            } else {
                // Track ring
                drawCircle(
                    color = Color.White.copy(alpha = if (ringAlpha > 0.5f) 0.08f else 0.05f),
                    radius = baseRadius * 1.28f,
                    center = centerOffset,
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                // Active progress arc
                drawArc(
                    color = TreasuryGold,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - baseRadius * 1.28f, centerOffset.y - baseRadius * 1.28f),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 2.56f, baseRadius * 2.56f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )

                // Orbiting moon showing the progress
                val radAngle = Math.toRadians((sweepAngle - 90).toDouble())
                val moonX = centerOffset.x + kotlin.math.cos(radAngle).toFloat() * (baseRadius * 1.28f)
                val moonY = centerOffset.y + kotlin.math.sin(radAngle).toFloat() * (baseRadius * 1.28f)

                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = Offset(moonX, moonY),
                )
                drawCircle(
                    color = TreasuryGold.copy(alpha = 0.45f),
                    radius = 5.dp.toPx(),
                    center = Offset(moonX, moonY),
                )
            }
        }
    }
}

@Composable
private fun TreasuryVaultDock(
    activeTheme: String,
    activeAura: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val dividerColor = if (colors.isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TreasuryVaultDockItem(
            label = stringResource(AYMR.strings.treasury_vault_pill_theme),
            value = activeTheme,
            accent = TreasuryGold,
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(34.dp)
                .background(dividerColor),
        )

        TreasuryVaultDockItem(
            label = stringResource(AYMR.strings.treasury_vault_pill_aura),
            value = activeAura,
            accent = TreasuryCyan,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TreasuryVaultDockItem(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(accent.copy(alpha = 0.85f), CircleShape),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp,
                ),
                color = if (colors.isDark) {
                    Color.White.copy(alpha = 0.46f)
                } else {
                    Color.Black.copy(alpha = 0.48f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value.uppercase(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
                color = if (colors.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TreasuryRewardPaths() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(AYMR.strings.treasury_vault_group_title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        TreasuryPathCard(
            title = stringResource(AYMR.strings.treasury_path_trinity_title),
            subtitle = stringResource(AYMR.strings.treasury_path_trinity_subtitle),
            accent = TreasuryViolet,
            index = "I",
        )
        TreasuryPathCard(
            title = stringResource(AYMR.strings.treasury_path_immersion_title),
            subtitle = stringResource(AYMR.strings.treasury_path_immersion_subtitle),
            accent = TreasuryCyan,
            index = "II",
        )
        TreasuryPathCard(
            title = stringResource(AYMR.strings.treasury_path_ascension_title),
            subtitle = stringResource(AYMR.strings.treasury_path_ascension_subtitle),
            accent = TreasuryGold,
            index = "III",
        )
    }
}

@Composable
private fun TreasuryPathCard(
    title: String,
    subtitle: String,
    accent: Color,
    index: String,
) {
    val isDark = AuroraTheme.colors.isDark
    val container = if (isDark) Color.White.copy(alpha = 0.055f) else Color.White.copy(alpha = 0.86f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, accent.copy(alpha = if (isDark) 0.36f else 0.48f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                    ),
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f))
                        .border(1.dp, accent.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = index,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = settingsTitleColor(),
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    )
                }
            }
        }
    }
}

private val TreasuryGold = Color(0xFFFFD36E)
private val TreasuryViolet = Color(0xFF9C7CFF)
private val TreasuryCyan = Color(0xFF5DE7D8)

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
            accentColor = TreasuryGold,
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
            accentColor = TreasuryCyan,
        ),
    )

    var parentLayoutCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    TreasurySectionStage(
        title = stringResource(AYMR.strings.treasury_themes),
        subtitle = stringResource(AYMR.strings.treasury_themes_subtitle),
        accent = TreasuryGold,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    parentLayoutCoordinates = coordinates
                },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(treasuryThemes.size) { index ->
                val spec = treasuryThemes[index]
                val theme = spec.theme
                val rewardId = "theme_${theme.name}"
                val isUnlocked = isThemePreviewUnlocked(theme, unlockedUnlockables)
                val achievementTitle = rewardToAchievementMap[rewardId]?.title
                    ?: stringResource(AYMR.strings.treasury_fallback_achievement)
                val isSelected = appTheme == theme

                TreasuryThemePoster(
                    spec = spec,
                    index = index,
                    isUnlocked = isUnlocked,
                    isSelected = isSelected,
                    achievementTitle = achievementTitle,
                    amoled = amoled,
                    parentLayoutCoordinates = parentLayoutCoordinates,
                    onClick = {
                        if (isUnlocked) {
                            uiPreferences.appTheme().set(theme)
                            (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TreasuryThemePoster(
    spec: TreasuryExclusiveThemeSpec,
    index: Int,
    isUnlocked: Boolean,
    isSelected: Boolean,
    achievementTitle: String,
    amoled: Boolean,
    parentLayoutCoordinates: androidx.compose.ui.layout.LayoutCoordinates?,
    onClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "theme-poster-${spec.theme.name}")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600 + index * 420),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "poster-pulse-${spec.theme.name}",
    )
    val title = spec.theme.titleRes?.let { stringResource(it) } ?: spec.theme.name

    var itemCenterOffsetX by remember { mutableStateOf(0f) }
    val parentWidth = parentLayoutCoordinates?.size?.width?.toFloat() ?: 0f

    val shape = RoundedCornerShape(26.dp)
    val colors = AuroraTheme.colors
    val borderColor = if (isSelected && isUnlocked) {
        spec.accentColor.copy(alpha = 0.82f)
    } else {
        if (colors.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    }
    val borderWidth = if (isSelected && isUnlocked) 1.5.dp else 1.dp

    Box(
        modifier = Modifier
            .width(238.dp)
            .height(472.dp)
            .onGloballyPositioned { coordinates ->
                parentLayoutCoordinates?.let { parent ->
                    val itemLeft = parent.localPositionOf(coordinates, Offset.Zero).x
                    itemCenterOffsetX = itemLeft + coordinates.size.width / 2f
                }
            }
            .graphicsLayer {
                if (parentWidth > 0f) {
                    val parentCenter = parentWidth / 2f
                    val distance = itemCenterOffsetX - parentCenter
                    val maxDistance = parentWidth / 1.5f
                    var pct = distance / maxDistance
                    pct = pct.coerceIn(-1f, 1f)

                    val rotY = pct * 18f
                    val rotZ = pct * -3.5f
                    val scale = 1f - (kotlin.math.abs(pct) * 0.06f)

                    rotationY = rotY
                    rotationZ = rotZ
                    scaleX = scale
                    scaleY = scale
                    cameraDistance = 8f * density

                    val baseAlpha = if (isUnlocked) 1f else 0.58f
                    alpha = baseAlpha - (kotlin.math.abs(pct) * 0.2f).coerceIn(0f, 0.2f)
                } else {
                    alpha = if (isUnlocked) 1f else 0.58f
                }
            }
            .springPress(enabled = isUnlocked, onClick = onClick)
            .border(borderWidth, borderColor, shape)
            .clip(shape)
            .clickable(enabled = isUnlocked, onClick = onClick),
    ) {
        TreasuryPosterBackdrop(
            accent = spec.accentColor,
            pulseProvider = { pulse },
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(spec.rarity).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = spec.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(254.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                TachiyomiTheme(appTheme = spec.theme, amoled = amoled) {
                    AppThemePreviewItem(
                        selected = isSelected && isUnlocked,
                        onClick = onClick,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 12.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.36f),
                                ),
                            ),
                        ),
                )
                if (!isUnlocked) {
                    TreasuryLockVeil(achievementTitle = achievementTitle)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 27.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(spec.tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f),
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isUnlocked) {
                        if (isSelected) {
                            stringResource(AYMR.strings.treasury_exclusive_active)
                        } else {
                            stringResource(AYMR.strings.treasury_exclusive_unlocked)
                        }
                    } else {
                        stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) spec.accentColor else MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TreasuryPosterBackdrop(
    accent: Color,
    pulseProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pulse = pulseProvider()
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF080810),
                    Color(0xFF171225),
                    accent.copy(alpha = 0.20f),
                ),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.44f), Color.Transparent),
                center = Offset(size.width * 0.80f, size.height * 0.18f),
                radius = size.minDimension * 0.64f * pulse,
            ),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = size.minDimension * 0.56f,
            center = Offset(size.width * 0.18f, size.height * 0.70f),
            style = Stroke(width = 1.1.dp.toPx()),
        )
        drawLine(
            color = accent.copy(alpha = 0.36f),
            start = Offset(size.width * 0.08f, size.height * 0.18f),
            end = Offset(size.width * 0.92f, size.height * 0.82f),
            strokeWidth = 1.4.dp.toPx(),
        )
    }
}

@Composable
private fun TreasuryLockVeil(
    achievementTitle: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.64f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(18.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = stringResource(AYMR.strings.treasury_cd_locked),
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = achievementTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TreasuryAuraSelector(
    uiPreferences: UiPreferences,
    unlockableManager: UnlockableManager,
    unlockedUnlockables: Set<String>,
    rewardToAchievementMap: Map<String, Achievement>,
    amoled: Boolean,
) {
    val enabledAuras by uiPreferences.enabledAuras().collectAsStateWithLifecycle()
    val auraPalettes = remember { allAuraPalettes() }
    val context = LocalContext.current

    TreasurySectionStage(
        title = stringResource(AYMR.strings.treasury_auras),
        subtitle = stringResource(AYMR.strings.treasury_auras_subtitle),
        accent = TreasuryCyan,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            auraPalettes.forEachIndexed { index, aura ->
                val isUnlocked = unlockedUnlockables.contains(aura.id)
                val isEnabled = enabledAuras.contains(aura.id)
                val achievementTitle = rewardToAchievementMap[aura.id]?.title
                    ?: stringResource(AYMR.strings.treasury_fallback_achievement)
                val rewardIconResId = remember(aura.id) { getRewardIconResourceId(aura.id, context) }

                TreasuryAuraChannel(
                    index = index,
                    title = stringResource(aura.titleRes),
                    description = if (isUnlocked) {
                        stringResource(aura.descriptionRes)
                    } else {
                        stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                    },
                    iconResId = rewardIconResId,
                    accent = aura.accentColor,
                    isUnlocked = isUnlocked,
                    isEnabled = isEnabled,
                    amoled = amoled,
                    onToggle = {
                        uiPreferences.enabledAuras().set(
                            if (isEnabled) {
                                emptySet()
                            } else {
                                setOf(aura.id)
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun TreasuryAuraChannel(
    index: Int,
    title: String,
    description: String,
    iconResId: Int,
    accent: Color,
    isUnlocked: Boolean,
    isEnabled: Boolean,
    amoled: Boolean,
    onToggle: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aura-channel-$title")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 9000 + index * 700)),
        label = "aura-rotation-$title",
    )
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200 + index * 220),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aura-breathe-$title",
    )

    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 12.dp, bottomEnd = 30.dp, bottomStart = 12.dp)
    val colors = AuroraTheme.colors
    val isAmoled = colors.isDark && amoled
    val baseBg1 = if (colors.isDark) {
        if (isAmoled) Color.Black else Color(0xFF0D0D16)
    } else {
        Color.White
    }
    val baseBg2 = if (colors.isDark) {
        if (isAmoled) Color.Black else Color(0xFF08080E)
    } else {
        Color(0xFFF9F9FB)
    }

    val bgBrush = if (!colors.isDark && !colors.isEInk) {
        if (isEnabled) {
            Brush.verticalGradient(
                listOf(
                    accent.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.68f),
                    Color.White.copy(alpha = 0.60f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.78f),
                    Color.White.copy(alpha = 0.68f),
                    Color.White.copy(alpha = 0.60f),
                ),
            )
        }
    } else if (isEnabled) {
        Brush.horizontalGradient(
            listOf(
                accent.copy(alpha = if (colors.isDark) 0.08f else 0.04f),
                baseBg1,
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                baseBg1,
                baseBg2,
            ),
        )
    }
    val borderBrush = if (!colors.isDark && !colors.isEInk) {
        if (isEnabled) {
            Brush.verticalGradient(
                listOf(
                    accent.copy(alpha = 0.60f),
                    accent.copy(alpha = 0.30f),
                    accent.copy(alpha = 0.15f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.75f),
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.12f),
                ),
            )
        }
    } else if (isEnabled) {
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.40f),
                accent.copy(alpha = 0.08f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                if (colors.isDark) {
                    if (isAmoled) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f)
                } else {
                    Color.Black.copy(alpha = 0.08f)
                },
                if (colors.isDark) {
                    if (isAmoled) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f)
                } else {
                    Color.Black.copy(alpha = 0.02f)
                },
            ),
        )
    }
    val borderWidth = if (isEnabled) 1.5.dp else 1.dp

    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val indentDp = if (screenWidth < 400) 8.dp else 22.dp

    val activeDesc = stringResource(AYMR.strings.treasury_toggle_active)
    val availableDesc = stringResource(AYMR.strings.treasury_toggle_available)
    val lockedDesc = stringResource(AYMR.strings.treasury_toggle_locked_with_desc, description)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (index % 2 == 0) 0.dp else indentDp)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "$title, " +
                    if (isEnabled) {
                        activeDesc
                    } else if (isUnlocked) {
                        availableDesc
                    } else {
                        lockedDesc
                    }
            }
            .graphicsLayer {
                alpha = if (isUnlocked) 1f else 0.50f
            }
            .springPress(enabled = isUnlocked, onClick = onToggle)
            .drawBehind {
                if (!colors.isDark && !colors.isEInk) {
                    val outline = shape.createOutline(size, layoutDirection, this)

                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()

                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    val center = Offset(size.width / 2f, size.height / 2f)

                    // 1. Neutral shadow
                    val neutralScaleX = (size.width - neutralInset * 2) / size.width
                    val neutralScaleY = (size.height - neutralInset * 2) / size.height
                    drawScopeScale(scaleX = neutralScaleX, scaleY = neutralScaleY, pivot = center) {
                        translate(left = 0f, top = neutralOffsetY) {
                            drawOutline(
                                outline = outline,
                                color = Color.Black.copy(alpha = 0.035f),
                            )
                        }
                    }

                    // 2. Warm shadow (accent color)
                    val warmScaleX = (size.width - warmInset * 2) / size.width
                    val warmScaleY = (size.height - warmInset * 2) / size.height
                    drawScopeScale(scaleX = warmScaleX, scaleY = warmScaleY, pivot = center) {
                        translate(left = 0f, top = warmOffsetY) {
                            drawOutline(
                                outline = outline,
                                color = accent.copy(alpha = 0.025f),
                            )
                        }
                    }
                }
            }
            .background(bgBrush, shape)
            .border(borderWidth, borderBrush, shape)
            .clickable(enabled = isUnlocked, onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = if (isEnabled) rotation else 0f },
            ) {
                val pulse = if (isEnabled) breathe else 1f
                drawCircle(
                    color = accent.copy(alpha = 0.24f * pulse),
                    radius = size.minDimension * 0.46f,
                    style = Stroke(width = 1.6.dp.toPx()),
                )
                drawLine(
                    color = accent.copy(alpha = 0.60f * pulse),
                    start = Offset(size.width * 0.50f, 0f),
                    end = Offset(size.width * 0.50f, size.height * 0.20f),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .scale(if (isEnabled) 1.08f else 1f),
                tint = if (isUnlocked) Color.Unspecified else Color.Gray,
            )
            if (!isUnlocked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(AYMR.strings.treasury_cd_locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = settingsTitleColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isEnabled) {
                    val checkScale by infiniteTransition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1200),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "check-scale",
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(AYMR.strings.treasury_cd_active),
                        tint = accent,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = checkScale
                                scaleY = checkScale
                            },
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
                color = if (isUnlocked) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = if (isEnabled) 0.72f else 0.18f)),
            )
        }
    }
}

@Composable
private fun TreasuryToggleSelector(
    title: String,
    subtitle: String,
    presets: List<TreasuryPreset>,
    unlockedUnlockables: Set<String>,
    rewardToAchievementMap: Map<String, Achievement>,
    amoled: Boolean,
) {
    val context = LocalContext.current
    val stageAccent = presets.firstOrNull()?.accentColor ?: TreasuryViolet

    TreasurySectionStage(
        title = title,
        subtitle = subtitle,
        accent = stageAccent,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            presets.forEachIndexed { index, preset ->
                val isUnlocked = unlockedUnlockables.contains(preset.unlockableId)
                val isActive = isUnlocked && preset.isActive()
                val achievementTitle = rewardToAchievementMap[preset.unlockableId]?.title
                    ?: stringResource(AYMR.strings.treasury_fallback_achievement)
                val rewardIconResId = remember(preset.unlockableId) {
                    getRewardIconResourceId(preset.unlockableId, context)
                }

                TreasuryArtifactShard(
                    index = index,
                    preset = preset,
                    iconResId = rewardIconResId,
                    isUnlocked = isUnlocked,
                    isActive = isActive,
                    description = if (isUnlocked) {
                        preset.description
                    } else {
                        stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                    },
                    amoled = amoled,
                    onToggle = {
                        if (isActive) {
                            preset.onDeactivate()
                        } else {
                            preset.onApply()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TreasuryArtifactShard(
    index: Int,
    preset: TreasuryPreset,
    iconResId: Int,
    isUnlocked: Boolean,
    isActive: Boolean,
    description: String,
    amoled: Boolean,
    onToggle: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "artifact-${preset.unlockableId}")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400 + index * 180),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "artifact-glow-${preset.unlockableId}",
    )
    val shape = when (index % 3) {
        0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 14.dp)
        1 -> RoundedCornerShape(topStart = 10.dp, topEnd = 28.dp, bottomEnd = 12.dp, bottomStart = 28.dp)
        else -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 4.dp, bottomStart = 28.dp)
    }

    val colors = AuroraTheme.colors
    val isAmoled = colors.isDark && amoled
    val baseBg1 = if (colors.isDark) {
        if (isAmoled) Color.Black else Color(0xFF0D0D16)
    } else {
        Color.White
    }
    val baseBg2 = if (colors.isDark) {
        if (isAmoled) Color.Black else Color(0xFF08080E)
    } else {
        Color(0xFFF9F9FB)
    }

    val bgBrush = if (!colors.isDark && !colors.isEInk) {
        if (isActive) {
            Brush.verticalGradient(
                listOf(
                    preset.accentColor.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.68f),
                    Color.White.copy(alpha = 0.60f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.78f),
                    Color.White.copy(alpha = 0.68f),
                    Color.White.copy(alpha = 0.60f),
                ),
            )
        }
    } else if (isActive) {
        Brush.horizontalGradient(
            listOf(
                preset.accentColor.copy(alpha = if (colors.isDark) 0.08f else 0.04f),
                baseBg1,
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                baseBg1,
                baseBg2,
            ),
        )
    }
    val borderBrush = if (!colors.isDark && !colors.isEInk) {
        if (isActive) {
            Brush.verticalGradient(
                listOf(
                    preset.accentColor.copy(alpha = 0.60f),
                    preset.accentColor.copy(alpha = 0.30f),
                    preset.accentColor.copy(alpha = 0.15f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.75f),
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.12f),
                ),
            )
        }
    } else if (isActive) {
        Brush.linearGradient(
            listOf(
                preset.accentColor.copy(alpha = 0.40f),
                preset.accentColor.copy(alpha = 0.08f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                if (colors.isDark) {
                    if (isAmoled) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f)
                } else {
                    Color.Black.copy(alpha = 0.08f)
                },
                if (colors.isDark) {
                    if (isAmoled) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f)
                } else {
                    Color.Black.copy(alpha = 0.02f)
                },
            ),
        )
    }
    val borderWidth = if (isActive) 1.5.dp else 1.dp

    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val indentDp = if (screenWidth < 400) 8.dp else 18.dp

    val activeDesc = stringResource(AYMR.strings.treasury_toggle_active)
    val availableDesc = stringResource(AYMR.strings.treasury_toggle_available)
    val lockedDesc = stringResource(AYMR.strings.treasury_toggle_locked_with_desc, description)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (index % 2 == 0) 0.dp else indentDp,
                end = if (index % 2 == 0) indentDp else 0.dp,
            )
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${preset.title}, " +
                    if (isActive) {
                        activeDesc
                    } else if (isUnlocked) {
                        availableDesc
                    } else {
                        lockedDesc
                    }
            }
            .graphicsLayer {
                alpha = if (isUnlocked) 1f else 0.48f
            }
            .springPress(enabled = isUnlocked, onClick = onToggle)
            .drawBehind {
                if (!colors.isDark && !colors.isEInk) {
                    val outline = shape.createOutline(size, layoutDirection, this)

                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()

                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    val center = Offset(size.width / 2f, size.height / 2f)

                    // 1. Neutral shadow
                    val neutralScaleX = (size.width - neutralInset * 2) / size.width
                    val neutralScaleY = (size.height - neutralInset * 2) / size.height
                    drawScopeScale(scaleX = neutralScaleX, scaleY = neutralScaleY, pivot = center) {
                        translate(left = 0f, top = neutralOffsetY) {
                            drawOutline(
                                outline = outline,
                                color = Color.Black.copy(alpha = 0.035f),
                            )
                        }
                    }

                    // 2. Warm shadow (accent color)
                    val warmScaleX = (size.width - warmInset * 2) / size.width
                    val warmScaleY = (size.height - warmInset * 2) / size.height
                    drawScopeScale(scaleX = warmScaleX, scaleY = warmScaleY, pivot = center) {
                        translate(left = 0f, top = warmOffsetY) {
                            drawOutline(
                                outline = outline,
                                color = preset.accentColor.copy(alpha = 0.025f),
                            )
                        }
                    }
                }
            }
            .background(bgBrush, shape)
            .border(borderWidth, borderBrush, shape)
            .clickable(enabled = isUnlocked, onClick = onToggle)
            .padding(16.dp),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val glowAlpha = if (isActive) glow else 0.5f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(preset.accentColor.copy(alpha = 0.24f * glowAlpha), Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 0.18f),
                    radius = size.minDimension * 0.72f,
                ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(58.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(
                        color = preset.accentColor.copy(alpha = 0.18f * glow),
                        radius = size.minDimension * 0.48f,
                    )
                    drawCircle(
                        color = preset.accentColor.copy(alpha = 0.44f),
                        radius = size.minDimension * 0.44f,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    tint = if (isUnlocked) Color.Unspecified else Color.Gray,
                )
                if (!isUnlocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(AYMR.strings.treasury_cd_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = settingsTitleColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isActive) {
                        val checkScale by infiniteTransition.animateFloat(
                            initialValue = 0.85f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1200),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "check-scale",
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(AYMR.strings.treasury_cd_active),
                            tint = preset.accentColor,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = checkScale
                                    scaleY = checkScale
                                },
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp,
                    color = if (isUnlocked) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TreasurySectionStage(
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(accent, accent.copy(alpha = 0.12f)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = settingsTitleColor(),
                    lineHeight = 28.sp,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                )
            }
        }
        content()
    }
}

internal data class TreasuryRewardProgress(
    val unlocked: Int,
    val total: Int,
)

internal fun calculateTreasuryRewardProgress(
    unlockedUnlockables: Set<String>,
    presetIds: List<String>,
    auraIds: List<String>,
    hiddenThemes: List<AppTheme>,
): TreasuryRewardProgress {
    val distinctPresetIds = presetIds.distinct()
    val distinctAuraIds = auraIds.distinct()
    val distinctHiddenThemes = hiddenThemes.distinct()

    val unlockedPresets = distinctPresetIds.count(unlockedUnlockables::contains)
    val unlockedAuras = distinctAuraIds.count(unlockedUnlockables::contains)
    val unlockedThemes = distinctHiddenThemes.count { theme ->
        isThemePreviewUnlocked(theme, unlockedUnlockables)
    }

    return TreasuryRewardProgress(
        unlocked = unlockedPresets + unlockedAuras + unlockedThemes,
        total = distinctPresetIds.size + distinctAuraIds.size + distinctHiddenThemes.size,
    )
}

private fun getRewardIconResourceId(rewardId: String, context: android.content.Context): Int {
    val formattedId = when (rewardId) {
        "special_background_petal_storm" -> "ic_reward_background_petal_storm"
        "special_background_neon_orbit" -> "ic_reward_background_neon_orbit"
        "title_trinity_initiate" -> "ic_reward_nickname_rank_sigils"
        "title_finisher" -> "ic_reward_badge_finisher"
        "title_closer" -> "ic_reward_badge_finisher"
        "title_deep_reader" -> "ic_reward_badge_immersion"
        "title_rank_4" -> "ic_reward_nickname_rank_sigils"
        "profile_nickname_effect_aurora_crown" -> "ic_reward_nickname_aurora_crown"
        "profile_nickname_effect_glitch_rune" -> "ic_reward_nickname_glitch_rune"
        "profile_nickname_effect_cipher" -> "ic_reward_nickname_cipher"
        "profile_nickname_effect_trinity_prism" -> "ic_reward_nickname_trinity_prism"
        "profile_nickname_effect_shadow_crown" -> "ic_reward_nickname_shadow_crown"
        "profile_nickname_effect_rank_sigils" -> "ic_reward_nickname_rank_sigils"
        "avatar_frame_neon" -> "ic_reward_frame_neon"
        "avatar_frame_hologram" -> "ic_reward_frame_hologram"
        "avatar_frame_prismatic" -> "ic_reward_frame_prismatic"
        "home_badge_orbit" -> "ic_reward_badge_orbit"
        "home_badge_crown" -> "ic_reward_badge_crown"
        "home_badge_shuriken" -> "ic_reward_badge_shuriken"
        "home_badge_trinity" -> "ic_reward_badge_trinity"
        "home_badge_finisher" -> "ic_reward_badge_finisher"
        "home_badge_immersion" -> "ic_reward_badge_immersion"
        "home_badge_ascendant" -> "ic_reward_badge_ascendant"
        "avatar_frame_trinity_orbit" -> "ic_reward_frame_trinity_orbit"
        "avatar_frame_deep_archive" -> "ic_reward_frame_deep_archive"
        "avatar_frame_hybrid_scroll" -> "ic_reward_frame_hybrid_scroll"
        "avatar_frame_ascendant" -> "ic_reward_frame_ascendant"
        "special_background_trinity_constellation" -> "ic_reward_background_trinity_constellation"
        "special_background_deep_space_archive" -> "ic_reward_background_deep_space_archive"
        "special_background_shadow_realm" -> "ic_reward_background_shadow_realm"
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

@Composable
private fun Modifier.springPress(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "spring-press-scale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try {
                        awaitRelease()
                    } finally {
                        isPressed = false
                    }
                },
                onTap = { onClick() },
            )
        }
}
