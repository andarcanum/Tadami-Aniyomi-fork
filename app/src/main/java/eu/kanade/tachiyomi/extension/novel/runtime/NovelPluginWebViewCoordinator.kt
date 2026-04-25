package eu.kanade.tachiyomi.extension.novel.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore

/**
 * Coordinates plugin-owned WebView/browser surfaces and synchronizes web storage
 * at critical lifecycle points.
 *
 * This coordinator ensures that:
 * - WebView-backed storage is synchronized after auth/browser-assisted flows
 * - WebView-backed storage is synchronized after plugin-owned pages mutate storage
 * - WebView-backed storage is synchronized before runtime parsing resumes
 *
 * Each plugin gets its own isolated [NovelPluginWebStorageBridge] instance for
 * storage operations, preventing cross-plugin contamination.
 */
class NovelPluginWebViewCoordinator(
    private val keyValueStore: NovelPluginKeyValueStore,
) {
    private val mutex = Mutex()
    private val activeSessions = mutableMapOf<String, WebViewSession>()
    private val storageBridges = mutableMapOf<String, NovelPluginWebStorageBridge>()

    suspend fun registerSession(
        pluginId: String,
        sessionId: String? = null,
    ): String = mutex.withLock {
        val id = sessionId ?: generateSessionId(pluginId)
        activeSessions[id] = WebViewSession(
            pluginId = pluginId,
            sessionId = id,
            createdAt = System.currentTimeMillis(),
        )
        id
    }

    suspend fun unregisterSession(sessionId: String) = mutex.withLock {
        activeSessions.remove(sessionId)
    }

    suspend fun hasActiveSession(pluginId: String): Boolean = mutex.withLock {
        activeSessions.values.any { it.pluginId == pluginId }
    }

    suspend fun getActiveSessions(pluginId: String): Set<String> = mutex.withLock {
        activeSessions.values
            .filter { it.pluginId == pluginId }
            .map { it.sessionId }
            .toSet()
    }

    fun getStorageBridge(pluginId: String): NovelPluginWebStorageBridge {
        return storageBridges.getOrPut(pluginId) {
            NovelPluginWebStorageBridge(pluginId, keyValueStore)
        }
    }

    suspend fun syncAfterAuthFlow(
        pluginId: String,
        webStorageData: Map<String, String>,
    ) = mutex.withLock {
        val hasAuth = webStorageData.containsKey("auth")
        val authLen = webStorageData["auth"]?.length ?: 0
        val authPreview = webStorageData["auth"]?.take(80) ?: "null"
        logcat(priority = LogPriority.DEBUG, tag = "WebViewCoordinator") {
            "sync plugin=$pluginId hasAuth=$hasAuth keys=${webStorageData.keys} authLen=$authLen authPreview=$authPreview"
        }
        val bridge = getStorageBridge(pluginId)
        syncLocalStorageSnapshot(bridge, webStorageData)
    }

    suspend fun syncAfterPageMutation(
        pluginId: String,
        localStorageData: Map<String, String>,
        sessionStorageData: Map<String, String>? = null,
    ) = mutex.withLock {
        val bridge = getStorageBridge(pluginId)

        syncLocalStorageSnapshot(bridge, localStorageData)
        sessionStorageData?.let { syncSessionStorageSnapshot(bridge, it) }
    }

    suspend fun syncBeforeParsing(pluginId: String): StorageSnapshot = mutex.withLock {
        val bridge = getStorageBridge(pluginId)

        val localStorage = bridge.localStorageKeys().associateWith { key ->
            bridge.localStorageGet(key) ?: ""
        }

        val sessionStorage = bridge.sessionStorageKeys().associateWith { key ->
            bridge.sessionStorageGet(key) ?: ""
        }

        StorageSnapshot(
            localStorage = localStorage,
            sessionStorage = sessionStorage,
            localSyncTimestamp = bridge.localStorageSyncTimestamp(),
            sessionSyncTimestamp = bridge.sessionStorageSyncTimestamp(),
        )
    }

    fun generateStorageInjectionScript(snapshot: StorageSnapshot): String {
        val localStorageStatements = buildList {
            add("try { localStorage.clear(); } catch(e) {}")
            addAll(
                snapshot.localStorage.entries.map { (key, value) ->
                    "try { localStorage.setItem(${escapeJsString(key)}, ${escapeJsString(value)}); } catch(e) {}"
                },
            )
        }

        val sessionStorageStatements = buildList {
            add("try { sessionStorage.clear(); } catch(e) {}")
            addAll(
                snapshot.sessionStorage.entries.map { (key, value) ->
                    "try { sessionStorage.setItem(${escapeJsString(key)}, ${escapeJsString(value)}); } catch(e) {}"
                },
            )
        }

        return """
            (function() {
              ${localStorageStatements.joinToString("\n              ")}
              ${sessionStorageStatements.joinToString("\n              ")}
            })();
        """.trimIndent()
    }

    suspend fun cleanup(pluginId: String) = mutex.withLock {
        activeSessions.entries.removeAll { it.value.pluginId == pluginId }
        storageBridges.remove(pluginId)?.let { bridge ->
            bridge.localStorageClear()
            bridge.sessionStorageClear()
        }
    }

    suspend fun cleanupAll() = mutex.withLock {
        activeSessions.clear()
        storageBridges.values.forEach { bridge ->
            bridge.localStorageClear()
            bridge.sessionStorageClear()
        }
        storageBridges.clear()
    }

    private fun generateSessionId(pluginId: String): String {
        return "${pluginId}_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    private fun escapeJsString(value: String): String {
        return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    }

    private fun syncLocalStorageSnapshot(
        bridge: NovelPluginWebStorageBridge,
        localStorageData: Map<String, String>,
    ) {
        val existingKeys = bridge.localStorageKeys()
        val incomingKeys = localStorageData.keys

        existingKeys
            .filterNot { it in incomingKeys }
            .forEach { bridge.localStorageRemove(it) }

        localStorageData.forEach { (key, value) ->
            bridge.localStorageSet(key, value)
        }
    }

    private fun syncSessionStorageSnapshot(
        bridge: NovelPluginWebStorageBridge,
        sessionStorageData: Map<String, String>,
    ) {
        val existingKeys = bridge.sessionStorageKeys()
        val incomingKeys = sessionStorageData.keys

        existingKeys
            .filterNot { it in incomingKeys }
            .forEach { bridge.sessionStorageRemove(it) }

        sessionStorageData.forEach { (key, value) ->
            bridge.sessionStorageSet(key, value)
        }
    }

    data class WebViewSession(
        val pluginId: String,
        val sessionId: String,
        val createdAt: Long,
    )

    data class StorageSnapshot(
        val localStorage: Map<String, String>,
        val sessionStorage: Map<String, String>,
        val localSyncTimestamp: Long,
        val sessionSyncTimestamp: Long,
    )
}
