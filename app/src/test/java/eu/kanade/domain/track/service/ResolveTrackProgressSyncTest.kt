package eu.kanade.domain.track.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ResolveTrackProgressSyncTest {

    private val resolver = ResolveTrackProgressSync()

    @Test
    fun `remote ahead returns MarkLocalUntil`() {
        resolver.resolve(
            local = 12.0,
            remote = 34.0,
            pullEnabled = true,
            trigger = ResolveTrackProgressSync.Trigger.OPEN_REFRESH,
        ) shouldBe ResolveTrackProgressSync.SyncAction.MarkLocalUntil(34.0)
    }

    @Test
    fun `local ahead returns PushRemoteTo`() {
        resolver.resolve(
            local = 34.0,
            remote = 12.0,
            pullEnabled = true,
            trigger = ResolveTrackProgressSync.Trigger.OPEN_REFRESH,
        ) shouldBe ResolveTrackProgressSync.SyncAction.PushRemoteTo(34.0)
    }

    @Test
    fun `equal returns NoOp`() {
        resolver.resolve(
            local = 34.0,
            remote = 34.0,
            pullEnabled = true,
            trigger = ResolveTrackProgressSync.Trigger.OPEN_REFRESH,
        ) shouldBe ResolveTrackProgressSync.SyncAction.NoOp
    }

    @Test
    fun `toggle off suppresses pull action`() {
        resolver.resolve(
            local = 12.0,
            remote = 34.0,
            pullEnabled = false,
            trigger = ResolveTrackProgressSync.Trigger.OPEN_REFRESH,
        ) shouldBe ResolveTrackProgressSync.SyncAction.NoOp
    }

    @Test
    fun `mark trigger keeps push behavior when pull toggle is off`() {
        resolver.resolve(
            local = 40.0,
            remote = 34.0,
            pullEnabled = false,
            trigger = ResolveTrackProgressSync.Trigger.LOCAL_MARK,
        ) shouldBe ResolveTrackProgressSync.SyncAction.PushRemoteTo(40.0)
    }
}
