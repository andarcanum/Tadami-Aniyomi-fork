package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnShadowIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnSpeed

internal data class NovelPageTurnPreset(
    val style: NovelPageTransitionStyle,
    val animationDurationMillis: Int,
    val curlAmount: Float,
    val shadowAlpha: Float,
    val backPageAlpha: Float,
)

internal fun resolveNovelPageTurnPreset(
    style: NovelPageTransitionStyle,
    speed: NovelPageTurnSpeed,
    intensity: NovelPageTurnIntensity,
    shadowIntensity: NovelPageTurnShadowIntensity,
): NovelPageTurnPreset {
    val base = when (style) {
        NovelPageTransitionStyle.CURL -> NovelPageTurnPreset(
            style = style,
            animationDurationMillis = 380,
            curlAmount = 0.72f,
            shadowAlpha = 0.38f,
            backPageAlpha = 0.32f,
        )
        else -> NovelPageTurnPreset(
            style = style,
            animationDurationMillis = 420,
            curlAmount = 0.48f,
            shadowAlpha = 0.24f,
            backPageAlpha = 0.18f,
        )
    }

    return base.copy(
        animationDurationMillis = base.animationDurationMillis + speed.durationDeltaMillis(),
        curlAmount = (base.curlAmount + intensity.curlDelta()).coerceIn(0.28f, 0.92f),
        shadowAlpha = (base.shadowAlpha + shadowIntensity.shadowDelta()).coerceIn(0.08f, 0.72f),
        backPageAlpha = (base.backPageAlpha + intensity.backPageDelta()).coerceIn(0.10f, 0.48f),
    )
}

private fun NovelPageTurnSpeed.durationDeltaMillis(): Int {
    return when (this) {
        NovelPageTurnSpeed.SLOWER -> 360
        NovelPageTurnSpeed.SLOW -> 260
        NovelPageTurnSpeed.NORMAL -> 0
        NovelPageTurnSpeed.FAST -> -120
        NovelPageTurnSpeed.FASTER -> -180
    }
}

private fun NovelPageTurnIntensity.curlDelta(): Float {
    return when (this) {
        NovelPageTurnIntensity.SOFTER -> -0.32f
        NovelPageTurnIntensity.LOW -> -0.16f
        NovelPageTurnIntensity.MEDIUM -> 0f
        NovelPageTurnIntensity.HIGH -> 0.08f
        NovelPageTurnIntensity.STRONGER -> 0.16f
    }
}

private fun NovelPageTurnIntensity.backPageDelta(): Float {
    return when (this) {
        NovelPageTurnIntensity.SOFTER -> -0.16f
        NovelPageTurnIntensity.LOW -> -0.08f
        NovelPageTurnIntensity.MEDIUM -> 0f
        NovelPageTurnIntensity.HIGH -> 0.04f
        NovelPageTurnIntensity.STRONGER -> 0.08f
    }
}

private fun NovelPageTurnShadowIntensity.shadowDelta(): Float {
    return when (this) {
        NovelPageTurnShadowIntensity.SOFTER -> -0.16f
        NovelPageTurnShadowIntensity.LOW -> -0.08f
        NovelPageTurnShadowIntensity.MEDIUM -> 0f
        NovelPageTurnShadowIntensity.HIGH -> 0.08f
        NovelPageTurnShadowIntensity.STRONGER -> 0.16f
    }
}
