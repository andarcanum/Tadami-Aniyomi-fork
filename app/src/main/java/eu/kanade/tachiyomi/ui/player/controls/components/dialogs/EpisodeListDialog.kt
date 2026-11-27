package eu.kanade.tachiyomi.ui.player.controls.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.util.lang.toRelativeString
import `is`.xyz.mpv.Utils
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class EpisodeListFilter {
    All,
    Unseen,
    Bookmarked,
}

internal fun filterEpisodeList(
    episodes: List<Episode>,
    filter: EpisodeListFilter,
): List<Episode> {
    return when (filter) {
        EpisodeListFilter.All -> episodes
        EpisodeListFilter.Unseen -> episodes.filter { !it.seen }
        EpisodeListFilter.Bookmarked -> episodes.filter { it.bookmark }
    }
}

internal fun computeEpisodeProgressFraction(
    lastSecondSeenMs: Long,
    totalSecondsMs: Long,
    seen: Boolean,
): Float? {
    if (seen || lastSecondSeenMs <= 0L || totalSecondsMs <= 0L) return null
    return (lastSecondSeenMs.toFloat() / totalSecondsMs.toFloat()).coerceIn(0.02f, 0.99f)
}

@Composable
fun EpisodeListDialog(
    displayMode: Long?,
    currentEpisodeIndex: Int,
    episodeList: List<Episode>,
    dateRelativeTime: Boolean,
    dateFormat: DateTimeFormatter,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onFillermarkClicked: (Long?, Boolean) -> Unit,
    onEpisodeClicked: (Long?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val dialogLayoutSpec = resolveEpisodeListDialogLayoutSpec(LocalConfiguration.current.screenWidthDp)
    var activeFilter by remember { mutableStateOf(EpisodeListFilter.All) }
    val reversedEpisodes = remember(episodeList, activeFilter) {
        filterEpisodeList(episodeList, activeFilter).asReversed()
    }
    val safeScrollIndex = resolveEpisodeListScrollIndex(episodeList.size, currentEpisodeIndex)
    val currentEpisodeId = episodeList.getOrNull(currentEpisodeIndex)?.id
    val episodeListState = rememberLazyListState(initialFirstVisibleItemIndex = safeScrollIndex)

    PlayerDialog(
        title = stringResource(AYMR.strings.episodes),
        modifier = Modifier
            .fillMaxHeight(fraction = dialogLayoutSpec.heightFraction)
            .fillMaxWidth(fraction = dialogLayoutSpec.widthFraction)
            .widthIn(max = dialogLayoutSpec.maxWidthDp.dp),
        onDismissRequest = onDismissRequest,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.padding.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            EpisodeListFilter.entries.forEach { filter ->
                val labelRes = when (filter) {
                    EpisodeListFilter.All -> AYMR.strings.player_episode_filter_all
                    EpisodeListFilter.Unseen -> AYMR.strings.player_episode_filter_unseen
                    EpisodeListFilter.Bookmarked -> AYMR.strings.player_episode_filter_bookmarked
                }
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { activeFilter = filter },
                    label = { Text(text = stringResource(labelRes)) },
                )
            }
        }
        VerticalFastScroller(
            listState = episodeListState,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                state = episodeListState,
            ) {
                items(
                    items = reversedEpisodes,
                    key = { "episode-${it.id}" },
                    contentType = { "episode" },
                ) { episode ->

                    val isCurrentEpisode = currentEpisodeId != null && episode.id == currentEpisodeId

                    val title = if (displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                        stringResource(
                            AYMR.strings.display_mode_episode,
                            formatEpisodeNumber(episode.episode_number.toDouble()),
                        )
                    } else {
                        episode.name
                    }

                    val date = episode.date_upload
                        .takeIf { it > 0L }
                        ?.let {
                            LocalDate.ofInstant(
                                Instant.ofEpochMilli(it),
                                ZoneId.systemDefault(),
                            ).toRelativeString(
                                context = context,
                                relative = dateRelativeTime,
                                dateFormat = dateFormat,
                            )
                        } ?: ""

                    EpisodeListItem(
                        episode = episode,
                        isCurrentEpisode = isCurrentEpisode,
                        title = title,
                        date = date,
                        onBookmarkClicked = onBookmarkClicked,
                        onFillermarkClicked = onFillermarkClicked,
                        onEpisodeClicked = onEpisodeClicked,
                    )
                }
            }
        }
    }
}

internal fun resolveEpisodeListScrollIndex(
    episodeCount: Int,
    currentEpisodeIndex: Int,
): Int {
    if (episodeCount <= 0) return 0
    if (currentEpisodeIndex !in 0 until episodeCount) return 0
    return (episodeCount - 1 - currentEpisodeIndex).coerceIn(0, episodeCount - 1)
}

internal data class EpisodeListDialogLayoutSpec(
    val widthFraction: Float,
    val heightFraction: Float,
    val maxWidthDp: Int,
)

internal fun resolveEpisodeListDialogLayoutSpec(screenWidthDp: Int): EpisodeListDialogLayoutSpec {
    return when {
        screenWidthDp >= 960 -> EpisodeListDialogLayoutSpec(
            widthFraction = 0.75f,
            heightFraction = 0.8f,
            maxWidthDp = 960,
        )
        screenWidthDp >= 600 -> EpisodeListDialogLayoutSpec(
            widthFraction = 0.85f,
            heightFraction = 0.8f,
            maxWidthDp = 760,
        )
        else -> EpisodeListDialogLayoutSpec(
            widthFraction = 0.9f,
            heightFraction = 0.85f,
            maxWidthDp = 560,
        )
    }
}

@Composable
private fun EpisodeListItem(
    episode: Episode,
    isCurrentEpisode: Boolean,
    title: String,
    date: String?,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onFillermarkClicked: (Long?, Boolean) -> Unit,
    onEpisodeClicked: (Long?) -> Unit,
) {
    var isBookmarked by remember(episode.id, episode.bookmark) { mutableStateOf(episode.bookmark) }
    var isFillermarked by remember(episode.id, episode.fillermark) { mutableStateOf(episode.fillermark) }
    var textHeight by remember { mutableStateOf(0) }

    val accent = AuroraTheme.colors.accent
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val bookmarkAlpha = if (isBookmarked) 1f else DISABLED_ALPHA
    val bookmarkColor = if (isBookmarked) accent else defaultColor
    val fillermarkAlpha = if (isFillermarked) 1f else DISABLED_ALPHA
    val fillermarkColor = if (isFillermarked) MaterialTheme.colorScheme.tertiary else defaultColor
    val episodeColor = when {
        isCurrentEpisode -> accent
        isBookmarked -> bookmarkColor
        isFillermarked -> fillermarkColor
        else -> defaultColor
    }
    val textAlpha = if (episode.seen && !isCurrentEpisode) DISABLED_ALPHA else 1f
    val textWeight = if (isCurrentEpisode) FontWeight.SemiBold else FontWeight.Normal
    val progressFraction = computeEpisodeProgressFraction(
        lastSecondSeenMs = episode.last_second_seen,
        totalSecondsMs = episode.total_seconds,
        seen = episode.seen,
    )

    val clickBookmark: (Boolean) -> Unit = { bookmarked ->
        episode.bookmark = bookmarked
        isBookmarked = bookmarked
        onBookmarkClicked(episode.id, bookmarked)
    }

    val clickFillermark: (Boolean) -> Unit = { fillermarked ->
        episode.fillermark = fillermarked
        isFillermarked = fillermarked
        onFillermarkClicked(episode.id, fillermarked)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentEpisode) accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = { onEpisodeClicked(episode.id) })
            .padding(vertical = MaterialTheme.padding.extraSmall, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { clickBookmark(!isBookmarked) }) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = bookmarkColor,
                modifier = Modifier
                    .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp })
                    .alpha(bookmarkAlpha),
            )
        }

        IconButton(onClick = { clickFillermark(!isFillermarked) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Label,
                contentDescription = null,
                tint = fillermarkColor,
                modifier = Modifier
                    .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp })
                    .alpha(fillermarkAlpha),
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = episodeColor,
                modifier = Modifier.alpha(textAlpha),
                onTextLayout = { textHeight = it.size.height },
                fontWeight = textWeight,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!date.isNullOrEmpty()) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = episodeColor,
                        modifier = Modifier.alpha(textAlpha),
                        fontWeight = textWeight,
                    )
                    if (episode.scanlator != null || progressFraction != null) {
                        DotSeparatorText(
                            modifier = Modifier.alpha(textAlpha),
                        )
                    }
                }
                if (episode.scanlator != null) {
                    Text(
                        text = episode.scanlator!!,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = episodeColor,
                        modifier = Modifier.alpha(textAlpha),
                        fontWeight = textWeight,
                    )
                    if (progressFraction != null) {
                        DotSeparatorText(
                            modifier = Modifier.alpha(textAlpha),
                        )
                    }
                }
                if (progressFraction != null) {
                    val watchedSec = (episode.last_second_seen / 1000L).toInt()
                    val totalSec = (episode.total_seconds / 1000L).toInt()
                    Text(
                        text = "${Utils.prettyTime(watchedSec)} / ${Utils.prettyTime(totalSec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        maxLines = 1,
                    )
                }
            }

            if (progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(3.dp)
                        .clip(CircleShape),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                )
            }
        }
    }
}
