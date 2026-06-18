package eu.kanade.presentation.download

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun DownloadEngineActionsRow(
    isRunning: Boolean,
    activeCount: Int,
    queuedCount: Int,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val isPaused = !isRunning && queuedCount > 0
    val isIdle = !isRunning && queuedCount == 0
    val pauseResumeLabel = if (isRunning) {
        stringResource(AYMR.strings.download_engine_pause_all)
    } else {
        stringResource(AYMR.strings.download_engine_resume_all)
    }

    // Bound to dynamic theme colors for custom-tailored branding
    val activeAccent = MaterialTheme.colorScheme.primary
    val pausedAccent = MaterialTheme.colorScheme.secondary
    val idleAccent = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val actionColor = when {
        isIdle -> idleAccent
        isPaused -> pausedAccent
        else -> activeAccent
    }

    val actionBg = actionColor.copy(alpha = 0.04f)
    val actionBorder = BorderStroke(1.dp, actionColor.copy(alpha = 0.2f))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Pause / Resume Button
        Surface(
            onClick = {
                if (isRunning) onPauseAll() else onResumeAll()
            },
            enabled = queuedCount > 0 || activeCount > 0 || isRunning,
            modifier = Modifier
                .weight(1.6f)
                .height(52.dp),
            shape = RoundedCornerShape(24.dp), // Organic pill shape
            color = actionBg,
            border = actionBorder,
            contentColor = actionColor,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = pauseResumeLabel,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pauseResumeLabel,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = (-0.01).sp),
                )
            }
        }

        // Cancel / Clear Button
        val cancelColor = Color(0xFFEF4444) // Premium Rose Red
        val cancelBg = cancelColor.copy(alpha = 0.04f)
        val cancelBorder = BorderStroke(1.dp, cancelColor.copy(alpha = 0.2f))

        Surface(
            onClick = onCancelAll,
            modifier = Modifier
                .weight(1.1f)
                .height(52.dp),
            shape = RoundedCornerShape(24.dp), // Organic pill shape
            color = cancelBg,
            border = cancelBorder,
            contentColor = cancelColor,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(AYMR.strings.download_engine_clear),
                    modifier = Modifier.size(18.dp),
                    tint = cancelColor,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(AYMR.strings.download_engine_clear),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = (-0.01).sp),
                )
            }
        }
    }
}
