package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Segment
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.player.VideoAspect
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.ceil

private const val NEXT_EPISODE_OVERLAY_THRESHOLD_SEC = 15f
private const val NEXT_EPISODE_MIN_DURATION_SEC = 60f

internal fun computeNextEpisodeCountdownSeconds(
    positionSec: Float,
    durationSec: Float,
    hasNextEpisode: Boolean,
    autoPlayEnabled: Boolean,
    repeatEpisode: Boolean,
    dismissedEpisodeId: Long?,
    currentEpisodeId: Long?,
): Int? {
    if (!hasNextEpisode || !autoPlayEnabled || repeatEpisode) return null
    if (currentEpisodeId != null && dismissedEpisodeId == currentEpisodeId) return null
    if (durationSec < NEXT_EPISODE_MIN_DURATION_SEC) return null
    val remaining = durationSec - positionSec
    if (remaining <= 0.5f || remaining > NEXT_EPISODE_OVERLAY_THRESHOLD_SEC) return null
    return ceil(remaining.toDouble()).toInt().coerceIn(1, NEXT_EPISODE_OVERLAY_THRESHOLD_SEC.toInt())
}

internal fun resolvePinchAspectTransition(
    cumulativeZoom: Float,
    currentAspect: VideoAspect,
): VideoAspect? {
    return when {
        cumulativeZoom >= 1.18f && currentAspect != VideoAspect.Crop -> VideoAspect.Crop
        cumulativeZoom <= 0.85f && currentAspect != VideoAspect.Fit -> VideoAspect.Fit
        else -> null
    }
}

internal fun resolveChapterTitleAtPosition(
    chapters: List<Segment>,
    targetPositionSec: Float,
): String? {
    if (chapters.isEmpty() || targetPositionSec < 0f) return null
    val matched = chapters
        .filter { it.start <= targetPositionSec }
        .maxByOrNull { it.start }
    return matched?.name?.takeIf { it.isNotBlank() }
}

@Composable
fun NextEpisodeCountdownOverlay(
    nextEpisodeTitle: String,
    secondsRemaining: Int,
    onPlayNextNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = AuroraTheme.colors.accent
    Surface(
        modifier = modifier.widthIn(min = 240.dp, max = 340.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xD910141C),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(AYMR.strings.player_next_episode_countdown, secondsRemaining),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = nextEpisodeTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPlayNextNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(AYMR.strings.player_next_episode_play_now))
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(MR.strings.action_cancel),
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}
