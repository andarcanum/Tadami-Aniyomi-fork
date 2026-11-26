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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.delay
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

        val counter = remember(firstGrant, motion) { Animatable(if (firstGrant && motion) 0f else 999f) }
        LaunchedEffect(firstGrant, motion) {
            if (firstGrant && motion) {
                delay(220)
                counter.animateTo(999f, tween(1100, easing = FastOutSlowInEasing))
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
        Box(
            Modifier
                .fillMaxSize()
                .background(veilBrush),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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

                CeremonyStaggeredBlock(delayMs = 80, motion = motion) {
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
                }

                Spacer(Modifier.height(14.dp))

                CeremonyConstellationStage(
                    palette = palette,
                    gold = gold,
                    rose = rose,
                    selectedItem = selectedItem,
                    motion = motion,
                    onToggleItem = { item ->
                        haptics.tap()
                        selectedOrdinal = if (selectedOrdinal == item.ordinal) null else item.ordinal
                    },
                )

                CeremonyStaggeredBlock(delayMs = 260, motion = motion) {
                    CeremonyWhisperArea(
                        selectedItem = selectedItem,
                        gold = gold,
                        motion = motion,
                    )
                }

                CeremonyStaggeredBlock(delayMs = 340, motion = motion) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 14.dp)
                            .clearAndSetSemantics { contentDescription = sparksSemantics },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WhisperHairline(gold = gold, isEInk = colors.isEInk)
                        Text(
                            text = sparksLabel,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            fontSize = 12.sp,
                            color = if (colors.isEInk) colors.textSecondary else gold.copy(alpha = 0.68f),
                            textAlign = TextAlign.Center,
                        )
                        WhisperHairline(gold = gold, isEInk = colors.isEInk)
                    }
                }

                CeremonyStaggeredBlock(delayMs = 420, motion = motion) {
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
}

@Composable
private fun CeremonyStaggeredBlock(
    delayMs: Long,
    motion: Boolean,
    content: @Composable () -> Unit,
) {
    val reveal = remember(motion) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) {
            delay(delayMs)
            reveal.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = reveal.value
            translationY = (1f - reveal.value) * 16.dp.toPx()
        },
    ) {
        content()
    }
}

@Composable
private fun CeremonyConstellationStage(
    palette: CrystalPalette,
    gold: Color,
    rose: Color,
    selectedItem: CrystalItem?,
    motion: Boolean,
    onToggleItem: (CrystalItem) -> Unit,
) {
    val colors = AuroraTheme.colors
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp),
    ) {
        val stageW = constraints.maxWidth.toFloat()
        val stageH = constraints.maxHeight.toFloat()

        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
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
            drawPath(
                path = upperArc,
                color = if (colors.isEInk) colors.textSecondary.copy(alpha = 0.5f) else gold.copy(alpha = 0.24f),
                style = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect),
            )
            drawPath(
                path = lowerArc,
                color = if (colors.isEInk) colors.textSecondary.copy(alpha = 0.35f) else rose.copy(alpha = 0.18f),
                style = Stroke(width = 1.dp.toPx()),
            )
            if (!colors.isEInk) {
                drawCircle(gold.copy(alpha = 0.55f), radius = 2.dp.toPx(), center = Offset(w * 0.50f, h * 0.34f))
                drawCircle(rose.copy(alpha = 0.45f), radius = 1.3.dp.toPx(), center = Offset(w * 0.24f, h * 0.44f))
                drawCircle(rose.copy(alpha = 0.45f), radius = 1.3.dp.toPx(), center = Offset(w * 0.76f, h * 0.44f))
            }
        }

        val orbSize = 56.dp
        CrystalConstellationSlots.forEachIndexed { index, slot ->
            val isSelected = selectedItem == slot.item
            val isDimmed = selectedItem != null && !isSelected
            val entrance = remember(slot.item, motion) { Animatable(if (motion) 0f else 1f) }
            LaunchedEffect(slot.item, motion) {
                if (motion) {
                    delay(140L + index * 55L)
                    entrance.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                }
            }
            val orbPx = with(LocalDensity.current) { orbSize.toPx() }
            val offsetX = (stageW * slot.xFraction - orbPx / 2f).roundToInt()
            val offsetY = (stageH * slot.yFraction - orbPx / 2f).roundToInt()

            ConstellationOrb(
                item = slot.item,
                palette = palette,
                gold = gold,
                rose = rose,
                selected = isSelected,
                dimmed = isDimmed,
                entrance = entrance.value,
                motion = motion,
                onClick = { onToggleItem(slot.item) },
                modifier = Modifier
                    .offset { IntOffset(offsetX, offsetY) }
                    .size(orbSize),
            )
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
    entrance: Float,
    motion: Boolean,
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
    val scale by animateFloatAsState(targetScale, animSpec, label = "orbScale")
    val alpha by animateFloatAsState(targetAlpha, animSpec, label = "orbAlpha")
    val ringAlpha by animateFloatAsState(if (selected) 1f else 0f, animSpec, label = "orbRing")
    val interactions = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .graphicsLayer {
                val combinedScale = (0.72f + 0.28f * entrance) * scale
                scaleX = combinedScale
                scaleY = combinedScale
                this.alpha = entrance * alpha
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
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.43f

            if (colors.isEInk) {
                drawCircle(colors.surface, radius = baseRadius, center = center)
                drawCircle(
                    colors.textPrimary,
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = if (selected) 2.dp.toPx() else 1.dp.toPx()),
                )
            } else {
                // Soft outer golden-rose bloom
                val bloomRadius = size.minDimension * (if (selected) 0.58f else 0.50f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to gold.copy(alpha = if (selected) 0.44f else 0.22f),
                        0.52f to rose.copy(alpha = if (selected) 0.20f else 0.10f),
                        1f to Color.Transparent,
                        center = center,
                        radius = bloomRadius,
                    ),
                    radius = bloomRadius,
                    center = center,
                )
                // Inner velvet jewel well
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to if (selected) Color(0xE62C2A42) else Color(0xBA1E2438),
                        1f to if (selected) Color(0xF0121626) else Color(0xDB0C101C),
                        center = center,
                        radius = baseRadius,
                    ),
                    radius = baseRadius,
                    center = center,
                )
                // Delicate rim shimmer
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(
                            gold.copy(alpha = if (selected) 0.62f else 0.34f),
                            rose.copy(alpha = if (selected) 0.28f else 0.14f),
                        ),
                    ),
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
                // Selection outer halo ring
                if (ringAlpha > 0.01f) {
                    drawCircle(
                        color = gold.copy(alpha = 0.58f * ringAlpha),
                        radius = baseRadius + 4.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                // Expanding concentric tap ripple
                val rp = rippleProgress.value
                if (rp < 0.99f) {
                    drawCircle(
                        color = gold.copy(alpha = (1f - rp) * 0.72f),
                        radius = baseRadius * (0.94f + rp * 0.55f),
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
            }
        }

        CrystalGlyphCanvas(
            glyph = item.glyph,
            modifier = Modifier.size(28.dp),
            palette = palette,
            animate = motion,
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
            .heightIn(min = 76.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = selectedItem,
            transitionSpec = {
                if (motion) {
                    fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                } else {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                }
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
