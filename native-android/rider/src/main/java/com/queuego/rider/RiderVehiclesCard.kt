package com.queuego.rider

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.core.content.FileProvider
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun riderVehicleStatusLabel(vehicle: RiderVehicle): String = when {
    vehicle.selected && vehicle.status == "approved" -> "กำลังใช้ · อนุมัติแล้ว"
    vehicle.legacySeed && vehicle.status != "approved" -> "รถเดิม · ยังไม่ยืนยันใหม่"
    vehicle.status == "approved" -> "อนุมัติแล้ว"
    vehicle.status == "pending" -> "รอ Admin ตรวจ"
    vehicle.status == "rejected" -> "ต้องแก้ไข"
    else -> vehicle.status
}

internal fun riderVehicleTypeLabel(type: String): String = when (type) {
    "motorcycle" -> "รถจักรยานยนต์"
    "car" -> "รถยนต์"
    "bicycle" -> "จักรยาน"
    "saleng" -> "รถซาเล้ง"
    else -> type
}

@Composable
internal fun RiderVehiclesCard(
    auth: QueueGoAuth,
    api: QueueGoApi,
    online: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vehicles by remember(auth.user.id) { mutableStateOf<List<RiderVehicle>>(emptyList()) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var seedVehicle by remember { mutableStateOf<RiderVehicle?>(null) }

    suspend fun reload() {
        vehicles = api.vehicles(auth)
        loaded = true
    }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        try {
            reload()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loaded = true
        }
    }

    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFEEE6E8)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("รถที่ใช้วิ่งงาน", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(
                        "เพิ่มได้สูงสุด 5 คัน · รถใหม่ต้องผ่าน Admin ก่อนใช้งาน",
                        color = QgMuted,
                        fontSize = 8.5.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                TextButton(
                    onClick = {
                        seedVehicle = null
                        showAdd = true
                    },
                    enabled = !busy
                ) {
                    Text("เพิ่มรถ", color = QgRed, fontWeight = FontWeight.Bold)
                }
            }

            if (!loaded) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            } else if (vehicles.isEmpty()) {
                Text("ยังไม่มีข้อมูลรถ", color = QgMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
            } else {
                Spacer(Modifier.height(8.dp))
                vehicles.forEachIndexed { index, vehicle ->
                    if (index > 0) HorizontalDivider(color = Color(0xFFF0EAEC))
                    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    listOfNotNull(
                                        riderVehicleTypeLabel(vehicle.type),
                                        vehicle.name?.takeIf { it.isNotBlank() }
                                    ).joinToString(" · "),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    vehicle.plate,
                                    color = QgMuted,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                vehicle.capacityKg?.let {
                                    Text(
                                        "ความจุ ${riderVehicleCapacityLabel(it)} กก.",
                                        color = QgMuted,
                                        fontSize = 8.5.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    riderVehicleStatusLabel(vehicle),
                                    color = when {
                                        vehicle.status == "approved" -> Color(0xFF12845E)
                                        vehicle.status == "rejected" -> QgRed
                                        else -> Color(0xFF9A6B00)
                                    },
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                vehicle.reviewNote?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        "เหตุผล: $it",
                                        color = QgRed,
                                        fontSize = 8.sp,
                                        modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                            }

                            when {
                                vehicle.selected -> {
                                    Text(
                                        "คันปัจจุบัน",
                                        color = Color(0xFF12845E),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                vehicle.status == "approved" -> {
                                    OutlinedButton(
                                        onClick = {
                                            busy = true
                                            message = null
                                            scope.launch {
                                                try {
                                                    api.selectVehicle(auth, vehicle.id)
                                                    reload()
                                                    onSelected()
                                                    message = "เปลี่ยนรถที่ใช้วิ่งงานแล้ว"
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    message = e.message ?: "เปลี่ยนรถไม่สำเร็จ"
                                                } finally {
                                                    busy = false
                                                }
                                            }
                                        },
                                        enabled = !busy && !online
                                    ) {
                                        Text(if (online) "ออฟไลน์ก่อน" else "ใช้คันนี้", fontSize = 9.sp)
                                    }
                                }
                                (vehicle.legacySeed || vehicle.status == "rejected") -> {
                                    OutlinedButton(
                                        onClick = {
                                            seedVehicle = vehicle
                                            showAdd = true
                                        },
                                        enabled = !busy && !(vehicle.selected && online)
                                    ) {
                                        Text(
                                            if (vehicle.status == "rejected") "ส่งใหม่" else "ยืนยันรถ",
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (online) {
                Text(
                    "ต้องออฟไลน์และจบงานก่อนเปลี่ยนรถที่กำลังใช้",
                    color = QgMuted,
                    fontSize = 8.5.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
            message?.let {
                Text(it, color = if (it.contains("แล้ว")) Color(0xFF12845E) else QgRed, fontSize = 9.sp, modifier = Modifier.padding(top = 7.dp))
            }
        }
    }

    if (showAdd) {
        RiderAddVehicleDialog(
            auth = auth,
            api = api,
            initial = seedVehicle,
            onDismiss = { if (!busy) showAdd = false },
            onBusy = { busy = it },
            onSaved = {
                showAdd = false
                scope.launch {
                    try {
                        reload()
                        message = "ส่งข้อมูลรถให้ Admin ตรวจแล้ว"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = e.message ?: "โหลดข้อมูลรถไม่สำเร็จ"
                    }
                }
            },
            onMessage = { message = it }
        )
    }
}

@Composable
private fun RiderAddVehicleDialog(
    auth: QueueGoAuth,
    api: QueueGoApi,
    initial: RiderVehicle?,
    onDismiss: () -> Unit,
    onBusy: (Boolean) -> Unit,
    onSaved: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: "motorcycle") }
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var plate by remember(initial?.id) { mutableStateOf(initial?.plate.orEmpty()) }
    var capacity by remember(initial?.id) { mutableStateOf(initial?.capacityKg?.let(::riderVehicleCapacityLabel).orEmpty()) }
    var photoUri by remember(initial?.id) { mutableStateOf<Uri?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var previewReady by remember(photoUri) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoUri = pendingUri
        pendingUri = null
    }

    fun openCamera() {
        runCatching {
            val uri = createRiderVehicleUri(context)
            pendingUri = uri
            camera.launch(uri)
        }.onFailure {
            pendingUri = null
            onMessage("เปิดกล้องไม่สำเร็จ กรุณาลองใหม่")
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
            Column(
                Modifier
                    .heightIn(max = 700.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    if (initial == null) "เพิ่มรถสำหรับวิ่งงาน" else "ยืนยันข้อมูลรถ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "รถใหม่ต้องถ่ายรูปจริงและรอ Admin อนุมัติก่อนเลือกใช้งาน",
                    color = QgMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                Text("ประเภทรถ", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Spacer(Modifier.height(5.dp))
                listOf(
                    "motorcycle" to "มอเตอร์ไซค์",
                    "car" to "รถยนต์",
                    "bicycle" to "จักรยาน",
                    "saleng" to "ซาเล้ง"
                ).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (key,label) ->
                            if (type == key) {
                                Button(onClick = { type = key }, modifier = Modifier.weight(1f)) { Text(label, fontSize = 9.sp) }
                            } else {
                                OutlinedButton(onClick = { type = key }, modifier = Modifier.weight(1f)) { Text(label, fontSize = 9.sp) }
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }

                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.take(32) },
                    label = { Text("ทะเบียนรถ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text("ยี่ห้อ / รุ่น (ไม่บังคับ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8) },
                    label = { Text("ความจุโดยประมาณ กก. (ไม่บังคับ)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                if (photoUri == null) {
                    OutlinedButton(onClick = ::openCamera, modifier = Modifier.fillMaxWidth()) {
                        Text("ถ่ายรูปรถ", fontWeight = FontWeight.Bold)
                    }
                } else {
                    RiderEvidencePreview(
                        photoUri!!,
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color(0xFFF2EFF0), RoundedCornerShape(16.dp))
                    ) { previewReady = it }
                    Spacer(Modifier.height(7.dp))
                    OutlinedButton(onClick = ::openCamera, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("ถ่ายใหม่")
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("ยกเลิก") }
                    Button(
                        onClick = {
                            val uri = photoUri ?: return@Button
                            val kg = capacity.trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
                            if (plate.trim().length < 2) {
                                onMessage("กรุณากรอกทะเบียนรถ")
                                return@Button
                            }
                            if (capacity.isNotBlank() && (kg == null || kg !in 0.0..1000.0)) {
                                onMessage("ความจุรถต้องอยู่ระหว่าง 0-1000 กก.")
                                return@Button
                            }
                            busy = true
                            onBusy(true)
                            scope.launch {
                                try {
                                    api.addVehicle(context, auth, type, name, plate, kg, uri)
                                    onSaved()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    onMessage(e.message ?: "ส่งข้อมูลรถไม่สำเร็จ")
                                } finally {
                                    busy = false
                                    onBusy(false)
                                }
                            }
                        },
                        enabled = !busy && photoUri != null && previewReady,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                    ) {
                        Text(if (busy) "กำลังส่ง…" else "ส่งให้ตรวจ", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

internal fun riderVehicleCapacityLabel(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun createRiderVehicleUri(context: Context): Uri {
    val dir = File(context.cacheDir, "queuego-evidence").apply { mkdirs() }
    val file = File.createTempFile("qg-rider-vehicle-", ".jpg", dir)
    return FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )
}
