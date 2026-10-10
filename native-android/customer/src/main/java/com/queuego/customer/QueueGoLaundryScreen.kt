package com.queuego.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgSectionTitle
import kotlinx.coroutines.launch

@Composable
fun LaundryNativeScreen(
    auth: NativeAuth,
    location: CustomerLocation?,
    address: String,
    onAddress: (String) -> Unit,
    onGps: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val api = remember { CustomerLaundryApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val requestStore = remember(auth.user.id) { CustomerLaundryRequestStore(context, auth.user.id) }
    val checkoutJournal = remember(requestStore) { requestStore.journal() }
    val checkoutRecovery = remember(checkoutJournal) {
        CustomerCheckoutRecovery<PendingLaundryCheckout, org.json.JSONObject>(checkoutJournal)
    }
    var pendingCheckoutRecord by remember(auth.user.id) {
        mutableStateOf(runCatching { checkoutJournal.read() }.getOrNull())
    }
    var retryPendingSignal by remember { mutableStateOf(0) }
    var catalog by remember { mutableStateOf<LaundryCatalog?>(null) }
    var selectedHub by remember { mutableStateOf<LaundryHub?>(null) }
    var services by remember { mutableStateOf<List<LaundryService>>(emptyList()) }
    var selectedService by remember { mutableStateOf<LaundryService?>(null) }
    var quantity by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val checkoutPending = pendingCheckoutRecord != null

    LaunchedEffect(auth.user.id) {
        loading = true
        runCatching { api.catalog(auth) }
            .onSuccess { catalog = it }
            .onFailure { message = it.message ?: "โหลดบริการฝากซักไม่สำเร็จ" }
        loading = false
    }

    LaunchedEffect(auth.session.accessToken, pendingCheckoutRecord?.body?.toString(), retryPendingSignal) {
        if (pendingCheckoutRecord == null || busy) return@LaunchedEffect
        busy = true
        runCatching {
            checkoutRecovery.reconcile { api.sendPrepared(auth, it.prepared()) }
        }.onSuccess { receipt ->
            if (receipt != null) {
                pendingCheckoutRecord = null
                message = "ตรวจพบคำขอฝากซักที่ยืนยันแล้ว"
                onDone()
            }
        }.onFailure {
            message = "มีคำขอฝากซักเดิมรอตรวจสอบ · กดตรวจผลเมื่อเชื่อมต่อได้"
        }
        pendingCheckoutRecord = runCatching { checkoutJournal.read() }.getOrNull()
        busy = false
    }

    fun chooseHub(hub: LaundryHub) {
        if (checkoutPending) return
        selectedHub = hub
        services = emptyList()
        selectedService = null
        quantity = ""
        scope.launch {
            runCatching { api.services(auth, hub.id) }
                .onSuccess {
                    services = it
                    selectedService = it.firstOrNull()
                }
                .onFailure { message = it.message ?: "โหลดบริการของร้านไม่สำเร็จ" }
        }
    }

    val cfg = selectedHub?.let { catalog?.settings?.get(it.id) }
    val svc = selectedService
    val qty = quantity.toDoubleOrNull()?.takeIf { it > 0 }
    val serviceAmount = when {
        svc == null -> null
        svc.pricingType == "fixed" -> maxOf(svc.price, cfg?.minimumOrder ?: 0.0)
        qty != null -> maxOf(svc.price * qty, cfg?.minimumOrder ?: 0.0)
        else -> null
    }
    val deliveryFee = cfg?.deliveryFee ?: 0.0
    val estimatedTotal = serviceAmount?.plus(deliveryFee)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("ฝากซัก", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("เลือกร้านและบริการ แล้วเรียกไรเดอร์รับ-ส่งถึงที่", color = QgMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(142.dp)
                .background(QgRed, RoundedCornerShape(20.dp))
                .padding(18.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column {
                Text("QueueGo Laundry", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                Text("รับผ้า → ร้านซัก → ส่งคืน · ไรเดอร์ขาไปและขากลับไม่จำเป็นต้องเป็นคนเดิม", color = Color.White)
            }
        }

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, color = MaterialTheme.colorScheme.error)
        }

        if (checkoutPending) {
            Spacer(Modifier.height(8.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("กำลังตรวจผลคำขอฝากซักเดิม", fontWeight = FontWeight.ExtraBold)
                    Text("ระบบจะใช้ request เดิมเท่านั้น เพื่อไม่สร้างคำขอซ้ำ", color = QgMuted)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { retryPendingSignal++ },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("ตรวจผลคำขอเดิม")
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        when {
            loading -> CircularProgressIndicator()
            catalog?.featureEnabled == false -> QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("ฝากซักยังปิดทดสอบ", fontWeight = FontWeight.ExtraBold)
                    Text("Admin ยังไม่ได้เปิด feature สำหรับลูกค้า", color = QgMuted)
                }
            }
            catalog?.hubs.isNullOrEmpty() -> QgCard(Modifier.fillMaxWidth()) {
                Text("ยังไม่มีร้านฝากซักเปิดให้บริการ", color = QgMuted)
            }
            selectedHub == null -> {
                QgSectionTitle("เลือกร้านฝากซัก")
                Spacer(Modifier.height(8.dp))
                catalog!!.hubs.forEach { hub ->
                    val settings = catalog!!.settings[hub.id]
                    QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(enabled = !checkoutPending) { chooseHub(hub) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(54.dp).background(Color(0xFFFFF0F2), RoundedCornerShape(17.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("◎", color = QgRed, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(hub.name, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "รับผ้า ฿" + "%.0f".format(settings?.pickupFee ?: 0.0) +
                                        " · ส่งคืน ฿" + "%.0f".format(settings?.returnFee ?: 0.0),
                                    color = QgMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text("›", color = QgMuted, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
            else -> {
                OutlinedButton(
                    onClick = { selectedHub = null; services = emptyList(); selectedService = null },
                    enabled = !checkoutPending
                ) {
                    Text("เปลี่ยนร้าน")
                }
                Spacer(Modifier.height(10.dp))
                QgSectionTitle(selectedHub!!.name, "เลือกบริการ")
                Spacer(Modifier.height(8.dp))

                if (services.isEmpty()) {
                    QgCard(Modifier.fillMaxWidth()) { Text("ร้านนี้ยังไม่มีบริการที่เปิดขาย", color = QgMuted) }
                } else services.forEach { service ->
                    QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(enabled = !checkoutPending) {
                        selectedService = service
                        quantity = ""
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedService?.id == service.id,
                                onClick = { selectedService = service; quantity = "" },
                                enabled = !checkoutPending
                            )
                            Column(Modifier.weight(1f)) {
                                Text(service.name, fontWeight = FontWeight.ExtraBold)
                                if (!service.description.isNullOrBlank()) {
                                    Text(service.description!!, color = QgMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                if (service.estimatedMinutes != null) {
                                    Text("ประมาณ " + service.estimatedMinutes + " นาที", color = QgMuted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(
                                "฿" + "%.0f".format(service.price) +
                                    if (service.pricingType == "fixed") "" else " / " + laundryUnit(service.pricingType),
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                if (svc != null && svc.pricingType != "fixed") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text(laundryQuantityLabel(svc.pricingType)) },
                        supportingText = { Text("ใช้ประมาณราคาเท่านั้น ร้านจะชั่ง/นับจริงก่อนสรุปยอด") },
                        enabled = !checkoutPending,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(10.dp))
                QgCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("สรุปราคา", fontWeight = FontWeight.ExtraBold)
                        LaundrySummary("ค่าบริการ", serviceAmount, serviceAmount == null)
                        if (cfg?.deliveryFeeMode == "round_trip") {
                            LaundrySummary("ค่ารับ-ส่งรวม", cfg.roundTripFee)
                        } else {
                            LaundrySummary("ค่ารับผ้า", cfg?.pickupFee ?: 0.0)
                            LaundrySummary("ค่าส่งคืน", cfg?.returnFee ?: 0.0)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        LaundrySummary("ยอดประมาณ", estimatedTotal, estimatedTotal == null, true)
                    }
                }

                Spacer(Modifier.height(10.dp))
                QgCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("จุดรับผ้า", fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (location == null) "ยังไม่ได้ระบุตำแหน่ง GPS"
                            else "%.5f, %.5f".format(location.latitude, location.longitude),
                            color = QgMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onGps, enabled = !checkoutPending, modifier = Modifier.fillMaxWidth()) { Text("ใช้ตำแหน่งปัจจุบัน") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = address,
                            onValueChange = onAddress,
                            label = { Text("ที่อยู่ / จุดสังเกต") },
                            enabled = !checkoutPending,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(500) },
                            label = { Text("หมายเหตุ เช่น โทรก่อนถึง / มีผ้าสีตก") },
                            enabled = !checkoutPending,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val hub = selectedHub
                        val service = selectedService
                        val loc = location?.copy(address = address.trim())
                        if (hub == null || service == null) {
                            message = "กรุณาเลือกบริการ"
                        } else if (loc == null || address.isBlank()) {
                            message = "กรุณาเลือกตำแหน่งและกรอกที่อยู่รับผ้า"
                        } else {
                            busy = true
                            scope.launch {
                                runCatching {
                                    checkoutRecovery.submit(
                                        create = {
                                            requestStore.pending(
                                                api.preparePlace(hub, service, loc, qty, note)
                                            )
                                        },
                                        send = { api.sendPrepared(auth, it.prepared()) },
                                        recover = { api.sendPrepared(auth, it.prepared()) },
                                        definitiveRejection = { e ->
                                            e is com.queuego.shared.QueueGoHttpException &&
                                                e.statusCode in 400..499 &&
                                                e.statusCode !in listOf(401, 403, 408, 429)
                                        }
                                    )
                                }.onSuccess {
                                    pendingCheckoutRecord = null
                                    message = "ส่งคำขอฝากซักแล้ว"
                                    onDone()
                                }.onFailure { e ->
                                    pendingCheckoutRecord = runCatching { checkoutJournal.read() }.getOrNull()
                                    message = if (pendingCheckoutRecord != null) {
                                        "กำลังตรวจผลคำขอฝากซักเดิม · ไม่ต้องส่งคำขอใหม่"
                                    } else {
                                        when {
                                            e.message?.contains("OUTSIDE_SERVICE_AREA") == true -> "ตำแหน่งรับผ้าอยู่นอกพื้นที่ให้บริการ"
                                            e.message?.contains("DELIVERY_DISTANCE_EXCEEDED") == true -> "ร้านซักและจุดรับผ้าอยู่ไกลเกินขอบเขต"
                                            else -> e.message ?: "ส่งคำขอฝากซักไม่สำเร็จ"
                                        }
                                    }
                                }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && !checkoutPending && selectedService != null && location != null && address.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text("ส่งคำขอฝากซัก", fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun LaundrySummary(label: String, value: Double?, pending: Boolean = false, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Normal)
        Text(
            if (pending || value == null) "รอสรุป" else "฿" + "%.0f".format(value),
            fontWeight = if (strong) FontWeight.Black else FontWeight.Bold
        )
    }
}

private fun laundryUnit(type: String): String = when (type) {
    "per_kg" -> "กก."
    "per_item" -> "ชิ้น"
    "per_set" -> "ชุด"
    else -> ""
}

private fun laundryQuantityLabel(type: String): String = when (type) {
    "per_kg" -> "น้ำหนักโดยประมาณ (กก.)"
    "per_item" -> "จำนวนชิ้นโดยประมาณ"
    "per_set" -> "จำนวนชุดโดยประมาณ"
    else -> "จำนวนโดยประมาณ"
}
