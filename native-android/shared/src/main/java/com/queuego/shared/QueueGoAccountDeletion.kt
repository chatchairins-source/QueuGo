package com.queuego.shared

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class QueueGoAccountDeletionApi {
    companion object {
        private const val URL_PATH = "https://pkypiqhlrmzocysgeqew.supabase.co/functions/v1/account-delete"
        private const val KEY = "sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre"
    }

    suspend fun delete(accessToken: String) = withContext(Dispatchers.IO) {
        val c = URL(URL_PATH).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.doOutput = true
            c.setRequestProperty("apikey", KEY)
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
            val text = stream?.use {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
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
    onDeleted: () -> Unit
) {
    val api = remember { QueueGoAccountDeletionApi() }
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
                            scope.launch {
                                runCatching { api.delete(accessToken) }
                                    .onSuccess { onDeleted() }
                                    .onFailure { message = it.message ?: "ลบบัญชีไม่สำเร็จ" }
                                busy = false
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
