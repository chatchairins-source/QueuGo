package com.queuego.merchant

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.queuego.shared.QgLine
import com.queuego.shared.QgLongdoLocationPickerMap
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val merchantCategories = listOf(
    "food" to "ร้านอาหาร",
    "grocery" to "ร้านขายของชำ",
    "cafe" to "เครื่องดื่ม / คาเฟ่",
    "laundry" to "ร้านฝากซัก",
    "market" to "ตลาดสด",
    "shopping" to "ช้อปปิ้ง",
    "other" to "อื่น ๆ"
)

private val merchantShoppingSubcategories = listOf(
    "mobile_accessories" to "มือถือและอุปกรณ์",
    "computer_it" to "คอมพิวเตอร์และไอที",
    "automotive_car" to "อะไหล่รถยนต์",
    "automotive_motorcycle" to "อะไหล่มอเตอร์ไซค์",
    "fashion_accessories" to "เสื้อผ้าและเครื่องประดับ",
    "toys" to "ของเล่น",
    "home_decor" to "ตกแต่งบ้าน"
)

@Composable
internal fun MerchantShopSetupScreen(
    shop: MerchantShop,
    contactName: String,
    busy: Boolean,
    gpsPoint: QgMapPoint?,
    gpsSignal: Int,
    logoUri: Uri?,
    coverUri: Uri?,
    onPickLogo: () -> Unit,
    onPickCover: () -> Unit,
    onGps: () -> Unit,
    onSave: (MerchantShopSetupDraft) -> Unit,
    onBack: () -> Unit
) {
    var name by remember(shop.id, shop.name) { mutableStateOf(shop.name) }
    var contact by remember(shop.id, contactName) { mutableStateOf(contactName) }
    var phone by remember(shop.id, shop.phone) { mutableStateOf(shop.phone.orEmpty()) }
    var category by remember(shop.id, shop.category) { mutableStateOf(shop.category.ifBlank { "other" }) }
    var categoriesOpen by remember { mutableStateOf(false) }
    var subcategories by remember(shop.id, shop.shoppingSubcategories) {
        mutableStateOf(shop.shoppingSubcategories)
    }
    var address by remember(shop.id, shop.address) { mutableStateOf(shop.address.orEmpty()) }
    var latitude by remember(shop.id, shop.latitude) { mutableStateOf(shop.latitude) }
    var longitude by remember(shop.id, shop.longitude) { mutableStateOf(shop.longitude) }
    var openTime by remember(shop.id, shop.openTime) { mutableStateOf(shop.openTime ?: "06:00") }
    var closeTime by remember(shop.id, shop.closeTime) { mutableStateOf(shop.closeTime ?: "22:00") }
    var description by remember(shop.id, shop.description) { mutableStateOf(shop.description.orEmpty()) }
    var mapReady by remember { mutableStateOf(false) }
    var recenterToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(gpsSignal, gpsPoint?.latitude, gpsPoint?.longitude) {
        val point = gpsPoint ?: return@LaunchedEffect
        if (!point.valid) return@LaunchedEffect
        latitude = point.latitude
        longitude = point.longitude
        recenterToken = gpsSignal
    }

    val compact = LocalConfiguration.current.screenWidthDp <= 380
    val currentPoint = if (latitude != null && longitude != null &&
        merchantCoordinateValid(latitude!!, longitude!!)
    ) QgMapPoint(latitude!!, longitude!!, 0) else null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(38.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
            ) { Text("กลับ") }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("แก้ไขข้อมูลร้าน", fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("ข้อมูลและตำแหน่งร้าน", color = QgMuted, fontSize = 11.sp)
            }
        }

        SetupField("ชื่อร้าน") {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true)
        }
        SetupField("ชื่อผู้ติดต่อ") {
            OutlinedTextField(contact, { contact = it }, Modifier.fillMaxWidth(), singleLine = true)
        }
        SetupField("เบอร์โทรศัพท์") {
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), singleLine = true)
        }

        SetupField("หมวดหมู่") {
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { categoriesOpen = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(merchantCategories.firstOrNull { it.first == category }?.second ?: "อื่น ๆ")
                }
                DropdownMenu(
                    expanded = categoriesOpen,
                    onDismissRequest = { categoriesOpen = false }
                ) {
                    merchantCategories.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                category = key
                                if (key != "shopping") subcategories = emptySet()
                                categoriesOpen = false
                            }
                        )
                    }
                }
            }
            Text(
                "ร้านช้อปปิ้งเลือกหมวดย่อยได้หลายหมวด โดยยังใช้สินค้าและสต๊อกชุดเดิม",
                color = QgMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (category == "shopping") {
            SetupField("หมวดย่อยช้อปปิ้ง") {
                merchantShoppingSubcategories.chunked(if (compact) 1 else 2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (key, label) ->
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(androidx.compose.ui.graphics.Color.White)
                                    .border(1.dp, QgLine, RoundedCornerShape(12.dp))
                                    .clickable {
                                        subcategories = if (key in subcategories) subcategories - key else subcategories + key
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = key in subcategories,
                                    onCheckedChange = {
                                        subcategories = if (it) subcategories + key else subcategories - key
                                    }
                                )
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (row.size == 1 && !compact) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        SetupField("ที่อยู่ร้าน") {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, bottom = 9.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFF4FFF9))
                .border(1.dp, androidx.compose.ui.graphics.Color(0xFFD9EEE3), RoundedCornerShape(17.dp))
                .padding(14.dp)
        ) {
            Text("ปักพิกัดร้าน", color = androidx.compose.ui.graphics.Color(0xFF183D32), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "เลื่อนแผนที่ให้หมุดอยู่ตรงหน้าร้าน แล้วกดบันทึก",
                color = androidx.compose.ui.graphics.Color(0xFF708A7D),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 11.dp)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (compact) 150.dp else 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFFE9F5EF))
            ) {
                QgLongdoLocationPickerMap(
                    initialPoint = currentPoint,
                    recenterPoint = gpsPoint,
                    recenterToken = recenterToken,
                    modifier = Modifier.fillMaxSize(),
                    onCenterChanged = {
                        latitude = it.latitude
                        longitude = it.longitude
                    },
                    onReady = { mapReady = it }
                )
                if (!mapReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onGps, modifier = Modifier.height(38.dp)) {
                Text("ใช้ตำแหน่ง GPS ปัจจุบัน", fontSize = 12.sp)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                if (currentPoint != null)
                    "พิกัด: %.6f, %.6f".format(currentPoint.latitude, currentPoint.longitude)
                else "ยังไม่ได้ปักพิกัดร้าน",
                color = androidx.compose.ui.graphics.Color(0xFF728C7E),
                fontSize = 11.sp
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(Modifier.weight(1f)) {
                Text("เปิด", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(openTime, { openTime = it }, Modifier.fillMaxWidth(), singleLine = true)
            }
            Column(Modifier.weight(1f)) {
                Text("ปิด", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(closeTime, { closeTime = it }, Modifier.fillMaxWidth(), singleLine = true)
            }
        }

        SetupField("รายละเอียด") {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MerchantSetupImage(
                title = "รูปหน้าร้าน / โปรไฟล์ร้าน",
                remote = shop.logo,
                local = logoUri,
                fallback = shop.name,
                modifier = Modifier.weight(1f),
                onPick = onPickLogo
            )
            MerchantSetupImage(
                title = "รูปหน้าปก",
                remote = shop.cover,
                local = coverUri,
                fallback = shop.name,
                modifier = Modifier.weight(1f),
                onPick = onPickCover
            )
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onSave(
                    MerchantShopSetupDraft(
                        shopName = name,
                        contactName = contact,
                        phone = phone,
                        category = category,
                        shoppingSubcategories = subcategories,
                        address = address,
                        latitude = latitude,
                        longitude = longitude,
                        openTime = openTime,
                        closeTime = closeTime,
                        description = description,
                        logo = null,
                        cover = null
                    )
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("บันทึกข้อมูลร้าน", fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SetupField(
    label: String,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
        content()
    }
}

@Composable
private fun MerchantSetupImage(
    title: String,
    remote: String?,
    local: Uri?,
    fallback: String,
    modifier: Modifier,
    onPick: () -> Unit
) {
    Column(modifier) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (local != null) {
            MerchantLocalImage(local, Modifier.fillMaxWidth().aspectRatio(1.4f))
        } else {
            QgRemoteImage(remote, Modifier.fillMaxWidth().aspectRatio(1.4f), fallback, cornerRadius = 12.dp)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().height(46.dp)) {
            Text("เลือกรูป", color = QgRed, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MerchantLocalImage(uri: Uri, modifier: Modifier) {
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
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFF0F1F2)),
            contentAlignment = Alignment.Center
        ) { Text("รูปที่เลือก", color = QgMuted) }
    }
}

internal fun merchantHasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

internal fun merchantLastKnownLocation(context: Context): QgMapPoint? {
    if (!merchantHasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val locations = manager.getProviders(true).mapNotNull { provider ->
        runCatching {
            @Suppress("MissingPermission")
            manager.getLastKnownLocation(provider)
        }.getOrNull()
    }
    val best = locations.maxByOrNull { it.time } ?: return null
    return QgMapPoint(best.latitude, best.longitude, 0).takeIf { it.valid }
}
