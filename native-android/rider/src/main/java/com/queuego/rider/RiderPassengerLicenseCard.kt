package com.queuego.rider

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class RiderPassengerLicenseStatus(
    val valid: Boolean,
    val status: String,
    val expiresOn: String?,
    val submittedAt: String?,
    val reviewNote: String?,
    val hasEvidence: Boolean
)

internal fun riderPassengerLicenseStatusFromRpc(raw: Any?): RiderPassengerLicenseStatus {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0)
        else -> null
    } ?: error("อ่านสถานะใบขับขี่สาธารณะไม่ได้")
    fun text(key: String) = row.optString(key).takeIf { it.isNotBlank() && it != "null" }
    return RiderPassengerLicenseStatus(
        valid = row.optBoolean("valid", false),
        status = text("status") ?: if (row.optBoolean("valid", false)) "approved" else "missing",
        expiresOn = text("expires_on"),
        submittedAt = text("submitted_at"),
        reviewNote = text("review_note"),
        hasEvidence = row.optBoolean("has_evidence", false)
    )
}

@Composable
internal fun RiderPassengerLicenseCard(
    auth: QueueGoAuth,
    api: QueueGoApi,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(auth.user.id) { mutableStateOf<RiderPassengerLicenseStatus?>(null) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf(false) }
    var previewReady by remember(selected) { mutableStateOf(false) }

    suspend fun refresh() {
        state = api.passengerLicenseStatus(auth)
        loaded = true
    }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        try {
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loaded = true
            message = "ยังตรวจสิทธิ์รับส่งผู้โดยสารไม่ได้"
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !busy) {
            selected = uri
            preview = true
        }
    }

    val current = state
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            when {
                current?.valid == true -> Color(0xFFCFE9D8)
                current?.status == "rejected" -> Color(0xFFFFC9CF)
                else -> Color(0xFFFFE2B8)
            }
        ),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(13.dp)) {
            Text("สิทธิ์รับส่งผู้โดยสาร", fontWeight = FontWeight.Black, fontSize = 14.sp)
            if (loaded) {
                val detail = when {
                    current?.valid == true && current.status == "pending" ->
                        "สิทธิ์เดิมยังใช้ได้ · เอกสารต่ออายุใหม่กำลังรอ Admin ตรวจ"
                    current?.valid == true ->
                        "ใบขับขี่สาธารณะผ่านการตรวจและยังไม่หมดอายุ"
                    current?.status == "pending" ->
                        "ส่งหลักฐานแล้ว · รอ Admin ตรวจ ก่อนรับเที่ยวผู้โดยสาร"
                    current?.status == "rejected" ->
                        current.reviewNote ?: "หลักฐานยังไม่ผ่าน กรุณาส่งใบขับขี่สาธารณะใหม่"
                    else ->
                        "ต้องมีใบขับขี่สาธารณะที่ Admin ตรวจอนุมัติก่อนรับเที่ยวผู้โดยสาร"
                }
                Text(detail, color = QgMuted, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 5.dp))
            }
            current?.expiresOn?.let {
                Text("วันหมดอายุ $it", color = QgMuted, fontSize = 8.5.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                "สิทธิ์นี้ใช้เฉพาะรับส่งผู้โดยสาร งานส่งอาหาร/สินค้าใช้สิทธิ์ Rider เดิม",
                color = QgMuted,
                fontSize = 8.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
            if (loaded && current?.status != "pending") {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { picker.launch("image/*") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (current?.hasEvidence == true) "ส่งใบขับขี่สาธารณะใหม่" else "อัปโหลดใบขับขี่สาธารณะ")
                }
            }
            message?.let {
                Text(it, color = QgRed, fontSize = 8.5.sp, modifier = Modifier.padding(top = 6.dp))
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { refresh() }
                                .onFailure { message = "ยังตรวจสิทธิ์ไม่ได้" }
                        }
                    }
                ) {
                    Text("ลองตรวจอีกครั้ง")
                }
            }
        }
    }

    if (preview && selected != null) {
        Dialog(onDismissRequest = { if (!busy) preview = false }) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.padding(14.dp)) {
                    Text("ตรวจรูปใบขับขี่สาธารณะ", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(
                        "ให้เห็นชื่อ ประเภทใบอนุญาต และวันหมดอายุชัดเจน",
                        color = QgMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    RiderEvidencePreview(
                        selected!!,
                        Modifier.fillMaxWidth().height(280.dp)
                    ) { quality -> previewReady = quality.acceptable }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { picker.launch("image/*") },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("เลือกใหม่") }
                        Button(
                            onClick = {
                                val uri = selected ?: return@Button
                                busy = true
                                message = null
                                scope.launch {
                                    try {
                                        state = api.submitPassengerLicense(context, auth, uri)
                                        preview = false
                                        selected = null
                                        message = "ส่งใบขับขี่สาธารณะให้ Admin ตรวจแล้ว"
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        message = e.message ?: "ส่งหลักฐานไม่สำเร็จ"
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
