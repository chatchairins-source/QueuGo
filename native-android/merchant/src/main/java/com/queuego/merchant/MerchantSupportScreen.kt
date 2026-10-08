package com.queuego.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgSectionTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MerchantSupportScreen(
    auth: NativeAuth,
    onBack: () -> Unit
) {
    val api = remember { MerchantApi() }
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<MerchantSupportMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        runCatching { api.loadSupportMessages(auth) }
            .onSuccess { messages = it; error = null }
            .onFailure { error = it.message ?: "โหลดข้อความไม่สำเร็จ" }
    }

    LaunchedEffect(auth.user.id) {
        while (true) {
            refresh()
            delay(5_000)
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
        }
        Spacer(Modifier.height(12.dp))
        QgSectionTitle("ข้อความถึงแอดมิน", "ใช้ช่องทางเดิมของ QueueGo Production")
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(error!!, color = QgRed)
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (messages.isEmpty()) {
                Text("ยังไม่มีข้อความ เริ่มพิมพ์ถึงทีม QueueGo ด้านล่างได้เลย", color = QgMuted)
            } else {
                messages.forEach { message ->
                    val mine = message.senderUserId == auth.user.id
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (mine) androidx.compose.foundation.layout.Arrangement.End else androidx.compose.foundation.layout.Arrangement.Start
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth(0.88f)
                                .background(
                                    if (mine) Color(0xFFFFECEF) else Color.White,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                if (mine) "ร้านค้า" else "QueueGo Admin",
                                color = if (mine) QgRed else QgMuted,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(message.body)
                            if (!message.createdAt.isNullOrBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    message.createdAt!!,
                                    color = QgMuted,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { if (it.length <= 2000) input = it },
            label = { Text("พิมพ์ข้อความถึงแอดมิน") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (busy || input.isBlank()) return@Button
                busy = true
                val body = input
                scope.launch {
                    runCatching { api.sendSupportMessage(auth, body) }
                        .onSuccess {
                            input = ""
                            refresh()
                        }
                        .onFailure { error = it.message ?: "ส่งข้อความไม่สำเร็จ" }
                    busy = false
                }
            },
            enabled = !busy && input.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "กำลังส่ง..." else "ส่งข้อความ")
        }
    }
}
