package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.discovery.model.DiscoveryMediaType

data class DiscoveryProgress(
    val isRunning: Boolean = false,
    val mediaType: DiscoveryMediaType? = null,
    val stageText: String = "",
    val current: Int = 0,
    val total: Int = 0,
)

object DiscoveryProgressTracker {
    private val _progress = MutableStateFlow(DiscoveryProgress())
    val progress: StateFlow<DiscoveryProgress> = _progress.asStateFlow()

    fun update(
        isRunning: Boolean = true,
        mediaType: DiscoveryMediaType? = null,
        stageText: String = "",
        current: Int = 0,
        total: Int = 0,
    ) {
        _progress.value = DiscoveryProgress(
            isRunning = isRunning,
            mediaType = mediaType,
            stageText = stageText,
            current = current,
            total = total,
        )
    }

    fun reset() {
        _progress.value = DiscoveryProgress()
    }
}
