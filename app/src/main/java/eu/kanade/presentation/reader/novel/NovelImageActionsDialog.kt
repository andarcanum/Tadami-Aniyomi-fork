package eu.kanade.presentation.reader.novel

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

@Composable
internal fun NovelImageActionsDialog(
    imageUrl: String,
    onDismissRequest: () -> Unit,
    onSaveImage: () -> Unit,
    onShareImage: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val aurora = AuroraTheme.colors
    val baseScheme = MaterialTheme.colorScheme
    var sheetReveal by remember { mutableFloatStateOf(0f) }

    val supportsBlurBehind = eu.kanade.presentation.util.rememberSupportsBlurBehind(aurora.isEInk)

    val sheetContainer = remember(aurora.isDark, aurora.isEInk, supportsBlurBehind) {
        when {
            aurora.isEInk -> baseScheme.surfaceContainerHigh
            !supportsBlurBehind -> aurora.surface
            aurora.isDark -> Color.Black.copy(alpha = 0.70f)
            else -> Color.White.copy(alpha = 0.88f)
        }
    }
    val auroraScheme = remember(baseScheme, aurora, sheetContainer) {
        baseScheme.copy(
            primary = aurora.accent,
            onPrimary = if (aurora.isDark) aurora.background else Color.White,
            surfaceContainerHigh = sheetContainer,
            surfaceContainerHighest = sheetContainer,
            secondaryContainer = aurora.accent.copy(alpha = 0.22f),
            onSecondaryContainer = aurora.accent,
        )
    }
    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp),
    )
    val pageMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp

    MaterialTheme(
        colorScheme = auroraScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        AdaptiveSheet(
            onDismissRequest = onDismissRequest,
            modifier = Modifier.border(
                width = 1.dp,
                color = auroraRimColor(),
                shape = sheetShape,
            ),
            containerColor = sheetContainer,
            scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
            applyStatusBarsPadding = false,
            onRevealChange = { sheetReveal = it },
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            val revealState = rememberUpdatedState(sheetReveal)

            DisposableEffect(window, supportsBlurBehind) {
                val w = window
                if (w != null && supportsBlurBehind) {
                    w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    w.setDimAmount(0f)
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                }
                onDispose {
                    if (w != null && supportsBlurBehind) {
                        w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                        w.setDimAmount(0f)
                        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
            }

            LaunchedEffect(window, supportsBlurBehind) {
                val w = window ?: return@LaunchedEffect
                if (!supportsBlurBehind) return@LaunchedEffect
                snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                    .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                    .distinctUntilChanged()
                    .collect { step ->
                        val glass = ((step / 20f - 0.18f) / 0.82f).coerceIn(0f, 1f)
                        val radius = if (glass <= 0.02f) 0 else (44f * glass).roundToInt().coerceIn(1, 48)
                        val attrs = w.attributes
                        if (attrs.blurBehindRadius != radius) {
                            w.attributes = attrs.apply { blurBehindRadius = radius }
                        }
                        w.setDimAmount(0.18f * glass)
                    }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = pageMaxHeight)
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (aurora.isDark) {
                                    Color.White.copy(alpha = 0.24f)
                                } else {
                                    Color.Black.copy(alpha = 0.16f)
                                },
                            ),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Действия с изображением",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = aurora.textPrimary,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NovelImageActionAuroraCard(
                        icon = Icons.Default.Download,
                        title = "Сохранить картинку",
                        subtitle = "Загрузить файл в память устройства",
                        onClick = {
                            onSaveImage()
                            onDismissRequest()
                        },
                    )

                    NovelImageActionAuroraCard(
                        icon = Icons.Default.Share,
                        title = "Поделиться",
                        subtitle = "Отправить изображение в другое приложение",
                        onClick = {
                            onShareImage()
                            onDismissRequest()
                        },
                    )

                    NovelImageActionAuroraCard(
                        icon = Icons.Default.ContentCopy,
                        title = "Скопировать ссылку",
                        subtitle = "Скопировать прямую ссылку в буфер обмена",
                        onClick = {
                            onCopyLink()
                            onDismissRequest()
                        },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            text = "Отмена",
                            color = aurora.textSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun NovelImageActionAuroraCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val aurora = AuroraTheme.colors
    val cardBg = if (aurora.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(0.5.dp, auroraRimColor()),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(aurora.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = aurora.accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = aurora.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = aurora.textSecondary,
                )
            }
        }
    }
}
