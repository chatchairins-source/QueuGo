package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** rider/index.html renderPending: no map, GPS, job feed or online toggle. */
@Composable
internal fun RiderPendingAccountScreen(modifier: Modifier, name: String, onLogout: () -> Unit) {
    RiderAccountCard(modifier, "ใบสมัครของคุณอยู่ระหว่างตรวจสอบ", "สวัสดี " + name.ifBlank { "ไรเดอร์" },
        "ใบสมัครของคุณถูกบันทึกแล้ว เมื่อ Admin อนุมัติ คุณจะสามารถเปิดรับงานได้ทันที", "ออกจากระบบ", onLogout)
}

@Composable
internal fun RiderConnectionRecoveryScreen(modifier: Modifier, onRetry: () -> Unit) {
    RiderAccountCard(modifier, null, "เชื่อมต่อไม่ได้",
        "บัญชียังอยู่ในเครื่อง กรุณาต่ออินเทอร์เน็ตแล้วลองใหม่", "ลองใหม่", onRetry)
}

@Composable
private fun RiderAccountCard(modifier: Modifier, badge: String?, title: String, description: String,
    button: String, onClick: () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().drawWithCache {
        val direction = Offset(.34202015f, .9396926f)
        val length = size.width * direction.x + size.height * direction.y
        val center = Offset(size.width / 2f, size.height / 2f)
        val brush = Brush.linearGradient(listOf(Color.White, Color(0xFFFFF1F4)),
            start = center - direction * (length / 2f), end = center + direction * (length / 2f))
        onDrawBehind { drawRect(brush) }
    }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
                .heightIn(min = (maxHeight - 40.dp).coerceAtLeast(0.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                Modifier.widthIn(max = 430.dp).fillMaxWidth().drawWithCache {
                    val sigma = 46.dp.toPx() / 2f
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        setShadowLayer((sigma - .5f) / .57735f, 0f, 18.dp.toPx(), android.graphics.Color.argb(31,58,19,30))
                    }
                    val radius = 28.dp.toPx()
                    onDrawBehind { drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f,0f,size.width,size.height,radius,radius,paint) } }
                },
                shape = RoundedCornerShape(28.dp), color = Color.White
            ) {
                Column(Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFE6002D)) {
                        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
                            Text("Q", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    if (badge != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(badge, color = Color(0xFFE6002D),
                        fontSize = 11.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.background(Color(0xFFFFF0F3), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(title, fontWeight = FontWeight.Bold,
                        fontSize = 32.sp, color = Color(0xFF17171B))
                    Spacer(Modifier.height(5.dp))
                    Text(description,
                        fontSize = 12.sp, lineHeight = 19.2.sp, color = Color(0xFF77747B),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFF3C7D1)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE6002D))) {
                        Text(button, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
