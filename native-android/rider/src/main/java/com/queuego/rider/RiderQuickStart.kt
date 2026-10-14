package com.queuego.rider

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed

internal data class RiderQuickStartStep(
    val title: String,
    val detail: String
)

internal val riderQuickStartSteps = listOf(
    RiderQuickStartStep(
        "พร้อมก่อนเปิดออนไลน์",
        "ตรวจอินเทอร์เน็ต GPS การแจ้งเตือน Google Maps เอกสาร รถ และการยืนยันตัวตนให้พร้อมก่อนเปิดรับงาน"
    ),
    RiderQuickStartStep(
        "ข้อเสนองาน 30 วินาที",
        "QueueGo ส่งงานให้ Rider เป้าหมายทีละคน กดรับหรือปฏิเสธได้ งานที่ยังไม่ได้เสนอให้คุณจะไม่แสดงเป็นกองงานรวม"
    ),
    RiderQuickStartStep(
        "นำทางไปจุดรับ",
        "กดนำทางเพื่อเปิด Google Maps แล้วใช้ปุ่ม Q ลอยกลับเข้า QueueGo ได้เร็ว หากเป็นงานตลาดให้ทำตามลำดับจุดรับที่แสดง"
    ),
    RiderQuickStartStep(
        "รับสินค้าและจ่ายร้าน",
        "ดูยอดที่ต้องจ่ายร้าน ตรวจรายการ แล้วถ่ายรูปหลักฐานจุดรับเพียงขั้นตอนเดียวก่อนออกไปส่งลูกค้า"
    ),
    RiderQuickStartStep(
        "ส่งลูกค้าและเก็บเงิน",
        "นำทางไปลูกค้า ดูยอดเงินที่ต้องเก็บ ถ่ายรูปหลักฐานตอนส่ง และทำตามหน้าจองานโดยไม่ต้องยืนยันเงินซ้ำ"
    ),
    RiderQuickStartStep(
        "จบงานและตรวจรายได้",
        "กดจบงานเมื่อส่งสำเร็จ รายได้และประวัติงานจะอัปเดตในหน้า วันนี้ / 7 วัน / 30 วัน และใช้ศูนย์ความปลอดภัยเมื่อจำเป็น"
    )
)

internal fun riderQuickStartIndexAfter(current: Int, delta: Int): Int =
    (current + delta).coerceIn(0, riderQuickStartSteps.lastIndex)

internal class RiderQuickStartStore(context: Context) {
    private val prefs = context.getSharedPreferences("queuego-rider-quickstart", Context.MODE_PRIVATE)

    fun hasSeen(userId: String): Boolean =
        prefs.getBoolean("v1:$userId", false)

    fun markSeen(userId: String) {
        prefs.edit().putBoolean("v1:$userId", true).apply()
    }
}

@Composable
internal fun RiderQuickStartDialog(
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    val step = riderQuickStartSteps[index]

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFEEE6E8))
        ) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .padding(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("คู่มือ Rider", fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text(
                            "ขั้นตอนใช้งาน QueueGo แบบสั้น",
                            color = QgMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("ข้าม", color = QgMuted, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .size(54.dp)
                        .background(Color(0xFFFFECEE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (index + 1).toString(),
                        color = QgRed,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(step.title, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(
                    step.detail,
                    color = Color(0xFF5F575B),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    riderQuickStartSteps.indices.forEach { dot ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(
                                    if (dot <= index) QgRed else Color(0xFFE8E3E5),
                                    RoundedCornerShape(99.dp)
                                )
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    OutlinedButton(
                        onClick = { index = riderQuickStartIndexAfter(index, -1) },
                        enabled = index > 0,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("ย้อนกลับ", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            if (index == riderQuickStartSteps.lastIndex) {
                                onComplete()
                            } else {
                                index = riderQuickStartIndexAfter(index, 1)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                    ) {
                        Text(
                            if (index == riderQuickStartSteps.lastIndex) "เริ่มใช้งาน" else "ถัดไป",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
