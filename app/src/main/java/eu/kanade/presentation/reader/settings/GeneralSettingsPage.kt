package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState

private val themes = listOf(
    MR.strings.black_background to 1,
    MR.strings.gray_background to 2,
    MR.strings.white_background to 0,
    MR.strings.automatic_background to 3,
)

private val flashColors = listOf(
    MR.strings.pref_flash_style_black to ReaderPreferences.FlashColor.BLACK,
    MR.strings.pref_flash_style_white to ReaderPreferences.FlashColor.WHITE,
    MR.strings.pref_flash_style_white_black to ReaderPreferences.FlashColor.WHITE_BLACK,
)

@Composable
internal fun ColumnScope.GeneralPage(screenModel: ReaderSettingsScreenModel) {
    val readerTheme by screenModel.preferences.readerTheme().collectAsState()

    val flashPageState by screenModel.preferences.flashOnPageChange().collectAsState()

    val flashMillisPref = screenModel.preferences.flashDurationMillis()
    val flashMillis by flashMillisPref.collectAsState()

    val flashIntervalPref = screenModel.preferences.flashPageInterval()
    val flashInterval by flashIntervalPref.collectAsState()

    val flashColorPref = screenModel.preferences.flashColor()
    val flashColor by flashColorPref.collectAsState()

    var showNavigatorSettings by remember { mutableStateOf(false) }

    if (showNavigatorSettings) {
        NavigatorSettingsDialog(
            onDismissRequest = { showNavigatorSettings = false },
            screenModel = screenModel,
        )
    }

    // Section: display.
    AuroraGlassSection(title = stringResource(MR.strings.pref_category_display)) {
        AuroraFieldLabel(stringResource(MR.strings.pref_reader_theme))
        AuroraSegmentedRow(
            options = themes.map { stringResource(it.first) },
            selectedIndex = themes.indexOfFirst { it.second == readerTheme },
            onSelect = { screenModel.preferences.readerTheme().set(themes[it].second) },
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_fullscreen),
            pref = screenModel.preferences.fullscreen(),
        )
        if (screenModel.hasDisplayCutout && screenModel.preferences.fullscreen().get()) {
            AuroraToggleRow(
                label = stringResource(MR.strings.pref_cutout_short),
                pref = screenModel.preferences.cutoutShort(),
            )
        }
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_keep_screen_on),
            pref = screenModel.preferences.keepScreenOn(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_show_page_number),
            pref = screenModel.preferences.showPageNumber(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_show_reading_time_left),
            pref = screenModel.preferences.showReadingTimeLeft(),
        )
    }

    // Section: reading behavior.
    val joinDoublePages by screenModel.preferences.joinDoublePages().collectAsState()
    AuroraGlassSection(title = stringResource(MR.strings.pref_category_reading)) {
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_auto_webtoon_mode),
            pref = screenModel.preferences.useAutoWebtoon(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_read_with_long_tap),
            pref = screenModel.preferences.readWithLongTap(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_always_show_chapter_transition),
            pref = screenModel.preferences.alwaysShowChapterTransition(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_page_transitions),
            pref = screenModel.preferences.pageTransitions(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_join_double_pages),
            pref = screenModel.preferences.joinDoublePages(),
        )
        if (joinDoublePages) {
            AuroraToggleRow(
                label = stringResource(MR.strings.pref_shift_double_pages),
                pref = screenModel.preferences.shiftDoublePages(),
            )
        }
    }

    // Section: top toolbar action buttons only.
    AuroraGlassSection(title = stringResource(AYMR.strings.reader_toolbar_heading)) {
        AuroraToggleRow(
            label = stringResource(AYMR.strings.reader_toolbar_show_webview),
            pref = screenModel.preferences.showToolbarWebViewButton(),
        )
        AuroraToggleRow(
            label = stringResource(AYMR.strings.reader_toolbar_show_share),
            pref = screenModel.preferences.showToolbarShareButton(),
        )
    }

    // Section: page slider / scrollbar — separate from toolbar (own studio panel).
    AuroraGlassSection(title = stringResource(MR.strings.pref_navigator_settings)) {
        AuroraNavRow(
            label = stringResource(MR.strings.action_settings),
            onClick = { showNavigatorSettings = true },
        )
    }

    val bottomBarButtonsOrder by screenModel.preferences.bottomBarButtonsOrder().collectAsState()

    val defaultOrder = remember {
        listOf("reading_mode", "orientation", "crop_borders", "chapter_list", "settings")
    }

    val orderListState = remember(bottomBarButtonsOrder) {
        val list = bottomBarButtonsOrder.split(",").filter { it.isNotBlank() && it in defaultOrder }.toMutableList()
        defaultOrder.forEach { if (it !in list) list.add(it) }
        list.toMutableStateList()
    }

    val updateOrder = { newList: List<String> ->
        screenModel.preferences.bottomBarButtonsOrder().set(newList.joinToString(","))
    }

    // Section: bottom bar actions (reorderable).
    AuroraGlassSection(title = stringResource(MR.strings.pref_bottom_bar)) {
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val item = orderListState.removeAt(from.index)
            orderListState.add(to.index, item)
            updateOrder(orderListState)
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            userScrollEnabled = false,
        ) {
            items(
                items = orderListState,
                key = { it },
            ) { buttonId ->
                ReorderableItem(reorderableState, buttonId) { isDragging ->
                    val label = when (buttonId) {
                        "reading_mode" -> stringResource(MR.strings.pref_bottom_bar_show_reading_mode)
                        "orientation" -> stringResource(MR.strings.pref_bottom_bar_show_orientation)
                        "crop_borders" -> stringResource(MR.strings.pref_bottom_bar_show_crop_borders)
                        "chapter_list" -> stringResource(MR.strings.pref_bottom_bar_show_chapter_list)
                        "settings" -> stringResource(MR.strings.pref_bottom_bar_show_settings)
                        else -> ""
                    }
                    val pref = when (buttonId) {
                        "reading_mode" -> screenModel.preferences.showBottomBarReadingMode()
                        "orientation" -> screenModel.preferences.showBottomBarOrientation()
                        "crop_borders" -> screenModel.preferences.showBottomBarCropBorders()
                        "chapter_list" -> screenModel.preferences.showBottomBarChapterList()
                        "settings" -> screenModel.preferences.showBottomBarSettings()
                        else -> null
                    }
                    if (pref != null) {
                        val checked by pref.collectAsState()
                        BottomBarReorderItem(
                            modifier = Modifier.animateItem(),
                            label = label,
                            checked = checked,
                            onCheckedChange = { pref.set(!checked) },
                            dragHandleModifier = Modifier.draggableHandle(),
                        )
                    }
                }
            }
        }
    }

    // Section: E-Ink.
    AuroraGlassSection(title = stringResource(MR.strings.pref_category_eink)) {
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_flash_page),
            pref = screenModel.preferences.flashOnPageChange(),
        )
        if (flashPageState) {
            AuroraSliderRow(
                label = stringResource(MR.strings.pref_flash_duration),
                value = flashMillis / ReaderPreferences.MILLI_CONVERSION,
                valueRange = 1..15,
                valueText = stringResource(MR.strings.pref_flash_duration_summary, flashMillis),
                onChange = { flashMillisPref.set(it * ReaderPreferences.MILLI_CONVERSION) },
            )
            AuroraSliderRow(
                label = stringResource(MR.strings.pref_flash_page_interval),
                value = flashInterval,
                valueRange = 1..10,
                valueText = pluralStringResource(MR.plurals.pref_pages, flashInterval, flashInterval),
                onChange = {
                    flashIntervalPref.set(it)
                },
            )
            AuroraFieldLabel(stringResource(MR.strings.pref_flash_with))
            AuroraChipFlow {
                flashColors.forEach { (labelRes, value) ->
                    AuroraChip(
                        selected = flashColor == value,
                        onClick = { flashColorPref.set(value) },
                        label = stringResource(labelRes),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BottomBarReorderItem(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val appHaptics = LocalAppHaptics.current
    Row(
        modifier = modifier
            .clickable(onClick = {
                appHaptics.tap()
                onCheckedChange()
            })
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AuroraTheme.colors.textPrimary,
            )
        }
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = null,
            tint = AuroraTheme.colors.textSecondary,
            modifier = dragHandleModifier.padding(horizontal = 8.dp),
        )
    }
}
