package eu.kanade.presentation.series.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.library.manga.components.SeriesStackedCoverCard
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun MangaSeriesHeader(
    series: LibraryMangaSeries,
    customCoverData: Any? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(0.7f),
            contentAlignment = Alignment.Center,
        ) {
            SeriesStackedCoverCard(
                covers = series.coverMangas.map { it.asMangaCover() },
                customCoverData = customCoverData,
                isSelected = false,
                widthFactor = 0.85f,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = series.title,
            color = colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Book,
                    contentDescription = null,
                    tint = colors.textPrimary.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = pluralStringResource(
                        AYMR.plurals.manga_series_num_titles,
                        count = series.entries.size,
                        series.entries.size,
                    ),
                    color = colors.textPrimary.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = null,
                    tint = colors.textPrimary.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = pluralStringResource(
                        AYMR.plurals.manga_series_num_chapters,
                        count = series.totalChapters.toInt(),
                        series.totalChapters.toInt(),
                    ),
                    color = colors.textPrimary.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
