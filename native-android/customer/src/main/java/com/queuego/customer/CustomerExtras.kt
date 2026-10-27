package com.queuego.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QgSectionTitle
import com.queuego.shared.QueueGoNativeApi
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.ThaiBuddhistChronology
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CustomerNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: String?,
    val referenceId: String?,
    val read: Boolean,
    val createdAt: String?
)

data class CustomerSupportTicket(
    val id: String,
    val category: String,
    val details: String,
    val status: String,
    val orderId: String?,
    val adminNote: String?,
    val createdAt: String?,
    val evidencePath: String?
)

class CustomerExtrasApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun notifications(auth: NativeAuth): List<CustomerNotification> {
        val rows = http.array(
            http.get(
                "notifications?select=id,title,message,type,reference_id,is_read,created_at" +
                    "&user_id=eq." + http.enc(auth.user.id) +
                    "&order=created_at.desc&limit=100",
                auth.session.accessToken
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(
                    CustomerNotification(
                        id = r.optString("id"),
                        title = r.optString("title").ifBlank { "แจ้งเตือน" },
                        message = r.optString("message"),
                        type = r.optString("type").takeIf { it.isNotBlank() && it != "null" },
                        referenceId = r.optString("reference_id").takeIf { it.isNotBlank() && it != "null" },
                        read = r.optBoolean("is_read", false),
                        createdAt = r.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    suspend fun markRead(auth: NativeAuth, id: String) {
        http.patch(
            "notifications?id=eq." + http.enc(id) + "&user_id=eq." + http.enc(auth.user.id),
            auth.session.accessToken,
            JSONObject().put("is_read", true)
        )
    }

    suspend fun supportTickets(auth: NativeAuth): List<CustomerSupportTicket> {
        val rows = http.array(
            http.get(
                "qg_support_tickets?select=id,category,details,status,order_id,admin_note,created_at,evidence_path" +
                    "&user_id=eq." + http.enc(auth.user.id) +
                    "&order=created_at.desc&limit=30",
                auth.session.accessToken
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(
                    CustomerSupportTicket(
                        id = r.optString("id"),
                        category = r.optString("category"),
                        details = r.optString("details"),
                        status = r.optString("status"),
                        orderId = r.optString("order_id").takeIf { it.isNotBlank() && it != "null" },
                        adminNote = r.optString("admin_note").takeIf { it.isNotBlank() && it != "null" },
                        createdAt = r.optString("created_at").takeIf { it.isNotBlank() && it != "null" },
                        evidencePath = r.optString("evidence_path").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    suspend fun createSupportTicket(
        auth: NativeAuth,
        ticketId: String,
        orderId: String?,
        category: String,
        details: String,
        evidencePath: String? = null
    ) {
        val existing = http.array(
            http.get(
                "qg_support_tickets?select=id&user_id=eq." + http.enc(auth.user.id) +
                    "&id=eq." + http.enc(ticketId) + "&limit=1",
                auth.session.accessToken
            )
        )
        if (existing.length() > 0) return
        val body = JSONObject()
            .put("p_ticket_id", ticketId)
            .put("p_order_id", orderId ?: JSONObject.NULL)
            .put("p_category", category)
            .put("p_details", details.trim())
            .put("p_evidence_path", evidencePath ?: JSONObject.NULL)
        val raw = http.rpc("qg_create_ticket", auth.session.accessToken, body)
        val confirmed = when (raw) {
            is String -> raw
            is JSONObject -> raw.optString("id").ifBlank { raw.optString("qg_create_ticket") }
            else -> raw.toString().trim('"')
        }
        if (confirmed != ticketId) error("ระบบยังไม่ยืนยันเลขคำร้อง")
    }

    suspend fun markAllRead(auth: NativeAuth) {
        http.patch(
            "notifications?user_id=eq." + http.enc(auth.user.id) + "&is_read=eq.false",
            auth.session.accessToken,
            JSONObject().put("is_read", true)
        )
    }
}

@Composable
fun CustomerNotificationsScreen(
    notifications: List<CustomerNotification>,
    busy: Boolean,
    onBack: () -> Unit,
    onMarkAll: () -> Unit,
    onOpen: (CustomerNotification) -> Unit
) {
    // Web preserves the unread badge until the user explicitly marks rows read.
    // Never invoke onMarkAll merely because this screen is opened.
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp)
                    .background(Color.White, RoundedCornerShape(15.dp))
                    .border(1.dp, Color(0xFFECEEF2), RoundedCornerShape(15.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = QgRed, fontSize = 32.sp)
            }
            Spacer(Modifier.size(12.dp))
            Text("แจ้งเตือน", modifier = Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onMarkAll, enabled = !busy) {
            Text("อ่านทั้งหมด", color = QgRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        if (notifications.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) {
                Text("ยังไม่มีการแจ้งเตือน", color = QgMuted)
            }
        } else {
            notifications.forEach { n ->
                QgCard(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable { onOpen(n) }
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            com.queuego.shared.nativeOrderNumberInText(n.title),
                            color = Color(0xFF202226),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (n.message.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                com.queuego.shared.nativeOrderNumberInText(n.message),
                                color = Color(0xFF30343B),
                                fontSize = 16.sp
                            )
                        }
                        if (!n.createdAt.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(customerThaiDateTime(n.createdAt!!), color = QgMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}



internal fun customerThaiDateTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss", Locale.forLanguageTag("th-TH"))
        .withChronology(ThaiBuddhistChronology.INSTANCE)
        .withZone(ZoneId.of("Asia/Bangkok"))
        .format(Instant.parse(value))
}.getOrDefault(value)
