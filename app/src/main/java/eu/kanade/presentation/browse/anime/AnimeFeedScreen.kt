package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.entries.components.AuroraEntryHoldToRefresh
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.anime.feed.AnimeFeedItemUI
import eu.kanade.tachiyomi.ui.browse.anime.feed.AnimeFeedScreenState
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AnimeFeedScreen(
    state: AnimeFeedScreenState,
    contentPadding: PaddingValues,
    onClickSource: (AnimeCatalogueSource, AnimeFeedItemUI) -> Unit,
    onClickAnime: (Anime) -> Unit,
    getAnimeState: @Composable (Anime) -> State<Anime>,
    onRefresh: () -> Unit,
) {
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()

    AuroraEntryHoldToRefresh(
        refreshing = state.isLoadingItems,
        onRefresh = onRefresh,
        enabled = !state.isLoading,
    ) {
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(MR.strings.loading))
                }
            }
            state.isEmpty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.feed_empty),
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .auroraCenteredMaxWidth(
                            auroraAdaptiveSpec.updatesMaxWidthDp
                                ?: auroraAdaptiveSpec.entryMaxWidthDp,
                        ),
                    contentPadding = contentPadding,
                ) {
                    state.items?.forEach { item ->
                        item(key = item.source.id) {
                            FeedSourceSection(
                                item = item,
                                getAnimeState = getAnimeState,
                                onClickSource = { onClickSource(item.source, item) },
                                onClickAnime = onClickAnime,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedSourceSection(
    item: AnimeFeedItemUI,
    getAnimeState: @Composable (Anime) -> State<Anime>,
    onClickSource: () -> Unit,
    onClickAnime: (Anime) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClickSource)
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.extraSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AuroraTheme.colors.accent,
                )
                Text(
                    text = LocaleHelper.getLocalizedDisplayName(item.source.lang),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onClickSource) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                )
            }
        }

        when {
            item.results == null -> {
                Text(
                    text = stringResource(MR.strings.loading),
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                )
            }
            item.results.isEmpty() -> {
                Text(
                    text = stringResource(MR.strings.no_results_found),
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                )
            }
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    items(item.results) { anime ->
                        val title by getAnimeState(anime)
                        Box(modifier = Modifier.width(96.dp)) {
                            EntryComfortableGridItem(
                                title = title.title,
                                titleMaxLines = 3,
                                coverData = title.asAnimeCover(),
                                coverBadgeStart = {
                                    InLibraryBadge(enabled = title.favorite)
                                },
                                coverAlpha = if (title.favorite) {
                                    CommonEntryItemDefaults.BrowseFavoriteCoverAlpha
                                } else {
                                    1f
                                },
                                onClick = { onClickAnime(title) },
                                onLongClick = { onClickAnime(title) },
                            )
                        }
                    }
                }
            }
        }
    }
}
