package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Composable
import eu.kanade.presentation.reader.components.FinalePlate
import eu.kanade.tachiyomi.ui.reader.model.ReaderFinaleState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Novel binding of the shared [FinalePlate]: revealed at the witnessed completion moment
 * (novels have no end-of-series transition page), primary action pops back to the entry screen.
 */
@Composable
fun NovelFinaleOverlay(
    state: ReaderFinaleState,
    reducedMotion: Boolean,
    onBackToNovel: () -> Unit,
    onStay: () -> Unit,
) {
    FinalePlate(
        state = state,
        reducedMotion = reducedMotion,
        backLabel = stringResource(MR.strings.reader_finale_back_to_novel),
        onBack = onBackToNovel,
        onStay = onStay,
    )
}
