package eu.kanade.tachiyomi.ui.download

import cafe.adriel.voyager.core.model.ScreenModel
import eu.kanade.tachiyomi.data.download.engine.DownloadEngineFacade
import eu.kanade.tachiyomi.data.download.engine.DownloadEngineSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Adapts [DownloadEngineFacade] for Compose consumption.
 * Contains no download business rules; delegates everything to the facade.
 */
class DownloadEngineScreenModel(
    private val facade: DownloadEngineFacade,
) : ScreenModel {

    /** Aggregated engine state exposed to the shared engine card. */
    val state: StateFlow<DownloadEngineSnapshot> = facade.state

    fun pauseAll() = facade.pauseAll()
    fun resumeAll() = facade.resumeAll()
    fun cancelAll() = facade.cancelAll()

    override fun onDispose() {
        facade.close()
    }
}
