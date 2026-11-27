package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.discovery.GenreMatcher
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

/**
 * Извлекает список доступных жанров из элементов ленты, отсортированный по частоте.
 */
internal fun extractAvailableGenres(
    items: List<DiscoverySuggestion>,
    maxGenres: Int = 12,
): List<String> {
    if (items.isEmpty()) return emptyList()
    val counts = LinkedHashMap<String, Int>()
    for (item in items) {
        val reason = item.reason
        val genres = if (item.genres.isNotEmpty()) {
            item.genres
        } else if (item.rowType == DiscoveryRowType.TASTE && !reason.isNullOrBlank()) {
            reason.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        for (genre in genres) {
            val formatted = genre.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            if (formatted.isNotEmpty()) {
                counts[formatted] = (counts[formatted] ?: 0) + 1
            }
        }
    }
    return counts.entries
        .sortedByDescending { it.value }
        .map { it.key }
        .distinctBy { it.lowercase() }
        .take(maxGenres)
}

/**
 * Фильтрует рекомендации по выбранному пользователем жанру с учётом RU/EN алиасов.
 */
internal fun filterDiscoveryFeed(
    items: List<DiscoverySuggestion>,
    selectedGenre: String?,
): List<DiscoverySuggestion> {
    if (selectedGenre.isNullOrBlank()) return items
    val wanted = selectedGenre.trim()
    return items.filter { item ->
        val reason = item.reason
        val genres = if (item.genres.isNotEmpty()) {
            item.genres
        } else if (item.rowType == DiscoveryRowType.TASTE && !reason.isNullOrBlank()) {
            reason.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        genres.any { GenreMatcher.matches(wanted, it) }
    }
}

/**
 * Интерактивная горизонтальная панель жанровых чипов с Aurora-стилизацией и тактильным откликом.
 */
@Composable
internal fun DiscoveryGenreChipBar(
    genres: List<String>,
    selectedGenre: String?,
    onGenreSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (genres.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            val isSelected = selectedGenre == null
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            colors.accent
                        } else if (colors.isDark) {
                            colors.glass.copy(alpha = 0.12f)
                        } else {
                            colors.cardBackground
                        },
                    )
                    .then(
                        if (!isSelected && (colors.isDark || colors.isEInk)) {
                            Modifier.border(1.dp, colors.divider.copy(alpha = 0.5f), CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable {
                        appHaptics.tap()
                        onGenreSelect(null)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(AYMR.strings.for_you_filter_all),
                    color = if (isSelected) colors.textOnAccent else colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }

        items(items = genres, key = { it }) { genre ->
            val isSelected = selectedGenre.equals(genre, ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            colors.accent
                        } else if (colors.isDark) {
                            colors.glass.copy(alpha = 0.12f)
                        } else {
                            colors.cardBackground
                        },
                    )
                    .then(
                        if (!isSelected && (colors.isDark || colors.isEInk)) {
                            Modifier.border(1.dp, colors.divider.copy(alpha = 0.5f), CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable {
                        appHaptics.tap()
                        onGenreSelect(if (isSelected) null else genre)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = genre,
                    color = if (isSelected) colors.textOnAccent else colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
