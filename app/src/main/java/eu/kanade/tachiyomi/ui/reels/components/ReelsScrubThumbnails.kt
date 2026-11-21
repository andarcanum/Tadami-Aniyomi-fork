package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared scrubbing state hoisted out of the compact progress bars so the floating video
 * thumbnail preview and YouTube-style fine-scrubbing filmstrip render at full page overlay
 * height without being clipped or crushed by the 24dp/48dp bar bounds.
 */
data class ReelsScrubUiState(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val startProgress: Float = 0f,
    val isFineMode: Boolean = false,
    val fromScreenGesture: Boolean = false,
    val barWidthPx: Float = 1f,
)

/**
 * Computes bounded thumbnail dimensions for [android.view.TextureView.getBitmap]
 * preserving video aspect ratio while keeping GPU readback fast and RAM-light.
 */
internal fun computeScrubThumbnailSize(
    videoWidth: Int,
    videoHeight: Int,
    maxDim: Int = 256,
): Pair<Int, Int> {
    val safeMax = maxDim.coerceAtLeast(32)
    if (videoWidth <= 0 || videoHeight <= 0) {
        return safeMax to ((safeMax * 9) / 16).coerceAtLeast(32)
    }
    val aspect = videoWidth.toFloat() / videoHeight.toFloat()
    return if (aspect >= 1f) {
        val w = min(videoWidth, safeMax).coerceAtLeast(32)
        val h = (w / aspect).roundToInt().coerceIn(32, safeMax)
        w to h
    } else {
        val h = min(videoHeight, safeMax).coerceAtLeast(32)
        val w = (h * aspect).roundToInt().coerceIn(32, safeMax)
        w to h
    }
}

/**
 * Bounded per-reel LRU cache of real video frames captured from the live ExoPlayer TextureView
 * during scrubbing. Works uniformly for HLS, DASH, progressive MP4, signed CDN URLs, and
 * offline files with zero extra network requests.
 */
internal class ReelsScrubThumbnailCache<T : Any>(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val entries = LinkedHashMap<Int, CachedFrame<T>>(maxEntries, 0.75f, true)
    private var lastCaptured: CachedFrame<T>? = null

    data class CachedFrame<T>(
        val timeSec: Float,
        val frame: T,
    )

    fun bucketSizeSec(durationSec: Float): Float = when {
        durationSec <= 30f -> 1f
        durationSec <= 120f -> 2f
        else -> 4f
    }

    fun bucketIndex(fraction: Float, durationSec: Float): Int {
        val safeDuration = durationSec.coerceAtLeast(0f)
        if (safeDuration <= 0f) return (fraction.coerceIn(0f, 1f) * 20f).roundToInt()
        val timeSec = fraction.coerceIn(0f, 1f) * safeDuration
        return (timeSec / bucketSizeSec(safeDuration)).roundToInt()
    }

    fun put(fraction: Float, durationSec: Float, frame: T) {
        val clamped = fraction.coerceIn(0f, 1f)
        val timeSec = if (durationSec > 0f) clamped * durationSec else clamped
        val key = bucketIndex(clamped, durationSec)
        val entry = CachedFrame(timeSec = timeSec, frame = frame)
        entries[key] = entry
        lastCaptured = entry
        while (entries.size > maxEntries) {
            val eldestKey = entries.entries.firstOrNull()?.key ?: break
            entries.remove(eldestKey)
        }
    }

    fun getExact(fraction: Float, durationSec: Float): T? {
        return entries[bucketIndex(fraction, durationSec)]?.frame
    }

    /**
     * Returns the closest cached frame to [fraction] within [maxDeltaSec], falling back to the
     * most recently captured frame when [fallbackToLatest] is true so the preview card never
     * flashes blank black while a seek keyframe is decoding.
     */
    fun getBestFrame(
        fraction: Float,
        durationSec: Float,
        maxDeltaSec: Float = 10f,
        fallbackToLatest: Boolean = true,
    ): T? {
        if (entries.isEmpty()) return null
        val exact = getExact(fraction, durationSec)
        if (exact != null) return exact

        val clamped = fraction.coerceIn(0f, 1f)
        val targetSec = if (durationSec > 0f) clamped * durationSec else clamped
        var bestKey: Int? = null
        var best: CachedFrame<T>? = null
        var bestDist = Float.MAX_VALUE
        for ((key, candidate) in entries) {
            val dist = abs(candidate.timeSec - targetSec)
            if (dist < bestDist) {
                bestDist = dist
                best = candidate
                bestKey = key
            }
        }
        val effectiveLimit = if (durationSec > 0f) maxDeltaSec else 0.5f
        if (best != null && bestDist <= effectiveLimit) {
            if (bestKey != null) {
                entries[bestKey]
            }
            return best.frame
        }
        return if (fallbackToLatest) lastCaptured?.frame else null
    }

    /** Returns cached frames ordered by time inside `[centerSec - halfWindowSec, centerSec + halfWindowSec]`. */
    fun framesInWindow(
        centerSec: Float,
        halfWindowSec: Float,
    ): List<CachedFrame<T>> {
        if (entries.isEmpty() || halfWindowSec <= 0f) return emptyList()
        val minSec = centerSec - halfWindowSec
        val maxSec = centerSec + halfWindowSec
        return entries.values
            .filter { it.timeSec in minSec..maxSec }
            .sortedBy { it.timeSec }
    }

    fun size(): Int = entries.size

    fun clear() {
        entries.clear()
        lastCaptured = null
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 24
    }
}

/**
 * Pure state machine for YouTube-style Pull-Up Fine / Precision Scrubbing.
 *
 * - Normal mode: horizontal drag maps 1:1 across [barWidthPx] (`dx / barWidthPx`).
 * - Pull finger upward past [fineEnterLiftPx] (while [allowFineMode] is true): enters precision
 *   filmstrip mode where horizontal drag zooms into a narrow time window (default ±12s / 24s span)
 *   for frame-accurate seeking.
 * - Slide finger back down below [fineExitLiftPx]: exits fine mode cleanly with hysteresis.
 */
internal class ReelsFineScrubController(
    private val fineEnterLiftPx: Float,
    private val fineExitLiftPx: Float,
    private val fineWindowMaxSec: Float = DEFAULT_FINE_WINDOW_SEC,
) {
    var isActive: Boolean = false
        private set
    var progress: Float = 0f
        private set
    var startProgress: Float = 0f
        private set
    var isFineMode: Boolean = false
        private set
    var liftUpPx: Float = 0f
        private set

    private var barWidthPx: Float = 1f
    private var durationSec: Float = 0f

    fun start(
        initialProgress: Float,
        barWidthPx: Float,
        durationSec: Float,
        startProgress: Float = initialProgress,
        startInFineMode: Boolean = false,
    ) {
        this.barWidthPx = barWidthPx.coerceAtLeast(1f)
        this.durationSec = durationSec.coerceAtLeast(0f)
        this.progress = initialProgress.coerceIn(0f, 1f)
        this.startProgress = startProgress.coerceIn(0f, 1f)
        this.isFineMode = startInFineMode
        this.liftUpPx = if (startInFineMode) fineEnterLiftPx else 0f
        this.isActive = true
    }

    /**
     * Advances scrubbing by pointer delta `(dx, dy)` where negative `dy` means lifting upward.
     * Returns `true` when [isFineMode] toggled so callers can trigger subtle haptic feedback.
     */
    fun move(
        dx: Float,
        dy: Float,
        allowFineMode: Boolean,
    ): Boolean {
        if (!isActive) return false
        val previousFine = isFineMode

        if (allowFineMode) {
            liftUpPx = (liftUpPx - dy).coerceIn(0f, fineEnterLiftPx * 3f)
            if (!isFineMode && liftUpPx >= fineEnterLiftPx) {
                isFineMode = true
            } else if (isFineMode && liftUpPx <= fineExitLiftPx) {
                isFineMode = false
            }
        } else {
            isFineMode = false
            liftUpPx = 0f
        }

        val scale = if (isFineMode) fineSensitivityScale(durationSec, fineWindowMaxSec) else 1f
        val deltaFraction = (dx / barWidthPx) * scale
        progress = (progress + deltaFraction).coerceIn(0f, 1f)
        return isFineMode != previousFine
    }

    fun fineWindowHalfSec(): Float {
        val span = if (durationSec > 0f) min(durationSec, fineWindowMaxSec) else fineWindowMaxSec
        return (span / 2f).coerceAtLeast(3f)
    }

    fun end(): Float? {
        if (!isActive) return null
        isActive = false
        isFineMode = false
        liftUpPx = 0f
        return progress
    }

    fun cancel() {
        isActive = false
        isFineMode = false
        liftUpPx = 0f
    }

    companion object {
        const val DEFAULT_FINE_WINDOW_SEC = 24f

        internal fun fineSensitivityScale(
            durationSec: Float,
            fineWindowMaxSec: Float = DEFAULT_FINE_WINDOW_SEC,
        ): Float {
            if (durationSec <= 0f) return 0.25f
            return (fineWindowMaxSec / durationSec).coerceIn(0.08f, 0.45f)
        }
    }
}

/**
 * Full-screen overlay rendering the unclipped live-frame scrub thumbnail preview, rising
 * progress cursor pin, and YouTube-style landscape precision filmstrip above the seekbar.
 */
@Composable
internal fun ReelsScrubPreviewOverlay(
    scrubState: ReelsScrubUiState,
    durationSec: Float,
    previewFrame: ImageBitmap?,
    filmstripFrames: List<ReelsScrubThumbnailCache.CachedFrame<ImageBitmap>>,
    posterUrl: String?,
    isLandscape: Boolean,
    chromeVisible: Boolean,
    fineScrubEnabled: Boolean,
    bottomAnchorPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    val accent = auroraColors.accent
    val currentFraction = scrubState.progress.coerceIn(0f, 1f)
    val safeDuration = durationSec.coerceAtLeast(0f)
    val currentSecFloat = currentFraction * safeDuration
    val totalSec = safeDuration.roundToInt()
    val currentSec = currentSecFloat.roundToInt()
    val deltaSec = ((currentFraction - scrubState.startProgress) * safeDuration).roundToInt()

    AnimatedVisibility(
        visible = scrubState.isActive,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLandscape) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val density = LocalDensity.current
            val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val horizontalMarginPx = with(density) { 16.dp.toPx() }
            val usableTrackWidthPx = (containerWidthPx - horizontalMarginPx * 2f).coerceAtLeast(1f)
            val thumbCenterPx = horizontalMarginPx + currentFraction * usableTrackWidthPx

            val showStandaloneTrack = scrubState.fromScreenGesture && !chromeVisible
            val effectiveBottomOffset = if (showStandaloneTrack) {
                if (isLandscape) 24.dp else 44.dp
            } else {
                bottomAnchorPadding
            }

            if (scrubState.isFineMode && isLandscape) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = effectiveBottomOffset + if (showStandaloneTrack) 22.dp else 8.dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ScrubThumbnailCard(
                        previewFrame = previewFrame,
                        posterUrl = posterUrl,
                        timeText = formatFineSeconds(currentSecFloat),
                        totalText = formatScrubSeconds(totalSec),
                        deltaSec = deltaSec,
                        badgeText = stringResource(MR.strings.reels_scrub_fine_mode),
                        cardWidth = 196.dp,
                        thumbHeight = 108.dp,
                        accent = accent,
                        isEInk = auroraColors.isEInk,
                        eInkCardBg = auroraColors.cardBackground,
                    )

                    ReelsFineFilmstripRuler(
                        centerSec = currentSecFloat,
                        durationSec = safeDuration,
                        halfWindowSec = (
                            min(
                                safeDuration.coerceAtLeast(12f),
                                ReelsFineScrubController.DEFAULT_FINE_WINDOW_SEC,
                            ) / 2f
                            ).coerceAtLeast(3f),
                        frames = filmstripFrames,
                        accent = accent,
                    )
                }
            } else {
                val cardWidthDp = if (isLandscape) 164.dp else 144.dp
                val thumbHeightDp = if (isLandscape) 92.dp else 80.dp
                val cardWidthPx = with(density) { cardWidthDp.toPx() }
                val halfCardPx = cardWidthPx / 2f
                val clampedCardCenterPx = thumbCenterPx.coerceIn(
                    horizontalMarginPx + halfCardPx,
                    (containerWidthPx - horizontalMarginPx - halfCardPx).coerceAtLeast(horizontalMarginPx + halfCardPx),
                )
                val pinHeightDp = if (scrubState.fromScreenGesture) 22.dp else 14.dp

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = effectiveBottomOffset + if (showStandaloneTrack) 16.dp else 2.dp)
                        .offset {
                            IntOffset(
                                x = (thumbCenterPx - with(density) { 1.5.dp.toPx() }).roundToInt(),
                                y = 0,
                            )
                        }
                        .width(3.dp)
                        .height(pinHeightDp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(accent, Color.White))),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            bottom = effectiveBottomOffset + pinHeightDp + if (showStandaloneTrack) 18.dp else 4.dp,
                        )
                        .offset {
                            IntOffset(
                                x = (clampedCardCenterPx - halfCardPx).roundToInt(),
                                y = 0,
                            )
                        },
                ) {
                    ScrubThumbnailCard(
                        previewFrame = previewFrame,
                        posterUrl = posterUrl,
                        timeText = formatScrubSeconds(currentSec),
                        totalText = formatScrubSeconds(totalSec),
                        deltaSec = deltaSec,
                        badgeText = if (isLandscape && fineScrubEnabled) {
                            stringResource(MR.strings.reels_scrub_pull_up_hint)
                        } else {
                            null
                        },
                        cardWidth = cardWidthDp,
                        thumbHeight = thumbHeightDp,
                        accent = accent,
                        isEInk = auroraColors.isEInk,
                        eInkCardBg = auroraColors.cardBackground,
                    )
                }
            }

            AnimatedVisibility(
                visible = showStandaloneTrack,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = currentFraction)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(fraction = currentFraction),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color.White, CircleShape)
                                .border(2.5.dp, accent, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrubThumbnailCard(
    previewFrame: ImageBitmap?,
    posterUrl: String?,
    timeText: String,
    totalText: String,
    deltaSec: Int,
    badgeText: String?,
    cardWidth: Dp,
    thumbHeight: Dp,
    accent: Color,
    isEInk: Boolean,
    eInkCardBg: Color,
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isEInk) eInkCardBg else Color(0xFF0E131E).copy(alpha = 0.94f))
            .border(1.2.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (previewFrame != null) {
                Image(
                    bitmap = previewFrame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(posterUrl)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (deltaSec != 0) {
                val deltaLabel = if (deltaSec > 0) "+${deltaSec}s" else "${deltaSec}s"
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = deltaLabel,
                        color = accent,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = timeText,
                color = if (isEInk) Color.Black else Color.White,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(
                text = " / $totalText",
                color = (if (isEInk) Color.Black else Color.White).copy(alpha = 0.62f),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontFeatureSettings = "tnum",
                ),
            )
        }

        if (!badgeText.isNullOrBlank()) {
            Text(
                text = badgeText,
                color = accent,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReelsFineFilmstripRuler(
    centerSec: Float,
    durationSec: Float,
    halfWindowSec: Float,
    frames: List<ReelsScrubThumbnailCache.CachedFrame<ImageBitmap>>,
    accent: Color,
) {
    val startSec = centerSec - halfWindowSec
    val endSec = centerSec + halfWindowSec
    val totalWindowSec = (endSec - startSec).coerceAtLeast(1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E131E).copy(alpha = 0.90f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val tileWidthDp = 64.dp
        val tileWidthPx = with(density) { tileWidthDp.toPx() }

        frames.forEach { entry ->
            val rel = ((entry.timeSec - startSec) / totalWindowSec).coerceIn(-0.15f, 1.15f)
            val xPx = (rel * widthPx - tileWidthPx / 2f).roundToInt()
            Image(
                bitmap = entry.frame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.52f,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(x = xPx, y = 0) }
                    .width(tileWidthDp)
                    .fillMaxHeight()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val firstTickSec = max(0, ceil(startSec).toInt())
            val lastTickSec = min(
                if (durationSec > 0f) floor(durationSec).toInt() else Int.MAX_VALUE,
                floor(endSec).toInt(),
            )
            for (sec in firstTickSec..lastTickSec) {
                val frac = (sec - startSec) / totalWindowSec
                if (frac < 0f || frac > 1f) continue
                val x = frac * w
                val isMajor = sec % 5 == 0
                val tickHeight = if (isMajor) h * 0.42f else h * 0.22f
                drawLine(
                    color = if (isMajor) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.38f),
                    start = Offset(x, h - tickHeight),
                    end = Offset(x, h - 4f),
                    strokeWidth = if (isMajor) 2.5f else 1.5f,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(3.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, accent, CircleShape),
        )

        val leftLabelSec = startSec.coerceAtLeast(0f).roundToInt()
        val rightLabelSec = if (durationSec > 0f) endSec.coerceAtMost(durationSec).roundToInt() else endSec.roundToInt()
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatScrubSeconds(leftLabelSec),
                color = Color.White.copy(alpha = 0.72f),
                style = TextStyle(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatScrubSeconds(rightLabelSec),
                color = Color.White.copy(alpha = 0.72f),
                style = TextStyle(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}

private fun formatScrubSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%02d:%02d".format(Locale.getDefault(), minutes, seconds)
}

private fun formatFineSeconds(totalSeconds: Float): String {
    val safe = totalSeconds.coerceAtLeast(0f)
    val whole = safe.toInt()
    val tenths = ((safe - whole) * 10f).toInt().coerceIn(0, 9)
    val minutes = whole / 60
    val seconds = whole % 60
    return "%02d:%02d.%d".format(Locale.getDefault(), minutes, seconds, tenths)
}
