package com.queuego.rider

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgAccountDeletionSection
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRedSoft
import com.queuego.shared.QgRed
import com.queuego.shared.QgStatusPill
import kotlinx.coroutines.launch

internal const val RIDER_PRIVACY_POLICY_URL = "https://chatchairins-source.github.io/QueuGo/docs/privacy.html"

@Composable
fun RiderProfileScreen(
    auth: QueueGoAuth,
    snapshot: RiderSnapshot?,
    history: List<RiderHistoryOrder>,
    api: QueueGoApi,
    onSnapshot: (RiderSnapshot) -> Unit,
    onNavigationSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = snapshot?.profile
    var editing by remember { mutableStateOf(false) }
    var name by remember(auth.user.id, auth.user.name) { mutableStateOf(auth.user.name) }
    var phone by remember(auth.user.id, auth.user.phone, profile?.phone) {
        mutableStateOf(auth.user.phone ?: profile?.phone.orEmpty())
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showSupport by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            runCatching { api.riderSnapshot(auth) }
                .onSuccess(onSnapshot)
                .onFailure { message = it.message ?: "โหลดข้อมูล Rider ไม่สำเร็จ" }
        }
    }

    if (showSupport) {
        RiderSupportScreen(
            auth = auth,
            activeJob = snapshot?.activeJob,
            history = history,
            onBack = { showSupport = false },
            modifier = modifier
        )
        return
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("โปรไฟล์", fontWeight = FontWeight.Black, fontSize = 27.sp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text("QueueGo", color = QgRed, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFFEEE6E8)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            val smallScreen = LocalConfiguration.current.screenWidthDp <= 360
            Row(Modifier.fillMaxWidth().padding(if (smallScreen) 10.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(if (smallScreen) 52.dp else 58.dp).background(QgRedSoft, RoundedCornerShape(if (smallScreen) 16.dp else 18.dp)), contentAlignment = Alignment.Center) {
                    Text(auth.user.name.take(1).uppercase(), color = QgRed, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text(auth.user.name, fontWeight = FontWeight.Black, fontSize = if (smallScreen) 17.sp else 19.sp)
                    Text(auth.user.phone ?: profile?.phone ?: "-", color = Color(0xFF8A8387), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                    val vehicle = riderVehicleLabel(profile?.vehicleType)
                    val plate = profile?.vehiclePlate?.takeIf { it.isNotBlank() }
                    Text(if (plate == null) vehicle + " · QueueGo Rider" else vehicle + " · " + plate,
                        color = Color(0xFF746A6E), fontSize = 8.5.sp,
                        modifier = Modifier.padding(top = 5.dp).background(Color(0xFFF7F3F4), RoundedCornerShape(99.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
                }
                val online = snapshot?.online == true
                Text(if (online) "ออนไลน์" else "ออฟไลน์", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (online) Color(0xFF15955A) else Color(0xFF85878C),
                    modifier = Modifier.background(if (online) Color(0xFFEEF8F2) else Color(0xFFF2F2F3), RoundedCornerShape(99.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
            }
        }

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, color = QgRed)
        }

        Spacer(Modifier.height(12.dp))
        RiderProfileSection("การรับงาน") {
            RiderProfileToggleRow(
                "สถานะรับงาน", "เปิดหรือปิดการรับงานจัดส่ง",
                snapshot?.online == true,
                !busy && snapshot?.activeJob == null
            ) { enabled ->
                busy = true
                scope.launch {
                    runCatching { api.setOnline(auth, enabled, null, null) }
                        .onSuccess {
                            message = if (enabled) "เปิดรับงานแล้ว" else "ปิดรับงานแล้ว"
                            runCatching { api.riderSnapshot(auth) }.getOrNull()?.let(onSnapshot)
                        }
                        .onFailure { message = it.message ?: "เปลี่ยนสถานะไม่สำเร็จ" }
                    busy = false
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        RiderProfileSection("บัญชีและช่วยเหลือ") {
            if (editing) {
                OutlinedTextField(name, { if (it.length <= 100) name = it }, Modifier.fillMaxWidth(), label = { Text("ชื่อที่แสดง") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10) }, Modifier.fillMaxWidth(), label = { Text("เบอร์โทรศัพท์") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    "ข้อมูลรถที่อนุมัติ: " + riderVehicleLabel(profile?.vehicleType) +
                        (profile?.vehiclePlate?.let { " · " + it } ?: ""),
                    color = QgMuted
                )
                Text("แก้เฉพาะชื่อและเบอร์โทร ไม่เปลี่ยนข้อมูลรถหรือสถานะอนุมัติ", color = QgMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = false }, modifier = Modifier.weight(1f)) { Text("ยกเลิก") }
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching { api.updateAccount(auth, name, phone) }
                                    .onSuccess { message = "บันทึกการตั้งค่าแล้ว"; editing = false; refresh() }
                                    .onFailure { message = it.message ?: "บันทึกการตั้งค่าไม่สำเร็จ" }
                                busy = false
                            }
                        },
                        enabled = !busy, modifier = Modifier.weight(1f)
                    ) { Text("บันทึก") }
                }
            } else {
                OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("ตั้งค่าบัญชี") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showSupport = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("แจ้งปัญหา / ติดตามเรื่อง")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        RiderProfileSection("การแจ้งเตือน") {
            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 26) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("การแจ้งเตือนเบื้องหลัง") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    busy = true
                    message = "ระบบจะส่งแจ้งเตือนทดสอบหลังประมาณ 7 วินาที"
                    scope.launch {
                        runCatching { api.testNativePush(auth) }
                            .onFailure { message = it.message ?: "ทดสอบการแจ้งเตือนไม่สำเร็จ" }
                        busy = false
                    }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth()
            ) { Text("ทดสอบการแจ้งเตือน") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onNavigationSettings, modifier = Modifier.fillMaxWidth()) {
                Text("สิทธิ์กลับจากแอปนำทาง")
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                Spacer(Modifier.height(6.dp))
                Text("Android ยังไม่ได้อนุญาตการแจ้งเตือน", color = QgRed)
            }
        }

        Spacer(Modifier.height(12.dp))
        RiderProfileSection("ความเป็นส่วนตัวและบัญชี") {
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RIDER_PRIVACY_POLICY_URL))
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                    runCatching { context.startActivity(intent) }
                        .onFailure { message = "ไม่สามารถเปิดนโยบายความเป็นส่วนตัวได้" }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("นโยบายความเป็นส่วนตัว") }
            Spacer(Modifier.height(8.dp))
            QgAccountDeletionSection(accessToken = auth.session.accessToken, onDeleted = onLogout)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("ออกจากระบบ") }
        }
        Spacer(Modifier.height(86.dp))
    }
}

@Composable
private fun RiderProfileSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, color = Color(0xFF898287), fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.height(5.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFEEE6E8)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(10.dp)) { content() }
        }
    }
}

@Composable
private fun RiderProfileToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, color = QgMuted, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

private fun riderVehicleLabel(type: String?): String = when (type) {
    "motorcycle" -> "รถจักรยานยนต์"
    "car" -> "รถยนต์"
    "bicycle" -> "จักรยาน"
    "saleng" -> "รถซาเล้ง"
    null, "" -> "รถที่อนุมัติ"
    else -> type
}
