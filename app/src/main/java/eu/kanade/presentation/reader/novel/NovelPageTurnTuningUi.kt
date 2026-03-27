package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnShadowIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnSpeed
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

internal fun shouldShowPageTurnTuningControls(
    pageReaderEnabled: Boolean,
    style: NovelPageTransitionStyle,
): Boolean {
    return pageReaderEnabled && resolvePageTransitionEngine(style) == NovelPageTransitionEngine.PAGE_TURN_RENDERER
}

@Composable
internal fun novelPageTurnSpeedEntries(): ImmutableMap<NovelPageTurnSpeed, String> {
    return persistentMapOf(
        NovelPageTurnSpeed.SLOW to stringResource(AYMR.strings.novel_reader_page_turn_speed_slow),
        NovelPageTurnSpeed.NORMAL to stringResource(AYMR.strings.novel_reader_page_turn_speed_normal),
        NovelPageTurnSpeed.FAST to stringResource(AYMR.strings.novel_reader_page_turn_speed_fast),
    )
}

@Composable
internal fun novelPageTurnIntensityEntries(): ImmutableMap<NovelPageTurnIntensity, String> {
    return persistentMapOf(
        NovelPageTurnIntensity.LOW to stringResource(AYMR.strings.novel_reader_page_turn_intensity_low),
        NovelPageTurnIntensity.MEDIUM to stringResource(AYMR.strings.novel_reader_page_turn_intensity_medium),
        NovelPageTurnIntensity.HIGH to stringResource(AYMR.strings.novel_reader_page_turn_intensity_high),
    )
}

@Composable
internal fun novelPageTurnShadowIntensityEntries(): ImmutableMap<NovelPageTurnShadowIntensity, String> {
    return persistentMapOf(
        NovelPageTurnShadowIntensity.LOW to
            stringResource(AYMR.strings.novel_reader_page_turn_shadow_intensity_low),
        NovelPageTurnShadowIntensity.MEDIUM to
            stringResource(AYMR.strings.novel_reader_page_turn_shadow_intensity_medium),
        NovelPageTurnShadowIntensity.HIGH to
            stringResource(AYMR.strings.novel_reader_page_turn_shadow_intensity_high),
    )
}

@Composable
internal fun novelPageTurnTuningSummary(
    speed: NovelPageTurnSpeed,
    intensity: NovelPageTurnIntensity,
    shadowIntensity: NovelPageTurnShadowIntensity,
    speedEntries: Map<NovelPageTurnSpeed, String>,
    intensityEntries: Map<NovelPageTurnIntensity, String>,
    shadowEntries: Map<NovelPageTurnShadowIntensity, String>,
): String {
    return stringResource(
        AYMR.strings.novel_reader_page_turn_tuning_summary_format,
        speedEntries[speed].orEmpty(),
        intensityEntries[intensity].orEmpty(),
        shadowEntries[shadowIntensity].orEmpty(),
    )
}
