package com.queuego.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgSectionTitle
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class CustomerChatModeration(
    val accepted: Boolean,
    val blockedByMe: Boolean,
    val blockedMe: Boolean
)

data class CustomerChatMessage(
    val id: String,
    val senderId: String,
    val message: String,
    val createdAt: String?
)

class CustomerChatApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    private fun obj(raw: Any): JSONObject = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }

    suspend fun moderation(auth: NativeAuth, orderId: String): CustomerChatModeration {
        val o = obj(
            http.rpc(
                "qg_chat_moderation_state",
                auth.session.accessToken,
                JSONObject().put("p_order_id", orderId)
            )
        )
        return CustomerChatModeration(
            accepted = o.optBoolean("accepted", false),
            blockedByMe = o.optBoolean("blockedByMe", o.optBoolean("blocked_by_me", false)),
            blockedMe = o.optBoolean("blockedMe", o.optBoolean("blocked_me", false))
        )
    }

    suspend fun acceptTerms(auth: NativeAuth) {
        http.rpc(
            "qg_accept_ugc_terms",
            auth.session.accessToken,
            JSONObject().put("p_version", 1)
        )
    }

    suspend fun block(auth: NativeAuth, orderId: String) {
        http.rpc(
            "qg_block_chat_counterpart",
            auth.session.accessToken,
            JSONObject().put("p_order_id", orderId)
        )
    }

    suspend fun unblock(auth: NativeAuth, orderId: String) {
        http.rpc(
            "qg_unblock_chat_counterpart",
            auth.session.accessToken,
            JSONObject().put("p_order_id", orderId)
        )
    }

    suspend fun report(
        auth: NativeAuth,
        orderId: String,
        messageId: String?,
        reason: String,
        details: String?
    ) {
        http.rpc(
            "qg_report_chat",
            auth.session.accessToken,
            JSONObject()
                .put("p_order_id", orderId)
                .put("p_message_id", messageId ?: JSONObject.NULL)
                .put("p_reason", reason)
                .put("p_details", details?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        )
    }

    suspend fun messages(auth: NativeAuth, orderId: String): List<CustomerChatMessage> {
        val rows = http.array(
            http.get(
                "order_chat_messages?select=id,sender_id,message,created_at" +
                    "&order_id=eq." + http.enc(orderId) +
                    "&order=created_at.asc&limit=100",
                auth.session.accessToken
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(
                    CustomerChatMessage(
                        id = r.optString("id"),
                        senderId = r.optString("sender_id"),
                        message = r.optString("message"),
                        createdAt = r.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    suspend fun send(auth: NativeAuth, orderId: String, text: String, requestId: String = UUID.randomUUID().toString()) {
        val clean = text.trim()
        require(clean.isNotBlank()) { "กรุณาพิมพ์ข้อความ" }
        require(clean.length <= 500) { "ข้อความยาวเกิน 500 ตัวอักษร" }

        val existing = http.array(
            http.get(
                "order_chat_messages?select=id&id=eq." + http.enc(requestId) +
                    "&order_id=eq." + http.enc(orderId),
                auth.session.accessToken
            )
        )
        if (existing.length() > 0) return

        val raw = http.post(
            "order_chat_messages",
            auth.session.accessToken,
            JSONObject()
                .put("id", requestId)
                .put("order_id", orderId)
                .put("sender_id", auth.user.id)
                .put("message", clean)
        )
        if (raw is JSONArray && raw.length() == 0) {
            val verify = http.array(
                http.get(
                    "order_chat_messages?select=id&id=eq." + http.enc(requestId) +
                        "&order_id=eq." + http.enc(orderId),
                    auth.session.accessToken
                )
            )
            if (verify.length() == 0) error("ยังไม่ได้รับผลยืนยันข้อความ")
        }
    }
}

private fun CustomerOrder.chatDeadlineMs(): Long? {
    if (status != "completed") return null
    val source = completedAt ?: updatedAt ?: return null
    val base = runCatching { Instant.parse(source).toEpochMilli() }.getOrNull() ?: return null
    return base + 30L * 60L * 1000L
}

fun CustomerOrder.chatAvailable(now: Long = System.currentTimeMillis()): Boolean {
    if (riderId.isNullOrBlank()) return false
    if (status != "completed") return status !in setOf("cancelled", "no_rider_available")
    return (chatDeadlineMs() ?: 0L) > now
}

@Composable
fun CustomerChatScreen(
    auth: NativeAuth,
    order: CustomerOrder,
    onBack: () -> Unit
) {
    val api = remember { CustomerChatApi() }
    val scope = rememberCoroutineScope()
    var moderation by remember { mutableStateOf<CustomerChatModeration?>(null) }
    var messages by remember { mutableStateOf<List<CustomerChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reportMessageId by remember { mutableStateOf<String?>(null) }
    var reportDetails by remember { mutableStateOf("") }

    suspend fun refresh() {
        val mod = api.moderation(auth, order.id)
        moderation = mod
        if (mod.accepted && !mod.blockedByMe && !mod.blockedMe && order.chatAvailable()) {
            messages = api.messages(auth, order.id)
        }
    }

    LaunchedEffect(order.id) {
        while (true) {
            runCatching { refresh() }
                .onFailure { message = it.message ?: "โหลดแชทไม่สำเร็จ" }
            delay(4_000)
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
            Spacer(Modifier.weight(1f))
            Text(order.number, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        QgSectionTitle("แชทกับ Rider", "ใช้เพื่อประสานงานออเดอร์นี้เท่านั้น")
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message!!, color = QgRed)
        }
        Spacer(Modifier.height(10.dp))

        if (!order.chatAvailable()) {
            QgCard(Modifier.fillMaxWidth()) {
                Text(
                    if (order.status == "completed")
                        "แชทปิดแล้ว ระบบให้ติดต่อกันได้ 30 นาทีหลังจบงาน"
                    else "แชทจะเปิดเมื่อ Rider รับงานแล้ว",
                    color = QgMuted
                )
            }
            return
        }

        val mod = moderation
        when {
            mod == null -> QgCard(Modifier.fillMaxWidth()) { Text("กำลังตรวจสอบสิทธิ์แชท...", color = QgMuted) }
            !mod.accepted -> {
                QgCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("กติกาการแชท QueueGo", fontWeight = FontWeight.Black)
                        Text(
                            "ใช้แชทเพื่อประสานงานออเดอร์ ห้ามคุกคาม สแปม หลอกลวง ส่งเนื้อหาไม่เหมาะสม หรือเผยข้อมูลส่วนบุคคลที่ไม่จำเป็น",
                            color = QgMuted
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (busy) return@Button
                                busy = true
                                scope.launch {
                                    runCatching {
                                        api.acceptTerms(auth)
                                        refresh()
                                    }.onFailure { message = it.message ?: "ยอมรับกติกาไม่สำเร็จ" }
                                    busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy
                        ) { Text("ยอมรับกติกาและเปิดแชท") }
                    }
                }
            }
            mod.blockedMe -> {
                QgCard(Modifier.fillMaxWidth()) {
                    Text("คู่สนทนาได้จำกัดการแชทไว้ การบล็อกไม่ยกเลิกออเดอร์", color = QgMuted)
                }
            }
            mod.blockedByMe -> {
                QgCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("คุณบล็อก Rider คนนี้ไว้", fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching {
                                        api.unblock(auth, order.id)
                                        refresh()
                                    }.onFailure { message = it.message ?: "ปลดบล็อกไม่สำเร็จ" }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ปลดบล็อกและเปิดแชท") }
                    }
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            reportMessageId = ""
                            reportDetails = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("รายงาน") }
                    OutlinedButton(
                        onClick = {
                            if (busy) return@OutlinedButton
                            busy = true
                            scope.launch {
                                runCatching {
                                    api.block(auth, order.id)
                                    refresh()
                                }.onFailure { message = it.message ?: "บล็อกไม่สำเร็จ" }
                                busy = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("บล็อก Rider") }
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (messages.isEmpty()) {
                        Text("ยังไม่มีข้อความ", color = QgMuted)
                    } else {
                        messages.forEach { m ->
                            val mine = m.senderId == auth.user.id
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth(0.86f)
                                        .background(
                                            if (mine) Color(0xFFFFECEF) else Color.White,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(11.dp)
                                ) {
                                    Text(
                                        if (mine) "คุณ" else "Rider",
                                        color = if (mine) QgRed else QgMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(m.message)
                                    if (!m.createdAt.isNullOrBlank()) {
                                        Text(m.createdAt!!, color = QgMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    }
                                    if (!mine) {
                                        Text(
                                            "รายงานข้อความ",
                                            color = QgRed,
                                            modifier = Modifier
                                                .padding(top = 5.dp)
                                                .clickable {
                                                    reportMessageId = m.id
                                                    reportDetails = ""
                                                },
                                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (reportMessageId != null) {
                    Spacer(Modifier.height(8.dp))
                    QgCard(Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                if (reportMessageId!!.isBlank()) "รายงานคู่สนทนา" else "รายงานข้อความ",
                                fontWeight = FontWeight.ExtraBold
                            )
                            OutlinedTextField(
                                value = reportDetails,
                                onValueChange = { if (it.length <= 1000) reportDetails = it },
                                label = { Text("รายละเอียดเพิ่มเติม (ถ้ามี)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(7.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { reportMessageId = null },
                                    modifier = Modifier.weight(1f)
                                ) { Text("ยกเลิก") }
                                Button(
                                    onClick = {
                                        busy = true
                                        val target = reportMessageId
                                        scope.launch {
                                            runCatching {
                                                api.report(
                                                    auth,
                                                    order.id,
                                                    target?.takeIf { it.isNotBlank() },
                                                    "other",
                                                    reportDetails
                                                )
                                            }.onSuccess {
                                                message = "ส่งรายงานให้ QueueGo ตรวจสอบแล้ว"
                                                reportMessageId = null
                                            }.onFailure {
                                                message = it.message ?: "ส่งรายงานไม่สำเร็จ"
                                            }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f)
                                ) { Text("ส่งรายงาน") }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (it.length <= 500) input = it },
                        label = { Text("พิมพ์ข้อความ") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.padding(4.dp))
                    Button(
                        onClick = {
                            if (busy || input.isBlank()) return@Button
                            val text = input
                            busy = true
                            scope.launch {
                                runCatching {
                                    api.send(auth, order.id, text)
                                    input = ""
                                    messages = api.messages(auth, order.id)
                                }.onFailure { message = it.message ?: "ส่งข้อความไม่สำเร็จ" }
                                busy = false
                            }
                        },
                        enabled = !busy && input.isNotBlank()
                    ) { Text("ส่ง") }
                }
            }
        }
    }
}
