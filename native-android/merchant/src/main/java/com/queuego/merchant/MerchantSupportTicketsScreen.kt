package com.queuego.merchant

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NATIVE_SUPPORT_TICKET_CATEGORIES
import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeSupportTicketApi
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MerchantSupportTicketsScreen(
    auth: NativeAuth,
    orders: List<MerchantOrder>,
    orderId: String?,
    orderNumber: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val api = remember { NativeSupportTicketApi() }
    val scope = rememberCoroutineScope()
    var tickets by remember(auth.user.id) { mutableStateOf(emptyList<com.queuego.shared.NativeSupportTicket>()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(NATIVE_SUPPORT_TICKET_CATEGORIES.first().first) }
    var categoryMenu by remember { mutableStateOf(false) }
    var orderMenu by remember { mutableStateOf(false) }
    var selectedOrder by remember(orderId) { mutableStateOf(orderId) }
    var evidence by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var openingEvidence by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { evidence = it }

    suspend fun refresh() {
        loading = true
        try {
            tickets = api.tickets(auth.session.accessToken)
            loadError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadError = failure.message ?: "โหลดเรื่องที่แจ้งไม่สำเร็จ"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(auth.user.id, auth.session.accessToken) { refresh() }
    BackHandler(enabled = !busy) { onBack() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onBack, enabled = !busy) { Text("ย้อนกลับ") }
            OutlinedButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !busy) { Text("รีเฟรช") }
        }
        Spacer(Modifier.height(10.dp))
        MerchantPageTitle("ติดต่อฝ่ายดูแล", "แจ้งปัญหาและติดตามสถานะจากระบบ")
        message?.let { Text(it, color = QgRed, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(12.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ออเดอร์ที่เกี่ยวข้อง (ถ้ามี)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { orderMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(orders.firstOrNull { it.id == selectedOrder }?.number ?: if (selectedOrder == null) "ไม่ระบุออเดอร์" else orderNumber ?: "ออเดอร์ที่เลือก")
                    }
                    DropdownMenu(expanded = orderMenu, onDismissRequest = { orderMenu = false }) {
                        DropdownMenuItem(text = { Text("ไม่ระบุออเดอร์") }, onClick = { selectedOrder = null; orderMenu = false })
                        orders.take(50).forEach { order -> DropdownMenuItem(
                            text = { Text(order.number) },
                            onClick = { selectedOrder = order.id; orderMenu = false }
                        ) }
                    }
                }
                Text("ประเภทปัญหา", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Text(NATIVE_SUPPORT_TICKET_CATEGORIES.firstOrNull { it.first == category }?.second ?: "อื่น ๆ")
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        NATIVE_SUPPORT_TICKET_CATEGORIES.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { category = key; categoryMenu = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 2000) details = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    label = { Text("รายละเอียด") },
                    placeholder = { Text("อธิบายปัญหาอย่างน้อย 10 ตัวอักษร") }
                )
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (evidence == null) "รูปหลักฐาน (ถ้ามี)" else "เลือกรูปหลักฐานแล้ว")
                }
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        message = null
                        scope.launch {
                            try {
                                val path = evidence?.let {
                                    api.uploadEvidence(context, auth.session.authUserId, auth.session.accessToken, it)
                                }
                                api.create(auth.session.accessToken, selectedOrder, category, details, path)
                                details = ""
                                evidence = null
                                message = "ส่งเรื่องให้ฝ่ายดูแลแล้ว"
                                refresh()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                message = failure.message ?: "ส่งเรื่องไม่สำเร็จ"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && details.trim().length in 10..2000,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                ) { Text(if (busy) "กำลังส่งเรื่อง..." else "ส่งเรื่อง") }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("เรื่องที่แจ้งไว้", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        when {
            loading -> Text("กำลังโหลด...", color = QgMuted, modifier = Modifier.padding(vertical = 14.dp))
            loadError != null -> Column {
                Text("โหลดเรื่องที่แจ้งไม่สำเร็จ: $loadError", color = QgRed, modifier = Modifier.padding(vertical = 10.dp))
                OutlinedButton(onClick = { scope.launch { refresh() } }) { Text("ลองอีกครั้ง") }
            }
            tickets.isEmpty() -> Text("ยังไม่มีเรื่องที่แจ้ง", color = QgMuted, modifier = Modifier.padding(vertical = 14.dp))
            else -> tickets.forEach { ticket ->
                Spacer(Modifier.height(8.dp))
                QgCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(NATIVE_SUPPORT_TICKET_CATEGORIES.firstOrNull { it.first == ticket.category }?.second ?: ticket.category, fontWeight = FontWeight.Bold)
                            Text(ticket.status, color = QgRed, fontSize = 11.sp)
                        }
                        Text(ticket.details, modifier = Modifier.padding(top = 7.dp))
                        ticket.createdAt?.let { Text(it, color = QgMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp)) }
                        ticket.adminNote?.let { Text("ฝ่ายดูแล: $it", color = QgMuted, modifier = Modifier.padding(top = 5.dp)) }
                        if (ticket.evidencePath != null) OutlinedButton(
                            onClick = {
                                if (openingEvidence) return@OutlinedButton
                                openingEvidence = true
                                scope.launch {
                                    try {
                                        val url = api.signedEvidenceUrl(auth.session.accessToken, ticket.evidencePath)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (failure: Exception) {
                                        message = failure.message ?: "เปิดหลักฐานไม่สำเร็จ"
                                    } finally { openingEvidence = false }
                                }
                            },
                            enabled = !openingEvidence
                        ) { Text("ดูหลักฐาน") }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}
