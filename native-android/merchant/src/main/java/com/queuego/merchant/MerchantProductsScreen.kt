package com.queuego.merchant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgIcon
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal fun merchantProductAddLabel(shopCategory: String): String =
    if (shopCategory.lowercase() in setOf("food", "cafe")) "เพิ่มเมนู" else "เพิ่มสินค้า"

@Composable
internal fun MerchantProductsScreen(
    products: List<MerchantProduct>,
    shopCategory: String,
    busy: Boolean,
    onToggle: (MerchantProduct, Boolean) -> Unit,
    onOpen: (MerchantProduct) -> Unit,
    onAddManual: () -> Unit,
    onOpenCatalog: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    val hasMasterCatalog = shopCategory == "grocery" || merchantMarketStockPermitted(shopCategory)
    val pricedProducts = merchantPricedProducts(products, shopCategory == "grocery")
    val categories = remember(pricedProducts) {
        pricedProducts.map { it.category.ifBlank { "ไม่ระบุหมวด" } }.distinct().sorted()
    }
    LaunchedEffect(categories) {
        if (selectedCategory.isNotBlank() && selectedCategory !in categories) selectedCategory = ""
    }
    val rows = remember(pricedProducts, mode, query, selectedCategory) {
        pricedProducts.filter { product ->
            merchantProductMatchesView(product, query, selectedCategory) &&
                (mode == "all" ||
                    (mode == "on" && product.available) ||
                    (mode == "off" && !product.available))
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = if (hasMasterCatalog) 142.dp else 86.dp)
    ) {
        item(key = "products-header") {
            Spacer(Modifier.height(8.dp))
            Text(
                if (shopCategory == "grocery") "สินค้าในร้าน" else "สินค้าและเมนู",
                fontSize = MerchantPageTitleSize,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                if (shopCategory == "grocery") "เพิ่มสินค้าเอง หรือเลือกจากแคตตาล็อก QueueGo"
                else "เปิด–ปิดขาย แก้ไขราคา และจัดการสต็อก",
                color = QgMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("ค้นหาสินค้า...") }
            )
            if (shopCategory == "grocery") {
                Box {
                    OutlinedButton(
                        onClick = { categoryMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("หมวดหมู่สินค้า: " + (selectedCategory.ifBlank { "ทุกหมวด" }), maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("ทุกหมวด") },
                            onClick = { selectedCategory = ""; categoryMenuOpen = false }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = { selectedCategory = category; categoryMenuOpen = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MerchantProductFilter("ทั้งหมด", "all", mode) { mode = it }
                MerchantProductFilter("เปิดขาย", "on", mode) { mode = it }
                MerchantProductFilter("ปิดขาย", "off", mode) { mode = it }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (rows.isEmpty()) {
            item(key = "products-empty") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (products.isEmpty()) "ยังไม่มีสินค้า" else "ไม่มีสินค้าในสถานะนี้",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        } else {
            items(rows, key = { it.id }) { product ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                        .clickable { onOpen(product) }
                        .padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QgRemoteImage(
                        source = product.image ?: product.catalogPreviewImage,
                        modifier = Modifier.size(66.dp),
                        fallback = product.name,
                        cornerRadius = 12.dp,
                        maxDecodeSide = 256
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            product.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "%.0f บาท".format(product.posPrice ?: product.price),
                            color = QgRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (product.category.isNotBlank()) {
                            Text(product.category, color = QgMuted, fontSize = 9.sp, maxLines = 1)
                        }
                        Text(
                            if (product.available) "เปิดขาย" else "ปิดขาย",
                            color = if (product.available) Color(0xFF0A9660) else QgMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = product.available,
                        onCheckedChange = { onToggle(product, it) },
                        enabled = !busy
                    )
                }
            }
        }

    }
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFFECEEF1), RoundedCornerShape(18.dp))
            .padding(8.dp)
    ) {
        if (hasMasterCatalog) {
            OutlinedButton(
                onClick = onAddManual,
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = QgRed),
                border = BorderStroke(1.dp, Color(0xFFEDC7CF)),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text("เพิ่มสินค้าเอง", fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onOpenCatalog,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = QgRed),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text("เพิ่มสินค้าจากแคตตาล็อก", fontWeight = FontWeight.ExtraBold) }
        } else {
            OutlinedButton(
                onClick = onOpenCatalog,
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = QgRed),
                border = BorderStroke(1.dp, Color(0xFFEDC7CF)),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text(
                    merchantProductAddLabel(shopCategory),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
    }
}

@Composable
private fun MerchantProductFilter(
    label: String,
    key: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    val active = selected == key
    Box(
        Modifier
            .background(if (active) Color(0xFFFFF0F3) else Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, if (active) QgRed else Color(0xFFE4E6E9), RoundedCornerShape(18.dp))
            .clickable { onSelect(key) }
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (active) Color(0xFFD90D2D) else Color(0xFF747A82),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun MerchantProductEditorScreen(
    shop: MerchantShop,
    product: MerchantProduct?,
    initialCategory: String? = null,
    gpRate: Double,
    busy: Boolean,
    onSave: (MerchantProductDraft, Uri?) -> Unit,
    onDelete: (MerchantProduct) -> Unit,
    onAdjustInventory: suspend (MerchantInventoryAdjustmentIntent) -> MerchantInventoryAdjustmentReceipt,
    onLoadInventoryHistory: suspend (MerchantProduct, String?, String?) -> MerchantInventoryAdjustmentPage,
    onBack: () -> Unit
) {
    val categories = remember(shop.category, shop.shoppingSubcategories, product?.category, initialCategory) {
        merchantProductCategories(shop, listOfNotNull(product?.category, initialCategory))
    }
    val food = merchantIsFoodProductShop(shop)
    val initialOptions = remember(product?.id, product?.variantsJson) {
        merchantRestaurantOptionState(product?.variantsJson ?: "[]")
    }
    val requestId = remember(product?.id) { product?.id ?: UUID.randomUUID().toString() }

    var name by remember(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var description by remember(product?.id) { mutableStateOf(product?.description.orEmpty()) }
    var category by remember(product?.id, initialCategory) {
        mutableStateOf(
            product?.category?.takeIf { it in categories }
                ?: initialCategory?.takeIf { it in categories }
                ?: merchantDefaultProductCategory(shop).takeIf { it in categories }
                ?: ""
        )
    }
    var categoryOpen by remember { mutableStateOf(false) }
    var newCategoryDialogOpen by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var addedCategories by remember(product?.id) { mutableStateOf(emptyList<String>()) }
    val allowCustomCategory = !food && !merchantIsMarketProductShop(shop) && shop.category.lowercase() != "shopping"
    val productCategories = remember(categories, addedCategories) { (categories + addedCategories).distinct() }
    var price by remember(product?.id) { mutableStateOf((product?.posPrice ?: product?.price)?.toString().orEmpty()) }
    var stock by remember(product?.id) { mutableStateOf(product?.stock?.toString() ?: "0") }
    var inventoryStockTracking by remember(product?.id) { mutableStateOf(product?.inventoryStockTracking ?: false) }
    var inventoryAdjustOpen by remember { mutableStateOf(false) }
    var inventoryHistoryOpen by remember { mutableStateOf(false) }
    var inventoryBusy by remember { mutableStateOf(false) }
    var inventoryError by remember { mutableStateOf<String?>(null) }
    var inventoryHistory by remember(product?.id) { mutableStateOf(MerchantInventoryAdjustmentPage(emptyList(), false)) }
    var inventoryRequestKey by rememberSaveable(product?.id) { mutableStateOf(UUID.randomUUID().toString()) }
    val inventoryScope = rememberCoroutineScope()
    var available by remember(product?.id) { mutableStateOf(product?.available ?: true) }
    var posAvailable by remember(product?.id) { mutableStateOf(product?.posAvailable ?: true) }
    var deliveryAvailable by remember(product?.id) { mutableStateOf(product?.deliveryAvailable ?: true) }
    var portionEnabled by remember(product?.id) { mutableStateOf(initialOptions.portionEnabled) }
    var specialPrice by remember(product?.id) { mutableStateOf(initialOptions.specialPrice.toString()) }
    var toppings by remember(product?.id) { mutableStateOf(initialOptions.toppings) }
    var genericVariants by remember(product?.id) {
        mutableStateOf(merchantGenericVariantText(product?.variantsJson ?: "[]"))
    }
    var imageUri by remember(product?.id) { mutableStateOf<Uri?>(null) }
    var deleteArmed by remember(product?.id) { mutableStateOf(false) }
    var localError by remember(product?.id) { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) imageUri = it
    }

    val storePrice = price.toDoubleOrNull()
    val gpRateValid = gpRate in 0.0..100.0 && gpRate.isFinite()
    val deliveryPrice = storePrice?.takeIf { gpRateValid }?.let { merchantDeliveryPriceFromStore(it, gpRate) }
    val restriction = merchantDeliveryRestriction(name, category, description)
    val effectiveDelivery = restriction == "none" && deliveryAvailable

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.padding(top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("กลับ") }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (product == null) "เพิ่มสินค้า" else "แก้ไขสินค้า",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "ข้อมูลสินค้า ราคา สต็อก และช่องทางการขาย",
                    color = QgMuted,
                    fontSize = 9.5.sp
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (imageUri != null) {
                    MerchantProductLocalImage(
                        imageUri!!,
                        Modifier.size(70.dp)
                    )
                } else {
                    QgRemoteImage(
                        product?.image,
                        Modifier.size(70.dp),
                        product?.name ?: "สินค้า",
                        cornerRadius = 13.dp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name.ifBlank { "สินค้าใหม่" },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    Text(
                        category.ifBlank { "เลือกหมวดหมู่" },
                        color = QgMuted,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; localError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ชื่อสินค้า *") },
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))
            Text("หมวดหมู่ *", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { categoryOpen = true },
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text(category.ifBlank { "เลือกหมวดหมู่สินค้า" })
                }
                DropdownMenu(
                    expanded = categoryOpen,
                    onDismissRequest = { categoryOpen = false }
                ) {
                    productCategories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                category = option
                                categoryOpen = false
                                localError = null
                            }
                        )
                    }
                    if (allowCustomCategory) {
                        DropdownMenuItem(
                            text = { Text("＋ เพิ่มหมวดหมู่ใหม่") },
                            onClick = {
                                categoryOpen = false
                                newCategoryInput = ""
                                newCategoryDialogOpen = true
                            }
                        )
                    }
                }
            }

            if (newCategoryDialogOpen) {
                AlertDialog(
                    onDismissRequest = { newCategoryDialogOpen = false },
                    title = { Text("เพิ่มหมวดหมู่สินค้า") },
                    text = {
                        OutlinedTextField(
                            value = newCategoryInput,
                            onValueChange = { newCategoryInput = it.take(60) },
                            label = { Text("ชื่อหมวดหมู่") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val clean = newCategoryInput.trim()
                            when {
                                clean.isBlank() -> localError = "กรุณากรอกชื่อหมวดหมู่"
                                productCategories.any { it.equals(clean, ignoreCase = true) } -> {
                                    category = productCategories.first { it.equals(clean, ignoreCase = true) }
                                    newCategoryDialogOpen = false
                                    localError = null
                                }
                                else -> {
                                    addedCategories = addedCategories + clean
                                    category = clean
                                    newCategoryDialogOpen = false
                                    localError = null
                                }
                            }
                        }) { Text("เพิ่ม") }
                    },
                    dismissButton = {
                        TextButton(onClick = { newCategoryDialogOpen = false }) { Text("ยกเลิก") }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    modifier = Modifier.weight(1f),
                    label = { Text("ราคาขายหน้าร้าน *") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { if (product?.inventoryStockTracking != true) stock = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text(if (product?.inventoryStockTracking == true) "จำนวนในสต็อก" else "จำนวนในสต็อก *") },
                    singleLine = true,
                    readOnly = product?.inventoryStockTracking == true && product.groceryMasterId == null
                )
            }

            if (product?.groceryMasterId != null) {
                Text(
                    if (product.groceryStockTracking) "สต็อก: จัดการผ่าน Grocery Master · ระบบติดตามสต็อกกลางทำงานอยู่"
                    else "สต็อก: จัดการผ่าน Grocery Master",
                    color = QgMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                ProductSwitchRow(
                    "ติดตามและกันสต็อกอัตโนมัติ",
                    "กันสต็อกทุกช่องทางและคืนเมื่อยกเลิกก่อนสำเร็จ",
                    inventoryStockTracking,
                    enabled = !busy && product?.inventoryStockTracking != true
                ) { inventoryStockTracking = it }
                if (product?.inventoryStockTracking == true) {
                    Text("จำนวนที่ขายได้ปัจจุบัน: $stock", color = QgMuted, fontSize = 9.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(enabled = !busy && !inventoryBusy, modifier = Modifier.weight(1f),
                            onClick = { inventoryAdjustOpen = true; inventoryError = null }) { Text("ปรับสต็อก") }
                        OutlinedButton(enabled = !busy && !inventoryBusy, modifier = Modifier.weight(1f),
                            onClick = {
                                inventoryBusy = true
                                inventoryError = null
                                inventoryScope.launch {
                                    try { inventoryHistory = onLoadInventoryHistory(product, null, null); inventoryHistoryOpen = true }
                                    catch (failure: Exception) { inventoryError = failure.message ?: "โหลดประวัติสต็อกไม่สำเร็จ" }
                                    finally { inventoryBusy = false }
                                }
                            }) { Text("ประวัติการเคลื่อนไหว") }
                    }
                    if (!inventoryError.isNullOrBlank()) Text(inventoryError!!, color = QgRed, fontSize = 9.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF7F8F9), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Row {
                    Text("ราคา Delivery", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "+ GP " + "%.2f".format(gpRate) + "%",
                        color = QgRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    deliveryPrice?.let { "฿" + "%.2f".format(it) }
                        ?: if (gpRateValid) "กรอกราคาหน้าร้าน" else "โหลดอัตรา GP ไม่สำเร็จ · บันทึกจะตรวจใหม่",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    "ระบบบวก GP จากราคาหน้าร้านอัตโนมัติ ร้านค้าแก้ไขราคานี้ไม่ได้",
                    color = QgMuted,
                    fontSize = 8.5.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                QgIcon("camera", Modifier.size(18.dp), QgRed)
                Spacer(Modifier.width(7.dp))
                Text("เลือกรูปจากเครื่อง", color = QgRed, fontWeight = FontWeight.Bold)
            }
            Text(
                "รูปจะเก็บในคลังรูปภาพของร้าน",
                color = QgMuted,
                fontSize = 8.5.sp,
                modifier = Modifier.padding(top = 3.dp)
            )

            Spacer(Modifier.height(8.dp))
            ProductSwitchRow("สถานะการขาย", "เปิดขายสินค้าในร้าน", available) { available = it }

            if (food) {
                Spacer(Modifier.height(12.dp))
                Text("ตัวเลือกเมนู", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "กำหนดธรรมดา / พิเศษ และท็อปปิ้งของเมนูนี้",
                    color = QgMuted,
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(8.dp))
                ProductSwitchRow(
                    "ขนาด / ความพิเศษ",
                    "ลูกค้าเลือกได้ 1 ตัวเลือก",
                    portionEnabled
                ) { portionEnabled = it }
                if (portionEnabled) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .background(Color(0xFFF5F6F7), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            Text("ธรรมดา · ราคาเดิม", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = specialPrice,
                            onValueChange = { specialPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            modifier = Modifier.weight(1f),
                            label = { Text("พิเศษ + บาท") },
                            singleLine = true
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("ท็อปปิ้ง", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text("ลูกค้าเลือกได้หลายอย่าง", color = QgMuted, fontSize = 8.5.sp)
                toppings.forEachIndexed { index, item ->
                    MerchantToppingRow(
                        item = item,
                        onChange = { changed ->
                            toppings = toppings.toMutableList().also { it[index] = changed }
                        },
                        onRemove = {
                            toppings = toppings.toMutableList().also { it.removeAt(index) }
                        }
                    )
                }
                OutlinedButton(
                    onClick = {
                        toppings = toppings + MerchantToppingOption("", 0.0, true)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) { Text("＋ เพิ่มท็อปปิ้งเอง") }
                Text(
                    "ติ๊กเฉพาะรายการที่ร้านต้องการเปิดขาย ราคาที่กรอกคือราคาเพิ่มจากราคาหลักของเมนู",
                    color = QgMuted,
                    fontSize = 8.5.sp,
                    lineHeight = 12.sp
                )
            } else {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = genericVariants,
                    onValueChange = { genericVariants = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ตัวเลือกเพิ่มเติม (คั่นด้วยเครื่องหมายจุลภาค)") }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("ช่องทางการขาย", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            ProductSwitchRow("ขายหน้าร้าน", "POS หน้าร้าน", posAvailable) { posAvailable = it }
            ProductSwitchRow(
                "ขาย Delivery",
                if (restriction == "none") "เปิดขายผ่าน QueueGo Delivery"
                else "สินค้ากลุ่ม " + merchantDeliveryRestrictionLabel(restriction) +
                    " ขายหน้าร้าน POS ได้ แต่ QueueGo จะไม่เปิดขายผ่าน Delivery",
                effectiveDelivery,
                enabled = restriction == "none"
            ) { deliveryAvailable = it }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("รายละเอียด") },
                minLines = 2
            )

            if (!localError.isNullOrBlank()) {
                Text(
                    localError!!,
                    color = QgRed,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    runCatching {
                        val value = price.toDoubleOrNull()
                            ?: throw IllegalArgumentException("กรุณากรอกราคาขายหน้าร้าน")
                        val stockValue = stock.toIntOrNull()
                            ?: throw IllegalArgumentException("กรุณากรอกจำนวนสินค้าในสต็อก")
                        require(name.trim().isNotBlank()) { "กรุณากรอกชื่อสินค้า" }
                        require(category.isNotBlank()) { "กรุณาเลือกหมวดหมู่สินค้า" }
                        require(value >= 0.0) { "ราคาขายหน้าร้านไม่ถูกต้อง" }
                        require(stockValue >= 0) { "จำนวนสินค้าในสต็อกไม่ถูกต้อง" }
                        val variants = if (food) {
                            merchantRestaurantVariantsJson(
                                portionEnabled = portionEnabled,
                                specialPrice = specialPrice.toDoubleOrNull() ?: 10.0,
                                toppings = toppings
                            )
                        } else {
                            merchantGenericVariantsJson(genericVariants)
                        }
                        MerchantProductDraft(
                            requestId = requestId,
                            name = name,
                            description = description,
                            category = category,
                            price = value,
                            stock = stockValue,
                            image = product?.image,
                            available = available,
                            posAvailable = posAvailable,
                            deliveryAvailable = effectiveDelivery,
                            variantsJson = variants,
                            inventoryStockTracking = inventoryStockTracking
                        )
                    }.onSuccess { draft ->
                        localError = null
                        onSave(draft, imageUri)
                    }.onFailure {
                        localError = it.message ?: "ข้อมูลสินค้าไม่ถูกต้อง"
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("เพิ่มเข้าร้าน / บันทึก", fontWeight = FontWeight.ExtraBold)
            }

            if (product != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (deleteArmed) onDelete(product) else deleteArmed = true
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (deleteArmed) "ยืนยันลบสินค้า" else "ลบสินค้า",
                        color = QgRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (deleteArmed) {
                    Text(
                        "ถ้ามีประวัติออเดอร์ ระบบจะ Archive สินค้าแทนการทำลายประวัติเดิม",
                        color = QgRed,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (inventoryAdjustOpen && product?.inventoryStockTracking == true) {
        MerchantInventoryAdjustmentDialog(
            busy = inventoryBusy, error = inventoryError,
            onDismiss = { if (!inventoryBusy) inventoryAdjustOpen = false },
            onSubmit = { delta, reason, note ->
                inventoryBusy = true
                inventoryError = null
                inventoryScope.launch {
                    try {
                        val receipt = onAdjustInventory(MerchantInventoryAdjustmentIntent(
                            inventoryRequestKey, product.id, delta, reason, note
                        ))
                        stock = receipt.stockAfter.toString()
                        inventoryRequestKey = UUID.randomUUID().toString()
                        inventoryAdjustOpen = false
                        inventoryHistory = onLoadInventoryHistory(product.copy(stock = receipt.stockAfter), null, null)
                    } catch (failure: Exception) {
                        inventoryError = failure.message ?: "ปรับสต็อกไม่สำเร็จ"
                    } finally { inventoryBusy = false }
                }
            }
        )
    }
    if (inventoryHistoryOpen) {
        AlertDialog(
            onDismissRequest = { inventoryHistoryOpen = false },
            title = { Text("ประวัติการเคลื่อนไหวสต็อก") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (inventoryHistory.entries.isEmpty()) Text("ยังไม่มีประวัติสต็อก", color = QgMuted)
                    inventoryHistory.entries.forEach { entry ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("${entry.actorName} · ${entry.eventType}", fontWeight = FontWeight.Bold)
                            Text("${entry.stockBefore} → ${entry.stockAfter} (${if (entry.delta > 0) "+" else ""}${entry.delta})", color = QgRed)
                            Text(listOfNotNull(entry.reason, entry.note, entry.createdAt).joinToString(" · "), color = QgMuted, fontSize = 10.sp)
                        }
                        HorizontalDivider()
                    }
                    if (inventoryHistory.hasMore && inventoryHistory.entries.isNotEmpty()) {
                        val last = inventoryHistory.entries.last()
                        TextButton(enabled = !inventoryBusy, onClick = {
                            inventoryBusy = true
                            inventoryScope.launch {
                                try {
                                    val next = onLoadInventoryHistory(product!!, last.createdAt, last.id)
                                    inventoryHistory = inventoryHistory.copy(entries = inventoryHistory.entries + next.entries, hasMore = next.hasMore)
                                } catch (failure: Exception) { inventoryError = failure.message ?: "โหลดประวัติเพิ่มไม่สำเร็จ" }
                                finally { inventoryBusy = false }
                            }
                        }) { Text("โหลดประวัติเพิ่ม") }
                    }
                    if (!inventoryError.isNullOrBlank()) Text(inventoryError!!, color = QgRed, fontSize = 10.sp)
                }
            },
            confirmButton = { TextButton(onClick = { inventoryHistoryOpen = false }) { Text("ปิด") } }
        )
    }
}

@Composable
private fun MerchantInventoryAdjustmentDialog(
    busy: Boolean, error: String?, onDismiss: () -> Unit, onSubmit: (Int, String, String) -> Unit
) {
    var delta by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(merchantInventoryAdjustmentReasons.first().first) }
    var note by remember { mutableStateOf("") }
    var reasonOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ปรับสต็อกสินค้า") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = delta,
                    onValueChange = { value -> delta = value.filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '-') }.take(7) },
                    label = { Text("เพิ่ม / ลดจำนวน") },
                    supportingText = { Text("จำนวนบวกเพื่อเพิ่ม จำนวนลบเพื่อลด") }, singleLine = true, enabled = !busy)
                Box {
                    OutlinedButton(onClick = { reasonOpen = true }, enabled = !busy) {
                        Text(merchantInventoryAdjustmentReasons.first { it.first == reason }.second)
                    }
                    DropdownMenu(expanded = reasonOpen, onDismissRequest = { reasonOpen = false }) {
                        merchantInventoryAdjustmentReasons.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { reason = key; reasonOpen = false })
                        }
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it.take(300) },
                    label = { Text("รายละเอียด / เอกสารอ้างอิง") }, supportingText = { Text("อย่างน้อย 3 ตัวอักษร") },
                    minLines = 2, enabled = !busy)
                if (!error.isNullOrBlank()) Text(error, color = QgRed, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && delta.toIntOrNull()?.let { it != 0 } == true && note.trim().length in 3..300,
                onClick = { delta.toIntOrNull()?.let { onSubmit(it, reason, note) } }) {
                Text(if (busy) "กำลังบันทึก…" else "บันทึกการปรับ")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("ยกเลิก") } }
    )
}

@Composable
private fun ProductSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = QgMuted, fontSize = 8.5.sp, lineHeight = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun MerchantToppingRow(
    item: MerchantToppingOption,
    onChange: (MerchantToppingOption) -> Unit,
    onRemove: () -> Unit
) {
    var priceText by remember(item.name, item.price) { mutableStateOf(item.price.toString()) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.enabled,
            onCheckedChange = { onChange(item.copy(enabled = it)) }
        )
        OutlinedTextField(
            value = item.name,
            onValueChange = { onChange(item.copy(name = it.take(60))) },
            modifier = Modifier.weight(1.25f),
            label = { Text("ชื่อท็อปปิ้ง", fontSize = 8.sp) },
            singleLine = true
        )
        Spacer(Modifier.width(5.dp))
        OutlinedTextField(
            value = priceText,
            onValueChange = { next ->
                priceText = next.filter { ch -> ch.isDigit() || ch == '.' }
                onChange(item.copy(price = priceText.toDoubleOrNull() ?: 0.0))
            },
            modifier = Modifier.weight(.85f),
            label = { Text("+ บาท", fontSize = 8.sp) },
            singleLine = true
        )
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            QgIcon("close", Modifier.size(16.dp), QgRed)
        }
    }
}

private const val MERCHANT_PRODUCT_PREVIEW_MAX_SIDE = 512

internal fun decodeMerchantProductPreview(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MERCHANT_PRODUCT_PREVIEW_MAX_SIDE) {
        sample *= 2
    }
    return resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(
            input,
            null,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }
}

@Composable
private fun MerchantProductLocalImage(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        image = withContext(Dispatchers.IO) {
            runCatching { decodeMerchantProductPreview(context, uri) }.getOrNull()
        }
    }
    DisposableEffect(image) {
        val owned = image
        onDispose {
            if (owned != null && !owned.isRecycled) owned.recycle()
        }
    }
    val shown = image
    if (shown != null && !shown.isRecycled) {
        Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(13.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFFF0F1F2)),
            contentAlignment = Alignment.Center
        ) { Text("รูปสินค้า", color = QgMuted, fontSize = 8.sp) }
    }
}
