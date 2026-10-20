package com.queuego.merchant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRemoteImage
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

@Composable
internal fun MerchantMarketCatalogScreen(
    auth: NativeAuth, shop: MerchantShop, api: MerchantApi,
    onSaved: () -> Unit, onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val pending = remember(auth.user.id, shop.id) {
        MerchantMarketCatalogPendingStore(File(context.noBackupFilesDir, "market-catalog-pending"), auth.user.id, shop.id)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<MerchantMarketCatalogItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<MerchantMarketCatalogItem?>(null) }
    var rate by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(search) {
        delay(300)
        if (!saving && search.trim() != query) { query = search.trim(); offset = 0 }
    }
    LaunchedEffect(query, category, offset, refresh, auth.session.accessToken) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            loading = true
            error = null
            try {
                val old = withContext(Dispatchers.IO) { pending.load() }
                if (old != null) {
                    api.addMarketCatalogProduct(auth, shop.id, old)
                    withContext(Dispatchers.IO) { pending.clear(old.requestId) }
                    notice = "ยืนยันสินค้าเพิ่มค้างแล้ว"
                    selected = null
                    onSaved()
                }
                coroutineScope {
                    val categoryRead = async { api.marketCatalogCategories(auth) }
                    val pageRead = async { api.marketCatalogPage(auth, shop.id, query, category, offset) }
                    val rateRead = async { try { api.effectiveGp(auth, shop.id) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null } }
                    val page = pageRead.await()
                    categories = categoryRead.await()
                    items = page.items
                    more = page.hasMore
                    rate = rateRead.await()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "โหลดแคตตาล็อกตลาดไม่ได้"
            } finally { loading = false }
            awaitCancellation()
        }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { if (selected != null) selected = null else onBack() }, enabled = !saving) { Text("กลับ") }
            Text("แคตตาล็อกตลาด", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
        }
        notice?.let { Text(it, color = QgMuted) }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { refresh++ }, enabled = !saving && !loading) { Text("ลองโหลด / ตรวจรายการค้างอีกครั้ง") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val selection = selected
        if (selection == null) {
            OutlinedTextField(value = search, onValueChange = { search = it.take(100) }, label = { Text("ค้นหาสินค้า") },
                singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
            MerchantCategorySelect(categories, category, !loading && !saving) { category = it; offset = 0 }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(items, key = { it.id }) { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        QgRemoteImage(source = row.image, modifier = Modifier.size(84.dp), fallback = row.name)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.name, fontWeight = FontWeight.Bold)
                            Text(row.category + " · " + row.sourceUnit, color = QgMuted, fontSize = 12.sp)
                            Button(onClick = { selected = row; error = null }, enabled = row.productId == null && !loading && !saving) {
                                Text(if (row.productId != null) "มีในร้านแล้ว" else "เพิ่มสินค้าเข้าร้าน")
                            }
                        }
                    }
                    HorizontalDivider()
                }
                if (items.isEmpty() && !loading && error == null) item { Text("ไม่พบสินค้าในหมวดนี้", Modifier.padding(20.dp)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { offset = (offset - 50).coerceAtLeast(0) }, enabled = offset > 0 && !loading && !saving) { Text("ก่อนหน้า") }
                Text("หน้า ${offset / 50 + 1}", Modifier.padding(12.dp))
                OutlinedButton(onClick = { offset += 50 }, enabled = more && !loading && !saving) { Text("ถัดไป") }
            }
        } else {
            MerchantMarketCatalogAddForm(selection, rate, saving || loading, onSave = { intent ->
                if (!saving && !loading) {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            val stable = withContext(Dispatchers.IO) { pending.prepare(intent) }
                            currentCoroutineContext().ensureActive()
                            api.addMarketCatalogProduct(auth, shop.id, stable)
                            withContext(Dispatchers.IO) { pending.clear(stable.requestId) }
                            notice = "เพิ่มสินค้าเข้าร้านแล้ว"
                            selected = null
                            onSaved()
                            refresh++
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = "ยังยืนยันการเพิ่มสินค้าไม่ได้ กดตรวจรายการค้างก่อนทำรายการอื่น: " + failure.message
                        } finally { saving = false }
                    }
                }
            })
        }
    }
}

@Composable
private fun ColumnScope.MerchantMarketCatalogAddForm(item: MerchantMarketCatalogItem, rate: Double?, busy: Boolean, onSave: (MarketCatalogIntent) -> Unit) {
    var price by rememberSaveable(item.id) { mutableStateOf("") }
    var unit by rememberSaveable(item.id) { mutableStateOf(item.suggestedUnit) }
    var pack by rememberSaveable(item.id) { mutableStateOf(item.suggestedPack.toString()) }
    var weight by rememberSaveable(item.id) { mutableStateOf(when (item.suggestedUnit) { "กิโลกรัม" -> item.suggestedPack.toString(); "ขีด" -> (item.suggestedPack / 10).toString(); "กรัม" -> (item.suggestedPack / 1000).toString(); else -> "" }) }
    var cost by rememberSaveable(item.id) { mutableStateOf("0") }
    var stock by rememberSaveable(item.id) { mutableStateOf("0") }
    var minimum by rememberSaveable(item.id) { mutableStateOf("0") }
    var available by rememberSaveable(item.id) { mutableStateOf(true) }
    var validation by remember { mutableStateOf<String?>(null) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        QgRemoteImage(source = item.image, modifier = Modifier.fillMaxWidth().height(170.dp), fallback = item.name)
        Text(item.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(item.category + " · " + item.sourceUnit, color = QgMuted)
        MarketCatalogNumber("ราคาหน้าร้าน / ชุด (บาท)", price, !busy) { price = it }
        val amount = price.toDoubleOrNull()
        Text(if (rate != null && amount != null && amount > 0) "GP ${rate}% · เดลิเวอรี่ ฿${merchantDeliveryPriceFromStore(amount, rate)}" else "ราคาเดลิเวอรี่และ GP ยืนยันจากระบบหลังบันทึก", color = QgMuted)
        Text("หน่วยขาย: $unit")
        MerchantCategorySelect(merchantMarketCatalogUnits, unit, !busy) { if (it in merchantMarketCatalogUnits) unit = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (kg in 1..3) OutlinedButton(onClick = { unit = "กิโลกรัม"; pack = kg.toString(); weight = kg.toString() }, enabled = !busy) { Text("$kg กก.") }
        }
        MarketCatalogNumber("ปริมาณต่อชุด (แก้ไขได้)", pack, !busy) { pack = it }
        MarketCatalogNumber("น้ำหนักต่อชุด (กก.)", weight, !busy) { weight = it }
        MarketCatalogNumber("ต้นทุนต่อชุด (บาท)", cost, !busy) { cost = it }
        MarketCatalogNumber("สต๊อกเริ่มต้น", stock, !busy) { stock = it }
        MarketCatalogNumber("แจ้งเตือนใกล้หมด", minimum, !busy) { minimum = it }
        Row { Checkbox(checked = available, onCheckedChange = { available = it }, enabled = !busy); Text("เปิดขายเดลิเวอรี่", Modifier.padding(top = 12.dp)) }
        validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
    }
    Button(onClick = {
        try {
            val intent = MarketCatalogIntent(UUID.randomUUID().toString(), item.id, price.toDoubleOrNull() ?: Double.NaN,
                unit, pack.toDoubleOrNull() ?: Double.NaN, weight.toDoubleOrNull() ?: Double.NaN,
                cost.toDoubleOrNull() ?: Double.NaN, stock.toDoubleOrNull() ?: Double.NaN, minimum.toDoubleOrNull() ?: Double.NaN, available)
            intent.validate()
            validation = null
            onSave(intent)
        } catch (failure: Exception) { validation = failure.message }
    }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(if (busy) "กำลังยืนยัน…" else "เพิ่มสินค้าเข้าร้าน") }
}

@Composable
private fun MarketCatalogNumber(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { onChange(it.take(24)) }, label = { Text(label) }, singleLine = true,
        enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}
