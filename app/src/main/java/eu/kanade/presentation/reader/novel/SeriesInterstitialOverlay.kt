package eu.kanade.presentation.reader.novel

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.novel.sourceAwareNovelCoverModel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

data class SeriesInterstitialState(
    val seriesTitle: String,
    val currentNovelTitle: String,
    val nextNovel: Novel?,
    val nextChapterId: Long?,
    val nextChapterName: String?,
)

@Composable
fun SeriesInterstitialOverlay(
    state: SeriesInterstitialState,
    onBackToSeries: () -> Unit,
    onContinue: (() -> Unit)?,
    onDismissRequest: () -> Unit = onBackToSeries,
) {
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
                        text = stringResource(AYMR.strings.series_interstitial_series_completed),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = state.seriesTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()

                if (state.nextNovel != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ItemCover.Book(
                            data = sourceAwareNovelCoverModel(state.nextNovel),
                            modifier = Modifier
                                .size(width = 72.dp, height = 108.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    AYMR.strings.series_interstitial_completed,
                                    state.currentNovelTitle,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    AYMR.strings.series_interstitial_next_novel,
                                    state.nextNovel.title,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            state.nextChapterName?.let { chapterName ->
                                Text(
                                    text = stringResource(
                                        AYMR.strings.series_interstitial_next_chapter,
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
                        text = stringResource(AYMR.strings.series_interstitial_series_completed),
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
                        Text(text = stringResource(AYMR.strings.series_interstitial_back_to_series))
                    }
                    if (onContinue != null && state.nextNovel != null && state.nextChapterId != null) {
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
