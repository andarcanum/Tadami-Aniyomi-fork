package eu.kanade.tachiyomi.ui.deeplink.manga

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.ResolvableSource
import eu.kanade.tachiyomi.source.online.UriType
import eu.kanade.tachiyomi.util.manga.fetchMangaChaptersForUpdate
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DeepLinkMangaScreenModel(
    query: String = "",
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getChapterByUrlAndMangaId: GetChapterByUrlAndMangaId = Injekt.get(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
) : StateScreenModel<DeepLinkMangaScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            val source = sourceManager.getCatalogueSources()
                .filterIsInstance<ResolvableSource>()
                .firstOrNull { it.getUriType(query) != UriType.Unknown }

            val manga = source?.getManga(query)?.let {
                getMangaFromSManga(it, source.id)
            } ?: resolveThroughSearch(query)

            val chapter = if (source?.getUriType(query) == UriType.Chapter && manga != null) {
                source.getChapter(query)?.let { getChapterFromSChapter(it, manga, source) }
            } else {
                null
            }

            mutableState.update {
                if (manga == null) {
                    State.NoResults
                } else {
                    if (chapter == null) {
                        State.Result(manga)
                    } else {
                        State.Result(manga, chapter.id)
                    }
                }
            }
        }
    }

    /**
     * Resolves a url through the regular search entry point.
     *
     * Sources built on the 1.6 base class do not implement [ResolvableSource]; instead their
     * getSearchManga hands a url-shaped query to getMangaByUrl, so passing the link as the query is
     * how a deep link reaches them. Only sources whose base url matches the link's host are tried.
     */
    private suspend fun resolveThroughSearch(query: String): Manga? {
        val url = query.toHttpUrlOrNull() ?: return null
        val candidates = sourceManager.getCatalogueSources()
            .filterIsInstance<HttpSource>()
            .filter { it.baseUrl.toHttpUrlOrNull()?.host == url.host }

        for (candidate in candidates) {
            val sManga = runCatching {
                candidate.getSearchManga(page = 1, query = query, filters = FilterList()).mangas.firstOrNull()
            }.getOrNull() ?: continue
            return getMangaFromSManga(sManga, candidate.id)
        }
        return null
    }

    private suspend fun getChapterFromSChapter(sChapter: SChapter, manga: Manga, source: MangaSource): Chapter? {
        val localChapter = getChapterByUrlAndMangaId.await(sChapter.url, manga.id)

        return if (localChapter == null) {
            val sourceChapters = source.fetchMangaChaptersForUpdate(
                manga = manga.toSManga(),
            )
            val newChapters = syncChaptersWithSource.await(sourceChapters, manga, source, false)
            newChapters.find { it.url == sChapter.url }
        } else {
            localChapter
        }
    }

    private suspend fun getMangaFromSManga(sManga: SManga, sourceId: Long): Manga {
        return getMangaByUrlAndSourceId.await(sManga.url, sourceId)
            ?: networkToLocalManga.await(sManga.toDomainManga(sourceId))
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data object NoResults : State

        @Immutable
        data class Result(val manga: Manga, val chapterId: Long? = null) : State
    }
}
