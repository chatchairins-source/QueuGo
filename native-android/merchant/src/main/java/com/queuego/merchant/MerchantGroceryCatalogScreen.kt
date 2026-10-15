package com.queuego.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import java.util.UUID

@Composable
internal fun MerchantGroceryCatalogScreen(
    items: List<MerchantGroceryCatalogItem>,
    categories: List<String>,
    query: String,
    category: String,
    mode: String,
    offset: Int,
    hasMore: Boolean,
    gpRate: Double,
    loading: Boolean,
    busy: Boolean,
    onSearch: (String) -> Unit,
    onCategory: (String) -> Unit,
    onMode: (String) -> Unit,
    onPage: (Int) -> Unit,
    onSave: (MerchantGroceryCatalogItem, MerchantGroceryCatalogDraft) -> Unit,
    onBack: () -> Unit
) {
    var searchText by remember(query) { mutableStateOf(query) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item(key = "grocery-catalog-header") {
            Row(
                Modifier.padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !busy,
                    modifier = Modifier.height(40.dp)
                ) { Text("กลับ") }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("เพิ่มสินค้าจากแคตตาล็อก", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "รูปและข้อมูลกลางมาจาก QueueGo · ร้านกำหนดราคาและช่องทางขายเอง",
                        color = QgMuted,
                        fontSize = 9.5.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it.take(100) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("ค้นหาชื่อสินค้า / ยี่ห้อ") }
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSearch(searchText.trim()) },
                    enabled = !busy && !loading,
                    modifier = Modifier.height(56.dp)
                ) { Text("ค้นหา") }
            }

            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GroceryCatalogChip("ทุกหมวด", category.isBlank()) { onCategory("") }
                categories.forEach { value ->
                    GroceryCatalogChip(value, category == value) { onCategory(value) }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "all" to "ทั้งหมด",
                    "unpriced" to "ยังไม่ใส่ราคา",
                    "on" to "เปิดขาย",
                    "off" to "ปิดขาย"
                ).forEach { (key, label) ->
                    GroceryCatalogChip(label, mode == key) { onMode(key) }
                }
            }

            if (loading) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("กำลังโหลดแคตตาล็อก…", color = QgMuted, fontSize = 10.sp)
                }
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }

        if (!loading && items.isEmpty()) {
            item(key = "grocery-catalog-empty") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ไม่พบสินค้าในเงื่อนไขนี้", color = QgMuted, fontSize = 11.sp)
                }
            }
        } else {
            items(items, key = { it.id }) { item ->
                MerchantGroceryCatalogCard(
                    item = item,
                    gpRate = gpRate,
                    busy = busy,
                    onSave = onSave
                )
            }
        }

        item(key = "grocery-catalog-page") {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { onPage((offset - 50).coerceAtLeast(0)) },
                    enabled = !busy && !loading && offset > 0
                ) { Text("ก่อนหน้า") }
                Text("หน้า " + (offset / 50 + 1), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                OutlinedButton(
                    onClick = { onPage(offset + 50) },
                    enabled = !busy && !loading && hasMore
                ) { Text("ถัดไป") }
            }
        }
    }
}

@Composable
private fun GroceryCatalogChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) Color(0xFFFFF0F3) else Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, if (selected) QgRed else Color(0xFFE4E6E9), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) Color(0xFFD90D2D) else Color(0xFF747A82),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MerchantGroceryCatalogCard(
    item: MerchantGroceryCatalogItem,
    gpRate: Double,
    busy: Boolean,
    onSave: (MerchantGroceryCatalogItem, MerchantGroceryCatalogDraft) -> Unit
) {
    var sellingPrice by remember(item.id, item.productUpdatedAt) {
        mutableStateOf(item.sellingPrice?.let { trimCatalogNumber(it) }.orEmpty())
    }
    var stockQty by remember(item.id, item.productUpdatedAt) {
        mutableStateOf(item.stockQty.toString())
    }
    var stockTracking by remember(item.id, item.productUpdatedAt) {
        mutableStateOf(item.stockTracking)
    }
    var posAvailable by remember(item.id, item.productUpdatedAt) {
        mutableStateOf(item.posAvailable)
    }
    var deliveryAvailable by remember(item.id, item.productUpdatedAt) {
        mutableStateOf(item.deliveryAvailable)
    }
    val requestId = remember(item.id, item.productUpdatedAt) { UUID.randomUUID().toString() }

    val storePrice = sellingPrice.toDoubleOrNull()
    val deliveryPrice = if (storePrice != null && storePrice > 0.0 && gpRate in 0.0..100.0) {
        merchantDeliveryPriceFromStore(storePrice, gpRate)
    } else null

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, QgLine, RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            QgRemoteImage(
                source = item.image,
                modifier = Modifier.size(width = 112.dp, height = 132.dp),
                fallback = item.name,
                cornerRadius = 12.dp,
                maxDecodeSide = 384
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                val detail = buildString {
                    if (item.brand.isNotBlank()) append(item.brand)
                    if (item.variant.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(item.variant)
                    }
                    if (item.sizeValue > 0.0) {
                        if (isNotEmpty()) append(" · ")
                        append(trimCatalogNumber(item.sizeValue)).append(" ").append(item.sizeUnit)
                    }
                    if (item.packSize > 1) append(" × ").append(item.packSize)
                }
                if (detail.isNotBlank()) Text(detail, color = QgMuted, fontSize = 9.sp)
                if (item.category.isNotBlank()) Text(item.category, color = QgMuted, fontSize = 9.sp)
                Text(
                    if (item.enabled) "อยู่ในร้านแล้ว" else "ยังไม่ได้เปิดขาย",
                    color = if (item.enabled) Color(0xFF0A9660) else QgMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = sellingPrice,
                onValueChange = { value ->
                    sellingPrice = value.filter { it.isDigit() || it == '.' }.take(12)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("ราคาหน้าร้าน") }
            )
            OutlinedTextField(
                value = stockQty,
                onValueChange = { stockQty = it.filter(Char::isDigit).take(8) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("สต็อก") }
            )
        }

        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = posAvailable,
                onCheckedChange = { posAvailable = it },
                enabled = !busy
            )
            Text("ขายหน้าร้าน", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Checkbox(
                checked = deliveryAvailable,
                onCheckedChange = { deliveryAvailable = it },
                enabled = !busy
            )
            Text("เดลิเวอรี่ลูกค้า", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("นับสต็อก", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = stockTracking,
                onCheckedChange = { stockTracking = it },
                enabled = !busy
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF6F7F8), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Text(
                if (deliveryPrice != null) {
                    "GP " + trimCatalogNumber(gpRate) + "% · ราคาเดลิเวอรี่ " +
                        "%.2f".format(deliveryPrice) + " บาท"
                } else {
                    "ราคาเดลิเวอรี่คำนวณ GP อัตโนมัติจากราคาหน้าร้าน"
                },
                color = Color(0xFF555B63),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onSave(
                    item,
                    MerchantGroceryCatalogDraft(
                        requestId = requestId,
                        sellingPrice = sellingPrice.toDoubleOrNull(),
                        stockQty = stockQty.toIntOrNull() ?: 0,
                        stockTracking = stockTracking,
                        posAvailable = posAvailable,
                        deliveryAvailable = deliveryAvailable
                    )
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(
                if (item.enabled || item.sellingPrice != null) "บันทึกราคา / ช่องทาง"
                else "เพิ่มสินค้าเข้าร้าน",
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

private fun trimCatalogNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else value.toString().trimEnd('0').trimEnd('.')
