package com.queuego.rider

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class RiderSupportTicket(
    val id: String,
    val category: String,
    val details: String,
    val status: String,
    val orderId: String?,
    val createdAt: String?,
    val adminNote: String?
)

private class RiderSupportApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun tickets(auth: QueueGoAuth): List<RiderSupportTicket> {
        val rows = http.array(
            http.get(
                "qg_support_tickets?select=id,category,details,status,order_id,created_at,admin_note,evidence_path" +
                    "&order=created_at.desc&limit=50",
                auth.session.accessToken
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(
                    RiderSupportTicket(
                        id = row.optString("id"),
                        category = row.optString("category"),
                        details = row.optString("details"),
                        status = row.optString("status"),
                        orderId = row.optString("order_id").takeIf { it.isNotBlank() },
                        createdAt = row.optString("created_at").takeIf { it.isNotBlank() },
                        adminNote = row.optString("admin_note").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    suspend fun uploadEvidence(
        context: Context,
        auth: QueueGoAuth,
        uri: Uri
    ): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val type = resolver.getType(uri).orEmpty()
        require(type in setOf("image/jpeg", "image/png", "image/webp")) {
            "หลักฐานต้องเป็น JPG, PNG หรือ WebP"
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("อ่านรูปหลักฐานไม่สำเร็จ")
        require(bytes.size <= 5 * 1024 * 1024) { "รูปหลักฐานต้องไม่เกิน 5 MB" }
        val ext = when (type) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val objectPath = auth.session.authUserId + "/" + UUID.randomUUID() + "." + ext
        val url = URL(
            QueueGoNativeApi.BASE_URL + "/storage/v1/object/qg-evidence/" +
                objectPath
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + auth.session.accessToken)
            connection.setRequestProperty("Content-Type", type)
            connection.setRequestProperty("x-upsert", "false")
            connection.outputStream.use { it.write(bytes) }
            if (connection.responseCode !in 200..299) error("อัปโหลดหลักฐานไม่สำเร็จ")
        } finally {
            connection.disconnect()
        }
        objectPath
    }

    suspend fun create(
        auth: QueueGoAuth,
        orderId: String?,
        category: String,
        details: String,
        evidencePath: String?
    ) {
        val clean = details.trim()
        require(clean.length in 10..2000) { "รายละเอียดต้องมี 10–2000 ตัวอักษร" }
        http.rpc(
            "qg_create_ticket",
            auth.session.accessToken,
            JSONObject()
                .put("p_ticket_id", UUID.randomUUID().toString())
                .put("p_order_id", orderId ?: JSONObject.NULL)
                .put("p_category", category)
                .put("p_details", clean)
                .put("p_evidence_path", evidencePath ?: JSONObject.NULL)
        )
    }
}

private val riderSupportCategories = listOf(
    "missing_item" to "สินค้าไม่ครบ",
    "wrong_item" to "สินค้าไม่ตรง",
    "damaged_food" to "อาหารเสียหาย",
    "rider" to "ปัญหาไรเดอร์",
    "merchant" to "ปัญหาร้านค้า",
    "payment" to "การชำระเงิน",
    "cash" to "เงินสด",
    "gp" to "GP",
    "other" to "อื่น ๆ"
)

@Composable
fun RiderSupportScreen(
    auth: QueueGoAuth,
    activeJob: RiderJob?,
    history: List<RiderHistoryOrder>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { RiderSupportApi() }
    var tickets by remember { mutableStateOf<List<RiderSupportTicket>>(emptyList()) }
    var selectedOrder by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf("other") }
    var details by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var orderMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        evidence = it
    }

    suspend fun refresh() {
        tickets = api.tickets(auth)
    }

    LaunchedEffect(auth.session.accessToken) {
        runCatching { refresh() }.onFailure { message = it.message ?: "โหลดเรื่องที่แจ้งไม่สำเร็จ" }
    }

    val orderOptions = buildList {
        activeJob?.let { add(it.id to it.numberLabel) }
        history.take(49).forEach { add(it.id to it.number) }
    }.distinctBy { it.first }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("ติดต่อฝ่ายดูแล", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("แจ้งปัญหาและติดตามสถานะจากระบบ", color = QgMuted)
            }
        }
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, color = QgRed)
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(12.dp)) {
                Text("แจ้งปัญหา", fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { orderMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(orderOptions.find { it.first == selectedOrder }?.second ?: "ไม่ระบุออเดอร์")
                    }
                    DropdownMenu(orderMenu, { orderMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("ไม่ระบุออเดอร์") },
                            onClick = { selectedOrder = null; orderMenu = false }
                        )
                        orderOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.second) },
                                onClick = { selectedOrder = option.first; orderMenu = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(riderSupportCategories.find { it.first == category }?.second ?: "อื่น ๆ")
                    }
                    DropdownMenu(categoryMenu, { categoryMenu = false }) {
                        riderSupportCategories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.second) },
                                onClick = { category = option.first; categoryMenu = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 2000) details = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("รายละเอียด") },
                    minLines = 4
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (evidence == null) "แนบรูปหลักฐาน (ถ้ามี)" else "เลือกรูปหลักฐานแล้ว")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        message = null
                        scope.launch {
                            runCatching {
                                val path = evidence?.let { api.uploadEvidence(context, auth, it) }
                                api.create(auth, selectedOrder, category, details, path)
                                refresh()
                            }.onSuccess {
                                details = ""
                                evidence = null
                                selectedOrder = null
                                message = "ส่งเรื่องให้ฝ่ายดูแลแล้ว"
                            }.onFailure {
                                message = it.message ?: "ส่งเรื่องไม่สำเร็จ"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && details.trim().length >= 10,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "กำลังส่ง..." else "ส่งเรื่อง") }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("เรื่องที่แจ้งไว้", fontWeight = FontWeight.Black)
        Spacer(Modifier.height(7.dp))
        if (tickets.isEmpty()) {
            Text("ยังไม่มีเรื่องที่แจ้ง", color = QgMuted)
        } else {
            tickets.forEach { ticket ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                riderSupportCategories.find { it.first == ticket.category }?.second ?: ticket.category,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )
                            Text(ticket.status, color = QgRed, fontWeight = FontWeight.Bold)
                        }
                        Text(ticket.details, color = QgMuted)
                        ticket.adminNote?.let {
                            Spacer(Modifier.height(4.dp))
                            Text("ฝ่ายดูแล: " + it, fontWeight = FontWeight.Bold)
                        }
                        ticket.createdAt?.let { Text(it, color = QgMuted, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        Spacer(Modifier.height(86.dp))
    }
}
