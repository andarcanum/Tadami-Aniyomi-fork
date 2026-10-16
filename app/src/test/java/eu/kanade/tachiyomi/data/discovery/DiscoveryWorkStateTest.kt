package eu.kanade.tachiyomi.data.discovery

import androidx.work.WorkInfo
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.ui.discovery.claimManualRefreshFailure
import eu.kanade.tachiyomi.util.system.isActiveWorkState
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import java.util.UUID

class DiscoveryWorkStateTest {

    @Test
    fun `only current unclaimed failed work produces feedback including completion before opening`() {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        val media = DiscoveryMediaType.NOVEL
        val current = UUID.randomUUID()
        preferences.manualRefreshWorkId(media).set(current.toString())
        fun work(id: UUID, state: WorkInfo.State) = WorkInfo(id, state, emptySet(), androidx.work.Data.EMPTY)

        listOf(WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED).forEach {
            claimManualRefreshFailure(preferences, media, work(current, it)) shouldBe false
        }
        claimManualRefreshFailure(preferences, media, work(UUID.randomUUID(), WorkInfo.State.FAILED)) shouldBe false
        claimManualRefreshFailure(preferences, media, work(current, WorkInfo.State.FAILED)) shouldBe true
        claimManualRefreshFailure(preferences, media, work(current, WorkInfo.State.FAILED)) shouldBe false
        claimManualRefreshFailure(preferences, media, null) shouldBe false
    }

    @Test
    fun `only enqueued and running states keep manual refresh active`() {
        listOf(
            WorkInfo.State.ENQUEUED to true,
            WorkInfo.State.RUNNING to true,
            WorkInfo.State.SUCCEEDED to false,
            WorkInfo.State.FAILED to false,
            WorkInfo.State.CANCELLED to false,
            WorkInfo.State.BLOCKED to false,
        ).forEach { (state, expected) -> isActiveWorkState(state) shouldBe expected }
    }
}
