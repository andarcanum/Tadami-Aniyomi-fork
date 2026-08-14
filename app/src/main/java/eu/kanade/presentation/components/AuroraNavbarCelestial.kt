package eu.kanade.presentation.components

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tachiyomi.core.common.preference.Preference
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * "Celestial Navigation" — визуальная награда для Aurora Bottom Bar.
 *
 * Смесь трёх эффектов (включается только когда разблокирован unlockable
 * [AURORA_CELESTIAL_NAVBAR_UNLOCKABLE] и активен Aurora-стиль нижней навигации):
 *  - «Ночное небо»   — мерцающие звёзды в стекле бара;
 *  - «Волна света»   — световая волна, пробегающая от старой вкладки к новой;
 *  - «Полярное гало» — светящееся кольцо с двумя орбитальными бликами вокруг
 *                      пилюли активной вкладки.
 *
 * Файл самодостаточный: ничего, кроме UnlockableManager (Injekt), не тянет.
 */
const val AURORA_CELESTIAL_NAVBAR_UNLOCKABLE = "special_navbar_aurora_celestial"

private const val SHEEN_DARK = 0xFFEAF6FF

private val StarTints = listOf(
    Color(0xFFFFFFFF), // Pure White
    Color(0xFFE2F1FF), // Ice white/blue tint
    Color(0xFFB3D9FF), // Light blue
    Color(0xFFE3F2FD), // Soft blue
    Color(0xFF90CAF9), // Cool astronomical blue
)

/**
 * Рисует мерцающие звёзды «ночного неба». Общий для нижнего бара и рейла.
 */
private fun DrawScope.drawCelestialStars(
    stars: List<CelestialStar>,
    time: Float,
    isDark: Boolean,
) {
    val twoPi = 2f * PI.toFloat()
    stars.forEach { star ->
        val twinkle = 0.5f + 0.5f * sin(time * twoPi * star.speed + star.phase)
        val alpha = star.baseAlpha * twinkle * (if (isDark) 1f else 0.55f)
        if (alpha > 0.02f) {
            val color = StarTints[star.tint]
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = star.radiusDp.dp.toPx(),
                center = Offset(star.x * size.width, star.y * size.height),
            )
        }
    }
}

/**
 * Общий гейт «награды-навбара»: включён переключателем И разблокирован в
 * Сокровищнице. Реактивно обновляется при разблокировке.
 */
@Composable
internal fun rememberUnlockableNavbarEnabled(
    enabled: Preference<Boolean>,
    unlockableId: String,
): Boolean {
    val isEnabled by enabled.collectAsState()
    val unlockableManager = remember { Injekt.get<UnlockableManager>() }
    val unlockedUnlockables by remember(unlockableManager) {
        unlockableManager.observeUnlockedUnlockables()
    }.collectAsState(initial = unlockableManager.getUnlockedUnlockables())
    val isUnlocked = remember(unlockedUnlockables) {
        unlockableManager.isUnlockableAvailable(unlockableId)
    }
    return isEnabled && isUnlocked
}

/**
 * true, когда награда доступна (разблокирована пасхалкой или включён
 * debug-обход замков Сокровищницы). Реактивно обновляется при разблокировке.
 */
@Composable
fun rememberAuroraCelestialNavbarUnlocked(): Boolean {
    val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
    return rememberUnlockableNavbarEnabled(
        enabled = uiPreferences.showCelestialNavbar(),
        unlockableId = AURORA_CELESTIAL_NAVBAR_UNLOCKABLE,
    )
}

/**
 * Гейт анимации Celestial: выключает бесконечные анимации при reduced-motion
 * (ANIMATOR_DURATION_SCALE = 0) или энергосбережении, оставляя статичный декор.
 */
@Composable
private fun rememberCelestialAnimationEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val animationsDisabled = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
        val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        !animationsDisabled && !powerSave
    }
}

/**
 * Эффекты уровня всего бара: звёзды + волна света при смене вкладки.
 * Добавлять В КОНЕЦ цепочки модификаторов бара (после clip/hazeEffect/border),
 * тогда рисование остаётся внутри формы бара, а контент вкладок — поверх.
 */
@Composable
fun Modifier.auroraCelestialBar(
    accent: Color,
    accentVariant: Color,
    isDark: Boolean,
    selectedIndex: Int,
    tabCount: Int,
): Modifier {
    val animate = rememberCelestialAnimationEnabled()
    val time = if (animate) {
        val transition = rememberInfiniteTransition(label = "auroraCelestialBar")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "starTime",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // Волна света: перезапускается при смене выбранной вкладки.
    val wave = remember { Animatable(1f) }
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    var waveFrom by remember { mutableIntStateOf(selectedIndex) }
    var waveTo by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex, animate) {
        if (selectedIndex >= 0 && selectedIndex != previousIndex) {
            waveFrom = previousIndex
            waveTo = selectedIndex
            previousIndex = selectedIndex
            if (animate) {
                wave.snapTo(0f)
                wave.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    val stars = remember { generateStars(count = 46, seed = 20260714) }
    val sheen = if (isDark) Color(SHEEN_DARK) else Color.White

    return this.drawBehind {
        // --- Ночное небо (вариант D) ---
        drawCelestialStars(stars, time.value, isDark)

        // --- Волна света (вариант C) ---
        val progress = wave.value
        if (progress < 1f && tabCount > 0) {
            val fromX = (waveFrom + 0.5f) / tabCount * size.width
            val toX = (waveTo + 0.5f) / tabCount * size.width
            val x = fromX + (toX - fromX) * progress
            val bandWidth = size.width * 0.24f
            val fade = sin(progress * PI.toFloat())
            val peak = if (isDark) 0.18f else 0.30f
            drawRect(
                brush = Brush.horizontalGradient(
                    0.00f to Color.Transparent,
                    0.35f to accent.copy(alpha = 0.10f * fade),
                    0.50f to sheen.copy(alpha = peak * fade),
                    0.65f to accentVariant.copy(alpha = 0.10f * fade),
                    1.00f to Color.Transparent,
                    startX = x - bandWidth / 2f,
                    endX = x + bandWidth / 2f,
                ),
                topLeft = Offset(x - bandWidth / 2f, 0f),
                size = Size(bandWidth, size.height),
            )
        }
    }
}

/**
 * «Полярное гало» вокруг пилюли активной вкладки: мягкое кольцо по контуру
 * + два орбитальных блика, бегущих по периметру. Добавлять ПЕРЕД
 * .background(...) пилюли, чтобы кольцо лежало под полупрозрачным фоном.
 */
@Composable
fun Modifier.auroraCelestialHalo(
    accent: Color,
    accentVariant: Color,
    isDark: Boolean,
    shape: Shape,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val animate = rememberCelestialAnimationEnabled()
    val orbit = if (animate) {
        val transition = rememberInfiniteTransition(label = "auroraCelestialHalo")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5_200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "orbit",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val sheen = if (isDark) Color(SHEEN_DARK) else Color.White

    return this.drawBehind {
        // Статичное кольцо-гало по контуру пилюли.
        val ringAlpha = if (isDark) 0.30f else 0.22f
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            brush = Brush.verticalGradient(
                listOf(
                    sheen.copy(alpha = ringAlpha),
                    accent.copy(alpha = ringAlpha * 0.6f),
                ),
            ),
            style = Stroke(width = 1.2.dp.toPx()),
        )

        // Два орбитальных блика на противоположных сторонах.
        drawOrbitGlint(t = orbit.value, outline = outline, coreColor = sheen, glowColor = accent, isDark = isDark)
        drawOrbitGlint(
            t = (orbit.value + 0.5f) % 1f,
            outline = outline,
            coreColor = sheen,
            glowColor = accentVariant,
            isDark = isDark,
        )
    }
}

private fun DrawScope.drawOrbitGlint(
    t: Float,
    outline: Outline,
    coreColor: Color,
    glowColor: Color,
    isDark: Boolean,
) {
    val p = outline.pillPerimeterPoint(t, size)
    val glowAlpha = if (isDark) 0.35f else 0.25f
    val coreAlpha = if (isDark) 0.95f else 0.75f
    drawCircle(color = glowColor.copy(alpha = glowAlpha * 0.6f), radius = 5.dp.toPx(), center = p)
    drawCircle(color = glowColor.copy(alpha = glowAlpha), radius = 2.6.dp.toPx(), center = p)
    drawCircle(color = coreColor.copy(alpha = coreAlpha), radius = 1.1.dp.toPx(), center = p)
}

/**
 * Точка на периметре пилюли по параметру t в [0..1]. Считается по той же
 * геометрии, что и кольцо гало ([Outline] от `shape.createOutline`), поэтому
 * блики и кольцо не разъезжаются при любой форме.
 */
private fun Outline.pillPerimeterPoint(t: Float, size: Size): Offset {
    val roundRect = when (this) {
        is Outline.Rounded -> roundRect
        else -> RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2f, size.height / 2f))
    }
    return roundRect.pillPerimeterPoint(t)
}

/**
 * Точка на периметре [RoundRect] (пилюля с круглыми торцами) по параметру
 * t в [0..1]. Обход по часовой стрелке от начала верхней грани.
 */
private fun RoundRect.pillPerimeterPoint(t: Float): Offset {
    val r = topLeftCornerRadius.x.coerceIn(0f, height / 2f)
    val straight = (width - 2f * r).coerceAtLeast(0f)
    val arc = PI.toFloat() * r
    val perimeter = 2f * straight + 2f * arc
    var d = t.coerceIn(0f, 1f) * perimeter
    // Верхняя грань: (left + r, top) -> (left + r + straight, top)
    if (d < straight) return Offset(left + r + d, top)
    d -= straight
    // Правый полукруг: от -90° до +90° вокруг (left + r + straight, top + r)
    if (d < arc) {
        val a = -PI.toFloat() / 2f + (d / arc) * PI.toFloat()
        return Offset(left + r + straight + r * cos(a), top + r + r * sin(a))
    }
    d -= arc
    // Нижняя грань: (left + r + straight, bottom) -> (left + r, bottom)
    if (d < straight) return Offset(left + r + straight - d, bottom)
    d -= straight
    // Левый полукруг: от 90° до 270° вокруг (left + r, top + r)
    val a = PI.toFloat() / 2f + (d / arc) * PI.toFloat()
    return Offset(left + r + r * cos(a), top + r + r * sin(a))
}

private data class CelestialStar(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val baseAlpha: Float,
    val phase: Float,
    val speed: Float,
    val tint: Int,
)

private fun generateStars(count: Int, seed: Int): List<CelestialStar> {
    val rnd = Random(seed)
    return List(count) {
        CelestialStar(
            x = rnd.nextFloat(),
            y = rnd.nextFloat(),
            radiusDp = 0.4f + rnd.nextFloat() * 0.9f,
            baseAlpha = 0.25f + rnd.nextFloat() * 0.5f,
            phase = rnd.nextFloat() * (2f * PI.toFloat()),
            speed = rnd.nextInt(1, 4).toFloat(),
            tint = rnd.nextInt(5),
        )
    }
}

/**
 * Вертикальный вариант звёздного бара для NavigationRail (планшет / лендскейп):
 * то же ночное небо + волна света, бегущая по вертикали между вкладками.
 */
@Composable
fun Modifier.auroraCelestialRail(
    accent: Color,
    accentVariant: Color,
    isDark: Boolean,
    selectedIndex: Int,
    tabCount: Int,
): Modifier {
    val animate = rememberCelestialAnimationEnabled()
    val time = if (animate) {
        val transition = rememberInfiniteTransition(label = "auroraCelestialRail")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "railStarTime",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val wave = remember { Animatable(1f) }
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    var waveFrom by remember { mutableIntStateOf(selectedIndex) }
    var waveTo by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex, animate) {
        if (selectedIndex >= 0 && selectedIndex != previousIndex) {
            waveFrom = previousIndex
            waveTo = selectedIndex
            previousIndex = selectedIndex
            if (animate) {
                wave.snapTo(0f)
                wave.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    val stars = remember { generateStars(count = 46, seed = 20260714) }
    val sheen = if (isDark) Color(SHEEN_DARK) else Color.White

    return this.drawBehind {
        drawCelestialStars(stars, time.value, isDark)

        val progress = wave.value
        if (progress < 1f && tabCount > 0) {
            val fromY = (waveFrom + 0.5f) / tabCount * size.height
            val toY = (waveTo + 0.5f) / tabCount * size.height
            val y = fromY + (toY - fromY) * progress
            val bandHeight = size.height * 0.24f
            val fade = sin(progress * PI.toFloat())
            val peak = if (isDark) 0.18f else 0.30f
            drawRect(
                brush = Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.35f to accent.copy(alpha = 0.10f * fade),
                    0.50f to sheen.copy(alpha = peak * fade),
                    0.65f to accentVariant.copy(alpha = 0.10f * fade),
                    1.00f to Color.Transparent,
                    startY = y - bandHeight / 2f,
                    endY = y + bandHeight / 2f,
                ),
                topLeft = Offset(0f, y - bandHeight / 2f),
                size = Size(size.width, bandHeight),
            )
        }
    }
}
