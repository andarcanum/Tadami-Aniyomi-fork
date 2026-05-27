package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import aniyomi.domain.anime.SeasonAnime

data class AnimeSeasonSwitcherItem(
    val animeId: Long,
    val label: String,
    val selected: Boolean,
    val seasonAnime: SeasonAnime,
)

fun resolveAnimeSeasonSwitcherItems(
    currentAnimeId: Long?,
    seasons: List<SeasonAnime>,
): List<AnimeSeasonSwitcherItem> {
    val sorted = seasons.sortedBy { it.anime.seasonNumber }
    return sorted.map { season ->
        AnimeSeasonSwitcherItem(
            animeId = season.anime.id,
            label = buildSeasonChipLabel(season),
            selected = season.anime.id == currentAnimeId,
            seasonAnime = season,
        )
    }
}

private fun buildSeasonChipLabel(season: SeasonAnime): String {
    val sn = season.anime.seasonNumber
    return if (sn > 0.0) {
        "S${sn.toInt()}"
    } else {
        "S1"
    }
}

@Composable
fun AnimeSeasonSwitcher(
    items: List<AnimeSeasonSwitcherItem>,
    onSeasonClicked: (SeasonAnime) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.size <= 1) return

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item.selected,
                onClick = { onSeasonClicked(item.seasonAnime) },
                label = { Text(item.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}
