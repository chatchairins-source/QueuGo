package com.queuego.customer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.queuego.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

internal data class PendingRide(val requestId: String, val quoteId: String) {
    fun body() = JSONObject().put("p_request_id", requestId).put("p_quote_id", quoteId)
}
internal data class PassengerRide(val id: String, val number: String, val status: String, val riderId: String?,
    val pickup: String, val destination: String, val fare: Double, val riderName: String?,
    val minutes: Double, val km: Double, val riderLat: Double?, val riderLng: Double?,
    val arrivedPickupAt: String? = null, val arrivedDropoffAt: String? = null,
    val pickupLat: Double? = null, val pickupLng: Double? = null,
    val destinationLat: Double? = null, val destinationLng: Double? = null) {
    val terminal get() = status in listOf("completed", "cancelled", "no_rider_available")
}
internal fun parsePassengerRide(o: JSONObject): PassengerRide {
    fun text(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" }
    fun point(k: String) = if (o.isNull(k)) null else o.optDouble(k).takeIf { it.isFinite() }
    return PassengerRide(o.getString("id"), nativeOrderNumber(o.optString("order_number")), o.getString("status"), text("rider_id"),
        o.optString("pickup_address"), o.optString("delivery_address"), o.getDouble("fare"), text("rider_name"),
        o.getDouble("duration_minutes"), o.getDouble("distance_km"), point("rider_latitude"), point("rider_longitude"),
        text("arrived_pickup_at"), text("arrived_dropoff_at"),
        point("pickup_latitude"), point("pickup_longitude"), point("delivery_latitude"), point("delivery_longitude"))
}
internal fun passengerRideMapPoints(ride: PassengerRide): List<QgMapPoint> {
    if (ride.terminal) return emptyList()
    fun point(lat: Double?, lng: Double?, color: Int, role: QgMapRole, label: String? = null): QgMapPoint? =
        if (lat == null || lng == null) null else QgMapPoint(lat, lng, color, role, label).takeIf { it.valid }
    return listOfNotNull(
        point(ride.pickupLat, ride.pickupLng, 0xFF1677FF.toInt(), QgMapRole.CUSTOMER, "รับ"),
        point(ride.destinationLat, ride.destinationLng, 0xFFE6002D.toInt(), QgMapRole.CUSTOMER, "ส่ง"),
        point(ride.riderLat, ride.riderLng, 0xFFE6002D.toInt(), QgMapRole.RIDER)
    )
}

internal fun passengerRideStatus(ride: PassengerRide): String {
    if (ride.status == "rider_assigned" && !ride.arrivedPickupAt.isNullOrBlank()) return "คนขับถึงจุดรับแล้ว"
    if (ride.status == "in_progress" && !ride.arrivedDropoffAt.isNullOrBlank()) return "ถึงจุดหมายแล้ว"
    return when (ride.status) {
        "searching_rider" -> "กำลังหาคนขับ"
        "rider_assigned" -> "คนขับกำลังไปจุดรับ"
        "in_progress" -> "กำลังเดินทาง"
        "completed" -> "จบการเดินทางแล้ว"
        "cancelled" -> "ยกเลิกแล้ว"
        "no_rider_available" -> "ยังไม่มีคนขับพร้อมรับเที่ยว"
        else -> ride.status
    }
}

internal fun passengerRideSupplyWaitMillis(lastReadAt: Long, now: Long): Long =
    if (lastReadAt <= 0L) 0L else (15_000L - (now - lastReadAt).coerceAtLeast(0L)).coerceIn(0L, 15_000L)

internal data class PassengerRideSupplyCluster(val point: QgMapPoint, val count: Int)
internal data class PassengerRideSupply(
    val nearbyCount: Int = 0,
    val clusters: List<PassengerRideSupplyCluster> = emptyList()
)
internal fun parsePassengerRideSupply(raw: JSONObject): PassengerRideSupply {
    val clusters = raw.optJSONArray("clusters")
    val parsed = (0 until minOf(clusters?.length() ?: 0, 12)).mapNotNull { index ->
        val row = clusters?.optJSONObject(index) ?: return@mapNotNull null
        val count = row.optInt("count", 0)
        val point = QgMapPoint(row.optDouble("latitude"), row.optDouble("longitude"),
            0xFFE6002D.toInt(), QgMapRole.RIDER, count.toString())
        if (count < 2 || !row.optBoolean("approximate", false) || !point.valid) null
        else PassengerRideSupplyCluster(point, count)
    }
    return PassengerRideSupply(raw.optInt("nearby_count", 0).coerceAtLeast(0), parsed)
}

private class PassengerApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun state(auth: NativeAuth, id: String? = null): List<PassengerRide> {
        val body = JSONObject().put("p_order_id", id ?: JSONObject.NULL)
        val raw = http.rpc("queuego_ride_state", auth.session.accessToken, body) as? JSONObject ?: error("อ่านสถานะเที่ยวไม่ได้")
        val rows = raw.getJSONArray("rides")
        return (0 until rows.length()).map { parsePassengerRide(rows.getJSONObject(it)) }
    }
    suspend fun supply(auth: NativeAuth, pickup: CustomerLocation): PassengerRideSupply {
        val raw = http.rpc("queuego_customer_ride_supply", auth.session.accessToken,
            JSONObject().put("p_lat", pickup.latitude).put("p_lng", pickup.longitude)) as? JSONObject
        return raw?.let(::parsePassengerRideSupply) ?: PassengerRideSupply()
    }
    suspend fun config(auth: NativeAuth) = http.rpc("queuego_ride_config", auth.session.accessToken) as JSONObject
    suspend fun quote(auth: NativeAuth, pickup: CustomerLocation, destination: CustomerLocation): JSONObject {
        fun point(p: CustomerLocation) = JSONObject().put("lat",p.latitude).put("lng",p.longitude).put("address",p.address)
        return http.functionPost("queuego-ride-quote",auth.session.accessToken,JSONObject().put("pickup",point(pickup)).put("destination",point(destination))) as JSONObject
    }
    suspend fun request(auth: NativeAuth, pending: PendingRide): PassengerRide {
        val result = http.rpc("queuego_request_ride", auth.session.accessToken, pending.body()) as? JSONObject ?: error("ยังยืนยันเที่ยวไม่ได้")
        check(result.optString("id") == pending.requestId) { "ยังยืนยันเลขเที่ยวไม่ได้" }
        return state(auth,pending.requestId).singleOrNull() ?: error("กำลังตรวจสถานะเที่ยว กรุณาลองอีกครั้ง")
    }
    suspend fun cancel(auth: NativeAuth, id: String) { http.rpc("qg_customer_cancel_order",auth.session.accessToken,JSONObject().put("p_order_id",id)) }
}

@Composable
internal fun CustomerRideScreen(auth: NativeAuth, initialPickup: CustomerLocation?, cartStore: CustomerCartStore, initialOrderId: String? = null, onContact: (String) -> Unit, onBack: () -> Unit) {
    val api = remember { PassengerApi() }
    val customerApi = remember { CustomerApi() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val mutex = remember(auth.user.id) { Mutex() }
    val journal = remember(cartStore) { cartStore.rideJournal() }
    val recovery = remember(journal) { CustomerCheckoutRecovery<PendingRide,PassengerRide>(journal) }
    var pickup by remember(auth.user.id) { mutableStateOf(initialPickup) }
    var destination by remember(auth.user.id) { mutableStateOf<CustomerLocation?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var rides by remember(auth.user.id) { mutableStateOf(emptyList<PassengerRide>()) }
    var selected by rememberSaveable(auth.user.id) { mutableStateOf<String?>(initialOrderId) }
    var pending by remember(auth.user.id) { mutableStateOf<PendingRide?>(null) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }
    var backendConfirmed by remember(auth.user.id) { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun useCurrentPickup() {
        if (busy || pending != null || pickup != null) return
        busy = true
        try {
            // Use the same Android location providers as the existing QueueGo
            // address picker. Never fabricate coordinates or preselect a stop.
            val coordinates = withContext(Dispatchers.IO) {
                // Never treat a stale last-known coordinate as the current
                // passenger pickup. The live provider has a bounded timeout.
                customerRegistrationLocation(context).let {
                    it.latitude to it.longitude
                }
            }
            if (coordinates == null || !QgMapPoint(coordinates.first, coordinates.second, 0).valid) {
                error = "ยังอ่าน GPS ไม่ได้ เลือกจุดรับบนแผนที่แทนได้"
                return
            }
            val address = try {
                customerApi.reverseGeocode(coordinates.first, coordinates.second)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                null
            }
            pickup = CustomerLocation(coordinates.first, coordinates.second,
                address?.takeIf { it.isNotBlank() } ?: "ตำแหน่งปัจจุบัน")
            quote = null
            error = null
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            error = "อ่านตำแหน่งปัจจุบันไม่สำเร็จ เลือกจุดรับบนแผนที่แทนได้"
        } finally {
            busy = false
        }
    }
    val currentPickupPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) scope.launch { useCurrentPickup() }
        else error = "ไม่ได้รับสิทธิ์ GPS เลือกจุดรับบนแผนที่แทนได้"
    }

    suspend fun refresh() {
        val recent = api.state(auth)
        val all = if (selected != null && recent.none { it.id == selected }) api.state(auth, selected) + recent else recent
        rides = all
        all.firstOrNull { !it.terminal }?.let { selected = it.id }
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
                        backendConfirmed = true
                        loaded = true
                    }
                    error = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loaded = true
                    enabled = false
                    error = if (backendConfirmed) e.message ?: "รอเชื่อมต่อเพื่ออ่านสถานะเที่ยว" else null
                }
                delay(5_000)
            }
        }
    }

    val current = rides.firstOrNull { it.id == selected }
    val supplyPickup = pickup
    var supply by remember(auth.user.id, supplyPickup?.latitude, supplyPickup?.longitude) {
        mutableStateOf(PassengerRideSupply())
    }
    var supplyReadAt by remember(auth.user.id, supplyPickup?.latitude, supplyPickup?.longitude) {
        mutableLongStateOf(0L)
    }
    val canReadSupply = current == null && picker == null && pending == null && loaded && enabled && !busy
    LaunchedEffect(
        auth.user.id, auth.session.accessToken, lifecycle,
        supplyPickup?.latitude, supplyPickup?.longitude, canReadSupply
    ) {
        if (!canReadSupply || supplyPickup == null ||
            !QgMapPoint(supplyPickup.latitude, supplyPickup.longitude, 0).valid
        ) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val waitMillis = passengerRideSupplyWaitMillis(
                    supplyReadAt,
                    android.os.SystemClock.elapsedRealtime()
                )
                if (waitMillis > 0L) delay(waitMillis)
                currentCoroutineContext().ensureActive()
                supplyReadAt = android.os.SystemClock.elapsedRealtime()
                val next = try {
                    api.supply(auth, supplyPickup)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    PassengerRideSupply()
                }
                currentCoroutineContext().ensureActive()
                supply = next
            }
        }
    }

    val frozen = busy || pending != null || current?.terminal == false
    val editing = picker
    if (editing != null && !frozen) {
        val original = if (editing == "pickup") pickup else destination
        CustomerLocationPickerScreen(
            location = original,
            address = original?.address.orEmpty(),
            busy = false,
            searchPlaces = { query -> customerApi.searchPlaces(query) },
            resolveAddress = { lat, lng -> customerApi.reverseGeocode(lat, lng) },
            rideStop = editing,
            cameraReference = if (editing == "destination") pickup else null,
            onSave = {
                if (editing == "pickup") pickup = it else destination = it
                quote = null
                picker = null
            },
            onBack = { picker = null }
        )
        return
    }

    val mapPoints = if (current != null) {
        listOfNotNull(
            current.pickupLat?.let { lat -> current.pickupLng?.let { lng ->
                QgMapPoint(lat, lng, 0xFF1677FF.toInt(), QgMapRole.CUSTOMER, "รับ")
            }},
            current.destinationLat?.let { lat -> current.destinationLng?.let { lng ->
                QgMapPoint(lat, lng, 0xFFE6002D.toInt(), QgMapRole.CUSTOMER, "ส่ง")
            }},
            if (!current.terminal) current.riderLat?.let { lat -> current.riderLng?.let { lng ->
                QgMapPoint(lat, lng, 0xFFE6002D.toInt(), QgMapRole.RIDER)
            }} else null
        )
    } else {
        listOfNotNull(
            pickup?.let { QgMapPoint(it.latitude, it.longitude, 0xFF1677FF.toInt(), QgMapRole.CUSTOMER, "รับ") },
            destination?.let { QgMapPoint(it.latitude, it.longitude, 0xFFE6002D.toInt(), QgMapRole.CUSTOMER, "ส่ง") }
        ) + supply.clusters.map { it.point }
    }

    // The Customer Web Ride screen is the functional and visual blueprint:
    // header -> contained QueueGo Map -> nearby supply -> two location rows -> fare.
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F7F9))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White
            ) {
                QgIconButton(icon = "back", onClick = onBack)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("บริการเรียกรถ", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold, color = Color(0xFF17191D))
                Text("รับส่งผู้โดยสารด้วย QueueGo", color = QgMuted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (current != null) {
            Box(Modifier.fillMaxWidth().height(300.dp)
                .clip(RoundedCornerShape(20.dp)).background(Color(0xFFEEF1F4))) {
                QgTrackingMap(points = mapPoints, modifier = Modifier.fillMaxSize(), showWebMapControls = true)
            }
            if (!current.terminal && current.riderId != null) {
                Text(
                    if (current.riderLat != null && current.riderLng != null &&
                        QgMapPoint(current.riderLat, current.riderLng, 0).valid)
                        "ตำแหน่งคนขับล่าสุด · อัปเดตประมาณทุก 5 วินาที"
                    else "กำลังรอตำแหน่งล่าสุดจากคนขับ",
                    color = Color(0xFF6F7680),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE6E9ED))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (current == null) {
                    Box(Modifier.fillMaxWidth().height(245.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFEEF1F4))) {
                        QgTrackingMap(points = mapPoints, modifier = Modifier.fillMaxSize(), showWebMapControls = true)
                    }
                }

                if (current != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(current.number, fontWeight = FontWeight.Black)
                            Text(passengerRideStatus(current), color = QgRed, fontWeight = FontWeight.Bold)
                        }
                        Text(customerBaht(current.fare), color = QgRed, fontWeight = FontWeight.Black)
                    }
                    Text("จุดรับ · " + current.pickup, fontWeight = FontWeight.Bold)
                    Text("จุดหมาย · " + current.destination, fontWeight = FontWeight.Bold)
                    Text(
                        "ประมาณ ${customerNumber(current.km,1)} กม. · ${customerNumber(current.minutes,0)} นาที",
                        style = MaterialTheme.typography.bodySmall,
                        color = QgMuted
                    )
                    current.riderName?.let { Text("คนขับ · $it", fontWeight = FontWeight.Bold) }

                    if (!current.terminal && current.riderId != null) {
                        OutlinedButton(
                            onClick = { onContact(current.id) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ติดต่อคนขับ") }
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
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ยกเลิกการเรียกรถ") }
                    }
                    if (current.terminal && pending == null) {
                        Button(
                            onClick = { selected = null; quote = null },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("เรียกรถเที่ยวใหม่") }
                    }
                } else {
                    if (loaded && !enabled) {
                        Text("บริการเรียกรถยังไม่เปิดให้ใช้ในขณะนี้", color = QgMuted)
                    }
                    if (pickup != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(
                                if (supply.nearbyCount > 0) Color(0xFF18A875) else Color(0xFFA0A5AD),
                                CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (supply.nearbyCount > 0)
                                    "มี Rider พร้อมรับงานใกล้จุดรับ ${supply.nearbyCount} คน"
                                else "ยังไม่พบ Rider พร้อมรับงานในระยะ 8 กม.",
                                color = Color(0xFF59606A),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("ตำแหน่งบนแผนที่เป็นแบบกลุ่มโดยประมาณ",
                            color = QgMuted, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.End))
                    }
                    QueueGoRideLocationRow(
                        label = "จุดรับ", address = pickup?.address,
                        markerColor = Color(0xFF1677FF),
                        enabled = loaded && enabled && !frozen,
                        onClick = { picker = "pickup" }
                    )
                    QueueGoRideLocationRow(
                        label = "จุดหมาย", address = destination?.address,
                        markerColor = Color(0xFFE6002D),
                        enabled = loaded && enabled && !frozen,
                        onClick = { picker = "destination" }
                    )
                    if (pickup == null && pending == null && loaded && enabled) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFF7FAFF),
                                contentColor = Color(0xFF2E6DB4)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFCFD9E6)),
                            onClick = {
                                if (hasLocation(context)) {
                                    scope.launch { useCurrentPickup() }
                                } else {
                                    currentPickupPermission.launch(arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ))
                                }
                            }
                        ) {
                            Text("ใช้ตำแหน่งปัจจุบันเป็นจุดรับ",
                                fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    if (quote == null && pending == null) {
                        Button(
                            enabled = loaded && enabled && !frozen && pickup != null && destination != null,
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        quote = api.quote(auth, requireNotNull(pickup), requireNotNull(destination))
                                        error = null
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        error = e.message ?: "ยังประเมินเส้นทางไม่ได้"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = QgRed,
                                disabledContainerColor = Color(0xFFF5A0AB),
                                disabledContentColor = Color.White
                            )
                        ) { Text(if (busy) "กำลังตรวจเส้นทาง…" else "ดูค่าโดยสาร",
                            fontWeight = FontWeight.Bold) }
                    }
                    quote?.let { price ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFF7F8FA),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("ค่าโดยสารเงินสด",
                                        color = Color(0xFF17191D),
                                        modifier = Modifier.weight(1f))
                                    Text(customerBaht(price.getDouble("fare")),
                                        color = QgRed,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black)
                                }
                                Text(
                                    "ระยะทาง ${customerNumber(price.optDouble("distance_km"),1)} กม. · " +
                                        "เวลาวิ่งประมาณ ${kotlin.math.ceil(price.getDouble("duration_minutes")).toInt()} นาที",
                                    color = QgMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
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
                                            create = {
                                                PendingRide(
                                                    UUID.randomUUID().toString(),
                                                    requireNotNull(quote).getString("quote_id")
                                                )
                                            },
                                            send = { api.request(auth, it) },
                                            recover = { api.state(auth, it.requestId).singleOrNull() },
                                            definitiveRejection = {
                                                it is QueueGoHttpException && it.statusCode in listOf(400, 422)
                                            }
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
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (pending != null) "ตรวจผลคำขอเดิม / ลองอีกครั้ง" else "เรียกรถ")
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueGoRideLocationRow(
    label: String,
    address: String?,
    markerColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFE6E7EA))
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).background(markerColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).background(markerColor, CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = Color(0xFF777D86),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(address?.takeIf { it.isNotBlank() } ?: "เลือกบนแผนที่",
                    color = Color(0xFF17191D),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = Color(0xFF9AA0A8), fontSize = 22.sp)
        }
    }
}
