package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.domain.entries.manga.model.readerOrientation
import eu.kanade.domain.entries.manga.model.readingMode
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import java.text.NumberFormat

@Composable
internal fun ColumnScope.ReadingModePage(screenModel: ReaderSettingsScreenModel) {
    val manga by screenModel.mangaFlow.collectAsStateWithLifecycle()
    val defaultReadingMode by screenModel.preferences.defaultReadingMode().collectAsStateWithLifecycle()
    val defaultOrientation by screenModel.preferences.defaultOrientationType().collectAsStateWithLifecycle()

    val storedReadingMode = remember(manga) {
        ReadingMode.fromPreference(manga?.readingMode?.toInt())
    }
    val storedOrientation = remember(manga) {
        ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
    }
    // Recompute from manga + toggle helper so UI tracks flag writes.
    // Derived from manga flags so UI updates when override is toggled or modes change.
    val seriesOverrideEnabled = remember(storedReadingMode, storedOrientation) {
        storedReadingMode != ReadingMode.DEFAULT || storedOrientation != ReaderOrientation.DEFAULT
    }

    // Active values shown in the grids: series flags, or global defaults when not overridden.
    val readingMode = remember(manga, defaultReadingMode, seriesOverrideEnabled) {
        if (seriesOverrideEnabled && storedReadingMode != ReadingMode.DEFAULT) {
            storedReadingMode
        } else {
            ReadingMode.fromPreference(defaultReadingMode)
        }
    }
    val orientation = remember(manga, defaultOrientation, seriesOverrideEnabled) {
        if (seriesOverrideEnabled && storedOrientation != ReaderOrientation.DEFAULT) {
            storedOrientation
        } else {
            ReaderOrientation.fromPreference(defaultOrientation)
        }
    }

    // Novel-like scope control: series override vs global defaults.
    AuroraGlassSection(title = stringResource(MR.strings.pref_category_for_this_series)) {
        manga?.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = AuroraTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        AuroraToggleRow(
            label = stringResource(MR.strings.reader_override_series),
            subtitle = stringResource(MR.strings.reader_override_series_summary),
            checked = seriesOverrideEnabled,
            onClick = { screenModel.onSetSeriesViewerOverride(!seriesOverrideEnabled) },
        )
        Text(
            text = if (seriesOverrideEnabled) {
                stringResource(MR.strings.reader_editing_series)
            } else {
                stringResource(MR.strings.reader_editing_global_defaults)
            },
            style = MaterialTheme.typography.bodySmall,
            color = AuroraTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        Text(
            text = stringResource(MR.strings.reader_override_series_hint),
            style = MaterialTheme.typography.bodySmall,
            color = AuroraTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }

    // Reading mode — exclude DEFAULT tile (scope is controlled by the toggle above).
    val modeOptions = remember {
        ReadingMode.entries.filter { it != ReadingMode.DEFAULT }
    }
    AuroraGlassSection(title = stringResource(MR.strings.pref_category_reading_mode)) {
        modeOptions.chunked(2).forEach { rowModes ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowModes.forEach { mode ->
                    AuroraModeCard(
                        selected = mode == readingMode,
                        onClick = { screenModel.onChangeReadingMode(mode) },
                        label = stringResource(mode.stringRes),
                        painter = painterResource(mode.iconRes),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowModes.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    // Orientation — same: no DEFAULT tile; toggle owns scope.
    val orientationOptions = remember {
        ReaderOrientation.entries.filter { it != ReaderOrientation.DEFAULT }
    }
    AuroraGlassSection(title = stringResource(MR.strings.rotation_type)) {
        orientationOptions.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    AuroraMiniOption(
                        selected = item == orientation,
                        onClick = { screenModel.onChangeOrientation(item) },
                        label = stringResource(item.stringRes),
                        icon = item.icon,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    val viewer by screenModel.viewerFlow.collectAsStateWithLifecycle()
    if (viewer is WebtoonViewer) {
        WebtoonViewerSettings(screenModel)
    } else {
        PagerViewerSettings(screenModel)
    }

    AuroraGlassSection {
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_save_long_page_position),
            pref = screenModel.preferences.saveLongPagePosition(),
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TapZonesSection(
    selected: Int,
    onSelect: (Int) -> Unit,
    invertMode: ReaderPreferences.TappingInvertMode,
    onSelectInvertMode: (ReaderPreferences.TappingInvertMode) -> Unit,
) {
    AuroraGlassSection(title = stringResource(MR.strings.pref_viewer_nav)) {
        AuroraChipFlow {
            ReaderPreferences.TapZones.forEachIndexed { index, it ->
                AuroraChip(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    label = stringResource(it),
                )
            }
        }

        if (selected != 5) {
            AuroraFieldLabel(stringResource(MR.strings.pref_read_with_tapping_inverted))
            AuroraChipFlow {
                ReaderPreferences.TappingInvertMode.entries.forEach {
                    AuroraChip(
                        selected = it == invertMode,
                        onClick = { onSelectInvertMode(it) },
                        label = stringResource(it.titleRes),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PagerViewerSettings(screenModel: ReaderSettingsScreenModel) {
    val navigationModePager by screenModel.preferences.navigationModePager().collectAsStateWithLifecycle()
    val pagerNavInverted by screenModel.preferences.pagerNavInverted().collectAsStateWithLifecycle()
    TapZonesSection(
        selected = navigationModePager,
        onSelect = screenModel.preferences.navigationModePager()::set,
        invertMode = pagerNavInverted,
        onSelectInvertMode = screenModel.preferences.pagerNavInverted()::set,
    )

    // Section: scaling.
    val imageScaleType by screenModel.preferences.imageScaleType().collectAsStateWithLifecycle()
    val zoomStart by screenModel.preferences.zoomStart().collectAsStateWithLifecycle()
    AuroraGlassSection(title = stringResource(MR.strings.pref_image_scale_type)) {
        AuroraChipFlow {
            ReaderPreferences.ImageScaleType.forEachIndexed { index, it ->
                AuroraChip(
                    selected = imageScaleType == index + 1,
                    onClick = { screenModel.preferences.imageScaleType().set(index + 1) },
                    label = stringResource(it),
                )
            }
        }
        AuroraFieldLabel(stringResource(MR.strings.pref_zoom_start))
        AuroraChipFlow {
            ReaderPreferences.ZoomStart.forEachIndexed { index, it ->
                AuroraChip(
                    selected = zoomStart == index + 1,
                    onClick = { screenModel.preferences.zoomStart().set(index + 1) },
                    label = stringResource(it),
                )
            }
        }
    }

    // Section: pager toggles.
    val dualPageSplitPaged by screenModel.preferences.dualPageSplitPaged().collectAsStateWithLifecycle()
    val dualPageRotateToFit by screenModel.preferences.dualPageRotateToFit().collectAsStateWithLifecycle()
    AuroraGlassSection(title = stringResource(MR.strings.pager_viewer)) {
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_crop_borders),
            pref = screenModel.preferences.cropBorders(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_landscape_zoom),
            pref = screenModel.preferences.landscapeZoom(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_navigate_pan),
            pref = screenModel.preferences.navigateToPan(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_dual_page_split),
            pref = screenModel.preferences.dualPageInvertPaged(),
        )
        if (dualPageSplitPaged) {
            AuroraToggleRow(
                label = stringResource(MR.strings.pref_dual_page_invert),
                pref = screenModel.preferences.dualPageRotateToFitInvert(),
            )
        }
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_page_rotate),
            pref = screenModel.preferences.dualPageRotateToFit(),
        )
        if (dualPageRotateToFit) {
            AuroraToggleRow(
                label = stringResource(MR.strings.pref_page_rotate_invert),
                pref = screenModel.preferences.dualPageRotateToFitInvert(),
            )
        }
    }
}

@Composable
private fun ColumnScope.WebtoonViewerSettings(screenModel: ReaderSettingsScreenModel) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }

    val navigationModeWebtoon by screenModel.preferences.navigationModeWebtoon().collectAsStateWithLifecycle()
    val webtoonNavInverted by screenModel.preferences.webtoonNavInverted().collectAsStateWithLifecycle()
    TapZonesSection(
        selected = navigationModeWebtoon,
        onSelect = screenModel.preferences.navigationModeWebtoon()::set,
        invertMode = webtoonNavInverted,
        onSelectInvertMode = screenModel.preferences.webtoonNavInverted()::set,
    )

    // Section: webtoon layout and toggles.
    val webtoonSidePadding by screenModel.preferences.webtoonSidePadding().collectAsStateWithLifecycle()
    val dualPageSplitWebtoon by screenModel.preferences.dualPageSplitWebtoon().collectAsStateWithLifecycle()
    val dualPageRotateToFitWebtoon by screenModel.preferences.dualPageRotateToFitWebtoon().collectAsStateWithLifecycle()
    AuroraGlassSection(title = stringResource(MR.strings.webtoon_viewer)) {
        AuroraSliderRow(
            label = stringResource(MR.strings.pref_webtoon_side_padding),
            value = webtoonSidePadding,
            valueRange = ReaderPreferences.WEBTOON_PADDING_MIN..ReaderPreferences.WEBTOON_PADDING_MAX,
            valueText = numberFormat.format(webtoonSidePadding / 100f),
            onChange = {
                screenModel.preferences.webtoonSidePadding().set(it)
            },
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_crop_borders),
            pref = screenModel.preferences.cropBordersWebtoon(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_webtoon_smart_fit),
            pref = screenModel.preferences.webtoonSmartFit(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_dual_page_split),
            pref = screenModel.preferences.dualPageSplitWebtoon(),
        )
        if (dualPageSplitWebtoon) {
            AuroraToggleRow(
                label = stringResource(MR.strings.pref_dual_page_invert),
                pref = screenModel.preferences.dualPageInvertWebtoon(),
            )
        }
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_page_rotate),
            pref = screenModel.preferences.dualPageRotateToFitWebtoon(),
        )
        if (dualPageRotateToFitWebtoon) {
            AuroraToggleRow(
                label = stringResource(MR.strings.pref_page_rotate_invert),
                pref = screenModel.preferences.dualPageRotateToFitInvertWebtoon(),
            )
        }
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_double_tap_zoom),
            pref = screenModel.preferences.webtoonDoubleTapZoomEnabled(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
            pref = screenModel.preferences.webtoonDisableZoomOut(),
        )
    }
}
