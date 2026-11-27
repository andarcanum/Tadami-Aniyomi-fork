package eu.kanade.presentation.reader.novel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

private const val IMAGE_VIEWER_MIN_SCALE = 1f
private const val IMAGE_VIEWER_MAX_SCALE = 5f
private const val IMAGE_VIEWER_DOUBLE_TAP_SCALE = 2.5f

/**
 * Fullscreen Pinch-to-Zoom viewer for light-novel illustrations and maps.
 * Reuses the active [NovelPluginImage] / [NovelReaderRefererImage] Coil model so cached and
 * plugin-encoded images open without re-fetching.
 */
@Composable
internal fun NovelImageViewerDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
    onSaveImage: () -> Unit,
    onShareImage: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val aurora = AuroraTheme.colors
    val referer = LocalNovelReaderReferer.current
    val imageModel = remember(imageUrl, referer) {
        if (NovelPluginImage.isSupported(imageUrl)) {
            NovelPluginImage(imageUrl)
        } else if (referer != null) {
            NovelReaderRefererImage(url = imageUrl, referer = referer)
        } else {
            imageUrl
        }
    }

    var scale by remember(imageUrl) { mutableFloatStateOf(IMAGE_VIEWER_MIN_SCALE) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var hudVisible by remember(imageUrl) { mutableStateOf(true) }

    val backdropColor = if (aurora.isEInk) {
        Color.White
    } else {
        Color.Black.copy(alpha = 0.96f)
    }
    val hudContainerColor = if (aurora.isEInk) {
        Color(0xFFF3F3F3)
    } else {
        Color(0xFF14121C).copy(alpha = 0.80f)
    }
    val hudBorderColor = if (aurora.isEInk) {
        Color.Black
    } else {
        Color.White.copy(alpha = 0.14f)
    }
    val hudContentColor = if (aurora.isEInk) Color.Black else Color(0xFFF4F2FA)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { stageSize = it }
                    .pointerInput(imageUrl) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(
                                IMAGE_VIEWER_MIN_SCALE,
                                IMAGE_VIEWER_MAX_SCALE,
                            )
                            val updatedOffset = if (newScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                val scaleRatio = newScale / scale
                                val center = Offset(stageSize.width / 2f, stageSize.height / 2f)
                                val focusDelta = centroid - center
                                val raw = (offset - focusDelta) * scaleRatio + focusDelta + pan
                                clampViewerOffset(raw, newScale, stageSize)
                            }
                            scale = newScale
                            offset = updatedOffset
                        }
                    }
                    .pointerInput(imageUrl) {
                        detectTapGestures(
                            onTap = { hudVisible = !hudVisible },
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.2f) {
                                    scale = IMAGE_VIEWER_MIN_SCALE
                                    offset = Offset.Zero
                                } else {
                                    val targetScale = IMAGE_VIEWER_DOUBLE_TAP_SCALE
                                    val center = Offset(stageSize.width / 2f, stageSize.height / 2f)
                                    val raw = (center - tapOffset) * (targetScale - 1f)
                                    scale = targetScale
                                    offset = clampViewerOffset(raw, targetScale, stageSize)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }

            AnimatedVisibility(
                visible = hudVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(hudContainerColor, CircleShape)
                            .border(1.dp, hudBorderColor, CircleShape)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                            tint = hudContentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(MR.strings.action_close),
                            color = hudContentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    val roundedZoom = ((scale * 10f).roundToInt() / 10f)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(hudContainerColor, CircleShape)
                            .border(1.dp, hudBorderColor, CircleShape)
                            .clickable {
                                scale = IMAGE_VIEWER_MIN_SCALE
                                offset = Offset.Zero
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (scale > 1.01f) {
                                "${roundedZoom}x · ${stringResource(AYMR.strings.novel_reader_image_viewer_reset_zoom)}"
                            } else {
                                "1.0x"
                            },
                            color = hudContentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = hudVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(hudContainerColor, RoundedCornerShape(999.dp))
                        .border(1.dp, hudBorderColor, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerHudAction(
                        icon = Icons.Default.Download,
                        label = stringResource(AYMR.strings.novel_reader_image_viewer_save),
                        tint = hudContentColor,
                        onClick = onSaveImage,
                    )
                    ViewerHudAction(
                        icon = Icons.Default.Share,
                        label = stringResource(AYMR.strings.novel_reader_image_viewer_share),
                        tint = hudContentColor,
                        onClick = onShareImage,
                    )
                    ViewerHudAction(
                        icon = Icons.Default.ContentCopy,
                        label = stringResource(AYMR.strings.novel_reader_image_viewer_copy_link),
                        tint = hudContentColor,
                        onClick = onCopyLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerHudAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = tint,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun clampViewerOffset(
    offset: Offset,
    scale: Float,
    stageSize: IntSize,
): Offset {
    if (stageSize.width <= 0 || stageSize.height <= 0 || scale <= 1f) return Offset.Zero
    val maxX = ((scale - 1f) * stageSize.width) / 2f
    val maxY = ((scale - 1f) * stageSize.height) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}
