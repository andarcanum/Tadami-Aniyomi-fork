package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun novelPageTransitionStyleEntries(): ImmutableMap<NovelPageTransitionStyle, String> {
    return persistentMapOf(
        NovelPageTransitionStyle.INSTANT to
            stringResource(AYMR.strings.novel_reader_page_transition_style_instant),
        NovelPageTransitionStyle.SLIDE to
            stringResource(AYMR.strings.novel_reader_page_transition_style_slide),
        NovelPageTransitionStyle.DEPTH to
            stringResource(AYMR.strings.novel_reader_page_transition_style_depth),
        NovelPageTransitionStyle.BOOK to
            stringResource(AYMR.strings.novel_reader_page_transition_style_book),
        NovelPageTransitionStyle.CURL to
            stringResource(AYMR.strings.novel_reader_page_transition_style_curl),
    )
}

@Composable
internal fun novelPageTransitionStyleSubtitle(
    style: NovelPageTransitionStyle,
    entries: Map<NovelPageTransitionStyle, String>,
): String {
    val currentLabel = entries[style].orEmpty()
    return when (style) {
        NovelPageTransitionStyle.BOOK,
        NovelPageTransitionStyle.CURL,
        -> {
            stringResource(
                AYMR.strings.novel_reader_page_transition_style_page_turn_fallback,
                currentLabel,
                entries[NovelPageTransitionStyle.SLIDE].orEmpty(),
            )
        }
        else -> currentLabel
    }
}
