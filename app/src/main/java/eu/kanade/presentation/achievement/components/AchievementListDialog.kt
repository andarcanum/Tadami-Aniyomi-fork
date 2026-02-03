package eu.kanade.presentation.achievement.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.achievement.model.Achievement

/**
 * Full-screen dialog showing list of newly unlocked achievements
 * Opens when user taps "Смотреть" on group notification
 */
@Composable
fun AchievementListDialog(
    achievements: List<Achievement>,
    onDismiss: () -> Unit,
) {
    val totalPoints = achievements.sumOf { it.points }

    AdaptiveSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header with title and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Новые достижения!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = AuroraTheme.colors.textPrimary,
                    )
                    Text(
                        text = "${achievements.size} достижений • +$totalPoints очков",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AuroraTheme.colors.textSecondary,
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = AuroraTheme.colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List of achievements
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(achievements) { achievement ->
                    AchievementListItem(
                        achievement = achievement,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Close button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraTheme.colors.accent,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Отлично!",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AchievementListItem(
    achievement: Achievement,
) {
    val colors = AuroraTheme.colors
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "scale",
    )

    val isRare = achievement.points >= 50

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = if (isRare) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF6B00).copy(alpha = 0.2f),
                            Color(0xFFFFD700).copy(alpha = 0.1f),
                        ),
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = 0.15f),
                            colors.surface,
                        ),
                    )
                },
            )
            .border(
                width = if (isRare) 2.dp else 1.dp,
                color = if (isRare) {
                    Color(0xFFFFD700).copy(alpha = 0.5f)
                } else {
                    colors.accent.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Achievement icon
        AchievementIcon(
            achievement = achievement,
            isUnlocked = true,
            modifier = Modifier.size(56.dp),
            size = 56.dp,
            useHexagonShape = true,
        )

        // Content
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isRare) Color(0xFFFF6B00) else colors.textPrimary,
                )

                if (isRare) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            achievement.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 2,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (isRare) Color(0xFFFFD700) else colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "+${achievement.points} очков",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isRare) Color(0xFFFFD700) else colors.accent,
                )
            }
        }
    }
}
