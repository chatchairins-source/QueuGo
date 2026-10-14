package com.queuego.rider

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class RiderDeliveryIssueReason(
    val key: String,
    val label: String
)

internal val riderDeliveryIssueReasons = listOf(
    RiderDeliveryIssueReason("customer_unreachable", "ติดต่อลูกค้าไม่ได้"),
    RiderDeliveryIssueReason("recipient_refuses_delivery", "ลูกค้าปฏิเสธรับสินค้า"),
    RiderDeliveryIssueReason("recipient_refuses_cash", "ลูกค้าปฏิเสธชำระเงิน"),
    RiderDeliveryIssueReason("wrong_dropoff", "ที่อยู่หรือจุดส่งมีปัญหา"),
    RiderDeliveryIssueReason("unsafe_dropoff", "ไม่สามารถส่งได้อย่างปลอดภัย"),
    RiderDeliveryIssueReason("other", "ปัญหาอื่น")
)

internal fun riderDeliveryIssueDetails(
    orderNumber: String,
    reason: RiderDeliveryIssueReason,
    note: String
): String = buildString {
    append("[DELIVERY_ISSUE:")
    append(reason.key)
    append("] ")
    append(orderNumber.ifBlank { "QueueGo order" })
    append(" · ")
    append(reason.label)
    note.trim().takeIf { it.isNotEmpty() }?.let {
        append(" · หมายเหตุ: ")
        append(it.take(1200))
    }
}

@Composable
internal fun RiderDeliveryIssueDialog(
    auth: QueueGoAuth,
    job: RiderJob,
    onDismiss: () -> Unit,
    onCallCustomer: () -> Unit,
    onOpenChat: () -> Unit,
    networkAvailable: Boolean,
    onReported: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val api = remember { QueueGoApi() }
    var selectedKey by rememberSaveable(job.id) { mutableStateOf(riderDeliveryIssueReasons.first().key) }
    var note by rememberSaveable(job.id) { mutableStateOf("") }
    var contacted by rememberSaveable(job.id) { mutableStateOf(false) }
    var busy by remember(job.id) { mutableStateOf(false) }
    var error by remember(job.id) { mutableStateOf<String?>(null) }
    val selected = riderDeliveryIssueReasons.first { it.key == selectedKey }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Column {
                Text("ส่งสินค้าไม่สำเร็จ", fontWeight = FontWeight.Black)
                Text(job.numberLabel, color = QgRed, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "ลองติดต่อลูกค้าก่อน แล้วเลือกสาเหตุเพื่อส่งเรื่องให้ฝ่ายดูแล งานจะยังไม่ถูกยกเลิกอัตโนมัติ",
                    color = QgMuted
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            contacted = true
                            error = null
                            onCallCustomer()
                        },
                        enabled = !busy && networkAvailable,
                        modifier = Modifier.weight(1f)
                    ) { Text("โทรหาลูกค้า") }
                    OutlinedButton(
                        onClick = {
                            contacted = true
                            error = null
                            onOpenChat()
                        },
                        enabled = !busy && networkAvailable,
                        modifier = Modifier.weight(1f)
                    ) { Text("แชต") }
                }

                HorizontalDivider()

                Text("เกิดอะไรขึ้น", fontWeight = FontWeight.Bold)
                riderDeliveryIssueReasons.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedKey == item.key,
                            onClick = { if (!busy) selectedKey = item.key },
                            enabled = !busy
                        )
                        Text(item.label, modifier = Modifier.weight(1f))
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = contacted,
                        onCheckedChange = { if (!busy) contacted = it },
                        enabled = !busy
                    )
                    Text("ฉันได้โทรหรือแชตกับลูกค้าแล้ว")
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 1200) note = it },
                    label = { Text("รายละเอียดเพิ่มเติม (ไม่บังคับ)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    maxLines = 4
                )

                if (!networkAvailable) {
                    Text(RIDER_NETWORK_GUARD_MESSAGE, color = QgRed, modifier = Modifier.padding(top = 2.dp))
                }
                error?.let {
                    Text(it, color = QgRed, modifier = Modifier.padding(top = 2.dp))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("กลับ") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!contacted) {
                        error = "กรุณาโทรหรือแชตกับลูกค้าก่อนรายงาน"
                        return@TextButton
                    }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            api.reportDeliveryIssue(auth, job, selected.key, note)
                            onReported(selected.label)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            error = failure.message ?: "ส่งรายงานไม่สำเร็จ"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && networkAvailable
            ) { Text(if (busy) "กำลังส่ง..." else "ส่งให้ฝ่ายดูแล") }
        }
    )
}
