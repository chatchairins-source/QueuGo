package com.queuego.shared

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

private const val ACCOUNT_DELETE_RESPONSE_MAX_BYTES = 256 * 1024
private const val QUEUEGO_PRIVACY_POLICY_URL =
    "https://chatchairins-source.github.io/QueuGo/docs/privacy.html"

private fun InputStream.readAccountDeleteResponseBounded(): String {
    val output = ByteArrayOutputStream(16 * 1024)
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= ACCOUNT_DELETE_RESPONSE_MAX_BYTES) {
            "ผลลัพธ์การลบบัญชีมีขนาดใหญ่เกินกำหนด"
        }
        output.write(buffer, 0, count)
    }
    return output.toString(StandardCharsets.UTF_8.name())
}

class QueueGoAccountDeletionApi {
    suspend fun delete(accessToken: String) = withContext(Dispatchers.IO) {
        val c = URL(QueueGoNativeApi.BASE_URL + "/functions/v1/account-delete").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.doOutput = true
            c.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
            c.setRequestProperty("Authorization", "Bearer " + accessToken)
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use {
                it.write(
                    JSONObject().put("confirm", "DELETE_ACCOUNT")
                        .toString()
                        .toByteArray(StandardCharsets.UTF_8)
                )
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.use { it.readAccountDeleteResponseBounded() }.orEmpty()
            val payload = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (code !in 200..299 || !payload.optBoolean("ok", false)) {
                val message = when (payload.optString("error")) {
                    "ACTIVE_WORK" -> "ยังลบบัญชีไม่ได้ เพราะมีออเดอร์หรืองานที่ยังไม่จบ"
                    "ADMIN_ACCOUNT" -> "บัญชีแอดมินไม่รองรับการลบจากแอปนี้"
                    "ACCOUNT_NOT_FOUND" -> "ไม่พบบัญชี QueueGo"
                    "CONFIRMATION_REQUIRED" -> "ระบบต้องการการยืนยันการลบบัญชี"
                    "LOGIN_REQUIRED" -> "เซสชันหมดอายุ กรุณาเข้าสู่ระบบใหม่"
                    else -> "ลบบัญชีไม่สำเร็จ กรุณาลองอีกครั้ง"
                }
                error(message)
            }
        } finally {
            c.disconnect()
        }
    }
}

@Composable
fun QgAccountDeletionSection(
    accessToken: String,
    onDeleted: () -> Unit,
    pendingChatUserId: String? = null,
    onBusyChange: (Boolean) -> Unit = {}
) {
    val api = remember { QueueGoAccountDeletionApi() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    QgCard(Modifier.fillMaxWidth()) {
        Column {
            Text("ความเป็นส่วนตัวและบัญชี", fontWeight = FontWeight.ExtraBold)
            Text(
                "คุณสามารถขอลบบัญชี QueueGo และข้อมูลส่วนบุคคลได้จากแอป",
                color = QgMuted
            )
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(message!!, color = QgRed)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(QUEUEGO_PRIVACY_POLICY_URL))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("นโยบายความเป็นส่วนตัว")
            }
            Spacer(Modifier.height(8.dp))
            if (!armed) {
                OutlinedButton(
                    onClick = {
                        armed = true
                        message = "การลบบัญชีถาวรและย้อนกลับไม่ได้ กรุณายืนยันอีกครั้ง"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ลบบัญชี QueueGo")
                }
            } else {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            armed = false
                            message = null
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ยกเลิก")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true
                            onBusyChange(true)
                            scope.launch {
                                runCatching { api.delete(accessToken) }
                                    .onSuccess {
                                        pendingChatUserId?.let { userId ->
                                            withContext(Dispatchers.IO) { NativeChatPendingStore.clearUser(File(context.noBackupFilesDir, "customer-chat-pending"), userId) }
                                        }
                                        onDeleted()
                                    }
                                    .onFailure { message = it.message ?: "ลบบัญชีไม่สำเร็จ" }
                                busy = false
                                onBusyChange(false)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (busy) "กำลังลบ..." else "ยืนยันลบบัญชี")
                    }
                }
            }
        }
    }
}
