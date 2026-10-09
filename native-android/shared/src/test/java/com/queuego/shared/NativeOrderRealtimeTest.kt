package com.queuego.shared

import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
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

    @Test fun merchantReceivesOwnNotificationsEvenWhenOrderSelectIsRestricted() {
        assertEquals(listOf(
            NativeRealtimeSubscription("notifications", "user_id=eq.owner-1"),
            NativeRealtimeSubscription("orders", "shop_id=eq.shop-2")),
            merchantRealtimeSubscriptions("owner-1", "shop-2"))
    }

    @Test fun riderFiltersUseUserIdForNotificationsAndProfileIdForAssignedOrders() {
        assertEquals(listOf(NativeRealtimeSubscription("notifications", "user_id=eq.user-1")),
            riderRealtimeSubscriptions("user-1", null))
        assertEquals(listOf(
            NativeRealtimeSubscription("notifications", "user_id=eq.user-1"),
            NativeRealtimeSubscription("orders", "rider_id=eq.profile-2")),
            riderRealtimeSubscriptions("user-1", "profile-2"))
        assertThrows(IllegalArgumentException::class.java) { NativeRealtimeSubscription("orders", "") }
    }

    @Test fun riderChannelValidatesAllFiltersAndOnlyAcceptedEventIdsInvalidate() = runBlocking {
        val server = MockWebServer()
        val sockets = LinkedBlockingQueue<WebSocket>()
        val joins = LinkedBlockingQueue<JSONObject>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(socket: WebSocket, text: String) {
                val message = JSONObject(text)
                if (message.optString("event") != "phx_join") return
                joins.add(message)
                val changes = message.getJSONObject("payload").getJSONObject("config").getJSONArray("postgres_changes")
                for (i in 0 until changes.length()) changes.getJSONObject(i).put("id", i + 10)
                socket.send(JSONObject().put("topic", message.getString("topic")).put("event", "phx_reply").put("ref", "1")
                    .put("payload", JSONObject().put("status", "ok").put("response", JSONObject().put("postgres_changes", changes))).toString())
                sockets.add(socket)
            }
        }))
        val client = OkHttpClient()
        val signals = Channel<Unit>(Channel.UNLIMITED)
        val collector = launch {
            NativeOrderRealtime(server.url("/websocket").toString(), client)
                .changes("rider-jwt", riderRealtimeSubscriptions("user-1", "profile-2")).collect { signals.send(it) }
        }
        try {
            withTimeout(5_000) { signals.receive() }
            val join = joins.poll(1, TimeUnit.SECONDS)
            assertEquals("rider-jwt", join.getJSONObject("payload").getString("access_token"))
            assertEquals(2, join.getJSONObject("payload").getJSONObject("config").getJSONArray("postgres_changes").length())
            val socket = sockets.poll(1, TimeUnit.SECONDS)
            fun change(id: Int) = JSONObject().put("topic", join.getString("topic"))
                .put("event", "postgres_changes").put("payload", JSONObject().put("ids", org.json.JSONArray().put(id))).toString()
            socket.send(change(999))
            assertNull(withTimeoutOrNull(150) { signals.receive() })
            socket.send(change(11))
            withTimeout(5_000) { signals.receive() }
        } finally {
            collector.cancel()
            collector.join()
            signals.close()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }

    @Test fun partialRiderSubscriptionAcknowledgementCannotReportAConnectedRefresh() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(socket: WebSocket, text: String) {
                val message = JSONObject(text)
                if (message.optString("event") != "phx_join") return
                val first = message.getJSONObject("payload").getJSONObject("config").getJSONArray("postgres_changes").getJSONObject(0).put("id", 10)
                socket.send(JSONObject().put("topic", message.getString("topic")).put("event", "phx_reply").put("ref", "1")
                    .put("payload", JSONObject().put("status", "ok").put("response", JSONObject().put("postgres_changes", org.json.JSONArray().put(first)))).toString())
            }
        }))
        val client = OkHttpClient()
        try {
            val realtime = NativeOrderRealtime(server.url("/websocket").toString(), client)
            assertNull(withTimeoutOrNull(500) { realtime.changes("rider-jwt", riderRealtimeSubscriptions("user-1", "profile-2")).first() })
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }

    @Test fun customerSubscriptionsNeverReadAnotherCustomersOrdersOrUnfilteredPool() {
        assertEquals(listOf(
            NativeRealtimeSubscription("notifications", "user_id=eq.customer-1"),
            NativeRealtimeSubscription("orders", "customer_id=eq.customer-1"),
            NativeRealtimeSubscription("market_orders", "customer_id=eq.customer-1"),
            NativeRealtimeSubscription("laundry_orders", "customer_id=eq.customer-1")),
            customerRealtimeSubscriptions("customer-1"))
    }

}
