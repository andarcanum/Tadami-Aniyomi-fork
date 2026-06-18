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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.achievement.utils.AchievementRevealHelper
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

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

    // Flat layout with a top hairline border
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                // Top hairline divider
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Achievement Icon with hexagonal shape
            if (achievement.isHidden && !isUnlocked) {
                HiddenAchievementIcon(
                    modifier = Modifier.size(48.dp),
                )
            } else {
                AchievementIcon(
                    achievement = achievement,
                    isUnlocked = isUnlocked,
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
                        text = AchievementRevealHelper.getDisplayName(achievement, progress),
                        color = if (isUnlocked) colors.textPrimary else colors.textSecondary.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Points Reward
                    if (achievement.points > 0) {
                        Text(
                            text = "+${achievement.points} XP",
                            color = if (isUnlocked) colors.accent else colors.textSecondary.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Description
                val displayDesc = if (achievement.isHidden && !isUnlocked) {
                    AchievementRevealHelper.getDisplayDescription(
                        achievement = achievement,
                        progress = progress,
                        vaguePrefix = stringResource(AYMR.strings.achievement_hint_vague_prefix),
                        directPrefix = stringResource(AYMR.strings.achievement_hint_direct_prefix),
                        obviousPrefix = stringResource(AYMR.strings.achievement_hint_obvious_prefix),
                        cluePrefix = stringResource(AYMR.strings.achievement_clue_prefix),
                    )
                } else {
                    achievement.description
                }

                if (!displayDesc.isNullOrBlank()) {
                    Text(
                        text = displayDesc,
                        color = colors.textSecondary.copy(alpha = if (isUnlocked) 0.8f else 0.5f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                }

                // Thin neon progress line (if locked and progress exists)
                if (!isUnlocked && achievement.threshold != null && progress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ThinNeonProgressBar(
                        progress = progress.progress,
                        threshold = achievement.threshold ?: 1,
                    )
                }
            }

            // Unlocked Checkmark indicator on the right
            if (isUnlocked) {
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
