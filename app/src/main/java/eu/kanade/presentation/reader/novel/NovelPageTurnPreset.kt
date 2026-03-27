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
        shadowAlpha = (base.shadowAlpha + shadowIntensity.shadowDelta()).coerceIn(0.12f, 0.72f),
        backPageAlpha = (base.backPageAlpha + intensity.backPageDelta()).coerceIn(0.10f, 0.48f),
    )
}

private fun NovelPageTurnSpeed.durationDeltaMillis(): Int {
    return when (this) {
        NovelPageTurnSpeed.SLOW -> 120
        NovelPageTurnSpeed.NORMAL -> 0
        NovelPageTurnSpeed.FAST -> -100
    }
}

private fun NovelPageTurnIntensity.curlDelta(): Float {
    return when (this) {
        NovelPageTurnIntensity.LOW -> -0.10f
        NovelPageTurnIntensity.MEDIUM -> 0f
        NovelPageTurnIntensity.HIGH -> 0.12f
    }
}

private fun NovelPageTurnIntensity.backPageDelta(): Float {
    return when (this) {
        NovelPageTurnIntensity.LOW -> -0.04f
        NovelPageTurnIntensity.MEDIUM -> 0f
        NovelPageTurnIntensity.HIGH -> 0.06f
    }
}

private fun NovelPageTurnShadowIntensity.shadowDelta(): Float {
    return when (this) {
        NovelPageTurnShadowIntensity.LOW -> -0.10f
        NovelPageTurnShadowIntensity.MEDIUM -> 0f
        NovelPageTurnShadowIntensity.HIGH -> 0.12f
    }
}
