package eu.kanade.presentation.easteregg.crystal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import tachiyomi.presentation.core.util.LocalAppHaptics

internal data class CrystalSceneLabels(
    val back: String,
    val title: String,
    val start: String,
    val continueLabel: String,
    val recognizes: String,
    val dedication: String,
    val moveHint: String,
    val soundOn: String,
    val soundOff: String,
    val loading: String,
    val retry: String,
    val loadFailed: String,
    val saving: String,
    val saveFailed: String,
    val startAction: String = start,
)

/** Authored visual scene. Its only business signal is an explicit, once-per-session finale callback. */
@Composable
internal fun CrystalMemorialScene(
    labels: CrystalSceneLabels,
    onBack: () -> Unit,
    onFinale: () -> Unit,
    onContinue: () -> Unit,
    onRetryGrant: () -> Unit,
    receiptReady: Boolean,
    receiptFailed: Boolean,
    modifier: Modifier = Modifier,
    portrait: CrystalPortrait = CrystalPortrait.Warm,
    front: CrystalDissolveStyle = CrystalDissolveStyle.Layers,
    reduceMotion: Boolean = false,
    soundEnabled: Boolean = true,
    onSoundChanged: (Boolean) -> Unit = {},
    previewTimeMs: Float? = null,
) {
    val context = LocalContext.current
    val colors = AuroraTheme.colors
    val haptics = LocalAppHaptics.current
    val policy = rememberCrystalMotionPolicy()
    val reduced = reduceMotion || !policy.systemAnimations || colors.isEInk
    val total = CrystalTimeline.total(reduced)
    var attempt by remember { mutableIntStateOf(0) }
    val prepared by produceState<Result<CrystalPortraitPool>?>(null, portrait, front, attempt) {
        value = null
        value = try {
            Result.success(
                withContext(Dispatchers.Default) {
                    CrystalPortraitPool.prepare(context.resources, portrait, front)
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val pool = prepared?.getOrNull()
    var started by rememberSaveable(portrait, front) { mutableStateOf(false) }
    var finished by rememberSaveable(portrait, front) { mutableStateOf(false) }
    val elapsed = rememberSaveable(portrait, front) { mutableFloatStateOf(0f) }
    val sound = soundEnabled
    val onFinaleLatest by rememberUpdatedState(onFinale)
    val audio = remember(context) { CrystalMemorialAudio(context.applicationContext) }
    DisposableEffect(audio) { onDispose { audio.release() } }
    LaunchedEffect(policy.resumed, sound) { if (!policy.resumed || !sound) audio.stop() }
    LaunchedEffect(started, policy.resumed, reduced, previewTimeMs, pool) {
        if (!started || finished || !policy.resumed || pool == null || previewTimeMs != null) return@LaunchedEffect
        if (!policy.systemAnimations || colors.isEInk) {
            elapsed.floatValue = total
        } else {
            var previous: Long? = null
            while (isActive && elapsed.floatValue < total) {
                withFrameNanos { now ->
                    previous?.let {
                        elapsed.floatValue =
                            (elapsed.floatValue + (now - it).coerceAtLeast(0L) / 1_000_000f).coerceAtMost(total)
                    }
                    previous = now
                }
            }
        }
        if (elapsed.floatValue >= total && !finished) {
            finished = true
            haptics.tap()
        }
    }
    // Re-deliver after a recreated composition if persistence had not yet committed. The repository is idempotent.
    LaunchedEffect(finished, receiptReady, receiptFailed, previewTimeMs) {
        if (finished && !receiptReady && !receiptFailed && previewTimeMs == null) onFinaleLatest()
    }
    val ambient = rememberCrystalTime(!reduced && previewTimeMs == null)
    val sensor by rememberCrystalSensor(!reduced)
    var drag by remember { mutableStateOf<Offset?>(null) }
    val pointer = drag ?: sensor
    val touchExploration = rememberCrystalTouchExploration()
    var controlsRequest by remember { mutableIntStateOf(0) }
    var controlsPeek by remember { mutableStateOf(false) }
    LaunchedEffect(controlsRequest, started, finished, policy.resumed) {
        controlsPeek = false
        if (controlsRequest > 0 && started && !finished && policy.resumed) {
            controlsPeek = true
            delay(3000)
            controlsPeek = false
        }
    }
    val keyboardInput = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val showControls =
        !started || finished || previewTimeMs != null || controlsPeek || touchExploration || keyboardInput
    val begin = {
        if (pool != null && !started && previewTimeMs == null && policy.resumed) {
            haptics.tap()
            started = true
            if (sound) audio.play()
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        val sceneHeight = maxHeight
        val horizontalArtwork = maxWidth > maxHeight
        val compactLayout = maxHeight < 720.dp || horizontalArtwork || LocalDensity.current.fontScale > 1.3f
        val scriptSize = (maxWidth.value * 0.20f).coerceIn(48f, 80f) / LocalDensity.current.fontScale
        val idleOpacity = {
            if (!started &&
                previewTimeMs == null
            ) {
                1f
            } else {
                CrystalTimeline.epitaphOpacity(previewTimeMs ?: elapsed.floatValue, reduced)
            }
        }
        val sceneTime = { previewTimeMs ?: elapsed.floatValue }
        val farewellOpacity = { CrystalTimeline.farewellOpacity(sceneTime(), reduced) }
        val artworkInput = Modifier
            .pointerInput(reduced) {
                if (!reduced) {
                    detectDragGestures(onDragEnd = {
                        drag = null
                    }, onDragCancel = { drag = null }) { change, _ ->
                        change.consume()
                        drag =
                            Offset(
                                (change.position.x / size.width * 2 - 1).coerceIn(-1f, 1f),
                                (
                                    change.position.y /
                                        size.height *
                                        2 -
                                        1
                                    ).coerceIn(-1f, 1f),
                            )
                    }
                }
            }
            .pointerInput(started, finished, previewTimeMs) {
                detectTapGestures {
                    if (started && !finished && previewTimeMs == null) controlsRequest++
                }
            }
        if (pool != null && !compactLayout) {
            CrystalMemorialCanvas(
                pool,
                { previewTimeMs ?: elapsed.floatValue },
                { if (previewTimeMs == null) ambient.value else 0f },
                pointer,
                reduced,
                Modifier.fillMaxSize().then(artworkInput),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(
                WindowInsets.safeDrawing.asPaddingValues(),
            ).padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(tween(if (reduced) 0 else 240)),
                    exit = fadeOut(tween(if (reduced) 0 else 420)),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, labels.back, tint = colors.textSecondary)
                        }
                        Text(
                            labels.title,
                            Modifier.weight(1f).padding(horizontal = 10.dp),
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            onClick = {
                                onSoundChanged(!sound)
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                if (sound) {
                                    Icons.AutoMirrored.Outlined.VolumeUp
                                } else {
                                    Icons.AutoMirrored.Outlined.VolumeOff
                                },
                                if (sound) labels.soundOff else labels.soundOn,
                                tint = colors.textSecondary,
                            )
                        }
                    }
                }
            }
            if (compactLayout) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val areaHeight = maxHeight
                    val areaWidth = maxWidth
                    val compactScript = ((if (horizontalArtwork) areaWidth.value * 0.5f else areaWidth.value) * 0.20f)
                        .coerceIn(40f, 70f) / LocalDensity.current.fontScale
                    val artwork: @Composable (Modifier) -> Unit = { artModifier ->
                        if (pool != null) {
                            CrystalMemorialCanvas(
                                pool,
                                { previewTimeMs ?: elapsed.floatValue },
                                { if (previewTimeMs == null) ambient.value else 0f },
                                pointer,
                                reduced,
                                artModifier.clip(RoundedCornerShape(24.dp)).clipToBounds().then(artworkInput),
                                fittedArtwork = true,
                            )
                        }
                    }
                    if (horizontalArtwork) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            artwork(Modifier.weight(1f).fillMaxHeight())
                            CrystalCompactTextStage(
                                labels,
                                started,
                                finished,
                                idleOpacity,
                                sceneTime,
                                reduced,
                                compactScript,
                                Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            artwork(Modifier.weight(1f).fillMaxWidth())
                            CrystalCompactTextStage(
                                labels,
                                started,
                                finished,
                                idleOpacity,
                                sceneTime,
                                reduced,
                                compactScript,
                                Modifier.fillMaxWidth().heightIn(max = areaHeight * 0.55f),
                            )
                        }
                    }
                    if (prepared == null) {
                        Text(
                            labels.loading,
                            Modifier.align(Alignment.Center),
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (prepared?.isFailure ==
                        true
                    ) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(labels.loadFailed, color = colors.textPrimary, textAlign = TextAlign.Center)
                            Button(onClick = { attempt++ }) { Text(labels.retry) }
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp).graphicsLayer {
                            alpha =
                                idleOpacity()
                        }
                            .clearAndSetSemantics {
                                if (!started) contentDescription = "CRYSTAL. " + labels.dedication
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("✦", color = colors.accent, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "CRYSTAL",
                            color = colors.textPrimary,
                            fontFamily = CrystalFonts.Serif,
                            fontSize = (38f / LocalDensity.current.fontScale).sp,
                            letterSpacing = 5.sp,
                        )
                        Text(
                            labels.dedication,
                            color = colors.textSecondary,
                            fontFamily = CrystalFonts.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 18.sp,
                        )
                    }
                    Column(
                        Modifier.align(Alignment.Center).offset(y = sceneHeight * 0.12f)
                            .clearAndSetSemantics {
                                if (finished) {
                                    contentDescription =
                                        labels.recognizes + ". Farewell, Crystal."
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            labels.recognizes.uppercase(),
                            Modifier.graphicsLayer { alpha = farewellOpacity() },
                            color = colors.accent,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        CrystalFarewellWord(sceneTime, reduced, scriptSize)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "CRYSTAL",
                            Modifier.graphicsLayer {
                                alpha = CrystalTimeline.farewellNameOpacity(sceneTime(), reduced)
                            },
                            color = colors.textPrimary,
                            fontFamily = CrystalFonts.Serif,
                            fontSize = (21f / LocalDensity.current.fontScale).sp,
                            letterSpacing = 4.sp,
                        )
                    }
                    if (prepared ==
                        null
                    ) {
                        Text(
                            labels.loading,
                            Modifier.align(Alignment.Center),
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (prepared?.isFailure ==
                        true
                    ) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(labels.loadFailed, color = colors.textPrimary, textAlign = TextAlign.Center)
                            Button(onClick = { attempt++ }) { Text(labels.retry) }
                        }
                    }
                }
            }
            if (previewTimeMs == null) {
                Box(Modifier.fillMaxWidth().heightIn(min = 130.dp), contentAlignment = Alignment.BottomCenter) {
                    if (!finished) {
                        CrystalMemorialTouch(
                            labels,
                            pool != null,
                            started,
                            sceneTime,
                            { ambient.value },
                            reduced,
                            begin,
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = finished,
                        enter = fadeIn(animationSpec = tween(durationMillis = if (reduced) 0 else 820)),
                        exit = fadeOut(animationSpec = tween(durationMillis = if (reduced) 0 else 220)),
                    ) {
                        if (receiptFailed) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    labels.saveFailed,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                )
                                CrystalSceneButton(labels.retry, true, onRetryGrant)
                            }
                        } else {
                            CrystalFelineInvitationButton(
                                label = if (receiptReady) labels.continueLabel else labels.saving,
                                enabled = receiptReady,
                                onClick = onContinue,
                            )
                        }
                    }
                }
            }
            if (!reduced) {
                Text(
                    labels.moveHint,
                    Modifier.fillMaxWidth().padding(top = 10.dp).graphicsLayer { alpha = idleOpacity() }
                        .clearAndSetSemantics { if (!started) contentDescription = labels.moveHint },
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun CrystalCompactTextStage(
    labels: CrystalSceneLabels,
    started: Boolean,
    finished: Boolean,
    idleOpacity: () -> Float,
    sceneTime: () -> Float,
    reduced: Boolean,
    scriptSize: Float,
    modifier: Modifier,
) {
    val colors = AuroraTheme.colors
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().heightIn(
                max = maxHeight,
            ).verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
        ) {
            Column(
                Modifier.align(Alignment.Center).graphicsLayer { alpha = idleOpacity() }
                    .clearAndSetSemantics { if (!started) contentDescription = "CRYSTAL. " + labels.dedication },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("✦", color = colors.accent, fontSize = (12f / scale).sp)
                Text(
                    "CRYSTAL",
                    color = colors.textPrimary,
                    fontFamily = CrystalFonts.Serif,
                    fontSize = (32f / scale).sp,
                    letterSpacing = (4f / scale).sp,
                )
                Text(
                    labels.dedication,
                    color = colors.textSecondary,
                    fontFamily = CrystalFonts.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = (18f / scale).sp,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                Modifier.align(Alignment.Center)
                    .clearAndSetSemantics {
                        if (finished) {
                            contentDescription =
                                labels.recognizes + ". Farewell, Crystal."
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    labels.recognizes.uppercase(),
                    Modifier.graphicsLayer { alpha = CrystalTimeline.farewellOpacity(sceneTime(), reduced) },
                    color = colors.accent,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                )
                CrystalFarewellWord(sceneTime, reduced, scriptSize)
                Text(
                    "CRYSTAL",
                    Modifier.graphicsLayer { alpha = CrystalTimeline.farewellNameOpacity(sceneTime(), reduced) },
                    color = colors.textPrimary,
                    fontFamily = CrystalFonts.Serif,
                    fontSize = (21f / scale).sp,
                    letterSpacing = (4f / scale).sp,
                )
            }
        }
    }
}

@Composable
private fun CrystalSceneButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            .background(
                if (colors.isEInk) {
                    Brush.linearGradient(
                        listOf(colors.surface, colors.surface),
                    )
                } else {
                    Brush.linearGradient(
                        listOf(colors.accent.copy(alpha = 0.22f), colors.accentVariant.copy(alpha = 0.10f)),
                    )
                },
                shape,
            )
            .border(1.dp, if (colors.isEInk) colors.textPrimary else colors.accent.copy(alpha = 0.42f), shape),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.textSecondary,
        ),
    ) { Text(label, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) }
}
