package eu.kanade.presentation.download

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.resolveAuroraTabContainerColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.download.DownloadQueueUiItem
import eu.kanade.tachiyomi.ui.download.DownloadQueueUiModel
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as preferenceCollectAsState

@Composable
fun DownloadQueueItem(
    item: DownloadQueueUiItem,
    onMoveToTop: (() -> Unit)? = null,
    onMoveToBottom: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sectionColor = Color(android.graphics.Color.parseColor(item.section.accentHex))
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme = uiPreferences.appTheme().preferenceCollectAsState()
    val isAurora = theme.value.isAuroraStyle
    val auroraColors = AuroraTheme.colors
    val cardShape = RoundedCornerShape(22.dp)
    val cardContainerColor = if (isAurora) {
        resolveAuroraTabContainerColor(auroraColors)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    }
    val cardBorder = if (isAurora) {
        BorderStroke(0.75.dp, auroraMenuRimLightBrush(auroraColors))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
    val coverRequest = remember(item.coverData) {
        buildAuroraCoverImageRequest(context, item.coverData)
    }
    val coverPlaceholder = rememberAuroraCoverPlaceholderPainter(AuroraCoverPlaceholderVariant.Portrait)
    val actionColor = if (isAurora) auroraColors.accent else sectionColor
    val statusColor = when (item.status) {
        DownloadQueueUiModel.QueueStatus.QUEUED -> actionColor
        DownloadQueueUiModel.QueueStatus.DOWNLOADING -> actionColor
        DownloadQueueUiModel.QueueStatus.DOWNLOADED -> Color(0xFF3FA66F)
        DownloadQueueUiModel.QueueStatus.FAILED -> Color(0xFFE0555B)
        DownloadQueueUiModel.QueueStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    var menuExpanded by remember(item.itemId) { mutableStateOf(false) }
    val hasActions = onMoveToTop != null || onMoveToBottom != null || onCancel != null
    val statusLineText = when (item.status) {
        DownloadQueueUiModel.QueueStatus.FAILED -> item.description.ifBlank { item.progressText }
        else -> item.progressText
    }
    val progressDetailText = item.progressDetailText
    val showProgressBar = item.status == DownloadQueueUiModel.QueueStatus.DOWNLOADING ||
        item.status == DownloadQueueUiModel.QueueStatus.QUEUED ||
        item.status == DownloadQueueUiModel.QueueStatus.DOWNLOADED
    val progressBarValue = when (item.status) {
        DownloadQueueUiModel.QueueStatus.DOWNLOADED -> 1f
        DownloadQueueUiModel.QueueStatus.DOWNLOADING,
        DownloadQueueUiModel.QueueStatus.QUEUED,
        -> item.progressFraction.coerceIn(0f, 1f).coerceAtLeast(0.01f)
        else -> item.progressFraction.coerceIn(0f, 1f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = cardShape,
        color = cardContainerColor,
        border = cardBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp), // Generous high-end spacing
        ) {
            // Premium poster cover
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 90.dp) // Sleeker cover dimensions
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.2f)),
            ) {
                AsyncImage(
                    model = coverRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = coverPlaceholder,
                    fallback = coverPlaceholder,
                )
            }

            // Content column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).sp,
                    ),
                    color = if (isAurora) auroraColors.textPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.subtitle.isNotEmpty()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = (
                            if (isAurora) {
                                auroraColors.textSecondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            ).copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }

                if (statusLineText.isNotEmpty()) {
                    Text(
                        text = statusLineText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = statusColor.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }

                if (progressDetailText.isNotEmpty()) {
                    Text(
                        text = progressDetailText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = (
                            if (isAurora) {
                                auroraColors.textSecondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            ).copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }

                // Progress bar follows the live queue state.
                if (showProgressBar) {
                    LinearProgressIndicator(
                        progress = {
                            progressBarValue.coerceAtLeast(
                                if (item.status ==
                                    DownloadQueueUiModel.QueueStatus.QUEUED
                                ) {
                                    0.01f
                                } else {
                                    0f
                                },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(5.dp) // Sleek thin bar
                            .clip(RoundedCornerShape(999.dp)),
                        color = statusColor,
                        trackColor = statusColor.copy(alpha = 0.12f),
                    )
                }
            }

            // Controls column on the right
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (hasActions) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.08f))
                            .border(BorderStroke(1.5.dp, statusColor.copy(alpha = 0.28f)), CircleShape)
                            .clickable { menuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = when (item.status) {
                                DownloadQueueUiModel.QueueStatus.DOWNLOADING -> Icons.Outlined.Pause
                                DownloadQueueUiModel.QueueStatus.QUEUED -> Icons.Outlined.Pause
                                DownloadQueueUiModel.QueueStatus.DOWNLOADED -> Icons.Filled.MoreVert
                                DownloadQueueUiModel.QueueStatus.FAILED -> Icons.Filled.Close
                                else -> Icons.Filled.MoreVert
                            },
                            contentDescription = stringResource(AYMR.strings.download_queue_actions),
                            tint = statusColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    DownloadQueueActionMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onMoveToTop = onMoveToTop,
                        onMoveToBottom = onMoveToBottom,
                        onCancel = onCancel,
                    )
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}
