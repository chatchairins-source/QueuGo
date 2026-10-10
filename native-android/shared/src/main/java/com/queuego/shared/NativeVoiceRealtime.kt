package com.queuego.shared

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

enum class NativeVoiceRealtimeState { CONNECTING, CONNECTED, CLOSED, FAILED }

data class NativeVoiceSignal(val event: String, val payload: JSONObject)

class NativeVoiceRealtime internal constructor(
    private val endpoint: String,
    private val client: OkHttpClient
) {
    constructor() : this(
        QueueGoNativeApi.BASE_URL.replace("https://", "wss://") +
            "/realtime/v1/websocket?apikey=" + QueueGoNativeApi.PUBLISHABLE_KEY + "&vsn=1.0.0",
        OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    )

    fun connect(accessToken: String, rawTopic: String): NativeVoiceRealtimeConnection =
        NativeVoiceRealtimeConnection(endpoint, client, accessToken, requireVoiceCallTopic(rawTopic))
}

class NativeVoiceRealtimeConnection internal constructor(
    endpoint: String,
    private val client: OkHttpClient,
    accessToken: String,
    val topic: String
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(NativeVoiceRealtimeState.CONNECTING)
    private val _signals = Channel<NativeVoiceSignal>(Channel.BUFFERED)
    private val refs = AtomicLong(2)
    private val lastHeartbeatReply = AtomicLong(System.nanoTime())
    private val realtimeTopic = "realtime:$topic"
    private var heartbeat: Job? = null

    val state: StateFlow<NativeVoiceRealtimeState> = _state
    val signals: Flow<NativeVoiceSignal> = _signals.receiveAsFlow()

    private val socket: WebSocket = client.newWebSocket(
        Request.Builder().url(endpoint).build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val config = JSONObject()
                    .put("broadcast", JSONObject().put("ack", true).put("self", false))
                    .put("presence", JSONObject().put("enabled", false))
                    .put("private", true)
                    .put("postgres_changes", JSONArray())
                webSocket.send(envelope(
                    realtimeTopic,
                    "phx_join",
                    JSONObject().put("config", config).put("access_token", accessToken),
                    "1",
                    "1"
                ))
                heartbeat = scope.launch {
                    delay(12_000)
                    if (_state.value != NativeVoiceRealtimeState.CONNECTED) {
                        fail(IOException("Voice Realtime join timeout"))
                        return@launch
                    }
                    while (true) {
                        if (System.nanoTime() - lastHeartbeatReply.get() > TimeUnit.SECONDS.toNanos(35)) {
                            fail(IOException("Voice Realtime heartbeat timeout"))
                            return@launch
                        }
                        val ref = refs.getAndIncrement().toString()
                        webSocket.send(envelope("phoenix", "heartbeat", JSONObject(), ref, null))
                        delay(20_000)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = JSONObject(text)
                    val incomingTopic = message.optString("topic")
                    val event = message.optString("event")
                    if (incomingTopic == "phoenix" && event == "phx_reply") {
                        lastHeartbeatReply.set(System.nanoTime())
                        return
                    }
                    if (incomingTopic != realtimeTopic) return
                    val payload = message.optJSONObject("payload") ?: JSONObject()
                    when (event) {
                        "phx_reply" -> if (message.optString("ref") == "1") {
                            if (payload.optString("status") == "ok") {
                                lastHeartbeatReply.set(System.nanoTime())
                                _state.value = NativeVoiceRealtimeState.CONNECTED
                            } else fail(IOException("Voice Realtime subscription rejected"))
                        }
                        "broadcast" -> {
                            val name = payload.optString("event")
                            val body = payload.optJSONObject("payload")
                            if (name in ALLOWED_EVENTS && body != null) {
                                _signals.trySend(NativeVoiceSignal(name, body))
                            }
                        }
                        "system" -> if (payload.optString("status") == "error") {
                            fail(IOException("Voice Realtime system error"))
                        }
                        "phx_error" -> fail(IOException("Voice Realtime channel error"))
                        "phx_close" -> close()
                    }
                } catch (failure: Exception) {
                    fail(IOException("Invalid Voice Realtime message", failure))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = fail(t)

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (_state.value != NativeVoiceRealtimeState.FAILED) {
                    _state.value = NativeVoiceRealtimeState.CLOSED
                }
                heartbeat?.cancel()
            }
        }
    )

    fun send(event: String, payload: JSONObject): Boolean {
        require(event in ALLOWED_EVENTS) { "Voice signal event ไม่ถูกต้อง" }
        if (_state.value != NativeVoiceRealtimeState.CONNECTED) return false
        val ref = refs.getAndIncrement().toString()
        return socket.send(envelope(
            realtimeTopic,
            "broadcast",
            JSONObject().put("type", "broadcast").put("event", event).put("payload", payload),
            ref,
            "1"
        ))
    }

    fun updateAccessToken(accessToken: String): Boolean {
        require(accessToken.isNotBlank())
        if (_state.value != NativeVoiceRealtimeState.CONNECTED) return false
        val ref = refs.getAndIncrement().toString()
        return socket.send(envelope(
            realtimeTopic,
            "access_token",
            JSONObject().put("access_token", accessToken),
            ref,
            "1"
        ))
    }

    private fun fail(cause: Throwable) {
        if (_state.value == NativeVoiceRealtimeState.CLOSED ||
            _state.value == NativeVoiceRealtimeState.FAILED
        ) return
        _state.value = NativeVoiceRealtimeState.FAILED
        heartbeat?.cancel()
        socket.cancel()
    }

    override fun close() {
        val previous = _state.value
        if (previous == NativeVoiceRealtimeState.CLOSED) return
        if (previous != NativeVoiceRealtimeState.FAILED) {
            socket.send(envelope(realtimeTopic, "phx_leave", JSONObject(), "leave", "1"))
            socket.close(1000, "voice call closed")
            _state.value = NativeVoiceRealtimeState.CLOSED
        }
        heartbeat?.cancel()
        _signals.close()
        scope.cancel()
    }

    companion object {
        internal val ALLOWED_EVENTS = setOf("ready", "offer", "answer", "ice", "hangup")

        internal fun envelope(
            topic: String,
            event: String,
            payload: JSONObject,
            ref: String,
            joinRef: String?
        ): String = JSONObject()
            .put("topic", topic)
            .put("event", event)
            .put("payload", payload)
            .put("ref", ref)
            .apply { if (joinRef != null) put("join_ref", joinRef) }
            .toString()
    }
}
