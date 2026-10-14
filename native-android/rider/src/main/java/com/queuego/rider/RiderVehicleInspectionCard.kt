package com.queuego.rider

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun riderVehicleInspectionTitle(status: String?): String = when (status) {
    "pending" -> "ส่งรูปรถแล้ว · รอตรวจสอบ"
    "rejected" -> "ต้องถ่ายรูปรถใหม่"
    "required" -> "QueueGo ขอให้ตรวจสภาพรถ"
    "approved" -> "ตรวจรถผ่านแล้ว"
    else -> "ตรวจสภาพรถ Rider"
}

@Composable
internal fun RiderVehicleInspectionCard(
    auth: QueueGoAuth,
    api: QueueGoApi,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember(auth.user.id) { mutableStateOf<RiderVehicleInspectionStatus?>(null) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showCapture by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var previewReady by remember(photoUri) { mutableStateOf(false) }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        try {
            status = api.vehicleInspectionStatus(auth)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            status = null
        } finally {
            loaded = true
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri = pendingCameraUri
            showCapture = true
        }
        pendingCameraUri = null
    }

    val current = status
    if (!loaded || current?.required != true) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (current.status == "rejected") Color(0xFFFFC9CF) else Color(0xFFDCE7FF)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (current.status == "rejected") Color(0xFFFFF4F5) else Color(0xFFF6F9FF)
        )
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(
                riderVehicleInspectionTitle(current.status),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = if (current.status == "rejected") QgRed else Color(0xFF234A84)
            )
            Text(
                when (current.status) {
                    "pending" -> "ระบบได้รับรูปรถปัจจุบันแล้ว ฝ่ายดูแลกำลังตรวจสอบ"
                    "rejected" -> current.reviewNote ?: "รูปก่อนหน้ายังไม่ผ่าน กรุณาถ่ายให้เห็นรถที่ใช้งานชัดเจน"
                    else -> current.reason ?: "ถ่ายรูปรถที่ใช้วิ่งงาน QueueGo ในปัจจุบัน ให้เห็นตัวรถและทะเบียนชัดเจน"
                },
                color = QgMuted,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
            if (current.status != "pending") {
                Spacer(Modifier.height(9.dp))
                Button(
                    onClick = {
                        runCatching {
                            val uri = createRiderVehicleInspectionUri(context)
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        }.onFailure {
                            pendingCameraUri = null
                            message = "เปิดกล้องไม่สำเร็จ กรุณาลองใหม่"
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                ) {
                    Text(
                        if (current.status == "rejected") "ถ่ายรูปรถใหม่" else "ถ่ายรูปรถเพื่อตรวจ",
                        fontWeight = FontWeight.Black
                    )
                }
            } else {
                Spacer(Modifier.height(7.dp))
                Text("ยังไม่ต้องส่งซ้ำจนกว่าจะมีผลตรวจ", color = QgMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            }
            message?.let {
                Text(it, color = QgRed, fontSize = 9.sp, modifier = Modifier.padding(top = 7.dp))
            }
        }
    }

    if (showCapture && photoUri != null) {
        Dialog(onDismissRequest = { if (!busy) showCapture = false }) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.padding(14.dp)) {
                    Text("ตรวจรูปสภาพรถ", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(
                        "ใช้รูปที่ถ่ายใหม่จากกล้อง ให้เห็นรถและทะเบียนชัดเจน ไม่ใช้รูปจากคลังภาพ",
                        color = QgMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    RiderEvidencePreview(
                        photoUri!!,
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(Color(0xFFF2EFF0), RoundedCornerShape(16.dp))
                    ) { previewReady = it }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val uri = createRiderVehicleInspectionUri(context)
                                    pendingCameraUri = uri
                                    cameraLauncher.launch(uri)
                                }.onFailure {
                                    pendingCameraUri = null
                                    message = "เปิดกล้องไม่สำเร็จ กรุณาลองใหม่"
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("ถ่ายใหม่") }
                        Button(
                            onClick = {
                                val uri = photoUri ?: return@Button
                                busy = true
                                message = null
                                scope.launch {
                                    try {
                                        status = api.submitVehicleInspection(context, auth, uri)
                                        showCapture = false
                                        photoUri = null
                                        message = "ส่งรูปรถให้ตรวจแล้ว"
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        message = e.message ?: "ส่งรูปรถไม่สำเร็จ"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && previewReady,
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
}

private fun createRiderVehicleInspectionUri(context: Context): Uri {
    val dir = File(context.cacheDir, "queuego-evidence").apply { mkdirs() }
    val file = File.createTempFile("qg-vehicle-inspection-", ".jpg", dir)
    return FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )
}
