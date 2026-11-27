package eu.kanade.presentation.easteregg.crystal

import android.graphics.Paint
import android.graphics.RectF
import android.view.ViewTreeObserver
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.domain.easteregg.crystal.CrystalAppearancePreferences
import eu.kanade.domain.easteregg.crystal.CrystalAtmosphereModel
import eu.kanade.domain.easteregg.crystal.CrystalCalendarMoment
import eu.kanade.domain.easteregg.crystal.CrystalDayPhase
import eu.kanade.domain.easteregg.crystal.CrystalWeather
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.presentation.more.settings.screen.SettingsTreasuryScreen
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import kotlin.math.min
import kotlin.math.roundToInt

private data class LivingPainterState(val painter: CrystalLivingCornerPainter? = null, val failed: Boolean = false)

@Composable
private fun rememberLivingPainter(useHeroCache: Boolean = true): State<LivingPainterState> {
    val assets = LocalContext.current.applicationContext.assets
    val result = remember(assets, useHeroCache) {
        val precompiled = if (useHeroCache) CrystalLivingCornerPainter.cachedHeroOrNull() else null
        mutableStateOf(
            if (precompiled != null) LivingPainterState(precompiled) else LivingPainterState(),
        )
    }
    LaunchedEffect(assets, useHeroCache) {
        if (result.value.painter != null) return@LaunchedEffect
        try {
            val painter = withContext(Dispatchers.IO) {
                if (useHeroCache) {
                    CrystalLivingCornerPainter.loadCachedHero(assets)
                } else {
                    CrystalLivingCornerPainter(CrystalLivingArtwork.loadCached(assets))
                }
            }
            result.value = LivingPainterState(painter)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            result.value = LivingPainterState(failed = true)
        }
    }
    return result
}

internal fun currentCrystalCalendarMoment(): CrystalCalendarMoment = Calendar.getInstance().let {
    CrystalCalendarMoment(
        it.get(Calendar.YEAR),
        it.get(Calendar.MONTH),
        it.get(Calendar.DAY_OF_MONTH),
        it
            .get(Calendar.HOUR_OF_DAY),
    )
}

@Composable
internal fun rememberLivingMoment(active: Boolean): State<CrystalCalendarMoment> {
    val moment = remember { mutableStateOf(currentCrystalCalendarMoment()) }
    LaunchedEffect(active) {
        if (active) {
            while (isActive) {
                moment.value = currentCrystalCalendarMoment()
                delay(60_000L - System.currentTimeMillis() % 60_000L + 30L)
            }
        }
    }
    return moment
}

@Composable
internal fun rememberLivingWindowFocus(): State<Boolean> {
    val view = LocalView.current
    val focused = remember(view) { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(view) {
        val observer = ViewTreeObserver.OnWindowFocusChangeListener { focused.value = it }
        view.viewTreeObserver.addOnWindowFocusChangeListener(observer)
        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver
                    .removeOnWindowFocusChangeListener(observer)
            }
        }
    }
    return focused
}

internal const val CRYSTAL_LIVING_ASPECT_RATIO = 640f / 530f
internal const val CRYSTAL_LIVING_PREVIEW_ASPECT_RATIO = 640f / 416f

internal fun crystalLivingArtworkHeight(width: Float): Float = width * 530f / 640f

internal fun crystalLivingVisibleControls(labMode: Boolean): Boolean = labMode

internal data class LivingViewport(val size: IntSize) {
    private val camera = CrystalLivingViewBox(0f, -110f, 640f, 530f).fit(size.width.toFloat(), size.height.toFloat())
    val scale = camera.scale.coerceAtLeast(.0001f)
    val left = camera.offsetX
    val top = camera.offsetY
    fun world(point: Offset) = Offset((point.x - left) / scale, (point.y - top) / scale)
    fun pixel(x: Float, y: Float) = Offset(left + x * scale, top + y * scale)
}

internal fun isCrystalLivingViewActive(
    resumed: Boolean,
    focused: Boolean,
    visible: Boolean,
    top: Boolean,
    occluded: Boolean,
): Boolean = resumed && focused && visible && top && !occluded

internal fun isCrystalLivingMotionActive(
    viewActive: Boolean,
    paused: Boolean,
    reduced: Boolean,
    loaded: Boolean,
): Boolean = viewActive && !paused && !reduced && loaded

private enum class LivingMessage { REST, PLAY, PET, CRYSTAL, FEATHER, POM, PAUSED, STATIC }

/** One native scene/frame owner; inventory and ceremony state remain outside this host. */
@Composable
internal fun CrystalLivingCornerHero(
    palette: CrystalPalette,
    reduceMotion: Boolean,
    occluded: Boolean = false,
    modifier: Modifier = Modifier,
    labMode: Boolean = false,
    forcedPhase: CrystalDayPhase? = null,
    forcedWeather: CrystalWeather? = null,
    forcedMoment: CrystalCalendarMoment? = null,
) {
    val appColors = AuroraTheme.colors
    val preferences = remember { Injekt.get<CrystalAppearancePreferences>() }
    val seed = remember(preferences, labMode) {
        if (labMode) {
            preferences.atmosphereSeed().get().ifEmpty { "crystal-debug-v4" }
        } else {
            preferences
                .getOrCreateAtmosphereSeed()
        }
    }
    val engine = remember(seed) { CrystalLivingEngine(seed.hashCode()) }
    val frame = remember(engine) { mutableStateOf(engine.snapshot()) }
    val clock = remember(engine) { CrystalFrameClock() }
    val policy = rememberCrystalMotionPolicy()
    val focused by rememberLivingWindowFocus()
    val navigator = LocalNavigator.current
    val top = navigator?.lastItem == if (labMode) CrystalLivingCornerDebugScreen else CrystalCollectionScreen
    val view = LocalView.current
    var visible by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    val loaded by rememberLivingPainter(useHeroCache = !labMode)
    val assets = LocalContext.current.applicationContext.assets
    val calendarActive = isCrystalLivingViewActive(policy.resumed, focused, visible, top, occluded)
    val liveMoment by rememberLivingMoment(calendarActive)
    val moment = forcedMoment ?: liveMoment
    val phase = forcedPhase ?: CrystalAtmosphereModel.phaseAt(moment)
    val weather = forcedWeather ?: CrystalAtmosphereModel.weatherAt(moment, seed)
    val sceneColors = remember(phase, weather, palette, appColors) {
        resolveCrystalLivingColors(
            phase,
            weather,
            palette,
            appColors.isEInk,
            appColors.isDark,
            appColors.isAmoled,
            appColors.eInkProfile == EInkProfile.COLOR,
        )
    }
    var heroSnapshot by remember(sceneColors, phase, weather, palette, labMode) {
        mutableStateOf(
            if (labMode) {
                null
            } else {
                CrystalLivingCornerPainter.cachedHeroSnapshotOrNull(sceneColors, phase, weather, palette)
            },
        )
    }
    LaunchedEffect(assets, sceneColors, phase, weather, palette, labMode) {
        if (!labMode && heroSnapshot == null) {
            runCatching {
                withContext(Dispatchers.Default) {
                    val widthPx = view.width.takeIf { it > 0 } ?: 1080
                    CrystalLivingCornerPainter.prewarmHeroSnapshot(
                        assets = assets,
                        widthPx = widthPx,
                        colors = sceneColors,
                        phase = phase,
                        weather = weather,
                        palette = palette,
                        seedHash = seed.hashCode(),
                    )
                }
            }.getOrNull()?.let { heroSnapshot = it }
        }
    }
    val snapshotPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val snapshotDst = remember { RectF() }
    val reduced = reduceMotion || !policy.systemAnimations || appColors.isEInk
    val running = isCrystalLivingMotionActive(calendarActive, paused, reduced, loaded.painter != null)
    val canvasAlpha by animateFloatAsState(
        targetValue = if (loaded.painter != null || heroSnapshot != null) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(260),
        label = "livingCornerCanvasAlpha",
    )
    val haptics = LocalAppHaptics.current
    var manualMessage by remember { mutableStateOf(LivingMessage.REST) }
    var messageUntil by remember { mutableStateOf(0f) }
    val message by remember(frame, reduced, paused, manualMessage, messageUntil) {
        derivedStateOf {
            val state = frame.value
            when {
                reduced -> LivingMessage.STATIC
                paused -> LivingMessage.PAUSED
                manualMessage != LivingMessage.REST && state.elapsedSeconds < messageUntil -> manualMessage
                state.playing -> LivingMessage.PLAY
                state.happiness > .05f -> LivingMessage.PET
                else -> LivingMessage.REST
            }
        }
    }
    val playing by remember(frame) { derivedStateOf { frame.value.playing } }
    val dragging by remember(frame) { derivedStateOf { frame.value.dragging } }
    fun interact(kind: LivingMessage, part: CrystalLivingPart? = null) {
        if (!running) return
        val accepted = when (kind) {
            LivingMessage.PLAY -> engine.play()
            LivingMessage.PET -> engine.pet()
            else -> part?.let(engine::activateProp) ?: false
        }
        if (accepted) {
            haptics.tap()
            manualMessage = kind
            messageUntil = engine.snapshot().elapsedSeconds + 2.4f
        }
    }
    LaunchedEffect(engine, reduced) {
        engine.setReduced(reduced)
        frame.value = if (reduced) engine.restingSnapshot() else engine.snapshot()
    }
    LaunchedEffect(engine, running) {
        clock.pause()
        engine.pause()
        frame.value = if (reduced) engine.restingSnapshot() else engine.snapshot()
        if (running) {
            var previous: Float? = null
            var lastRenderNanos = 0L
            try {
                while (isActive) {
                    withFrameNanos { now ->
                        // Run cozy idle breathing at ~36fps and interactive play/drag at ~60fps.
                        val minInterval = if (frame.value.dragging || frame.value.playing) 15_000_000L else 27_000_000L
                        if (lastRenderNanos != 0L && now - lastRenderNanos < minInterval) return@withFrameNanos
                        lastRenderNanos = now
                        val milliseconds = clock.advance(now, true)
                        val delta = previous?.let { (milliseconds - it).coerceAtLeast(0f) / 1000f } ?: 0f
                        previous = milliseconds
                        frame.value = engine.advance(delta)
                    }
                }
            } finally {
                clock.pause()
                engine.pause()
            }
        }
    }
    val encounterPreference = remember(preferences) { preferences.rainEncountered() }
    val encountered by encounterPreference.collectAsStateWithLifecycle()
    var showEncounter by remember { mutableStateOf(false) }
    val forced = labMode || forcedPhase != null || forcedWeather != null || forcedMoment != null
    LaunchedEffect(weather, forced, calendarActive, encountered, loaded.painter) {
        if (!encountered && loaded.painter != null && CrystalAtmosphereModel.shouldRecordEncounter(
                weather,
                forced,
                calendarActive,
            )
        ) {
            encounterPreference.set(true)
            showEncounter = true
        }
    }
    LaunchedEffect(showEncounter) {
        if (showEncounter) {
            delay(4500)
            showEncounter = false
        }
    }

    val skyDescription = crystalLivingPhaseLabel(phase) + ", " + crystalLivingWeatherLabel(weather)
    val playLabel = stringResource(AYMR.strings.crystal_living_play)
    val petLabel = stringResource(AYMR.strings.crystal_living_pet)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CRYSTAL_LIVING_ASPECT_RATIO)
                .background(Color(sceneColors.wall))
                .clipToBounds()
                .semantics {
                    stateDescription = skyDescription
                    customActions = listOf(
                        CustomAccessibilityAction(playLabel) {
                            if (running) {
                                interact(LivingMessage.PLAY)
                                true
                            } else {
                                false
                            }
                        },
                        CustomAccessibilityAction(petLabel) {
                            if (running) {
                                interact(LivingMessage.PET)
                                true
                            } else {
                                false
                            }
                        },
                    )
                }
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    val clipped = (min(bounds.bottom, view.height.toFloat()) - maxOf(bounds.top, 0f)).coerceAtLeast(0f)
                    visible = clipped > coordinates.size.height * .15f && bounds.width > 0f
                },
        ) {
            val sceneSize = IntSize(constraints.maxWidth, constraints.maxHeight)
            val viewport = remember(sceneSize) { LivingViewport(sceneSize) }
            val artScale = with(LocalDensity.current) { sceneSize.width.toDp().value / 390f }
                .takeIf { it > 0f }?.coerceIn(.75f, 1.2f) ?: 1f
            Canvas(
                Modifier.fillMaxSize().graphicsLayer { alpha = canvasAlpha }.clearAndSetSemantics { }
                    .pointerInput(engine, running, viewport) {
                        if (!running) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val world = viewport.world(down.position)
                            val toy = engine.hitToy(world.x, world.y, 22.dp.toPx() / viewport.scale)
                            if (toy != null) {
                                val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                if (start != null && engine.beginDrag(toy, world.x, world.y)) {
                                    var lastTime = start.uptimeMillis
                                    try {
                                        val completed = drag(start.id) { change ->
                                            val point = viewport.world(change.position)
                                            engine.dragTo(
                                                point.x,
                                                point.y,
                                                (
                                                    (
                                                        change
                                                            .uptimeMillis - lastTime
                                                        ) / 1000f
                                                    ).coerceAtLeast(.008f),
                                            )
                                            lastTime = change.uptimeMillis
                                            change.consume()
                                        }
                                        engine.endDrag(cancelled = !completed)
                                    } finally {
                                        engine.endDrag(cancelled = true)
                                    }
                                }
                            } else {
                                val up = waitForUpOrCancellation()
                                if (up != null && !up.isConsumed && (up.position - down.position)
                                        .getDistance() < viewConfiguration.touchSlop
                                ) {
                                    val head = ((world.x - 403f) / 66f).let { it * it } + ((world.y - 163f) / 52f)
                                        .let { it * it }
                                    val body = ((world.x - 400f) / 68f).let { it * it } + ((world.y - 265f) / 75f)
                                        .let { it * it }
                                    if (head < 1f || body < 1f) interact(LivingMessage.PET)
                                }
                            }
                        }
                    }
                    .pointerInput(engine, running, viewport) {
                        if (!running) return@pointerInput
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val point = event.changes.firstOrNull()
                                if (event.type == PointerEventType.Move && point?.type == PointerType.Mouse) {
                                    val world = viewport.world(point.position)
                                    engine.lookAt((world.x - 403f) / 115f, (world.y - 166f) / 210f)
                                }
                            }
                        }
                    },
            ) {
                val snapshot = heroSnapshot
                if (snapshot != null && !snapshot.isRecycled && (occluded || !running)) {
                    // Single GPU texture blit during screen transitions / paused state (0 SVG tessellation).
                    drawIntoCanvas {
                        snapshotDst.set(0f, 0f, this.size.width, this.size.height)
                        it.nativeCanvas.drawBitmap(snapshot, null, snapshotDst, snapshotPaint)
                    }
                } else {
                    val currentFrame = if (reduced) engine.restingSnapshot() else frame.value
                    loaded.painter?.let { painter ->
                        drawIntoCanvas {
                            painter.draw(
                                it.nativeCanvas,
                                this.size.width,
                                this.size.height,
                                currentFrame,
                                sceneColors,
                                phase,
                                weather,
                                reduced,
                                palette = palette,
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.TopStart)
                    .padding(
                        start = 20.dp,
                        top = 20.dp + with(LocalDensity.current) { viewport.top.toDp() },
                    )
                    .widthIn(max = 220.dp)
                    .clearAndSetSemantics { },
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    stringResource(AYMR.strings.crystal_section_title),
                    fontFamily = CrystalFonts.Serif,
                    fontSize = (57f * artScale).sp,
                    lineHeight = (66f * artScale).sp,
                    color = Color(sceneColors.heading),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .size(width = (42f * artScale).dp, height = 1.2.dp)
                        .background(
                            Color(sceneColors.heading).copy(alpha = 0.48f),
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
            Text(
                stringResource(AYMR.strings.crystal_living_subtitle),
                Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 12.dp),
                color = Color(sceneColors.muted),
                fontFamily = CrystalFonts.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
            if (crystalLivingVisibleControls(labMode)) {
                IconButton(
                    onClick = { paused = !paused },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    enabled = !reduced,
                ) {
                    Icon(
                        if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        stringResource(
                            if (paused) {
                                AYMR
                                    .strings.crystal_living_resume
                            } else {
                                AYMR.strings.crystal_living_pause
                            },
                        ),
                        tint = Color(
                            sceneColors
                                .heading,
                        ),
                    )
                }
            }
            val density = LocalDensity.current
            val propPoints = listOf(
                Triple(CrystalLivingPart.CRYSTAL, Offset(354f, 6f), AYMR.strings.crystal_living_touch_crystal),
                Triple(CrystalLivingPart.FEATHER, Offset(46f, 316f), AYMR.strings.crystal_living_touch_feather),
                Triple(CrystalLivingPart.POM, Offset(616f, 272f), AYMR.strings.crystal_living_touch_pom),
            )
            propPoints.forEach { (part, point, label) ->
                val projected = viewport.pixel(point.x, point.y)
                val half = with(density) { 24.dp.toPx() }
                val name = stringResource(label)
                val propInteractionSource = remember(part) { MutableInteractionSource() }
                Box(
                    modifier = Modifier.offset {
                        IntOffset(
                            (projected.x - half).coerceIn(
                                0f,
                                (
                                    sceneSize
                                        .width - 2 * half
                                    ).coerceAtLeast(0f),
                            ).roundToInt(),
                            (projected.y - half).coerceIn(
                                0f,
                                (sceneSize.height - 2 * half).coerceAtLeast(0f),
                            ).roundToInt(),
                        )
                    }
                        .size(48.dp)
                        .clickable(
                            interactionSource = propInteractionSource,
                            indication = null,
                            enabled = running,
                            role = Role.Button,
                            onClick = {
                                interact(
                                    when (part) {
                                        CrystalLivingPart.CRYSTAL -> LivingMessage.CRYSTAL
                                        CrystalLivingPart.FEATHER -> LivingMessage.FEATHER
                                        else -> LivingMessage.POM
                                    },
                                    part,
                                )
                            },
                        )
                        .semantics {
                            contentDescription = name
                            role = Role.Button
                        },
                )
            }
            if (showEncounter) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).widthIn(max = 250.dp),
                    color = if (appColors.isEInk) appColors.surface else Color(0xEE10222B),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, appColors.divider),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            stringResource(AYMR.strings.crystal_living_rain_title),
                            color = if (appColors
                                    .isEInk
                            ) {
                                appColors.textPrimary
                            } else {
                                Color(0xFFFFE6BA)
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                        Text(
                            stringResource(AYMR.strings.crystal_living_rain_description),
                            color = if (appColors
                                    .isEInk
                            ) {
                                appColors.textPrimary
                            } else {
                                Color(0xFFEDF1E5)
                            },
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
        if (crystalLivingVisibleControls(labMode)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { interact(LivingMessage.PLAY) },
                    enabled = running && !playing && !dragging,
                    modifier = Modifier.weight(1.4f).heightIn(min = 44.dp),
                    contentPadding = ButtonDefaults
                        .TextButtonContentPadding,
                ) {
                    Text(
                        stringResource(AYMR.strings.crystal_living_play),
                        fontSize = 12.sp,
                        maxLines = 2,
                    )
                }
                OutlinedButton(
                    onClick = { interact(LivingMessage.PET) },
                    enabled = running && !playing,
                    modifier =
                    Modifier.weight(1.2f).heightIn(min = 44.dp),
                    contentPadding = ButtonDefaults
                        .TextButtonContentPadding,
                ) {
                    Text(
                        stringResource(AYMR.strings.crystal_living_pet),
                        fontSize = 12.sp,
                        maxLines = 2,
                    )
                }
                OutlinedButton(
                    onClick = {
                        engine.reset()
                        frame.value = engine.snapshot()
                        manualMessage =
                            LivingMessage.REST
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    contentPadding =
                    ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(
                        stringResource(AYMR.strings.crystal_living_reset),
                        fontSize = 12.sp,
                        maxLines = 2,
                    )
                }
            }
        }
        val messageResource = when {
            loaded.failed -> AYMR.strings.crystal_living_load_failed
            loaded.painter == null -> AYMR.strings.crystal_living_loading
            else -> when (message) {
                LivingMessage.REST -> AYMR.strings.crystal_living_hint
                LivingMessage.PLAY -> AYMR.strings.crystal_living_playing
                LivingMessage.PET -> AYMR.strings.crystal_living_purring
                LivingMessage.CRYSTAL -> AYMR.strings.crystal_living_crystal_hint
                LivingMessage.FEATHER -> AYMR.strings.crystal_living_feather_hint
                LivingMessage.POM -> AYMR.strings.crystal_living_pom_hint
                LivingMessage.PAUSED -> AYMR.strings.crystal_living_paused
                LivingMessage.STATIC -> AYMR.strings.crystal_living_static
            }
        }
        Text(
            stringResource(messageResource),
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            color = appColors.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Static, cropped-room Treasury preview. It does not create a second animation clock or finding. */
@Composable
internal fun CrystalLivingCornerPreview(palette: CrystalPalette, modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    val preferences = remember { Injekt.get<CrystalAppearancePreferences>() }
    val seed = remember(preferences) { preferences.getOrCreateAtmosphereSeed() }
    val policy = rememberCrystalMotionPolicy()
    val focused by rememberLivingWindowFocus()
    var visible by remember { mutableStateOf(false) }
    val navigator = LocalNavigator.current
    val moment by rememberLivingMoment(
        policy.resumed && focused && visible && navigator?.lastItem == SettingsTreasuryScreen,
    )
    val phase = CrystalAtmosphereModel.phaseAt(moment)
    val weather = CrystalAtmosphereModel.weatherAt(moment, seed)
    val sceneColors = remember(phase, weather, palette, colors) {
        resolveCrystalLivingColors(
            phase,
            weather,
            palette,
            colors.isEInk,
            colors.isDark,
            colors.isAmoled,
            colors.eInkProfile == EInkProfile.COLOR,
        )
    }
    val assets = LocalContext.current.applicationContext.assets
    val view = LocalView.current
    var previewBitmap by remember(sceneColors, phase, weather, palette) {
        mutableStateOf(CrystalLivingCornerPainter.cachedPreviewSnapshotOrNull(sceneColors, phase, weather, palette))
    }
    LaunchedEffect(assets, sceneColors, phase, weather, palette) {
        val widthPx = view.width.takeIf { it > 0 } ?: 1080
        if (previewBitmap == null) {
            runCatching {
                withContext(Dispatchers.Default) {
                    CrystalLivingCornerPainter.loadOrRenderPreviewSnapshot(
                        assets = assets,
                        widthPx = widthPx,
                        colors = sceneColors,
                        phase = phase,
                        weather = weather,
                        palette = palette,
                    )
                }
            }.getOrNull()?.let { previewBitmap = it }
        }
        // Pre-compile the full-room hero painter AND pre-render its resting Bitmap off the UI thread
        // while viewing Treasury so tapping into Crystal's Corner opens with 0ms SVG freeze.
        runCatching {
            withContext(Dispatchers.Default) {
                CrystalLivingCornerPainter.loadCachedHero(assets, initialColors = sceneColors)
                CrystalLivingCornerPainter.prewarmHeroSnapshot(
                    assets = assets,
                    widthPx = widthPx,
                    colors = sceneColors,
                    phase = phase,
                    weather = weather,
                    palette = palette,
                    seedHash = seed.hashCode(),
                )
            }
        }
    }
    val previewAlpha by animateFloatAsState(
        targetValue = if (previewBitmap != null) 1f else 0f,
        animationSpec = if (colors.isEInk || !policy.systemAnimations) snap() else tween(220),
        label = "livingCornerPreviewAlpha",
    )
    val previewPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val previewDst = remember { RectF() }
    Box(
        modifier.background(Color(sceneColors.wall)).onGloballyPositioned {
            val bounds = it.boundsInWindow()
            visible = bounds.bottom > 0 && bounds.top < view.height && bounds.width > 0
        },
    ) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer { alpha = previewAlpha }.clearAndSetSemantics { },
        ) {
            val bmp = previewBitmap
            if (bmp != null && !bmp.isRecycled) {
                drawIntoCanvas {
                    previewDst.set(0f, 0f, size.width, size.height)
                    it.nativeCanvas.drawBitmap(bmp, null, previewDst, previewPaint)
                }
            }
        }
    }
}

@Composable
internal fun crystalLivingPhaseLabel(phase: CrystalDayPhase): String = stringResource(
    when (phase) {
        CrystalDayPhase.DAWN -> AYMR.strings.crystal_living_dawn
        CrystalDayPhase.DAY -> AYMR.strings.crystal_living_day
        CrystalDayPhase.SUNSET -> AYMR.strings.crystal_living_sunset
        CrystalDayPhase.NIGHT -> AYMR.strings.crystal_living_night
    },
)

@Composable
internal fun crystalLivingWeatherLabel(weather: CrystalWeather): String = stringResource(
    when (weather) {
        CrystalWeather.CLEAR -> AYMR.strings.crystal_living_clear
        CrystalWeather.RAIN -> AYMR.strings.crystal_living_rain
        CrystalWeather.STORM -> AYMR.strings.crystal_living_storm
    },
)
