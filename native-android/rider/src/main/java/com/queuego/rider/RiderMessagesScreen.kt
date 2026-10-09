package com.queuego.rider

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.time.Instant

data class RiderInboxRow(
    val job: RiderJob,
    val lastMessage: String?,
    val lastMessageAt: String?
)

private class RiderInboxApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun rows(
        auth: QueueGoAuth,
        active: RiderJob?,
        history: List<RiderHistoryOrder>
    ): List<RiderInboxRow> {
        val jobs = buildList {
            active?.let(::add)
            history.take(99).forEach { row ->
                add(
                    RiderJob(
                        id = row.id,
                        orderNumber = row.number,
                        status = "completed",
                        pickupAddress = row.pickupAddress,
                        pickupLat = null,
                        pickupLng = null,
                        deliveryAddress = row.deliveryAddress,
                        deliveryLat = null,
                        deliveryLng = null,
                        deliveryFee = row.deliveryFee,
                        marketOrderId = null,
                        arrivedShopAt = null,
                        arrivedCustomerAt = null
                    )
                )
            }
        }.distinctBy { it.id }.take(100)

        if (jobs.isEmpty()) return emptyList()
        val ids = jobs.joinToString(",") { it.id }
        val messages = http.array(
            http.get(
                "order_chat_messages?select=order_id,sender_id,message,created_at" +
                    "&order_id=in.(" + ids + ")" +
                    "&order=created_at.desc&limit=500",
                auth.session.accessToken
            )
        )
        val deliveries = http.array(
            http.get(
                "deliveries?select=order_id,delivered_at&order_id=in.(" + ids + ")",
                auth.session.accessToken
            )
        )

        val latest = linkedMapOf<String, Pair<String, String?>>()
        for (i in 0 until messages.length()) {
            val row = messages.optJSONObject(i) ?: continue
            val id = row.optString("order_id")
            if (id.isBlank() || latest.containsKey(id)) continue
            latest[id] = row.optString("message") to row.optString("created_at").takeIf { it.isNotBlank() }
        }
        val delivered = mutableMapOf<String, String>()
        for (i in 0 until deliveries.length()) {
            val row = deliveries.optJSONObject(i) ?: continue
            val id = row.optString("order_id")
            val at = row.optString("delivered_at")
            if (id.isNotBlank() && at.isNotBlank()) delivered[id] = at
        }

        val now = System.currentTimeMillis()
        return jobs.mapNotNull { job ->
            val available = if (job.status != "completed") {
                true
            } else {
                val at = delivered[job.id]?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                at != null && now >= at && now < at + 30 * 60_000L
            }
            if (!available) return@mapNotNull null
            val last = latest[job.id]
            RiderInboxRow(job, last?.first, last?.second)
        }.sortedByDescending { row ->
            row.lastMessageAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: if (row.job.status == "completed") 0L else Long.MAX_VALUE
        }
    }
}

@Composable
fun RiderMessagesScreen(
    auth: QueueGoAuth,
    activeJob: RiderJob?,
    history: List<RiderHistoryOrder>,
    onOpenChat: (RiderJob) -> Unit,
    modifier: Modifier = Modifier
) {
    val api = remember { RiderInboxApi() }
    var rows by remember { mutableStateOf<List<RiderInboxRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(auth.session.accessToken, activeJob?.id, history) {
        while (true) {
            runCatching { api.rows(auth, activeJob, history) }
                .onSuccess {
                    rows = it
                    error = null
                    loading = false
                }
                .onFailure {
                    error = it.message ?: "โหลดข้อความไม่สำเร็จ"
                    loading = false
                }
            delay(15_000)
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("ข้อความ", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        Text("แชทออเดอร์ที่กำลังส่งและหลังส่งสำเร็จไม่เกิน 30 นาที", color = QgMuted)
        Spacer(Modifier.height(12.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (!error.isNullOrBlank()) {
            Text(error!!, color = QgRed)
        } else if (rows.isEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text("ยังไม่มีการสนทนา", color = QgMuted, modifier = Modifier.padding(18.dp))
            }
        } else {
            rows.forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Card(
                    Modifier.fillMaxWidth().clickable { onOpenChat(row.job) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("ออเดอร์ " + row.job.numberLabel, fontWeight = FontWeight.Black)
                            Text(
                                row.lastMessage?.let {
                                    if (it.startsWith("__IMG__")) "รูปภาพ" else it.take(85)
                                } ?: "เปิดแชตระหว่างจัดส่ง",
                                color = QgMuted,
                                maxLines = 2
                            )
                        }
                        Text("›", color = QgRed, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Spacer(Modifier.height(86.dp))
    }
}
