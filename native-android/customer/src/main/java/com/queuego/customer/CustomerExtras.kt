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
import androidx.compose.foundation.layout.weight
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

    suspend fun markAllRead(auth: NativeAuth) {
        http.patch(
            "notifications?user_id=eq." + http.enc(auth.user.id) + "&is_read=eq.false",
            auth.session.accessToken,
            JSONObject().put("is_read", true)
        )
    }
}

@Composable
fun CustomerSearchScreen(
    shops: List<CustomerShop>,
    onOpenShop: (CustomerShop) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val filtered = if (q.isBlank()) shops else shops.filter { shop ->
        listOf(shop.name, shop.category, shop.address.orEmpty())
            .joinToString(" ")
            .lowercase()
            .contains(q)
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        QgSectionTitle("ค้นหา", "ค้นหาร้าน อาหาร เครื่องดื่ม ของชำ และช้อปปิ้ง")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("ค้นหาร้านหรือหมวดหมู่") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) {
                Text("ไม่พบร้านที่ตรงกับคำค้นหา", color = QgMuted)
            }
        } else {
            filtered.forEach { shop ->
                QgCard(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onOpenShop(shop) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QgRemoteImage(shop.logo ?: shop.cover, Modifier.size(70.dp), shop.name)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(shop.name, fontWeight = FontWeight.ExtraBold)
                            Text(customerSearchCategory(shop.category), color = QgMuted)
                            if (!shop.address.isNullOrBlank()) {
                                Text(shop.address!!, color = QgMuted, maxLines = 1)
                            }
                            Text(
                                if (shop.open) "เปิดอยู่" else "ปิดอยู่",
                                color = if (shop.open) com.queuego.shared.QgGreen else QgMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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

private fun customerSearchCategory(category: String): String = when (category.lowercase()) {
    "food" -> "อาหาร"
    "cafe", "drink" -> "เครื่องดื่ม"
    "grocery" -> "ร้านขายของชำ"
    "market", "fresh", "fresh_market" -> "ตลาดสด"
    "laundry" -> "ฝากซัก"
    "shopping" -> "ช้อปปิ้ง"
    else -> "ร้านค้า"
}
