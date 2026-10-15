package com.queuego.customer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    val minutes: Double, val km: Double, val riderLat: Double?, val riderLng: Double?) {
    val terminal get() = status in listOf("completed", "cancelled", "no_rider_available")
}
internal fun parsePassengerRide(o: JSONObject): PassengerRide {
    fun text(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" }
    fun point(k: String) = if (o.isNull(k)) null else o.optDouble(k).takeIf { it.isFinite() }
    return PassengerRide(o.getString("id"), nativeOrderNumber(o.optString("order_number")), o.getString("status"), text("rider_id"),
        o.optString("pickup_address"), o.optString("delivery_address"), o.getDouble("fare"), text("rider_name"),
        o.getDouble("duration_minutes"), o.getDouble("distance_km"), point("rider_latitude"), point("rider_longitude"))
}
private class PassengerApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun state(auth: NativeAuth, id: String? = null): List<PassengerRide> {
        val body = JSONObject().put("p_order_id", id ?: JSONObject.NULL)
        val raw = http.rpc("queuego_ride_state", auth.session.accessToken, body) as? JSONObject ?: error("อ่านสถานะเที่ยวไม่ได้")
        val rows = raw.getJSONArray("rides")
        return (0 until rows.length()).map { parsePassengerRide(rows.getJSONObject(it)) }
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
    var gpsPoint by remember { mutableStateOf<CustomerLocation?>(null) }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var rides by remember(auth.user.id) { mutableStateOf(emptyList<PassengerRide>()) }
    var selected by rememberSaveable(auth.user.id) { mutableStateOf<String?>(initialOrderId) }
    var pending by remember(auth.user.id) { mutableStateOf<PendingRide?>(null) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }
    var backendConfirmed by remember(auth.user.id) { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun refresh() {
        val recent = api.state(auth)
        val current = if (selected != null && recent.none { it.id == selected }) api.state(auth,selected) + recent else recent
        rides = current
        current.firstOrNull { !it.terminal }?.let { selected = it.id }
        val config = api.config(auth)
        enabled = config.optBoolean("enabled") && config.optBoolean("configured")
    }
    fun gps() {
        val position = lastKnownLocation(context)
        if (position != null) gpsPoint = CustomerLocation(position.first, position.second, "")
        else error = "ยังอ่านตำแหน่ง GPS ไม่ได้"
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) gps() else error = "เลือกจุดบนแผนที่ได้โดยไม่ต้องใช้ GPS"
    }
    LaunchedEffect(auth.user.id,auth.session.accessToken,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    mutex.withLock {
                        pending = withContext(Dispatchers.IO) { journal.read() }
                        recovery.reconcile { api.state(auth,it.requestId).singleOrNull() }?.let { selected = it.id }
                        pending = withContext(Dispatchers.IO) { journal.read() }
                        refresh(); backendConfirmed = true; loaded = true
                    }
                    error = null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    loaded = true
                    enabled = false
                    error = if (backendConfirmed) e.message ?: "รอเชื่อมต่อเพื่ออ่านสถานะเที่ยว" else null
                }
                delay(5000)
            }
        }
    }
    val current = rides.firstOrNull { it.id == selected }
    val frozen = busy || pending != null || current?.terminal == false
    val editing = picker
    if (editing != null && !frozen) {
        val original = if (editing == "pickup") pickup else destination
        CustomerLocationPickerScreen(location = gpsPoint ?: original, address = original?.address.orEmpty(), busy = false,
            onGps = { if (hasLocation(context)) gps() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)) },
            resolveAddress = { lat,lng -> customerApi.reverseGeocode(lat,lng) },
            onSave = { if (editing == "pickup") pickup = it else destination = it; quote = null; picker = null; gpsPoint = null },
            onBack = { picker = null; gpsPoint = null })
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("กลับ") }
        Text("รับส่งผู้โดยสาร",style = MaterialTheme.typography.headlineSmall,fontWeight = FontWeight.Bold)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loaded) Text("กำลังตรวจสถานะเที่ยวล่าสุด…")
        if (current != null) {
            Text(current.number,fontWeight = FontWeight.Bold)
            Text(when(current.status) { "searching_rider" -> "กำลังหาคนขับ"; "rider_assigned" -> "คนขับกำลังไปจุดรับ"; "in_progress" -> "กำลังเดินทาง"; "completed" -> "จบการเดินทางแล้ว"; "cancelled" -> "ยกเลิกแล้ว"; else -> "ยังไม่มีคนขับพร้อมรับเที่ยว" })
            Text("จุดรับ: " + current.pickup);Text("จุดหมาย: " + current.destination)
            Text("ค่าโดยสารเงินสด " + customerBaht(current.fare),fontWeight = FontWeight.Bold)
            current.riderName?.let { Text("คนขับ: " + it) }
            if (!current.terminal && current.riderId != null) OutlinedButton(onClick = { onContact(current.id) },enabled = !busy) { Text("ติดต่อคนขับ") }
            Text("เส้นทางที่ประเมิน ${customerNumber(current.km,1)} กม. · เวลาวิ่งประมาณ ${customerNumber(current.minutes,0)} นาที ไม่รวมเวลารอคนขับ")
            if (!current.terminal && current.riderLat != null && current.riderLng != null) {
                QgLongdoTrackingMap(points = listOf(QgMapPoint(current.riderLat,current.riderLng,0xFFE6002D.toInt(),QgMapRole.RIDER)),modifier = Modifier.fillMaxWidth().height(220.dp))
            }
            if (current.status == "searching_rider" && current.riderId == null && pending == null) {
                OutlinedButton(enabled = !busy,onClick = {
                    busy = true; scope.launch {
                        try { mutex.withLock { api.cancel(auth,current.id);refresh() } }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "ยกเลิกไม่ได้ กรุณาตรวจสถานะ" }
                        finally { busy = false }
                    }
                }) { Text("ยกเลิกการเรียกรถ") }
            }
            if (current.terminal && pending == null) Button(onClick = { selected = null;quote = null },enabled = !busy) { Text("เรียกรถเที่ยวใหม่") }
        } else {
            if (loaded && !enabled) Text("บริการเรียกรถยังไม่เปิดให้ใช้ในขณะนี้")
            OutlinedButton(enabled = loaded && enabled && !frozen,onClick = { picker = "pickup" }) { Text("จุดรับ: " + (pickup?.address ?: "เลือกบนแผนที่")) }
            OutlinedButton(enabled = loaded && enabled && !frozen,onClick = { picker = "destination" }) { Text("จุดหมาย: " + (destination?.address ?: "เลือกบนแผนที่")) }
            if (quote == null && pending == null) Button(enabled = loaded && enabled && !frozen && pickup != null && destination != null,onClick = {
                busy = true; scope.launch {
                    try { quote = api.quote(auth,requireNotNull(pickup),requireNotNull(destination));error = null }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "ยังประเมินเส้นทางไม่ได้" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "กำลังตรวจเส้นทาง…" else "ดูค่าโดยสาร") }
            quote?.let { price ->
                Text("ค่าโดยสารเงินสด " + customerBaht(price.getDouble("fare")),fontWeight = FontWeight.Bold)
                Text("เวลาวิ่งประมาณ ${customerNumber(price.getDouble("duration_minutes"),0)} นาที ไม่รวมเวลารอคนขับ")
            }
        }
        if (pending != null || current == null && quote != null) Button(enabled = loaded && !busy,onClick = {
            busy = true; scope.launch {
                try {
                    mutex.withLock {
                        val receipt = recovery.submit(
                            create = { PendingRide(UUID.randomUUID().toString(),requireNotNull(quote).getString("quote_id")) },
                            send = { api.request(auth,it) }, recover = { api.state(auth,it.requestId).singleOrNull() },
                            definitiveRejection = { it is QueueGoHttpException && it.statusCode in listOf(400,422) })
                        selected = receipt.id;refresh();quote = null;error = null
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "ยังยืนยันผลไม่ได้ กรุณาตรวจอีกครั้ง" }
                finally { busy = false;pending = withContext(Dispatchers.IO) { journal.read() } }
            }
        }) { Text(if (pending != null) "ตรวจผลคำขอเดิม / ลองอีกครั้ง" else "เรียกรถ") }
        if (current == null && pending == null) rides.filter { it.terminal }.take(5).forEach { old ->
            TextButton(onClick = { selected = old.id }) { Text(old.number + " · " + customerBaht(old.fare)) }
        }
    }
}
