package eu.kanade.presentation.components

import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.presentation.core.util.LocalAppHaptics

/**
 * Эталонный Aurora frost-диалог (см. AGENTS.md §«Aurora frost-диалоги»): отдельное окно с
 * системным blur (API 31+), тёмное полупрозрачное стекло с rim-light кромкой, шапка с
 * заголовком и круглой кнопкой закрытия, скроллируемый контент и закреплённый футер.
 *
 * Рецепт поверхностей (1-в-1 с жанровым диалогом подборок):
 * - dark: 0xFF0E0C13 α.74 (AMOLED 0xFF08070C α.80); light: White α.78; e-ink: сплошная
 *   surfaceContainerHigh без blur/полупрозрачностей;
 * - frost-подложка: молочный вертикальный градиент .04→.015 (dark) / .28→.18 (light);
 * - blur: setBackgroundBlurRadius(48) внутри окна + blurBehindRadius(32) и FLAG_BLUR_BEHIND
 *   вокруг; ниже API 31 / без поддержки вендором радиусы игнорируются — стекло+дим = фолбэк;
 * - дим .35 (dark) / .45 (light); габариты: 92% ширины, widthIn 520dp, heightIn 700dp.
 */
@Composable
fun AuroraFrostDialog(
    onDismiss: () -> Unit,
    title: String,
    innerScroll: Boolean = true,
    footer: (@Composable RowScope.() -> Unit)? = null,
    bottomAligned: Boolean = false,
    headerContent: (@Composable () -> Unit)? = null,
    surfaceOverlay: Brush? = null,
    closeLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val shape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setGravity(if (bottomAligned) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER)
            if (!colors.isEInk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow?.let { w ->
                    runCatching { w.setBackgroundBlurRadius(48) }
                    val lp = w.attributes
                    lp.blurBehindRadius = 32
                    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    w.attributes = lp
                }
            }
            @Suppress("DEPRECATION")
            dialogWindow?.setDimAmount(if (colors.isDark) 0.35f else 0.45f)
        }
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .heightIn(max = 700.dp)
                .then(if (bottomAligned) Modifier.padding(bottom = 12.dp) else Modifier)
                .clip(shape)
                .border(1.dp, auroraMenuRimLightBrush(colors), shape)
                .background(
                    if (colors.isEInk) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else if (colors.isDark) {
                        if (colors.isAmoled) {
                            Color(0xFF08070C).copy(alpha = 0.80f)
                        } else {
                            Color(0xFF0E0C13).copy(alpha = 0.74f)
                        }
                    } else {
                        Color.White.copy(alpha = 0.78f)
                    },
                    shape,
                )
                .then(
                    if (surfaceOverlay != null &&
                        !colors.isEInk
                    ) {
                        Modifier.background(surfaceOverlay, shape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (colors.isEInk) {
                            Modifier
                        } else {
                            Modifier.background(
                                if (colors.isDark) {
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.015f)),
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.18f)),
                                    )
                                },
                                shape,
                            )
                        },
                    )
                    .padding(20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (headerContent != null) {
                            headerContent()
                        } else {
                            Text(
                                title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.textPrimary,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Color.White.copy(alpha = if (colors.isDark) 0.08f else 0.40f),
                                CircleShape,
                            )
                            .clickable {
                                appHaptics.tap()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = closeLabel,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .then(if (innerScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                ) {
                    content()
                }
                if (footer != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        footer()
                    }
                }
            }
        }
    }
}

/** Кнопка «Отмена» футера frost-диалога: стеклянная пилюля в языке чипов. */
@Composable
fun RowScope.AuroraFrostCancel(
    label: String,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    TextButton(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.textPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        modifier = Modifier
            .background(
                if (colors.isEInk) {
                    Brush.verticalGradient(listOf(colors.surface, colors.surface))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (colors.isDark) 0.09f else 0.72f),
                            Color.White.copy(alpha = if (colors.isDark) 0.04f else 0.50f),
                        ),
                    )
                },
                CircleShape,
            )
            .border(
                1.dp,
                if (colors.isEInk) {
                    colors.divider
                } else {
                    Color.White.copy(alpha = if (colors.isDark) 0.12f else 0.40f)
                },
                CircleShape,
            ),
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

/** Кнопка подтверждения футера frost-диалога: чистая accent-пилла без навесных слоёв. */
@Composable
fun RowScope.AuroraFrostConfirm(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (colors.isEInk) colors.textPrimary else colors.accent,
            contentColor = colors.textOnAccent,
            disabledContainerColor = if (colors.isDark) {
                Color.White.copy(alpha = 0.06f)
            } else {
                Color.Black.copy(alpha = 0.04f)
            },
            disabledContentColor = colors.textSecondary.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}
