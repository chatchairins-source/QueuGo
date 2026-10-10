package com.queuego.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgLine
import com.queuego.shared.QgLongdoLocationPickerMap
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun CustomerCartScreen(
    cart: List<CartLine>,
    busy: Boolean,
    checkoutPending: Boolean,
    onBack: () -> Unit,
    onMinus: (String) -> Unit,
    onPlus: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCheckout: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 96.dp)
        ) {
            CustomerTransactionHeader("ตะกร้าสินค้า", onBack)
            if (cart.isEmpty()) {
                Text(
                    "ตะกร้าว่าง",
                    color = QgMuted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 35.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                return@Column
            }

            cart.forEach { line ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    QgRemoteImage(
                        source = line.product.image,
                        modifier = Modifier.width(62.dp).height(56.dp),
                        fallback = line.product.name,
                        cornerRadius = 12.dp
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            line.product.name,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(3.dp))
                        val optionLabel = runCatching { customerCartLineOptionsLabel(line) }.getOrDefault("")
                        if (optionLabel.isNotBlank()) {
                            Text(optionLabel, fontSize = 10.sp, color = QgMuted, maxLines = 2)
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            "฿" + "%.0f".format(customerCartLineUnitPrice(line)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CustomerQtyButton("−", enabled = !checkoutPending) { onMinus(customerCartLineKey(line)) }
                            Text(
                                line.quantity.toString(),
                                modifier = Modifier.widthIn(min = 18.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            CustomerQtyButton("+", enabled = !checkoutPending) { onPlus(customerCartLineKey(line)) }
                            CustomerQtyButton("ลบ", compact = true, enabled = !checkoutPending) { onRemove(customerCartLineKey(line)) }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = QgLine)
            }

            val subtotal = cart.sumOf { customerCartLineUnitPrice(it) * it.quantity }
            Spacer(Modifier.height(10.dp))
            CustomerSummaryCard(
                rows = listOf("ค่าอาหาร" to subtotal),
                totalLabel = "รวม",
                total = subtotal
            )
            Spacer(Modifier.height(24.dp))
        }

        if (cart.isNotEmpty()) {
            CustomerActionDock(
                label = when {
                    busy -> "กำลังตรวจสอบ..."
                    checkoutPending -> "ตรวจผลคำสั่งซื้อเดิม"
                    else -> "ไปชำระเงิน"
                },
                enabled = !busy,
                onClick = onCheckout
            )
        }
    }
}

@Composable
internal fun CustomerCheckoutScreen(
    pendingCheckout: PendingCustomerCheckout?,
    cart: List<CartLine>,
    shop: CustomerShop?,
    location: CustomerLocation?,
    address: String,
    note: String,
    busy: Boolean,
    resolveAddress: suspend (Double, Double) -> String?,
    onBack: () -> Unit,
    onGps: () -> Unit,
    onLocationChange: (CustomerLocation) -> Unit,
    onAddress: (String) -> Unit,
    onNote: (String) -> Unit,
    onPlace: () -> Unit
) {
    val checkoutPending = pendingCheckout != null
    val pendingBody = pendingCheckout?.body
    val pendingLocation = pendingBody?.let { body ->
        runCatching {
            CustomerLocation(
                body.getDouble("p_delivery_lat"),
                body.getDouble("p_delivery_lng"),
                body.getString("p_delivery_address")
            )
        }.getOrNull()
    }
    var selected by remember(pendingCheckout?.body?.optString("p_order_id")) {
        mutableStateOf(
            pendingLocation?.takeIf { validCheckoutPoint(it.latitude, it.longitude) }
                ?: location?.takeIf { validCheckoutPoint(it.latitude, it.longitude) }
        )
    }
    var recenterToken by remember { mutableStateOf(0) }
    var mapReady by remember { mutableStateOf(false) }
    var lastAutoAddress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(location?.latitude, location?.longitude) {
        val next = location?.takeIf { validCheckoutPoint(it.latitude, it.longitude) } ?: return@LaunchedEffect
        val current = selected
        if (current == null ||
            abs(current.latitude - next.latitude) > 0.000001 ||
            abs(current.longitude - next.longitude) > 0.000001
        ) {
            selected = next.copy(address = address)
            recenterToken++
        }
    }

    LaunchedEffect(selected?.latitude, selected?.longitude) {
        if (checkoutPending) return@LaunchedEffect
        val point = selected ?: return@LaunchedEffect
        if (!(address.isBlank() || address == lastAutoAddress)) return@LaunchedEffect
        delay(350)
        val resolved = resolveAddress(point.latitude, point.longitude)?.trim().orEmpty()
        if (resolved.isNotBlank() && (address.isBlank() || address == lastAutoAddress)) {
            lastAutoAddress = resolved
            onAddress(resolved)
            onLocationChange(point.copy(address = resolved))
        }
    }

    val subtotal = pendingBody?.optDouble("p_expected_subtotal")
        ?.takeIf { it.isFinite() }
        ?: cart.sumOf { customerCartLineUnitPrice(it) * it.quantity }
    val fee = if (checkoutPending) {
        pendingBody?.optDouble("p_expected_delivery_fee")?.takeIf { it.isFinite() }
    } else {
        runCatching {
            if (shop != null && selected != null) CustomerApi().deliveryFee(shop, selected!!.copy(address = address))
            else null
        }.getOrNull()
    }
    val pendingMessage = if (checkoutPending) {
        "คำขอนี้ยังไม่ได้รับผลยืนยัน ระบบจะใช้รายการและที่อยู่เดิมเพื่อป้องกันออเดอร์ซ้ำ"
    } else null
    val visibleAddress = pendingBody?.optString("p_delivery_address")?.takeIf { it.isNotBlank() } ?: address
    val visibleNote = pendingBody?.optString("p_note")?.takeIf { it.isNotBlank() && it != "null" } ?: note

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 108.dp)
        ) {
            CustomerTransactionHeader("ยืนยันคำสั่งซื้อ", onBack)

            if (checkoutPending) {
                CustomerCheckoutCard {
                    Text("ตรวจผลคำสั่งซื้อเดิม", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text(pendingMessage.orEmpty(), color = QgMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    if (visibleAddress.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(visibleAddress, fontSize = 12.sp)
                    }
                    if (visibleNote.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("หมายเหตุ: " + visibleNote, color = QgMuted, fontSize = 11.sp)
                    }
                }
            } else {
                CustomerCheckoutCard {
                    Text(shop?.name ?: "ร้านค้า", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    Text("ชำระเงินสด • ระบบเดลิเวอรี่ QueueGo", color = QgMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color(0xFFF0F1F2))
                            .border(1.dp, QgLine, RoundedCornerShape(15.dp))
                    ) {
                        QgLongdoLocationPickerMap(
                            initialPoint = selected?.let { QgMapPoint(it.latitude, it.longitude, 0) },
                            recenterPoint = location?.takeIf { validCheckoutPoint(it.latitude, it.longitude) }
                                ?.let { QgMapPoint(it.latitude, it.longitude, 0) },
                            recenterToken = recenterToken,
                            modifier = Modifier.fillMaxSize(),
                            onCenterChanged = { point ->
                                val next = CustomerLocation(point.latitude, point.longitude, address)
                                selected = next
                                onLocationChange(next)
                            },
                            onReady = { mapReady = it }
                        )
                        if (!mapReady) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(26.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onGps,
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text("ใช้ตำแหน่ง GPS ปัจจุบัน", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        selected?.let { "Lat: %.6f · Lng: %.6f".format(it.latitude, it.longitude) }
                            ?: "Lat: -- · Lng: --",
                        color = QgMuted,
                        fontSize = 10.sp
                    )

                    Spacer(Modifier.height(10.dp))
                    Text("ที่อยู่จัดส่ง", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedTextField(
                        value = address,
                        onValueChange = {
                            onAddress(it)
                            selected?.let { point -> onLocationChange(point.copy(address = it)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(Modifier.height(10.dp))
                    Text("หมายเหตุสำหรับร้าน", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedTextField(
                        value = note,
                        onValueChange = onNote,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            CustomerSummaryCard(
                rows = listOf(
                    "ค่าสินค้า" to subtotal,
                    "ค่าจัดส่ง" to fee
                ),
                totalLabel = "ยอดรวม",
                total = if (fee == null) subtotal else subtotal + fee,
                nullAmountLabel = "เลือกตำแหน่งจัดส่ง"
            )
            Spacer(Modifier.height(28.dp))
        }

        CustomerActionDock(
            label = when {
                busy -> "กำลังดำเนินการ..."
                checkoutPending -> "ตรวจผลคำสั่งซื้อเดิม"
                else -> "ยืนยันสั่งซื้อ"
            },
            enabled = !busy && (checkoutPending || (selected != null && address.isNotBlank() && fee != null && cart.isNotEmpty())),
            onClick = onPlace
        )
    }
}

@Composable
private fun CustomerTransactionHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.qg_shop_back),
                contentDescription = "ย้อนกลับ",
                tint = Color(0xFF24272D),
                modifier = Modifier.size(20.dp)
            )
        }
        Text(title, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun CustomerQtyButton(
    label: String,
    compact: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFDDDDDD), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) Color(0xFF202226) else Color(0xFFB8BABE),
            fontSize = if (compact) 9.sp else 16.sp,
            lineHeight = if (compact) 10.sp else 18.sp,
            fontWeight = if (compact) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun CustomerSummaryCard(
    rows: List<Pair<String, Double?>>,
    totalLabel: String,
    total: Double,
    nullAmountLabel: String = "--"
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, QgLine, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        rows.forEach { (label, amount) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text(
                    amount?.let { "฿" + "%.0f".format(it) } ?: nullAmountLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 2.dp, bottom = 6.dp), color = QgLine)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(totalLabel, modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("฿" + "%.0f".format(total), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun CustomerCheckoutCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, QgLine, RoundedCornerShape(16.dp))
            .padding(13.dp),
        content = content
    )
}

@Composable
private fun BoxScope.CustomerActionDock(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF7FFFFFF))
            .border(1.dp, Color(0xFFECE5E8), RoundedCornerShape(20.dp))
            .padding(8.dp)
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF04455),
                disabledContainerColor = Color(0x80F04455)
            )
        ) {
            Text(label, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun validCheckoutPoint(latitude: Double, longitude: Double): Boolean =
    latitude in 5.0..21.0 && longitude in 97.0..106.0 && !(latitude == 0.0 && longitude == 0.0)
