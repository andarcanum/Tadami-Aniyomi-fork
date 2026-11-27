package eu.kanade.presentation.reader.novel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Bottom info overlay: battery/time, time-to-end and word count.
 *
 * Leaf composable extracted from [NovelReaderContentHost] with a narrow parameter list - only the
 * values this block actually reads, so changing unrelated reader state does not recompose it.
 */
@Composable
internal fun NovelReaderInfoOverlay(
    visible: Boolean,
    settings: NovelReaderSettings,
    batteryLevel: Int,
    timeText: String,
    remainingMinutes: Int?,
    readWords: Int,
    totalWords: Int,
    bottomBarHeightPx: Int,
    density: Density,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.displayCutout
                    .union(WindowInsets.navigationBars)
                    .only(WindowInsetsSides.Horizontal),
            )
            .padding(
                bottom = with(density) { bottomBarHeightPx.toDp() } + MaterialTheme.padding.small,
            ),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.padding.small,
                    vertical = 6.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (settings.showBatteryAndTime) {
                    Text(
                        text = "${batteryLevel.coerceIn(0, 100)}% $timeText",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (settings.showKindleInfoBlock && settings.showTimeToEnd) {
                    Text(
                        text = if (remainingMinutes == null) {
                            stringResource(AYMR.strings.novel_reader_time_to_end_unknown)
                        } else {
                            stringResource(
                                AYMR.strings.novel_reader_time_to_end_minutes,
                                remainingMinutes.coerceAtLeast(0),
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (settings.showKindleInfoBlock && settings.showWordCount) {
                    Text(
                        text = stringResource(
                            AYMR.strings.novel_reader_words_progress,
                            readWords,
                            totalWords,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
