package com.queuego.merchant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.PathParser
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image

private val MerchantLoginRed = Color(0xFFE7092B)
private val MerchantLoginMuted = Color(0xFF858B93)

/** Production qg-login-v08 dimensions and controls, using native Compose inputs. */
@Composable
fun MerchantProductionLogin(
    loginId: String,
    onLoginId: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    busy: Boolean,
    error: String?,
    message: String?,
    onLogin: () -> Unit,
    onForgot: () -> Unit,
    onRegister: () -> Unit,
    onStaff: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White).imePadding(), contentAlignment = Alignment.TopCenter) {
        val short = maxHeight <= 700.dp
        val veryShort = maxHeight <= 560.dp
        val viewportWidth = maxWidth.value
        val side = (viewportWidth * 0.06f).coerceIn(18f, 30f).dp
        val copySide = (viewportWidth * 0.06f).coerceIn(20f, 34f).dp
        val panelHeight = if (short) 373.dp else 439.dp
        val heroHeight = if (veryShort) (maxHeight * 0.28f).coerceAtLeast(120.dp)
            else (maxHeight - panelHeight + 16.dp).coerceAtLeast(if (short) 140.dp else 175.dp)
        val titleSize = if (veryShort) 21f else (viewportWidth * 0.064f).coerceIn(23f, 31f)
        val subtitleSize = (viewportWidth * 0.037f).coerceIn(12f, 16f)
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
                Image(painterResource(R.drawable.qgm_merchant_photo), null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(0f, -0.26f))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    0f to Color(0xFF070707).copy(alpha = 0.68f),
                    0.78f to Color(0xFF070707).copy(alpha = 0.08f),
                    1f to Color(0xFF070707).copy(alpha = 0.08f)
                )))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.13f), 0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.28f)
                )))
                Column(Modifier.padding(start = copySide, end = 14.dp, top = if (short) 25.dp else 44.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MerchantQueueGoMark(Modifier.size(42.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Queue", color = Color.White, fontSize = 25.sp, lineHeight = 25.sp,
                            fontWeight = FontWeight(850), letterSpacing = (-0.5).sp)
                        Text("Go", color = Color(0xFFF0092D), fontSize = 25.sp, lineHeight = 25.sp,
                            fontWeight = FontWeight(850), letterSpacing = (-0.5).sp)
                    }
                    Spacer(Modifier.height(if (short) 14.dp else 27.dp))
                    Text("เข้าสู่ระบบร้านค้า", color = Color.White, fontSize = titleSize.sp,
                        lineHeight = (titleSize * 1.24f).sp, letterSpacing = (-0.4).sp, fontWeight = FontWeight.Bold)
                    if (!veryShort) {
                        Spacer(Modifier.height(8.dp))
                        Text("จัดการคิวร้านของคุณ\nให้ง่ายขึ้น ในทุก ๆ วัน", color = Color.White,
                            fontSize = subtitleSize.sp, lineHeight = (subtitleSize * 1.45f).sp)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = 16.dp.roundToPx()
                layout(placeable.width, (placeable.height - overlap).coerceAtLeast(0)) {
                    placeable.placeRelative(0, -overlap)
                }
            }.background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(start = side, end = side, top = if (short) 16.dp else 24.dp, bottom = if (short) 12.dp else 20.dp)) {
                MerchantLoginField(loginId, onLoginId, "เบอร์โทรศัพท์ / อีเมล", false, true, busy, short, {}, onLogin)
                Spacer(Modifier.height(if (short) 8.dp else 12.dp))
                MerchantLoginField(password, onPassword, "รหัสผ่าน", true, passwordVisible, busy, short, onTogglePassword, onLogin)
                Spacer(Modifier.height((if (short) 8 else 12).dp + 6.dp))
                Button(onClick = onLogin, enabled = !busy, modifier = Modifier.fillMaxWidth().height(if (short) 48.dp else 54.dp),
                    shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = MerchantLoginRed,
                        disabledContainerColor = MerchantLoginRed.copy(alpha = 0.65f), disabledContentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("เข้าสู่ระบบ", fontSize = 16.sp, fontWeight = FontWeight(750))
                }
                if (!error.isNullOrBlank()) {
                    Text(error, color = Color(0xFF9D1431), fontSize = 14.sp, lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
                            .background(Color(0xFFFFF1F4), RoundedCornerShape(11.dp))
                            .border(1.dp, Color(0xFFF2BDC8), RoundedCornerShape(11.dp)).padding(12.dp))
                }
                if (!message.isNullOrBlank()) Text(message, color = Color(0xFF10855E), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(if (short) 8.dp else 16.dp))
                Box(Modifier.align(Alignment.CenterHorizontally).height(32.dp).clickable(enabled = !busy, onClick = onForgot), contentAlignment = Alignment.Center) {
                    Text("ลืมรหัสผ่าน?", color = MerchantLoginRed, fontSize = 14.sp, fontWeight = FontWeight(650))
                }
                Spacer(Modifier.height((if (short) 8 else 14).dp + 1.dp))
                Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE2E5E8), thickness = 1.dp)
                    Text("หรือ", fontSize = 13.sp, color = MerchantLoginMuted)
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE2E5E8), thickness = 1.dp)
                }
                Spacer(Modifier.height(11.dp))
                OutlinedButton(onClick = onRegister, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(if (short) 46.dp else 52.dp),
                    shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, MerchantLoginRed)) {
                    Text("สมัครร้านค้าใหม่", color = MerchantLoginRed, fontSize = 15.sp, fontWeight = FontWeight(750))
                }
                Spacer(Modifier.height(9.dp))
                OutlinedButton(onClick = onStaff, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFFEF9CAB)), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("พนักงานหน้าร้าน: สมัคร / ใส่รหัสเชิญ", color = MerchantLoginRed, fontSize = 14.sp, fontWeight = FontWeight(750))
                }
            }
        }
    }
}

@Composable
private fun MerchantLoginField(value: String, change: (String) -> Unit, hint: String, secret: Boolean,
    visible: Boolean, busy: Boolean, short: Boolean, toggle: () -> Unit, submit: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Row(Modifier.fillMaxWidth().height(if (short) 46.dp else 54.dp)
        .border(1.dp, if (focused) MerchantLoginRed else Color(0xFFDCE0E5), RoundedCornerShape(13.dp))
        .padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MerchantLoginInputIcon(secret, Modifier.size(19.dp))
        BasicTextField(value = value, onValueChange = change, enabled = !busy, singleLine = true,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = hint },
            textStyle = TextStyle(color = Color(0xFF17191D), fontSize = 16.sp, lineHeight = 20.sp), cursorBrush = SolidColor(MerchantLoginRed),
            visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                imeAction = if (secret) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { submit() }),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = MerchantLoginMuted, fontSize = 16.sp, lineHeight = 20.sp); inner() } })
        if (secret) Canvas(Modifier.size(42.dp).semantics { contentDescription = if (visible) "ซ่อนรหัสผ่าน" else "แสดงรหัสผ่าน" }
            .clickable(enabled = !busy, onClick = toggle).padding(11.dp)) {
            val iconScale = size.width / 24f
            val color = Color(0xFF59616C)
            // The Production password toggle keeps this exact SVG on both visibility states.
            val path = PathParser.createPathFromPathData(
                "M3 3l18 18M10.6 10.7a2 2 0 002.7 2.7 " +
                "M8.4 5.5A10.5 10.5 0 0112 5c5 0 8.5 4.5 9 7a10 10 0 01-2.3 4.2 " +
                "M6.1 6.1C4.5 7.3 3.4 9 3 12c.5 2.5 4 7 9 7 1.2 0 2.4-.3 3.4-.7"
            )?.asComposePath()
            if (path != null) scale(iconScale, iconScale, pivot = Offset.Zero) {
                drawPath(path, color, style = Stroke(1.8f, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun MerchantLoginInputIcon(lock: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val k = size.width / 24f
        val color = Color(0xFF6E747C)
        val stroke = Stroke(1.8f*k)
        if (lock) {
            drawRoundRect(color, Offset(5f*k,10f*k), Size(14f*k,11f*k), CornerRadius(2f*k), style = stroke)
            drawArc(color, 180f, 180f, false, Offset(8f*k,3f*k), Size(8f*k,8f*k), style = stroke)
            drawLine(color, Offset(8f*k,7f*k), Offset(8f*k,10f*k), 1.8f*k)
            drawLine(color, Offset(16f*k,7f*k), Offset(16f*k,10f*k), 1.8f*k)
        } else {
            drawCircle(color, 4f*k, Offset(12f*k,8f*k), style = stroke)
            drawArc(color, 180f, 180f, false, Offset(4f*k,13f*k), Size(16f*k,16f*k), style = stroke)
        }
    }
}
