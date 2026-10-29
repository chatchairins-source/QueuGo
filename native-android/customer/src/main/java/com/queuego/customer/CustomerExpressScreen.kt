package com.queuego.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.queuego.shared.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

internal data class PendingExpress(val requestId: String, val quoteId: String) {
    fun body(): JSONObject = JSONObject().put("p_request_id", requestId).put("p_quote_id", quoteId)
}

internal data class CustomerExpressOrder(
    val id: String,
    val number: String,
    val status: String,
    val riderId: String?,
    val pickup: String,
    val destination: String,
    val fare: Double,
    val distanceKm: Double,
    val durationMinutes: Double,
    val itemType: String,
    val weightKg: Double,
    val itemSize: String,
    val vehicleType: String,
    val senderName: String,
    val receiverName: String,
    val riderName: String?,
    val riderLat: Double?,
    val riderLng: Double?
) {
    val terminal: Boolean get() = status in setOf("completed", "cancelled", "no_rider_available")
}

private fun parseCustomerExpress(o: JSONObject): CustomerExpressOrder {
    fun text(key: String): String? = o.optString(key).takeIf { it.isNotBlank() && it != "null" }
    fun point(key: String): Double? = if (o.isNull(key)) null else o.optDouble(key).takeIf { it.isFinite() }
    return CustomerExpressOrder(
        id = o.getString("id"),
        number = nativeOrderNumber(o.optString("order_number")),
        status = o.getString("status"),
        riderId = text("rider_id"),
        pickup = o.optString("pickup_address"),
        destination = o.optString("delivery_address"),
        fare = o.getDouble("fare"),
        distanceKm = o.getDouble("distance_km"),
        durationMinutes = o.getDouble("duration_minutes"),
        itemType = o.optString("item_type"),
        weightKg = o.getDouble("weight_kg"),
        itemSize = o.optString("item_size"),
        vehicleType = o.optString("vehicle_type"),
        senderName = o.optString("sender_name"),
        receiverName = o.optString("receiver_name"),
        riderName = text("rider_name"),
        riderLat = point("rider_latitude"),
        riderLng = point("rider_longitude")
    )
}

private class CustomerExpressApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun config(auth: NativeAuth): JSONObject =
        http.rpc("queuego_express_config", auth.session.accessToken) as? JSONObject
            ?: error("อ่านสถานะ Express ไม่ได้")

    suspend fun state(auth: NativeAuth, orderId: String? = null): List<CustomerExpressOrder> {
        val raw = http.rpc(
            "queuego_express_state",
            auth.session.accessToken,
            JSONObject().put("p_order_id", orderId ?: JSONObject.NULL)
        ) as? JSONObject ?: error("อ่านสถานะ Express ไม่ได้")
        val rows = raw.getJSONArray("express_orders")
        return (0 until rows.length()).map { parseCustomerExpress(rows.getJSONObject(it)) }
    }

    suspend fun quote(
        auth: NativeAuth,
        pickup: CustomerLocation,
        destination: CustomerLocation,
        senderName: String,
        senderPhone: String,
        receiverName: String,
        receiverPhone: String,
        itemType: String,
        weightKg: Double,
        itemSize: String,
        vehicleType: String,
        instructions: String
    ): JSONObject {
        fun point(location: CustomerLocation) = JSONObject()
            .put("lat", location.latitude)
            .put("lng", location.longitude)
            .put("address", location.address)
        return http.functionPost(
            "queuego-express-quote",
            auth.session.accessToken,
            JSONObject()
                .put("pickup", point(pickup))
                .put("destination", point(destination))
                .put("sender", JSONObject().put("name", senderName.trim()).put("phone", senderPhone.trim()))
                .put("receiver", JSONObject().put("name", receiverName.trim()).put("phone", receiverPhone.trim()))
                .put(
                    "item",
                    JSONObject()
                        .put("type", itemType.trim())
                        .put("weightKg", weightKg)
                        .put("size", itemSize)
                        .put("vehicleType", vehicleType)
                        .put("instructions", instructions.trim())
                )
        ) as? JSONObject ?: error("ยังประเมินราคา Express ไม่ได้")
    }

    suspend fun request(auth: NativeAuth, pending: PendingExpress): CustomerExpressOrder {
        val receipt = http.rpc("queuego_request_express", auth.session.accessToken, pending.body()) as? JSONObject
            ?: error("ยังยืนยันงาน Express ไม่ได้")
        check(receipt.optString("id") == pending.requestId) { "ยังยืนยันเลขงาน Express ไม่ได้" }
        return state(auth, pending.requestId).singleOrNull()
            ?: error("กำลังตรวจสถานะ Express กรุณาลองอีกครั้ง")
    }

    suspend fun cancel(auth: NativeAuth, id: String) {
        http.rpc("qg_customer_cancel_order", auth.session.accessToken, JSONObject().put("p_order_id", id))
    }
}

@Composable
internal fun CustomerExpressScreen(
    auth: NativeAuth,
    initialPickup: CustomerLocation?,
    cartStore: CustomerCartStore,
    onContact: (String) -> Unit,
    onBack: () -> Unit
) {
    val api = remember { CustomerExpressApi() }
    val customerApi = remember { CustomerApi() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val mutex = remember(auth.user.id) { Mutex() }
    val journal = remember(cartStore) { cartStore.expressJournal() }
    val recovery = remember(journal) { CustomerCheckoutRecovery<PendingExpress, CustomerExpressOrder>(journal) }

    var pickup by remember(auth.user.id) { mutableStateOf(initialPickup) }
    var destination by remember(auth.user.id) { mutableStateOf<CustomerLocation?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    var senderName by rememberSaveable(auth.user.id) { mutableStateOf(auth.user.name) }
    var senderPhone by rememberSaveable(auth.user.id) { mutableStateOf("") }
    var receiverName by rememberSaveable(auth.user.id) { mutableStateOf("") }
    var receiverPhone by rememberSaveable(auth.user.id) { mutableStateOf("") }
    var itemType by rememberSaveable(auth.user.id) { mutableStateOf("พัสดุทั่วไป") }
    var weightText by rememberSaveable(auth.user.id) { mutableStateOf("1") }
    var itemSize by rememberSaveable(auth.user.id) { mutableStateOf("medium") }
    var vehicleType by rememberSaveable(auth.user.id) { mutableStateOf("motorcycle") }
    var instructions by rememberSaveable(auth.user.id) { mutableStateOf("") }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var orders by remember(auth.user.id) { mutableStateOf(emptyList<CustomerExpressOrder>()) }
    var selected by rememberSaveable(auth.user.id) { mutableStateOf<String?>(null) }
    var pending by remember(auth.user.id) { mutableStateOf<PendingExpress?>(null) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val recent = api.state(auth)
        val retained = selected?.takeIf { id -> recent.none { it.id == id } }?.let { api.state(auth, it) }.orEmpty()
        val current = retained + recent
        orders = current.distinctBy { it.id }
        current.firstOrNull { !it.terminal }?.let { selected = it.id }
        val config = api.config(auth)
        enabled = config.optBoolean("enabled") && config.optBoolean("configured")
    }

    LaunchedEffect(auth.user.id, auth.session.accessToken, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    mutex.withLock {
                        pending = withContext(Dispatchers.IO) { journal.read() }
                        recovery.reconcile { api.state(auth, it.requestId).singleOrNull() }?.let { selected = it.id }
                        pending = withContext(Dispatchers.IO) { journal.read() }
                        refresh()
                        loaded = true
                    }
                    error = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message ?: "รอเชื่อมต่อเพื่ออ่านสถานะ Express"
                }
                delay(5_000)
            }
        }
    }

    val current = orders.firstOrNull { it.id == selected }
    val frozen = busy || pending != null || current?.terminal == false
    val weightKg = weightText.toDoubleOrNull()
    val formReady = pickup != null && destination != null &&
        senderName.trim().isNotEmpty() && senderPhone.trim().length >= 5 &&
        receiverName.trim().isNotEmpty() && receiverPhone.trim().length >= 5 &&
        itemType.trim().isNotEmpty() && weightKg != null && weightKg > 0

    val editing = picker
    if (editing != null && !frozen) {
        val original = if (editing == "pickup") pickup else destination
        CustomerLocationPickerScreen(
            location = original,
            address = original?.address.orEmpty(),
            busy = false,
            searchPlaces = { query -> customerApi.searchPlaces(query) },
            resolveAddress = { lat, lng -> customerApi.reverseGeocode(lat, lng) },
            onSave = {
                if (editing == "pickup") pickup = it else destination = it
                quote = null
                picker = null
            },
            onBack = { picker = null }
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack, enabled = !busy) { Text("กลับ") }
        Text("QueueGo Express", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("ส่งด่วน 1 จุดรับ · 1 จุดส่ง · ชำระเงินสด", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (current != null) {
            Text(current.number, fontWeight = FontWeight.Bold)
            Text(
                when (current.status) {
                    "searching_rider" -> "กำลังหาไรเดอร์"
                    "rider_assigned" -> "ไรเดอร์กำลังไปจุดรับ"
                    "picked_up" -> "รับพัสดุแล้ว"
                    "in_progress" -> "กำลังนำส่ง"
                    "completed" -> "ส่งสำเร็จแล้ว"
                    "cancelled" -> "ยกเลิกแล้ว"
                    else -> "ยังไม่มีไรเดอร์พร้อมรับงาน"
                }
            )
            Text("จุดรับ: " + current.pickup)
            Text("จุดส่ง: " + current.destination)
            Text(current.itemType + " · " + customerNumber(current.weightKg, 2) + " กก. · " + current.itemSize)
            Text("ค่าส่งเงินสด " + customerBaht(current.fare), fontWeight = FontWeight.Bold)
            Text("เส้นทาง " + customerNumber(current.distanceKm, 1) + " กม. · ประมาณ " + customerNumber(current.durationMinutes, 0) + " นาที")
            current.riderName?.let { Text("ไรเดอร์: " + it) }
            if (!current.terminal && current.riderId != null) {
                OutlinedButton(onClick = { onContact(current.id) }, enabled = !busy) { Text("ติดต่อไรเดอร์") }
            }
            if (!current.terminal && current.riderLat != null && current.riderLng != null) {
                QgTrackingMap(
                    points = listOf(QgMapPoint(current.riderLat, current.riderLng, 0xFFE6002D.toInt(), QgMapRole.RIDER)),
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
            if (current.status == "searching_rider" && current.riderId == null && pending == null) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                mutex.withLock { api.cancel(auth, current.id); refresh() }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "ยกเลิกไม่ได้ กรุณาตรวจสถานะ"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("ยกเลิกงาน Express") }
            }
            if (current.terminal && pending == null) {
                Button(onClick = { selected = null; quote = null }, enabled = !busy) { Text("ส่งพัสดุรายการใหม่") }
            }
        } else {
            if (loaded && !enabled) Text("บริการ Express ยังไม่เปิดให้ใช้ในขณะนี้")
            OutlinedButton(enabled = loaded && enabled && !frozen, onClick = { picker = "pickup" }) {
                Text("จุดรับ: " + (pickup?.address ?: "เลือกบนแผนที่"))
            }
            OutlinedButton(enabled = loaded && enabled && !frozen, onClick = { picker = "destination" }) {
                Text("จุดส่ง: " + (destination?.address ?: "เลือกบนแผนที่"))
            }
            OutlinedTextField(value = senderName, onValueChange = { senderName = it; quote = null }, enabled = !frozen, label = { Text("ชื่อผู้ส่ง") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = senderPhone, onValueChange = { senderPhone = it; quote = null }, enabled = !frozen, label = { Text("เบอร์ผู้ส่ง") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = receiverName, onValueChange = { receiverName = it; quote = null }, enabled = !frozen, label = { Text("ชื่อผู้รับ") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = receiverPhone, onValueChange = { receiverPhone = it; quote = null }, enabled = !frozen, label = { Text("เบอร์ผู้รับ") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = itemType, onValueChange = { itemType = it; quote = null }, enabled = !frozen, label = { Text("ประเภทสิ่งของ") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = weightText, onValueChange = { weightText = it; quote = null }, enabled = !frozen, label = { Text("น้ำหนักโดยประมาณ (กก.)") }, modifier = Modifier.fillMaxWidth())

            Text("ขนาดพัสดุ", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("small" to "เล็ก", "medium" to "กลาง", "large" to "ใหญ่").forEach { (value, label) ->
                    FilterChip(selected = itemSize == value, onClick = { itemSize = value; quote = null }, label = { Text(label) }, enabled = !frozen)
                }
            }
            Text("ประเภทรถ", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("motorcycle" to "มอเตอร์ไซค์", "car" to "รถยนต์", "saleng" to "ซาเล้ง").forEach { (value, label) ->
                    FilterChip(selected = vehicleType == value, onClick = { vehicleType = value; quote = null }, label = { Text(label) }, enabled = !frozen)
                }
            }
            OutlinedTextField(value = instructions, onValueChange = { instructions = it; quote = null }, enabled = !frozen, label = { Text("หมายเหตุถึงไรเดอร์ (ถ้ามี)") }, modifier = Modifier.fillMaxWidth())

            if (quote == null && pending == null) {
                Button(
                    enabled = loaded && enabled && !frozen && formReady,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                quote = api.quote(
                                    auth, requireNotNull(pickup), requireNotNull(destination),
                                    senderName, senderPhone, receiverName, receiverPhone, itemType,
                                    requireNotNull(weightKg), itemSize, vehicleType, instructions
                                )
                                error = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "ยังประเมินเส้นทาง Express ไม่ได้"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "กำลังตรวจเส้นทาง…" else "ดูค่าส่ง") }
            }
            quote?.let { price ->
                Text("ค่าส่งเงินสด " + customerBaht(price.getDouble("fare")), fontWeight = FontWeight.Bold)
                Text("เส้นทาง " + customerNumber(price.getDouble("distance_km"), 1) + " กม. · ประมาณ " + customerNumber(price.getDouble("duration_minutes"), 0) + " นาที")
            }
        }

        if (pending != null || current == null && quote != null) {
            Button(
                enabled = loaded && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            mutex.withLock {
                                val receipt = recovery.submit(
                                    create = { PendingExpress(UUID.randomUUID().toString(), requireNotNull(quote).getString("quote_id")) },
                                    send = { api.request(auth, it) },
                                    recover = { api.state(auth, it.requestId).singleOrNull() },
                                    definitiveRejection = { it is QueueGoHttpException && it.statusCode in setOf(400, 422) }
                                )
                                selected = receipt.id
                                refresh()
                                quote = null
                                error = null
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "ยังยืนยันผลไม่ได้ กรุณาตรวจอีกครั้ง"
                        } finally {
                            busy = false
                            pending = withContext(Dispatchers.IO) { journal.read() }
                        }
                    }
                }
            ) { Text(if (pending != null) "ตรวจผลคำขอเดิม / ลองอีกครั้ง" else "เรียกไรเดอร์ส่งด่วน") }
        }

        if (current == null && pending == null) {
            orders.filter { it.terminal }.take(5).forEach { old ->
                TextButton(onClick = { selected = old.id }) {
                    Text(old.number + " · " + customerBaht(old.fare))
                }
            }
        }
    }
}
