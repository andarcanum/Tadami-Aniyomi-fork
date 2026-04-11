package eu.kanade.tachiyomi.extension.novel.runtime

import tachiyomi.data.extension.novel.NovelPluginKeyValueStore

/**
 * Bridge that provides browser-like localStorage and sessionStorage semantics
 * for novel plugins that depend on web storage APIs.
 *
 * - localStorage: persisted via [NovelPluginKeyValueStore] (SharedPreferences-backed)
 * - sessionStorage: in-memory only, cleared when the process dies
 *
 * Both namespaces are plugin-scoped and use separate key prefixes from generic
 * plugin key-value storage to avoid collisions.
 *
 * Each write records a [SyncTimestamp] so consumers can detect staleness or
 * resolve conflicts instead of silently overwriting.
 */
class NovelPluginWebStorageBridge(
    private val pluginId: String,
    private val keyValueStore: NovelPluginKeyValueStore,
) {
    // ── Namespace prefixes ──────────────────────────────────────────────
    // These are intentionally different from the generic "novel_plugin_storage_"
    // prefix used by AndroidNovelPluginKeyValueStore so web-storage keys never
    // collide with regular plugin preferences.

    private companion object {
        /** Prefix for localStorage keys persisted through the KeyValueStore. */
        const val LOCAL_PREFIX = "web_local_"

        /** Prefix for sessionStorage keys (in-memory only, never persisted). */
        const val SESSION_PREFIX = "web_session_"

        /** Meta-key that stores the last-write epoch millis for localStorage. */
        const val LOCAL_SYNC_META = "web_local_sync_timestamp"

        /** Meta-key that stores the last-write epoch millis for sessionStorage. */
        const val SESSION_SYNC_META = "web_session_sync_timestamp"
    }

    // ── In-memory sessionStorage ────────────────────────────────────────

    private val sessionStore: MutableMap<String, String> = mutableMapOf()
    private var sessionSyncTimestamp: Long = 0L

    // ── localStorage (persistent) ───────────────────────────────────────

    /**
     * Retrieves a value from plugin-scoped localStorage.
     *
     * @return the stored value, or `null` if the key does not exist.
     */
    fun localStorageGet(key: String): String? {
        return keyValueStore.get(pluginId, LOCAL_PREFIX + key)
    }

    /**
     * Stores a value in plugin-scoped localStorage.
     * Also updates the sync timestamp.
     */
    fun localStorageSet(key: String, value: String) {
        keyValueStore.set(pluginId, LOCAL_PREFIX + key, value)
        updateLocalSyncTimestamp()
    }

    /**
     * Removes a single key from plugin-scoped localStorage.
     * Also updates the sync timestamp.
     */
    fun localStorageRemove(key: String) {
        keyValueStore.remove(pluginId, LOCAL_PREFIX + key)
        updateLocalSyncTimestamp()
    }

    /**
     * Clears **all** localStorage entries for this plugin.
     * Keys matching the [LOCAL_PREFIX] prefix are removed; other keys in the
     * underlying store are left untouched.
     */
    fun localStorageClear() {
        val allKeys = keyValueStore.keys(pluginId)
        allKeys
            .filter { it.startsWith(LOCAL_PREFIX) }
            .forEach { keyValueStore.remove(pluginId, it) }
        updateLocalSyncTimestamp()
    }

    /**
     * Returns all keys currently stored in plugin-scoped localStorage.
     */
    fun localStorageKeys(): Set<String> {
        return keyValueStore.keys(pluginId)
            .filter { it.startsWith(LOCAL_PREFIX) && it != LOCAL_SYNC_META }
            .map { it.removePrefix(LOCAL_PREFIX) }
            .toSet()
    }

    /**
     * Returns the epoch-millis timestamp of the last localStorage write,
     * or `0` if nothing has been written in this process lifetime.
     */
    fun localStorageSyncTimestamp(): Long {
        val stored = keyValueStore.get(pluginId, LOCAL_SYNC_META)
        return stored?.toLongOrNull() ?: 0L
    }

    // ── sessionStorage (in-memory) ──────────────────────────────────────

    /**
     * Retrieves a value from plugin-scoped sessionStorage.
     *
     * @return the stored value, or `null` if the key does not exist.
     */
    fun sessionStorageGet(key: String): String? {
        return sessionStore[key]
    }

    /**
     * Stores a value in plugin-scoped sessionStorage.
     * Also updates the sync timestamp.
     */
    fun sessionStorageSet(key: String, value: String) {
        sessionStore[key] = value
        updateSessionSyncTimestamp()
    }

    /**
     * Removes a single key from plugin-scoped sessionStorage.
     * Also updates the sync timestamp.
     */
    fun sessionStorageRemove(key: String) {
        sessionStore.remove(key)
        updateSessionSyncTimestamp()
    }

    /**
     * Clears **all** sessionStorage entries for this plugin.
     */
    fun sessionStorageClear() {
        sessionStore.clear()
        updateSessionSyncTimestamp()
    }

    /**
     * Returns all keys currently stored in plugin-scoped sessionStorage.
     */
    fun sessionStorageKeys(): Set<String> {
        return sessionStore.keys.toSet()
    }

    /**
     * Returns the epoch-millis timestamp of the last sessionStorage write,
     * or `0` if nothing has been written in this process lifetime.
     */
    fun sessionStorageSyncTimestamp(): Long {
        return sessionSyncTimestamp
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun updateLocalSyncTimestamp() {
        val now = System.currentTimeMillis()
        keyValueStore.set(pluginId, LOCAL_SYNC_META, now.toString())
    }

    private fun updateSessionSyncTimestamp() {
        sessionSyncTimestamp = System.currentTimeMillis()
    }
}
