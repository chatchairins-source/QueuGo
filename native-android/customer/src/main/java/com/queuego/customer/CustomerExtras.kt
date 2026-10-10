package com.queuego.customer

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QgSectionTitle
import com.queuego.shared.QueueGoNativeApi
import org.json.JSONObject

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
    val createdAt: String?
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
                "qg_support_tickets?select=id,category,details,status,order_id,admin_note,created_at" +
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
                        createdAt = r.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
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
        details: String
    ) {
        val body = JSONObject()
            .put("p_ticket_id", ticketId)
            .put("p_order_id", orderId ?: JSONObject.NULL)
            .put("p_category", category)
            .put("p_details", details.trim())
            .put("p_evidence_path", JSONObject.NULL)
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
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
            Spacer(Modifier.weight(1f))
            if (notifications.any { !it.read }) {
                OutlinedButton(onClick = onMarkAll, enabled = !busy) { Text("อ่านทั้งหมด") }
            }
        }
        Spacer(Modifier.height(12.dp))
        QgSectionTitle("แจ้งเตือน", "อัปเดตจากออเดอร์ ร้าน และ Rider")
        Spacer(Modifier.height(10.dp))
        if (notifications.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) {
                Text("ยังไม่มีการแจ้งเตือน", color = QgMuted)
            }
        } else {
            notifications.forEach { n ->
                QgCard(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onOpen(n) }
                ) {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                if (n.read) n.title else "●  " + n.title,
                                modifier = Modifier.weight(1f),
                                color = if (n.read) androidx.compose.ui.graphics.Color.Unspecified else QgRed,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        if (n.message.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(n.message, color = QgMuted)
                        }
                        if (!n.createdAt.isNullOrBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Text(n.createdAt!!, color = QgMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}


