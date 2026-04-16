package eu.kanade.presentation.series.novel.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.library.novel.components.SeriesStackedCoverCard
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.presentation.core.util.plus

@Composable
fun NovelSeriesHeader(
    series: LibraryNovelSeries,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Stacked Covers
        Box(
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(0.7f), // Typical book ratio
            contentAlignment = Alignment.Center,
        ) {
            SeriesStackedCoverCard(
                covers = series.coverNovels.map { it.asNovelCover() },
                isSelected = false,
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = series.title,
            color = colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Stats
        Text(
            text = "${series.entries.size} titles • ${series.totalChapters} chapters",
            color = colors.textPrimary.copy(alpha = 0.7f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
