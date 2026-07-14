package eu.kanade.presentation.achievement.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.domain.easteregg.aurora.AuroraLocalization
import eu.kanade.presentation.achievement.utils.AchievementRevealHelper
import eu.kanade.presentation.easteregg.aurora.AuroraCodexScreen
import eu.kanade.presentation.easteregg.aurora.AuroraMaterialSpec
import eu.kanade.presentation.easteregg.aurora.auroraMetal
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Aurora-themed Achievement Card with glassmorphism and neon effects
 */
@Composable
fun AchievementCard(
    achievement: Achievement,
    progress: AchievementProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val isUnlocked = progress?.isUnlocked == true

    val context = LocalContext.current
    val manager = remember { Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>() }
    val managerState by manager.state.collectAsState()
    val isAuroraHeart = achievement.id == "aurora_heart"

    val payload = remember(managerState.unlocked) { manager.unlockedPayload() }
    val spec = remember(payload) { AuroraMaterialSpec.from(payload) }
    val cardBackgroundModifier = if (isAuroraHeart && spec != null) {
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .auroraMetal(spec)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    } else {
        Modifier
    }
    var showCodexDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Авто-открытие только ОДИН РАЗ на этап -- теперь через centralized manager
    LaunchedEffect(managerState.stageIndex, managerState.hintRevealed) {
        if (isAuroraHeart && !managerState.unlocked && manager.currentRiddle() != null) {
            if (manager.shouldAutoShowRiddleForCurrentStage() && !showCodexDialog) {
                manager.requestAutoShowForAchievements()
                // mark now centralized inside requestAutoShowForAchievements for browse one-time
            }
        }
    }

    val displayName = remember(achievement, progress, managerState, payload) {
        if (isAuroraHeart) {
            val title = when {
                managerState.unlocked && payload != null -> payload.achievementTitle ?: "Сердце Авроры"
                managerState.stageIndex > 0 -> "Сердце Авроры"
                managerState.hintRevealed -> "???"
                else -> "???"
            }
            if (title == "???") title else AuroraLocalization.translate(title).orEmpty()
        } else {
            AchievementRevealHelper.getDisplayName(achievement, progress)
        }
    }

    val vaguePrefix = stringResource(AYMR.strings.achievement_hint_vague_prefix)
    val directPrefix = stringResource(AYMR.strings.achievement_hint_direct_prefix)
    val obviousPrefix = stringResource(AYMR.strings.achievement_hint_obvious_prefix)
    val cluePrefix = stringResource(AYMR.strings.achievement_clue_prefix)

    val displayDesc =
        remember(
            achievement,
            progress,
            managerState,
            payload,
            vaguePrefix,
            directPrefix,
            obviousPrefix,
            cluePrefix,
        ) {
            if (isAuroraHeart) {
                val desc = when {
                    managerState.unlocked && payload != null ->
                        payload.achievementDescription
                            ?: "Скрыто северным сиянием"
                    else -> "Скрыто северным сиянием"
                }
                AuroraLocalization.translate(desc).orEmpty()
            } else {
                if (achievement.isHidden && !isUnlocked) {
                    AchievementRevealHelper.getDisplayDescription(
                        achievement = achievement,
                        progress = progress,
                        vaguePrefix = vaguePrefix,
                        directPrefix = directPrefix,
                        obviousPrefix = obviousPrefix,
                        cluePrefix = cluePrefix,
                    )
                } else {
                    achievement.description
                }
            }
        }

    val customIsUnlocked = if (isAuroraHeart) managerState.unlocked else isUnlocked

    val cardClick = {
        if (isAuroraHeart) {
            val hasActiveRiddle = !managerState.unlocked && manager.currentRiddle() != null
            if (managerState.unlocked) {
                // Полностью пройден — показываем архив + возможность пережить финал
                showCodexDialog = true
            } else if (hasActiveRiddle || managerState.hintRevealed) {
                // Есть активная загадка — request to centralized (overlay will show the host)
                manager.requestAutoShowForAchievements()
            }
        } else {
            onClick()
        }
    }

    val points = if (isAuroraHeart) (payload?.bonusPoints ?: 0) else achievement.points

    // Flat layout with a top hairline border
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(cardBackgroundModifier)
            .clickable(onClick = cardClick)
            .drawBehind {
                // Top hairline divider
                if (spec == null) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Achievement Icon with hexagonal shape
            if (achievement.isHidden && !customIsUnlocked && (!isAuroraHeart || !managerState.hintRevealed)) {
                HiddenAchievementIcon(
                    modifier = Modifier.size(48.dp),
                )
            } else {
                AchievementIcon(
                    achievement = achievement,
                    isUnlocked = customIsUnlocked,
                    modifier = Modifier.size(48.dp),
                    size = 48.dp,
                    useHexagonShape = true,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Content (Title & Description & Progress)
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayName,
                        color = if (customIsUnlocked) colors.textPrimary else colors.textSecondary.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Points Reward
                    if (points > 0) {
                        Text(
                            text = "+$points XP",
                            color = if (customIsUnlocked) colors.accent else colors.textSecondary.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Description
                if (!displayDesc.isNullOrBlank()) {
                    Text(
                        text = displayDesc,
                        color = colors.textSecondary.copy(alpha = if (customIsUnlocked) 0.8f else 0.5f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                }

                // Thin neon progress line (if locked and progress exists)
                if (!customIsUnlocked && achievement.threshold != null && progress != null && !isAuroraHeart) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ThinNeonProgressBar(
                        progress = progress.progress,
                        threshold = achievement.threshold ?: 1,
                    )
                }
            }

            // Unlocked Checkmark indicator on the right
            if (customIsUnlocked) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showCodexDialog) {
        Dialog(
            onDismissRequest = { showCodexDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AuroraCodexScreen(
                firstRiddle = manager.firstRiddle(),
                entries = manager.codex(),
                payload = manager.unlockedPayload(),
                onReplay = {
                    manager.unlockedPayload()?.let(eu.kanade.domain.easteregg.aurora.AuroraEchoBus::emitUnlocked)
                },
                onClose = { showCodexDialog = false },
            )
        }
    }
}

/**
 * Hidden achievement icon with lock and scanline effect
 */
@Composable
private fun HiddenAchievementIcon(
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = colors.textSecondary.copy(alpha = 0.2f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Sleek, thin neon progress line for achievements
 */
@Composable
private fun ThinNeonProgressBar(
    progress: Int,
    threshold: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val progressFraction = (progress.toFloat() / threshold).coerceIn(0f, 1f)

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "PROGRESS",
                color = colors.textSecondary.copy(alpha = 0.4f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = "$progress/$threshold",
                color = colors.textSecondary.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Thin progress line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.05f)),
        ) {
            if (progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(colors.accent),
                )
            }
        }
    }
}
