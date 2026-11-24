package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeFeedRoundRobinSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.reels.anime.ReelsRoundRobinStateCorruptException
import tachiyomi.domain.reels.anime.interactor.ReelsRoundRobinScheduler
import tachiyomi.domain.reels.anime.model.ReelsActiveCheckpoint
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsConsumptionEntry
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.domain.reels.anime.model.ReelsInboxCapacityException
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalog
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalogSource
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCommit
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinHandle
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinMutation
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSnapshot
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinStop
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinUpdate
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionWindow
import tachiyomi.domain.reels.anime.repository.ReelsCandidateRepository
import tachiyomi.domain.reels.anime.repository.ReelsConsumptionRepository
import tachiyomi.domain.reels.anime.repository.ReelsHiddenRepository
import tachiyomi.domain.reels.anime.repository.ReelsRoundRobinRepository
import tachiyomi.domain.reels.anime.repository.ReelsSessionRepository
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsRoundRobinCoordinatorTest {

    private val defaultSourceId = 101L

    @Test
    fun four_scans_complete_in_reverse_and_publication_keeps_member_order() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b", "c", "d")
        val gates = mutableMapOf<String, CompletableDeferred<FeedRoundRobinScanResult>>()
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                gates.getOrPut(request.sourceKey) { CompletableDeferred() }.await()
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty-${request.sourceKey}", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.runCurrent()

        assertEquals(4, h.source.activeScans.size)
        assertEquals("at most four scans may run at once", 4, h.source.maxConcurrentScans)
        listOf("d", "c", "b", "a").forEachIndexed { index, key ->
            gates.getValue(key).complete(
                h.page(key, listOf("$key-1" to (100L + index)), historyExhausted = false, nextHeadCheckAt = null),
            )
        }
        h.idle()

        assertTrue(
            "pages=${h.pages.map { "${it.stop}/${it.loading}/${it.suffix.map { s -> s.id }}/${it.error}" }}",
            h.pages.any { it.suffix.isNotEmpty() },
        )
        val suffix = h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id }
        assertEquals(listOf("a-1", "b-1", "c-1", "d-1"), suffix)
        assertTrue(h.repo.mutationNames.contains("Catalog"))
        assertTrue(h.repo.mutationNames.contains("Import").not())
    }

    @Test
    fun cancelled_scan_result_is_never_committed_or_published() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        val gate = CompletableDeferred<FeedRoundRobinScanResult>()
        h.source.scanHandler = { gate.await() }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.advance(1L)
        assertEquals("events=${h.repo.events}, scans=${h.source.scanRequests}", 1, h.source.activeScans.size)

        h.coordinator.stop()
        h.idle()
        gate.complete(h.page("a", listOf("late" to 100L), historyExhausted = false, nextHeadCheckAt = null))
        h.idle()

        assertTrue(h.pages.none { page -> page.suffix.any { it.id == "late" } })
        assertTrue(h.repo.pageUpdates.none { update -> update.entries.any { it.candidate.itemId == "late" } })
        assertTrue(h.repo.entriesSeen().isEmpty())
    }

    @Test
    fun superseded_scan_from_a_replaced_session_is_discarded() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        val gate = CompletableDeferred<FeedRoundRobinScanResult>()
        h.source.scanHandler = { gate.await() }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.advance(1L)
        assertEquals("events=${h.repo.events}, scans=${h.source.scanRequests}", 1, h.source.activeScans.size)

        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("fresh" to 100L), historyExhausted = false, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }
        h.coordinator.attach(
            h.source,
            defaultSourceId,
            AnimeFilterList(listOf(TextFilter("q", "changed"))),
            unseenOnly = true,
            incognito = false,
        )
        h.idle()
        gate.complete(h.page("a", listOf("late" to 200L), historyExhausted = false, nextHeadCheckAt = null))
        h.idle()

        val suffix = h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id }
        assertEquals(listOf("fresh"), suffix)
        assertTrue(h.repo.pageUpdates.none { update -> update.entries.any { it.candidate.itemId == "late" } })
    }

    @Test
    fun horizon_does_not_trigger_another_scan_round() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        assertEquals(
            "pages=${h.pages.map { "${it.stop}/${it.loading}/${it.error}" }}",
            ReelsRoundRobinStop.RESERVATION_HORIZON,
            h.pages.last().stop,
        )
        val scansAtHorizon = h.source.scanRequests.size
        h.advance(60_000L)
        assertEquals(scansAtHorizon, h.source.scanRequests.size)
    }

    @Test
    fun wait_retry_does_not_busy_poll_before_the_due_time() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            FeedRoundRobinScanResult.Deferred(
                reason = FeedRoundRobinDeferral.RATE_LIMIT,
                retryAtEpochMillis = h.now() + 5_000L,
                providerWide = false,
            )
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.runCurrent()

        assertEquals(1, h.source.scanRequests.size)
        assertEquals(ReelsRoundRobinStop.WAIT_RETRY, h.pages.last().stop)
        h.advance(1_000L)
        assertEquals(1, h.source.scanRequests.size)
    }

    @Test
    fun capacity_pause_is_not_end_of_feed() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.repo.failPumpWith = ReelsInboxCapacityException(required = 2L, available = 1L)

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        val last = h.pages.last()
        assertEquals(ReelsRoundRobinStop.PAUSED, last.stop)
        assertTrue(last.error is ReelsInboxCapacityException)
        assertTrue(h.pages.none { it.stop == ReelsRoundRobinStop.EMPTY })
        val pumps = h.repo.pumpCount
        // Stay inside the 60 s periodic catalog reconciliation: that tick is a designed
        // operation, so it would legitimately pump again. A capacity pause itself must not
        // schedule an automatic retry/rescan before the catalog interval.
        h.advance(30_000L)
        assertEquals(pumps, h.repo.pumpCount)
    }

    @Test
    fun a_future_head_check_is_not_scanned_early_by_empty_recovery() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        var scans = 0
        h.source.scanHandler = {
            scans++
            if (scans == 1) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = 60_000L)
            } else {
                h.page("a", listOf("a2" to 200L), historyExhausted = true, nextHeadCheckAt = null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.runCurrent()

        assertEquals(1, h.source.scanRequests.size)
        assertEquals(ReelsRoundRobinStop.PAUSED, h.pages.last().stop)
        assertTrue(h.repo.resetCursorSources.isEmpty())
        h.advance(1_000L)
        assertEquals(1, h.source.scanRequests.size)

        h.advance(59_000L)
        h.idle()
        assertEquals(2, h.source.scanRequests.size)
        assertEquals(listOf("a2"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })
    }

    @Test
    fun a_rate_limited_chat_is_not_reset_before_its_retry_time() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b")
        var nullScansA = 0
        var rateLimitedA = false
        h.source.scanHandler = { request ->
            when {
                request.sourceKey == "a" && request.checkpoint == null -> {
                    nullScansA++
                    val id = if (nullScansA == 1) "a1" else "a2"
                    h.page("a", listOf(id to 200L), historyExhausted = false, nextHeadCheckAt = null)
                }
                request.sourceKey == "a" -> {
                    if (!rateLimitedA) {
                        rateLimitedA = true
                        FeedRoundRobinScanResult.Deferred(
                            FeedRoundRobinDeferral.RATE_LIMIT,
                            h.now() + 5_000L,
                            providerWide = false,
                        )
                    } else {
                        h.page("a", listOf("a3" to 100L), historyExhausted = true, nextHeadCheckAt = null)
                    }
                }
                request.checkpoint == null ->
                    h.page("b", listOf("b1" to 200L), historyExhausted = true, nextHeadCheckAt = null)
                else -> h.page("b", listOf("b2" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.runCurrent()
        h.coordinator.activate("a1")
        h.runCurrent()

        assertTrue("rechecks=${h.repo.rechecks}", h.repo.rechecks.none { "a" in it })
        assertTrue("resets=${h.repo.resetCursorSources}", h.repo.resetCursorSources.none { "a" in it })
        val rateLimited = requireNotNull(h.repo.lastRead).sources.first { it.key == "a" }
        assertNotNull(rateLimited.retryAt)
        assertEquals(FeedRoundRobinDeferral.RATE_LIMIT.name, rateLimited.retryReason)

        val scansBefore = h.source.scanRequests.size
        h.advance(1_000L)
        assertEquals(scansBefore, h.source.scanRequests.size)

        h.advance(5_000L)
        h.idle()
        assertTrue(
            "now=${h.now()} wake=${h.coordinator.debugWakeAt()} stop=${h.coordinator.debugLastStop()} " +
                "scans=${h.source.scanRequests.map { it.sourceKey + ':' + it.checkpoint }} " +
                "sources=${h.repo.lastRead?.sources?.map { it.key + ':' + it.retryAt + ':' + it.retryReason }} " +
                "pages=${h.pages.map { "${it.stop}/${it.suffix.map { item -> item.id }}" }} " +
                "events=${h.repo.events} debug=${h.coordinator.debugEvents}",
            h.source.scanRequests.size > scansBefore,
        )
        assertTrue(h.pages.last { it.suffix.isNotEmpty() }.suffix.any { it.id == "a3" })
    }

    @Test
    fun a_genuine_all_skips_page_is_recovered_once_and_not_on_every_activation() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        var scans = 0
        h.source.scanHandler = {
            scans++
            val id = "a$scans"
            h.page("a", listOf(id to (1_000L - scans)), historyExhausted = true, nextHeadCheckAt = null)
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        assertEquals(listOf("a1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        h.coordinator.activate("a1")
        h.idle()
        assertEquals(listOf(setOf("a")), h.repo.rechecks)
        assertEquals(listOf(setOf("a")), h.repo.resetCursorSources)
        assertEquals(listOf("a2"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })
        val scansAfterRecovery = h.source.scanRequests.size

        // Repeated activation inside the cooldown must not rewind the source again.
        h.coordinator.activate("a2")
        h.idle()
        h.coordinator.activate("a2")
        h.idle()
        assertEquals(listOf(setOf("a")), h.repo.rechecks)
        assertEquals(listOf(setOf("a")), h.repo.resetCursorSources)
        assertEquals(scansAfterRecovery, h.source.scanRequests.size)

        // After the cooldown a genuinely empty page may be recovered again.
        h.advance(30_000L)
        h.coordinator.activate("a2")
        h.idle()
        assertEquals(2, h.repo.rechecks.size)
    }

    @Test
    fun durable_resume_restores_frontier_playback_and_position() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()
        h.coordinator.sample("a1", 4_300L, foreground = true)
        h.coordinator.positionBoundary("a1", 4_300L)
        h.idle()

        h.coordinator.stop()
        h.idle()
        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        val page = h.pages.last()
        assertEquals("a1", page.frontierItemId)
        assertEquals("a1", page.playbackItemId)
        // The resume seek is published exactly once; later pages must not replay it.
        val firstResumed = h.pages.last { it.playbackItemId == "a1" && it.pendingSeekPositionMs != null }
        assertEquals(4_300L, firstResumed.pendingSeekPositionMs)
        assertNull(page.pendingSeekPositionMs)
        assertEquals("a1", h.repo.lastRead?.anchor?.itemId)
    }

    @Test
    fun foreground_samples_never_echo_a_resume_seek_into_later_pages() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            when {
                request.sourceKey == "a" && request.checkpoint == null ->
                    h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
                request.sourceKey == "b" && request.checkpoint == null ->
                    h.page("b", listOf("b1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
                else -> FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()

        // A 4 Hz foreground sample (and the visit boundary) is live position evidence, never
        // a resume instruction: the next publication must not carry it as a seek hint.
        val pagesBefore = h.pages.size
        h.coordinator.sample("a1", 1_000L, foreground = true)
        h.coordinator.positionBoundary("a1", 1_000L)

        // A normal page publication after the sample: a chat joins the catalog and its first
        // card extends the suffix, including the non-loading control page that ends the pass.
        h.source.catalogKeys = listOf("a", "b")
        h.coordinator.refresh()
        h.idle()

        val published = h.pages.drop(pagesBefore)
        val diagnosis = published.joinToString { page ->
            "${page.playbackItemId}/${page.pendingSeekPositionMs}/${page.loading}/${page.stop}"
        }
        assertTrue("pages=$diagnosis", published.any { !it.loading })
        assertTrue(
            "a sampled position leaked into a later page: " + published.map { it.pendingSeekPositionMs },
            published.all { it.pendingSeekPositionMs == null },
        )
        assertNull(h.pages.last().pendingSeekPositionMs)
    }

    @Test
    fun reactivation_publishes_a_stored_positive_position_as_a_one_shot_seek() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        val pagesBefore = h.pages.size
        h.coordinator.sample("a1", 1_500L, foreground = true)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()

        val seeks = h.pages.drop(pagesBefore).filter { it.pendingSeekPositionMs != null }
        assertEquals(1, seeks.size)
        assertEquals(1_500L, seeks.single().pendingSeekPositionMs)
        assertEquals("a1", seeks.single().playbackItemId)

        // The seek is one-shot: a later publication no longer carries it.
        h.coordinator.loadNext()
        h.idle()
        assertNull(h.pages.last().pendingSeekPositionMs)
        assertEquals(1, h.pages.drop(pagesBefore).count { it.pendingSeekPositionMs != null })
    }

    @Test
    fun a_stored_zero_position_does_not_produce_a_resume_seek() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        val pagesBefore = h.pages.size
        h.coordinator.sample("a1", 0L, foreground = true)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()

        assertTrue(
            "pages=${h.pages.drop(pagesBefore).map { "${it.playbackItemId}/${it.pendingSeekPositionMs}" }}",
            h.pages.size > pagesBefore,
        )
        assertTrue(
            "a zero position produced a seek: " + h.pages.drop(pagesBefore).map { it.pendingSeekPositionMs },
            h.pages.drop(pagesBefore).all { it.pendingSeekPositionMs == null },
        )
    }

    @Test
    fun same_v27_context_change_with_same_storage_key_does_not_reset_the_round() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()
        val firstKey = h.repo.openKeys.last()

        h.source.context = FeedReplayContext(h.source.context.accountId, "v27-next")
        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        assertEquals(firstKey.contextKey, h.repo.openKeys.last().contextKey)
        assertEquals("a1", h.repo.lastRead?.anchor?.itemId)
        assertEquals("a1", h.pages.last().frontierItemId)
        assertEquals("v27-next", h.source.catalogRequests.last().first.continuityKey)
    }

    @Test
    fun catalog_import_then_show_and_head_check_hint_is_persisted() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("chat-a")
        h.source.identifyMap["legacy"] = FeedRoundRobinIdentity("chat-a", 100L)
        h.candidates.backlog += h.candidate("legacy")
        val hint = 1_900_000L
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                FeedRoundRobinScanResult.Page(
                    candidates = listOf(
                        FeedRoundRobinCandidate(
                            item = h.item("canonical"),
                            identity = FeedRoundRobinIdentity("chat-a", 200L),
                            contentKey = "remote-unique",
                        ),
                    ),
                    checkpoint = "cp-canonical",
                    historyExhausted = true,
                    nextHeadCheckAtEpochMillis = hint,
                )
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }
        h.source.resolveHandler = { itemId -> FeedReplayItemResult.Available(h.item(itemId)) }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        val catalogIndex = h.events.indexOf("Catalog")
        val importIndex = h.events.indexOf("Import")
        val firstContentIndex = h.events.indexOfFirst { it.startsWith("publish:") && it != "publish:" }
        assertTrue(catalogIndex >= 0)
        assertTrue(importIndex > catalogIndex)
        assertTrue(
            "events=${h.events}, pages=${h.pages.map { "${it.stop}/${it.loading}/${it.suffix.size}/${it.error}" }}",
            firstContentIndex > importIndex,
        )
        assertTrue(
            h.repo.mutations.filterIsInstance<ReelsRoundRobinMutation.Import>()
                .any { it.identities.containsKey("legacy") },
        )

        val suffix = h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id }
        assertEquals(setOf("canonical", "legacy"), suffix.toSet())
        assertEquals(2, suffix.size)
        assertEquals(hint, h.repo.lastRead?.sources?.firstOrNull { it.key == "chat-a" }?.nextHeadCheckAt)
    }

    @Test
    fun nullable_identity_opens_the_fallback_scope_not_another_account_backlog() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.accountIdentity = "42"
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()
        assertEquals("acc:101:42", h.repo.openKeys.last().accountScopeId)

        h.source.accountIdentity = null
        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        assertEquals("src:101", h.repo.openKeys.last().accountScopeId)
        assertNull(h.repo.lastRead?.anchor)
    }

    @Test
    fun incognito_uses_the_memory_repository_without_persistent_writes() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                val ids = when (request.sourceKey) {
                    "a" -> listOf("a1" to 200L, "a-hidden" to 190L)
                    else -> listOf("b1" to 100L)
                }
                h.page(request.sourceKey, ids, historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }
        h.hidden.entries += ReelsHiddenEntry(
            sourceId = defaultSourceId,
            kind = ReelsHiddenEntry.KIND_VIDEO,
            value = "a-hidden",
            hiddenAt = Date(1_000L),
        )
        val seededCheckpoint = ReelsActiveCheckpoint(h.candidate("resume"), 2_000L, false, 1L)
        h.session.activeCheckpoint = seededCheckpoint
        h.session.currentHandle = ReelsSessionHandle(
            ReelsSessionKey("acc:101:42", defaultSourceId, "persistent-context"),
            "persistent-session",
            "persistent-owner",
            1L,
        )
        val persistentHandle = h.session.currentHandle

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = true)
        h.idle()

        assertTrue(h.repo.mutationNames.isEmpty())
        assertTrue(h.repo.openKeys.isEmpty())
        assertTrue(h.memory.events.contains("open"))
        assertTrue(h.memory.mutations.isNotEmpty())
        assertEquals(0, h.consumption.ensureScopeCalls)
        assertEquals(0, h.consumption.mergeScopeCalls)
        assertTrue(h.consumption.generationCalls >= 1)
        assertTrue(h.consumption.consumedIdCalls >= 1)
        assertTrue(h.hidden.getCalls >= 1)
        assertTrue(h.session.checkpoints.isEmpty())
        assertSame(seededCheckpoint, h.session.activeCheckpoint)
        assertSame(persistentHandle, h.session.currentHandle)

        val page = h.pages.last { it.suffix.isNotEmpty() }
        assertEquals(listOf("a1", "b1"), page.suffix.map { it.id })
        val owner = requireNotNull(page.owner)
        assertFalse(owner.persistEligible)
        assertEquals("acc:101:42", owner.accountScope)
    }

    @Test
    fun leaving_incognito_restores_the_previous_persistent_progress() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                val id = if (request.expectedContext.continuityKey == h.source.context.continuityKey) "a1" else "a1"
                h.page("a", listOf(id to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()
        h.coordinator.sample("a1", 3_200L, foreground = true)
        h.coordinator.positionBoundary("a1", 3_200L)
        h.idle()
        assertEquals("a1", h.pages.last().frontierItemId)

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = true)
        h.idle()
        assertFalse(requireNotNull(h.pages.last().owner).persistEligible)

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        val page = h.pages.last()
        assertEquals("a1", page.frontierItemId)
        assertEquals("a1", page.playbackItemId)
        val firstResumed = h.pages.last { it.playbackItemId == "a1" && it.pendingSeekPositionMs != null }
        assertEquals(3_200L, firstResumed.pendingSeekPositionMs)
        assertNull(page.pendingSeekPositionMs)
    }

    @Test
    fun backward_and_repeated_activation_keep_the_frontier_monotonic() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                val ids = when (request.sourceKey) {
                    "a" -> listOf("a1" to 200L, "a2" to 100L)
                    else -> listOf("b1" to 200L, "b2" to 100L)
                }
                h.page(request.sourceKey, ids, historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        val initial = h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id }
        assertEquals(listOf("a1", "b1", "a2", "b2"), initial)
        h.coordinator.activate("a1")
        h.coordinator.activate("b1")
        h.idle()
        assertEquals("b1", h.pages.last().frontierItemId)

        h.coordinator.activate("a1")
        h.idle()
        assertEquals("b1", h.pages.last().frontierItemId)
        assertEquals("a1", h.pages.last().playbackItemId)
        assertEquals("b1", h.repo.lastRead?.anchor?.itemId)

        h.coordinator.activate("b1")
        h.idle()
        assertEquals("b1", h.pages.last().frontierItemId)
        assertEquals("b1", h.pages.last().playbackItemId)
    }

    @Test
    fun replace_suffix_keeps_the_prefix_and_reinserts_an_unhidden_reservation() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                val id = if (request.sourceKey == "a") "a1" else "b1"
                h.page(request.sourceKey, listOf(id to 100L), historyExhausted = false, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty-${request.sourceKey}", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        assertEquals(listOf("a1", "b1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        h.coordinator.activate("a1")
        h.idle()
        assertEquals("a1", h.pages.last().frontierItemId)
        assertEquals(listOf("b1"), h.pages.last().suffix.map { it.id })

        h.hidden.entries += ReelsHiddenEntry(
            sourceId = defaultSourceId,
            kind = ReelsHiddenEntry.KIND_VIDEO,
            value = "b1",
            hiddenAt = Date(2_000L),
        )
        h.coordinator.refresh()
        h.idle()
        assertEquals("a1", h.pages.last().frontierItemId)
        assertTrue(h.pages.last().suffix.none { it.id == "b1" })
        assertTrue(h.repo.rechecks.last().contains("b"))

        h.hidden.entries.clear()
        val resetsBeforeUnhide = h.repo.resetCursorSources.size
        h.coordinator.refresh()
        h.idle()

        assertEquals("a1", h.pages.last().frontierItemId)
        assertEquals(
            "events=${h.repo.events}, pages=${h.pages.map {
                "${it.stop}/${it.loading}/${it.suffix.map { s -> s.id }}"
            }}",
            listOf("b1"),
            h.pages.last().suffix.map { it.id },
        )
        assertEquals("rechecks=${h.repo.rechecks} events=${h.events}", setOf("b"), h.repo.rechecks.last())
        // The re-admitted payload is still proven for this context and b is waiting for its
        // fruitless head check, so unhiding is a pure eligibility recheck: no cursor rewind
        // that would re-scan already-seen history before the scheduled head check.
        assertEquals(
            "resets=${h.repo.resetCursorSources} rechecks=${h.repo.rechecks} events=${h.events}",
            resetsBeforeUnhide,
            h.repo.resetCursorSources.size,
        )
    }

    @Test
    fun an_eligibility_update_with_the_same_row_count_still_rechecks_every_affected_source() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                val id = if (request.sourceKey == "a") "a1" else "b1"
                h.page(request.sourceKey, listOf(id to 100L), historyExhausted = false, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty-${request.sourceKey}", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        assertEquals(listOf("a1", "b1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        h.hidden.entries += ReelsHiddenEntry(defaultSourceId, ReelsHiddenEntry.KIND_VIDEO, "b1", Date(3_000L))
        h.repo.emitEligibility()
        h.idle()
        assertTrue(h.repo.rechecks.any { it == setOf("b") })

        h.hidden.entries.clear()
        h.hidden.entries += ReelsHiddenEntry(defaultSourceId, ReelsHiddenEntry.KIND_VIDEO, "a1", Date(4_000L))
        assertEquals(1, h.hidden.entries.size)
        h.repo.emitEligibility()
        h.idle()

        assertTrue(h.repo.rechecks.any { it == setOf("a", "b") })
        assertTrue(h.pages.last().suffix.none { it.id == "a1" })
    }

    @Test
    fun a_qualified_watch_of_the_active_item_does_not_rewind_its_source() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                val ids = if (request.sourceKey == "a") {
                    listOf("a1" to 300L, "a2" to 100L)
                } else {
                    listOf("b1" to 300L, "b2" to 100L)
                }
                h.page(request.sourceKey, ids, historyExhausted = false, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty-${request.sourceKey}", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()
        assertEquals(
            listOf("b1", "a2", "b2"),
            h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id },
        )

        h.consumption.consumed += setOf("a1", "b1")
        val rechecksBefore = h.repo.rechecks.size
        val resetsBefore = h.repo.resetCursorSources.size
        h.repo.emitEligibility()
        h.idle()

        val newRechecks = h.repo.rechecks.drop(rechecksBefore)
        assertTrue("rechecks=$newRechecks", newRechecks.isNotEmpty())
        newRechecks.forEach { assertEquals(setOf("b"), it) }
        assertEquals(emptyList<Set<String>>(), h.repo.resetCursorSources.drop(resetsBefore))
        assertEquals("a1", h.pages.last().frontierItemId)
        assertEquals("a1", h.pages.last().playbackItemId)
        assertEquals(listOf("a2", "b2"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })
    }

    @Test
    fun recontext_drops_the_old_display_prefix_and_starts_a_new_context() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()
        assertEquals("a1", h.pages.last().frontierItemId)
        val firstKey = h.repo.openKeys.last()

        h.coordinator.attach(
            h.source,
            defaultSourceId,
            AnimeFilterList(listOf(TextFilter("q", "different"))),
            unseenOnly = true,
            incognito = false,
        )
        assertNull("the initial replace after recontext has no frontier", h.pages.last().frontierItemId)
        h.idle()

        assertTrue(firstKey.contextKey != h.repo.openKeys.last().contextKey)
        assertNull(h.pages.last().frontierItemId)
        assertNull(h.repo.lastRead?.anchor)
    }

    @Test
    fun an_empty_page_with_a_real_reserved_turn_is_not_rewound_as_complete() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        assertEquals(listOf("a1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        val injected = AtomicInteger(0)
        h.repo.pumpOverride = { commit, mutation ->
            if (mutation is ReelsRoundRobinMutation.Pump && commit.page.isNotEmpty() &&
                injected.incrementAndGet() == 1
            ) {
                commit.copy(page = emptyList(), stop = ReelsRoundRobinStop.RESERVATION_HORIZON)
            } else {
                commit
            }
        }
        h.coordinator.refresh()
        h.idle()

        // A resolved item turn is not an all-skip page: rewinding would bypass the head checks.
        assertTrue("rechecks=${h.repo.rechecks}", h.repo.rechecks.isEmpty())
        assertTrue(h.repo.resetCursorSources.isEmpty())
        assertEquals(ReelsRoundRobinStop.PAUSED, h.pages.last().stop)
        assertTrue(h.pages.none { it.suffix.isEmpty() && it.stop == ReelsRoundRobinStop.EMPTY })
        assertEquals(listOf("a1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })
        assertTrue(h.repo.pumpCount < 10)
    }

    @Test
    fun pending_readiness_does_not_open_scan_or_fall_back() = runTest {
        val h = harness()
        h.source.readinessOverride = FeedRoundRobinReadiness.Pending(retryAtEpochMillis = null)

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        val last = h.pages.last()
        assertNull(last.owner)
        assertEquals(ReelsRoundRobinStop.PAUSED, last.stop)
        val error = last.error
        assertTrue("error=$error", error is ReelsRoundRobinNotReadyException)
        assertTrue(
            (error as ReelsRoundRobinNotReadyException).readiness is FeedRoundRobinReadiness.Pending,
        )
        assertTrue(h.repo.openKeys.isEmpty())
        assertTrue(h.source.catalogRequests.isEmpty())
        assertTrue(h.source.scanRequests.isEmpty())
    }

    @Test
    fun import_waits_for_a_catalog_snapshot() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("chat-a")
        h.source.identifyMap["legacy"] = FeedRoundRobinIdentity("chat-a", 100L)
        h.candidates.backlog += h.candidate("legacy")
        var catalogReady = false
        h.source.catalogHandler = { expectedContext, _ ->
            if (!catalogReady) {
                FeedRoundRobinCatalogResult.Deferred(retryAtEpochMillis = null)
            } else {
                FeedRoundRobinCatalogResult.Snapshot(
                    FeedRoundRobinCatalog(
                        context = expectedContext,
                        revision = "rev-chat-a",
                        complete = true,
                        sources = listOf(
                            FeedRoundRobinSourceRef(
                                key = "chat-a",
                                title = "Chat chat-a",
                                author = null,
                                contentRevision = null,
                                excluded = false,
                                previousKeys = emptyList(),
                            ),
                        ),
                        retryAtEpochMillis = null,
                    ),
                )
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.advance(1_000L)

        assertTrue(h.repo.openKeys.isNotEmpty())
        assertTrue(h.repo.mutations.filterIsInstance<ReelsRoundRobinMutation.Import>().isEmpty())
        assertTrue(h.pages.none { it.suffix.isNotEmpty() })
        assertTrue(h.source.scanRequests.isEmpty())

        catalogReady = true
        h.coordinator.refresh()
        h.idle()

        val catalogIndex = h.events.lastIndexOf("Catalog")
        val importIndex = h.events.indexOf("Import")
        assertTrue("events=${h.events}", catalogIndex >= 0)
        assertTrue("events=${h.events}", importIndex > catalogIndex)
        assertTrue(
            h.pages.last { it.suffix.isNotEmpty() }.suffix.any { it.id == "legacy" },
        )
    }

    @Test
    fun a_replay_context_emission_with_the_same_storage_key_keeps_the_round() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()
        val opens = h.repo.openKeys.size

        h.source.context = FeedReplayContext(h.source.context.accountId, "v27-next")
        h.source.emitContext(h.source.context)
        h.idle()

        assertEquals("a same-storage context emission must not reopen the session", opens, h.repo.openKeys.size)
        assertEquals("a1", h.repo.lastRead?.anchor?.itemId)
        assertEquals("a1", h.pages.last().frontierItemId)
        assertEquals("v27-next", h.source.catalogRequests.last().first.continuityKey)
    }

    @Test
    fun an_account_change_from_replay_context_fences_the_old_generation() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()
        val firstOwner = requireNotNull(h.pages.last { it.owner != null }.owner)
        val opens = h.repo.openKeys.size

        h.source.accountIdentity = "99"
        h.source.context = FeedReplayContext("99", "v27-account-b")
        h.source.emitContext(h.source.context)
        h.idle()

        assertEquals(opens + 1, h.repo.openKeys.size)
        assertEquals("acc:101:99", h.repo.openKeys.last().accountScopeId)
        val owner = requireNotNull(h.pages.last().owner)
        assertTrue("the old generation token must be replaced", owner.token != firstOwner.token)
        assertEquals("acc:101:99", owner.accountScope)
        assertNull(h.pages.last().frontierItemId)
    }

    @Test
    fun a_transient_identity_loss_during_a_recontext_keeps_the_pinned_scope() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        val firstOwner = requireNotNull(h.pages.last { it.owner != null }.owner)
        assertEquals("acc:101:42", firstOwner.accountScope)

        // The backend rotated its storage key while the source could no longer prove its
        // identity: a blank context account id is not proof of a switch, so the pin survives.
        h.source.accountIdentity = null
        h.source.context = FeedReplayContext("", "v27-next")
        h.source.storageKey = "tdlib-rr-v2"
        h.source.emitContext(h.source.context)
        h.idle()

        assertEquals("acc:101:42", h.repo.openKeys.last().accountScopeId)
        val owner = requireNotNull(h.pages.last().owner)
        assertTrue("the in-place reattach must fence the old generation", owner.token != firstOwner.token)
        assertEquals("acc:101:42", owner.accountScope)
    }

    @Test
    fun a_fallback_scope_upgrades_once_the_readiness_context_proves_the_account() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.accountIdentity = null
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        val firstOwner = requireNotNull(h.pages.last { it.owner != null }.owner)
        assertEquals("src:101", firstOwner.accountScope)

        h.source.accountIdentity = "42"
        h.source.context = FeedReplayContext("42", "v27-next")
        h.source.storageKey = "tdlib-rr-v2"
        h.source.emitContext(h.source.context)
        h.idle()

        assertEquals("acc:101:42", h.repo.openKeys.last().accountScopeId)
        val owner = requireNotNull(h.pages.last().owner)
        assertTrue("upgrading the scope must fence the fallback generation", owner.token != firstOwner.token)
        assertEquals("acc:101:42", owner.accountScope)
    }

    @Test
    fun hiding_an_author_rewinds_only_sources_with_proven_authors_and_unhide_restores() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                if (request.sourceKey == "a") {
                    h.authoredPage("a", listOf(Triple("a1", 100L, "@ann")))
                } else {
                    h.authoredPage("b", listOf(Triple("b1", 100L, "@bob")))
                }
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty-${request.sourceKey}", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        assertEquals(listOf("a1", "b1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        h.hidden.entries += ReelsHiddenEntry(
            defaultSourceId,
            ReelsHiddenEntry.KIND_AUTHOR,
            "@ann",
            Date(2_000L),
        )
        val rechecks = h.repo.rechecks.size
        h.repo.emitEligibility()
        h.idle()

        val newRechecks = h.repo.rechecks.drop(rechecks)
        assertTrue("rechecks=$newRechecks", newRechecks.isNotEmpty())
        newRechecks.forEach { assertEquals(setOf("a"), it) }
        assertEquals(listOf("b1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        h.hidden.entries.clear()
        val resets = h.repo.resetCursorSources.size
        h.repo.emitEligibility()
        h.idle()

        assertTrue(h.repo.resetCursorSources.drop(resets).contains(setOf("a")))
        assertEquals(listOf("a1", "b1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })
    }

    @Test
    fun load_next_runs_a_new_operation_while_waiting_for_a_retry() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = {
            FeedRoundRobinScanResult.Deferred(
                reason = FeedRoundRobinDeferral.RATE_LIMIT,
                retryAtEpochMillis = h.now() + 5_000L,
                providerWide = false,
            )
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.runCurrent()
        assertEquals(ReelsRoundRobinStop.WAIT_RETRY, h.pages.last().stop)
        val pumps = h.repo.pumpCount

        h.coordinator.loadNext()
        h.runCurrent()

        assertTrue(h.repo.pumpCount > pumps)
    }

    @Test
    fun video_completed_writes_a_completed_checkpoint_for_the_playback_item() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()

        h.coordinator.videoCompleted("a1")
        h.idle()

        val checkpoint = requireNotNull(h.session.checkpoints.lastOrNull())
        assertEquals("a1", checkpoint.item.itemId)
        assertTrue(checkpoint.completed)
    }

    @Test
    fun foreground_lost_flushes_the_latest_sampled_position() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()

        h.coordinator.sample("a1", 7_000L, foreground = true)
        h.advance(500L)
        h.coordinator.foregroundLost()
        h.idle()

        val checkpoint = requireNotNull(h.session.checkpoints.lastOrNull())
        assertEquals("a1", checkpoint.item.itemId)
        assertEquals(7_000L, checkpoint.positionMs)
    }

    @Test
    fun incognito_tracks_live_hidden_and_consumed_without_any_persistent_write() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                val id = if (request.sourceKey == "a") "a1" else "b1"
                h.page(request.sourceKey, listOf(id to 100L), historyExhausted = false, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty-${request.sourceKey}", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = true)
        h.idle()
        assertEquals(listOf("a1", "b1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        h.hidden.entries += ReelsHiddenEntry(
            defaultSourceId,
            ReelsHiddenEntry.KIND_VIDEO,
            "a1",
            Date(2_000L),
        )
        h.repo.emitEligibility()
        h.idle()
        assertEquals(listOf("b1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        h.hidden.entries.clear()
        h.repo.emitEligibility()
        h.idle()
        assertEquals(listOf("a1", "b1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        h.consumption.consumed += "b1"
        h.repo.emitEligibility()
        h.idle()
        assertEquals(listOf("a1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        h.consumption.consumed.clear()
        h.repo.emitEligibility()
        h.idle()
        assertEquals(listOf("a1", "b1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })

        assertTrue("persistent mutations=${h.repo.mutationNames}", h.repo.mutationNames.isEmpty())
        assertTrue("persistent opens=${h.repo.openKeys}", h.repo.openKeys.isEmpty())
    }

    @Test
    fun a_failed_eligibility_read_keeps_the_previous_snapshot_and_skips_the_rewind() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page(
                    "a",
                    listOf("a1" to 200L, "a2" to 100L),
                    historyExhausted = false,
                    nextHeadCheckAt = null,
                )
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.hidden.entries += ReelsHiddenEntry(
            defaultSourceId,
            ReelsHiddenEntry.KIND_VIDEO,
            "a1",
            Date(2_000L),
        )
        h.repo.emitEligibility()
        h.idle()
        assertTrue(h.pages.last().suffix.none { it.id == "a1" })
        val rechecks = h.repo.rechecks.size

        h.hidden.failReads = true
        h.hidden.entries.clear()
        h.repo.emitEligibility()
        h.idle()

        assertEquals("a failed read must not rewind", rechecks, h.repo.rechecks.size)
        assertTrue(
            "previously hidden item must stay hidden",
            h.pages.last().suffix.none { it.id == "a1" },
        )

        h.hidden.failReads = false
        h.repo.emitEligibility()
        h.idle()
        assertTrue(h.pages.last().suffix.any { it.id == "a1" })
    }

    @Test
    fun a_failed_consumed_read_defers_unseen_only_scans() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.consumption.failConsumedReads = true

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.runCurrent()

        assertTrue(h.source.scanRequests.isNotEmpty())
        assertTrue(h.repo.pageUpdates.isEmpty())
        assertEquals(ReelsRoundRobinStop.WAIT_RETRY, h.pages.last().stop)

        h.consumption.failConsumedReads = false
        h.advance(5_000L)
        h.idle()

        assertFalse(h.repo.pageUpdates.isEmpty())
    }

    @Test
    fun a_deferred_revalidation_waits_for_its_retry_time() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("chat-a")
        h.source.identifyMap["legacy"] = FeedRoundRobinIdentity("chat-a", 100L)
        h.candidates.backlog += h.candidate("legacy")
        var resolves = 0
        h.source.resolveHandler = { itemId ->
            resolves++
            if (resolves == 1) {
                FeedReplayItemResult.Deferred(h.now() + 5_000L)
            } else {
                FeedReplayItemResult.Available(h.item(itemId))
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.runCurrent()

        assertEquals(1, h.source.resolveRequests.size)
        assertEquals(ReelsRoundRobinStop.WAIT_RETRY, h.pages.last().stop)
        h.advance(1_000L)
        h.runCurrent()
        assertEquals(1, h.source.resolveRequests.size)

        h.advance(4_000L)
        h.idle()

        assertTrue(h.source.resolveRequests.size >= 2)
        assertTrue(h.pages.last { it.suffix.isNotEmpty() }.suffix.any { it.id == "legacy" })
    }

    @Test
    fun a_scan_page_with_a_foreign_identity_or_a_zero_order_key_is_rejected() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        var scans = 0
        h.source.scanHandler = {
            scans++
            if (scans == 1) {
                // The page mixes a card owned by another chat with an unorderable card: a
                // single bad identity poisons the whole page, so nothing may be committed.
                FeedRoundRobinScanResult.Page(
                    candidates = listOf(
                        FeedRoundRobinCandidate(
                            item = h.item("foreign"),
                            identity = FeedRoundRobinIdentity("b", 100L),
                            contentKey = null,
                        ),
                        FeedRoundRobinCandidate(
                            item = h.item("unordered"),
                            identity = FeedRoundRobinIdentity("a", 0L),
                            contentKey = null,
                        ),
                    ),
                    checkpoint = "cp-invalid",
                    historyExhausted = false,
                    nextHeadCheckAtEpochMillis = null,
                )
            } else {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.runCurrent()

        assertEquals(1, scans)
        assertEquals(ReelsRoundRobinStop.WAIT_RETRY, h.pages.last().stop)
        assertTrue(h.pages.none { page -> page.suffix.any { it.id == "foreign" || it.id == "unordered" } })
        assertTrue(h.repo.pageUpdates.isEmpty())
        assertTrue(h.repo.lastRead?.entries.orEmpty().isEmpty())

        h.advance(5_000L)
        h.idle()
        assertEquals(2, scans)
        assertEquals(listOf("a1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })
    }

    @Test
    fun a_revalidation_with_a_foreign_payload_is_not_attached_to_the_slot() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("chat-a")
        h.source.identifyMap["legacy"] = FeedRoundRobinIdentity("chat-a", 100L)
        h.candidates.backlog += h.candidate("legacy")
        h.source.resolveHandler = { itemId ->
            // The plugin resolves the requested card into a different item: accepting it would
            // attach a foreign payload to the reserved slot.
            FeedReplayItemResult.Available(h.item("foreign-$itemId"))
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.runCurrent()

        assertEquals(listOf("legacy"), h.source.resolveRequests)
        assertEquals(ReelsRoundRobinStop.WAIT_RETRY, h.pages.last().stop)
        assertTrue(h.pages.none { page -> page.suffix.any { it.id.startsWith("foreign") } })
        assertTrue(
            "entries=${h.repo.lastRead?.entries?.map { it.candidate.itemId }}",
            h.repo.lastRead?.entries.orEmpty().none { it.candidate.itemId.startsWith("foreign") },
        )
        val source = requireNotNull(h.repo.lastRead).sources.first { it.key == "chat-a" }
        assertNotNull("a deferred retry must be recorded for the poisoned resolve", source.retryAt)
    }

    @Test
    fun a_failed_replay_context_reattach_keeps_the_actor_alive() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        h.coordinator.activate("a1")
        h.idle()
        val pumps = h.repo.pumpCount

        h.repo.failNextOpen = true
        h.source.accountIdentity = "99"
        h.source.context = FeedReplayContext("99", "v27-account-b")
        h.source.emitContext(h.source.context)
        h.idle()

        val errorPage = h.pages.last()
        assertEquals(ReelsRoundRobinStop.PAUSED, errorPage.stop)
        assertNotNull(errorPage.error)
        assertEquals("a1", errorPage.frontierItemId)

        h.coordinator.loadNext()
        h.idle()

        assertTrue("a later command must still run", h.repo.pumpCount > pumps)
        assertEquals("a1", h.pages.last().frontierItemId)
    }

    @Test
    fun a_dead_revalidate_is_repaired_and_the_next_page_still_publishes() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page(
                    request.sourceKey,
                    listOf("${request.sourceKey}1" to 100L),
                    historyExhausted = false,
                    nextHeadCheckAt = null,
                )
            } else {
                h.page(
                    request.sourceKey,
                    listOf("${request.sourceKey}2" to 50L),
                    historyExhausted = true,
                    nextHeadCheckAt = null,
                )
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        assertEquals(
            listOf("a1", "b1", "a2", "b2"),
            h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id },
        )
        h.coordinator.activate("a1")
        h.idle()

        // The prepared future card loses its payload (pruned while the owner is inactive).
        h.storage.forgetPayloads(listOf("a2"))
        val pagesBefore = h.pages.size
        val scansBefore = h.source.scanRequests.size
        h.coordinator.loadNext()
        h.idle()

        assertTrue(
            "repairs=${h.repo.repairs} events=${h.repo.events}",
            h.repo.repairs.any { "a2" in it },
        )
        val after = h.pages.drop(pagesBefore)
        assertTrue(
            "pages=${after.map { it.suffix.map { item -> item.id } }}",
            after.any { page -> page.suffix.any { it.id == "b1" } },
        )
        assertTrue(
            "scans=${h.source.scanRequests.map { it.sourceKey + ':' + it.checkpoint }}",
            h.source.scanRequests.size > scansBefore,
        )
        // The repaired source is scanned again and the card returns instead of stalling.
        assertTrue(
            "pages=${h.pages.map { it.suffix.map { item -> item.id } }}",
            h.pages.last { it.suffix.isNotEmpty() }.suffix.any { it.id == "a2" },
        )
    }

    @Test
    fun the_catalog_is_reconciled_after_the_interval_without_a_manual_refresh() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.runCurrent()
        assertEquals(1, h.source.catalogRequests.size)

        h.advance(30_000L)
        h.idle()
        assertEquals(1, h.source.catalogRequests.size)

        val pagesBefore = h.pages.size
        h.source.catalogKeys = listOf("a", "b")
        h.advance(30_001L)
        h.idle()

        assertTrue("catalogs=${h.source.catalogRequests}", h.source.catalogRequests.size >= 2)
        assertTrue(h.source.catalogRequests.last().second)
        assertTrue(
            "a background tick must not pulse the loader: pages=${h.pages.drop(pagesBefore).map { it.loading }}",
            h.pages.drop(pagesBefore).none { it.loading },
        )
        assertTrue(
            "scans=${h.source.scanRequests.map { it.sourceKey }}",
            h.source.scanRequests.any { it.sourceKey == "b" },
        )

        h.coordinator.stop()
        h.idle()
        val catalogsAfterStop = h.source.catalogRequests.size
        h.advance(600_000L)
        h.idle()
        assertEquals(catalogsAfterStop, h.source.catalogRequests.size)
    }

    @Test
    fun a_manual_refresh_rearms_the_periodic_catalog_interval() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.runCurrent()
        assertEquals(1, h.source.catalogRequests.size)

        h.advance(30_000L)
        h.idle()
        assertEquals(1, h.source.catalogRequests.size)

        // A manual refresh reconciles immediately and restarts the 60 s interval.
        val pagesBefore = h.pages.size
        h.coordinator.refresh()
        h.idle()
        assertEquals(2, h.source.catalogRequests.size)
        assertTrue(
            "a manual refresh may still show loading: pages=${h.pages.drop(pagesBefore).map { it.loading }}",
            h.pages.drop(pagesBefore).any { it.loading },
        )

        // The original tick at 60 s was canceled by the refresh and must not fire now.
        h.advance(31_000L)
        h.idle()
        assertEquals(2, h.source.catalogRequests.size)

        // The re-armed tick fires 60 s after the manual refresh instead.
        h.advance(30_000L)
        h.idle()
        assertEquals(3, h.source.catalogRequests.size)
    }

    @Test
    fun a_deferred_periodic_catalog_tick_retries_by_force_and_keeps_membership_current() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        var catalogCalls = 0
        h.source.catalogHandler = { context, _ ->
            catalogCalls++
            if (catalogCalls == 2) {
                // The tick hits a FLOOD_WAIT-style deferral 25 s before membership can move.
                FeedRoundRobinCatalogResult.Deferred(h.now() + 25_000L)
            } else {
                catalogResult(context, if (catalogCalls >= 3) listOf("a", "b") else listOf("a"))
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        assertEquals(1, h.source.catalogRequests.size)
        assertFalse(h.source.catalogRequests.single().second)

        // Park the tick operation in PAUSED: the catalog is deferred and the pump cannot
        // commit. The old self-disarm dropped the only wake while a revision already existed.
        h.repo.failPumpWith = ReelsInboxCapacityException(required = 2L, available = 1L)
        val pagesBefore = h.pages.size
        h.advance(60_000L)
        h.idle()

        assertEquals(2, h.source.catalogRequests.size)
        assertTrue(h.source.catalogRequests.last().second)
        assertEquals("the deferral must leave the feed paused", ReelsRoundRobinStop.PAUSED, h.pages.last().stop)
        assertEquals("the retry wake must survive a PAUSED tick", 85_000L, h.coordinator.debugWakeAt())
        assertTrue(
            "a background tick must not pulse the loader: pages=${h.pages.drop(pagesBefore).map { it.loading }}",
            h.pages.drop(pagesBefore).none { it.loading },
        )

        h.repo.failPumpWith = null
        h.advance(25_000L)
        h.idle()

        assertEquals(3, h.source.catalogRequests.size)
        assertTrue(
            "the deferred catalog must be re-fetched, not skipped by its revision",
            h.source.catalogRequests.last().second,
        )
        assertTrue(
            "scans=${h.source.scanRequests.map { it.sourceKey }}",
            h.source.scanRequests.any { it.sourceKey == "b" },
        )
        assertTrue("membership must update", h.repo.lastRead?.sources?.any { it.key == "b" } == true)
        assertEquals("the 60 s tick is re-armed after the retry", 145_000L, h.coordinator.debugWakeAt())

        // No busy loop between the retry and the next interval, then the tick fires again.
        h.advance(30_000L)
        h.idle()
        assertEquals(3, h.source.catalogRequests.size)
        h.advance(30_000L)
        h.idle()
        assertEquals(4, h.source.catalogRequests.size)
        assertTrue(h.source.catalogRequests.last().second)
    }

    @Test
    fun a_corrupt_state_is_reset_and_rebuilt_without_touching_inbox_or_history() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.candidates.backlog += h.candidate("legacy")
        val backlogSize = h.candidates.backlog.size

        h.repo.failOpenWith = ReelsRoundRobinStateCorruptException("corrupt state row")
        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        assertEquals(1, h.repo.resetProjectionCalls.size)
        assertEquals(defaultSourceId, h.repo.resetProjectionCalls.single().second)
        assertTrue(h.pages.last().suffix.isNotEmpty())
        assertNull(h.pages.last().error)
        assertEquals(backlogSize, h.candidates.backlog.size)

        val pagesBefore = h.pages.size
        h.repo.failReadWith = ReelsRoundRobinStateCorruptException("corrupt frames json")
        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        assertEquals(2, h.repo.resetProjectionCalls.size)
        assertTrue(
            "pages=${h.pages.drop(pagesBefore).map { it.suffix.map { item -> item.id } }}",
            h.pages.drop(pagesBefore).any { it.suffix.isNotEmpty() },
        )
        assertEquals(backlogSize, h.candidates.backlog.size)
    }

    @Test
    fun a_fully_filtered_chat_becomes_a_budget_skip_without_a_rescan_loop() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a", "b")
        var aScans = 0
        h.source.scanHandler = { request ->
            if (request.sourceKey == "a") {
                aScans++
                h.page(
                    "a",
                    listOf((if (aScans == 1) "a1" else "a2") to 100L),
                    historyExhausted = true,
                    nextHeadCheckAt = null,
                )
            } else {
                h.page("b", listOf("b1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            }
        }
        h.hidden.entries += ReelsHiddenEntry(
            defaultSourceId,
            ReelsHiddenEntry.KIND_VIDEO,
            "a1",
            Date(1_000L),
        )

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()

        assertEquals(listOf("b1"), h.pages.last { it.suffix.isNotEmpty() }.suffix.map { it.id })
        assertEquals(1, h.source.scanRequests.count { it.sourceKey == "a" })
        val source = h.repo.lastRead?.sources?.first { it.key == "a" }
        assertTrue(source?.fruitlessScan == true)
        assertTrue((source?.nextHeadCheckAt ?: 0L) > h.now())

        h.advance(59_000L)
        h.idle()
        assertEquals(1, h.source.scanRequests.count { it.sourceKey == "a" })

        h.advance(ReelsRoundRobinScheduler.FRUITLESS_RESCAN_MS)
        h.idle()
        h.coordinator.refresh()
        h.idle()

        assertTrue(
            "scans=${h.source.scanRequests.map { it.sourceKey + ':' + it.refreshHead }}",
            h.source.scanRequests.count { it.sourceKey == "a" } >= 2,
        )
        assertTrue(h.source.scanRequests.last { it.sourceKey == "a" }.refreshHead)
        assertTrue(
            "pages=${h.pages.map { it.suffix.map { item -> item.id } }}",
            h.pages.last { it.suffix.isNotEmpty() }.suffix.any { it.id == "a2" },
        )
    }

    @Test
    fun the_memory_reset_cursor_clears_the_fruitless_budget_like_the_durable_adapter() = runTest {
        val storage = InMemoryReelsRoundRobinRepository()
        val key = ReelsSessionKey("acc:101:42", defaultSourceId, "ctx")
        val handle = storage.open(key, "owner", 1L, unseenOnly = true)
        val catalog = ReelsRoundRobinCatalog(
            revision = "rev-a",
            complete = true,
            sources = listOf(
                ReelsRoundRobinCatalogSource(
                    key = "a",
                    title = "Chat a",
                    author = null,
                    contentRevision = null,
                    excluded = false,
                    previousKeys = emptyList(),
                ),
            ),
        )
        var snapshot = storage.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(catalog, nowMs = 0L),
        ).snapshot
        snapshot = storage.mutate(
            handle,
            snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(
                    ReelsRoundRobinUpdate.Page(
                        sourceKey = "a",
                        fromCheckpoint = null,
                        nextCheckpoint = "cp-a",
                        entries = emptyList(),
                        historyExhausted = false,
                        nextHeadCheckAt = null,
                        fruitless = true,
                    ),
                ),
                limit = 12,
                nowMs = 1_000L,
            ),
        ).snapshot
        val fruitless = snapshot.sources.single()
        assertTrue(fruitless.fruitlessScan)
        assertEquals(1_000L + ReelsRoundRobinScheduler.FRUITLESS_RESCAN_MS, fruitless.nextHeadCheckAt)

        snapshot = storage.mutate(
            handle,
            snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(ReelsRoundRobinUpdate.ResetCursor("a")),
                limit = 12,
                nowMs = 2_000L,
            ),
        ).snapshot
        val reset = snapshot.sources.single()
        assertFalse(
            "an InvalidCheckpoint reset must not keep skipping the source as FILTER_BUDGET",
            reset.fruitlessScan,
        )
        assertNull(reset.nextHeadCheckAt)
    }

    @Test
    fun need_input_schedules_fast_wake_when_slice_budget_exhausted() = runTest {
        val h = harness()
        val sliceBudgetMs = 8_000L
        val needInputWakeMs = 250L
        h.source.catalogKeys = listOf("a")
        var attempts = 0
        h.source.scanHandler = { request ->
            attempts++
            if (attempts == 1) {
                delay(sliceBudgetMs + 1_000L)
            }
            h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.advance(sliceBudgetMs)
        h.runCurrent()

        assertTrue(
            "expected NEED_INPUT wake scheduled; debug=${h.coordinator.debugEvents}",
            h.coordinator.debugWakeAt() != null &&
                h.coordinator.debugEvents.any { it.contains("stop=NEED_INPUT") },
        )

        h.advance(needInputWakeMs)
        h.idle()

        assertTrue(
            "expected NEED_INPUT wake to recover and publish a1; pages=${h.pages.map { it.suffix.map { i -> i.id } }}",
            h.pages.any { page -> page.suffix.any { it.id == "a1" } },
        )
    }

    @Test
    fun foreground_lost_suspends_periodic_catalog_wake_and_foreground_gained_resumes_it() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        assertEquals(1, h.source.catalogRequests.size)
        assertNotNull(h.coordinator.debugWakeAt())

        h.coordinator.foregroundLost()
        h.idle()
        assertNull(h.coordinator.debugWakeAt())

        h.advance(90_000L)
        h.idle()
        assertEquals("catalog wake must not fire while backgrounded", 1, h.source.catalogRequests.size)

        h.coordinator.foregroundGained()
        h.idle()
        assertEquals(
            "overdue catalog wake must fire immediately on foreground return",
            2,
            h.source.catalogRequests.size,
        )
        assertNotNull(h.coordinator.debugWakeAt())
    }

    @Test
    fun foreground_lost_before_future_wake_rearms_remaining_interval_without_early_fire() = runTest {
        val h = harness()
        h.source.catalogKeys = listOf("a")
        h.source.scanHandler = { request ->
            if (request.checkpoint == null) {
                h.page("a", listOf("a1" to 100L), historyExhausted = true, nextHeadCheckAt = null)
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty", true, null)
            }
        }

        h.coordinator.attach(h.source, defaultSourceId, AnimeFilterList(), unseenOnly = true, incognito = false)
        h.idle()
        val armedAt = requireNotNull(h.coordinator.debugWakeAt())

        h.coordinator.foregroundLost()
        h.advance(20_000L)
        h.idle()
        assertNull(h.coordinator.debugWakeAt())
        assertEquals(1, h.source.catalogRequests.size)

        h.coordinator.foregroundGained()
        h.idle()
        assertEquals("future wake must not fire early on foreground return", 1, h.source.catalogRequests.size)
        assertEquals(armedAt, h.coordinator.debugWakeAt())

        h.advance(40_000L)
        h.idle()
        assertEquals(2, h.source.catalogRequests.size)
    }

    // ---------------------------------------------------------------------------------------
    // Harness and fakes
    // ---------------------------------------------------------------------------------------

    private fun TestScope.harness(): Harness {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val now = { testScheduler.currentTime }
        val events = mutableListOf<String>()
        val persistentStorage = InMemoryReelsRoundRobinRepository(
            resolvePayload = { scope, sourceId, itemId ->
                if (itemId.startsWith("legacy")) testCandidate(itemId, scope, sourceId) else null
            },
        )
        val memoryStorage = InMemoryReelsRoundRobinRepository()
        val repo = RecordingRepository(persistentStorage, events)
        val memory = RecordingRepository(memoryStorage, mutableListOf())
        val consumption = FakeConsumptionRepository()
        val hidden = FakeHiddenRepository()
        val session = FakeReelsSessionRepository()
        val candidates = FakeCandidateRepository()
        val source = FakeRoundRobinSource(defaultSourceId, now)
        val pages = mutableListOf<ReelsRoundRobinUiPage>()

        val coordinatorScope = CoroutineScope(
            SupervisorJob(backgroundScope.coroutineContext[Job]) + dispatcher,
        )
        val coordinator = ReelsRoundRobinCoordinator(
            scope = coordinatorScope,
            ioDispatcher = dispatcher,
            repository = repo,
            sessionRepository = session,
            consumptionRepository = consumption,
            hiddenRepository = hidden,
            candidateRepository = candidates,
            publish = { page ->
                pages += page
                events += "publish:" + page.suffix.joinToString(",") { it.id }
            },
            nowMs = now,
            wallClock = { Date(now()) },
            mainDispatcher = dispatcher,
            memoryRepositoryFactory = { locallyEligible ->
                memoryStorage.locallyEligible = locallyEligible
                memory
            },
        )

        return Harness(
            scope = this,
            dispatcher = dispatcher,
            now = now,
            source = source,
            repo = repo,
            memory = memory,
            consumption = consumption,
            hidden = hidden,
            session = session,
            candidates = candidates,
            pages = pages,
            events = events,
            storage = persistentStorage,
            coordinator = coordinator,
        )
    }

    private class Harness(
        val scope: TestScope,
        val dispatcher: CoroutineDispatcher,
        val now: () -> Long,
        val source: FakeRoundRobinSource,
        val repo: RecordingRepository,
        val memory: RecordingRepository,
        val consumption: FakeConsumptionRepository,
        val hidden: FakeHiddenRepository,
        val session: FakeReelsSessionRepository,
        val candidates: FakeCandidateRepository,
        val pages: MutableList<ReelsRoundRobinUiPage>,
        val events: MutableList<String>,
        val storage: InMemoryReelsRoundRobinRepository,
        val coordinator: ReelsRoundRobinCoordinator,
    ) {
        private val checkpointCounter = AtomicInteger(0)

        fun runCurrent() = scope.runCurrent()

        /**
         * Drains immediate work without chasing the periodic catalog timer forever. Tests that
         * need a due time advance it explicitly with [advance] (which runs everything strictly
         * before the target); the extra millisecond fires tasks scheduled exactly at that time.
         */
        fun idle() {
            scope.runCurrent()
            scope.advanceTimeBy(1L)
            scope.runCurrent()
        }

        fun advance(ms: Long) = scope.advanceTimeBy(ms)

        fun item(id: String, author: String? = null): ShortVideoItem = ShortVideoItem(
            id = id,
            title = "Title $id",
            author = author,
            videoUrl = "https://cdn.example/$id.mp4",
            videoUrlHd = null,
            posterUrl = "https://cdn.example/$id.jpg",
            posterUrlVertical = null,
            durationSec = 30f,
            hasAudio = true,
            webUrl = "https://web.example/$id",
        )

        fun candidate(id: String, scope: String = "acc:101:42", sourceId: Long = 101L): ReelsCandidate =
            testCandidate(id, scope, sourceId)

        fun page(
            sourceKey: String,
            items: List<Pair<String, Long>>,
            historyExhausted: Boolean,
            nextHeadCheckAt: Long?,
        ): FeedRoundRobinScanResult.Page {
            val candidates = items.map { (id, orderKey) ->
                FeedRoundRobinCandidate(
                    item = item(id),
                    identity = FeedRoundRobinIdentity(sourceKey, orderKey),
                    contentKey = null,
                )
            }
            return FeedRoundRobinScanResult.Page(
                candidates = candidates,
                checkpoint = "cp-$sourceKey-${checkpointCounter.incrementAndGet()}",
                historyExhausted = historyExhausted,
                nextHeadCheckAtEpochMillis = nextHeadCheckAt,
            )
        }

        fun authoredPage(
            sourceKey: String,
            items: List<Triple<String, Long, String?>>,
        ): FeedRoundRobinScanResult.Page {
            val candidates = items.map { (id, orderKey, author) ->
                FeedRoundRobinCandidate(
                    item = item(id, author),
                    identity = FeedRoundRobinIdentity(sourceKey, orderKey),
                    contentKey = null,
                )
            }
            return FeedRoundRobinScanResult.Page(
                candidates = candidates,
                checkpoint = "cp-$sourceKey-${checkpointCounter.incrementAndGet()}",
                historyExhausted = true,
                nextHeadCheckAtEpochMillis = null,
            )
        }
    }

    private fun catalogResult(
        context: FeedReplayContext,
        keys: List<String>,
    ): FeedRoundRobinCatalogResult.Snapshot = FeedRoundRobinCatalogResult.Snapshot(
        FeedRoundRobinCatalog(
            context = context,
            revision = "rev-" + keys.joinToString("-"),
            complete = true,
            sources = keys.map { key ->
                FeedRoundRobinSourceRef(
                    key = key,
                    title = "Chat $key",
                    author = null,
                    contentRevision = null,
                    excluded = false,
                    previousKeys = emptyList(),
                )
            },
            retryAtEpochMillis = null,
        ),
    )

    private class TextFilter(name: String, state: String) : AnimeFilter.Text(name, state)

    private class FakeRoundRobinSource(
        private val sourceId: Long,
        private val now: () -> Long,
    ) : AnimeFeedSource, AnimeFeedRoundRobinSource {

        override val id: Long = sourceId
        override val name: String = "Fake Round Robin"
        override val lang: String = "all"

        var context = FeedReplayContext("42", "v27-a")
        var storageKey = "tdlib-rr-v1"
        var accountIdentity: String? = "42"
        var readinessOverride: FeedRoundRobinReadiness? = null
        var catalogKeys: List<String> = emptyList()
        var catalogHandler: (suspend (FeedReplayContext, Boolean) -> FeedRoundRobinCatalogResult)? = null
        var scanHandler: suspend (FeedRoundRobinScanRequest) -> FeedRoundRobinScanResult = { request ->
            if (request.checkpoint == null) {
                FeedRoundRobinScanResult.Page(
                    candidates = listOf(
                        FeedRoundRobinCandidate(
                            item = ShortVideoItem(
                                id = "${request.sourceKey}-1",
                                title = "Title ${request.sourceKey}",
                                videoUrl = "https://cdn.example/${request.sourceKey}.mp4",
                                posterUrl = "https://cdn.example/${request.sourceKey}.jpg",
                            ),
                            identity = FeedRoundRobinIdentity(request.sourceKey, 100L),
                            contentKey = null,
                        ),
                    ),
                    checkpoint = "cp-${request.sourceKey}",
                    historyExhausted = true,
                    nextHeadCheckAtEpochMillis = null,
                )
            } else {
                FeedRoundRobinScanResult.Page(emptyList(), "cp-empty-${request.sourceKey}", true, null)
            }
        }
        var resolveHandler: suspend (String) -> FeedReplayItemResult = { itemId ->
            FeedReplayItemResult.Available(
                ShortVideoItem(
                    id = itemId,
                    title = "Title $itemId",
                    videoUrl = "https://cdn.example/$itemId.mp4",
                    posterUrl = "https://cdn.example/$itemId.jpg",
                ),
            )
        }

        val identifyMap = mutableMapOf<String, FeedRoundRobinIdentity>()
        val identifyCalls = mutableListOf<String>()
        val catalogRequests = mutableListOf<Pair<FeedReplayContext, Boolean>>()
        val scanRequests = mutableListOf<FeedRoundRobinScanRequest>()
        val activeScans = mutableListOf<FeedRoundRobinScanRequest>()
        val resolveRequests = mutableListOf<String>()
        var maxConcurrentScans = 0
        var prepareCalls = 0

        private val replayContextFlow = MutableSharedFlow<FeedReplayContext?>(extraBufferCapacity = 16)

        override val replayContextChanges: Flow<FeedReplayContext?> = replayContextFlow

        override fun accountScopeId(): String? = accountIdentity

        override suspend fun prepareReplay(): FeedReplayContext? = context

        override suspend fun resolveReplayItem(
            itemId: String,
            expectedContext: FeedReplayContext,
            filters: AnimeFilterList,
        ): FeedReplayItemResult {
            resolveRequests += itemId
            return resolveHandler(itemId)
        }

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override fun isRoundRobinEnabled(filters: AnimeFilterList): Boolean = true

        override suspend fun prepareRoundRobin(): FeedRoundRobinReadiness {
            prepareCalls++
            return readinessOverride ?: FeedRoundRobinReadiness.Ready(context, storageKey)
        }

        override fun identifyRoundRobinItem(itemId: String): FeedRoundRobinIdentity? {
            identifyCalls += itemId
            return identifyMap[itemId]
        }

        override suspend fun getRoundRobinCatalog(
            expectedContext: FeedReplayContext,
            forceRefresh: Boolean,
        ): FeedRoundRobinCatalogResult {
            catalogRequests += expectedContext to forceRefresh
            catalogHandler?.let { return it(expectedContext, forceRefresh) }
            return FeedRoundRobinCatalogResult.Snapshot(
                FeedRoundRobinCatalog(
                    context = expectedContext,
                    revision = "rev-${catalogKeys.joinToString("-")}",
                    complete = true,
                    sources = catalogKeys.map { key ->
                        FeedRoundRobinSourceRef(
                            key = key,
                            title = "Chat $key",
                            author = null,
                            contentRevision = null,
                            excluded = false,
                            previousKeys = emptyList(),
                        )
                    },
                    retryAtEpochMillis = null,
                ),
            )
        }

        override suspend fun scanRoundRobinSource(request: FeedRoundRobinScanRequest): FeedRoundRobinScanResult {
            scanRequests += request
            activeScans += request
            maxConcurrentScans = maxOf(maxConcurrentScans, activeScans.size)
            try {
                return scanHandler(request)
            } finally {
                activeScans -= request
            }
        }

        suspend fun emitContext(context: FeedReplayContext?) {
            replayContextFlow.emit(context)
        }
    }

    private class RecordingRepository(
        private val delegate: ReelsRoundRobinRepository,
        private val eventLog: MutableList<String>,
    ) : ReelsRoundRobinRepository {

        val events: MutableList<String> = eventLog
        val mutationNames = mutableListOf<String>()
        val mutations = mutableListOf<ReelsRoundRobinMutation>()
        val openKeys = mutableListOf<ReelsSessionKey>()
        val pageUpdates = mutableListOf<ReelsRoundRobinUpdate.Page>()
        val rechecks = mutableListOf<Set<String>>()
        val resetCursorSources = mutableListOf<Set<String>>()
        val resetProjectionCalls = mutableListOf<Pair<String, Long>>()
        val repairs = mutableListOf<Set<String>>()
        var pumpCount = 0
        var lastRead: ReelsRoundRobinSnapshot? = null
        var failPumpWith: Throwable? = null
        var failNextOpen = false
        var failOpenWith: Throwable? = null
        var failReadWith: Throwable? = null
        var pumpOverride: ((ReelsRoundRobinCommit, ReelsRoundRobinMutation) -> ReelsRoundRobinCommit)? = null
        private val eligibility = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

        fun entriesSeen(): List<String> = lastRead?.entries?.map { it.candidate.itemId }.orEmpty()

        suspend fun emitEligibility() {
            eligibility.emit(Unit)
        }

        override suspend fun open(
            key: ReelsSessionKey,
            ownerNonce: String,
            historyGeneration: Long,
            unseenOnly: Boolean,
        ): ReelsRoundRobinHandle {
            events += "open"
            openKeys += key
            if (failNextOpen) {
                failNextOpen = false
                throw IllegalStateException("open failed")
            }
            failOpenWith?.let { failure ->
                failOpenWith = null
                throw failure
            }
            return delegate.open(key, ownerNonce, historyGeneration, unseenOnly)
        }

        override suspend fun read(handle: ReelsRoundRobinHandle): ReelsRoundRobinSnapshot {
            failReadWith?.let { failure ->
                failReadWith = null
                throw failure
            }
            return delegate.read(handle).also { lastRead = it }
        }

        override suspend fun mutate(
            handle: ReelsRoundRobinHandle,
            expectedRevision: Long,
            change: ReelsRoundRobinMutation,
        ): ReelsRoundRobinCommit {
            val name = when (change) {
                is ReelsRoundRobinMutation.Catalog -> "Catalog"
                is ReelsRoundRobinMutation.Import -> "Import"
                is ReelsRoundRobinMutation.Pump -> "Pump"
                is ReelsRoundRobinMutation.Activate -> "Activate"
                is ReelsRoundRobinMutation.Recheck -> "Recheck"
                is ReelsRoundRobinMutation.Repair -> "Repair"
            }
            events += name
            mutationNames += name
            mutations += change
            when (change) {
                is ReelsRoundRobinMutation.Pump -> {
                    pumpCount++
                    pageUpdates += change.updates.filterIsInstance<ReelsRoundRobinUpdate.Page>()
                    val reset = change.updates.filterIsInstance<ReelsRoundRobinUpdate.ResetCursor>()
                    if (reset.isNotEmpty()) resetCursorSources += reset.mapTo(mutableSetOf()) { it.sourceKey }
                    failPumpWith?.let { throw it }
                }
                is ReelsRoundRobinMutation.Recheck -> rechecks += change.rewindSources
                is ReelsRoundRobinMutation.Repair -> repairs += change.invalidItemIds
                else -> Unit
            }
            val commit = delegate.mutate(handle, expectedRevision, change)
            lastRead = commit.snapshot
            return pumpOverride?.invoke(commit, change) ?: commit
        }

        override fun observeEligibility(scope: String, sourceId: Long): Flow<Unit> = eligibility

        override suspend fun release(handle: ReelsRoundRobinHandle) {
            events += "release"
            delegate.release(handle)
        }

        override suspend fun clearScope(scope: String) {
            events += "clearScope"
            delegate.clearScope(scope)
        }

        override suspend fun resetProjection(scope: String, sourceId: Long) {
            events += "resetProjection"
            resetProjectionCalls += scope to sourceId
            delegate.resetProjection(scope, sourceId)
        }
    }

    private class FakeConsumptionRepository : ReelsConsumptionRepository {
        var generationValue = 1L
        val consumed = mutableSetOf<String>()
        var ensureScopeCalls = 0
        var mergeScopeCalls = 0
        var generationCalls = 0
        var consumedIdCalls = 0
        var failConsumedReads = false

        override suspend fun generation(scope: String): Long {
            generationCalls++
            return generationValue
        }

        override suspend fun ensureScope(scope: String, policyVersion: Int): Long {
            ensureScopeCalls++
            return generationValue
        }

        override suspend fun markWatched(
            scope: String,
            sourceId: Long,
            itemId: String,
            at: Date,
            surface: String?,
            policyVersion: Int,
            generation: Long,
        ): Boolean = true

        override suspend fun markSkipped(
            scope: String,
            sourceId: Long,
            itemId: String,
            at: Date,
            surface: String?,
            policyVersion: Int,
            generation: Long,
        ): Boolean = true

        override suspend fun consumedIds(
            scope: String,
            sourceId: Long,
            itemIds: Collection<String>,
        ): Set<String> {
            consumedIdCalls++
            if (failConsumedReads) throw IllegalStateException("consumed read failed")
            return consumed.intersect(itemIds.toSet())
        }

        override suspend fun entry(scope: String, sourceId: Long, itemId: String): ReelsConsumptionEntry? = null

        override suspend fun countConsumed(scope: String, sourceId: Long): Long = 0L

        override suspend fun mergeScope(fromScope: String, toScope: String, sourceId: Long) {
            mergeScopeCalls++
        }

        override suspend fun resetHistory(scope: String, policyVersion: Int): Long {
            generationValue++
            return generationValue
        }
    }

    private class FakeHiddenRepository : ReelsHiddenRepository {
        val entries = mutableListOf<ReelsHiddenEntry>()
        var getCalls = 0
        var failReads = false

        override fun subscribeAll(): Flow<List<ReelsHiddenEntry>> = emptyFlow()

        override suspend fun getBySource(sourceId: Long): List<ReelsHiddenEntry> {
            getCalls++
            if (failReads) throw IllegalStateException("hidden read failed")
            return entries.filter { it.sourceId == sourceId }
        }

        override suspend fun insert(entry: ReelsHiddenEntry) {
            entries += entry
        }

        override suspend fun delete(sourceId: Long, kind: String, value: String) {
            entries.removeAll { it.sourceId == sourceId && it.kind == kind && it.value == value }
        }

        override suspend fun deleteBySource(sourceId: Long) {
            entries.removeAll { it.sourceId == sourceId }
        }

        override suspend fun deleteAll() {
            entries.clear()
        }
    }

    private class FakeCandidateRepository : ReelsCandidateRepository {
        val backlog = mutableListOf<ReelsCandidate>()
        var unconsumedCalls = 0

        override suspend fun ingest(scope: String, sourceId: Long, candidates: List<ReelsCandidate>): Int {
            backlog += candidates
            return candidates.size
        }

        override suspend fun unconsumed(scope: String, sourceId: Long, limit: Int): List<ReelsCandidate> {
            unconsumedCalls++
            return backlog.filter { it.accountScopeId == scope && it.sourceId == sourceId }.take(limit)
        }

        override suspend fun saveResumePosition(
            scope: String,
            sourceId: Long,
            itemId: String,
            positionMs: Long,
        ) = Unit

        override suspend fun remove(scope: String, sourceId: Long, itemIds: Collection<String>) {
            backlog.removeAll { it.itemId in itemIds }
        }

        override suspend fun count(scope: String, sourceId: Long): Long = backlog.size.toLong()

        override suspend fun clear(scope: String) {
            backlog.removeAll { it.accountScopeId == scope }
        }
    }
}

private fun testCandidate(id: String, scope: String, sourceId: Long): ReelsCandidate =
    ReelsCandidate(
        accountScopeId = scope,
        sourceId = sourceId,
        itemId = id,
        title = "Title $id",
        author = null,
        videoUrl = "",
        videoUrlHd = null,
        posterUrl = "",
        posterUrlVertical = null,
        webUrl = "https://web.example/$id",
        durationSec = 30.0,
        hasAudio = true,
        createdAtEpochSec = 1_700_000_000L,
        discoveredAt = Date(1_000L),
        tagsJson = "[]",
    )
