package com.queuego.customer

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgSectionTitle
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CustomerSupportScreen(
    auth: NativeAuth,
    orders: List<CustomerOrder>,
    initialOrderId: String? = null,
    onBack: () -> Unit
) {
    val api = remember { CustomerExtrasApi() }
    val scope = rememberCoroutineScope()
    var tickets by remember { mutableStateOf<List<CustomerSupportTicket>>(emptyList()) }
    var selectedOrderId by remember { mutableStateOf(initialOrderId ?: "") }
    var category by remember { mutableStateOf("other") }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            runCatching { api.supportTickets(auth) }
                .onSuccess { tickets = it }
                .onFailure { message = it.message ?: "โหลดรายการช่วยเหลือไม่สำเร็จ" }
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
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (selectedOrderId.isBlank()) "✓ เรื่องทั่วไป" else "เรื่องทั่วไป") }
                }
                orders.take(10).forEach { order ->
                    Spacer(Modifier.height(5.dp))
                    OutlinedButton(
                        onClick = { selectedOrderId = order.id },
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
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (busy || details.trim().length < 10) return@Button
                        busy = true
                        message = null
                        val ticketId = UUID.randomUUID().toString()
                        scope.launch {
                            runCatching {
                                api.createSupportTicket(
                                    auth = auth,
                                    ticketId = ticketId,
                                    orderId = selectedOrderId.ifBlank { null },
                                    category = category,
                                    details = details
                                )
                            }.onSuccess {
                                details = ""
                                message = "ส่งเรื่องให้ทีม QueueGo แล้ว"
                                refresh()
                            }.onFailure {
                                message = it.message ?: "ส่งเรื่องไม่สำเร็จ"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && details.trim().length >= 10,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "กำลังส่ง..." else "ส่งเรื่องให้ทีมงาน")
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
                        Spacer(Modifier.height(4.dp))
                        Text(ticket.details)
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

private val supportCategories = listOf(
    "missing_item" to "สินค้าไม่ครบ",
    "wrong_item" to "สินค้าไม่ตรงรายการ",
    "damaged_food" to "สินค้ามีปัญหา",
    "rider" to "เกี่ยวกับ Rider",
    "merchant" to "เกี่ยวกับร้านค้า",
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
