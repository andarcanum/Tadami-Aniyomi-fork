package eu.kanade.tachiyomi.data.shikimori

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMEntry
import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.shikimori.ShikimoriImportEntry
import tachiyomi.data.shikimori.ShikimoriImportMediaType
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class FetchShikimoriImportEntries(
    private val trackerManager: TrackerManager,
    private val rateLimiter: ShikimoriApiRateLimiter = ShikimoriApiRateLimiter(),
) {

    class NotLoggedInException : Exception()

    class NetworkException(cause: Throwable? = null) : Exception(cause)

    class RateLimitedException : Exception()

    suspend fun await(mediaType: ShikimoriImportMediaType): List<ShikimoriImportEntry> {
        val shikimori = trackerManager.shikimori
        if (!shikimori.isLoggedIn) throw NotLoggedInException()

        return try {
            val userId = shikimori.api.getCurrentUser()
            when (mediaType) {
                ShikimoriImportMediaType.ANIME -> fetchAnime(shikimori.api, userId)
                ShikimoriImportMediaType.MANGA -> fetchManga(shikimori.api, userId, ranobeOnly = false)
                ShikimoriImportMediaType.RANOBE -> fetchManga(shikimori.api, userId, ranobeOnly = true)
            }
        } catch (e: NotLoggedInException) {
            throw e
        } catch (e: RateLimitedException) {
            throw e
        } catch (e: NetworkException) {
            throw e
        } catch (e: HttpException) {
            if (e.code == 429) throw RateLimitedException()
            throw NetworkException(e)
        } catch (e: IOException) {
            throw NetworkException(e)
        } catch (e: Exception) {
            throw NetworkException(e)
        }
    }

    private suspend fun fetchAnime(api: ShikimoriApi, userId: Int): List<ShikimoriImportEntry> {
        val rates = api.getAllUserAnimeRates(userId)
        val animeById = rates
            .map { it.targetId }
            .distinct()
            .chunked(BULK_CHUNK_SIZE)
            .flatMap { chunk -> rateLimiter.withRateLimit { api.getAnimesByIds(chunk) } }
            .associateBy { it.id }

        return rates.mapNotNull { rate ->
            val anime = animeById[rate.targetId] ?: return@mapNotNull null
            ShikimoriImportEntry(
                mediaType = ShikimoriImportMediaType.ANIME,
                rateId = rate.id,
                remoteId = rate.targetId,
                name = anime.name,
                russian = anime.russian,
                status = rate.status,
                score = rate.score,
                progress = rate.episodes,
                totalCount = anime.episodes,
                thumbnailUrl = ShikimoriApi.BASE_URL + anime.image.original,
            )
        }
    }

    private suspend fun fetchManga(
        api: ShikimoriApi,
        userId: Int,
        ranobeOnly: Boolean,
    ): List<ShikimoriImportEntry> {
        val rates = api.getAllUserMangaRates(userId)
        val targetIds = rates.map { it.targetId }.distinct()

        // Bulk-first for BOTH manga and ranobe: /mangas?ids= costs one request
        // per 50 entries. Ids missing from the bulk response (ranobe kinds are
        // often excluded from it) are fetched individually as a fallback,
        // instead of doing one request per entry upfront for ranobe.
        val bulk = targetIds
            .chunked(BULK_CHUNK_SIZE)
            .flatMap { chunk -> rateLimiter.withRateLimit { api.getMangasByIds(chunk) } }
            .associateBy { it.id }
            .toMutableMap()
        val stillMissing = targetIds.filter { it !in bulk }
        if (stillMissing.isNotEmpty()) {
            bulk.putAll(fetchMangaByIdsParallel(api, stillMissing))
        }
        val mangaById: Map<Long, SMEntry> = bulk

        return rates.mapNotNull { rate ->
            val manga = mangaById[rate.targetId] ?: return@mapNotNull null
            val isRanobe = ShikimoriImportEntry.isRanobeKind(manga.kind)
            if (ranobeOnly != isRanobe) return@mapNotNull null
            ShikimoriImportEntry(
                mediaType = if (isRanobe) ShikimoriImportMediaType.RANOBE else ShikimoriImportMediaType.MANGA,
                rateId = rate.id,
                remoteId = rate.targetId,
                name = manga.name,
                russian = manga.russian,
                status = rate.status,
                score = rate.score,
                progress = rate.chapters,
                totalCount = manga.chapters,
                thumbnailUrl = ShikimoriApi.BASE_URL + manga.image.original,
                kind = manga.kind,
            )
        }
    }

    private suspend fun fetchMangaByIdsParallel(
        api: ShikimoriApi,
        ids: List<Long>,
    ): Map<Long, SMEntry> = coroutineScope {
        if (ids.isEmpty()) return@coroutineScope emptyMap()
        val result = ConcurrentHashMap<Long, SMEntry>()
        val semaphore = Semaphore(ShikimoriApiRateLimiter.FETCH_CONCURRENCY)
        ids.map { id ->
            async {
                semaphore.withPermit {
                    try {
                        rateLimiter.withRateLimit {
                            val manga = api.getMangaById(id)
                            result[manga.id] = manga
                        }
                    } catch (e: HttpException) {
                        if (e.code == 429) throw RateLimitedException()
                        // Don't silently drop the entry on other HTTP errors.
                        logcat(LogPriority.WARN, e) { "Shikimori entry fetch failed for id=$id" }
                    }
                }
            }
        }.awaitAll()
        result
    }

    companion object {
        private const val BULK_CHUNK_SIZE = 50
    }
}
