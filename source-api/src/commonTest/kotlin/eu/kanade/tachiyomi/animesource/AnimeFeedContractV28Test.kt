package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedReplayItemResult
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinCandidate
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinCatalog
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinCatalogResult
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinDeferral
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinIdentity
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinReadiness
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinScanRequest
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinScanResult
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinSourceRef
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimeFeedContractV28Test {

    private val storageKey = "tdlib-rr-v1"

    private fun context(continuityKey: String = "feed_global_unseen") =
        FeedReplayContext(accountId = "tg_42", continuityKey = continuityKey)

    private class LegacyFeedSource : AnimeFeedSource {
        override val id: Long = 1L
        override val name: String = "legacy"

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(videos = emptyList(), hasNextPage = false)
    }

    private class RoundRobinSource(
        private val accountId: String? = "tg_42",
        private val storageKey: String = "tdlib-rr-v1",
    ) : AnimeFeedRoundRobinSource {
        private val provenContext: FeedReplayContext? =
            accountId?.let { FeedReplayContext(it, "feed_global_unseen") }

        override fun accountScopeId(): String? = accountId

        override val replayContextChanges: Flow<FeedReplayContext?> = flowOf(provenContext)

        override suspend fun prepareReplay(): FeedReplayContext? = provenContext

        override suspend fun resolveReplayItem(
            itemId: String,
            expectedContext: FeedReplayContext,
            filters: AnimeFilterList,
        ): FeedReplayItemResult =
            if (expectedContext == provenContext) {
                FeedReplayItemResult.Unavailable
            } else {
                FeedReplayItemResult.ContextChanged
            }

        override fun isRoundRobinEnabled(filters: AnimeFilterList): Boolean = provenContext != null

        override suspend fun prepareRoundRobin(): FeedRoundRobinReadiness =
            provenContext?.let { FeedRoundRobinReadiness.Ready(it, storageKey) }
                ?: FeedRoundRobinReadiness.SignedOut

        override fun identifyRoundRobinItem(itemId: String): FeedRoundRobinIdentity? {
            val messageId = itemId.substringAfter('/', missingDelimiterValue = "").toLongOrNull()
                ?: return null
            return FeedRoundRobinIdentity(
                sourceKey = itemId.removePrefix("tdlib:").substringBefore('/'),
                orderKey = messageId,
            )
        }

        override suspend fun getRoundRobinCatalog(
            expectedContext: FeedReplayContext,
            forceRefresh: Boolean,
        ): FeedRoundRobinCatalogResult =
            if (expectedContext != provenContext) {
                FeedRoundRobinCatalogResult.ContextChanged
            } else {
                FeedRoundRobinCatalogResult.Snapshot(
                    FeedRoundRobinCatalog(
                        context = expectedContext,
                        revision = "rev-1",
                        complete = true,
                        sources = emptyList(),
                        retryAtEpochMillis = null,
                    ),
                )
            }

        override suspend fun scanRoundRobinSource(
            request: FeedRoundRobinScanRequest,
        ): FeedRoundRobinScanResult =
            if (request.expectedContext != provenContext) {
                FeedRoundRobinScanResult.ContextChanged
            } else if (request.checkpoint == "invalid") {
                FeedRoundRobinScanResult.InvalidCheckpoint
            } else {
                FeedRoundRobinScanResult.Page(
                    candidates = emptyList(),
                    checkpoint = request.checkpoint ?: "cp-1",
                    historyExhausted = false,
                    nextHeadCheckAtEpochMillis = null,
                )
            }
    }

    /**
     * Documented v28 result invariants, encoded in the test on purpose: the production API has
     * no validator, a host checks them before consuming a [FeedRoundRobinScanResult.Page].
     */
    private fun isProtocolConformantPage(
        request: FeedRoundRobinScanRequest,
        result: FeedRoundRobinScanResult,
    ): Boolean {
        if (result !is FeedRoundRobinScanResult.Page) return false
        if (request.maxCandidates !in 1..2) return false
        if (result.checkpoint.isEmpty()) return false
        return result.candidates.size <= request.maxCandidates &&
            result.candidates.all { candidate ->
                candidate.identity.sourceKey == request.sourceKey &&
                    candidate.item.id.isNotEmpty() &&
                    candidate.identity.orderKey > 0L
            }
    }

    @Test
    fun roundRobinCapabilityIsAdditiveAndInstanceofDetected() {
        val legacy: Any = LegacyFeedSource()
        assertTrue(legacy is AnimeFeedSource)
        assertFalse(legacy is AnimeFeedReplaySource)
        assertFalse(legacy is AnimeFeedRoundRobinSource)

        val roundRobin: Any = RoundRobinSource()
        assertTrue(roundRobin is AnimeFeedRoundRobinSource)
        assertTrue(roundRobin is AnimeFeedReplaySource)
        assertTrue(roundRobin is AnimeFeedAccountIdentitySource)
        assertFalse(roundRobin is AnimeFeedLoginSource)
    }

    @Test
    fun readinessHasThreeExhaustiveStates() {
        val signedOut: FeedRoundRobinReadiness = FeedRoundRobinReadiness.SignedOut
        val pending: FeedRoundRobinReadiness =
            FeedRoundRobinReadiness.Pending(retryAtEpochMillis = 1_700_000_000_000L)
        val ready: FeedRoundRobinReadiness = FeedRoundRobinReadiness.Ready(context(), storageKey)

        assertIs<FeedRoundRobinReadiness.SignedOut>(signedOut)
        assertIs<FeedRoundRobinReadiness.Pending>(pending)
        assertEquals(1_700_000_000_000L, pending.retryAtEpochMillis)
        assertIs<FeedRoundRobinReadiness.Ready>(ready)
        assertEquals(context(), ready.context)
        assertEquals(storageKey, ready.storageKey)

        val pendingWithoutWindow = FeedRoundRobinReadiness.Pending(retryAtEpochMillis = null)
        assertNull(pendingWithoutWindow.retryAtEpochMillis)

        // A fourth readiness subtype must break this when at compile time (no else) and the
        // sealed-subclass assertion below at run time.
        val labels = listOf<FeedRoundRobinReadiness>(signedOut, pending, ready, pendingWithoutWindow)
            .map { readiness ->
                when (readiness) {
                    is FeedRoundRobinReadiness.SignedOut -> "SignedOut"
                    is FeedRoundRobinReadiness.Pending -> "Pending"
                    is FeedRoundRobinReadiness.Ready -> "Ready"
                }
            }
        assertEquals(listOf("SignedOut", "Pending", "Ready", "Pending"), labels)
        assertEquals(
            setOf("SignedOut", "Pending", "Ready"),
            FeedRoundRobinReadiness::class.sealedSubclasses.mapNotNull { it.simpleName }.toSet(),
        )
    }

    @Test
    fun readyStorageKeyIsIndependentFromMutableReplayContext() {
        val first = FeedRoundRobinReadiness.Ready(
            context = context(continuityKey = "feed_global_unseen"),
            storageKey = storageKey,
        )
        val second = FeedRoundRobinReadiness.Ready(
            context = context(continuityKey = "feed_global_unseen|channels=1,2"),
            storageKey = storageKey,
        )

        assertEquals(first.storageKey, second.storageKey)
        assertNotEquals(first.context, second.context)
    }

    @Test
    fun invalidResultIdentityAndOversizedPageAreDetectable() {
        val request = FeedRoundRobinScanRequest(
            sourceKey = "-1001234567890",
            expectedContext = context(),
            filters = AnimeFilterList(),
            checkpoint = null,
            refreshHead = false,
            maxCandidates = 2,
        )
        val item = ShortVideoItem(
            id = "tdlib:-1001234567890/77",
            videoUrl = "https://cdn.example.invalid/77.mp4",
            posterUrl = "https://cdn.example.invalid/77.jpg",
        )
        val identity = FeedRoundRobinIdentity(sourceKey = request.sourceKey, orderKey = 77L)
        val candidate = FeedRoundRobinCandidate(item = item, identity = identity, contentKey = null)
        val page = FeedRoundRobinScanResult.Page(
            candidates = listOf(candidate),
            checkpoint = "cp-77",
            historyExhausted = false,
            nextHeadCheckAtEpochMillis = null,
        )

        assertTrue(request.maxCandidates in 1..2)
        assertTrue(page.checkpoint.isNotEmpty())
        assertTrue(isProtocolConformantPage(request, page))

        val wrongSource = candidate.copy(identity = identity.copy(sourceKey = "-1009999999999"))
        assertFalse(isProtocolConformantPage(request, page.copy(candidates = listOf(wrongSource))))

        val emptyItemId = candidate.copy(item = item.copy(id = ""))
        assertFalse(isProtocolConformantPage(request, page.copy(candidates = listOf(emptyItemId))))

        val nonPositiveOrder = candidate.copy(identity = identity.copy(orderKey = 0L))
        assertFalse(isProtocolConformantPage(request, page.copy(candidates = listOf(nonPositiveOrder))))

        val oversized = page.copy(candidates = List(request.maxCandidates + 1) { candidate })
        assertFalse(isProtocolConformantPage(request, oversized))

        val emptyCheckpoint = page.copy(checkpoint = "")
        assertFalse(isProtocolConformantPage(request, emptyCheckpoint))
    }

    @Test
    fun catalogAndScanResultsExposeEveryDocumentedState() {
        val sourceRef = FeedRoundRobinSourceRef(
            key = "-1001234567890",
            title = "Round-robin chat",
            author = null,
            contentRevision = "rev-1",
            excluded = false,
            previousKeys = listOf("-1000000000001", "-1000000000002"),
        )
        assertEquals(2, sourceRef.previousKeys.size)
        assertFalse(sourceRef.excluded)

        val catalog = FeedRoundRobinCatalog(
            context = context(),
            revision = "rev-7",
            complete = false,
            sources = listOf(sourceRef),
            retryAtEpochMillis = 123L,
        )
        val snapshot: FeedRoundRobinCatalogResult = FeedRoundRobinCatalogResult.Snapshot(catalog)
        assertIs<FeedRoundRobinCatalogResult.Snapshot>(snapshot)
        assertEquals("rev-7", snapshot.catalog.revision)
        assertFalse(snapshot.catalog.complete)

        val deferredCatalog: FeedRoundRobinCatalogResult =
            FeedRoundRobinCatalogResult.Deferred(retryAtEpochMillis = null)
        assertNull(assertIs<FeedRoundRobinCatalogResult.Deferred>(deferredCatalog).retryAtEpochMillis)
        assertIs<FeedRoundRobinCatalogResult.ContextChanged>(FeedRoundRobinCatalogResult.ContextChanged)

        val page = FeedRoundRobinScanResult.Page(
            candidates = emptyList(),
            checkpoint = "cp-head",
            historyExhausted = true,
            nextHeadCheckAtEpochMillis = 456L,
        )
        assertTrue(page.historyExhausted)
        assertEquals("cp-head", page.checkpoint)
        assertEquals(456L, page.nextHeadCheckAtEpochMillis)

        val deferredScan: FeedRoundRobinScanResult = FeedRoundRobinScanResult.Deferred(
            reason = FeedRoundRobinDeferral.RATE_LIMIT,
            retryAtEpochMillis = 789L,
            providerWide = true,
        )
        assertIs<FeedRoundRobinScanResult.Deferred>(deferredScan)
        assertTrue(deferredScan.providerWide)
        assertEquals(789L, deferredScan.retryAtEpochMillis)

        assertIs<FeedRoundRobinScanResult.Unavailable>(FeedRoundRobinScanResult.Unavailable)
        assertIs<FeedRoundRobinScanResult.ContextChanged>(FeedRoundRobinScanResult.ContextChanged)
        assertIs<FeedRoundRobinScanResult.InvalidCheckpoint>(FeedRoundRobinScanResult.InvalidCheckpoint)

        assertEquals(
            setOf(
                FeedRoundRobinDeferral.NETWORK,
                FeedRoundRobinDeferral.RATE_LIMIT,
                FeedRoundRobinDeferral.TIME_BUDGET,
                FeedRoundRobinDeferral.UPSTREAM_PENDING,
            ),
            FeedRoundRobinDeferral.entries.toSet(),
        )
    }

    @Test
    fun v27ModelDescriptorsAreUnchanged() {
        val contextConstructor = FeedReplayContext::class.java.getConstructor(
            String::class.java,
            String::class.java,
        )
        assertEquals(
            listOf("java.lang.String", "java.lang.String"),
            contextConstructor.parameterTypes.map { it.name },
        )
        val contextCopy = FeedReplayContext::class.java.getMethod(
            "copy",
            String::class.java,
            String::class.java,
        )
        assertEquals(FeedReplayContext::class.java, contextCopy.returnType)

        val pageConstructor = FeedPage::class.java.getConstructor(
            List::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
        )
        assertEquals(
            listOf("java.util.List", "boolean", "java.lang.String"),
            pageConstructor.parameterTypes.map { it.name },
        )
        val pageCopy = FeedPage::class.java.getMethod(
            "copy",
            List::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
        )
        assertEquals(FeedPage::class.java, pageCopy.returnType)

        val itemConstructor = ShortVideoItem::class.java.getConstructor(
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Float::class.javaObjectType,
            Boolean::class.javaPrimitiveType,
            List::class.java,
            String::class.java,
            Long::class.javaObjectType,
            Long::class.javaObjectType,
            Boolean::class.javaPrimitiveType,
            Long::class.javaObjectType,
        )
        assertEquals(
            listOf(
                "java.lang.String",
                "java.lang.String",
                "java.lang.String",
                "java.lang.String",
                "java.lang.String",
                "java.lang.String",
                "java.lang.String",
                "java.lang.Float",
                "boolean",
                "java.util.List",
                "java.lang.String",
                "java.lang.Long",
                "java.lang.Long",
                "boolean",
                "java.lang.Long",
            ),
            itemConstructor.parameterTypes.map { it.name },
        )
    }

    @Test
    fun v27CapabilityDescriptorsAreUnchangedAndGainNoRoundRobinMembers() {
        val replay = AnimeFeedReplaySource::class.java
        assertEquals(
            setOf("getReplayContextChanges", "prepareReplay", "resolveReplayItem"),
            replay.declaredMethods.map { it.name }.toSet(),
        )
        assertEquals(Flow::class.java, replay.getMethod("getReplayContextChanges").returnType)
        assertEquals(
            "java.lang.Object",
            replay.getMethod("prepareReplay", Continuation::class.java).returnType.name,
        )
        assertEquals(
            "java.lang.Object",
            replay.getMethod(
                "resolveReplayItem",
                String::class.java,
                FeedReplayContext::class.java,
                AnimeFilterList::class.java,
                Continuation::class.java,
            ).returnType.name,
        )
        assertEquals(
            setOf("accountScopeId"),
            AnimeFeedAccountIdentitySource::class.java.declaredMethods.map { it.name }.toSet(),
        )
        assertTrue(
            AnimeFeedAccountIdentitySource::class.java
                .getMethod("accountScopeId")
                .returnType == String::class.java,
        )

        val itemResult = FeedReplayItemResult::class.java
        assertTrue(itemResult.isAssignableFrom(FeedReplayItemResult.Available::class.java))
        FeedReplayItemResult.Available::class.java.getConstructor(ShortVideoItem::class.java)
        FeedReplayItemResult.Deferred::class.java.getConstructor(Long::class.javaObjectType)
        assertTrue(itemResult.isAssignableFrom(FeedReplayItemResult.FilteredOut::class.java))
        assertTrue(itemResult.isAssignableFrom(FeedReplayItemResult.Unavailable::class.java))
        assertTrue(itemResult.isAssignableFrom(FeedReplayItemResult.ContextChanged::class.java))

        val roundRobinMethodNames = setOf(
            "isRoundRobinEnabled",
            "prepareRoundRobin",
            "identifyRoundRobinItem",
            "getRoundRobinCatalog",
            "scanRoundRobinSource",
        )
        assertTrue(replay.declaredMethods.none { it.name in roundRobinMethodNames })
    }

    @Test
    fun roundRobinInterfaceDeclaresOnlyTheV28CapabilityMembers() {
        val replay = AnimeFeedReplaySource::class.java
        val roundRobin = AnimeFeedRoundRobinSource::class.java
        assertTrue(replay.isAssignableFrom(roundRobin))
        assertFalse(roundRobin.isAssignableFrom(replay))
        assertEquals(
            setOf(
                "isRoundRobinEnabled",
                "prepareRoundRobin",
                "identifyRoundRobinItem",
                "getRoundRobinCatalog",
                "scanRoundRobinSource",
            ),
            roundRobin.declaredMethods.map { it.name }.toSet(),
        )

        assertEquals(
            Boolean::class.javaPrimitiveType,
            roundRobin.getMethod("isRoundRobinEnabled", AnimeFilterList::class.java).returnType,
        )
        assertEquals(
            "java.lang.Object",
            roundRobin.getMethod("prepareRoundRobin", Continuation::class.java).returnType.name,
        )
        assertEquals(
            FeedRoundRobinIdentity::class.java,
            roundRobin.getMethod("identifyRoundRobinItem", String::class.java).returnType,
        )
        assertEquals(
            "java.lang.Object",
            roundRobin.getMethod(
                "getRoundRobinCatalog",
                FeedReplayContext::class.java,
                Boolean::class.javaPrimitiveType,
                Continuation::class.java,
            ).returnType.name,
        )
        assertEquals(
            "java.lang.Object",
            roundRobin.getMethod(
                "scanRoundRobinSource",
                FeedRoundRobinScanRequest::class.java,
                Continuation::class.java,
            ).returnType.name,
        )
    }
}
