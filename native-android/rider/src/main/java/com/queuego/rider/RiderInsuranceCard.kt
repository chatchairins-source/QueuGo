package com.queuego.rider

import android.content.Intent
import android.net.Uri
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
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException

internal fun riderInsuranceTypeLabel(type: String): String = when (type) {
    "personal_accident" -> "ประกันอุบัติเหตุ"
    "income_protection" -> "คุ้มครองรายได้"
    "vehicle" -> "ประกันรถ"
    else -> "ความคุ้มครองอื่น"
}

internal fun riderInsuranceStatusLabel(status: String): String = when (status) {
    "active" -> "คุ้มครองอยู่"
    "upcoming" -> "ยังไม่เริ่มคุ้มครอง"
    "expired" -> "หมดอายุแล้ว"
    else -> "ไม่ทราบสถานะ"
}

@Composable
internal fun RiderInsuranceCard(
    auth: QueueGoAuth,
    api: QueueGoApi,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var policies by remember(auth.user.id) { mutableStateOf<List<RiderInsurancePolicy>>(emptyList()) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        try {
            policies = api.insurancePolicies(auth)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            policies = emptyList()
        } finally {
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
            Text("Insurance Center", fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(
                "แสดงเฉพาะกรมธรรม์หรือความคุ้มครองจริงที่ QueueGo บันทึกไว้",
                color = QgMuted,
                fontSize = 8.5.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            when {
                !loaded -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 9.dp))
                policies.isEmpty() -> Text(
                    "ยังไม่มีกรมธรรม์ที่บันทึกใน QueueGo",
                    color = QgMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 9.dp)
                )
                else -> {
                    Spacer(Modifier.height(7.dp))
                    policies.forEachIndexed { index, policy ->
                        if (index > 0) HorizontalDivider(color = Color(0xFFF0EAEC))
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        policy.productName?.takeIf { it.isNotBlank() } ?: riderInsuranceTypeLabel(policy.coverageType),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(policy.providerName, color = QgMuted, fontSize = 8.5.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                Text(
                                    riderInsuranceStatusLabel(policy.status),
                                    color = when (policy.status) {
                                        "active" -> Color(0xFF12845E)
                                        "expired" -> QgRed
                                        else -> Color(0xFF9A6B00)
                                    },
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            policy.policyNumber?.let {
                                Text("กรมธรรม์ $it", color = QgMuted, fontSize = 8.5.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            Text(
                                "คุ้มครอง ${policy.startsOn} – ${policy.expiresOn}",
                                color = QgMuted,
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            policy.coverageSummary?.let {
                                Text(it, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 5.dp))
                            }
                            if (!policy.supportPhone.isNullOrBlank() || !policy.supportUrl.isNullOrBlank()) {
                                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    policy.supportPhone?.let { phone ->
                                        OutlinedButton(
                                            onClick = {
                                                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("โทรประกัน", fontSize = 9.sp) }
                                    }
                                    policy.supportUrl?.let { url ->
                                        OutlinedButton(
                                            onClick = {
                                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("รายละเอียด", fontSize = 9.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
