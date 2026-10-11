package com.queuego.merchant

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun MerchantProductEditorScreen(
    shop: MerchantShop,
    product: MerchantProduct?,
    initialCategory: String? = null,
    gpRate: Double,
    busy: Boolean,
    onSave: (MerchantProductDraft, Uri?) -> Unit,
    onDelete: (MerchantProduct) -> Unit,
    onBack: () -> Unit
) {
    val categories = remember(shop.category, shop.shoppingSubcategories) { merchantProductCategories(shop) }
    val food = merchantIsFoodProductShop(shop)
    val initialOptions = remember(product?.id, product?.variantsJson) {
        merchantRestaurantOptionState(product?.variantsJson ?: "[]")
    }
    val requestId = remember(product?.id) { product?.id ?: UUID.randomUUID().toString() }

    var name by remember(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    val description = product?.description.orEmpty()
    var category by remember(product?.id, initialCategory) {
        mutableStateOf(
            product?.category?.takeIf { it in categories }
                ?: initialCategory?.takeIf { it in categories }
                ?: merchantDefaultProductCategory(shop).takeIf { it in categories }
                ?: ""
        )
    }
    var categoryOpen by remember { mutableStateOf(false) }
    var price by remember(product?.id) { mutableStateOf((product?.posPrice ?: product?.price)?.toString().orEmpty()) }
    var stock by remember(product?.id) { mutableStateOf(product?.stock?.toString() ?: "0") }
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
    val deliveryPrice = storePrice?.let { merchantDeliveryPriceFromStore(it, gpRate) }
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
                .background(Color.White, RoundedCornerShape(17.dp))
                .border(1.dp, QgLine, RoundedCornerShape(17.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (imageUri != null) {
                    MerchantProductLocalImage(
                        imageUri!!,
                        Modifier.size(58.dp)
                    )
                } else {
                    QgRemoteImage(
                        product?.image,
                        Modifier.size(58.dp),
                        product?.name ?: "สินค้า",
                        cornerRadius = 12.dp
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
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(category.ifBlank { "เลือกหมวดหมู่สินค้า" })
                }
                DropdownMenu(
                    expanded = categoryOpen,
                    onDismissRequest = { categoryOpen = false }
                ) {
                    categories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                category = option
                                categoryOpen = false
                                localError = null
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    modifier = Modifier.weight(1f),
                    label = { Text("ราคาขายหน้าร้าน *") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text("จำนวนในสต็อก *") },
                    singleLine = true
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
            if (imageUri != null || !product?.image.isNullOrBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp)
                        .background(Color(0xFFF7F8F9), RoundedCornerShape(11.dp))
                        .border(1.dp, QgLine, RoundedCornerShape(11.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (imageUri != null) {
                        MerchantProductLocalImage(imageUri!!, Modifier.size(42.dp))
                    } else {
                        QgRemoteImage(
                            product?.image,
                            Modifier.size(42.dp),
                            product?.name ?: "สินค้า",
                            cornerRadius = 9.dp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (imageUri != null) "รูปสินค้าที่เลือก" else "รูปสินค้าปัจจุบัน",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            if (imageUri != null) "รองรับ JPG, PNG, WEBP" else "มีรูปบันทึกอยู่แล้ว",
                            color = QgMuted,
                            fontSize = 8.5.sp
                        )
                        Text(
                            if (imageUri != null) "เลือกรูปแล้วกดบันทึกเพื่ออัปโหลด" else "บันทึกอยู่ในระบบแล้ว",
                            color = Color(0xFF0A9660),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

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
            if (restriction != "none") {
                Text(
                    "สินค้ากลุ่ม " + merchantDeliveryRestrictionLabel(restriction) +
                        " ขายหน้าร้าน POS ได้ แต่ QueueGo จะไม่เปิดขายผ่าน Delivery",
                    color = QgRed,
                    fontSize = 8.5.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
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
                    deliveryPrice?.let { "฿" + "%.2f".format(it) } ?: "กำลังคำนวณ",
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
                            variantsJson = variants
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

@Composable
private fun MerchantProductLocalImage(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        image = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    if (image != null) {
        Image(
            bitmap = image!!,
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
