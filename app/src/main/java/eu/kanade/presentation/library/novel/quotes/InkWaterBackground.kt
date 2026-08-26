package eu.kanade.presentation.library.novel.quotes

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.components.InkWaterShader
import eu.kanade.presentation.components.drawInkWaterFallback
import eu.kanade.presentation.components.drawInkWaterVignette
import eu.kanade.presentation.components.inkWaterPalette
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalIsEInkMode

/**
 * V6 «Чернила в воде» — полноэкранный фоновый шейдер для экрана цитат.
 *
 * - API 33+: анимированный AGSL-шейдер (fbm + двойной domain warp), каппинг ~30 fps.
 * - Reduced motion: тот же шейдер, но один статичный кадр.
 * - API < 33 или e-ink: статичный мягкий градиент (без анимации и шейдера).
 * Рисуется ТОЛЬКО на тёмной теме (вызов изолирован на месте использования).
 * Шейдер и фолбэк общие с наградой Сокровищницы — см. [InkWaterShader].
 */
private const val FRAME_INTERVAL_NS = 33_000_000L // ~30 fps — туману достаточно

@Composable
fun InkWaterBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isEInk = LocalIsEInkMode.current
    val colors = AuroraTheme.colors
    val accent = colors.accent
    val accentVariant = colors.accentVariant
    val backgroundColor = colors.background

    val inkShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { InkWaterShader.getInstance() }.getOrNull()
        } else {
            null
        }
    }

    val animatorScale = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }
    val animate = inkShader != null && !isEInk && animatorScale > 0f
    val timeSec = remember { mutableFloatStateOf(20f) }

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var lastFrameNs = 0L
        val startNs = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                if (now - lastFrameNs >= FRAME_INTERVAL_NS) {
                    lastFrameNs = now
                    timeSec.floatValue = (now - startNs) / 1_000_000_000f
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        val palette = inkWaterPalette(accent, accentVariant, backgroundColor, dark = colors.isDark)
        val activeShader = inkShader
        if (activeShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            with(activeShader) {
                drawInkWater(
                    timeSec = timeSec.floatValue,
                    deepColor = palette.deep,
                    midColor = palette.mid,
                    accentColor = palette.accent,
                    density = if (colors.isDark) 0.67f else 0.40f,
                )
            }
        } else {
            // Статичный фолбэк: мягкое акцентное пятно сверху + увод вправо.
            drawInkWaterFallback(accent = palette.accent, accentVariant = palette.mid, dark = colors.isDark)
        }

        // Шейд-виньетка поверх чернил (плотность к шапке, затемнение к низу под цвет темы).
        drawInkWaterVignette(palette.deep, palette.background, dark = colors.isDark)
    }
}
