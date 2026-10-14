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

internal fun riderBenefitCategoryLabel(category: String): String = when (category) {
    "fuel" -> "น้ำมัน / พลังงาน"
    "maintenance" -> "ดูแลรถ"
    "food" -> "อาหารและเครื่องดื่ม"
    "telecom" -> "โทรศัพท์ / อินเทอร์เน็ต"
    "health" -> "สุขภาพ"
    else -> "สิทธิประโยชน์อื่น"
}

@Composable
internal fun RiderBenefitsCard(
    auth: QueueGoAuth,
    api: QueueGoApi,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var benefits by remember(auth.user.id) { mutableStateOf<List<RiderBenefit>>(emptyList()) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        try {
            benefits = api.benefits(auth)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            benefits = emptyList()
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
            Text("Rider Benefits", fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(
                "แสดงเฉพาะสิทธิประโยชน์จริงที่ QueueGo เปิดใช้งานในช่วงเวลาปัจจุบัน",
                color = QgMuted,
                fontSize = 8.5.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            when {
                !loaded -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 9.dp))
                benefits.isEmpty() -> Text(
                    "ยังไม่มีสิทธิประโยชน์ที่เปิดใช้งาน",
                    color = QgMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 9.dp)
                )
                else -> {
                    Spacer(Modifier.height(7.dp))
                    benefits.forEachIndexed { index, benefit ->
                        if (index > 0) HorizontalDivider(color = Color(0xFFF0EAEC))
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(benefit.title, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(
                                benefit.providerName + " · " + riderBenefitCategoryLabel(benefit.category),
                                color = QgMuted,
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            benefit.description?.let {
                                Text(it, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 5.dp))
                            }
                            val period = buildString {
                                append("เริ่ม ")
                                append(benefit.startsOn)
                                benefit.expiresOn?.let {
                                    append(" · ถึง ")
                                    append(it)
                                }
                            }
                            Text(period, color = QgMuted, fontSize = 8.5.sp, modifier = Modifier.padding(top = 4.dp))

                            if (!benefit.actionUrl.isNullOrBlank() || !benefit.supportPhone.isNullOrBlank()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 7.dp),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    benefit.actionUrl?.let { url ->
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                benefit.actionLabel?.takeIf { it.isNotBlank() } ?: "ดูสิทธิ์",
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    benefit.supportPhone?.let { phone ->
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("ติดต่อ", fontSize = 9.sp) }
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
