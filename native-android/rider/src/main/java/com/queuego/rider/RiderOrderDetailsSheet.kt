package com.queuego.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Existing Web qgOpenOrderDetails read-only flow; no order/payment mutation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RiderOrderDetailsSheet(auth: QueueGoAuth, job: RiderJob, api: QueueGoApi, onDismiss: () -> Unit) {
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * .78f).dp
    var items by remember(job.id) { mutableStateOf<List<RiderItem>?>(null) }
    var details by remember(job.id) { mutableStateOf<RiderVerificationDetails?>(null) }
    var error by remember(job.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(job.id, auth.session.accessToken) {
        items = null
        error = null
        try {
            coroutineScope {
                val metadata = async { api.verificationDetails(auth, job.id) }
                val rows = async { api.orderItems(auth, job.id) }
                details = metadata.await()
                items = rows.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "โหลดรายการไม่สำเร็จ"
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White,
        sheetMaxWidth = 680.dp, dragHandle = null, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).verticalScroll(rememberScrollState()).padding(horizontal = 15.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("รายการออเดอร์", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(job.numberLabel, fontSize = 8.sp, color = QgMuted)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp).background(Color(0xFFFFF0F3), RoundedCornerShape(99.dp))) {
                    Text("✕", color = QgRed)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFEEE6E8))
            Spacer(Modifier.height(11.dp))
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                items == null -> Text("กำลังโหลดรายการ…", color = QgMuted, fontSize = 12.sp)
                else -> {
                    val rows = items.orEmpty()
                    Row(Modifier.fillMaxWidth().background(Color(0xFFFAF6F7), RoundedCornerShape(14.dp)).padding(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("ร้านค้า", color = QgMuted, fontSize = 8.sp)
                            Text(details?.shopName ?: "ร้านค้า", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("ลูกค้า", color = QgMuted, fontSize = 8.sp)
                            Text(details?.customerName ?: "ลูกค้า", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("รายการสินค้า", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${rows.sumOf { it.quantity }} รายการ", color = QgMuted, fontSize = 10.sp)
                    }
                    if (rows.isEmpty()) Text("ไม่มีรายการสินค้าในออเดอร์นี้", color = QgMuted, fontSize = 12.sp)
                    rows.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!item.imageUrl.isNullOrBlank()) QgRemoteImage(item.imageUrl, Modifier.size(46.dp), cornerRadius = 11.dp, showFallback = false)
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                item.description?.let { Text(it, color = QgMuted, fontSize = 8.sp) }
                                Text("× ${item.quantity}", color = QgMuted, fontSize = 8.sp)
                            }
                            Text(riderMoneyBaht(item.totalPrice), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        HorizontalDivider(color = Color(0xFFF0EAEC))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("รวมค่าสินค้า", fontSize = 12.sp)
                        Text(riderMoneyBaht(rows.sumOf { it.totalPrice }), color = QgRed, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    details?.note?.let {
                        Column(Modifier.fillMaxWidth().background(Color(0xFFFFF4F6), RoundedCornerShape(14.dp)).padding(10.dp)) {
                            Text("หมายเหตุจากลูกค้า", color = QgMuted, fontSize = 8.sp)
                            Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
