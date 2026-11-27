package com.queuego.rider

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

internal data class RiderSafetyIncidentReason(
    val key: String,
    val label: String
)

internal val riderSafetyIncidentReasons = listOf(
    RiderSafetyIncidentReason("accident_or_injury", "อุบัติเหตุ / มีผู้บาดเจ็บ"),
    RiderSafetyIncidentReason("threat_or_harassment", "ถูกคุกคาม / ทะเลาะวิวาท"),
    RiderSafetyIncidentReason("unsafe_location", "พื้นที่หรือจุดส่งไม่ปลอดภัย"),
    RiderSafetyIncidentReason("road_hazard", "อันตรายบนถนน / เส้นทาง"),
    RiderSafetyIncidentReason("vehicle_issue", "ปัญหารถระหว่างงาน"),
    RiderSafetyIncidentReason("other", "เหตุการณ์อื่น")
)

internal fun riderSafetyIncidentDetails(
    orderNumber: String?,
    reason: RiderSafetyIncidentReason,
    note: String,
    location: RiderSafetyLocation?
): String = buildString {
    append("[SAFETY_INCIDENT:")
    append(reason.key)
    append("] ")
    append(orderNumber?.trim()?.takeIf { it.isNotEmpty() } ?: "ไม่มีออเดอร์")
    append(" · ")
    append(reason.label)
    location?.let {
        append(" · ตำแหน่งที่ Rider เลือกแนบ=")
        append(String.format(Locale.US, "%.6f,%.6f", it.latitude, it.longitude))
    }
    note.trim().takeIf { it.isNotEmpty() }?.let {
        append(" · หมายเหตุ: ")
        append(it.take(1200))
    }
}

private class RiderSafetyIncidentApi(
    private val http: QueueGoNativeApi = QueueGoNativeApi()
) {
    suspend fun report(
        auth: QueueGoAuth,
        activeJob: RiderJob?,
        reason: RiderSafetyIncidentReason,
        note: String,
        location: RiderSafetyLocation?
    ) {
        val details = riderSafetyIncidentDetails(
            orderNumber = activeJob?.numberLabel,
            reason = reason,
            note = note,
            location = location
        )
        http.rpc(
            "qg_create_ticket",
            auth.session.accessToken,
            JSONObject()
                .put("p_ticket_id", UUID.randomUUID().toString())
                .put("p_order_id", activeJob?.id ?: JSONObject.NULL)
                .put("p_category", "rider")
                .put("p_details", details)
                .put("p_evidence_path", JSONObject.NULL)
        )
    }
}

@Composable
internal fun RiderSafetyIncidentDialog(
    auth: QueueGoAuth,
    activeJob: RiderJob?,
    currentLocation: RiderSafetyLocation?,
    onDismiss: () -> Unit,
    onReported: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val api = remember { RiderSafetyIncidentApi() }
    var selectedKey by rememberSaveable(activeJob?.id) {
        mutableStateOf(riderSafetyIncidentReasons.first().key)
    }
    var note by rememberSaveable(activeJob?.id) { mutableStateOf("") }
    var includeLocation by rememberSaveable(activeJob?.id) { mutableStateOf(false) }
    var busy by remember(activeJob?.id) { mutableStateOf(false) }
    var error by remember(activeJob?.id) { mutableStateOf<String?>(null) }
    val selected = riderSafetyIncidentReasons.first { it.key == selectedKey }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Column {
                Text("รายงานเหตุการณ์ความปลอดภัย", fontWeight = FontWeight.Black)
                activeJob?.let {
                    Text(it.numberLabel, color = QgRed, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "หากเป็นเหตุฉุกเฉินที่ต้องการตำรวจหรือการแพทย์ ให้ใช้ 191 / 1669 ก่อน รายงานนี้ส่งให้ QueueGo Support และไม่ยกเลิกออเดอร์อัตโนมัติ",
                    color = QgMuted
                )

                HorizontalDivider()
                Text("เกิดเหตุอะไรขึ้น", fontWeight = FontWeight.Bold)

                riderSafetyIncidentReasons.forEach { item ->
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
                        checked = includeLocation,
                        onCheckedChange = {
                            if (!busy && currentLocation != null) includeLocation = it
                        },
                        enabled = !busy && currentLocation != null
                    )
                    Column(Modifier.weight(1f)) {
                        Text("แนบตำแหน่งปัจจุบันให้ QueueGo Support")
                        Text(
                            if (currentLocation == null) {
                                "ยังไม่มีตำแหน่งที่ใหม่พอ จึงไม่แนบอัตโนมัติ"
                            } else {
                                "ค่าเริ่มต้นปิด · แนบเฉพาะเมื่อคุณเลือก"
                            },
                            color = QgMuted
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 1200) note = it },
                    label = { Text("รายละเอียดเพิ่มเติม (ไม่บังคับ)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    maxLines = 4
                )

                error?.let {
                    Text(it, color = QgRed)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("กลับ")
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            api.report(
                                auth = auth,
                                activeJob = activeJob,
                                reason = selected,
                                note = note,
                                location = currentLocation.takeIf { includeLocation }
                            )
                            onReported(selected.label)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            error = failure.message ?: "ส่งรายงานไม่สำเร็จ"
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text(if (busy) "กำลังส่ง..." else "ส่งให้ QueueGo Support")
            }
        }
    )
}
