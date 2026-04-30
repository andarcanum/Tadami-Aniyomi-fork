package eu.kanade.domain.track.service

class ResolveTrackProgressSync {

    enum class Trigger {
        OPEN_REFRESH,
        LOCAL_MARK,
    }

    sealed interface SyncAction {
        data object NoOp : SyncAction
        data class MarkLocalUntil(val value: Double) : SyncAction
        data class PushRemoteTo(val value: Double) : SyncAction
    }

    fun resolve(
        local: Double,
        remote: Double,
        pullEnabled: Boolean,
        trigger: Trigger,
    ): SyncAction {
        return when (trigger) {
            Trigger.OPEN_REFRESH -> {
                if (!pullEnabled) {
                    SyncAction.NoOp
                } else if (remote > local) {
                    SyncAction.MarkLocalUntil(remote)
                } else if (local > remote) {
                    SyncAction.PushRemoteTo(local)
                } else {
                    SyncAction.NoOp
                }
            }
            Trigger.LOCAL_MARK -> {
                if (local > remote) {
                    SyncAction.PushRemoteTo(local)
                } else {
                    SyncAction.NoOp
                }
            }
        }
    }
}
