package com.queuego.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Realtime is an invalidation signal only. Owned API reads remain authoritative. */
class NativeOrderRealtime internal constructor(
    private val endpoint: String,
    private val client: OkHttpClient
) {
    constructor() : this(
        QueueGoNativeApi.BASE_URL.replace("https://", "wss://") +
            "/realtime/v1/websocket?apikey=" + QueueGoNativeApi.PUBLISHABLE_KEY + "&vsn=1.0.0",
        OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    )

    fun changes(token: String, shopId: String): Flow<Unit> =
        changes(token, listOf(NativeRealtimeSubscription("orders", "shop_id=eq.$shopId")), "realtime:merchant-orders")

    fun changes(token: String, subscriptions: List<NativeRealtimeSubscription>): Flow<Unit> =
        changes(token, subscriptions, "realtime:queuego-native")

    private fun changes(token: String, subscriptions: List<NativeRealtimeSubscription>, topic: String): Flow<Unit> = flow {
        require(subscriptions.isNotEmpty())
        require(subscriptions.distinct().size == subscriptions.size)
        var backoff = 1_000L
        while (true) {
            try {
                emitAll(connection(token, subscriptions, topic))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Never log credentials or order payloads. Polling remains the fallback.
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }.conflate()

    private fun connection(token: String, subscriptions: List<NativeRealtimeSubscription>, topic: String): Flow<Unit> = callbackFlow {
        val subscriptionIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
        val joined = java.util.concurrent.atomic.AtomicBoolean(false)
        val lastReply = java.util.concurrent.atomic.AtomicLong(System.nanoTime())
        fun message(event: String, payload: JSONObject, ref: String) = JSONObject()
            .put("topic", topic).put("event", event).put("payload", payload)
            .put("ref", ref).put("join_ref", "1").toString()
        val socket = client.newWebSocket(Request.Builder().url(endpoint).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val config = JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("enabled", false))
                    .put("private", false)
                    .put("postgres_changes", JSONArray().also { rows ->
                        subscriptions.forEach { rows.put(JSONObject().put("event", "*")
                            .put("schema", "public").put("table", it.table).put("filter", it.filter)) }
                    })
                webSocket.send(message("phx_join", JSONObject().put("config", config)
                    .put("access_token", token), "1"))
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = JSONObject(text)
                    if (envelope.optString("topic") == "phoenix" && envelope.optString("event") == "phx_reply") {
                        lastReply.set(System.nanoTime())
                        return
                    }
                    if (envelope.optString("topic") != topic) return
                    val payload = envelope.optJSONObject("payload") ?: JSONObject()
                    when (envelope.optString("event")) {
                        "phx_reply" -> if (envelope.optString("ref") == "1") {
                            val accepted = payload.optJSONObject("response")?.optJSONArray("postgres_changes")
                            val matched = accepted != null && accepted.length() == subscriptions.size &&
                                subscriptions.all { expected -> (0 until accepted.length()).count { i ->
                                    val row = accepted.optJSONObject(i)
                                    row?.optString("table") == expected.table && row.optString("schema") == "public" &&
                                        row.optString("filter") == expected.filter && row.has("id")
                                } == 1 }
                            if (payload.optString("status") != "ok" || !matched) {
                                close(IOException("Realtime subscription rejected"))
                            } else {
                                (0 until accepted!!.length()).forEach { i ->
                                    subscriptionIds.add(accepted.getJSONObject(i).getLong("id"))
                                }
                                joined.set(true)
                                trySend(Unit) // Refetch on every successful reconnect.
                            }
                        }
                        "postgres_changes" -> if (joined.get()) {
                            val ids = payload.optJSONArray("ids")
                            if (ids != null && (0 until ids.length()).any { subscriptionIds.contains(ids.optLong(it, -1)) }) {
                                trySend(Unit)
                            }
                        }
                        "phx_error", "phx_close" -> close(IOException("Realtime channel closed"))
                        "system" -> if (payload.optString("status") == "error") close(IOException("Realtime unavailable"))
                    }
                } catch (_: Exception) {
                    close(IOException("Invalid Realtime message"))
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(t) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                close()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
        })
        val heartbeat = launch {
            delay(12_000)
            if (!joined.get()) { close(IOException("Realtime join timeout")); return@launch }
            lastReply.set(System.nanoTime())
            var ref = 2L
            while (true) {
                if (System.nanoTime() - lastReply.get() > TimeUnit.SECONDS.toNanos(35)) {
                    close(IOException("Realtime heartbeat timeout")); return@launch
                }
                socket.send(JSONObject().put("topic", "phoenix").put("event", "heartbeat")
                    .put("payload", JSONObject()).put("ref", (ref++).toString()).toString())
                delay(20_000)
            }
        }
        awaitClose {
            heartbeat.cancel()
            socket.send(message("phx_leave", JSONObject(), "leave"))
            socket.cancel()
        }
    }
}
