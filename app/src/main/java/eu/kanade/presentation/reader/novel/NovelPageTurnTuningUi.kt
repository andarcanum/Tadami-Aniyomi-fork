package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnActivationZone
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnShadowIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnSpeed
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

private data class NovelPageTurnSliderOption<T>(
    val value: T,
    val labelRes: StringResource,
)

private val novelPageTurnSpeedSliderOptions = listOf(
    NovelPageTurnSliderOption(
        value = NovelPageTurnSpeed.SLOWER,
        labelRes = AYMR.strings.novel_reader_page_turn_speed_slower,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnSpeed.SLOW,
        labelRes = AYMR.strings.novel_reader_page_turn_speed_slow,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnSpeed.NORMAL,
        labelRes = AYMR.strings.novel_reader_page_turn_speed_normal,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnSpeed.FAST,
        labelRes = AYMR.strings.novel_reader_page_turn_speed_fast,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnSpeed.FASTER,
        labelRes = AYMR.strings.novel_reader_page_turn_speed_faster,
    ),
)

private val novelPageTurnIntensitySliderOptions = listOf(
    NovelPageTurnSliderOption(
        value = NovelPageTurnIntensity.SOFTER,
        labelRes = AYMR.strings.novel_reader_page_turn_intensity_softer,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnIntensity.LOW,
        labelRes = AYMR.strings.novel_reader_page_turn_intensity_low,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnIntensity.MEDIUM,
        labelRes = AYMR.strings.novel_reader_page_turn_intensity_medium,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnIntensity.HIGH,
        labelRes = AYMR.strings.novel_reader_page_turn_intensity_high,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnIntensity.STRONGER,
        labelRes = AYMR.strings.novel_reader_page_turn_intensity_stronger,
    ),
)

private val novelPageTurnShadowIntensitySliderOptions = listOf(
    NovelPageTurnSliderOption(
        value = NovelPageTurnShadowIntensity.SOFTER,
        labelRes = AYMR.strings.novel_reader_page_turn_shadow_intensity_softer,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnShadowIntensity.LOW,
        labelRes = AYMR.strings.novel_reader_page_turn_shadow_intensity_low,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnShadowIntensity.MEDIUM,
        labelRes = AYMR.strings.novel_reader_page_turn_shadow_intensity_medium,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnShadowIntensity.HIGH,
        labelRes = AYMR.strings.novel_reader_page_turn_shadow_intensity_high,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnShadowIntensity.STRONGER,
        labelRes = AYMR.strings.novel_reader_page_turn_shadow_intensity_stronger,
    ),
)

private val novelPageTurnActivationZoneSliderOptions = listOf(
    NovelPageTurnSliderOption(
        value = NovelPageTurnActivationZone.NARROWER,
        labelRes = AYMR.strings.novel_reader_page_turn_activation_zone_narrower,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnActivationZone.NARROW,
        labelRes = AYMR.strings.novel_reader_page_turn_activation_zone_narrow,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnActivationZone.NORMAL,
        labelRes = AYMR.strings.novel_reader_page_turn_activation_zone_normal,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnActivationZone.WIDE,
        labelRes = AYMR.strings.novel_reader_page_turn_activation_zone_wide,
    ),
    NovelPageTurnSliderOption(
        value = NovelPageTurnActivationZone.WIDER,
        labelRes = AYMR.strings.novel_reader_page_turn_activation_zone_wider,
    ),
)

internal fun shouldShowPageTurnTuningControls(
    pageReaderEnabled: Boolean,
    style: NovelPageTransitionStyle,
): Boolean {
    return pageReaderEnabled && resolvePageTransitionEngine(style) == NovelPageTransitionEngine.PAGE_TURN_RENDERER
}

private fun <T> resolveSliderIndex(
    options: List<NovelPageTurnSliderOption<T>>,
    value: T,
): Int {
    return options.indexOfFirst { it.value == value }.coerceAtLeast(0)
}

private fun <T> resolveSliderValue(
    options: List<NovelPageTurnSliderOption<T>>,
    index: Int,
): T {
    return options[index.coerceIn(0, options.lastIndex)].value
}

@Composable
private fun <T> resolveSliderEntries(
    options: List<NovelPageTurnSliderOption<T>>,
): ImmutableMap<T, String> {
    return options.associate { option ->
        option.value to stringResource(option.labelRes)
    }.toImmutableMap()
}

internal fun <T> resolveNovelPageTurnSliderLabel(
    value: T,
    entries: Map<T, String>,
): String {
    return entries[value].orEmpty()
}

internal fun resolveNovelPageTurnTuningSummaryText(
    format: String,
    speedLabel: String,
    intensityLabel: String,
    shadowLabel: String,
    activationZoneLabel: String,
): String {
    return format.format(speedLabel, intensityLabel, shadowLabel, activationZoneLabel)
}

internal fun resolveNovelPageTurnTuningSummaryText(
    format: String,
    speedLabel: String,
    intensityLabel: String,
    shadowLabel: String,
): String {
    return format.format(speedLabel, intensityLabel, shadowLabel)
}

internal fun novelPageTurnSpeedSliderIndex(speed: NovelPageTurnSpeed): Int {
    return resolveSliderIndex(novelPageTurnSpeedSliderOptions, speed)
}

internal fun resolveNovelPageTurnSpeedSliderValue(index: Int): NovelPageTurnSpeed {
    return resolveSliderValue(novelPageTurnSpeedSliderOptions, index)
}

@Composable
internal fun novelPageTurnSpeedEntries(): ImmutableMap<NovelPageTurnSpeed, String> {
    return resolveSliderEntries(novelPageTurnSpeedSliderOptions)
}

internal fun novelPageTurnIntensitySliderIndex(intensity: NovelPageTurnIntensity): Int {
    return resolveSliderIndex(novelPageTurnIntensitySliderOptions, intensity)
}

internal fun resolveNovelPageTurnIntensitySliderValue(index: Int): NovelPageTurnIntensity {
    return resolveSliderValue(novelPageTurnIntensitySliderOptions, index)
}

@Composable
internal fun novelPageTurnIntensityEntries(): ImmutableMap<NovelPageTurnIntensity, String> {
    return resolveSliderEntries(novelPageTurnIntensitySliderOptions)
}

internal fun novelPageTurnShadowIntensitySliderIndex(
    shadowIntensity: NovelPageTurnShadowIntensity,
): Int {
    return resolveSliderIndex(novelPageTurnShadowIntensitySliderOptions, shadowIntensity)
}

internal fun resolveNovelPageTurnShadowIntensitySliderValue(
    index: Int,
): NovelPageTurnShadowIntensity {
    return resolveSliderValue(novelPageTurnShadowIntensitySliderOptions, index)
}

@Composable
internal fun novelPageTurnShadowIntensityEntries(): ImmutableMap<NovelPageTurnShadowIntensity, String> {
    return resolveSliderEntries(novelPageTurnShadowIntensitySliderOptions)
}

internal fun novelPageTurnActivationZoneSliderIndex(zone: NovelPageTurnActivationZone): Int {
    return resolveSliderIndex(novelPageTurnActivationZoneSliderOptions, zone)
}

internal fun resolveNovelPageTurnActivationZoneSliderValue(index: Int): NovelPageTurnActivationZone {
    return resolveSliderValue(novelPageTurnActivationZoneSliderOptions, index)
}

@Composable
internal fun novelPageTurnActivationZoneEntries(): ImmutableMap<NovelPageTurnActivationZone, String> {
    return resolveSliderEntries(novelPageTurnActivationZoneSliderOptions)
}

@Composable
internal fun novelPageTurnTuningSummary(
    speed: NovelPageTurnSpeed,
    intensity: NovelPageTurnIntensity,
    shadowIntensity: NovelPageTurnShadowIntensity,
    activationZone: NovelPageTurnActivationZone,
    speedEntries: Map<NovelPageTurnSpeed, String>,
    intensityEntries: Map<NovelPageTurnIntensity, String>,
    shadowEntries: Map<NovelPageTurnShadowIntensity, String>,
    activationZoneEntries: Map<NovelPageTurnActivationZone, String>,
): String {
    return resolveNovelPageTurnTuningSummaryText(
        format = stringResource(AYMR.strings.novel_reader_page_turn_tuning_summary_format),
        speedLabel = resolveNovelPageTurnSliderLabel(speed, speedEntries),
        intensityLabel = resolveNovelPageTurnSliderLabel(intensity, intensityEntries),
        shadowLabel = resolveNovelPageTurnSliderLabel(shadowIntensity, shadowEntries),
        activationZoneLabel = resolveNovelPageTurnSliderLabel(activationZone, activationZoneEntries),
    )
}
