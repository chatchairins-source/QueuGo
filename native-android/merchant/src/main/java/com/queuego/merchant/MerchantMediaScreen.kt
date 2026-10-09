package com.queuego.merchant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgIcon
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class MerchantMediaItem(
    val name: String,
    val url: String,
    val kind: String
)

internal suspend fun loadMerchantMedia(auth: NativeAuth): List<MerchantMediaItem> =
    withContext(Dispatchers.IO) {
        val endpoint = QueueGoNativeApi.BASE_URL + "/storage/v1/object/list/merchant-media"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + auth.session.accessToken)
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("prefix", auth.session.authUserId + "/")
                .put("limit", 100)
                .put("offset", 0)
                .put(
                    "sortBy",
                    JSONObject().put("column", "created_at").put("order", "desc")
                )
            connection.outputStream.use {
                it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(raw).optString("message")
                }.getOrNull().orEmpty().ifBlank { "โหลดคลังรูปไม่สำเร็จ" }
                error(message)
            }
            val rows = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val name = row.optString("name").trim()
                    if (name.isBlank() || name.startsWith(".")) continue
                    val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
                        .replace("+", "%20")
                    val publicUrl = QueueGoNativeApi.BASE_URL +
                        "/storage/v1/object/public/merchant-media/" +
                        auth.session.authUserId + "/" + encoded
                    add(
                        MerchantMediaItem(
                            name = name,
                            url = publicUrl,
                            kind = name.substringBefore('-').ifBlank { "image" }
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

@Composable
internal fun MerchantMediaScreen(
    auth: NativeAuth,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<MerchantMediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var uploading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        runCatching { loadMerchantMedia(auth) }
            .onSuccess { rows = it; message = null }
            .onFailure { message = it.message ?: "โหลดคลังรูปไม่สำเร็จ" }
        loading = false
    }

    LaunchedEffect(auth.user.id) { refresh() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !uploading) {
            uploading = true
            scope.launch {
                runCatching {
                    uploadMerchantImage(context, auth, uri, "gallery")
                }.onSuccess {
                    message = "เก็บรูปในคลังแล้ว"
                    refresh()
                }.onFailure {
                    message = "อัปโหลดไม่สำเร็จ: " + (it.message ?: "เกิดข้อผิดพลาด")
                }
                uploading = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.padding(top = 7.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("กลับ") }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(50.dp)
                    .background(Color(0xFFFFEDF0), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("gallery", Modifier.size(24.dp), QgRed)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("คลังรูปภาพร้าน", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("รูปหน้าร้าน โลโก้ และรูปสินค้า", color = QgMuted, fontSize = 10.sp)
            }
        }

        Button(
            onClick = { picker.launch("image/*") },
            enabled = !uploading,
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            QgIcon("gallery", Modifier.size(19.dp), Color.White)
            Spacer(Modifier.width(7.dp))
            if (uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("เพิ่มรูปจากเครื่อง", fontWeight = FontWeight.ExtraBold)
        }

        if (!message.isNullOrBlank()) {
            Text(
                message!!,
                color = if (message!!.contains("แล้ว")) Color(0xFF0A9660) else QgRed,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(
                Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            rows.isEmpty() -> Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                    .padding(vertical = 34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("ยังไม่มีรูปในคลัง", color = QgMuted)
            }

            else -> rows.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    row.forEach { item ->
                        Column(
                            Modifier
                                .weight(1f)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE8EAEE), RoundedCornerShape(12.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("QueueGo image", item.url))
                                    message = "คัดลอกลิงก์รูปแล้ว"
                                }
                                .padding(4.dp)
                        ) {
                            QgRemoteImage(
                                item.url,
                                Modifier.fillMaxWidth().aspectRatio(1f),
                                item.kind,
                                cornerRadius = 9.dp
                            )
                            Text(
                                item.kind,
                                color = QgMuted,
                                fontSize = 8.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
