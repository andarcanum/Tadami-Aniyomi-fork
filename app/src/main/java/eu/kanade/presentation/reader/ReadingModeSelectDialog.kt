package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.entries.manga.model.readingMode
import eu.kanade.presentation.reader.components.AuroraReaderSheet
import eu.kanade.presentation.reader.components.ModeSelectionDialog
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraModeCard
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle

private val ReadingModesWithoutDefault = ReadingMode.entries - ReadingMode.DEFAULT

@Composable
fun ReadingModeSelectDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    onChange: (StringResource) -> Unit,
) {
    val manga by screenModel.mangaFlow.collectAsStateWithLifecycle()
    val defaultReadingMode by screenModel.preferences.defaultReadingMode().collectAsStateWithLifecycle()
    val storedReadingMode = remember(manga) {
        ReadingMode.fromPreference(manga?.readingMode?.toInt())
    }
    // Highlight the mode the viewer really runs on (series flags, auto-detected webtoon or the
    // global default) — the manga stays on DEFAULT for most entries, which would otherwise leave
    // every tile unselected.
    // B-L: resolvedReadingMode() also depends on the LIVE viewer (auto-detected webtoon rebuilds
    // it); the old keys (manga/defaultReadingMode) missed those changes and left a stale
    // highlight while the dialog was open. Key on the viewer flow too.
    val viewer by screenModel.viewerFlow.collectAsStateWithLifecycle()
    val readingMode = remember(storedReadingMode, defaultReadingMode, viewer) {
        screenModel.resolvedReadingMode().takeIf { it != ReadingMode.DEFAULT }
            ?: ReadingMode.fromPreference(defaultReadingMode)
    }

    AuroraReaderSheet(onDismissRequest = onDismissRequest) {
        DialogContent(
            readingMode = readingMode,
            hasSeriesOverride = storedReadingMode != ReadingMode.DEFAULT,
            onChangeReadingMode = {
                screenModel.onChangeReadingMode(it)
                onChange(it.stringRes)
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun DialogContent(
    readingMode: ReadingMode,
    hasSeriesOverride: Boolean,
    onChangeReadingMode: (ReadingMode) -> Unit,
) {
    var selected by remember { mutableStateOf(readingMode) }

    ModeSelectionDialog(
        onUseDefault = { onChangeReadingMode(ReadingMode.DEFAULT) }.takeIf { hasSeriesOverride },
        onApply = { onChangeReadingMode(selected) },
    ) {
        AuroraGlassSection(title = stringResource(MR.strings.pref_category_reading_mode)) {
            ReadingModesWithoutDefault.chunked(2).forEach { rowModes ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowModes.forEach { mode ->
                        AuroraModeCard(
                            selected = mode == selected,
                            onClick = { selected = mode },
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
    }
}
