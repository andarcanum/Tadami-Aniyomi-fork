package eu.kanade.tachiyomi.data.discord

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
private const val OP_HELLO = 10
private const val CLOSE_INVALID_TOKEN = 4004
private const val CLOSE_NORMAL = 1000

class RealDiscordGatewayClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("DiscordGateway"),
    ),
) : DiscordGatewayClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow(ConnectionStatus.Disconnected)
    override val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val desiredToken = AtomicReference<String?>(null)

    @Volatile
    private var socket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    @Volatile
    private var reconnectAttempt = 0

    @Synchronized
    override fun connect(token: String) {
        val tokenChanged = desiredToken.getAndSet(token) != token
        if (socket != null) {
            if (tokenChanged) {
                // onClosed переподключится: desiredToken уже обновлён
                socket?.close(CLOSE_NORMAL, null)
            }
            return
        }
        openSocket()
    }

    @Synchronized
    override fun sendFrame(frame: String) {
        if (_status.value == ConnectionStatus.Connected) {
            socket?.send(frame)
        }
    }

    @Synchronized
    override fun disconnect() {
        desiredToken.set(null)
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        socket?.close(CLOSE_NORMAL, null)
        socket = null
        _status.value = ConnectionStatus.Disconnected
    }

    @Synchronized
    private fun openSocket() {
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        _status.value = ConnectionStatus.Connecting
        val request = Request.Builder().url(GATEWAY_URL).build()
        socket = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempt = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = json.parseToJsonElement(text).jsonObject
            when (obj["op"]?.jsonPrimitive?.content?.toIntOrNull()) {
                OP_HELLO -> {
                    val interval = obj["d"]?.jsonObject?.get("heartbeat_interval")
                        ?.jsonPrimitive?.long ?: 41250L
                    startHeartbeat(interval)
                    desiredToken.get()?.let { webSocket.send(DiscordPresencePayload.identify(it)) }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleDown(code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleDown(0)
        }
    }

    @Synchronized
    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var nonce = 0L
            while (isActive) {
                delay(intervalMs)
                socket?.send(DiscordPresencePayload.heartbeat(++nonce))
            }
        }
        _status.value = ConnectionStatus.Connected
    }

    @Synchronized
    private fun handleDown(code: Int) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        socket = null
        if (code == CLOSE_INVALID_TOKEN) {
            desiredToken.set(null)
            _status.value = ConnectionStatus.InvalidToken
            return
        }
        if (desiredToken.get() == null) {
            _status.value = ConnectionStatus.Disconnected
            return
        }
        // транзиентный обрыв: фоновый реконнект с экспоненциальным backoff
        val attempt = reconnectAttempt++
        _status.value = ConnectionStatus.Connecting
        reconnectJob = scope.launch {
            delay(min(60_000L, 2000L shl attempt.coerceAtMost(5)))
            reconnectIfDesired()
        }
    }

    @Synchronized
    private fun reconnectIfDesired() {
        if (desiredToken.get() != null && socket == null) {
            openSocket()
        }
    }
}
