package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QueueGoNativeApi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal data class RiderNotificationRow(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val referenceId: String?,
    val isRead: Boolean,
    val createdAt: String?
)

internal fun riderNotificationsFromRows(rows: JSONArray): List<RiderNotificationRow> =
    buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("id").trim()
            val title = row.optString("title").trim()
            val message = row.optString("message").trim()
            if (id.isBlank() || title.isBlank() || message.isBlank()) continue
            val reference = row.optString("reference_id").trim()
                .takeIf { it.isNotBlank() && it != "null" }
            add(
                RiderNotificationRow(
                    id = id,
                    title = title,
                    message = message,
                    type = row.optString("type").trim(),
                    referenceId = reference,
                    isRead = row.optBoolean("is_read", false),
                    createdAt = row.optString("created_at").trim()
                        .takeIf { it.isNotBlank() && it != "null" }
                )
            )
        }
    }

internal fun riderNotificationCanOpenOrder(row: RiderNotificationRow): Boolean =
    row.referenceId != null &&
        row.type in setOf("order", "rider_offer") &&
        runCatching { java.util.UUID.fromString(row.referenceId) }.isSuccess

internal fun riderNotificationTimeLabel(raw: String?): String? {
    val instant = raw?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    return DateTimeFormatter
        .ofPattern("d MMM · HH:mm", Locale("th", "TH"))
        .withZone(ZoneId.of("Asia/Bangkok"))
        .format(instant)
}

internal class RiderNotificationCenterApi(
    private val http: QueueGoNativeApi = QueueGoNativeApi()
) {
    suspend fun rows(auth: QueueGoAuth): List<RiderNotificationRow> {
        val raw = http.array(
            http.get(
                "notifications?select=id,title,message,type,reference_id,is_read,created_at" +
                    "&order=created_at.desc&limit=100",
                auth.session.accessToken
            )
        )
        return riderNotificationsFromRows(raw)
    }

    suspend fun unreadCount(auth: QueueGoAuth): Int {
        val rows = http.array(
            http.get(
                "notifications?select=id&is_read=eq.false&order=created_at.desc&limit=100",
                auth.session.accessToken
            )
        )
        return rows.length().coerceAtMost(99)
    }

    suspend fun markRead(auth: QueueGoAuth, notificationId: String) {
        http.patch(
            "notifications?id=eq." + http.enc(notificationId),
            auth.session.accessToken,
            JSONObject().put("is_read", true)
        )
    }

    suspend fun markAllRead(auth: QueueGoAuth) {
        http.patch(
            "notifications?is_read=eq.false",
            auth.session.accessToken,
            JSONObject().put("is_read", true)
        )
    }
}

@Composable
internal fun RiderNotificationCenter(
    auth: QueueGoAuth,
    onUnreadChanged: (Int) -> Unit,
    onOpenOrderReference: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val api = remember { RiderNotificationCenterApi() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var rows by remember(auth.user.id, auth.session.sessionId) {
        mutableStateOf<List<RiderNotificationRow>>(emptyList())
    }
    var loading by remember(auth.user.id, auth.session.sessionId) { mutableStateOf(true) }
    var error by remember(auth.user.id, auth.session.sessionId) { mutableStateOf<String?>(null) }
    var retry by remember(auth.user.id, auth.session.sessionId) { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    fun applyRows(next: List<RiderNotificationRow>) {
        rows = next
        onUnreadChanged(next.count { !it.isRead })
    }

    LaunchedEffect(
        auth.user.id,
        auth.session.sessionId,
        auth.session.accessToken,
        lifecycle,
        retry
    ) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    val next = api.rows(auth)
                    currentCoroutineContext().ensureActive()
                    applyRows(next)
                    error = null
                    loading = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    error = "โหลดแจ้งเตือนไม่สำเร็จ"
                    loading = false
                }
                delay(15_000L)
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        if (rows.any { !it.isRead }) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ยังไม่ได้อ่าน ${rows.count { !it.isRead }} รายการ",
                    color = QgMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        if (busy) return@TextButton
                        busy = true
                        scope.launch {
                            try {
                                api.markAllRead(auth)
                                applyRows(rows.map { it.copy(isRead = true) })
                            } catch (_: Exception) {
                                error = "ทำเครื่องหมายอ่านทั้งหมดไม่สำเร็จ"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy
                ) {
                    Text("อ่านทั้งหมด", color = QgRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (loading || !error.isNullOrBlank() || rows.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (loading) "กำลังโหลดแจ้งเตือน..." else error ?: "ยังไม่มีแจ้งเตือน",
                    color = Color(0xFF8A8387)
                )
                if (!error.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            loading = true
                            error = null
                            retry++
                        }
                    ) { Text("ลองใหม่") }
                }
            }
        } else {
            rows.forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(7.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                if (!row.isRead) {
                                    try {
                                        api.markRead(auth, row.id)
                                        applyRows(
                                            rows.map {
                                                if (it.id == row.id) it.copy(isRead = true) else it
                                            }
                                        )
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        error = "ทำเครื่องหมายอ่านไม่สำเร็จ"
                                    }
                                }
                                if (riderNotificationCanOpenOrder(row)) {
                                    onOpenOrderReference(row.referenceId!!)
                                }
                            }
                        },
                    shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(
                        1.dp,
                        if (row.isRead) Color(0xFFEEE6E8) else Color(0xFFFFCCD4)
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (row.isRead) Color.White else Color(0xFFFFF7F8)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .size(9.dp)
                                .background(
                                    if (row.isRead) Color(0xFFD9D4D6) else QgRed,
                                    CircleShape
                                )
                        )
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    row.title,
                                    fontWeight = if (row.isRead) FontWeight.Bold else FontWeight.Black,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                riderNotificationTimeLabel(row.createdAt)?.let {
                                    Text(it, color = QgMuted, fontSize = 8.sp)
                                }
                            }
                            Text(
                                row.message,
                                color = Color(0xFF756E72),
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (riderNotificationCanOpenOrder(row)) {
                                Text(
                                    "แตะเพื่อเปิดงาน",
                                    color = QgRed,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
