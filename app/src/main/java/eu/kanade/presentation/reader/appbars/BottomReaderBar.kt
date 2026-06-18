package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tadami.aurora.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

data class BottomBarButtonFlags(
    val readingMode: Boolean = true,
    val orientation: Boolean = true,
    val cropBorders: Boolean = true,
    val chapterList: Boolean = true,
    val settings: Boolean = true,
) {
    fun hasAnyVisible(): Boolean =
        readingMode || orientation || cropBorders || chapterList || settings
}

@Composable
fun BottomReaderBar(
    backgroundColor: Color,
    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickChapterList: () -> Unit,
    onClickSettings: () -> Unit,
    visibleButtons: BottomBarButtonFlags = BottomBarButtonFlags(),
    buttonsOrder: List<String> = emptyList(),
) {
    val appHaptics = LocalAppHaptics.current

    val defaultOrder = remember {
        listOf("reading_mode", "orientation", "crop_borders", "chapter_list", "settings")
    }
    val order = remember(buttonsOrder) {
        val list = buttonsOrder.filter { it in defaultOrder }.toMutableList()
        defaultOrder.forEach { if (it !in list) list.add(it) }
        list
    }

    if (visibleButtons.hasAnyVisible()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                )
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            order.forEach { buttonId ->
                when (buttonId) {
                    "reading_mode" -> {
                        if (visibleButtons.readingMode) {
                            IconButton(onClick = {
                                appHaptics.tap()
                                onClickReadingMode()
                            }) {
                                Icon(
                                    painter = painterResource(readingMode.iconRes),
                                    contentDescription = stringResource(MR.strings.viewer),
                                )
                            }
                        }
                    }
                    "orientation" -> {
                        if (visibleButtons.orientation) {
                            IconButton(onClick = {
                                appHaptics.tap()
                                onClickOrientation()
                            }) {
                                Icon(
                                    imageVector = orientation.icon,
                                    contentDescription = stringResource(MR.strings.rotation_type),
                                )
                            }
                        }
                    }
                    "crop_borders" -> {
                        if (visibleButtons.cropBorders) {
                            IconButton(onClick = {
                                appHaptics.tap()
                                onClickCropBorder()
                            }) {
                                Icon(
                                    painter = painterResource(
                                        if (cropEnabled) R.drawable.ic_crop_24dp else R.drawable.ic_crop_off_24dp,
                                    ),
                                    contentDescription = stringResource(MR.strings.pref_crop_borders),
                                )
                            }
                        }
                    }
                    "chapter_list" -> {
                        if (visibleButtons.chapterList) {
                            IconButton(onClick = {
                                appHaptics.tap()
                                onClickChapterList()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = stringResource(MR.strings.chapters),
                                )
                            }
                        }
                    }
                    "settings" -> {
                        if (visibleButtons.settings) {
                            IconButton(onClick = {
                                appHaptics.tap()
                                onClickSettings()
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = stringResource(MR.strings.action_settings),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
