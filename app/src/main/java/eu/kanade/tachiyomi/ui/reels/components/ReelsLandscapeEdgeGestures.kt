package eu.kanade.tachiyomi.ui.reels.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/**
 * Side zones for landscape-only vertical edge gestures in Reels.
 * Left edge (0%..16%) adjusts window brightness; right edge (84%..100%) adjusts media volume.
 * Center (16%..84%) stays untouched so VerticalPager swipes work effortlessly.
 */
enum class ReelsLandscapeEdgeZone {
    Brightness,
    Volume,
}

data class ReelsEdgeGestureHudState(
    val zone: ReelsLandscapeEdgeZone,
    val level: Float,
    val isDragging: Boolean = false,
)

internal const val LANDSCAPE_EDGE_ZONE_FRACTION = 0.16f
internal const val LANDSCAPE_MIN_BRIGHTNESS = 0.01f
internal const val LANDSCAPE_EDGE_HUD_HOLD_MS = 550L

/**
 * Classifies a landscape touch start into [ReelsLandscapeEdgeZone.Brightness],
 * [ReelsLandscapeEdgeZone.Volume], or `null` (center lane / top-bottom chrome exclusion / disabled).
 */
internal fun resolveLandscapeEdgeZone(
    xPx: Float,
    yPx: Float,
    widthPx: Float,
    heightPx: Float,
    topExclusionPx: Float,
    bottomExclusionPx: Float,
    brightnessEnabled: Boolean,
    volumeEnabled: Boolean,
): ReelsLandscapeEdgeZone? {
    if (widthPx <= 0f || heightPx <= 0f) return null
    if (yPx < topExclusionPx || yPx > heightPx - bottomExclusionPx) return null
    val normX = (xPx / widthPx).coerceIn(0f, 1f)
    return when {
        normX <= LANDSCAPE_EDGE_ZONE_FRACTION ->
            if (brightnessEnabled) ReelsLandscapeEdgeZone.Brightness else null
        normX >= 1f - LANDSCAPE_EDGE_ZONE_FRACTION ->
            if (volumeEnabled) ReelsLandscapeEdgeZone.Volume else null
        else -> null
    }
}

/**
 * Maps a cumulative vertical finger displacement ([totalDyPx], negative = upward) to a clamped
 * normalized level in `[minLevel, maxLevel]`.
 */
internal fun computeUpdatedEdgeGestureLevel(
    startLevel: Float,
    totalDyPx: Float,
    gestureSpanPx: Float,
    minLevel: Float = 0f,
    maxLevel: Float = 1f,
): Float {
    val clampedStart = startLevel.coerceIn(minLevel, maxLevel)
    if (gestureSpanPx <= 0f) return clampedStart
    val delta = -totalDyPx / gestureSpanPx
    return (clampedStart + delta).coerceIn(minLevel, maxLevel)
}

/**
 * Converts a normalized volume `[0f, 1f]` into an Android [AudioManager] stream index `[0, maxVolume]`.
 */
internal fun mapNormalizedVolumeToStreamIndex(level: Float, maxVolume: Int): Int {
    if (maxVolume <= 0) return 0
    return (level.coerceIn(0f, 1f) * maxVolume).roundToInt().coerceIn(0, maxVolume)
}

/**
 * Formats a `[0f, 1f]` level as a compact percentage string (e.g. `"65%"`).
 */
internal fun formatGesturePercent(level: Float): String {
    val pct = (level.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
    return "$pct%"
}

/**
 * Reads the current effective screen brightness in `[LANDSCAPE_MIN_BRIGHTNESS, 1f]`.
 * Falls back to `Settings.System.SCREEN_BRIGHTNESS` when the window has no per-window override.
 */
internal fun readCurrentScreenBrightness(activity: Activity?): Float {
    val attrBrightness =
        activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    if (attrBrightness in 0f..1f) {
        return attrBrightness.coerceIn(LANDSCAPE_MIN_BRIGHTNESS, 1f)
    }
    val sysVal = runCatching {
        activity?.contentResolver?.let { resolver ->
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        }
    }.getOrNull() ?: 128
    return (sysVal.toFloat() / 255f).coerceIn(LANDSCAPE_MIN_BRIGHTNESS, 1f)
}

/**
 * Applies per-window screen brightness to [activity] without mutating global system settings.
 */
internal fun applyScreenBrightness(activity: Activity?, level: Float) {
    val window = activity?.window ?: return
    val clamped = level.coerceIn(LANDSCAPE_MIN_BRIGHTNESS, 1f)
    val attrs = window.attributes
    if (attrs.screenBrightness != clamped) {
        attrs.screenBrightness = clamped
        window.attributes = attrs
    }
}

/**
 * Reads current normalized media volume `(level, maxVolume)` from [AudioManager.STREAM_MUSIC].
 */
internal fun readCurrentMediaVolume(context: Context): Pair<Float, Int> {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0.5f to 15
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume)
    return (currentVolume.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f) to maxVolume
}

/**
 * Updates [AudioManager.STREAM_MUSIC] silently (`flags = 0`) when the rounded stream step changes.
 * Returns the applied stream index so callers can trigger subtle step haptics.
 */
internal fun applyMediaVolume(
    context: Context,
    level: Float,
    maxVolume: Int,
    lastAppliedIndex: Int,
): Int {
    val targetIndex = mapNormalizedVolumeToStreamIndex(level, maxVolume)
    if (targetIndex == lastAppliedIndex) return lastAppliedIndex
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return targetIndex
    runCatching {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
    }
    return targetIndex
}

/**
 * Returns the opposite-side HUD alignment for a landscape edge gesture so the dragging thumb
 * never covers the indicator: left-edge brightness renders on the right, right-edge volume
 * renders on the left.
 */
internal fun resolveEdgeGestureHudAlignment(zone: ReelsLandscapeEdgeZone): Alignment = when (zone) {
    ReelsLandscapeEdgeZone.Brightness -> Alignment.CenterEnd
    ReelsLandscapeEdgeZone.Volume -> Alignment.CenterStart
}

/**
 * Slim iOS-style vertical bar HUD shown during landscape edge brightness/volume adjustments.
 * Mirrored to the side opposite the active finger (`end = 28.dp` for left-edge brightness,
 * `start = 28.dp` for right-edge volume) with no outer dark card wrapper; e-ink mode adds a
 * solid monochrome backplate for contrast.
 */
@Composable
internal fun BoxScope.ReelsEdgeGestureHud(
    state: ReelsEdgeGestureHudState?,
    modifier: Modifier = Modifier,
) {
    // Remember the last active state so the exit fade-out animates at the exact side and
    // percentage where the drag ended instead of jumping to Volume 0%.
    var lastState by remember { mutableStateOf(state) }
    if (state != null) {
        lastState = state
    }
    val displayed = state ?: lastState
    val visible = state != null
    val activeZone = displayed?.zone ?: ReelsLandscapeEdgeZone.Volume
    val level = (displayed?.level ?: 0f).coerceIn(0f, 1f)
    val alignment = resolveEdgeGestureHudAlignment(activeZone)
    val horizontalPad = when (activeZone) {
        ReelsLandscapeEdgeZone.Brightness -> Modifier.padding(end = 28.dp)
        ReelsLandscapeEdgeZone.Volume -> Modifier.padding(start = 28.dp)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
        modifier = modifier
            .align(alignment)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .then(horizontalPad),
    ) {
        val auroraColors = AuroraTheme.colors
        val isEInk = auroraColors.isEInk
        val onSurface = MaterialTheme.colorScheme.onSurface
        val percentText = formatGesturePercent(level)
        val a11yLabel = when (activeZone) {
            ReelsLandscapeEdgeZone.Brightness ->
                "${stringResource(MR.strings.reels_gesture_brightness_label)} $percentText"
            ReelsLandscapeEdgeZone.Volume ->
                "${stringResource(MR.strings.reels_gesture_volume_label)} $percentText"
        }
        val icon = when (activeZone) {
            ReelsLandscapeEdgeZone.Brightness -> Icons.Filled.Brightness6
            ReelsLandscapeEdgeZone.Volume -> when {
                level <= 0.005f -> Icons.AutoMirrored.Filled.VolumeOff
                level < 0.45f -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }
        }
        val accent = auroraColors.accent
        val fillBrush = when {
            isEInk -> Brush.verticalGradient(colors = listOf(onSurface, onSurface))
            activeZone == ReelsLandscapeEdgeZone.Brightness -> Brush.verticalGradient(
                colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B)),
            )
            else -> Brush.verticalGradient(
                colors = listOf(accent, accent.copy(alpha = 0.72f)),
            )
        }
        val iconTint = when {
            isEInk -> onSurface
            activeZone == ReelsLandscapeEdgeZone.Brightness -> Color(0xFFFDE68A)
            level <= 0.005f -> Color(0xFFFDA4AF)
            else -> accent
        }
        val textShadow = if (isEInk) {
            null
        } else {
            Shadow(color = Color.Black.copy(alpha = 0.85f), offset = Offset(0f, 1f), blurRadius = 6f)
        }

        Column(
            modifier = Modifier
                .then(
                    if (isEInk) {
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(auroraColors.cardBackground)
                            .border(1.dp, onSurface.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    } else {
                        Modifier
                    },
                )
                .semantics { contentDescription = a11yLabel },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReelsChromeIcon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                size = 21.dp,
                eInk = isEInk,
            )

            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(128.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEInk) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color(0xFF0E1018).copy(alpha = 0.56f)
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (isEInk) {
                            onSurface.copy(alpha = 0.45f)
                        } else {
                            Color.White.copy(alpha = 0.18f)
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (level > 0.005f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(level.coerceIn(0.03f, 1f))
                            .clip(CircleShape)
                            .background(fillBrush),
                    )
                }
            }

            Text(
                text = percentText,
                color = if (isEInk) onSurface else Color.White,
                style = TextStyle(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                    shadow = textShadow,
                ),
                maxLines = 1,
            )
        }
    }
}
