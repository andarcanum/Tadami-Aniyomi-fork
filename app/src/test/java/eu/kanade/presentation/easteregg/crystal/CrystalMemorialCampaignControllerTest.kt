package eu.kanade.presentation.easteregg.crystal

import eu.kanade.domain.easteregg.crystal.CrystalCampaignConditions
import eu.kanade.domain.easteregg.crystal.CrystalCampaignController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMedia
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRepository
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState
import tachiyomi.domain.easteregg.crystal.CrystalProgressCounter
import tachiyomi.domain.easteregg.crystal.CrystalProgressEntry
import tachiyomi.domain.easteregg.crystal.CrystalProgressItem
import tachiyomi.domain.easteregg.crystal.CrystalProgressResult
import tachiyomi.domain.easteregg.crystal.CrystalProgressSource
import tachiyomi.domain.easteregg.crystal.CrystalTitleInput

class CrystalMemorialCampaignControllerTest {
    private val saved = MutableStateFlow(CrystalMemorialState())
    private val repository = mockk<CrystalMemorialRepository>(relaxed = true) {
        every { observeState() } returns saved
        coEvery { getState() } answers { saved.value }
        coEvery { makeEligible(any()) } answers {
            saved.value.copy(eligibilityReason = firstArg(), eligibleAt = 123).also { saved.value = it }
        }
    }
    private class Progress : CrystalProgressSource {
        var count = 9
        var calls = 0
        var duringSnapshot: () -> Unit = {}
        override fun changes() = MutableStateFlow(Unit)
        override suspend fun snapshot(): CrystalProgressResult {
            calls++
            duringSnapshot()
            return CrystalProgressCounter().evaluate(
                listOf(
                    CrystalProgressEntry(
                        7,
                        CrystalTitleInput(CrystalMedia.MANGA, "Кошечка из Сакурасо"),
                        (1..count).map { CrystalProgressItem(it.toLong(), it.toDouble(), "chapter/$it", true) },
                        true,
                    ),
                ),
            )
        }
    }
    private val progress = Progress()
    private val safe = CrystalCampaignConditions(true, true, true, true, false, false, true, true, true)
    private fun controller(
        version: Int = 101,
        release: Boolean = true,
        codes: Set<Int> = setOf(100),
        debug: Boolean = true,
    ) =
        CrystalCampaignController(repository, progress, version, release, codes, debug)

    @Test fun targetReleaseCreatesEligibilityWithoutTitleReadsOrXpGrant() = runTest {
        val result = controller(100).refresh(safe)
        assertEquals(CrystalEligibilityReason.RELEASE, result.state.eligibilityReason)
        assertTrue(result.shouldOffer)
        assertEquals(0, progress.calls)
        coVerify(exactly = 0) { repository.completeMemorial(any()) }
    }

    @Test fun skippedReleaseRequiresThresholdFromSavedProgress() = runTest {
        val c = controller()
        assertFalse(c.refresh(safe).state.isEligible)
        progress.count = 10
        val result = c.refresh(safe)
        assertTrue(result.shouldOffer)
        assertEquals(CrystalEligibilityReason.TITLE, result.state.eligibilityReason)
        assertEquals(10, result.progress!!.eligibleEntries.single().distinctCompleted)
        coVerify(exactly = 0) { repository.completeMemorial(any()) }
    }

    @Test fun privateLockedBackgroundAndRestoringSessionsNeverScanOrMutate() = runTest {
        progress.count = 10
        val c = controller()
        val blocked = listOf(
            safe.copy(incognito = true),
            safe.copy(restoring = true),
            safe.copy(resumed = false),
            safe.copy(unlocked = false),
            safe.copy(migrationsReady = false),
            safe.copy(catalogueReady = false),
        )
        blocked.forEach { assertFalse(c.refresh(it).shouldOffer) }
        assertEquals(0, progress.calls)
        coVerify(exactly = 0) { repository.makeEligible(any()) }
    }

    @Test fun finishingReaderMakesGiftPendingButNeverInterruptsContent() = runTest {
        progress.count = 10
        val c = controller()
        val pending = c.refresh(safe.copy(atRoot = false))
        assertTrue(pending.state.isEligible)
        assertFalse(pending.shouldOffer)
        val root = c.refresh(safe)
        assertTrue(root.shouldOffer)
        assertEquals(1, progress.calls)
    }

    @Test fun startupAndModalFocusAreRequiredForPresentationButNotEntitlement() = runTest {
        val c = controller(100)
        assertFalse(c.refresh(safe.copy(startupSettled = false)).shouldOffer)
        assertFalse(c.refresh(safe.copy(windowFocused = false)).shouldOffer)
        assertTrue(c.refresh(safe).shouldOffer)
    }

    @Test fun retainedAndConsumedEntitlementsNeverRequireAnotherRead() = runTest {
        saved.value = CrystalMemorialState(CrystalEligibilityReason.RELEASE, 1, automaticPresentationConsumedAt = 2)
        val result = controller(500).refresh(safe)
        assertTrue(result.state.isEligible)
        assertFalse(result.shouldOffer)
        assertEquals(0, progress.calls)
    }

    @Test fun grantedGiftAndUnconfiguredProductionNeverScan() = runTest {
        progress.count = 10
        assertFalse(controller(codes = emptySet()).refresh(safe).shouldOffer)
        assertFalse(controller(release = false).refresh(safe).shouldOffer)
        saved.value = CrystalMemorialState(CrystalEligibilityReason.TITLE, 1, grantedAt = 2, grantedXp = 999)
        assertFalse(controller().refresh(safe).shouldOffer)
        assertEquals(0, progress.calls)
    }

    @Test fun duplicateRefreshesCreateOneEligibilityAndOnePresentationClaim() = runTest {
        progress.count = 10
        val c = controller()
        val results = (1..12).map { async(Dispatchers.Default) { c.refresh(safe) } }.awaitAll()
        coVerify(exactly = 1) { repository.makeEligible(CrystalEligibilityReason.TITLE) }
        assertEquals(1, results.count { c.claimPresentation(it, saved.value, safe) })
        val stale = results.first()
        assertFalse(c.claimPresentation(stale, saved.value.copy(generation = 5), safe))
    }

    @Test fun debugUsesActualProgressWithoutAssigningReleaseOrCallingGrant() = runTest {
        progress.count = 10
        val c = controller(codes = emptySet(), release = false)
        val result = c.checkSavedProgressForDebug(safe.copy(atRoot = false))
        assertTrue(result.state.isEligible)
        assertEquals(CrystalEligibilityReason.TITLE, result.state.eligibilityReason)
        assertFalse(result.shouldOffer)
        assertFalse(c.configured)
        coVerify(exactly = 0) { repository.completeMemorial(any()) }
    }

    @Test fun debugCheckCannotBeCalledFromReleaseOrWhileRestoring() = runTest {
        var denied = false
        try {
            controller(debug = false).checkSavedProgressForDebug(safe)
        } catch (_: IllegalStateException) {
            denied =
                true
        }
        assertTrue(denied)
        assertFalse(controller().checkSavedProgressForDebug(safe.copy(restoring = true)).state.isEligible)
        assertEquals(0, progress.calls)
    }

    @Test fun restoreStartingDuringSnapshotCannotActivatePartialProgress() = runTest {
        progress.count = 10
        var conditions = safe
        progress.duringSnapshot = { conditions = safe.copy(restoring = true) }
        val result = controller().refresh(safe) { conditions }
        assertFalse(result.state.isEligible)
        coVerify(exactly = 0) { repository.makeEligible(any()) }
    }

    @Test fun restoreFinishingDuringDebugSnapshotAlsoRequiresFreshCounts() = runTest {
        progress.count = 10
        var conditions = safe
        progress.duringSnapshot = { conditions = safe.copy(restoreRevision = 1) }
        val result = controller().checkSavedProgressForDebug(safe) { conditions }
        assertFalse(result.state.isEligible)
        coVerify(exactly = 0) { repository.makeEligible(any()) }
    }

    @Test fun privacyChangingDuringReceiptReadPreventsTheMediaScan() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var gate = safe
        coEvery { repository.getState() } coAnswers {
            entered.complete(Unit)
            release.await()
            saved.value
        }
        val task = async { controller().refresh(safe) { gate } }
        entered.await()
        gate = safe.copy(incognito = true)
        release.complete(Unit)
        task.await()
        assertEquals(0, progress.calls)
        coVerify(exactly = 0) { repository.makeEligible(any()) }
    }

    @Test fun restoreStartingDuringReceiptReadPreventsExactReleaseEligibility() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var gate = safe
        coEvery { repository.getState() } coAnswers {
            entered.complete(Unit)
            release.await()
            saved.value
        }
        val task = async { controller(100).refresh(safe) { gate } }
        entered.await()
        gate = safe.copy(restoring = true)
        release.complete(Unit)
        task.await()
        assertEquals(0, progress.calls)
        coVerify(exactly = 0) { repository.makeEligible(any()) }
    }

    @Test fun debugScanRechecksPrivacyAfterWaitingForReceipt() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var gate = safe
        coEvery { repository.getState() } coAnswers {
            entered.complete(Unit)
            release.await()
            saved.value
        }
        val task = async { controller().checkSavedProgressForDebug(safe) { gate } }
        entered.await()
        gate = safe.copy(incognito = true)
        release.complete(Unit)
        task.await()
        assertEquals(0, progress.calls)
    }

    @Test fun waitingBehindAnotherRefreshCannotUseOldPrivacyConditions() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var gate = safe
        coEvery { repository.getState() } coAnswers {
            entered.complete(Unit)
            release.await()
            saved.value
        }
        val c = controller()
        val first = async(start = CoroutineStart.UNDISPATCHED) { c.refresh(safe) { gate } }
        entered.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { c.refresh(safe) { gate } }
        gate = safe.copy(incognito = true)
        release.complete(Unit)
        first.await()
        second.await()
        assertEquals(0, progress.calls)
    }

    @Test fun privacyChangingDuringSecondReceiptReadPreventsTitlePersistence() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var gate = safe
        var reads = 0
        progress.count = 10
        coEvery { repository.getState() } coAnswers {
            if (++reads == 2) {
                entered.complete(Unit)
                release.await()
            }
            saved.value
        }
        val task = async { controller().refresh(safe) { gate } }
        entered.await()
        gate = safe.copy(incognito = true)
        release.complete(Unit)
        task.await()
        assertEquals(1, progress.calls)
        coVerify(exactly = 0) { repository.makeEligible(any()) }
    }

    @Test fun debugRestoreChangingDuringSecondReceiptReadPreventsTitlePersistence() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var gate = safe
        var reads = 0
        progress.count = 10
        coEvery { repository.getState() } coAnswers {
            if (++reads == 2) {
                entered.complete(Unit)
                release.await()
            }
            saved.value
        }
        val task = async { controller().checkSavedProgressForDebug(safe) { gate } }
        entered.await()
        gate = safe.copy(restoring = true)
        release.complete(Unit)
        task.await()
        assertEquals(1, progress.calls)
        coVerify(exactly = 0) { repository.makeEligible(any()) }
    }

    @Test fun restoreCompletionTriggersBackfillWithoutRReinstall() = runTest {
        progress.count = 10
        val c = controller()
        assertFalse(c.refresh(safe.copy(restoring = true)).state.isEligible)
        assertEquals(CrystalEligibilityReason.TITLE, c.refresh(safe).state.eligibilityReason)
    }
}
