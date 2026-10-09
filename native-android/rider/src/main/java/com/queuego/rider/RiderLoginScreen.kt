package com.queuego.rider

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AuthInk = Color(0xFF17171B)
private val AuthRed = Color(0xFFE6002D)
private val AuthMuted = Color(0xFF77747B)

private fun authText(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontSize = size.sp, lineHeight = height.sp, fontWeight = weight,
    color = AuthInk, platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/** Production rider/index.html AUTH rules; mobile hides the desktop brand panel. */
@Composable
internal fun RiderLoginScreen(
    modifier: Modifier,
    busy: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var navigationError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val submit = {
        if (!busy) {
            if (identifier.isBlank() || password.isBlank()) {
                validation = "กรุณากรอกอีเมลหรือเบอร์โทรและรหัสผ่าน"
            } else {
                validation = null
                onLogin(identifier.trim(), password)
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().imePadding().drawWithCache {
        // CSS linear-gradient(160deg); preserve the 54% colour stop.
        val direction = Offset(0.34202015f, 0.9396926f)
        val length = size.width * direction.x + size.height * direction.y
        val center = Offset(size.width / 2f, size.height / 2f)
        val brush = Brush.linearGradient(
            0f to Color.White, .54f to Color(0xFFFFF7F8), 1f to Color(0xFFFCE7EC),
            start = center - direction * (length / 2f),
            end = center + direction * (length / 2f)
        )
        onDrawBehind { drawRect(brush) }
    }) {
        val wide = maxWidth > 800.dp
        val viewportHeight = maxHeight
        val brandPadding = (maxWidth.value * .06f).coerceIn(34f, 86f).dp
        val headingSize = (maxWidth.value * .06f).coerceIn(42f, 78f)
        val card: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.widthIn(max = 430.dp).fillMaxWidth()
                    .shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x1F3A131E), spotColor = Color(0x1F3A131E)),
                shape = RoundedCornerShape(30.dp), color = Color.White
            ) {
                Column(Modifier.padding(28.dp)) {
                    Text("เข้าสู่ระบบ", style = authText(29, 44, FontWeight.Bold))
                    Spacer(Modifier.height(6.dp))
                    Text("ใช้เบอร์โทรศัพท์หรืออีเมลที่สมัครไว้", style = authText(12, 18), color = AuthMuted)
                    Spacer(Modifier.height(20.dp))
                    LoginField("เบอร์โทรศัพท์ / อีเมล", "0812345678", identifier,
                        { identifier = it; validation = null }, false, busy, submit)
                    Spacer(Modifier.height(13.dp))
                    LoginField("รหัสผ่าน", "รหัสผ่าน", password,
                        { password = it; validation = null }, true, busy, submit)
                    Spacer(Modifier.height(13.dp))
                    val message = validation ?: navigationError ?: error
                    if (message != null) {
                        Text(message, style = authText(11, 17), color = Color(0xFFB4233D),
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF0F3), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp))
                        Spacer(Modifier.height(10.dp))
                    }
                    Button(onClick = submit, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455), disabledContainerColor = Color(0x73F04455), disabledContentColor = Color.White)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("เข้าสู่ระบบ", style = authText(15, 23, FontWeight.Black), color = Color.White)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            // Existing Production onboarding remains usable; native registration
                            // is still a functional parity gap, never certified by login capture.
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatchairins-source.github.io/QueuGo/rider/"))) }
                                .onFailure { navigationError = "เปิดหน้าสมัครไม่สำเร็จ กรุณาลองใหม่" }
                        },
                        enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFF3C7D1)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = AuthRed)
                    ) { Text("สมัครเป็นไรเดอร์", style = authText(15, 23, FontWeight.Black), color = AuthRed) }
                    Spacer(Modifier.height(16.dp))
                    Text("QueueGo Rider • Beta", style = authText(9, 14), color = Color(0xFFA29BA0), modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
        if (!wide) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) { card() }
        } else {
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.weight(1.05f).heightIn(min = viewportHeight).background(Color(0xFFFFF5F7)).padding(brandPadding), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(18.dp), color = AuthRed) { Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) { Text("Q", color = Color.White, style = authText(38, 58, FontWeight.Black)) } }
                        Spacer(Modifier.width(12.dp))
                        Column { Row { Text("Queue", style = authText(28, 28, FontWeight.Bold)); Text("Go", style = authText(28, 28, FontWeight.Bold), color = AuthRed) }; Text("Rider", color = Color(0xFF6F6970), style = authText(13, 20, FontWeight.ExtraBold)) }
                    }
                    Column {
                        Text("สำหรับไรเดอร์ QueueGo", color = AuthRed, style = authText(12, 18, FontWeight.Black), modifier = Modifier.background(Color.White, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
                        Spacer(Modifier.height(18.dp))
                        Text("รับงานง่าย\nเห็นข้อมูลชัด\nทำงานได้เร็ว", style = authText(42, 44, FontWeight.Bold).copy(fontSize = headingSize.sp, lineHeight = (headingSize * 1.03f).sp, letterSpacing = (-headingSize * .045f).sp))
                        Spacer(Modifier.height(18.dp))
                        Text("เข้าสู่ระบบเพื่อดูงานใกล้คุณ เส้นทาง รายได้ และสถานะงานทั้งหมดในที่เดียว", style = authText(15, 26), color = Color(0xFF5F5960))
                    }
                }
                Box(Modifier.weight(.75f).heightIn(min = viewportHeight).padding(horizontal = 17.dp), contentAlignment = Alignment.Center) { card() }
            }
        }
    }
}

@Composable
private fun LoginField(label: String, placeholder: String, value: String, change: (String) -> Unit, secret: Boolean, busy: Boolean, submit: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, style = authText(11, 17, FontWeight(850)))
        Spacer(Modifier.height(7.dp))
        BasicTextField(value, change, enabled = !busy, singleLine = true,
            textStyle = authText(16, 24), cursorBrush = androidx.compose.ui.graphics.SolidColor(AuthRed),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Email, imeAction = if (secret) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().height(50.dp).onFocusChanged { focused = it.isFocused }
                .border(1.dp, if (focused) Color(0x88E6002D) else Color(0xFFE6DFE2), RoundedCornerShape(15.dp)),
            decorationBox = { inner -> Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = authText(16, 24), color = Color(0xFF757575))
                inner()
            } }
        )
    }
}
