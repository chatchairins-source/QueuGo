package com.queuego.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgSectionTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun posHistoryAmount(value: Double?): String =
    if (value == null || !value.isFinite()) "—"
    else "฿" + String.format(Locale.US, "%,.2f", value)

private fun posHistoryTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return runCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Asia/Bangkok"))
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("th-TH")))
    }.getOrElse { value.take(16).replace('T', ' ') }
}

@Composable
internal fun MerchantPosShiftHistoryScreen(
    auth: NativeAuth,
    api: MerchantPosApi,
    canCashier: Boolean
) {
    val scope = rememberCoroutineScope()
    var loading by remember(auth.session.authUserId) { mutableStateOf(true) }
    var history by remember(auth.session.authUserId) {
        mutableStateOf<List<PosShiftHistory>>(emptyList())
    }
    var currentShift by remember(auth.session.authUserId) {
        mutableStateOf<PosCashShift?>(null)
    }
    var error by remember(auth.session.authUserId) { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        error = null
        try {
            history = api.shiftHistory(auth, 20)
            currentShift = if (canCashier) api.currentCashShift(auth) else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "โหลดประวัติกะรับเงินไม่สำเร็จ"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(auth.session.authUserId, canCashier) { refresh() }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QgSectionTitle("กะรับเงิน", "ประวัติกะจากระบบ POS · ไม่ใช่ข้อมูลทดลอง")
        if (canCashier) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (currentShift?.open == true) "กำลังเปิดกะ"
                        else if (loading) "กำลังตรวจสถานะกะ"
                        else "ยังไม่มีการเปิดกะ",
                        color = if (currentShift?.open == true) Color(0xFF15803D) else QgRed,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    if (currentShift?.open == true) {
                        Text(
                            "เงินสดคาดหมาย " + posHistoryAmount(currentShift?.expectedCash),
                            fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                    }
                    Text("เปิดหรือปิดกะได้จากแท็บคิดเงิน",
                        color = QgMuted, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ประวัติกะล่าสุด", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            OutlinedButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                Text("โหลดใหม่")
            }
        }
        if (error != null) {
            Surface(
                color = Color(0xFFFFE8ED),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(error ?: "", Modifier.padding(12.dp), color = QgRed)
            }
        }
        if (loading) {
            Text("กำลังอ่านประวัติกะจากเซิร์ฟเวอร์", color = QgMuted)
        } else if (history.isEmpty() && error == null) {
            Text("ยังไม่มีประวัติกะรับเงิน", color = QgMuted)
        } else {
            history.forEach { entry ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 1.dp
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.cashierName, fontWeight = FontWeight.ExtraBold)
                            Text(
                                if (entry.closedAt == null) "เปิดอยู่" else "ปิดแล้ว",
                                color = if (entry.closedAt == null) Color(0xFF15803D) else QgMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text("เริ่ม " + posHistoryTimestamp(entry.openedAt),
                            color = QgMuted, fontSize = 12.sp)
                        if (entry.closedAt != null) {
                            Text("ปิด " + posHistoryTimestamp(entry.closedAt),
                                color = QgMuted, fontSize = 12.sp)
                        }
                        Text("เงินตั้งต้น " + posHistoryAmount(entry.openingFloat))
                        if (entry.closedAt != null) {
                            Text("เงินสดคาดหมาย " + posHistoryAmount(entry.expectedCash))
                            Text("นับจริง " + posHistoryAmount(entry.countedCash))
                            Text("ผลต่าง " + posHistoryAmount(entry.variance),
                                fontWeight = FontWeight.Bold,
                                color = if (entry.variance != null && entry.variance != 0.0) QgRed
                                else Color(0xFF15803D))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}
