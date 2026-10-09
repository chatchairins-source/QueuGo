package com.queuego.shared

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class NativeOrderRealtimeTest {
    @Test fun authenticatedFilteredJoinAndReconnectRefetch() = runBlocking {
        val server = MockWebServer()
        val joins = LinkedBlockingQueue<JSONObject>()
        repeat(2) {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(socket: WebSocket, text: String) {
                    val message = JSONObject(text)
                    if (message.optString("event") != "phx_join") return
                    joins.add(message)
                    socket.send(JSONObject().put("topic", "realtime:merchant-orders")
                        .put("event", "phx_reply").put("ref", "1")
                        .put("payload", JSONObject().put("status", "ok")
                            .put("response", JSONObject().put("postgres_changes",
                                org.json.JSONArray().put(JSONObject().put("id", 1)
                                    .put("table", "orders").put("schema", "public")
                                    .put("filter", "shop_id=eq.owned-shop"))))).toString())
                    socket.close(1000, "reconnect test")
                }
            }))
        }
        val client = OkHttpClient()
        try {
            val realtime = NativeOrderRealtime(server.url("/websocket").toString(), client)
            val invalidations = withTimeout(10_000) { realtime.changes("user-jwt", "owned-shop").take(2).toList() }
            assertEquals(2, invalidations.size)
            repeat(2) {
                val payload = joins.poll(1, TimeUnit.SECONDS).getJSONObject("payload")
                assertEquals("user-jwt", payload.getString("access_token"))
                val filter = payload.getJSONObject("config").getJSONArray("postgres_changes")
                assertEquals(1, filter.length())
                assertEquals("orders", filter.getJSONObject(0).getString("table"))
                assertEquals("shop_id=eq.owned-shop", filter.getJSONObject(0).getString("filter"))
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }
    @Test fun rejectedSubscriptionCannotReportAConnectedRefresh() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(socket: WebSocket, text: String) {
                if (JSONObject(text).optString("event") != "phx_join") return
                socket.send("""{"topic":"realtime:merchant-orders","event":"postgres_changes","payload":{}}""")
                socket.send("""{"topic":"realtime:merchant-orders","event":"phx_reply","ref":"1","payload":{"status":"error","response":{}}}""")
            }
        }))
        val client = OkHttpClient()
        try {
            val realtime = NativeOrderRealtime(server.url("/websocket").toString(), client)
            assertNull(withTimeoutOrNull(500) { realtime.changes("user-jwt", "owned-shop").first() })
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }

}
