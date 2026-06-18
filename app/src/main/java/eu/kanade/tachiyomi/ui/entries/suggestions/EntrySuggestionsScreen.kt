package eu.kanade.tachiyomi.ui.entries.suggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionSourceWeight
import eu.kanade.tachiyomi.data.suggestions.SuggestionState
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.anime.AnimeFallbackOutcome
import eu.kanade.tachiyomi.data.suggestions.anime.AnimeSearchFallbackEngine
import eu.kanade.tachiyomi.data.suggestions.manga.MangaFallbackOutcome
import eu.kanade.tachiyomi.data.suggestions.manga.MangaSearchFallbackEngine
import eu.kanade.tachiyomi.data.suggestions.novel.NovelFallbackOutcome
import eu.kanade.tachiyomi.data.suggestions.novel.NovelRelatedSuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.novel.NovelSearchFallbackEngine
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.bestMatchScoreFor
import eu.kanade.tachiyomi.data.suggestions.util.dedupeByCleanTitle
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable

class EntrySuggestionsScreen(
    val seed: SuggestionSeed,
    val sourceId: Long? = null,
    val entryUrl: String? = null,
) : Screen(), Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        val screenModel = rememberScreenModel {
            EntrySuggestionsScreenModel(
                seed = seed,
                sourceId = sourceId,
                entryUrl = entryUrl,
            )
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        EntrySuggestionsContent(
            state = state,
            primaryTitle = seed.primaryTitle,
            navigateUp = navigator::pop,
            onSuggestionClick = { item ->
                scope.launch {
                    navigator.push(item.toDirectEntryScreenOrNull() ?: item.toGlobalSearchScreen())
                }
            },
            onRetryClick = screenModel::fetchSuggestions,
            onShowMoreClick = screenModel::showMore,
        )
    }
}

class EntrySuggestionsScreenModel(
    private val seed: SuggestionSeed,
    private val sourceId: Long?,
    private val entryUrl: String?,
    private val coordinator: SuggestionCoordinator = Injekt.get(),
    private val novelRelatedCoordinator: NovelRelatedSuggestionCoordinator = Injekt.get(),
    private val novelSearchFallbackEngine: NovelSearchFallbackEngine = Injekt.get(),
    private val mangaSearchFallbackEngine: MangaSearchFallbackEngine = Injekt.get(),
    private val animeSearchFallbackEngine: AnimeSearchFallbackEngine = Injekt.get(),
) : StateScreenModel<SuggestionState>(SuggestionState.Loading) {

    private var allItems: List<SuggestionItem> = emptyList()
    private var showAll: Boolean = false

    private companion object {
        const val INITIAL_LIMIT = 24
        const val FULL_SCREEN_LIMIT = 80
        const val PROVIDER_LIMIT = 40
    }

    init {
        fetchSuggestions()
    }

    fun showMore() {
        showAll = true
        publishItems()
    }

    private fun publishItems() {
        val visibleItems = if (showAll) allItems else allItems.take(INITIAL_LIMIT)
        mutableState.value = when {
            allItems.isEmpty() -> SuggestionState.Empty()
            else -> SuggestionState.Success(
                items = visibleItems,
                hasMore = !showAll && allItems.size > INITIAL_LIMIT,
            )
        }
    }

    private fun mergeAndPublish(seed: SuggestionSeed, primaryTitle: String, incoming: List<SuggestionItem>) {
        allItems = (allItems + incoming)
            .dedupeByCleanTitle(seed)
            .filter { item ->
                val isSelf = SuggestionTitleResolver.isSameProviderEntry(item, entryUrl)
                val isFranchise = SuggestionTitleResolver.isFranchiseDuplicate(item.title, primaryTitle)
                !isSelf && !isFranchise
            }
            .sortedByDescending { SuggestionSourceWeight.finalScore(it.reason, it.bestMatchScoreFor(seed)) }
            .take(FULL_SCREEN_LIMIT)
        publishItems()
    }

    fun fetchSuggestions() {
        screenModelScope.launchIO {
            mutableState.value = SuggestionState.Loading
            try {
                val primaryTitle = seed.primaryTitle

                allItems = emptyList()
                showAll = false

                // External and native fetch in parallel, but publish each result as soon as it arrives.
                coroutineScope {
                    val externalDeferred = async(Dispatchers.IO) {
                        coordinator.fetchSuggestions(seed, limit = PROVIDER_LIMIT).items
                    }
                    val nativeDeferred = async(Dispatchers.IO) {
                        fetchNativeFallback(PROVIDER_LIMIT)
                    }

                    try {
                        val externalItems = externalDeferred.await()
                        if (externalItems.isNotEmpty()) {
                            mergeAndPublish(seed, primaryTitle, externalItems)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Keep the screen usable when one provider group fails.
                    }

                    try {
                        val pluginItems = nativeDeferred.await()
                        if (pluginItems.isNotEmpty()) {
                            mergeAndPublish(seed, primaryTitle, pluginItems)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Keep partial external results visible.
                    }
                }

                if (allItems.isEmpty()) {
                    mutableState.value = SuggestionState.Empty()
                } else {
                    publishItems()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = SuggestionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun fetchNativeFallback(maxResults: Int): List<SuggestionItem> {
        if (sourceId == null || entryUrl == null) return emptyList()
        return when (seed.mediaType) {
            SuggestionMediaType.NOVEL -> {
                val sourceManager = Injekt.get<NovelSourceManager>()
                val source = sourceManager.getOrStub(sourceId) as? NovelCatalogueSource
                    ?: return emptyList()
                val dummyNovel = tachiyomi.domain.entries.novel.model.Novel.create().copy(
                    url = entryUrl,
                    title = seed.primaryTitle,
                    author = seed.author,
                    genre = seed.genres,
                )
                val relatedOutcome = novelRelatedCoordinator.fetchRelatedSuggestions(
                    novel = dummyNovel,
                    source = source,
                    seed = seed,
                    maxResults = maxResults,
                )
                val relatedList = if (relatedOutcome is NovelFallbackOutcome.Success) {
                    relatedOutcome.items
                } else {
                    emptyList()
                }
                val searchOutcome = novelSearchFallbackEngine.fetchSearchFallback(
                    novel = dummyNovel,
                    source = source,
                    seed = seed,
                    maxResults = maxResults,
                )
                val searchList = if (searchOutcome is NovelFallbackOutcome.Success) {
                    searchOutcome.items
                } else {
                    emptyList()
                }
                (relatedList + searchList).dedupeByCleanTitle()
            }
            SuggestionMediaType.MANGA -> {
                val sourceManager = Injekt.get<MangaSourceManager>()
                val source = sourceManager.getOrStub(sourceId) as? CatalogueSource
                    ?: return emptyList()
                val dummyManga = tachiyomi.domain.entries.manga.model.Manga.create().copy(
                    url = entryUrl,
                    title = seed.primaryTitle,
                    author = seed.author,
                    genre = seed.genres,
                )
                val searchOutcome = mangaSearchFallbackEngine.fetchSearchFallback(
                    manga = dummyManga,
                    source = source,
                    seed = seed,
                    maxResults = maxResults,
                )
                if (searchOutcome is MangaFallbackOutcome.Success) {
                    searchOutcome.items
                } else {
                    emptyList()
                }
            }
            SuggestionMediaType.ANIME -> {
                val sourceManager = Injekt.get<AnimeSourceManager>()
                val source = sourceManager.getOrStub(sourceId) as? AnimeCatalogueSource
                    ?: return emptyList()
                val dummyAnime = tachiyomi.domain.entries.anime.model.Anime.create().copy(
                    url = entryUrl,
                    title = seed.primaryTitle,
                    author = seed.author,
                    genre = seed.genres,
                )
                val searchOutcome = animeSearchFallbackEngine.fetchSearchFallback(
                    anime = dummyAnime,
                    source = source,
                    seed = seed,
                    maxResults = maxResults,
                )
                if (searchOutcome is AnimeFallbackOutcome.Success) {
                    searchOutcome.items
                } else {
                    emptyList()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySuggestionsContent(
    state: SuggestionState,
    primaryTitle: String,
    navigateUp: () -> Unit,
    onSuggestionClick: (SuggestionItem) -> Unit,
    onRetryClick: () -> Unit,
    onShowMoreClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(MR.strings.suggestions_similar_titles),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = primaryTitle,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                ),
            )
        },
        containerColor = Color(0xFF121212),
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when (state) {
                is SuggestionState.Loading -> {
                    EntrySuggestionsSkeletonGrid()
                }
                is SuggestionState.Success -> {
                    val groups = state.items.groupForFullScreen(
                        topPicksTitle = stringResource(MR.strings.suggestions_group_top_picks),
                        databasesTitle = stringResource(MR.strings.suggestions_group_recommendation_databases),
                        currentSourceTitle = stringResource(MR.strings.suggestions_group_from_this_source),
                        discoveryTitle = stringResource(MR.strings.suggestions_group_more_discoveries),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        groups.forEach { group ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "section_${group.title}") {
                                EntrySuggestionsSectionHeader(group.title)
                            }
                            items(group.items, key = { it.providerId ?: it.providerUrl }) { item ->
                                EntrySuggestionGridCard(
                                    item = item,
                                    onClick = { onSuggestionClick(item) },
                                )
                            }
                        }

                        if (state.hasMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(MR.strings.action_show_more),
                                        color = Color(0xFF64B5F6),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.White.copy(alpha = 0.1f))
                                            .clickable(onClick = onShowMoreClick)
                                            .padding(horizontal = 24.dp, vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                is SuggestionState.Empty -> {
                    // Explicit empty state – feature is on but provider returned nothing
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.message ?: stringResource(MR.strings.suggestions_empty_state),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                is SuggestionState.Disabled -> {
                    // Feature is explicitly disabled in settings
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(MR.strings.suggestions_disabled_state),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                is SuggestionState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.message,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(MR.strings.action_retry),
                            color = Color(0xFF64B5F6),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onRetryClick)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(Color.White.copy(alpha = 0.1f)),
                        )
                    }
                }
                else -> Unit // Idle state – nothing to show
            }
        }
    }
}

private data class SuggestionUiGroup(
    val title: String,
    val items: List<SuggestionItem>,
)

private fun List<SuggestionItem>.groupForFullScreen(
    topPicksTitle: String,
    databasesTitle: String,
    currentSourceTitle: String,
    discoveryTitle: String,
): List<SuggestionUiGroup> {
    if (isEmpty()) return emptyList()
    val topPicks = take(8)
    val topIds = topPicks.map { it.providerId ?: it.providerUrl }.toSet()

    val databases = filter {
        (it.providerId ?: it.providerUrl) !in topIds &&
            it.reason in setOf(
                eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_ANILIST,
                eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_MAL,
                eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_MU,
                eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_NU,
            )
    }
    val databaseIds = databases.map { it.providerId ?: it.providerUrl }.toSet()

    val currentSource = filter {
        val id = it.providerId ?: it.providerUrl
        id !in topIds &&
            id !in databaseIds &&
            it.reason in setOf(
                eu.kanade.tachiyomi.data.suggestions.SuggestionReason.RELATED,
                eu.kanade.tachiyomi.data.suggestions.SuggestionReason.SEARCH_TITLE,
                eu.kanade.tachiyomi.data.suggestions.SuggestionReason.SEARCH_AUTHOR,
            )
    }
    val sourceIds = currentSource.map { it.providerId ?: it.providerUrl }.toSet()

    val discovery = filter {
        val id = it.providerId ?: it.providerUrl
        id !in topIds && id !in databaseIds && id !in sourceIds
    }

    return buildList {
        if (topPicks.isNotEmpty()) add(SuggestionUiGroup(topPicksTitle, topPicks))
        if (databases.isNotEmpty()) add(SuggestionUiGroup(databasesTitle, databases))
        if (currentSource.isNotEmpty()) add(SuggestionUiGroup(currentSourceTitle, currentSource))
        if (discovery.isNotEmpty()) add(SuggestionUiGroup(discoveryTitle, discovery))
    }
}

@Composable
private fun EntrySuggestionsSectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun EntrySuggestionGridCard(
    item: SuggestionItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(154.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            AsyncImage(
                model = getCoverModel(item),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.82f),
                            ),
                            startY = 55f,
                        ),
                    ),
            )
            Text(
                text = item.providerBadgeLabel(),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(
                text = item.reasonBadgeLabel(),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                    .background(item.reasonAccentColor().copy(alpha = 0.74f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun EntrySuggestionsSkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(12) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            )
        }
    }
}

private fun SuggestionItem.providerBadgeLabel(): String = when (providerName) {
    "MyAnimeList" -> "MAL"
    else -> providerName
}

private fun SuggestionItem.reasonBadgeLabel(): String = when (reason) {
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.RELATED -> "Related"
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_ANILIST,
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_MAL,
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_MU,
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_NU,
    -> "Recommended"
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.SEARCH_TITLE -> "Title match"
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.SEARCH_AUTHOR -> "Same author"
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.SEARCH_GENRE -> "Similar genre"
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.POPULAR_BACKFILL -> "Discovery"
}

private fun SuggestionItem.reasonAccentColor(): Color = when (reason) {
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.RELATED -> Color(0xFF7E57C2)
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_ANILIST,
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_MAL,
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_MU,
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.EXTERNAL_NU,
    -> Color(0xFF1976D2)
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.SEARCH_TITLE -> Color(0xFF00897B)
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.SEARCH_AUTHOR -> Color(0xFF5E35B1)
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.SEARCH_GENRE -> Color(0xFFF57C00)
    eu.kanade.tachiyomi.data.suggestions.SuggestionReason.POPULAR_BACKFILL -> Color(0xFF546E7A)
}

private fun getCoverModel(item: SuggestionItem): Any? {
    val url = item.thumbnailUrl ?: return null
    if (item.mediaType != eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL) return url

    if (eu.kanade.tachiyomi.source.novel.NovelPluginImage.isSupported(url)) {
        return eu.kanade.tachiyomi.source.novel.NovelPluginImage(url)
    }

    val sourceId = item.providerId?.substringBefore(":")?.toLongOrNull() ?: -1L
    return tachiyomi.domain.entries.novel.model.NovelCover(
        novelId = -1L,
        sourceId = sourceId,
        isNovelFavorite = false,
        url = url,
        lastModified = 0L,
    )
}
