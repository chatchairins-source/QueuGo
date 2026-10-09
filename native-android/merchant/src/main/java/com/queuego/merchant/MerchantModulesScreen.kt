package com.queuego.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.launch

@Composable
internal fun MerchantModulesScreen(
    auth: NativeAuth,
    api: MerchantApi,
    onBack: () -> Unit,
    onSetup: () -> Unit,
    onHours: () -> Unit,
    onLaundry: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var modules by remember { mutableStateOf<List<MerchantModule>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        runCatching { api.loadModules(auth) }
            .onSuccess { modules = it; message = null }
            .onFailure { message = it.message ?: "โหลดการตั้งค่าไม่สำเร็จ" }
        loading = false
    }

    LaunchedEffect(auth.user.id) { refresh() }

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
            Column {
                Text("ตั้งค่าร้านค้า", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("จัดการโมดูลและทางลัดการตั้งค่าร้าน", color = QgMuted, fontSize = 10.sp)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, QgLine, RoundedCornerShape(16.dp))
        ) {
            MerchantSettingsLink("ข้อมูลและตำแหน่งร้าน", onSetup)
            MerchantSettingsDivider()
            MerchantSettingsLink("เวลาทำการและวันหยุด", onHours)
            MerchantSettingsDivider()
            MerchantSettingsLink("ฝากซัก · บริการ ราคา และ Rider", onLaundry)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "โมดูลร้านค้า",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 5.dp)
        )

        if (!message.isNullOrBlank()) {
            Text(
                message!!,
                color = if (message!!.contains("→")) Color(0xFF0A9660) else QgRed,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 5.dp)
            )
        }

        if (loading) {
            Box(
                Modifier.fillMaxWidth().height(140.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            merchantModuleCatalog.forEach { (key, label) ->
                val row = modules.find { it.key == key }
                    ?: MerchantModule(null, key, "disabled", null, null)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            merchantModuleStatusLabel(row.status),
                            color = QgMuted,
                            fontSize = 9.5.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (busyKey == null) {
                                busyKey = key
                                scope.launch {
                                    runCatching { api.cycleModule(auth, key, row) }
                                        .onSuccess { saved ->
                                            modules = modules
                                                .filterNot { it.key == key } + saved
                                            message = "$label → ${merchantModuleStatusLabel(saved.status)}"
                                        }
                                        .onFailure {
                                            message = "บันทึกโมดูลไม่สำเร็จ: " +
                                                (it.message ?: "เกิดข้อผิดพลาด")
                                        }
                                    busyKey = null
                                }
                            }
                        },
                        enabled = busyKey == null,
                        modifier = Modifier.height(40.dp)
                    ) {
                        if (busyKey == key) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                when (row.status) {
                                    "enabled" -> "ปิดใช้งาน"
                                    "trial" -> "เปิดใช้งานเต็มรูปแบบ"
                                    else -> "เปิดใช้งาน"
                                },
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MerchantSettingsLink(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("›", color = Color(0xFFA3A7AD), fontSize = 20.sp)
    }
}

@Composable
private fun MerchantSettingsDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(QgLine)
    )
}
