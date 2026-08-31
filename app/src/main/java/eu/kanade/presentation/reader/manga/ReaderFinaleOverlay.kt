package eu.kanade.presentation.reader.manga

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import coil3.imageLoader
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.rememberSupportsBlurBehind
import eu.kanade.tachiyomi.ui.reader.model.ReaderFinaleState
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import android.graphics.Color as AndroidColor

/**
 * One-time «THE END» plate revealed on the end-of-manga transition when the last chapter of a
 * completed series gets read. Aurora glass window (blur-behind + soft dim, same chrome pattern
 * as [eu.kanade.presentation.reader.components.AuroraReaderSheet]) so the reader page stays
 * visible behind. Night reads (02:45–04:15, [ReaderFinaleState.nightVeil]) get a static gradient
 * wordmark and a slow glass sweep across the hairline rule; e-ink/reduced motion collapses
 * everything to a static opaque card; RTL hides the rotated seal.
 */
@Composable
fun ReaderFinaleOverlay(
    state: ReaderFinaleState,
    reducedMotion: Boolean,
    onBackToManga: () -> Unit,
    onStay: () -> Unit,
) {
    val context = LocalContext.current
    val colors = AuroraTheme.colors
    val haptics = LocalAppHaptics.current
    val supportsBlurBehind = rememberSupportsBlurBehind(colors.isEInk)
    var revealed by remember { mutableStateOf(reducedMotion) }
    var sealRevealed by remember { mutableStateOf(reducedMotion) }
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window

    LaunchedEffect(Unit) {
        if (!revealed) {
            haptics.tap()
            revealed = true
        }
    }
    LaunchedEffect(revealed) {
        if (revealed && !reducedMotion) {
            delay(FINALE_SEAL_DELAY_MS)
            sealRevealed = true
            haptics.tap()
        }
    }

    // One-shot window chrome setup — flags set once (no add/clear thrash, no open flicker).
    DisposableEffect(window, supportsBlurBehind) {
        val w = window
        if (w != null && supportsBlurBehind) {
            w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
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
    // Glass eases in shortly after the plate starts entering, so blur never flashes
    // full-strength before the panel is on screen.
    LaunchedEffect(window, revealed, supportsBlurBehind) {
        val w = window ?: return@LaunchedEffect
        if (!supportsBlurBehind || !revealed) return@LaunchedEffect
        delay(FINALE_GLASS_DELAY_MS)
        w.attributes = w.attributes.apply { blurBehindRadius = FINALE_BLUR_RADIUS_PX }
        w.setDimAmount(FINALE_WINDOW_DIM)
    }

    val sealShown = sealRevealed && LocalLayoutDirection.current != LayoutDirection.Rtl
    val letterSpacing by animateFloatAsState(
        targetValue = if (revealed) 8f else 18f,
        animationSpec = if (reducedMotion) {
            tween(0)
        } else {
            tween(FINALE_LS_MS, easing = FastOutSlowInEasing)
        },
        label = "finale-letter-spacing",
    )
    val plateShape = RoundedCornerShape(24.dp)

    Dialog(
        onDismissRequest = onStay,
        // Platform default dialog width (~290dp) is too tight for the wide-tracked «THE END»
        // wordmark — it wrapped mid-animation and re-laid out the window every frame.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visible = revealed,
            enter = if (reducedMotion) {
                fadeIn()
            } else {
                fadeIn(tween(FINALE_ENTER_MS, easing = FINALE_EASING)) + scaleIn(
                    animationSpec = tween(FINALE_ENTER_MS + FINALE_SCALE_EXTRA_MS, easing = FINALE_EASING),
                    initialScale = 0.92f,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = PLATE_MAX_WIDTH)
                            .clip(plateShape)
                            .background(finalePlateBackground(colors, supportsBlurBehind))
                            .border(1.dp, auroraRimColor(), plateShape)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "✦",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.nightVeil) colors.accent else colors.textSecondary,
                            )
                            FinaleRule(
                                veil = state.nightVeil && !reducedMotion,
                                color = colors.divider,
                                glassColor = colors.glass,
                            )
                            Text(
                                text = stringResource(MR.strings.reader_finale_title),
                                // letterSpacing adds trailing space after the last glyph —
                                // pad start by ls/2 so the glyphs stay optically centered.
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (letterSpacing / 2f).dp),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = letterSpacing.sp,
                                color = if (state.nightVeil) {
                                    Color.Unspecified
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                style = if (state.nightVeil) {
                                    TextStyle(
                                        brush = Brush.linearGradient(
                                            listOf(colors.accent, colors.gradientPurple),
                                        ),
                                    )
                                } else {
                                    TextStyle.Default
                                },
                            )
                            Text(
                                text = stringResource(MR.strings.reader_finale_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The reader's ComposeView context resolves a plain Coil loader without
                            // the app's cover fetchers, so the cover is loaded through the
                            // application ImageLoader explicitly.
                            val appContext = context.applicationContext
                            val coverImageLoader = remember(appContext) { appContext.imageLoader }
                            val coverRequest = remember(state.manga) {
                                buildAuroraCoverImageRequest(context, state.manga)
                            }
                            AsyncImage(
                                model = coverRequest,
                                imageLoader = coverImageLoader,
                                contentDescription = stringResource(MR.strings.manga_cover),
                                contentScale = ContentScale.Crop,
                                error = rememberThemeAwareCoverErrorPainter(),
                                modifier = Modifier
                                    .size(width = 72.dp, height = 108.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = state.manga.displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = finaleJourneyLine(state),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = onBackToManga,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(MR.strings.reader_finale_back_to_manga),
                                    maxLines = 1,
                                )
                            }
                            TextButton(onClick = onStay) {
                                Text(
                                    text = stringResource(MR.strings.reader_finale_stay),
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    // «A · round stamp»: double-ring seal sitting on the top-right plate corner,
                    // half overhanging the edge — pressed in with an overshoot.
                    AnimatedVisibility(
                        visible = sealShown,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 16.dp, y = (-16).dp),
                        enter = if (reducedMotion) {
                            fadeIn()
                        } else {
                            fadeIn(tween(FINALE_SEAL_FADE_MS)) + scaleIn(
                                animationSpec = tween(FINALE_SEAL_POP_MS, easing = FINALE_SEAL_EASE),
                                initialScale = 1.6f,
                            )
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .rotate(-12f)
                                .border(2.dp, colors.accent, CircleShape)
                                .padding(5.dp)
                                .border(1.dp, colors.accent.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = stringResource(MR.strings.reader_finale_stamp_label),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.4.sp,
                                    maxLines = 1,
                                    color = colors.accent,
                                )
                                Text(
                                    text = state.finishedOn,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.4.sp,
                                    maxLines = 1,
                                    color = colors.accent,
                                )
                                Text(
                                    text = "✦ tadami ✦",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = colors.accent.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Plate surface: real Aurora glass when the window blurs the reader behind (translucent with a
 * darker top for the "верхний дим" feel), opaque fallback for e-ink and pre-S devices.
 */
@Composable
private fun finalePlateBackground(colors: AuroraColors, supportsBlurBehind: Boolean): Brush {
    return when {
        colors.isEInk -> SolidColor(MaterialTheme.colorScheme.surface)
        !supportsBlurBehind -> SolidColor(colors.surface)
        colors.isDark -> Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.80f),
                Color.Black.copy(alpha = 0.60f),
            ),
        )
        else -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.94f),
                Color.White.copy(alpha = 0.87f),
            ),
        )
    }
}

@Composable
private fun finaleJourneyLine(state: ReaderFinaleState): String {
    val parts = buildList {
        add(
            pluralStringResource(MR.plurals.reader_finale_chapters, state.chapterCount, state.chapterCount),
        )
        state.daysOnShelf?.let { days ->
            val daysInt = days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            add(pluralStringResource(MR.plurals.reader_finale_days_on_shelf, daysInt, daysInt))
        }
    }
    return parts.joinToString(" · ")
}

/**
 * Hairline rule with an optional night-veil sweep: one soft glass pass per ~13 s
 * (active duty ~12%), quiet the rest of the cycle.
 */
@Composable
private fun FinaleRule(
    veil: Boolean,
    color: Color,
    glassColor: Color,
) {
    Box(
        modifier = Modifier
            .size(width = RULE_WIDTH, height = 1.dp)
            .background(color),
    ) {
        if (veil) {
            val transition = rememberInfiniteTransition(label = "finale-rule")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(FINALE_RULE_SWEEP_PERIOD_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "finale-rule-sweep",
            )
            val phase = progress / RULE_SWEEP_DUTY
            val alpha = if (phase <= 1f) {
                kotlin.math.sin(phase * kotlin.math.PI).toFloat()
            } else {
                0f
            }
            Box(
                modifier = Modifier
                    .offset(x = -RULE_STRIP_WIDTH + (RULE_WIDTH + RULE_STRIP_WIDTH) * phase.coerceIn(0f, 1f))
                    .alpha(alpha)
                    .size(width = RULE_STRIP_WIDTH, height = 1.dp)
                    .background(
                        Brush.horizontalGradient(listOf(Color.Transparent, glassColor, Color.Transparent)),
                    ),
            )
        }
    }
}

private const val FINALE_ENTER_MS = 1400
private const val FINALE_SCALE_EXTRA_MS = 160
private const val FINALE_LS_MS = 1400
private const val FINALE_GLASS_DELAY_MS = 260L
private const val FINALE_SEAL_DELAY_MS = 1300L
private const val FINALE_SEAL_POP_MS = 800
private const val FINALE_SEAL_FADE_MS = 220
private const val FINALE_BLUR_RADIUS_PX = 72
private const val FINALE_WINDOW_DIM = 0.54f
private const val FINALE_RULE_SWEEP_PERIOD_MS = 13_000
private const val RULE_SWEEP_DUTY = 0.12f
private val RULE_WIDTH = 74.dp
private val RULE_STRIP_WIDTH = 28.dp
private val PLATE_MAX_WIDTH = 360.dp
private val FINALE_EASING = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val FINALE_SEAL_EASE = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
