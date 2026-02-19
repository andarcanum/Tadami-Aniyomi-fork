package eu.kanade.tachiyomi.ui.deeplink.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.online.ResolvableNovelSource
import eu.kanade.tachiyomi.novelsource.online.UriType
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DeepLinkNovelScreenModel(
    query: String = "",
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val getNovelByUrlAndSourceId: GetNovelByUrlAndSourceId = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val syncNovelChaptersWithSource: SyncNovelChaptersWithSource = Injekt.get(),
) : StateScreenModel<DeepLinkNovelScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            val source = sourceManager.getCatalogueSources()
                .filterIsInstance<ResolvableNovelSource>()
                .firstOrNull { it.getUriType(query) != UriType.Unknown }

            val novel = source?.getNovel(query)?.let {
                getNovelFromSNovel(it, source.id)
            }

            val chapter = if (source?.getUriType(query) == UriType.Chapter && novel != null) {
                source.getChapter(query)?.let { getChapterFromSNovelChapter(it, novel, source) }
            } else {
                null
            }

            mutableState.update {
                resolveDeepLinkNovelState(novel = novel, chapter = chapter)
            }
        }
    }

    private suspend fun getChapterFromSNovelChapter(
        sChapter: SNovelChapter,
        novel: Novel,
        source: NovelSource,
    ): NovelChapter? {
        val localChapter = novelChapterRepository.getChapterByUrlAndNovelId(sChapter.url, novel.id)

        return if (localChapter == null) {
            val sourceChapters = source.getChapterList(novel.toSNovel())
            val newChapters = syncNovelChaptersWithSource.await(sourceChapters, novel, source, false)
            newChapters.find { it.url == sChapter.url }
        } else {
            localChapter
        }
    }

    private suspend fun getNovelFromSNovel(sNovel: SNovel, sourceId: Long): Novel {
        return getNovelByUrlAndSourceId.await(sNovel.url, sourceId)
            ?: networkToLocalNovel.await(sNovel.toDomainNovel(sourceId))
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data object NoResults : State

        @Immutable
        data class Result(val novel: Novel, val chapterId: Long? = null) : State
    }
}

internal fun resolveDeepLinkNovelState(
    novel: Novel?,
    chapter: NovelChapter?,
): DeepLinkNovelScreenModel.State {
    if (novel == null) return DeepLinkNovelScreenModel.State.NoResults
    return if (chapter == null) {
        DeepLinkNovelScreenModel.State.Result(novel = novel)
    } else {
        DeepLinkNovelScreenModel.State.Result(novel = novel, chapterId = chapter.id)
    }
}
