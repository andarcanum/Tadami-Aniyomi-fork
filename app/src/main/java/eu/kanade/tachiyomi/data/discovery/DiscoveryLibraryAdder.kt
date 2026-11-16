package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Adds a source-bound recommendation by its exact identity, never by a provider display name. */
class DiscoveryLibraryAdder(
    private val sourcePolicyProvider: () -> DiscoverySourcePolicyProvider = { Injekt.get() },
) {

    companion object {
        const val SOURCE_TIMEOUT_MS = 8_000L
    }

    suspend fun add(item: DiscoverySuggestion): Boolean = withContext(Dispatchers.IO) {
        val sourceId = item.sourceId?.takeIf { it > 0L } ?: return@withContext false
        val sourceUrl = item.sourceUrl?.takeIf { it.isNotBlank() } ?: return@withContext false
        try {
            if (!isAllowed(item)) return@withContext false
            when (item.mediaType) {
                DiscoveryMediaType.MANGA -> addManga(item, sourceId, sourceUrl)
                DiscoveryMediaType.ANIME -> addAnime(item, sourceId, sourceUrl)
                DiscoveryMediaType.NOVEL -> addNovel(item, sourceId, sourceUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[DiscoveryAdd] FAILED '${item.title}' from source=$sourceId: ${e.message}" }
            false
        }
    }

    /** Unbound legacy callers retain their explicit Find fallback instead of guessing ownership. */
    @Deprecated("Use add(item) with its exact native source binding")
    @Suppress("UNUSED_PARAMETER")
    suspend fun addFromProvider(mediaType: DiscoveryMediaType, title: String, provider: String?): Boolean = false

    private fun isAllowed(item: DiscoverySuggestion): Boolean =
        sourcePolicyProvider().current(item.mediaType).allows(item)

    private fun bindingAllowed(item: DiscoverySuggestion, source: Any): Boolean =
        (sourcePolicyProvider() as? DiscoverySdkBindingAuthority)?.isCurrentSourceBinding(
            item.mediaType,
            item.sourceId ?: -1L,
            source,
        ) ==
            true

    private suspend fun addManga(item: DiscoverySuggestion, sourceId: Long, sourceUrl: String): Boolean {
        val source = Injekt.get<MangaSourceManager>().get(sourceId) as? CatalogueSource ?: return false
        if (!bindingAllowed(item, source)) return false
        val manga = SManga.create().apply {
            url = sourceUrl
            title = item.title
            thumbnail_url = item.coverUrl
        }.toDomainManga(sourceId).copy(genre = item.genres.takeIf { it.isNotEmpty() }?.toList())
        val materializer = Injekt.get<NetworkToLocalManga>()
        if (!isAllowed(item) || !bindingAllowed(item, source)) return false
        return materializer.await(listOf(manga), autoFavorite = true).isNotEmpty()
    }

    private suspend fun addAnime(item: DiscoverySuggestion, sourceId: Long, sourceUrl: String): Boolean {
        val source = Injekt.get<AnimeSourceManager>().get(sourceId) as? AnimeCatalogueSource ?: return false
        if (!bindingAllowed(item, source)) return false
        val anime = SAnime.create().apply {
            url = sourceUrl
            title = item.title
            thumbnail_url = item.coverUrl
        }.toDomainAnime(sourceId).copy(genre = item.genres.takeIf { it.isNotEmpty() }?.toList())
        val materializer = Injekt.get<NetworkToLocalAnime>()
        if (!isAllowed(item) || !bindingAllowed(item, source)) return false
        return materializer.await(listOf(anime), autoFavorite = true).isNotEmpty()
    }

    private suspend fun addNovel(item: DiscoverySuggestion, sourceId: Long, sourceUrl: String): Boolean {
        val source = Injekt.get<NovelSourceManager>().get(sourceId) as? NovelCatalogueSource ?: return false
        if (!bindingAllowed(item, source)) return false
        val novel = SNovel.create().apply {
            url = sourceUrl
            title = item.title
            thumbnail_url = item.coverUrl
        }.toDomainNovel(sourceId).copy(genre = item.genres.takeIf { it.isNotEmpty() }?.toList())
        val materializer = Injekt.get<NetworkToLocalNovel>()
        if (!isAllowed(item) || !bindingAllowed(item, source)) return false
        return materializer.await(listOf(novel), autoFavorite = true).isNotEmpty()
    }
}
