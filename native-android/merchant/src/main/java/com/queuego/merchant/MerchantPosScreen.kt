package com.queuego.merchant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QgSectionTitle
import com.queuego.shared.QgStatusPill
import kotlinx.coroutines.launch

@Composable
fun MerchantPosScreen(auth: NativeAuth, onBack: () -> Unit) {
    val api = remember { MerchantPosApi() }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<PosSnapshot?>(null) }
    var selectedBillId by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf("TAKEAWAY") }
    var tableId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var cashReceived by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            runCatching { api.snapshot(auth) }
                .onSuccess { fresh ->
                    snapshot = fresh
                    if (selectedBillId != null && fresh.bills.none { it.id == selectedBillId }) {
                        selectedBillId = null
                    }
                }
                .onFailure { message = it.message ?: "โหลด POS ไม่สำเร็จ" }
        }
    }

    LaunchedEffect(auth.user.id) { reload() }

    val snap = snapshot
    val bill = snap?.bills?.find { it.id == selectedBillId }
    val lines = bill?.let { snap.linesByOrder[it.id].orEmpty() }.orEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, enabled = !busy) { Text("ย้อนกลับ") }
            Spacer(Modifier.width(10.dp))
            QgSectionTitle("POS หน้าร้าน", "ใช้ฐานข้อมูลและ RPC เดิมของ QueueGo")
        }
        Spacer(Modifier.height(10.dp))

        if (!message.isNullOrBlank()) {
            Text(message!!, color = if (message!!.contains("แล้ว")) QgRed else androidx.compose.material3.MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (snap == null) {
            CircularProgressIndicator()
            return@Column
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == "TAKEAWAY") Button(onClick = { mode = "TAKEAWAY"; tableId = null; selectedBillId = null }) { Text("รับกลับ") }
            else OutlinedButton(onClick = { mode = "TAKEAWAY"; tableId = null; selectedBillId = null }) { Text("รับกลับ") }

            if (mode == "DINE_IN" && tableId == null) Button(onClick = { mode = "DINE_IN"; tableId = null; selectedBillId = null }) { Text("ทานที่ร้าน · ไม่ระบุโต๊ะ") }
            else OutlinedButton(onClick = { mode = "DINE_IN"; tableId = null; selectedBillId = null }) { Text("ไม่ระบุโต๊ะ") }

            snap.tables.filter { it.active }.forEach { table ->
                if (mode == "DINE_IN" && tableId == table.id) {
                    Button(onClick = { mode = "DINE_IN"; tableId = table.id; selectedBillId = snap.bills.find { it.tableId == table.id }?.id }) { Text(table.label) }
                } else {
                    OutlinedButton(onClick = { mode = "DINE_IN"; tableId = table.id; selectedBillId = snap.bills.find { it.tableId == table.id }?.id }) { Text(table.label) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(500) },
            label = { Text("หมายเหตุสำหรับครัว") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        QgSectionTitle("เมนูสินค้า", if (bill == null) "แตะสินค้าเพื่อเปิดบิล" else bill.number)
        Spacer(Modifier.height(8.dp))

        snap.products.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { product ->
                    QgCard(
                        Modifier.weight(1f).padding(bottom = 8.dp).clickable(enabled = !busy) {
                            busy = true
                            scope.launch {
                                runCatching {
                                    api.addProduct(auth, selectedBillId, mode, tableId, product.id, note)
                                }.onSuccess { id ->
                                    selectedBillId = id
                                    message = "เพิ่มสินค้าแล้ว"
                                    snapshot = runCatching { api.snapshot(auth) }.getOrNull() ?: snapshot
                                }.onFailure { message = it.message ?: "เพิ่มสินค้าไม่สำเร็จ" }
                                busy = false
                            }
                        }
                    ) {
                        Column {
                            QgRemoteImage(product.image, Modifier.fillMaxWidth().height(84.dp), product.name)
                            Spacer(Modifier.height(6.dp))
                            Text(product.name, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                            Text("฿" + "%.0f".format(product.price), color = QgRed, fontWeight = FontWeight.Black)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))
        if (bill != null) {
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(bill.number, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        QgStatusPill(bill.kitchenStatus, true)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (lines.isEmpty()) {
                        Text("ยังไม่มีรายการ", color = QgMuted)
                    } else lines.forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(line.name, fontWeight = FontWeight.Bold)
                                Text("จำนวน " + line.quantity + " · ฿" + "%.0f".format(line.total), color = QgMuted)
                            }
                            if (bill.kitchenStatus == "NEW" && line.productId != null) {
                                OutlinedButton(
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            runCatching { api.reduceProduct(auth, bill.id, bill.type, bill.tableId, line.productId, line.description) }
                                                .onSuccess {
                                                    snapshot = api.snapshot(auth)
                                                    message = "ลดรายการแล้ว"
                                                }
                                                .onFailure { message = it.message ?: "ลดรายการไม่สำเร็จ" }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy
                                ) { Text("−") }
                            }
                        }
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("ยอดรวม", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("฿" + "%.0f".format(bill.total), color = QgRed, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            val action = when (bill.kitchenStatus) {
                "NEW" -> "send" to "ส่งเข้าครัว"
                "SENT_TO_KITCHEN" -> "cook" to "เริ่มทำ"
                "COOKING" -> "ready" to "พร้อมเสิร์ฟ"
                "READY" -> "serve" to "เสิร์ฟแล้ว"
                else -> null
            }
            if (action != null) {
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { api.billAction(auth, bill.id, action.first) }
                                .onSuccess {
                                    snapshot = api.snapshot(auth)
                                    message = action.second + "แล้ว"
                                }
                                .onFailure { message = it.message ?: "เปลี่ยนสถานะบิลไม่สำเร็จ" }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(action.second, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(8.dp))
            }

            if (bill.kitchenStatus in setOf("READY", "SERVED")) {
                OutlinedTextField(
                    value = cashReceived,
                    onValueChange = { cashReceived = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("เงินสดที่รับ · ยอดบิล ฿" + "%.0f".format(bill.total)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val cash = cashReceived.toDoubleOrNull()
                        if (cash == null || cash < bill.total) {
                            message = "เงินสดที่รับต้องไม่น้อยกว่ายอดบิล"
                        } else {
                            busy = true
                            scope.launch {
                                runCatching { api.takeCash(auth, bill.id, cash) }
                                    .onSuccess {
                                        snapshot = api.snapshot(auth)
                                        selectedBillId = null
                                        cashReceived = ""
                                        message = "ปิดบิลแล้ว · เงินทอน ฿" + "%.0f".format(cash - bill.total)
                                    }
                                    .onFailure { message = it.message ?: "ปิดบิลไม่สำเร็จ" }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("รับเงินสดและปิดบิล", fontWeight = FontWeight.ExtraBold) }
            }
        }

        Spacer(Modifier.height(16.dp))
        QgSectionTitle("บิลที่ยังไม่ปิด")
        Spacer(Modifier.height(8.dp))
        if (snap.bills.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีบิล", color = QgMuted) }
        } else snap.bills.forEach { open ->
            QgCard(Modifier.fillMaxWidth().padding(bottom = 7.dp).clickable {
                selectedBillId = open.id
                mode = open.type
                tableId = open.tableId
            }) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(open.number, fontWeight = FontWeight.ExtraBold)
                        Text(if (open.type == "DINE_IN") "ทานที่ร้าน" else "รับกลับ", color = QgMuted)
                    }
                    Text("฿" + "%.0f".format(open.total), color = QgRed, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
