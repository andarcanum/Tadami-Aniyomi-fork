package mihon.data.extension.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.data.extension.model.AvailableExtensionData
import mihon.data.extension.model.NetworkExtensionStore
import mihon.data.extension.model.NetworkLegacyExtension
import mihon.data.extension.model.NetworkLegacyExtensionRepo
import mihon.data.extension.model.toAvailableExtensionData
import mihon.domain.extensionstore.model.ExtensionStore
import okhttp3.OkHttpClient
import okio.BufferedSource
import okio.buffer
import okio.gzip
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

class ExtensionStoreService(
    private val client: OkHttpClient,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) {
    constructor(network: NetworkHelper, json: Json, protoBuf: ProtoBuf) : this(
        client = network.client,
        json = json,
        protoBuf = protoBuf,
    )

    suspend fun fetch(indexUrl: String): Result<ExtensionStore> {
        return try {
            if (indexUrl.endsWith("/index.min.json")) {
                val isLegacyArray = withDecodedBody(indexUrl) { source ->
                    source.peek().readByte() == 0x5B.toByte()
                }
                if (isLegacyArray) {
                    val repoUrl = indexUrl.replace("/index.min.json", "/repo.json")
                    return fetchFromUrl(requestUrl = repoUrl, storeIndexUrl = repoUrl)
                }
            }

            fetchFromUrl(requestUrl = indexUrl, storeIndexUrl = indexUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Failed to fetch extension store '$indexUrl'"
            }
            Result.failure(e)
        }
    }

    private suspend fun fetchFromUrl(requestUrl: String, storeIndexUrl: String): Result<ExtensionStore> {
        return try {
            val networkStore = withDecodedBody(requestUrl) { source ->
                when (source.peek().readByte()) {
                    0x5B.toByte() -> {
                        if (!requestUrl.endsWith("/index.min.json")) {
                            throw IllegalArgumentException("Provided legacy store url is not valid")
                        }
                        throw IllegalArgumentException("Legacy index must be resolved before fetchFromUrl")
                    }
                    0x7B.toByte() -> try {
                        json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(source.peek())
                    } catch (_: IllegalArgumentException) {
                        json.decodeFromBufferedSource<NetworkExtensionStore>(source)
                    }
                    else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                }
            }

            if (networkStore is NetworkLegacyExtensionRepo && networkStore.indexV2 != null) {
                return fetch(networkStore.indexV2)
            }

            Result.success(networkStore.toExtensionStore(storeIndexUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Failed to add extension store '$storeIndexUrl'"
            }
            Result.failure(e)
        }
    }

    suspend fun getExtensions(store: ExtensionStore): Result<List<AvailableExtensionData>> {
        return try {
            val extensions = if (store.extensionListUrl != null) {
                withDecodedBody(store.extensionListUrl!!) { source ->
                    when (source.peek().readByte()) {
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkExtensionStore.ExtensionList>(source)
                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(
                            source.readByteArray(),
                        )
                    }
                        .toAvailableExtensionData(store)
                }
            } else if (!store.isLegacy) {
                withDecodedBody(store.indexUrl) { source ->
                    when (source.peek().readByte()) {
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkExtensionStore>(source)
                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                    }
                        .extensionList!!
                        .toAvailableExtensionData(store)
                }
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                withDecodedBody("$storeBaseUrl/index.min.json") { source ->
                    json.decodeFromBufferedSource<List<NetworkLegacyExtension>>(source)
                        .map { it.toAvailableExtensionData(store, storeBaseUrl) }
                }
            }
            Result.success(extensions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> withDecodedBody(url: String, block: (BufferedSource) -> T): T {
        val response = client.newCall(GET(url)).awaitSuccess()
        return response.body.source().decompressIfGzipped().use(block)
    }

    internal fun BufferedSource.decompressIfGzipped(): BufferedSource {
        val isGzip = peek().use { peeked ->
            try {
                peeked.readShort().toInt() == 0x1f8b
            } catch (_: Exception) {
                false
            }
        }

        return if (isGzip) gzip().buffer() else this
    }
}
