package com.queuego.merchant

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import kotlinx.coroutines.launch

private val merchantMarketUnits = listOf(
    "ชิ้น", "กิโลกรัม", "ขีด", "กรัม", "ถุง",
    "แพ็ก", "มัด", "ลูก", "ขวด", "กล่อง"
)

@Composable
internal fun MerchantMarketStockScreen(
    auth: NativeAuth,
    shop: MerchantShop,
    api: MerchantApi,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(shop.id) { mutableStateOf(MerchantMarketStockState(emptyList(), emptyList())) }
    var loading by remember(shop.id) { mutableStateOf(true) }
    var busy by remember(shop.id) { mutableStateOf(false) }
    var message by remember(shop.id) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<MerchantMarketProduct?>(null) }
    var adding by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf<MerchantMarketProduct?>(null) }

    suspend fun refresh() {
        loading = true
        runCatching { api.loadMarketStock(auth, shop.id) }
            .onSuccess { state = it; message = null }
            .onFailure { message = it.message ?: "โหลดข้อมูลสินค้าไม่สำเร็จ" }
        loading = false
    }

    LaunchedEffect(shop.id) { refresh() }

    if (!merchantMarketStockPermitted(shop.category)) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "เลือกประเภทร้านเป็นตลาดสด ร้านเนื้อ ปลา ผัก ผลไม้ หรือของชำในข้อมูลร้านก่อน",
                color = QgMuted
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack) { Text("กลับ") }
        }
        return
    }

    if (adding || editing != null) {
        MerchantMarketProductEditor(
            product = editing,
            busy = busy,
            onBack = {
                adding = false
                editing = null
            },
            onSave = { draft, imageUri ->
                if (!busy) {
                    busy = true
                    scope.launch {
                        runCatching {
                            val image = imageUri?.let {
                                uploadMerchantImage(context, auth, it, "market-product")
                            }
                            api.saveMarketProduct(
                                auth,
                                draft.copy(image = image ?: draft.image)
                            )
                            api.loadMarketStock(auth, shop.id)
                        }.onSuccess {
                            state = it
                            message = "บันทึกสินค้าแล้ว"
                            adding = false
                            editing = null
                        }.onFailure {
                            message = it.message ?: "บันทึกสินค้าไม่สำเร็จ"
                        }
                        busy = false
                    }
                }
            }
        )
        return
    }

    if (adjusting != null) {
        MerchantMarketStockAdjust(
            product = adjusting!!,
            busy = busy,
            onBack = { adjusting = null },
            onSave = { delta, note ->
                if (!busy) {
                    busy = true
                    scope.launch {
                        runCatching {
                            require(adjusting!!.stockQuantity + delta >= 0.0) { "สต๊อกไม่พอ" }
                            api.adjustMarketStock(auth, adjusting!!.productId, delta, note)
                            api.loadMarketStock(auth, shop.id)
                        }.onSuccess {
                            state = it
                            message = "ปรับสต๊อกแล้ว"
                            adjusting = null
                        }.onFailure {
                            message = it.message ?: "ปรับสต๊อกไม่สำเร็จ"
                        }
                        busy = false
                    }
                }
            }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.padding(top = 7.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("หน้าหลัก") }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("สินค้าและสต๊อกตลาด", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "ขายผ่าน QueueGo Delivery · ราคาขายและสต๊อกยืนยันจากฐานข้อมูล",
                    color = QgMuted,
                    fontSize = 9.5.sp
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { adding = true; editing = null },
                modifier = Modifier.weight(1f)
            ) { Text("＋ เพิ่มสินค้า") }
            OutlinedButton(
                onClick = { scope.launch { refresh() } },
                enabled = !loading,
                modifier = Modifier.weight(1f)
            ) { Text("อัปเดต") }
        }

        if (!message.isNullOrBlank()) {
            Text(
                message!!,
                color = if (message!!.contains("แล้ว")) Color(0xFF0A9660) else QgRed,
                fontSize = 10.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        when {
            loading -> Box(
                Modifier.fillMaxWidth().height(150.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.products.isEmpty() -> Text(
                "ยังไม่มีสินค้าในตลาด กด “เพิ่มสินค้า” เพื่อเริ่มขาย",
                color = QgMuted,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            else -> state.products.forEach { product ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Color.White, RoundedCornerShape(15.dp))
                        .border(1.dp, QgLine, RoundedCornerShape(15.dp))
                        .padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QgRemoteImage(
                        product.image,
                        Modifier.size(68.dp),
                        product.name,
                        cornerRadius = 12.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(product.name, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            product.category + " · " +
                                "%.3f".format(product.packSize).trimEnd('0').trimEnd('.') +
                                " " + product.unit + " / ชุด · " +
                                "%.0f".format(product.deliveryPrice) + " ฿",
                            color = QgMuted,
                            fontSize = 8.8.sp,
                            lineHeight = 12.sp
                        )
                        val sellable = kotlin.math.floor(product.stockQuantity / product.packSize).toInt()
                        Text(
                            "คงเหลือ %.3f %s · ขายได้ %d ชุด%s".format(
                                product.stockQuantity,
                                product.unit,
                                sellable,
                                if (product.stockQuantity <= product.minStock) " · ใกล้หมด" else ""
                            ),
                            color = if (product.stockQuantity <= product.minStock) QgRed else QgMuted,
                            fontSize = 8.8.sp
                        )
                        Text(
                            "ต้นทุน %.0f ฿ / ชุด".format(product.costPrice),
                            color = QgMuted,
                            fontSize = 8.5.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        OutlinedButton(
                            onClick = { editing = product },
                            contentPadding = PaddingValues(horizontal = 9.dp)
                        ) { Text("แก้ไข", fontSize = 9.sp) }
                        OutlinedButton(
                            onClick = { adjusting = product },
                            contentPadding = PaddingValues(horizontal = 7.dp)
                        ) { Text("รับเข้า / ปรับยอด", fontSize = 8.5.sp) }
                    }
                }
            }
        }

        if (state.movements.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("ประวัติสต๊อกล่าสุด", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            state.movements.forEach { movement ->
                Text(
                    (movement.createdAt ?: "") + " · " +
                        movement.type + " " + movement.quantity + " · " +
                        movement.note.orEmpty(),
                    color = QgMuted,
                    fontSize = 8.8.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MerchantMarketProductEditor(
    product: MerchantMarketProduct?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (MerchantMarketProductDraft, Uri?) -> Unit
) {
    var name by remember(product?.productId) { mutableStateOf(product?.name.orEmpty()) }
    var category by remember(product?.productId) { mutableStateOf(product?.category.orEmpty()) }
    var categoryOpen by remember { mutableStateOf(false) }
    var price by remember(product?.productId) {
        mutableStateOf((product?.deliveryPrice ?: product?.price)?.toString().orEmpty())
    }
    var unit by remember(product?.productId) { mutableStateOf(product?.unit ?: "ชิ้น") }
    var unitOpen by remember { mutableStateOf(false) }
    var pack by remember(product?.productId) { mutableStateOf(product?.packSize?.toString() ?: "1") }
    var weight by remember(product?.productId) { mutableStateOf(product?.weightKg?.toString().orEmpty()) }
    var cost by remember(product?.productId) { mutableStateOf(product?.costPrice?.toString() ?: "0") }
    var stock by remember(product?.productId) { mutableStateOf("0") }
    var minStock by remember(product?.productId) { mutableStateOf(product?.minStock?.toString() ?: "0") }
    var available by remember(product?.productId) { mutableStateOf(product?.available ?: true) }
    var imageUri by remember(product?.productId) { mutableStateOf<Uri?>(null) }
    var error by remember(product?.productId) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) imageUri = it
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.padding(top = 7.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("ยกเลิก") }
            Spacer(Modifier.width(10.dp))
            Text(
                if (product == null) "เพิ่มสินค้า" else "แก้ไขสินค้า",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(),
            label = { Text("ชื่อสินค้า") }, singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { categoryOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(category.ifBlank { "เลือกหมวดหมู่สินค้า" }) }
            DropdownMenu(categoryOpen, { categoryOpen = false }) {
                merchantMarketProductCategories.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { category = option; categoryOpen = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            price,
            { price = it.filter { ch -> ch.isDigit() || ch == '.' } },
            Modifier.fillMaxWidth(),
            label = { Text("ราคา / ชุด (บาท)") },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { unitOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("หน่วยสต๊อก: $unit")
            }
            DropdownMenu(unitOpen, { unitOpen = false }) {
                merchantMarketUnits.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { unit = option; unitOpen = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarketNumberField(pack, { pack = it }, "ปริมาณต่อ 1 ชุดขาย", Modifier.weight(1f))
            MarketNumberField(weight, { weight = it }, "น้ำหนักต่อชุด (กก.)", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarketNumberField(cost, { cost = it }, "ต้นทุนต่อชุด", Modifier.weight(1f))
            MarketNumberField(minStock, { minStock = it }, "แจ้งเตือนใกล้หมด", Modifier.weight(1f))
        }
        if (product == null) {
            Spacer(Modifier.height(8.dp))
            MarketNumberField(stock, { stock = it }, "สต๊อกเริ่มต้น", Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { picker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("เลือกรูปสินค้า") }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("เปิดขาย Delivery", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Switch(available, { available = it })
        }

        if (!error.isNullOrBlank()) {
            Text(error!!, color = QgRed, fontSize = 10.sp)
        }

        Button(
            onClick = {
                runCatching {
                    val draft = MerchantMarketProductDraft(
                        productId = product?.productId,
                        name = name,
                        category = category,
                        price = price.toDoubleOrNull() ?: error("กรุณากรอกราคา"),
                        image = product?.image,
                        unit = unit,
                        packSize = pack.toDoubleOrNull() ?: error("กรุณากรอกปริมาณต่อชุด"),
                        weightKg = weight.toDoubleOrNull() ?: error("กรุณากรอกน้ำหนักต่อชุด"),
                        costPrice = cost.toDoubleOrNull() ?: error("กรุณากรอกต้นทุน"),
                        initialStock = if (product == null) stock.toDoubleOrNull()
                            ?: error("กรุณากรอกสต๊อกเริ่มต้น") else 0.0,
                        minStock = minStock.toDoubleOrNull()
                            ?: error("กรุณากรอกระดับแจ้งเตือน"),
                        available = available
                    )
                    require(name.isNotBlank()) { "กรุณากรอกชื่อสินค้า" }
                    require(category in merchantMarketProductCategories) { "กรุณาเลือกหมวดหมู่สินค้า" }
                    draft
                }.onSuccess {
                    error = null
                    onSave(it, imageUri)
                }.onFailure {
                    error = it.message ?: "ข้อมูลสินค้าไม่ถูกต้อง"
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("บันทึก", fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MarketNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
        modifier = modifier,
        label = { Text(label, fontSize = 9.sp) },
        singleLine = true
    )
}

@Composable
private fun MerchantMarketStockAdjust(
    product: MerchantMarketProduct,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (Double, String) -> Unit
) {
    var delta by remember(product.productId) { mutableStateOf("") }
    var note by remember(product.productId) { mutableStateOf("รับเข้า/ตรวจนับ") }
    var error by remember(product.productId) { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)
    ) {
        Text("รับเข้า / ปรับยอด", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(product.name, color = QgMuted, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
        Text(
            "คงเหลือ %.3f %s".format(product.stockQuantity, product.unit),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            delta,
            {
                delta = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }
                error = null
            },
            Modifier.fillMaxWidth(),
            label = { Text("จำนวนปรับ (+ รับเข้า / - ปรับลด)") },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            note,
            { note = it },
            Modifier.fillMaxWidth(),
            label = { Text("เหตุผลการปรับยอด") },
            minLines = 2
        )
        if (!error.isNullOrBlank()) Text(error!!, color = QgRed, fontSize = 10.sp)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val value = delta.toDoubleOrNull()
                if (value == null || value == 0.0) error = "ระบุจำนวนที่ไม่เป็นศูนย์"
                else if (product.stockQuantity + value < 0.0) error = "สต๊อกไม่พอ"
                else if (note.trim().isBlank()) error = "กรุณาระบุเหตุผล"
                else onSave(value, note)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("บันทึกการปรับสต๊อก") }
        OutlinedButton(
            onClick = onBack,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("ยกเลิก") }
    }
}
