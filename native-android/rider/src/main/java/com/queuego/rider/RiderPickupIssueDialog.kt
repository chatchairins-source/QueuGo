package com.queuego.rider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class RiderPickupIssueReason(val key: String, val label: String, val category: String)

internal val riderPickupIssueReasons = listOf(
    RiderPickupIssueReason("merchant_unreachable", "ร้านติดต่อไม่ได้", "merchant"),
    RiderPickupIssueReason("merchant_closed", "ร้านปิด", "merchant"),
    RiderPickupIssueReason("merchant_not_ready", "ร้านยังไม่พร้อม / รอนาน", "merchant"),
    RiderPickupIssueReason("missing_items", "สินค้าไม่ครบ", "missing_item"),
    RiderPickupIssueReason("out_of_stock", "สินค้าหมด", "missing_item"),
    RiderPickupIssueReason("merchant_cash_dispute", "ยอดเงินที่ต้องจ่ายร้านมีปัญหา", "cash"),
    RiderPickupIssueReason("pickup_not_found", "จุดรับหาไม่เจอ", "merchant")
)

internal fun riderPickupIssueDetails(jobNumber: String, reason: RiderPickupIssueReason, note: String, pickupId: String?): String =
    "[PICKUP_ISSUE:${reason.key}] $jobNumber · ${reason.label}" +
        (pickupId?.let { " · [PICKUP_STOP:$it]" } ?: "") +
        note.trim().take(1200).takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()

// Identical reports retry the same authorized ticket, including after process death.
internal fun riderPickupIssueRequestId(userId: String, orderId: String, details: String): String =
    UUID.nameUUIDFromBytes("pickup-issue|$userId|$orderId|$details".toByteArray(StandardCharsets.UTF_8)).toString()

@Composable
internal fun RiderPickupIssueDialog(
    auth: QueueGoAuth,
    job: RiderJob,
    pickupId: String?,
    onDismiss: () -> Unit,
    onCallShop: (RiderJob) -> Unit,
    onChat: (RiderJob) -> Unit,
    onReported: (String) -> Unit
) {
    val api = remember { QueueGoApi() }
    val scope = rememberCoroutineScope()
    var order by remember(job.id, pickupId) { mutableStateOf<RiderJob?>(if (pickupId == null) job else null) }
    var reasonKey by rememberSaveable(job.id, pickupId) { mutableStateOf(riderPickupIssueReasons.first().key) }
    var note by rememberSaveable(job.id, pickupId) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(auth.user.id, job.id, pickupId) {
        if (pickupId != null) try {
            order = job.copy(id = api.pickupIssueOrderId(auth, pickupId))
        } catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) { error = failure.message ?: "ตรวจสอบจุดรับไม่สำเร็จ กรุณาเปิดใหม่" }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("ปัญหาที่จุดรับ · ${job.numberLabel}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ติดต่อร้านเมื่อเหมาะสม แล้วส่งเรื่องให้ฝ่ายดูแล งานและยอดเงินจะไม่เปลี่ยนจากรายงานนี้")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { order?.let(onCallShop) }, enabled = order != null && !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("โทรหาร้าน") }
                    OutlinedButton(onClick = { order?.let(onChat) }, enabled = order != null && !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("แชตออเดอร์") }
                }
                Text("แชตออเดอร์ใช้ห้องเดิมร่วมกับร้านและลูกค้า")
                riderPickupIssueReasons.forEach { reason ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = reasonKey == reason.key, onClick = { reasonKey = reason.key }, enabled = !busy)
                        Text(reason.label)
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it.take(1200) },
                    label = { Text("รายละเอียดเพิ่มเติม") }, enabled = !busy, maxLines = 4)
                error?.let { Text(it, color = QgRed) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("กลับ") } },
        confirmButton = {
            TextButton(enabled = order != null && !busy, onClick = {
                if (busy) return@TextButton
                val current = order ?: return@TextButton
                val reason = riderPickupIssueReasons.first { it.key == reasonKey }
                busy = true
                error = null
                scope.launch {
                    try {
                        api.reportPickupIssue(auth, current, reason, note, pickupId)
                        onReported(reason.label)
                    } catch (failure: CancellationException) { throw failure }
                    catch (failure: Exception) { error = failure.message ?: "ส่งรายงานไม่สำเร็จ กรุณาลองใหม่" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "กำลังส่ง…" else "ส่งให้ฝ่ายดูแล") }
        }
    )
}
