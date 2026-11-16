package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
import eu.kanade.tachiyomi.data.discovery.DiscoverySdkBindingAuthority
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.novelsource.online.HttpNovelSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Resolves native cover credentials by exact source identity and live participation. */
object DiscoveryCoverSource {

    internal data class Resolved(
        val headers: Map<String, String>?,
        val client: Call.Factory?,
        val referer: String?,
    )

    private data class GuardedClientKey(
        val delegateId: Int,
        val authorityId: Int,
        val mediaType: DiscoveryMediaType,
        val sourceId: Long,
        val rowType: DiscoveryRowType,
        val provider: String,
    )

    private val guardedClientCache = WeakHashMap<Any, MutableMap<GuardedClientKey, Call.Factory>>()
    private val guardedClientLock = Any()

    private fun okhttpHeadersToMap(headers: okhttp3.Headers): Map<String, String>? {
        val map = mutableMapOf<String, String>()
        for (i in 0 until headers.size) {
            map[headers.name(i)] = headers.value(i)
        }
        return map.ifEmpty { null }
    }

    internal fun isAllowed(item: DiscoverySuggestion): Boolean = try {
        Injekt.get<DiscoverySourcePolicyProvider>().current(item.mediaType).allows(item)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private fun sourceReference(mediaType: DiscoveryMediaType, sourceId: Long): Any? = when (mediaType) {
        DiscoveryMediaType.ANIME -> Injekt.get<AnimeSourceManager>().get(sourceId)
        DiscoveryMediaType.MANGA -> Injekt.get<MangaSourceManager>().get(sourceId)
        DiscoveryMediaType.NOVEL -> Injekt.get<NovelSourceManager>().get(sourceId)
    }

    internal fun resolve(item: DiscoverySuggestion): Resolved? {
        val sourceId = item.sourceId?.takeIf { it > 0L } ?: return null
        if (!isAllowed(item)) return null
        return try {
            val source = sourceReference(item.mediaType, sourceId) ?: return null
            val authority = Injekt.get<DiscoverySourcePolicyProvider>() as? DiscoverySdkBindingAuthority ?: return null
            if (!authority.isCurrentSourceBinding(item.mediaType, sourceId, source)) return null
            if (source is StubAnimeSource || source is StubMangaSource || source is StubNovelSource) return null
            val resolved = when (source) {
                is AnimeHttpSource -> Resolved(okhttpHeadersToMap(source.headers), source.client, source.baseUrl)
                is HttpSource -> Resolved(okhttpHeadersToMap(source.headers), source.client, source.baseUrl)
                is NovelHttpSource -> Resolved(
                    okhttpHeadersToMap(source.headers),
                    (source as? HttpNovelSource)?.client,
                    source.baseUrl,
                )
                else -> Resolved(null, null, null)
            }
            val delegate = resolved.client as? OkHttpClient ?: Injekt.get<NetworkHelper>().client
            if (!isAllowed(item) || !authority.isCurrentSourceBinding(item.mediaType, sourceId, source) ||
                sourceReference(item.mediaType, sourceId) !== source
            ) {
                return null
            }
            val sourceRef = WeakReference(source)
            fun checkSource() {
                val boundSource = sourceRef.get()
                    ?: throw IOException("Discovery cover source is no longer available")
                if (!isAllowed(item) || !authority.isCurrentSourceBinding(item.mediaType, sourceId, boundSource) ||
                    sourceReference(item.mediaType, sourceId) !== boundSource
                ) {
                    throw IOException("Discovery cover source is no longer available")
                }
            }
            val key = GuardedClientKey(
                delegateId = System.identityHashCode(delegate),
                authorityId = System.identityHashCode(authority),
                mediaType = item.mediaType,
                sourceId = sourceId,
                rowType = item.rowType,
                provider = item.provider,
            )
            val guardedFactory = synchronized(guardedClientLock) {
                val byKey = guardedClientCache.getOrPut(source) { HashMap() }
                byKey.getOrPut(key) {
                    val guardedClient = delegate.newBuilder().apply {
                        interceptors().add(
                            0,
                            Interceptor { chain ->
                                checkSource()
                                chain.proceed(chain.request())
                            },
                        )
                    }.build()
                    Call.Factory { request ->
                        checkSource()
                        guardedClient.newCall(request)
                    }
                }
            }
            resolved.copy(client = guardedFactory)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    @Deprecated("Use resolve(item) with its native source identity")
    @Suppress("UNUSED_PARAMETER")
    internal fun resolve(mediaType: DiscoveryMediaType, provider: String?): Resolved? = null
}

/** Source-bound cover model. Recovery retains the existing primary/fallback URL semantics. */
internal fun discoveryCoverData(item: DiscoverySuggestion, recoveryUrl: String? = null): Any? {
    if (!DiscoveryCoverSource.isAllowed(item)) return null
    val primary = item.coverUrl?.takeIf { it.isNotBlank() } ?: recoveryUrl ?: return null
    val fallback = recoveryUrl?.takeIf { it.isNotBlank() && it != primary && !item.coverUrl.isNullOrBlank() }
    val resolved = DiscoveryCoverSource.resolve(item)
    if (item.sourceId != null && resolved == null) return null
    return coverModel(primary, fallback, resolved)
}

/** Shared lifecycle-aware cover model for Discovery cards across Home and Feed. */
@Composable
internal fun rememberDiscoveryCoverData(item: DiscoverySuggestion, recoveryUrl: String? = null): Any? {
    val provider = remember { Injekt.get<DiscoverySourcePolicyProvider>() }
    val mediaType = item.mediaType
    val policyFlow = remember(provider, mediaType) { provider.observe(mediaType) }
    val policy by policyFlow.collectAsStateWithLifecycle(initialValue = provider.current(mediaType))
    val sourceId = item.sourceId?.takeIf { it > 0L }
    val sourceVersionFlow = remember(mediaType, sourceId) {
        if (sourceId == null) {
            flowOf(0)
        } else {
            runCatching {
                when (mediaType) {
                    DiscoveryMediaType.ANIME -> Injekt.get<AnimeSourceManager>().catalogueSources.map { sources ->
                        System.identityHashCode(sources.firstOrNull { it.id == sourceId })
                    }
                    DiscoveryMediaType.MANGA -> Injekt.get<MangaSourceManager>().catalogueSources.map { sources ->
                        System.identityHashCode(sources.firstOrNull { it.id == sourceId })
                    }
                    DiscoveryMediaType.NOVEL -> Injekt.get<NovelSourceManager>().catalogueSources.map { sources ->
                        System.identityHashCode(sources.firstOrNull { it.id == sourceId })
                    }
                }.catch { emit(0) }
            }.getOrElse { flowOf(0) }
        }
    }
    val sourceVersion by sourceVersionFlow.collectAsStateWithLifecycle(initialValue = 0)
    return remember(
        mediaType,
        item.sourceId,
        item.sourceUrl,
        item.provider,
        item.rowType,
        item.coverUrl,
        recoveryUrl,
        policy.revision,
        sourceVersion,
    ) {
        discoveryCoverData(item, recoveryUrl)
    }
}

/** Legacy unbound callers never receive native credentials inferred from a display name. */
@Deprecated("Use discoveryCoverData(item, recoveryUrl)")
@Suppress("UNUSED_PARAMETER")
internal fun discoveryCoverData(
    mediaType: DiscoveryMediaType?,
    provider: String?,
    coverUrl: String?,
    recoveryUrl: String? = null,
): Any? {
    val primary = coverUrl?.takeIf { it.isNotBlank() } ?: recoveryUrl ?: return null
    val fallback = recoveryUrl?.takeIf { it.isNotBlank() && it != primary && !coverUrl.isNullOrBlank() }
    return coverModel(primary, fallback, null)
}

private fun coverModel(primary: String, fallback: String?, resolved: DiscoveryCoverSource.Resolved?): Any =
    if (resolved != null || fallback != null) {
        AuroraPosterRequest(
            primaryUrl = primary,
            fallbackUrl = fallback,
            refererUrl = resolved?.referer,
            headers = resolved?.headers,
            client = resolved?.client,
        )
    } else {
        primary
    }
