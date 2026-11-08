package eu.kanade.presentation.easteregg.crystal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.sin

@Composable
internal fun CrystalTitlePill(palette: CrystalPalette, animate: Boolean, modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    val policy = rememberCrystalMotionPolicy()
    val moving = animate && policy.systemAnimations && !colors.isEInk
    val time = rememberCrystalTime(moving)
    val base = if (colors.isDark) Color(palette.gold) else lerp(Color(palette.gold), Color(0xFF604626), 0.65f)
    val phase = (time.value % 7500f) / 7500f
    val sweep = ((phase - 0.55f) / 0.3f).coerceIn(0f, 1f)
    val density = LocalDensity.current.density
    val start = (240f - sweep * 480f) * density
    val brush = if (colors.isEInk) {
        null
    } else {
        Brush.linearGradient(
            listOf(base, base, if (colors.isDark) Color.White else Color(0xFF94704B), Color(palette.rose), base),
            start = Offset(start, 0f),
            end = Offset(start + 180f * density, 45f * density),
        )
    }
    Row(
        modifier.background(if (colors.isEInk) colors.surface else base.copy(alpha = 0.09f), RoundedCornerShape(50))
            .border(1.dp, if (colors.isEInk) colors.textPrimary else base.copy(alpha = 0.32f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            stringResource(AYMR.strings.unlockable_title_crystal_light_keeper),
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (colors.isEInk) colors.textPrimary else base,
            ).let {
                if (brush !=
                    null
                ) {
                    it.copy(brush = brush)
                } else {
                    it
                }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun CrystalProfileTitle(modifier: Modifier = Modifier) {
    val appearance = rememberCrystalAppearance()
    if (appearance.title) CrystalTitlePill(appearance.palette, !appearance.reduced, modifier)
}

/** Local profile atmosphere; never modifies global app-theme or aura preferences. */
@Composable
internal fun CrystalSilkBackdrop(modifier: Modifier = Modifier) {
    val appearance = rememberCrystalAppearance()
    if (!appearance.silk) return
    CrystalSilkAtmosphere(appearance.palette, !appearance.reduced, modifier)
}

/** The same local atmosphere serves equipped profiles and uncommitted palette previews. */
@Composable
internal fun CrystalSilkAtmosphere(palette: CrystalPalette, animate: Boolean, modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    val policy = rememberCrystalMotionPolicy()
    val time = rememberCrystalTime(animate && policy.systemAnimations && !colors.isEInk)
    Canvas(modifier) {
        if (colors.isEInk) {
            drawRoundRect(colors.textSecondary, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.dp.toPx()))
        } else {
            val radius = size.height * 0.46f
            val drift = sin(time.value / 8000f) * size.width * 0.035f
            val alpha = if (colors.isDark) 0.16f else 0.11f
            for ((x, color) in listOf(0.28f to palette.gold, 0.72f to palette.rose)) {
                val center = Offset(size.width * x + drift, size.height * 0.5f)
                withTransform({ scale(2.5f, 1f, center) }) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(color).copy(alpha = alpha), Color.Transparent),
                            center,
                            radius.coerceAtLeast(1f),
                        ),
                        radius,
                        center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CrystalBackgroundOverlay(modifier: Modifier = Modifier) {
    val appearance = rememberCrystalAppearance()
    if (appearance.paws) CrystalPawsBackground(modifier.clipToBounds(), appearance.palette, !appearance.reduced)
}
