package com.queuego.rider

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed

internal data class RiderDiagnosticsSnapshot(
    val networkReady: Boolean,
    val locationPermission: Boolean,
    val locationServices: Boolean,
    val notifications: Boolean,
    val googleMaps: Boolean,
    val overlayReturn: Boolean,
    val microphone: Boolean
) {
    val readyForDelivery: Boolean
        get() = networkReady && locationPermission && locationServices && notifications && googleMaps

    val essentialPassedCount: Int
        get() = listOf(networkReady, locationPermission, locationServices, notifications, googleMaps).count { it }
}

internal fun readRiderDiagnostics(context: Context): RiderDiagnosticsSnapshot {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities)
    val networkReady = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    val locationPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val locationManager = context.getSystemService(LocationManager::class.java)
    val locationServices = runCatching {
        locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }.getOrDefault(false)

    val googleMaps = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps") != null
    val overlayReturn = Settings.canDrawOverlays(context)
    val microphone =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    return RiderDiagnosticsSnapshot(
        networkReady = networkReady,
        locationPermission = locationPermission,
        locationServices = locationServices,
        notifications = riderNotificationsReady(context),
        googleMaps = googleMaps,
        overlayReturn = overlayReturn,
        microphone = microphone
    )
}

@Composable
internal fun RiderDiagnosticsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val diagnostics = remember(refresh) { readRiderDiagnostics(context) }
    val lastExit = remember(refresh) { readRiderLastProcessExit(context) }
    val mapDiagnostics by RiderRuntimeDiagnosticsStore.mapDiagnostics.collectAsState()
    val networkDiagnostics by RiderRuntimeDiagnosticsStore.networkDiagnostics.collectAsState()
    val uiJankDiagnostics by RiderRuntimeDiagnosticsStore.uiJankDiagnostics.collectAsState()
    val mainThreadDiagnostics by RiderRuntimeDiagnosticsStore.mainThreadWatchdog.collectAsState()
    val locationIntegrityDiagnostics by RiderLocationIntegrityStore.diagnostics.collectAsState()
    val mapHealth = remember(mapDiagnostics) { mapDiagnostics?.let(::riderMapRuntimeHealth) }
    val networkHealth = remember(networkDiagnostics) { riderNetworkRuntimeHealth(networkDiagnostics) }
    val uiJankHealth = remember(uiJankDiagnostics) { riderUiJankRuntimeHealth(uiJankDiagnostics) }
    val mainThreadHealth = remember(mainThreadDiagnostics) {
        riderMainThreadWatchdogHealth(mainThreadDiagnostics)
    }
    val locationIntegrityHealth = remember(locationIntegrityDiagnostics) {
        riderLocationIntegrityHealth(locationIntegrityDiagnostics)
    }

    fun launch(intent: Intent) {
        runCatching { context.startActivity(intent) }
        refresh += 1
    }

    Column(
        modifier
            .heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ตรวจสอบระบบ Rider", fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("ตรวจจากสถานะจริงของโทรศัพท์เครื่องนี้", color = QgMuted, fontSize = 10.sp)
            }
            TextButton(onClick = onClose) { Text("ปิด", color = QgRed, fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (diagnostics.readyForDelivery) Color(0xFFEAF8F2) else Color(0xFFFFF3F3)
            ),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, if (diagnostics.readyForDelivery) Color(0xFFCBECDD) else Color(0xFFFFD5D8))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    if (diagnostics.readyForDelivery) "ระบบหลักพร้อมรับงาน" else "ยังมีจุดที่ควรแก้ก่อนรับงาน",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                Text(
                    "ผ่าน ${diagnostics.essentialPassedCount}/5 รายการหลัก",
                    color = QgMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        RiderDiagnosticRow("อินเทอร์เน็ต", "ต้องเชื่อมต่อและออกอินเทอร์เน็ตได้", diagnostics.networkReady) {
            launch(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
        RiderDiagnosticRow("สิทธิ์ตำแหน่ง", "ใช้ GPS สำหรับตำแหน่ง Rider และการนำทาง", diagnostics.locationPermission) {
            launch(appDetailsIntent(context))
        }
        RiderDiagnosticRow("บริการตำแหน่ง / GPS", "ต้องเปิด Location ของเครื่อง", diagnostics.locationServices) {
            launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        RiderDiagnosticRow("การแจ้งเตือน", "ใช้รับข้อเสนองานและสถานะสำคัญ", diagnostics.notifications) {
            launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
        RiderDiagnosticRow("Google Maps", "QueueGo ใช้แอป Google Maps สำหรับการนำทางภายนอก", diagnostics.googleMaps) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.maps")))
        }

        Spacer(Modifier.height(8.dp))
        Text("แนะนำเพื่อใช้งานลื่นขึ้น", color = QgMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        RiderDiagnosticRow("ปุ่มลอยกลับ QueueGo", "ช่วยกลับจาก Google Maps เข้างานเดิมได้เร็ว", diagnostics.overlayReturn, required = false) {
            launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        }
        RiderDiagnosticRow("ไมโครโฟน", "ใช้สำหรับโทรเสียงใน QueueGo", diagnostics.microphone, required = false) {
            launch(appDetailsIntent(context))
        }

        Spacer(Modifier.height(12.dp))
        Text("ประสิทธิภาพแอปและแผนที่", color = QgMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        RiderRuntimeInfoCard(
            title = "GPS integrity",
            headline = locationIntegrityHealth.headline,
            detail = locationIntegrityHealth.detail,
            healthy = locationIntegrityHealth.healthy
        )
        RiderRuntimeInfoCard(
            title = "Map session",
            headline = mapHealth?.headline ?: "ยังไม่มีข้อมูลแผนที่ใน session นี้",
            detail = mapHealth?.detail ?: "เปิดหน้า Home/แผนที่อย่างน้อยหนึ่งครั้งเพื่อดูสถานะ reuse",
            healthy = mapHealth?.healthy
        )
        RiderRuntimeInfoCard(
            title = "QueueGo API",
            headline = networkHealth.headline,
            detail = networkHealth.detail,
            healthy = networkHealth.healthy
        )
        RiderRuntimeInfoCard(
            title = "ความลื่นของ UI",
            headline = uiJankHealth.headline,
            detail = uiJankHealth.detail,
            healthy = uiJankHealth.healthy
        )
        RiderRuntimeInfoCard(
            title = "Main-thread watchdog",
            headline = mainThreadHealth.headline,
            detail = mainThreadHealth.detail,
            healthy = mainThreadHealth.healthy
        )
        RiderRuntimeInfoCard(
            title = "การปิดแอปครั้งก่อน",
            headline = lastExit?.label ?: "ไม่พบ crash/exit history ที่อ่านได้",
            detail = lastExit?.detail ?: "Android จะเก็บข้อมูลนี้เฉพาะเวอร์ชันที่รองรับ",
            healthy = lastExit?.problematic?.not()
        )

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { refresh += 1 },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("ตรวจอีกครั้ง", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RiderDiagnosticRow(
    title: String,
    detail: String,
    passed: Boolean,
    required: Boolean = true,
    onFix: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 7.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEEE6E8))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(28.dp).background(
                    if (passed) Color(0xFFE8F7F1) else Color(0xFFFFECEE),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(if (passed) "✓" else "!", color = if (passed) Color(0xFF12845E) else QgRed, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(detail, color = QgMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                if (!required) Text("รายการแนะนำ", color = Color(0xFF9A8C92), fontSize = 8.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (!passed) {
                TextButton(onClick = onFix) {
                    Text("ตั้งค่า", color = QgRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun RiderRuntimeInfoCard(
    title: String,
    headline: String,
    detail: String,
    healthy: Boolean?
) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 7.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEEE6E8))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(28.dp).background(
                    when (healthy) {
                        true -> Color(0xFFE8F7F1)
                        false -> Color(0xFFFFECEE)
                        null -> Color(0xFFF2F2F3)
                    },
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (healthy) { true -> "✓"; false -> "!"; null -> "i" },
                    color = when (healthy) { true -> Color(0xFF12845E); false -> QgRed; null -> QgMuted },
                    fontWeight = FontWeight.Black
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = QgMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Text(headline, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                Text(detail, color = QgMuted, fontSize = 8.5.sp, lineHeight = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
