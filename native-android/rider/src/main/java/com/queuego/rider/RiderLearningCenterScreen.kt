package com.queuego.rider

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed

internal fun riderShouldShowLearningOnboarding(activeUser: Boolean, seen: Boolean): Boolean =
    activeUser && !seen

internal class RiderLearningOnboardingStore(context: Context) {
    private val prefs = context.getSharedPreferences("queuego-rider-learning", Context.MODE_PRIVATE)

    fun hasSeen(userId: String): Boolean =
        prefs.getBoolean("onboarding-v1:$userId", false)

    fun markSeen(userId: String) {
        prefs.edit().putBoolean("onboarding-v1:$userId", true).apply()
    }
}

internal data class RiderLearningLesson(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<String>
)

internal fun riderLearningLessons(): List<RiderLearningLesson> = listOf(
    RiderLearningLesson(
        id = "offer",
        title = "รับข้อเสนองาน",
        summary = "ข้อเสนอส่งตรงถึง Rider ทีละคนเป็นเวลา 30 วินาที",
        steps = listOf(
            "กดรับงานภายในเวลาที่แสดงบนหน้าข้อเสนอ",
            "ถ้าปฏิเสธหรือหมดเวลา ระบบจะส่งต่อไป Rider คนถัดไป",
            "งานที่ยังไม่ได้เสนอให้คุณจะไม่แสดงเป็นงานให้แย่งกด"
        )
    ),
    RiderLearningLesson(
        id = "merchant-navigation",
        title = "เดินทางไปร้าน",
        summary = "ใช้ Google Maps สำหรับนำทาง แล้วกลับ QueueGo ด้วยปุ่ม Q",
        steps = listOf(
            "กดนำทางไปร้านเพื่อเปิดแอป Google Maps",
            "ปุ่มลอย Q ช่วยกลับเข้าหน้างานเดิมโดยไม่เริ่มงานใหม่",
            "เมื่อใกล้ร้านให้กลับ QueueGo เพื่อดำเนินขั้นตอนรับสินค้า"
        )
    ),
    RiderLearningLesson(
        id = "pickup",
        title = "รับสินค้าและจ่ายร้าน",
        summary = "ตรวจรายการ ดูยอดเงินร้าน และถ่ายหลักฐานเพียงครั้งเดียว",
        steps = listOf(
            "ดูยอดที่ต้องจ่ายร้านจากหน้างานก่อนส่งมอบเงินสด",
            "ตรวจรายการสินค้าและถ่ายรูปหลักฐานจุดรับ 1 รูป",
            "QueueGo ไม่มีขั้นตอน PIN หรือ OTP สำหรับการรับสินค้า"
        )
    ),
    RiderLearningLesson(
        id = "market",
        title = "งาน Market หลายร้าน",
        summary = "เก็บสินค้าตามจุด 1, 2, 3… แล้วส่งลูกค้าหนึ่งปลายทาง",
        steps = listOf(
            "ทำจุดรับตามลำดับที่ QueueGo แสดง",
            "แต่ละร้านแสดงยอดเงินที่ต้องจ่ายของร้านนั้น",
            "เมื่อรับครบทุกจุดแล้วจึงเดินทางไปหาลูกค้า"
        )
    ),
    RiderLearningLesson(
        id = "delivery",
        title = "ส่งสินค้าและเก็บเงินลูกค้า",
        summary = "นำทางไปลูกค้า ดูยอดที่ต้องเก็บ ถ่ายรูป และจบงาน",
        steps = listOf(
            "กดนำทางไปลูกค้าเพื่อเปิด Google Maps",
            "ถึงลูกค้าแล้วตรวจยอดเงินสดที่ต้องเก็บจากหน้าจอ QueueGo",
            "ถ่ายรูปหลักฐานตอนส่ง แล้วกดจบงานเมื่อส่งมอบและเก็บเงินเรียบร้อย"
        )
    ),
    RiderLearningLesson(
        id = "location",
        title = "GPS และการถึงจุดหมาย",
        summary = "QueueGo ใช้ตำแหน่งเพื่อช่วยตรวจเส้นทาง แต่ไม่ล็อกปุ่มแบบแข็ง",
        steps = listOf(
            "เปิดสิทธิ์ตำแหน่งและ GPS ระหว่างรับงาน",
            "หากกดถึงร้าน/ลูกค้าเมื่ออยู่ห่างเกินประมาณ 200 เมตร ระบบจะแจ้งเตือน",
            "คำเตือนระยะทางใช้ช่วยตรวจสอบ ไม่ใช่การบังคับ geofence 250 เมตร"
        )
    ),
    RiderLearningLesson(
        id = "quality",
        title = "คะแนน คำชม และภารกิจ",
        summary = "คะแนนและความคืบหน้ามาจากงานจริงเท่านั้น",
        steps = listOf(
            "ลูกค้าให้คะแนน Rider หลังงานสำเร็จ และคำชมจะแสดงเมื่อมีข้อมูลจริง",
            "ภารกิจ Rider นับจากเที่ยวส่งสำเร็จจริงตามช่วงเวลาที่ Admin กำหนด",
            "งาน Market หลายร้านในเที่ยวเดียวจะนับเป็น 1 เที่ยวสำหรับ Mission"
        )
    ),
    RiderLearningLesson(
        id = "safety",
        title = "ความปลอดภัยและระบบเครื่อง",
        summary = "เช็กสิทธิ์สำคัญและใช้ศูนย์ความปลอดภัยเมื่อจำเป็น",
        steps = listOf(
            "เปิดการแจ้งเตือนเพื่อไม่พลาดข้อเสนองานและสถานะสำคัญ",
            "ใช้เมนูตรวจสอบระบบ Rider เพื่อตรวจอินเทอร์เน็ต GPS Google Maps และปุ่มลอย",
            "ใช้ศูนย์ความปลอดภัยหรือ QueueGo Support เมื่อมีเหตุฉุกเฉินหรือปัญหางาน"
        )
    )
)

@Composable
internal fun RiderLearningCenterScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lessons = remember { riderLearningLessons() }
    var expandedId by remember { mutableStateOf<String?>(lessons.firstOrNull()?.id) }

    Column(
        modifier
            .heightIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ศูนย์เรียนรู้ Rider", fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(
                    "คู่มือสั้นตามขั้นตอนใช้งาน QueueGo ปัจจุบัน",
                    color = QgMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            TextButton(onClick = onClose) {
                Text("ปิด", color = QgRed, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFFFFE2E6)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7F8))
        ) {
            Text(
                "อ่านก่อนเริ่มวิ่งงานครั้งแรก และกลับมาเปิดดูได้ทุกเมื่อจากหน้าโปรไฟล์",
                modifier = Modifier.padding(13.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A3D41)
            )
        }

        Spacer(Modifier.height(10.dp))
        lessons.forEachIndexed { index, lesson ->
            val expanded = expandedId == lesson.id
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable(role = Role.Button) {
                        expandedId = if (expanded) null else lesson.id
                    },
                shape = RoundedCornerShape(17.dp),
                border = BorderStroke(1.dp, Color(0xFFEEE6E8)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(0xFFFFECEF), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (index + 1).toString(),
                                color = QgRed,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(lesson.title, fontSize = 13.sp, fontWeight = FontWeight.Black)
                            Text(
                                lesson.summary,
                                color = QgMuted,
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(if (expanded) "▲" else "▼", color = QgRed, fontSize = 10.sp)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(9.dp))
                        lesson.steps.forEach { step ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("•", color = QgRed, fontWeight = FontWeight.Black)
                                Text(
                                    step,
                                    modifier = Modifier.weight(1f),
                                    color = Color(0xFF4F484C),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            "หากขั้นตอนในงานจริงเปลี่ยนจากคู่มือนี้ ให้ยึดสถานะและปุ่มที่ QueueGo แสดงในงานนั้นเป็นหลัก",
            color = QgMuted,
            fontSize = 8.5.sp,
            lineHeight = 12.sp,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp)
        )
    }
}
