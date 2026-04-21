package eu.kanade.presentation.reader.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.request.ImageRequest
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.series.manga.resolveMangaResumeChapter
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

data class MangaSeriesInterstitialState(
    val seriesTitle: String,
    val currentMangaTitle: String,
    val nextManga: LibraryManga?,
    val nextChapterId: Long?,
    val nextChapterName: String?,
)

fun resolveMangaSeriesInterstitialState(
    series: LibraryMangaSeries,
    currentManga: Manga,
    currentChapter: Chapter,
    chaptersByManga: List<Pair<LibraryManga, List<Chapter>>>,
): MangaSeriesInterstitialState? {
    val currentIndex = series.entries.indexOfFirst { it.id == currentManga.id }
    if (currentIndex < 0) return null

    val currentEntry = chaptersByManga.firstOrNull { it.first.id == currentManga.id } ?: return null
    val currentChapters = currentEntry.second.sortedWith(
        compareBy<Chapter> { it.chapterNumber }
            .thenBy { it.sourceOrder }
            .thenBy { it.id },
    )
    if (currentChapters.lastOrNull()?.id != currentChapter.id) return null

    val nextManga = series.entries.getOrNull(currentIndex + 1)
    val nextChapter = nextManga?.let { nextEntry ->
        val nextChapters = chaptersByManga.firstOrNull { it.first.id == nextEntry.id }?.second.orEmpty()
        resolveMangaResumeChapter(nextChapters)
    }

    return MangaSeriesInterstitialState(
        seriesTitle = series.title,
        currentMangaTitle = currentManga.title,
        nextManga = nextManga,
        nextChapterId = nextChapter?.id,
        nextChapterName = nextChapter?.name,
    )
}

@Composable
fun MangaSeriesInterstitialOverlay(
    state: MangaSeriesInterstitialState,
    onBackToSeries: () -> Unit,
    onContinue: (() -> Unit)?,
    onDismissRequest: () -> Unit = onBackToSeries,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(AYMR.strings.manga_series_interstitial_series_completed),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = state.seriesTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()

                if (state.nextManga != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ItemCover.Book(
                            data = ImageRequest.Builder(context)
                                .data(state.nextManga.manga)
                                .build(),
                            modifier = Modifier
                                .size(width = 72.dp, height = 108.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentDescription = stringResource(MR.strings.manga_cover),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    AYMR.strings.manga_series_interstitial_completed,
                                    state.currentMangaTitle,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    AYMR.strings.manga_series_interstitial_next_manga,
                                    state.nextManga.manga.title,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            state.nextChapterName?.let { chapterName ->
                                Text(
                                    text = stringResource(
                                        AYMR.strings.manga_series_interstitial_next_chapter,
                                        chapterName,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(AYMR.strings.manga_series_interstitial_series_completed),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = onBackToSeries,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(AYMR.strings.manga_series_interstitial_back_to_series))
                    }
                    if (onContinue != null && state.nextManga != null && state.nextChapterId != null) {
                        Button(
                            onClick = onContinue,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(AYMR.strings.action_continue))
                        }
                    }
                }
            }
        }
    }
}
