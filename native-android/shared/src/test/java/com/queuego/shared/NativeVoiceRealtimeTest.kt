package com.queuego.shared

import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeVoiceRealtimeTest {
    @Test fun privateJoinBroadcastAndTokenRefreshUseAuthenticatedCallTopic() = runBlocking {
        val server = MockWebServer()
        val received = LinkedBlockingQueue<JSONObject>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = JSONObject(text)
                received.add(message)
                when (message.optString("event")) {
                    "phx_join" -> webSocket.send(
                        JSONObject()
                            .put("topic", message.getString("topic"))
                            .put("event", "phx_reply")
                            .put("ref", "1")
                            .put("join_ref", "1")
                            .put("payload", JSONObject().put("status", "ok").put("response", JSONObject()))
                            .toString()
                    )
                    "broadcast" -> webSocket.send(
                        JSONObject()
                            .put("topic", message.getString("topic"))
                            .put("event", "broadcast")
                            .put(
                                "payload",
                                JSONObject()
                                    .put("event", "answer")
                                    .put("payload", JSONObject().put("sdp", "answer-sdp"))
                            )
                            .toString()
                    )
                }
            }
        }))
        val client = OkHttpClient()
        val topic = "qg-call:" + UUID.randomUUID()
        val connection = NativeVoiceRealtime(server.url("/websocket").toString(), client)
            .connect("jwt-one", topic)
        try {
            withTimeout(5_000) { connection.state.first { it == NativeVoiceRealtimeState.CONNECTED } }
            val join = received.poll(1, TimeUnit.SECONDS)
            assertNotNull(join)
            assertEquals("realtime:$topic", join.getString("topic"))
            val joinPayload = join.getJSONObject("payload")
            assertEquals("jwt-one", joinPayload.getString("access_token"))
            val config = joinPayload.getJSONObject("config")
            assertTrue(config.getBoolean("private"))
            assertTrue(config.getJSONObject("broadcast").getBoolean("ack"))
            assertFalse(config.getJSONObject("broadcast").getBoolean("self"))
            assertEquals(0, config.getJSONArray("postgres_changes").length())

            assertTrue(connection.send("offer", JSONObject().put("sdp", "offer-sdp")))
            val outbound = received.poll(1, TimeUnit.SECONDS)
            assertEquals("broadcast", outbound.getString("event"))
            assertEquals("offer", outbound.getJSONObject("payload").getString("event"))
            assertEquals("offer-sdp", outbound.getJSONObject("payload")
                .getJSONObject("payload").getString("sdp"))

            val inbound = withTimeout(5_000) { connection.signals.first() }
            assertEquals("answer", inbound.event)
            assertEquals("answer-sdp", inbound.payload.getString("sdp"))

            assertTrue(connection.updateAccessToken("jwt-two"))
            val tokenUpdate = received.poll(1, TimeUnit.SECONDS)
            assertEquals("access_token", tokenUpdate.getString("event"))
            assertEquals("jwt-two", tokenUpdate.getJSONObject("payload").getString("access_token"))
        } finally {
            connection.close()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }

    @Test fun rejectedPrivateJoinFailsClosedAndCannotSendSignals() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = JSONObject(text)
                if (message.optString("event") == "phx_join") {
                    webSocket.send(
                        JSONObject()
                            .put("topic", message.getString("topic"))
                            .put("event", "phx_reply")
                            .put("ref", "1")
                            .put("join_ref", "1")
                            .put(
                                "payload",
                                JSONObject()
                                    .put("status", "error")
                                    .put("response", JSONObject().put("reason", "RLS denied"))
                            )
                            .toString()
                    )
                }
            }
        }))
        val client = OkHttpClient()
        val connection = NativeVoiceRealtime(server.url("/websocket").toString(), client)
            .connect("jwt", "qg-call:" + UUID.randomUUID())
        try {
            withTimeout(5_000) { connection.state.first { it == NativeVoiceRealtimeState.FAILED } }
            assertFalse(connection.send("offer", JSONObject().put("sdp", "nope")))
            assertFalse(connection.updateAccessToken("jwt-next"))
        } finally {
            connection.close()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }

    @Test fun callTopicsAndEventsAreStrictlyValidated() {
        assertThrows(IllegalArgumentException::class.java) {
            requireVoiceCallTopic("qg-call:../../orders")
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireVoiceCallTopic("qg-call:not-a-uuid")
        }
        val valid = "qg-call:" + UUID.randomUUID()
        assertEquals(valid, requireVoiceCallTopic(valid))
        assertEquals(setOf("ready", "offer", "answer", "ice", "hangup"),
            NativeVoiceRealtimeConnection.ALLOWED_EVENTS)
    }

    @Test fun callApiParserRejectsFabricatedTargetsAndTopics() {
        val api = NativeVoiceCallApi()
        val row = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("order_id", UUID.randomUUID().toString())
            .put("topic", "qg-call:" + UUID.randomUUID())
            .put("target", "rider")
            .put("status", "ringing")
            .put("caller_user_id", UUID.randomUUID().toString())
            .put("callee_user_id", UUID.randomUUID().toString())
        assertEquals("rider", api.parse(row).target)

        row.put("target", "arbitrary-user")
        assertThrows(IllegalArgumentException::class.java) { api.parse(row) }

        row.put("target", "rider").put("topic", "public-room")
        assertThrows(IllegalArgumentException::class.java) { api.parse(row) }
    }
}
