package mihon.data.extension.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.data.extension.model.AvailableExtensionData
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.extensionstore.model.ExtensionStore
import tachiyomi.core.common.util.system.logcat

/**
 * The result of fetching the extension lists of a set of stores.
 *
 * @param extensions The extensions of the stores that responded successfully.
 * @param failedStores The stores whose extension list could not be fetched. Consumers should
 * treat the fetch as partial when this is not empty, instead of assuming that the extensions of
 * these stores were removed.
 */
data class ExtensionStoreFetchResult(
    val extensions: List<AvailableExtensionData>,
    val failedStores: List<ExtensionStore>,
) {
    val isComplete: Boolean
        get() = failedStores.isEmpty()
}

class ExtensionStoreFetcher(
    private val service: ExtensionStoreService,
) {
    suspend fun fetchExtensions(stores: List<ExtensionStore>): ExtensionStoreFetchResult {
        if (stores.isEmpty()) return ExtensionStoreFetchResult(emptyList(), emptyList())
        val results = supervisorScope {
            stores.map { store ->
                async {
                    store to service.getExtensions(store).onFailure {
                        logcat(LogPriority.ERROR, it) {
                            "Failed to fetch extensions for store '${store.name} (${store.indexUrl})'"
                        }
                    }
                }
            }.awaitAll()
        }
        return ExtensionStoreFetchResult(
            extensions = results.flatMap { (_, result) -> result.getOrDefault(emptyList()) },
            failedStores = results.mapNotNull { (store, result) -> store.takeIf { result.isFailure } },
        )
    }
}
