package eu.kanade.presentation.easteregg.crystal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.presentation.theme.AuroraTheme

@Composable
internal fun CrystalMemorialCanvas(
    renderer: CrystalMemorialRenderer,
    elapsedMs: () -> Float,
    ambientMs: () -> Float,
    pointer: () -> Offset,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    fittedArtwork: Boolean = false,
    ceremonyBlend: () -> Float = { 0f },
    entranceReveal: () -> Float = { 1f },
) {
    val colors = AuroraTheme.colors
    Canvas(modifier) {
        val currentPointer = if (reduced) Offset.Zero else pointer()
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            val saved = canvas.save()
            canvas.scale(density, density)
            renderer.draw(
                canvas, size.width / density, size.height / density, elapsedMs(), ambientMs() / 1000f,
                currentPointer.x, currentPointer.y, reduced,
                colors.isEInk,
                colors.eInkProfile == EInkProfile.MONOCHROME,
                colors.background.toArgb(),
                colors.textPrimary.toArgb(),
                fittedArtwork,
                ceremonyBlend(),
                entranceReveal(),
            )
            canvas.restoreToCount(saved)
        }
    }
}

internal enum class CrystalGlyph { Relic, Frame, Paw, Title, Badge, Palette, CatFace, Cameo }

@Composable
internal fun CrystalGlyphCanvas(
    glyph: CrystalGlyph,
    modifier: Modifier = Modifier,
    palette: CrystalPalette = CrystalPalette.Moon,
    frame: CrystalFrameVariant = CrystalFrameVariant.Fold,
    animate: Boolean = true,
    painter: CrystalJewelPainter = remember { CrystalJewelPainter() },
) {
    val colors = AuroraTheme.colors
    val canAnimate = animate && !colors.isEInk && (glyph == CrystalGlyph.Relic || glyph == CrystalGlyph.Badge)
    val motion = if (canAnimate) rememberCrystalMotionPolicy().systemAnimations else false
    val timeState = if (motion) rememberCrystalTime(true) else null
    Canvas(modifier) {
        val seconds = if (timeState != null) timeState.value / 1000f else 0f
        drawIntoCanvas { compose ->
            val canvas = compose.nativeCanvas
            val saved = canvas.save()
            canvas.scale(density, density)
            val width = size.width / density
            val height = size.height / density
            val ink = if (colors.isEInk) colors.textPrimary.toArgb() else null
            when (glyph) {
                CrystalGlyph.Relic -> painter.relic(canvas, width, height, seconds, palette, !motion, ink)
                CrystalGlyph.Frame -> {
                    // Inventory thumbnail contains the full ear/medallion silhouette, including its overflow.
                    canvas.translate(width * 0.16f, height * 0.21f)
                    painter.avatarFrame(
                        canvas,
                        width * 0.68f,
                        height * 0.68f,
                        frame,
                        palette,
                        0f,
                        true,
                        inkColor = ink,
                    )
                }
                CrystalGlyph.Paw -> painter.paw(canvas, 0f, 0f, minOf(width, height), palette, inkColor = ink)
                CrystalGlyph.Title -> painter.titleGlyph(canvas, width, height, palette, ink)
                CrystalGlyph.Badge -> painter.pawPendant(canvas, width, height, palette, seconds, !motion, ink)
                CrystalGlyph.Palette -> painter.paletteGlyph(canvas, width, height, palette, ink)
                CrystalGlyph.CatFace -> painter.catFace(canvas, width, height, palette, ink)
                CrystalGlyph.Cameo -> painter.cameoPortrait(canvas, width, height, palette, ink)
            }
            canvas.restoreToCount(saved)
        }
    }
}

@Composable
internal fun CrystalAvatarFrame(
    variant: CrystalFrameVariant,
    palette: CrystalPalette,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    avatarInsetDp: Float = 3f,
) {
    val painter = remember { CrystalJewelPainter() }
    val colors = AuroraTheme.colors
    Canvas(modifier) {
        drawIntoCanvas { compose ->
            val canvas = compose.nativeCanvas
            val save = canvas.save()
            canvas.scale(density, density)
            painter.avatarFrame(
                canvas, size.width / density, size.height / density, variant, palette, 0f, true,
                avatarInsetDp, if (colors.isEInk) colors.textPrimary.toArgb() else null,
            )
            canvas.restoreToCount(save)
        }
    }
}
