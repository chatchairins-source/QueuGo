package com.queuego.rider

import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ข้อความใช้บ่อย (สูงสุด 5)", modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("+ เพิ่ม") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { text ->
                Text(text, Modifier.combinedClickable(enabled = enabled, onClick = { onPick(text) }, onLongClick = onEdit).padding(8.dp))
            }
        }
    }
}

@Composable
internal fun RiderQuickMessageEditor(rows: List<String>, onSave: (List<String>) -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("ข้อความใช้บ่อย") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("บันทึกได้สูงสุด 5 ข้อความ แตะข้อความในแชตเพื่อใช้งานทันที")
                if (rows.isEmpty()) Text("ยังไม่มีข้อความที่บันทึกไว้")
                rows.forEachIndexed { index, text ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text, Modifier.weight(1f))
                        TextButton(onClick = { onSave(rows.filterIndexed { i, _ -> i != index }) }) { Text("ลบ") }
                    }
                }
                OutlinedTextField(value = draft, onValueChange = { if (it.length <= 100) draft = it },
                    placeholder = { Text("เพิ่มข้อความ เช่น กำลังไปส่งครับ") },
                    enabled = rows.size < 5, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onSave(addRiderQuickMessage(rows, draft)); draft = "" },
                    enabled = rows.size < 5 && draft.isNotBlank()) { Text("เพิ่ม") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("ปิด") } }
    )
}
