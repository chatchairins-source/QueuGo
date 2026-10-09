package com.queuego.customer

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.queuego.shared.NativeChatImage
import com.queuego.shared.prepareNativeChatImage
import com.queuego.shared.validateNativeChatPayload
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.queuego.shared.NativeChatPending
import com.queuego.shared.NativeChatPendingStore
import com.queuego.shared.nativeChatPermanentFailure
import com.queuego.shared.QueueGoHttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.queuego.shared.sendNativeChatOnce
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

private val customerChatReportReasons = linkedMapOf(
    "harassment" to "คุกคาม / กลั่นแกล้ง", "inappropriate" to "เนื้อหาไม่เหมาะสม",
    "spam" to "สแปม", "fraud" to "หลอกลวง / ฉ้อโกง", "safety" to "ความปลอดภัย", "other" to "อื่น ๆ"
)

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
        require(reason in customerChatReportReasons) { "เหตุผลไม่ถูกต้อง" }
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
                    "&order=created_at.desc&limit=100",
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
        }.asReversed()
    }

    suspend fun send(auth: NativeAuth, orderId: String, text: String, requestId: String = UUID.randomUUID().toString()) {
        val clean = validateNativeChatPayload(text)

        val path = "order_chat_messages?select=id&id=eq." + http.enc(requestId) +
            "&order_id=eq." + http.enc(orderId) + "&sender_id=eq." + http.enc(auth.user.id)
        sendNativeChatOnce(
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
fun CustomerChatScreen(
    auth: NativeAuth,
    order: CustomerOrder,
    onBack: () -> Unit
) {
    key(auth.user.id, auth.session.sessionId, order.id) { CustomerChatRoom(auth, order, onBack) }
}

@Composable
private fun CustomerChatRoom(auth: NativeAuth, order: CustomerOrder, onBack: () -> Unit) {
    val api = remember { CustomerChatApi() }
    val scope = rememberCoroutineScope()
    var moderation by remember { mutableStateOf<CustomerChatModeration?>(null) }
    var messages by remember { mutableStateOf<List<CustomerChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reportMessageId by remember { mutableStateOf<String?>(null) }
    var reportDetails by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("harassment") }
    var reasonMenu by remember { mutableStateOf(false) }
    var reportError by remember { mutableStateOf<String?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOrder by rememberUpdatedState(order)
    val back by rememberUpdatedState(onBack)
    val context = LocalContext.current
    val pendingStore = remember { NativeChatPendingStore(File(context.noBackupFilesDir, "customer-chat-pending"), auth.user.id, order.id) }
    var pending by remember { mutableStateOf<NativeChatPending?>(null) }
    var pendingReady by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoName by remember { mutableStateOf<String?>(null) }
    var pendingPhotoSource by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            photoUri = uri
            photoName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
            }.getOrNull()
        }
    }
    var readError by remember { mutableStateOf<String?>(null) }
    var closed by remember { mutableStateOf(false) }
    BackHandler { if (!busy) back() }

    suspend fun closeExpired() {
        if (!closed) {
            closed = true
            try { withContext(Dispatchers.IO) { pendingStore.clear() } }
            finally { back() }
        }
    }

    suspend fun refresh() {
        if (!currentOrder.chatAvailable()) { closeExpired(); return }
        val mod = api.moderation(auth, order.id)
        val nextMessages = if (mod.accepted && !mod.blockedByMe && !mod.blockedMe) {
            api.messages(auth, order.id)
        } else emptyList()
        currentCoroutineContext().ensureActive()
        if (!currentOrder.chatAvailable()) { closeExpired(); return }
        moderation = mod
        messages = nextMessages
        readError = null
    }

    LaunchedEffect(reportMessageId) {
        reportReason = "harassment"
        reasonMenu = false
        reportError = null
    }

    LaunchedEffect(pendingStore) {
        try {
            pending = withContext(Dispatchers.IO) { pendingStore.load() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { message = failure.message ?: "กู้คืนข้อความค้างส่งไม่สำเร็จ" }
        finally { pendingReady = true }
    }

    suspend fun refreshAfterConfirmedAction() {
        try { refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            readError = failure.message ?: "โหลดแชทไม่สำเร็จ"
        }
    }

    LaunchedEffect(auth.session.accessToken, lifecycle) {
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

    LaunchedEffect(order.status, order.completedAt, order.updatedAt, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!currentOrder.chatAvailable()) { closeExpired(); return@repeatOnLifecycle }
            currentOrder.chatDeadlineMs()?.let { deadline ->
                delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L))
                if (!currentOrder.chatAvailable()) closeExpired()
            }
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
        val visibleMessage = readError ?: message
        if (!visibleMessage.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(visibleMessage, color = QgRed)
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
                                        refreshAfterConfirmedAction()
                                    }.onFailure { if (it is CancellationException) throw it; message = it.message ?: "ยอมรับกติกาไม่สำเร็จ" }
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
                                        refreshAfterConfirmedAction()
                                    }.onFailure { if (it is CancellationException) throw it; message = it.message ?: "ปลดบล็อกไม่สำเร็จ" }
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
                                    refreshAfterConfirmedAction()
                                }.onFailure { if (it is CancellationException) throw it; message = it.message ?: "บล็อกไม่สำเร็จ" }
                                busy = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("บล็อก Rider") }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty()) {
                        item { Text("ยังไม่มีข้อความ", color = QgMuted) }
                    } else {
                        items(messages, key = { it.id }) { m ->
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
                                    if (m.message.startsWith("__IMG__")) NativeChatImage(m.message.removePrefix("__IMG__"))
                                    else Text(m.message)
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
                    AlertDialog(
                        onDismissRequest = { if (!busy) reportMessageId = null },
                        title = { Text("รายงานเนื้อหาแชต") },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                reportError?.let { Text(it, color = QgRed) }
                                Text("เหตุผล")
                                Box {
                                    OutlinedButton(onClick = { reasonMenu = true }, enabled = !busy) {
                                        Text(customerChatReportReasons.getValue(reportReason))
                                    }
                                    DropdownMenu(expanded = reasonMenu, onDismissRequest = { reasonMenu = false }) {
                                        customerChatReportReasons.forEach { (value, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = { reportReason = value; reasonMenu = false })
                                        }
                                    }
                                }
                                OutlinedTextField(value = reportDetails,
                                    onValueChange = { if (it.length <= 1000) reportDetails = it },
                                    label = { Text("รายละเอียด") },
                                    placeholder = { Text("อธิบายเพิ่มเติม (ไม่บังคับ)") },
                                    minLines = 4, maxLines = 4,
                                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                            }
                        },
                        dismissButton = { TextButton(onClick = { reportMessageId = null }, enabled = !busy) { Text("ยกเลิก") } },
                        confirmButton = {
                            TextButton(onClick = {
                                if (busy) return@TextButton
                                busy = true
                                reportError = null
                                val target = reportMessageId
                                val reason = reportReason
                                val details = reportDetails
                                scope.launch {
                                    try {
                                        api.report(auth, order.id, target?.takeIf { it.isNotBlank() }, reason, details)
                                        currentCoroutineContext().ensureActive()
                                        message = "ส่งรายงานให้ QueueGo ตรวจสอบแล้ว"
                                        reportMessageId = null
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { reportError = failure.message ?: "ส่งรายงานไม่สำเร็จ" }
                                    finally { busy = false }
                                }
                            }, enabled = !busy) { Text("ส่งรายงาน") }
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = input, onValueChange = { if (it.length <= 500) input = it },
                    placeholder = { Text("พิมพ์ข้อความ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { photoPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, enabled = !busy) { Text("เลือกไฟล์") }
                    if (photoUri != null) Text(photoName ?: "รูปภาพที่เลือก", color = QgMuted, modifier = Modifier.weight(1f))
                }
                Button(onClick = {
                    if (busy || closed || !pendingReady || (pending == null && input.isBlank() && photoUri == null) || !currentOrder.chatAvailable()) return@Button
                    val text = input
                    val uri = photoUri
                    busy = true
                    scope.launch {
                        try {
                            val recovered = withContext(Dispatchers.IO) { pendingStore.load() }
                            val payload = recovered?.payload ?: (uri?.let { prepareNativeChatImage(context.contentResolver, it,
                                setOf("image/jpeg", "image/png", "image/webp")) } ?: text)
                            val request = withContext(Dispatchers.IO) { pendingStore.prepare(payload) }
                            pending = request
                            if (recovered == null) pendingPhotoSource = uri
                            if (!currentOrder.chatAvailable()) { closeExpired(); return@launch }
                            api.send(auth, order.id, request.payload, request.id)
                            currentCoroutineContext().ensureActive()
                            withContext(Dispatchers.IO) { pendingStore.clear() }
                            pending = null
                            if ((recovered == null && input == text) || input == request.payload) input = ""
                            if (photoUri != null && photoUri == pendingPhotoSource) { photoUri = null; photoName = null }
                            pendingPhotoSource = null
                            message = null
                            refreshAfterConfirmedAction()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) {
                            if (failure is QueueGoHttpException && nativeChatPermanentFailure(failure.statusCode)) {
                                withContext(Dispatchers.IO) { pendingStore.clear() }
                                pending = null
                            }
                            message = failure.message ?: "ส่งข้อความไม่สำเร็จ"
                        }
                        finally { busy = false }
                    }
                }, enabled = !busy && !closed && pendingReady && (pending != null || input.isNotBlank() || photoUri != null) && currentOrder.chatAvailable(),
                    modifier = Modifier.fillMaxWidth()) { Text("ส่งข้อความ / ตรวจผลข้อความเดิม") }
            }
        }
    }
}
