package eu.kanade.tachiyomi.ui.history

import android.app.Application
import android.content.Intent
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.tadami.aurora.R
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.anime.animeHistoryTab
import eu.kanade.tachiyomi.ui.history.manga.MangaHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.manga.mangaHistoryTab
import eu.kanade.tachiyomi.ui.history.novel.novelHistoryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

data object HistoriesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            val index: UShort = when (currentNavigationStyle()) {
                NavStyle.MOVE_HISTORY_TO_MORE -> 5u
                NavStyle.MOVE_BROWSE_TO_MORE -> 3u
                else -> 2u
            }
            return TabOptions(
                index = index,
                title = stringResource(MR.strings.history),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        openLatestHistoryEntry(navigator)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val fromMore = currentNavigationStyle() == NavStyle.MOVE_HISTORY_TO_MORE
        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().collectAsStateWithLifecycle()

        val showAnimeSection by uiPreferences.showAnimeSection().collectAsStateWithLifecycle()
        val showMangaSection by uiPreferences.showMangaSection().collectAsStateWithLifecycle()
        val showNovelSection by uiPreferences.showNovelSection().collectAsStateWithLifecycle()

        // Hoisted for history tab's search bar
        val mangaHistoryScreenModel = rememberScreenModel { MangaHistoryScreenModel() }
        val mangaSearchQuery by mangaHistoryScreenModel.query.collectAsStateWithLifecycle()

        val animeHistoryScreenModel = rememberScreenModel { AnimeHistoryScreenModel() }
        val animeSearchQuery by animeHistoryScreenModel.query.collectAsStateWithLifecycle()

        val tabs = historyContentTabs(showAnimeSection, showMangaSection, showNovelSection)
            .map { tab ->
                when (tab) {
                    HistoryContentTab.ANIME -> animeHistoryTab(context, fromMore)
                    HistoryContentTab.MANGA -> mangaHistoryTab(context, fromMore)
                    HistoryContentTab.NOVEL -> novelHistoryTab(context, fromMore)
                }
            }
            .toPersistentList()

        if (theme.isAuroraStyle) {
            TabbedScreenAurora(
                titleRes = MR.strings.label_recent_manga,
                tabs = tabs,
                mangaSearchQuery = mangaSearchQuery,
                onChangeMangaSearchQuery = mangaHistoryScreenModel::search,
                animeSearchQuery = animeSearchQuery,
                onChangeAnimeSearchQuery = animeHistoryScreenModel::search,
                highlightSearchAction = false,
                extraSearchToActionsGap = 4.dp,
            )
        } else {
            TabbedScreen(
                titleRes = MR.strings.label_recent_manga,
                tabs = tabs,
                mangaSearchQuery = mangaSearchQuery,
                onChangeMangaSearchQuery = mangaHistoryScreenModel::search,
                animeSearchQuery = animeSearchQuery,
                onChangeAnimeSearchQuery = animeHistoryScreenModel::search,
                searchActionIconTint = MaterialTheme.colorScheme.onSurface,
                extraSearchToActionsGap = 4.dp,
            )
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

private const val TAB_ANIME = 0
private const val TAB_MANGA = 1

internal enum class HistoryContentTab {
    ANIME,
    MANGA,
    NOVEL,
}

internal fun historyContentTabs(
    showAnimeSection: Boolean,
    showMangaSection: Boolean,
    showNovelSection: Boolean,
): List<HistoryContentTab> {
    return buildList {
        if (showAnimeSection) add(HistoryContentTab.ANIME)
        if (showMangaSection) add(HistoryContentTab.MANGA)
        if (showNovelSection) add(HistoryContentTab.NOVEL)
    }
}

internal suspend fun resolveLatestHistoryContentTab(
    animeHistoryRepository: AnimeHistoryRepository = Injekt.get(),
    mangaHistoryRepository: MangaHistoryRepository = Injekt.get(),
    novelHistoryRepository: NovelHistoryRepository = Injekt.get(),
    uiPreferences: UiPreferences = Injekt.get(),
): HistoryContentTab? {
    val showAnimeSection = uiPreferences.showAnimeSection().get()
    val showMangaSection = uiPreferences.showMangaSection().get()
    val showNovelSection = uiPreferences.showNovelSection().get()

    return resolveLatestHistoryContentTab(
        animeLastSeenAt = animeHistoryRepository.getLastAnimeHistory()?.seenAt.takeIf { showAnimeSection },
        mangaLastReadAt = mangaHistoryRepository.getLastMangaHistory()?.readAt.takeIf { showMangaSection },
        novelLastReadAt = novelHistoryRepository.getLastNovelHistory()?.readAt.takeIf { showNovelSection },
    )
}

internal fun resolveLatestHistoryContentTab(
    animeLastSeenAt: Date?,
    mangaLastReadAt: Date?,
    novelLastReadAt: Date?,
): HistoryContentTab? {
    return listOfNotNull(
        animeLastSeenAt?.time?.let { it to HistoryContentTab.ANIME },
        mangaLastReadAt?.time?.let { it to HistoryContentTab.MANGA },
        novelLastReadAt?.time?.let { it to HistoryContentTab.NOVEL },
    ).maxByOrNull { it.first }?.second
}

private suspend fun openLatestHistoryEntry(navigator: Navigator) {
    val animeHistoryRepository = Injekt.get<AnimeHistoryRepository>()
    val mangaHistoryRepository = Injekt.get<MangaHistoryRepository>()
    val novelHistoryRepository = Injekt.get<NovelHistoryRepository>()
    val episodeRepository = Injekt.get<EpisodeRepository>()
    val chapterRepository = Injekt.get<ChapterRepository>()
    val playerPreferences = Injekt.get<PlayerPreferences>()
    val appContext = Injekt.get<Application>()

    val animeHistory = animeHistoryRepository.getLastAnimeHistory()
    val mangaHistory = mangaHistoryRepository.getLastMangaHistory()
    val novelHistory = novelHistoryRepository.getLastNovelHistory()

    val uiPreferences = Injekt.get<UiPreferences>()
    val showAnimeSection = uiPreferences.showAnimeSection().get()
    val showMangaSection = uiPreferences.showMangaSection().get()
    val showNovelSection = uiPreferences.showNovelSection().get()

    when (
        resolveLatestHistoryContentTab(
            animeLastSeenAt = animeHistory?.seenAt.takeIf { showAnimeSection },
            mangaLastReadAt = mangaHistory?.readAt.takeIf { showMangaSection },
            novelLastReadAt = novelHistory?.readAt.takeIf { showNovelSection },
        )
    ) {
        HistoryContentTab.ANIME -> {
            val episode = animeHistory?.let { episodeRepository.getEpisodeById(it.episodeId) } ?: return
            val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
            if (extPlayer) {
                MainActivity.startPlayerActivity(appContext, episode.animeId, episode.id, true)
            } else {
                appContext.startActivity(
                    PlayerActivity.newIntent(appContext, episode.animeId, episode.id)
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                )
            }
        }
        HistoryContentTab.MANGA -> {
            val chapter = mangaHistory?.let { chapterRepository.getChapterById(it.chapterId) } ?: return
            appContext.startActivity(
                ReaderActivity.newIntent(appContext, chapter.mangaId, chapter.id)
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
        }
        HistoryContentTab.NOVEL -> {
            val lastNovelHistory = novelHistory ?: return
            navigator.push(NovelReaderScreen(lastNovelHistory.chapterId))
        }
        null -> Unit
    }
}
