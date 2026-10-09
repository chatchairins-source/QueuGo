package com.queuego.rider

import com.queuego.shared.QueueGoNativeApi
import java.time.Instant

internal data class BadgeMessage(val id: String, val orderId: String, val senderId: String, val text: String, val at: Long)
internal data class BadgeRoom(val id: String, val customerId: String, val deliveredAt: Long?)

/** Web parity: baseline the active room, then count each new latest counterpart message.
 * Completed rooms count customer messages after delivery and the user's inbox seen time. */
internal class RiderMessageBadgeTracker(private val userId: String) {
    private var activeId: String? = null
    private var lastId: String? = null
    private var activeUnread = 0

    fun update(active: String?, openChat: String?, inboxOpen: Boolean, rooms: List<BadgeRoom>,
               messages: List<BadgeMessage>, seenAt: Long, now: Long): Pair<Int, String?> {
        val last = messages.filter { it.orderId == active }.maxByOrNull { it.at }
        var notice: String? = null
        if (activeId != active) {
            activeId = active
            lastId = last?.id
            activeUnread = 0
        } else if (last != null && last.id != lastId) {
            lastId = last.id
            if (openChat != active && last.senderId != userId) {
                activeUnread++
                notice = if (last.text.startsWith("__IMG__")) "ส่งรูปภาพ" else last.text
            }
        }
        if (openChat != null) activeUnread = 0
        val completed = if (inboxOpen) 0 else messages.count { m ->
            val room = rooms.find { it.id == m.orderId }
            val at = room?.deliveredAt
            at != null && now >= at && now < at + 30 * 60_000L &&
                m.senderId == room.customerId && m.at > at && m.at < at + 30 * 60_000L && m.at > seenAt
        }
        return (activeUnread + completed) to notice
    }
}

internal class RiderMessageBadgeApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun read(auth: QueueGoAuth, ids: List<String>): Pair<List<BadgeRoom>, List<BadgeMessage>> {
        if (ids.isEmpty()) return emptyList<BadgeRoom>() to emptyList()
        val scope = ids.distinct().take(100).joinToString(",") { http.enc(it) }
        val orders = http.array(http.get("orders?select=id,customer_id,status&id=in.($scope)", auth.session.accessToken))
        val deliveries = http.array(http.get("deliveries?select=order_id,delivered_at&order_id=in.($scope)", auth.session.accessToken))
        val delivered = mutableMapOf<String, Long>()
        for (i in 0 until deliveries.length()) {
            val r = deliveries.getJSONObject(i)
            parseAt(r.optString("delivered_at"))?.let { delivered[r.optString("order_id")] = it }
        }
        val rooms = (0 until orders.length()).map { i ->
            val r = orders.getJSONObject(i)
            BadgeRoom(r.optString("id"), r.optString("customer_id"), if (r.optString("status") == "completed") delivered[r.optString("id")] else null)
        }
        val raw = http.array(http.get("order_chat_messages?select=id,order_id,sender_id,message,created_at&order_id=in.($scope)&order=created_at.desc&limit=500", auth.session.accessToken))
        val messages = (0 until raw.length()).mapNotNull { i ->
            val r = raw.getJSONObject(i)
            parseAt(r.optString("created_at"))?.let { BadgeMessage(r.optString("id"), r.optString("order_id"), r.optString("sender_id"), r.optString("message"), it) }
        }
        return rooms to messages
    }
    private fun parseAt(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
