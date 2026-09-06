package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt

/**
 * Theme-derived TTS highlight palette — the approved "V4 auto-contrast" variant of
 * `docs/prototypes/prototype_tts_highlight.html`.
 *
 * The old colors were hardcoded pastels (`rgba(255,224,130,.30)` in the chapter WebView script,
 * `rgba(128,128,128,.28)` in the book engine CSS, `primary @ 24%` natively) that washed out on the
 * reader's paper/parchment/dark backgrounds. The palette instead starts from the theme accent and
 * adapts it to the luminance of the reader background, so the spoken paragraph, its accent bar and
 * the spoken-word chip stay visible on every background the user can pick:
 *  - light background + light accent → the accent is darkened before use;
 *  - dark background + dark accent → the accent is lightened before use;
 *  - the word chip always gets a contrasting text color by the accent's own luminance.
 */
internal data class NovelTtsHighlightPalette(
    /** Spoken-paragraph backdrop (accent @ 26%). */
    val paragraphBackground: Color,
    /** Solid accent for the leading bar (WebView renderers). */
    val accentBar: Color,
    /** Solid accent behind the spoken word. */
    val wordBackground: Color,
    /** Contrasting text color for the spoken-word chip. */
    val wordTextColor: Color,
)

/**
 * CSS colors the WebView renderers inject.
 *
 * Public border DTO: the navigation adapter lives in the reader UI package and passes these
 * strings to the internal script builders, so the holder itself cannot be internal.
 */
data class NovelTtsHighlightCss(
    val paragraphBackgroundRgba: String,
    val barInsetRgba: String,
) {
    companion object {
        /** Neutral fallback for callers without a themed palette (tests, previews). */
        val DEFAULT = NovelTtsHighlightCss(
            paragraphBackgroundRgba = "rgba(128, 128, 128, 0.28)",
            barInsetRgba = "rgba(128, 128, 128, 1.0)",
        )
    }
}

internal fun resolveNovelTtsHighlightPalette(
    accent: Color,
    backgroundColor: Color,
): NovelTtsHighlightPalette {
    val backgroundIsLight = backgroundColor.luminance() > LIGHT_BACKGROUND_LUMINANCE
    val accentIsDark = accent.luminance() < DARK_ACCENT_LUMINANCE
    val solid = when {
        backgroundIsLight && !accentIsDark -> lerp(accent, Color.Black, LIGHT_BG_ACCENT_DARKEN_FRACTION)
        !backgroundIsLight && accentIsDark -> lerp(accent, Color.White, DARK_BG_ACCENT_LIGHTEN_FRACTION)
        else -> accent
    }
    return NovelTtsHighlightPalette(
        paragraphBackground = solid.copy(alpha = PARAGRAPH_BACKGROUND_ALPHA),
        accentBar = solid,
        wordBackground = solid,
        wordTextColor = resolveTtsWordChipTextColor(solid),
    )
}

/** Chip text stays readable on the solid accent: dark ink on light accents, white on dark ones. */
internal fun resolveTtsWordChipTextColor(wordBackground: Color): Color =
    if (wordBackground.luminance() > CHIP_TEXT_LUMINANCE) CHIP_TEXT_ON_LIGHT else CHIP_TEXT_ON_DARK

internal fun NovelTtsHighlightPalette.toChapterWebViewCss(): NovelTtsHighlightCss =
    NovelTtsHighlightCss(
        paragraphBackgroundRgba = paragraphBackground.toCssRgba(),
        barInsetRgba = accentBar.toCssRgba(),
    )

/**
 * The book engine document paints `[data-an-tts-highlight]` from its own flow stylesheet with a
 * hardcoded gray, and that stylesheet is emitted *after* the reader CSS. The override therefore
 * rides the reader CSS with `!important`, which beats the non-important flow rule regardless of
 * order — no new parameter through the engine/document chain.
 */
internal fun NovelTtsHighlightPalette.bookEngineOverrideCss(): String = """
    #an-book-content [data-an-tts-highlight] {
      background-color: ${paragraphBackground.toCssRgba()} !important;
      border-radius: 6px !important;
      box-shadow: inset 3px 0 0 ${accentBar.toCssRgba()} !important;
    }
""".trimIndent()

/**
 * `rgba(r, g, b, a)` with the alpha rounded to 2 decimals: Compose quantizes color components to
 * 10-bit fixed point (0.26 reads back as 0.259), and CSS does not need finer alpha precision.
 */
internal fun Color.toCssRgba(): String {
    val r = (red * 255f).roundToInt()
    val g = (green * 255f).roundToInt()
    val b = (blue * 255f).roundToInt()
    val a = (alpha * 100f).roundToInt() / 100f
    return "rgba($r, $g, $b, $a)"
}

/** Above this background luminance the reader surface counts as light. */
private const val LIGHT_BACKGROUND_LUMINANCE = 0.45f

/** Below this accent luminance the accent counts as dark. */
private const val DARK_ACCENT_LUMINANCE = 0.30f

/** How much a light accent is darkened on a light background (prototype: mix 25% black). */
private const val LIGHT_BG_ACCENT_DARKEN_FRACTION = 0.25f

/** How much a dark accent is lightened on a dark background (emulates the dark-scheme accent). */
private const val DARK_BG_ACCENT_LIGHTEN_FRACTION = 0.35f

/** Spoken-paragraph backdrop alpha (prototype V4: accent @ 26%). */
private const val PARAGRAPH_BACKGROUND_ALPHA = 0.26f

/** Chip text flips at this accent luminance (prototype: 0.45). */
private const val CHIP_TEXT_LUMINANCE = 0.45f

private val CHIP_TEXT_ON_LIGHT = Color(0xFF141414)
private val CHIP_TEXT_ON_DARK = Color.White
