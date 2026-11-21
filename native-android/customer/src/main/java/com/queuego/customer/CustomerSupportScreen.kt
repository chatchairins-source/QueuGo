package com.queuego.customer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeSupportTicketApi
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgSectionTitle
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun CustomerSupportScreen(
    auth: NativeAuth,
    orders: List<CustomerOrder>,
    initialOrderId: String? = null,
    onBack: () -> Unit
) {
    val api = remember { CustomerExtrasApi() }
    val evidenceApi = remember { NativeSupportTicketApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cache = remember(context.applicationContext) {
        CustomerSupportCache(context.applicationContext)
    }
    var tickets by remember { mutableStateOf<List<CustomerSupportTicket>>(emptyList()) }
    var selectedOrderId by remember { mutableStateOf(initialOrderId ?: "") }
    var category by remember { mutableStateOf("other") }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var openingEvidenceId by remember { mutableStateOf<String?>(null) }
    var selectedEvidenceUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedEvidenceUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedEvidencePath by remember { mutableStateOf<String?>(null) }
    var pendingTicketId by remember { mutableStateOf<String?>(null) }
    val evidencePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedEvidenceUri = uri
            uploadedEvidenceUri = null
            uploadedEvidencePath = null
        }
    }

    suspend fun refresh(force: Boolean = false) {
        val cached = if (!force && tickets.isEmpty()) {
            withContext(Dispatchers.IO) { cache.read(auth.user.id) }
        } else null
        if (cached != null) {
            tickets = cached.rows
            if (cached.fresh) {
                message = null
                return
            }
        }
        try {
            val fresh = api.supportTickets(auth)
            tickets = fresh
            withContext(Dispatchers.IO) { cache.write(auth.user.id, fresh) }
            message = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (tickets.isEmpty()) message = failure.message ?: "โหลดรายการช่วยเหลือไม่สำเร็จ"
        }
    }

    LaunchedEffect(auth.user.id) { refresh() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
        Spacer(Modifier.height(12.dp))
        QgSectionTitle("ติดต่อฝ่ายช่วยเหลือ", "แจ้งปัญหาเกี่ยวกับออเดอร์หรือการใช้งาน QueueGo")
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, color = QgRed)
        }
        Spacer(Modifier.height(10.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("ออเดอร์ที่เกี่ยวข้อง", fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { selectedOrderId = "" },
                        enabled = pendingTicketId == null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (selectedOrderId.isBlank()) "✓ เรื่องทั่วไป" else "เรื่องทั่วไป") }
                }
                orders.take(30).forEach { order ->
                    Spacer(Modifier.height(5.dp))
                    OutlinedButton(
                        onClick = { selectedOrderId = order.id },
                        enabled = pendingTicketId == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text((if (selectedOrderId == order.id) "✓ " else "") + order.number)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("ประเภทเรื่อง", fontWeight = FontWeight.ExtraBold)
                supportCategories.forEach { (key, label) ->
                    Spacer(Modifier.height(5.dp))
                    OutlinedButton(
                        onClick = { category = key },
                        enabled = pendingTicketId == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text((if (category == key) "✓ " else "") + label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 2000) details = it },
                    label = { Text("รายละเอียดปัญหา") },
                    minLines = 4,
                    maxLines = 8,
                    enabled = pendingTicketId == null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { evidencePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                    enabled = pendingTicketId == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedEvidenceUri?.lastPathSegment?.substringAfterLast('/')?.take(48)
                        ?: "เลือกรูปหลักฐาน (ไม่เกิน 5 MB)")
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (busy || details.trim().length < 10) return@Button
                        val submittedDetails = details
                        val submittedOrderId = selectedOrderId.ifBlank { null }
                        val submittedCategory = category
                        val submittedEvidenceUri = selectedEvidenceUri
                        val ticketId = pendingTicketId ?: UUID.randomUUID().toString()
                        pendingTicketId = ticketId
                        busy = true
                        message = null
                        scope.launch {
                            runCatching {
                                val evidencePath = if (submittedEvidenceUri == null) null else {
                                    if (uploadedEvidenceUri == submittedEvidenceUri) uploadedEvidencePath
                                    else evidenceApi.uploadEvidence(
                                        context,
                                        auth.session.authUserId,
                                        auth.session.accessToken,
                                        submittedEvidenceUri
                                    ).also {
                                        uploadedEvidenceUri = submittedEvidenceUri
                                        uploadedEvidencePath = it
                                    }
                                }
                                api.createSupportTicket(
                                    auth = auth,
                                    ticketId = ticketId,
                                    orderId = submittedOrderId,
                                    category = submittedCategory,
                                    details = submittedDetails,
                                    evidencePath = evidencePath
                                )
                            }.onSuccess {
                                details = ""
                                selectedEvidenceUri = null
                                uploadedEvidenceUri = null
                                uploadedEvidencePath = null
                                pendingTicketId = null
                                withContext(Dispatchers.IO) { cache.invalidate(auth.user.id) }
                                refresh(force = true)
                                message = "ส่งเรื่องให้ทีม QueueGo แล้ว"
                            }.onFailure {
                                message = it.message ?: "ส่งเรื่องไม่สำเร็จ"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && details.trim().length >= 10,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(when {
                        busy -> "กำลังส่ง..."
                        pendingTicketId != null -> "ตรวจผลคำร้องเดิม"
                        else -> "ส่งเรื่องให้ทีมงาน"
                    })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        QgSectionTitle("เรื่องที่ส่งแล้ว")
        Spacer(Modifier.height(8.dp))
        if (tickets.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีเรื่องที่ส่ง", color = QgMuted) }
        } else {
            tickets.forEach { ticket ->
                QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column {
                        Text(
                            supportCategories.firstOrNull { it.first == ticket.category }?.second ?: "เรื่องที่แจ้ง",
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(supportStatus(ticket.status), color = QgMuted)
                        ticket.createdAt?.takeIf { it.isNotBlank() }?.let { createdAt ->
                            Text(
                                customerThaiDateTime(createdAt),
                                color = QgMuted,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(ticket.details)
                        ticket.evidencePath?.takeIf { it.isNotBlank() }?.let { path ->
                            Spacer(Modifier.height(7.dp))
                            OutlinedButton(
                                onClick = {
                                    if (openingEvidenceId != null) return@OutlinedButton
                                    if (!customerEvidencePathBelongsTo(path, auth.session.authUserId)) {
                                        message = "ลิงก์หลักฐานไม่ถูกต้อง"
                                        return@OutlinedButton
                                    }
                                    openingEvidenceId = ticket.id
                                    message = null
                                    scope.launch {
                                        try {
                                            val signedUrl = evidenceApi.signedEvidenceUrl(
                                                auth.session.accessToken,
                                                path
                                            )
                                            val uri = Uri.parse(signedUrl)
                                            require(uri.scheme == "https" && uri.host ==
                                                Uri.parse(QueueGoNativeApi.BASE_URL).host &&
                                                uri.path.orEmpty().startsWith("/storage/v1/object/sign/qg-evidence/")) {
                                                "ลิงก์หลักฐานไม่ถูกต้อง"
                                            }
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, uri)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            message = failure.message ?: "เปิดหลักฐานไม่สำเร็จ"
                                        } finally {
                                            openingEvidenceId = null
                                        }
                                    }
                                },
                                enabled = openingEvidenceId == null
                            ) {
                                Text(if (openingEvidenceId == ticket.id) "กำลังเปิด..." else "ดูรูปหลักฐาน")
                            }
                        }
                        if (!ticket.adminNote.isNullOrBlank()) {
                            Spacer(Modifier.height(7.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(7.dp))
                            Text("คำตอบจากทีม QueueGo", color = QgRed, fontWeight = FontWeight.ExtraBold)
                            Text(ticket.adminNote!!)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun customerEvidencePathBelongsTo(path: String, userId: String): Boolean {
    if (userId.isBlank() || !path.startsWith("$userId/")) return false
    val objectName = path.removePrefix("$userId/")
    return Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png|webp)")
        .matches(objectName)
}

private val supportCategories = listOf(
    "missing_item" to "สินค้าไม่ครบ",
    "wrong_item" to "สินค้าไม่ตรงรายการ",
    "damaged_food" to "สินค้ามีปัญหา",
    "rider" to "ติดต่อเกี่ยวกับไรเดอร์",
    "merchant" to "ติดต่อเกี่ยวกับร้าน",
    "payment" to "การชำระเงิน",
    "other" to "เรื่องอื่น ๆ"
)

private fun supportStatus(status: String): String = when (status.uppercase()) {
    "OPEN" -> "รับเรื่องแล้ว"
    "IN_REVIEW" -> "กำลังตรวจสอบ"
    "WAITING_USER" -> "รอข้อมูลเพิ่มเติม"
    "RESOLVED" -> "ดำเนินการแล้ว"
    "REJECTED" -> "ไม่สามารถดำเนินการได้"
    "CLOSED" -> "ปิดเรื่องแล้ว"
    else -> status
}
