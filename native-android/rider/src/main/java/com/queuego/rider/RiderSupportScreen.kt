package com.queuego.rider

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    val adminNote: String?,
    val evidencePath: String?
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
                        adminNote = row.optString("admin_note").takeIf { it.isNotBlank() },
                        evidencePath = row.optString("evidence_path").takeIf { it.isNotBlank() }
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

    suspend fun signedEvidenceUrl(auth: QueueGoAuth, path: String): String = withContext(Dispatchers.IO) {
        require(path.isNotBlank()) { "ไม่พบรูปหลักฐาน" }
        val url = URL(
            QueueGoNativeApi.BASE_URL + "/storage/v1/object/sign/qg-evidence/" + path
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + auth.session.accessToken)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(JSONObject().put("expiresIn", 300).toString().toByteArray(Charsets.UTF_8))
            }
            if (connection.responseCode !in 200..299) error("เปิดหลักฐานไม่สำเร็จ")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val signed = JSONObject(body).optString("signedURL")
                .ifBlank { JSONObject(body).optString("signedUrl") }
            require(signed.isNotBlank()) { "เปิดหลักฐานไม่สำเร็จ" }
            if (signed.startsWith("http")) signed
            else QueueGoNativeApi.BASE_URL + "/storage/v1" + signed
        } finally {
            connection.disconnect()
        }
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

internal fun riderSupportInitialCategory(requested: String?): String =
    requested?.takeIf { candidate ->
        riderSupportCategories.any { it.first == candidate }
    } ?: riderSupportCategories.first().first

@Composable
fun RiderSupportScreen(
    auth: QueueGoAuth,
    activeJob: RiderJob?,
    history: List<RiderHistoryOrder>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialOrderId: String? = null,
    initialOrderLabel: String? = null,
    initialCategory: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { RiderSupportApi() }
    var tickets by remember { mutableStateOf<List<RiderSupportTicket>?>(null) }
    var ticketLoadError by remember { mutableStateOf<String?>(null) }
    var selectedOrder by remember(initialOrderId) { mutableStateOf(initialOrderId) }
    var category by remember(initialCategory) { mutableStateOf(riderSupportInitialCategory(initialCategory)) }
    var details by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var openingEvidenceId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var orderMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        evidence = it
    }

    suspend fun refresh() {
        tickets = api.tickets(auth)
        ticketLoadError = null
    }

    LaunchedEffect(auth.session.accessToken) {
        try { refresh() } catch (e: CancellationException) { throw e } catch (e: Exception) {
            ticketLoadError = e.message ?: "โหลดเรื่องที่แจ้งไม่สำเร็จ"
        }
    }

    LaunchedEffect(message) { if (message != null) { delay(4_000); message = null } }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .9f).dp
    val orderOptions = buildList {
        if (!initialOrderId.isNullOrBlank() && !initialOrderLabel.isNullOrBlank()) {
            add(initialOrderId to initialOrderLabel)
        }
        activeJob?.let { add(it.id to it.numberLabel) }
        history.take(49).forEach { add(it.id to it.number) }
    }.distinctBy { it.first }

    Column(modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ติดต่อฝ่ายดูแล", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).background(Color(0xFFFFF0F3), RoundedCornerShape(99.dp))) {
                Text("✕", color = QgRed)
            }
        }
        Text("แจ้งปัญหาและติดตามสถานะจากระบบ", color = QgMuted, fontSize = 12.sp)
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, color = QgRed)
        }

        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ออเดอร์ที่เกี่ยวข้อง (ถ้ามี)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { orderMenu = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFFE6DDE0))) {
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

                Text("ประเภทปัญหา", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFFE6DDE0))) {
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

                Text("รายละเอียด", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                BasicTextField(value = details, onValueChange = { if (it.length <= 2000) details = it },
                    enabled = !busy, minLines = 2, textStyle = TextStyle(fontSize = 16.sp, color = Color(0xFF17171B)),
                    cursorBrush = SolidColor(QgRed), modifier = Modifier.fillMaxWidth()
                        .border(1.dp, Color(0xFFE6DDE0), RoundedCornerShape(13.dp)).padding(10.dp),
                    decorationBox = { inner -> Box {
                        if (details.isEmpty()) Text("อธิบายปัญหาอย่างน้อย 10 ตัวอักษร", fontSize = 16.sp, color = QgMuted)
                        inner()
                    } })
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
                                category = riderSupportCategories.first().first
                                message = "ส่งเรื่องให้ฝ่ายดูแลแล้ว"
                            }.onFailure {
                                message = it.message ?: "ส่งเรื่องไม่สำเร็จ"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && details.trim().length >= 10,
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                ) { Text(if (busy) "กำลังส่ง..." else "ส่งเรื่อง", fontWeight = FontWeight.Black) }
        }

        Spacer(Modifier.height(12.dp))
        Text("เรื่องที่แจ้งไว้", fontWeight = FontWeight.Black)
        Spacer(Modifier.height(7.dp))
        if (ticketLoadError != null) {
            Text(ticketLoadError!!, color = QgRed, fontSize = 12.sp)
        } else if (tickets == null) {
            Text("กำลังโหลด…", color = QgMuted, fontSize = 12.sp)
        } else if (tickets.orEmpty().isEmpty()) {
            Text("ยังไม่มีเรื่องที่แจ้ง", color = QgMuted, fontSize = 12.sp)
        } else {
            tickets.orEmpty().forEach { ticket ->
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
                        ticket.evidencePath?.let { path ->
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    if (openingEvidenceId != null) return@OutlinedButton
                                    openingEvidenceId = ticket.id
                                    message = null
                                    scope.launch {
                                        runCatching {
                                            api.signedEvidenceUrl(auth, path)
                                        }.onSuccess { signedUrl ->
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(signedUrl)))
                                            }.onFailure {
                                                message = "ไม่สามารถเปิดรูปหลักฐานได้"
                                            }
                                        }.onFailure {
                                            message = it.message ?: "เปิดหลักฐานไม่สำเร็จ"
                                        }
                                        openingEvidenceId = null
                                    }
                                },
                                enabled = openingEvidenceId == null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (openingEvidenceId == ticket.id) "กำลังเปิด..." else "ดูหลักฐาน")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
