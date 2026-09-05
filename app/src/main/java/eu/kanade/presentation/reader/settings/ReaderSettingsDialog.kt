package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.components.AuroraReaderSheet
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Aurora glass sheet for manga reader quick settings.
 */
@Composable
fun ReaderSettingsDialog(
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    onHideMenus: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
) {
    val tabTitles = listOf(
        stringResource(MR.strings.pref_category_reading_mode),
        stringResource(MR.strings.pref_category_general),
        stringResource(MR.strings.reader_settings_tab_color),
    )
    val pagerState = rememberPagerState { tabTitles.size }
    val scope = rememberCoroutineScope()
    val pageMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    // РЕШ-5 revival: onShowMenus/onHideMenus were dead parameters. The intent recorded at the
    // call site (ReaderActivity passes the real setMenuVisibility) is to hide the reader menus
    // behind the settings sheet and restore them when it closes.
    DisposableEffect(Unit) {
        onHideMenus()
        onDispose { onShowMenus() }
    }

    AuroraReaderSheet(onDismissRequest = onDismissRequest) {
        AuroraTabRow(
            titles = tabTitles,
            selectedIndex = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
        )
        // No divider under tabs — glass cards already separate content; a rim line
        // reads as a flat "belt" between the capsule tabs and the first section.
        HorizontalPager(
            modifier = Modifier.heightIn(max = pageMaxHeight),
            state = pagerState,
            verticalAlignment = Alignment.Top,
            beyondViewportPageCount = 0,
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = pageMaxHeight)
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (page) {
                    0 -> ReadingModePage(screenModel)
                    1 -> GeneralPage(screenModel)
                    2 -> ColorFilterPage(screenModel)
                }
            }
        }
    }
}
