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
import mihon.data.extension.model.BaseNetworkExtensionStore
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
import java.net.URI
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

    /**
     * Fetches the store metadata behind [indexUrl] using a single request for the index itself.
     *
     * Supported formats:
     * - Legacy min index (JSON array): resolves the sibling `repo.json` for metadata. When
     *   `repo.json` is unavailable, a placeholder legacy store is synthesized so that the
     *   repo can still be added and used.
     * - Legacy `repo.json` (JSON object with `meta`): used directly, following `index_v2` redirects.
     * - Store index (JSON object or protobuf): used directly.
     */
    suspend fun fetch(indexUrl: String): Result<ExtensionStore> {
        return try {
            val networkStore: BaseNetworkExtensionStore? = withDecodedBody(indexUrl) { source ->
                when (source.peek().readByte()) {
                    JSON_ARRAY_PREFIX -> null // Legacy min index; metadata lives in repo.json
                    JSON_OBJECT_PREFIX -> decodeJsonStore(source)
                    else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                }
            }

            val indexV2 = (networkStore as? NetworkLegacyExtensionRepo)?.indexV2
            when {
                networkStore == null -> {
                    if (!indexUrl.endsWith("/index.min.json")) {
                        throw IllegalArgumentException("Provided legacy store url is not valid")
                    }
                    fetchLegacyRepoDetails(indexUrl.removeSuffix("/index.min.json"))
                }
                indexV2 != null -> fetch(indexV2)
                else -> Result.success(networkStore.toExtensionStore(indexUrl))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Failed to fetch extension store '$indexUrl'"
            }
            Result.failure(e)
        }
    }

    /**
     * Fetches the `repo.json` metadata of a legacy repo. When it cannot be retrieved (some
     * legacy repos only host `index.min.json`), a placeholder store is synthesized instead of
     * failing, since the extension index itself is known to be reachable at this point.
     */
    private suspend fun fetchLegacyRepoDetails(baseUrl: String): Result<ExtensionStore> {
        val repoUrl = "$baseUrl/repo.json"
        return try {
            val networkStore = withDecodedBody(repoUrl) { source ->
                when (source.peek().readByte()) {
                    JSON_OBJECT_PREFIX -> decodeJsonStore(source)
                    else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                }
            }
            val indexV2 = (networkStore as? NetworkLegacyExtensionRepo)?.indexV2
            if (indexV2 != null) {
                return fetch(indexV2)
            }
            Result.success(networkStore.toExtensionStore(repoUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) {
                "Failed to fetch repo details from '$repoUrl'; using placeholder store metadata"
            }
            Result.success(placeholderLegacyStore(repoUrl))
        }
    }

    private fun decodeJsonStore(source: BufferedSource): BaseNetworkExtensionStore {
        return try {
            json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(source.peek())
        } catch (_: IllegalArgumentException) {
            json.decodeFromBufferedSource<NetworkExtensionStore>(source)
        }
    }

    private fun placeholderLegacyStore(repoUrl: String): ExtensionStore {
        val baseUrl = repoUrl.removeSuffix("/repo.json")
        val name = extractStoreName(baseUrl)
        return ExtensionStore(
            indexUrl = repoUrl,
            name = name,
            badgeLabel = name,
            signingKey = NO_SIGNING_KEY,
            contact = ExtensionStore.Contact(website = baseUrl, discord = null),
            isLegacy = true,
            extensionListUrl = null,
        )
    }

    private fun extractStoreName(baseUrl: String): String {
        return try {
            val uri = URI(baseUrl)
            val segments = uri.path?.trim('/')?.split("/").orEmpty().filter { it.isNotBlank() }
            when {
                segments.size >= 2 -> "${segments[0]}/${segments[1]}"
                segments.isNotEmpty() -> segments[0]
                else -> uri.host ?: baseUrl
            }
        } catch (_: Exception) {
            baseUrl
        }
    }

    suspend fun getExtensions(store: ExtensionStore): Result<List<AvailableExtensionData>> {
        return try {
            val extensions = if (store.extensionListUrl != null) {
                withDecodedBody(store.extensionListUrl!!) { source ->
                    when (source.peek().readByte()) {
                        JSON_OBJECT_PREFIX -> json.decodeFromBufferedSource<NetworkExtensionStore.ExtensionList>(source)
                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(
                            source.readByteArray(),
                        )
                    }
                        .toAvailableExtensionData(store)
                }
            } else if (!store.isLegacy) {
                withDecodedBody(store.indexUrl) { source ->
                    val networkStore = when (source.peek().readByte()) {
                        JSON_OBJECT_PREFIX -> json.decodeFromBufferedSource<NetworkExtensionStore>(source)
                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                    }
                    val extensionList = networkStore.extensionList
                        ?: throw IllegalStateException(
                            "Store '${store.name}' provides neither an extension list nor an extension list url",
                        )
                    extensionList.toAvailableExtensionData(store)
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

    private companion object {
        const val NO_SIGNING_KEY = "NO_SIGNING_KEY"
        val JSON_ARRAY_PREFIX = '['.code.toByte()
        val JSON_OBJECT_PREFIX = '{'.code.toByte()
    }
}
