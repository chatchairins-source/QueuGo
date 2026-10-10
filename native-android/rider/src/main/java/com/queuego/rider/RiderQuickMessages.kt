package com.queuego.rider

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgRed
import org.json.JSONArray

internal val riderDefaultQuickMessages = listOf(
    "กำลังเดินทางไปครับ", "ถึงหน้าร้านแล้วครับ", "ได้รับสินค้าแล้ว กำลังไปส่งครับ",
    "ใกล้ถึงแล้วครับ อีกประมาณ 5 นาที", "ถึงจุดส่งแล้วครับ"
)

internal fun addRiderQuickMessage(rows: List<String>, text: String): List<String> {
    require(rows.size < 5) { "บันทึกได้สูงสุด 5 ข้อความ" }
    val clean = text.trim()
    require(clean.isNotBlank()) { "กรุณาพิมพ์ข้อความ" }
    return rows + clean.take(100)
}

internal class RiderQuickMessageStore(context: Context, userId: String) {
    private val prefs = context.applicationContext.getSharedPreferences("queuego_rider_quick_messages", Context.MODE_PRIVATE)
    private val key = "messages:$userId"
    fun load(): List<String> {
        val raw = prefs.getString(key, null) ?: return riderDefaultQuickMessages
        return runCatching {
            val array = JSONArray(raw)
            (0 until minOf(5, array.length())).map { array.getString(it).take(100) }
        }.getOrDefault(emptyList())
    }
    fun save(rows: List<String>) { prefs.edit().putString(key, JSONArray(rows.take(5)).toString()).apply() }
}

@Composable
internal fun RiderQuickMessageShelf(rows: List<String>, enabled: Boolean, onPick: (String) -> Unit, onEdit: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ข้อความด่วน",
                modifier = Modifier.weight(1f),
                color = Color(0xFF6F676B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = onEdit,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("จัดการ", color = QgRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (rows.isEmpty()) {
            Text(
                "ยังไม่มีข้อความด่วน",
                color = Color(0xFF9B9498),
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                rows.forEach { text ->
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier
                            .heightIn(min = 36.dp)
                            .widthIn(max = 240.dp)
                            .clip(shape)
                            .background(Color(0xFFFFF7F9))
                            .border(1.dp, Color(0xFFF0DDE3), shape)
                            .combinedClickable(
                                enabled = enabled,
                                onClick = { onPick(text) },
                                onLongClick = onEdit
                            )
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text,
                            color = Color(0xFF393336),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RiderQuickMessageEditor(rows: List<String>, onSave: (List<String>) -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("จัดการข้อความด่วน", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "บันทึกได้สูงสุด 5 ข้อความ แตะข้อความด่วนในหน้าแชตเพื่อใส่ลงช่องพิมพ์",
                    color = Color(0xFF777075),
                    fontSize = 12.sp
                )
                if (rows.isEmpty()) {
                    Text("ยังไม่มีข้อความที่บันทึกไว้", color = Color(0xFF9B9498), fontSize = 12.sp)
                }
                rows.forEachIndexed { index, text ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFAF7F8), RoundedCornerShape(14.dp))
                            .padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text,
                            Modifier.weight(1f),
                            color = Color(0xFF393336),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { onSave(rows.filterIndexed { i, _ -> i != index }) }) {
                            Text("ลบ", color = QgRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 100) draft = it },
                    placeholder = { Text("เช่น กำลังไปส่งครับ") },
                    label = { Text("ข้อความใหม่") },
                    enabled = rows.size < 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                TextButton(
                    onClick = { onSave(addRiderQuickMessage(rows, draft)); draft = "" },
                    enabled = rows.size < 5 && draft.isNotBlank(),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("เพิ่มข้อความ", color = QgRed, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("เสร็จสิ้น", color = QgRed, fontWeight = FontWeight.Bold)
            }
        }
    )
}
