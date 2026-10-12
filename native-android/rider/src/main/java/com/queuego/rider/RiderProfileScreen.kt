package com.queuego.rider

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.queuego.shared.QgAccountDeletionSection
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRedSoft
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    val displayName = profile?.riderName ?: auth.user.name
    val displayPhone = profile?.phone ?: auth.user.phone.orEmpty()
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deletionBusy by remember { mutableStateOf(false) }
    var name by remember(auth.user.id) { mutableStateOf(displayName) }
    var phone by remember(auth.user.id) { mutableStateOf(displayPhone) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showSupport by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        if (message != null) { delay(4_000); message = null }
    }

    val smallScreen = LocalConfiguration.current.screenWidthDp <= 360
    Box(modifier.fillMaxSize().background(Color(0xFFF8F7F8))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp).padding(top = 15.dp, bottom = 78.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.width(44.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("โปรไฟล์", fontWeight = FontWeight.Bold, fontSize = 26.sp)
                }
                Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
                    Text("QueueGo", color = QgRed, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFEEE6E8)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(if (smallScreen) 10.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Box(Modifier.size(if (smallScreen) 52.dp else 58.dp).background(QgRedSoft, RoundedCornerShape(if (smallScreen) 16.dp else 18.dp)), contentAlignment = Alignment.Center) {
                        Text(displayName.take(1).uppercase(), color = QgRed, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(displayName, fontWeight = FontWeight.Bold, fontSize = if (smallScreen) 17.sp else 19.sp)
                        Text(displayPhone.ifBlank { "-" }, color = QgMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                        val vehicle = riderVehicleLabel(profile?.vehicleType)
                        val plate = profile?.vehiclePlate?.takeIf { it.isNotBlank() }
                        Text(vehicle + (plate?.let { " · $it" } ?: " · QueueGo Rider"), color = Color(0xFF746A6E), fontSize = 8.5.sp,
                            modifier = Modifier.padding(top = 5.dp).background(Color(0xFFF7F3F4), RoundedCornerShape(99.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                    RiderProfilePill(snapshot?.online == true)
                }
            }
            Spacer(Modifier.height(10.dp))
            RiderProfileSection("การรับงาน") {
                RiderSettingRow("สถานะรับงาน", "เปิดหรือปิดการรับงานจัดส่ง", R.drawable.qg_rider_flow_loc,
                    enabled = !busy && snapshot != null && snapshot.activeJob == null, green = true,
                    trailing = { RiderProfilePill(snapshot?.online == true, offlineLabel = "ปิด") }) {
                    val enabled = snapshot?.online != true
                    busy = true
                    scope.launch {
                        try {
                            api.setOnline(auth, enabled, null, null)
                            onSnapshot(api.riderSnapshot(auth))
                            message = if (enabled) "เปิดรับงานแล้ว" else "ปิดรับงานแล้ว"
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message ?: "เปลี่ยนสถานะไม่สำเร็จ" } finally { busy = false }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            RiderProfileSection("บัญชีและช่วยเหลือ") {
                RiderSettingRow("ตั้งค่าบัญชี", "ชื่อ เบอร์โทร และข้อมูลบัญชี", R.drawable.qg_rider_nav_user, enabled = !busy) {
                    name = displayName; phone = displayPhone; editing = true
                }
                RiderSettingDivider()
                RiderSettingRow("แจ้งปัญหา / ติดตามเรื่อง", "ติดต่อฝ่ายดูแล QueueGo", R.drawable.qg_rider_nav_chat) { showSupport = true }
            }
            Spacer(Modifier.height(10.dp))
            RiderProfileSection("การแจ้งเตือน") {
                RiderSettingRow("การแจ้งเตือนเบื้องหลัง", "รับงานและสถานะสำคัญแม้ไม่ได้เปิดหน้านี้", R.drawable.qg_rider_nav_chat) {
                    val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                    runCatching { context.startActivity(intent) }.onFailure { message = "เปิดการตั้งค่าการแจ้งเตือนไม่สำเร็จ" }
                }
                RiderSettingDivider()
                RiderSettingRow("ทดสอบการแจ้งเตือน", "ส่งแจ้งเตือนทดสอบหลัง 7 วินาที", R.drawable.qg_rider_nav_chat, enabled = !busy) {
                    busy = true
                    scope.launch {
                        try { api.testNativePush(auth); message = "ระบบจะส่งแจ้งเตือนทดสอบหลังประมาณ 7 วินาที" } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message ?: "ทดสอบการแจ้งเตือนไม่สำเร็จ" } finally { busy = false }
                    }
                }
                RiderSettingDivider()
                // Existing Android-only permission control; never an extra Home card.
                RiderSettingRow("สิทธิ์กลับจากแอปนำทาง", "การแจ้งเตือนและสิทธิ์แสดงทับแอปอื่น", R.drawable.qg_rider_flow_loc, onClick = onNavigationSettings)
            }
            Spacer(Modifier.height(10.dp))
            RiderProfileSection("ความเป็นส่วนตัวและบัญชี") {
                RiderSettingRow("นโยบายความเป็นส่วนตัว", "การใช้ข้อมูลและการลบบัญชี", R.drawable.qg_rider_nav_chat) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RIDER_PRIVACY_POLICY_URL)).addCategory(Intent.CATEGORY_BROWSABLE)
                    runCatching { context.startActivity(intent) }.onFailure { message = "ไม่สามารถเปิดนโยบายความเป็นส่วนตัวได้" }
                }
                RiderSettingDivider()
                RiderSettingRow("ลบบัญชีถาวร", "ลบ Auth และข้อมูลส่วนบุคคล", R.drawable.qg_rider_flow_back, danger = true) { deleting = true }
                RiderSettingDivider()
                RiderSettingRow("ออกจากระบบ", "ออกจากบัญชีในอุปกรณ์นี้", R.drawable.qg_rider_flow_back, onClick = onLogout)
            }
        }
        message?.let {
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 82.dp).widthIn(max = 430.dp)
                .background(Color(0xFF19171A), RoundedCornerShape(14.dp)).padding(horizontal = 15.dp, vertical = 11.dp)) {
                Text(it, color = Color.White, fontSize = 12.sp)
            }
        }
    }
    if (showSupport) Dialog(onDismissRequest = { showSupport = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(14.dp).widthIn(max = 620.dp).fillMaxWidth().imePadding(), shape = RoundedCornerShape(24.dp), color = Color.White) {
            RiderSupportScreen(auth = auth, activeJob = snapshot?.activeJob, history = history,
                onBack = { showSupport = false }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (editing) Dialog(onDismissRequest = { if (!busy) editing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(14.dp).widthIn(max = 520.dp).fillMaxWidth().imePadding(), shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .9f).dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ตั้งค่าบัญชี", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("ข้อมูลที่ Rider ใช้ติดต่อและแสดงในระบบ", fontSize = 10.sp, color = QgMuted)
                    }
                    IconButton(onClick = { editing = false }, enabled = !busy) { Text("✕", color = QgRed) }
                }
                RiderAccountField("ชื่อที่แสดง", name, { if (it.length <= 100) name = it }, !busy, false)
                RiderAccountField("เบอร์โทรศัพท์", phone, { phone = it.filter(Char::isDigit).take(10) }, !busy, true)
                Column(Modifier.fillMaxWidth().background(Color(0xFFFAF7F8), RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text("ข้อมูลรถที่อนุมัติ", color = QgMuted, fontSize = 10.sp)
                    Text(riderVehicleLabel(profile?.vehicleType) + (profile?.vehiclePlate?.let { " · $it" } ?: ""), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("หากต้องการเปลี่ยนรถหรือทะเบียน ให้แจ้งฝ่ายดูแลเพื่อป้องกันข้อมูลอนุมัติไม่ตรงกับรถที่ใช้งานจริง", fontSize = 10.sp, color = QgMuted)
                }
                Text("หน้านี้แก้เฉพาะชื่อที่แสดงและเบอร์โทร ไม่เปลี่ยนสถานะอนุมัติ เอกสารยืนยันตัวตน ข้อมูลรถที่ผ่านการตรวจ หรือบัญชีการเงิน",
                    fontSize = 10.sp, color = QgMuted, modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            api.updateAccount(auth, name, phone)
                            onSnapshot(api.riderSnapshot(auth))
                            editing = false
                            message = "บันทึกการตั้งค่าแล้ว"
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message ?: "บันทึกการตั้งค่าไม่สำเร็จ" } finally { busy = false }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455))) {
                    Text(if (busy) "กำลังบันทึก…" else "บันทึกการตั้งค่า", fontWeight = FontWeight.Black)
                }
                message?.let { Text(it, color = QgRed, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
    if (deleting) Dialog(onDismissRequest = { if (!deletionBusy) deleting = false }) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(Modifier.padding(16.dp)) {
                // Reuse existing deletion confirmations/API and cleanup; opening this never deletes.
                QgAccountDeletionSection(accessToken = auth.session.accessToken, onDeleted = onLogout, onBusyChange = { deletionBusy = it })
                TextButton(onClick = { deleting = false }, enabled = !deletionBusy, modifier = Modifier.align(Alignment.End)) { Text("ปิด") }
            }
        }
    }
}

@Composable
private fun RiderProfileSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, color = Color(0xFF898287), fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.height(5.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFEEE6E8)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column { content() }
        }
    }
}

@Composable
private fun RiderSettingRow(title: String, detail: String, icon: Int, enabled: Boolean = true, green: Boolean = false,
    danger: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val color = if (danger) QgRed else if (green) Color(0xFF14A35C) else Color(0xFF777177)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(34.dp).background(if (danger) QgRedSoft else if (green) Color(0xFFEDF9F2) else Color(0xFFF7F4F5), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) QgRed else Color(0xFF211E20), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Color(0xFF8A8387), fontSize = 8.5.sp, lineHeight = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) trailing() else Text("›", color = Color(0xFFB4ADB1), fontSize = 21.sp)
    }
}

@Composable
private fun RiderSettingDivider() { HorizontalDivider(color = Color(0xFFF0EAEC)) }

@Composable
private fun RiderProfilePill(online: Boolean, offlineLabel: String = "ออฟไลน์") {
    Text(if (online) "ออนไลน์" else offlineLabel, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold,
        color = if (online) Color(0xFF15955A) else Color(0xFF85878C),
        modifier = Modifier.background(if (online) Color(0xFFEEF8F2) else Color(0xFFF2F2F3), RoundedCornerShape(99.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
}

@Composable
private fun RiderAccountField(label: String, value: String, change: (String) -> Unit, enabled: Boolean, phone: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Surface(shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color(0xFFE6DFE2)), color = Color.White) {
            BasicTextField(value, change, enabled = enabled, singleLine = true, cursorBrush = SolidColor(QgRed),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = Color(0xFF17171B)),
                keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                decorationBox = { inner -> Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) { inner() } })
        }
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
