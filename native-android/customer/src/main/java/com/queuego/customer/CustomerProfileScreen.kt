package com.queuego.customer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.NativePushApi
import com.queuego.shared.QgAccountDeletionSection
import com.queuego.shared.QgIcon
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.launch

private data class CustomerProfileData(
    val name: String,
    val phone: String?,
    val email: String?,
    val photo: String?
)

private class CustomerProfileApi(
    private val http: QueueGoNativeApi = QueueGoNativeApi()
) {
    suspend fun load(auth: NativeAuth): CustomerProfileData {
        val rows = http.array(
            http.get(
                "users?select=name,phone,email,metadata&id=eq." + http.enc(auth.user.id) + "&limit=1",
                auth.session.accessToken
            )
        )
        val row = rows.optJSONObject(0)
        val metadata = row?.optJSONObject("metadata")
        return CustomerProfileData(
            name = row?.optString("name")?.takeIf { it.isNotBlank() } ?: auth.user.name,
            phone = row?.optString("phone")?.takeIf { it.isNotBlank() && it != "null" },
            email = row?.optString("email")?.takeIf { it.isNotBlank() && it != "null" },
            photo = listOf(
                metadata?.optString("profileImage"),
                metadata?.optString("profile_image"),
                metadata?.optString("avatarUrl")
            ).firstOrNull { !it.isNullOrBlank() && it != "null" }
        )
    }
}

@Composable
internal fun CustomerProfileScreen(
    auth: NativeAuth,
    onAddress: () -> Unit,
    onOrders: () -> Unit,
    onNotifications: () -> Unit,
    onFavorites: () -> Unit,
    onPromotion: () -> Unit,
    onSupport: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { CustomerProfileApi() }
    val pushApi = remember { NativePushApi() }
    val prefs = remember {
        context.applicationContext.getSharedPreferences(
            "qg_customer_profile_preferences",
            Context.MODE_PRIVATE
        )
    }
    var profile by remember(auth.user.id) {
        mutableStateOf(CustomerProfileData(auth.user.name, null, null, null))
    }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var arrivalSound by remember { mutableStateOf(prefs.getBoolean("arrival_sound", false)) }
    var pushEnabled by remember { mutableStateOf(customerPushEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            message = "Android ยังไม่ได้อนุญาตการแจ้งเตือน"
            return@rememberLauncherForActivityResult
        }
        busy = true
        scope.launch {
            runCatching {
                setCustomerPushEnabled(context, true)
                syncCustomerNativePush(context, auth)
            }.onSuccess {
                pushEnabled = true
                message = "เปิดการแจ้งเตือนเบื้องหลังแล้ว"
            }.onFailure {
                setCustomerPushEnabled(context, false)
                pushEnabled = false
                message = it.message ?: "เปิดการแจ้งเตือนไม่สำเร็จ"
            }
            busy = false
        }
    }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        runCatching { api.load(auth) }.onSuccess { profile = it }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QgRemoteImage(
                source = profile.photo,
                modifier = Modifier.size(60.dp),
                fallback = profile.name,
                cornerRadius = 20.dp
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.ifBlank { "ลูกค้า" },
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF202329)
                )
                val contact = profile.phone ?: profile.email.orEmpty()
                if (contact.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(contact, color = QgMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .background(Color(0xFFEAF9F1), RoundedCornerShape(99.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "บัญชีลูกค้า",
                        color = Color(0xFF0A9660),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        CustomerProfileSectionTitle("บัญชีและการใช้งาน")
        CustomerProfileCard {
            CustomerProfileActionRow("pin", "ที่อยู่จัดส่ง", null, onAddress)
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileActionRow("orders", "ออเดอร์ของฉัน", null, onOrders)
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileActionRow("bell", "การแจ้งเตือน", null, onNotifications)
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileActionRow("heart", "รายการโปรด", null, onFavorites)
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileActionRow("gift", "โปรโมชั่นจากร้าน", null, onPromotion)
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileActionRow("support", "ติดต่อฝ่ายช่วยเหลือ", null, onSupport)
        }

        Spacer(Modifier.height(12.dp))
        CustomerProfileSectionTitle("การแจ้งเตือน")
        CustomerProfileCard {
            CustomerProfileStateRow(
                icon = "bell",
                title = "เสียงแจ้งเตือนเมื่อ Rider ถึง",
                subtitle = "เสียงเตือนเมื่อ Rider มาถึงจุดส่ง",
                state = if (arrivalSound) "เปิด" else "ปิด",
                on = arrivalSound,
                enabled = !busy
            ) {
                arrivalSound = !arrivalSound
                prefs.edit().putBoolean("arrival_sound", arrivalSound).apply()
            }
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileStateRow(
                icon = "bell",
                title = "การแจ้งเตือนเบื้องหลัง",
                subtitle = "รับสถานะออเดอร์แม้ไม่ได้เปิดแอป",
                state = if (pushEnabled) "เปิด" else "ปิด",
                on = pushEnabled,
                enabled = !busy
            ) {
                if (pushEnabled) {
                    busy = true
                    scope.launch {
                        runCatching {
                            disableCustomerNativePush(context, auth)
                            setCustomerPushEnabled(context, false)
                        }.onSuccess {
                            pushEnabled = false
                            message = "ปิดการแจ้งเตือนเบื้องหลังแล้ว"
                        }.onFailure {
                            message = it.message ?: "ปิดการแจ้งเตือนไม่สำเร็จ"
                        }
                        busy = false
                    }
                } else if (
                    Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    busy = true
                    scope.launch {
                        runCatching {
                            setCustomerPushEnabled(context, true)
                            syncCustomerNativePush(context, auth)
                        }.onSuccess {
                            pushEnabled = true
                            message = "เปิดการแจ้งเตือนเบื้องหลังแล้ว"
                        }.onFailure {
                            setCustomerPushEnabled(context, false)
                            pushEnabled = false
                            message = it.message ?: "เปิดการแจ้งเตือนไม่สำเร็จ"
                        }
                        busy = false
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileActionRow(
                icon = "bell",
                title = "ทดสอบการแจ้งเตือน",
                subtitle = "ส่งแจ้งเตือนทดสอบหลังประมาณ 7 วินาที",
                enabled = !busy
            ) {
                busy = true
                message = "ระบบจะส่งแจ้งเตือนทดสอบหลังประมาณ 7 วินาที"
                scope.launch {
                    runCatching { pushApi.test(auth) }
                        .onFailure { message = it.message ?: "ทดสอบการแจ้งเตือนไม่สำเร็จ" }
                    busy = false
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .background(Color(0xFFF7F7F8), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("การชำระเงิน", color = Color(0xFF5D6269), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("ชำระเงินสดเมื่อรับสินค้า", color = Color(0xFF737980), fontSize = 9.5.sp)
        }

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, color = QgRed, fontSize = 11.sp)
        }

        Spacer(Modifier.height(12.dp))
        CustomerProfileSectionTitle("ความเป็นส่วนตัวและบัญชี")
        QgAccountDeletionSection(
            accessToken = auth.session.accessToken,
            onDeleted = onLogout,
            pendingChatUserId = auth.user.id,
            showHeader = false
        )
        Spacer(Modifier.height(8.dp))
        CustomerProfileCard {
            CustomerProfileActionRow(
                icon = "logout",
                title = "ออกจากระบบ",
                subtitle = null,
                onClick = onLogout
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CustomerProfileSectionTitle(text: String) {
    Text(
        text,
        color = Color(0xFF777D85),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
    )
}

@Composable
private fun CustomerProfileCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFECEEF1), RoundedCornerShape(16.dp))
    ) { content() }
}

@Composable
private fun CustomerProfileActionRow(
    icon: String,
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).background(Color(0xFFF5F5F6), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(icon, Modifier.size(17.dp), Color(0xFF737982))
        }
        Spacer(Modifier.size(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFF202329), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Color(0xFF8A8F96), fontSize = 9.5.sp, lineHeight = 13.sp)
            }
        }
        Text("›", color = Color(0xFFAFB3B9), fontSize = 22.sp)
    }
}

@Composable
private fun CustomerProfileStateRow(
    icon: String,
    title: String,
    subtitle: String,
    state: String,
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).background(Color(0xFFF5F5F6), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(icon, Modifier.size(17.dp), Color(0xFF737982))
        }
        Spacer(Modifier.size(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFF202329), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Color(0xFF8A8F96), fontSize = 9.5.sp, lineHeight = 13.sp)
        }
        Box(
            Modifier
                .background(
                    if (on) Color(0xFFE9FAF2) else Color(0xFFF1F2F4),
                    RoundedCornerShape(99.dp)
                )
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                state,
                color = if (on) Color(0xFF0A9660) else Color(0xFF7D8289),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
