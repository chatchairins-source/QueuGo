package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val riderMapFeedbackCategories = listOf(
    "road_closed" to "ถนนปิด",
    "wrong_route" to "เส้นทางไม่เหมาะสม",
    "missing_road" to "ถนน/ซอยหาย",
    "bad_road" to "สภาพถนนไม่ดี",
    "unsafe_turn" to "จุดเลี้ยวไม่ปลอดภัย",
    "other" to "อื่น ๆ"
)

internal val riderMapFeedbackCategoryKeys: Set<String> =
    riderMapFeedbackCategories.mapTo(linkedSetOf()) { it.first }

internal fun riderMapFeedbackCategoryLabel(key: String): String =
    riderMapFeedbackCategories.firstOrNull { it.first == key }?.second ?: key

@Composable
internal fun RiderMapFeedbackDialog(
    auth: QueueGoAuth,
    api: QueueGoApi,
    location: RiderSafetyLocation,
    onSubmitted: () -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("รายงานปัญหาแผนที่", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("กรุณาจอดในที่ปลอดภัยก่อนรายงาน", color = QgRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onClose, enabled = !busy) { Text("ปิด", color = QgRed) }
            }

            Text(
                "QueueGo จะส่งเฉพาะหมวดปัญหา จุดตำแหน่งนี้ และข้อความที่คุณกรอก ไม่แนบออเดอร์ ลูกค้า หรือร้านค้า และจะไม่แก้เส้นทางอัตโนมัติ",
                color = QgMuted,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
            )

            riderMapFeedbackCategories.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { (key, label) ->
                        val active = selected == key
                        if (active) {
                            Button(
                                onClick = { selected = key },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                            ) { Text(label, fontSize = 10.sp) }
                        } else {
                            OutlinedButton(
                                onClick = { selected = key },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(13.dp),
                                border = BorderStroke(1.dp, Color(0xFFE4DDE0))
                            ) { Text(label, fontSize = 10.sp, color = Color(0xFF3F383B)) }
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
            }

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 500) note = it },
                label = { Text("รายละเอียดเพิ่มเติม (ไม่บังคับ)") },
                supportingText = { Text("${note.length}/500") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                enabled = !busy
            )

            error?.let { Text(it, color = QgRed, fontSize = 9.sp, modifier = Modifier.padding(top = 7.dp)) }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val category = selected ?: return@Button
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            api.submitMapFeedback(
                                auth = auth,
                                category = category,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                note = note
                            )
                            onSubmitted()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "ส่งรายงานไม่สำเร็จ"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && selected != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QgRed)
            ) {
                Text(if (busy) "กำลังส่ง…" else "ส่งให้ทีมแผนที่ตรวจสอบ", fontWeight = FontWeight.Black)
            }
        }
    }
}
