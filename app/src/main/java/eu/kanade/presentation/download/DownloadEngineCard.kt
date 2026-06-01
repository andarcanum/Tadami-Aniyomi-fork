package eu.kanade.presentation.download

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.resolveAuroraTabContainerColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.download.engine.DownloadEngineSnapshot
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as preferenceCollectAsState

@Composable
fun DownloadEngineCard(
    snapshot: DownloadEngineSnapshot,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme = uiPreferences.appTheme().preferenceCollectAsState()
    val isAurora = theme.value.isAuroraStyle
    val auroraColors = AuroraTheme.colors

    // Bento-inspired design constants
    val cardShape = RoundedCornerShape(32.dp)
    val cardBorder = if (isAurora) {
        BorderStroke(1.dp, auroraMenuRimLightBrush(auroraColors))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
    val cardContainerColor = if (isAurora) {
        resolveAuroraTabContainerColor(auroraColors)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    }
    val textPrimary = if (isAurora) auroraColors.textPrimary else MaterialTheme.colorScheme.onSurface
    val textSecondary = if (isAurora) auroraColors.textSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val engineState = snapshot.engineState()
    val isRussian = LocalContext.current.resources.configuration.locales.get(0).language == "ru"

    // Sleek Calibrated Palette (Theme primary accent, slate/zinc neutrals)
    val accentColor = if (isAurora) auroraColors.textPrimary else MaterialTheme.colorScheme.primary
    val textMuted = if (isAurora) {
        auroraColors.textSecondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = 0.6f,
        )
    }

    // Distinct status colors to provide intuitive feedback (Emerald active, Amber waiting, Red stopped)
    val engineStateColor = when (engineState) {
        EngineState.ACTIVE -> Color(0xFF10B981) // Vivid Emerald Green
        EngineState.WAITING -> Color(0xFFF59E0B) // Sand/Amber
        EngineState.STOPPED -> Color(0xFFEF4444) // Red/Rose
        EngineState.IDLE -> textMuted
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Engine Head: Integrated Title and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), RoundedCornerShape(12.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.03f), Color.White.copy(alpha = 0.01f)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = if (engineState == EngineState.ACTIVE) Color(0xFF10B981) else accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    if (engineState == EngineState.ACTIVE) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 4.dp, end = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                                .border(BorderStroke(3.dp, Color(0xFF10B981).copy(alpha = 0.15f)), CircleShape),
                        )
                    }
                }

                Text(
                    text = if (isRussian) "Диспетчер" else stringResource(AYMR.strings.download_engine_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 17.sp, // Decreased size to prevent awkward wrapping
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).sp,
                    ),
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                DownloadEngineStatePill(
                    label = when (engineState) {
                        EngineState.ACTIVE -> if (isRussian) "Скачивание" else "Downloading"
                        EngineState.WAITING -> if (isRussian) "В очереди" else "Waiting"
                        EngineState.STOPPED -> if (isRussian) "Приостановлено" else "Paused"
                        EngineState.IDLE -> if (isRussian) "Ожидание" else "Idle"
                    },
                    color = engineStateColor,
                )
            }

            // Statistics Horizontal Row: Strict Anti-Emoji compliant, elegant separators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.01f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(20.dp))
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Metric 1: Active
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.ArrowDownward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${snapshot.activeCount}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRussian) "Активно" else "Active",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textMuted,
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.05f)))

                // Metric 2: Queued
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${snapshot.queuedCount}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRussian) "В очереди" else "Queued",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textMuted,
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.05f)))

                // Metric 3: Done
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${snapshot.completedCount}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRussian) "Завершено" else "Done",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textMuted,
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.05f)))

                // Metric 4: Free Space
                Column(
                    modifier = Modifier.weight(1.3f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Formatter.formatFileSize(context, snapshot.freeSpaceBytes),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRussian) "Свободно" else "Free",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textMuted,
                    )
                }
            }

            DownloadEngineActionsRow(
                isRunning = snapshot.isRunning,
                activeCount = snapshot.activeCount,
                queuedCount = snapshot.queuedCount,
                onPauseAll = onPauseAll,
                onResumeAll = onResumeAll,
                onCancelAll = onCancelAll,
            )
        }
    }
}

@Composable
private fun DownloadEngineStatePill(
    label: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = RoundedCornerShape(99.dp),
        border = BorderStroke(0.75.dp, color.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

private enum class EngineState {
    ACTIVE,
    WAITING,
    STOPPED,
    IDLE,
}

private fun DownloadEngineSnapshot.engineState(): EngineState {
    return when {
        isRunning -> EngineState.ACTIVE
        activeCount > 0 -> EngineState.ACTIVE
        queuedCount > 0 -> EngineState.WAITING
        failedCount > 0 -> EngineState.STOPPED
        else -> EngineState.IDLE
    }
}
