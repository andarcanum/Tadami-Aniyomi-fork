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
    private val progressByMedia = DiscoveryMediaType.entries.associateWith { MutableStateFlow(DiscoveryProgress()) }

    fun progress(mediaType: DiscoveryMediaType): StateFlow<DiscoveryProgress> =
        progressByMedia.getValue(mediaType).asStateFlow()

    fun reset(mediaType: DiscoveryMediaType) {
        progressByMedia.getValue(mediaType).value = DiscoveryProgress()
    }

    fun update(
        isRunning: Boolean = true,
        mediaType: DiscoveryMediaType,
        stageText: String = "",
        current: Int = 0,
        total: Int = 0,
    ) {
        progressByMedia.getValue(mediaType).value = DiscoveryProgress(
            isRunning = isRunning,
            mediaType = mediaType,
            stageText = stageText,
            current = current,
            total = total,
        )
    }

    fun reset() {
        DiscoveryMediaType.entries.forEach(::reset)
    }
}
