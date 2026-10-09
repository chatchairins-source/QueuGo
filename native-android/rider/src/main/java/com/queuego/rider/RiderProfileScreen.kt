package com.queuego.rider

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgAccountDeletionSection
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgStatusPill
import kotlinx.coroutines.launch

@Composable
fun RiderProfileScreen(
    auth: QueueGoAuth,
    snapshot: RiderSnapshot?,
    api: QueueGoApi,
    onSnapshot: (RiderSnapshot) -> Unit,
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

    fun refresh() {
        scope.launch {
            runCatching { api.riderSnapshot(auth) }
                .onSuccess(onSnapshot)
                .onFailure { message = it.message ?: "โหลดข้อมูล Rider ไม่สำเร็จ" }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("โปรไฟล์", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(auth.user.name, fontWeight = FontWeight.Black)
                    Text(auth.user.phone ?: profile?.phone ?: "-", color = QgMuted)
                    val vehicle = riderVehicleLabel(profile?.vehicleType)
                    val plate = profile?.vehiclePlate?.takeIf { it.isNotBlank() }
                    Text(if (plate == null) vehicle + " · QueueGo Rider" else vehicle + " · " + plate, color = QgMuted)
                }
                QgStatusPill(if (snapshot?.online == true) "ออนไลน์" else "ออฟไลน์", snapshot?.online == true)
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
                !busy && snapshot?.activeJob == null && snapshot?.laundry?.activeJob == null
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
            RiderProfileToggleRow(
                "รับงานฝากซัก", "งานรับ–ส่งผ้าจากร้านที่เข้าร่วม",
                snapshot?.laundry?.modeEnabled == true, !busy
            ) { enabled ->
                busy = true
                scope.launch {
                    runCatching { api.setLaundryMode(auth, enabled) }
                        .onSuccess {
                            message = if (enabled) "เปิดรับงานฝากซักแล้ว" else "ปิดรับงานฝากซักแล้ว"
                            runCatching { api.riderSnapshot(auth) }.getOrNull()?.let(onSnapshot)
                        }
                        .onFailure { message = it.message ?: "เปลี่ยนโหมดฝากซักไม่สำเร็จ" }
                    busy = false
                }
            }
        }

        snapshot?.laundry?.invites.orEmpty().forEach { invite ->
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Text("คำเชิญงานฝากซัก", color = QgRed, fontWeight = FontWeight.Bold)
                    Text(invite.shopName, fontWeight = FontWeight.Black)
                    Text(invite.hubName, color = QgMuted)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { api.laundryInviteAction(auth, invite.inviteId, false) }
                                        .onSuccess { refresh() }
                                        .onFailure { message = it.message ?: "ตอบคำเชิญไม่สำเร็จ" }
                                    busy = false
                                }
                            },
                            enabled = !busy, modifier = Modifier.weight(1f)
                        ) { Text("ปฏิเสธ") }
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { api.laundryInviteAction(auth, invite.inviteId, true) }
                                        .onSuccess { refresh() }
                                        .onFailure { message = it.message ?: "ตอบคำเชิญไม่สำเร็จ" }
                                    busy = false
                                }
                            },
                            enabled = !busy, modifier = Modifier.weight(1f)
                        ) { Text("ยอมรับ") }
                    }
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
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                Spacer(Modifier.height(6.dp))
                Text("Android ยังไม่ได้อนุญาตการแจ้งเตือน", color = QgRed)
            }
        }

        Spacer(Modifier.height(12.dp))
        RiderProfileSection("ความเป็นส่วนตัวและบัญชี") {
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
        Text(title, color = QgMuted, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
