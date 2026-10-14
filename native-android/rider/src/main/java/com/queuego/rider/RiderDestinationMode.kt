package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.queuego.shared.QgLongdoLocationPickerMap
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun validRiderDestinationPoint(latitude: Double?, longitude: Double?): Boolean =
    latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() &&
        latitude in 5.0..21.0 && longitude in 97.0..106.0

@Composable
internal fun RiderDestinationModeCard(
    auth: QueueGoAuth,
    api: QueueGoApi,
    currentLocation: () -> Pair<Double, Double>?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var preference by remember(auth.user.id) { mutableStateOf<RiderDestinationPreference?>(null) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var draftLabel by remember { mutableStateOf("") }
    var draftPoint by remember { mutableStateOf<QgMapPoint?>(null) }
    var recenterPoint by remember { mutableStateOf<QgMapPoint?>(null) }
    var recenterToken by remember { mutableIntStateOf(0) }
    var mapReady by remember { mutableStateOf(false) }

    suspend fun refresh() {
        preference = api.destinationPreference(auth)
        loaded = true
    }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        try {
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loaded = true
            message = "โหลดโหมดไปทางนี้ไม่สำเร็จ"
        }
    }

    fun openEditor() {
        val saved = preference
        val savedPoint = if (validRiderDestinationPoint(saved?.latitude, saved?.longitude)) {
            QgMapPoint(saved!!.latitude!!, saved.longitude!!, 0)
        } else {
            currentLocation()?.let { (lat, lng) ->
                QgMapPoint(lat, lng, 0).takeIf { it.valid }
            }
        }
        draftLabel = saved?.label.orEmpty()
        draftPoint = savedPoint
        recenterPoint = savedPoint
        recenterToken++
        mapReady = false
        message = null
        editing = true
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFEEE5E8)),
        colors = CardDefaults.cardColors(
            containerColor = if (preference?.enabled == true) Color(0xFFFFF6F7) else Color.White
        )
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ไปทางนี้", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(
                        when {
                            !loaded -> "กำลังโหลดจุดหมาย…"
                            preference?.configured != true -> "ยังไม่ได้ตั้งจุดหมาย"
                            preference?.enabled == true -> "เปิดอยู่ · " + preference?.label.orEmpty()
                            else -> "ปิดอยู่ · " + preference?.label.orEmpty()
                        },
                        color = QgMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (loaded && preference?.configured == true) {
                    Switch(
                        checked = preference?.enabled == true,
                        onCheckedChange = { enabled ->
                            val saved = preference ?: return@Switch
                            val lat = saved.latitude ?: return@Switch
                            val lng = saved.longitude ?: return@Switch
                            val label = saved.label ?: return@Switch
                            if (busy) return@Switch
                            busy = true
                            scope.launch {
                                try {
                                    preference = api.setDestinationPreference(
                                        auth = auth,
                                        label = label,
                                        latitude = lat,
                                        longitude = lng,
                                        enabled = enabled
                                    )
                                    message = if (enabled) "เปิดโหมดไปทางนี้แล้ว" else "ปิดโหมดไปทางนี้แล้ว"
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    message = e.message ?: "เปลี่ยนโหมดไปทางนี้ไม่สำเร็จ"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy
                    )
                }
            }

            Text(
                "ช่วยจัดลำดับงานที่พาเข้าใกล้ปลายทางในกลุ่มงานระยะรับใกล้เคียง ไม่ได้กรองงานและไม่รับประกันว่าจะได้งานไปทางนั้น",
                color = Color(0xFF7B6870),
                fontSize = 8.5.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 7.dp)
            )

            OutlinedButton(
                onClick = ::openEditor,
                enabled = loaded && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(13.dp)
            ) {
                Text(
                    if (preference?.configured == true) "เปลี่ยนจุดหมาย" else "ตั้งจุดหมาย",
                    fontWeight = FontWeight.Bold
                )
            }

            message?.let {
                Text(
                    it,
                    color = if (it.contains("ไม่สำเร็จ")) QgRed else QgMuted,
                    fontSize = 8.5.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    if (editing) {
        Dialog(onDismissRequest = { if (!busy) editing = false }) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color.White
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ตั้งจุดหมาย · ไปทางนี้", fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text(
                                "ลากแผนที่ให้กากบาทอยู่ที่จุดหมาย",
                                color = QgMuted,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        TextButton(
                            onClick = { editing = false },
                            enabled = !busy
                        ) { Text("ปิด", color = QgRed) }
                    }

                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(Color(0xFFF0F1F2), RoundedCornerShape(15.dp))
                    ) {
                        QgLongdoLocationPickerMap(
                            initialPoint = draftPoint,
                            recenterPoint = recenterPoint,
                            recenterToken = recenterToken,
                            modifier = Modifier.fillMaxSize(),
                            onCenterChanged = { point ->
                                if (point.valid) draftPoint = point
                            },
                            onReady = { mapReady = it }
                        )
                        if (!mapReady) {
                            CircularProgressIndicator(
                                Modifier.align(Alignment.Center).size(28.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val location = currentLocation()
                            if (location == null) {
                                message = "ยังอ่านตำแหน่ง GPS ไม่ได้ กรุณาเปิดสิทธิ์ตำแหน่งแล้วลองอีกครั้ง"
                            } else {
                                val point = QgMapPoint(location.first, location.second, 0)
                                if (point.valid) {
                                    draftPoint = point
                                    recenterPoint = point
                                    recenterToken++
                                } else {
                                    message = "ตำแหน่ง GPS อยู่นอกพื้นที่ที่รองรับ"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) {
                        Text("ใช้ตำแหน่ง GPS ปัจจุบัน")
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftLabel,
                        onValueChange = { draftLabel = it.take(160) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("ชื่อจุดหมาย เช่น บ้าน / ตัวเมือง") }
                    )

                    draftPoint?.let {
                        Text(
                            "Lat: %.5f · Lng: %.5f".format(it.latitude, it.longitude),
                            color = QgMuted,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val point = draftPoint ?: return@Button
                            val label = draftLabel.trim()
                            if (label.isBlank()) {
                                message = "กรุณาระบุชื่อจุดหมาย"
                                return@Button
                            }
                            busy = true
                            scope.launch {
                                try {
                                    preference = api.setDestinationPreference(
                                        auth = auth,
                                        label = label,
                                        latitude = point.latitude,
                                        longitude = point.longitude,
                                        enabled = true
                                    )
                                    message = "บันทึกจุดหมายและเปิดโหมดไปทางนี้แล้ว"
                                    editing = false
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    message = e.message ?: "บันทึกจุดหมายไม่สำเร็จ"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && draftPoint?.valid == true && draftLabel.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("บันทึกและเปิดใช้", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
