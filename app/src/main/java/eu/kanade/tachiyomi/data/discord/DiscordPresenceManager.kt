package eu.kanade.tachiyomi.data.discord

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DiscordPresenceManager(
    private val preferences: DiscordPreferences,
    private val gateway: DiscordGatewayClient,
    private val scope: CoroutineScope = CoroutineScope(
        // Весь стейт менеджера доступается только из этого скоупа (однопоточный confinement).
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1) + CoroutineName("DiscordPresence"),
    ),
    private val debounceMillis: Long = 2000,
) {
    val status: StateFlow<ConnectionStatus> get() = gateway.status

    private data class ActiveSession(val handle: Any, val info: DiscordPresenceInfo)

    private var activeSession: ActiveSession? = null
    private var connectedToken: String? = null
    private var sentKey: String? = null
    private var sendJob: Job? = null

    init {
        scope.launch {
            preferences.enabled().changes().collect { resync() }
        }
        scope.launch {
            preferences.token().changes().collect { resync() }
        }
        scope.launch {
            gateway.status.collect { status ->
                if (status == ConnectionStatus.Connected) {
                    // после реконнекта Discord сбросил presence — переотправляем
                    sentKey = null
                    scheduleSend()
                }
            }
        }
    }

    fun setSession(handle: Any, info: DiscordPresenceInfo) {
        scope.launch {
            activeSession = ActiveSession(handle, info)
            resync()
        }
    }

    fun clearSession(handle: Any) {
        scope.launch {
            if (activeSession?.handle != handle) return@launch
            activeSession = null
            resync()
        }
    }

    private fun resync() {
        val enabled = preferences.enabled().get()
        val token = preferences.token().get().takeIf { it.isNotBlank() }
        val session = activeSession
        if (!enabled || token == null || session == null) {
            sendJob?.cancel()
            sendClearedIfSent()
            connectedToken = null
            gateway.disconnect()
            return
        }
        val tokenChanged = token != connectedToken
        connectedToken = token
        if (tokenChanged) {
            sentKey = null
            gateway.connect(token)
        }
        scheduleSend()
    }

    private fun scheduleSend() {
        val session = activeSession ?: return
        val key = session.info.dedupeKey()
        if (key == sentKey) return
        sendJob?.cancel()
        sendJob = scope.launch {
            delay(debounceMillis)
            val current = activeSession ?: return@launch
            val currentKey = current.info.dedupeKey()
            if (currentKey != key) return@launch
            gateway.sendFrame(DiscordPresencePayload.presenceUpdate(current.info))
            sentKey = currentKey
        }
    }

    private fun sendClearedIfSent() {
        if (sentKey != null) {
            gateway.sendFrame(DiscordPresencePayload.presenceUpdate(null))
            sentKey = null
        }
    }

    private fun DiscordPresenceInfo.dedupeKey(): String =
        "$mediaKind|$title|$primaryNumber|$secondaryLine"
}
