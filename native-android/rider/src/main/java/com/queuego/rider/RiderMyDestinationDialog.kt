package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.queuego.shared.QgLine
import com.queuego.shared.QgLongdoLocationPickerMap
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class RiderDestinationDraft(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

internal fun riderDestinationControlLabel(value: RiderDestinationPreference?): String = when {
    value?.configured != true -> "ตั้งจุดหมาย"
    value.enabled -> "ไปทาง " + value.label.orEmpty()
    else -> "จุดหมายส่วนตัว"
}

internal fun riderDestinationReady(latitude: Double?, longitude: Double?, label: String): Boolean =
    latitude != null && longitude != null &&
        latitude in 5.0..21.0 &&
        longitude in 97.0..106.0 &&
        label.trim().isNotEmpty() &&
        label.trim().length <= 160

@Composable
internal fun RiderMyDestinationDialog(
    auth: QueueGoAuth,
    api: QueueGoApi,
    current: RiderDestinationPreference?,
    currentGps: Pair<Double, Double>?,
    onSaved: (RiderDestinationPreference) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val configuredPoint = current
        ?.takeIf { it.configured && it.latitude != null && it.longitude != null }
        ?.let { QgMapPoint(it.latitude!!, it.longitude!!, 0) }
    val gpsPoint = currentGps
        ?.takeIf { it.first in 5.0..21.0 && it.second in 97.0..106.0 }
        ?.let { QgMapPoint(it.first, it.second, 0) }

    var selected by remember(current?.updatedAt) {
        mutableStateOf(configuredPoint ?: gpsPoint)
    }
    var draftLabel by remember(current?.updatedAt) {
        mutableStateOf(current?.label.orEmpty())
    }
    var lastAutoLabel by remember { mutableStateOf<String?>(null) }
    var recenterToken by remember { mutableIntStateOf(0) }
    var mapReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selected?.latitude, selected?.longitude) {
        val point = selected ?: return@LaunchedEffect
        if (!(draftLabel.isBlank() || draftLabel == lastAutoLabel)) return@LaunchedEffect
        delay(350)
        val resolved = api.reverseGeocodeDestination(point.latitude, point.longitude)?.trim().orEmpty()
        if (resolved.isBlank()) return@LaunchedEffect
        val currentPoint = selected ?: return@LaunchedEffect
        if (
            abs(currentPoint.latitude - point.latitude) > 0.000001 ||
            abs(currentPoint.longitude - point.longitude) > 0.000001
        ) return@LaunchedEffect
        if (draftLabel.isBlank() || draftLabel == lastAutoLabel) {
            draftLabel = resolved
            lastAutoLabel = resolved
        }
    }

    fun save(enabled: Boolean, useExisting: Boolean = false) {
        val point = if (useExisting) configuredPoint else selected
        val label = if (useExisting) current?.label.orEmpty() else draftLabel
        if (!riderDestinationReady(point?.latitude, point?.longitude, label)) {
            message = "กรุณาปักหมุดและระบุชื่อจุดหมาย"
            return
        }
        busy = true
        message = null
        scope.launch {
            try {
                val saved = api.setDestinationPreference(
                    auth = auth,
                    label = label,
                    latitude = point!!.latitude,
                    longitude = point.longitude,
                    enabled = enabled
                )
                onSaved(saved)
                if (!enabled) onClose()
                else onClose()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "บันทึกจุดหมายไม่สำเร็จ"
            } finally {
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onClose() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("My Destination", fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text("ช่วยจัดลำดับงานที่พาไปทางจุดหมาย", color = QgMuted, fontSize = 9.sp)
                    }
                    TextButton(onClick = onClose, enabled = !busy) {
                        Text("ปิด", color = QgRed, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(9.dp))
                Text(
                    "ไม่ใช่การรับงานอัตโนมัติ · ระบบยังเสนอทีละ Rider 30 วินาที และคุณต้องกดรับงานเอง",
                    color = Color(0xFF6A6064),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF7F8), RoundedCornerShape(12.dp))
                        .padding(9.dp)
                )

                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF0F1F2))
                        .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                ) {
                    QgLongdoLocationPickerMap(
                        initialPoint = selected,
                        recenterPoint = gpsPoint,
                        recenterToken = recenterToken,
                        modifier = Modifier.fillMaxSize(),
                        onCenterChanged = { point ->
                            selected = point
                            if (draftLabel == lastAutoLabel) draftLabel = ""
                        },
                        onReady = { mapReady = it }
                    )
                    if (!mapReady) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (gpsPoint != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            selected = gpsPoint
                            recenterToken++
                            if (draftLabel == lastAutoLabel) draftLabel = ""
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ใช้ตำแหน่ง GPS ปัจจุบัน")
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draftLabel,
                    onValueChange = {
                        draftLabel = it.take(160)
                        if (it != lastAutoLabel) lastAutoLabel = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ชื่อ/ที่อยู่จุดหมาย") },
                    minLines = 2,
                    enabled = !busy
                )

                message?.let {
                    Text(it, color = QgRed, fontSize = 9.sp, modifier = Modifier.padding(top = 7.dp))
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { save(true) },
                    enabled = !busy && riderDestinationReady(selected?.latitude, selected?.longitude, draftLabel),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                ) {
                    Text(
                        if (current?.enabled == true) "อัปเดตจุดหมาย" else "บันทึกและเปิด My Destination",
                        fontWeight = FontWeight.Black
                    )
                }

                if (current?.configured == true && current.enabled) {
                    Spacer(Modifier.height(7.dp))
                    OutlinedButton(
                        onClick = { save(false, useExisting = true) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text("ปิด My Destination", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
