package com.queuego.merchant

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.queuego.shared.QgGreen
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgSectionTitle
import com.queuego.shared.QgStatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MerchantLaundryScreen(auth: NativeAuth) {
    val api = remember { MerchantLaundryApi() }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<MerchantLaundryState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        runCatching { api.load(auth) }
            .onSuccess { state = it; message = null }
            .onFailure { message = it.message ?: "โหลดฝากซักไม่สำเร็จ" }
        loading = false
    }

    LaunchedEffect(auth.user.id) {
        reload()
        while (true) {
            delay(5_000)
            if (!busy) runCatching { api.load(auth) }.onSuccess { state = it }
        }
    }

    val current = state
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("ฝากซัก", "บริการร้าน + เรียก Express รับและส่งคืนได้คนละ Rider")
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(10.dp))

        if (loading && current == null) {
            Text("กำลังโหลด...", color = QgMuted)
            return
        }
        if (current == null) {
            QgCard(Modifier.fillMaxWidth()) { Text("โหลดข้อมูลไม่สำเร็จ", color = QgRed) }
            return
        }

        if (!current.featureEnabled) {
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Admin ยังปิดฝากซักสำหรับลูกค้า", fontWeight = FontWeight.ExtraBold)
                    Text("ร้านยังตั้งค่าบริการและราคาเตรียมไว้ก่อนได้", color = QgMuted)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (current.hubId == null) {
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("เปิดระบบฝากซักของร้านนี้", fontWeight = FontWeight.Black)
                    Text("ใช้ข้อมูลร้านปัจจุบันสร้าง Laundry Hub โดยไม่สร้างร้านซ้ำ", color = QgMuted)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true
                            scope.launch {
                                runCatching { api.setup(auth) }
                                    .onSuccess { reload(); message = "เปิดระบบฝากซักแล้ว" }
                                    .onFailure { message = it.message }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("เปิดตั้งค่าฝากซัก") }
                }
            }
            return
        }

        LaundrySettingsCard(
            settings = current.settings,
            busy = busy,
            onSave = { next ->
                if (busy) return@LaundrySettingsCard
                busy = true
                scope.launch {
                    runCatching { api.saveSettings(auth, next) }
                        .onSuccess { reload(); message = "บันทึกค่าฝากซักแล้ว" }
                        .onFailure { message = it.message }
                    busy = false
                }
            }
        )

        Spacer(Modifier.height(12.dp))
        LaundryServicesCard(
            services = current.services,
            busy = busy,
            onSave = { id, name, desc, type, price, minutes, active ->
                if (busy) return@LaundryServicesCard
                busy = true
                scope.launch {
                    runCatching { api.saveService(auth, id, name, desc, type, price, minutes, active) }
                        .onSuccess { reload(); message = "บันทึกบริการแล้ว" }
                        .onFailure { message = it.message }
                    busy = false
                }
            }
        )

        Spacer(Modifier.height(12.dp))
        QgSectionTitle("งานฝากซักล่าสุด", "ไม่ผูก Rider คนเดิมระหว่างขารับและขาส่งคืน")
        Spacer(Modifier.height(8.dp))
        if (current.orders.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีงานฝากซัก", color = QgMuted) }
        } else current.orders.forEach { order ->
            LaundryOrderCard(
                order = order,
                busy = busy,
                onAction = { action, qty, note ->
                    if (busy) return@LaundryOrderCard
                    busy = true
                    scope.launch {
                        runCatching { api.orderAction(auth, order.id, action, qty, note) }
                            .onSuccess { reload(); message = "อัปเดตงานฝากซักแล้ว" }
                            .onFailure { message = it.message }
                        busy = false
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun LaundrySettingsCard(
    settings: MerchantLaundrySettings,
    busy: Boolean,
    onSave: (MerchantLaundrySettings) -> Unit
) {
    var enabled by remember(settings) { mutableStateOf(settings.enabled) }
    var minimum by remember(settings) { mutableStateOf(settings.minimumOrder.toString()) }
    var pickup by remember(settings) { mutableStateOf(settings.pickupFee.toString()) }
    var returnFee by remember(settings) { mutableStateOf(settings.returnFee.toString()) }
    var roundTrip by remember(settings) { mutableStateOf(settings.roundTripFee.toString()) }
    var roundMode by remember(settings) { mutableStateOf(settings.deliveryFeeMode == "round_trip") }

    QgCard(Modifier.fillMaxWidth()) {
        Column {
            Text("ค่าบริการรับ-ส่ง", fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth()) {
                Text("เปิดรับคำขอฝากซัก", Modifier.weight(1f))
                Switch(enabled, { enabled = it })
            }
            Row(Modifier.fillMaxWidth()) {
                Text("คิดค่ารับ-ส่งรวม", Modifier.weight(1f))
                Switch(roundMode, { roundMode = it })
            }
            OutlinedTextField(minimum, { minimum = numeric(it) }, label = { Text("ยอดบริการขั้นต่ำ") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            if (roundMode) {
                OutlinedTextField(roundTrip, { roundTrip = numeric(it) }, label = { Text("ค่ารับ-ส่งรวม") }, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(pickup, { pickup = numeric(it) }, label = { Text("ค่ารับผ้า") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(returnFee, { returnFee = numeric(it) }, label = { Text("ค่าส่งคืน") }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    onSave(
                        MerchantLaundrySettings(
                            enabled,
                            minimum.toDoubleOrNull() ?: 0.0,
                            pickup.toDoubleOrNull() ?: 0.0,
                            returnFee.toDoubleOrNull() ?: 0.0,
                            roundTrip.toDoubleOrNull() ?: 0.0,
                            if (roundMode) "round_trip" else "separate"
                        )
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("บันทึกค่าฝากซัก") }
        }
    }
}

@Composable
private fun LaundryServicesCard(
    services: List<MerchantLaundryService>,
    busy: Boolean,
    onSave: (String?, String, String?, String, Double, Int?, Boolean) -> Unit
) {
    var editing by remember { mutableStateOf<MerchantLaundryService?>(null) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("per_kg") }
    var price by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }

    fun edit(service: MerchantLaundryService?) {
        editing = service
        adding = true
        name = service?.name ?: ""
        desc = service?.description ?: ""
        type = service?.pricingType ?: "per_kg"
        price = service?.price?.toString() ?: ""
        minutes = service?.estimatedMinutes?.toString() ?: ""
        active = service?.active ?: true
    }

    QgCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                Text("บริการของร้าน", Modifier.weight(1f), fontWeight = FontWeight.Black)
                OutlinedButton(onClick = { edit(null) }) { Text("+ เพิ่ม") }
            }
            if (adding) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("ชื่อบริการ") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(desc, { desc = it }, label = { Text("รายละเอียด") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("รูปแบบราคา", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth()) {
                    listOf("per_kg" to "กก.", "per_item" to "ชิ้น", "per_set" to "ชุด", "fixed" to "เหมาจ่าย").forEach { (key, label) ->
                        OutlinedButton(
                            onClick = { type = key },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (type == key) "✓ $label" else label) }
                    }
                }
                OutlinedTextField(price, { price = numeric(it) }, label = { Text("ราคา") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("เวลาประมาณ (นาที)") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth()) {
                    Text("เปิดขาย", Modifier.weight(1f))
                    Switch(active, { active = it })
                }
                Button(
                    onClick = {
                        val p = price.toDoubleOrNull()
                        if (name.trim().length >= 2 && p != null) {
                            onSave(editing?.id, name, desc, type, p, minutes.toIntOrNull(), active)
                            adding = false
                        }
                    },
                    enabled = !busy && name.trim().length >= 2 && price.toDoubleOrNull() != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("บันทึกบริการ") }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }

            if (services.isEmpty()) Text("ยังไม่มีบริการ", color = QgMuted)
            else services.forEach { service ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { edit(service) }
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(service.name, fontWeight = FontWeight.ExtraBold)
                        Text(
                            laundryMerchantUnit(service.pricingType) + " · ฿" + "%.0f".format(service.price) +
                                (service.estimatedMinutes?.let { " · $it นาที" } ?: ""),
                            color = QgMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    QgStatusPill(if (service.active) "เปิด" else "ปิด", service.active)
                }
            }
        }
    }
}

@Composable
private fun LaundryOrderCard(
    order: MerchantLaundryOrder,
    busy: Boolean,
    onAction: (String, Double?, String?) -> Unit
) {
    var qty by remember(order.id, order.status) { mutableStateOf("") }
    var reason by remember(order.id, order.status) { mutableStateOf("") }
    QgCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                Text(order.number, Modifier.weight(1f), fontWeight = FontWeight.Black)
                QgStatusPill(merchantLaundryStatus(order.status), order.status != "cancelled")
            }
            Text(order.serviceName, fontWeight = FontWeight.Bold)
            if (!order.pickupAddress.isNullOrBlank()) Text(order.pickupAddress!!, color = QgMuted, style = MaterialTheme.typography.bodySmall)
            val total = order.finalTotal ?: order.estimatedTotal
            if (total != null) Text("ยอด " + (if (order.finalTotal != null) "จริง" else "ประมาณ") + " ฿" + "%.0f".format(total), color = QgRed, fontWeight = FontWeight.Black)
            if (!order.note.isNullOrBlank()) Text("หมายเหตุ: " + order.note, color = QgMuted)

            Spacer(Modifier.height(8.dp))
            when (order.status) {
                "pending" -> {
                    Button(onClick = { onAction("accept", null, null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("รับคำขอ")
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(reason, { reason = it }, label = { Text("เหตุผลหากปฏิเสธ") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = { if (reason.isNotBlank()) onAction("cancel", null, reason) },
                        enabled = !busy && reason.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ปฏิเสธ") }
                }
                "accepted", "pickup_assigned", "picked_up" -> {
                    Text("รอผ้าถึงร้าน · งานรับผ้าใช้ Express/Rider ที่ถูกเรียกในรอบนี้", color = QgMuted)
                }
                "at_hub" -> Button(
                    onClick = { onAction("start_washing", null, null) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("เริ่มซัก / ทำความสะอาด") }
                "washing" -> {
                    if (order.pricingType != "fixed") {
                        OutlinedTextField(
                            qty,
                            { qty = numeric(it) },
                            label = { Text("จำนวนจริงก่อนสรุปยอด") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Button(
                        onClick = { onAction("ready_return", qty.toDoubleOrNull(), null) },
                        enabled = !busy && (order.pricingType == "fixed" || qty.toDoubleOrNull()?.let { it > 0 } == true),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("งานเสร็จ · พร้อมเรียก Express ส่งคืน") }
                }
                "ready_return", "return_assigned", "out_for_return" -> {
                    Text("พร้อมส่งคืน / กำลังส่งคืน · สามารถเป็น Rider คนละคนกับขารับ", color = QgMuted)
                }
                "completed" -> Text("ส่งคืนสำเร็จ", color = QgGreen, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun numeric(value: String): String = value.filter { it.isDigit() || it == '.' }

private fun laundryMerchantUnit(type: String): String = when (type) {
    "per_kg" -> "ต่อกก."
    "per_item" -> "ต่อชิ้น"
    "per_set" -> "ต่อชุด"
    "fixed" -> "เหมาจ่าย"
    else -> type
}

private fun merchantLaundryStatus(status: String): String = when (status) {
    "pending" -> "รอร้านรับ"
    "accepted" -> "รอรับผ้า"
    "pickup_assigned" -> "Express ไปรับผ้า"
    "picked_up" -> "กำลังนำผ้ามาร้าน"
    "at_hub" -> "ผ้าถึงร้าน"
    "washing" -> "กำลังซัก"
    "ready_return" -> "พร้อมส่งคืน"
    "return_assigned" -> "Express รับงานส่งคืน"
    "out_for_return" -> "กำลังส่งคืน"
    "completed" -> "เสร็จแล้ว"
    "cancelled" -> "ยกเลิก"
    else -> status
}
