package eu.kanade.presentation.easteregg.crystal

import androidx.compose.animation.core.CubicBezierEasing
import com.tadami.aurora.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Slow, cinematic "Lunar Veil Breathing" entrance choreography for [CrystalMemorialScreen].
 *
 * A single linear 0..1 driver over [DURATION_MS] feeds smooth, overlapping phases so Home dissolves
 * calmly into twilight, a delicate golden breath-ring opens around the portrait, the portrait exhales
 * gently from 1.055 -> 1.0, and the epitaph + golden paw rise softly at the end.
 */
internal object CrystalLunarVeil {
    const val DURATION_MS = 2600
    const val NAV_ENTER_MS = 1050
    const val NAV_EXIT_MS = 950
    const val NAV_EXIT_SCALE = 0.965f

    val CINEMATIC_EASING = CubicBezierEasing(0.42f, 0.0f, 0.22f, 1.0f)

    fun atmosphereAlpha(progress: Float): Float = CrystalTimeline.smooth(progress / 0.44f)

    fun veilLift(progress: Float): Float = CrystalTimeline.smooth(progress / 0.56f)

    fun ringProgress(progress: Float): Float = ((progress - 0.06f) / 0.56f).coerceIn(0f, 1f)

    fun echoRingProgress(progress: Float): Float = ((progress - 0.16f) / 0.54f).coerceIn(0f, 1f)

    fun portraitAlpha(progress: Float): Float = CrystalTimeline.smooth((progress - 0.14f) / 0.64f)

    fun portraitScale(progress: Float): Float = 1.055f - 0.055f * portraitAlpha(progress)

    fun uiAlpha(progress: Float): Float = CrystalTimeline.smooth((progress - 0.56f) / 0.44f)

    fun uiOffsetDp(progress: Float): Float = (1f - uiAlpha(progress)) * 12f
}

/**
 * Cat-Face Lunar Iris diaphragm reveal for transitioning from the 1D ceremony into Crystal's Living Corner.
 *
 * Opens a glowing circular aperture directly from Crystal's starlit nose ([CrystalSceneGeometry.constellationIrisCenter])
 * with rotating gold & rose 4-point sparkles along the rim while the room zooms gently forward (0.93 -> 1.0).
 */
internal object CrystalLunarIris {
    const val DURATION_MS = 1650
    const val EXIT_FADE_MS = 1450
    const val EXIT_DELAY_MS = 200
    const val EXIT_SCALE = 1.06f
    const val SPARKLE_COUNT = 16

    val EASING = CubicBezierEasing(0.36f, 0.0f, 0.20f, 1.0f)

    fun roomScale(progress: Float): Float = 0.93f + 0.07f * CrystalTimeline.smooth(progress)

    fun rimAlpha(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0.005f || p >= 0.985f) return 0f
        return sin(p * PI.toFloat()).coerceIn(0f, 1f)
    }

    fun noseBloomAlpha(progress: Float): Float {
        val local = (progress / 0.34f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) return 0f
        return sin(local * PI.toFloat()).coerceIn(0f, 1f)
    }

    fun maxCornerRadius(cx: Float, cy: Float, width: Float, height: Float): Float {
        val dx = max(cx, width - cx)
        val dy = max(cy, height - cy)
        return hypot(dx, dy)
    }
}

/**
 * One-shot coordinator arming the Lunar Iris transition specifically when the user enters
 * [CrystalCollectionScreen] from [CrystalMemorialScreen]'s 1D ceremony ("Заглянуть к Crystal").
 * Normal back-presses and direct Treasury entries remain completely unaffected.
 */
internal object CrystalPortalTransition {
    private const val TTL_MS = 2_500L

    @Volatile
    private var navigatorArmedAtMs: Long = 0L

    @Volatile
    private var irisArmedAtMs: Long = 0L

    fun arm(nowMs: Long = System.currentTimeMillis()) {
        navigatorArmedAtMs = nowMs
        irisArmedAtMs = nowMs
    }

    fun consumeNavigatorPortal(nowMs: Long = System.currentTimeMillis()): Boolean {
        val armedAt = navigatorArmedAtMs
        navigatorArmedAtMs = 0L
        return armedAt > 0L && (nowMs - armedAt) in 0L..TTL_MS
    }

    fun consumeIrisReveal(nowMs: Long = System.currentTimeMillis()): Boolean {
        val armedAt = irisArmedAtMs
        irisArmedAtMs = 0L
        return armedAt > 0L && (nowMs - armedAt) in 0L..TTL_MS
    }

    fun clear() {
        navigatorArmedAtMs = 0L
        irisArmedAtMs = 0L
    }
}

internal data class CrystalDebugReleaseRequest(
    val nonce: Int = 0,
    val portrait: CrystalPortrait = CrystalPortrait.Warm,
    val front: CrystalDissolveStyle = CrystalDissolveStyle.Layers,
)

/**
 * Debug-only trigger to rehearse the full release upgrade sequence from the real Home screen:
 * Home -> What's New sheet -> 550ms settle -> Lunar Veil Memorial -> 1D Ceremony -> Cat-Face Iris Room.
 */
internal object CrystalDebugReleaseSimulation {
    private val _request = MutableStateFlow(CrystalDebugReleaseRequest())
    val request: StateFlow<CrystalDebugReleaseRequest> = _request.asStateFlow()

    fun trigger(
        portrait: CrystalPortrait = CrystalPortrait.Warm,
        front: CrystalDissolveStyle = CrystalDissolveStyle.Layers,
    ) {
        if (BuildConfig.DEBUG && BuildConfig.BUILD_TYPE == "debug") {
            _request.value = CrystalDebugReleaseRequest(
                nonce = _request.value.nonce + 1,
                portrait = portrait,
                front = front,
            )
        }
    }
}
