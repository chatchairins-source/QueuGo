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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgStatusPill
import kotlinx.coroutines.delay

@Composable
fun MarketTripDetailScreen(
    auth: NativeAuth,
    trip: MarketTripSummary,
    onBack: () -> Unit
) {
    val api = remember { CustomerMarketApi() }
    var children by remember { mutableStateOf<List<MarketTripChild>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(trip.id) {
        while (true) {
            runCatching { api.tripChildren(auth, trip.id) }
                .onSuccess { children = it; error = null }
                .onFailure { error = it.message }
            delay(3_000)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
        Spacer(Modifier.height(12.dp))
        Text("Market Trip", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        Text(trip.shopCount.toString() + " ร้าน", color = QgMuted)
        Spacer(Modifier.height(8.dp))
        QgStatusPill(marketTripStatus(trip.status), trip.status.uppercase() != "CANCELLED")
        Spacer(Modifier.height(12.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("จุดจัดส่ง", fontWeight = FontWeight.ExtraBold)
                Text(trip.deliveryAddress ?: "-", color = QgMuted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("ยอดรวม", Modifier.weight(1f))
                    Text("฿" + "%.0f".format(trip.total), color = QgRed, fontWeight = FontWeight.Black)
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
                            Text(child.number, fontWeight = FontWeight.Black)
                            Text(customerStatusLabel(child.status), color = QgMuted)
                        }
                        Text("฿" + "%.0f".format(child.total), fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
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
    var current by remember { mutableStateOf(order) }

    LaunchedEffect(order.id) {
        while (true) {
            runCatching { api.orders(auth) }.onSuccess { rows ->
                current = rows.find { it.id == order.id } ?: current
            }
            runCatching { api.events(auth, order.id) }.onSuccess { events = it }
            delay(4_000)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
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
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(if (current.finalTotal != null) "ยอดรวม" else "ยอดประมาณ", Modifier.weight(1f))
                    Text(
                        current.finalTotal?.let { "฿" + "%.0f".format(it) }
                            ?: current.estimatedTotal?.let { "฿" + "%.0f".format(it) }
                            ?: "รอสรุป",
                        color = QgRed,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        QgCard(Modifier.fillMaxWidth()) {
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
