package eu.kanade.tachiyomi.ui.reels

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeFeedVideoQualitySource
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualitiesResult
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityResult
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityVariant
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.domain.reels.anime.interactor.ReelsQualityDecision
import tachiyomi.domain.reels.anime.interactor.ReelsQualitySelector
import tachiyomi.domain.reels.anime.model.ReelsQualityPreference
import java.security.MessageDigest

/**
 * Screen-local quality owner: global preference, session overrides and the actual media
 * binding. It performs no database work; global preference goes through [SourcePreferences],
 * overrides live only in this instance.
 */
class ReelsQualityController(
    private val preferences: SourcePreferences,
    private val codecPredicate: (FeedVideoQualityVariant) -> Boolean = ReelsQualityCodecSupport::isSupported,
) {

    private val _state = MutableStateFlow(ReelsQualityState())
    val state: StateFlow<ReelsQualityState> = _state.asStateFlow()

    private var source: AnimeFeedVideoQualitySource? = null
    private val overrides = mutableMapOf<String, ReelsQualityPreference>()

    fun attach(source: AnimeFeedVideoQualitySource, owner: ReelsQualityOwner) {
        this.source = source
        val current = _state.value.owner
        if (current?.sourceId == owner.sourceId && current.accountId == owner.accountId) return
        overrides.clear()
        val global = ReelsQualitySelector.parseGlobal(
            preferences.reelsVideoQuality(owner.sourceId).get(),
        )
        _state.value = ReelsQualityState(
            owner = owner,
            global = global,
            items = emptyMap(),
            selectionRevision = _state.value.selectionRevision + 1,
        )
    }

    fun detach() {
        source = null
        overrides.clear()
        _state.value = ReelsQualityState(selectionRevision = _state.value.selectionRevision + 1)
    }

    fun supports(itemId: String): Boolean = source?.supportsVideoQuality(itemId) == true

    fun itemState(itemId: String): ReelsQualityItemState? = _state.value.items[itemId]

    fun playback(itemId: String): ReelsQualityPlayback? = _state.value.items[itemId]?.actual

    fun setGlobal(expectedOwner: ReelsQualityOwner, value: ReelsQualityPreference) {
        val current = _state.value
        if (current.owner != expectedOwner) return
        preferences.reelsVideoQuality(expectedOwner.sourceId).set(
            ReelsQualitySelector.serializeGlobal(value),
        )
        _state.update { state ->
            state.copy(
                global = value,
                items = state.items.mapValues { (itemId, item) ->
                    if (overrides.containsKey(itemId)) {
                        item
                    } else {
                        item.copy(
                            requested = value,
                            usingGlobal = true,
                            revision = item.revision + 1,
                        )
                    }
                },
                selectionRevision = state.selectionRevision + 1,
            )
        }
    }

    fun setOverride(
        expectedOwner: ReelsQualityOwner,
        itemId: String,
        value: ReelsQualityPreference,
    ) {
        val current = _state.value
        if (current.owner != expectedOwner) return
        overrides[itemId] = value
        _state.update { state ->
            val item = state.items[itemId]
            state.copy(
                items = if (item == null) {
                    state.items
                } else {
                    state.items + (
                        itemId to item.copy(
                            requested = value,
                            usingGlobal = false,
                            revision = item.revision + 1,
                        )
                        )
                },
                selectionRevision = state.selectionRevision + 1,
            )
        }
    }

    fun clearOverride(expectedOwner: ReelsQualityOwner, itemId: String) {
        val current = _state.value
        if (current.owner != expectedOwner) return
        overrides.remove(itemId)
        _state.update { state ->
            val item = state.items[itemId]
            state.copy(
                items = if (item == null) {
                    state.items
                } else {
                    state.items + (
                        itemId to item.copy(
                            requested = state.global,
                            usingGlobal = true,
                            revision = item.revision + 1,
                        )
                        )
                },
                selectionRevision = state.selectionRevision + 1,
            )
        }
    }

    suspend fun prepare(
        expectedOwner: ReelsQualityOwner,
        item: ShortVideoItem,
        forceSd: Boolean,
        recheckOnActivation: Boolean,
    ): ReelsQualityPlayback? {
        val source = source ?: return null
        val current = _state.value
        if (current.owner != expectedOwner) return null
        if (expectedOwner.accountId.isNullOrBlank()) {
            fail(expectedOwner, item.id, (current.items[item.id]?.revision ?: 0L) + 1L, ReelsQualityFailure.NeedsAuth)
            return null
        }
        if (!source.supportsVideoQuality(item.id)) {
            markUnsupported(expectedOwner, item.id, forceSd)
            return null
        }

        val override = overrides[item.id]
        val requested = override ?: if (forceSd) {
            ReelsQualityPreference.Resolution(480)
        } else {
            current.global
        }
        val existing = current.items[item.id]
        val bound = existing?.actual
        if (
            !recheckOnActivation &&
            existing != null &&
            bound != null &&
            bound.decision.requested == requested &&
            existing.forceSd == forceSd &&
            existing.catalogStatus in CACHED_CATALOG_STATUSES &&
            !existing.switching
        ) {
            return bound
        }

        val revision = (existing?.revision ?: 0L) + 1L
        putItem(
            ReelsQualityItemState(
                owner = expectedOwner,
                itemId = item.id,
                requested = requested,
                usingGlobal = override == null,
                catalogStatus = if (existing == null || existing.options.isEmpty()) {
                    ReelsQualityCatalogStatus.LOADING
                } else {
                    existing.catalogStatus
                },
                options = existing?.options.orEmpty(),
                actual = existing?.actual,
                switching = true,
                failure = null,
                revision = revision,
                forceSd = forceSd,
            ),
        )

        val options = if (
            existing != null &&
            existing.options.isNotEmpty() &&
            existing.catalogStatus in CACHED_CATALOG_STATUSES
        ) {
            existing.options
        } else {
            val catalog = try {
                source.getVideoQualities(item.id, expectedOwner.accountId.orEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                fail(expectedOwner, item.id, revision, ReelsQualityFailure.Retry(null))
                return null
            }
            if (!isCurrent(expectedOwner, item.id, revision)) return null
            when (catalog) {
                is FeedVideoQualitiesResult.Available -> {
                    if (
                        catalog.itemId != item.id ||
                        catalog.accountId != expectedOwner.accountId ||
                        catalog.variants.isEmpty() ||
                        catalog.variants.count { it.isSource } != 1
                    ) {
                        fail(expectedOwner, item.id, revision, ReelsQualityFailure.Unavailable)
                        return null
                    }
                    putCatalog(
                        expectedOwner,
                        item.id,
                        revision,
                        catalog.variants,
                        if (catalog.variants.all { it.isSource }) {
                            ReelsQualityCatalogStatus.SOURCE_ONLY
                        } else {
                            ReelsQualityCatalogStatus.READY
                        },
                    )
                    catalog.variants
                }
                is FeedVideoQualitiesResult.Retry -> {
                    fail(expectedOwner, item.id, revision, ReelsQualityFailure.Retry(catalog.retryAtEpochMillis))
                    return null
                }
                FeedVideoQualitiesResult.NeedsAuth -> {
                    fail(expectedOwner, item.id, revision, ReelsQualityFailure.NeedsAuth)
                    return null
                }
                FeedVideoQualitiesResult.ContextChanged -> {
                    fail(expectedOwner, item.id, revision, ReelsQualityFailure.ContextChanged)
                    return null
                }
                FeedVideoQualitiesResult.Unavailable -> {
                    fail(expectedOwner, item.id, revision, ReelsQualityFailure.Unavailable)
                    return null
                }
                FeedVideoQualitiesResult.Unsupported -> {
                    fail(expectedOwner, item.id, revision, ReelsQualityFailure.Unsupported)
                    return null
                }
            }
        }

        val compatibleIds = options.asSequence()
            .filterNot { it.isSource }
            .filter { variant -> runCatching { codecPredicate(variant) }.getOrDefault(false) }
            .map { it.id }
            .toSet()
        val decision = runCatching {
            ReelsQualitySelector.select(
                global = current.global,
                override = override,
                forceSd = forceSd,
                variants = options,
                compatibleIds = compatibleIds,
            )
        }.getOrNull() ?: run {
            fail(expectedOwner, item.id, revision, ReelsQualityFailure.Unavailable)
            return null
        }

        val resolved = try {
            source.resolveVideoQuality(item.id, decision.variant.id, expectedOwner.accountId.orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            fail(expectedOwner, item.id, revision, ReelsQualityFailure.Retry(null))
            return null
        }
        if (!isCurrent(expectedOwner, item.id, revision)) return null

        return when (resolved) {
            is FeedVideoQualityResult.Available -> {
                if (
                    resolved.itemId != item.id ||
                    resolved.accountId != expectedOwner.accountId ||
                    resolved.variant.id != decision.variant.id ||
                    resolved.videoUrl.isBlank() ||
                    resolved.cacheIdentity.isBlank()
                ) {
                    fail(expectedOwner, item.id, revision, ReelsQualityFailure.Unavailable)
                    return null
                }
                val actual = ReelsQualityPlayback(
                    owner = expectedOwner,
                    itemId = item.id,
                    revision = revision,
                    decision = decision,
                    videoUrl = resolved.videoUrl,
                    cacheIdentity = resolved.cacheIdentity,
                    cacheKey = cacheKey(expectedOwner, item.id, resolved.cacheIdentity),
                )
                publishActual(expectedOwner, item.id, revision, actual)
                actual
            }
            is FeedVideoQualityResult.Retry -> {
                fail(expectedOwner, item.id, revision, ReelsQualityFailure.Retry(resolved.retryAtEpochMillis))
                null
            }
            FeedVideoQualityResult.NeedsAuth -> {
                fail(expectedOwner, item.id, revision, ReelsQualityFailure.NeedsAuth)
                null
            }
            FeedVideoQualityResult.ContextChanged -> {
                fail(expectedOwner, item.id, revision, ReelsQualityFailure.ContextChanged)
                null
            }
            FeedVideoQualityResult.Unavailable -> {
                fail(expectedOwner, item.id, revision, ReelsQualityFailure.Unavailable)
                null
            }
            FeedVideoQualityResult.Unsupported -> {
                fail(expectedOwner, item.id, revision, ReelsQualityFailure.Unsupported)
                null
            }
        }
    }

    /**
     * Prepares the bounded forward window in feed order: each item gets a catalog and a resolved
     * URI so the player preload and the head-prewarm can warm real bytes before activation.
     * Metadata only — no media download is started here.
     */
    suspend fun prepareWindow(
        expectedOwner: ReelsQualityOwner,
        items: List<ShortVideoItem>,
        fromIndex: Int,
        toIndex: Int,
        forceSd: Boolean,
    ) {
        if (fromIndex < 0 || toIndex < fromIndex) return
        for (index in fromIndex..minOf(toIndex, items.lastIndex)) {
            currentCoroutineContext().ensureActive()
            if (_state.value.owner != expectedOwner) return
            val item = items.getOrNull(index) ?: return
            if (!supports(item.id)) continue
            prepare(
                expectedOwner = expectedOwner,
                item = item,
                forceSd = forceSd,
                recheckOnActivation = false,
            )
        }
    }

    fun captureDownload(
        expectedOwner: ReelsQualityOwner,
        item: ShortVideoItem,
        headers: Map<String, String>,
    ): ReelsQualityDownloadSnapshot? {
        val current = _state.value
        if (current.owner != expectedOwner) return null
        val state = current.items[item.id] ?: return null
        if (state.switching) return null
        val actual = state.actual ?: return null
        return ReelsQualityDownloadSnapshot(
            owner = expectedOwner,
            item = item.copy(videoUrl = actual.videoUrl, videoUrlHd = null),
            variant = actual.decision.variant,
            cacheIdentity = actual.cacheIdentity,
            headers = HashMap(headers),
        )
    }

    suspend fun rehydrateDownload(
        snapshot: ReelsQualityDownloadSnapshot,
        resumeFrom: Long,
    ): ReelsQualityDownloadSnapshot? {
        if (resumeFrom < 0L) return null
        if (_state.value.owner != snapshot.owner) return null
        val source = source ?: return null
        val resolved = try {
            source.resolveVideoQuality(
                snapshot.item.id,
                snapshot.variant.id,
                snapshot.owner.accountId.orEmpty(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return null
        }
        if (resolved !is FeedVideoQualityResult.Available) return null
        if (
            resolved.itemId != snapshot.item.id ||
            resolved.accountId != snapshot.owner.accountId ||
            resolved.variant.id != snapshot.variant.id ||
            resolved.cacheIdentity != snapshot.cacheIdentity
        ) {
            return null
        }
        return snapshot.copy(
            item = snapshot.item.copy(videoUrl = resolved.videoUrl, videoUrlHd = null),
            variant = resolved.variant,
        )
    }

    fun onPlaybackFailure(expectedOwner: ReelsQualityOwner, itemId: String, revision: Long) {
        val current = _state.value
        if (current.owner != expectedOwner) return
        val item = current.items[itemId] ?: return
        if (item.revision != revision) return
        putItem(
            item.copy(
                failure = ReelsQualityFailure.Retry(null),
                switching = false,
            ),
        )
    }

    private fun markUnsupported(
        expectedOwner: ReelsQualityOwner,
        itemId: String,
        forceSd: Boolean,
    ) {
        val current = _state.value
        if (current.owner != expectedOwner) return
        val item = current.items[itemId]
        val revision = (item?.revision ?: 0L) + 1L
        putItem(
            ReelsQualityItemState(
                owner = expectedOwner,
                itemId = itemId,
                requested = current.global,
                usingGlobal = true,
                catalogStatus = ReelsQualityCatalogStatus.UNSUPPORTED,
                options = emptyList(),
                actual = item?.actual,
                switching = false,
                failure = ReelsQualityFailure.Unsupported,
                revision = revision,
                forceSd = forceSd,
            ),
        )
    }

    private fun putCatalog(
        expectedOwner: ReelsQualityOwner,
        itemId: String,
        revision: Long,
        options: List<FeedVideoQualityVariant>,
        status: ReelsQualityCatalogStatus,
    ) {
        val item = currentItem(expectedOwner, itemId, revision) ?: return
        putItem(
            item.copy(
                catalogStatus = status,
                options = options,
                failure = null,
            ),
        )
    }

    private fun publishActual(
        expectedOwner: ReelsQualityOwner,
        itemId: String,
        revision: Long,
        actual: ReelsQualityPlayback,
    ) {
        val item = currentItem(expectedOwner, itemId, revision) ?: return
        putItem(
            item.copy(
                actual = actual,
                switching = false,
                failure = null,
            ),
        )
    }

    private fun fail(
        expectedOwner: ReelsQualityOwner,
        itemId: String,
        revision: Long,
        failure: ReelsQualityFailure,
    ) {
        val item = currentItem(expectedOwner, itemId, revision) ?: return
        putItem(
            item.copy(
                switching = false,
                failure = failure,
                catalogStatus = when (failure) {
                    is ReelsQualityFailure.Unavailable -> ReelsQualityCatalogStatus.UNAVAILABLE
                    ReelsQualityFailure.Unsupported -> ReelsQualityCatalogStatus.UNSUPPORTED
                    else -> ReelsQualityCatalogStatus.ERROR
                },
            ),
        )
    }

    private fun currentItem(
        expectedOwner: ReelsQualityOwner,
        itemId: String,
        revision: Long,
    ): ReelsQualityItemState? {
        val current = _state.value
        if (current.owner != expectedOwner) return null
        return current.items[itemId]?.takeIf { it.revision == revision }
    }

    private fun isCurrent(expectedOwner: ReelsQualityOwner, itemId: String, revision: Long): Boolean =
        currentItem(expectedOwner, itemId, revision) != null

    private fun putItem(item: ReelsQualityItemState) {
        _state.update { state ->
            if (state.owner != item.owner) return@update state
            state.copy(items = state.items + (item.itemId to item))
        }
    }

    companion object {
        private val CACHED_CATALOG_STATUSES = setOf(
            ReelsQualityCatalogStatus.READY,
            ReelsQualityCatalogStatus.SOURCE_ONLY,
        )

        fun cacheKey(owner: ReelsQualityOwner, itemId: String, cacheIdentity: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${owner.sourceId}:${owner.accountId.orEmpty()}:$itemId:$cacheIdentity".toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return "q29:$digest"
        }
    }
}
