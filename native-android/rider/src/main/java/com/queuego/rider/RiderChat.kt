package com.queuego.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgSectionTitle
import com.queuego.shared.QueueGoNativeApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RiderChatModeration(
    val accepted: Boolean,
    val blockedByMe: Boolean,
    val blockedMe: Boolean
)

data class RiderChatMessage(
    val id: String,
    val senderId: String,
    val message: String,
    val createdAt: String?
)

class RiderChatApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    private fun obj(raw: Any): JSONObject = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }

    internal suspend fun window(auth: QueueGoAuth, orderId: String): RiderChatWindow {
        val rows = http.array(http.get(
            "orders?select=status,updated_at&id=eq." + http.enc(orderId) + "&limit=1",
            auth.session.accessToken
        ))
        val order = rows.optJSONObject(0) ?: return RiderChatWindow("cancelled", null)
        val status = order.optString("status")
        val deliveredAt = if (status == "completed") {
            http.array(http.get(
                "deliveries?select=delivered_at&order_id=eq." + http.enc(orderId) + "&limit=1",
                auth.session.accessToken
            )).optJSONObject(0)?.optString("delivered_at")
        } else null
        return riderChatWindow(status, deliveredAt, order.optString("updated_at"))
    }

    suspend fun moderation(auth: QueueGoAuth, orderId: String): RiderChatModeration {
        val o = obj(
            http.rpc(
                "qg_chat_moderation_state",
                auth.session.accessToken,
                JSONObject().put("p_order_id", orderId)
            )
        )
        return RiderChatModeration(
            accepted = o.optBoolean("accepted", false),
            blockedByMe = o.optBoolean("blockedByMe", o.optBoolean("blocked_by_me", false)),
            blockedMe = o.optBoolean("blockedMe", o.optBoolean("blocked_me", false))
        )
    }

    suspend fun acceptTerms(auth: QueueGoAuth) {
        http.rpc(
            "qg_accept_ugc_terms",
            auth.session.accessToken,
            JSONObject().put("p_version", 1)
        )
    }

    suspend fun block(auth: QueueGoAuth, orderId: String) {
        http.rpc(
            "qg_block_chat_counterpart",
            auth.session.accessToken,
            JSONObject().put("p_order_id", orderId)
        )
    }

    suspend fun unblock(auth: QueueGoAuth, orderId: String) {
        http.rpc(
            "qg_unblock_chat_counterpart",
            auth.session.accessToken,
            JSONObject().put("p_order_id", orderId)
        )
    }

    suspend fun report(
        auth: QueueGoAuth,
        orderId: String,
        messageId: String?,
        details: String?
    ) {
        http.rpc(
            "qg_report_chat",
            auth.session.accessToken,
            JSONObject()
                .put("p_order_id", orderId)
                .put("p_message_id", messageId ?: JSONObject.NULL)
                .put("p_reason", "other")
                .put("p_details", details?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        )
    }

    suspend fun messages(auth: QueueGoAuth, orderId: String): List<RiderChatMessage> {
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
                    RiderChatMessage(
                        id = r.optString("id"),
                        senderId = r.optString("sender_id"),
                        message = r.optString("message"),
                        createdAt = r.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    suspend fun send(
        auth: QueueGoAuth,
        orderId: String,
        text: String,
        requestId: String = UUID.randomUUID().toString()
    ) {
        val clean = validateRiderChatPayload(text)
        val path = "order_chat_messages?select=id&id=eq." + http.enc(requestId) +
            "&order_id=eq." + http.enc(orderId) + "&sender_id=eq." + http.enc(auth.user.id)
        sendRiderChatOnce(
            exists = { http.array(http.get(path, auth.session.accessToken)).length() > 0 },
            insert = {
                val response = http.post("order_chat_messages", auth.session.accessToken,
                    JSONObject().put("id", requestId).put("order_id", orderId)
                        .put("sender_id", auth.user.id).put("message", clean))
                val rows = http.array(response)
                (0 until rows.length()).any { rows.optJSONObject(it)?.optString("id") == requestId }
            }
        )
    }
}

@Composable
fun RiderChatScreen(
    auth: QueueGoAuth,
    job: RiderJob,
    onBack: () -> Unit
) {
    key(auth.user.id, auth.session.sessionId, job.id) {
        RiderChatRoom(auth, job, onBack)
    }
}

@Composable
private fun RiderChatRoom(auth: QueueGoAuth, job: RiderJob, onBack: () -> Unit) {
    val api = remember { RiderChatApi() }
    val scope = rememberCoroutineScope()
    var moderation by remember(auth.user.id, auth.session.sessionId, job.id) { mutableStateOf<RiderChatModeration?>(null) }
    var messages by remember(auth.user.id, auth.session.sessionId, job.id) { mutableStateOf<List<RiderChatMessage>>(emptyList()) }
    var input by remember(auth.user.id, auth.session.sessionId, job.id) { mutableStateOf("") }
    var busy by remember(auth.user.id, auth.session.sessionId, job.id) { mutableStateOf(false) }
    var message by remember(auth.user.id, auth.session.sessionId, job.id) { mutableStateOf<String?>(null) }
    var readError by remember(auth.user.id, auth.session.sessionId, job.id) { mutableStateOf<String?>(null) }
    var reportMessageId by remember(auth.user.id, auth.session.sessionId, job.id) { mutableStateOf<String?>(null) }
    var reportDetails by remember(auth.user.id, auth.session.sessionId, job.id) { mutableStateOf("") }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val back by rememberUpdatedState(onBack)
    var window by remember(auth.user.id, auth.session.sessionId, job.id) { mutableStateOf<RiderChatWindow?>(null) }
    var closed by remember(auth.user.id, auth.session.sessionId, job.id) { mutableStateOf(false) }

    fun closeExpired() {
        if (!closed) { closed = true; back() }
    }

    suspend fun refresh() {
        val nextWindow = api.window(auth, job.id)
        currentCoroutineContext().ensureActive()
        window = nextWindow
        if (!nextWindow.isOpen(System.currentTimeMillis())) { closeExpired(); return }
        val mod = api.moderation(auth, job.id)
        val nextMessages = if (mod.accepted && !mod.blockedByMe && !mod.blockedMe) {
            api.messages(auth, job.id)
        } else emptyList()
        currentCoroutineContext().ensureActive()
        if (!nextWindow.isOpen(System.currentTimeMillis())) { closeExpired(); return }
        moderation = mod
        messages = nextMessages
        readError = null
    }

    LaunchedEffect(auth.user.id, auth.session.sessionId, auth.session.accessToken, job.id, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (!closed) {
                try { refresh() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    currentCoroutineContext().ensureActive()
                    readError = failure.message ?: "โหลดแชทไม่สำเร็จ"
                }
                delay(4_000)
            }
        }
    }

    LaunchedEffect(window, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            window?.deadline?.let { deadline ->
                delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L))
                closeExpired()
            }
        }
    }


    val context = LocalContext.current
    val outbox = remember { RiderChatOutbox() }

    suspend fun deliver(payload: String, clearInput: Boolean) {
        val liveWindow = api.window(auth, job.id)
        currentCoroutineContext().ensureActive()
        window = liveWindow
        if (!liveWindow.isOpen(System.currentTimeMillis())) { closeExpired(); return }
        api.send(auth, job.id, payload, outbox.requestId(payload))
        currentCoroutineContext().ensureActive()
        outbox.confirmed(payload)
        if (clearInput && input.trim() == payload.trim()) input = ""
        message = null
        // Delivery is already confirmed. A read failure must not become a send failure.
        try { refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            readError = failure.message ?: "โหลดแชทไม่สำเร็จ"
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !busy && !closed) {
            busy = true
            scope.launch {
                try {
                    val payload = prepareRiderChatImage(context.contentResolver, uri)
                    currentCoroutineContext().ensureActive()
                    deliver(payload, false)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { message = failure.message ?: "ส่งรูปไม่สำเร็จ" }
                finally { busy = false }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
            Spacer(Modifier.weight(1f))
            Text(job.numberLabel, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        QgSectionTitle("แชทกับลูกค้า", "ใช้เพื่อประสานงานออเดอร์นี้เท่านั้น")
        val visibleMessage = readError ?: message
        if (!visibleMessage.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(visibleMessage, color = QgRed)
        }
        Spacer(Modifier.height(10.dp))

        val mod = moderation
        when {
            mod == null -> QgCard(Modifier.fillMaxWidth()) {
                Text("กำลังตรวจสอบสิทธิ์แชท...", color = QgMuted)
            }
            !mod.accepted -> QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("กติกาการแชท QueueGo", fontWeight = FontWeight.Black)
                    Text(
                        "ใช้แชทเพื่อประสานงานออเดอร์ ห้ามคุกคาม สแปม หลอกลวง หรือส่งเนื้อหาไม่เหมาะสม",
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
                                }.onFailure { if (it is CancellationException) throw it; message = it.message ?: "ยอมรับกติกาไม่สำเร็จ" }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) { Text("ยอมรับกติกาและเปิดแชท") }
                }
            }
            mod.blockedMe -> QgCard(Modifier.fillMaxWidth()) {
                Text("คู่สนทนาได้จำกัดการแชทไว้ การบล็อกไม่ยกเลิกออเดอร์", color = QgMuted)
            }
            mod.blockedByMe -> QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("คุณบล็อกลูกค้าคนนี้ไว้", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching {
                                    api.unblock(auth, job.id)
                                    refresh()
                                }.onFailure { if (it is CancellationException) throw it; message = it.message ?: "ปลดบล็อกไม่สำเร็จ" }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ปลดบล็อกและเปิดแชท") }
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
                                    api.block(auth, job.id)
                                    refresh()
                                }.onFailure { if (it is CancellationException) throw it; message = it.message ?: "บล็อกไม่สำเร็จ" }
                                busy = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("บล็อกลูกค้า") }
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty()) {
                        item { Text("ยังไม่มีข้อความ", color = QgMuted) }
                    } else {
                        items(messages, key = { it.id }) { chat ->
                            val mine = chat.senderId == auth.user.id
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
                                        if (mine) "คุณ" else "ลูกค้า",
                                        color = if (mine) QgRed else QgMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (chat.message.startsWith("__IMG__")) {
                                        RiderChatImage(chat.message.removePrefix("__IMG__"))
                                    } else Text(chat.message)
                                    if (!chat.createdAt.isNullOrBlank()) {
                                        Text(
                                            chat.createdAt!!,
                                            color = QgMuted,
                                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    if (!mine) {
                                        Text(
                                            "รายงานข้อความ",
                                            color = QgRed,
                                            modifier = Modifier
                                                .padding(top = 5.dp)
                                                .clickable {
                                                    reportMessageId = chat.id
                                                    reportDetails = ""
                                                },
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
                                                    job.id,
                                                    target?.takeIf { it.isNotBlank() },
                                                    reportDetails
                                                )
                                            }.onSuccess {
                                                message = "ส่งรายงานให้ QueueGo ตรวจสอบแล้ว"
                                                reportMessageId = null
                                            }.onFailure {
                                                if (it is CancellationException) throw it
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
                    OutlinedButton(
                        onClick = { photoPicker.launch("image/*") },
                        modifier = Modifier.width(44.dp).height(46.dp),
                        contentPadding = PaddingValues(0.dp),
                        enabled = !busy && !closed && window?.isOpen(System.currentTimeMillis()) == true
                    ) { Text("ส่งรูป", fontSize = 9.sp) }
                    Spacer(Modifier.padding(4.dp))
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
                            if (busy || input.isBlank() || window?.isOpen(System.currentTimeMillis()) != true) return@Button
                            val text = input
                            busy = true
                            scope.launch {
                                try { deliver(text, true) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { message = failure.message ?: "ส่งข้อความไม่สำเร็จ" }
                                finally { busy = false }
                            }
                        },
                        enabled = !busy && input.isNotBlank() && !closed && window?.isOpen(System.currentTimeMillis()) == true
                    ) { Text("ส่ง") }
                }
            }
        }
    }
}
