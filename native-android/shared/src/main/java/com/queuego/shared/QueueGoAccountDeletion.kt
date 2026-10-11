package com.queuego.shared

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onDeleted: () -> Unit,
    pendingChatUserId: String? = null,
    showHeader: Boolean = true,
    privacySubtitle: String = "การใช้ข้อมูลและการลบบัญชี",
    deleteSubtitle: String = "ลบ Auth และข้อมูลส่วนบุคคล",
    onLogout: (() -> Unit)? = null,
    logoutSubtitle: String = "ออกจากบัญชีในอุปกรณ์นี้"
) {
    val api = remember { QueueGoAccountDeletionApi() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column {
        if (showHeader) {
            Text("ความเป็นส่วนตัวและบัญชี", fontWeight = FontWeight.ExtraBold)
            Text(
                "คุณสามารถดูนโยบายความเป็นส่วนตัวและขอลบบัญชี QueueGo ได้จากแอป",
                color = QgMuted
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, QgLine, RoundedCornerShape(16.dp))
        ) {
            AccountSettingsRow(
                icon = "gear",
                title = "นโยบายความเป็นส่วนตัว",
                subtitle = privacySubtitle,
                danger = false,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://chatchairins-source.github.io/QueuGo/docs/privacy.html")
                            )
                        )
                    }.onFailure {
                        message = "เปิดนโยบายความเป็นส่วนตัวไม่สำเร็จ"
                    }
                }
            )
            HorizontalDivider(color = QgLine)
            if (!armed) {
                AccountSettingsRow(
                    icon = "close",
                    title = "ลบบัญชีถาวร",
                    subtitle = deleteSubtitle,
                    danger = true,
                    onClick = {
                        armed = true
                        message = "การลบบัญชีถาวรและย้อนกลับไม่ได้ กรุณายืนยันอีกครั้ง"
                    }
                )
            } else {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("ยืนยันลบบัญชีถาวร", color = QgRed, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "การลบบัญชีไม่สามารถย้อนกลับได้",
                        color = QgMuted
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                armed = false
                                message = null
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("ยกเลิก") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (busy) return@Button
                                busy = true
                                scope.launch {
                                    runCatching { api.delete(accessToken) }
                                        .onSuccess {
                                            pendingChatUserId?.let { userId ->
                                                withContext(Dispatchers.IO) {
                                                    NativeChatPendingStore.clearUser(
                                                        File(context.noBackupFilesDir, "customer-chat-pending"),
                                                        userId
                                                    )
                                                }
                                            }
                                            onDeleted()
                                        }
                                        .onFailure { message = it.message ?: "ลบบัญชีไม่สำเร็จ" }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (busy) "กำลังลบ..." else "ยืนยันลบ") }
                    }
                }
            }
            if (onLogout != null) {
                HorizontalDivider(color = QgLine)
                AccountSettingsRow(
                    icon = "back",
                    title = "ออกจากระบบ",
                    subtitle = logoutSubtitle,
                    danger = false,
                    onClick = onLogout
                )
            }
        }
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(message!!, color = if (armed) QgRed else QgMuted)
        }
    }
}

@Composable
private fun AccountSettingsRow(
    icon: String,
    title: String,
    subtitle: String,
    danger: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(
                    if (danger) Color(0xFFFFF0F3) else Color(0xFFF5F5F6),
                    RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(
                icon,
                Modifier.size(17.dp),
                if (danger) QgRed else Color(0xFF737982)
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (danger) Color(0xFFD92F3E) else Color(0xFF17191D),
                fontWeight = FontWeight.ExtraBold
            )
            Text(subtitle, color = Color(0xFF8A8F96))
        }
        Text("›", color = Color(0xFFAFB3B9))
    }
}
