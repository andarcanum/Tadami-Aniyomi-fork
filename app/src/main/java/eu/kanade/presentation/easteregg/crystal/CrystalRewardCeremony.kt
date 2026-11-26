package eu.kanade.presentation.easteregg.crystal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import kotlin.math.roundToInt

private data class ConstellationNodeSlot(
    val item: CrystalItem,
    val xFraction: Float,
    val yFraction: Float,
)

private val CrystalConstellationSlots = listOf(
    ConstellationNodeSlot(CrystalItem.Relic, 0.15f, 0.62f),
    ConstellationNodeSlot(CrystalItem.Frame, 0.32f, 0.28f),
    ConstellationNodeSlot(CrystalItem.Paws, 0.68f, 0.28f),
    ConstellationNodeSlot(CrystalItem.Title, 0.85f, 0.62f),
    ConstellationNodeSlot(CrystalItem.Badge, 0.38f, 0.80f),
    ConstellationNodeSlot(CrystalItem.Silk, 0.62f, 0.80f),
)

@Composable
internal fun CrystalRewardCeremony(
    firstGrant: Boolean,
    reduced: Boolean,
    onDismiss: () -> Unit,
    onTreasury: () -> Unit,
    palette: CrystalPalette = CrystalPalette.Moon,
) {
    BackHandler(onBack = onDismiss)
    CrystalArtTheme(palette = palette) {
        val colors = AuroraTheme.colors
        val haptics = LocalAppHaptics.current
        val policy = rememberCrystalMotionPolicy()
        val motion = !reduced && !colors.isEInk && policy.systemAnimations
        val gold = if (colors.isEInk) colors.textPrimary else Color(palette.gold)
        val rose = if (colors.isEInk) colors.textSecondary else Color(palette.rose)

        var selectedOrdinal by rememberSaveable { mutableStateOf<Int?>(null) }
        val selectedItem = selectedOrdinal?.let { CrystalItem.entries.getOrNull(it) }

        val sharedPainter = remember { CrystalJewelPainter() }

        // Separate stationary backdrop veil from one unified offscreen content layer (zero vertical slide/unfold).
        val veilReveal = remember(motion) { Animatable(if (motion) 0f else 1f) }
        val contentReveal = remember(motion) { Animatable(if (motion) 0.002f else 1f) }
        LaunchedEffect(motion) {
            if (motion) {
                withFrameNanos { }
                launch {
                    veilReveal.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(90)
                    contentReveal.animateTo(1f, tween(640, easing = FastOutSlowInEasing))
                }
            }
        }

        val veilBrush = remember(colors.isEInk, colors.background) {
            if (colors.isEInk) {
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.30f to Color.Transparent,
                    0.46f to colors.background.copy(alpha = 0.92f),
                    1f to colors.background,
                )
            } else {
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.30f to Color(0x0A050811),
                    0.50f to Color(0xA8050811),
                    0.72f to Color(0xE6050811),
                    1f to Color(0xF704060D),
                )
            }
        }

        val clearSelectionInteraction = remember { MutableInteractionSource() }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = veilReveal.value }
                    .background(veilBrush),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = contentReveal.value
                        alpha = p
                        val s = 0.988f + 0.012f * p
                        scaleX = s
                        scaleY = s
                        compositingStrategy = if (p < 0.999f) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                    }
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(top = 52.dp)
                    .clickable(
                        interactionSource = clearSelectionInteraction,
                        indication = null,
                        enabled = selectedItem != null,
                        onClick = { selectedOrdinal = null },
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                // Leave upper sky clear for Crystal's live 3D 82-star constellation.
                Spacer(Modifier.weight(1f, fill = true).heightIn(min = 138.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(AYMR.strings.crystal_dedication),
                        fontFamily = CrystalFonts.Script,
                        fontSize = 26.sp,
                        color = if (colors.isEInk) colors.textPrimary else gold.copy(alpha = 0.86f),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(AYMR.strings.crystal_ceremony_title),
                        fontFamily = CrystalFonts.Serif,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 38.sp,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(
                            if (firstGrant) {
                                AYMR.strings.crystal_ceremony_constellation_sub
                            } else {
                                AYMR.strings.crystal_ceremony_constellation_repeat
                            },
                        ),
                        modifier = Modifier.padding(top = 7.dp),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(14.dp))

                CeremonyConstellationStage(
                    palette = palette,
                    gold = gold,
                    rose = rose,
                    selectedItem = selectedItem,
                    motion = motion,
                    painter = sharedPainter,
                    onToggleItem = { item ->
                        haptics.tap()
                        selectedOrdinal = if (selectedOrdinal == item.ordinal) null else item.ordinal
                    },
                )

                CeremonyWhisperArea(
                    selectedItem = selectedItem,
                    gold = gold,
                    motion = motion,
                )

                CeremonySparksRow(
                    firstGrant = firstGrant,
                    motion = motion,
                    gold = gold,
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CrystalFelineInvitationButton(
                        label = stringResource(AYMR.strings.crystal_room_invite),
                        onClick = onTreasury,
                        palette = palette,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = stringResource(AYMR.strings.crystal_stay_here),
                            fontSize = 13.sp,
                            color = colors.textPrimary.copy(alpha = if (colors.isEInk) 0.9f else 0.56f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CeremonySparksRow(
    firstGrant: Boolean,
    motion: Boolean,
    gold: Color,
) {
    val colors = AuroraTheme.colors
    val counter = remember(firstGrant, motion) { Animatable(if (firstGrant && motion) 0f else 999f) }
    LaunchedEffect(firstGrant, motion) {
        if (firstGrant && motion) {
            delay(320)
            counter.animateTo(999f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    val sparksLabel = if (firstGrant) {
        stringResource(AYMR.strings.crystal_ceremony_sparks, counter.value.roundToInt())
    } else {
        stringResource(AYMR.strings.crystal_ceremony_repeat_note)
    }
    val sparksSemantics = if (firstGrant) {
        stringResource(AYMR.strings.crystal_ceremony_sparks, 999)
    } else {
        sparksLabel
    }
    val textColor = if (colors.isEInk) colors.textSecondary else gold.copy(alpha = 0.68f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 14.dp)
            .clearAndSetSemantics { contentDescription = sparksSemantics },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WhisperHairline(gold = gold, isEInk = colors.isEInk)
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Reserve final 999 bounds from frame 0 so the 0 -> 999 count-up never wraps or shifts layout height.
            val sparksStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
            Text(
                text = sparksSemantics,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                style = sparksStyle,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0f),
            )
            Text(
                text = sparksLabel,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                style = sparksStyle,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
        WhisperHairline(gold = gold, isEInk = colors.isEInk)
    }
}

@Composable
private fun CeremonyConstellationStage(
    palette: CrystalPalette,
    gold: Color,
    rose: Color,
    selectedItem: CrystalItem?,
    motion: Boolean,
    painter: CrystalJewelPainter,
    onToggleItem: (CrystalItem) -> Unit,
) {
    val colors = AuroraTheme.colors
    val orbSize = 56.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()), 0f)
                    val upperArc = Path().apply {
                        moveTo(w * 0.11f, h * 0.66f)
                        quadraticTo(w * 0.50f, h * 0.06f, w * 0.89f, h * 0.66f)
                    }
                    val lowerArc = Path().apply {
                        moveTo(w * 0.295f, h * 0.81f)
                        quadraticTo(w * 0.50f, h * 0.53f, w * 0.705f, h * 0.81f)
                    }
                    val upperStroke = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect)
                    val lowerStroke = Stroke(width = 1.dp.toPx())
                    onDrawBehind {
                        drawPath(
                            path = upperArc,
                            color = if (colors.isEInk) {
                                colors.textSecondary.copy(
                                    alpha = 0.5f,
                                )
                            } else {
                                gold.copy(alpha = 0.24f)
                            },
                            style = upperStroke,
                        )
                        drawPath(
                            path = lowerArc,
                            color = if (colors.isEInk) {
                                colors.textSecondary.copy(
                                    alpha = 0.35f,
                                )
                            } else {
                                rose.copy(alpha = 0.18f)
                            },
                            style = lowerStroke,
                        )
                        if (!colors.isEInk) {
                            drawCircle(
                                gold.copy(alpha = 0.55f),
                                radius = 2.dp.toPx(),
                                center = Offset(
                                    w * 0.50f,
                                    h * 0.34f,
                                ),
                            )
                            drawCircle(
                                rose.copy(alpha = 0.45f),
                                radius = 1.3.dp.toPx(),
                                center = Offset(
                                    w * 0.24f,
                                    h * 0.44f,
                                ),
                            )
                            drawCircle(
                                rose.copy(alpha = 0.45f),
                                radius = 1.3.dp.toPx(),
                                center = Offset(
                                    w * 0.76f,
                                    h * 0.44f,
                                ),
                            )
                        }
                    }
                },
        )

        // Direct Layout avoids BoxWithConstraints subcomposition on the opening frame.
        Layout(
            content = {
                CrystalConstellationSlots.forEach { slot ->
                    val isSelected = selectedItem == slot.item
                    val isDimmed = selectedItem != null && !isSelected
                    ConstellationOrb(
                        item = slot.item,
                        palette = palette,
                        gold = gold,
                        rose = rose,
                        selected = isSelected,
                        dimmed = isDimmed,
                        motion = motion,
                        painter = painter,
                        onClick = { onToggleItem(slot.item) },
                        modifier = Modifier.size(orbSize),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val stageW = constraints.maxWidth
            val stageH = constraints.maxHeight
            val orbPx = orbSize.roundToPx()
            val orbConstraints = Constraints.fixed(orbPx, orbPx)
            val placeables = measurables.map { it.measure(orbConstraints) }
            layout(stageW, stageH) {
                placeables.forEachIndexed { index, placeable ->
                    val slot = CrystalConstellationSlots[index]
                    val x = (stageW * slot.xFraction - placeable.width / 2f).roundToInt()
                    val y = (stageH * slot.yFraction - placeable.height / 2f).roundToInt()
                    placeable.placeRelative(x, y)
                }
            }
        }
    }
}

@Composable
private fun ConstellationOrb(
    item: CrystalItem,
    palette: CrystalPalette,
    gold: Color,
    rose: Color,
    selected: Boolean,
    dimmed: Boolean,
    motion: Boolean,
    painter: CrystalJewelPainter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val title = crystalItemTitle(item)
    var rippleCount by remember { mutableIntStateOf(0) }
    val rippleProgress = remember { Animatable(1f) }
    LaunchedEffect(rippleCount) {
        if (rippleCount > 0 && motion) {
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        }
    }

    val targetScale = when {
        selected -> 1.28f
        dimmed -> 0.92f
        else -> 1f
    }
    val targetAlpha = when {
        dimmed -> 0.46f
        else -> 1f
    }
    val animSpec = if (motion) tween<Float>(280, easing = FastOutSlowInEasing) else snap()
    val scaleState = animateFloatAsState(targetScale, animSpec, label = "orbScale")
    val alphaState = animateFloatAsState(targetAlpha, animSpec, label = "orbAlpha")
    val ringAlphaState = animateFloatAsState(if (selected) 1f else 0f, animSpec, label = "orbRing")
    val interactions = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
                this.alpha = alphaState.value
            }
            .semantics {
                role = Role.Button
                contentDescription = title
                this.selected = selected
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactions,
                indication = null,
                onClick = {
                    rippleCount++
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension * 0.43f
                    val bloomRadius = size.minDimension * (if (selected) 0.58f else 0.50f)
                    val bloomBrush = Brush.radialGradient(
                        0f to gold.copy(alpha = if (selected) 0.44f else 0.22f),
                        0.52f to rose.copy(alpha = if (selected) 0.20f else 0.10f),
                        1f to Color.Transparent,
                        center = center,
                        radius = bloomRadius,
                    )
                    val wellBrush = Brush.radialGradient(
                        0f to if (selected) Color(0xE62C2A42) else Color(0xBA1E2438),
                        1f to if (selected) Color(0xF0121626) else Color(0xDB0C101C),
                        center = center,
                        radius = baseRadius,
                    )
                    val rimBrush = Brush.verticalGradient(
                        listOf(
                            gold.copy(alpha = if (selected) 0.62f else 0.34f),
                            rose.copy(alpha = if (selected) 0.28f else 0.14f),
                        ),
                    )
                    val stroke1 = Stroke(width = 1.dp.toPx())
                    val stroke12 = Stroke(width = 1.2.dp.toPx())
                    val eInkStroke = Stroke(width = if (selected) 2.dp.toPx() else 1.dp.toPx())
                    val haloRadius = baseRadius + 4.dp.toPx()
                    onDrawBehind {
                        if (colors.isEInk) {
                            drawCircle(colors.surface, radius = baseRadius, center = center)
                            drawCircle(colors.textPrimary, radius = baseRadius, center = center, style = eInkStroke)
                        } else {
                            drawCircle(brush = bloomBrush, radius = bloomRadius, center = center)
                            drawCircle(brush = wellBrush, radius = baseRadius, center = center)
                            drawCircle(brush = rimBrush, radius = baseRadius, center = center, style = stroke1)
                            val ringAlpha = ringAlphaState.value
                            if (ringAlpha > 0.01f) {
                                drawCircle(
                                    color = gold.copy(alpha = 0.58f * ringAlpha),
                                    radius = haloRadius,
                                    center = center,
                                    style = stroke1,
                                )
                            }
                            val rp = rippleProgress.value
                            if (rp < 0.99f) {
                                drawCircle(
                                    color = gold.copy(alpha = (1f - rp) * 0.72f),
                                    radius = baseRadius * (0.94f + rp * 0.55f),
                                    center = center,
                                    style = stroke12,
                                )
                            }
                        }
                    }
                },
        )

        CrystalGlyphCanvas(
            glyph = item.glyph,
            modifier = Modifier.size(28.dp),
            palette = palette,
            animate = false,
            painter = painter,
        )
    }
}

@Composable
private fun CeremonyWhisperArea(
    selectedItem: CrystalItem?,
    gold: Color,
    motion: Boolean,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = selectedItem,
            transitionSpec = {
                val transition = if (motion) {
                    fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                } else {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                }
                transition using null
            },
            label = "ceremonyWhisper",
        ) { item ->
            if (item == null) {
                Text(
                    text = stringResource(AYMR.strings.crystal_ceremony_tap_hint),
                    fontFamily = CrystalFonts.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 16.5.sp,
                    lineHeight = 22.sp,
                    color = if (colors.isEInk) colors.textSecondary else gold.copy(alpha = 0.74f),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = crystalItemTitle(item),
                        fontFamily = CrystalFonts.Serif,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 26.sp,
                        color = if (colors.isEInk) colors.textPrimary else gold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = crystalItemDescription(item),
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhisperHairline(gold: Color, isEInk: Boolean) {
    val color = if (isEInk) gold.copy(alpha = 0.35f) else gold.copy(alpha = 0.34f)
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent))),
    )
}
