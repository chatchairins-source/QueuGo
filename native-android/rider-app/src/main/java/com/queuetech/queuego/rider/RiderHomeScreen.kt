package com.queuetech.queuego.rider

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.delay

@Composable
fun RiderHomeScreen(
    viewModel: RiderViewModel,
    onLogout: () -> Unit,
    onNavigate: (Double, Double, String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.setOnline(true)
        } else {
            viewModel.setError("ต้องอนุญาตตำแหน่งแบบแม่นยำเพื่อเปิดรับงาน")
        }
    }

    fun requestGoOnline() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.setOnline(true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    val profile = state.profile
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "QueueGo Rider",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if (profile?.online == true) "ออนไลน์" else "ออฟไลน์",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile?.online == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Switch(
                    checked = profile?.online == true,
                    enabled = profile?.status == "active" &&
                        !state.actionBusy &&
                        !(profile?.online == true && state.activeJob != null),
                    onCheckedChange = { enabled ->
                        if (enabled) requestGoOnline() else viewModel.setOnline(false)
                    },
                )
                TextButton(onClick = onLogout) {
                    Text("ออก")
                }
            }

            state.message?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            state.error?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            when {
                state.loading -> {
                    Spacer(Modifier.height(40.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                profile == null -> {
                    StatusCard(
                        title = "ยังโหลดโปรไฟล์ไรเดอร์ไม่ได้",
                        body = "ตรวจอินเทอร์เน็ตแล้วลองใหม่",
                    )
                    Button(
                        onClick = viewModel::refreshNow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("ลองใหม่")
                    }
                }

                profile.status != "active" -> {
                    StatusCard(
                        title = "บัญชีอยู่ระหว่างตรวจสอบ",
                        body = "เปิดรับงานได้หลัง Admin อนุมัติบัญชีไรเดอร์แล้ว",
                    )
                }

                state.activeJob != null -> {
                    ActiveJobCard(
                        job = state.activeJob!!,
                        onNavigate = onNavigate,
                    )
                }

                state.offer != null -> {
                    OfferCard(
                        offer = state.offer!!,
                        busy = state.actionBusy,
                        onAccept = viewModel::acceptOffer,
                        onDecline = viewModel::declineOffer,
                        onExpired = viewModel::offerExpired,
                    )
                }

                profile.online -> {
                    StatusCard(
                        title = "พร้อมรับงานแล้ว",
                        body = "ระบบกำลังค้นหางานที่ Server เลือกให้คุณ",
                    )
                    OutlinedButton(
                        onClick = { viewModel.setOnline(false) },
                        enabled = !state.actionBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("ปิดรับงาน")
                    }
                }

                else -> {
                    StatusCard(
                        title = "พร้อมรับงานเมื่อคุณพร้อม",
                        body = "เปิดรับงานแล้ว QueueGo จะใช้ตำแหน่งปัจจุบันเพื่อค้นหางานใกล้คุณ",
                    )
                    Button(
                        onClick = ::requestGoOnline,
                        enabled = !state.actionBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.actionBusy) "กำลังเปิดรับงาน..." else "เปิดรับงาน")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OfferCard(
    offer: RiderOffer,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onExpired: () -> Unit,
) {
    var now by remember(offer.orderId) { mutableStateOf(System.currentTimeMillis()) }
    val leftSeconds = ceil(
        ((offer.expiresAtMillis - now).coerceAtLeast(0L)) / 1000.0,
    ).toInt()

    LaunchedEffect(offer.orderId, offer.expiresAtMillis) {
        while (now < offer.expiresAtMillis) {
            delay(250L)
            now = System.currentTimeMillis()
        }
        onExpired()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("มีงานใหม่", fontWeight = FontWeight.Black)
                    Text(
                        offer.orderNumber,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "$leftSeconds วิ",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                )
            }

            HorizontalDivider()
            LabelValue("รับสินค้าที่", offer.shopName)
            LabelValue(
                "ส่งที่",
                offer.deliveryAddress.ifBlank { "ตำแหน่งที่ลูกค้าระบุ" },
            )
            offer.distanceKm?.let {
                LabelValue("ระยะทาง", String.format(Locale.US, "%.1f กม.", it))
            }
            LabelValue(
                "ค่าจัดส่ง",
                "฿" + String.format(Locale.US, "%.0f", offer.deliveryFee),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !busy && leftSeconds > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("ปฏิเสธ")
                }
                Button(
                    onClick = onAccept,
                    enabled = !busy && leftSeconds > 0,
                    modifier = Modifier.weight(1.3f),
                ) {
                    Text(if (busy) "กำลังรับงาน..." else "รับงาน · $leftSeconds วิ")
                }
            }
        }
    }
}

@Composable
private fun ActiveJobCard(
    job: RiderActiveJob,
    onNavigate: (Double, Double, String) -> Unit,
) {
    val targetLat = if (job.isPickupPhase) job.pickupLatitude else job.deliveryLatitude
    val targetLng = if (job.isPickupPhase) job.pickupLongitude else job.deliveryLongitude
    val targetLabel = if (job.isPickupPhase) job.shopName else job.deliveryAddress
    val buttonLabel = if (job.isPickupPhase) "นำทางไปร้าน" else "นำทางไปหาลูกค้า"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("งานที่กำลังทำ", fontWeight = FontWeight.Black)
            Text(
                job.orderNumber,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider()
            LabelValue("สถานะ", riderStatusLabel(job.status))
            LabelValue("ร้าน", job.shopName)
            if (job.deliveryAddress.isNotBlank()) {
                LabelValue("ลูกค้า", job.deliveryAddress)
            }
            Button(
                onClick = {
                    if (targetLat != null && targetLng != null) {
                        onNavigate(targetLat, targetLng, targetLabel)
                    }
                },
                enabled = targetLat != null && targetLng != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(buttonLabel)
            }
            Text(
                "ขั้นถัดไปจะเพิ่ม ถึงร้าน → ตรวจรายการ+ถ่ายรูป → ไปหาลูกค้า → ส่ง+ถ่ายรูป",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun riderStatusLabel(status: String): String = when (status) {
    "rider_assigned", "assigned" -> "รับงานแล้ว · กำลังไปร้าน"
    "preparing" -> "ร้านกำลังเตรียมสินค้า"
    "ready" -> "สินค้าพร้อมรับ"
    "picked_up" -> "รับสินค้าแล้ว"
    "in_progress" -> "กำลังไปส่งลูกค้า"
    else -> status
}
