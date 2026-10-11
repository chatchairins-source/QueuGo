package com.queuego.customer

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgIcon
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@Composable
fun LaundryNativeScreen(
    auth: NativeAuth,
    location: CustomerLocation?,
    address: String,
    onChooseAddress: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val api = remember { CustomerLaundryApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pendingPrefs = remember(auth.user.id) {
        context.getSharedPreferences(
            "qg_laundry_pending_" + auth.user.id,
            Context.MODE_PRIVATE
        )
    }

    var catalog by remember { mutableStateOf<LaundryCatalog?>(null) }
    var selectedHub by remember { mutableStateOf<LaundryHub?>(null) }
    var services by remember { mutableStateOf<List<LaundryService>>(emptyList()) }
    var selectedService by remember { mutableStateOf<LaundryService?>(null) }
    var quantity by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var serviceLoading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(auth.user.id) {
        loading = true
        runCatching { api.catalog(auth) }
            .onSuccess {
                catalog = it
                message = null
            }
            .onFailure { message = it.message ?: "โหลดบริการฝากซักไม่สำเร็จ" }
        loading = false
    }

    fun chooseHub(hub: LaundryHub) {
        selectedHub = hub
        services = emptyList()
        selectedService = null
        quantity = ""
        serviceLoading = true
        message = null
        scope.launch {
            runCatching { api.services(auth, hub.id) }
                .onSuccess {
                    services = it
                    selectedService = it.firstOrNull()
                }
                .onFailure { message = it.message ?: "โหลดบริการของร้านไม่สำเร็จ" }
            serviceLoading = false
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

    fun pendingRequestId(hub: LaundryHub, service: LaundryService, qtyValue: Double?): String {
        val qtyKey = qtyValue?.toString().orEmpty()
        val savedId = pendingPrefs.getString("request_id", null)
        val matches = pendingPrefs.getString("hub_id", null) == hub.id &&
            pendingPrefs.getString("service_id", null) == service.id &&
            pendingPrefs.getString("qty", "") == qtyKey &&
            savedId != null &&
            runCatching { UUID.fromString(savedId) }.isSuccess
        if (matches) return savedId!!
        val fresh = UUID.randomUUID().toString()
        pendingPrefs.edit()
            .putString("request_id", fresh)
            .putString("hub_id", hub.id)
            .putString("service_id", service.id)
            .putString("qty", qtyKey)
            .apply()
        return fresh
    }

    fun clearPendingRequest() {
        pendingPrefs.edit().clear().apply()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F7F9))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(Color(0xFFF2F3F5), RoundedCornerShape(14.dp))
                    .clickable(enabled = !busy, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("back", Modifier.size(21.dp), Color(0xFF17181A))
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "ฝากซัก",
                color = Color(0xFF17181A),
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        HorizontalDivider(color = Color(0xFFEEEEEE))

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                if (selectedHub == null) {
                    LaundryHero()
                    Spacer(Modifier.height(18.dp))
                }

                if (!message.isNullOrBlank()) {
                    LaundryNotice(message.orEmpty())
                    Spacer(Modifier.height(12.dp))
                }

                when {
                    loading -> Box(
                        Modifier.fillMaxWidth().padding(vertical = 35.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = QgRed)
                    }

                    catalog?.featureEnabled == false -> LaundryNotice(
                        "ฝากซักอยู่ในโหมดปิดทดสอบ\nระบบจะเปิดใช้งานเมื่อผ่าน Regression และ Admin เปิดสวิตช์"
                    )

                    catalog?.hubs.isNullOrEmpty() -> Text(
                        "ยังไม่มีร้านซักเปิดให้บริการ",
                        color = QgMuted,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 35.dp)
                    )

                    selectedHub == null -> {
                        catalog!!.hubs.forEachIndexed { index, hub ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            LaundryShopCard(hub = hub, onClick = { chooseHub(hub) })
                        }
                    }

                    else -> LaundryOrderCard(
                        hub = selectedHub!!,
                        services = services,
                        selectedService = selectedService,
                        serviceLoading = serviceLoading,
                        quantity = quantity,
                        onQuantity = { quantity = it },
                        onSelectService = {
                            selectedService = it
                            quantity = ""
                        },
                        cfg = cfg,
                        serviceAmount = serviceAmount,
                        estimatedTotal = estimatedTotal,
                        address = address,
                        note = note,
                        onNote = { note = it.take(500) },
                        busy = busy,
                        onSubmit = {
                            val hub = selectedHub
                            val service = selectedService
                            val loc = location?.copy(address = address.trim())
                            when {
                                hub == null || service == null ->
                                    message = "กรุณาเลือกบริการ"
                                loc == null || address.isBlank() -> {
                                    message = "กรุณาปักพิกัดจัดส่งใน QueueGo ก่อน"
                                    onChooseAddress()
                                }
                                else -> {
                                    busy = true
                                    scope.launch {
                                        val requestId = pendingRequestId(hub, service, qty)
                                        runCatching {
                                            api.place(auth, requestId, hub, service, loc, qty, note)
                                        }
                                            .onSuccess {
                                                clearPendingRequest()
                                                message = "ส่งคำขอฝากซักแล้ว รอร้านกดรับคำขอ"
                                                onDone()
                                            }
                                            .onFailure { error ->
                                                val raw = error.message.orEmpty()
                                                if (
                                                    Regex(
                                                        "disabled|unavailable|invalid|active customer|Rider unavailable|OUTSIDE_SERVICE_AREA|DELIVERY_DISTANCE_EXCEEDED",
                                                        RegexOption.IGNORE_CASE
                                                    ).containsMatchIn(raw)
                                                ) {
                                                    clearPendingRequest()
                                                }
                                                message = when {
                                                    raw.contains("OUTSIDE_SERVICE_AREA") ->
                                                        "ตำแหน่งรับผ้าอยู่นอกพื้นที่ให้บริการ QueueGo Pilot"
                                                    raw.contains("DELIVERY_DISTANCE_EXCEEDED") ->
                                                        "ร้านซักและจุดรับผ้าอยู่ห่างเกินขอบเขตที่ QueueGo Pilot ให้บริการ"
                                                    else -> error.message ?: "ส่งคำขอฝากซักไม่สำเร็จ"
                                                }
                                            }
                                        busy = false
                                    }
                                }
                            }
                        }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun LaundryHero() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFE6002D), RoundedCornerShape(24.dp))
            .padding(22.dp)
    ) {
        Text(
            "ฝากซัก",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "เลือกร้านและบริการ ร้านยืนยันก่อน แล้ว Rider ไปรับผ้าถึงที่และส่งคืนเมื่อเสร็จ",
            color = Color.White.copy(alpha = .90f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun LaundryShopCard(
    hub: LaundryHub,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(54.dp)
                .background(Color(0xFFFFF0F2), RoundedCornerShape(17.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            LaundryWasherIcon()
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hub.name,
                color = Color(0xFF17181A),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Rider รับผ้าและส่งคืนถึงที่",
                color = Color(0xFF777777),
                fontSize = 14.sp
            )
        }
        Text("›", color = Color(0xFF999999), fontSize = 27.sp)
    }
}

@Composable
private fun LaundryWasherIcon() {
    Canvas(Modifier.fillMaxSize()) {
        val red = Color(0xFFE6002D)
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val left = size.width * .12f
        val top = size.height * .04f
        val width = size.width * .76f
        val height = size.height * .90f
        drawRoundRect(
            color = red,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(size.width * .09f),
            style = stroke
        )
        drawLine(
            color = red,
            start = Offset(left, size.height * .28f),
            end = Offset(left + width, size.height * .28f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = red,
            radius = size.width * .21f,
            center = Offset(size.width * .50f, size.height * .66f),
            style = stroke
        )
        drawCircle(red, size.width * .025f, Offset(size.width * .31f, size.height * .17f))
        drawCircle(red, size.width * .025f, Offset(size.width * .44f, size.height * .17f))
    }
}

@Composable
private fun LaundryOrderCard(
    hub: LaundryHub,
    services: List<LaundryService>,
    selectedService: LaundryService?,
    serviceLoading: Boolean,
    quantity: String,
    onQuantity: (String) -> Unit,
    onSelectService: (LaundryService) -> Unit,
    cfg: LaundrySettings?,
    serviceAmount: Double?,
    estimatedTotal: Double?,
    address: String,
    note: String,
    onNote: (String) -> Unit,
    busy: Boolean,
    onSubmit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(17.dp)
    ) {
        Text(
            hub.name,
            color = Color(0xFF17181A),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(10.dp))

        when {
            serviceLoading -> CircularProgressIndicator(color = QgRed)
            services.isEmpty() -> LaundryNotice("ร้านนี้ยังไม่มีบริการที่เปิดขาย")
            else -> services.forEach { service ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(15.dp))
                        .clickable(enabled = !busy) { onSelectService(service) }
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedService?.id == service.id,
                        onClick = { if (!busy) onSelectService(service) }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            service.name,
                            color = Color(0xFF17181A),
                            fontWeight = FontWeight.ExtraBold
                        )
                        val detail = buildString {
                            if (!service.description.isNullOrBlank()) append(service.description)
                            if (service.estimatedMinutes != null) {
                                if (isNotEmpty()) append(" · ")
                                append("ประมาณ ").append(service.estimatedMinutes).append(" นาที")
                            }
                        }
                        if (detail.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(detail, color = Color(0xFF777777), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        laundryFeeText(service.price) +
                            if (service.pricingType == "fixed") ""
                            else " / " + laundryUnit(service.pricingType),
                        color = Color(0xFF17181A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(9.dp))
            }
        }

        val service = selectedService
        if (service != null && service.pricingType != "fixed") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(16.dp))
                    .padding(15.dp)
            ) {
                Text(
                    laundryQuantityLabel(service.pricingType),
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { raw ->
                        val clean = raw.filter { it.isDigit() || it == '.' }
                        onQuantity(clean.take(12))
                    },
                    placeholder = { Text("ไม่แน่ใจสามารถเว้นว่างได้") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "ใช้เพื่อประมาณราคาเท่านั้น ร้านจะชั่ง/นับจำนวนจริงก่อนสรุปยอด",
                    color = Color(0xFF777777),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        if (cfg?.deliveryFeeMode == "round_trip") {
            LaundryFeeRow("ค่ารับ-ส่งรวม", laundryFeeText(cfg.roundTripFee))
        } else {
            LaundryFeeRow("ค่ารับผ้า", laundryFeeText(cfg?.pickupFee ?: 0.0))
            LaundryFeeRow("ค่าส่งคืน", laundryFeeText(cfg?.returnFee ?: 0.0))
        }
        LaundryFeeRow(
            if (serviceAmount == null) "ค่าบริการ (สรุปตามจำนวนจริง)" else "ค่าบริการ",
            serviceAmount?.let(::laundryFeeText) ?: "รอร้านสรุป"
        )
        LaundryFeeRow(
            "ยอดประมาณ",
            estimatedTotal?.let(::laundryFeeText) ?: "รอสรุปจำนวนจริง",
            total = true
        )

        Spacer(Modifier.height(14.dp))
        Text("จุดรับผ้า", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 14.dp)
                .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(16.dp))
                .padding(15.dp)
        ) {
            Text(
                address.ifBlank { "ยังไม่มีพิกัดจัดส่งที่บันทึกไว้" },
                color = Color(0xFF555555),
                fontSize = 14.sp
            )
        }

        Text("หมายเหตุ", fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = note,
            onValueChange = onNote,
            placeholder = { Text("เช่น โทรก่อนถึง / มีผ้าสีตก") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 3
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSubmit,
            enabled = !busy && selectedService != null && services.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6002D))
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(21.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "ส่งคำขอฝากซัก",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "ราคาและค่ารับส่งจะถูกบันทึกกับออเดอร์ทันที ร้านเปลี่ยนราคาในภายหลังจะไม่ย้อนมาเปลี่ยนออเดอร์นี้",
            color = Color(0xFF777777),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun LaundryFeeRow(
    label: String,
    value: String,
    total: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(
                width = if (total) 0.dp else 0.dp,
                color = Color.Transparent
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = if (total) 17.sp else 14.sp,
            fontWeight = if (total) FontWeight.ExtraBold else FontWeight.Normal
        )
        Text(
            value,
            fontSize = if (total) 17.sp else 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
    HorizontalDivider(color = Color(0xFFEEEEEE))
}

@Composable
private fun LaundryNotice(text: String) {
    Text(
        text,
        color = Color(0xFF744B00),
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF8E6), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFFFE1A6), RoundedCornerShape(16.dp))
            .padding(14.dp)
    )
}

private fun laundryFeeText(value: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("th", "TH")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return formatter.format(value) + " บาท"
}

private fun laundryUnit(type: String): String = when (type) {
    "per_kg" -> "กก."
    "per_item" -> "ชิ้น"
    "per_set" -> "ชุด"
    "fixed" -> "เหมาจ่าย"
    else -> ""
}

private fun laundryQuantityLabel(type: String): String = when (type) {
    "per_kg" -> "น้ำหนักโดยประมาณ (กก.)"
    "per_item" -> "จำนวนชิ้นโดยประมาณ"
    "per_set" -> "จำนวนชุดโดยประมาณ"
    else -> "จำนวนโดยประมาณ"
}
