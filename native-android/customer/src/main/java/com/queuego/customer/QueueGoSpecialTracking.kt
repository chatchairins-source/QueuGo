package com.queuego.customer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgStatusPill
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.text.NumberFormat
import java.util.Locale
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.chrono.ThaiBuddhistChronology

@Composable
fun MarketTripDetailScreen(
    auth: NativeAuth,
    trip: MarketTripSummary,
    onBack: () -> Unit,
    onBrowse: () -> Unit,
    onTrackChild: (String) -> Unit,
    trackingBusy: Boolean
) {
    val api = remember { CustomerMarketApi() }
    val scope = rememberCoroutineScope()
    var shops by remember(auth.user.id, trip.id) { mutableStateOf<List<MarketShop>>(emptyList()) }
    var cancelling by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var children by remember { mutableStateOf<List<MarketTripChild>>(emptyList()) }
    var error by remember(auth.user.id, trip.id) { mutableStateOf<String?>(null) }
    var current by remember(auth.user.id, trip.id) { mutableStateOf(trip) }
    var missing by remember(auth.user.id, trip.id) { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(auth.user.id, auth.session.accessToken, trip.id, lifecycle, reload) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    val fresh = api.trip(auth, trip.id)
                    missing = fresh == null
                    if (fresh == null) { children = emptyList(); error = "ไม่พบ Market Trip ของบัญชีนี้" }
                    else {
                        current = fresh
                        children = api.tripChildren(auth, trip.id)
                        shops = api.loadShops(auth)
                        error = null
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) { error = e.message ?: "โหลดสถานะล่าสุดไม่สำเร็จ" }
                delay(3_000)
            }
        }
    }

    val locked = current.status.uppercase() in setOf("IN_PROGRESS", "COMPLETED", "CANCELLED")
    if (confirmCancel) AlertDialog(
        onDismissRequest = { if (!cancelling) confirmCancel = false },
        title = { Text("ยกเลิก Market Trip") }, text = { Text("ยกเลิกคำสั่งซื้อนี้?") },
        confirmButton = { TextButton(enabled = !cancelling, onClick = {
            if (cancelling) return@TextButton
            cancelling = true
            scope.launch {
                try { api.cancelTrip(auth, trip.id); confirmCancel = false; reload++ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) { error = e.message ?: "ยกเลิกไม่สำเร็จ"; confirmCancel = false; reload++ }
                finally { cancelling = false }
            }
        }) { Text(if (cancelling) "กำลังยกเลิก..." else "ยืนยันยกเลิก") } },
        dismissButton = { TextButton(enabled = !cancelling, onClick = { confirmCancel = false }) { Text("กลับ") } }
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
        OutlinedButton(onClick = { reload++ }) { Text("โหลดสถานะล่าสุด") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (missing) return@Column
        Spacer(Modifier.height(12.dp))
        Text("Market Trip", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        Text(current.shopCount.toString() + " ร้าน", color = QgMuted)
        Spacer(Modifier.height(8.dp))
        QgStatusPill(marketTripStatus(current.status), current.status.uppercase() != "CANCELLED")
        Text(if (locked) "ล็อกการเพิ่มร้านแล้ว" else "ยังเพิ่มร้านใหม่ได้จนกว่า Rider จะออกจากตลาด", color = QgMuted)
        if (!locked) {
            OutlinedButton(enabled = !cancelling && !trackingBusy, onClick = onBrowse) { Text("ซื้อเพิ่มจากตลาดเดิม") }
            OutlinedButton(enabled = !cancelling && !trackingBusy, onClick = { confirmCancel = true }) { Text("ยกเลิก Market Trip") }
        }
        children.firstOrNull { it.status.lowercase() !in setOf("completed", "cancelled") }?.let { child ->
            OutlinedButton(enabled = !cancelling && !trackingBusy, onClick = { onTrackChild(child.id) }) { Text(if (trackingBusy) "กำลังโหลดการติดตาม..." else "ติดตาม Rider / ติดต่อ") }
        }
        Spacer(Modifier.height(12.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("จุดจัดส่ง", fontWeight = FontWeight.ExtraBold)
                Text(current.deliveryAddress ?: "-", color = QgMuted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("ยอดรวม", Modifier.weight(1f))
                    Text(specialMoney(current.total), color = QgRed, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("ร้านในทริป", fontWeight = FontWeight.ExtraBold)
                if (children.isEmpty()) {
                    Text(error ?: "กำลังโหลดร้านในทริป...", color = QgMuted)
                } else children.forEach { child ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(shops.find { it.id == child.shopId }?.name ?: "ร้านค้า", fontWeight = FontWeight.Bold)
                            Text(child.number, fontWeight = FontWeight.Black)
                            Text(customerStatusLabel(child.status), color = QgMuted)
                        }
                        Text(specialMoney(child.total), fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("สรุปค่าใช้จ่าย", fontWeight = FontWeight.ExtraBold)
                SpecialSummary("ค่าสินค้า", current.subtotal?.let(::specialMoney) ?: "รอโหลด")
                SpecialSummary("ค่ารอบ/จัดส่ง", current.deliveryFee?.let(::specialMoney) ?: "รอโหลด")
                SpecialSummary("ค่ารับหลายร้าน", current.multiShopFee?.let(::specialMoney) ?: "รอโหลด")
                SpecialSummary("รวม", specialMoney(current.total))
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun LaundryOrderDetailScreen(
    auth: NativeAuth,
    order: LaundryOrderSummary,
    onBack: () -> Unit
) {
    val api = remember { CustomerLaundryApi() }
    var events by remember { mutableStateOf<List<LaundryEvent>>(emptyList()) }
    var current by remember(auth.user.id, order.id) { mutableStateOf(order) }
    var details by remember(auth.user.id, order.id) { mutableStateOf<LaundryOrderDetails?>(null) }
    var error by remember(auth.user.id, order.id) { mutableStateOf<String?>(null) }
    var missing by remember(auth.user.id, order.id) { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(auth.user.id, auth.session.accessToken, order.id, lifecycle, reload) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    val fresh = api.order(auth, order.id)
                    missing = fresh == null
                    if (fresh == null) { details = null; events = emptyList(); error = "ไม่พบงานฝากซักของบัญชีนี้" }
                    else {
                        current = fresh.summary
                        details = fresh
                        events = api.events(auth, order.id)
                        error = null
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) { error = e.message ?: "โหลดสถานะล่าสุดไม่สำเร็จ" }
                delay(4_000)
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
        OutlinedButton(onClick = { reload++ }) { Text("โหลดสถานะล่าสุด") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (missing) return@Column
        Spacer(Modifier.height(12.dp))
        Text(current.number, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        Text("ฝากซัก · " + current.serviceName, color = QgMuted)
        Spacer(Modifier.height(8.dp))
        QgStatusPill(laundryStatus(current.status), current.status != "cancelled")
        Spacer(Modifier.height(12.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("จุดรับ/ส่งผ้า", fontWeight = FontWeight.ExtraBold)
                Text(current.pickupAddress ?: "-", color = QgMuted)

            }
        }
        Spacer(Modifier.height(10.dp))
        details?.let { d ->
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("สรุปราคา", fontWeight = FontWeight.ExtraBold)
                    val unit = when (d.pricingType) { "per_kg" -> "กก."; "per_item" -> "ชิ้น"; "per_set" -> "ชุด"; else -> "" }
                    SpecialSummary("รูปแบบราคา", if (d.pricingType == "fixed") "เหมาจ่าย" else d.unitPrice?.let { specialMoney(it) + " / " + unit } ?: "-")
                    d.quantity?.let { SpecialSummary(if (d.actualQuantity) "จำนวนจริง" else "จำนวนประมาณ", specialNumber(it) + " " + unit) }
                    SpecialSummary("ค่าบริการ", d.serviceAmount?.let(::specialMoney) ?: "รอร้านชั่ง/นับจริง")
                    SpecialSummary("ค่ารับผ้า", specialMoney(d.pickupFee))
                    SpecialSummary("ค่าส่งคืน", specialMoney(d.returnFee))
                    SpecialSummary(if (current.finalTotal != null) "รวม" else "รวมโดยประมาณ", (current.finalTotal ?: current.estimatedTotal)?.let(::specialMoney) ?: "รอสรุป")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (current.status == "cancelled") Text("งานนี้ถูกยกเลิก", color = MaterialTheme.colorScheme.error)
        else QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("ขั้นตอนงาน", fontWeight = FontWeight.ExtraBold)
                laundryFlow.forEach { status ->
                    val active = laundryFlow.indexOf(status) <= laundryFlow.indexOf(current.status).coerceAtLeast(0)
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(if (active) "●" else "○", color = if (active) QgRed else QgMuted)
                        Text("  " + laundryStatus(status), color = if (active) MaterialTheme.colorScheme.onSurface else QgMuted)
                    }
                }
            }
        }
        if (events.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("ประวัติสถานะ", fontWeight = FontWeight.ExtraBold)
                    events.forEach { event ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Text(laundryStatus(event.status), fontWeight = FontWeight.Bold)
                            event.createdAt?.let { raw ->
                                val date = runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).format(
                                    DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss", Locale("th", "TH")).withChronology(ThaiBuddhistChronology.INSTANCE)) }.getOrDefault(raw)
                                Text(date, color = QgMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!event.note.isNullOrBlank()) Text(event.note!!, color = QgMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

private val laundryFlow = listOf(
    "pending", "accepted", "pickup_assigned", "picked_up", "at_hub",
    "washing", "ready_return", "return_assigned", "out_for_return", "completed"
)

fun laundryStatus(status: String): String = when (status.lowercase()) {
    "pending" -> "รอร้านรับ"
    "accepted" -> "ร้านรับแล้ว · รอเรียก Rider"
    "pickup_assigned" -> "Rider กำลังไปรับผ้า"
    "picked_up" -> "รับผ้าแล้ว"
    "at_hub" -> "ผ้าถึงร้านแล้ว"
    "washing" -> "กำลังซัก / ทำความสะอาด"
    "ready_return" -> "พร้อมส่งคืน"
    "return_assigned" -> "Rider กำลังไปรับผ้าสะอาด"
    "out_for_return" -> "กำลังส่งคืน"
    "completed" -> "ส่งคืนสำเร็จ"
    "cancelled" -> "ยกเลิก"
    else -> status
}

fun marketTripStatus(status: String): String = when (status.uppercase()) {
    "PENDING", "WAITING_SHOPS" -> "รอร้านยืนยัน"
    "READY_FOR_RIDER" -> "พร้อมหา Rider"
    "RIDER_ASSIGNED" -> "Rider รับงานแล้ว"
    "SHOPPING_IN_MARKET" -> "กำลังรับของในตลาด"
    "MARKET_COMPLETE" -> "รับของครบแล้ว"
    "IN_PROGRESS" -> "กำลังนำไปส่ง"
    "COMPLETED" -> "ส่งสำเร็จ"
    "CANCELLED" -> "ยกเลิก"
    else -> status
}

fun customerStatusLabel(status: String): String = when (status.lowercase()) {
    "pending" -> "รอร้านรับ"
    "accepted" -> "ร้านรับแล้ว"
    "searching_rider" -> "กำลังหา Rider"
    "rider_assigned", "assigned" -> "Rider รับงานแล้ว"
    "preparing" -> "กำลังเตรียม"
    "ready" -> "พร้อมรับสินค้า"
    "picked_up" -> "รับสินค้าแล้ว"
    "in_progress", "rider_to_customer" -> "กำลังจัดส่ง"
    "arrived" -> "Rider ถึงลูกค้าแล้ว"
    "completed" -> "ส่งสำเร็จ"
    "cancelled" -> "ยกเลิก"
    else -> status
}

private fun specialNumber(value: Double): String = NumberFormat.getNumberInstance(Locale("th", "TH")).apply { maximumFractionDigits = 2 }.format(value)
private fun specialMoney(value: Double) = specialNumber(value) + " บาท"
@Composable private fun SpecialSummary(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label, Modifier.weight(1f)); Text(value, fontWeight = FontWeight.Bold) }
}
