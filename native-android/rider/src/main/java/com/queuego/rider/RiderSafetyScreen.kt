package com.queuego.rider

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val RIDER_EMERGENCY_POLICE = "191"
internal const val RIDER_EMERGENCY_MEDICAL = "1669"
internal const val RIDER_TOURIST_POLICE = "1155"

internal data class RiderSafetyContact(
    val title: String,
    val detail: String,
    val number: String,
    val urgent: Boolean
)

internal fun riderSafetyContacts(): List<RiderSafetyContact> = listOf(
    RiderSafetyContact(
        title = "เหตุด่วนเหตุร้าย",
        detail = "ตำรวจ 191",
        number = RIDER_EMERGENCY_POLICE,
        urgent = true
    ),
    RiderSafetyContact(
        title = "เจ็บป่วย / อุบัติเหตุฉุกเฉิน",
        detail = "การแพทย์ฉุกเฉิน 1669",
        number = RIDER_EMERGENCY_MEDICAL,
        urgent = true
    ),
    RiderSafetyContact(
        title = "ตำรวจท่องเที่ยว",
        detail = "สายด่วน 1155",
        number = RIDER_TOURIST_POLICE,
        urgent = false
    )
)

@Composable
internal fun RiderSafetyScreen(
    auth: QueueGoAuth,
    api: QueueGoApi,
    activeJob: RiderJob?,
    onSupport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var emergencyContact by remember(auth.user.id) { mutableStateOf<RiderEmergencyContact?>(null) }
    var emergencyLoaded by remember(auth.user.id) { mutableStateOf(false) }
    var emergencyEditing by remember { mutableStateOf(false) }
    var emergencyBusy by remember { mutableStateOf(false) }
    var emergencyName by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }
    var emergencyMessage by remember { mutableStateOf<String?>(null) }
    var locationShareMessage by remember { mutableStateOf<String?>(null) }
    var mapFeedbackLocation by remember { mutableStateOf<RiderSafetyLocation?>(null) }
    var showMapFeedback by remember { mutableStateOf(false) }
    var fatigueStatus by remember(auth.user.id) { mutableStateOf<RiderFatigueStatus?>(null) }
    var fatigueLoaded by remember(auth.user.id) { mutableStateOf(false) }
    val navigationFix by RiderNavigationLocationStore.fix.collectAsState()

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        try {
            emergencyContact = api.emergencyContact(auth)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emergencyMessage = "โหลดผู้ติดต่อฉุกเฉินไม่สำเร็จ"
        } finally {
            emergencyLoaded = true
        }
    }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        fatigueStatus = try {
            api.fatigueStatus(auth)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        fatigueLoaded = true
    }

    fun dial(number: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
            )
        }
    }

    fun currentShareLocation(): RiderSafetyLocation? =
        navigationFix?.point?.let {
            riderSafetyLocationOrNull(it.latitude, it.longitude)
        } ?: riderSafetyLastKnownLocation(context)

    fun shareCurrentLocation() {
        val location = currentShareLocation()
        if (location == null) {
            locationShareMessage = "ยังไม่มีตำแหน่งที่ใหม่พอ กรุณาเปิด GPS แล้วลองอีกครั้ง"
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, riderSafetyLocationShareText(location))
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "แชร์ตำแหน่งปัจจุบัน"))
        }.onSuccess {
            locationShareMessage = null
        }.onFailure {
            locationShareMessage = "ไม่สามารถเปิดเมนูแชร์ได้"
        }
    }

    fun messageEmergencyContactLocation(phone: String) {
        val location = currentShareLocation()
        if (location == null) {
            locationShareMessage = "ยังไม่มีตำแหน่งที่ใหม่พอ กรุณาเปิด GPS แล้วลองอีกครั้ง"
            return
        }
        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("smsto:" + Uri.encode(phone))
        ).putExtra("sms_body", riderSafetyLocationShareText(location))
        runCatching {
            context.startActivity(intent)
        }.onSuccess {
            locationShareMessage = null
        }.onFailure {
            locationShareMessage = "ไม่สามารถเปิดแอปข้อความได้"
        }
    }

    Column(
        modifier
            .heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("ศูนย์ความปลอดภัย", fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("QueueGo Rider", color = QgMuted, fontSize = 10.sp)
            }
            TextButton(onClick = onClose) {
                Text("ปิด", color = QgRed, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4F4)),
            border = BorderStroke(1.dp, Color(0xFFFFD8DB))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("กรณีฉุกเฉิน", color = QgRed, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(
                    "ปุ่มด้านล่างจะเปิดหน้าจอโทรศัพท์ก่อนทุกครั้ง เพื่อป้องกันการโทรโดยไม่ตั้งใจ",
                    color = QgMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                activeJob?.let { job ->
                    Spacer(Modifier.height(8.dp))
                    Text("ออเดอร์ปัจจุบัน", color = QgMuted, fontSize = 9.sp)
                    Text(job.numberLabel, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        riderSafetyContacts().forEach { contact ->
            RiderSafetyContactCard(contact) { dial(contact.number) }
        }

        Spacer(Modifier.height(5.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFEEE6E8))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("ผู้ติดต่อฉุกเฉินของฉัน", fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(
                    "QueueGo จะไม่โทรหรือส่งข้อมูลให้บุคคลนี้อัตโนมัติ",
                    color = QgMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(8.dp))
                when {
                    !emergencyLoaded -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    emergencyContact?.configured == true &&
                        !emergencyContact?.name.isNullOrBlank() &&
                        !emergencyContact?.phone.isNullOrBlank() -> {
                        Text(emergencyContact?.name.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(emergencyContact?.phone.orEmpty(), color = QgMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { dial(emergencyContact?.phone.orEmpty()) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17191D))
                            ) {
                                Text("โทรหาผู้ติดต่อ", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    emergencyName = emergencyContact?.name.orEmpty()
                                    emergencyPhone = emergencyContact?.phone.orEmpty()
                                    emergencyMessage = null
                                    emergencyEditing = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text("แก้ไข", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        Text(
                            "เพิ่มคนที่คุณไว้ใจ 1 คน เพื่อให้โทรหาได้เร็วเมื่อเกิดเหตุฉุกเฉิน",
                            color = QgMuted,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                emergencyName = ""
                                emergencyPhone = ""
                                emergencyMessage = null
                                emergencyEditing = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("เพิ่มผู้ติดต่อฉุกเฉิน", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                emergencyMessage?.let {
                    Text(it, color = QgRed, fontSize = 9.sp, modifier = Modifier.padding(top = 7.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (fatigueStatus?.level) {
                    "strong_rest_recommended" -> Color(0xFFFFF1F2)
                    "rest_recommended" -> Color(0xFFFFFAEF)
                    else -> Color.White
                }
            ),
            border = BorderStroke(
                1.dp,
                when (fatigueStatus?.level) {
                    "strong_rest_recommended" -> Color(0xFFFFCCD2)
                    "rest_recommended" -> Color(0xFFFFE0A8)
                    else -> Color(0xFFEEE6E8)
                }
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("เวลาทำงานและการพัก", fontWeight = FontWeight.Black, fontSize = 14.sp)
                when {
                    !fatigueLoaded -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    fatigueStatus == null -> Text(
                        "ยังโหลดเวลาทำงานไม่ได้",
                        color = QgMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    else -> {
                        val fatigue = fatigueStatus!!
                        Text(
                            riderFatigueTitle(fatigue.level),
                            color = when (fatigue.level) {
                                "strong_rest_recommended" -> QgRed
                                "rest_recommended" -> Color(0xFF9A6500)
                                else -> Color(0xFF15845E)
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            riderFatigueDetail(fatigue.level),
                            color = QgMuted,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8F5F6)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("ออนไลน์ต่อเนื่อง", color = QgMuted, fontSize = 8.sp)
                                    Text(
                                        riderFatigueDurationLabel(fatigue.continuousOnlineMinutes),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8F5F6)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("วันนี้รวม", color = QgMuted, fontSize = 8.sp)
                                    Text(
                                        riderFatigueDurationLabel(fatigue.onlineMinutesToday),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "คำนวณจากประวัติเปิด/ปิดรับงานของบัญชีนี้ ไม่ใช่ระบบตรวจความง่วงหรือข้อมูลสุขภาพ",
                            color = QgMuted,
                            fontSize = 8.sp,
                            lineHeight = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFEEE6E8))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("แชร์ตำแหน่งเพื่อความปลอดภัย", fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(
                    "แชร์เฉพาะตำแหน่งปัจจุบัน ณ ตอนที่กด ไม่ใช่การติดตามแบบเรียลไทม์ และไม่แนบที่อยู่ลูกค้าหรือร้านค้า",
                    color = QgMuted,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { shareCurrentLocation() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("แชร์ตำแหน่งปัจจุบัน", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(7.dp))
                OutlinedButton(
                    onClick = {
                        val location = currentShareLocation()
                        if (location == null) {
                            locationShareMessage = "ยังไม่มีตำแหน่งที่ใหม่พอ กรุณาเปิด GPS แล้วลองอีกครั้ง"
                        } else {
                            mapFeedbackLocation = location
                            showMapFeedback = true
                            locationShareMessage = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("รายงานปัญหาแผนที่ / ถนน", fontWeight = FontWeight.Bold)
                }
                emergencyContact?.takeIf {
                    it.configured && !it.phone.isNullOrBlank()
                }?.phone?.let { phone ->
                    Spacer(Modifier.height(7.dp))
                    Button(
                        onClick = { messageEmergencyContactLocation(phone) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17191D))
                    ) {
                        Text("เปิด SMS ถึงผู้ติดต่อฉุกเฉิน", fontWeight = FontWeight.Bold)
                    }
                }
                locationShareMessage?.let {
                    Text(
                        it,
                        color = QgRed,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFEEE6E8))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("QueueGo Support", fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(
                    "ปัญหาเกี่ยวกับออเดอร์ ลูกค้า ร้านค้า การรับงาน หรือบัญชี Rider",
                    color = QgMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSupport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("ติดต่อฝ่ายดูแล QueueGo", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showMapFeedback && mapFeedbackLocation != null) {
        Dialog(onDismissRequest = { showMapFeedback = false }) {
            RiderMapFeedbackDialog(
                auth = auth,
                api = api,
                location = mapFeedbackLocation!!,
                onSubmitted = {
                    showMapFeedback = false
                    mapFeedbackLocation = null
                    locationShareMessage = "ส่งรายงานให้ทีมแผนที่ตรวจสอบแล้ว"
                },
                onClose = {
                    showMapFeedback = false
                    mapFeedbackLocation = null
                }
            )
        }
    }

    if (emergencyEditing) {
        Dialog(onDismissRequest = { if (!emergencyBusy) emergencyEditing = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White
            ) {
                Column(Modifier.fillMaxWidth().imePadding().padding(16.dp)) {
                    Text("ผู้ติดต่อฉุกเฉิน", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(
                        "บันทึกไว้เพื่อให้คุณกดโทรได้เร็วจากศูนย์ความปลอดภัย",
                        color = QgMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 3.dp, bottom = 10.dp)
                    )
                    OutlinedTextField(
                        value = emergencyName,
                        onValueChange = { if (it.length <= 100) emergencyName = it },
                        label = { Text("ชื่อผู้ติดต่อ") },
                        enabled = !emergencyBusy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emergencyPhone,
                        onValueChange = { if (it.length <= 24) emergencyPhone = it },
                        label = { Text("เบอร์โทรศัพท์") },
                        enabled = !emergencyBusy,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "รองรับหมายเลข 8–15 หลัก และหมายเลขที่ขึ้นต้นด้วย +",
                        color = QgMuted,
                        fontSize = 8.5.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            emergencyBusy = true
                            emergencyMessage = null
                            scope.launch {
                                try {
                                    emergencyContact = api.setEmergencyContact(
                                        auth,
                                        emergencyName.trim(),
                                        emergencyPhone.trim()
                                    )
                                    emergencyEditing = false
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    emergencyMessage = e.message ?: "บันทึกผู้ติดต่อไม่สำเร็จ"
                                } finally {
                                    emergencyBusy = false
                                }
                            }
                        },
                        enabled = !emergencyBusy &&
                            emergencyName.trim().isNotEmpty() &&
                            emergencyPhone.count { it.isDigit() } in 8..15,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                    ) {
                        Text(if (emergencyBusy) "กำลังบันทึก…" else "บันทึกผู้ติดต่อ", fontWeight = FontWeight.Black)
                    }

                    if (emergencyContact?.configured == true) {
                        TextButton(
                            onClick = {
                                emergencyBusy = true
                                emergencyMessage = null
                                scope.launch {
                                    try {
                                        emergencyContact = api.setEmergencyContact(auth, "", "")
                                        emergencyEditing = false
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        emergencyMessage = e.message ?: "ลบผู้ติดต่อไม่สำเร็จ"
                                    } finally {
                                        emergencyBusy = false
                                    }
                                }
                            },
                            enabled = !emergencyBusy,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("ลบผู้ติดต่อฉุกเฉิน", color = QgRed)
                        }
                    }

                    TextButton(
                        onClick = { emergencyEditing = false },
                        enabled = !emergencyBusy,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("ปิด")
                    }
                }
            }
        }
    }
}

@Composable
private fun RiderSafetyContactCard(
    contact: RiderSafetyContact,
    onDial: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.dp,
            if (contact.urgent) Color(0xFFFFD8DB) else Color(0xFFEEE6E8)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    contact.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = if (contact.urgent) QgRed else Color(0xFF17191D)
                )
                Text(contact.detail, color = QgMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Button(
                onClick = onDial,
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (contact.urgent) QgRed else Color(0xFF17191D)
                )
            ) {
                Text("โทร " + contact.number, fontWeight = FontWeight.Bold)
            }
        }
    }
}
